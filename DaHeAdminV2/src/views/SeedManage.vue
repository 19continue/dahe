<template>
  <div>
    <PageToolbar
      title="种子批次管理"
      subtitle="批次与检测记录联动维护，支持动态参数扩展。"
      collapsible
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.cropFilterKey ? `作物品种：${resolveCropFilterLabel(filters.cropFilterKey)}` : '',
        filters.enabled !== '' ? `状态：${Number(filters.enabled) === 1 ? '启用' : '禁用'}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="批次号关键字" clearable style="width: 180px" />
        <el-select v-model="filters.cropFilterKey" clearable filterable placeholder="作物/品种筛选" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option-group v-for="group in cropFilterGroupOptions" :key="group.cropType" :label="group.cropType">
            <el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" />
          </el-option-group>
        </el-select>
        <el-select v-model="filters.enabled" clearable placeholder="状态筛选" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="loadBatches(1)">查询</el-button>
        <el-button type="primary" @click="openCreateBatch">新增批次</el-button>
      </div>
    </PageToolbar>

    <el-tabs v-model="activeViewTab" class="seed-view-tabs">
      <el-tab-pane label="批次管理" name="batches">
        <section class="seed-pane seed-pane-list">
        <div class="pane-head">
          <div>
            <span>批次列表</span>
            <span v-if="batchMode" class="card-meta">已选 {{ selectedBatchRows.length }} 条</span>
            <span class="card-meta">共 {{ batchTotal }} 条</span>
          </div>
          <div>
            <el-button :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
              {{ batchMode ? '退出多选' : '多选操作' }}
            </el-button>
            <el-button
              v-if="batchMode"
              type="danger"
              plain
              :disabled="!batchDeleteCount || batchDeleting || !isAdmin"
              :loading="batchDeleting"
              @click="onBatchListDelete"
            >
              删除已选（{{ batchDeleteCount }}）
            </el-button>
          </div>
        </div>
        <el-table
          ref="batchTableRef"
          :data="batches"
          border
          v-loading="loadingBatches"
          @row-click="onBatchRowClick"
          @selection-change="onBatchSelectionChange"
        >
          <el-table-column
            v-if="batchMode"
            type="selection"
            width="46"
            align="center"
            :selectable="isBatchRowSelectable"
          />
          <el-table-column prop="batchCode" label="批次号" min-width="160" />
          <el-table-column label="作物品种" min-width="180">
            <template #default="scope">
              <CropPairTags :crop-type="scope.row.cropType" :crop-variety="scope.row.varietyName" empty-text="未配置" />
            </template>
          </el-table-column>
          <el-table-column prop="productionDate" label="生产日期" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag size="small" :type="isBatchDisabled(scope.row) ? 'info' : 'success'">
                {{ isBatchDisabled(scope.row) ? '禁用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="扩展参数" min-width="170">
            <template #default="scope">
              <div v-if="parseExtraEntries(scope.row.extraJson, scope.row.extraLabelMap, scope.row.extraValueLabelMap).length" class="extra-param-tags">
                <el-tag
                  v-for="item in parseExtraEntries(scope.row.extraJson, scope.row.extraLabelMap, scope.row.extraValueLabelMap)"
                  :key="`batch-${scope.row.id}-${item.key}`"
                  size="small"
                  effect="plain"
                >
                  {{ item.displayLabel }}:{{ item.value }}
                </el-tag>
              </div>
              <span v-else class="extra-text">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="264" fixed="right" class-name="op-col">
            <template #default="scope">
              <div class="table-op-line">
                <el-button size="small" @click.stop="goBatchTests(scope.row)">检测记录</el-button>
                <el-button
                  size="small"
                  :type="isBatchDisabled(scope.row) ? 'success' : 'warning'"
                  plain
                  :disabled="!isAdmin"
                  @click.stop="toggleBatchEnabled(scope.row)"
                >
                  {{ isBatchDisabled(scope.row) ? '启用' : '禁用' }}
                </el-button>
                <el-button size="small" type="primary" @click.stop="openEditBatch(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" plain :disabled="!isAdmin" @click.stop="deleteBatch(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-foot">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="batchTotal"
            :page-size="batchPageSize"
            :current-page="batchPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onBatchPageSizeChange"
            @current-change="loadBatches"
          />
        </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="检测记录" name="tests">
        <section class="seed-pane seed-pane-detail">
        <div class="pane-head">
          <span v-if="selectedBatch">检测记录：{{ selectedBatch.batchCode }}</span>
          <span v-else>请先选择批次</span>
          <div>
            <el-button :type="testMode ? 'primary' : 'default'" plain :disabled="!selectedBatch" @click="toggleTestMode">
              {{ testMode ? '退出多选' : '多选操作' }}
            </el-button>
            <el-button
              v-if="testMode && selectedBatch"
              type="danger"
              plain
              :disabled="!testDeleteCount || testDeleting || !isAdmin"
              :loading="testDeleting"
              @click="onTestBatchDelete"
            >
              删除已选（{{ testDeleteCount }}）
            </el-button>
            <el-button type="primary" :disabled="!selectedBatch" @click="openCreateTest">新增检测</el-button>
            <el-button :disabled="!selectedBatch" @click="loadTests">刷新</el-button>
          </div>
        </div>

        <div v-if="selectedBatch" class="batch-meta">
          <CropPairTags :crop-type="selectedBatch.cropType" :crop-variety="selectedBatch.varietyName" empty-text="未配置" />
          <el-tag size="small" :type="isBatchDisabled(selectedBatch) ? 'info' : 'success'">{{ isBatchDisabled(selectedBatch) ? '禁用' : '启用' }}</el-tag>
          <span>生产日期：{{ selectedBatch.productionDate || '-' }}</span>
        </div>

        <el-table
          v-if="selectedBatch"
          ref="testTableRef"
          :data="pagedTests"
          border
          v-loading="loadingTests"
          @selection-change="onTestSelectionChange"
        >
          <el-table-column
            v-if="testMode"
            type="selection"
            width="46"
            align="center"
            :selectable="isTestRowSelectable"
          />
          <el-table-column prop="testDate" label="检测日期" width="120" />
          <el-table-column label="芽率" min-width="210">
            <template #default="scope">
              <div class="seed-test-germination">
                <div class="seed-test-germination-rate">
                  {{ scope.row.germinationRate != null ? `${scope.row.germinationRate}%` : '-' }}
                </div>
                <div class="seed-test-germination-meta">
                  样本 {{ scope.row.sampleCount != null ? scope.row.sampleCount : '-' }} 粒
                  <span class="seed-test-germination-dot">·</span>
                  发芽 {{ scope.row.germinationCount != null ? scope.row.germinationCount : '-' }} 粒
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="moisture" label="水分(%)" width="95" />
          <el-table-column prop="purity" label="纯度(%)" width="95" />
          <el-table-column prop="cleanliness" label="净度(%)" width="95" />
          <el-table-column prop="testerName" label="检测员" width="110" />
          <el-table-column label="扩展参数" min-width="170">
            <template #default="scope">
              <div v-if="parseExtraEntries(scope.row.extraJson, scope.row.extraLabelMap, scope.row.extraValueLabelMap).length" class="extra-param-tags">
                <el-tag
                  v-for="item in parseExtraEntries(scope.row.extraJson, scope.row.extraLabelMap, scope.row.extraValueLabelMap)"
                  :key="`test-${scope.row.id}-${item.key}`"
                  size="small"
                  effect="plain"
                >
                  {{ item.displayLabel }}:{{ item.value }}
                </el-tag>
              </div>
              <span v-else class="extra-text">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" />
          <el-table-column label="操作" width="152" fixed="right" class-name="op-col">
            <template #default="scope">
              <div class="table-op-line">
                <el-button size="small" type="primary" @click="openEditTest(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" plain :disabled="!isAdmin" @click="deleteTest(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="selectedBatch" class="table-foot">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="testTotal"
            :page-size="testPageSize"
            :current-page="testPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onTestPageSizeChange"
            @current-change="onTestPageChange"
          />
        </div>
        <el-empty v-else description="请先在“批次管理”中选择一个批次" :image-size="52" />
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="batchDialogVisible" :title="batchEditMode ? '编辑种子批次' : '新增种子批次'" width="780px">
      <el-form label-width="96px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="作物">
              <el-select v-model="batchForm.cropType" filterable style="width: 100%" placeholder="请选择已有作物" @change="onBatchCropChange">
                <el-option v-for="item in cropOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次号">
              <el-input v-model="batchForm.batchCode" placeholder="例如：DH-2026-CORN-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品种名">
              <el-select v-model="batchForm.varietyName" filterable style="width: 100%" placeholder="请选择已有品种">
                <el-option v-for="item in batchVarietyOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="24">
            <el-form-item label="生产日期">
              <el-date-picker v-model="batchForm.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="24">
            <el-form-item label="启用状态">
              <el-switch v-model="batchForm.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <el-divider>动态参数</el-divider>
      <div v-if="batchDynamicFields.length" class="dynamic-grid">
        <div v-for="item in batchDynamicFields" :key="item.key" class="dynamic-item">
          <div class="dynamic-label">{{ item.label }}<span v-if="item.required" class="required">*</span></div>
          <el-input
            v-if="item.type === 'text'"
            v-model="batchDynamicValues[item.key]"
            :placeholder="item.placeholder || ''"
          />
          <div v-else-if="item.type === 'location'" class="location-inline">
            <el-input v-model="batchDynamicValues[item.key]" :placeholder="item.placeholder || '可手填或点击定位'" />
            <el-button @click="fillDynamicLocation('batch', item.key)">定位填充</el-button>
          </div>
          <el-input-number
            v-else-if="item.type === 'number'"
            v-model="batchDynamicValues[item.key]"
            :precision="2"
            :step="0.1"
            style="width: 100%"
          />
          <el-date-picker
            v-else-if="item.type === 'date'"
            v-model="batchDynamicValues[item.key]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-select
            v-else-if="item.type === 'select'"
            v-model="batchDynamicValues[item.key]"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option v-for="opt in normalizeOptions(item.options)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-input
            v-else
            v-model="batchDynamicValues[item.key]"
            :placeholder="item.placeholder || ''"
            type="textarea"
            :rows="2"
          />
        </div>
      </div>
      <el-empty v-else description="当前未配置批次动态字段" :image-size="52" />

      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBatch" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDialogVisible" :title="testEditMode ? '编辑检测记录' : '新增检测记录'" width="860px">
      <el-form label-width="96px">
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="检测日期">
              <el-date-picker v-model="testForm.testDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8" v-if="!fixedSample">
            <el-form-item label="芽率样本数">
              <el-input-number v-model="testForm.sampleCount" :min="1" :max="10000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8" v-else>
            <el-form-item label="芽率样本数">
              <el-input :model-value="`${defaultSampleCount}`" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="发芽数量">
              <el-input-number v-model="testForm.germinationCount" :min="0" :max="Number(testForm.sampleCount || defaultSampleCount)" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="芽率(%)">
              <el-input :model-value="germinationRateView" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="水分(%)">
              <el-input-number v-model="testForm.moisture" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纯度(%)">
              <el-input-number v-model="testForm.purity" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="净度(%)">
              <el-input-number v-model="testForm.cleanliness" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider>信息补充</el-divider>
      <div v-if="testDynamicFields.length" class="dynamic-grid">
        <div v-for="item in testDynamicFields" :key="item.key" class="dynamic-item">
          <div class="dynamic-label">{{ item.label }}<span v-if="item.required" class="required">*</span></div>
          <el-input v-if="item.type === 'text'" v-model="testDynamicValues[item.key]" />
          <div v-else-if="item.type === 'location'" class="location-inline">
            <el-input v-model="testDynamicValues[item.key]" :placeholder="item.placeholder || '可手填或点击定位'" />
            <el-button @click="fillDynamicLocation('test', item.key)">定位填充</el-button>
          </div>
          <el-input-number
            v-else-if="item.type === 'number'"
            v-model="testDynamicValues[item.key]"
            :precision="2"
            :step="0.1"
            style="width: 100%"
          />
          <el-date-picker
            v-else-if="item.type === 'date'"
            v-model="testDynamicValues[item.key]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-select v-else-if="item.type === 'select'" v-model="testDynamicValues[item.key]" clearable filterable style="width: 100%">
            <el-option v-for="opt in normalizeOptions(item.options)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-input v-else v-model="testDynamicValues[item.key]" type="textarea" :rows="2" />
        </div>
      </div>
      <el-empty v-else description="当前未配置检测动态字段" :image-size="52" />

      <el-form label-width="96px" class="seed-test-extra-form">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="检测员">
              <el-input v-model="testForm.testerName" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="testForm.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTest" @click="saveTest">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import CropPairTags from '../components/ui/CropPairTags.vue'
import request from '../utils/request'
import { isAdmin as isAdminUser } from '../utils/auth'

const filters = reactive({
  keyword: '',
  cropFilterKey: '',
  enabled: ''
})
const activeViewTab = ref('batches')

const loadingBatches = ref(false)
const loadingTests = ref(false)
const batches = ref([])
const batchPage = ref(1)
const batchPageSize = ref(10)
const batchTotal = ref(0)
const batchTableRef = ref(null)
const batchMode = ref(false)
const selectedBatchRows = ref([])
const batchDeleting = ref(false)
const selectedBatch = ref(null)
const tests = ref([])
const testPage = ref(1)
const testPageSize = ref(10)
const testTableRef = ref(null)
const testMode = ref(false)
const selectedTestRows = ref([])
const testDeleting = ref(false)
const cropOptions = ref([])
const varietyOptions = ref([])
const batchVarietyOptions = ref([])
const cropFilterGroupOptions = ref([])
const CROP_FILTER_DELIMITER = '||'

const batchDialogVisible = ref(false)
const batchEditMode = ref(false)
const savingBatch = ref(false)
const batchForm = reactive({
  id: null,
  batchCode: '',
  cropType: '',
  varietyName: '',
  productionDate: '',
  remark: '',
  enabled: true
})
const batchDynamicConfigId = ref(null)
const batchDynamicFields = ref([])
const batchDynamicValues = reactive({})

const testDialogVisible = ref(false)
const testEditMode = ref(false)
const savingTest = ref(false)
const testForm = reactive({
  id: null,
  testDate: '',
  sampleCount: 100,
  germinationCount: null,
  moisture: null,
  purity: null,
  cleanliness: null,
  testerName: '',
  remark: ''
})
const testDynamicConfigId = ref(null)
const testDynamicFields = ref([])
const testDynamicValues = reactive({})

const fixedSample = ref(true)
const defaultSampleCount = ref(100)
const isAdmin = computed(() => isAdminUser())
const testTotal = computed(() => (Array.isArray(tests.value) ? tests.value.length : 0))
const batchDeleteCount = computed(() => {
  return selectedBatchRows.value.filter((row) => isBatchRowSelectable(row)).length
})
const testDeleteCount = computed(() => {
  return selectedTestRows.value.filter((row) => isTestRowSelectable(row)).length
})
const pagedTests = computed(() => {
  const rows = Array.isArray(tests.value) ? tests.value : []
  const size = Number(testPageSize.value || 10)
  const current = Number(testPage.value || 1)
  const from = (current - 1) * size
  return rows.slice(from, from + size)
})
const germinationRateView = computed(() => {
  const count = Number(testForm.germinationCount || 0)
  const sample = Number(testForm.sampleCount || defaultSampleCount.value || 0)
  if (!(sample > 0) || !(count >= 0)) return '-'
  return ((count * 100) / sample).toFixed(2)
})

function toUniqueStrings(rows) {
  return Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
}

function makeCropFilterValue(cropType, varietyName) {
  return `${String(cropType || '').trim()}${CROP_FILTER_DELIMITER}${String(varietyName || '').trim()}`
}

function parseCropFilterValue(value) {
  const text = String(value || '').trim()
  if (!text.includes(CROP_FILTER_DELIMITER)) {
    return { cropType: '', varietyName: '' }
  }
  const [cropType, ...rest] = text.split(CROP_FILTER_DELIMITER)
  return {
    cropType: String(cropType || '').trim(),
    varietyName: String(rest.join(CROP_FILTER_DELIMITER) || '').trim()
  }
}

function resolveCropFilterLabel(value) {
  const parsed = parseCropFilterValue(value)
  if (parsed.cropType && parsed.varietyName) {
    return `${parsed.cropType}·${parsed.varietyName}`
  }
  return parsed.cropType || parsed.varietyName || '-'
}

function ensureStringOption(targetRef, value) {
  const text = String(value || '').trim()
  if (!text) return
  if ((targetRef.value || []).includes(text)) return
  targetRef.value = [...(targetRef.value || []), text]
}

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((k) => {
    const v = input[k]
    if (v === null || v === undefined || v === '') return
    out[k] = v
  })
  return out
}

function normalizeFieldType(type) {
  const text = String(type || 'text').toLowerCase().trim()
  if (['text', 'number', 'date', 'select', 'textarea', 'location'].includes(text)) {
    return text
  }
  return 'text'
}

function parseSchema(text) {
  if (!text) return []
  try {
    const arr = JSON.parse(text)
    if (!Array.isArray(arr)) return []
    return arr
      .filter((item) => item && item.key && item.label)
      .map((item) => ({
        ...item,
        key: String(item.key).trim(),
        label: String(item.label).trim(),
        type: normalizeFieldType(item.type)
      }))
  } catch (e) {
    return []
  }
}

function rememberLabelMap(scene, configId, fields) {
  // Seed extra labels are returned by backend (`extraLabelMap`), keep this no-op for compatibility.
  void scene
  void configId
  void fields
}

function normalizeOptions(options) {
  const arr = Array.isArray(options) ? options : []
  return arr
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const label = String(item.label || item.name || item.value || '').trim()
      const value = String(item.value || item.code || item.label || '').trim()
      if (!label && !value) return null
      return {
        label: label || value,
        value: value || label
      }
    })
    .filter(Boolean)
}

function isBatchRowSelectable(row) {
  return !!row && isAdmin.value
}

function isTestRowSelectable(row) {
  return !!row && !!selectedBatch.value && isAdmin.value
}

function isBatchDisabled(row) {
  return Number((row && row.enabled) || 0) === 0
}

function clearBatchSelection() {
  selectedBatchRows.value = []
  nextTick(() => {
    if (batchTableRef.value && typeof batchTableRef.value.clearSelection === 'function') {
      batchTableRef.value.clearSelection()
    }
  })
}

function clearTestSelection() {
  selectedTestRows.value = []
  nextTick(() => {
    if (testTableRef.value && typeof testTableRef.value.clearSelection === 'function') {
      testTableRef.value.clearSelection()
    }
  })
}

function onBatchSelectionChange(rowsInput) {
  selectedBatchRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function onTestSelectionChange(rowsInput) {
  selectedTestRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearBatchSelection()
  }
}

function toggleTestMode() {
  if (!selectedBatch.value) return
  testMode.value = !testMode.value
  if (!testMode.value) {
    clearTestSelection()
  }
}

async function onBatchListDelete() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可删除批次')
    return
  }
  const ids = Array.from(new Set(
    selectedBatchRows.value
      .filter((row) => isBatchRowSelectable(row))
      .map((row) => Number(row && row.id))
      .filter((id) => Number.isFinite(id) && id > 0)
  ))
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的批次')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 个批次吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除失败')
    }
    return
  }

  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => request.delete(`/seed-batches/${id}`)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (successCount > 0) {
      if (selectedBatch.value && ids.includes(Number(selectedBatch.value.id))) {
        selectedBatch.value = null
        tests.value = []
        clearTestSelection()
      }
      const remainAfterDelete = Math.max(0, batchTotal.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(batchPageSize.value || 10)))
      await loadBatches(Math.min(batchPage.value, maxPage))
    }
    clearBatchSelection()
    if (failedCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 个，${failedCount} 个删除失败`)
      return
    }
    ElMessage.success(`已删除 ${successCount} 个批次`)
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

