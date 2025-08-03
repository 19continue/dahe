<template>
  <div class="image-asset-picker">
    <div class="picker-trigger">
      <button type="button" class="preview-box" :disabled="disabled" @click="openDialog">
        <el-image v-if="selectedUrl" :src="selectedUrl" fit="cover" class="preview-image" />
        <span v-else class="preview-empty">图片</span>
      </button>
      <div class="picker-main">
        <el-input :model-value="selectedUrl" :placeholder="placeholder" readonly :disabled="disabled" @click="openDialog" />
        <div class="picker-actions">
          <el-button :disabled="disabled" @click="openDialog">选择图片</el-button>
          <el-button v-if="clearable && selectedUrl" :disabled="disabled" @click="clearValue">清空</el-button>
          <span v-if="selectedMetaText" class="picker-meta">{{ selectedMetaText }}</span>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="980px"
      append-to-body
      destroy-on-close
      align-center
      class="image-picker-dialog"
    >
      <div class="dialog-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="按名称、备注、文件夹筛选"
          style="width: 280px"
          @keyup.enter="loadRows(1)"
        />
        <el-button :loading="loading" @click="loadRows(1)">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <el-upload
          v-if="allowUpload"
          accept="image/*"
          :show-file-list="false"
          :http-request="uploadByRequest"
          :before-upload="beforeUpload"
        >
          <el-button type="primary" :loading="uploading">上传图片</el-button>
        </el-upload>
      </div>

      <div class="dialog-layout">
        <aside class="folder-pane" v-loading="folderLoading">
          <div class="folder-head">
            <strong>文件夹</strong>
            <el-tag size="small" type="info" effect="plain">{{ folderLocked ? '固定' : '筛选' }}</el-tag>
          </div>
          <div class="folder-list">
            <button
              type="button"
              :disabled="folderLocked"
              :class="['folder-item', { active: !activeFolderPath }]"
              @click="pickFolder('')"
            >
              <span class="folder-item-main">全部文件夹</span>
              <span class="folder-item-sub">显示全部图片资源</span>
            </button>
            <button
              v-for="folder in renderFolderOptions"
              :key="folder.path"
              type="button"
              :disabled="folderLocked"
              :class="['folder-item', { active: folder.path === activeFolderPath }]"
              @click="pickFolder(folder.path)"
            >
              <span class="folder-item-main">{{ folder.path }}</span>
              <span class="folder-item-sub">
                <span>{{ folder.countText }}</span>
                <span v-if="folder.remark">{{ folder.remark }}</span>
                <span v-if="folder.protectedFlag" class="folder-protected">受保护</span>
              </span>
            </button>
          </div>
          <div v-if="folderLocked" class="folder-tip">当前组件已固定文件夹</div>
        </aside>

        <section class="dialog-body" v-loading="loading">
          <div v-if="rows.length" class="asset-grid">
            <button
              v-for="row in rows"
              :key="row.idKey"
              type="button"
              :class="['asset-card', { active: row.fileUrl === draftValue, disabled: isRowDisabled(row) }]"
              @click="pickAsset(row)"
            >
              <el-image :src="row.fileUrl" fit="cover" class="asset-thumb" />
              <div v-if="row.reviewStatusTag" class="asset-status">
                <el-tag size="small" :type="row.reviewStatusTag.type" effect="dark">
                  {{ row.reviewStatusTag.label }}
                </el-tag>
              </div>
              <div class="asset-name" :title="row.fileName || '-'">{{ row.fileName || '-' }}</div>
              <div class="asset-sub">{{ row.folderPath || '/默认' }}</div>
            </button>
          </div>
          <el-empty v-else description="暂无图片资源" :image-size="64" />
        </section>
      </div>

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSizeLocal"
          :current-page="page"
          :page-sizes="pageSizes"
          @current-change="loadRows"
          @size-change="onPageSizeChange"
        />
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canConfirmSelect" @click="confirmSelect">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listAdminAssets,
  listAssetFolderManageRows,
  listAssetFolders,
  uploadAssetFile
} from '../../api/assets'

