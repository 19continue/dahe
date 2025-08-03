<template>
  <div class="process-templates-page">
    <PageToolbar
      title="流程模板管理"
      subtitle="列表页负责检索与入口分发，模板步骤编辑在详情页完成。"
      collapsible
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.cropFilterKey && filters.cropFilterKey !== 'all' ? `作物品种：${selectedCropFilterLabel}` : '',
        filters.enabled ? `状态：${enabledFilterLabel}` : '',
        batchMode ? `多选：已选 ${selectedRows.length} 条` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" clearable placeholder="模板名关键字" style="width: 220px" @keyup.enter="loadTemplates(1)" />
        <el-select v-model="filters.cropFilterKey" clearable filterable placeholder="作物/品种" style="width: 220px">
          <el-option label="全部" value="all" />
          <el-option v-for="item in cropFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.enabled" clearable placeholder="状态" style="width: 130px">
          <el-option label="全部" value="all" />
          <el-option label="启用" value="1" />
          <el-option label="禁用" value="0" />
        </el-select>
        <el-button @click="loadTemplates(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openCreateDialog">新建模板</el-button>
        <el-button
          type="default"
          plain
          class="batch-toggle-btn"
          :class="{ 'is-active': batchMode }"
          @click="toggleBatchMode"
        >
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <el-button
          v-if="batchMode"
          type="success"
          :disabled="!batchEnableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchToggleEnabled(true)"
        >
          批量启用（{{ batchEnableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="warning"
          :disabled="!batchDisableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchToggleEnabled(false)"
        >
          批量禁用（{{ batchDisableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="danger"
          :disabled="!batchDeleteCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchDeleteTemplates"
        >
          批量删除（{{ batchDeleteCount }}）
        </el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-head">
          <span>流程模板列表</span>
          <span class="card-meta">共 {{ total }} 条</span>
        </div>
      </template>

      <el-table
        ref="templateTableRef"
        :data="templates"
        border
        :row-key="(row) => row.id"
        @selection-change="onTemplateSelectionChange"
      >
        <el-table-column v-if="batchMode" type="selection" width="48" />
        <el-table-column prop="templateName" label="模板名称" min-width="220" />
        <el-table-column label="绑定作物/品种" min-width="220">
          <template #default="scope">
            <CropPairTags
              :pairs="[
                {
                  cropType: scope.row.categoryName,
                  cropVariety: scope.row.varietyName
                }
              ]"
              empty-text="未绑定"
              tag-type="warning"
            />
          </template>
        </el-table-column>
        <el-table-column label="绑定范围" width="120">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.bindScope === 'variety' ? 'warning' : 'info'">
              {{ scope.row.bindScope === 'variety' ? '品种专属' : '作物通用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="90">
          <template #default="scope">
            <el-tag v-if="Number(scope.row.isDefault) === 1" size="small" type="success">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="Number(scope.row.enabled) === 1 ? 'success' : 'info'">
              {{ Number(scope.row.enabled) === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="步骤数" width="90">
          <template #default="scope">{{ stepCount(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="264" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line">
              <el-button size="small" @click="openDetail(scope.row, false)">查看</el-button>
              <el-button size="small" type="primary" @click="openDetail(scope.row, true)">编辑</el-button>
              <el-button
                size="small"
                :type="Number(scope.row.enabled) === 1 ? 'warning' : 'success'"
                plain
                @click="switchTemplateEnabled(scope.row)"
              >
                {{ Number(scope.row.enabled) === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" plain @click="removeTemplate(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onPageSizeChange"
          @current-change="loadTemplates"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建流程模板" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="作物">
          <el-select v-model="createForm.categoryId" filterable style="width: 100%" @change="onCreateCategoryChange">
            <el-option v-for="item in cropCategories" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-select
            v-model="createForm.varietyId"
            clearable
            filterable
            placeholder="不选则为作物通用模板"
            style="width: 100%"
            @change="onCreateVarietyChange"
          >
            <el-option v-for="item in createVarietyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名">
          <el-input v-model="createForm.templateName" placeholder="例如：玉米标准流程v3" />
        </el-form-item>
        <el-form-item label="默认模板">
          <el-switch v-model="createForm.isDefault" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="createForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCreate" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CropPairTags from '../components/ui/CropPairTags.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const router = useRouter()

const loading = ref(false)
const savingCreate = ref(false)
const templates = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const templateTableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)

const cropCategories = ref([])
const allVarieties = ref([])
const varietyToCategory = ref(new Map())

const filters = reactive({
  keyword: '',
  cropFilterKey: 'all',
  enabled: 'all'
})

const createDialogVisible = ref(false)
const createForm = reactive({
  categoryId: null,
  varietyId: null,
  templateName: '',
  isDefault: false,
  enabled: true
})

const batchEnableCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.enabled) !== 1).length
})

const batchDisableCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.enabled) === 1).length
})
const batchDeleteCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.id) > 0).length
})

