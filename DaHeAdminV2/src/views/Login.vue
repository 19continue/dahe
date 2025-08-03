<template>
  <div class="login-page admin-login-page">
    <el-card class="login-card admin-login-card" shadow="never">
      <div class="brand-head">
        <div class="brand-logo-wrap">
          <img v-if="companyLogo" :src="companyLogo" :alt="`${companyName} logo`" class="brand-logo" />
          <div v-else class="brand-logo-fallback">{{ companyInitial }}</div>
        </div>
        <div class="brand-copy">
          <div class="brand-name">{{ companyName }}</div>
          <div class="brand-subtitle">后台管理登录</div>
        </div>
      </div>

      <div class="scope-tip">仅后台管理员账号可登录</div>

      <el-form label-position="top" class="login-form" @submit.prevent="login">
        <el-form-item label="登录账号">
          <el-input
            v-model="loginName"
            placeholder="请输入登录账号"
            autocomplete="username"
            @keyup.enter="login"
          />
        </el-form-item>

        <el-form-item label="登录密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入登录密码"
            autocomplete="current-password"
            @keyup.enter="login"
          />
        </el-form-item>

        <div class="form-row">
          <el-checkbox v-model="rememberPassword" @change="onRememberPasswordChange">记住账号密码</el-checkbox>
        </div>
      </el-form>

      <el-button type="primary" :loading="loading" class="submit-btn" @click="login">登录</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { adminLogin } from '../api/adminAuth'
import { setToken, setUser } from '../utils/auth'
import logoUrl from '../assets/logo.png'

const DEVICE_ID_KEY = 'dahe.admin.v2.deviceId'
const REMEMBER_LOGIN_NAME_KEY = 'dahe.admin.v2.remember.loginName'
const REMEMBER_PASSWORD_KEY = 'dahe.admin.v2.remember.password'
const REMEMBER_FLAG_KEY = 'dahe.admin.v2.remember.enabled'

const router = useRouter()
const loading = ref(false)
const loginName = ref('')
const password = ref('')
const rememberPassword = ref(false)
const companyName = ref('大禾种业')
const companyLogo = logoUrl

const companyInitial = computed(() => {
  const text = String(companyName.value || '大').trim()
  return text ? text.slice(0, 1).toUpperCase() : '大'
})

function loadRememberedCredentials() {
  const enabled = localStorage.getItem(REMEMBER_FLAG_KEY) === '1'
  rememberPassword.value = enabled
  if (!enabled) return
  loginName.value = String(localStorage.getItem(REMEMBER_LOGIN_NAME_KEY) || '')
  password.value = String(localStorage.getItem(REMEMBER_PASSWORD_KEY) || '')
}

function persistRememberedCredentials() {
  if (!rememberPassword.value) {
    localStorage.removeItem(REMEMBER_FLAG_KEY)
    localStorage.removeItem(REMEMBER_LOGIN_NAME_KEY)
    localStorage.removeItem(REMEMBER_PASSWORD_KEY)
    return
  }
  localStorage.setItem(REMEMBER_FLAG_KEY, '1')
  localStorage.setItem(REMEMBER_LOGIN_NAME_KEY, String(loginName.value || '').trim())
  localStorage.setItem(REMEMBER_PASSWORD_KEY, String(password.value || ''))
}

function onRememberPasswordChange(value) {
  if (value) return
  localStorage.removeItem(REMEMBER_FLAG_KEY)
  localStorage.removeItem(REMEMBER_LOGIN_NAME_KEY)
  localStorage.removeItem(REMEMBER_PASSWORD_KEY)
}

async function login() {
  const account = String(loginName.value || '').trim().toLowerCase()
  const rawPassword = String(password.value || '')
  if (!account) {
    ElMessage.warning('请输入登录账号')
    return
  }
  if (!rawPassword) {
    ElMessage.warning('请输入登录密码')
    return
  }

  loading.value = true
  try {
    const data = await adminLogin({
      loginName: account,
      password: rawPassword,
      deviceContext: buildDeviceContext()
    })
    if (!data || !data.approved || !data.accessToken) {
      ElMessage.error(`登录失败：${(data && data.message) || '账号尚未审核通过'}`)
      return
    }
    loginName.value = account
    persistRememberedCredentials()
    setToken(data.accessToken)
    setUser(data.user)
    ElMessage.success('登录成功')
    router.replace('/dashboard')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function buildDeviceContext() {
  return {
    deviceId: getOrCreateDeviceId(),
    deviceName: resolveDeviceName(),
    userAgent: resolveUserAgent()
  }
}

function getOrCreateDeviceId() {
  let saved = String(localStorage.getItem(DEVICE_ID_KEY) || '').trim()
  if (saved) return saved
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    saved = crypto.randomUUID()
  } else {
    saved = `admin-web-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
  }
  localStorage.setItem(DEVICE_ID_KEY, saved)
  return saved
}

function resolveDeviceName() {
  const platform = typeof navigator !== 'undefined' ? String(navigator.platform || '').trim() : ''
  return platform ? `admin-web:${platform}` : 'admin-web'
}

function resolveUserAgent() {
  return typeof navigator !== 'undefined' ? String(navigator.userAgent || '') : ''
}

onMounted(() => {
  loadRememberedCredentials()
})
</script>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  padding: 32px 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(720px 320px at 0% 0%, rgba(21, 128, 61, 0.12), transparent 62%),
    radial-gradient(680px 320px at 100% 100%, rgba(202, 138, 4, 0.1), transparent 58%),
    linear-gradient(180deg, #f7fbf6 0%, #eef6ee 100%);
}

.admin-login-card {
  width: min(440px, 100%);
  border-radius: 20px;
  border: 1px solid rgba(21, 128, 61, 0.08);
  box-shadow: 0 18px 46px rgba(15, 23, 42, 0.08);
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-logo-wrap {
  width: 72px;
  height: 72px;
  border-radius: 18px;
  overflow: hidden;
  background: linear-gradient(180deg, #ebf7ed 0%, #dfeee1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-logo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.brand-logo-fallback {
  font-size: 30px;
  font-weight: 700;
  color: #166534;
}

.brand-copy {
  min-width: 0;
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  color: #11221a;
}

.brand-subtitle {
  margin-top: 4px;
  font-size: 14px;
  color: #5d6b62;
}

.scope-tip {
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f4f8f3;
  color: #5e6e62;
  font-size: 13px;
  line-height: 1.5;
}

.login-form {
  margin-top: 22px;
}

.form-row {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.submit-btn {
  width: 100%;
  margin-top: 20px;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 640px) {
  .admin-login-page {
    padding: 20px 14px;
  }

  .admin-login-card {
    border-radius: 16px;
  }

  .brand-name {
    font-size: 21px;
  }
}
</style>
