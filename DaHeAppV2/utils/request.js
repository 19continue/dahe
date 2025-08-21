import { clearTokenSession, getAccessToken, getTokenExpiresAt, saveAuthSession } from './auth'

const FIXED_BASE_URL = 'https://www.daheseeds.com:3100/api/v2'
const SESSION_REFRESH_THRESHOLD_MS = 3 * 24 * 60 * 60 * 1000
const SESSION_REFRESH_RETRY_INTERVAL_MS = 60 * 1000
const AUTH_LAUNCH_PAGE = '/pages/launch/index'

let sessionRefreshPromise = null
let lastSessionRefreshAttemptAt = 0
const inflightRequestMap = new Map()
const settledRequestMap = new Map()
const DEFAULT_GET_DEDUPE_WINDOW_MS = 1000

const normalizeBaseUrl = (url) => {
  const value = String(url || '').trim().replace(/\/+$/, '')
  if (!value) return ''
  if (value.endsWith('/api/v2')) return value
  if (value.endsWith('/api')) return `${value}/v2`
  return `${value}/api/v2`
}

const getConfiguredBaseUrl = () => {
  return normalizeBaseUrl(FIXED_BASE_URL)
}

const isVueComponentLike = (value) => {
  return !!value
    && typeof value === 'object'
    && (
      !!value.$
      || !!value.$options
      || !!value.$scope
      || typeof value.$emit === 'function'
      || typeof value.$watch === 'function'
    )
}

const isPlainObject = (value) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  if (Object.prototype.toString.call(value) !== '[object Object]') return false
  const proto = Object.getPrototypeOf(value)
  return proto === Object.prototype || proto === null
}

const normalizeObjectPayload = (value, label) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value
  if (isVueComponentLike(value)) {
    console.warn(`[request] ignore component instance passed as ${label}`)
    return {}
  }
  return value
}

const buildStableValue = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => buildStableValue(item))
  }
  if (isPlainObject(value)) {
    return Object.keys(value)
      .sort()
      .reduce((result, key) => {
        result[key] = buildStableValue(value[key])
        return result
      }, {})
  }
  return value
}

const buildRequestDedupeKey = (options) => {
  const method = String((options && options.method) || 'GET').trim().toUpperCase()
  const explicitDedupe = options && Object.prototype.hasOwnProperty.call(options, 'dedupe')
    ? !!options.dedupe
    : method === 'GET'
  if (!explicitDedupe) return ''
  const explicitKey = String((options && options.dedupeKey) || '').trim()
  if (explicitKey) {
    return `${method}:${explicitKey}`
  }
  const normalizedHeader = normalizeObjectPayload((options && options.header) || {}, 'request header')
  const cleanedData = cleanPayload(options && options.data, method)
  const keyPayload = {
    url: String((options && options.url) || '').trim(),
    method,
    data: buildStableValue(cleanedData),
    header: buildStableValue(isPlainObject(normalizedHeader) ? normalizedHeader : {})
  }
  return JSON.stringify(keyPayload)
}

const readSettledRequestCache = (key) => {
  const safeKey = String(key || '').trim()
  if (!safeKey) return null
  const cached = settledRequestMap.get(safeKey)
  if (!cached) return null
  if (Date.now() >= Number(cached.expiresAt || 0)) {
    settledRequestMap.delete(safeKey)
    return null
  }
  return cached.value
}

const writeSettledRequestCache = (key, value, windowMs) => {
  const safeKey = String(key || '').trim()
  const safeWindow = Math.max(0, Number(windowMs) || 0)
  if (!safeKey || safeWindow <= 0) return
  settledRequestMap.set(safeKey, {
    value,
    expiresAt: Date.now() + safeWindow
  })
}

const clearSettledRequestCache = () => {
  settledRequestMap.clear()
}

const cleanPayload = (data, method) => {
  const normalizedData = normalizeObjectPayload(data, 'request data')
  if (!isPlainObject(normalizedData)) return normalizedData
  const isQueryMethod = String(method || 'GET').toUpperCase() === 'GET'
  const next = {}
  Object.keys(normalizedData).forEach((key) => {
    const value = normalizedData[key]
    if (value === undefined) return
    if (isQueryMethod) {
      if (value === null) return
      if (typeof value === 'string') {
        const normalized = value.trim().toLowerCase()
        if (!normalized || normalized === 'undefined' || normalized === 'null') return
      }
    }
    next[key] = value
  })
  return next
}

