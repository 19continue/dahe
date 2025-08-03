<template>
  <div class="field-cycle-detail-page">
    <PageToolbar
      :title="`田块计划详情 · ${fieldInfo.name || fieldId}`"
      subtitle="上方查看田块信息，下方逐条管理该田块种植计划与步骤节点。"
      :collapsible="false"
    >
      <template #head-actions>
        <el-button @click="goBack">返回计划总览</el-button>
        <el-button type="primary" @click="openCreateDialog">新建计划</el-button>
      </template>
    </PageToolbar>

    <section class="field-summary" v-loading="loadingField">
      <div class="summary-title">
        <strong>{{ fieldInfo.name || '-' }}</strong>
        <el-tag size="small">阶段：{{ fieldStatusText(fieldInfo.status) }}</el-tag>
      </div>
      <div class="summary-meta">
        <span>面积：{{ areaText(fieldInfo.areaMu) }}</span>
        <span>乡镇：{{ fieldInfo.township || '-' }}</span>
        <span class="summary-crops">
          <span>作物品种：</span>
          <CropPairTags
            :crop-variety-groups="fieldInfo.cropVarietyGroups"
            :crop-type="fieldInfo.cropType"
            :crop-variety="fieldInfo.cropVariety"
            empty-text="未配置"
          />
        </span>
        <span>位置：{{ fieldInfo.formattedAddress || fieldInfo.locationDesc || '-' }}</span>
      </div>
    </section>

    <section class="plan-list" v-loading="loadingCycles">
      <article v-for="plan in pagedCycles" :key="plan.id" class="plan-item">
        <header class="plan-head">
          <div class="plan-main">
            <div class="plan-name">
              <strong>{{ plan.cycleName || '-' }}</strong>
              <el-tag size="small" type="info">{{ planModeText(plan.planMode) }}</el-tag>
              <el-tag size="small" :type="cycleStatusType(plan.status)">{{ cycleStatusText(plan.status) }}</el-tag>
              <el-tag v-if="Number(plan.isCurrent) === 1" size="small" type="success">当前计划</el-tag>
            </div>
            <div class="plan-time">{{ plan.startDate || '-' }} ~ {{ plan.endDate || '-' }}</div>
          </div>
          <div class="plan-actions">
            <el-button size="small" type="primary" @click="openEditDialog(plan)">编辑</el-button>
            <el-button size="small" type="success" :disabled="Number(plan.isCurrent) === 1" @click="setCurrent(plan)">设为当前</el-button>
            <el-button size="small" @click="toggleExpand(plan.id)">{{ isExpanded(plan.id) ? '收起节点' : '展开节点' }}</el-button>
          </div>
        </header>

        <transition name="plan-expand">
          <div v-show="isExpanded(plan.id)" class="plan-body">
            <div class="plan-crops">
              <span class="plan-crops-label">作物组合：</span>
              <div v-if="resolvePlanCropGroups(plan).length" class="plan-crop-switch">
                <button
                  v-for="group in resolvePlanCropGroups(plan)"
                  :key="group.key"
                  type="button"
                  :class="['crop-switch-btn', { active: selectedPlanCropKey(plan) === group.key }]"
                  @click="selectPlanCrop(plan, group.key)"
                >
                  {{ group.label }}
                </button>
              </div>
              <span v-else class="empty-text">{{ isPlanFallow(plan) ? '休耕计划，无需配置作物' : '未配置' }}</span>
            </div>
            <div v-if="isPlanFallow(plan)" class="plan-fallow-tip">当前计划为休耕模式，田块阶段将同步为休耕。</div>
            <div class="step-flow">
              <div v-for="(step, idx) in visibleCycleSteps(plan)" :key="`${plan.id}-${step.id || idx}`" class="step-node">
                <div class="step-dot">{{ idx + 1 }}</div>
                <div class="step-content">
                  <div class="step-name">{{ step.stepName || '-' }}</div>
                  <div class="step-meta">
                    <span>{{ step.templateName || '模板' }}</span>
                    <span>{{ growthStageLabel(step.growthStage) }}</span>
                  </div>
                  <div v-if="step.requirementDesc" class="step-desc">{{ step.requirementDesc }}</div>
                  <div class="step-params">
                    <span class="step-params-label">参数：</span>
                    <el-tag
                      v-for="item in resolveStepSchema(step)"
                      :key="`${step.id || idx}-${item.key || item.label}`"
                      size="small"
                      type="warning"
                      effect="plain"
                    >
                      {{ item.required ? `*${item.label}` : item.label }}
                    </el-tag>
                    <span v-if="!resolveStepSchema(step).length" class="empty-text">无</span>
                  </div>
                </div>
              </div>
              <el-empty
                v-if="!visibleCycleSteps(plan).length"
                :description="isPlanFallow(plan) ? '休耕计划无流程步骤' : '所关联的流程中没有创建任何步骤'"
                :image-size="44"
              />
            </div>
          </div>
        </transition>
      </article>
      <el-empty v-if="!cycles.length" description="该田块暂无种植计划" :image-size="56" />
      <div v-if="cycles.length" class="table-foot plan-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="cycleTotal"
          :page-size="cyclePageSize"
          :current-page="cyclePage"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onCyclePageSizeChange"
          @current-change="onCyclePageChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑种植计划' : '新建种植计划'" width="820px" destroy-on-close>
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="计划名称">
              <el-input v-model="form.cycleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="种植模式">
              <el-select v-model="form.planMode" style="width: 100%">
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
        </el-row>
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="计划状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="进行中" value="active" />
                <el-option label="已结束" value="completed" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设为当前">
              <el-switch v-model="form.isCurrent" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="起止日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
          <span style="padding: 0 10px">至</span>
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
        </el-form-item>
        <el-form-item label="作物组合" v-if="form.planMode !== 'fallow'">
          <div class="crop-editor">
            <div v-for="(crop, idx) in form.crops" :key="crop.uid" class="crop-line">
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
                <el-option v-for="tpl in cycleTemplateOptions(crop)" :key="tpl.id" :label="templateOptionLabel(tpl)" :value="tpl.id" />
              </el-select>
              <el-button size="small" type="danger" @click="removeCrop(idx)" :disabled="form.crops.length <= 1">删除</el-button>
            </div>
            <el-button size="small" type="primary" @click="addCrop">添加作物</el-button>
          </div>
        </el-form-item>
        <el-form-item label="休耕说明" v-else>
          <el-alert type="info" show-icon :closable="false" title="休耕计划无需配置作物与流程模板，保存后田块阶段自动同步为休耕。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CropPairTags from '../components/ui/CropPairTags.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const fieldId = ref(String(route.params.fieldId || '').trim())

