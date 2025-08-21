const SHARE_CONFIG_STORAGE_KEY = 'dahe.v2.shareConfig'

const DEFAULT_SHARE_CONFIG = {
  title: '大禾种业',
  path: '/pages/auth/login?entry=share',
  imageUrl: '/static/images/farm.png'
}

export function getCachedMiniappShareConfig() {
  const cached = uni.getStorageSync(SHARE_CONFIG_STORAGE_KEY) || {}
  return {
    ...DEFAULT_SHARE_CONFIG,
    ...(cached || {})
  }
}

export function cacheMiniappShareConfig(config) {
  const next = {
    ...DEFAULT_SHARE_CONFIG,
    ...(config || {})
  }
  uni.setStorageSync(SHARE_CONFIG_STORAGE_KEY, next)
  return next
}

export async function syncMiniappShareConfig(api) {
  const data = await api.get('/miniapp/share/config')
  return cacheMiniappShareConfig(data)
}

export function buildMiniappShareMessage() {
  const config = getCachedMiniappShareConfig()
  return {
    title: String(config.title || DEFAULT_SHARE_CONFIG.title).trim() || DEFAULT_SHARE_CONFIG.title,
    path: String(config.path || DEFAULT_SHARE_CONFIG.path).trim() || DEFAULT_SHARE_CONFIG.path,
    imageUrl: String(config.imageUrl || DEFAULT_SHARE_CONFIG.imageUrl).trim() || DEFAULT_SHARE_CONFIG.imageUrl
  }
}

export async function fetchMiniappShareQrCode(api) {
  return api.get('/miniapp/share/qrcode')
}

export function getMiniappFileSystemManager() {
  if (typeof uni !== 'undefined' && typeof uni.getFileSystemManager === 'function') {
    return uni.getFileSystemManager()
  }
  if (typeof wx !== 'undefined' && typeof wx.getFileSystemManager === 'function') {
    return wx.getFileSystemManager()
  }
  return null
}

export function getMiniappUserDataPath() {
  if (typeof wx !== 'undefined' && wx.env && wx.env.USER_DATA_PATH) {
    return wx.env.USER_DATA_PATH
  }
  return ''
}

export function writeBase64ImageToTempFile(base64, fileName = 'dahe-share-code.png') {
  const fs = getMiniappFileSystemManager()
  const basePath = getMiniappUserDataPath()
  if (!fs || !basePath) {
    return Promise.reject(new Error('当前环境不支持生成二维码预览'))
  }
  const normalized = String(base64 || '').trim().replace(/^data:image\/[a-zA-Z0-9+.-]+;base64,/, '')
  if (!normalized) {
    return Promise.reject(new Error('二维码内容为空'))
  }
  const safeName = String(fileName || 'dahe-share-code.png').replace(/[^\w.-]/g, '_')
  const targetPath = `${basePath}/${Date.now()}-${safeName}`
  return new Promise((resolve, reject) => {
    fs.writeFile({
      filePath: targetPath,
      data: normalized,
      encoding: 'base64',
      success: () => resolve(targetPath),
      fail: (err) => reject(err)
    })
  })
}