async function onTestBatchDelete() {
  if (!selectedBatch.value) return
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可删除检测记录')
    return
  }
  const ids = Array.from(new Set(
    selectedTestRows.value
      .filter((row) => isTestRowSelectable(row))
      .map((row) => Number(row && row.id))
      .filter((id) => Number.isFinite(id) && id > 0)
  ))
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的检测记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 条检测记录吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除失败')
    }
    return
  }

  testDeleting.value = true
  try {
    const batchId = Number(selectedBatch.value.id)
    const results = await Promise.allSettled(
      ids.map((id) => request.delete(`/seed-batches/${batchId}/tests/${id}`))
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (successCount > 0) {
      const remainAfterDelete = Math.max(0, testTotal.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(testPageSize.value || 10)))
      await loadTests(Math.min(testPage.value, maxPage))
    }
    clearTestSelection()
    if (failedCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 条，${failedCount} 条删除失败`)
      return
    }
    ElMessage.success(`已删除 ${successCount} 条检测记录`)
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    testDeleting.value = false
  }
}

function parseExtraEntries(text, extraLabelMap, extraValueLabelMap) {
  if (!text) return []
  const labelMap = extraLabelMap && typeof extraLabelMap === 'object' ? extraLabelMap : {}
  const valueLabelMap = extraValueLabelMap && typeof extraValueLabelMap === 'object' ? extraValueLabelMap : {}
  try {
    const data = JSON.parse(text)
    if (!data || typeof data !== 'object' || Array.isArray(data)) return []
    return Object.keys(data)
      .filter((key) => data[key] !== null && data[key] !== undefined && String(data[key]).trim() !== '')
      .map((key) => {
        const value = String(data[key]).trim()
        const keyText = String(key || '').trim()
        const fieldValueMap = valueLabelMap && typeof valueLabelMap[keyText] === 'object' ? valueLabelMap[keyText] : null
        const displayValue = fieldValueMap && fieldValueMap[value] != null ? String(fieldValueMap[value]).trim() || value : value
        return {
          key: keyText,
          value: displayValue,
          displayLabel: String(labelMap[keyText] || '').trim() || keyText
        }
      })
      .filter((item) => item.key && item.value)
  } catch (e) {
    return []
  }
}

function resetDynamicValues(fields, store, initial) {
  Object.keys(store).forEach((k) => delete store[k])
  ;(fields || []).forEach((f) => {
    if (initial && initial[f.key] != null) {
      store[f.key] = initial[f.key]
      return
    }
    if (f.defaultValue != null && f.defaultValue !== '') {
      store[f.key] = f.defaultValue
      return
    }
    store[f.key] = ''
  })
}

function validateDynamicFields(fields, values) {
  for (const f of fields || []) {
    if (!f.required) continue
    const value = values[f.key]
    if (value === null || value === undefined || String(value).trim() === '') {
      return `请填写 ${f.label}`
    }
  }
  return null
}

function buildDynamicPayload(fields, values) {
  const out = {}
  ;(fields || []).forEach((f) => {
    const value = values[f.key]
    if (value === null || value === undefined || String(value).trim() === '') return
    out[f.key] = value
  })
  return Object.keys(out).length ? JSON.stringify(out) : null
}

function getBrowserPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator || !navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve(pos),
      (err) => reject(err || new Error('定位失败')),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }
    )
  })
}

async function fillDynamicLocation(scene, key) {
  const fieldKey = String(key || '').trim()
  if (!fieldKey) return
  try {
    const pos = await getBrowserPosition()
    const lat = Number(pos && pos.coords ? pos.coords.latitude : 0)
    const lng = Number(pos && pos.coords ? pos.coords.longitude : 0)
    if (!(Number.isFinite(lat) && Number.isFinite(lng))) {
      ElMessage.warning('定位结果无效，请手动填写')
      return
    }
    const value = `${lat.toFixed(6)},${lng.toFixed(6)}`
    if (scene === 'batch') {
      batchDynamicValues[fieldKey] = value
    } else {
      testDynamicValues[fieldKey] = value
    }
    ElMessage.success('已填入当前位置')
  } catch (e) {
    ElMessage.warning('定位失败，请检查浏览器定位权限')
  }
}

async function loadCropOptions() {
  try {
    const rows = await request.get('/meta/options/crops')
    cropOptions.value = toUniqueStrings(rows)
  } catch (e) {
    cropOptions.value = []
  }
}

async function loadCropFilterOptions() {
  try {
    const tree = await request.get('/meta/options/crop-tree')
    cropFilterGroupOptions.value = (Array.isArray(tree) ? tree : [])
      .map((item) => {
        const cropType = String((item && item.categoryName) || '').trim()
        if (!cropType) return null
        const options = [
          {
            label: `${cropType}`,
            value: makeCropFilterValue(cropType, ''),
            cropType,
            varietyName: ''
          },
          ...toUniqueStrings((Array.isArray(item && item.varieties) ? item.varieties : []).map((x) => x && x.name)).map((varietyName) => ({
            label: `${cropType}·${varietyName}`,
            value: makeCropFilterValue(cropType, varietyName),
            cropType,
            varietyName
          }))
        ]
        return { cropType, options }
      })
      .filter(Boolean)
  } catch (e) {
    cropFilterGroupOptions.value = []
  }
}

async function loadVarietyOptions(cropName = null) {
  try {
    const rows = await request.get('/meta/options/varieties', {
      params: cleanParams({
        cropName: cropName || undefined
      })
    })
    const values = toUniqueStrings(rows)
    if (String(cropName || '').trim()) {
      batchVarietyOptions.value = values
    } else {
      varietyOptions.value = values
      if (!batchVarietyOptions.value.length) {
        batchVarietyOptions.value = values
      }
    }
  } catch (e) {
    if (String(cropName || '').trim()) {
      batchVarietyOptions.value = [...varietyOptions.value]
    } else {
      varietyOptions.value = []
      if (!batchVarietyOptions.value.length) {
        batchVarietyOptions.value = []
      }
    }
  }
}

async function onBatchCropChange(value) {
  const crop = String(value || '').trim()
  batchForm.varietyName = ''
  await loadVarietyOptions(crop || null)
}

async function loadRule() {
  try {
    const row = await request.get('/seed-settings')
    fixedSample.value = Number((row && row.fixedSampleSize) || 1) === 1
    defaultSampleCount.value = Number((row && row.defaultSampleSize) || 100)
  } catch (e) {
    fixedSample.value = true
    defaultSampleCount.value = 100
  }
}

async function loadBatchSchema(targetConfigId = null) {
  let schemaText = null
  let cfgId = targetConfigId
  if (!schemaText) {
    try {
      const current = await request.get('/dynamic-configs/current', {
        params: {
          moduleKey: 'seed',
          sceneKey: 'batch_fields',
          status: 'enabled'
        }
      })
      cfgId = cfgId || (current && current.id) || null
      schemaText = current && current.schemaJson
    } catch (e) {}
  }
  batchDynamicConfigId.value = cfgId || null
  batchDynamicFields.value = parseSchema(schemaText)
  rememberLabelMap('batch', batchDynamicConfigId.value, batchDynamicFields.value)
}

async function loadTestSchema(targetConfigId = null) {
  let schemaText = null
  let cfgId = targetConfigId
  if (!schemaText) {
    try {
      const current = await request.get('/dynamic-configs/current', {
        params: {
          moduleKey: 'seed',
          sceneKey: 'test_fields',
          status: 'enabled'
        }
      })
      cfgId = cfgId || (current && current.id) || null
      schemaText = current && current.schemaJson
    } catch (e) {}
  }
  testDynamicConfigId.value = cfgId || null
  testDynamicFields.value = parseSchema(schemaText)
  rememberLabelMap('test', testDynamicConfigId.value, testDynamicFields.value)
}

async function loadBatches(nextPage = batchPage.value) {
  loadingBatches.value = true
  try {
    batchPage.value = Number(nextPage || 1)
    const cropFilter = parseCropFilterValue(filters.cropFilterKey)
    const data = await request.get('/seed-batches', {
      params: cleanParams({
        page: batchPage.value,
        pageSize: batchPageSize.value,
        includeDisabled: true,
        keyword: filters.keyword,
        cropType: cropFilter.cropType || undefined,
        varietyName: cropFilter.varietyName || undefined,
        enabled: filters.enabled
      })
    })
    batches.value = (data && data.records) || []
    batchTotal.value = Number((data && data.total) || 0)
    const crops = toUniqueStrings(batches.value.map((x) => x.cropType))
    if (crops.length) {
      cropOptions.value = toUniqueStrings([...(cropOptions.value || []), ...crops])
    }
    const varieties = toUniqueStrings(batches.value.map((x) => x.varietyName))
    if (varieties.length) {
      varietyOptions.value = toUniqueStrings([...(varietyOptions.value || []), ...varieties])
    }
    if (!cropFilterGroupOptions.value.length && (crops.length || varieties.length)) {
      const grouped = []
      const cropSet = toUniqueStrings(batches.value.map((x) => x.cropType))
      cropSet.forEach((cropType) => {
        const rows = toUniqueStrings(
          batches.value
            .filter((x) => String((x && x.cropType) || '').trim() === cropType)
            .map((x) => x && x.varietyName)
        )
        grouped.push({
          cropType,
          options: [
            { label: `${cropType}`, value: makeCropFilterValue(cropType, ''), cropType, varietyName: '' },
            ...rows.map((varietyName) => ({
              label: `${cropType}·${varietyName}`,
              value: makeCropFilterValue(cropType, varietyName),
              cropType,
              varietyName
            }))
          ]
        })
      })
      cropFilterGroupOptions.value = grouped
    }
    if (selectedBatch.value) {
      const found = batches.value.find((x) => x.id === selectedBatch.value.id)
      if (found) {
        selectedBatch.value = found
      } else {
        selectedBatch.value = null
        tests.value = []
        clearTestSelection()
      }
    }
    clearBatchSelection()
  } catch (e) {
    clearBatchSelection()
    ElMessage.error(e.message || '批次加载失败')
  } finally {
    loadingBatches.value = false
  }
}

async function loadTests(nextPage = testPage.value) {
  if (!selectedBatch.value) return
  loadingTests.value = true
  try {
    testPage.value = Number(nextPage || 1)
    tests.value = (await request.get(`/seed-batches/${selectedBatch.value.id}/tests`)) || []
    const maxPage = Math.max(1, Math.ceil(testTotal.value / Number(testPageSize.value || 10)))
    if (testPage.value > maxPage) {
      testPage.value = maxPage
    }
    clearTestSelection()
  } catch (e) {
    ElMessage.error(e.message || '检测记录加载失败')
    tests.value = []
    clearTestSelection()
  } finally {
    loadingTests.value = false
  }
}

async function selectBatch(row) {
  selectedBatch.value = row
  testPage.value = 1
  clearTestSelection()
  await loadTests(1)
  activeViewTab.value = 'tests'
}

async function onBatchRowClick(row, column, event) {
  if (batchMode.value) return
  if (column && column.type === 'selection') return
  const target = event && event.target
  if (target && typeof target.closest === 'function') {
    if (target.closest('.el-checkbox')) return
    if (target.closest('.el-button')) return
  }
  await selectBatch(row)
}

async function goBatchTests(row) {
  if (!row || !row.id) return
  await selectBatch(row)
}

function onBatchPageSizeChange(size) {
  batchPageSize.value = Number(size || 10)
  loadBatches(1)
}

function onTestPageSizeChange(size) {
  testPageSize.value = Number(size || 10)
  const maxPage = Math.max(1, Math.ceil(testTotal.value / testPageSize.value))
  if (testPage.value > maxPage) {
    testPage.value = maxPage
  }
  clearTestSelection()
}

function onTestPageChange(nextPage) {
  testPage.value = Number(nextPage || 1)
  clearTestSelection()
}

async function openCreateBatch() {
  batchEditMode.value = false
  batchForm.id = null
  batchForm.batchCode = ''
  batchForm.cropType = ''
  batchForm.varietyName = ''
  batchForm.productionDate = ''
  batchForm.remark = ''
  batchForm.enabled = true
  batchVarietyOptions.value = [...varietyOptions.value]
  await loadBatchSchema(null)
  resetDynamicValues(batchDynamicFields.value, batchDynamicValues, null)
  batchDialogVisible.value = true
}

async function openEditBatch(row) {
  batchEditMode.value = true
  try {
    const detail = await request.get(`/seed-batches/${row.id}`)
    batchForm.id = detail.id
    batchForm.batchCode = detail.batchCode || ''
    batchForm.cropType = detail.cropType || ''
    ensureStringOption(cropOptions, batchForm.cropType)
    batchForm.varietyName = detail.varietyName || ''
    batchForm.productionDate = detail.productionDate || ''
    batchForm.remark = detail.remark || ''
    const enabledValue = detail ? detail.enabled : null
    batchForm.enabled = Number(enabledValue == null ? 1 : enabledValue) === 1
    await loadVarietyOptions(batchForm.cropType || null)
    ensureStringOption(batchVarietyOptions, batchForm.varietyName)
    if (!batchVarietyOptions.value.length) {
      batchVarietyOptions.value = [...varietyOptions.value]
    }
    if (detail.formSchema) {
      batchDynamicConfigId.value = detail.formConfigId || null
      batchDynamicFields.value = parseSchema(detail.formSchema)
      rememberLabelMap('batch', batchDynamicConfigId.value, batchDynamicFields.value)
    } else {
      await loadBatchSchema(detail.formConfigId || null)
    }
    const extra = detail.extraJson ? JSON.parse(detail.extraJson) : null
    resetDynamicValues(batchDynamicFields.value, batchDynamicValues, extra)
    batchDialogVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '批次详情加载失败')
  }
}

async function saveBatch() {
  if (!String(batchForm.batchCode || '').trim()) {
    ElMessage.warning('请填写批次号')
    return
  }
  if (!String(batchForm.cropType || '').trim()) {
    ElMessage.warning('请选择作物')
    return
  }
  if (!String(batchForm.varietyName || '').trim()) {
    ElMessage.warning('请填写品种名')
    return
  }
  const dynamicError = validateDynamicFields(batchDynamicFields.value, batchDynamicValues)
  if (dynamicError) {
    ElMessage.warning(dynamicError)
    return
  }
  savingBatch.value = true
  try {
    const payload = {
      batchCode: batchForm.batchCode.trim(),
      cropType: batchForm.cropType.trim(),
      varietyName: batchForm.varietyName.trim(),
      productionDate: batchForm.productionDate || null,
      remark: batchForm.remark || null,
      enabled: !!batchForm.enabled,
      formConfigId: batchDynamicConfigId.value || null,
      extraJson: buildDynamicPayload(batchDynamicFields.value, batchDynamicValues)
    }
    if (batchEditMode.value && batchForm.id) {
      await request.put(`/seed-batches/${batchForm.id}`, payload)
    } else {
      await request.post('/seed-batches', payload)
    }
    ElMessage.success('批次保存成功')
    batchDialogVisible.value = false
    await loadBatches()
    if (selectedBatch.value) {
      const found = batches.value.find((x) => x.id === selectedBatch.value.id)
      if (found) {
        selectedBatch.value = found
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '批次保存失败')
  } finally {
    savingBatch.value = false
  }
}

async function deleteBatch(row) {
  if (!isAdmin.value) return
  try {
    await ElMessageBox.confirm(`确认删除批次 ${row.batchCode} 吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/seed-batches/${row.id}`)
    ElMessage.success('批次已删除')
    if (selectedBatch.value && selectedBatch.value.id === row.id) {
      selectedBatch.value = null
      tests.value = []
    }
    await loadBatches()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

async function toggleBatchEnabled(row) {
  if (!isAdmin.value || !row || !row.id) return
  const nextEnabled = isBatchDisabled(row)
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${actionText}批次 ${row.batchCode} 吗？`, `${actionText}确认`, { type: 'warning' })
    await request.put(`/seed-batches/${row.id}/enabled`, { enabled: nextEnabled })
    ElMessage.success(`批次已${actionText}`)
    await loadBatches(batchPage.value)
    if (selectedBatch.value && Number(selectedBatch.value.id) === Number(row.id)) {
      selectedBatch.value = { ...selectedBatch.value, enabled: nextEnabled ? 1 : 0 }
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || `批次${actionText}失败`)
    }
  }
}

