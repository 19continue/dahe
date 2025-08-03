<template>
  <div>
    <PageToolbar
      title="记录导出"
      subtitle="按业务条件导出农事记录与种子检测 CSV。"
      :collapsible="false"
    >
      <template #head-actions>
        <el-button @click="loadBaseData">刷新基础数据</el-button>
      </template>
    </PageToolbar>
    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">农事记录导出（CSV）</div>
          </template>
          <el-form label-width="100px">
            <el-form-item label="田块">
              <el-select v-model="farmForm.fieldId" clearable filterable style="width: 100%">
                <el-option v-for="item in fields" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="乡镇">
              <el-select v-model="farmForm.township" clearable filterable style="width: 100%">
                <el-option v-for="item in townshipOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="年份">
              <el-select v-model="farmForm.year" clearable style="width: 100%" @change="onFarmYearChange">
                <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
              </el-select>
            </el-form-item>
            <el-form-item label="导出模板">
              <el-select v-model="farmForm.templateCode" clearable style="width: 100%">
                <el-option v-for="item in farmTemplateOptions" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="快捷时间">
              <el-button @click="setFarmThisYear">今年</el-button>
              <el-button @click="setFarmThisMonth">本月</el-button>
              <el-button @click="setFarmLast30Days">近30天</el-button>
            </el-form-item>
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="farmForm.startDate"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="farmForm.endDate"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="downloadingFarm" @click="downloadFarm">导出农事记录</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">种子检测导出（CSV）</div>
          </template>
          <el-form label-width="100px">
            <el-form-item label="批次">
              <el-select v-model="seedForm.batchId" clearable filterable style="width: 100%">
                <el-option
                  v-for="item in seedBatches"
                  :key="item.id"
                  :label="`${item.batchCode} - ${item.cropType || '-'} / ${item.varietyName || '-'}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="年份">
              <el-select v-model="seedForm.year" clearable style="width: 100%" @change="onSeedYearChange">
                <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
              </el-select>
            </el-form-item>
            <el-form-item label="导出模板">
              <el-select v-model="seedForm.templateCode" clearable style="width: 100%">
                <el-option v-for="item in seedTemplateOptions" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始日期">
              <el-date-picker v-model="seedForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束日期">
              <el-date-picker v-model="seedForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="快捷时间">
              <el-button @click="setSeedThisYear">今年</el-button>
              <el-button @click="setSeedLast30Days">近30天</el-button>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="downloadingSeed" @click="downloadSeed">导出检测记录</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request, { getBaseUrl } from '../utils/request'
import { getToken } from '../utils/auth'

const fields = ref([])
const seedBatches = ref([])
const farmTemplateOptions = ref([])
const seedTemplateOptions = ref([])
const townshipOptions = ref([])
const downloadingFarm = ref(false)
const downloadingSeed = ref(false)
const nowYear = new Date().getFullYear()
const yearOptions = Array.from({ length: 6 }, (_, i) => nowYear - i)

const farmForm = reactive({
  fieldId: null,
  templateCode: 'farm_records_standard',
  township: '',
  year: nowYear,
  startDate: '',
  endDate: ''
})

const seedForm = reactive({
  batchId: null,
  templateCode: 'seed_tests_standard',
  year: nowYear,
  startDate: '',
  endDate: ''
})

function templateLabel(item) {
  if (!item) return '-'
  return `${item.templateName || item.templateCode} (v${item.versionNo || 1})`
}

function toQuery(params) {
  const query = new URLSearchParams()
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    query.set(key, String(value))
  })
  const text = query.toString()
  return text ? `?${text}` : ''
}

function setFarmThisYear() {
  const y = nowYear
  farmForm.startDate = `${y}-01-01 00:00:00`
  farmForm.endDate = `${y}-12-31 23:59:59`
  farmForm.year = y
}

function onFarmYearChange(value) {
  const year = Number(value || 0)
  if (!year) return
  farmForm.startDate = `${year}-01-01 00:00:00`
  farmForm.endDate = `${year}-12-31 23:59:59`
}

