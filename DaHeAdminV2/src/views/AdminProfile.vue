<template>
  <div class="admin-profile-page">
    <PageToolbar title="个人主页" subtitle="维护后台管理员资料、头像和登录密码。" :collapsible="false">
      <div class="actions">
        <el-button :loading="loading" @click="loadProfile">刷新</el-button>
      </div>
    </PageToolbar>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>头像设置</span>
            </div>
          </template>
          <div class="avatar-pane">
            <el-avatar v-if="profile.avatarUrl" :src="profile.avatarUrl" :size="88" />
            <el-avatar v-else :size="88">{{ userInitial }}</el-avatar>
            <div class="avatar-meta">
              <span>头像来源：{{ avatarSourceText(profile.avatarSource) }}</span>
              <span>当前地址：{{ profile.avatarUrl || '-' }}</span>
            </div>
          </div>
          <ImageAssetPicker
            v-model="avatarCandidate"
            upload-module-key="auth"
            :upload-biz-id="profile.id || ''"
            upload-folder-path="/admin/avatar"
            :page-size="12"
          />
          <div class="avatar-actions">
            <el-button type="primary" :loading="savingAvatar" @click="saveAvatar">保存头像</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="15">
        <div class="profile-stack">
          <el-card shadow="never" v-loading="loading">
            <template #header>
              <div class="card-head">
                <span>资料维护</span>
              </div>
            </template>
            <el-form label-width="92px">
              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="真实姓名">
                    <el-input v-model="profile.realName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="昵称">
                    <el-input v-model="profile.nickName" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="手机号">
                    <el-input v-model="profile.phone" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="角色">
                    <el-input :model-value="profile.roleName || profile.roleCode || '-'" readonly />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="账号状态">
                    <el-input :model-value="enabledText(profile.enabled)" readonly />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="审核状态">
                    <el-input :model-value="profile.status || '-'" readonly />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="10">
                <el-col :span="12">
                  <el-form-item label="创建时间">
                    <el-input :model-value="formatDateTime(profile.createdAt)" readonly />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="更新时间">
                    <el-input :model-value="formatDateTime(profile.updatedAt)" readonly />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item>
                <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span>修改密码</span>
              </div>
            </template>
            <el-form label-width="108px" @submit.prevent="submitPasswordChange">
              <el-form-item label="当前密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <div class="password-tip">密码至少 8 位，且同时包含字母和数字。修改成功后会强制重新登录。</div>
              <el-form-item>
                <el-button type="primary" :loading="savingPassword" @click="submitPasswordChange">更新密码</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'
import ImageAssetPicker from '../components/ui/ImageAssetPicker.vue'
import { changeAdminPassword, fetchAdminMe, updateAdminAvatar, updateAdminProfile } from '../api/adminAuth'
import { clearSession, getUser, setUser } from '../utils/auth'

const router = useRouter()
const loading = ref(false)
const savingProfile = ref(false)
const savingAvatar = ref(false)
const savingPassword = ref(false)
const avatarCandidate = ref('')
const profile = reactive({
  id: '',
  realName: '',
  nickName: '',
  phone: '',
  roleCode: '',
  roleName: '',
  status: '',
  enabled: 1,
  avatarUrl: '',
  avatarSource: '',
  createdAt: '',
  updatedAt: ''
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const userInitial = computed(() => {
  const text = String(profile.realName || profile.nickName || 'A').trim()
  return text ? text.slice(0, 1).toUpperCase() : 'A'
})

function applyProfile(user) {
  const row = user || {}
  profile.id = row.id || ''
  profile.realName = row.realName || ''
  profile.nickName = row.nickName || ''
  profile.phone = row.phone || ''
  profile.roleCode = row.roleCode || ''
  profile.roleName = row.roleName || ''
  profile.status = row.status || ''
  profile.enabled = Number((row && row.enabled) ?? 1)
  profile.avatarUrl = row.avatarUrl || row.wxAvatarUrl || ''
  profile.avatarSource = row.avatarSource || 'none'
  profile.createdAt = row.createdAt || ''
  profile.updatedAt = row.updatedAt || ''
  avatarCandidate.value = profile.avatarUrl || ''
}

function mergeUserToLocal(patch = {}) {
  const current = getUser() || {}
  const next = { ...current, ...patch }
  setUser(next)
  window.dispatchEvent(new CustomEvent('dahe-user-updated', { detail: next }))
}

function avatarSourceText(source) {
  const key = String(source || '').trim().toLowerCase()
  if (key === 'wx') return '微信'
  if (key === 'upload' || key === 'admin') return '后台上传'
  return '未设置'
}

function enabledText(value) {
  return Number(value) === 1 ? '启用' : '禁用'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ')
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await fetchAdminMe()
    const user = data && data.user ? data.user : null
    if (!user) {
      throw new Error('个人信息加载失败')
    }
    applyProfile(user)
    mergeUserToLocal(user)
  } catch (error) {
    ElMessage.error(error.message || '个人信息加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const realName = String(profile.realName || '').trim()
  if (!realName) {
    ElMessage.warning('真实姓名不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await updateAdminProfile({
      realName,
      nickName: String(profile.nickName || '').trim() || null,
      phone: String(profile.phone || '').trim() || null
    })
    profile.updatedAt = (res && res.updatedAt) || profile.updatedAt
    mergeUserToLocal({
      realName,
      nickName: String(profile.nickName || '').trim() || realName,
      phone: String(profile.phone || '').trim() || null,
      updatedAt: profile.updatedAt
    })
    ElMessage.success('资料已更新')
  } catch (error) {
    ElMessage.error(error.message || '资料更新失败')
  } finally {
    savingProfile.value = false
  }
}

async function saveAvatar() {
  const avatarUrl = String(avatarCandidate.value || '').trim()
  if (!avatarUrl) {
    ElMessage.warning('请先选择头像图片')
    return
  }
  savingAvatar.value = true
  try {
    const res = await updateAdminAvatar({
      avatarUrl,
      avatarSource: 'admin'
    })
    profile.avatarUrl = (res && res.avatarUrl) || avatarUrl
    profile.avatarSource = (res && res.avatarSource) || 'admin'
    mergeUserToLocal({
      avatarUrl: profile.avatarUrl,
      avatarSource: profile.avatarSource
    })
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(error.message || '头像更新失败')
  } finally {
    savingAvatar.value = false
  }
}

async function submitPasswordChange() {
  const oldPassword = String(passwordForm.oldPassword || '')
  const newPassword = String(passwordForm.newPassword || '')
  const confirmPassword = String(passwordForm.confirmPassword || '')
  if (!oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (newPassword !== confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await changeAdminPassword({ oldPassword, newPassword })
    ElMessage.success('密码已更新，请重新登录')
    resetPasswordForm()
    clearSession()
    router.replace('/login')
  } catch (error) {
    ElMessage.error(error.message || '密码更新失败')
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.admin-profile-page {
  display: block;
}

.profile-stack {
  display: grid;
  gap: 12px;
}

.avatar-pane {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.avatar-meta {
  min-width: 0;
  display: grid;
  gap: 4px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.6;
}

.avatar-actions {
  margin-top: 10px;
}

.password-tip {
  margin: -6px 0 10px 108px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .password-tip {
    margin-left: 0;
  }
}
</style>