const listCache = new Map()

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '请选择图片资源'
  },
  dialogTitle: {
    type: String,
    default: '选择图片资源'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: true
  },
  allowUpload: {
    type: Boolean,
    default: true
  },
  sourceType: {
    type: String,
    default: ''
  },
  reviewStatus: {
    type: String,
    default: ''
  },
  folderPath: {
    type: String,
    default: ''
  },
  uploadFolderPath: {
    type: String,
    default: '/默认'
  },
  uploadModuleKey: {
    type: String,
    default: ''
  },
  uploadBizId: {
    type: [String, Number],
    default: ''
  },
  pageSizes: {
    type: Array,
    default: () => [10, 24, 48, 96]
  },
  pageSize: {
    type: Number,
    default: 24
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const dialogVisible = ref(false)
const loading = ref(false)
const uploading = ref(false)
const folderLoading = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSizeLocal = ref(Number(props.pageSize || 24))
const total = ref(0)
const rows = ref([])
const folderOptions = ref(['/默认'])
const folderRows = ref([])
const selectedFolder = ref('')
const draftValue = ref(normalizeText(props.modelValue))
const selectedMeta = ref(null)

const selectedUrl = computed(() => normalizeText(props.modelValue))
const folderLocked = computed(() => !!normalizeFolderPath(props.folderPath))
const activeFolderPath = computed(() => {
  if (folderLocked.value) return normalizeFolderPath(props.folderPath)
  return normalizeFolderPath(selectedFolder.value)
})
const renderFolderOptions = computed(() => {
  const rowMap = new Map()
  ;(Array.isArray(folderRows.value) ? folderRows.value : []).forEach((item) => {
    const path = normalizeFolderPath(item && item.folderPath)
    if (!path) return
    rowMap.set(path, {
      path,
      totalAssetCount: Number((item && item.totalAssetCount) || 0),
      protectedFlag: Number((item && item.protectedFlag) || 0) === 1,
      remark: normalizeText(item && item.remark)
    })
  })
  const set = new Set(['/默认'])
  ;(Array.isArray(folderOptions.value) ? folderOptions.value : []).forEach((item) => {
    const path = normalizeFolderPath(item)
    if (path) set.add(path)
  })
  const fixedFolder = normalizeFolderPath(props.folderPath)
  if (fixedFolder) set.add(fixedFolder)
  const uploadFolder = normalizeFolderPath(props.uploadFolderPath)
  if (uploadFolder) set.add(uploadFolder)
  const active = activeFolderPath.value
  if (active) set.add(active)
  const out = Array.from(set).map((path) => {
    const row = rowMap.get(path)
    const count = row ? Number(row.totalAssetCount || 0) : 0
    return {
      path,
      totalAssetCount: count,
      countText: `${count} 张`,
      protectedFlag: !!(row && row.protectedFlag),
      remark: row ? normalizeText(row.remark) : ''
    }
  })
  out.sort((a, b) => a.path.localeCompare(b.path, 'zh-CN'))
  return out
})
const selectedMetaText = computed(() => {
  if (selectedMeta.value && selectedMeta.value.fileName) return selectedMeta.value.fileName
  return ''
})
const canConfirmSelect = computed(() => {
  const value = normalizeText(draftValue.value)
  if (!value) return false
  const hit = rows.value.find((item) => item.fileUrl === value)
  if (!hit) return true
  return !isRowDisabled(hit)
})

watch(
  () => props.modelValue,
  (next) => {
    draftValue.value = normalizeText(next)
  },
  { immediate: true }
)

watch(
  () => props.folderPath,
  (next) => {
    if (!folderLocked.value) return
    selectedFolder.value = normalizeFolderPath(next)
  },
  { immediate: true }
)

function normalizeText(value) {
  return String(value || '').trim()
}

function normalizeFolderPath(value) {
  let path = normalizeText(value).replace(/\\/g, '/')
  if (!path) return ''
  path = path.replace(/\/{2,}/g, '/')
  if (!path.startsWith('/')) {
    path = `/${path}`
  }
  if (path === '/') {
    return '/默认'
  }
  const lower = path.toLowerCase()
  if (lower === '/default') return '/默认'
  if (lower === '/field' || path === '/田块图片') return '/田块封面'
  if (lower === '/crop') return '/作物封面'
  if (path.length > 255) {
    return path.slice(0, 255)
  }
  return path
}

function cleanParams(params) {
  const out = {}
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value === null || value === undefined) return
    if (typeof value === 'string' && !value.trim()) return
    out[key] = typeof value === 'string' ? value.trim() : value
  })
  return out
}