const loadingField = ref(false)
const loadingCycles = ref(false)
const saving = ref(false)
const editMode = ref(false)
const dialogVisible = ref(false)

const fieldInfo = reactive({
  id: '',
  name: '',
  areaMu: null,
  status: '',
  township: '',
  cropType: '',
  cropVariety: '',
  cropVarietyGroups: [],
  formattedAddress: '',
  locationDesc: ''
})

const cycles = ref([])
const cyclePage = ref(1)
const cyclePageSize = ref(10)
const expandedMap = ref({})
const planSelectedCropMap = ref({})
const templateOptions = ref([])
const templateLoadingCount = ref(0)
const templateLoading = computed(() => templateLoadingCount.value > 0)
const loadedTemplateScopeKeys = new Set()
const templateScopeTasks = new Map()
const loadedTemplateIds = new Set()
const templateIdTasks = new Map()
const cropOptions = ref([])
const varietyOptions = ref([])
const cropToVarieties = reactive({})
const varietyNameToCropName = ref(new Map())
const varietyNameToCategoryId = ref(new Map())
const varietyNameToId = ref(new Map())
const cropNameToCategoryId = ref(new Map())
let cropUidSeed = 1

const form = reactive({
  id: null,
  cycleName: '',
  planMode: 'single',
  status: 'active',
  startDate: '',
  endDate: '',
  isCurrent: true,
  crops: [createCropRow()]
})