async function openCreateTest() {
  if (!selectedBatch.value) return
  testEditMode.value = false
  testForm.id = null
  testForm.testDate = ''
  testForm.sampleCount = defaultSampleCount.value
  testForm.germinationCount = null
  testForm.moisture = null
  testForm.purity = null
  testForm.cleanliness = null
  testForm.testerName = ''
  testForm.remark = ''
  await loadTestSchema(null)
  resetDynamicValues(testDynamicFields.value, testDynamicValues, null)
  testDialogVisible.value = true
}

async function openEditTest(row) {
  if (!selectedBatch.value) return
  testEditMode.value = true
  try {
    const detail = await request.get(`/seed-batches/${selectedBatch.value.id}/tests/${row.id}`)
    testForm.id = detail.id
    testForm.testDate = detail.testDate || ''
    testForm.sampleCount = Number(detail.sampleCount || defaultSampleCount.value)
    if (detail.germinationCount != null) {
      testForm.germinationCount = Number(detail.germinationCount)
    } else if (detail.germinationRate != null && Number(testForm.sampleCount) > 0) {
      testForm.germinationCount = Math.round((Number(detail.germinationRate) * Number(testForm.sampleCount)) / 100)
    } else {
      testForm.germinationCount = null
    }
    testForm.moisture = detail.moisture
    testForm.purity = detail.purity
    testForm.cleanliness = detail.cleanliness
    testForm.testerName = detail.testerName || ''
    testForm.remark = detail.remark || ''
    if (detail.formSchema) {
      testDynamicConfigId.value = detail.formConfigId || null
      testDynamicFields.value = parseSchema(detail.formSchema)
      rememberLabelMap('test', testDynamicConfigId.value, testDynamicFields.value)
    } else {
      await loadTestSchema(detail.formConfigId || null)
    }
    const extra = detail.extraJson ? JSON.parse(detail.extraJson) : null
    resetDynamicValues(testDynamicFields.value, testDynamicValues, extra)
    testDialogVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '检测详情加载失败')
  }
}

