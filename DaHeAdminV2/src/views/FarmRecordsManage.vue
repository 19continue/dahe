<template>
  <div class="farm-records-page">
    <PageToolbar
      title="农事记录管理"
      subtitle="对齐田块种植计划：田块记录卡片视图 + 全部记录表格视图，单田块在独立标签页管理。"
      collapsible
      :summary="[
        mode === 'field' ? '当前模式：田块记录' : '当前模式：全部记录',
        filters.keyword ? `田块关键词：${filters.keyword}` : '',
        filters.township ? `乡镇：${filters.township}` : '',
        filters.fieldId ? `田块：${fieldNameById(filters.fieldId)}` : '',
        filters.cycleId ? `计划：${cycleDisplayNameById(filters.fieldId, filters.cycleId)}` : '',
        hasRange ? `日期：${filters.range[0]} ~ ${filters.range[1]}` : ''
      ]"
    >
      <template #head-actions>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button label="field">田块记录</el-radio-button>
          <el-radio-button label="all">全部记录</el-radio-button>
        </el-radio-group>
      </template>

      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="田块名称关键字" clearable style="width: 220px" />
        <el-select v-model="filters.township" clearable filterable placeholder="乡镇" style="width: 160px">
          <el-option label="全部" value="" />
          <el-option v-for="item in townshipOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker
          v-model="filters.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
        <el-select v-if="mode === 'all'" v-model="filters.fieldId" clearable filterable placeholder="按田块筛选" style="width: 200px" @change="onFilterFieldChange">
          <el-option label="全部田块" value="" />
          <el-option v-for="item in fieldOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-if="mode === 'all'" v-model="filters.cycleId" clearable filterable placeholder="按计划筛选" style="width: 220px">
          <el-option label="全部计划" value="" />
          <el-option v-for="item in filterCycleOptions" :key="item.idKey" :label="item.displayName" :value="item.idKey">
            <div class="cycle-option">
              <span class="cycle-option-name">{{ item.displayName }}</span>
              <el-tag v-if="Number(item.isCurrent) === 1" size="small" type="success">当前</el-tag>
              <el-tag size="small" :type="cycleStatusTagType(item.status)">{{ cycleStatusLabel(item.status) }}</el-tag>
            </div>
          </el-option>
        </el-select>
        <el-button @click="runQuery">查询</el-button>
      </div>
    </PageToolbar>

    <section v-if="mode === 'field'" class="field-board" v-loading="loadingFieldCards">
      <div v-if="fieldCards.length" class="field-grid">
        <article v-for="field in fieldCards" :key="field.id" class="field-card">
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
                  <span class="field-crops">
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
              <span class="metric-label">记录总数</span>
              <span class="metric-value">{{ fieldRecordCount(field.id) }}</span>
            </div>
          </div>

          <div class="field-foot">
            <el-popover trigger="hover" placement="top-start" :width="360">
              <template #reference>
                <el-button size="small">悬停预览记录</el-button>
              </template>
              <div class="record-preview">
                <div class="record-preview-title">记录预览</div>
                <div v-if="fieldRecordPreview(field.id).length" class="record-preview-list">
                  <div v-for="item in fieldRecordPreview(field.id)" :key="`${field.id}-${item.key}`" class="record-preview-item">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.time }}</span>
                  </div>
                </div>
                <el-empty v-else description="该田块暂无记录" :image-size="40" />
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
          layout="total, sizes, prev, pager, next, jumper"
          :total="fieldCardTotal"
          :page-size="fieldCardPageSize"
          :current-page="fieldCardPage"
          :page-sizes="[12, 15, 18, 24, 30]"
          @size-change="onFieldCardPageSizeChange"
          @current-change="loadFieldCards"
        />
      </div>
    </section>

    <section v-else class="all-record-section">
      <el-card shadow="never" v-loading="loadingAll">
        <template #header>
          <div class="card-head">
            <span>全部农事记录</span>
            <div class="actions">
              <el-button :type="allBatchMode ? 'primary' : 'default'" plain @click="toggleAllBatchMode">
                {{ allBatchMode ? '退出多选' : '多选操作' }}
              </el-button>
              <span v-if="allBatchMode" class="card-meta">已选 {{ allSelectedRows.length }} 条</span>
              <span class="card-meta">共 {{ allTotal }} 条</span>
              <el-button
                v-if="allBatchMode"
                type="danger"
                plain
                :disabled="!allBatchDeleteCount || allBatchDeleting"
                :loading="allBatchDeleting"
                @click="onAllBatchDelete"
              >
                删除已选（{{ allBatchDeleteCount }}）
              </el-button>
              <el-button type="primary" @click="openCreateRecord">新增记录</el-button>
            </div>
          </div>
        </template>

        <el-table ref="allTableRef" :data="allRows" border @selection-change="onAllSelectionChange">
          <el-table-column
            v-if="allBatchMode"
            type="selection"
            width="46"
            align="center"
            :selectable="isAllRowSelectable"
          />
          <el-table-column prop="fieldName" label="田块" min-width="140" show-overflow-tooltip />
          <el-table-column label="计划" min-width="210" show-overflow-tooltip>
            <template #default="scope">
              <div class="cycle-cell">
                <span class="cycle-name">{{ scope.row.cycleName || '未指定计划' }}</span>
                <el-tag v-if="Number(scope.row.cycleIsCurrent) === 1" size="small" type="success">当前</el-tag>
                <el-tag
                  v-if="scope.row.cycleStatusLabel"
                  size="small"
                  :type="scope.row.cycleStatusTagType"
                >
                  {{ scope.row.cycleStatusLabel }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="作业时间" width="170">
            <template #default="scope">{{ normalizeDateTime(scope.row.workDate) }}</template>
          </el-table-column>
          <el-table-column label="步骤节点" width="110">
            <template #default="scope">{{ formatStepLabel(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="操作员" width="180">
            <template #default="scope">
              <span>{{ scope.row.operatorName || '-' }}</span>
              <el-button
                v-if="toId(scope.row.operatorUserId)"
                link
                type="primary"
                @click="openOperatorDetail(scope.row)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="步骤参数" min-width="260">
            <template #default="scope">
              <div v-if="(scope.row.extraParamEntries || []).length" class="extra-param-tags">
                <el-tag
                  v-for="item in scope.row.extraParamEntries"
                  :key="`${scope.row.id || 'row'}-${item.key}`"
                  size="small"
                  effect="plain"
                >
                  {{ item.displayLabel }}:{{ item.value }}
                </el-tag>
              </div>
              <span v-else class="extra-param-empty">-</span>
            </template>
          </el-table-column>
          <el-table-column label="天气要素" min-width="220">
            <template #default="scope">{{ formatWeatherSummary(scope.row) }}</template>
          </el-table-column>
          <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right" class-name="op-col">
            <template #default="scope">
              <div class="table-op-line">
                <el-button size="small" @click="openFieldDetail(scope.row)">进入田块页</el-button>
                <el-button size="small" type="primary" :disabled="scope.row.canEdit === false" @click="openEditRecord(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" plain :disabled="scope.row.canDelete === false" @click="removeRecord(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-foot">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="allTotal"
            :page-size="allPageSize"
            :current-page="allPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onAllPageSizeChange"
            @current-change="loadAllRecords"
          />
        </div>
      </el-card>
    </section>

    <el-dialog
      v-model="recordDialogVisible"
      :title="recordEditMode ? '编辑农事记录' : '新增农事记录'"
      width="860px"
      class="form-dialog"
      align-center
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="所属田块">
              <el-select v-model="recordForm.fieldId" filterable style="width: 100%" @change="onDialogFieldChange">
                <el-option v-for="item in fieldOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="种植计划">
              <el-select v-model="recordForm.cycleId" clearable filterable placeholder="可选" style="width: 100%" @change="onDialogCycleChange">
                <el-option label="不指定计划" value="" />
                <el-option v-for="item in dialogCycleOptions" :key="item.idKey" :label="item.displayName" :value="item.idKey">
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
            v-model="recordForm.stepId"
            v-model:segment-key="recordSelectedSegmentKey"
            :segments="recordSegments"
            :steps="recordProcessState.steps"
            empty-text="当前计划没有可选步骤，请先在流程模板中配置步骤。"
            @change="onRecordStepChanged"
          />
        </el-form-item>

        <el-form-item label="步骤参数" v-if="recordDynamicFields.length">
          <div class="dynamic-editor">
            <div v-for="item in recordDynamicFields" :key="item.key" class="dynamic-item">
              <div class="dynamic-label">{{ item.label }}<span v-if="item.required" class="required">*</span></div>
              <el-select
                v-if="item.type === 'select'"
                :model-value="recordDynamicValues[item.key] || ''"
                placeholder="请选择"
                clearable
                @update:model-value="updateRecordDynamicValue(item.key, $event)"
              >
                <el-option v-for="opt in resolveDynamicOptions(item)" :key="`${item.key}-${opt.value}`" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-input
                v-else-if="item.type === 'textarea'"
                :model-value="recordDynamicValues[item.key] || ''"
                type="textarea"
                :rows="2"
                :placeholder="item.placeholder || `请输入${item.label}`"
                @update:model-value="updateRecordDynamicValue(item.key, $event)"
              />
              <el-date-picker
                v-else-if="item.type === 'date'"
                :model-value="recordDynamicValues[item.key] || ''"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                @update:model-value="updateRecordDynamicValue(item.key, $event)"
              />
              <el-input
                v-else
                :model-value="recordDynamicValues[item.key] || ''"
                :placeholder="item.placeholder || `请输入${item.label}`"
                @update:model-value="updateRecordDynamicValue(item.key, $event)"
              />
            </div>
          </div>
        </el-form-item>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="作业时间">
              <el-date-picker
                v-model="recordForm.workDate"
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
                  v-model="recordForm.operatorUserId"
                  :model-label="recordForm.operatorName"
                  placeholder="按姓名/昵称/手机号检索操作员"
                  :fetcher="fetchOperatorOptions"
                  @change="onRecordOperatorPicked"
                />
                <el-input v-model="recordForm.operatorName" placeholder="也可手动输入操作员姓名" />
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="6">
            <el-form-item label="天气">
              <el-input v-model="recordForm.weather" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="温度">
              <el-input v-model="recordForm.temperature" placeholder="例如 25℃" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="湿度(%)">
              <el-input v-model="recordForm.humidity" placeholder="例如 68" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="风力">
              <el-input v-model="recordForm.windPower" placeholder="例如 3级" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="风向">
              <el-input v-model="recordForm.windDirection" placeholder="例如 北风" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="天气发布时间">
              <el-input v-model="recordForm.weatherReportTime" placeholder="例如 2026-02-10 07:50:00" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="天气位置">
          <el-input v-model="recordForm.weatherLocation" placeholder="例如：临河镇东湾村" />
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
              <el-button :loading="recordImageUploading">上传图片</el-button>
            </el-upload>
            <div v-if="recordForm.imageAssets.length" class="record-image-list">
              <div v-for="item in recordForm.imageAssets" :key="`asset-${item.id}`" class="record-image-item">
                <el-image v-if="item.fileUrl" :src="item.fileUrl" fit="cover" :preview-src-list="recordImagePreviewUrls" />
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
          <el-input v-model="recordForm.notes" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="recordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRecord" @click="saveRecord">保存</el-button>
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
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CropPairTags from '../components/ui/CropPairTags.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import ProcessStepSelector from '../components/ui/ProcessStepSelector.vue'
import OperatorUserSelector from '../components/ui/OperatorUserSelector.vue'
import { listFarmRecordImages, uploadAssetFile } from '../api/assets'
import request from '../utils/request'

const router = useRouter()

const mode = ref('field')

const filters = reactive({
  keyword: '',
  township: '',
  range: [],
  fieldId: '',
  cycleId: ''
})

const townshipOptions = ref([])
const fieldOptions = ref([])
const cycleCache = ref(new Map())

const loadingFieldCards = ref(false)
const fieldCards = ref([])
const fieldCardPage = ref(1)
const fieldCardPageSize = ref(12)
const fieldCardTotal = ref(0)
const fieldRecordMeta = ref({})

const loadingAll = ref(false)
const allRows = ref([])
const allPage = ref(1)
const allPageSize = ref(10)
const allTotal = ref(0)
const allTableRef = ref(null)
const allBatchMode = ref(false)
const allSelectedRows = ref([])
const allBatchDeleting = ref(false)

const recordDialogVisible = ref(false)
const recordEditMode = ref(false)
const savingRecord = ref(false)
const recordImageUploading = ref(false)
const dialogCycleOptions = ref([])
const recordForm = reactive({
  id: null,
  fieldId: '',
  cycleId: '',
  stepId: '',
  workDate: '',
  operatorUserId: '',
  operatorName: '',
  weather: '',
  temperature: '',
  weatherLocation: '',
  humidity: '',
  windDirection: '',
  windPower: '',
  weatherReportTime: '',
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

const filterCycleOptions = computed(() => {
  const fieldId = toId(filters.fieldId)
  if (!fieldId) return []
  return cyclesByFieldId(fieldId)
})

const allBatchDeleteCount = computed(() => {
  return allSelectedRows.value.filter((row) => row && row.canDelete !== false).length
})

const recordImagePreviewUrls = computed(() => {
  return (recordForm.imageAssets || [])
    .map((x) => String((x && x.fileUrl) || '').trim())
    .filter(Boolean)
})

const recordProcessState = reactive({
  segments: [],
  steps: []
})
const recordSelectedSegmentKey = ref('')
const recordDynamicValues = ref({})
const hydratingRecordProcess = ref(false)

const recordSegments = computed(() => {
  return Array.isArray(recordProcessState.segments) ? recordProcessState.segments : []
})

const recordVisibleSteps = computed(() => {
  if (recordSegments.value.length) {
    const hit = recordSegments.value.find((x) => toId(x && x.segmentKey) === toId(recordSelectedSegmentKey.value))
    if (hit && Array.isArray(hit.steps)) return hit.steps
  }
  return Array.isArray(recordProcessState.steps) ? recordProcessState.steps : []
})

const recordSelectedStep = computed(() => {
  const selectedId = toId(recordForm.stepId)
  if (!selectedId) return null
  return recordVisibleSteps.value.find((x) => toId(x && x.id) === selectedId) || null
})

const recordDynamicFields = computed(() => {
  const step = recordSelectedStep.value
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

function onRecordOperatorPicked(payload) {
  if (!payload) {
    recordForm.operatorUserId = ''
    recordForm.operatorName = ''
    return
  }
  const id = toId(payload.value)
  if (!id) {
    recordForm.operatorUserId = ''
    return
  }
  recordForm.operatorUserId = id
  const name = String(payload.label || '').trim() || normalizeUserDisplayName(payload.row)
  if (name) {
    recordForm.operatorName = name
  }
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

function formatCropVarietyPair(row) {
  const cropType = String((row && row.cropType) || '').trim()
  const cropVariety = String((row && row.cropVariety) || '').trim()
  if (cropType && cropVariety) return `${cropType} + ${cropVariety}`
  return cropType || cropVariety || '-'
}

function fieldNameById(fieldId) {
  const id = toId(fieldId)
  if (!id) return '-'
  const hit = fieldOptions.value.find((item) => toId(item && item.id) === id)
  return (hit && hit.name) || `田块#${id}`
}

function cycleStatusLabel(status) {
  return String(status || '').toLowerCase() === 'completed' ? '已结束' : '进行中'
}

function cycleStatusTagType(status) {
  return String(status || '').toLowerCase() === 'completed' ? 'info' : 'warning'
}

function findCycleById(fieldId, cycleId) {
  const fieldKey = toId(fieldId)
  const cycleKey = toId(cycleId)
  if (!fieldKey || !cycleKey) return null
  const rows = cyclesByFieldId(fieldKey)
  return rows.find((item) => toId(item && item.idKey) === cycleKey) || null
}

function cycleDisplayNameById(fieldId, cycleId) {
  const cycle = findCycleById(fieldId, cycleId)
  if (cycle) return cycle.displayName
  const cycleKey = toId(cycleId)
  return cycleKey ? `计划#${cycleKey}` : '-'
}

function normalizeDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  if (text.includes('T')) {
    return text.replace('T', ' ').slice(0, 19)
  }
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

function updateRecordDynamicValue(key, value) {
  const k = String(key || '').trim()
  if (!k) return
  const next = { ...(recordDynamicValues.value || {}) }
  next[k] = value == null ? '' : String(value)
  recordDynamicValues.value = next
}

function formatStepLabel(row) {
  if (!row) return '-'
  const name = String((row && row.stepName) || '').trim()
  if (name) return name
  const stepId = toId(row.stepId)
  return stepId ? `步骤#${stepId}` : '-'
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

function isAllRowSelectable(row) {
  return !!row && row.canDelete !== false
}

function clearAllTableSelection() {
  allSelectedRows.value = []
  nextTick(() => {
    if (allTableRef.value && typeof allTableRef.value.clearSelection === 'function') {
      allTableRef.value.clearSelection()
    }
  })
}

function onAllSelectionChange(rows) {
  allSelectedRows.value = Array.isArray(rows) ? rows : []
}

function toggleAllBatchMode() {
  allBatchMode.value = !allBatchMode.value
  if (!allBatchMode.value) {
    clearAllTableSelection()
  }
}

async function onAllBatchDelete() {
  const ids = Array.from(new Set(
    allSelectedRows.value
      .filter((row) => row && row.canDelete !== false)
      .map((row) => toId(row && row.id))
      .filter(Boolean)
  ))
  if (!ids.length) {
    ElMessage.warning('请先选择可删除的记录')
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 条农事记录吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
    return
  }

  allBatchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => request.delete(`/farm-records/${id}`)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount

    if (successCount > 0) {
      const remainAfterDelete = Math.max(0, allTotal.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(allPageSize.value || 10)))
      await Promise.all([loadAllRecords(Math.min(allPage.value, maxPage)), loadFieldCards(fieldCardPage.value)])
    }
    clearAllTableSelection()

    if (failedCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 条，${failedCount} 条删除失败`)
      return
    }
    ElMessage.success(`已删除 ${successCount} 条记录`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    allBatchDeleting.value = false
  }
}

function resetRecordProcessState() {
  recordProcessState.segments = []
  recordProcessState.steps = []
  recordSelectedSegmentKey.value = ''
  recordForm.stepId = ''
  recordDynamicValues.value = {}
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

function initRecordDynamicValues(initialValues = null) {
  const next = {}
  const fields = recordDynamicFields.value || []
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
  recordDynamicValues.value = next
}

function onRecordStepChanged() {
  if (hydratingRecordProcess.value) return
  initRecordDynamicValues()
}

async function loadRecordProcess(preferredStepId = '', preferredExtra = null) {
  const fieldId = toId(recordForm.fieldId)
  if (!fieldId) {
    resetRecordProcessState()
    return
  }
  hydratingRecordProcess.value = true
  try {
    const params = {}
    const cycleId = toId(recordForm.cycleId)
    if (cycleId) params.cycleId = cycleId
    const data = await request.get(`/fields/${fieldId}/process`, { params })
    recordProcessState.segments = Array.isArray(data && data.segments) ? data.segments : []
    recordProcessState.steps = Array.isArray(data && data.steps) ? data.steps : []
    if (recordProcessState.segments.length) {
      const defaultSegment = toId(data && data.selectedSegmentKey) || toId(recordProcessState.segments[0].segmentKey)
      recordSelectedSegmentKey.value = defaultSegment
    } else {
      recordSelectedSegmentKey.value = ''
    }

    let targetStepId = toId(preferredStepId)
    const allSteps = recordSegments.value.length
      ? recordSegments.value.flatMap((x) => (Array.isArray(x.steps) ? x.steps : []))
      : (Array.isArray(recordProcessState.steps) ? recordProcessState.steps : [])
    if (!targetStepId || !allSteps.some((x) => toId(x && x.id) === targetStepId)) {
      const visible = recordVisibleSteps.value
      targetStepId = visible.length ? toId(visible[0].id) : ''
    }
    recordForm.stepId = targetStepId

    if (recordSegments.value.length && targetStepId) {
      const segmentHit = recordSegments.value.find((seg) => {
        const rows = Array.isArray(seg && seg.steps) ? seg.steps : []
        return rows.some((x) => toId(x && x.id) === targetStepId)
      })
      if (segmentHit) {
        recordSelectedSegmentKey.value = toId(segmentHit.segmentKey)
      }
    }
    initRecordDynamicValues(preferredExtra || null)
  } catch (error) {
    resetRecordProcessState()
  } finally {
    hydratingRecordProcess.value = false
  }
}

function buildRecordExtraJson() {
  const payload = {}
  Object.keys(recordDynamicValues.value || {}).forEach((key) => {
    const value = String((recordDynamicValues.value || {})[key] || '').trim()
    if (!value) return
    payload[key] = value
  })
  return Object.keys(payload).length ? JSON.stringify(payload) : null
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
  if ((recordForm.imageAssets || []).length >= 9) {
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
  recordImageUploading.value = true
  try {
    const form = new FormData()
    form.append('file', option.file)
    form.append('remark', 'farm-record-image')
    const data = await uploadAssetFile(form)
    const asset = normalizeAssetRow(data)
    if (!asset) {
      throw new Error('upload failed')
    }
    const exists = (recordForm.imageAssets || []).some((x) => String(x.id || '') === String(asset.id || ''))
    if (!exists) {
      recordForm.imageAssets = (recordForm.imageAssets || []).concat([asset])
    }
    if (asset.reviewStatus === 'pending') {
      ElMessage.warning('图片已绑定到当前记录，审核通过后小程序端可见')
    }
    option.onSuccess && option.onSuccess(asset)
  } catch (error) {
    option.onError && option.onError(error)
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    recordImageUploading.value = false
  }
}

function removeRecordImage(assetId) {
  const id = String(assetId || '').trim()
  if (!/^\d+$/.test(id)) return
  recordForm.imageAssets = (recordForm.imageAssets || []).filter((x) => String((x && x.id) || '') !== id)
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

function toRangeParams() {
  const startDate = hasRange.value ? `${filters.range[0]} 00:00:00` : undefined
  const endDate = hasRange.value ? `${filters.range[1]} 23:59:59` : undefined
  return { startDate, endDate }
}

function cycleLabel(cycle) {
  return String((cycle && (cycle.planName || cycle.cycleName)) || '').trim() || `计划#${toId(cycle && cycle.id)}`
}

function cyclesByFieldId(fieldId) {
  const key = toId(fieldId)
  if (!key) return []
  return cycleCache.value.get(key) || []
}

async function ensureCyclesForFields(fieldIds, options = {}) {
  const force = !!(options && options.force)
  const ids = Array.from(new Set((Array.isArray(fieldIds) ? fieldIds : []).map((x) => toId(x)).filter(Boolean)))
  if (!ids.length) return new Map()

  const cache = new Map(cycleCache.value)
  const missingIds = force ? ids : ids.filter((id) => !cache.has(id))
  if (missingIds.length) {
    try {
      const data = await request.get('/fields/cycles/by-fields', {
        params: {
          fieldIds: missingIds.join(',')
        }
      })
      const payload = data && typeof data === 'object' ? data : {}
      missingIds.forEach((id) => {
        const rows = Array.isArray(payload[id]) ? payload[id] : []
        const normalized = rows.map((item) => ({
          ...item,
          idKey: toId(item && item.id),
          displayName: cycleLabel(item)
        }))
        cache.set(id, normalized)
      })
    } catch (error) {
      missingIds.forEach((id) => {
        if (!cache.has(id)) cache.set(id, [])
      })
    }
  }
  cycleCache.value = cache
  return ids.reduce((map, id) => {
    map.set(id, cache.get(id) || [])
    return map
  }, new Map())
}

async function loadTownships() {
  try {
    const rows = await request.get('/meta/options/townships')
    townshipOptions.value = Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
  } catch (error) {
    townshipOptions.value = []
  }
}

async function loadFieldOptions() {
  try {
    const data = await request.get('/fields', {
      params: {
        page: 1,
        pageSize: 500,
        includeDisabled: true
      }
    })
    const records = (data && data.records) || []
    fieldOptions.value = records.map((item) => ({
      id: toId(item && item.id),
      name: item && item.name
        ? `${item.name}${Number(item && item.enabled) === 0 ? '（禁用）' : ''}`
        : `田块#${toId(item && item.id)}`
    }))
  } catch (error) {
    fieldOptions.value = []
  }
}

async function ensureCyclesForField(fieldId) {
  const key = toId(fieldId)
  if (!key) return []
  await ensureCyclesForFields([key])
  return cyclesByFieldId(key)
}

async function onFilterFieldChange() {
  if (!toId(filters.fieldId)) {
    filters.cycleId = ''
    return
  }
  await ensureCyclesForField(filters.fieldId)
  const cycles = cyclesByFieldId(filters.fieldId)
  if (!cycles.some((item) => toId(item.idKey) === toId(filters.cycleId))) {
    filters.cycleId = ''
  }
}

async function loadFieldCards(nextPage = fieldCardPage.value) {
  loadingFieldCards.value = true
  try {
    fieldCardPage.value = Number(nextPage || 1)
    const data = await request.get('/fields', {
      params: cleanParams({
        page: fieldCardPage.value,
        pageSize: fieldCardPageSize.value,
        keyword: filters.keyword,
        township: filters.township,
        includeDisabled: true
      })
    })

    fieldCards.value = (data && data.records) || []
    fieldCardTotal.value = Number((data && data.total) || 0)

    const { startDate, endDate } = toRangeParams()
    const meta = {}
    const fieldIds = fieldCards.value.map((field) => toId(field && field.id)).filter(Boolean)
    fieldIds.forEach((fieldId) => {
      meta[fieldId] = { count: 0, preview: [] }
    })
    if (fieldIds.length) {
      try {
        const grouped = await request.get('/farm-records/grouped', {
          params: cleanParams({
            fieldIdList: fieldIds.join(','),
            startDate,
            endDate,
            maxGroups: Math.min(1000, Math.max(200, fieldIds.length * 80))
          })
        })
        const rowsInput = Array.isArray(grouped) ? grouped : []
        rowsInput.forEach((item) => {
          const fieldId = toId(item && item.fieldId)
          if (!fieldId) return
          if (!meta[fieldId]) meta[fieldId] = { count: 0, preview: [] }
          meta[fieldId].count += Number((item && item.recordCount) || 0)
          if (meta[fieldId].preview.length >= 4) return
          meta[fieldId].preview.push({
            key: `${fieldId}-${meta[fieldId].preview.length}`,
            title: `${item && item.cycleName ? item.cycleName : '未命名计划'} · ${item && item.latestStepName ? item.latestStepName : '最新作业'}`,
            time: normalizeDateTime(item && item.latestWorkDate) || '-'
          })
        })
      } catch (error) {
        fieldIds.forEach((fieldId) => {
          meta[fieldId] = { count: 0, preview: [] }
        })
      }
    }
    fieldRecordMeta.value = meta
  } catch (error) {
    fieldCards.value = []
    fieldCardTotal.value = 0
    fieldRecordMeta.value = {}
    ElMessage.error(error.message || '田块记录加载失败')
  } finally {
    loadingFieldCards.value = false
  }
}

function fieldRecordCount(fieldId) {
  const key = toId(fieldId)
  const row = fieldRecordMeta.value[key]
  return row ? Number(row.count || 0) : 0
}

function fieldRecordPreview(fieldId) {
  const key = toId(fieldId)
  const row = fieldRecordMeta.value[key]
  return row && Array.isArray(row.preview) ? row.preview : []
}

function openFieldDetail(row) {
  const fieldId = toId(row && (row.fieldId || row.id))
  if (!fieldId) return
  router.push({
    path: `/farm-records-manage/field/${fieldId}`,
    query: {
      fieldName: String((row && (row.fieldName || row.name)) || '').trim() || undefined
    }
  })
}

async function resolveAllRowLabels(records) {
  const fieldIds = Array.from(new Set((Array.isArray(records) ? records : []).map((item) => toId(item && item.fieldId)).filter(Boolean)))
  await ensureCyclesForFields(fieldIds)

  return (Array.isArray(records) ? records : []).map((item) => {
    const fieldId = toId(item && item.fieldId)
    const cycleId = toId(item && item.cycleId)
    const cycle = findCycleById(fieldId, cycleId)
    return {
      ...item,
      workDate: normalizeDateTime(item && item.workDate),
      fieldName: fieldNameById(fieldId),
      cycleName: cycle ? cycle.displayName : cycleDisplayNameById(fieldId, cycleId),
      cycleStatusLabel: cycle ? cycleStatusLabel(cycle.status) : '',
      cycleStatusTagType: cycle ? cycleStatusTagType(cycle.status) : 'info',
      cycleIsCurrent: cycle ? Number(cycle.isCurrent || 0) : 0,
      extraParamEntries: parseExtraEntries(item && item.extraJson, item && item.extraLabelMap, item && item.extraValueLabelMap)
    }
  })
}

async function loadAllRecords(nextPage = allPage.value) {
  loadingAll.value = true
  try {
    allPage.value = Number(nextPage || 1)
    const { startDate, endDate } = toRangeParams()
    const data = await request.get('/farm-records', {
      params: cleanParams({
        page: allPage.value,
        pageSize: allPageSize.value,
        township: filters.township,
        fieldId: filters.fieldId,
        cycleId: filters.cycleId,
        startDate,
        endDate
      })
    })
    const records = (data && data.records) || []
    allRows.value = await resolveAllRowLabels(records)
    allTotal.value = Number((data && data.total) || 0)
    clearAllTableSelection()
  } catch (error) {
    allRows.value = []
    allTotal.value = 0
    clearAllTableSelection()
    ElMessage.error(error.message || '记录加载失败')
  } finally {
    loadingAll.value = false
  }
}

function onFieldCardPageSizeChange(size) {
  fieldCardPageSize.value = Number(size || 12)
  loadFieldCards(1)
}

function onAllPageSizeChange(size) {
  allPageSize.value = Number(size || 10)
  loadAllRecords(1)
}

function resetRecordForm() {
  recordForm.id = null
  recordForm.fieldId = ''
  recordForm.cycleId = ''
  recordForm.stepId = ''
  recordForm.workDate = ''
  recordForm.operatorUserId = ''
  recordForm.operatorName = ''
  recordForm.weather = ''
  recordForm.temperature = ''
  recordForm.weatherLocation = ''
  recordForm.humidity = ''
  recordForm.windDirection = ''
  recordForm.windPower = ''
  recordForm.weatherReportTime = ''
  recordForm.notes = ''
  recordForm.imageAssets = []
  dialogCycleOptions.value = []
  resetRecordProcessState()
}

async function onDialogFieldChange(value, options = {}) {
  const fieldId = toId(value)
  if (!fieldId) {
    dialogCycleOptions.value = []
    recordForm.cycleId = ''
    resetRecordProcessState()
    return
  }
  const cycles = await ensureCyclesForField(fieldId)
  dialogCycleOptions.value = cycles
  const preferredCycleId = toId(options.preferredCycleId)
  if (preferredCycleId && cycles.some((item) => toId(item.idKey) === preferredCycleId)) {
    recordForm.cycleId = preferredCycleId
  } else if (!cycles.some((item) => toId(item.idKey) === toId(recordForm.cycleId))) {
    recordForm.cycleId = ''
  }
  await loadRecordProcess(options.preferredStepId, options.preferredExtra)
}

async function onDialogCycleChange() {
  await loadRecordProcess()
}

async function openCreateRecord() {
  resetRecordForm()
  recordEditMode.value = false
  if (toId(filters.fieldId)) {
    recordForm.fieldId = toId(filters.fieldId)
    await onDialogFieldChange(recordForm.fieldId)
  }
  recordDialogVisible.value = true
}

async function openEditRecord(row) {
  const id = toId(row && row.id)
  if (!id) return
  try {
    const detail = await request.get(`/farm-records/${id}`)
    resetRecordForm()
    recordEditMode.value = true
    recordForm.id = id
    recordForm.fieldId = toId(detail && detail.fieldId)
    recordForm.cycleId = toId(detail && detail.cycleId)
    recordForm.stepId = toId(detail && detail.stepId)
    recordForm.workDate = normalizeDateTime(detail && detail.workDate)
    recordForm.operatorUserId = toId(detail && detail.operatorUserId)
    recordForm.operatorName = (detail && detail.operatorName) || ''
    recordForm.weather = (detail && detail.weather) || ''
    recordForm.temperature = (detail && detail.temperature) || ''
    recordForm.weatherLocation = (detail && detail.weatherLocation) || ''
    recordForm.humidity = (detail && detail.humidity) || ''
    recordForm.windDirection = (detail && detail.windDirection) || ''
    recordForm.windPower = (detail && detail.windPower) || ''
    recordForm.weatherReportTime = (detail && detail.weatherReportTime) || ''
    recordForm.notes = (detail && detail.notes) || ''
    recordForm.imageAssets = await loadRecordImages(id)
    await onDialogFieldChange(recordForm.fieldId, {
      preferredCycleId: recordForm.cycleId,
      preferredStepId: recordForm.stepId,
      preferredExtra: parseExtraJsonObject(detail && detail.extraJson)
    })
    recordDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '记录详情加载失败')
  }
}

async function saveRecord() {
  const fieldId = toId(recordForm.fieldId)
  const workDate = String(recordForm.workDate || '').trim()

  if (!fieldId) {
    ElMessage.warning('请选择所属田块')
    return
  }
  if (!workDate) {
    ElMessage.warning('请选择作业时间')
    return
  }
  if (recordDynamicFields.value.some((item) => item && item.required && !String((recordDynamicValues.value || {})[item.key] || '').trim())) {
    ElMessage.warning('请补全必填步骤参数')
    return
  }

  savingRecord.value = true
  try {
    const payload = {
      fieldId,
      cycleId: toId(recordForm.cycleId) || null,
      stepId: toId(recordForm.stepId) || null,
      workDate,
      operatorUserId: toId(recordForm.operatorUserId) || null,
      operatorName: recordForm.operatorName || null,
      notes: recordForm.notes || null,
      weather: recordForm.weather || null,
      temperature: recordForm.temperature || null,
      weatherLocation: recordForm.weatherLocation || null,
      humidity: recordForm.humidity || null,
      windDirection: recordForm.windDirection || null,
      windPower: recordForm.windPower || null,
      weatherReportTime: recordForm.weatherReportTime || null,
      extraJson: buildRecordExtraJson(),
      imageAssetIds: (recordForm.imageAssets || [])
        .map((x) => String((x && x.id) || '').trim())
        .filter((x) => /^\d+$/.test(x))
    }

    if (recordEditMode.value && toId(recordForm.id)) {
      await request.put(`/farm-records/${recordForm.id}`, payload)
    } else {
      await request.post('/farm-records', payload)
    }

    recordDialogVisible.value = false
    ElMessage.success('记录保存成功')
    await Promise.all([loadAllRecords(allPage.value), loadFieldCards(fieldCardPage.value)])
  } catch (error) {
    ElMessage.error(error.message || '记录保存失败')
  } finally {
    savingRecord.value = false
  }
}

async function removeRecord(row) {
  const id = toId(row && row.id)
  if (!id) return
  try {
    await ElMessageBox.confirm('确认删除该农事记录吗？', '删除确认', { type: 'warning' })
    await request.delete(`/farm-records/${id}`)
    ElMessage.success('删除成功')
    const remainAfterDelete = Math.max(0, allTotal.value - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(allPageSize.value || 10)))
    await Promise.all([loadAllRecords(Math.min(allPage.value, maxPage)), loadFieldCards(fieldCardPage.value)])
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function runQuery() {
  if (mode.value === 'field') {
    loadFieldCards(1)
    return
  }
  loadAllRecords(1)
}

watch(
  () => mode.value,
  async (next) => {
    if (next === 'field') {
      await loadFieldCards(fieldCardPage.value)
      return
    }
    await loadAllRecords(allPage.value)
  }
)

watch(
  () => filters.fieldId,
  async (value) => {
    const fieldId = toId(value)
    if (!fieldId) {
      filters.cycleId = ''
      return
    }
    await ensureCyclesForField(fieldId)
    onFilterFieldChange()
  }
)

watch(
  () => recordForm.stepId,
  (next, prev) => {
    if (hydratingRecordProcess.value) return
    if (toId(next) === toId(prev)) return
    initRecordDynamicValues()
  }
)

onMounted(async () => {
  await Promise.all([loadTownships(), loadFieldOptions()])
  await Promise.all([loadFieldCards(1), loadAllRecords(1)])
})
</script>

<style scoped>
.farm-records-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

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

.field-crops {
  display: inline-flex;
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

.record-preview-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}

.record-preview-list {
  display: grid;
  gap: 6px;
}

.record-preview-item {
  display: grid;
  gap: 2px;
  font-size: 12px;
  color: var(--text-sub);
}

.all-record-section :deep(.el-card__body) {
  padding-top: 10px;
}

.extra-param-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.extra-param-empty {
  color: var(--text-sub);
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

.cycle-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.cycle-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-image-editor {
  display: grid;
  gap: 10px;
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
}
</style>


