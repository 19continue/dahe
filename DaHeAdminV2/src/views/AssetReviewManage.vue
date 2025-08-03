<template>
  <div class="asset-review-page">
    <PageToolbar
      title="资源审核"
      subtitle="集中处理小程序上传资源的通过、驳回与驳回回收，避免在资源库大表中混合审核动作。"
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.folderPath ? `文件夹：${filters.folderPath}` : '',
        filters.reviewStatus ? `状态：${formatReviewStatus(filters.reviewStatus)}` : '',
        `待审核：${pendingCount}`,
        `已驳回：${rejectedCount}`
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="文件名/备注关键字" clearable style="width: 220px" @keyup.enter="loadRows(1)" />
        <el-select v-model="filters.folderPath" clearable filterable placeholder="文件夹" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option v-for="item in folderOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.reviewStatus" style="width: 130px">
          <el-option label="待审核" value="pending" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button @click="go(ASSET_CENTER_ROUTE)">返回资源总览</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>审核列表</span>
          <span class="card-meta">仅展示小程序上传资源</span>
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
        <el-table-column label="文件信息" min-width="260">
          <template #default="scope">
            <div class="name-cell">
              <strong>{{ scope.row.fileName || '-' }}</strong>
              <span>{{ scope.row.folderPath || '/' }}</span>
              <span v-if="scope.row.remark">{{ scope.row.remark }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="scope">
            <el-tag size="small" :type="reviewStatusTagType(scope.row.reviewStatus)">
              {{ formatReviewStatus(scope.row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核备注" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.reviewRemark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传人" width="120">
          <template #default="scope">{{ scope.row.createdByName || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="190">
          <template #default="scope">
            <div class="time-cell">
              <span>创建：{{ formatDateTime(scope.row.createdAt) }}</span>
              <span>更新：{{ formatDateTime(scope.row.updatedAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="scope">
            <div class="row-actions">
              <el-button size="small" type="success" @click="openReviewDialog(scope.row, 'approved')">
                {{ normalizeReviewStatus(scope.row.reviewStatus) === 'rejected' ? '重新通过' : '审核通过' }}
              </el-button>
              <el-button size="small" type="danger" plain @click="openReviewDialog(scope.row, 'rejected')">
                {{ normalizeReviewStatus(scope.row.reviewStatus) === 'rejected' ? '重新驳回' : '驳回' }}
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

    <el-dialog v-model="reviewDialog.visible" :title="reviewDialog.form.reviewStatus === 'approved' ? '审核通过' : '审核驳回'" width="460px">
      <el-form label-width="88px">
        <el-form-item label="资源名称">
          <el-input :model-value="reviewDialog.row?.fileName || '-'" readonly />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-tag :type="reviewDialog.form.reviewStatus === 'approved' ? 'success' : 'danger'">
            {{ reviewDialog.form.reviewStatus === 'approved' ? '通过' : '驳回并回收' }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="reviewDialog.form.reviewStatus === 'approved' ? '审核备注' : '驳回原因'">
          <el-input
            v-model="reviewDialog.form.reviewRemark"
            type="textarea"
            :rows="4"
            :placeholder="reviewDialog.form.reviewStatus === 'approved' ? '可选，补充审核说明' : '必填，驳回后资源会自动进入回收站'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="reviewDialog.submitting" @click="submitReview">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { listAdminAssets, listAssetFolders, reviewAsset } from '../api/assets'
import { ASSET_CENTER_ROUTE } from '../utils/adminRouteMap'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const folderOptions = ref([])
const pendingCount = ref(0)
const rejectedCount = ref(0)
const filters = reactive({
  keyword: '',
  folderPath: '',
  reviewStatus: 'pending'
})
const reviewDialog = reactive({
  visible: false,
  submitting: false,
  row: null,
  form: {
    reviewStatus: 'approved',
    reviewRemark: ''
  }
})

function go(path) {
  router.push(path)
}

function normalizeReviewStatus(value) {
  return String(value || '').trim().toLowerCase()
}

function formatReviewStatus(value) {
  const map = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }
  return map[normalizeReviewStatus(value)] || '-'
}

function reviewStatusTagType(value) {
  const status = normalizeReviewStatus(value)
  if (status === 'approved') return 'success'
  if (status === 'rejected') return 'danger'
  return 'warning'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

async function loadFolderOptions() {
  try {
    const data = await listAssetFolders({ recycleFlag: 0 })
    folderOptions.value = Array.isArray(data) ? data.filter(Boolean) : []
  } catch (error) {
    folderOptions.value = []
  }
}

async function loadSummary() {
  try {
    const [pendingData, rejectedData] = await Promise.all([
      listAdminAssets({ page: 1, pageSize: 1, recycleFlag: 0, sourceType: 'miniapp_upload', reviewStatus: 'pending' }),
      listAdminAssets({ page: 1, pageSize: 1, recycleFlag: 0, sourceType: 'miniapp_upload', reviewStatus: 'rejected' })
    ])
    pendingCount.value = Number((pendingData && pendingData.total) || 0)
    rejectedCount.value = Number((rejectedData && rejectedData.total) || 0)
  } catch (error) {
    pendingCount.value = 0
    rejectedCount.value = 0
  }
}

async function loadRows(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await listAdminAssets({
      page: page.value,
      pageSize: pageSize.value,
      recycleFlag: 0,
      sourceType: 'miniapp_upload',
      keyword: filters.keyword || undefined,
      folderPath: filters.folderPath || undefined,
      reviewStatus: filters.reviewStatus || 'pending'
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '资源审核列表加载失败')
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
  filters.reviewStatus = 'pending'
  loadRows(1)
}

function openReviewDialog(row, nextStatus) {
  reviewDialog.row = row
  reviewDialog.form.reviewStatus = nextStatus
  reviewDialog.form.reviewRemark = nextStatus === 'rejected' ? '' : String((row && row.reviewRemark) || '').trim()
  reviewDialog.visible = true
}

async function submitReview() {
  if (!reviewDialog.row || !reviewDialog.row.id) {
    return
  }
  if (reviewDialog.form.reviewStatus === 'rejected' && !String(reviewDialog.form.reviewRemark || '').trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  reviewDialog.submitting = true
  try {
    await reviewAsset(reviewDialog.row.id, {
      reviewStatus: reviewDialog.form.reviewStatus,
      reviewRemark: reviewDialog.form.reviewRemark,
      expectedUpdatedAt: reviewDialog.row.updatedAt
    })
    ElMessage.success(reviewDialog.form.reviewStatus === 'approved' ? '审核已通过' : '已驳回并移入回收站')
    reviewDialog.visible = false
    await Promise.all([loadRows(page.value), loadSummary()])
  } catch (error) {
    ElMessage.error(error.message || '资源审核保存失败')
  } finally {
    reviewDialog.submitting = false
  }
}

onMounted(async () => {
  await Promise.all([loadFolderOptions(), loadSummary(), loadRows(1)])
})
</script>

<style scoped>
.asset-review-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head,
.row-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-meta {
  font-size: 13px;
  color: #6b7280;
}

.name-cell,
.time-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-cell span,
.time-cell span {
  font-size: 12px;
  color: #6b7280;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
