<template>
  <div>
    <PageToolbar
      title="系统操作日志"
      subtitle="记录增删改、审核、启停与恢复等关键操作，支持按时间与结果回溯。"
      collapsible
      :summary="[
        batchMode ? `多选：已选 ${selectedRows.length} 条` : '',
        filters.keyword ? `关键字：${filters.keyword}` : '',
        filters.operationType ? `类型：${operationTypeFilterText(filters.operationType)}` : '',
        filters.successFlag ? `结果：${successFlagFilterText(filters.successFlag)}` : '',
        filters.undoStatus ? `撤销：${undoStatusFilterText(filters.undoStatus)}` : '',
        filters.userId ? `用户ID：${filters.userId}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" clearable placeholder="操作人/API/结果关键字" style="width: 220px" />
        <el-select v-model="filters.operationType" clearable placeholder="操作类型" style="width: 150px">
          <el-option label="全部" value="all" />
          <el-option v-for="item in operationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.successFlag" clearable placeholder="结果" style="width: 120px">
          <el-option label="全部" value="all" />
          <el-option label="成功" value="1" />
          <el-option label="失败" value="0" />
        </el-select>
        <el-select v-model="filters.undoStatus" clearable placeholder="撤销状态" style="width: 140px">
          <el-option label="全部" value="all" />
          <el-option label="待撤销" value="pending" />
          <el-option label="已撤销" value="applied" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-input v-model="filters.userId" clearable placeholder="用户ID" style="width: 120px" />
        <el-date-picker
          v-model="filters.dateRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <el-button
          v-if="batchMode"
          type="warning"
          :disabled="batchUndoCount <= 0 || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchUndoSelected"
        >
          批量撤销（{{ batchUndoCount }}）
        </el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-head">
          <span>日志列表</span>
          <span class="card-meta">共 {{ total }} 条<span v-if="batchMode">，已选 {{ selectedRows.length }} 条</span></span>
        </div>
      </template>

      <el-table ref="tableRef" :data="rows" border @selection-change="onSelectionChange">
        <el-table-column v-if="batchMode" type="selection" width="48" :selectable="isRowBatchSelectable" />
        <el-table-column prop="createdAt" label="时间" :width="tableWidths.createdAt" />
        <el-table-column label="操作类型" :width="tableWidths.operationType">
          <template #default="scope">
            <el-tag size="small" :type="operationTypeTag(scope.row.operationType)">{{ operationTypeText(scope.row.operationType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作摘要" :width="tableWidths.operationSummary" show-overflow-tooltip>
          <template #default="scope">
            <div class="summary-main">{{ operationSummary(scope.row) }}</div>
            <div class="cell-sub">{{ targetSummary(scope.row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="结果" :width="tableWidths.successFlag">
          <template #default="scope">
            <el-tag size="small" :type="Number(scope.row.successFlag) === 1 ? 'success' : 'danger'">
              {{ Number(scope.row.successFlag) === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="撤销状态" :width="tableWidths.undoStatus">
          <template #default="scope">
            <el-tag size="small" :type="undoStatusTag(scope.row.undoStatus)">
              {{ undoStatusText(scope.row.undoStatus) }}
            </el-tag>
            <div v-if="chainUndoHint(scope.row)" class="cell-sub chain-hint">{{ chainUndoHint(scope.row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="链式撤销" :width="tableWidths.chainUndo">
          <template #default="scope">
            <el-tag size="small" :type="chainUndoMeta(scope.row).type">{{ chainUndoMeta(scope.row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" :width="tableWidths.operatorName">
          <template #default="scope">
            <div>{{ scope.row.operatorName || '-' }}</div>
            <div class="cell-sub">ID: {{ scope.row.userId || '-' }} / {{ scope.row.roleName || roleText(scope.row.roleCode) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="apiPath" label="接口路径" :width="tableWidths.apiPath" show-overflow-tooltip />
        <el-table-column label="请求" :width="tableWidths.requestMeta">
          <template #default="scope">
            <div>{{ scope.row.httpMethod || '-' }}</div>
            <div class="cell-sub">{{ scope.row.clientIp || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="结果码" :width="tableWidths.resultCode">
          <template #default="scope">{{ scope.row.resultCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果信息" :width="tableWidths.resultMessage" show-overflow-tooltip>
          <template #default="scope">{{ resultMessageText(scope.row) }}</template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" :width="tableWidths.costMs" />
        <el-table-column label="操作" :width="tableWidths.actions" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line">
              <el-tooltip :disabled="canUndoRow(scope.row)" :content="undoDisableReason(scope.row)" placement="top">
                <span>
                  <el-button size="small" type="warning" plain :disabled="!canUndoRow(scope.row)" @click="undoRow(scope.row)">撤销</el-button>
                </span>
              </el-tooltip>
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
          :page-sizes="[10, 20, 50, 100, 200]"
          @size-change="onPageSizeChange"
          @current-change="loadRows"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import { buildAdaptiveWidths } from '../utils/tableAutoWidth'

const loading = ref(false)
const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const viewportWidth = ref(typeof window === 'undefined' ? 1400 : window.innerWidth)

const filters = reactive({
  keyword: '',
  operationType: 'all',
  successFlag: 'all',
  undoStatus: 'all',
  userId: '',
  dateRange: []
})

const operationTypeOptions = [
  { label: '新增', value: 'create' },
  { label: '更新', value: 'update' },
  { label: '排序', value: 'reorder' },
  { label: '删除', value: 'delete' },
  { label: '审核', value: 'review' },
  { label: '启停', value: 'enable_toggle' },
  { label: '恢复', value: 'restore' },
  { label: '彻底删除', value: 'purge' },
  { label: '其他', value: 'operate' }
]

const targetModuleTextMap = {
  field: '田块',
  field_reorder: '田块排序',
  crop: '作物',
  crop_reorder: '作物排序',
  farm_record: '农事记录',
  process_template: '流程模板',
  process_step: '流程步骤',
  field_cycle: '田块种植计划',
  field_cycle_current: '当前计划节点',
  seed: '种子批次',
  seed_batch: '种子批次',
  seed_test: '种子检测',
  seed_rule: '种子规则',
  user: '用户',
  dynamic_config: '动态参数配置',
  export_template: '导出模板',
  export_dict: '导出字段',
  assets: '资源',
  asset_reorder: '资源排序',
  asset_policy: '资源上传策略',
  amap_quota: '高德额度',
  amap_audit: '高德审计',
  record_policy: '农事记录策略'
}

function syncViewportWidth() {
  if (typeof window === 'undefined') return
  viewportWidth.value = window.innerWidth
}

const tableWidths = computed(() =>
  buildAdaptiveWidths({
    containerWidth: Math.max(1120, viewportWidth.value - 320),
    rows: rows.value,
    columns: [
      { key: 'createdAt', title: '时间', min: 166, max: 182, fixed: true },
      {
        key: 'operationType',
        title: '操作类型',
        min: 96,
        max: 116,
        fixed: true,
        extractor: (row) => operationTypeText(row && row.operationType)
      },
      {
        key: 'operationSummary',
        title: '操作摘要',
        min: 260,
        max: 400,
        weight: 1.42,
        extractor: (row) => `${operationSummary(row)} ${targetSummary(row)}`
      },
      { key: 'successFlag', title: '结果', min: 76, max: 84, fixed: true },
      { key: 'undoStatus', title: '撤销状态', min: 96, max: 108, fixed: true },
      {
        key: 'chainUndo',
        title: '链式撤销',
        min: 126,
        max: 164,
        extractor: (row) => `${chainUndoMeta(row).label} ${chainUndoMeta(row).hint || ''}`
      },
      {
        key: 'operatorName',
        title: '操作人',
        min: 150,
        max: 210,
        weight: 1.04,
        extractor: (row) => `${(row && row.operatorName) || ''} ${(row && row.userId) || ''} ${roleText(row && row.roleCode)}`
      },
      { key: 'apiPath', title: '接口路径', min: 160, max: 280, weight: 1.18 },
      {
        key: 'requestMeta',
        title: '请求',
        min: 120,
        max: 180,
        weight: 0.9,
        extractor: (row) => `${(row && row.httpMethod) || ''} ${(row && row.clientIp) || ''}`
      },
      { key: 'resultCode', title: '结果码', min: 86, max: 96, fixed: true },
      { key: 'resultMessage', title: '结果信息', min: 150, max: 280, weight: 1.02, extractor: (row) => resultMessageText(row) },
      { key: 'costMs', title: '耗时(ms)', min: 92, max: 106, fixed: true },
      { key: 'actions', title: '操作', min: 90, max: 90, fixed: true }
    ]
  })
)

const batchUndoCount = computed(() => selectedRows.value.filter((row) => canUndoRow(row)).length)

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((key) => {
    const value = input[key]
    if (value === null || value === undefined || value === '') return
    out[key] = value
  })
  return out
}

function operationTypeText(value) {
  const key = String(value || '').trim().toLowerCase()
  const hit = operationTypeOptions.find((item) => item.value === key)
  return hit ? hit.label : (key || '-')
}

function operationTypeFilterText(value) {
  const key = String(value || '').trim().toLowerCase()
  if (key === 'all') return '全部'
  return operationTypeText(key)
}

function successFlagFilterText(value) {
  const key = String(value || '').trim()
  if (key === 'all') return '全部'
  if (key === '1') return '成功'
  if (key === '0') return '失败'
  return '-'
}

function operationTypeTag(value) {
  const key = String(value || '').trim().toLowerCase()
  if (key === 'delete' || key === 'purge') return 'danger'
  if (key === 'enable_toggle' || key === 'restore') return 'warning'
  if (key === 'create' || key === 'update' || key === 'review' || key === 'reorder') return 'primary'
  return 'info'
}

function roleText(value) {
  const key = String(value || '').trim()
  return key || '-'
}

function normalizeText(value) {
  return String(value == null ? '' : value).trim()
}

function formatLogTime(value) {
  const text = normalizeText(value)
  if (!text) return '-'
  const normalized = text.includes('T') ? text : text.replace(/-/g, '/')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return text
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`
}

