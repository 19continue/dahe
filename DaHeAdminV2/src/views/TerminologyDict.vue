<template>
  <div>
    <PageToolbar
      title="术语词典管理"
      subtitle="维护字段 key / 选项 value 自动生成时使用的中文术语映射。"
      collapsible
      :summary="[
        batchMode ? `多选：已选 ${selectedRows.length} 条` : '',
        filters.keyword ? `关键词：${filters.keyword}` : '',
        `术语总数：${total}`
      ]"
    >
      <div class="actions">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="中文术语/英文语义关键字"
          style="width: 220px"
          @keyup.enter="loadRows(1)"
        />
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button @click="loadRows(page)">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增术语</el-button>
        <el-button :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <el-button
          v-if="batchMode"
          type="danger"
          :disabled="!batchDeleteCount"
          @click="batchDelete"
        >
          批量删除（{{ batchDeleteCount }}）
        </el-button>
        <el-button @click="exportJson">导出 JSON</el-button>
        <el-upload
          accept=".json,application/json,text/plain"
          :show-file-list="false"
          :auto-upload="false"
          :on-change="onImportFileChange"
        >
          <el-button>导入 JSON</el-button>
        </el-upload>
        <el-button type="danger" plain :disabled="!rows.length" @click="clearAllTerms">清空词典</el-button>
      </div>
    </PageToolbar>

    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>术语映射列表</span>
              <span class="card-meta">共 {{ total }} 条</span>
            </div>
          </template>
          <el-table ref="tableRef" :data="rows" row-key="id" border v-loading="loading" @selection-change="onSelectionChange">
            <el-table-column v-if="batchMode" type="selection" width="48" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="source" label="中文术语" min-width="180" />
            <el-table-column prop="target" label="英文语义" min-width="200" />
            <el-table-column label="机器键预览" min-width="220">
              <template #default="scope">
                <div class="preview-lines">
                  <div>
                    <span class="preview-label">字段：</span>
                    <code>{{ previewFieldKey(scope.row.source) }}</code>
                  </div>
                  <div>
                    <span class="preview-label">选项：</span>
                    <code>{{ previewOptionValue(scope.row.source) }}</code>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="152" fixed="right" class-name="op-col">
              <template #default="scope">
                <div class="table-op-line">
                  <el-button size="small" type="primary" @click="openEdit(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click="removeTerm(scope.row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-foot">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              :page-size="pageSize"
              :current-page="page"
              :page-sizes="[10, 20, 50, 100]"
              @size-change="onPageSizeChange"
              @current-change="loadRows"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>生成预览</span>
            </div>
          </template>
          <el-form label-width="86px">
            <el-form-item label="字段名称">
              <el-input v-model="previewInput" placeholder="例如：玉米施肥数量" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="previewLoading" @click="generatePreview">生成 key / value</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="字段 key">{{ previewResult.fieldKey || '-' }}</el-descriptions-item>
            <el-descriptions-item label="选项 value">{{ previewResult.optionValue || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑术语' : '新增术语'" width="540px">
        <el-form label-width="96px">
          <el-form-item label="中文术语">
            <el-input v-model="form.source" maxlength="60" show-word-limit placeholder="例如：复合肥" />
          </el-form-item>
          <el-form-item label="英文语义">
            <el-input v-model="form.target" maxlength="120" show-word-limit placeholder="例如：compound fertilizer" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :max="99999" style="width: 100%" />
          </el-form-item>
        </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import {
  generateFieldKey,
  generateFieldKeyAsync,
  generateOptionValue,
  generateOptionValueAsync,
  setCustomPhraseEntries
} from '../utils/schemaKeyGenerator'

const tableRef = ref(null)
const loading = ref(false)
const syncingCache = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({
  keyword: ''
})
const batchMode = ref(false)
const selectedRows = ref([])
const dialogVisible = ref(false)
const editMode = ref(false)
const localSyncAt = ref(0)
const TERMINOLOGY_SYNC_VERSION_KEY = 'dahe.admin.v2.terminology.syncVersion'
const localSyncVersion = ref(typeof window === 'undefined'
  ? ''
  : String(window.localStorage.getItem(TERMINOLOGY_SYNC_VERSION_KEY) || ''))

const form = reactive({
  id: null,
  source: '',
  target: '',
  sortOrder: 0
})

const previewInput = ref('')
const previewLoading = ref(false)
const previewResult = reactive({
  fieldKey: '',
  optionValue: ''
})

const batchDeleteCount = computed(() => (Array.isArray(selectedRows.value) ? selectedRows.value.length : 0))

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function normalizeEntries(entries) {
  const out = []
  const used = new Set()
  ;(Array.isArray(entries) ? entries : []).forEach((item, index) => {
    const source = String(item && item.source ? item.source : '').trim()
    const target = String(item && item.target ? item.target : '').trim()
    if (!source || !target || used.has(source)) return
    used.add(source)
    const id = toId(item && item.id)
    out.push({
      id: id || `tmp-${source}-${index}`,
      source,
      target,
      sortOrder: Number(item && item.sortOrder) > 0 ? Number(item.sortOrder) : 0
    })
  })
  return out
}

function toGeneratorEntries(entries) {
  return normalizeEntries(entries).map((item) => [item.source, item.target])
}

function toReplaceEntries(entries) {
  return normalizeEntries(entries).map((item) => ({
    source: item.source,
    target: item.target,
    sortOrder: Number(item.sortOrder) > 0 ? Number(item.sortOrder) : 0
  }))
}

async function fetchAllRows() {
  const data = await request.get('/admin/terminology-dict/all', {
    params: {
      limit: 5000
    }
  })
  return normalizeEntries(data)
}

async function fetchSyncMeta(version = '') {
  const params = {}
  const token = toId(version)
  if (token) params.version = token
  const data = await request.get('/admin/terminology-dict/sync-meta', { params })
  return {
    changed: !!(data && data.changed),
    version: toId(data && data.version)
  }
}

async function syncGeneratorCache(force = false) {
  if (syncingCache.value) return
  const now = Date.now()
  if (!force && now - Number(localSyncAt.value || 0) < 60000) return
  syncingCache.value = true
  try {
    const syncMeta = await fetchSyncMeta(force ? '' : localSyncVersion.value)
    if (!force && !syncMeta.changed) {
      localSyncAt.value = Date.now()
      if (syncMeta.version) {
        localSyncVersion.value = syncMeta.version
        if (typeof window !== 'undefined') {
          window.localStorage.setItem(TERMINOLOGY_SYNC_VERSION_KEY, syncMeta.version)
        }
      }
      return
    }
    const allRows = await fetchAllRows()
    setCustomPhraseEntries(toGeneratorEntries(allRows))
    localSyncAt.value = Date.now()
    if (syncMeta.version) {
      localSyncVersion.value = syncMeta.version
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(TERMINOLOGY_SYNC_VERSION_KEY, syncMeta.version)
      }
    }
  } catch (error) {
    // keep local cache when remote sync fails
  } finally {
    syncingCache.value = false
  }
}

async function loadRows(targetPage = page.value) {
  loading.value = true
  try {
    const data = await request.get('/admin/terminology-dict', {
      params: {
        keyword: filters.keyword,
        page: Number(targetPage) > 0 ? Number(targetPage) : 1,
        pageSize: Number(pageSize.value) > 0 ? Number(pageSize.value) : 20
      }
    })
    rows.value = normalizeEntries(data && data.records)
    page.value = Number((data && data.current) || targetPage || 1)
    pageSize.value = Number((data && data.size) || pageSize.value || 10)
    total.value = Number((data && data.total) || 0)
    clearSelection()
    await syncGeneratorCache(false)
  } catch (error) {
    ElMessage.error(error.message || '加载术语词典失败')
  } finally {
    loading.value = false
  }
}

function clearSelection() {
  selectedRows.value = []
  if (tableRef.value && typeof tableRef.value.clearSelection === 'function') {
    tableRef.value.clearSelection()
  }
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearSelection()
  }
}

function onSelectionChange(selection) {
  selectedRows.value = Array.isArray(selection) ? selection : []
}

function resetForm() {
  form.id = null
  form.source = ''
  form.target = ''
  form.sortOrder = 0
}

function openCreate() {
  editMode.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editMode.value = true
  form.id = toId(row && row.id) || null
  form.source = String((row && row.source) || '').trim()
  form.target = String((row && row.target) || '').trim()
  form.sortOrder = Number(row && row.sortOrder) > 0 ? Number(row.sortOrder) : 0
  dialogVisible.value = true
}

async function saveTerm() {
  const source = String(form.source || '').trim()
  const target = String(form.target || '').trim()
  if (!source) {
    ElMessage.warning('请填写中文术语')
    return
  }
  if (!target) {
    ElMessage.warning('请填写英文语义')
    return
  }
  try {
    const payload = {
      source,
      target,
      sortOrder: Number(form.sortOrder) > 0 ? Number(form.sortOrder) : 0
    }
    if (editMode.value && toId(form.id)) {
      await request.put(`/admin/terminology-dict/${form.id}`, payload)
      ElMessage.success('术语已更新')
      dialogVisible.value = false
      await loadRows(page.value)
      await syncGeneratorCache(true)
      return
    }
    await request.post('/admin/terminology-dict', payload)
    ElMessage.success('术语已新增')
    dialogVisible.value = false
    await loadRows(1)
    await syncGeneratorCache(true)
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function removeTerm(row) {
  const id = toId(row && row.id)
  const source = String((row && row.source) || '').trim() || '-'
  if (!id) return
  try {
    await ElMessageBox.confirm(`确认删除术语“${source}”吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/terminology-dict/${id}`)
    const nextPage = rows.value.length <= 1 && page.value > 1 ? page.value - 1 : page.value
    await loadRows(nextPage)
    await syncGeneratorCache(true)
    ElMessage.success('术语已删除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

async function batchDelete() {
  const ids = Array.isArray(selectedRows.value)
    ? selectedRows.value.map((item) => toId(item && item.id)).filter(Boolean)
    : []
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的术语')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 条术语吗？`, '批量删除确认', { type: 'warning' })
    await request.post('/admin/terminology-dict/batch-delete', { ids })
    const nextPage = rows.value.length <= ids.length && page.value > 1 ? page.value - 1 : page.value
    await loadRows(nextPage)
    await syncGeneratorCache(true)
    ElMessage.success('批量删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
  }
}

async function exportJson() {
  try {
    const allRows = await fetchAllRows()
    const payload = allRows.map((item) => ({
      source: item.source,
      target: item.target,
      sortOrder: Number(item.sortOrder) > 0 ? Number(item.sortOrder) : 0
    }))
    const content = JSON.stringify(payload, null, 2)
    const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `dahe-terminology-dict-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  }
}

function parseImportedRows(parsed) {
  if (Array.isArray(parsed)) {
    return parsed
      .map((item) => {
        if (Array.isArray(item)) {
          return {
            source: String(item[0] || '').trim(),
            target: String(item[1] || '').trim(),
            sortOrder: 0
          }
        }
        if (item && typeof item === 'object') {
          return {
            source: String(item.source || '').trim(),
            target: String(item.target || '').trim(),
            sortOrder: Number(item.sortOrder) > 0 ? Number(item.sortOrder) : 0
          }
        }
        return { source: '', target: '', sortOrder: 0 }
      })
      .filter((item) => item.source && item.target)
  }
  if (parsed && typeof parsed === 'object') {
    return Object.entries(parsed)
      .map(([source, target]) => ({
        source: String(source || '').trim(),
        target: String(target || '').trim(),
        sortOrder: 0
      }))
      .filter((item) => item.source && item.target)
  }
  return []
}

async function onImportFileChange(file) {
  const raw = file && file.raw
  if (!raw || typeof raw.text !== 'function') {
    ElMessage.warning('导入文件不可读取')
    return
  }
  try {
    const text = await raw.text()
    const parsed = JSON.parse(String(text || ''))
    const importedEntries = parseImportedRows(parsed)
    if (!importedEntries.length) {
      ElMessage.warning('导入文件中未识别到有效术语')
      return
    }
    const currentRows = await fetchAllRows()
    const merged = new Map()
    currentRows.forEach((item) => {
      merged.set(item.source, {
        source: item.source,
        target: item.target,
        sortOrder: Number(item.sortOrder) > 0 ? Number(item.sortOrder) : 0
      })
    })
    importedEntries.forEach((item) => {
      merged.set(item.source, {
        source: item.source,
        target: item.target,
        sortOrder: Number(item.sortOrder) > 0 ? Number(item.sortOrder) : 0
      })
    })
    await request.post('/admin/terminology-dict/replace-all', {
      entries: toReplaceEntries(Array.from(merged.values()))
    })
    await loadRows(1)
    await syncGeneratorCache(true)
    ElMessage.success(`导入完成，共合并 ${importedEntries.length} 条术语`)
  } catch (error) {
    ElMessage.error(error.message || '导入失败，请检查 JSON 格式')
  }
}

async function clearAllTerms() {
  if (!total.value) return
  try {
    await ElMessageBox.confirm('确认清空全部自定义术语吗？此操作不可撤销。', '清空确认', { type: 'warning' })
    await request.post('/admin/terminology-dict/replace-all', {
      entries: []
    })
    await loadRows(1)
    await syncGeneratorCache(true)
    ElMessage.success('已清空术语词典')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '清空失败')
    }
  }
}

function resetFilters() {
  filters.keyword = ''
  loadRows(1)
}

function onPageSizeChange(size) {
  pageSize.value = Number(size) > 0 ? Number(size) : 20
  loadRows(1)
}

function previewFieldKey(label) {
  return generateFieldKey(String(label || '').trim(), 1, new Set())
}

function previewOptionValue(label) {
  return generateOptionValue(String(label || '').trim(), 1, new Set())
}

async function generatePreview() {
  const label = String(previewInput.value || '').trim()
  if (!label) {
    ElMessage.warning('请先输入字段名称')
    return
  }
  previewLoading.value = true
  try {
    previewResult.fieldKey = await generateFieldKeyAsync(label, 1, new Set())
    previewResult.optionValue = await generateOptionValueAsync(label, 1, new Set())
  } catch (error) {
    ElMessage.error(error.message || '预览生成失败')
  } finally {
    previewLoading.value = false
  }
}

onMounted(() => {
  loadRows(1)
})
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.preview-lines {
  display: grid;
  gap: 4px;
}

.preview-label {
  color: var(--text-sub);
  margin-right: 4px;
}

.table-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