function normalizeRows(records) {
  return (Array.isArray(records) ? records : [])
    .filter((item) => item && normalizeText(item.fileUrl))
    .map((item) => {
      const reviewStatus = normalizeReviewStatus(item.reviewStatus)
      const disabledReason = resolveDisabledReason(reviewStatus)
      return {
        ...item,
        idKey: String(item.id || item.fileUrl || Math.random()),
        fileUrl: normalizeText(item.fileUrl),
        fileName: normalizeText(item.fileName),
        folderPath: normalizeFolderPath(item.folderPath) || '/默认',
        reviewStatus,
        reviewStatusTag: resolveReviewStatusTag(reviewStatus),
        selectable: !disabledReason,
        disabledReason
      }
    })
}

function normalizeReviewStatus(value) {
  const text = normalizeText(value).toLowerCase()
  if (text === 'pending') return 'pending'
  if (text === 'rejected') return 'rejected'
  return 'approved'
}

function resolveReviewStatusTag(status) {
  if (status === 'pending') return { label: '审核中', type: 'warning' }
  if (status === 'rejected') return { label: '未通过', type: 'danger' }
  return null
}

function resolveDisabledReason(status) {
  if (status === 'pending') return '该图片正在审核中，暂不可选择'
  if (status === 'rejected') return '该图片审核未通过，暂不可选择'
  return ''
}

function isRowDisabled(row) {
  return !!(row && row.selectable === false)
}

function buildCacheKey() {
  return JSON.stringify({
    keyword: normalizeText(keyword.value),
    page: Number(page.value || 1),
    pageSize: Number(pageSizeLocal.value || 24),
    sourceType: normalizeText(props.sourceType),
    folderPath: normalizeFolderPath(activeFolderPath.value)
  })
}

function writeCache(payload) {
  const key = buildCacheKey()
  if (!listCache.has(key) && listCache.size >= 80) {
    const first = listCache.keys().next()
    if (first && !first.done) listCache.delete(first.value)
  }
  listCache.set(key, payload)
}

function readCache() {
  const key = buildCacheKey()
  return listCache.has(key) ? listCache.get(key) : null
}

function syncSelectedMeta() {
  const target = normalizeText(props.modelValue)
  if (!target) {
    selectedMeta.value = null
    return
  }
  const hit = rows.value.find((item) => item.fileUrl === target)
  if (hit) {
    selectedMeta.value = hit
  }
}

