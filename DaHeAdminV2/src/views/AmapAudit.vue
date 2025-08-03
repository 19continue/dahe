<template>
  <div class="amap-audit-page">
    <PageToolbar
      title="高德运维中心"
      subtitle="统一查看额度、缓存策略、健康检查与调用审计。"
      :collapsible="false"
      :summary="[
        `密钥状态：${keyStatusText}`,
        `额度：${quotaUsageSummary}`,
        `体检：${healthStatusText}`,
        quota.lastHealthCheckAt ? `最近体检：${formatDateTime(quota.lastHealthCheckAt)}` : ''
      ]"
    >
      <template #head-actions>
        <el-button @click="toggleConfigPanel">{{ configPanelOpened ? '收起配置' : '展开配置' }}</el-button>
        <el-button :loading="healthChecking" @click="runHealthCheck">一键体检</el-button>
        <el-button :loading="clearingCache" @click="clearAmapCache">清空缓存</el-button>
        <el-button @click="loadAll">刷新</el-button>
      </template>
    </PageToolbar>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="说明：月限额按真实高德远程调用次数统计；缓存命中不计费。位置额度统一覆盖逆地理、位置查询、区域查询等全部位置相关接口。"
      style="margin-bottom: 10px"
    />

    <el-collapse v-model="configCollapseNames" class="config-collapse">
      <el-collapse-item name="config">
        <template #title>
          <div class="config-title">高德配置与体检</div>
        </template>
        <el-row :gutter="12">
          <el-col :span="14">
            <el-card shadow="never" v-loading="loadingQuota">
              <el-form label-width="126px">
                <el-form-item label="统计日期">
                  <el-input :model-value="quota.recordDate || '-'" readonly />
                </el-form-item>
                <el-form-item label="账号名称">
                  <el-input v-model="quota.accountName" placeholder="例如：dahe-prod-account" />
                </el-form-item>
                <el-form-item label="账号登录名">
                  <el-input v-model="quota.accountLogin" placeholder="手机号/邮箱/登录名（可选）" />
                </el-form-item>
                <el-form-item label="应用名称">
                  <el-input v-model="quota.appName" placeholder="例如：DaHe 生产环境" />
                </el-form-item>
                <el-form-item label="控制台地址">
                  <div class="inline-row">
                    <el-input v-model="quota.consoleUrl" placeholder="https://console.amap.com/dev" />
                    <el-button @click="openExternal(quota.consoleUrl)">打开</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="Key管理地址">
                  <div class="inline-row">
                    <el-input v-model="quota.keyConsoleUrl" placeholder="https://console.amap.com/dev/key/app" />
                    <el-button @click="openExternal(quota.keyConsoleUrl)">打开</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="当前密钥">
                  <el-input :model-value="quota.appKeyMasked || '未配置'" readonly />
                </el-form-item>
                <el-form-item label="更新密钥">
                  <el-input v-model="quota.appKeyInput" placeholder="留空则不修改密钥" show-password />
                </el-form-item>
                <el-form-item label="密钥状态">
                  <div class="key-status-wrap">
                    <el-tag :type="keyStatusTagType">{{ keyStatusText }}</el-tag>
                    <el-button size="small" :loading="verifyingKey" @click="verifyAmapKey">校验密钥</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="能力边界">
                  <div class="verify-meta">{{ quota.capabilityNote || '-' }}</div>
                </el-form-item>
                <el-form-item label="告警阈值(%)">
                  <el-input-number v-model="quota.alertThreshold" :min="1" :max="100" style="width: 100%" />
                </el-form-item>
                <el-form-item label="并发上限(QPS)">
                  <el-input-number v-model="quota.qpsLimit" :min="1" :max="100" style="width: 100%" />
                </el-form-item>
                <el-form-item label="天气月限额">
                  <el-input-number v-model="quota.weatherMonthlyLimit" :min="0" :step="100" style="width: 100%" />
                </el-form-item>
                <el-form-item label="位置月限额">
                  <el-input-number v-model="quota.locationMonthlyLimit" :min="0" :step="100" style="width: 100%" />
                </el-form-item>
                <el-form-item label="Redis缓存">
                  <el-switch v-model="quota.cacheRedisEnabled" />
                </el-form-item>
                <el-form-item label="缓存前缀">
                  <el-input v-model="quota.cacheRedisKeyPrefix" placeholder="dahe:v2:amap:cache:" />
                </el-form-item>
                <el-form-item label="区划TTL(分钟)">
                  <el-input-number v-model="quota.cacheRegionTtlMinutes" :min="1" :step="10" style="width: 100%" />
                </el-form-item>
                <el-form-item label="区划兜底(分钟)">
                  <el-input-number v-model="quota.cacheRegionStaleMinutes" :min="1" :step="10" style="width: 100%" />
                </el-form-item>
                <el-form-item label="天气TTL(分钟)">
                  <el-input-number v-model="quota.cacheWeatherTtlMinutes" :min="1" :step="5" style="width: 100%" />
                </el-form-item>
                <el-form-item label="本地区划容量">
                  <el-input-number v-model="quota.cacheLocalRegionMaxEntries" :min="32" :step="16" style="width: 100%" />
                </el-form-item>
                <el-form-item label="本地天气容量">
                  <el-input-number v-model="quota.cacheLocalWeatherMaxEntries" :min="32" :step="16" style="width: 100%" />
                </el-form-item>
                <el-form-item label="自动清理审计">
                  <el-switch v-model="quota.auditAutoPurgeEnabled" />
                </el-form-item>
                <el-form-item label="审计保留天数">
                  <el-input-number v-model="quota.auditRetainDays" :min="7" :step="7" style="width: 100%" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="quota.remark" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingQuota" @click="saveQuota">保存配置</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card shadow="never" class="health-card">
              <template #header>
                <div class="card-head">
                  <span>运行体检</span>
                  <el-button type="primary" :loading="healthChecking" @click="runHealthCheck">一键体检</el-button>
                </div>
              </template>
              <div class="guide-wrap">
                <el-tag :type="healthStatusTagType">{{ healthStatusText }}</el-tag>
                <span class="guide-message">{{ health.message || '尚未执行体检' }}</span>
              </div>
              <div class="guide-steps">
                <div v-for="item in health.steps" :key="`${item.order}-${item.text}`" class="guide-step">
                  <span class="guide-step-no">{{ item.order }}</span>
                  <span>{{ item.text }}</span>
                </div>
              </div>
              <div v-if="health.checks.length" class="check-list">
                <div v-for="item in health.checks" :key="item.name" class="check-item">
                  <el-tag size="small" :type="item.success ? 'success' : 'danger'">{{ item.success ? '通过' : '失败' }}</el-tag>
                  <span class="check-name">{{ item.name }}</span>
                  <span class="check-msg">{{ item.message }}</span>
                  <span class="check-cost">{{ item.costMs }}ms</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>

    <el-card shadow="never" class="audit-main-card">
      <template #header>
        <div class="card-head">
          <span>调用审计日志<span v-if="logBatchMode" class="card-meta">（已选 {{ selectedLogRows.length }} 条）</span></span>
          <div class="actions audit-toolbar">
            <el-select v-model="filters.bizScene" clearable filterable placeholder="场景" style="width: 160px">
              <el-option label="全部场景" value="" />
              <el-option v-for="item in bizSceneOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="filters.apiType" clearable placeholder="计费分类" style="width: 128px">
              <el-option label="全部分类" value="" />
              <el-option label="天气" value="weather" />
              <el-option label="位置/城市" value="location" />
            </el-select>
            <el-select v-model="filters.requestSource" clearable filterable placeholder="来源" style="width: 180px">
              <el-option label="全部来源" value="" />
              <el-option v-for="item in requestSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="filters.success" clearable placeholder="结果" style="width: 108px">
              <el-option label="全部结果" :value="null" />
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-date-picker
              v-model="filters.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 250px"
            />
            <el-button @click="loadLogs(1)">查询</el-button>
            <el-button @click="resetLogFilters">重置</el-button>
            <el-button plain :disabled="!isAdmin" @click="purgeOldLogs">按保留策略清理</el-button>
            <el-button
              type="default"
              plain
              class="batch-toggle-btn"
              :class="{ 'is-active': logBatchMode }"
              :disabled="!isAdmin"
              @click="toggleLogBatchMode"
            >
              {{ logBatchMode ? '退出多选' : '多选操作' }}
            </el-button>
            <el-button
              v-if="logBatchMode"
              type="danger"
              :disabled="!logBatchDeleteCount || logBatchSubmitting || !isAdmin"
              :loading="logBatchSubmitting"
              @click="batchDeleteLogs"
            >
              批量删除（{{ logBatchDeleteCount }}）
            </el-button>
          </div>
        </div>
      </template>

      <div class="overview-strip" v-loading="loadingOverview || loadingQuota">
        <div class="overview-card">
          <div class="overview-title">天气接口</div>
          <div class="overview-main">{{ quota.weatherTotalUsedCount || 0 }}</div>
          <div class="overview-sub">累计真实用量</div>
          <div class="overview-sub">本月真实用量 {{ quota.weatherCurrentMonthUsedCount || 0 }}</div>
          <div class="overview-sub">近7天总代理调用 {{ overview.weatherSummary.totalCount || 0 }}</div>
        </div>
        <div class="overview-card">
          <div class="overview-title">位置接口</div>
          <div class="overview-main">{{ quota.locationTotalUsedCount || 0 }}</div>
          <div class="overview-sub">累计真实用量</div>
          <div class="overview-sub">本月真实用量 {{ quota.locationCurrentMonthUsedCount || 0 }}</div>
          <div class="overview-sub">近7天总代理调用 {{ overview.locationSummary.totalCount || 0 }}</div>
        </div>
      </div>

      <el-table :data="overview.trend" border size="small" class="trend-table" v-if="overview.trend.length">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column label="天气调用" width="120">
          <template #default="scope">{{ scope.row.weather?.totalCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="天气真实用量" width="132">
          <template #default="scope">{{ scope.row.weather?.officialUsageCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="天气命中率" width="128">
          <template #default="scope">{{ toPercent(scope.row.weather?.cacheHitRate) }}</template>
        </el-table-column>
        <el-table-column label="位置调用" width="120">
          <template #default="scope">{{ scope.row.location?.totalCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="位置真实用量" width="132">
          <template #default="scope">{{ scope.row.location?.officialUsageCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="位置命中率" width="128">
          <template #default="scope">{{ toPercent(scope.row.location?.cacheHitRate) }}</template>
        </el-table-column>
      </el-table>

      <el-table
        ref="logTableRef"
        :data="logs"
        border
        v-loading="loadingLogs"
        :row-key="(row) => row.id"
        @selection-change="onLogSelectionChange"
      >
        <el-table-column v-if="logBatchMode" type="selection" width="48" />
        <el-table-column label="时间" width="182">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="bizScene" label="场景" width="160">
          <template #default="scope">
            <el-tag size="small" effect="plain" :type="bizSceneTagType(scope.row.bizScene)" class="scene-tag">
              {{ bizSceneText(scope.row.bizScene) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计费分类" width="100">
          <template #default="scope">
            <el-tag size="small" effect="plain" :type="scope.row.apiType === 'weather' ? 'success' : 'warning'">
              {{ scope.row.apiType === 'weather' ? '天气' : '位置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接口路径" min-width="210">
          <template #default="scope">
            <el-tooltip :content="scope.row.apiPath || '-'" placement="top">
              <span class="api-path-text">{{ scope.row.apiPath || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="76">
          <template #default="scope">
            <el-tag :type="Number(scope.row.successFlag) === 1 ? 'success' : 'danger'" size="small">
              {{ Number(scope.row.successFlag) === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="scope">
            <el-tag size="small" effect="plain" :type="costTagType(scope.row.costMs)">
              {{ formatCostMs(scope.row.costMs) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="220">
          <template #default="scope">
            <el-tooltip :content="scope.row.errorMessage || '-'" placement="top">
              <span class="error-text">{{ shortText(scope.row.errorMessage, 56) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="180">
          <template #default="scope">
            <el-tag size="small" effect="plain" :type="requestSourceTagType(scope.row.requestSource)">
              {{ requestSourceText(scope.row.requestSource) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line">
              <el-button
                type="danger"
                plain
                size="small"
                :disabled="!isAdmin || logBatchSubmitting"
                @click="removeLog(scope.row)"
              >
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
          :total="logTotal"
          :page-size="logPageSize"
          :current-page="logPage"
          :page-sizes="[10, 20, 50, 100, 200]"
          @size-change="onLogPageSizeChange"
          @current-change="loadLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import { isAdmin as isAdminUser } from '../utils/auth'

const loadingQuota = ref(false)
const savingQuota = ref(false)
const loadingLogs = ref(false)
const loadingOverview = ref(false)
const verifyingKey = ref(false)
const healthChecking = ref(false)
const clearingCache = ref(false)
const configCollapseNames = ref([])

const quota = reactive({
  recordDate: '',
  currentMonth: '',
  usedCount: 0,
  dailyLimit: 50000,
  alertThreshold: 80,
  qpsLimit: 3,
  usageRate: 0,
  accountName: '',
  accountLogin: '',
  appName: '',
  consoleUrl: '',
  keyConsoleUrl: '',
  appKeyMasked: '',
  appKeyInput: '',
  appKeyStatus: 'unknown',
  appKeyLastCheckAt: '',
  appKeyLastCheckMessage: '',
  lastHealthCheckAt: '',
  lastHealthCheckMessage: '',
  capabilityNote: '',
  weatherMonthlyLimit: 20000,
  weatherUsedCount: 0,
  weatherCurrentMonthUsedCount: 0,
  weatherTotalUsedCount: 0,
  weatherUsageRate: 0,
  weatherRemain: 0,
  locationMonthlyLimit: 20000,
  locationUsedCount: 0,
  locationCurrentMonthUsedCount: 0,
  locationTotalUsedCount: 0,
  locationUsageRate: 0,
  locationRemain: 0,
  cacheRedisEnabled: true,
  cacheRedisKeyPrefix: 'dahe:v2:amap:cache:',
  cacheRegionTtlMinutes: 720,
  cacheRegionStaleMinutes: 1440,
  cacheWeatherTtlMinutes: 60,
  cacheLocalRegionMaxEntries: 256,
  cacheLocalWeatherMaxEntries: 256,
  auditAutoPurgeEnabled: true,
  auditRetainDays: 90,
  remark: ''
})

const health = reactive({
  status: 'unknown',
  message: '',
  checks: [],
  steps: []
})

const filters = reactive({
  bizScene: '',
  apiType: '',
  requestSource: '',
  success: null,
  dateRange: []
})

const logs = ref([])
const logPage = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)
const logTableRef = ref(null)
const logBatchMode = ref(false)
const selectedLogRows = ref([])
const logBatchSubmitting = ref(false)
const requestSourceOptions = ref([
  { value: 'backend-proxy', label: '远程直连（backend-proxy）' },
  { value: 'backend-proxy-cache:local', label: '本地缓存（local）' },
  { value: 'backend-proxy-cache:redis', label: 'Redis缓存（redis）' },
  { value: 'backend-proxy-cache:local-stale', label: '过期兜底缓存（local-stale）' }
])
const bizSceneOptions = ref([
  { value: 'weather', label: '天气查询 (weather)' },
  { value: 'geocode', label: '地理编码 (geocode)' },
  { value: 'regeo', label: '逆地理编码 (regeo)' },
  { value: 'city', label: '城市检索 (city)' }
])

const bizSceneLabelMap = {
  weather: '天气查询',
  geocode: '地理编码',
  regeo: '逆地理编码',
  city: '城市检索'
}

const isAdmin = computed(() => isAdminUser())
const configPanelOpened = computed(() => Array.isArray(configCollapseNames.value) && configCollapseNames.value.includes('config'))
const logBatchDeleteCount = computed(() => {
  return (Array.isArray(selectedLogRows.value) ? selectedLogRows.value : []).filter((row) => Number((row && row.id) || 0) > 0).length
})
const quotaUsageSummary = computed(() => {
  const used = Number(quota.usedCount || 0)
  const limit = Number(quota.dailyLimit || 0)
  const rate = Number(quota.usageRate || 0)
  return `本月 ${used}/${limit}（${rate.toFixed(1)}%）`
})

const overview = reactive({
  weatherSummary: {},
  locationSummary: {},
  trend: []
})

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((k) => {
    const v = input[k]
    if (v === null || v === undefined || v === '') return
    out[k] = v
  })
  return out
}

function toggleConfigPanel() {
  if (configPanelOpened.value) {
    configCollapseNames.value = []
  } else {
    configCollapseNames.value = ['config']
  }
}

function toPercent(value) {
  const num = Number(value || 0)
  if (!Number.isFinite(num)) return '0%'
  return `${num.toFixed(2)}%`
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  const normalized = text.includes('T') ? text : text.replace(/-/g, '/')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return text
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`
}

function shortText(value, max = 36) {
  const text = String(value || '').trim()
  if (!text) return '-'
  if (text.length <= max) return text
  return `${text.slice(0, max)}...`
}

function formatCostMs(value) {
  const num = Number(value || 0)
  if (!(num >= 0)) return '-'
  return `${Math.round(num)}ms`
}

function costTagType(value) {
  const num = Number(value || 0)
  if (!(num >= 0)) return 'info'
  if (num >= 1500) return 'danger'
  if (num >= 800) return 'warning'
  return 'success'
}

function bizSceneTagType(value) {
  const key = String(value || '').trim().toLowerCase()
  if (!key) return 'info'
  if (key.includes('weather')) return 'success'
  if (key.includes('geo') || key.includes('location') || key.includes('nearby')) return 'warning'
  if (key.includes('city') || key.includes('town')) return 'primary'
  return 'info'
}

function requestSourceText(value) {
  const key = String(value || '').trim().toLowerCase()
  if (!key) return '-'
  if (key === 'backend-proxy') return '远程直连'
  if (key.startsWith('backend-proxy-cache:')) {
    if (key.includes('redis')) return 'Redis缓存命中'
    if (key.includes('local-stale')) return '本地过期兜底'
    return '本地缓存命中'
  }
  if (key.startsWith('backend-proxy-mixed:')) return '混合（缓存+远程）'
  if (key === 'app' || key === 'miniapp') return '小程序'
  if (key === 'admin' || key === 'console') return '后台'
  if (key === 'system') return '系统'
  return key
}

function requestSourceTagType(value) {
  const key = String(value || '').trim().toLowerCase()
  if (!key) return 'info'
  if (key === 'backend-proxy') return 'primary'
  if (key.startsWith('backend-proxy-cache:')) return 'success'
  if (key.startsWith('backend-proxy-mixed:')) return 'warning'
  if (key === 'app' || key === 'miniapp') return 'success'
  if (key === 'admin' || key === 'console') return 'primary'
  if (key === 'system') return 'warning'
  return 'info'
}

function resetLogFilters() {
  filters.bizScene = ''
  filters.apiType = ''
  filters.requestSource = ''
  filters.success = null
  filters.dateRange = []
  loadLogs(1)
}

function clearLogSelection() {
  selectedLogRows.value = []
  if (logTableRef.value && typeof logTableRef.value.clearSelection === 'function') {
    logTableRef.value.clearSelection()
  }
}

function onLogSelectionChange(rows) {
  selectedLogRows.value = Array.isArray(rows) ? rows : []
}

function toggleLogBatchMode() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可操作审计日志删除')
    return
  }
  logBatchMode.value = !logBatchMode.value
  if (!logBatchMode.value) {
    clearLogSelection()
  }
}

function applyQuota(data) {
  quota.recordDate = data.recordDate || ''
  quota.currentMonth = data.currentMonth || ''
  quota.usedCount = Number(data.usedCount || 0)
  quota.dailyLimit = Number(data.dailyLimit || 0)
  quota.alertThreshold = Number(data.alertThreshold || 80)
  quota.qpsLimit = Number(data.qpsLimit || 3)
  quota.usageRate = Number(data.usageRate || 0)
  quota.accountName = data.accountName || ''
  quota.accountLogin = data.accountLogin || ''
  quota.appName = data.appName || ''
  quota.consoleUrl = data.consoleUrl || 'https://console.amap.com/dev'
  quota.keyConsoleUrl = data.keyConsoleUrl || 'https://console.amap.com/dev/key/app'
  quota.appKeyMasked = data.appKeyMasked || ''
  quota.appKeyInput = ''
  quota.appKeyStatus = data.appKeyStatus || 'unknown'
  quota.appKeyLastCheckAt = data.appKeyLastCheckAt || ''
  quota.appKeyLastCheckMessage = data.appKeyLastCheckMessage || ''
  quota.lastHealthCheckAt = data.lastHealthCheckAt || ''
  quota.lastHealthCheckMessage = data.lastHealthCheckMessage || ''
  quota.capabilityNote = data.capabilityNote || ''
  quota.weatherMonthlyLimit = Number(data.weatherMonthlyLimit ?? data.weatherDailyLimit ?? 0)
  quota.weatherUsedCount = Number(data.weatherUsedCount || 0)
  quota.weatherCurrentMonthUsedCount = Number(data.weatherCurrentMonthUsedCount ?? data.weatherUsedCount ?? 0)
  quota.weatherTotalUsedCount = Number(data.weatherTotalUsedCount || 0)
  quota.weatherUsageRate = Number(data.weatherUsageRate || 0)
  quota.weatherRemain = Number(data.weatherRemain || 0)
  quota.locationMonthlyLimit = Number(data.locationMonthlyLimit ?? data.locationDailyLimit ?? data.geocodeDailyLimit ?? data.cityDailyLimit ?? 0)
  quota.locationUsedCount = Number(data.locationUsedCount ?? (Number(data.geocodeUsedCount || 0) + Number(data.cityUsedCount || 0)))
  quota.locationCurrentMonthUsedCount = Number(data.locationCurrentMonthUsedCount ?? data.locationUsedCount ?? 0)
  quota.locationTotalUsedCount = Number(data.locationTotalUsedCount || 0)
  quota.locationUsageRate = Number(data.locationUsageRate || 0)
  quota.locationRemain = Number(data.locationRemain ?? data.geocodeRemain ?? data.cityRemain ?? 0)
  quota.cacheRedisEnabled = Boolean(data.cacheRedisEnabled ?? true)
  quota.cacheRedisKeyPrefix = data.cacheRedisKeyPrefix || 'dahe:v2:amap:cache:'
  quota.cacheRegionTtlMinutes = Number(data.cacheRegionTtlMinutes || 720)
  quota.cacheRegionStaleMinutes = Number(data.cacheRegionStaleMinutes || 1440)
  quota.cacheWeatherTtlMinutes = Number(data.cacheWeatherTtlMinutes || 60)
  quota.cacheLocalRegionMaxEntries = Number(data.cacheLocalRegionMaxEntries || 256)
  quota.cacheLocalWeatherMaxEntries = Number(data.cacheLocalWeatherMaxEntries || 256)
  quota.auditAutoPurgeEnabled = Boolean(data.auditAutoPurgeEnabled ?? true)
  quota.auditRetainDays = Number(data.auditRetainDays || 90)
  quota.remark = data.remark || ''
}

const keyStatusText = computed(() => {
  const status = String(quota.appKeyStatus || '').trim().toLowerCase()
  if (status === 'valid') return '有效'
  if (status === 'invalid') return '无效'
  return '待校验'
})

const keyStatusTagType = computed(() => {
  const status = String(quota.appKeyStatus || '').trim().toLowerCase()
  if (status === 'valid') return 'success'
  if (status === 'invalid') return 'danger'
  return 'info'
})

const healthStatusText = computed(() => {
  const status = String(health.status || '').trim().toLowerCase()
  if (status === 'healthy') return '运行正常'
  if (status === 'degraded') return '部分异常'
  if (status === 'blocked') return '不可用'
  return '未体检'
})

const healthStatusTagType = computed(() => {
  const status = String(health.status || '').trim().toLowerCase()
  if (status === 'healthy') return 'success'
  if (status === 'degraded') return 'warning'
  if (status === 'blocked') return 'danger'
  return 'info'
})

function mergeBizSceneOptions(rows) {
  const map = new Map()
  ;(bizSceneOptions.value || []).forEach((item) => {
    const value = String((item && item.value) || '').trim()
    const label = String((item && item.label) || '').trim() || bizSceneText(value)
    if (!value) return
    map.set(value, { value, label })
  })
  ;(rows || []).forEach((row) => {
    const scene = String((row && row.bizScene) || '').trim()
    if (!scene || map.has(scene)) return
    map.set(scene, { value: scene, label: bizSceneText(scene) })
  })
  bizSceneOptions.value = Array.from(map.values())
}

function mergeRequestSourceOptions(rows) {
  const map = new Map()
  ;(requestSourceOptions.value || []).forEach((item) => {
    const value = String((item && item.value) || '').trim()
    const label = String((item && item.label) || '').trim() || requestSourceText(value)
    if (!value) return
    map.set(value, { value, label })
  })
  ;(rows || []).forEach((row) => {
    const source = String((row && row.requestSource) || '').trim()
    if (!source || map.has(source)) return
    map.set(source, { value: source, label: `${requestSourceText(source)} (${source})` })
  })
  requestSourceOptions.value = Array.from(map.values())
}

function bizSceneText(value) {
  const key = String(value || '').trim()
  if (!key) return '-'
  if (bizSceneLabelMap[key]) {
    return `${bizSceneLabelMap[key]} (${key})`
  }
  if (key.includes('weather')) return `天气相关 (${key})`
  if (key.includes('geo') || key.includes('location') || key.includes('nearby')) return `位置相关 (${key})`
  if (key.includes('city') || key.includes('town')) return `城镇相关 (${key})`
  return `其他场景 (${key})`
}

async function loadQuota() {
  loadingQuota.value = true
  try {
    const data = await request.get('/admin/amap/quota')
    applyQuota(data || {})
  } catch (e) {
    ElMessage.error(e.message || '额度配置加载失败')
  } finally {
    loadingQuota.value = false
  }
}

async function saveQuota() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可修改额度配置')
    return
  }
  if (!(Number(quota.weatherMonthlyLimit) >= 0)) {
    ElMessage.warning('天气月限额不能小于0')
    return
  }
  if (!(Number(quota.locationMonthlyLimit) >= 0)) {
    ElMessage.warning('位置月限额不能小于0')
    return
  }
  if (!(Number(quota.qpsLimit) > 0)) {
    ElMessage.warning('并发上限(QPS)必须大于0')
    return
  }
  savingQuota.value = true
  try {
    const data = await request.put('/admin/amap/quota', {
      alertThreshold: Number(quota.alertThreshold || 80),
      qpsLimit: Number(quota.qpsLimit || 3),
      accountName: quota.accountName || null,
      accountLogin: quota.accountLogin || null,
      appName: quota.appName || null,
      consoleUrl: quota.consoleUrl || null,
      keyConsoleUrl: quota.keyConsoleUrl || null,
      appKey: quota.appKeyInput ? quota.appKeyInput : null,
      weatherMonthlyLimit: Number(quota.weatherMonthlyLimit || 0),
      locationMonthlyLimit: Number(quota.locationMonthlyLimit || 0),
      cacheRedisEnabled: Boolean(quota.cacheRedisEnabled),
      cacheRedisKeyPrefix: quota.cacheRedisKeyPrefix || null,
      cacheRegionTtlMinutes: Number(quota.cacheRegionTtlMinutes || 0),
      cacheRegionStaleMinutes: Number(quota.cacheRegionStaleMinutes || 0),
      cacheWeatherTtlMinutes: Number(quota.cacheWeatherTtlMinutes || 0),
      cacheLocalRegionMaxEntries: Number(quota.cacheLocalRegionMaxEntries || 0),
      cacheLocalWeatherMaxEntries: Number(quota.cacheLocalWeatherMaxEntries || 0),
      auditAutoPurgeEnabled: Boolean(quota.auditAutoPurgeEnabled),
      auditRetainDays: Number(quota.auditRetainDays || 0),
      remark: quota.remark || null
    })
    applyQuota(data || {})
    ElMessage.success('额度配置已保存')
  } catch (e) {
    ElMessage.error(e.message || '额度配置保存失败')
  } finally {
    savingQuota.value = false
  }
}

function openExternal(url) {
  const target = String(url || '').trim()
  if (!target) {
    ElMessage.warning('请先填写链接地址')
    return
  }
  window.open(target, '_blank')
}

async function runHealthCheck() {
  healthChecking.value = true
  try {
    const data = await request.post('/admin/amap/health/check', {})
    health.status = String((data && data.status) || 'unknown').trim().toLowerCase() || 'unknown'
    health.message = (data && data.message) || ''
    health.checks = Array.isArray(data && data.checks) ? data.checks : []
    health.steps = Array.isArray(data && data.steps) ? data.steps : []
    if (data && data.quota) {
      applyQuota(data.quota)
    }
    if (health.status === 'healthy') {
      ElMessage.success(health.message || '体检通过')
    } else if (health.status === 'degraded') {
      ElMessage.warning(health.message || '体检部分异常')
    } else {
      ElMessage.error(health.message || '体检失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '体检失败')
  } finally {
    healthChecking.value = false
  }
}

async function loadOverview() {
  loadingOverview.value = true
  try {
    const startDate = Array.isArray(filters.dateRange) ? filters.dateRange[0] : null
    const endDate = Array.isArray(filters.dateRange) ? filters.dateRange[1] : null
    const data = await request.get('/admin/amap/audits/overview', {
      params: cleanParams({
        startDate,
        endDate
      })
    })
    overview.weatherSummary = (data && data.weatherSummary) || {}
    overview.locationSummary = (data && data.locationSummary) || {}
    overview.trend = Array.isArray(data && data.trend) ? data.trend : []
  } catch (e) {
    overview.weatherSummary = {}
    overview.locationSummary = {}
    overview.trend = []
    ElMessage.error(e.message || '审计趋势加载失败')
  } finally {
    loadingOverview.value = false
  }
}

async function purgeOldLogs() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可清理审计日志')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认按保留策略清理历史日志吗？当前保留天数：${Number(quota.auditRetainDays || 90)} 天`,
      '清理确认',
      { type: 'warning' }
    )
    const data = await request.post('/admin/amap/audits/purge', {
      retainDays: Number(quota.auditRetainDays || 90),
      batchSize: 500,
      maxBatches: 20
    })
    const deleted = Number((data && data.deletedRows) || 0)
    ElMessage.success(`清理完成，删除 ${deleted} 条历史日志`)
    await Promise.all([loadLogs(logPage.value), loadOverview()])
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '清理失败')
    }
  }
}

async function verifyAmapKey() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可校验密钥')
    return
  }
  verifyingKey.value = true
  try {
    const data = await request.post('/admin/amap/key/verify', {
      appKey: quota.appKeyInput ? quota.appKeyInput : null
    })
    applyQuota(data || {})
    if (data && data.valid) {
      ElMessage.success(data.verifyMessage || '密钥校验通过')
    } else {
      ElMessage.warning((data && data.verifyMessage) || '密钥校验失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '密钥校验失败')
  } finally {
    verifyingKey.value = false
  }
}

async function clearAmapCache() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可清空缓存')
    return
  }
  try {
    await ElMessageBox.confirm('确认清空高德接口缓存吗？清空后下一次查询会重新请求高德官方。', '清空缓存确认', { type: 'warning' })
  } catch (e) {
    return
  }
  clearingCache.value = true
  try {
    const data = await request.post('/admin/amap/cache/clear')
    const districtLocal = Number((data && data.districtLocalCleared) || 0)
    const weatherLocal = Number((data && data.weatherLocalCleared) || 0)
    const redisCleared = Number((data && data.redisCleared) || 0)
    ElMessage.success(`缓存已清空：本地区划 ${districtLocal}，本地天气 ${weatherLocal}，Redis ${redisCleared}`)
  } catch (e) {
    ElMessage.error(e.message || '缓存清空失败')
  } finally {
    clearingCache.value = false
  }
}

async function removeLog(row) {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可删除审计日志')
    return
  }
  const id = Number((row && row.id) || 0)
  if (!(id > 0)) {
    ElMessage.warning('日志ID无效，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除审计日志 #${id} 吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/amap/audits/${id}`)
    ElMessage.success('日志已删除')
    const remainAfterDelete = Math.max(0, Number(logTotal.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(logPageSize.value || 10)))
    await loadLogs(Math.min(Number(logPage.value || 1), maxPage))
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '日志删除失败')
    }
  }
}

async function batchDeleteLogs() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可删除审计日志')
    return
  }
  const ids = (Array.isArray(selectedLogRows.value) ? selectedLogRows.value : [])
    .map((row) => Number((row && row.id) || 0))
    .filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的审计日志')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 条审计日志吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除已取消')
    }
    return
  }

  logBatchSubmitting.value = true
  try {
    const data = await request.post('/admin/amap/audits/batch-delete', { ids })
    const success = Number((data && data.success) || 0)
    const failedIds = Array.isArray(data && data.failedIds) ? data.failedIds : []
    if (failedIds.length) {
      ElMessage.warning(`批量删除完成：成功 ${success} 条，失败 ${failedIds.length} 条`)
    } else {
      ElMessage.success(`批量删除成功，共 ${success} 条`)
    }
    const remainAfterDelete = Math.max(0, Number(logTotal.value || 0) - success)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(logPageSize.value || 10)))
    await loadLogs(Math.min(Number(logPage.value || 1), maxPage))
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    logBatchSubmitting.value = false
  }
}

