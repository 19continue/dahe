<template>
  <div class="messages-page">
    <PageToolbar
      title="消息发布"
      subtitle="按对象和权限创建消息任务，异步派发，不阻塞其他业务请求。"
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.noticeType ? `类型：${formatNoticeType(filters.noticeType)}` : '',
        filters.targetType ? `对象：${formatTargetType(filters.targetType)}` : '',
        filters.dispatchStatus ? `状态：${formatDispatchStatus(filters.dispatchStatus)}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="标题/内容/创建人" clearable style="width: 220px" @keyup.enter="loadRows(1)" />
        <el-select v-model="filters.noticeType" clearable placeholder="消息类型" style="width: 120px">
          <el-option v-for="item in noticeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.targetType" clearable placeholder="发送对象" style="width: 160px">
          <el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.dispatchStatus" clearable placeholder="派发状态" style="width: 130px">
          <el-option v-for="item in dispatchStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openCreateDialog">新建消息</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" class="message-config-card">
      <template #header>
        <div class="card-head">
          <span>消息保留配置</span>
          <span class="card-meta">这里只保存配置，不会在本轮立即执行自动清理。</span>
        </div>
      </template>
      <el-form inline class="compact-inline-form">
        <el-form-item label="自动消息清理">
          <el-switch v-model="messageConfig.autoPurgeEnabled" />
        </el-form-item>
        <el-form-item label="在库保留天数">
          <el-input-number v-model="messageConfig.retainDays" :min="1" :step="30" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
      <div class="config-meta">
        <span>最后更新人：{{ messageConfig.updatedByName || '-' }}</span>
        <span>最后更新时间：{{ formatDateTime(messageConfig.updatedAt) }}</span>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>消息任务列表</span>
          <span class="card-meta">删除任务后，已派发收件箱记录会同步隐藏</span>
        </div>
      </template>
      <el-table :data="rows" border v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="scope">
            <el-tag size="small">{{ formatNoticeType(scope.row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送对象" min-width="180">
          <template #default="scope">
            <div class="target-cell">
              <span>{{ formatTargetType(scope.row.targetType) }}</span>
              <span v-if="scope.row.routeCode">路由：{{ resolveRouteLabel(scope.row.routeCode) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="派发状态" width="110">
          <template #default="scope">
            <el-tag size="small" :type="dispatchStatusTagType(scope.row.dispatchStatus)">
              {{ formatDispatchStatus(scope.row.dispatchStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投递结果" width="170">
          <template #default="scope">
            <div class="count-cell">
              <span>目标：{{ scope.row.targetCount || 0 }}</span>
              <span>成功：{{ scope.row.successCount || 0 }}</span>
              <span>失败：{{ scope.row.failedCount || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果说明" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.resultMessage || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建信息" width="190">
          <template #default="scope">
            <div class="time-cell">
              <span>创建人：{{ scope.row.createdByName || '-' }}</span>
              <span>创建：{{ formatDateTime(scope.row.createdAt) }}</span>
              <span>更新：{{ formatDateTime(scope.row.updatedAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button size="small" type="danger" plain @click="removeTask(scope.row)">删除</el-button>
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
          @current-change="loadRows"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialog.visible" title="新建消息" width="620px">
      <el-form label-width="96px">
        <el-form-item label="消息标题">
          <el-input v-model="createDialog.form.title" maxlength="128" show-word-limit placeholder="请输入消息标题" />
        </el-form-item>
        <el-form-item label="消息内容">
          <el-input v-model="createDialog.form.content" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="请输入消息内容" />
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="createDialog.form.noticeType" style="width: 100%">
            <el-option v-for="item in noticeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发送对象">
          <el-select v-model="createDialog.form.targetType" style="width: 100%">
            <el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createDialog.form.targetType === 'admin_role'" label="后台角色">
          <el-select
            v-model="createDialog.form.targetRoleCodes"
            multiple
            collapse-tags
            collapse-tags-tooltip
            filterable
            style="width: 100%"
            placeholder="选择接收消息的后台角色"
          >
            <el-option v-for="item in roleOptions" :key="item.roleCode" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createDialog.form.targetType === 'admin_route'" label="路由权限">
          <el-select v-model="createDialog.form.routeCode" filterable style="width: 100%" placeholder="选择要接收消息的后台页面路由">
            <el-option v-for="item in routeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createDialog.form.targetType === 'explicit_users'" label="指定用户">
          <el-select
            v-model="createDialog.form.targetUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
            placeholder="输入姓名、昵称、手机号后搜索"
          >
            <el-option v-for="item in userOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitTask">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import {
  createAdminMessageTask,
  deleteAdminMessageTask,
  fetchAdminMessageConfig,
  fetchAdminMessageTasks,
  saveAdminMessageConfig
} from '../api/adminMessage'
import { fetchAdminUsers, fetchRoleOptions } from '../api/adminUser'

const noticeTypeOptions = [
  { label: '系统通知', value: 'system' },
  { label: '消息通知', value: 'message' },
  { label: '审核通知', value: 'review' },
  { label: '状态通知', value: 'status' }
]
const targetTypeOptions = [
  { label: '全部后台用户', value: 'admin_all' },
  { label: '按后台角色', value: 'admin_role' },
  { label: '按后台路由权限', value: 'admin_route' },
  { label: '全部已通过小程序用户', value: 'miniapp_approved' },
  { label: '全部小程序控制台用户', value: 'miniapp_console' },
  { label: '指定用户', value: 'explicit_users' }
]
const dispatchStatusOptions = [
  { label: '待发送', value: 'pending' },
  { label: '发送中', value: 'sending' },
  { label: '已发送', value: 'sent' },
  { label: '发送失败', value: 'failed' }
]
const routeOptions = [
  { label: '小程序用户审核（/users）', value: '/users' },
  { label: '小程序用户管理（/users/manage）', value: '/users/manage' },
  { label: '后台用户管理（/admin-users）', value: '/admin-users' },
  { label: '角色与权限配置（/roles）', value: '/roles' },
  { label: '田块管理（/field-manage）', value: '/field-manage' },
  { label: '田块种植计划（/field-cycles）', value: '/field-cycles' },
  { label: '农事记录管理（/farm-records-manage）', value: '/farm-records-manage' },
  { label: '作物品种管理（/crop-manage）', value: '/crop-manage' },
  { label: '种子批次管理（/seed-manage）', value: '/seed-manage' },
  { label: '资源审核（/assets/review）', value: '/assets/review' },
  { label: '图片与资源管理（/assets）', value: '/assets' },
  { label: '系统操作日志（/operation-logs）', value: '/operation-logs' },
  { label: '消息发布（/messages）', value: '/messages' }
]

const loading = ref(false)
const configSaving = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const userSearchLoading = ref(false)
const userOptions = ref([])
const roleOptions = ref([])
const messageConfig = reactive({
  autoPurgeEnabled: false,
  retainDays: 90,
  updatedByName: '',
  updatedAt: ''
})
const filters = reactive({
  keyword: '',
  noticeType: '',
  targetType: '',
  dispatchStatus: ''
})
const createDialog = reactive({
  visible: false,
  submitting: false,
  form: {
    title: '',
    content: '',
    noticeType: 'message',
    targetType: 'admin_route',
    routeCode: '/users',
    targetUserIds: [],
    targetRoleCodes: []
  }
})

function formatNoticeType(value) {
  const hit = noticeTypeOptions.find((item) => item.value === String(value || '').trim())
  return hit ? hit.label : '系统通知'
}

function formatTargetType(value) {
  const hit = targetTypeOptions.find((item) => item.value === String(value || '').trim())
  return hit ? hit.label : '-'
}

function formatDispatchStatus(value) {
  const hit = dispatchStatusOptions.find((item) => item.value === String(value || '').trim())
  return hit ? hit.label : '-'
}

function dispatchStatusTagType(value) {
  const status = String(value || '').trim()
  if (status === 'sent') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'sending') return 'warning'
  return 'info'
}

function resolveRouteLabel(routeCode) {
  const hit = routeOptions.find((item) => item.value === String(routeCode || '').trim())
  return hit ? hit.label : routeCode
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ').slice(0, 16)
}

function resetFilters() {
  filters.keyword = ''
  filters.noticeType = ''
  filters.targetType = ''
  filters.dispatchStatus = ''
  loadRows(1)
}

function openCreateDialog() {
  createDialog.form.title = ''
  createDialog.form.content = ''
  createDialog.form.noticeType = 'message'
  createDialog.form.targetType = 'admin_route'
  createDialog.form.routeCode = '/users'
  createDialog.form.targetUserIds = []
  createDialog.form.targetRoleCodes = []
  userOptions.value = []
  createDialog.visible = true
}

async function loadRoleOptions() {
  try {
    const data = await fetchRoleOptions(false)
    roleOptions.value = Array.isArray(data) ? data : []
  } catch (error) {
    roleOptions.value = []
  }
}

async function loadConfig() {
  try {
    const data = await fetchAdminMessageConfig()
    messageConfig.autoPurgeEnabled = !!(data && data.autoPurgeEnabled)
    messageConfig.retainDays = Number((data && data.retainDays) || 90)
    messageConfig.updatedByName = String((data && data.updatedByName) || '').trim()
    messageConfig.updatedAt = String((data && data.updatedAt) || '').trim()
  } catch (error) {
    messageConfig.autoPurgeEnabled = false
    messageConfig.retainDays = 90
    messageConfig.updatedByName = ''
    messageConfig.updatedAt = ''
  }
}

async function searchUsers(keyword) {
  const text = String(keyword || '').trim()
  if (!text) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const data = await fetchAdminUsers({
      keyword: text,
      page: 1,
      pageSize: 20,
      recycleFlag: 0,
      reviewOnly: false
    })
    const records = Array.isArray(data && data.records) ? data.records : []
    userOptions.value = records.map((item) => {
      const name = item.realName || item.nickName || item.loginName || item.id
      const typeText = String(item.userType || '').trim().toLowerCase() === 'admin' ? '后台' : '小程序'
      return {
        value: item.id,
        label: `${name}（${typeText}）`
      }
    })
  } catch (error) {
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

async function loadRows(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await fetchAdminMessageTasks({
      page: page.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      noticeType: filters.noticeType || undefined,
      targetType: filters.targetType || undefined,
      dispatchStatus: filters.dispatchStatus || undefined
    })
    rows.value = Array.isArray(data && data.records) ? data.records : []
    total.value = Number((data && data.total) || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
    ElMessage.error(error.message || '消息任务加载失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRows(1)
}

async function saveConfig() {
  configSaving.value = true
  try {
    await saveAdminMessageConfig({
      autoPurgeEnabled: !!messageConfig.autoPurgeEnabled,
      retainDays: Number(messageConfig.retainDays || 90)
    })
    ElMessage.success('消息保留配置已保存')
    await loadConfig()
  } catch (error) {
    ElMessage.error(error.message || '消息保留配置保存失败')
  } finally {
    configSaving.value = false
  }
}

async function submitTask() {
  if (!String(createDialog.form.title || '').trim()) {
    ElMessage.warning('请输入消息标题')
    return
  }
  if (!String(createDialog.form.content || '').trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }
  if (createDialog.form.targetType === 'admin_route' && !String(createDialog.form.routeCode || '').trim()) {
    ElMessage.warning('请选择路由权限')
    return
  }
  if (createDialog.form.targetType === 'admin_role' && !createDialog.form.targetRoleCodes.length) {
    ElMessage.warning('请至少选择一个后台角色')
    return
  }
  if (createDialog.form.targetType === 'explicit_users' && !createDialog.form.targetUserIds.length) {
    ElMessage.warning('请至少选择一个接收用户')
    return
  }
  createDialog.submitting = true
  try {
    await createAdminMessageTask({
      title: createDialog.form.title,
      content: createDialog.form.content,
      noticeType: createDialog.form.noticeType,
      targetType: createDialog.form.targetType,
      routeCode: createDialog.form.targetType === 'admin_route' ? createDialog.form.routeCode : undefined,
      targetRoleCodes: createDialog.form.targetType === 'admin_role' ? createDialog.form.targetRoleCodes : undefined,
      targetUserIds: createDialog.form.targetType === 'explicit_users' ? createDialog.form.targetUserIds : undefined
    })
    ElMessage.success('消息任务已创建，系统正在异步派发')
    createDialog.visible = false
    await loadRows(1)
  } catch (error) {
    ElMessage.error(error.message || '消息任务创建失败')
  } finally {
    createDialog.submitting = false
  }
}

async function removeTask(row) {
  try {
    await ElMessageBox.confirm(`确认删除消息“${row.title || row.id}”吗？删除后对应收件箱记录会一并隐藏。`, '删除消息确认', { type: 'warning' })
    await deleteAdminMessageTask(row.id, { expectedUpdatedAt: row.updatedAt })
    ElMessage.success('消息已删除')
    await loadRows(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除消息失败')
    }
  }
}

onMounted(() => {
  loadRoleOptions()
  loadRows(1)
  loadConfig()
})
</script>

<style scoped>
.messages-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-meta {
  font-size: 13px;
  color: #6b7280;
}

.compact-inline-form {
  row-gap: 8px;
}

.config-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.target-cell,
.count-cell,
.time-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
