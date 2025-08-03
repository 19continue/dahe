<template>
  <div class="user-recycle-page">
    <PageToolbar
      title="小程序用户回收站"
      subtitle="集中处理已通过用户的回收、恢复与彻底删除，避免审核页和回收治理混在一起。"
      :summary="[
        keyword ? `关键词：${keyword}` : '',
        enabled !== '' ? `可用性：${Number(enabled) === 1 ? '启用' : '禁用'}` : '',
        `回收用户：${total}`
      ]"
    >
      <div class="actions">
        <el-input v-model="keyword" placeholder="姓名/昵称/手机号/openid" clearable style="width: 240px" @keyup.enter="load(1)" />
        <el-select v-model="enabled" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="load(1)">查询</el-button>
        <el-button @click="go('/users')">返回用户审核</el-button>
      </div>
    </PageToolbar>

    <el-table :data="rows" border v-loading="loading">
      <el-table-column label="头像" width="76" align="center">
        <template #default="scope">
          <el-avatar v-if="scope.row.avatarUrl" :src="scope.row.avatarUrl" :size="34" />
          <el-avatar v-else :size="34">{{ userInitial(scope.row) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="编号" min-width="170" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="nickName" label="昵称" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="控制台" width="100">
        <template #default="scope">
          <el-tag size="small" :type="Number(scope.row.canConsole) === 1 ? 'success' : 'info'">
            {{ Number(scope.row.canConsole) === 1 ? '已开通' : '未开通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可用性" width="92">
        <template #default="scope">
          <el-tag size="small" :type="Number(scope.row.enabled) === 1 ? 'success' : 'danger'">
            {{ Number(scope.row.enabled) === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回收信息" min-width="220">
        <template #default="scope">
          <div class="time-cell">
            <span>回收时间：{{ formatDateTime(scope.row.recycledAt) }}</span>
            <span>回收备注：{{ scope.row.recycleRemark || '—' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="scope">
          <div class="row-actions">
            <el-button size="small" type="primary" @click="restore(scope.row)">恢复</el-button>
            <el-button size="small" type="info" plain @click="revokeSessions(scope.row)">强制下线</el-button>
            <el-button size="small" type="danger" plain @click="removeUser(scope.row)">彻底删除</el-button>
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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { deleteUserWithPayload, fetchAdminUsers, restoreMiniappUser, revokeUserSessions } from '../api/adminUser'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const enabled = ref('')

function go(path) {
  router.push(path)
}

function userInitial(row) {
  const text = String((row && (row.realName || row.nickName)) || '').trim()
  return text ? text.slice(0, 1) : '用'
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
      enabled: enabled.value === '' ? undefined : Number(enabled.value),
      recycleFlag: 1,
      reviewOnly: true
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '回收用户加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  load(1)
}

async function restore(row) {
  try {
    await ElMessageBox.confirm(`确认恢复用户“${row.realName || row.nickName || row.id}”吗？`, '恢复确认', { type: 'info' })
    await restoreMiniappUser(row.id, { expectedUpdatedAt: row.updatedAt })
    ElMessage.success('用户已恢复')
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '恢复失败')
    }
  }
}

async function revokeSessions(row) {
  try {
    await ElMessageBox.confirm(`确认强制下线“${row.realName || row.nickName || row.id}”吗？`, '强制下线确认', { type: 'warning' })
    await revokeUserSessions(row.id)
    ElMessage.success('已强制下线')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '强制下线失败')
    }
  }
}

async function removeUser(row) {
  try {
    await ElMessageBox.confirm(`确认彻底删除用户“${row.realName || row.nickName || row.id}”吗？该操作不可恢复。`, '彻底删除确认', { type: 'warning' })
    await deleteUserWithPayload(row.id, { expectedUpdatedAt: row.updatedAt })
    ElMessage.success('已彻底删除')
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '彻底删除失败')
    }
  }
}

onMounted(() => {
  load(1)
})
</script>

<style scoped>
.user-recycle-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.row-actions,
.time-cell {
  display: flex;
  gap: 8px;
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
