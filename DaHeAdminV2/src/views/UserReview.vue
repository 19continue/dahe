<template>
  <div class="user-review-page">
    <PageToolbar
      title="小程序用户审核"
      subtitle="这里只处理待审核用户；已驳回、资格已回收、黑名单用户统一通过弹窗查看与放出。"
      collapsible
      :summary="toolbarSummary"
    >
      <div class="actions">
        <el-input v-model="keyword" placeholder="姓名/昵称/手机号" clearable style="width: 240px" @keyup.enter="load(1)" />
        <el-button @click="load(1)">查询</el-button>
        <el-button @click="openStatusDialog('rejected')">已驳回用户</el-button>
        <el-button @click="openStatusDialog('revoked')">已被回收资格用户</el-button>
        <el-button @click="openStatusDialog('blacklisted')">黑名单用户</el-button>
        <el-button plain class="batch-toggle-btn" :class="{ 'is-active': batchMode }" @click="toggleBatchMode">
          {{ batchMode ? '退出多选' : '多选审核' }}
        </el-button>
        <template v-if="batchMode">
          <el-button
            type="success"
            :disabled="!batchApproveCount || batchSubmitting"
            :loading="batchSubmitting"
            @click="batchApprove(false)"
          >
            批量通过（{{ batchApproveCount }}）
          </el-button>
          <el-button
            type="success"
            plain
            :disabled="!batchApproveCount || batchSubmitting"
            :loading="batchSubmitting"
            @click="batchApprove(true)"
          >
            批量通过并开控制台（{{ batchApproveCount }}）
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="!batchRejectCount || batchSubmitting"
            :loading="batchSubmitting"
            @click="batchReject"
          >
            批量驳回（{{ batchRejectCount }}）
          </el-button>
        </template>
      </div>
    </PageToolbar>

    <el-table
      ref="tableRef"
      :data="rows"
      border
      v-loading="loading"
      :row-key="(row) => row.id"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="batchMode" type="selection" width="48" />
      <el-table-column label="头像" width="78" align="center">
        <template #default="scope">
          <el-avatar v-if="scope.row.avatarUrl" :src="scope.row.avatarUrl" :size="34" />
          <el-avatar v-else :size="34">{{ userInitial(scope.row) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="编号" width="180" show-overflow-tooltip />
      <el-table-column prop="realName" label="姓名" width="120" show-overflow-tooltip />
      <el-table-column prop="nickName" label="昵称" width="120" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="108">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">{{ formatStatus(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applyReason" label="申请说明" min-width="240" show-overflow-tooltip />
      <el-table-column label="驳回原因" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.rejectReason || '—' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="196">
        <template #default="scope">
          <div class="time-cell">
            <span>创建：{{ formatDateTime(scope.row.createdAt) }}</span>
            <span>更新：{{ formatDateTime(scope.row.updatedAt) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="312" fixed="right" class-name="op-col">
        <template #default="scope">
          <div class="table-op-line">
            <el-button v-if="canApprove(scope.row)" size="small" type="success" @click="approve(scope.row, false)">
              {{ isRejected(scope.row) ? '重新通过' : '通过' }}
            </el-button>
            <el-button v-if="canApprove(scope.row)" size="small" type="success" plain @click="approve(scope.row, true)">
              {{ isRejected(scope.row) ? '重新通过并开控制台' : '通过并开控制台' }}
            </el-button>
            <el-button v-if="canReject(scope.row)" size="small" type="danger" plain @click="reject(scope.row)">
              {{ isRejected(scope.row) ? '再次驳回' : '驳回' }}
            </el-button>
            <el-button v-if="canBlacklist(scope.row)" size="small" type="danger" plain @click="blacklist(scope.row)">
              加入黑名单
            </el-button>
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
        @current-change="load"
      />
    </div>

    <el-dialog v-model="statusDialog.visible" :title="statusDialog.title" width="920px" destroy-on-close>
      <div class="actions" style="margin-bottom: 12px">
        <el-input
          v-model="statusDialog.keyword"
          placeholder="姓名/昵称/手机号"
          clearable
          style="width: 240px"
          @keyup.enter="loadStatusUsers(1)"
        />
        <el-button @click="loadStatusUsers(1)">查询</el-button>
      </div>
      <el-table :data="statusDialog.rows" border v-loading="statusDialog.loading">
        <el-table-column prop="realName" label="姓名" width="120" show-overflow-tooltip />
        <el-table-column prop="nickName" label="昵称" width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="140" show-overflow-tooltip />
        <el-table-column prop="rejectReason" label="最近原因" min-width="240" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.rejectReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="168">
          <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="144" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line">
              <el-button size="small" type="warning" plain @click="releaseStatusUser(scope.row)">
                {{ statusDialog.actionLabel }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-foot" style="margin-top: 12px">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="statusDialog.total"
          :page-size="statusDialog.pageSize"
          :current-page="statusDialog.page"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onStatusDialogPageSizeChange"
          @current-change="loadStatusUsers"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveMiniappUser, fetchAdminUsers, updateMiniappUserStatus } from '../api/adminUser'
import PageToolbar from '../components/ui/PageToolbar.vue'

const DEFAULT_REJECT_REASON = '资料不全'

const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)
const statusDialog = ref({
  visible: false,
  loading: false,
  status: 'blacklisted',
  title: '黑名单用户',
  actionLabel: '移出黑名单',
  keyword: '',
  rows: [],
  total: 0,
  page: 1,
  pageSize: 10
})

const toolbarSummary = computed(() => {
  return [
    keyword.value ? `关键词：${keyword.value}` : '',
    '状态：待审核',
    batchMode.value ? `多选：已选 ${selectedRows.value.length} 条` : ''
  ].filter(Boolean)
})

const batchApproveRows = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => canApprove(row))
})

