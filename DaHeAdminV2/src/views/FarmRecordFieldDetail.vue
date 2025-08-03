<template>
  <div class="farm-record-detail-page">
    <PageToolbar
      :title="`田块农事记录 · ${fieldInfo.name || fieldId}`"
      subtitle="单田块农事记录独立管理页：上方田块信息，下方按记录条目管理。"
      :collapsible="false"
    >
      <template #head-actions>
        <el-button @click="goBack">返回记录总览</el-button>
        <el-button @click="refreshAll">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增记录</el-button>
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
        <span>地址：{{ fieldInfo.formattedAddress || fieldInfo.locationDesc || '-' }}</span>
      </div>
    </section>

    <section class="filters-strip">
      <div class="actions">
        <el-select v-model="filters.cycleId" clearable filterable placeholder="种植计划" style="width: 260px">
          <el-option label="全部计划" value="" />
          <el-option v-for="item in cycles" :key="item.idKey" :label="item.displayName" :value="item.idKey">
            <div class="cycle-option">
              <span class="cycle-option-name">{{ item.displayName }}</span>
              <el-tag v-if="Number(item.isCurrent) === 1" size="small" type="success">当前</el-tag>
              <el-tag size="small" :type="cycleStatusTagType(item.status)">{{ cycleStatusLabel(item.status) }}</el-tag>
            </div>
          </el-option>
        </el-select>
        <el-date-picker
          v-model="filters.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
        <el-button @click="loadRecords(1)">查询</el-button>
      </div>
    </section>

    <section class="record-list" v-loading="loadingRecords">
      <article v-for="(row, rowIndex) in records" :key="row.id" class="record-item">
        <div class="record-timeline">
          <span class="record-dot"></span>
          <span v-if="rowIndex < records.length - 1" class="record-line"></span>
        </div>
        <div class="record-panel">
          <header class="record-head">
            <div class="record-main">
              <div class="record-title">
                <strong>{{ recordTitle(row) }}</strong>
                <el-tag size="small" type="info">{{ cycleDisplayNameById(row.cycleId) }}</el-tag>
                <el-tag v-if="Number(row.cycleIsCurrent) === 1" size="small" type="success">当前</el-tag>
                <el-tag
                  v-if="row.cycleStatusLabel"
                  size="small"
                  :type="row.cycleStatusTagType"
                >
                  {{ row.cycleStatusLabel }}
                </el-tag>
                <el-tag size="small">{{ normalizeDateTime(row.workDate) }}</el-tag>
              </div>
              <div class="record-meta">
                <span>
                  操作员：{{ row.operatorName || '-' }}
                  <el-button v-if="toId(row.operatorUserId)" link type="primary" @click="openOperatorDetail(row)">查看</el-button>
                </span>
                <span>天气：{{ formatWeatherSummary(row) }}</span>
                <span>天气发布时间：{{ row.weatherReportTime || '-' }}</span>
                <span>位置：{{ row.weatherLocation || '-' }}</span>
              </div>
              <div class="record-note">备注：{{ row.notes || '无' }}</div>
              <div class="record-note">
                步骤参数：
                <span v-if="!(row.extraParamEntries || []).length">-</span>
                <span v-else class="record-param-tags">
                  <el-tag
                    v-for="item in row.extraParamEntries"
                    :key="`${row.id || 'row'}-${item.key}`"
                    size="small"
                    effect="plain"
                  >
                    {{ item.displayLabel }}:{{ item.value }}
                  </el-tag>
                </span>
              </div>
            </div>
            <div class="record-actions">
              <el-button size="small" type="primary" :disabled="row.canEdit === false" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" plain :disabled="row.canDelete === false" @click="removeRecord(row)">删除</el-button>
            </div>
          </header>
        </div>
      </article>

      <el-empty v-if="!records.length" description="当前筛选下暂无记录" :image-size="56" />

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onPageSizeChange"
          @current-change="loadRecords"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editMode ? '编辑农事记录' : '新增农事记录'"
      width="820px"
      class="form-dialog"
      align-center
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="种植计划">
              <el-select v-model="form.cycleId" clearable filterable placeholder="可选" style="width: 100%" @change="onCycleChange">
                <el-option label="不指定计划" value="" />
                <el-option v-for="item in cycles" :key="item.idKey" :label="item.displayName" :value="item.idKey">
                  <div class="cycle-option">
                    <span class="cycle-option-name">{{ item.displayName }}</span>
                    <el-tag v-if="Number(item.isCurrent) === 1" size="small" type="success">当前</el-tag>
                    <el-tag size="small" :type="cycleStatusTagType(item.status)">{{ cycleStatusLabel(item.status) }}</el-tag>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="流程步骤">
          <ProcessStepSelector
            v-model="form.stepId"
            v-model:segment-key="selectedSegmentKey"
            :segments="processSegments"
            :steps="processState.steps"
            empty-text="当前计划没有可选步骤，请先在流程模板中配置步骤。"
            @change="onStepChanged"
          />
        </el-form-item>

        <el-form-item label="步骤参数" v-if="dynamicFields.length">
          <div class="dynamic-editor">
            <div v-for="item in dynamicFields" :key="item.key" class="dynamic-item">
              <div class="dynamic-label">{{ item.label }}<span v-if="item.required" class="required">*</span></div>
              <el-select
                v-if="item.type === 'select'"
                :model-value="dynamicValues[item.key] || ''"
                placeholder="请选择"
                clearable
                @update:model-value="updateDynamicValue(item.key, $event)"
              >
                <el-option v-for="opt in resolveDynamicOptions(item)" :key="`${item.key}-${opt.value}`" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-input
                v-else-if="item.type === 'textarea'"
                :model-value="dynamicValues[item.key] || ''"
                type="textarea"
                :rows="2"
                :placeholder="item.placeholder || `请输入${item.label}`"
                @update:model-value="updateDynamicValue(item.key, $event)"
              />
              <el-date-picker
                v-else-if="item.type === 'date'"
                :model-value="dynamicValues[item.key] || ''"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                @update:model-value="updateDynamicValue(item.key, $event)"
              />
              <el-input
                v-else
                :model-value="dynamicValues[item.key] || ''"
                :placeholder="item.placeholder || `请输入${item.label}`"
                @update:model-value="updateDynamicValue(item.key, $event)"
              />
            </div>
          </div>
        </el-form-item>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="作业时间">
              <el-date-picker
                v-model="form.workDate"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作员">
              <div class="operator-editor">
                <OperatorUserSelector
                  v-model="form.operatorUserId"
                  :model-label="form.operatorName"
                  placeholder="按姓名/昵称/手机号检索操作员"
                  :fetcher="fetchOperatorOptions"
                  @change="onOperatorPicked"
                />
                <el-input v-model="form.operatorName" placeholder="也可手动输入操作员姓名" />
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="6">
            <el-form-item label="天气">
              <el-input v-model="form.weather" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="温度">
              <el-input v-model="form.temperature" placeholder="例如 25℃" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="湿度(%)">
              <el-input v-model="form.humidity" placeholder="例如 68" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="风力">
              <el-input v-model="form.windPower" placeholder="例如 3级" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="风向">
              <el-input v-model="form.windDirection" placeholder="例如 北风" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="天气发布时间">
              <el-input v-model="form.weatherReportTime" placeholder="例如 2026-02-10 07:50:00" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="天气位置">
          <el-input v-model="form.weatherLocation" />
        </el-form-item>

        <el-form-item label="记录图片">
          <div class="record-image-editor">
            <el-upload
              accept="image/*"
              :show-file-list="false"
              :http-request="uploadRecordImage"
              :before-upload="beforeRecordImageUpload"
              multiple
            >
              <el-button :loading="imageUploading">上传图片</el-button>
            </el-upload>
            <div v-if="form.imageAssets.length" class="record-image-list">
              <div v-for="item in form.imageAssets" :key="`asset-${item.id}`" class="record-image-item">
                <el-image v-if="item.fileUrl" :src="item.fileUrl" fit="cover" :preview-src-list="imagePreviewUrls" />
                <div v-else class="record-image-placeholder">{{ assetReviewLabel(item) }}</div>
                <div class="record-image-meta">
                  <el-tag size="small" :type="assetReviewTagType(item.reviewStatus)">{{ assetReviewLabel(item) }}</el-tag>
                </div>
                <div v-if="item.hintMessage" class="record-image-hint">{{ item.hintMessage }}</div>
                <el-button link type="danger" @click="removeRecordImage(item.id)">移除</el-button>
              </div>
            </div>
            <div v-else class="record-image-tip">可上传现场图片，保存后自动关联到该农事记录。</div>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.notes" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="operatorDetailVisible" title="操作员信息" width="520px">
      <el-skeleton v-if="loadingOperatorDetail" :rows="5" animated />
      <el-descriptions v-else :column="1" border size="small">
        <el-descriptions-item label="显示名称">{{ operatorDetail.displayName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ operatorDetail.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ operatorDetail.nickName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ operatorDetail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ operatorDetail.roleName || operatorDetail.roleCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ formatUserStatus(operatorDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="控制台">{{ formatConsoleFlag(operatorDetail.canConsole) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CropPairTags from '../components/ui/CropPairTags.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import ProcessStepSelector from '../components/ui/ProcessStepSelector.vue'
import OperatorUserSelector from '../components/ui/OperatorUserSelector.vue'
import { listFarmRecordImages, uploadAssetFile } from '../api/assets'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const fieldId = ref(String(route.params.fieldId || '').trim())

const loadingField = ref(false)
const loadingRecords = ref(false)
const saving = ref(false)
const imageUploading = ref(false)

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

const filters = reactive({
  cycleId: '',
  range: []
})

const records = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editMode = ref(false)
const form = reactive({
  id: null,
  cycleId: '',
  stepId: '',
  workDate: '',
  operatorUserId: '',
  operatorName: '',
  weather: '',
  temperature: '',
  humidity: '',
  windDirection: '',
  windPower: '',
  weatherReportTime: '',
  weatherLocation: '',
  notes: '',
  imageAssets: []
})
const operatorDetailVisible = ref(false)
const loadingOperatorDetail = ref(false)
const operatorDetail = reactive({
  id: '',
  displayName: '',
  realName: '',
  nickName: '',
  phone: '',
  roleCode: '',
  roleName: '',
  status: '',
  canConsole: null
})

const hasRange = computed(() => Array.isArray(filters.range) && !!filters.range[0] && !!filters.range[1])
const imagePreviewUrls = computed(() => (form.imageAssets || []).map((x) => String((x && x.fileUrl) || '').trim()).filter(Boolean))
const processState = reactive({
  segments: [],
  steps: []
})
const selectedSegmentKey = ref('')
const dynamicValues = ref({})
const hydratingProcess = ref(false)

const processSegments = computed(() => {
  return Array.isArray(processState.segments) ? processState.segments : []
})

const visibleSteps = computed(() => {
  if (processSegments.value.length) {
    const hit = processSegments.value.find((x) => toId(x && x.segmentKey) === toId(selectedSegmentKey.value))
    if (hit && Array.isArray(hit.steps)) return hit.steps
  }
  return Array.isArray(processState.steps) ? processState.steps : []
})

const selectedStep = computed(() => {
  const stepId = toId(form.stepId)
  if (!stepId) return null
  return visibleSteps.value.find((x) => toId(x && x.id) === stepId) || null
})

const dynamicFields = computed(() => {
  const step = selectedStep.value
  if (!step || !step.formSchema) return []
  return parseStepSchema(step.formSchema)
})

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
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

function formatRoleName(value) {
  const text = String(value || '').trim()
  return text || '-'
}

function formatUserStatus(status) {
  const map = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已拒绝',
    disabled: '已禁用'
  }
  return map[String(status || '').trim().toLowerCase()] || String(status || '').trim() || '-'
}

function assetReviewTagType(status) {
  const value = String(status || '').trim().toLowerCase()
  if (value === 'pending') return 'warning'
  if (value === 'rejected') return 'danger'
  return 'success'
}

function assetReviewLabel(row) {
  const text = String((row && row.reviewStatusText) || '').trim()
  if (text) return text
  const status = String((row && row.reviewStatus) || '').trim().toLowerCase()
  if (status === 'pending') return '待审核'
  if (status === 'rejected') return '未通过'
  return '已通过'
}

function formatConsoleFlag(value) {
  return Number(value || 0) === 1 ? '已开通' : '未开通'
}

function normalizeUserDisplayName(row) {
  const displayName = String((row && row.displayName) || '').trim()
  if (displayName) return displayName
  const realName = String((row && row.realName) || '').trim()
  if (realName) return realName
  const nickName = String((row && row.nickName) || '').trim()
  if (nickName) return nickName
  const id = Number((row && row.id) || 0)
  return id > 0 ? `用户#${id}` : ''
}

function normalizeUserDesc(row) {
  const displayDesc = String((row && row.displayDesc) || '').trim()
  if (displayDesc) return displayDesc
  const phone = String((row && row.phone) || '').trim()
  const role = formatRoleName(row && row.roleCode)
  if (phone && role) return `${phone} / ${role}`
  return phone || role || ''
}

async function fetchOperatorOptions({ keyword, page, pageSize }) {
  const data = await request.get('/farm-records/operator-options', {
    params: cleanParams({
      keyword: String(keyword || '').trim(),
      page: Number(page || 1),
      pageSize: Number(pageSize || 10)
    })
  })
  const records = ((data && data.records) || []).map((row) => ({
    ...row,
    displayName: normalizeUserDisplayName(row),
    displayDesc: normalizeUserDesc(row)
  }))
  return {
    records,
    total: Number((data && data.total) || 0)
  }
}

function onOperatorPicked(payload) {
  if (!payload) {
    form.operatorUserId = ''
    form.operatorName = ''
    return
  }
  const id = toId(payload.value)
  if (!id) {
    form.operatorUserId = ''
    return
  }
  form.operatorUserId = id
  const name = String(payload.label || '').trim() || normalizeUserDisplayName(payload.row)
  if (name) {
    form.operatorName = name
  }
}

function normalizeDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  if (text.includes('T')) return text.replace('T', ' ').slice(0, 19)
  return text.slice(0, 19)
}

function normalizeFieldType(type) {
  const raw = String(type || 'text').trim().toLowerCase()
  const supported = ['text', 'number', 'textarea', 'date', 'time', 'select', 'location']
  return supported.includes(raw) ? raw : 'text'
}

function parseStepSchema(schemaText) {
  if (!schemaText) return []
  try {
    const parsed = JSON.parse(schemaText)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((x) => x && x.key && x.label)
      .map((x) => ({
        ...x,
        type: normalizeFieldType(x.type)
      }))
  } catch (error) {
    return []
  }
}

function resolveDynamicOptions(item) {
  const raw = (item && item.options) || []
  const rows = Array.isArray(raw) ? raw : String(raw).split(',').map((x) => x.trim()).filter(Boolean)
  return rows
    .map((x) => {
      if (typeof x === 'object' && x) {
        const label = String(x.label || x.name || x.value || '').trim()
        const value = String(x.value || x.code || x.label || '').trim()
        return {
          label: label || value,
          value: value || label
        }
      }
      const value = String(x || '').trim()
      return { label: value, value }
    })
    .filter((x) => x.label && x.value)
}

function updateDynamicValue(key, value) {
  const k = String(key || '').trim()
  if (!k) return
  const next = { ...(dynamicValues.value || {}) }
  next[k] = value == null ? '' : String(value)
  dynamicValues.value = next
}

function parseExtraEntries(extraJson, extraLabelMap, extraValueLabelMap) {
  if (!extraJson) return []
  const labelMap = extraLabelMap && typeof extraLabelMap === 'object' ? extraLabelMap : {}
  const valueLabelMap = extraValueLabelMap && typeof extraValueLabelMap === 'object' ? extraValueLabelMap : {}
  try {
    const parsed = JSON.parse(extraJson)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return []
    return Object.keys(parsed)
      .filter((key) => parsed[key] !== null && parsed[key] !== undefined && String(parsed[key]).trim() !== '')
      .map((key) => {
        const keyText = String(key || '').trim()
        const value = String(parsed[key]).trim()
        const fieldValueMap = valueLabelMap && typeof valueLabelMap[keyText] === 'object' ? valueLabelMap[keyText] : null
        const displayValue = fieldValueMap && fieldValueMap[value] != null ? String(fieldValueMap[value]).trim() || value : value
        return {
          key: keyText,
          value: displayValue,
          displayLabel: String(labelMap[keyText] || '').trim() || keyText
        }
      })
      .filter((item) => item.key && item.value)
  } catch (error) {
    return []
  }
}

function resetProcessState() {
  processState.segments = []
  processState.steps = []
  selectedSegmentKey.value = ''
  form.stepId = ''
  dynamicValues.value = {}
}

function parseExtraJsonObject(text) {
  if (!text) return {}
  try {
    const parsed = JSON.parse(text)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return parsed
  } catch (error) {
    return {}
  }
}

function initDynamicValues(initialValues = null) {
  const next = {}
  const fields = dynamicFields.value || []
  fields.forEach((item) => {
    if (initialValues && initialValues[item.key] != null) {
      next[item.key] = String(initialValues[item.key])
      return
    }
    if (item.defaultValue != null) {
      next[item.key] = String(item.defaultValue)
      return
    }
    next[item.key] = ''
  })
  dynamicValues.value = next
}

function onStepChanged() {
  if (hydratingProcess.value) return
  initDynamicValues()
}

async function loadProcess(preferredStepId = '', preferredExtra = null) {
  if (!fieldId.value) {
    resetProcessState()
    return
  }
  hydratingProcess.value = true
  try {
    const params = {}
    const cycleId = toId(form.cycleId)
    if (cycleId) params.cycleId = cycleId
    const data = await request.get(`/fields/${fieldId.value}/process`, { params })
    processState.segments = Array.isArray(data && data.segments) ? data.segments : []
    processState.steps = Array.isArray(data && data.steps) ? data.steps : []
    if (processState.segments.length) {
      selectedSegmentKey.value = toId(data && data.selectedSegmentKey) || toId(processState.segments[0].segmentKey)
    } else {
      selectedSegmentKey.value = ''
    }

    let targetStepId = toId(preferredStepId)
    const allSteps = processSegments.value.length
      ? processSegments.value.flatMap((x) => (Array.isArray(x.steps) ? x.steps : []))
      : (Array.isArray(processState.steps) ? processState.steps : [])
    if (!targetStepId || !allSteps.some((x) => toId(x && x.id) === targetStepId)) {
      const rows = visibleSteps.value
      targetStepId = rows.length ? toId(rows[0].id) : ''
    }
    form.stepId = targetStepId

    if (processSegments.value.length && targetStepId) {
      const segmentHit = processSegments.value.find((seg) => {
        const rows = Array.isArray(seg && seg.steps) ? seg.steps : []
        return rows.some((x) => toId(x && x.id) === targetStepId)
      })
      if (segmentHit) {
        selectedSegmentKey.value = toId(segmentHit.segmentKey)
      }
    }
    initDynamicValues(preferredExtra || null)
  } catch (error) {
    resetProcessState()
  } finally {
    hydratingProcess.value = false
  }
}

async function onCycleChange() {
  await loadProcess()
}

function buildExtraJsonPayload() {
  const payload = {}
  Object.keys(dynamicValues.value || {}).forEach((key) => {
    const value = String((dynamicValues.value || {})[key] || '').trim()
    if (!value) return
    payload[key] = value
  })
  return Object.keys(payload).length ? JSON.stringify(payload) : null
}

function formatWeatherSummary(row) {
  const weather = String((row && row.weather) || '').trim()
  const temperature = String((row && row.temperature) || '').trim()
  const humidity = String((row && row.humidity) || '').trim()
  const windDirection = String((row && row.windDirection) || '').trim()
  const windPower = String((row && row.windPower) || '').trim()
  const sections = []
  if (weather) sections.push(weather)
  if (temperature) sections.push(`${temperature}°C`)
  if (humidity) sections.push(`湿度${humidity}%`)
  if (windDirection || windPower) sections.push([windDirection, windPower].filter(Boolean).join(' '))
  return sections.length ? sections.join(' / ') : '-'
}

function resetOperatorDetail() {
  operatorDetail.id = ''
  operatorDetail.displayName = ''
  operatorDetail.realName = ''
  operatorDetail.nickName = ''
  operatorDetail.phone = ''
  operatorDetail.roleCode = ''
  operatorDetail.roleName = ''
  operatorDetail.status = ''
  operatorDetail.canConsole = null
}

async function openOperatorDetail(row) {
  const userId = toId(row && row.operatorUserId)
  if (!userId) {
    ElMessage.warning('该记录未绑定操作员账号')
    return
  }
  operatorDetailVisible.value = true
  loadingOperatorDetail.value = true
  resetOperatorDetail()
  try {
    const data = await request.get(`/farm-records/operator-detail/${userId}`)
    operatorDetail.id = toId(data && data.id)
    operatorDetail.displayName = String((data && data.displayName) || (row && row.operatorName) || '').trim()
    operatorDetail.realName = String((data && data.realName) || '').trim()
    operatorDetail.nickName = String((data && data.nickName) || '').trim()
    operatorDetail.phone = String((data && data.phone) || '').trim()
    operatorDetail.roleCode = String((data && data.roleCode) || '').trim()
    operatorDetail.roleName = String((data && data.roleName) || '').trim()
    operatorDetail.status = String((data && data.status) || '').trim()
    operatorDetail.canConsole = data && data.canConsole != null ? Number(data.canConsole) : 0
  } catch (error) {
    ElMessage.error(error.message || '操作员信息加载失败')
    operatorDetailVisible.value = false
  } finally {
    loadingOperatorDetail.value = false
  }
}

function normalizeAssetRow(row) {
  if (!row) return null
  const id = String(row.id || '').trim()
  if (!/^\d+$/.test(id)) return null
  const fileUrl = String(row.fileUrl || '').trim()
  return {
    id,
    fileName: String(row.fileName || '').trim(),
    fileUrl,
    reviewStatus: String(row.reviewStatus || 'approved').trim() || 'approved',
    reviewStatusText: assetReviewLabel(row),
    reviewRemark: String(row.reviewRemark || '').trim(),
    hintMessage: String(row.hintMessage || '').trim()
  }
}

async function loadRecordImages(recordId) {
  const id = String(recordId || '').trim()
  if (!/^\d+$/.test(id)) return []
  try {
    const data = await listFarmRecordImages(id)
    return (Array.isArray(data) ? data : [])
      .map((x) => normalizeAssetRow(x))
      .filter(Boolean)
  } catch (error) {
    return []
  }
}

function beforeRecordImageUpload(rawFile) {
  const isImage = !!(rawFile && String(rawFile.type || '').toLowerCase().startsWith('image/'))
  if (!isImage) {
    ElMessage.warning('仅支持上传图片文件')
    return false
  }
  if ((form.imageAssets || []).length >= 9) {
    ElMessage.warning('最多上传 9 张图片')
    return false
  }
  return true
}

async function uploadRecordImage(option) {
  if (!option || !option.file) return
  if (!beforeRecordImageUpload(option.file)) {
    option.onError && option.onError(new Error('invalid file'))
    return
  }
  imageUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', option.file)
    formData.append('remark', 'farm-record-image')
    const data = await uploadAssetFile(formData)
    const asset = normalizeAssetRow(data)
    if (!asset) {
      throw new Error('upload failed')
    }
    const exists = (form.imageAssets || []).some((x) => String(x.id || '') === String(asset.id || ''))
    if (!exists) {
      form.imageAssets = (form.imageAssets || []).concat([asset])
    }
    if (asset.reviewStatus === 'pending') {
      ElMessage.warning('图片已绑定到当前记录，审核通过后小程序端可见')
    }
    option.onSuccess && option.onSuccess(asset)
  } catch (error) {
    option.onError && option.onError(error)
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    imageUploading.value = false
  }
}

function removeRecordImage(assetId) {
  const id = String(assetId || '').trim()
  if (!/^\d+$/.test(id)) return
  form.imageAssets = (form.imageAssets || []).filter((x) => String((x && x.id) || '') !== id)
}

function areaText(value) {
  const num = Number(value || 0)
  return Number.isFinite(num) && num > 0 ? `${num.toFixed(2)} 亩` : '-'
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

function formatCropVarietyPair(row) {
  const cropType = String((row && row.cropType) || '').trim()
  const cropVariety = String((row && row.cropVariety) || '').trim()
  if (cropType && cropVariety) return `${cropType} + ${cropVariety}`
  return cropType || cropVariety || '-'
}

function cycleLabel(cycle) {
  return String((cycle && (cycle.planName || cycle.cycleName)) || '').trim() || `计划#${toId(cycle && cycle.id)}`
}

function cycleStatusLabel(status) {
  return String(status || '').toLowerCase() === 'completed' ? '已结束' : '进行中'
}

function cycleStatusTagType(status) {
  return String(status || '').toLowerCase() === 'completed' ? 'info' : 'warning'
}

function findCycleById(cycleId) {
  const key = toId(cycleId)
  if (!key) return null
  return cycles.value.find((item) => toId(item && item.idKey) === key) || null
}

function cycleDisplayNameById(cycleId) {
  const cycle = findCycleById(cycleId)
  if (cycle) return cycle.displayName
  const key = toId(cycleId)
  return key ? `计划#${key}` : '未指定计划'
}

function recordTitle(row) {
  const stepName = String((row && row.stepName) || '').trim()
  if (stepName) return stepName
  const stepId = toId(row && row.stepId)
  if (stepId) return `步骤#${stepId}`
  return '农事记录'
}

function rangeParams() {
  const startDate = hasRange.value ? `${filters.range[0]} 00:00:00` : undefined
  const endDate = hasRange.value ? `${filters.range[1]} 23:59:59` : undefined
  return { startDate, endDate }
}

async function loadFieldInfo() {
  if (!fieldId.value) return
  loadingField.value = true
  try {
    const data = await request.get(`/fields/${fieldId.value}`)
    fieldInfo.id = toId(data && data.id)
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
  try {
    const rows = await request.get(`/fields/${fieldId.value}/cycles`)
    cycles.value = (Array.isArray(rows) ? rows : []).map((item) => ({
      ...item,
      idKey: toId(item && item.id),
      displayName: cycleLabel(item)
    }))
    if (!cycles.value.some((item) => toId(item.idKey) === toId(filters.cycleId))) {
      filters.cycleId = ''
    }
  } catch (error) {
    cycles.value = []
  }
}

async function loadRecords(nextPage = page.value) {
  if (!fieldId.value) return
  loadingRecords.value = true
  try {
    page.value = Number(nextPage || 1)
    const { startDate, endDate } = rangeParams()
    const data = await request.get('/farm-records', {
      params: cleanParams({
        page: page.value,
        pageSize: pageSize.value,
        fieldId: fieldId.value,
        cycleId: filters.cycleId,
        startDate,
        endDate
      })
    })
    records.value = ((data && data.records) || []).map((item) => {
      const cycle = findCycleById(item && item.cycleId)
      return {
        ...item,
        workDate: normalizeDateTime(item && item.workDate),
        cycleName: cycle ? cycle.displayName : cycleDisplayNameById(item && item.cycleId),
        cycleStatusLabel: cycle ? cycleStatusLabel(cycle.status) : '',
        cycleStatusTagType: cycle ? cycleStatusTagType(cycle.status) : 'info',
        cycleIsCurrent: cycle ? Number(cycle.isCurrent || 0) : 0,
        extraParamEntries: parseExtraEntries(item && item.extraJson, item && item.extraLabelMap, item && item.extraValueLabelMap)
      }
    })
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(error.message || '农事记录加载失败')
  } finally {
    loadingRecords.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRecords(1)
}

function resetForm() {
  form.id = null
  form.cycleId = ''
  form.stepId = ''
  form.workDate = ''
  form.operatorUserId = ''
  form.operatorName = ''
  form.weather = ''
  form.temperature = ''
  form.humidity = ''
  form.windDirection = ''
  form.windPower = ''
  form.weatherReportTime = ''
  form.weatherLocation = ''
  form.notes = ''
  form.imageAssets = []
  resetProcessState()
}

async function openCreate() {
  resetForm()
  editMode.value = false
  await loadProcess()
  dialogVisible.value = true
}

async function openEdit(row) {
  const id = toId(row && row.id)
  if (!id) return
  try {
    const detail = await request.get(`/farm-records/${id}`)
    resetForm()
    editMode.value = true
    form.id = id
    form.cycleId = toId(detail && detail.cycleId)
    form.stepId = toId(detail && detail.stepId)
    form.workDate = normalizeDateTime(detail && detail.workDate)
    form.operatorUserId = toId(detail && detail.operatorUserId)
    form.operatorName = (detail && detail.operatorName) || ''
    form.weather = (detail && detail.weather) || ''
    form.temperature = (detail && detail.temperature) || ''
    form.humidity = (detail && detail.humidity) || ''
    form.windDirection = (detail && detail.windDirection) || ''
    form.windPower = (detail && detail.windPower) || ''
    form.weatherReportTime = (detail && detail.weatherReportTime) || ''
    form.weatherLocation = (detail && detail.weatherLocation) || ''
    form.notes = (detail && detail.notes) || ''
    form.imageAssets = await loadRecordImages(id)
    await loadProcess(form.stepId, parseExtraJsonObject(detail && detail.extraJson))
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '记录详情加载失败')
  }
}

async function saveRecord() {
  if (!fieldId.value) return
  const workDate = String(form.workDate || '').trim()
  if (!workDate) {
    ElMessage.warning('请选择作业时间')
    return
  }
  if (dynamicFields.value.some((item) => item && item.required && !String((dynamicValues.value || {})[item.key] || '').trim())) {
    ElMessage.warning('请补全必填步骤参数')
    return
  }

  saving.value = true
  try {
    const payload = {
      fieldId: fieldId.value,
      cycleId: toId(form.cycleId) || null,
      stepId: toId(form.stepId) || null,
      workDate,
      operatorUserId: toId(form.operatorUserId) || null,
      operatorName: form.operatorName || null,
      notes: form.notes || null,
      weather: form.weather || null,
      temperature: form.temperature || null,
      weatherLocation: form.weatherLocation || null,
      humidity: form.humidity || null,
      windDirection: form.windDirection || null,
      windPower: form.windPower || null,
      weatherReportTime: form.weatherReportTime || null,
      extraJson: buildExtraJsonPayload(),
      imageAssetIds: (form.imageAssets || [])
        .map((x) => String((x && x.id) || '').trim())
        .filter((x) => /^\d+$/.test(x))
    }

    if (editMode.value && toId(form.id)) {
      await request.put(`/farm-records/${form.id}`, payload)
    } else {
      await request.post('/farm-records', payload)
    }

    dialogVisible.value = false
    ElMessage.success('记录保存成功')
    await loadRecords(page.value)
  } catch (error) {
    ElMessage.error(error.message || '记录保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRecord(row) {
  const id = toId(row && row.id)
  if (!id) return
  try {
    await ElMessageBox.confirm('确认删除该农事记录吗？', '删除确认', { type: 'warning' })
    await request.delete(`/farm-records/${id}`)
    ElMessage.success('删除成功')
    const remainAfterDelete = Math.max(0, total.value - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadRecords(Math.min(page.value, maxPage))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function goBack() {
  router.push('/farm-records-manage')
}

async function refreshAll() {
  await Promise.all([loadFieldInfo(), loadCycles()])
  await loadRecords(page.value)
}

watch(
  () => route.params.fieldId,
  async (next) => {
    fieldId.value = String(next || '').trim()
    filters.cycleId = ''
    await refreshAll()
  }
)

watch(
  () => form.stepId,
  (next, prev) => {
    if (hydratingProcess.value) return
    if (toId(next) === toId(prev)) return
    initDynamicValues()
  }
)

onMounted(async () => {
  await refreshAll()
})
</script>

<style scoped>
.farm-record-detail-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.field-summary {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 12px 8px;
  margin-bottom: 10px;
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

.filters-strip {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 10px 8px;
  margin-bottom: 10px;
}

.record-list {
  display: grid;
  gap: 10px;
}

.record-item {
  display: flex;
  align-items: stretch;
  gap: 8px;
}

.record-panel {
  flex: 1;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-panel);
}

.record-timeline {
  width: 16px;
  display: flex;
  justify-content: center;
  position: relative;
}

.record-dot {
  margin-top: 16px;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: var(--brand);
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px rgba(111, 152, 64, 0.45);
  z-index: 2;
}

.record-line {
  position: absolute;
  top: 30px;
  bottom: -10px;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  background: #d6e5c8;
}

.record-head {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
}

.record-main {
  min-width: 0;
}

.record-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.record-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-sub);
  font-size: 12px;
}

.record-note {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.6;
}

.record-param-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-left: 6px;
}

.record-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cycle-option {
  display: inline-flex;
  width: 100%;
  align-items: center;
  gap: 6px;
}

.cycle-option-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-image-editor {
  display: grid;
  gap: 10px;
}

.record-image-tip {
  color: var(--text-sub);
  font-size: 12px;
}

.record-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.record-image-item {
  width: 110px;
}

.record-image-placeholder,
.record-image-item .el-image {
  width: 110px;
  height: 86px;
  border-radius: 6px;
  border: 1px solid var(--border);
  overflow: hidden;
}

.record-image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7f7;
  color: var(--text-secondary);
  font-size: 12px;
  text-align: center;
  padding: 8px;
}

.record-image-meta {
  margin-top: 6px;
}

.record-image-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.form-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 220px);
  overflow: auto;
}

.dynamic-editor {
  width: 100%;
  display: grid;
  gap: 10px;
}

.dynamic-item {
  display: grid;
  gap: 6px;
}

.operator-editor {
  width: 100%;
  display: grid;
  gap: 8px;
}

.dynamic-label {
  font-size: 12px;
  color: var(--text-sub);
}

.required {
  color: #d64545;
  margin-left: 2px;
}

@media (max-width: 1080px) {
  .record-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