async function saveTest() {
  if (!selectedBatch.value) return
  if (!String(testForm.testDate || '').trim()) {
    ElMessage.warning('请选择检测日期')
    return
  }
  if (!fixedSample.value && !(Number(testForm.sampleCount) > 0)) {
    ElMessage.warning('芽率样本数必须大于0')
    return
  }
  const resolvedSampleCount = fixedSample.value ? Number(defaultSampleCount.value) : Number(testForm.sampleCount || 0)
  if (!(resolvedSampleCount > 0)) {
    ElMessage.warning('芽率样本数必须大于0')
    return
  }
  if (!(Number(testForm.germinationCount) >= 0)) {
    ElMessage.warning('请填写发芽数量')
    return
  }
  if (Number(testForm.germinationCount) > resolvedSampleCount) {
    ElMessage.warning('发芽数量不能大于芽率样本数')
    return
  }
  const dynamicError = validateDynamicFields(testDynamicFields.value, testDynamicValues)
  if (dynamicError) {
    ElMessage.warning(dynamicError)
    return
  }
  savingTest.value = true
  try {
    const payload = {
      testDate: testForm.testDate,
      sampleCount: fixedSample.value ? null : resolvedSampleCount,
      germinationCount: testForm.germinationCount != null ? Number(testForm.germinationCount) : null,
      moisture: testForm.moisture,
      purity: testForm.purity,
      cleanliness: testForm.cleanliness,
      testerName: testForm.testerName || null,
      remark: testForm.remark || null,
      formConfigId: testDynamicConfigId.value || null,
      extraJson: buildDynamicPayload(testDynamicFields.value, testDynamicValues)
    }
    if (testEditMode.value && testForm.id) {
      await request.put(`/seed-batches/${selectedBatch.value.id}/tests/${testForm.id}`, payload)
    } else {
      await request.post(`/seed-batches/${selectedBatch.value.id}/tests`, payload)
    }
    ElMessage.success('检测记录保存成功')
    testDialogVisible.value = false
    await loadTests()
  } catch (e) {
    ElMessage.error(e.message || '检测记录保存失败')
  } finally {
    savingTest.value = false
  }
}

