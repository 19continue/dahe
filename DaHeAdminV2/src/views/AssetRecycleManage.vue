<template>
  <div class="asset-recycle-page">
    <PageToolbar
      title="资源回收站"
      subtitle="集中处理误删恢复和严格来源资源的最终清理。"
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.folderPath ? `文件夹：${filters.folderPath}` : '',
        filters.sourceType ? `来源：${formatSourceType(filters.sourceType)}` : '',
        filters.lockedFlag === 1 ? '仅看已锁定' : (filters.lockedFlag === 0 ? '仅看未锁定' : '')
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="文件名/备注关键字" clearable style="width: 220px" @keyup.enter="loadRows(1)" />
        <el-select v-model="filters.folderPath" clearable filterable placeholder="文件夹" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option v-for="item in folderOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.sourceType" clearable placeholder="来源" style="width: 140px">
          <el-option label="全部" value="" />
          <el-option label="后台上传" value="admin_upload" />
          <el-option label="小程序上传" value="miniapp_upload" />
          <el-option label="系统资源" value="system_upload" />
        </el-select>
        <el-select v-model="filters.lockedFlag" clearable placeholder="资源锁" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="已锁定" :value="1" />
          <el-option label="未锁定" :value="0" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button @click="go(ASSET_LIBRARY_ROUTE)">返回资源库</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>回收站列表</span>
          <span class="card-meta">共 {{ total }} 条</span>
        </div>
      </template>
      <el-table :data="rows" border v-loading="loading">
        <el-table-column label="预览" width="92">
          <template #default="scope">
            <el-image
              v-if="scope.row.fileType === 'image'"
              :src="scope.row.fileUrl"
              fit="cover"
              style="width: 54px; height: 54px; border-radius: 8px"
              :preview-src-list="[scope.row.fileUrl]"
              preview-teleported
            />
            <el-tag v-else size="small" effect="plain" type="info">文件</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="folderPath" label="文件夹" min-width="180" />
        <el-table-column label="来源" width="120">
          <template #default="scope">{{ formatSourceType(scope.row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="资源锁" width="110">
          <template #default="scope">
            <el-tag size="small" :type="isAssetLocked(scope.row) ? 'warning' : 'info'">
              {{ isAssetLocked(scope.row) ? '已锁定' : '未锁定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回收信息" min-width="190">
          <template #default="scope">
            <div class="time-cell">
              <span>回收：{{ formatDateTime(scope.row.recycledAt) }}</span>
              <span>审核：{{ formatReviewStatus(scope.row.reviewStatus) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line row-actions">
              <el-button size="small" type="primary" @click="restore(scope.row)">恢复</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="!canPurge(scope.row)"
                :title="purgeDisabledReason(scope.row)"
                @click="purge(scope.row)"
              >
                彻底删除
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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { getUser } from '../utils/auth'
import { listAdminAssets, listAssetFolders, purgeAsset, restoreAsset } from '../api/assets'
import { ASSET_LIBRARY_ROUTE } from '../utils/adminRouteMap'

const router = useRouter()
const currentUser = ref(getUser() || {})
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const folderOptions = ref([])
const filters = reactive({
  keyword: '',
  folderPath: '',
  sourceType: '',
  lockedFlag: ''
})

function go(path) {
  router.push(path)
}

function isSuperAdmin() {
  return Number((currentUser.value && currentUser.value.isSuperAdmin) || 0) === 1
}

function normalizeSourceType(sourceType) {
  const text = String(sourceType || '').trim().toLowerCase()
  if (text === 'operator_upload') return 'miniapp_upload'
  return text
}

function isAssetLocked(row) {
  return Number((row && row.lockedFlag) || 0) === 1
}

function formatSourceType(sourceType) {
  const map = {
    admin_upload: '后台上传',
    miniapp_upload: '小程序上传',
    system_upload: '系统资源'
  }
  return map[normalizeSourceType(sourceType)] || (sourceType || '-')
}

function formatReviewStatus(status) {
  const map = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }
  return map[String(status || '').trim().toLowerCase()] || '已通过'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

function canPurge(row) {
  if (!row) return false
  const sourceType = normalizeSourceType(row.sourceType)
  if (sourceType === 'miniapp_upload' && !isSuperAdmin()) {
    return false
  }
  return true
}

function purgeDisabledReason(row) {
  if (!row) return ''
  const sourceType = normalizeSourceType(row.sourceType)
  if (sourceType === 'miniapp_upload' && !isSuperAdmin()) {
    return '小程序用户上传资源仅超级管理员可彻底删除'
  }
  return ''
}

async function loadFolderOptions() {
  try {
    const rows = await listAssetFolders({ recycleFlag: 1 })
    folderOptions.value = (Array.isArray(rows) ? rows : []).filter(Boolean)
  } catch (error) {
    folderOptions.value = []
  }
}

async function loadRows(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await listAdminAssets({
      page: page.value,
      pageSize: pageSize.value,
      recycleFlag: 1,
      keyword: filters.keyword || undefined,
      folderPath: filters.folderPath || undefined,
      sourceType: filters.sourceType || undefined,
      lockedFlag: filters.lockedFlag === '' ? undefined : Number(filters.lockedFlag)
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '回收站资源加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRows(1)
}

function resetFilters() {
  filters.keyword = ''
  filters.folderPath = ''
  filters.sourceType = ''
  filters.lockedFlag = ''
  loadRows(1)
}

async function restore(row) {
  try {
    await ElMessageBox.confirm(`确认恢复资源“${row.fileName || '-'}”吗？`, '恢复确认', { type: 'info' })
    await restoreAsset(row.id)
    ElMessage.success('恢复成功')
    await loadRows(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '恢复失败')
    }
  }
}

async function purge(row) {
  if (!canPurge(row)) {
    ElMessage.warning(purgeDisabledReason(row) || '当前资源不允许彻底删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认彻底删除资源“${row.fileName || '-'}”吗？该操作不可恢复。`, '彻底删除确认', { type: 'warning' })
    await purgeAsset(row.id)
    ElMessage.success('已彻底删除')
    await loadRows(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '彻底删除失败')
    }
  }
}

onMounted(async () => {
  await Promise.all([loadFolderOptions(), loadRows(1)])
})
</script>

<style scoped>
.asset-recycle-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head,
.row-actions {
  display: flex;
  align-items: center;
}

.card-head {
  justify-content: space-between;
  gap: 12px;
}

.row-actions {
  gap: 6px;
}

.card-meta {
  font-size: 13px;
  color: #6b7280;
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