function normalizeRows(source) {
  const list = Array.isArray(source) ? source : []
  return list.map((row) => ({
    ...(row || {}),
    createdAt: formatLogTime(row && row.createdAt),
    operationType: normalizeText(row && row.operationType).toLowerCase(),
    targetModule: normalizeText(row && row.targetModule).toLowerCase()
  }))
}

function targetModuleText(value) {
  const key = normalizeText(value).toLowerCase()
  if (!key) return '系统对象'
  return targetModuleTextMap[key] || key
}

function operationSummary(row) {
  const action = operationTypeText(row && row.operationType)
  const module = targetModuleText(row && row.targetModule)
  const targetId = normalizeText(row && row.targetId)
  if (targetId) {
    return `${action} · ${module} #${targetId}`
  }
  return `${action} · ${module}`
}

function targetSummary(row) {
  const method = normalizeText(row && row.httpMethod).toUpperCase()
  const path = normalizeText(row && row.apiPath)
  if (!method && !path) return '-'
  if (!method) return path
  if (!path) return method
  return `${method} ${path}`
}

function resultMessageText(row) {
  const text = normalizeText(row && row.resultMessage)
  if (text) return text
  return Number(row && row.successFlag) === 1 ? '执行成功' : '-'
}

function undoStatusText(value) {
  const key = String(value || '').trim().toLowerCase()
  if (key === 'pending') return '待撤销'
  if (key === 'applied') return '已撤销'
  if (key === 'failed') return '失败'
  return '-'
}