async function deleteTest(row) {
  if (!selectedBatch.value) return
  try {
    await ElMessageBox.confirm(`确认删除检测记录 ${row.id} 吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/seed-batches/${selectedBatch.value.id}/tests/${row.id}`)
    ElMessage.success('检测记录已删除')
    await loadTests()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

onMounted(async () => {
  await Promise.all([loadRule(), loadCropOptions(), loadVarietyOptions(), loadCropFilterOptions(), loadBatches(1)])
})
</script>

<style scoped>
.seed-view-tabs {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-panel);
  padding: 0 10px 10px;
}

.seed-view-tabs :deep(.el-tabs__header) {
  margin: 0 0 10px;
}

.seed-pane {
  background: transparent;
}

.pane-head {
  min-height: 44px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pane-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.batch-meta {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-sub);
  font-size: 13px;
}

.dynamic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.dynamic-item {
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.dynamic-label {
  margin-bottom: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.required {
  color: var(--danger);
}

.extra-text {
  color: var(--text-sub);
  font-size: 12px;
}

.extra-param-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.location-inline {
  display: flex;
  gap: 8px;
}

.seed-test-germination-rate {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
}

.seed-test-germination-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.5;
}

.seed-test-germination-dot {
  display: inline-block;
  padding: 0 4px;
}

.seed-test-extra-form {
  margin-top: 12px;
}

.seed-pane :deep(.el-empty) {
  padding: 44px 0;
}
</style>

