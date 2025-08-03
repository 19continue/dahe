<template>
  <div>
    <PageToolbar
      title="资源上传策略"
      subtitle="按小程序用户与后台用户分主体配置上传配额、文件类型与审核规则。"
      :collapsible="false"
    >
      <div class="actions">
        <el-button @click="loadPolicy">刷新</el-button>
        <el-button type="primary" :loading="saving" @click="savePolicy">保存策略</el-button>
      </div>
    </PageToolbar>

    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never" v-loading="loading">
          <div class="policy-section">
            <div class="section-title">小程序用户上传策略</div>
            <el-form label-width="210px">
              <el-form-item label="日上传条数上限">
                <el-input-number v-model="policy.miniappDailyUploadLimit" :min="0" :max="100000" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="日上传容量上限（MB）">
                <el-input-number v-model="policy.miniappDailyUploadSizeMb" :min="0" :max="1024 * 100" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="单文件上限（MB）">
                <el-input-number v-model="policy.miniappSingleFileMaxMb" :min="0" :max="1024 * 10" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="允许文件类型">
                <el-checkbox-group v-model="policy.miniappAllowedFileTypes">
                  <el-checkbox label="image">图片</el-checkbox>
                  <el-checkbox label="file">文件</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="上传后需审核">
                <el-switch v-model="miniappRequireReviewBool" />
              </el-form-item>
            </el-form>
          </div>

          <el-divider />

          <div class="policy-section">
            <div class="section-title">后台用户上传策略</div>
            <el-form label-width="210px">
              <el-form-item label="日上传条数上限">
                <el-input-number v-model="policy.adminDailyUploadLimit" :min="0" :max="100000" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="日上传容量上限（MB）">
                <el-input-number v-model="policy.adminDailyUploadSizeMb" :min="0" :max="1024 * 100" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="单文件上限（MB）">
                <el-input-number v-model="policy.adminSingleFileMaxMb" :min="0" :max="1024 * 10" />
                <span class="hint">`0` 表示不限制</span>
              </el-form-item>
              <el-form-item label="允许文件类型">
                <el-checkbox-group v-model="policy.adminAllowedFileTypes">
                  <el-checkbox label="image">图片</el-checkbox>
                  <el-checkbox label="file">文件</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="上传后需审核">
                <el-switch v-model="adminRequireReviewBool" />
              </el-form-item>
            </el-form>
          </div>

          <el-divider />

          <el-form label-width="210px">
            <el-form-item label="资源回收站保留期（天）">
              <el-input-number v-model="policy.strictSourcePurgeRetainDays" :min="1" :max="3650" />
            </el-form-item>
            <el-form-item label="全局资源锁密码">
              <div class="lock-password-block">
                <div class="hint-row">
                  <span class="hint">{{ policy.hasLockPassword ? '已设置全局资源锁密码' : '未设置全局资源锁密码' }}</span>
                  <span v-if="policy.lockPasswordUpdatedAt" class="hint">最近更新：{{ formatDateTime(policy.lockPasswordUpdatedAt) }} {{ policy.lockPasswordUpdatedByName ? `· ${policy.lockPasswordUpdatedByName}` : '' }}</span>
                </div>
                <div class="lock-password-row">
                  <el-input
                    v-model="lockPasswordForm.password"
                    type="password"
                    show-password
                    placeholder="请输入 6-32 位全局资源锁密码"
                    style="width: 260px"
                  />
                  <el-input
                    v-model="lockPasswordForm.confirmPassword"
                    type="password"
                    show-password
                    placeholder="请再次输入密码"
                    style="width: 260px"
                  />
                  <el-button
                    type="primary"
                    :disabled="!isSuperAdmin"
                    :loading="lockPasswordSaving"
                    @click="saveLockPassword"
                  >
                    {{ policy.hasLockPassword ? '重置密码' : '设置密码' }}
                  </el-button>
                </div>
                <div v-if="!isSuperAdmin" class="hint">仅超级管理员可设置或重置全局资源锁密码。</div>
              </div>
            </el-form-item>
            <el-form-item label="策略说明">
              <el-input v-model="policy.remark" type="textarea" :rows="3" placeholder="例如：农忙期临时收紧小程序上传容量" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="usage-head">今日上传用量</div>
          </template>
          <div class="usage-date">统计日期：{{ usageDate }}</div>

          <div class="usage-item">
            <div class="usage-title">小程序用户</div>
            <div class="usage-main">
              <strong>{{ miniappUsage.uploadCount || 0 }}</strong>
              <span>/ {{ miniappUsage.countLimit > 0 ? miniappUsage.countLimit : '不限' }}</span>
            </div>
            <el-progress :percentage="miniappUsage.countUsageRate || 0" :status="progressStatus(miniappUsage.countUsageRate)" />
            <div class="usage-sub">容量：{{ formatSize(miniappUsage.uploadSizeBytes) }} / {{ miniappUsage.sizeLimitMb > 0 ? `${miniappUsage.sizeLimitMb} MB` : '不限' }}</div>
          </div>

          <div class="usage-item">
            <div class="usage-title">后台用户</div>
            <div class="usage-main">
              <strong>{{ adminUsage.uploadCount || 0 }}</strong>
              <span>/ {{ adminUsage.countLimit > 0 ? adminUsage.countLimit : '不限' }}</span>
            </div>
            <el-progress :percentage="adminUsage.countUsageRate || 0" :status="progressStatus(adminUsage.countUsageRate)" />
            <div class="usage-sub">容量：{{ formatSize(adminUsage.uploadSizeBytes) }} / {{ adminUsage.sizeLimitMb > 0 ? `${adminUsage.sizeLimitMb} MB` : '不限' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import { getUser } from '../utils/auth'

const loading = ref(false)
const saving = ref(false)
const lockPasswordSaving = ref(false)
const currentUser = ref(getUser() || {})
const isSuperAdmin = computed(() => Number((currentUser.value && currentUser.value.isSuperAdmin) || 0) === 1)

const policy = reactive({
  miniappDailyUploadLimit: 500,
  miniappDailyUploadSizeMb: 2048,
  miniappSingleFileMaxMb: 30,
  miniappAllowedFileTypes: ['image', 'file'],
  miniappRequireReview: 0,
  adminDailyUploadLimit: 0,
  adminDailyUploadSizeMb: 0,
  adminSingleFileMaxMb: 0,
  adminAllowedFileTypes: ['image', 'file'],
  adminRequireReview: 0,
  strictSourcePurgeRetainDays: 7,
  remark: '',
  hasLockPassword: false,
  lockPasswordUpdatedAt: '',
  lockPasswordUpdatedByName: ''
})

const lockPasswordForm = reactive({
  password: '',
  confirmPassword: ''
})

const miniappUsage = reactive({
  usageDate: '',
  uploadCount: 0,
  uploadSizeBytes: 0,
  countLimit: 0,
  sizeLimitMb: 0,
  countUsageRate: 0,
  sizeUsageRate: 0
})

const adminUsage = reactive({
  usageDate: '',
  uploadCount: 0,
  uploadSizeBytes: 0,
  countLimit: 0,
  sizeLimitMb: 0,
  countUsageRate: 0,
  sizeUsageRate: 0
})

const usageDate = computed(() => miniappUsage.usageDate || adminUsage.usageDate || '-')

const miniappRequireReviewBool = computed({
  get: () => Number(policy.miniappRequireReview) === 1,
  set: (v) => {
    policy.miniappRequireReview = v ? 1 : 0
  }
})

const adminRequireReviewBool = computed({
  get: () => Number(policy.adminRequireReview) === 1,
  set: (v) => {
    policy.adminRequireReview = v ? 1 : 0
  }
})

function normalizeAllowedTypes(value) {
  const input = Array.isArray(value) ? value : []
  const out = input.filter((x) => x === 'image' || x === 'file')
  if (!out.length) return ['image', 'file']
  return Array.from(new Set(out))
}

function applyUsage(target, row) {
  target.usageDate = (row && row.usageDate) || ''
  target.uploadCount = Number((row && row.uploadCount) || 0)
  target.uploadSizeBytes = Number((row && row.uploadSizeBytes) || 0)
  target.countLimit = Number((row && row.countLimit) || 0)
  target.sizeLimitMb = Number((row && row.sizeLimitMb) || 0)
  target.countUsageRate = Number((row && row.countUsageRate) || 0)
  target.sizeUsageRate = Number((row && row.sizeUsageRate) || 0)
}

function applyPolicy(row) {
  const legacyAllowedTypes = normalizeAllowedTypes(row && row.operatorAllowedFileTypes)
  policy.miniappDailyUploadLimit = Number((row && (row.miniappDailyUploadLimit ?? row.operatorDailyUploadLimit)) || 0)
  policy.miniappDailyUploadSizeMb = Number((row && (row.miniappDailyUploadSizeMb ?? row.operatorDailyUploadSizeMb)) || 0)
  policy.miniappSingleFileMaxMb = Number((row && (row.miniappSingleFileMaxMb ?? row.operatorSingleFileMaxMb)) || 0)
  policy.miniappAllowedFileTypes = normalizeAllowedTypes((row && row.miniappAllowedFileTypes) || legacyAllowedTypes)
  policy.miniappRequireReview = Number((row && (row.miniappRequireReview ?? row.operatorRequireReview)) || 0) === 1 ? 1 : 0

  policy.adminDailyUploadLimit = Number((row && row.adminDailyUploadLimit) || 0)
  policy.adminDailyUploadSizeMb = Number((row && row.adminDailyUploadSizeMb) || 0)
  policy.adminSingleFileMaxMb = Number((row && row.adminSingleFileMaxMb) || 0)
  policy.adminAllowedFileTypes = normalizeAllowedTypes((row && row.adminAllowedFileTypes) || ['image', 'file'])
  policy.adminRequireReview = Number((row && row.adminRequireReview) || 0) === 1 ? 1 : 0

  policy.strictSourcePurgeRetainDays = Math.max(1, Number((row && row.strictSourcePurgeRetainDays) || 7))
  policy.remark = (row && row.remark) || ''
  policy.hasLockPassword = !!(row && row.hasLockPassword)
  policy.lockPasswordUpdatedAt = (row && row.lockPasswordUpdatedAt) || ''
  policy.lockPasswordUpdatedByName = (row && row.lockPasswordUpdatedByName) || ''

  applyUsage(miniappUsage, row && row.miniappTodayUsage)
  applyUsage(adminUsage, row && row.adminTodayUsage)
}

async function loadPolicy() {
  loading.value = true
  try {
    const data = await request.get('/admin/asset-policy')
    applyPolicy(data || {})
  } catch (error) {
    ElMessage.error(error.message || '资源策略加载失败')
  } finally {
    loading.value = false
  }
}

async function savePolicy() {
  if (!policy.miniappAllowedFileTypes.length || !policy.adminAllowedFileTypes.length) {
    ElMessage.warning('每个主体至少保留一种可上传文件类型')
    return
  }
  saving.value = true
  try {
    const data = await request.put('/admin/asset-policy', {
      miniappDailyUploadLimit: Number(policy.miniappDailyUploadLimit || 0),
      miniappDailyUploadSizeMb: Number(policy.miniappDailyUploadSizeMb || 0),
      miniappSingleFileMaxMb: Number(policy.miniappSingleFileMaxMb || 0),
      miniappAllowedFileTypes: policy.miniappAllowedFileTypes.slice(),
      miniappRequireReview: Number(policy.miniappRequireReview || 0) === 1 ? 1 : 0,
      adminDailyUploadLimit: Number(policy.adminDailyUploadLimit || 0),
      adminDailyUploadSizeMb: Number(policy.adminDailyUploadSizeMb || 0),
      adminSingleFileMaxMb: Number(policy.adminSingleFileMaxMb || 0),
      adminAllowedFileTypes: policy.adminAllowedFileTypes.slice(),
      adminRequireReview: Number(policy.adminRequireReview || 0) === 1 ? 1 : 0,
      strictSourcePurgeRetainDays: Math.max(1, Number(policy.strictSourcePurgeRetainDays || 1)),
      remark: policy.remark || null
    })
    applyPolicy(data || {})
    ElMessage.success('资源策略已保存')
  } catch (error) {
    ElMessage.error(error.message || '资源策略保存失败')
  } finally {
    saving.value = false
  }
}

async function saveLockPassword() {
  if (!isSuperAdmin.value) {
    ElMessage.warning('仅超级管理员可设置或重置全局资源锁密码')
    return
  }
  const password = String(lockPasswordForm.password || '').trim()
  const confirmPassword = String(lockPasswordForm.confirmPassword || '').trim()
  if (!password) {
    ElMessage.warning('请输入全局资源锁密码')
    return
  }
  if (password.length < 6 || password.length > 32) {
    ElMessage.warning('全局资源锁密码长度需为 6-32 位')
    return
  }
  if (password !== confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  lockPasswordSaving.value = true
  try {
    const data = await request.put('/admin/asset-policy/lock-password', { password })
    applyPolicy(data || {})
    lockPasswordForm.password = ''
    lockPasswordForm.confirmPassword = ''
    ElMessage.success(policy.hasLockPassword ? '全局资源锁密码已重置' : '全局资源锁密码已设置')
  } catch (error) {
    ElMessage.error(error.message || '全局资源锁密码保存失败')
  } finally {
    lockPasswordSaving.value = false
  }
}

function progressStatus(rate) {
  const val = Number(rate || 0)
  if (val >= 90) return 'exception'
  if (val >= 75) return 'warning'
  return ''
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

function formatSize(value) {
  const num = Number(value || 0)
  if (!(num > 0)) return '0 B'
  if (num < 1024) return `${num.toFixed(0)} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  if (num < 1024 * 1024 * 1024) return `${(num / (1024 * 1024)).toFixed(1)} MB`
  return `${(num / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

onMounted(loadPolicy)
</script>

<style scoped>
.policy-section + .policy-section {
  margin-top: 2px;
}

.section-title {
  margin-bottom: 8px;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
}

.hint {
  margin-left: 12px;
  color: var(--text-sub);
  font-size: 12px;
}

.lock-password-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.hint-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.lock-password-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.usage-head {
  font-weight: 700;
}

.usage-date {
  color: var(--text-sub);
  font-size: 12px;
}

.usage-item {
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-plain);
}

.usage-title {
  color: var(--text-sub);
  font-size: 12px;
}

.usage-main {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--text-main);
}

.usage-main strong {
  font-size: 22px;
}

.usage-sub {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
}
</style>