function setFarmThisMonth() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  farmForm.startDate = `${y}-${m}-01 00:00:00`
  const end = new Date(y, d.getMonth() + 1, 0).getDate()
  farmForm.endDate = `${y}-${m}-${String(end).padStart(2, '0')} 23:59:59`
}

function setFarmLast30Days() {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 29)
  const f = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  farmForm.startDate = `${f(start)} 00:00:00`
  farmForm.endDate = `${f(end)} 23:59:59`
  farmForm.year = null
}

function onSeedYearChange(value) {
  const year = Number(value || 0)
  if (!year) return
  seedForm.startDate = `${year}-01-01`
  seedForm.endDate = `${year}-12-31`
}

function setSeedThisYear() {
  seedForm.year = nowYear
  seedForm.startDate = `${nowYear}-01-01`
  seedForm.endDate = `${nowYear}-12-31`
}

function setSeedLast30Days() {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 29)
  const f = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  seedForm.startDate = f(start)
  seedForm.endDate = f(end)
  seedForm.year = null
}

async function downloadCsv(path, params, filename) {
  const token = getToken()
  if (!token) {
    ElMessage.error('请先登录')
    return
  }
  const url = `${getBaseUrl()}/admin/exports/${path}${toQuery(params)}`
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || contentType.includes('application/json')) {
    let message = '导出失败'
    try {
      const body = await response.json()
      message = (body && body.message) || message
    } catch (e) {}
    throw new Error(message)
  }
  const blob = await response.blob()
  const link = document.createElement('a')
  const objectUrl = window.URL.createObjectURL(blob)
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(objectUrl)
}

async function downloadFarm() {
  downloadingFarm.value = true
  try {
    await downloadCsv(
      'farm-records.csv',
      {
        fieldId: farmForm.fieldId,
        templateCode: farmForm.templateCode,
        township: farmForm.township,
        year: farmForm.year,
        startDate: farmForm.startDate,
        endDate: farmForm.endDate
      },
      'farm-records.csv'
    )
    ElMessage.success('农事记录导出成功')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    downloadingFarm.value = false
  }
}

async function downloadSeed() {
  downloadingSeed.value = true
  try {
    await downloadCsv(
      'seed-tests.csv',
      {
        batchId: seedForm.batchId,
        templateCode: seedForm.templateCode,
        year: seedForm.year,
        startDate: seedForm.startDate,
        endDate: seedForm.endDate
      },
      'seed-tests.csv'
    )
    ElMessage.success('种子检测导出成功')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    downloadingSeed.value = false
  }
}

async function loadBaseData() {
  try {
    const [fieldData, batchData, farmTpl, seedTpl, townships] = await Promise.all([
      request.get('/fields', { params: { page: 1, pageSize: 200, includeDisabled: true } }),
      request.get('/seed-batches', { params: { page: 1, pageSize: 200, includeDisabled: true } }),
      request.get('/admin/export-templates', { params: { page: 1, pageSize: 100, moduleKey: 'farm', status: 'enabled' } }),
      request.get('/admin/export-templates', { params: { page: 1, pageSize: 100, moduleKey: 'seed', status: 'enabled' } }),
      request.get('/meta/options/townships')
    ])
    fields.value = (fieldData && fieldData.records) || []
    seedBatches.value = (batchData && batchData.records) || []
    farmTemplateOptions.value = (farmTpl && farmTpl.records) || []
    seedTemplateOptions.value = (seedTpl && seedTpl.records) || []
    townshipOptions.value = Array.isArray(townships) ? townships : []
    if (!farmTemplateOptions.value.find((x) => x.templateCode === farmForm.templateCode)) {
      farmForm.templateCode = farmTemplateOptions.value[0] ? farmTemplateOptions.value[0].templateCode : ''
    }
    if (!seedTemplateOptions.value.find((x) => x.templateCode === seedForm.templateCode)) {
      seedForm.templateCode = seedTemplateOptions.value[0] ? seedTemplateOptions.value[0].templateCode : ''
    }
  } catch (e) {
    ElMessage.error(e.message || '基础数据加载失败')
  }
}

onMounted(async () => {
  setFarmThisYear()
  setSeedThisYear()
  await loadBaseData()
})
</script>

<style scoped>
.card-head {
  font-weight: 600;
}
</style>
