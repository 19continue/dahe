<template>
  <div>
    <PageToolbar
      title="后台角色管理"
      subtitle="角色仅作用于后台用户，小程序用户不参与角色体系。权限粒度为菜单/页面级。"
      collapsible
      :summary="[
        keyword ? `关键词：${keyword}` : '',
        enabled !== '' ? `状态：${Number(enabled) === 1 ? '启用' : '禁用'}` : '状态：全部',
        batchMode ? `多选：已选 ${selectedRows.length} 条` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="keyword" placeholder="角色名称/说明" clearable style="width: 240px" />
        <el-select v-model="enabled" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="load(1)">查询</el-button>
        <el-button type="primary" @click="openCreate">新增角色</el-button>
        <el-button :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <el-button
          v-if="batchMode"
          type="success"
          :disabled="!batchEnableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchSetEnabled(true)"
        >
          批量启用（{{ batchEnableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="warning"
          :disabled="!batchDisableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchSetEnabled(false)"
        >
          批量禁用（{{ batchDisableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="danger"
          plain
          :disabled="!batchDeleteCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchDelete"
        >
          批量删除（{{ batchDeleteCount }}）
        </el-button>
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
      <el-table-column prop="sortOrder" label="排序" :width="tableWidths.sortOrder" />
      <el-table-column label="角色名称" :min-width="tableWidths.roleName">
        <template #default="scope">
          <div class="role-name-cell">
            <span>{{ scope.row.roleName }}</span>
            <el-tag v-if="isLockedRole(scope.row)" size="small" type="danger" effect="plain">系统锁定</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="继承角色" :width="tableWidths.inheritRoleCode">
        <template #default="scope">
          <el-tag size="small" :type="inheritTagType(scope.row.inheritRoleCode)">
            {{ formatInherit(scope.row.inheritRoleCode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="菜单权限" :min-width="tableWidths.menuPermissions">
        <template #default="scope">
          <div class="menu-tags">
            <el-tag
              v-for="(name, idx) in menuLabelPreview(scope.row.menuPermissions).slice(0, 3)"
              :key="`${scope.row.id}-${idx}-${name}`"
              size="small"
              effect="plain"
            >
              {{ name }}
            </el-tag>
            <el-tag
              v-if="menuLabelPreview(scope.row.menuPermissions).length > 3"
              size="small"
              type="info"
              effect="plain"
            >
              +{{ menuLabelPreview(scope.row.menuPermissions).length - 3 }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="userCount" label="关联用户" :width="tableWidths.userCount" />
      <el-table-column label="状态" :width="tableWidths.enabled">
        <template #default="scope">
          <el-tag :type="Number(scope.row.enabled) === 1 ? 'success' : 'danger'" size="small">
            {{ Number(scope.row.enabled) === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" :min-width="tableWidths.description" show-overflow-tooltip />
      <el-table-column label="操作" :width="tableWidths.actions" fixed="right" class-name="op-col">
        <template #default="scope">
          <div class="table-op-line">
            <el-button
              size="small"
              type="primary"
              :disabled="isLockedRole(scope.row)"
              @click="openEdit(scope.row)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              :type="Number(scope.row.enabled) === 1 ? 'warning' : 'success'"
              plain
              :disabled="isLockedRole(scope.row)"
              @click="toggleEnabled(scope.row)"
            >
              {{ Number(scope.row.enabled) === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="!canDelete(scope.row)"
              @click="removeRole(scope.row)"
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
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @size-change="onPageSizeChange"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="editVisible" :title="editMode === 'create' ? '新增角色' : '编辑角色'" width="760px" destroy-on-close>
      <el-form label-width="110px">
        <div class="form-grid">
          <el-form-item label="角色名称">
            <el-input v-model="form.roleName" placeholder="必填，例如：农资主管" />
          </el-form-item>
          <el-form-item label="继承角色">
            <el-select v-model="form.inheritRoleCode" clearable style="width: 100%">
              <el-option label="不继承" value="" />
              <el-option
                v-for="item in inheritRoleOptions"
                :key="item.roleCode"
                :label="item.roleName"
                :value="item.roleCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="-99999" :max="99999" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="状态" v-if="editMode === 'create'">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-form-item label="角色说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可选，用于说明角色职责边界"
          />
        </el-form-item>

        <el-form-item label="菜单权限">
          <div class="menu-permission-box">
            <div class="menu-tools">
              <el-button size="small" @click="fillDefaultMenus">填充基础菜单</el-button>
              <el-button size="small" @click="selectAllMenus">全选</el-button>
              <el-button size="small" @click="clearMenus">清空</el-button>
              <span class="menu-tip">已选 {{ form.menuPermissions.length }} 项</span>
            </div>
            <el-checkbox-group v-model="form.menuPermissions" class="menu-groups">
              <div v-for="group in menuGroups" :key="group.key" class="menu-group">
                <div class="menu-group-title">{{ group.label }}</div>
                <div class="menu-items">
                  <el-checkbox v-for="item in group.items" :key="item.key" :label="item.key">
                    {{ item.label }}
                  </el-checkbox>
                </div>
              </div>
            </el-checkbox-group>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import { buildAdaptiveWidths } from '../utils/tableAutoWidth'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const enabled = ref('')

const menuGroups = ref([])
const menuLabelMap = ref(new Map())
const roleOptions = ref([])

const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)

const editVisible = ref(false)
const editMode = ref('create')
const saving = ref(false)
const form = reactive({
  id: null,
  roleName: '',
  inheritRoleCode: '',
  sortOrder: 0,
  enabled: true,
  description: '',
  menuPermissions: []
})

const allMenuKeys = computed(() => {
  const out = []
  menuGroups.value.forEach((group) => {
    ;(group.items || []).forEach((item) => {
      const key = String((item && item.key) || '').trim()
      if (key && !out.includes(key)) out.push(key)
    })
  })
  return out
})

const viewportWidth = ref(typeof window === 'undefined' ? 1400 : window.innerWidth)

function syncViewportWidth() {
  if (typeof window === 'undefined') return
  viewportWidth.value = window.innerWidth
}

const tableWidths = computed(() =>
  buildAdaptiveWidths({
    containerWidth: Math.max(980, viewportWidth.value - 320),
    rows: rows.value,
    columns: [
      { key: 'sortOrder', title: '排序', min: 72, max: 78, fixed: true },
      { key: 'roleName', title: '角色名称', min: 140, max: 220, weight: 1.1 },
      { key: 'inheritRoleCode', title: '继承角色', min: 112, max: 160, weight: 0.9, extractor: (row) => formatInherit(row && row.inheritRoleCode) },
      { key: 'menuPermissions', title: '菜单权限', min: 220, max: 360, weight: 1.7, extractor: (row) => menuLabelPreview(row && row.menuPermissions) },
      { key: 'userCount', title: '关联用户', min: 90, max: 98, fixed: true },
      { key: 'enabled', title: '状态', min: 88, max: 94, fixed: true },
      { key: 'description', title: '说明', min: 160, max: 260, weight: 1.2 },
      { key: 'actions', title: '操作', min: 198, max: 198, fixed: true }
    ]
  })
)

const roleNameMap = computed(() => {
  const map = new Map()
  ;(Array.isArray(roleOptions.value) ? roleOptions.value : []).forEach((item) => {
    const code = String((item && item.roleCode) || '').trim().toLowerCase()
    const name = String((item && item.roleName) || '').trim()
    if (code && name) {
      map.set(code, name)
    }
  })
  return map
})

const inheritRoleOptions = computed(() => {
  const currentId = Number(form.id || 0)
  return (Array.isArray(roleOptions.value) ? roleOptions.value : [])
    .filter((item) => Number((item && item.id) || 0) !== currentId && Number((item && item.enabled) || 0) === 1)
    .map((item) => ({
      roleCode: String((item && item.roleCode) || '').trim().toLowerCase(),
      roleName: String((item && item.roleName) || '').trim()
    }))
    .filter((item) => item.roleCode && item.roleName)
})

function formatInherit(value) {
  const code = String(value || '').trim().toLowerCase()
  if (!code) return '-'
  return roleNameMap.value.get(code) || code
}

function inheritTagType(value) {
  const code = String(value || '').trim().toLowerCase()
  if (!code) return 'info'
  return roleNameMap.value.has(code) ? '' : 'warning'
}

function canDelete(row) {
  if (!row || !row.id) return false
  if (isLockedRole(row)) return false
  if (Number(row.userCount || 0) > 0) return false
  return true
}

function canBatchEnable(row) {
  return !!(row && row.id && Number(row.enabled) === 0 && !isLockedRole(row))
}

function canBatchDisable(row) {
  if (!row || !row.id) return false
  if (isLockedRole(row)) return false
  return Number(row.enabled) === 1
}

function isLockedRole(row) {
  if (!row || !row.id) return false
  return Number(row.locked || 0) === 1 || Number(row.superAdminBound || 0) === 1
}

function roleLockedReason(row) {
  const reason = String((row && row.lockedReason) || '').trim()
  return reason || '该角色已被系统锁定，暂不允许修改'
}

const batchEnableCount = computed(() => {
  const rowsList = Array.isArray(selectedRows.value) ? selectedRows.value : []
  return rowsList.filter((row) => canBatchEnable(row)).length
})

const batchDisableCount = computed(() => {
  const rowsList = Array.isArray(selectedRows.value) ? selectedRows.value : []
  return rowsList.filter((row) => canBatchDisable(row)).length
})

const batchDeleteCount = computed(() => {
  const rowsList = Array.isArray(selectedRows.value) ? selectedRows.value : []
  return rowsList.filter((row) => canDelete(row)).length
})

function menuLabelPreview(menuPermissions) {
  const keys = Array.isArray(menuPermissions) ? menuPermissions : []
  return keys
    .map((key) => {
      const text = String(key || '').trim()
      return text ? menuLabelMap.value.get(text) || text : ''
    })
    .filter(Boolean)
}

function resolveFrontendRouteMetaMap() {
  const map = new Map()
  const routes = typeof router.getRoutes === 'function' ? router.getRoutes() : []
  ;(Array.isArray(routes) ? routes : []).forEach((route) => {
    const path = String((route && route.path) || '').trim()
    if (!path || path === '/' || path === '/login') return
    if (!path.startsWith('/')) return
    if (path.includes('/:')) return
    const title = String((route && route.meta && route.meta.title) || '').trim()
    map.set(path, title || path)
  })
  return map
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
  if (!batchMode.value) {
    clearBatchSelection()
  }
}

function resetForm() {
  form.id = null
  form.roleName = ''
  form.inheritRoleCode = ''
  form.sortOrder = 0
  form.enabled = true
  form.description = ''
  form.menuPermissions = []
}

function fillDefaultMenus() {
  form.menuPermissions = ['/dashboard'].filter((key) => allMenuKeys.value.includes(key))
}

function selectAllMenus() {
  form.menuPermissions = [...allMenuKeys.value]
}

function clearMenus() {
  form.menuPermissions = []
}

async function loadMenuOptions() {
  try {
    const data = await request.get('/admin/roles/menu-codes')
    const frontendRouteMap = resolveFrontendRouteMetaMap()
    const items = (Array.isArray(data) ? data : [])
      .map((code) => String(code || '').trim())
      .filter((code) => code && frontendRouteMap.has(code))
      .sort((a, b) => a.localeCompare(b))
      .map((code) => ({
        key: code,
        label: frontendRouteMap.get(code) || code
      }))
    menuGroups.value = items.length
      ? [{ key: 'routes', label: '路由菜单', items }]
      : []
    const map = new Map()
    items.forEach((item) => {
      const key = String((item && item.key) || '').trim()
      const label = String((item && item.label) || '').trim()
      if (key) map.set(key, label || key)
    })
    menuLabelMap.value = map
  } catch (e) {
    menuGroups.value = []
    menuLabelMap.value = new Map()
    ElMessage.error(e.message || '菜单权限选项加载失败')
  }
}

async function loadRoleOptions() {
  try {
    const data = await request.get('/admin/roles/options', {
      params: { includeDisabled: true }
    })
    roleOptions.value = Array.isArray(data) ? data : []
  } catch (e) {
    roleOptions.value = []
  }
}

async function load(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const [data, options] = await Promise.all([
      request.get('/admin/roles', {
        params: {
          page: page.value,
          pageSize: pageSize.value,
          keyword: keyword.value,
          enabled: enabled.value === '' ? undefined : Number(enabled.value)
        }
      }),
      request.get('/admin/roles/options', {
        params: { includeDisabled: true }
      })
    ])
    rows.value = (data && data.records) || []
    total.value = Number((data && data.total) || 0)
    roleOptions.value = Array.isArray(options) ? options : []
    clearBatchSelection()
  } catch (e) {
    ElMessage.error(e.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  load(1)
}

function openCreate() {
  editMode.value = 'create'
  resetForm()
  fillDefaultMenus()
  editVisible.value = true
}

function openEdit(row) {
  if (isLockedRole(row)) {
    ElMessage.warning(roleLockedReason(row))
    return
  }
  editMode.value = 'edit'
  form.id = row && row.id ? row.id : null
  form.roleName = String((row && row.roleName) || '')
  form.inheritRoleCode = String((row && row.inheritRoleCode) || '').toLowerCase()
  form.sortOrder = Number((row && row.sortOrder) || 0)
  form.enabled = Number((row && row.enabled) || 0) === 1
  form.description = String((row && row.description) || '')
  form.menuPermissions = Array.isArray(row && row.menuPermissions) ? [...row.menuPermissions] : []
  editVisible.value = true
}

function buildPayload() {
  return {
    roleName: form.roleName.trim(),
    inheritRoleCode: String(form.inheritRoleCode || '').trim() || null,
    sortOrder: Number(form.sortOrder || 0),
    description: form.description.trim() || null,
    menuPermissions: Array.from(new Set((form.menuPermissions || []).map((item) => String(item || '').trim()).filter(Boolean)))
  }
}

async function submitEdit() {
  if (!form.roleName.trim()) {
    ElMessage.warning('请填写角色名称')
    return
  }
  if (!form.menuPermissions.length) {
    ElMessage.warning('请至少选择一个菜单权限')
    return
  }

  saving.value = true
  try {
    if (editMode.value === 'create') {
      const payload = {
        ...buildPayload(),
        enabled: form.enabled ? 1 : 0
      }
      await request.post('/admin/roles', payload)
      ElMessage.success('角色已创建')
    } else {
      await request.put(`/admin/roles/${form.id}`, buildPayload())
      ElMessage.success('角色已更新')
    }
    editVisible.value = false
    await load(editMode.value === 'create' ? 1 : page.value)
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  if (isLockedRole(row)) {
    ElMessage.warning(roleLockedReason(row))
    return
  }
  const nextEnabled = Number(row.enabled) !== 1
  const tip = nextEnabled ? '确认启用该角色吗？' : '确认禁用该角色吗？'
  try {
    await ElMessageBox.confirm(tip, '确认操作', { type: 'warning' })
    await request.put(`/admin/roles/${row.id}/enabled`, { enabled: nextEnabled ? 1 : 0 })
    ElMessage.success('状态已更新')
    await load(page.value)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '状态更新失败')
  }
}

async function removeRole(row) {
  if (isLockedRole(row)) {
    ElMessage.warning(roleLockedReason(row))
    return
  }
  if (!canDelete(row)) {
    ElMessage.warning('已分配用户的角色不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.roleName || row.roleCode}」吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/roles/${row.id}`)
    ElMessage.success('角色已删除')
    await load(page.value)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function runBatch(rowsList, task, actionText) {
  if (!rowsList.length) return
  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(rowsList.map((row) => task(row)))
    const successCount = results.filter((x) => x.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await load(page.value)
    if (failedCount > 0) {
      ElMessage.warning(`${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
    } else {
      ElMessage.success(`${actionText}成功：${successCount} 条`)
    }
  } catch (e) {
    ElMessage.error(e.message || `${actionText}失败`)
  } finally {
    batchSubmitting.value = false
  }
}

async function batchSetEnabled(nextEnabled) {
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) =>
    nextEnabled ? canBatchEnable(row) : canBatchDisable(row)
  )
  if (!targets.length) {
    ElMessage.warning(nextEnabled ? '没有可启用角色' : '没有可禁用角色')
    return
  }
  const actionText = nextEnabled ? '批量启用' : '批量禁用'
  try {
    await ElMessageBox.confirm(`确认${actionText}已选 ${targets.length} 条角色吗？`, `${actionText}确认`, { type: 'warning' })
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e.message || `${actionText}确认失败`)
    return
  }
  await runBatch(targets, (row) => request.put(`/admin/roles/${row.id}/enabled`, { enabled: nextEnabled ? 1 : 0 }), actionText)
}

async function batchDelete() {
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => canDelete(row))
  if (!targets.length) {
    ElMessage.warning('没有可删除角色')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量删除已选 ${targets.length} 条角色吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e.message || '批量删除确认失败')
    return
  }
  await runBatch(targets, (row) => request.delete(`/admin/roles/${row.id}`), '批量删除')
}

onMounted(async () => {
  syncViewportWidth()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncViewportWidth, { passive: true })
  }
  await loadMenuOptions()
  await loadRoleOptions()
  await load(1)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncViewportWidth)
  }
})
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 14px;
}

.menu-permission-box {
  width: 100%;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 10px;
  background: #fff;
}

.menu-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.menu-tip {
  margin-left: auto;
  color: var(--text-sub);
  font-size: 12px;
}

.menu-groups {
  display: block;
}

.menu-group {
  border: 1px dashed var(--line-soft);
  border-radius: 8px;
  padding: 8px 10px;
}

.menu-group + .menu-group {
  margin-top: 10px;
}

.menu-group-title {
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}

.menu-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
}

.menu-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.role-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 960px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

