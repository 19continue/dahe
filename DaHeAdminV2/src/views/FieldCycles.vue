<template>
  <div class="field-cycles-page">
    <PageToolbar
      title="田块种植计划管理"
      subtitle="田块计划与全部计划双视角运营，计划详情在独立标签页中管理。"
      collapsible
      :summary="[
        mode === 'field' ? '当前模式：田块计划' : '当前模式：全部计划',
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.township ? `乡镇：${filters.township}` : '',
        filters.cropFilterKey ? `作物品种：${resolveVarietyFilterText(filters.cropFilterKey)}` : '',
        filters.status ? `阶段：${fieldStatusText(filters.status)}` : '',
        mode === 'all' && filters.planMode ? `计划模式：${planModeText(filters.planMode)}` : '',
        mode === 'all' && allBatchMode ? `多选：已选 ${allSelectedRows.length} 条` : ''
      ]"
    >
      <template #head-actions>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button label="field">田块计划</el-radio-button>
          <el-radio-button label="all">全部计划</el-radio-button>
        </el-radio-group>
      </template>

      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="田块名/计划名关键字" clearable style="width: 220px" />
        <el-select v-model="filters.township" clearable filterable placeholder="乡镇" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option v-for="item in townshipOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.cropFilterKey" clearable filterable placeholder="作物/品种" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option-group v-for="group in varietyGroupOptions" :key="group.cropType" :label="group.cropType">
            <el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" />
          </el-option-group>
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="田块阶段" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="播种" value="sowing" />
          <el-option label="生长" value="growing" />
          <el-option label="收获" value="harvesting" />
          <el-option label="空闲" value="idle" />
          <el-option label="休耕" value="fallow" />
        </el-select>
        <el-select v-if="mode === 'all'" v-model="filters.planMode" clearable placeholder="计划模式" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="单作" value="single" />
          <el-option label="轮作" value="rotation" />
          <el-option label="间作" value="intercropping" />
          <el-option label="套作" value="relay" />
          <el-option label="混作" value="mixed" />
          <el-option label="休耕" value="fallow" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <el-button @click="runQuery">查询</el-button>
      </div>
    </PageToolbar>

    <section v-if="mode === 'field'" class="field-board" v-loading="loadingFields">
      <div v-if="fieldRows.length" class="field-grid">
        <article v-for="field in fieldRows" :key="field.id" class="field-card">
          <div class="field-main" @click="openFieldDetail(field)">
            <div class="field-main-top">
              <el-image v-if="field.coverImageUrl" :src="field.coverImageUrl" fit="cover" class="field-thumb" />
              <div v-else class="field-thumb field-thumb-placeholder">田</div>
              <div class="field-main-content">
                <div class="field-title">
                  <strong>{{ field.name || '-' }}</strong>
                </div>
                <div class="field-state-col">
                  <el-tag size="small" :type="isFieldDisabled(field) ? 'info' : 'success'">{{ isFieldDisabled(field) ? '禁用' : '启用' }}</el-tag>
                  <el-tag size="small" type="info">阶段：{{ fieldStatusText(field.status) }}</el-tag>
                </div>
                <div class="field-meta">
                  <span>乡镇：{{ field.township || '-' }}</span>
                  <span class="field-crop-tags">
                    <span>作物品种：</span>
                    <CropPairTags
                      :crop-variety-groups="field.cropVarietyGroups"
                      :crop-type="field.cropType"
                      :crop-variety="field.cropVariety"
                      empty-text="未配置"
                    />
                  </span>
                </div>
              </div>
            </div>
            <div class="field-metrics">
              <span class="metric-label">计划总数</span>
              <span class="metric-value">{{ fieldPlanCount(field.id) }}</span>
            </div>
          </div>
          <div class="field-foot">
            <el-popover trigger="hover" placement="top-start" :width="360">
              <template #reference>
                <el-button size="small">悬停预览计划</el-button>
              </template>
              <div class="plan-preview">
                <div class="plan-preview-title">计划预览</div>
                <div v-if="fieldPlanPreview(field.id).length" class="plan-preview-list">
                  <div v-for="plan in fieldPlanPreview(field.id)" :key="`${field.id}-${plan.id}`" class="plan-preview-item">
                    <strong>{{ plan.cycleName || '-' }}</strong>
                    <span>{{ plan.startDate || '-' }} ~ {{ plan.endDate || '-' }}</span>
                  </div>
                </div>
                <el-empty v-else description="该田块暂无计划" :image-size="40" />
              </div>
            </el-popover>
            <el-button size="small" type="primary" @click.stop="openFieldDetail(field)">进入管理</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无田块数据" :image-size="56" />

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="fieldTotal"
          :page-size="fieldPageSize"
          :current-page="fieldPage"
          :page-sizes="[12, 15, 18, 24, 30]"
          @size-change="onFieldPageSizeChange"
          @current-change="loadFieldCards"
        />
      </div>
    </section>

    <section v-else class="all-plan-section">
      <el-card shadow="never" v-loading="loadingAll">
        <template #header>
          <div class="card-head">
            <div>
              <span>全部种植计划</span>
              <span class="card-meta">共 {{ allTotal }} 条</span>
            </div>
            <div class="actions">
              <el-button
                size="small"
                type="default"
                plain
                class="batch-toggle-btn"
                :class="{ 'is-active': allBatchMode }"
                @click="toggleAllBatchMode"
              >
                {{ allBatchMode ? '退出多选' : '多选操作' }}
              </el-button>
              <el-button
                v-if="allBatchMode"
                size="small"
                type="success"
                :disabled="!batchSetCurrentCount || allBatchSubmitting"
                :loading="allBatchSubmitting"
                @click="batchSetCurrent"
              >
                批量设为当前（{{ batchSetCurrentCount }}）
              </el-button>
              <el-button
                v-if="allBatchMode"
                size="small"
                type="danger"
                :disabled="!batchDeleteCount || allBatchSubmitting"
                :loading="allBatchSubmitting"
                @click="batchDeleteCycles"
              >
                批量删除（{{ batchDeleteCount }}）
              </el-button>
            </div>
          </div>
        </template>
        <el-table
          ref="allTableRef"
          :data="allRows"
          border
          :row-key="(row) => `${row.fieldId || ''}-${row.id || ''}`"
          @selection-change="onAllSelectionChange"
        >
          <el-table-column v-if="allBatchMode" type="selection" width="48" />
          <el-table-column prop="fieldName" label="田块" min-width="140" show-overflow-tooltip />
          <el-table-column prop="township" label="乡镇" width="120" />
          <el-table-column prop="cycleName" label="计划名称" min-width="170" show-overflow-tooltip />
          <el-table-column label="作物组合" min-width="220">
            <template #default="scope">
              <div class="plan-crop-tag-list">
                <el-tag v-if="isFallowCycle(scope.row)" size="small" type="info" effect="plain">休耕</el-tag>
                <CropPairTags v-else :pairs="resolveCycleCropPairs(scope.row)" empty-text="未配置" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="110">
            <template #default="scope">
              <el-tag :type="planModeTagType(scope.row.planMode)" size="small">{{ planModeText(scope.row.planMode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="scope">
              <el-tag :type="cycleStatusType(scope.row.status)" size="small">{{ cycleStatusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前" width="80">
            <template #default="scope">
              <el-tag v-if="Number(scope.row.isCurrent) === 1" type="success" size="small">当前</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="开始日期" width="120">
            <template #default="scope">{{ formatDate(scope.row.startDate) }}</template>
          </el-table-column>
          <el-table-column label="结束日期" width="120">
            <template #default="scope">{{ formatDate(scope.row.endDate) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right" class-name="op-col">
            <template #default="scope">
              <div class="table-op-line">
                <el-button size="small" @click="openFieldDetail(scope.row)">进入田块页</el-button>
                <el-button size="small" type="primary" @click="openEditCycle(scope.row)">编辑计划</el-button>
                <el-button size="small" type="success" :disabled="Number(scope.row.isCurrent) === 1" @click="setCurrent(scope.row)">
                  设为当前
                </el-button>
                <el-button size="small" type="danger" plain @click="removeCycle(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-foot">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="allTotal"
            :page-size="allPageSize"
            :current-page="allPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onAllPageSizeChange"
            @current-change="loadAllPlans"
          />
        </div>
      </el-card>
    </section>

    <el-dialog v-model="cycleDialogVisible" title="编辑种植计划" width="820px" destroy-on-close>
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="所属田块">
              <el-input :model-value="cycleDialog.fieldName || '-'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划名称">
              <el-input v-model="cycleDialog.form.cycleName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="种植模式">
              <el-select v-model="cycleDialog.form.planMode" style="width: 100%">
                <el-option label="单作" value="single" />
                <el-option label="轮作" value="rotation" />
                <el-option label="间作" value="intercropping" />
                <el-option label="套作" value="relay" />
                <el-option label="混作" value="mixed" />
                <el-option label="休耕" value="fallow" />
                <el-option label="自定义" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划状态">
              <el-select v-model="cycleDialog.form.status" style="width: 100%">
                <el-option label="进行中" value="active" />
                <el-option label="已结束" value="completed" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设为当前">
              <el-switch v-model="cycleDialog.form.isCurrent" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="起止日期">
          <el-date-picker v-model="cycleDialog.form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
          <span style="padding: 0 10px">至</span>
          <el-date-picker v-model="cycleDialog.form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
        </el-form-item>
        <el-form-item label="作物组合" v-if="cycleDialog.form.planMode !== 'fallow'">
          <div class="crop-editor">
            <div v-for="(crop, idx) in cycleDialog.form.crops" :key="crop.uid" class="crop-line">
              <el-select v-model="crop.name" filterable placeholder="作物" @change="onCycleCropChange(crop)">
                <el-option v-for="item in cropOptions" :key="item" :label="item" :value="item" />
              </el-select>
              <el-select v-model="crop.variety" filterable placeholder="品种" @change="onCycleVarietyChange(crop)">
                <el-option v-for="item in cycleCropVarieties(crop)" :key="item" :label="item" :value="item" />
              </el-select>
              <el-select
                v-model="crop.templateId"
                filterable
                placeholder="流程模板"
                :disabled="(!crop.name && !crop.variety) || templateLoading"
                :loading="templateLoading"
              >
                <el-option
                  v-for="tpl in cycleTemplateOptions(crop)"
                  :key="tpl.id"
                  :label="templateOptionLabel(tpl)"
                  :value="tpl.id"
                  :disabled="Number(tpl && tpl.enabled) === 0 && toId(crop.templateId) !== toId(tpl.id)"
                />
              </el-select>
              <el-button size="small" type="danger" @click="removeCycleCrop(idx)" :disabled="cycleDialog.form.crops.length <= 1">删除</el-button>
            </div>
            <el-button size="small" type="primary" @click="addCycleCrop">添加作物</el-button>
          </div>
        </el-form-item>
        <el-form-item label="休耕说明" v-else>
          <el-alert type="info" show-icon :closable="false" title="休耕计划无需配置作物和流程模板，保存后田块阶段会自动同步为休耕。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cycleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCycle" @click="saveCycle">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CropPairTags from '../components/ui/CropPairTags.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const router = useRouter()

const mode = ref('field')
const loadingFields = ref(false)
const loadingAll = ref(false)
const savingCycle = ref(false)

const filters = reactive({
  keyword: '',
  township: '',
  cropFilterKey: '',
  status: '',
  planMode: ''
})

const fieldRows = ref([])
const fieldPage = ref(1)
const fieldPageSize = ref(12)
const fieldTotal = ref(0)
const fieldPlanMeta = ref({})

const allRows = ref([])
const allPage = ref(1)
const allPageSize = ref(10)
const allTotal = ref(0)
const allTableRef = ref(null)
const allBatchMode = ref(false)
const allSelectedRows = ref([])
const allBatchSubmitting = ref(false)

const townshipOptions = ref([])
const cropOptions = ref([])
const varietyOptions = ref([])
const varietyGroupOptions = ref([])
const cropToVarieties = reactive({})
const varietyNameToCropName = ref(new Map())
const varietyNameToCategoryId = ref(new Map())
const varietyNameToId = ref(new Map())
const cropNameToCategoryId = ref(new Map())
const templateOptions = ref([])
const templateLoadingCount = ref(0)
const templateLoading = computed(() => templateLoadingCount.value > 0)
const loadedTemplateScopeKeys = new Set()
const templateScopeTasks = new Map()
const VARIETY_FILTER_DELIMITER = '||'
let cropUidSeed = 1

const cycleDialogVisible = ref(false)
const cycleDialog = reactive({
  fieldName: '',
  form: {
    id: null,
    fieldId: null,
    cycleName: '',
    planMode: 'single',
    status: 'active',
    startDate: '',
    endDate: '',
    isCurrent: false,
    crops: [createCropRow()]
  }
})

const batchSetCurrentCount = computed(() => {
  return collectBatchCurrentTargets(allSelectedRows.value).length
})
const batchDeleteCount = computed(() => {
  return collectBatchDeleteTargets(allSelectedRows.value).length
})

function clearAllSelection() {
  allSelectedRows.value = []
  if (allTableRef.value && typeof allTableRef.value.clearSelection === 'function') {
    allTableRef.value.clearSelection()
  }
}

function onAllSelectionChange(rows) {
  allSelectedRows.value = Array.isArray(rows) ? rows : []
}

function toggleAllBatchMode() {
  allBatchMode.value = !allBatchMode.value
  if (!allBatchMode.value) {
    clearAllSelection()
  }
}

function collectBatchCurrentTargets(rowsInput) {
  const rows = Array.isArray(rowsInput) ? rowsInput : []
  const targetMap = new Map()
  rows.forEach((row) => {
    const fieldId = toId(row && row.fieldId)
    const cycleId = toId(row && row.id)
    if (!fieldId || !cycleId) return
    if (Number(row && row.isCurrent) === 1) return
    targetMap.set(fieldId, row)
  })
  return Array.from(targetMap.values())
}

function collectBatchDeleteTargets(rowsInput) {
  const rows = Array.isArray(rowsInput) ? rowsInput : []
  return rows.filter((row) => {
    const fieldId = toId(row && row.fieldId)
    const cycleId = toId(row && row.id)
    return !!(fieldId && cycleId)
  })
}

function toId(value) {
  if (value === null || value === undefined) return null
  const raw = String(value).trim()
  if (!raw || raw === '0') return null
  if (!/^\d+$/.test(raw)) return null
  const normalized = raw.replace(/^0+/, '')
  return normalized || null
}

function text(value) {
  return String(value || '').trim()
}

function makeVarietyFilterValue(cropType, cropVariety) {
  return `${text(cropType)}${VARIETY_FILTER_DELIMITER}${text(cropVariety)}`
}

function parseVarietyFilterValue(value) {
  const raw = text(value)
  if (!raw.includes(VARIETY_FILTER_DELIMITER)) {
    return { cropType: '', cropVariety: '' }
  }
  const [cropType, ...rest] = raw.split(VARIETY_FILTER_DELIMITER)
  return {
    cropType: text(cropType),
    cropVariety: text(rest.join(VARIETY_FILTER_DELIMITER))
  }
}

function resolveVarietyFilterText(value) {
  const parsed = parseVarietyFilterValue(value)
  if (parsed.cropType && parsed.cropVariety) {
    return `${parsed.cropType}·${parsed.cropVariety}`
  }
  return parsed.cropType || parsed.cropVariety || '-'
}

function isFallowCycle(row) {
  return text(row && row.planMode).toLowerCase() === 'fallow'
}

function resolveCycleCropPairs(row) {
  const used = new Set()
  return parseJsonArray(row && row.cropsJson)
    .map((item) => {
      const cropType = text(item && item.name)
      const cropVariety = text(item && item.variety)
      if (!cropType && !cropVariety) return null
      const key = `${cropType}__${cropVariety}`
      if (used.has(key)) return null
      used.add(key)
      return {
        cropType,
        cropVariety
      }
    })
    .filter(Boolean)
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

function toUnique(rows) {
  return Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
}

function clearObject(obj) {
  Object.keys(obj || {}).forEach((k) => delete obj[k])
}

function fieldStatusText(status) {
  const map = {
    sowing: '播种',
    growing: '生长',
    harvesting: '收获',
    idle: '空闲',
    fallow: '休耕'
  }
  return map[String(status || '').toLowerCase()] || '未知'
}

function isFieldDisabled(row) {
  return Number(row && row.enabled) === 0
}

function planModeText(mode) {
  const map = {
    single: '单作',
    rotation: '轮作',
    intercropping: '间作',
    relay: '套作',
    mixed: '混作',
    fallow: '休耕',
    custom: '自定义'
  }
  return map[String(mode || '').toLowerCase()] || '单作'
}

function planModeTagType(mode) {
  const key = String(mode || '').toLowerCase()
  if (key === 'fallow') return 'info'
  if (key === 'rotation' || key === 'intercropping' || key === 'relay' || key === 'mixed') return 'success'
  if (key === 'custom') return 'warning'
  return 'primary'
}

function cycleStatusType(status) {
  return String(status || '').toLowerCase() === 'completed' ? 'success' : 'warning'
}

function cycleStatusText(status) {
  return String(status || '').toLowerCase() === 'completed' ? '已结束' : '进行中'
}

function formatDate(value) {
  const textValue = String(value || '').trim()
  if (!textValue) return '-'
  return textValue.slice(0, 10)
}

function formatDateTime(value) {
  const textValue = String(value || '').trim()
  if (!textValue) return '-'
  if (textValue.includes('T')) return textValue.replace('T', ' ').slice(0, 19)
  return textValue.slice(0, 19)
}

function createCropRow(name = '', variety = '', templateId = null) {
  return {
    uid: cropUidSeed++,
    name,
    variety,
    templateId
  }
}

function parseJsonArray(input) {
  if (!input) return []
  try {
    const rows = JSON.parse(input)
    return Array.isArray(rows) ? rows : []
  } catch (error) {
    return []
  }
}

function resolveCropNameByVariety(varietyName) {
  return text(varietyNameToCropName.value.get(text(varietyName)))
}

function resolveCategoryId(cropName, varietyName) {
  const variety = text(varietyName)
  if (variety) {
    const byVariety = toId(varietyNameToCategoryId.value.get(variety))
    if (byVariety) return byVariety
  }
  return toId(cropNameToCategoryId.value.get(text(cropName)))
}

function resolveVarietyId(varietyName) {
  return toId(varietyNameToId.value.get(text(varietyName)))
}

function templateScopeKeyByCrop(cropName, cropVariety) {
  const categoryId = resolveCategoryId(cropName, cropVariety)
  if (categoryId) {
    return {
      key: `category:${categoryId}`,
      params: { categoryId }
    }
  }
  const varietyId = resolveVarietyId(cropVariety)
  if (varietyId) {
    return {
      key: `variety:${varietyId}`,
      params: { varietyId }
    }
  }
  return null
}

function mergeTemplateOptions(rowsInput) {
  const mergedMap = new Map()
  ;(Array.isArray(templateOptions.value) ? templateOptions.value : []).forEach((item) => {
    const id = toId(item && item.id)
    if (id) mergedMap.set(id, item)
  })
  ;(Array.isArray(rowsInput) ? rowsInput : []).forEach((item) => {
    const id = toId(item && item.id)
    if (id) mergedMap.set(id, item)
  })
  templateOptions.value = Array.from(mergedMap.values())
}

async function fetchTemplatesByScope(scope) {
  const pageSize = 80
  let nextPage = 1
  let expectedTotal = Number.MAX_SAFE_INTEGER
  const merged = []
  while ((nextPage - 1) * pageSize < expectedTotal) {
    const data = await request.get('/farm-process/templates', {
      params: {
        page: nextPage,
        pageSize,
        includeSteps: true,
        includeDisabled: true,
        ...scope.params
      }
    })
    const records = Array.isArray(data && data.records) ? data.records : []
    merged.push(...records)
    expectedTotal = Number((data && data.total) || merged.length || 0)
    if (!records.length || records.length < pageSize) break
    nextPage += 1
    if (nextPage > 20) break
  }
  return merged
}

async function ensureTemplatesForCrop(cropName, cropVariety) {
  const scope = templateScopeKeyByCrop(cropName, cropVariety)
  if (!scope || !scope.key) return
  if (loadedTemplateScopeKeys.has(scope.key)) return
  if (templateScopeTasks.has(scope.key)) {
    await templateScopeTasks.get(scope.key)
    return
  }
  const task = (async () => {
    templateLoadingCount.value += 1
    try {
      const rows = await fetchTemplatesByScope(scope)
      mergeTemplateOptions(rows)
      loadedTemplateScopeKeys.add(scope.key)
    } finally {
      templateLoadingCount.value = Math.max(0, templateLoadingCount.value - 1)
    }
  })()
  templateScopeTasks.set(scope.key, task)
  try {
    await task
  } finally {
    templateScopeTasks.delete(scope.key)
  }
}

async function ensureTemplatesForCrops(cropsInput) {
  const tasks = []
  const rows = Array.isArray(cropsInput) ? cropsInput : []
  rows.forEach((item) => {
    const cropName = text(item && item.name)
    const cropVariety = text(item && item.variety)
    if (!cropName && !cropVariety) return
    tasks.push(ensureTemplatesForCrop(cropName, cropVariety))
  })
  if (!tasks.length) return
  await Promise.all(tasks)
}

function isTemplateMatched(tpl, cropName, cropVariety) {
  if (!tpl) return false
  const selectedCrop = text(cropName)
  const selectedVariety = text(cropVariety)
  const selectedCategoryId = resolveCategoryId(selectedCrop, selectedVariety)
  const selectedVarietyId = resolveVarietyId(selectedVariety)
  const tplCategoryId = toId(tpl.categoryId)
  const tplVarietyId = toId(tpl.varietyId)
  const tplCategoryName = text(tpl.categoryName || tpl.cropName)
  const tplVarietyName = text(tpl.varietyName)

  if (selectedCategoryId) {
    if (tplCategoryId && tplCategoryId !== selectedCategoryId) return false
    if (!tplCategoryId && selectedCrop && tplCategoryName && tplCategoryName !== selectedCrop) return false
  } else if (selectedCrop && tplCategoryName && tplCategoryName !== selectedCrop) {
    return false
  }

  if (selectedVarietyId) return !tplVarietyId || tplVarietyId === selectedVarietyId
  if (selectedVariety) return !tplVarietyName || tplVarietyName === selectedVariety
  return !tplVarietyId && !tplVarietyName
}

function cycleTemplateOptions(crop) {
  const cropName = text(crop && crop.name)
  const cropVariety = text(crop && crop.variety)
  if (!cropName && !cropVariety) return []
  return (templateOptions.value || []).filter((tpl) => isTemplateMatched(tpl, cropName, cropVariety))
}

function defaultCycleTemplateId(cropName, cropVariety) {
  const rows = cycleTemplateOptions({ name: cropName, variety: cropVariety })
  if (!rows.length) return null
  const preferred = rows.find((tpl) => Number(tpl.isDefault) === 1)
  const picked = preferred || rows[0]
  return toId(picked && picked.id)
}

function templateOptionLabel(tpl) {
  const categoryName = text(tpl && (tpl.categoryName || tpl.cropName)) || '作物'
  const varietyName = text(tpl && tpl.varietyName)
  const bindText = varietyName ? ` · ${categoryName} · ${varietyName}` : ` · ${categoryName}（通用）`
  const defaultText = Number(tpl.isDefault) === 1 ? ' · 默认' : ''
  const disabledText = Number(tpl && tpl.enabled) === 0 ? ' · 禁用' : ''
  return `${text(tpl && tpl.templateName) || '-'}${bindText}${defaultText}${disabledText}`
}

function cycleCropVarieties(crop) {
  const cropName = text(crop && crop.name)
  if (!cropName) return [...varietyOptions.value]
  const rows = cropToVarieties[cropName]
  return Array.isArray(rows) && rows.length ? rows : [...varietyOptions.value]
}

async function onCycleCropChange(crop) {
  if (!crop) return
  const cropName = text(crop.name)
  crop.variety = ''
  await ensureTemplatesForCrop(cropName, '')
  crop.templateId = defaultCycleTemplateId(cropName, '')
}

async function onCycleVarietyChange(crop) {
  if (!crop) return
  const varietyName = text(crop.variety)
  if (!varietyName) {
    await ensureTemplatesForCrop(crop.name, '')
    crop.templateId = defaultCycleTemplateId(crop.name, '')
    return
  }
  const hitCrop = resolveCropNameByVariety(varietyName)
  if (hitCrop && hitCrop !== text(crop.name)) {
    crop.name = hitCrop
  }
  await ensureTemplatesForCrop(crop.name, crop.variety)
  crop.templateId = defaultCycleTemplateId(crop.name, crop.variety)
}

function addCycleCrop() {
  cycleDialog.form.crops.push(createCropRow())
}

function removeCycleCrop(idx) {
  if (cycleDialog.form.crops.length <= 1) return
  cycleDialog.form.crops.splice(idx, 1)
}

function buildCycleDialogDefaultCropRow(baseRow) {
  const cropType = text(baseRow && baseRow.cropType)
  const cropVariety = text(baseRow && baseRow.cropVariety)
  if (!cropType && !cropVariety) {
    return createCropRow()
  }
  return createCropRow(cropType, cropVariety, defaultCycleTemplateId(cropType, cropVariety))
}

function ensureCycleDialogCropRowsForPlanMode(baseRow = null) {
  if (cycleDialog.form.planMode === 'fallow') return
  const rows = Array.isArray(cycleDialog.form.crops) ? cycleDialog.form.crops : []
  if (rows.some((item) => text(item && item.name) || text(item && item.variety) || toId(item && item.templateId))) {
    return
  }
  cycleDialog.form.crops = [buildCycleDialogDefaultCropRow(baseRow)]
}

function parseCycleCrops(cropsJson, templateIdsJson) {
  const rows = parseJsonArray(cropsJson).map((item) => {
    const directId = toId(item && item.templateId)
    const listId = toId((Array.isArray(item && item.templateIds) ? item.templateIds : [])[0])
    return createCropRow(
      text(item && item.name),
      text(item && item.variety),
      directId || listId || null
    )
  })
  if (rows.length) return rows
  const templateIds = parseJsonArray(templateIdsJson).map((x) => toId(x)).filter(Boolean)
  if (!templateIds.length) return [createCropRow()]
  return templateIds.map((id) => createCropRow('', '', id))
}

function fieldPlanCount(fieldId) {
  const key = String(fieldId || '')
  const row = fieldPlanMeta.value[key]
  return row ? Number(row.count || 0) : 0
}

function fieldPlanPreview(fieldId) {
  const key = String(fieldId || '')
  const row = fieldPlanMeta.value[key]
  return row && Array.isArray(row.preview) ? row.preview : []
}

function openFieldDetail(row) {
  const fieldId = toId(row && (row.fieldId || row.id))
  if (!fieldId) return
  router.push({
    path: `/field-cycles/field/${fieldId}`,
    query: {
      fieldName: String((row && (row.fieldName || row.name)) || '').trim() || undefined
    }
  })
}

async function loadMetaOptions() {
  try {
    const [townships, treeRows] = await Promise.all([
      request.get('/meta/options/townships'),
      request.get('/meta/options/crop-tree')
    ])
    townshipOptions.value = toUnique(townships)
    const rows = Array.isArray(treeRows) ? treeRows : []
    const crops = []
    const varieties = []
    const grouped = []
    clearObject(cropToVarieties)
    cropNameToCategoryId.value = new Map()
    varietyNameToCropName.value = new Map()
    varietyNameToCategoryId.value = new Map()
    varietyNameToId.value = new Map()
    rows.forEach((item) => {
      const categoryName = text(item && item.categoryName)
      const categoryId = toId(item && item.categoryId)
      if (!categoryName) return
      crops.push(categoryName)
      if (categoryId) cropNameToCategoryId.value.set(categoryName, categoryId)
      const child = Array.isArray(item && item.varieties) ? item.varieties : []
      const names = []
      child.forEach((node) => {
        const name = text(node && node.name)
        const id = toId(node && node.id)
        if (!name) return
        names.push(name)
        varieties.push(name)
        if (!varietyNameToCropName.value.has(name)) varietyNameToCropName.value.set(name, categoryName)
        if (!varietyNameToCategoryId.value.has(name) && categoryId) varietyNameToCategoryId.value.set(name, categoryId)
        if (!varietyNameToId.value.has(name) && id) varietyNameToId.value.set(name, id)
      })
      const uniqueNames = toUnique(names)
      cropToVarieties[categoryName] = uniqueNames
      const options = uniqueNames.map((name) => ({
        label: `${categoryName}·${name}`,
        value: makeVarietyFilterValue(categoryName, name),
        cropType: categoryName,
        cropVariety: name
      }))
      if (options.length) {
        options.unshift({
          label: `${categoryName}`,
          value: makeVarietyFilterValue(categoryName, ''),
          cropType: categoryName,
          cropVariety: ''
        })
        grouped.push({
          cropType: categoryName,
          options
        })
      }
    })
    cropOptions.value = toUnique(crops)
    varietyOptions.value = toUnique(varieties)
    varietyGroupOptions.value = grouped
  } catch (error) {
    townshipOptions.value = []
    cropOptions.value = []
    varietyOptions.value = []
    varietyGroupOptions.value = []
    clearObject(cropToVarieties)
  }
}

async function loadCyclesByFieldIds(fieldIds) {
  const ids = Array.from(new Set((Array.isArray(fieldIds) ? fieldIds : []).map((x) => toId(x)).filter(Boolean)))
  if (!ids.length) return {}
  try {
    const data = await request.get('/fields/cycles/by-fields', {
      params: {
        fieldIds: ids.join(',')
      }
    })
    const payload = data && typeof data === 'object' ? data : {}
    return ids.reduce((out, id) => {
      out[id] = Array.isArray(payload[id]) ? payload[id] : []
      return out
    }, {})
  } catch (error) {
    return ids.reduce((out, id) => {
      out[id] = []
      return out
    }, {})
  }
}

async function loadFieldCards(nextPage = fieldPage.value) {
  loadingFields.value = true
  try {
    fieldPage.value = Number(nextPage || 1)
    const varietyFilter = parseVarietyFilterValue(filters.cropFilterKey)
    const data = await request.get('/fields', {
      params: cleanParams({
        page: fieldPage.value,
        pageSize: fieldPageSize.value,
        keyword: filters.keyword,
        township: filters.township,
        cropType: varietyFilter.cropType || null,
        cropVariety: varietyFilter.cropVariety || null,
        status: filters.status,
        includeDisabled: true
      })
    })
    fieldRows.value = (data && data.records) || []
    fieldTotal.value = Number((data && data.total) || 0)
    const fieldIds = fieldRows.value.map((field) => toId(field && field.id)).filter(Boolean)
    const cycleMap = await loadCyclesByFieldIds(fieldIds)
    const result = {}
    fieldRows.value.forEach((field) => {
      const key = toId(field && field.id)
      if (!key) return
      const plans = Array.isArray(cycleMap[key]) ? cycleMap[key] : []
      result[key] = {
        count: plans.length,
        preview: plans.slice(0, 4)
      }
    })
    fieldPlanMeta.value = result
  } catch (error) {
    ElMessage.error(error.message || '田块计划加载失败')
  } finally {
    loadingFields.value = false
  }
}

async function loadAllPlans(nextPage = allPage.value) {
  loadingAll.value = true
  try {
    allPage.value = Number(nextPage || 1)
    const varietyFilter = parseVarietyFilterValue(filters.cropFilterKey)
    const data = await request.get('/fields/cycles/all', {
      params: cleanParams({
        page: allPage.value,
        pageSize: allPageSize.value,
        keyword: filters.keyword,
        township: filters.township,
        status: filters.status,
        planMode: filters.planMode,
        cropType: varietyFilter.cropType || null,
        cropVariety: varietyFilter.cropVariety || null
      })
    })
    allRows.value = (data && data.records) || []
    allTotal.value = Number((data && data.total) || 0)
    clearAllSelection()
  } catch (error) {
    allRows.value = []
    allTotal.value = 0
    clearAllSelection()
    ElMessage.error(error.message || '全部计划加载失败')
  } finally {
    loadingAll.value = false
  }
}

function runQuery() {
  if (mode.value === 'field') {
    loadFieldCards(1)
    return
  }
  loadAllPlans(1)
}

function onFieldPageSizeChange(size) {
  fieldPageSize.value = Number(size || 12)
  loadFieldCards(1)
}

function onAllPageSizeChange(size) {
  allPageSize.value = Number(size || 10)
  loadAllPlans(1)
}

async function openEditCycle(row) {
  const fieldId = toId(row && row.fieldId)
  const cycleId = toId(row && row.id)
  if (!fieldId || !cycleId) return
  cycleDialog.fieldName = row.fieldName || `田块#${fieldId}`
  cycleDialog.form.id = cycleId
  cycleDialog.form.fieldId = fieldId
  cycleDialog.form.cycleName = row.cycleName || ''
  cycleDialog.form.planMode = row.planMode || 'single'
  cycleDialog.form.status = row.status || 'active'
  cycleDialog.form.startDate = row.startDate || ''
  cycleDialog.form.endDate = row.endDate || ''
  cycleDialog.form.isCurrent = Number(row.isCurrent) === 1
  cycleDialog.form.crops = parseCycleCrops(row.cropsJson, row.templateIdsJson)
  await ensureTemplatesForCrops(cycleDialog.form.crops)
  cycleDialog.form.crops = cycleDialog.form.crops.map((item) => {
    const currentTemplateId = toId(item && item.templateId)
    if (currentTemplateId) return item
    return {
      ...item,
      templateId: defaultCycleTemplateId(text(item && item.name), text(item && item.variety))
    }
  })
  ensureCycleDialogCropRowsForPlanMode(row)
  cycleDialogVisible.value = true
}

async function saveCycle() {
  const fieldId = toId(cycleDialog.form.fieldId)
  const cycleId = toId(cycleDialog.form.id)
  if (!fieldId || !cycleId) return
  if (!text(cycleDialog.form.cycleName)) {
    ElMessage.warning('请填写计划名称')
    return
  }
  const planMode = cycleDialog.form.planMode || 'single'
  const crops = (Array.isArray(cycleDialog.form.crops) ? cycleDialog.form.crops : [])
    .map((item) => {
      const variety = text(item && item.variety)
      let name = text(item && item.name)
      if (!name && variety) name = resolveCropNameByVariety(variety)
      const selectedTemplateId = toId(item && item.templateId)
      const defaultTemplateId = toId(defaultCycleTemplateId(name, variety))
      return {
        name,
        variety,
        templateId: selectedTemplateId || defaultTemplateId
      }
    })
    .filter((item) => item.name || item.variety || item.templateId)
  if (planMode !== 'fallow' && !crops.length) {
    ElMessage.warning('请至少添加一条作物记录')
    return
  }
  const finalCrops = planMode === 'fallow' ? [] : crops.filter((item) => item.name)
  if (planMode !== 'fallow' && !finalCrops.length) {
    ElMessage.warning('作物必须包含名称')
    return
  }
  if (planMode !== 'fallow' && finalCrops.some((item) => !toId(item.templateId))) {
    ElMessage.warning('每个作物都需要选择流程模板')
    return
  }
  const cropsPayload = finalCrops.map((item) => ({
    name: item.name,
    variety: item.variety || '',
    templateId: toId(item.templateId),
    templateIds: toId(item.templateId) ? [toId(item.templateId)] : []
  }))
  const templateIds = Array.from(new Set(cropsPayload.map((x) => toId(x.templateId)).filter(Boolean)))

  savingCycle.value = true
  try {
    await request.put(`/fields/${fieldId}/cycles/${cycleId}`, {
      cycleName: cycleDialog.form.cycleName.trim(),
      planMode,
      status: cycleDialog.form.status || 'active',
      startDate: cycleDialog.form.startDate || null,
      endDate: cycleDialog.form.endDate || null,
      isCurrent: !!cycleDialog.form.isCurrent,
      cropsJson: cropsPayload.length ? JSON.stringify(cropsPayload) : null,
      templateIdsJson: templateIds.length ? JSON.stringify(templateIds) : null
    })
    ElMessage.success('计划更新成功')
    cycleDialogVisible.value = false
    await Promise.all([loadAllPlans(allPage.value), loadFieldCards(fieldPage.value)])
  } catch (error) {
    ElMessage.error(error.message || '计划更新失败')
  } finally {
    savingCycle.value = false
  }
}

async function setCurrentByIds(fieldId, cycleId) {
  await request.put(`/fields/${fieldId}/cycles/${cycleId}/current`)
}

async function setCurrent(row) {
  const fieldId = toId(row && row.fieldId)
  const cycleId = toId(row && row.id)
  if (!fieldId || !cycleId) return
  try {
    await setCurrentByIds(fieldId, cycleId)
    ElMessage.success('已切换为当前计划')
    await Promise.all([loadAllPlans(allPage.value), loadFieldCards(fieldPage.value)])
  } catch (error) {
    ElMessage.error(error.message || '切换失败')
  }
}

async function removeCycle(row) {
  const fieldId = toId(row && row.fieldId)
  const cycleId = toId(row && row.id)
  if (!fieldId || !cycleId) return
  const cycleName = text(row && row.cycleName) || `计划#${cycleId}`
  try {
    await ElMessageBox.confirm(`确认删除计划“${cycleName}”吗？`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除确认失败')
    }
    return
  }
  allBatchSubmitting.value = true
  try {
    await request.delete(`/fields/${fieldId}/cycles/${cycleId}`)
    ElMessage.success('计划已删除')
    await Promise.all([loadAllPlans(allPage.value), loadFieldCards(fieldPage.value)])
  } catch (error) {
    ElMessage.error(error.message || '计划删除失败')
  } finally {
    allBatchSubmitting.value = false
  }
}

async function batchSetCurrent() {
  const targets = collectBatchCurrentTargets(allSelectedRows.value)
  if (!targets.length) {
    ElMessage.warning('当前所选中没有可设为当前的计划')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将已选计划按田块去重后共 ${targets.length} 条设为当前吗？`,
      '批量设为当前确认',
      { type: 'warning' }
    )
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量设为当前确认失败')
    }
    return
  }

  allBatchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => setCurrentByIds(toId(row && row.fieldId), toId(row && row.id)))
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await Promise.all([loadAllPlans(allPage.value), loadFieldCards(fieldPage.value)])
    if (failedCount > 0) {
      ElMessage.warning(`批量设为当前完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量设为当前成功：${successCount} 条计划`)
  } catch (error) {
    ElMessage.error(error.message || '批量设为当前失败')
  } finally {
    allBatchSubmitting.value = false
  }
}

async function batchDeleteCycles() {
  const targets = collectBatchDeleteTargets(allSelectedRows.value)
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的计划')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选 ${targets.length} 条计划吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除确认失败')
    }
    return
  }

  allBatchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => {
        const fieldId = toId(row && row.fieldId)
        const cycleId = toId(row && row.id)
        return request.delete(`/fields/${fieldId}/cycles/${cycleId}`)
      })
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await Promise.all([loadAllPlans(allPage.value), loadFieldCards(fieldPage.value)])
    if (failedCount > 0) {
      ElMessage.warning(`批量删除完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量删除成功：${successCount} 条计划`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    allBatchSubmitting.value = false
  }
}

watch(
  () => mode.value,
  (next) => {
    if (next === 'field') {
      allBatchMode.value = false
      clearAllSelection()
      loadFieldCards(fieldPage.value)
      return
    }
    loadAllPlans(allPage.value)
  }
)

watch(
  () => cycleDialog.form.planMode,
  () => {
    ensureCycleDialogCropRowsForPlanMode()
  }
)

onMounted(async () => {
  await loadMetaOptions()
  await loadFieldCards(1)
})
</script>

<style scoped>
.field-board {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  padding: 12px 4px;
  background: var(--bg-panel);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.field-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-panel);
  display: flex;
  flex-direction: column;
}

.field-main {
  padding: 10px;
  cursor: pointer;
}

.field-main:hover {
  background: var(--bg-soft);
}

.field-main-top {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
}

.field-thumb {
  width: 82px;
  height: 82px;
  border-radius: 10px;
}

.field-thumb-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  font-size: 20px;
  border: 1px dashed var(--border);
  background: var(--bg-soft);
}

.field-main-content {
  min-width: 0;
}

.field-title {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.field-state-col {
  margin-top: 6px;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.field-meta {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--text-sub);
  font-size: 12px;
}

.field-crop-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-metrics {
  margin-top: 10px;
  border-top: 1px dashed var(--border);
  padding-top: 8px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.metric-label {
  font-size: 12px;
  color: var(--text-sub);
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
}

.field-foot {
  padding: 0 10px 10px;
  display: flex;
  gap: 8px;
}

.field-foot .el-button {
  flex: 1;
}

.plan-preview-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}

.plan-preview-list {
  display: grid;
  gap: 6px;
}

.plan-preview-item {
  display: grid;
  gap: 2px;
  font-size: 12px;
  color: var(--text-sub);
}

.all-plan-section :deep(.el-card__body) {
  padding-top: 10px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.table-foot {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.plan-crop-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.text-sub {
  color: var(--text-sub);
}

.crop-editor {
  width: 100%;
}

.crop-line {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 90px;
  gap: 8px;
  margin-bottom: 8px;
}

@media (max-width: 1320px) {
  .field-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .field-main-top {
    grid-template-columns: 68px minmax(0, 1fr);
  }

  .field-thumb {
    width: 68px;
    height: 68px;
  }

  .crop-line {
    grid-template-columns: 1fr;
  }
}
</style>

