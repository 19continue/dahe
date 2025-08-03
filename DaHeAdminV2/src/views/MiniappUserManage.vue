<template>
  <div class="miniapp-user-manage-page">
    <PageToolbar
      title="小程序用户管理"
      subtitle="这里只处理已通过用户的资格收回、控制台、启用状态与会话治理；驳回、回收、黑名单等审核状态统一在小程序用户审核中查看。"
      collapsible
      :summary="toolbarSummary"
    >
      <div class="actions">
        <el-input v-model="keyword" placeholder="姓名/昵称/手机号" clearable style="width: 240px" @keyup.enter="load(1)" />
        <el-select v-model="enabled" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="load(1)">查询</el-button>
        <el-button @click="goReview">进入小程序用户审核</el-button>
      </div>
    </PageToolbar>

    <el-table :data="rows" border v-loading="loading">
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
      <el-table-column label="状态" width="126">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">{{ formatStatus(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="控制台" width="96">
        <template #default="scope">
          <el-tag :type="Number(scope.row.canConsole) === 1 ? 'success' : 'info'" size="small">
            {{ Number(scope.row.canConsole) === 1 ? '已开通' : '未开通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可用性" width="92">
        <template #default="scope">
          <el-tag :type="isDisabled(scope.row) ? 'danger' : 'success'" size="small">
            {{ isDisabled(scope.row) ? '禁用' : '启用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applyReason" label="申请说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="驳回原因" min-width="200" show-overflow-tooltip>
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
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="scope">
          <div class="row-actions">
            <el-button
              size="small"
              @click="toggleConsole(scope.row)"
              :disabled="!canToggleConsole(scope.row)"
            >
              {{ Number(scope.row.canConsole) === 1 ? '关闭控制台' : '开通控制台' }}
            </el-button>
            <el-button
              size="small"
              :type="isDisabled(scope.row) ? 'success' : 'warning'"
              plain
              :disabled="!canToggleEnabled(scope.row)"
              @click="toggleEnabled(scope.row)"
            >
              {{ isDisabled(scope.row) ? '解除禁用' : '禁用' }}
            </el-button>
            <el-button size="small" type="info" plain @click="revokeSessions(scope.row)">强制下线</el-button>
            <el-button
              v-if="canRevokeQualification(scope.row)"
              size="small"
              type="warning"
              plain
              @click="changeMiniappStatus(scope.row, 'revoked', '收回登录资格')"
            >
              收回资格
            </el-button>
            <el-button size="small" type="danger" plain @click="removeUser(scope.row)">删除</el-button>
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
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteUserWithPayload,
  fetchAdminUsers,
  revokeUserSessions,
  updateMiniappUserStatus,
  updateUserEnabled,
  updateUserRole
} from '../api/adminUser'
import PageToolbar from '../components/ui/PageToolbar.vue'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const enabled = ref('')

const toolbarSummary = computed(() => {
  return [
    keyword.value ? `关键词：${keyword.value}` : '',
    '状态：已通过',
    enabled.value !== '' ? `可用性：${Number(enabled.value) === 1 ? '启用' : '禁用'}` : ''
  ].filter(Boolean)
})

function goReview() {
  router.push('/users')
}

function normalizeStatus(value) {
  return String(value || '').trim().toLowerCase()
}

function formatStatus(value) {
  const map = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回',
    revoked: '资格已收回',
    blacklisted: '黑名单'
  }
  return map[normalizeStatus(value)] || '-'
}

function statusTagType(value) {
  const text = normalizeStatus(value)
  if (text === 'approved') return 'success'
  if (text === 'pending') return 'warning'
  if (text === 'rejected') return 'danger'
  if (text === 'revoked') return 'warning'
  if (text === 'blacklisted') return 'danger'
  return 'info'
}

function userInitial(row) {
  const text = String((row && (row.realName || row.nickName)) || '').trim()
  return text ? text.slice(0, 1) : '用'
}

function isDisabled(row) {
  return Number(row && row.enabled) === 0
}

function isApproved(row) {
  return normalizeStatus(row && row.status) === 'approved'
}

function canToggleConsole(row) {
  return !!row && isApproved(row)
}

function canToggleEnabled(row) {
  if (isDisabled(row)) {
    return true
  }
  return isApproved(row)
}

function canRevokeQualification(row) {
  return isApproved(row)
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

async function load(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await fetchAdminUsers({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: 'approved',
      enabled: enabled.value === '' ? undefined : Number(enabled.value),
      userType: 'miniapp',
      reviewOnly: false
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '小程序用户加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  load(1)
}

async function toggleConsole(row) {
  if (!canToggleConsole(row)) {
    ElMessage.warning('仅已通过的小程序用户可开通控制台')
    return
  }
  try {
    await updateUserRole(row.id, {
      canConsole: Number(row.canConsole) !== 1,
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success('控制台权限已更新')
    await load(page.value)
  } catch (error) {
    ElMessage.error(error.message || '控制台权限更新失败')
  }
}

async function toggleEnabled(row) {
  if (!canToggleEnabled(row)) {
    ElMessage.warning('仅已通过的小程序用户可禁用')
    return
  }
  const nextEnabled = isDisabled(row)
  const actionText = nextEnabled ? '解除禁用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${actionText}用户“${row.realName || row.nickName || row.id}”吗？`, `${actionText}确认`, {
      type: 'warning'
    })
    await updateUserEnabled(row.id, {
      enabled: nextEnabled,
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success(`已${actionText}`)
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}失败`)
    }
  }
}

async function revokeSessions(row) {
  try {
    await ElMessageBox.confirm(`确认强制下线用户“${row.realName || row.nickName || row.id}”吗？`, '强制下线确认', {
      type: 'warning'
    })
    const data = await revokeUserSessions(row.id)
    const revokedCount = Number((data && data.revokedCount) || 0)
    ElMessage.success(revokedCount > 0 ? `已强制下线 ${revokedCount} 个会话` : '该用户当前无活跃会话')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '强制下线失败')
    }
  }
}

async function changeMiniappStatus(row, nextStatus, actionText) {
  try {
    await ElMessageBox.confirm(`确认${actionText}用户“${row.realName || row.nickName || row.id}”吗？`, `${actionText}确认`, {
      type: nextStatus === 'blacklisted' ? 'warning' : 'info'
    })
    await updateMiniappUserStatus(row.id, {
      status: nextStatus,
      expectedUpdatedAt: row.updatedAt
    })
    ElMessage.success(`${actionText}成功`)
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}失败`)
    }
  }
}

async function removeUser(row) {
  try {
    await ElMessageBox.confirm(`确认删除用户“${row.realName || row.nickName || row.id}”吗？`, '删除确认', {
      type: 'warning'
    })
    await deleteUserWithPayload(row.id, { expectedUpdatedAt: row.updatedAt })
    ElMessage.success('用户已删除')
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await load(Math.min(Number(page.value || 1), maxPage))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

load(1)
</script>

<style scoped>
.miniapp-user-manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.row-actions,
.time-cell {
  display: flex;
  gap: 6px;
}

.row-actions {
  flex-wrap: wrap;
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