const batchRejectRows = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => canReject(row))
})

const batchApproveCount = computed(() => batchApproveRows.value.length)
const batchRejectCount = computed(() => batchRejectRows.value.length)

function normalizeStatus(value) {
  return String(value || '').trim().toLowerCase()
}

function formatStatus(value) {
  const map = {
    pending: '待审核',
    rejected: '已驳回',
    approved: '已通过',
    revoked: '资格已收回',
    blacklisted: '黑名单'
  }
  return map[normalizeStatus(value)] || '-'
}

function statusTagType(value) {
  const text = normalizeStatus(value)
  if (text === 'pending') return 'warning'
  if (text === 'rejected') return 'danger'
  if (text === 'blacklisted') return 'danger'
  if (text === 'approved') return 'success'
  return 'info'
}

function canApprove(row) {
  const text = normalizeStatus(row && row.status)
  return text === 'pending' || text === 'rejected'
}

function canReject(row) {
  return canApprove(row)
}

function canBlacklist(row) {
  const text = normalizeStatus(row && row.status)
  return text === 'pending' || text === 'rejected'
}

function isRejected(row) {
  return normalizeStatus(row && row.status) === 'rejected'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

function userInitial(row) {
  const text = String((row && (row.realName || row.nickName)) || '').trim()
  return text ? text.slice(0, 1) : '用'
}

function clearBatchSelection() {
  selectedRows.value = []
  if (tableRef.value && typeof tableRef.value.clearSelection === 'function') {
    tableRef.value.clearSelection()
  }
}

function onSelectionChange(selection) {
  selectedRows.value = Array.isArray(selection) ? selection : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearBatchSelection()
  }
}

