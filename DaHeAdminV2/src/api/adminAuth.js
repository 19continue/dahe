import request from '../utils/request'

export function adminLogin(payload) {
  return request.post('/admin/auth/login', payload)
}

export function fetchAdminMe() {
  return request.get('/admin/auth/me')
}

export function updateAdminProfile(payload) {
  return request.put('/admin/auth/me/profile', payload)
}

export function updateAdminAvatar(payload) {
  return request.put('/admin/auth/me/avatar', payload)
}

export function changeAdminPassword(payload) {
  return request.put('/admin/auth/me/password', payload)
}

export function logoutAllAdminSessions() {
  return request.post('/admin/auth/logout-all', {})
}

export function fetchAdminNotices(params) {
  return request.get('/admin/auth/me/notices', { params })
}

export function markAdminNoticeRead(id) {
  return request.put(`/admin/auth/me/notices/${encodeURIComponent(id)}/read`, {})
}

export function markAllAdminNoticesRead() {
  return request.put('/admin/auth/me/notices/read-all', {})
}

export function deleteAdminNotice(id) {
  return request.delete(`/admin/auth/me/notices/${encodeURIComponent(id)}`)
}

export function fetchPublicCompanyIntro() {
  return request.get('/public/company-intro')
}
