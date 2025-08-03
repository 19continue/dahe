<template>
  <div class="message-station-page">
    <PageToolbar
      title="消息通知站"
      subtitle="这里只管理当前登录后台用户自己的站内消息，可标记已读、全部已读和个人删除。"
      :summary="toolbarSummary"
    >
      <div class="actions">
        <el-select v-model="filters.noticeType" clearable placeholder="消息类型" style="width: 132px">
          <el-option label="系统通知" value="system" />
          <el-option label="消息通知" value="message" />
          <el-option label="审核通知" value="review" />
          <el-option label="状态通知" value="status" />
          <el-option label="审核申请" value="review_apply" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button :disabled="!unreadCount" type="primary" @click="markAllRead">全部已读</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>我的消息</span>
          <span class="card-meta">未读 {{ unreadCount }} 条</span>
        </div>
      </template>

      <el-table :data="rows" border v-loading="loading">
        <el-table-column label="状态" width="88">
          <template #default="scope">
            <el-tag size="small" :type="Number(scope.row.isRead) === 1 ? 'info' : 'danger'">
              {{ Number(scope.row.isRead) === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="108">
          <template #default="scope">{{ formatNoticeType(scope.row.noticeType) }}</template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="跳转路由" min-width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.routeCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="176">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="178" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line row-actions">
              <el-button
                v-if="Number(scope.row.isRead) !== 1"
                size="small"
                type="primary"
                @click="markRead(scope.row)"
              >
                标记已读
              </el-button>
              <el-button
                v-if="scope.row.routeCode"
                size="small"
                @click="openRoute(scope.row)"
              >
                前往
              </el-button>
              <el-button size="small" type="danger" plain @click="removeNotice(scope.row)">
                删除
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
          @current-change="loadRows"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import {
  deleteAdminNotice,
  fetchAdminNotices,
  markAdminNoticeRead,
  markAllAdminNoticesRead
} from '../api/adminAuth'

const NOTICE_UPDATED_EVENT = 'dahe-admin-notice-updated'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const unreadCount = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({
  noticeType: ''
})

const toolbarSummary = computed(() => {
  return [
    filters.noticeType ? `类型：${formatNoticeType(filters.noticeType)}` : '',
    `未读：${unreadCount.value}`
  ].filter(Boolean)
})

function formatNoticeType(value) {
  const map = {
    system: '系统通知',
    message: '消息通知',
    review: '审核通知',
    status: '状态通知',
    review_apply: '审核申请'
  }
  return map[String(value || '').trim().toLowerCase()] || '系统通知'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

function emitNoticeUpdated() {
  window.dispatchEvent(new Event(NOTICE_UPDATED_EVENT))
}

async function loadRows(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await fetchAdminNotices({
      page: page.value,
      pageSize: pageSize.value,
      noticeType: filters.noticeType || undefined
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
    unreadCount.value = Number((data && data.unreadCount) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    unreadCount.value = 0
    ElMessage.error(error.message || '消息通知站加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRows(1)
}

async function markRead(row) {
  try {
    await markAdminNoticeRead(row.id)
    await loadRows(page.value)
    emitNoticeUpdated()
  } catch (error) {
    ElMessage.error(error.message || '标记已读失败')
  }
}

async function markAllRead() {
  try {
    await markAllAdminNoticesRead()
    ElMessage.success('已全部标记为已读')
    await loadRows(1)
    emitNoticeUpdated()
  } catch (error) {
    ElMessage.error(error.message || '全部已读失败')
  }
}

function openRoute(row) {
  const routeCode = String((row && row.routeCode) || '').trim()
  if (!routeCode) return
  router.push(routeCode)
}

async function removeNotice(row) {
  try {
    const title = row && (row.title || row.id) ? (row.title || row.id) : '当前消息'
    await ElMessageBox.confirm(`确认删除“${title}”吗？`, '删除确认', {
      type: 'warning'
    })
    await deleteAdminNotice(row.id)
    ElMessage.success('消息已删除')
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadRows(Math.min(Number(page.value || 1), maxPage))
    emitNoticeUpdated()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

loadRows(1)
</script>

<style scoped>
.message-station-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-meta {
  font-size: 13px;
  color: #6b7280;
}

.row-actions {
  gap: 2px;
}

.row-actions :deep(.el-button--primary) {
  color: #fff !important;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