const normalizeUploadErrorMessage = (message) => {
  const text = String(message || '').trim()
  if (!text) return '上传失败'
  if (
    text.includes('图片文件内容无效') ||
    text.includes('图片文件内容校验失败') ||
    text.includes('图片文件内容与扩展名不匹配')
  ) {
    return '上传失败：图片内容与文件格式不一致，请重新选择'
  }
  if (text.includes('文件夹正被使用')) {
    return '上传失败：目标文件夹处理中，请稍后重试'
  }
  return text
}

const relaunchAuthEntry = (message) => {
  clearTokenSession()
  uni.showToast({
    title: message || '登录状态失效，请重新登录',
    icon: 'none'
  })
  const pages = getCurrentPages()
  const route = pages.length ? `/${pages[pages.length - 1].route}` : ''
  if (route !== AUTH_LAUNCH_PAGE) {
    setTimeout(() => uni.reLaunch({ url: AUTH_LAUNCH_PAGE }), 180)
  }
}

const resolveHttpErrorMessage = (statusCode, data) => {
  const code = Number(statusCode || 0)
  const backendMessage = data && typeof data === 'object' ? String(data.message || '').trim() : ''
  if (backendMessage) return backendMessage
  if (code === 404) return '接口不存在(404)'
  if (code === 500) return '服务器内部错误(500)'
  if (code > 0) return `请求失败(${code})`
  return '网络请求失败'
}

const parseLocalDateTime = (value) => {
  const text = String(value || '').trim()
  if (!text) return null
  const matched = text.match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/)
  if (!matched) return null
  const [, year, month, day, hour, minute, second, fraction] = matched
  const milliseconds = fraction ? Number(String(fraction).padEnd(3, '0').slice(0, 3)) : 0
  const date = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second || 0),
    milliseconds
  )
  return Number.isNaN(date.getTime()) ? null : date
}

const isMiniappAuthOpenPath = (url) => {
  const path = String(url || '').trim()
  return path === '/miniapp/auth/entry'
    || path === '/miniapp/auth/apply'
    || path === '/miniapp/auth/wechat-login'
}

const isRefreshPath = (url) => String(url || '').trim() === '/miniapp/auth/session/refresh'

const shouldAutoRefreshSession = (url) => {
  if (isMiniappAuthOpenPath(url) || isRefreshPath(url)) return false
  const token = getAccessToken()
  if (!token) return false
  const expiresAt = parseLocalDateTime(getTokenExpiresAt())
  if (!expiresAt) return false
  const remainingMs = expiresAt.getTime() - Date.now()
  if (remainingMs > SESSION_REFRESH_THRESHOLD_MS) return false
  if (Date.now() - lastSessionRefreshAttemptAt < SESSION_REFRESH_RETRY_INTERVAL_MS) return false
  return true
}

const requestOnce = (baseUrl, options, meta = {}) => {
  const { url, method = 'GET', data, header = {}, __skipAutoRefresh, ...rest } = options
  const cleanedData = cleanPayload(data, method)
  const headerPayload = normalizeObjectPayload(header, 'request header')
  const normalizedHeader = isPlainObject(headerPayload) ? headerPayload : {}
  const config = {
    url: `${baseUrl}${url}`,
    method,
    data: cleanedData,
    header: {
      'Content-Type': 'application/json',
      ...normalizedHeader
    },
    ...rest
  }

  const token = String(getAccessToken() || '').trim()
  if (token) {
    config.header.Authorization = `Bearer ${token}`
    config.header['X-Access-Token'] = token
  }

  return new Promise((resolve, reject) => {
    uni.request({
      ...config,
      success: (res) => {
        const { statusCode, data: body } = res
        if (statusCode >= 200 && statusCode < 300) {
          if (body && body.code === 10200) {
            resolve(body.data)
            return
          }
          if (body && Number(body.code) === 10100) {
            if (!meta.silentUnauthorized && !meta.deferUnauthorizedHandling) {
              relaunchAuthEntry((body && body.message) || '请先登录')
            }
          } else if (!meta.silentErrorToast) {
            uni.showToast({
              title: (body && body.message) || '请求失败',
              icon: 'none'
            })
          }
          reject(body || { code: 0, message: '请求失败' })
          return
        }
        if (!meta.silentErrorToast) {
          uni.showToast({
            title: resolveHttpErrorMessage(statusCode, body),
            icon: 'none'
          })
        }
        reject({
          ...res,
          errorMessage: resolveHttpErrorMessage(statusCode, body)
        })
      },
      fail: (err) => {
        if (!meta.silentErrorToast) {
          uni.showToast({
            title: '网络请求失败',
            icon: 'none'
          })
        }
        reject(err)
      }
    })
  })
}