function toId(value) {
  if (value === null || value === undefined) return null
  const raw = String(value).trim()
  if (!raw || raw === '0') return null
  if (!/^\d+$/.test(raw)) return null
  const normalized = raw.replace(/^0+/, '')
  return normalized || null
}

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((key) => {
    const value = input[key]
    if (value === null || value === undefined || value === '') return
    out[key] = value
  })
  return out
}

function clearTemplateSelection() {
  selectedRows.value = []
  if (templateTableRef.value && typeof templateTableRef.value.clearSelection === 'function') {
    templateTableRef.value.clearSelection()
  }
}

function onTemplateSelectionChange(selection) {
  selectedRows.value = Array.isArray(selection) ? selection : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearTemplateSelection()
  }
}

const createVarietyOptions = computed(() => {
  const categoryId = toId(createForm.categoryId)
  if (!categoryId) return allVarieties.value
  return allVarieties.value.filter((item) => toId(item && item.categoryId) === categoryId)
})

const cropFilterOptions = computed(() => {
  const out = []
  ;(Array.isArray(cropCategories.value) ? cropCategories.value : []).forEach((item) => {
    const categoryId = toId(item && item.value)
    const categoryName = String((item && item.label) || '').trim()
    if (!categoryId || !categoryName) return
    out.push({
      value: `c:${categoryId}`,
      label: categoryName
    })
  })
  ;(Array.isArray(allVarieties.value) ? allVarieties.value : []).forEach((item) => {
    const varietyId = toId(item && item.value)
    const varietyName = String((item && item.label) || '').trim()
    const categoryName = String((item && item.categoryName) || '').trim()
    if (!varietyId || !varietyName) return
    out.push({
      value: `v:${varietyId}`,
      label: categoryName ? `${categoryName}·${varietyName}` : varietyName
    })
  })
  return out
})

const selectedCropFilterLabel = computed(() => {
  const raw = String(filters.cropFilterKey || '').trim().toLowerCase()
  if (!raw || raw === 'all') return '全部'
  const hit = cropFilterOptions.value.find((item) => String(item && item.value).trim().toLowerCase() === raw)
  return String((hit && hit.label) || raw).trim()
})

const enabledFilterLabel = computed(() => {
  const value = String(filters.enabled || '').trim().toLowerCase()
  if (value === 'all') return '全部'
  return value === '1' ? '启用' : (value === '0' ? '禁用' : '')
})

function stepCount(row) {
  const steps = Array.isArray(row && row.steps) ? row.steps : []
  return steps.length
}

function parseCropFilterKey(value) {
  const raw = String(value || '').trim().toLowerCase()
  if (!raw || raw === 'all') {
    return { categoryId: null, varietyId: null }
  }
  if (raw.startsWith('c:')) {
    return { categoryId: toId(raw.slice(2)), varietyId: null }
  }
  if (raw.startsWith('v:')) {
    const varietyId = toId(raw.slice(2))
    if (!varietyId) {
      return { categoryId: null, varietyId: null }
    }
    const categoryId = toId(varietyToCategory.value.get(varietyId))
    return { categoryId: categoryId || null, varietyId }
  }
  return { categoryId: null, varietyId: null }
}

function rebuildCropOptions(treeRows) {
  const categories = []
  const varieties = []
  const v2c = new Map()
  ;(Array.isArray(treeRows) ? treeRows : []).forEach((item) => {
    const categoryId = toId(item && item.categoryId)
    const categoryName = String((item && item.categoryName) || '').trim()
    if (!categoryId || !categoryName) return
    categories.push({ value: categoryId, label: categoryName })
    const child = Array.isArray(item && item.varieties) ? item.varieties : []
    child.forEach((v) => {
      const varietyId = toId(v && v.id)
      const varietyName = String((v && v.name) || '').trim()
      if (!varietyId || !varietyName) return
      varieties.push({
        value: varietyId,
        label: varietyName,
        categoryId,
        categoryName
      })
      v2c.set(varietyId, categoryId)
    })
  })
  cropCategories.value = categories
  allVarieties.value = varieties
  varietyToCategory.value = v2c
}

async function loadCropTree() {
  try {
    const data = await request.get('/meta/options/crop-tree')
    rebuildCropOptions(data)
    if (!createForm.categoryId && cropCategories.value.length) {
      createForm.categoryId = cropCategories.value[0].value
    }
  } catch (error) {
    cropCategories.value = []
    allVarieties.value = []
    varietyToCategory.value = new Map()
  }
}

function onCreateCategoryChange() {
  const categoryId = toId(createForm.categoryId)
  if (!categoryId) return
  const varietyId = toId(createForm.varietyId)
  if (!varietyId) return
  const hit = varietyToCategory.value.get(varietyId)
  if (!hit || toId(hit) !== categoryId) {
    createForm.varietyId = null
  }
}

function onCreateVarietyChange(value) {
  const varietyId = toId(value)
  if (!varietyId) return
  const hit = varietyToCategory.value.get(varietyId)
  if (hit) createForm.categoryId = hit
}

