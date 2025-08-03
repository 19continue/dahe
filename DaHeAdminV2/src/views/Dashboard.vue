<template>
  <div class="dashboard-page">
    <PageToolbar
      title="控制台概览"
      subtitle="以数据为主、操作为辅的工作台视图。"
      :collapsible="false"
    >
      <template #head-actions>
        <el-button @click="loadDashboard">刷新数据</el-button>
      </template>
    </PageToolbar>

    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>核心指标</span>
              <span class="card-meta">实时聚合</span>
            </div>
          </template>

          <el-table :data="metricRows" border>
            <el-table-column prop="name" label="指标" min-width="180" />
            <el-table-column prop="value" label="当前值" min-width="140" />
            <el-table-column prop="desc" label="说明" min-width="260" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>常用操作</span>
              <span class="card-meta">运营高频入口</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button @click="go('/users')">用户审核</el-button>
            <el-button @click="go('/field-manage')">田块管理</el-button>
            <el-button @click="go('/field-cycles')">种植计划</el-button>
            <el-button @click="go('/seed-manage')">种子批次</el-button>
            <el-button @click="go('/process-templates')">流程模板</el-button>
            <el-button @click="go('/exports')">记录导出</el-button>
          </div>
          <el-divider />
          <div class="system-summary">
            <div class="summary-row">
              <span>种子规则</span>
              <strong>{{ ruleText }}</strong>
            </div>
            <div class="summary-row">
              <span>资源总大小</span>
              <strong>{{ assetSizeText }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { getAssetStats } from '../api/assets'
import request from '../utils/request'

const router = useRouter()

const pendingCount = ref(0)
const fieldTotal = ref(0)
const batchTotal = ref(0)
const ruleText = ref('未配置')
const assetTotal = ref(0)
const imageAssetCount = ref(0)
const assetSizeText = ref('0 B')

const metricRows = computed(() => {
  return [
    { name: '待审核账号', value: pendingCount.value, desc: '需要管理员审核后才能访问控制台' },
    { name: '田块总数', value: fieldTotal.value, desc: '当前系统内可管理田块数量' },
    { name: '种子批次数', value: batchTotal.value, desc: '已录入并可追溯的批次总量' },
    { name: '资源总数', value: assetTotal.value, desc: '图片与文件资源总量（含外链）' },
    { name: '图片资源数', value: imageAssetCount.value, desc: '可用于封面/说明图的图片资源' },
    { name: '资源总大小', value: assetSizeText.value, desc: '资源库总占用空间' }
  ]
})

function formatSize(value) {
  const num = Number(value || 0)
  if (!(num > 0)) return '0 B'
  if (num < 1024) return `${num.toFixed(0)} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  if (num < 1024 * 1024 * 1024) return `${(num / (1024 * 1024)).toFixed(1)} MB`
  return `${(num / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function go(path) {
  router.push(path)
}

async function loadDashboard() {
  try {
    const [pending, fields, batches, rule, assetStats] = await Promise.all([
      request.get('/admin/users/pending-count').catch(() => ({ pendingCount: 0 })),
      request.get('/fields', { params: { page: 1, pageSize: 1, includeDisabled: true } }).catch(() => ({ total: 0 })),
      request.get('/seed-batches', { params: { page: 1, pageSize: 1, includeDisabled: true } }).catch(() => ({ total: 0 })),
      request.get('/seed-settings').catch(() => null),
      getAssetStats().catch(() => ({ totalCount: 0, imageCount: 0, totalSizeBytes: 0 }))
    ])

    pendingCount.value = Number((pending && pending.pendingCount) || 0)
    fieldTotal.value = Number((fields && fields.total) || 0)
    batchTotal.value = Number((batches && batches.total) || 0)
    assetTotal.value = Number((assetStats && assetStats.totalCount) || 0)
    imageAssetCount.value = Number((assetStats && assetStats.imageCount) || 0)
    assetSizeText.value = formatSize((assetStats && assetStats.totalSizeBytes) || 0)

    if (rule) {
      const sample = Number(rule.defaultSampleSize || 100)
      ruleText.value = Number(rule.fixedSampleSize) === 1 ? `固定 ${sample} 粒` : `可修改（默认 ${sample} 粒）`
    } else {
      ruleText.value = '未配置'
    }
  } catch (error) {
    console.error(error)
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quick-actions :deep(.el-button) {
  justify-content: flex-start;
}

.system-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-sub);
}

@media (max-width: 980px) {
  .dashboard-page :deep(.el-row > .el-col) {
    width: 100%;
    max-width: 100%;
    flex: 0 0 100%;
  }
}

@media (max-width: 520px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
