import request from '../utils/request'

export function fetchAdminMessageTasks(params) {
  return request.get('/admin/messages', { params })
}

export function createAdminMessageTask(payload) {
  return request.post('/admin/messages', payload)
}

export function deleteAdminMessageTask(id, payload) {
  return request.delete(`/admin/messages/${id}`, {
    data: payload || undefined
  })
}

export function fetchAdminMessageConfig() {
  return request.get('/admin/messages/config')
}

export function saveAdminMessageConfig(payload) {
  return request.post('/admin/messages/config', payload)
}