const cycleTotal = computed(() => Number((cycles.value && cycles.value.length) || 0))
const pagedCycles = computed(() => {
  const pageNo = Math.max(1, Number(cyclePage.value || 1))
  const size = Math.max(1, Number(cyclePageSize.value || 10))
  const start = (pageNo - 1) * size
  return (Array.isArray(cycles.value) ? cycles.value : []).slice(start, start + size)
})

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

function cycleStatusType(status) {
  return String(status || '').toLowerCase() === 'completed' ? 'success' : 'warning'
}

function cycleStatusText(status) {
  return String(status || '').toLowerCase() === 'completed' ? '已结束' : '进行中'
}

function growthStageLabel(value) {
  const map = {
    sowing: '播种阶段',
    growing: '生长阶段',
    harvesting: '收获阶段'
  }
  return map[String(value || '').toLowerCase()] || '通用阶段'
}

function areaText(value) {
  const num = Number(value || 0)
  return Number.isFinite(num) && num > 0 ? `${num.toFixed(2)} 亩` : '-'
}

function formatCropVarietyPair(row) {
  const cropType = text(row && row.cropType)
  const cropVariety = text(row && row.cropVariety)
  if (cropType && cropVariety) return `${cropType} + ${cropVariety}`
  return cropType || cropVariety || '-'
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

function createCropRow(name = '', variety = '', templateId = null) {
  return {
    uid: cropUidSeed++,
    name,
    variety,
    templateId
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
      rows.forEach((item) => {
        const id = toId(item && item.id)
        if (id) loadedTemplateIds.add(id)
      })
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

function collectTemplateIdsFromPlan(plan) {
  const ids = []
  const crops = parseJsonArray(plan && plan.cropsJson)
  crops.forEach((item) => {
    const id = toId(item && item.templateId) || toId((Array.isArray(item && item.templateIds) ? item.templateIds : [])[0])
    if (id) ids.push(id)
  })
  parseJsonArray(plan && plan.templateIdsJson).forEach((id) => {
    const normalized = toId(id)
    if (normalized) ids.push(normalized)
  })
  return Array.from(new Set(ids))
}

async function ensureTemplateById(templateId) {
  const id = toId(templateId)
  if (!id) return
  if (loadedTemplateIds.has(id)) return
  if (templateIdTasks.has(id)) {
    await templateIdTasks.get(id)
    return
  }
  const task = (async () => {
    templateLoadingCount.value += 1
    try {
      const row = await request.get(`/farm-process/templates/${id}`, {
        params: { includeSteps: true }
      })
      if (row && row.id) {
        mergeTemplateOptions([row])
        loadedTemplateIds.add(id)
      }
    } catch (error) {
      // ignore single template preload error to keep page usable
    } finally {
      templateLoadingCount.value = Math.max(0, templateLoadingCount.value - 1)
    }
  })()
  templateIdTasks.set(id, task)
  try {
    await task
  } finally {
    templateIdTasks.delete(id)
  }
}

async function ensureTemplatesForPlans(plansInput) {
  const ids = Array.from(new Set(
    (Array.isArray(plansInput) ? plansInput : [])
      .flatMap((plan) => collectTemplateIdsFromPlan(plan))
      .map((id) => toId(id))
      .filter(Boolean)
  ))
  if (!ids.length) return
  await Promise.all(ids.map((id) => ensureTemplateById(id)))
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
  if (hitCrop && hitCrop !== text(crop.name)) crop.name = hitCrop
  await ensureTemplatesForCrop(crop.name, crop.variety)
  crop.templateId = defaultCycleTemplateId(crop.name, crop.variety)
}

function addCrop() {
  form.crops.push(createCropRow())
}

function removeCrop(idx) {
  if (form.crops.length <= 1) return
  form.crops.splice(idx, 1)
}

function templateOptionLabel(tpl) {
  const categoryName = text(tpl && (tpl.categoryName || tpl.cropName)) || '作物'
  const varietyName = text(tpl && tpl.varietyName)
  const bindText = varietyName ? ` · ${categoryName} · ${varietyName}` : ` · ${categoryName}（通用）`
  const defaultText = Number(tpl.isDefault) === 1 ? ' · 默认' : ''
  return `${text(tpl && tpl.templateName) || '-'}${bindText}${defaultText}`
}

function isPlanFallow(plan) {
  return text(plan && plan.planMode).toLowerCase() === 'fallow'
}

function normalizePlanCropLabel(name, variety, fallback = '-') {
  const cropName = text(name)
  const varietyName = text(variety)
  if (cropName && varietyName) return `${cropName} + ${varietyName}`
  return cropName || varietyName || fallback
}

function sortStepRows(rows) {
  return [...(Array.isArray(rows) ? rows : [])].sort((a, b) => {
    const sa = Number((a && a.sortOrder) || 0)
    const sb = Number((b && b.sortOrder) || 0)
    if (sa !== sb) return sa - sb
    return String((a && a.id) || '').localeCompare(String((b && b.id) || ''))
  })
}

function resolveStepSchema(step) {
  if (!step || !step.formSchema) return []
  try {
    const rows = JSON.parse(step.formSchema)
    if (!Array.isArray(rows)) return []
    return rows
      .map((item, idx) => {
        const key = text(item && item.key) || `field_${idx}`
        const label = text(item && item.label) || key
        return {
          key,
          label,
          required: !!(item && item.required)
        }
      })
      .filter((item) => !!item.label)
  } catch (error) {
    return []
  }
}

function resolveCycleTemplateById(id) {
  if (!id) return null
  return (templateOptions.value || []).find((item) => toId(item && item.id) === id) || null
}

function resolvePlanCropGroups(plan) {
  if (!plan || isPlanFallow(plan)) return []
  const crops = parseJsonArray(plan.cropsJson)
  const groups = []
  const used = new Set()
  crops.forEach((item, idx) => {
    const name = text(item && item.name)
    const variety = text(item && item.variety)
    const templateId = toId(item && item.templateId) || toId((Array.isArray(item && item.templateIds) ? item.templateIds : [])[0])
    const tpl = resolveCycleTemplateById(templateId)
    const steps = sortStepRows(Array.isArray(tpl && tpl.steps) ? tpl.steps : []).map((step) => ({
      ...step,
      templateName: tpl && tpl.templateName
    }))
    const key = `crop-${idx}-${name}-${variety}-${templateId || 'none'}`
    groups.push({
      key,
      label: normalizePlanCropLabel(name, variety, tpl ? `${tpl.templateName || '模板'}节点` : `作物分组${idx + 1}`),
      templateId,
      steps
    })
    used.add(key)
  })
  if (groups.length) return groups

  const templateIds = Array.from(new Set(parseJsonArray(plan.templateIdsJson).map((x) => toId(x)).filter(Boolean)))
  templateIds.forEach((templateId, idx) => {
    const tpl = resolveCycleTemplateById(templateId)
    const steps = sortStepRows(Array.isArray(tpl && tpl.steps) ? tpl.steps : []).map((step) => ({
      ...step,
      templateName: tpl && tpl.templateName
    }))
    const key = `tpl-${idx}-${templateId}`
    groups.push({
      key,
      label: text(tpl && tpl.templateName) || `模板#${templateId}`,
      templateId,
      steps
    })
  })
  return groups
}

function selectedPlanCropKey(plan) {
  const planId = toId(plan && plan.id)
  if (!planId) return ''
  const groups = resolvePlanCropGroups(plan)
  if (!groups.length) {
    if (planSelectedCropMap.value[String(planId)]) {
      planSelectedCropMap.value = {
        ...planSelectedCropMap.value,
        [String(planId)]: ''
      }
    }
    return ''
  }
  const existing = text(planSelectedCropMap.value[String(planId)])
  if (existing && groups.some((item) => item.key === existing)) {
    return existing
  }
  const fallback = groups[0].key
  planSelectedCropMap.value = {
    ...planSelectedCropMap.value,
    [String(planId)]: fallback
  }
  return fallback
}

function selectPlanCrop(plan, key) {
  const planId = toId(plan && plan.id)
  const nextKey = text(key)
  if (!planId || !nextKey) return
  planSelectedCropMap.value = {
    ...planSelectedCropMap.value,
    [String(planId)]: nextKey
  }
}

function visibleCycleSteps(plan) {
  if (isPlanFallow(plan)) return []
  const groups = resolvePlanCropGroups(plan)
  if (!groups.length) return []
  const key = selectedPlanCropKey(plan)
  const hit = groups.find((item) => item.key === key) || groups[0]
  return Array.isArray(hit && hit.steps) ? hit.steps : []
}

function resolveCycleSteps(plan) {
  const groups = resolvePlanCropGroups(plan)
  if (!groups.length) return []
  const rows = []
  groups.forEach((group) => {
    ;(group.steps || []).forEach((step) => rows.push(step))
  })
  return sortStepRows(rows)
}

function parseCycleFormFromPlan(plan) {
  const rows = parseJsonArray(plan && plan.cropsJson).map((item) => {
    const direct = toId(item && item.templateId)
    const fallback = toId((Array.isArray(item && item.templateIds) ? item.templateIds : [])[0])
    return createCropRow(text(item && item.name), text(item && item.variety), direct || fallback || null)
  })
  return rows.length ? rows : [createCropRow()]
}

function isExpanded(planId) {
  return !!expandedMap.value[String(planId || '')]
}

function toggleExpand(planId) {
  const key = String(planId || '')
  expandedMap.value = {
    ...expandedMap.value,
    [key]: !expandedMap.value[key]
  }
}

function goBack() {
  router.push('/field-cycles')
}

async function loadMetaOptions() {
  try {
    const treeRows = await request.get('/meta/options/crop-tree')
    const rows = Array.isArray(treeRows) ? treeRows : []
    const crops = []
    const varieties = []
    Object.keys(cropToVarieties).forEach((k) => delete cropToVarieties[k])
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
      cropToVarieties[categoryName] = Array.from(new Set(names))
    })
    cropOptions.value = Array.from(new Set(crops))
    varietyOptions.value = Array.from(new Set(varieties))
  } catch (error) {
    cropOptions.value = []
    varietyOptions.value = []
  }
}

async function loadFieldInfo() {
  if (!fieldId.value) return
  loadingField.value = true
  try {
    const data = await request.get(`/fields/${fieldId.value}`)
    fieldInfo.id = toId(data && data.id) || ''
    fieldInfo.name = (data && data.name) || ''
    fieldInfo.areaMu = (data && data.areaMu) || null
    fieldInfo.status = (data && data.status) || ''
    fieldInfo.township = (data && data.township) || ''
    fieldInfo.cropType = (data && data.cropType) || ''
    fieldInfo.cropVariety = (data && data.cropVariety) || ''
    fieldInfo.cropVarietyGroups = Array.isArray(data && data.cropVarietyGroups) ? data.cropVarietyGroups : []
    fieldInfo.formattedAddress = (data && data.formattedAddress) || ''
    fieldInfo.locationDesc = (data && data.locationDesc) || ''
  } catch (error) {
    ElMessage.error(error.message || '田块信息加载失败')
  } finally {
    loadingField.value = false
  }
}

async function loadCycles() {
  if (!fieldId.value) return
  loadingCycles.value = true
  try {
    const data = await request.get(`/fields/${fieldId.value}/cycles`)
    cycles.value = Array.isArray(data) ? data : []
    await ensureTemplatesForPlans(cycles.value)
    cyclePage.value = 1
    const nextExpand = {}
    cycles.value.forEach((row, idx) => {
      nextExpand[String(row.id)] = idx === 0
    })
    expandedMap.value = nextExpand
    planSelectedCropMap.value = {}
  } catch (error) {
    cycles.value = []
    ElMessage.error(error.message || '种植计划加载失败')
  } finally {
    loadingCycles.value = false
  }
}

function onCyclePageSizeChange(size) {
  cyclePageSize.value = Number(size || 10)
  cyclePage.value = 1
}

function onCyclePageChange(nextPage) {
  cyclePage.value = Number(nextPage || 1)
}

function resetForm() {
  form.id = null
  form.cycleName = ''
  form.planMode = 'single'
  form.status = 'active'
  form.startDate = ''
  form.endDate = ''
  form.isCurrent = true
  form.crops = [createCropRow()]
}

function resolveFieldPrimaryCropPair() {
  const groups = Array.isArray(fieldInfo.cropVarietyGroups) ? fieldInfo.cropVarietyGroups : []
  if (groups.length) {
    const first = groups[0] || {}
    return {
      cropType: text(first.cropType || first.cropName || first.name),
      cropVariety: text(first.cropVariety || first.varietyName || first.variety)
    }
  }
  return {
    cropType: text(fieldInfo.cropType),
    cropVariety: text(fieldInfo.cropVariety)
  }
}

function buildFieldDefaultCropRow() {
  const primary = resolveFieldPrimaryCropPair()
  let cropType = primary.cropType
  const cropVariety = primary.cropVariety
  if (!cropType && cropVariety) {
    cropType = resolveCropNameByVariety(cropVariety)
  }
  if (!cropType && !cropVariety) {
    return createCropRow()
  }
  return createCropRow(cropType, cropVariety, defaultCycleTemplateId(cropType, cropVariety))
}

function ensureCycleCropRowsForPlanMode() {
  if (form.planMode === 'fallow') return
  const rows = Array.isArray(form.crops) ? form.crops : []
  if (rows.some((item) => text(item && item.name) || text(item && item.variety) || toId(item && item.templateId))) {
    return
  }
  form.crops = [buildFieldDefaultCropRow()]
}

async function openCreateDialog() {
  resetForm()
  const primary = resolveFieldPrimaryCropPair()
  await ensureTemplatesForCrop(primary.cropType, primary.cropVariety)
  ensureCycleCropRowsForPlanMode()
  form.crops = (Array.isArray(form.crops) ? form.crops : []).map((item) => {
    const currentTemplateId = toId(item && item.templateId)
    if (currentTemplateId) return item
    return {
      ...item,
      templateId: defaultCycleTemplateId(text(item && item.name), text(item && item.variety))
    }
  })
  editMode.value = false
  dialogVisible.value = true
}

async function openEditDialog(plan) {
  form.id = toId(plan && plan.id)
  form.cycleName = (plan && plan.cycleName) || ''
  form.planMode = (plan && plan.planMode) || 'single'
  form.status = (plan && plan.status) || 'active'
  form.startDate = (plan && plan.startDate) || ''
  form.endDate = (plan && plan.endDate) || ''
  form.isCurrent = Number(plan && plan.isCurrent) === 1
  form.crops = parseCycleFormFromPlan(plan)
  await Promise.all([
    ensureTemplatesForCrops(form.crops),
    ensureTemplatesForPlans([plan])
  ])
  form.crops = (Array.isArray(form.crops) ? form.crops : []).map((item) => {
    const currentTemplateId = toId(item && item.templateId)
    if (currentTemplateId) return item
    return {
      ...item,
      templateId: defaultCycleTemplateId(text(item && item.name), text(item && item.variety))
    }
  })
  ensureCycleCropRowsForPlanMode()
  editMode.value = true
  dialogVisible.value = true
}

async function savePlan() {
  if (!fieldId.value) return
  if (!text(form.cycleName)) {
    ElMessage.warning('请填写计划名称')
    return
  }
  const planMode = form.planMode || 'single'
  const crops = (Array.isArray(form.crops) ? form.crops : [])
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
  saving.value = true
  try {
    const payload = {
      cycleName: form.cycleName.trim(),
      planMode,
      status: form.status || 'active',
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      isCurrent: !!form.isCurrent,
      cropsJson: cropsPayload.length ? JSON.stringify(cropsPayload) : null,
      templateIdsJson: templateIds.length ? JSON.stringify(templateIds) : null
    }
    if (editMode.value && form.id) {
      await request.put(`/fields/${fieldId.value}/cycles/${form.id}`, payload)
    } else {
      await request.post(`/fields/${fieldId.value}/cycles`, payload)
    }
    ElMessage.success('计划保存成功')
    dialogVisible.value = false
    await Promise.all([loadFieldInfo(), loadCycles()])
  } catch (error) {
    ElMessage.error(error.message || '计划保存失败')
  } finally {
    saving.value = false
  }
}

async function setCurrent(plan) {
  const cycleId = toId(plan && plan.id)
  if (!cycleId || !fieldId.value) return
  try {
    await request.put(`/fields/${fieldId.value}/cycles/${cycleId}/current`)
    ElMessage.success('已切换为当前计划')
    await Promise.all([loadFieldInfo(), loadCycles()])
  } catch (error) {
    ElMessage.error(error.message || '切换失败')
  }
}

watch(
  () => route.params.fieldId,
  async (next) => {
    fieldId.value = String(next || '').trim()
    await Promise.all([loadFieldInfo(), loadCycles()])
  }
)

watch(
  () => form.planMode,
  () => {
    ensureCycleCropRowsForPlanMode()
  }
)

onMounted(async () => {
  await loadMetaOptions()
  await Promise.all([loadFieldInfo(), loadCycles()])
})
</script>

<style scoped>
.field-summary {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  padding: 12px 8px;
  background: var(--bg-panel);
  margin-bottom: 12px;
}

.summary-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--text-sub);
  font-size: 13px;
}