async function loadFolders() {
  folderLoading.value = true
  try {
    let rows = []
    try {
      const manageRows = await listAssetFolderManageRows()
      if (Array.isArray(manageRows) && manageRows.length) {
        rows = manageRows
      }
    } catch (error) {
      rows = []
    }
    if (!rows.length) {
      const folderPaths = await listAssetFolders({ recycleFlag: 0 })
      rows = (Array.isArray(folderPaths) ? folderPaths : []).map((path) => ({
        folderPath: path,
        totalAssetCount: 0,
        protectedFlag: normalizeFolderPath(path) === '/默认' ? 1 : 0
      }))
    }
    folderRows.value = Array.isArray(rows) ? rows : []
    const set = new Set(['/默认'])
    ;(Array.isArray(rows) ? rows : []).forEach((item) => {
      const path = normalizeFolderPath(item && item.folderPath ? item.folderPath : item)
      if (path) set.add(path)
    })
    const fixedFolder = normalizeFolderPath(props.folderPath)
    if (fixedFolder) set.add(fixedFolder)
    const uploadFolder = normalizeFolderPath(props.uploadFolderPath)
    if (uploadFolder) set.add(uploadFolder)
    folderOptions.value = Array.from(set)
    if (folderLocked.value) {
      selectedFolder.value = fixedFolder
    } else {
      const current = normalizeFolderPath(selectedFolder.value)
      selectedFolder.value = current && set.has(current) ? current : ''
    }
  } catch (error) {
    folderOptions.value = ['/默认']
    folderRows.value = [
      {
        folderPath: '/默认',
        totalAssetCount: 0,
        protectedFlag: 1
      }
    ]
    if (folderLocked.value) {
      selectedFolder.value = normalizeFolderPath(props.folderPath)
    } else {
      selectedFolder.value = ''
    }
  } finally {
    folderLoading.value = false
  }
}

async function loadRows(nextPage = page.value, options = {}) {
  page.value = Number(nextPage || 1)
  const force = !!(options && options.force)
  const cached = force ? null : readCache()
  if (cached) {
    rows.value = normalizeRows(cached.records)
    total.value = Number(cached.total || 0)
    syncSelectedMeta()
    return
  }

  loading.value = true
  try {
    const data = await listAdminAssets(
      cleanParams({
        page: page.value,
        pageSize: pageSizeLocal.value,
        keyword: keyword.value,
        fileType: 'image',
        recycleFlag: 0,
        sourceType: props.sourceType || undefined,
        reviewStatus: props.reviewStatus || undefined,
        folderPath: activeFolderPath.value || undefined
      })
    )
    rows.value = normalizeRows(data && data.records)
    total.value = Number((data && data.total) || 0)
    writeCache({
      records: rows.value,
      total: total.value
    })
    syncSelectedMeta()
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '图片资源加载失败')
  } finally {
    loading.value = false
  }
}

async function openDialog() {
  if (props.disabled) return
  draftValue.value = normalizeText(props.modelValue)
  dialogVisible.value = true
  await loadFolders()
  await loadRows(1)
}

function pickFolder(path) {
  if (folderLocked.value) return
  selectedFolder.value = normalizeFolderPath(path)
  loadRows(1)
}

function pickAsset(row) {
  if (isRowDisabled(row)) {
    ElMessage.warning(row && row.disabledReason ? row.disabledReason : '当前图片不可选择')
    return
  }
  draftValue.value = normalizeText(row && row.fileUrl)
}

function confirmSelect() {
  const value = normalizeText(draftValue.value)
  const row = rows.value.find((item) => item.fileUrl === value) || null
  if (row && isRowDisabled(row)) {
    ElMessage.warning(row.disabledReason || '当前图片不可选择')
    return
  }
  emit('update:modelValue', value)
  selectedMeta.value = row
  emit('change', row)
  dialogVisible.value = false
}

function clearValue() {
  emit('update:modelValue', '')
  selectedMeta.value = null
  emit('change', null)
}

function onPageSizeChange(size) {
  pageSizeLocal.value = Number(size || props.pageSize || 24)
  loadRows(1)
}

function resetSearch() {
  keyword.value = ''
  loadRows(1)
}

function beforeUpload(file) {
  const isImage = !!(file && String(file.type || '').startsWith('image/'))
  if (!isImage) {
    ElMessage.warning('仅支持上传图片文件')
    return false
  }
  return true
}