function undoStatusFilterText(value) {
  const key = String(value || '').trim().toLowerCase()
  if (key === 'all') return '全部'
  return undoStatusText(key)
}

function undoStatusTag(value) {
  const key = String(value || '').trim().toLowerCase()
  if (key === 'pending') return 'warning'
  if (key === 'applied') return 'success'
  if (key === 'failed') return 'danger'
  return 'info'
}

function chainUndoMeta(row) {
  if (!row) {
    return { label: '-', type: 'info', hint: '' }
  }
  const success = Number(row.successFlag) === 1
  const undoType = normalizeText(row.undoType)
  const undoStatus = normalizeText(row.undoStatus).toLowerCase()
  const chainAllowed = row.chainUndoAllowed !== false
  const latestId = normalizeText(row.chainLatestUndoLogId)

  if (!success || !undoType) {
    return { label: '不可撤销', type: 'info', hint: '该记录不支持撤销' }
  }
  if (undoStatus === 'applied') {
    return { label: '已撤销', type: 'success', hint: '' }
  }
  if (!(undoStatus === '' || undoStatus === 'pending' || undoStatus === 'failed')) {
    return { label: '不可撤销', type: 'info', hint: '当前状态不可撤销' }
  }
  if (chainAllowed) {
    return { label: '链头可撤销', type: 'success', hint: '' }
  }
  return {
    label: '需按链撤销',
    type: 'warning',
    hint: latestId ? `请先撤销 #${latestId}` : '请先撤销更晚的记录'
  }
}

function chainUndoHint(row) {
  const meta = chainUndoMeta(row)
  if (meta.label !== '需按链撤销') {
    return ''
  }
  return normalizeText(meta.hint)
}

