import request from '../utils/request'

function normalizeUploadErrorMessage(rawMessage) {
  const message = String(rawMessage || '').trim()
  if (!message) return '上传失败'
  if (
    message.includes('图片文件内容无效') ||
    message.includes('图片文件内容校验失败') ||
    message.includes('图片文件内容与扩展名不匹配')
  ) {
    return '上传失败：图片内容与文件格式不一致，请重新选择图片'
  }
  if (message.includes('文件夹正被使用')) {
    return '上传失败：目标文件夹正在处理，请稍后重试'
  }
  return message
}

export async function listPublicAssets(params) {
  return request.get('/assets', { params })
}

export async function listAdminAssets(params) {
  return request.get('/admin/assets', { params })
}

export async function listFarmRecordImages(recordId) {
  return request.get(`/farm-records/${recordId}/images`)
}

export async function uploadAssetFile(formData) {
  try {
    return await request.post('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  } catch (error) {
    throw new Error(normalizeUploadErrorMessage(error && error.message))
  }
}

export async function createAssetLink(payload) {
  return request.post('/admin/assets/link', payload)
}

export async function updateAsset(id, payload) {
  return request.put(`/admin/assets/${id}`, payload)
}

export async function removeAsset(id, payload) {
  return request.delete(`/admin/assets/${id}`, {
    data: payload || undefined
  })
}

export async function restoreAsset(id) {
  return request.post(`/admin/assets/${id}/restore`)
}

export async function purgeAsset(id, payload) {
  return request.delete(`/admin/assets/${id}/purge`, {
    data: payload || undefined
  })
}

export async function reviewAsset(id, payload) {
  return request.put(`/admin/assets/${id}/review`, payload)
}

export async function updateAssetLock(id, payload) {
  return request.put(`/admin/assets/${id}/lock`, payload)
}

export async function reorderAssets(ids) {
  return request.post('/admin/assets/reorder', { ids })
}

export async function getAssetStats() {
  return request.get('/admin/assets/stats')
}

export async function getAssetReferences() {
  return request.get('/admin/assets/references')
}

export async function getAssetReferenceDetails(id, params) {
  return request.get(`/admin/assets/${id}/reference-details/page`, { params })
}

export async function listAssetFolders(params) {
  return request.get('/admin/assets/folders', { params })
}

export async function listAssetFolderManageRows() {
  return request.get('/admin/assets/folders/manage')
}

export async function createAssetFolder(payload) {
  return request.post('/admin/assets/folders', payload)
}

export async function deleteAssetFolder(folderPath) {
  return request.delete('/admin/assets/folders', {
    params: { folderPath }
  })
}
