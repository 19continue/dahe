<template>
  <div class="admin-user-page">
    <PageToolbar
      title="后台用户管理"
      subtitle="这里只管理后台管理员账号。后台用户通过账号密码登录，不参与小程序审核流。"
      collapsible
      :summary="toolbarSummary"
    >
      <div class="actions">
        <el-input v-model="keyword" placeholder="账号/姓名/昵称/手机号" clearable style="width: 240px" @keyup.enter="load(1)" />
        <el-select v-model="enabled" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="load(1)">查询</el-button>
        <el-button type="primary" @click="openCreate">新增后台用户</el-button>
        <el-button plain class="batch-toggle-btn" :class="{ 'is-active': batchMode }" @click="toggleBatchMode">
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <template v-if="batchMode">
          <el-button :disabled="!batchEditCount || batchSubmitting" @click="openBatchEdit">批量编辑（{{ batchEditCount }}）</el-button>
          <el-button type="success" :disabled="!batchEnableCount || batchSubmitting" :loading="batchSubmitting" @click="batchSetEnabled(true)">
            批量启用（{{ batchEnableCount }}）
          </el-button>
          <el-button type="warning" :disabled="!batchDisableCount || batchSubmitting" :loading="batchSubmitting" @click="batchSetEnabled(false)">
            批量禁用（{{ batchDisableCount }}）
          </el-button>
          <el-button type="info" plain :disabled="!batchRevokeCount || batchSubmitting" :loading="batchSubmitting" @click="batchRevokeSessions">
            批量强制下线（{{ batchRevokeCount }}）
          </el-button>
          <el-button type="danger" :disabled="!batchDeleteCount || batchSubmitting" :loading="batchSubmitting" @click="batchDeleteUsers">
            批量删除（{{ batchDeleteCount }}）
          </el-button>
        </template>
      </div>
    </PageToolbar>

    <el-table
      ref="tableRef"
      :data="rows"
      border
      v-loading="loading"
      :row-key="(row) => row.id"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="batchMode" type="selection" width="48" />
      <el-table-column label="头像" width="76" align="center">
        <template #default="scope">
          <el-avatar v-if="scope.row.avatarUrl" :src="scope.row.avatarUrl" :size="34" />
          <el-avatar v-else :size="34">{{ userInitial(scope.row) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="编号" min-width="170" />
      <el-table-column prop="loginName" label="登录账号" min-width="160" show-overflow-tooltip />
      <el-table-column prop="realName" label="姓名" width="112" />
      <el-table-column prop="nickName" label="昵称" width="132" />
      <el-table-column prop="phone" label="手机号" width="146" />
      <el-table-column prop="roleCode" label="角色" width="150">
        <template #default="scope">
          <span>{{ formatRole(scope.row.roleCode) }}</span>
          <el-tag v-if="isSuperAdmin(scope.row)" type="danger" size="small" style="margin-left: 6px">超级</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可用状态" width="100">
        <template #default="scope">
          <el-tag :type="isDisabled(scope.row) ? 'danger' : 'success'" size="small">
            {{ isDisabled(scope.row) ? '禁用' : '启用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="430" fixed="right" class-name="op-col">
        <template #default="scope">
          <div class="table-op-line">
            <el-button size="small" :disabled="isSuperAdmin(scope.row)" @click="openEdit(scope.row)">编辑角色</el-button>
            <el-button
              size="small"
              :type="isDisabled(scope.row) ? 'success' : 'warning'"
              plain
              :disabled="isSuperAdmin(scope.row)"
              @click="toggleEnabled(scope.row)"
            >
              {{ isDisabled(scope.row) ? '解除禁用' : '禁用' }}
            </el-button>
            <el-button size="small" type="primary" class="reset-password-btn" :disabled="isSuperAdmin(scope.row)" @click="openResetPassword(scope.row)">
              重置密码
            </el-button>
            <el-button size="small" type="info" plain :disabled="isSuperAdmin(scope.row)" @click="revokeSessions(scope.row)">
              强制下线
            </el-button>
            <el-button size="small" type="danger" plain :disabled="isSuperAdmin(scope.row)" @click="removeUser(scope.row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-foot">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @size-change="onPageSizeChange"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="createVisible" title="新增后台用户" width="520px">
      <el-form label-width="110px">
        <el-form-item label="姓名">
          <el-input v-model="createForm.realName" placeholder="必填" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" placeholder="可选" />
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input v-model="createForm.loginName" placeholder="4-32位，小写字母/数字/._-" />
        </el-form-item>
        <el-form-item label="登录密码">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少8位，且包含字母和数字" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleCode" style="width: 100%">
            <el-option v-for="opt in roleOptionList" :key="opt.roleCode" :label="opt.roleName" :value="opt.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="头像 URL">
          <el-input v-model="createForm.avatarUrl" placeholder="可选，支持 http/https" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCreate" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑后台用户角色" width="420px">
      <el-form label-width="96px">
        <el-form-item label="角色">
          <el-select v-model="editForm.roleCode" style="width: 100%">
            <el-option v-for="opt in roleOptionList" :key="opt.roleCode" :label="opt.roleName" :value="opt.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchEditVisible" title="批量编辑后台用户" width="420px">
      <el-form label-width="96px">
        <el-form-item label="角色">
          <el-select v-model="batchEditForm.roleCode" style="width: 100%">
            <el-option v-for="opt in roleOptionList" :key="opt.roleCode" :label="opt.roleName" :value="opt.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <div class="batch-hint">将应用到 {{ batchEditCount }} 位可编辑用户；超级管理员会自动跳过。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatchEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetPasswordVisible" title="重置后台用户密码" width="420px">
      <el-form label-width="96px">
        <el-form-item label="目标用户">
          <el-input :model-value="resetPasswordForm.loginName || '-'" readonly />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="resetPasswordForm.newPassword" type="password" show-password placeholder="至少8位，且包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="resetPasswordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingResetPassword" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  createAdminUser,
  deleteUser,
  fetchAdminUsers,
  fetchRoleOptions,
  resetUserPassword,
  revokeUserSessions,
  updateUserEnabled,
  updateUserRole
} from '../api/adminUser'
import PageToolbar from '../components/ui/PageToolbar.vue'

const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const enabled = ref('')
const roleOptions = ref([])
const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)

const createVisible = ref(false)
const savingCreate = ref(false)
const createForm = reactive({
  realName: '',
  nickName: '',
  phone: '',
  loginName: '',
  password: '',
  roleCode: '',
  avatarUrl: ''
})

const editVisible = ref(false)
const savingEdit = ref(false)
const editForm = reactive({
  id: null,
  roleCode: ''
})

const batchEditVisible = ref(false)
const batchEditForm = reactive({
  roleCode: ''
})

const resetPasswordVisible = ref(false)
const savingResetPassword = ref(false)
const resetPasswordForm = reactive({
  id: null,
  loginName: '',
  newPassword: '',
  confirmPassword: ''
})

const roleOptionList = computed(() => (Array.isArray(roleOptions.value) ? roleOptions.value : []))

const toolbarSummary = computed(() => {
  return [
    keyword.value ? `关键词：${keyword.value}` : '',
    enabled.value !== '' ? `可用性：${Number(enabled.value) === 1 ? '启用' : '禁用'}` : '',
    batchMode.value ? `多选：已选 ${selectedRows.value.length} 条` : ''
  ].filter(Boolean)
})

const roleNameMap = computed(() => {
  const map = new Map()
  roleOptionList.value.forEach((item) => {
    const code = String((item && item.roleCode) || '').trim().toLowerCase()
    const name = String((item && item.roleName) || '').trim()
    if (code && name) map.set(code, name)
  })
  return map
})

function isDisabled(row) {
  return Number(row && row.enabled) === 0
}

function isSuperAdmin(row) {
  return Number(row && row.isSuperAdmin) === 1
}

function isBatchEditable(row) {
  return !!(row && row.id && !isSuperAdmin(row))
}

const batchEditableRows = computed(() => {
  const rowsList = Array.isArray(selectedRows.value) ? selectedRows.value : []
  return rowsList.filter((row) => isBatchEditable(row))
})

const batchEditCount = computed(() => batchEditableRows.value.length)
const batchEnableCount = computed(() => batchEditableRows.value.filter((row) => isDisabled(row)).length)
const batchDisableCount = computed(() => batchEditableRows.value.filter((row) => !isDisabled(row)).length)
const batchDeleteCount = computed(() => batchEditableRows.value.filter((row) => Number(row && row.id) > 0).length)
const batchRevokeCount = computed(() => batchEditableRows.value.filter((row) => Number(row && row.id) > 0).length)

function formatRole(value) {
  const code = String(value || '').trim().toLowerCase()
  if (!code) return '-'
  return roleNameMap.value.get(code) || code
}

function resolveDefaultRoleCode() {
  const codes = roleOptionList.value
    .map((item) => String((item && item.roleCode) || '').trim().toLowerCase())
    .filter(Boolean)
  return codes[0] || ''
}

async function loadRoleOptions() {
  try {
    const data = await fetchRoleOptions(false)
    roleOptions.value = Array.isArray(data) ? data : []
    const defaultCode = resolveDefaultRoleCode()
    if (!roleNameMap.value.has(String(createForm.roleCode || '').trim().toLowerCase())) createForm.roleCode = defaultCode
    if (!roleNameMap.value.has(String(editForm.roleCode || '').trim().toLowerCase())) editForm.roleCode = defaultCode
    if (!roleNameMap.value.has(String(batchEditForm.roleCode || '').trim().toLowerCase())) batchEditForm.roleCode = defaultCode
  } catch (error) {
    roleOptions.value = []
    ElMessage.error(error.message || '角色选项加载失败')
  }
}

function userInitial(row) {
  const text = String((row && (row.realName || row.nickName || row.loginName)) || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : 'U'
}

function resolveDisplayName(row) {
  return String((row && (row.realName || row.nickName || row.loginName || row.id)) || '').trim()
}

function clearBatchSelection() {
  selectedRows.value = []
  if (tableRef.value && typeof tableRef.value.clearSelection === 'function') {
    tableRef.value.clearSelection()
  }
}

function onSelectionChange(selection) {
  selectedRows.value = Array.isArray(selection) ? selection : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) clearBatchSelection()
}

async function load(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await fetchAdminUsers({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      enabled: enabled.value === '' ? undefined : Number(enabled.value),
      reviewOnly: false,
      userType: 'admin'
    })
    rows.value = (data && data.records) || []
    total.value = Number((data && data.total) || 0)
    clearBatchSelection()
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  load(1)
}

function openCreate() {
  if (!roleOptionList.value.length) {
    ElMessage.warning('请先在“后台角色管理”中创建并启用角色')
    return
  }
  createForm.realName = ''
  createForm.nickName = ''
  createForm.phone = ''
  createForm.loginName = ''
  createForm.password = ''
  createForm.roleCode = resolveDefaultRoleCode()
  createForm.avatarUrl = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.realName.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  if (!String(createForm.loginName || '').trim()) {
    ElMessage.warning('请填写登录账号')
    return
  }
  if (!String(createForm.password || '').trim()) {
    ElMessage.warning('请填写登录密码')
    return
  }
  if (!String(createForm.roleCode || '').trim()) {
    ElMessage.warning('请选择角色')
    return
  }
  savingCreate.value = true
  try {
    const data = await createAdminUser({
      realName: createForm.realName.trim(),
      nickName: createForm.nickName.trim() || null,
      phone: createForm.phone.trim() || null,
      loginName: createForm.loginName.trim().toLowerCase(),
      password: createForm.password,
      roleCode: createForm.roleCode,
      avatarUrl: createForm.avatarUrl.trim() || null
    })
    createVisible.value = false
    ElMessage.success('后台用户已创建')
    if (data && data.loginName) {
      ElMessage.info(`登录账号：${data.loginName}`)
    }
    await load(1)
  } catch (error) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    savingCreate.value = false
  }
}

function openEdit(row) {
  if (isSuperAdmin(row)) {
    ElMessage.warning('超级管理员角色不可修改')
    return
  }
  if (!roleOptionList.value.length) {
    ElMessage.warning('请先在“后台角色管理”中创建并启用角色')
    return
  }
  editForm.id = row && row.id ? row.id : null
  editForm.roleCode = String((row && row.roleCode) || resolveDefaultRoleCode()).toLowerCase()
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.id) return
  if (!String(editForm.roleCode || '').trim()) {
    ElMessage.warning('请选择角色')
    return
  }
  savingEdit.value = true
  try {
    await updateUserRole(editForm.id, {
      roleCode: editForm.roleCode
    })
    editVisible.value = false
    ElMessage.success('用户角色已更新')
    await load(page.value)
  } catch (error) {
    ElMessage.error(error.message || '更新失败')
  } finally {
    savingEdit.value = false
  }
}

