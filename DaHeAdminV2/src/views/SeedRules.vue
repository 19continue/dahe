<template>
  <div>
    <PageToolbar
      title="种子检测规则"
      subtitle="定义样本数量策略，影响批次检测录入与统计口径。"
      :collapsible="false"
    />
    <el-card style="max-width: 680px" v-loading="loading">
      <el-form label-width="130px">
        <el-form-item label="固定样本数">
          <el-switch v-model="fixedMode" />
        </el-form-item>
        <el-form-item label="默认样本数(粒)">
          <el-input-number v-model="defaultSample" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存规则</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const loading = ref(false)
const saving = ref(false)
const fixedMode = ref(true)
const defaultSample = ref(100)
const remark = ref('')

async function load() {
  loading.value = true
  try {
    const data = await request.get('/seed-settings')
    fixedMode.value = Number((data && data.fixedSampleSize) || 1) === 1
    defaultSample.value = Number((data && data.defaultSampleSize) || 100)
    remark.value = (data && data.remark) || ''
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await request.put('/seed-settings', {
      fixedSampleSize: fixedMode.value ? 1 : 0,
      defaultSampleSize: Number(defaultSample.value || 100),
      remark: remark.value
    })
    ElMessage.success('保存成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
