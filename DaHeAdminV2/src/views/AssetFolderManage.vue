<template>
  <div class="asset-folder-page">
    <PageToolbar
      title="资源目录管理"
      subtitle="目录治理独立成页，只处理目录本身的备注、保护状态、占用数量与可删除性。"
      :summary="[
        `目录数：${rows.length}`,
        `保护目录：${protectedCount}`,
        `可删除目录：${deletableCount}`
      ]"
    >
      <div class="actions">
        <el-button @click="go(ASSET_CENTER_ROUTE)">返回总览</el-button>
        <el-button @click="go(ASSET_LIBRARY_ROUTE)">返回资源库</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>新建目录</span>
          <el-button :loading="loading" @click="loadRows">刷新</el-button>
        </div>
      </template>
      <div class="create-row">
        <el-input v-model="form.folderPath" placeholder="例如：/品牌/logo" clearable />
        <el-input v-model="form.remark" placeholder="目录备注（可选）" clearable />
        <el-button type="primary" :loading="saving" @click="createFolder">保存目录</el-button>
      </div>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-head">
          <span>目录列表</span>
          <span class="card-meta">共 {{ rows.length }} 条</span>
        </div>
      </template>
      <el-table :data="rows" border>
        <el-table-column prop="folderPath" label="目录路径" min-width="220" />
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        <el-table-column prop="totalAssetCount" label="资源数" width="90" />
        <el-table-column prop="miniappAssetCount" label="小程序资源" width="110" />
        <el-table-column prop="adminAssetCount" label="后台资源" width="100" />
        <el-table-column prop="systemAssetCount" label="系统资源" width="100" />
        <el-table-column label="保护状态" width="120">
          <template #default="scope">
            <el-tag size="small" :type="Number(scope.row.protectedFlag || 0) === 1 ? 'warning' : 'info'">
              {{ Number(scope.row.protectedFlag || 0) === 1 ? '保护目录' : '普通目录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="删除状态" min-width="220">
          <template #default="scope">
            <span v-if="scope.row.deletable" class="ok-text">可删除</span>
            <span v-else class="warn-text">{{ scope.row.deleteDisabledReason || '当前不可删除' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="!scope.row.deletable || deletingPath === scope.row.folderPath"
              @click="removeFolder(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { createAssetFolder, deleteAssetFolder, listAssetFolderManageRows } from '../api/assets'
import { ASSET_CENTER_ROUTE, ASSET_LIBRARY_ROUTE } from '../utils/adminRouteMap'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const deletingPath = ref('')
const rows = ref([])
const form = reactive({
  folderPath: '',
  remark: ''
})

const protectedCount = computed(() => rows.value.filter((row) => Number((row && row.protectedFlag) || 0) === 1).length)
const deletableCount = computed(() => rows.value.filter((row) => row && row.deletable).length)

function go(path) {
  router.push(path)
}

async function loadRows() {
  loading.value = true
  try {
    const data = await listAssetFolderManageRows()
    rows.value = Array.isArray(data) ? data : []
  } catch (error) {
    rows.value = []
    ElMessage.error(error.message || '目录列表加载失败')
  } finally {
    loading.value = false
  }
}

async function createFolder() {
  const path = String(form.folderPath || '').trim()
  if (!path) {
    ElMessage.warning('请输入目录路径')
    return
  }
  saving.value = true
  try {
    await createAssetFolder({
      folderPath: path,
      remark: String(form.remark || '').trim() || null
    })
    ElMessage.success('目录已保存')
    form.folderPath = ''
    form.remark = ''
    await loadRows()
  } catch (error) {
    ElMessage.error(error.message || '目录保存失败')
  } finally {
    saving.value = false
  }
}

async function removeFolder(row) {
  if (!(row && row.deletable)) {
    ElMessage.warning((row && row.deleteDisabledReason) || '当前目录不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除目录“${row.folderPath}”吗？`, '目录删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '目录删除失败')
    }
    return
  }
  deletingPath.value = row.folderPath
  try {
    await deleteAssetFolder(row.folderPath)
    ElMessage.success('目录已删除')
    await loadRows()
  } catch (error) {
    ElMessage.error(error.message || '目录删除失败')
  } finally {
    deletingPath.value = ''
  }
}

onMounted(loadRows)
</script>

<style scoped>
.asset-folder-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head,
.create-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-head {
  justify-content: space-between;
}

.card-meta {
  font-size: 13px;
  color: #6b7280;
}

.create-row {
  width: 100%;
}

.ok-text {
  color: #059669;
}

.warn-text {
  color: #b45309;
}
</style>