function undoDisableReason(row) {
  const meta = chainUndoMeta(row)
  if (meta.label === '链头可撤销') {
    return ''
  }
  return normalizeText(meta.hint) || meta.label
}

function canUndoRow(row) {
  return chainUndoMeta(row).label === '链头可撤销'
}

function compareLogIdDesc(left, right) {
  const normalizeNumericId = (row) => {
    const text = normalizeText(row && row.id)
    if (!/^\d+$/.test(text)) return ''
    const normalized = text.replace(/^0+/, '')
    return normalized || '0'
  }
  const a = normalizeNumericId(left)
  const b = normalizeNumericId(right)
  if (a && b) {
    if (a.length !== b.length) {
      return b.length - a.length
    }
    if (a === b) {
      return 0
    }
    return b.localeCompare(a)
  }
  return Number((right && right.id) || 0) - Number((left && left.id) || 0)
}

function resetFilters() {
  filters.keyword = ''
  filters.operationType = 'all'
  filters.successFlag = 'all'
  filters.undoStatus = 'all'
  filters.userId = ''
  filters.dateRange = []
  loadRows(1)
}

function onSelectionChange(selection) {
  selectedRows.value = Array.isArray(selection) ? selection : []
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

function isRowBatchSelectable(row) {
  return canUndoRow(row)
}

async function undoRow(row) {
  if (!row || !row.id || !canUndoRow(row)) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认撤销「${operationSummary(row)}」吗？`, '撤销确认', { type: 'warning' })
    await request.post(`/admin/operation-logs/${row.id}/undo`, {})
    ElMessage.success('撤销成功')
    await loadRows(page.value)
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || '撤销失败')
  }
}

async function batchUndoSelected() {
  const candidates = selectedRows.value.filter((row) => canUndoRow(row))
  if (!candidates.length) {
    ElMessage.warning('请先勾选可撤销的日志')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量撤销选中的 ${candidates.length} 条日志吗？系统将按日志ID倒序执行以尽量满足链式撤销约束。`,
      '批量撤销确认',
      { type: 'warning' }
    )
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || '批量撤销已取消')
    return
  }
  batchSubmitting.value = true
  try {
    const ordered = [...candidates].sort(compareLogIdDesc)
    let successCount = 0
    const failedRows = []
    for (const row of ordered) {
      try {
        await request.post(`/admin/operation-logs/${row.id}/undo`, {})
        successCount += 1
      } catch (error) {
        failedRows.push({
          id: row.id,
          message: error.message || '撤销失败'
        })
      }
    }
    if (failedRows.length) {
      const preview = failedRows
        .slice(0, 3)
        .map((item) => `#${item.id}: ${item.message}`)
        .join('；')
      ElMessage.warning(`批量撤销完成：成功 ${successCount} 条，失败 ${failedRows.length} 条。${preview}`)
    } else {
      ElMessage.success(`批量撤销成功，共 ${successCount} 条`)
    }
    await loadRows(page.value)
  } finally {
    batchSubmitting.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRows(1)
}

async function loadRows(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const startAt = Array.isArray(filters.dateRange) ? filters.dateRange[0] : null
    const endAt = Array.isArray(filters.dateRange) ? filters.dateRange[1] : null
    const data = await request.get('/admin/operation-logs', {
      params: cleanParams({
        page: page.value,
        pageSize: pageSize.value,
        keyword: filters.keyword,
        operationType: filters.operationType === 'all' ? undefined : filters.operationType,
        successFlag: (filters.successFlag === 'all' || filters.successFlag === '' || filters.successFlag === null || filters.successFlag === undefined)
          ? undefined
          : Number(filters.successFlag),
        undoStatus: filters.undoStatus === 'all' ? undefined : (filters.undoStatus || undefined),
        userId: filters.userId ? Number(filters.userId) : undefined,
        startAt,
        endAt
      })
    })
    rows.value = normalizeRows((data && data.records) || [])
    total.value = Number((data && data.total) || 0)
    clearSelection()
  } catch (error) {
    rows.value = []
    total.value = 0
    clearSelection()
    ElMessage.error(error.message || '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  syncViewportWidth()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncViewportWidth, { passive: true })
  }
  loadRows(1)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncViewportWidth)
  }
})
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.cell-sub {
  color: var(--text-sub);
  font-size: 12px;
}

.summary-main {
  color: var(--text-main);
  font-weight: 600;
  line-height: 1.45;
}

.chain-hint {
  margin-top: 4px;
}

.table-foot {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>