async function load(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await fetchAdminUsers({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: 'pending',
      reviewOnly: true
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
    clearBatchSelection()
  } catch (error) {
    ElMessage.error(error.message || '审核列表加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  load(1)
}

async function approve(row, withConsole) {
  try {
    await approveMiniappUser(row.id, {
      approve: true,
      canConsole: !!withConsole,
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success(withConsole ? '审核已通过并开通控制台' : '审核已通过')
    await load(page.value)
  } catch (error) {
    ElMessage.error(error.message || '审核通过失败')
  }
}

async function reject(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入驳回原因。默认“资料不全”，也可以清空不填；驳回后用户可重新提交资料。',
      '驳回确认',
      {
        type: 'warning',
        inputType: 'textarea',
        inputValue: DEFAULT_REJECT_REASON,
        inputPlaceholder: '默认：资料不全（可清空）'
      }
    )
    await approveMiniappUser(row.id, {
      approve: false,
      canConsole: Number(row.canConsole) === 1,
      rejectReason: value,
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success('已驳回')
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '驳回失败')
    }
  }
}

async function blacklist(row) {
  try {
    await ElMessageBox.confirm(`确认将用户“${row.realName || row.nickName || row.id}”加入黑名单吗？加入后将不能再提交申请。`, '加入黑名单确认', {
      type: 'warning'
    })
    await updateMiniappUserStatus(row.id, {
      status: 'blacklisted',
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success('已加入黑名单')
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '加入黑名单失败')
    }
  }
}

function openStatusDialog(nextStatus) {
  const normalized = normalizeStatus(nextStatus)
  const dialogMap = {
    rejected: { title: '已驳回用户', actionLabel: '恢复待审核' },
    revoked: { title: '已被回收资格用户', actionLabel: '恢复待审核' },
    blacklisted: { title: '黑名单用户', actionLabel: '移出黑名单' }
  }
  const config = dialogMap[normalized] || dialogMap.blacklisted
  statusDialog.value.status = normalized
  statusDialog.value.title = config.title
  statusDialog.value.actionLabel = config.actionLabel
  statusDialog.value.keyword = ''
  statusDialog.value.rows = []
  statusDialog.value.total = 0
  statusDialog.value.page = 1
  statusDialog.value.visible = true
  loadStatusUsers(1)
}

async function loadStatusUsers(nextPage = statusDialog.value.page) {
  statusDialog.value.loading = true
  try {
    statusDialog.value.page = Number(nextPage || 1)
    const data = await fetchAdminUsers({
      page: statusDialog.value.page,
      pageSize: statusDialog.value.pageSize,
      keyword: statusDialog.value.keyword || undefined,
      status: statusDialog.value.status,
      reviewOnly: true
    })
    statusDialog.value.rows = Array.isArray(data && data.records) ? data.records : []
    statusDialog.value.total = Number((data && data.total) || 0)
  } catch (error) {
    statusDialog.value.rows = []
    statusDialog.value.total = 0
    ElMessage.error(error.message || `${statusDialog.value.title}加载失败`)
  } finally {
    statusDialog.value.loading = false
  }
}

function onStatusDialogPageSizeChange(size) {
  statusDialog.value.pageSize = Number(size || 10)
  loadStatusUsers(1)
}

async function releaseStatusUser(row) {
  const currentStatus = normalizeStatus(statusDialog.value.status)
  const userLabel = row.realName || row.nickName || row.id
  const confirmText = currentStatus === 'blacklisted'
    ? `确认将用户“${userLabel}”移出黑名单并恢复为待审核吗？`
    : `确认将用户“${userLabel}”恢复为待审核吗？`
  const successText = currentStatus === 'blacklisted' ? '已移出黑名单' : '已恢复为待审核'
  try {
    await ElMessageBox.confirm(confirmText, `${statusDialog.value.actionLabel}确认`, {
      type: 'warning'
    })
    await updateMiniappUserStatus(row.id, {
      status: 'pending',
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success(successText)
    await Promise.all([load(page.value), loadStatusUsers(statusDialog.value.page)])
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${statusDialog.value.actionLabel}失败`)
      await Promise.all([load(page.value), loadStatusUsers(statusDialog.value.page)])
    }
  }
}

async function runBatch(actionText, rowsList, taskBuilder) {
  if (!rowsList.length) {
    return
  }
  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(rowsList.map((row) => taskBuilder(row)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await load(page.value)
    if (failedCount > 0) {
      ElMessage.warning(`${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`${actionText}成功：${successCount} 位用户`)
    }
  } finally {
    batchSubmitting.value = false
  }
}

async function batchApprove(withConsole) {
  const targets = batchApproveRows.value
  if (!targets.length) {
    ElMessage.warning('请先选择可审核通过的用户')
    return
  }
  const actionText = withConsole ? '批量通过并开通控制台' : '批量通过'
  try {
    await ElMessageBox.confirm(`确认${actionText}已选 ${targets.length} 位用户吗？`, `${actionText}确认`, { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}确认失败`)
    }
    return
  }
  await runBatch(actionText, targets, (row) => approveMiniappUser(row.id, {
    approve: true,
    canConsole: !!withConsole,
    expectedUpdatedAt: row.updatedAt
  }))
}

async function batchReject() {
  const targets = batchRejectRows.value
  if (!targets.length) {
    ElMessage.warning('请先选择可驳回的用户')
    return
  }
  const actionText = '批量驳回'
  try {
    const { value } = await ElMessageBox.prompt(
      `确认${actionText}已选 ${targets.length} 位用户吗？请输入统一驳回原因，默认“资料不全”，也可以清空不填。`,
      `${actionText}确认`,
      {
        type: 'warning',
        inputType: 'textarea',
        inputValue: DEFAULT_REJECT_REASON,
        inputPlaceholder: '默认：资料不全（可清空）'
      }
    )
    const rejectReason = value
    await runBatch(actionText, targets, (row) => approveMiniappUser(row.id, {
      approve: false,
      canConsole: Number(row.canConsole) === 1,
      rejectReason,
      expectedUpdatedAt: row.updatedAt
    }))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}确认失败`)
    }
  }
}

load(1)
</script>

<style scoped>
.user-review-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-op-line,
.time-cell {
  display: flex;
  gap: 6px;
}

.time-cell {
  flex-direction: column;
  gap: 4px;
  color: #6b7280;
  font-size: 12px;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
}
</style>