async function uploadByRequest(option) {
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', option.file)
    if (normalizeText(props.uploadModuleKey)) form.append('moduleKey', normalizeText(props.uploadModuleKey))
    if (normalizeText(props.uploadBizId)) form.append('bizId', normalizeText(props.uploadBizId))
    if (normalizeText(props.uploadFolderPath)) form.append('folderPath', normalizeFolderPath(props.uploadFolderPath))
    const row = await uploadAssetFile(form)
    listCache.clear()
    await loadFolders()
    if (row && row.fileUrl) {
      draftValue.value = normalizeText(row.fileUrl)
      await loadRows(1, { force: true })
      ElMessage.success('图片上传成功')
    }
    if (typeof option.onSuccess === 'function') {
      option.onSuccess(row)
    }
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
    if (typeof option.onError === 'function') {
      option.onError(error)
    }
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.image-asset-picker {
  width: 100%;
}

.picker-trigger {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
}

.preview-box {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-soft);
  padding: 0;
  height: 84px;
  cursor: pointer;
  overflow: hidden;
}

.preview-box:disabled {
  cursor: not-allowed;
}

.preview-image {
  width: 84px;
  height: 84px;
}

.preview-empty {
  width: 84px;
  height: 84px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-sub);
}

.picker-main {
  display: grid;
  gap: 8px;
}

.picker-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.picker-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.dialog-layout {
  min-height: 360px;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
}

.folder-pane {
  border: 1px solid var(--border);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(246, 250, 255, 0.96), rgba(255, 255, 255, 0.98));
  display: flex;
  flex-direction: column;
  min-height: 360px;
}

.folder-head {
  padding: 10px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.folder-list {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: auto;
  max-height: 380px;
}

.folder-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #ffffff;
  text-align: left;
  padding: 8px 10px 9px;
  font-size: 13px;
  color: var(--text-main);
  cursor: pointer;
  display: grid;
  gap: 2px;
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}

.folder-item:hover {
  border-color: rgba(22, 103, 183, 0.3);
  background: rgba(22, 103, 183, 0.04);
}

.folder-item.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(22, 103, 183, 0.1);
  background: rgba(22, 103, 183, 0.08);
}

.folder-item:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.folder-item-main {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.2;
  word-break: break-all;
}

.folder-item-sub {
  display: grid;
  gap: 3px;
  font-size: 12px;
  color: var(--text-sub);
}

.folder-protected {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(16, 24, 40, 0.18);
  color: #344054;
  border-radius: 999px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  background: rgba(16, 24, 40, 0.04);
  width: fit-content;
}

.folder-tip {
  margin-top: auto;
  padding: 8px 10px;
  color: var(--text-sub);
  font-size: 12px;
  border-top: 1px dashed var(--border);
}

.dialog-body {
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #f8fbff;
  padding: 10px;
  min-height: 360px;
  max-height: 420px;
  overflow: auto;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(136px, 1fr));
  gap: 10px;
}

.asset-card {
  position: relative;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  text-align: left;
  padding: 6px;
  cursor: pointer;
  transition: transform 0.12s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.asset-card:hover {
  transform: translateY(-1px);
  border-color: rgba(22, 103, 183, 0.34);
  box-shadow: 0 6px 18px rgba(22, 103, 183, 0.08);
}

.asset-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(22, 103, 183, 0.12), 0 6px 18px rgba(22, 103, 183, 0.12);
}

.asset-card.disabled {
  cursor: not-allowed;
  opacity: 0.78;
}

.asset-card.disabled:hover {
  transform: none;
  border-color: var(--border);
  box-shadow: none;
}

.asset-thumb {
  width: 100%;
  height: 92px;
  border-radius: 8px;
}

.asset-status {
  position: absolute;
  top: 10px;
  left: 10px;
}

.asset-name {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.asset-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .dialog-layout {
    grid-template-columns: 1fr;
  }

  .folder-pane {
    min-height: auto;
  }

  .folder-list {
    max-height: 180px;
  }

  .dialog-body {
    min-height: 280px;
    max-height: 360px;
  }
}

@media (max-width: 720px) {
  .picker-trigger {
    grid-template-columns: 1fr;
  }

  .preview-box,
  .preview-image,
  .preview-empty {
    width: 100%;
    height: 160px;
  }
}
</style>