async function loadTemplates(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const cropFilter = parseCropFilterKey(filters.cropFilterKey)
    const data = await request.get('/farm-process/templates', {
      params: cleanParams({
        page: page.value,
        pageSize: pageSize.value,
        includeDisabled: true,
        includeSteps: true,
        keyword: filters.keyword,
        categoryId: cropFilter.categoryId || undefined,
        varietyId: cropFilter.varietyId || undefined,
        enabled: String(filters.enabled || '').trim().toLowerCase() === 'all' ? undefined : filters.enabled
      })
    })
    templates.value = (data && data.records) || []
    total.value = Number((data && data.total) || 0)
    clearTemplateSelection()
  } catch (error) {
    templates.value = []
    total.value = 0
    ElMessage.error(error.message || '模板列表加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadTemplates(1)
}

function resetFilters() {
  filters.keyword = ''
  filters.cropFilterKey = 'all'
  filters.enabled = 'all'
  loadTemplates(1)
}

function openDetail(row, editable) {
  const id = toId(row && row.id)
  if (!id) return
  const templateName = String((row && row.templateName) || '').trim()
  const query = {
    ...(editable ? { mode: 'edit' } : {}),
    ...(templateName ? { templateName } : {})
  }
  router.push({
    path: `/process-templates/${id}`,
    query: Object.keys(query).length ? query : undefined
  })
}

function resetCreateForm() {
  createForm.categoryId = cropCategories.value.length ? cropCategories.value[0].value : null
  createForm.varietyId = null
  createForm.templateName = ''
  createForm.isDefault = false
  createForm.enabled = true
}

function openCreateDialog() {
  resetCreateForm()
  createDialogVisible.value = true
}

async function saveCreate() {
  const categoryId = toId(createForm.categoryId)
  const varietyId = toId(createForm.varietyId)
  const templateName = String(createForm.templateName || '').trim()

  if (!categoryId && !varietyId) {
    ElMessage.warning('请选择作物')
    return
  }
  if (!templateName) {
    ElMessage.warning('请输入模板名')
    return
  }

  savingCreate.value = true
  try {
    const payload = {
      categoryId,
      varietyId,
      cropId: varietyId || categoryId,
      templateName,
      isDefault: !!createForm.isDefault,
      enabled: !!createForm.enabled
    }
    const saved = await request.post('/farm-process/templates', payload)
    createDialogVisible.value = false
    await loadTemplates(1)
    ElMessage.success('模板创建成功')
    if (saved && saved.id) {
      openDetail(saved, true)
    }
  } catch (error) {
    ElMessage.error(error.message || '模板创建失败')
  } finally {
    savingCreate.value = false
  }
}

async function switchTemplateEnabled(row) {
  const id = toId(row && row.id)
  if (!id) return
  const currentEnabled = Number((row && row.enabled) || 0) === 1
  const nextEnabled = !currentEnabled
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await request.put(`/farm-process/templates/${id}/enabled`, { enabled: nextEnabled })
    ElMessage.success(`模板已${actionText}`)
    await loadTemplates(page.value)
  } catch (error) {
    ElMessage.error(error.message || `模板${actionText}失败`)
  }
}

async function removeTemplate(row) {
  const id = toId(row && row.id)
  if (!id) return
  const name = String((row && row.templateName) || '').trim() || `模板#${id}`
  try {
    await ElMessageBox.confirm(`确认删除模板“${name}”吗？`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除确认失败')
    }
    return
  }
  batchSubmitting.value = true
  try {
    await request.delete(`/farm-process/templates/${id}`)
    ElMessage.success('模板已删除')
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadTemplates(Math.min(Number(page.value || 1), maxPage))
  } catch (error) {
    ElMessage.error(error.message || '模板删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function batchToggleEnabled(nextEnabled) {
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => {
    return Number(row && row.enabled) !== (nextEnabled ? 1 : 0)
  })
  if (!targets.length) {
    ElMessage.warning(nextEnabled ? '当前所选中没有可启用模板' : '当前所选中没有可禁用模板')
    return
  }
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认批量${actionText}已选 ${targets.length} 个模板吗？`, `批量${actionText}确认`, { type: 'warning' })
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || `批量${actionText}确认失败`)
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => request.put(`/farm-process/templates/${row.id}/enabled`, { enabled: !!nextEnabled }))
    )
    const successCount = results.filter((x) => x.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await loadTemplates(page.value)
    if (failedCount > 0) {
      ElMessage.warning(`批量${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`批量${actionText}成功：${successCount} 个模板`)
    }
  } catch (error) {
    ElMessage.error(error.message || `批量${actionText}失败`)
  } finally {
    batchSubmitting.value = false
  }
}

async function batchDeleteTemplates() {
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.id) > 0)
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的模板')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选 ${targets.length} 个模板吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除确认失败')
    }
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => request.delete(`/farm-process/templates/${row.id}`))
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - successCount)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadTemplates(Math.min(Number(page.value || 1), maxPage))
    if (failedCount > 0) {
      ElMessage.warning(`批量删除完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量删除成功：${successCount} 个模板`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

onMounted(async () => {
  await loadCropTree()
  await loadTemplates(1)
})
</script>

<style scoped>
.process-templates-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}
</style>