const refreshMiniappSession = async (baseUrl) => {
  const token = getAccessToken()
  if (!token) return false
  const payload = await requestOnce(
    baseUrl,
    {
      url: '/miniapp/auth/session/refresh',
      method: 'POST',
      data: {}
    },
    {
      silentErrorToast: true,
      silentUnauthorized: true
    }
  )
  if (!payload || !payload.accessToken) {
    relaunchAuthEntry((payload && payload.message) || '登录状态失效，请重新登录')
    throw new Error('miniapp session refresh denied')
  }
  saveAuthSession(payload)
  return true
}

const tryRecoverUnauthorized = async (baseUrl, options, error) => {
  if (options.__skipUnauthorizedRecovery || isMiniappAuthOpenPath(options.url) || isRefreshPath(options.url)) {
    throw error
  }
  const token = getAccessToken()
  if (!token) {
    throw error
  }
  const refreshed = await refreshMiniappSession(baseUrl).catch(() => false)
  if (!refreshed) {
    throw error
  }
  return requestOnce(
    baseUrl,
    {
      ...options,
      __skipAutoRefresh: true,
      __skipUnauthorizedRecovery: true
    },
    { deferUnauthorizedHandling: false }
  )
}

const ensureMiniappSessionFresh = async (baseUrl, url) => {
  if (!shouldAutoRefreshSession(url)) {
    return false
  }
  if (!sessionRefreshPromise) {
    lastSessionRefreshAttemptAt = Date.now()
    sessionRefreshPromise = refreshMiniappSession(baseUrl)
      .catch((error) => {
        if (error && Number(error.code) === 10100) {
          relaunchAuthEntry((error && error.message) || '登录已过期，请重新登录')
        }
        return false
      })
      .finally(() => {
        sessionRefreshPromise = null
      })
  }
  return sessionRefreshPromise
}