async function loadLogs(nextPage = logPage.value) {
  loadingLogs.value = true
  try {
    logPage.value = Number(nextPage || 1)
    const startDate = Array.isArray(filters.dateRange) ? filters.dateRange[0] : null
    const endDate = Array.isArray(filters.dateRange) ? filters.dateRange[1] : null
    const data = await request.get('/admin/amap/audits', {
      params: cleanParams({
        page: logPage.value,
        pageSize: logPageSize.value,
        bizScene: filters.bizScene,
        apiType: filters.apiType,
        requestSource: filters.requestSource,
        successFlag: filters.success,
        startDate,
        endDate
      })
    })
    logs.value = (data && data.records) || []
    logTotal.value = Number((data && data.total) || 0)
    mergeBizSceneOptions(logs.value)
    mergeRequestSourceOptions(logs.value)
    clearLogSelection()
    await loadOverview()
  } catch (e) {
    logs.value = []
    logTotal.value = 0
    clearLogSelection()
    ElMessage.error(e.message || '审计日志加载失败')
  } finally {
    loadingLogs.value = false
  }
}

function onLogPageSizeChange(size) {
  logPageSize.value = Number(size || 50)
  loadLogs(1)
}

async function loadAll() {
  health.status = 'unknown'
  health.message = ''
  health.checks = []
  health.steps = []
  await Promise.all([loadQuota(), loadLogs(1)])
}

