import api from './request'
import {
  getAccessToken,
  getTokenExpiresAt,
  isApprovedUser,
  setAccessToken,
  setAuthUser,
  setTokenExpiresAt
} from './auth'

const LOGIN_ROUTE = '/pages/auth/login'
const LAUNCH_ROUTE = '/pages/launch/index'
const HOME_ROUTE = '/pages/home/index'
const USER_AGREEMENT_ROUTE = '/pages/legal/user-agreement'
const PRIVACY_POLICY_ROUTE = '/pages/legal/privacy-policy'
const APPLY_ROUTE = `${LOGIN_ROUTE}?mode=apply`
const LOGIN_ENTRY_ROUTE = `${LOGIN_ROUTE}?mode=login`
const PUBLIC_ROUTES = new Set([LOGIN_ROUTE, LAUNCH_ROUTE, USER_AGREEMENT_ROUTE, PRIVACY_POLICY_ROUTE])
const SESSION_PROBE_INTERVAL_MS = 90 * 1000

let overlayListener = null
const guardPromiseMap = new Map()
let entryPromise = null
let lastValidatedAt = 0
let lastRedirectAt = 0

function emitOverlay(patch) {
  if (typeof overlayListener === 'function') {
    overlayListener(patch)
  }
}

function normalizeRoute(route) {
  const value = String(route || '').trim()
  if (!value) return ''
  const path = value.split('?')[0]
  return path.startsWith('/') ? path : `/${path}`
}

function getCurrentRoute() {
  const pages = getCurrentPages()
  if (!pages || !pages.length) {
    return ''
  }
  const current = pages[pages.length - 1]
  return normalizeRoute(current && current.route)
}

function parseLocalDateTime(value) {
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

function hasUsableLocalSession() {
  if (!getAccessToken() || !isApprovedUser()) {
    return false
  }
  const expiresAt = parseLocalDateTime(getTokenExpiresAt())
  if (!expiresAt) {
    return false
  }
  return expiresAt.getTime() > Date.now()
}

function clearMiniappSession() {
  setAccessToken('')
  setTokenExpiresAt('')
}

function redirectTo(url) {
  if (Date.now() - lastRedirectAt < 400) {
    return
  }
  lastRedirectAt = Date.now()
  uni.reLaunch({ url })
}

async function fetchWechatCode() {
  const loginRes = await new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: resolve,
      fail: reject
    })
  })
  const code = String((loginRes && loginRes.code) || '').trim()
  if (!code) {
    throw new Error('wechat login code missing')
  }
  return code
}

function isApprovedProfile(user) {
  if (!user) return false
  const enabled = user.enabled == null ? true : Number(user.enabled) === 1
  return enabled && String(user.status || '').toLowerCase() === 'approved'
}

function applyEntryUser(user) {
  if (!user) {
    setAuthUser(null)
    clearMiniappSession()
    return
  }
  setAuthUser(user)
  if (!isApprovedProfile(user)) {
    clearMiniappSession()
  }
}

async function resolveEntryRouteInternal() {
  const code = await fetchWechatCode()
  const resp = await api.post('/miniapp/auth/entry', {
    code,
    loginScene: 'task_center'
  })
  const user = resp && resp.user ? resp.user : null
  const approved = isApprovedProfile(user)

  applyEntryUser(user)

  if (!approved) {
    return APPLY_ROUTE
  }

  if (!hasUsableLocalSession()) {
    clearMiniappSession()
    return LOGIN_ENTRY_ROUTE
  }

  const valid = await validateApprovedSession()
  return valid ? HOME_ROUTE : LOGIN_ENTRY_ROUTE
}

async function validateApprovedSession() {
  if (!hasUsableLocalSession()) {
    return false
  }
  if (Date.now() - lastValidatedAt < SESSION_PROBE_INTERVAL_MS) {
    return true
  }
  try {
    const payload = await api.probeSession()
    const approved = !!(payload && payload.user && isApprovedUser())
    if (!approved) {
      clearMiniappSession()
      return false
    }
    lastValidatedAt = Date.now()
    return true
  } catch (error) {
    clearMiniappSession()
    return false
  }
}

async function runGuard(rawRoute) {
  const route = normalizeRoute(rawRoute) || getCurrentRoute()

  if (route === LAUNCH_ROUTE) {
    emitOverlay({ visible: false })
    return true
  }

  if (route === LOGIN_ROUTE) {
    emitOverlay({ visible: false })
    if (hasUsableLocalSession()) {
      const approved = await validateApprovedSession()
      if (approved) {
        redirectTo(HOME_ROUTE)
        return false
      }
    }
    return true
  }

  if (PUBLIC_ROUTES.has(route)) {
    emitOverlay({ visible: false })
    return true
  }

  if (!hasUsableLocalSession()) {
    emitOverlay({ visible: true })
    redirectTo(LAUNCH_ROUTE)
    return false
  }

  const approved = await validateApprovedSession()
  if (!approved) {
    emitOverlay({ visible: true })
    redirectTo(LAUNCH_ROUTE)
    return false
  }

  emitOverlay({ visible: false })
  return true
}

export function bindAppAuthOverlayListener(listener) {
  overlayListener = listener
}

export function guardMiniappPageAccess(route) {
  const normalizedRoute = normalizeRoute(route) || getCurrentRoute()
  const promiseKey = normalizedRoute || '__unknown__'
  if (guardPromiseMap.has(promiseKey)) {
    return guardPromiseMap.get(promiseKey)
  }
  const guardPromise = runGuard(normalizedRoute).finally(() => {
    if (guardPromiseMap.get(promiseKey) === guardPromise) {
      guardPromiseMap.delete(promiseKey)
    }
  })
  guardPromiseMap.set(promiseKey, guardPromise)
  return guardPromise
}

export function resolveMiniappEntryRoute() {
  if (entryPromise) {
    return entryPromise
  }
  entryPromise = resolveEntryRouteInternal().finally(() => {
    entryPromise = null
  })
  return entryPromise
}

export function hideAppAuthOverlay() {
  emitOverlay({ visible: false })
}