.summary-crops {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.plan-list {
  display: grid;
  gap: 10px;
}

.plan-foot {
  margin-top: 2px;
  display: flex;
  justify-content: flex-end;
}

.plan-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-panel);
}

.plan-head {
  min-height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
}

.plan-main {
  min-width: 0;
}

.plan-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.plan-time {
  margin-top: 5px;
  color: var(--text-sub);
  font-size: 12px;
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.plan-body {
  border-top: 1px dashed var(--border);
  padding: 10px 12px 12px;
}

.plan-crops {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.plan-crop-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.crop-switch-btn {
  border: 1px solid var(--border);
  background: var(--bg-soft);
  color: var(--text-sub);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}

.crop-switch-btn.active {
  color: var(--primary);
  border-color: color-mix(in srgb, var(--primary) 48%, var(--border));
  background: color-mix(in srgb, var(--primary-soft) 72%, #ffffff 28%);
  font-weight: 600;
}

.plan-crops-label {
  color: var(--text-sub);
  font-size: 12px;
  line-height: 28px;
}

.empty-text {
  color: var(--text-sub);
  font-size: 12px;
}

.plan-fallow-tip {
  border: 1px dashed #f5c579;
  background: #fff8e8;
  color: #875a13;
  border-radius: 8px;
  font-size: 12px;
  padding: 8px 10px;
  margin-bottom: 10px;
}

.step-flow {
  display: grid;
  gap: 10px;
}

.step-node {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
}

.step-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-content {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  background: var(--bg-soft);
}

.step-name {
  font-weight: 700;
  color: var(--text-main);
}

.step-meta {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-sub);
  font-size: 12px;
}

.step-desc {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.6;
}

.step-params {
  margin-top: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.step-params-label {
  color: var(--text-sub);
  font-size: 12px;
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

.plan-expand-enter-active,
.plan-expand-leave-active {
  transition: opacity 0.2s ease, max-height 0.24s ease;
  overflow: hidden;
  max-height: 720px;
}

.plan-expand-enter-from,
.plan-expand-leave-to {
  opacity: 0;
  max-height: 0;
}

@media (max-width: 1080px) {
  .plan-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .crop-line {
    grid-template-columns: 1fr;
  }
}
</style>