const uploadOnce = (baseUrl, options) => {
  const { url, filePath, name = 'file', formData = {}, header = {}, __skipAutoRefresh, ...rest } = options
  const formPayload = normalizeObjectPayload(formData, 'upload formData')
  const headerPayload = normalizeObjectPayload(header, 'upload header')
  const normalizedFormData = isPlainObject(formPayload) ? formPayload : {}
  const normalizedHeader = isPlainObject(headerPayload) ? headerPayload : {}
  const token = String(getAccessToken() || '').trim()
  const uploadFormData = token
    ? {
      accessToken: token,
      ...normalizedFormData
    }
    : { ...normalizedFormData }
  const uploadHeader = token
    ? {
      Authorization: `Bearer ${token}`,
      'X-Access-Token': token,
      ...normalizedHeader
    }
    : { ...normalizedHeader }
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${baseUrl}${url}`,
      filePath,
      name,
      formData: uploadFormData,
      header: uploadHeader,
      ...rest,
      success: (res) => {
        let parsed = null
        try {
          parsed = JSON.parse((res && res.data) || '{}')
        } catch (error) {
          reject(new Error('上传失败'))
          return
        }
        if (!parsed || Number(parsed.code) !== 10200 || !parsed.data) {
          reject(parsed || { code: 0, message: '上传失败' })
          return
        }
        resolve(parsed.data)
      },
      fail: (err) => reject(err)
    })
  })
}

const request = async (options) => {
  const baseUrl = getConfiguredBaseUrl()
  const safeOptions = options || {}
  const {
    dedupe,
    dedupeKey,
    dedupeWindowMs,
    ...requestOptions
  } = safeOptions
  const method = String((requestOptions && requestOptions.method) || 'GET').trim().toUpperCase()
  const normalizedRequestOptions = {
    ...requestOptions
  }
  if (dedupe !== undefined) {
    normalizedRequestOptions.dedupe = dedupe
  }
  if (dedupeKey !== undefined) {
    normalizedRequestOptions.dedupeKey = dedupeKey
  }
  if (dedupeWindowMs !== undefined) {
    normalizedRequestOptions.dedupeWindowMs = dedupeWindowMs
  }
  const inflightKey = buildRequestDedupeKey(normalizedRequestOptions)
  const effectiveDedupeWindowMs = method === 'GET'
    ? Math.max(0, Number(dedupeWindowMs == null ? DEFAULT_GET_DEDUPE_WINDOW_MS : dedupeWindowMs) || 0)
    : 0
  if (method !== 'GET') {
    clearSettledRequestCache()
  } else if (inflightKey) {
    const settledValue = readSettledRequestCache(inflightKey)
    if (settledValue !== null) {
      return Promise.resolve(settledValue)
    }
  }
  if (inflightKey && inflightRequestMap.has(inflightKey)) {
    return inflightRequestMap.get(inflightKey)
  }
  const runner = (async () => {
    if (!requestOptions.__skipAutoRefresh) {
      await ensureMiniappSessionFresh(baseUrl, requestOptions.url)
    }
    try {
      return await requestOnce(baseUrl, requestOptions, { deferUnauthorizedHandling: true })
    } catch (error) {
      if (error && Number(error.code) === 10100) {
        try {
          return await tryRecoverUnauthorized(baseUrl, requestOptions, error)
        } catch (recoverError) {
          relaunchAuthEntry((recoverError && recoverError.message) || (error && error.message) || '登录已过期，请重新登录')
          throw recoverError
        }
      }
      throw error
    }
  })()
  if (inflightKey) {
    inflightRequestMap.set(inflightKey, runner)
    runner.finally(() => {
      if (inflightRequestMap.get(inflightKey) === runner) {
        inflightRequestMap.delete(inflightKey)
      }
    })
    runner.then((value) => {
      writeSettledRequestCache(inflightKey, value, effectiveDedupeWindowMs)
      return value
    }).catch(() => {
      if (inflightKey) {
        settledRequestMap.delete(inflightKey)
      }
    })
  }
  return runner
}

const upload = async (options) => {
  const baseUrl = getConfiguredBaseUrl()
  if (!options.__skipAutoRefresh) {
    await ensureMiniappSessionFresh(baseUrl, options.url)
  }
  try {
    return await uploadOnce(baseUrl, options)
  } catch (error) {
    if (error && Number(error.code) === 10100) {
      try {
        const refreshed = await refreshMiniappSession(baseUrl).catch(() => false)
        if (!refreshed) {
          relaunchAuthEntry((error && error.message) || '登录已过期，请重新登录')
          throw new Error(normalizeUploadErrorMessage((error && error.message) || '登录已过期，请重新登录'))
        }
      } catch (recoverError) {
        relaunchAuthEntry((recoverError && recoverError.message) || (error && error.message) || '登录已过期，请重新登录')
        throw new Error(normalizeUploadErrorMessage((recoverError && recoverError.message) || (error && error.message)))
      }
      return uploadOnce(baseUrl, { ...options, __skipAutoRefresh: true, __skipUnauthorizedRecovery: true })
    }
    throw new Error(normalizeUploadErrorMessage(error && error.message))
  }
}

export default {
  getBaseUrl: () => getConfiguredBaseUrl(),
  probeSession: async () => {
    const baseUrl = getConfiguredBaseUrl()
    await ensureMiniappSessionFresh(baseUrl, '/miniapp/auth/me').catch(() => false)
    return requestOnce(
      baseUrl,
      {
        url: '/miniapp/auth/me',
        method: 'GET'
      },
      {
        silentErrorToast: true,
        silentUnauthorized: true
      }
    )
  },
  ensureSessionFresh: async (url = '') => {
    const baseUrl = getConfiguredBaseUrl()
    await ensureMiniappSessionFresh(baseUrl, url)
  },
  get: (url, params, options = {}) => {
    return request({
      url,
      method: 'GET',
      data: params,
      ...options
    })
  },
  post: (url, data, options = {}) => {
    return request({
      url,
      method: 'POST',
      data,
      ...options
    })
  },
  put: (url, data, options = {}) => {
    return request({
      url,
      method: 'PUT',
      data,
      ...options
    })
  },
  delete: (url, data, options = {}) => {
    return request({
      url,
      method: 'DELETE',
      data,
      ...options
    })
  },
  upload: (url, filePath, options = {}) => {
    return upload({
      url,
      filePath,
      ...options
    })
  }
}
