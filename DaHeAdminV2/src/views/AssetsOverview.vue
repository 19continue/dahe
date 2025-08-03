<template>
  <div class="assets-overview-page">
    <PageToolbar
      title="图片与资源管理"
      subtitle="按职责进入资源库、资源审核、回收站和目录管理，避免在同一页面混合处理不同事务。"
      :summary="[
        `资源总数：${stats.totalCount || 0}`,
        `待审核：${pendingCount}`,
        `回收站：${recycleCount}`,
        `已锁定：${lockedCount}`
      ]"
    >
      <div class="actions">
        <el-button type="primary" @click="go(ASSET_LIBRARY_ROUTE)">进入资源库</el-button>
        <el-button @click="go(ASSET_REVIEW_ROUTE)">进入资源审核</el-button>
        <el-button @click="go(ASSET_RECYCLE_ROUTE)">进入回收站</el-button>
        <el-button @click="go(ASSET_FOLDERS_ROUTE)">进入目录管理</el-button>
      </div>
    </PageToolbar>

    <div class="stats-grid" v-loading="loading">
      <div class="stat-card">
        <span class="stat-label">资源总数</span>
        <strong class="stat-value">{{ stats.totalCount || 0 }}</strong>
        <span class="stat-desc">当前正常资源总量</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">图片数量</span>
        <strong class="stat-value">{{ stats.imageCount || 0 }}</strong>
        <span class="stat-desc">适合优先进入资源库管理</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">待审核资源</span>
        <strong class="stat-value">{{ pendingCount }}</strong>
        <span class="stat-desc">建议先进入资源审核页集中处理</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">回收站资源</span>
        <strong class="stat-value">{{ recycleCount }}</strong>
        <span class="stat-desc">集中处理恢复与彻底删除</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">已锁定资源</span>
        <strong class="stat-value">{{ lockedCount }}</strong>
        <span class="stat-desc">锁定资源删除前需要密码校验</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">总大小</span>
        <strong class="stat-value">{{ formatSize(stats.totalSizeBytes) }}</strong>
        <span class="stat-desc">按当前资源库统计</span>
      </div>
    </div>

    <el-row :gutter="16" class="overview-entry-grid">
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="entry-card" @click="go(ASSET_LIBRARY_ROUTE)">
          <div class="entry-head">
            <div>
              <h3>资源库</h3>
              <p>上传、登记外链、编辑、锁定、排序。</p>
            </div>
            <el-tag type="primary" effect="light">主入口</el-tag>
          </div>
          <div class="entry-meta">
            <span>正常资源：{{ stats.totalCount || 0 }}</span>
            <span>待审核：{{ pendingCount }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="entry-card" @click="go(ASSET_REVIEW_ROUTE)">
          <div class="entry-head">
            <div>
              <h3>资源审核</h3>
              <p>集中处理小程序上传资源的通过、驳回与驳回入回收站。</p>
            </div>
            <el-tag type="success" effect="light">审核入口</el-tag>
          </div>
          <div class="entry-meta">
            <span>待审核：{{ pendingCount }}</span>
            <span>审核动作与资源库拆分</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="entry-card" @click="go(ASSET_RECYCLE_ROUTE)">
          <div class="entry-head">
            <div>
              <h3>回收站</h3>
              <p>集中处理误删恢复、锁定资源删除和彻底删除。</p>
            </div>
            <el-tag type="warning" effect="light">谨慎操作</el-tag>
          </div>
          <div class="entry-meta">
            <span>回收条目：{{ recycleCount }}</span>
            <span>已锁定：{{ lockedCount }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="entry-card" @click="go(ASSET_FOLDERS_ROUTE)">
          <div class="entry-head">
            <div>
              <h3>目录管理</h3>
              <p>单独维护目录、备注、保护目录与资源占用情况。</p>
            </div>
            <el-tag effect="light">目录入口</el-tag>
          </div>
          <div class="entry-meta">
            <span>目录信息单独管理</span>
            <span>减少资源列表操作干扰</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { getAssetStats, listAdminAssets } from '../api/assets'
import {
  ASSET_FOLDERS_ROUTE,
  ASSET_LIBRARY_ROUTE,
  ASSET_RECYCLE_ROUTE,
  ASSET_REVIEW_ROUTE
} from '../utils/adminRouteMap'

const router = useRouter()
const loading = ref(false)
const pendingCount = ref(0)
const recycleCount = ref(0)
const lockedCount = ref(0)
const stats = reactive({
  totalCount: 0,
  imageCount: 0,
  fileCount: 0,
  totalSizeBytes: 0
})

function go(path) {
  router.push(path)
}

function formatSize(bytes) {
  const value = Number(bytes || 0)
  if (value <= 0) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}

async function loadSummary() {
  loading.value = true
  try {
    const [statsData, pendingData, recycleData, lockedData] = await Promise.all([
      getAssetStats(),
      listAdminAssets({ page: 1, pageSize: 1, recycleFlag: 0, reviewStatus: 'pending' }),
      listAdminAssets({ page: 1, pageSize: 1, recycleFlag: 1 }),
      listAdminAssets({ page: 1, pageSize: 1, recycleFlag: 0, lockedFlag: 1 })
    ])
    stats.totalCount = Number((statsData && statsData.totalCount) || 0)
    stats.imageCount = Number((statsData && statsData.imageCount) || 0)
    stats.fileCount = Number((statsData && statsData.fileCount) || 0)
    stats.totalSizeBytes = Number((statsData && statsData.totalSizeBytes) || 0)
    pendingCount.value = Number((pendingData && pendingData.total) || 0)
    recycleCount.value = Number((recycleData && recycleData.total) || 0)
    lockedCount.value = Number((lockedData && lockedData.total) || 0)
  } catch (error) {
    ElMessage.error(error.message || '资源总览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadSummary()
})
</script>

<style scoped>
.assets-overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card,
.entry-card {
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 18px 20px;
  background: #fff;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
}

.stat-value {
  font-size: 28px;
  color: #111827;
  font-weight: 600;
}

.stat-desc {
  font-size: 13px;
  color: #9ca3af;
}

.overview-entry-grid {
  margin: 0;
}

.entry-card {
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.entry-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}

.entry-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.entry-head h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.entry-head p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #6b7280;
}

.entry-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
  font-size: 13px;
  color: #4b5563;
}

.reference-card {
  border-radius: 18px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
