const TOKEN_KEY = 'dahe.admin.v2.token'
const USER_KEY = 'dahe.admin.v2.user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (!token) {
    localStorage.removeItem(TOKEN_KEY)
    return ''
  }
  localStorage.setItem(TOKEN_KEY, token)
  return token
}

export function getUser() {
  const text = localStorage.getItem(USER_KEY)
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

export function setUser(user) {
  if (!user) {
    localStorage.removeItem(USER_KEY)
    return null
  }
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  return user
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isLoggedIn() {
  return !!getToken()
}

export function isAdmin() {
  const user = getUser()
  return String((user && user.userType) || '').toLowerCase() === 'admin'
}

export function getUserType() {
  const user = getUser()
  return String((user && user.userType) || '').toLowerCase()
}

export function getRoleCode() {
  const user = getUser()
  return String((user && (user.effectiveRoleCode || user.roleCode)) || '').toLowerCase()
}

export function getMenuPermissions() {
  const user = getUser()
  const rows = user && Array.isArray(user.menuPermissions) ? user.menuPermissions : []
  return rows
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}

export function hasMenuPermission(path) {
  const target = String(path || '').trim()
  if (!target) return false
  const rows = getMenuPermissions()
  if (!rows.length) return false
  if (rows.includes('*') || rows.includes(target)) return true
  return rows.some((item) => {
    const code = String(item || '').trim()
    if (!code || code === '*') return false
    return target.startsWith(`${code}/`)
  })
}

export function hasAnyRole(...roles) {
  if (getUserType() === 'admin') return true
  const role = getRoleCode()
  return roles.map((x) => String(x || '').toLowerCase()).includes(role)
}
