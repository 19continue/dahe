import request from '../utils/request'

export function fetchAdminUsers(params) {
  return request.get('/admin/users', { params })
}

export function fetchRoleOptions(includeDisabled = false) {
  return request.get('/admin/users/role-options', {
    params: { includeDisabled: !!includeDisabled }
  })
}

export function createAdminUser(payload) {
  return request.post('/admin/users/admin-create', payload)
}

export function updateUserRole(userId, payload) {
  return request.put(`/admin/users/${userId}/role`, payload)
}

export function updateUserEnabled(userId, enabled) {
  if (enabled && typeof enabled === 'object' && !Array.isArray(enabled)) {
    return request.put(`/admin/users/${userId}/enabled`, enabled)
  }
  return request.put(`/admin/users/${userId}/enabled`, { enabled: !!enabled })
}

export function approveMiniappUser(userId, payload) {
  return request.put(`/admin/users/${userId}/approve`, payload)
}

export function updateMiniappUserStatus(userId, payload) {
  return request.put(`/admin/users/${userId}/miniapp-status`, payload)
}

export function revokeUserSessions(userId) {
  return request.post(`/admin/users/${userId}/sessions/revoke`)
}

export function deleteUser(userId) {
  return request.delete(`/admin/users/${userId}`)
}

export function deleteUserWithPayload(userId, payload) {
  return request.delete(`/admin/users/${userId}`, {
    data: payload || undefined
  })
}

export function resetUserPassword(userId, payload) {
  return request.put(`/admin/users/${userId}/password/reset`, payload)
}