onMounted(loadAll)
</script>

<style scoped>
.amap-audit-page {
  display: block;
}

.config-collapse {
  margin-bottom: 12px;
}

.config-title {
  font-weight: 600;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-meta {
  margin-left: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.audit-toolbar {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  max-width: 100%;
}

.scene-tag {
  max-width: 138px;
}

.api-path-text {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-main);
}

.error-text {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-sub);
}

.audit-main-card {
  width: 100%;
}

.overview-strip {
  margin-bottom: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.overview-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--bg-plain);
}

.overview-title {
  color: var(--text-sub);
  font-size: 13px;
}

.overview-main {
  margin-top: 4px;
  color: var(--text-main);
  font-size: 24px;
  font-weight: 700;
}

.overview-sub {
  margin-top: 2px;
  color: var(--text-sub);
  font-size: 12px;
}

.trend-table {
  margin-bottom: 10px;
}

.key-status-wrap {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.verify-meta {
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.5;
}

.inline-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.guide-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.guide-message {
  color: var(--text-sub);
  font-size: 13px;
}

.guide-steps {
  margin-top: 8px;
  display: grid;
  gap: 6px;
}

.guide-step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-sub);
  font-size: 13px;
}

.guide-step-no {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--text-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.check-list {
  margin-top: 8px;
  border-top: 1px dashed var(--border);
  padding-top: 8px;
  display: grid;
  gap: 6px;
}

.check-item {
  display: grid;
  grid-template-columns: 56px 90px 1fr auto;
  gap: 8px;
  align-items: center;
}

.check-name {
  color: var(--text-main);
  font-size: 13px;
}

.check-msg {
  color: var(--text-sub);
  font-size: 12px;
}

.check-cost {
  color: var(--text-sub);
  font-size: 12px;
}

@media (max-width: 1200px) {
  .overview-strip {
    grid-template-columns: 1fr;
  }

  .audit-toolbar {
    justify-content: flex-start;
  }
}
</style>