function openResetPassword(row) {
  if (!row || !row.id || isSuperAdmin(row)) return
  resetPasswordForm.id = row.id
  resetPasswordForm.loginName = String(row.loginName || '').trim()
  resetPasswordForm.newPassword = ''
  resetPasswordForm.confirmPassword = ''
  resetPasswordVisible.value = true
}

async function submitResetPassword() {
  if (!resetPasswordForm.id) return
  if (!String(resetPasswordForm.newPassword || '')) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (resetPasswordForm.newPassword !== resetPasswordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingResetPassword.value = true
  try {
    const data = await resetUserPassword(resetPasswordForm.id, {
      newPassword: resetPasswordForm.newPassword
    })
    resetPasswordVisible.value = false
    const revokedCount = Number((data && data.revokedCount) || 0)
    if (revokedCount > 0) {
      ElMessage.success(`密码已重置，并已使 ${revokedCount} 个会话失效`)
    } else {
      ElMessage.success('密码已重置')
    }
  } catch (error) {
    ElMessage.error(error.message || '密码重置失败')
  } finally {
    savingResetPassword.value = false
  }
}

async function toggleEnabled(row) {
  const tip = isDisabled(row) ? '确认解除禁用该后台账号吗？' : '确认禁用该后台账号吗？'
  try {
    await ElMessageBox.confirm(tip, '确认操作', { type: 'warning' })
    await updateUserEnabled(row.id, isDisabled(row))
    ElMessage.success('可用状态已更新')
    await load(page.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || '可用状态更新失败')
  }
}

async function revokeSessions(row) {
  if (!row || !row.id || isSuperAdmin(row)) return
  const name = resolveDisplayName(row)
  try {
    await ElMessageBox.confirm(`确认强制下线后台用户“${name}”的全部会话吗？`, '强制下线确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '强制下线确认失败')
    }
    return
  }
  batchSubmitting.value = true
  try {
    const data = await revokeUserSessions(row.id)
    const revokedCount = Number((data && data.revokedCount) || 0)
    if (revokedCount > 0) {
      ElMessage.success(`已强制下线 ${revokedCount} 个会话`)
    } else {
      ElMessage.success('该用户当前无活跃会话')
    }
  } catch (error) {
    ElMessage.error(error.message || '强制下线失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function removeUser(row) {
  if (!row || !row.id || isSuperAdmin(row)) return
  const name = resolveDisplayName(row)
  try {
    await ElMessageBox.confirm(`确认删除后台用户“${name}”吗？`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除确认失败')
    }
    return
  }
  batchSubmitting.value = true
  try {
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await load(Math.min(Number(page.value || 1), maxPage))
  } catch (error) {
    ElMessage.error(error.message || '用户删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

function openBatchEdit() {
  if (!roleOptionList.value.length) {
    ElMessage.warning('请先在“后台角色管理”中创建并启用角色')
    return
  }
  const targets = batchEditableRows.value
  if (!targets.length) {
    ElMessage.warning('请先选择可编辑的用户')
    return
  }
  const first = targets[0]
  batchEditForm.roleCode = String((first && first.roleCode) || resolveDefaultRoleCode()).toLowerCase()
  batchEditVisible.value = true
}

async function submitBatchEdit() {
  const targets = batchEditableRows.value
  if (!targets.length) {
    ElMessage.warning('当前没有可批量编辑的用户')
    return
  }
  if (!String(batchEditForm.roleCode || '').trim()) {
    ElMessage.warning('请选择角色')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量编辑已选 ${targets.length} 位用户吗？`, '批量编辑确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || '批量编辑确认失败')
    return
  }

  batchSubmitting.value = true
  try {
    const payload = { roleCode: batchEditForm.roleCode }
    const results = await Promise.allSettled(targets.map((row) => updateUserRole(row.id, payload)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    batchEditVisible.value = false
    await load(page.value)
    if (failedCount > 0) {
      ElMessage.warning(`批量编辑完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`批量编辑成功：${successCount} 位用户`)
    }
  } catch (error) {
    ElMessage.error(error.message || '批量编辑失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function batchSetEnabled(nextEnabled) {
  const targets = batchEditableRows.value.filter((row) => Number(row.enabled) !== (nextEnabled ? 1 : 0))
  if (!targets.length) {
    ElMessage.warning(nextEnabled ? '当前选择中没有可启用用户' : '当前选择中没有可禁用用户')
    return
  }
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认批量${actionText}已选 ${targets.length} 位用户吗？`, `批量${actionText}确认`, { type: 'warning' })
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || `批量${actionText}确认失败`)
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(targets.map((row) => updateUserEnabled(row.id, !!nextEnabled)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await load(page.value)
    if (failedCount > 0) {
      ElMessage.warning(`批量${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`批量${actionText}成功：${successCount} 位用户`)
    }
  } catch (error) {
    ElMessage.error(error.message || `批量${actionText}失败`)
  } finally {
    batchSubmitting.value = false
  }
}

async function batchRevokeSessions() {
  const targets = batchEditableRows.value.filter((row) => Number(row && row.id) > 0)
  if (!targets.length) {
    ElMessage.warning('请先选择要强制下线的后台用户')
    return
  }
  try {
    await ElMessageBox.confirm(`确认强制下线已选 ${targets.length} 位后台用户吗？`, '批量强制下线确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || '批量强制下线确认失败')
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(targets.map((row) => revokeUserSessions(row.id)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    const revokedTotal = results.reduce((sum, item) => {
      if (item.status !== 'fulfilled') return sum
      return sum + Number(((item.value && item.value.revokedCount) || 0))
    }, 0)
    clearBatchSelection()
    if (failedCount > 0) {
      ElMessage.warning(`批量强制下线完成：成功 ${successCount}，失败 ${failedCount}，总下线会话 ${revokedTotal}`)
    } else {
      ElMessage.success(`批量强制下线成功：${successCount} 位用户，总下线会话 ${revokedTotal}`)
    }
  } catch (error) {
    ElMessage.error(error.message || '批量强制下线失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function batchDeleteUsers() {
  const targets = batchEditableRows.value.filter((row) => Number(row && row.id) > 0)
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的后台用户')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选 ${targets.length} 位后台用户吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel') return
    ElMessage.error(error.message || '批量删除确认失败')
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(targets.map((row) => deleteUser(row.id)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - successCount)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await load(Math.min(Number(page.value || 1), maxPage))
    if (failedCount > 0) {
      ElMessage.warning(`批量删除完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`批量删除成功：${successCount} 位用户`)
    }
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

onMounted(async () => {
  await loadRoleOptions()
  await load(1)
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', clearBatchSelection)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', clearBatchSelection)
  }
})
</script>

<style scoped>
.admin-user-page {
  display: block;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.batch-toggle-btn.is-active {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.table-op-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.reset-password-btn {
  min-width: 88px;
}

.table-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.batch-hint {
  color: var(--text-sub);
  line-height: 1.7;
}
</style>
