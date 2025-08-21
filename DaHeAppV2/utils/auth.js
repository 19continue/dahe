const ACCESS_TOKEN_KEY = 'dahe.v2.accessToken'
const AUTH_USER_KEY = 'dahe.v2.authUser'
const TOKEN_EXPIRES_AT_KEY = 'dahe.v2.tokenExpiresAt'

export function getAccessToken() {
  return String(uni.getStorageSync(ACCESS_TOKEN_KEY) || '').trim()
}

export function setAccessToken(token) {
  const next = String(token || '').trim()
  if (!next) {
    uni.removeStorageSync(ACCESS_TOKEN_KEY)
    return ''
  }
  uni.setStorageSync(ACCESS_TOKEN_KEY, next)
  return next
}

export function getTokenExpiresAt() {
  return String(uni.getStorageSync(TOKEN_EXPIRES_AT_KEY) || '').trim()
}

export function setTokenExpiresAt(value) {
  const next = String(value || '').trim()
  if (!next) {
    uni.removeStorageSync(TOKEN_EXPIRES_AT_KEY)
    return ''
  }
  uni.setStorageSync(TOKEN_EXPIRES_AT_KEY, next)
  return next
}

export function getAuthUser() {
  return uni.getStorageSync(AUTH_USER_KEY) || null
}

export function setAuthUser(user) {
  if (!user) {
    uni.removeStorageSync(AUTH_USER_KEY)
    return null
  }
  uni.setStorageSync(AUTH_USER_KEY, user)
  return user
}

export function saveAuthSession(payload) {
  const token = payload && payload.accessToken ? payload.accessToken : ''
  const user = payload && payload.user ? payload.user : null
  const tokenExpiresAt = payload && payload.tokenExpiresAt ? payload.tokenExpiresAt : ''
  setAccessToken(token)
  setTokenExpiresAt(tokenExpiresAt)
  setAuthUser(user)
  return { token, tokenExpiresAt, user }
}

export function clearAuthSession() {
  uni.removeStorageSync(ACCESS_TOKEN_KEY)
  uni.removeStorageSync(AUTH_USER_KEY)
  uni.removeStorageSync(TOKEN_EXPIRES_AT_KEY)
  uni.removeStorageSync('dahe.v2.mockOpenId')
}

export function clearTokenSession() {
  uni.removeStorageSync(ACCESS_TOKEN_KEY)
  uni.removeStorageSync(TOKEN_EXPIRES_AT_KEY)
}

export function isApprovedUser() {
  const user = getAuthUser()
  const token = getAccessToken()
  if (!user || !token) return false
  const enabled = user.enabled == null ? true : Number(user.enabled) === 1
  if (!enabled) return false
  const userType = String(user.userType || '').toLowerCase()
  const backendAccount = userType === 'admin'
  if (backendAccount) return true
  return String(user.status || '').toLowerCase() === 'approved'
}

export function getUserRoleCode() {
  const user = getAuthUser()
  return String((user && user.roleCode) || '').toLowerCase()
}

export function isAdminUser() {
  return getUserRoleCode() === 'admin'
}

export function isSupervisorUser() {
  return getUserRoleCode() === 'supervisor'
}

export function canManageSeedBatch() {
  const role = getUserRoleCode()
  return role === 'admin' || role === 'supervisor'
}

export function canUseConsole() {
  const user = getAuthUser()
  return !!(user && Number(user.canConsole) === 1)
}

export function getOperatorNameFromUser() {
  const user = getAuthUser()
  if (!user) return ''
  return (user.realName || user.nickName || '').trim()
}
