import request from '../utils/request'

export async function listMiniappStaticAssets(params) {
  return request.get('/admin/miniapp-static-assets', { params })
}

export async function uploadMiniappStaticAsset(formData) {
  return request.post('/admin/miniapp-static-assets/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export async function updateMiniappStaticAsset(id, payload) {
  return request.put(`/admin/miniapp-static-assets/${id}`, payload)
}

export async function deleteMiniappStaticAsset(id, password) {
  return request.delete(`/admin/miniapp-static-assets/${id}`, {
    data: { password }
  })
}
