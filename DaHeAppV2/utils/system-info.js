function safeCall(methodName, fallback = {}) {
  try {
    if (typeof uni !== 'undefined' && typeof uni[methodName] === 'function') {
      return uni[methodName]() || fallback
    }
  } catch (e) {
    return fallback
  }
  return fallback
}

export function getWindowInfoSafe() {
  return safeCall('getWindowInfo', safeCall('getSystemInfoSync', {}))
}

export function getDeviceInfoSafe() {
  const fallback = safeCall('getSystemInfoSync', {})
  return {
    ...fallback,
    ...safeCall('getDeviceInfo', {})
  }
}

export function getAppBaseInfoSafe() {
  const fallback = safeCall('getSystemInfoSync', {})
  return {
    ...fallback,
    ...safeCall('getAppBaseInfo', {})
  }
}

export function getWindowWidth() {
  return Number(getWindowInfoSafe().windowWidth || 375)
}

export function getWindowHeight() {
  return Number(getWindowInfoSafe().windowHeight || 667)
}

export function getStatusBarHeight() {
  return Number(getWindowInfoSafe().statusBarHeight || 0)
}

export function buildDeviceName() {
  const deviceInfo = getDeviceInfoSafe()
  const appInfo = getAppBaseInfoSafe()
  const model = String(deviceInfo.model || '').trim()
  const platform = String(deviceInfo.platform || '').trim()
  const brand = String(deviceInfo.brand || '').trim()
  const appName = String(appInfo.appName || '').trim()
  return [appName || 'miniapp', brand, model, platform].filter(Boolean).join('-').slice(0, 120) || 'miniapp-unknown'
}

export function buildUserAgent() {
  const deviceInfo = getDeviceInfoSafe()
  const appInfo = getAppBaseInfoSafe()
  const rows = [
    `host:${String(appInfo.host || '').trim()}`,
    `platform:${String(deviceInfo.platform || '').trim()}`,
    `system:${String(deviceInfo.system || '').trim()}`,
    `version:${String(appInfo.version || '').trim()}`,
    `sdk:${String(appInfo.SDKVersion || '').trim()}`
  ].filter((item) => item && !item.endsWith(':'))
  return rows.join('; ').slice(0, 500) || 'miniapp'
}
