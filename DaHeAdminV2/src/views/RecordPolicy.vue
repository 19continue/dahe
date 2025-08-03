<template>
  <div>
    <PageToolbar
      title="记录权限策略"
      subtitle="定义小程序用户编辑/删除窗口，保证数据追溯安全。"
      :collapsible="false"
    >
      <div class="actions">
        <el-button @click="loadPolicy">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="savePolicy">保存策略</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
        <el-form label-width="200px">
        <el-form-item label="小程序用户可编辑时限（小时）">
          <el-input-number v-model="policy.editWindowHours" :min="0" />
          <span class="hint">0 表示不限制时间</span>
        </el-form-item>
        <el-form-item label="允许小程序用户编辑">
          <el-switch v-model="allowUpdate" />
        </el-form-item>
        <el-form-item label="允许小程序用户删除">
          <el-switch v-model="allowDelete" />
        </el-form-item>
        <el-form-item label="策略说明">
          <el-input v-model="policy.remark" type="textarea" :rows="3" placeholder="例如：超时后仅管理员可修订" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const saving = ref(false)
const policy = reactive({
  editWindowHours: 48,
  allowOperatorUpdate: 1,
  allowOperatorDelete: 1,
  remark: ''
})

const allowUpdate = computed({
  get: () => Number(policy.allowOperatorUpdate) === 1,
  set: (v) => {
    policy.allowOperatorUpdate = v ? 1 : 0
  }
})

const allowDelete = computed({
  get: () => Number(policy.allowOperatorDelete) === 1,
  set: (v) => {
    policy.allowOperatorDelete = v ? 1 : 0
  }
})

async function loadPolicy() {
  try {
    const row = await request.get('/admin/record-policy')
    policy.editWindowHours = row && row.editWindowHours != null ? Number(row.editWindowHours) : 48
    policy.allowOperatorUpdate = Number((row && (row.allowMiniappUpdate ?? row.allowOperatorUpdate)) || 0) === 1 ? 1 : 0
    policy.allowOperatorDelete = Number((row && (row.allowMiniappDelete ?? row.allowOperatorDelete)) || 0) === 1 ? 1 : 0
    policy.remark = (row && row.remark) || ''
  } catch (e) {
    ElMessage.error(e.message || '策略加载失败')
  }
}

async function savePolicy() {
  saving.value = true
  try {
    await request.put('/admin/record-policy', {
      editWindowHours: policy.editWindowHours != null ? Number(policy.editWindowHours) : 0,
      allowOperatorUpdate: policy.allowOperatorUpdate,
      allowMiniappUpdate: policy.allowOperatorUpdate,
      allowOperatorDelete: policy.allowOperatorDelete,
      allowMiniappDelete: policy.allowOperatorDelete,
      remark: policy.remark || null
    })
    ElMessage.success('策略已保存')
    await loadPolicy()
  } catch (e) {
    ElMessage.error(e.message || '策略保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadPolicy)
</script>

<style scoped>
.hint {
  margin-left: 12px;
  color: var(--text-sub);
  font-size: 12px;
}
</style>
