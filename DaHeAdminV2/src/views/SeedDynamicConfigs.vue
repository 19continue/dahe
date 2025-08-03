<template>
  <div class="seed-dynamic-config-page">
    <PageToolbar
      :title="pageTitle"
      :subtitle="pageSubtitle"
      collapsible
      :summary="[
        `配置范围：${scopeLabel}`,
        filters.status ? `状态：${statusFilterLabel(filters.status)}` : '',
        filters.keyword ? `关键字：${filters.keyword}` : '',
        batchMode ? `多选：已选 ${selectedRows.length} 条` : '',
        detailVisible && selected ? `当前：${selected.configName}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" clearable placeholder="配置名关键字" style="width: 220px" />
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px">
          <el-option label="全部" value="all" />
          <el-option label="启用" value="enabled" />
          <el-option label="停用" value="disabled" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openCreate">新建配置</el-button>
        <el-button
          type="default"
          plain
          class="batch-toggle-btn"
          :class="{ 'is-active': batchMode }"
          @click="toggleBatchMode"
        >
          {{ batchMode ? '退出多选' : '多选操作' }}
        </el-button>
        <el-button
          v-if="batchMode"
          type="success"
          :disabled="!batchEnableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchToggleStatus('enabled')"
        >
          批量启用（{{ batchEnableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="warning"
          :disabled="!batchDisableCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchToggleStatus('disabled')"
        >
          批量停用（{{ batchDisableCount }}）
        </el-button>
        <el-button
          v-if="batchMode"
          type="danger"
          :disabled="!batchDeleteCount || batchSubmitting"
          :loading="batchSubmitting"
          @click="batchDeleteRows"
        >
          批量删除（{{ batchDeleteCount }}）
        </el-button>
      </div>
    </PageToolbar>

    <div :class="['dynamic-workbench', { 'split-active': detailVisible }]">
      <section class="dynamic-panel list-panel">
        <header class="panel-head">
          <div>
            <span>配置列表</span>
            <span class="card-meta">共 {{ total }} 条</span>
          </div>
          <el-button v-if="detailVisible" size="small" @click="collapseDetail">收起右栏</el-button>
        </header>

        <el-table
          ref="tableRef"
          :data="rows"
          border
          v-loading="loading"
          :row-key="(row) => row.id"
          @row-click="selectRow"
          @selection-change="onSelectionChange"
        >
          <el-table-column v-if="batchMode" type="selection" width="48" />
          <el-table-column prop="configName" label="配置名称" min-width="200" />
          <el-table-column prop="versionNo" label="版本" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'enabled' ? 'success' : 'info'" size="small">
                {{ statusLabel(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column prop="remark" label="备注" min-width="180" />
          <el-table-column label="操作" width="264" fixed="right" class-name="op-col">
            <template #default="scope">
              <div class="table-op-line">
                <el-button size="small" @click.stop="openView(scope.row)">查看</el-button>
                <el-button size="small" type="primary" @click.stop="openEdit(scope.row)">编辑</el-button>
                <el-button
                  size="small"
                  :type="scope.row.status === 'enabled' ? 'warning' : 'success'"
                  plain
                  @click.stop="toggleRowStatus(scope.row)"
                >
                  {{ scope.row.status === 'enabled' ? '停用' : '启用' }}
                </el-button>
                <el-button size="small" type="danger" plain @click.stop="removeRow(scope.row)">删除</el-button>
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
            @current-change="loadRows"
          />
        </div>
      </section>

      <transition name="detail-slide">
        <section v-show="detailVisible" class="dynamic-panel detail-panel" v-loading="loadingDetail">
          <header class="panel-head">
            <span v-if="selected">配置详情：{{ selected.configName }}</span>
            <span v-else>请先选择配置</span>
            <div class="actions">
              <el-button :disabled="!selected" @click="refreshSelected">刷新</el-button>
              <el-button :disabled="!selected" type="primary" @click="openEdit(selected)">编辑当前</el-button>
            </div>
          </header>

          <el-empty v-if="!selected" description="从左侧点击“查看”打开详情" :image-size="52" />

          <template v-else>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="配置范围">{{ scopeLabel }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ statusLabel(selected.status) }}</el-descriptions-item>
              <el-descriptions-item label="配置名称">{{ selected.configName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="版本号">{{ selected.versionNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ selected.updatedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ selected.remark || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider>字段定义</el-divider>

            <div class="schema-list">
              <article v-for="(row, idx) in selectedSchemaRows" :key="`${row.key || idx}-${idx}`" class="schema-card">
                <div class="schema-card-head">
                  <strong>{{ row.label || '-' }}</strong>
                  <el-tag size="small">{{ row.type || 'text' }}</el-tag>
                  <el-tag size="small" :type="row.required ? 'danger' : 'info'">{{ row.required ? '必填' : '可选' }}</el-tag>
                </div>
                <div class="schema-card-meta">
                  <span>Key：{{ row.key || '-' }}</span>
                  <span>占位：{{ row.placeholder || '-' }}</span>
                  <span>默认值：{{ row.defaultValue != null && row.defaultValue !== '' ? row.defaultValue : '-' }}</span>
                </div>
                <div class="schema-card-options">选项：{{ formatOptions(row.options) }}</div>
              </article>

              <el-empty v-if="!selectedSchemaRows.length" description="该配置未定义字段" :image-size="46" />
            </div>
          </template>
        </section>
      </transition>
    </div>

    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑配置' : '新建配置'" width="980px" destroy-on-close>
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="配置范围">
              <el-input :model-value="scopeLabel" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="启用" value="enabled" />
                <el-option label="停用" value="disabled" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本号">
              <el-input-number v-model="form.versionNo" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="配置名称">
              <el-input v-model="form.configName" placeholder="例如：种子批次扩展参数" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-tabs v-model="editorMode">
        <el-tab-pane label="可视化编辑" name="visual">
          <div class="editor-toolbar">
            <el-button @click="addSchemaRow">添加字段</el-button>
            <el-button @click="addNumberPreset">数值字段</el-button>
            <el-button @click="addSelectPreset">下拉字段</el-button>
            <el-button @click="syncJsonByRows">同步到JSON</el-button>
          </div>

          <div class="editor-schema-list">
            <div v-for="(row, idx) in schemaRows" :key="row.uid" class="editor-schema-item">
              <div class="editor-schema-index">{{ idx + 1 }}</div>
              <div class="editor-schema-main">
                <div class="editor-schema-grid">
                  <el-input v-model="row.label" placeholder="字段名称" @input="onSchemaLabelInput(row)" />
                  <el-input :model-value="row.key" readonly placeholder="字段Key自动生成" />
                  <el-select v-model="row.type" placeholder="类型" @change="onSchemaTypeChange(row)">
                    <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-input v-model="row.placeholder" placeholder="占位提示" />
                  <el-input v-model="row.defaultValue" placeholder="默认值" />
                </div>
                <div v-if="row.type === 'select'" class="schema-option-editor">
                  <div class="schema-option-head">
                    <span>选项</span>
                    <el-button size="small" @click="addSchemaOption(row)">新增选项</el-button>
                  </div>
                  <div v-for="(opt, optIdx) in row.options" :key="opt.uid" class="schema-option-row">
                    <el-input v-model="opt.label" placeholder="选项名称" @input="onSchemaOptionLabelInput(row, opt, optIdx)" />
                    <el-input :model-value="opt.value" readonly placeholder="选项值自动生成" />
                    <el-button size="small" type="danger" @click="removeSchemaOption(row, optIdx)">删除</el-button>
                  </div>
                  <el-empty v-if="!row.options.length" description="暂无选项，请新增" :image-size="36" />
                </div>
              </div>
              <div class="editor-schema-side">
                <el-switch v-model="row.required" />
                <div class="editor-schema-actions">
                  <el-button size="small" @click="moveSchemaRow(idx, -1)">上移</el-button>
                  <el-button size="small" @click="moveSchemaRow(idx, 1)">下移</el-button>
                  <el-button size="small" type="danger" @click="removeSchemaRow(idx)">删除</el-button>
                </div>
              </div>
            </div>

            <el-empty v-if="!schemaRows.length" description="暂无字段，请先添加" :image-size="48" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="JSON编辑（专业）" name="json">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="专业模式可直接编辑JSON；可视化模式会自动生成字段Key与选项Value。"
          />
          <el-input v-model="form.schemaJson" type="textarea" :rows="14" class="schema-json" />
          <div class="json-toolbar">
            <el-button @click="syncRowsByJson">从JSON同步可视化</el-button>
            <el-button @click="formatJson">格式化JSON</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'
import {
  ensureUniqueMachineKey,
  generateFieldKey,
  generateFieldKeyAsync,
  generateOptionValue,
  generateOptionValueAsync,
  normalizeManualMachineKey,
  normalizeText
} from '../utils/schemaKeyGenerator'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadingDetail = ref(false)
const saving = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchSubmitting = ref(false)
const selected = ref(null)
const detailVisible = ref(false)

const dialogVisible = ref(false)
const editMode = ref(false)
const editorMode = ref('visual')
const schemaRows = ref([])

const filters = reactive({
  keyword: '',
  status: 'enabled'
})

const form = reactive({
  id: null,
  configName: '',
  schemaJson: '[]',
  status: 'enabled',
  versionNo: 1,
  remark: ''
})

const typeOptions = [
  { label: '文本', value: 'text' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '时间', value: 'time' },
  { label: '下拉选择', value: 'select' },
  { label: '开关', value: 'switch' },
  { label: '多行文本', value: 'textarea' },
  { label: '位置', value: 'location' }
]

let schemaUid = 1
let schemaOptionUid = 1

const configType = computed(() => normalizeConfigType(route.params.configType))
const scopeLabel = computed(() => configType.value === 'test' ? '种子检测参数' : '种子批次参数')
const pageTitle = computed(() => configType.value === 'test' ? '种子检测参数中心' : '种子批次参数中心')
const pageSubtitle = computed(() => '管理员仅需维护业务字段，不再接触模块和场景技术参数。')
const selectedSchemaRows = computed(() => parseSchema((selected.value && selected.value.schemaJson) || '[]'))
const batchEnableCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => normalizeStatus(row && row.status) !== 'enabled').length
})
const batchDisableCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => normalizeStatus(row && row.status) === 'enabled').length
})
const batchDeleteCount = computed(() => {
  return (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.id) > 0).length
})

function normalizeConfigType(value) {
  const text = String(value || '').trim().toLowerCase()
  if (text === 'test') return 'test'
  if (text === 'batch') return 'batch'
  return ''
}

function statusLabel(value) {
  return String(value || '').toLowerCase() === 'enabled' ? '启用' : '停用'
}

function statusFilterLabel(value) {
  return String(value || '').trim().toLowerCase() === 'all' ? '全部' : statusLabel(value)
}

function normalizeStatus(value) {
  return String(value || '').trim().toLowerCase() === 'disabled' ? 'disabled' : 'enabled'
}

function clearSelection() {
  selectedRows.value = []
  if (tableRef.value && typeof tableRef.value.clearSelection === 'function') {
    tableRef.value.clearSelection()
  }
}

function onSelectionChange(rowsInput) {
  selectedRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearSelection()
  }
}

async function updateStatusById(id, nextStatus) {
  const detail = await request.get(`/admin/seed-dynamic-configs/${id}`)
  const payload = {
    configType: configType.value,
    configName: String((detail && detail.configName) || '').trim(),
    schemaJson: String((detail && detail.schemaJson) || '[]'),
    status: normalizeStatus(nextStatus),
    versionNo: Number((detail && detail.versionNo) || 1),
    remark: String((detail && detail.remark) || '').trim() || null
  }
  await request.put(`/admin/seed-dynamic-configs/${id}`, payload)
}

async function toggleRowStatus(row) {
  const id = Number(row && row.id)
  if (!(id > 0) || !configType.value) return
  const nextStatus = normalizeStatus(row && row.status) === 'enabled' ? 'disabled' : 'enabled'
  const actionText = nextStatus === 'enabled' ? '启用' : '停用'
  try {
    await updateStatusById(id, nextStatus)
    ElMessage.success(`配置已${actionText}`)
    await loadRows(page.value)
    if (selected.value && String(selected.value.id) === String(id)) {
      await refreshSelected()
    }
  } catch (error) {
    ElMessage.error(error.message || `配置${actionText}失败`)
  }
}

async function removeRow(row) {
  const id = Number(row && row.id)
  if (!(id > 0) || !configType.value) return
  const name = String((row && row.configName) || '').trim() || `配置#${id}`
  try {
    await ElMessageBox.confirm(`确认删除配置“${name}”吗？`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除确认失败')
    }
    return
  }
  batchSubmitting.value = true
  try {
    await request.delete(`/admin/seed-dynamic-configs/${id}`)
    ElMessage.success('配置已删除')
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadRows(Math.min(Number(page.value || 1), maxPage))
    if (selected.value && String(selected.value.id) === String(id)) {
      selected.value = null
      detailVisible.value = false
    }
  } catch (error) {
    ElMessage.error(error.message || '配置删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

async function batchToggleStatus(nextStatusInput) {
  if (!configType.value) return
  const nextStatus = normalizeStatus(nextStatusInput)
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => normalizeStatus(row && row.status) !== nextStatus)
  if (!targets.length) {
    ElMessage.warning(nextStatus === 'enabled' ? '当前所选中没有可启用配置' : '当前所选中没有可停用配置')
    return
  }
  const actionText = nextStatus === 'enabled' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认批量${actionText}已选 ${targets.length} 条配置吗？`, `批量${actionText}确认`, { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `批量${actionText}确认失败`)
    }
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => updateStatusById(Number(row && row.id), nextStatus))
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await loadRows(page.value)
    if (selected.value && selected.value.id) {
      await refreshSelected()
    }
    if (failedCount > 0) {
      ElMessage.warning(`批量${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量${actionText}成功：${successCount} 条配置`)
  } catch (error) {
    ElMessage.error(error.message || `批量${actionText}失败`)
  } finally {
    batchSubmitting.value = false
  }
}

async function batchDeleteRows() {
  if (!configType.value) return
  const targets = (Array.isArray(selectedRows.value) ? selectedRows.value : []).filter((row) => Number(row && row.id) > 0)
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的配置')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选 ${targets.length} 条配置吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除确认失败')
    }
    return
  }

  batchSubmitting.value = true
  try {
    const results = await Promise.allSettled(
      targets.map((row) => request.delete(`/admin/seed-dynamic-configs/${row.id}`))
    )
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    const remainAfterDelete = Math.max(0, Number(total.value || 0) - successCount)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadRows(Math.min(Number(page.value || 1), maxPage))
    if (selected.value && !rows.value.some((item) => String(item && item.id) === String(selected.value && selected.value.id))) {
      selected.value = null
      detailVisible.value = false
    }
    if (failedCount > 0) {
      ElMessage.warning(`批量删除完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量删除成功：${successCount} 条配置`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    batchSubmitting.value = false
  }
}

function parseSchema(schemaJson) {
  try {
    const parsed = JSON.parse(schemaJson || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

function formatOptions(options) {
  if (Array.isArray(options)) {
    if (!options.length) return '-'
    return options
      .map((item) => {
        if (item && typeof item === 'object') {
          const label = item.label || item.name || item.value || '-'
          const value = item.value || item.code || item.label || '-'
          return label === value ? String(value) : `${label}:${value}`
        }
        return String(item)
      })
      .join(' / ')
  }
  return options ? String(options) : '-'
}

function createSchemaOption(partial = {}) {
  return {
    uid: schemaOptionUid++,
    label: '',
    value: '',
    ...partial
  }
}

function parseSchemaOptions(optionsInput) {
  const rowsInput = Array.isArray(optionsInput) ? optionsInput : []
  return rowsInput
    .map((item, idx) => {
      if (item && typeof item === 'object') {
        const label = normalizeText(item.label || item.name || item.value)
        const value = normalizeText(item.value || item.code || item.label)
        return createSchemaOption({
          label,
          value: value || generateOptionValue(label, idx + 1)
        })
      }
      const label = normalizeText(item)
      if (!label) return null
      return createSchemaOption({
        label,
        value: generateOptionValue(label, idx + 1)
      })
    })
    .filter(Boolean)
}

function createSchemaRow(partial = {}) {
  return {
    uid: schemaUid++,
    key: '',
    label: '',
    type: 'text',
    required: false,
    placeholder: '',
    defaultValue: '',
    options: [],
    ...partial
  }
}

function toVisualRows(schemaJson) {
  return parseSchema(schemaJson).map((item, idx) => {
    const label = normalizeText(item && item.label)
    const key = normalizeText(item && item.key) || generateFieldKey(label, idx + 1)
    const options = parseSchemaOptions(item && item.options)
    return createSchemaRow({
      key,
      label,
      type: normalizeText(item && item.type) || 'text',
      required: Number(item && item.required) === 1 || (item && item.required) === true,
      placeholder: normalizeText(item && item.placeholder),
      defaultValue: item && item.defaultValue != null ? String(item.defaultValue) : '',
      options
    })
  })
}

function collectUsedSchemaKeys(exceptUid) {
  const used = new Set()
  schemaRows.value.forEach((item, idx) => {
    if (!item || item.uid === exceptUid) return
    const key = normalizeText(item.key)
    if (!key) return
    used.add(normalizeManualMachineKey(key, 'field', idx + 1))
  })
  return used
}

function normalizeSchemaRowKey(row, index) {
  if (!row) return ''
  const used = collectUsedSchemaKeys(row.uid)
  const key = generateFieldKey(row.label, Math.max(1, Number(index || 1)), used)
  row.key = key
  return key
}

function onSchemaLabelInput(row) {
  if (!row) return
  const index = schemaRows.value.findIndex((item) => item && item.uid === row.uid)
  normalizeSchemaRowKey(row, index >= 0 ? index + 1 : 1)
}

function normalizeOptionValue(label, fallbackIndex, usedSet) {
  return generateOptionValue(label, Math.max(1, Number(fallbackIndex || 1)), usedSet)
}

function normalizeSchemaOptionValues(row) {
  if (!row || !Array.isArray(row.options)) return
  const used = new Set()
  row.options.forEach((item, idx) => {
    if (!item) return
    item.value = normalizeOptionValue(item.label, idx + 1, used)
  })
}

function onSchemaOptionLabelInput(row, opt, optIdx) {
  if (!row || !opt) return
  normalizeSchemaOptionValues(row)
}

function onSchemaTypeChange(row) {
  if (!row) return
  if (row.type !== 'select') {
    row.options = []
    return
  }
  if (!Array.isArray(row.options)) {
    row.options = []
  }
  if (!row.options.length) {
    addSchemaOption(row)
  }
}

function addSchemaOption(row) {
  if (!row) return
  if (!Array.isArray(row.options)) {
    row.options = []
  }
  const index = row.options.length + 1
  row.options.push(
    createSchemaOption({
      value: normalizeOptionValue('', index)
    })
  )
  normalizeSchemaOptionValues(row)
}

function removeSchemaOption(row, optIdx) {
  if (!row || !Array.isArray(row.options)) return
  row.options.splice(optIdx, 1)
  normalizeSchemaOptionValues(row)
}

function addSchemaRow() {
  const next = createSchemaRow()
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function addNumberPreset() {
  const next = createSchemaRow({
    label: '数量',
    type: 'number',
    required: false,
    placeholder: '请输入数量'
  })
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function addSelectPreset() {
  const next = createSchemaRow({
    label: '类型',
    type: 'select',
    required: false,
    placeholder: '请选择类型',
    options: [createSchemaOption({ label: '选项A', value: 'optionA' }), createSchemaOption({ label: '选项B', value: 'optionB' })]
  })
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function removeSchemaRow(index) {
  schemaRows.value.splice(index, 1)
}

function moveSchemaRow(index, delta) {
  const target = index + delta
  if (target < 0 || target >= schemaRows.value.length) return
  const current = schemaRows.value[index]
  schemaRows.value.splice(index, 1)
  schemaRows.value.splice(target, 0, current)
}

function normalizeSchemaRows(strict) {
  const usedKeys = new Set()
  const rowsOut = []

  for (let i = 0; i < schemaRows.value.length; i += 1) {
    const row = schemaRows.value[i]
    const label = normalizeText(row && row.label)
    const type = normalizeText(row && row.type) || 'text'
    const placeholder = normalizeText(row && row.placeholder)
    const defaultValue = normalizeText(row && row.defaultValue)
    const hasAnyValue = label || placeholder || defaultValue || !!(row && row.required) || (type && type !== 'text')
    if (!hasAnyValue) continue

    const rawKey = normalizeText(row && row.key)
    let key = rawKey ? normalizeManualMachineKey(rawKey, 'field', i + 1) : generateFieldKey(label, i + 1)
    key = ensureUniqueMachineKey(key, usedKeys)
    row.key = key
    if (!label) return { rows: [], error: `第${i + 1}个字段缺少名称` }
    if (!key) return { rows: [], error: `第${i + 1}个字段缺少Key` }
    if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(key)) return { rows: [], error: `字段 key 不合法：${key}` }

    const item = {
      key,
      label,
      type,
      required: row.required ? 1 : 0
    }
    if (placeholder) item.placeholder = placeholder
    if (defaultValue) item.defaultValue = defaultValue

    if (type === 'select') {
      const options = []
      const optionRows = Array.isArray(row && row.options) ? row.options : []
      const usedValues = new Set()
      for (let optIdx = 0; optIdx < optionRows.length; optIdx += 1) {
        const opt = optionRows[optIdx]
        const optionLabel = normalizeText(opt && opt.label)
        if (!optionLabel) continue
        const rawValue = normalizeText(opt && opt.value)
        let value = rawValue ? normalizeManualMachineKey(rawValue, 'option', optIdx + 1) : generateOptionValue(optionLabel, optIdx + 1)
        value = ensureUniqueMachineKey(value, usedValues)
        if (opt) opt.value = value
        options.push({
          label: optionLabel,
          value
        })
      }
      if (strict && !options.length) {
        return { rows: [], error: `字段 ${label} 为下拉类型，至少需要一个选项` }
      }
      if (options.length) item.options = options
    }

    rowsOut.push(item)
  }

  return { rows: rowsOut, error: null }
}

async function normalizeSchemaRowsWithRemote(strict) {
  const usedKeys = new Set()
  const rowsOut = []

  for (let i = 0; i < schemaRows.value.length; i += 1) {
    const row = schemaRows.value[i]
    const label = normalizeText(row && row.label)
    const type = normalizeText(row && row.type) || 'text'
    const placeholder = normalizeText(row && row.placeholder)
    const defaultValue = normalizeText(row && row.defaultValue)
    const hasAnyValue = label || placeholder || defaultValue || !!(row && row.required) || (type && type !== 'text')
    if (!hasAnyValue) continue

    const rawKey = normalizeText(row && row.key)
    let key = rawKey ? normalizeManualMachineKey(rawKey, 'field', i + 1) : await generateFieldKeyAsync(label, i + 1)
    key = ensureUniqueMachineKey(key, usedKeys)
    row.key = key
    if (!label) return { rows: [], error: `第${i + 1}个字段缺少名称` }
    if (!key) return { rows: [], error: `第${i + 1}个字段缺少Key` }
    if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(key)) return { rows: [], error: `字段 key 不合法：${key}` }

    const item = {
      key,
      label,
      type,
      required: row.required ? 1 : 0
    }
    if (placeholder) item.placeholder = placeholder
    if (defaultValue) item.defaultValue = defaultValue

    if (type === 'select') {
      const options = []
      const optionRows = Array.isArray(row && row.options) ? row.options : []
      const usedValues = new Set()
      for (let optIdx = 0; optIdx < optionRows.length; optIdx += 1) {
        const opt = optionRows[optIdx]
        const optionLabel = normalizeText(opt && opt.label)
        if (!optionLabel) continue
        const rawValue = normalizeText(opt && opt.value)
        let value = rawValue ? normalizeManualMachineKey(rawValue, 'option', optIdx + 1) : await generateOptionValueAsync(optionLabel, optIdx + 1)
        value = ensureUniqueMachineKey(value, usedValues)
        if (opt) opt.value = value
        options.push({
          label: optionLabel,
          value
        })
      }
      if (strict && !options.length) {
        return { rows: [], error: `字段 ${label} 为下拉类型，至少需要一个选项` }
      }
      if (options.length) item.options = options
    }

    rowsOut.push(item)
  }

  return { rows: rowsOut, error: null }
}

function syncJsonByRows(strict = false, showMessage = false) {
  const normalized = normalizeSchemaRows(strict)
  if (normalized.error) {
    if (showMessage) ElMessage.warning(normalized.error)
    return false
  }
  form.schemaJson = JSON.stringify(normalized.rows, null, 2)
  return true
}

function parseSchemaJsonText(schemaJsonText) {
  const raw = String(schemaJsonText || '').trim()
  if (!raw) return { rows: [], error: null }
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return { rows: [], error: 'schemaJson 必须是数组格式' }
    }
    return { rows: parsed, error: null }
  } catch (error) {
    return { rows: [], error: 'schemaJson 不是合法 JSON' }
  }
}

function syncRowsByJson(showMessage = true) {
  const parsed = parseSchemaJsonText(form.schemaJson)
  if (parsed.error) {
    if (showMessage) ElMessage.warning(parsed.error)
    return false
  }
  form.schemaJson = JSON.stringify(parsed.rows, null, 2)
  schemaRows.value = toVisualRows(form.schemaJson)
  if (showMessage) ElMessage.success('已从JSON同步到可视化编辑')
  return true
}

function formatJson() {
  const parsed = parseSchemaJsonText(form.schemaJson)
  if (parsed.error) {
    ElMessage.warning(parsed.error)
    return
  }
  form.schemaJson = JSON.stringify(parsed.rows, null, 2)
}

function fillForm(row) {
  form.id = row && row.id ? row.id : null
  form.configName = (row && row.configName) || ''
  form.status = (row && row.status) || 'enabled'
  form.versionNo = Number((row && row.versionNo) || 1)
  form.remark = (row && row.remark) || ''

  const parsedRows = parseSchema((row && row.schemaJson) || '[]')
  form.schemaJson = JSON.stringify(parsedRows, null, 2)
  schemaRows.value = toVisualRows(form.schemaJson)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = 'enabled'
  loadRows(1)
}

function resetForm() {
  fillForm(null)
  form.status = 'enabled'
  form.versionNo = 1
  form.schemaJson = '[]'
  schemaRows.value = []
  editorMode.value = 'visual'
}

function collapseDetail() {
  detailVisible.value = false
}

function openCreate() {
  editMode.value = false
  resetForm()
  dialogVisible.value = true
}

async function openView(row) {
  selected.value = row || null
  detailVisible.value = true
  await refreshSelected()
}

async function openEdit(row) {
  if (!row || !row.id) return
  editMode.value = true
  try {
    const detail = await request.get(`/admin/seed-dynamic-configs/${row.id}`)
    fillForm(detail || row)
    editorMode.value = 'visual'
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '配置详情加载失败')
  }
}

function validateForm() {
  if (!String(form.configName || '').trim()) return '请填写配置名称'
  if (!String(form.schemaJson || '').trim()) return '请填写 schemaJson'
  const parsed = parseSchemaJsonText(form.schemaJson)
  if (parsed.error) return parsed.error
  return ''
}

async function saveConfig() {
  if (!configType.value) return
  if (editorMode.value === 'visual') {
    const normalized = await normalizeSchemaRowsWithRemote(true)
    if (normalized.error) {
      ElMessage.warning(normalized.error)
      return
    }
    form.schemaJson = JSON.stringify(normalized.rows, null, 2)
  }

  const validationMsg = validateForm()
  if (validationMsg) {
    ElMessage.warning(validationMsg)
    return
  }

  saving.value = true
  try {
    const payload = {
      configType: configType.value,
      configName: form.configName.trim(),
      schemaJson: form.schemaJson,
      status: form.status || 'enabled',
      versionNo: Number(form.versionNo || 1),
      remark: String(form.remark || '').trim() || null
    }
    if (editMode.value && form.id) {
      await request.put(`/admin/seed-dynamic-configs/${form.id}`, payload)
      ElMessage.success('配置已更新')
    } else {
      await request.post('/admin/seed-dynamic-configs', payload)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await loadRows(page.value)
    if (selected.value && selected.value.id) {
      await refreshSelected()
    }
  } catch (error) {
    ElMessage.error(error.message || '配置保存失败')
  } finally {
    saving.value = false
  }
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadRows(1)
}

async function refreshSelected() {
  if (!selected.value || !selected.value.id) return
  loadingDetail.value = true
  try {
    selected.value = await request.get(`/admin/seed-dynamic-configs/${selected.value.id}`)
  } catch (error) {
    ElMessage.error(error.message || '详情刷新失败')
  } finally {
    loadingDetail.value = false
  }
}

function selectRow(row) {
  selected.value = row || null
}

async function loadRows(nextPage = page.value) {
  if (!configType.value) return
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const data = await request.get('/admin/seed-dynamic-configs', {
      params: {
        page: page.value,
        pageSize: pageSize.value,
        configType: configType.value,
        keyword: filters.keyword || undefined,
        status: filters.status === 'all' ? undefined : (filters.status || undefined)
      }
    })

    rows.value = (data && data.records) || []
    total.value = Number((data && data.total) || 0)
    clearSelection()

    if (selected.value && selected.value.id) {
      const current = rows.value.find((item) => String(item.id) === String(selected.value.id))
      selected.value = current || null
      if (detailVisible.value && selected.value) {
        await refreshSelected()
      }
      if (detailVisible.value && !selected.value) {
        detailVisible.value = false
      }
    }
  } catch (error) {
    rows.value = []
    total.value = 0
    clearSelection()
    ElMessage.error(error.message || '配置列表加载失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => editorMode.value,
  (next, prev) => {
    if (next === prev) return
    if (next === 'json' && prev === 'visual') {
      syncJsonByRows(false, false)
      return
    }
    if (next === 'visual' && prev === 'json') {
      syncRowsByJson(false)
    }
  }
)

watch(
  () => route.params.configType,
  async (next) => {
    const type = normalizeConfigType(next)
    if (!type) {
      router.replace('/seed-dynamic-configs/batch')
      return
    }
    selected.value = null
    detailVisible.value = false
    await loadRows(1)
  }
)

onMounted(async () => {
  if (!configType.value) {
    await router.replace('/seed-dynamic-configs/batch')
    return
  }
  await loadRows(1)
})
</script>

<style scoped>
.seed-dynamic-config-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.dynamic-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 0;
  gap: 12px;
  transition: grid-template-columns 0.28s ease;
}

.dynamic-workbench.split-active {
  grid-template-columns: minmax(0, 68%) minmax(320px, 32%);
}

.dynamic-panel {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 10px 8px;
  min-height: 440px;
}

.panel-head {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.schema-list {
  display: grid;
  gap: 8px;
}

.schema-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 8px;
}

.schema-card-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.schema-card-meta {
  margin-top: 6px;
  display: grid;
  gap: 4px;
  color: var(--text-sub);
  font-size: 12px;
}

.schema-card-options {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.editor-toolbar {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.editor-schema-list {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 8px;
  display: grid;
  gap: 8px;
}

.editor-schema-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 220px;
  gap: 8px;
  align-items: start;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-panel);
  padding: 8px;
}

.editor-schema-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  font-size: 12px;
}

.editor-schema-main {
  display: grid;
  gap: 8px;
}

.editor-schema-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.editor-schema-side {
  display: grid;
  gap: 8px;
  align-content: space-between;
  justify-items: end;
}

.editor-schema-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.schema-option-editor {
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 8px;
  display: grid;
  gap: 8px;
}

.schema-option-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.schema-option-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
}

.schema-json {
  margin-top: 8px;
}

.json-toolbar {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.table-foot {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.card-meta {
  margin-left: 8px;
  color: var(--text-sub);
  font-size: 12px;
}

.detail-slide-enter-active,
.detail-slide-leave-active {
  transition: all 0.2s ease;
}

.detail-slide-enter-from,
.detail-slide-leave-to {
  opacity: 0;
  transform: translateX(6px);
}

@media (max-width: 1200px) {
  .dynamic-workbench,
  .dynamic-workbench.split-active {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .editor-schema-item {
    grid-template-columns: 1fr;
  }

  .editor-schema-grid {
    grid-template-columns: 1fr;
  }

  .editor-schema-side {
    justify-items: start;
  }

  .editor-schema-actions {
    justify-content: flex-start;
  }

  .schema-option-row {
    grid-template-columns: 1fr;
  }
}
</style>

