<template>
  <div>
    <PageToolbar
      title="导出模板标准化"
      subtitle="维护导出字典与模板字段顺序，确保导出结构一致。"
      collapsible
      :summary="[moduleKey ? `当前模块：${moduleLabel(moduleKey)}` : '当前模块：未选择']"
    >
      <div class="actions">
        <el-select v-model="moduleKey" style="width: 180px" @change="loadAll(true)">
          <el-option :label="moduleLabel('farm')" value="farm" />
          <el-option :label="moduleLabel('seed')" value="seed" />
        </el-select>
        <el-button @click="loadAll(true)">刷新</el-button>
      </div>
    </PageToolbar>

    <el-row :gutter="12">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <span>字段字典（{{ moduleLabel(moduleKey) }}）</span>
                <span v-if="dictBatchMode" class="card-meta">已选 {{ selectedDictRows.length }} 条</span>
                <span class="card-meta">共 {{ dictTotal }} 条</span>
              </div>
              <div class="card-actions">
                <el-button type="primary" size="small" @click="openCreateDict">新增字段</el-button>
                <el-button
                  size="small"
                  type="default"
                  plain
                  class="batch-toggle-btn"
                  :class="{ 'is-active': dictBatchMode }"
                  @click="toggleDictBatchMode"
                >
                  {{ dictBatchMode ? '退出多选' : '多选操作' }}
                </el-button>
                <el-button
                  v-if="dictBatchMode"
                  size="small"
                  type="danger"
                  :disabled="!dictBatchDeleteCount || dictBatchSubmitting"
                  :loading="dictBatchSubmitting"
                  @click="batchDeleteDictRows"
                >
                  批量删除（{{ dictBatchDeleteCount }}）
                </el-button>
              </div>
            </div>
          </template>
          <el-table
            ref="dictTableRef"
            :data="dictRows"
            border
            v-loading="loadingDict"
            :row-key="(row) => row.id"
            @selection-change="onDictSelectionChange"
          >
            <el-table-column v-if="dictBatchMode" type="selection" width="46" />
            <el-table-column prop="fieldCode" label="字段编码" min-width="150" />
            <el-table-column prop="fieldName" label="字段名称" min-width="130" />
            <el-table-column prop="dataType" label="类型" width="90" />
            <el-table-column label="操作" width="152" class-name="op-col">
              <template #default="scope">
                <div class="table-op-line">
                  <el-button size="small" type="primary" @click="openEditDict(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click="removeDictRow(scope.row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-foot">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="dictTotal"
              :page-size="dictPageSize"
              :current-page="dictPage"
              :page-sizes="[10, 20, 50, 100]"
              @size-change="onDictPageSizeChange"
              @current-change="loadDict"
            />
          </div>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <span>导出模板（{{ moduleLabel(moduleKey) }}）</span>
                <span v-if="templateBatchMode" class="card-meta">已选 {{ selectedTemplateRows.length }} 条</span>
                <span class="card-meta">共 {{ templateTotal }} 条</span>
              </div>
              <div class="card-actions">
                <el-button type="primary" size="small" @click="openCreateTemplate">新增模板</el-button>
                <el-button
                  size="small"
                  type="default"
                  plain
                  class="batch-toggle-btn"
                  :class="{ 'is-active': templateBatchMode }"
                  @click="toggleTemplateBatchMode"
                >
                  {{ templateBatchMode ? '退出多选' : '多选操作' }}
                </el-button>
                <el-button
                  v-if="templateBatchMode"
                  size="small"
                  type="success"
                  :disabled="!templateBatchEnableCount || templateBatchSubmitting"
                  :loading="templateBatchSubmitting"
                  @click="batchToggleTemplateStatus('enabled')"
                >
                  批量启用（{{ templateBatchEnableCount }}）
                </el-button>
                <el-button
                  v-if="templateBatchMode"
                  size="small"
                  type="warning"
                  :disabled="!templateBatchDisableCount || templateBatchSubmitting"
                  :loading="templateBatchSubmitting"
                  @click="batchToggleTemplateStatus('disabled')"
                >
                  批量停用（{{ templateBatchDisableCount }}）
                </el-button>
                <el-button
                  v-if="templateBatchMode"
                  size="small"
                  type="danger"
                  :disabled="!templateBatchDeleteCount || templateBatchSubmitting"
                  :loading="templateBatchSubmitting"
                  @click="batchDeleteTemplateRows"
                >
                  批量删除（{{ templateBatchDeleteCount }}）
                </el-button>
              </div>
            </div>
          </template>
          <el-table
            ref="templateTableRef"
            :data="templateRows"
            border
            v-loading="loadingTemplates"
            :row-key="(row) => row.id"
            @selection-change="onTemplateSelectionChange"
          >
            <el-table-column v-if="templateBatchMode" type="selection" width="46" />
            <el-table-column prop="templateCode" label="模板编码" width="170" />
            <el-table-column prop="templateName" label="模板名称" min-width="150" />
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'enabled' ? 'success' : 'info'" size="small">
                  {{ scope.row.status === 'enabled' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="170" />
            <el-table-column label="操作" width="210" class-name="op-col">
              <template #default="scope">
                <div class="table-op-line">
                  <el-button size="small" type="primary" @click="openEditTemplate(scope.row)">编辑</el-button>
                <el-button
                  size="small"
                  type="warning"
                  plain
                  @click="toggleTemplateStatus(scope.row)"
                >
                  {{ scope.row.status === 'enabled' ? '停用' : '启用' }}
                </el-button>
                <el-button size="small" type="danger" plain @click="removeTemplateRow(scope.row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-foot">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="templateTotal"
              :page-size="templatePageSize"
              :current-page="templatePage"
              :page-sizes="[10, 20, 50, 100]"
              @size-change="onTemplatePageSizeChange"
              @current-change="loadTemplates"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dictDialogVisible" :title="dictEditMode ? '编辑字典字段' : '新增字典字段'" width="620px">
      <el-form label-width="96px">
        <el-form-item label="模块">
          <el-input :model-value="moduleLabel(dictForm.moduleKey)" readonly />
        </el-form-item>
        <el-form-item label="字段编码">
          <el-input v-model="dictForm.fieldCode" :disabled="dictEditMode" placeholder="例如：fieldName" />
        </el-form-item>
        <el-form-item label="字段名称">
          <el-input v-model="dictForm.fieldName" placeholder="例如：田块名称" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="dictForm.dataType" style="width: 100%">
            <el-option label="string" value="string" />
            <el-option label="number" value="number" />
            <el-option label="date" value="date" />
            <el-option label="datetime" value="datetime" />
            <el-option label="json" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="示例值">
          <el-input v-model="dictForm.exampleValue" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="dictForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDict" @click="saveDict">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialogVisible" :title="templateEditMode ? '编辑导出模板' : '新增导出模板'" width="780px">
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="模块">
              <el-input :model-value="moduleLabel(templateForm.moduleKey)" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="templateForm.status" style="width: 100%">
                <el-option label="启用" value="enabled" />
                <el-option label="停用" value="disabled" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="模板编码">
              <el-input v-model="templateForm.templateCode" :disabled="templateEditMode" placeholder="例如：farm_records_standard" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称">
              <el-input v-model="templateForm.templateName" placeholder="例如：农事记录标准模板" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="版本号">
              <el-input-number v-model="templateForm.versionNo" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="templateForm.remark" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="字段顺序">
          <el-select v-model="templateForm.fieldCodes" multiple filterable clearable style="width: 100%" @change="onTemplateFieldsChange">
            <el-option
              v-for="item in dictRows"
              :key="item.fieldCode"
              :label="`${item.fieldName} (${item.fieldCode})`"
              :value="item.fieldCode"
            />
          </el-select>
          <div class="field-hint">导出将按此顺序输出列头。</div>
          <div class="field-sort-toolbar">
            <el-button size="small" :disabled="!orderedFieldCodes.length" @click="openFieldSortCenter">字段排序中心</el-button>
            <span class="field-sort-meta">已选字段 {{ orderedFieldCodes.length }} 个</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldSortCenterVisible" title="字段排序中心" width="960px" destroy-on-close align-center>
      <SortCenterShell
        ref="fieldSortShellRef"
        :mode="fieldSortMode"
        :keyword="fieldSortKeyword"
        keyword-placeholder="按钮模式下可按字段名/编码搜索"
        :total="fieldSortMode === 'drag' ? fieldSortDraftRows.length : fieldSortFilteredRows.length"
        :page="fieldSortPage"
        :page-total="fieldSortPageTotal"
        save-text="应用顺序"
        @update:mode="(val) => (fieldSortMode = val)"
        @update:keyword="(val) => (fieldSortKeyword = val)"
        @reset="resetFieldSortCenter"
        @save="applyFieldSortResult"
      >
        <SortDragBoard
          v-show="fieldSortMode === 'drag'"
          :model-value="fieldSortDragPageRows"
          item-key="code"
          sort-id-key="code"
          :dragging="fieldSortDragging"
          :suspend-update="fieldSortSuspendPageUpdate"
          :turn-direction="fieldSortTurnDirection"
          :page="fieldSortPage"
          :page-total="fieldSortPageTotal"
          :resolve-index="resolveFieldSortIndex"
          idle-tip="拖动把手排序；分页后可跨页调整字段顺序"
          @turn-page="turnFieldSortPage"
          @drag-start="onFieldSortDragStart"
          @drag-end="onFieldSortDragEnd"
          @drag-move="onFieldSortDragMove"
        >
          <template #item="{ row }">
            <span class="drag-handle">⋮⋮</span>
            <span class="drag-order">{{ resolveFieldSortIndex(row) }}</span>
            <div class="drag-main">
              <div class="drag-title-row">
                <span class="drag-name">{{ fieldLabelByCode(row.code) }}</span>
                <span class="drag-status">{{ fieldDataTypeByCode(row.code) }}</span>
              </div>
              <div class="drag-sub">
                <span>字段编码 {{ row.code }}</span>
                <span>·</span>
                <span>{{ fieldDescriptionByCode(row.code) }}</span>
              </div>
              <div class="drag-extra">拖拽可跨页排序，最终导出列顺序按保存结果生效。</div>
            </div>
            <span class="drag-meta">CODE</span>
          </template>
        </SortDragBoard>

        <div v-show="fieldSortMode !== 'drag'">
          <el-table :data="fieldSortCenterRows" border>
            <el-table-column label="序号" width="72">
              <template #default="scope">{{ resolveFieldSortIndex(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="字段名称" min-width="220">
              <template #default="scope">{{ fieldLabelByCode(scope.row.code) }}</template>
            </el-table-column>
            <el-table-column prop="code" label="字段编码" width="220" />
            <el-table-column label="移动到序号" width="210">
              <template #default="scope">
                <div class="jump-box">
                  <el-input-number
                    v-model="scope.row.jumpTo"
                    :min="1"
                    :max="Math.max(1, fieldSortDraftRows.length)"
                    size="small"
                  />
                  <el-button size="small" @click="applyFieldSortJump(scope.row)">跳转</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" class-name="op-col">
              <template #default="scope">
                <div class="sort-op">
                  <el-button-group>
                    <el-button size="small" @click="moveFieldSortRow(scope.row, -1)">上移</el-button>
                    <el-button size="small" @click="moveFieldSortRow(scope.row, 1)">下移</el-button>
                  </el-button-group>
                  <el-dropdown trigger="click" @command="(command) => onFieldSortOpCommand(command, scope.row)">
                    <el-button size="small">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="top">置顶</el-dropdown-item>
                        <el-dropdown-item command="bottom">置底</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <template #foot>
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="fieldSortPageTotalRecords"
            :page-size="fieldSortPageSize"
            :current-page="fieldSortPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onFieldSortPageSizeChange"
            @current-change="onFieldSortPageChange"
          />
        </template>
      </SortCenterShell>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import SortCenterShell from '../components/ui/SortCenterShell.vue'
import SortDragBoard from '../components/ui/SortDragBoard.vue'
import request from '../utils/request'

const moduleKey = ref('farm')

const loadingDict = ref(false)
const savingDict = ref(false)
const dictRows = ref([])
const dictPage = ref(1)
const dictPageSize = ref(10)
const dictTotal = ref(0)
const dictTableRef = ref(null)
const dictBatchMode = ref(false)
const selectedDictRows = ref([])
const dictBatchSubmitting = ref(false)

const loadingTemplates = ref(false)
const savingTemplate = ref(false)
const templateRows = ref([])
const templatePage = ref(1)
const templatePageSize = ref(10)
const templateTotal = ref(0)
const templateTableRef = ref(null)
const templateBatchMode = ref(false)
const selectedTemplateRows = ref([])
const templateBatchSubmitting = ref(false)

const dictDialogVisible = ref(false)
const dictEditMode = ref(false)
const dictForm = reactive({
  id: null,
  moduleKey: 'farm',
  fieldCode: '',
  fieldName: '',
  dataType: 'string',
  description: '',
  exampleValue: ''
})

const templateDialogVisible = ref(false)
const templateEditMode = ref(false)
const templateForm = reactive({
  id: null,
  moduleKey: 'farm',
  templateCode: '',
  templateName: '',
  versionNo: 1,
  status: 'enabled',
  remark: '',
  fieldCodes: []
})
const orderedFieldCodes = ref([])
const fieldSortCenterVisible = ref(false)
const fieldSortMode = ref('drag')
const fieldSortKeyword = ref('')
const fieldSortDraftRows = ref([])
const fieldSortInitialRows = ref([])
const fieldSortDragPageRows = ref([])
const fieldSortShellRef = ref(null)
const fieldSortPage = ref(1)
const fieldSortPageSize = ref(10)
const fieldSortDragging = ref(false)
const fieldSortDraggingCode = ref('')
const fieldSortSuspendPageUpdate = ref(false)
const fieldSortDragStartSnapshot = ref([])
const fieldSortDragStartPage = ref(1)
const fieldSortDragSourceIndex = ref(-1)
const fieldSortDragSourceRow = ref(null)
const fieldSortVisitedCrossPageDuringDrag = ref(false)
const fieldSortTurnDirection = ref('')
let fieldSortTurnTimer = null
let fieldSortTurnRaf = 0
let fieldSortTurnPendingPoint = null
let fieldSortTurnConsumedDirection = ''
let fieldSortAutoScrollLastAt = 0
let fieldSortAutoScrollCarry = 0
let fieldSortAutoScrollDirection = 0
let fieldSortAutoScrollVelocity = 0
let fieldSortCrossPreviewLastPage = 0
let fieldSortCrossPreviewLastInsertIndex = -1
let fieldSortCrossPreviewLastPointKey = ''
let fieldSortCrossPreviewLastPointerY = NaN
let fieldSortCrossPreviewMoveDown = true

function getFieldSortBodyEl() {
  if (!fieldSortShellRef.value || typeof fieldSortShellRef.value.getBodyEl !== 'function') return null
  return fieldSortShellRef.value.getBodyEl()
}

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((k) => {
    const v = input[k]
    if (v === null || v === undefined || v === '') return
    out[k] = v
  })
  return out
}

function parseFieldsJson(text) {
  if (!text) return []
  try {
    const arr = JSON.parse(text)
    if (!Array.isArray(arr)) return []
    return arr.map((x) => String(x || '').trim()).filter(Boolean)
  } catch (e) {
    return []
  }
}

function toUniqueStrings(rows) {
  return Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
}

function moduleLabel(value) {
  const key = String(value || '').trim()
  const map = {
    farm: '农事（farm）',
    seed: '种子（seed）',
    export: '导出（export）',
    field: '田块（field）'
  }
  return map[key] || key || '未归类'
}

const templateBatchEnableCount = computed(() => {
  return (Array.isArray(selectedTemplateRows.value) ? selectedTemplateRows.value : []).filter((row) => {
    return String((row && row.status) || '').trim().toLowerCase() !== 'enabled'
  }).length
})

const templateBatchDisableCount = computed(() => {
  return (Array.isArray(selectedTemplateRows.value) ? selectedTemplateRows.value : []).filter((row) => {
    return String((row && row.status) || '').trim().toLowerCase() === 'enabled'
  }).length
})
const templateBatchDeleteCount = computed(() => {
  return (Array.isArray(selectedTemplateRows.value) ? selectedTemplateRows.value : []).filter((row) => Number(row && row.id) > 0).length
})

const dictBatchDeleteCount = computed(() => {
  return (Array.isArray(selectedDictRows.value) ? selectedDictRows.value : []).length
})

function clearDictSelection() {
  selectedDictRows.value = []
  if (dictTableRef.value && typeof dictTableRef.value.clearSelection === 'function') {
    dictTableRef.value.clearSelection()
  }
}

function onDictSelectionChange(rows) {
  selectedDictRows.value = Array.isArray(rows) ? rows : []
}

function toggleDictBatchMode() {
  dictBatchMode.value = !dictBatchMode.value
  if (!dictBatchMode.value) {
    clearDictSelection()
  }
}

async function removeDictRow(row) {
  const id = Number(row && row.id)
  const name = String((row && row.fieldName) || (row && row.fieldCode) || '').trim()
  if (!(id > 0)) {
    ElMessage.warning('字段ID无效，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除字段“${name || id}”吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/admin/export-dicts/${id}`)
    ElMessage.success('字段已删除')
    const remainAfterDelete = Math.max(0, Number(dictTotal.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(dictPageSize.value || 10)))
    await loadDict(Math.min(Number(dictPage.value || 1), maxPage))
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '字段删除失败')
    }
  }
}

async function batchDeleteDictRows() {
  const targets = Array.isArray(selectedDictRows.value) ? selectedDictRows.value.filter((row) => Number(row && row.id) > 0) : []
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的字段')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${targets.length} 个字段吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除已取消')
    }
    return
  }
  dictBatchSubmitting.value = true
  try {
    let successCount = 0
    const failed = []
    for (const row of targets) {
      const id = Number(row && row.id)
      try {
        await request.delete(`/admin/export-dicts/${id}`)
        successCount += 1
      } catch (e) {
        failed.push({ id, message: e.message || '删除失败' })
      }
    }
    if (failed.length) {
      const preview = failed
        .slice(0, 3)
        .map((item) => `#${item.id}: ${item.message}`)
        .join('；')
      ElMessage.warning(`批量删除完成：成功 ${successCount} 条，失败 ${failed.length} 条。${preview}`)
    } else {
      ElMessage.success(`批量删除成功，共 ${successCount} 条`)
    }
    const remainAfterDelete = Math.max(0, Number(dictTotal.value || 0) - successCount)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(dictPageSize.value || 10)))
    await loadDict(Math.min(Number(dictPage.value || 1), maxPage))
  } finally {
    dictBatchSubmitting.value = false
  }
}

function clearTemplateSelection() {
  selectedTemplateRows.value = []
  if (templateTableRef.value && typeof templateTableRef.value.clearSelection === 'function') {
    templateTableRef.value.clearSelection()
  }
}

function onTemplateSelectionChange(rows) {
  selectedTemplateRows.value = Array.isArray(rows) ? rows : []
}

function toggleTemplateBatchMode() {
  templateBatchMode.value = !templateBatchMode.value
  if (!templateBatchMode.value) {
    clearTemplateSelection()
  }
}

function normalizeTemplateStatus(value) {
  return String(value || '').trim().toLowerCase() === 'disabled' ? 'disabled' : 'enabled'
}

function buildTemplatePayloadWithStatus(row, nextStatus) {
  const status = normalizeTemplateStatus(nextStatus)
  const templateCode = String((row && row.templateCode) || '').trim()
  const templateName = String((row && row.templateName) || '').trim()
  const payloadModuleKey = String((row && row.moduleKey) || moduleKey.value || '').trim()
  const fieldCodes = parseFieldsJson(row && row.fieldsJson)
  if (!templateCode || !templateName || !payloadModuleKey || !fieldCodes.length) {
    throw new Error(`模板“${templateName || templateCode || row?.id || '-'}”缺少必要配置，无法批量更新状态`)
  }
  return {
    moduleKey: payloadModuleKey,
    templateCode,
    templateName,
    versionNo: Number((row && row.versionNo) || 1) || 1,
    status,
    remark: row && row.remark ? row.remark : null,
    fieldsJson: JSON.stringify(fieldCodes)
  }
}

async function updateTemplateStatus(row, nextStatus) {
  const id = Number(row && row.id)
  if (!(id > 0)) {
    throw new Error('模板ID无效')
  }
  const payload = buildTemplatePayloadWithStatus(row, nextStatus)
  await request.put(`/admin/export-templates/${id}`, payload)
}

async function toggleTemplateStatus(row) {
  const currentStatus = normalizeTemplateStatus(row && row.status)
  const nextStatus = currentStatus === 'enabled' ? 'disabled' : 'enabled'
  const actionText = nextStatus === 'enabled' ? '启用' : '停用'
  try {
    await updateTemplateStatus(row, nextStatus)
    ElMessage.success(`模板已${actionText}`)
    await loadTemplates(templatePage.value)
  } catch (e) {
    ElMessage.error(e.message || `模板${actionText}失败`)
  }
}

async function removeTemplateRow(row) {
  const id = Number(row && row.id)
  if (!(id > 0)) {
    ElMessage.warning('模板ID无效，无法删除')
    return
  }
  const name = String((row && row.templateName) || (row && row.templateCode) || '').trim()
  try {
    await ElMessageBox.confirm(`确认删除模板“${name || id}”吗？`, '删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除确认失败')
    }
    return
  }
  templateBatchSubmitting.value = true
  try {
    await request.delete(`/admin/export-templates/${id}`)
    ElMessage.success('模板已删除')
    const remainAfterDelete = Math.max(0, Number(templateTotal.value || 0) - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(templatePageSize.value || 10)))
    await loadTemplates(Math.min(Number(templatePage.value || 1), maxPage))
  } catch (e) {
    ElMessage.error(e.message || '模板删除失败')
  } finally {
    templateBatchSubmitting.value = false
  }
}

async function batchToggleTemplateStatus(nextStatusInput) {
  const nextStatus = normalizeTemplateStatus(nextStatusInput)
  const targets = (Array.isArray(selectedTemplateRows.value) ? selectedTemplateRows.value : []).filter((row) => {
    return normalizeTemplateStatus(row && row.status) !== nextStatus
  })
  if (!targets.length) {
    ElMessage.warning(nextStatus === 'enabled' ? '当前所选中没有可启用模板' : '当前所选中没有可停用模板')
    return
  }
  const actionText = nextStatus === 'enabled' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认批量${actionText}已选 ${targets.length} 个模板吗？`, `批量${actionText}确认`, { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || `批量${actionText}确认失败`)
    }
    return
  }

  templateBatchSubmitting.value = true
  try {
    const results = await Promise.allSettled(targets.map((row) => updateTemplateStatus(row, nextStatus)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await loadTemplates(templatePage.value)
    if (failedCount > 0) {
      ElMessage.warning(`批量${actionText}完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量${actionText}成功：${successCount} 个模板`)
  } catch (e) {
    ElMessage.error(e.message || `批量${actionText}失败`)
  } finally {
    templateBatchSubmitting.value = false
  }
}

async function batchDeleteTemplateRows() {
  const targets = (Array.isArray(selectedTemplateRows.value) ? selectedTemplateRows.value : []).filter((row) => Number(row && row.id) > 0)
  if (!targets.length) {
    ElMessage.warning('请先选择要删除的模板')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选 ${targets.length} 个模板吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除确认失败')
    }
    return
  }

  templateBatchSubmitting.value = true
  try {
    const results = await Promise.allSettled(targets.map((row) => request.delete(`/admin/export-templates/${row.id}`)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    const remainAfterDelete = Math.max(0, Number(templateTotal.value || 0) - successCount)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(templatePageSize.value || 10)))
    await loadTemplates(Math.min(Number(templatePage.value || 1), maxPage))
    if (failedCount > 0) {
      ElMessage.warning(`批量删除完成：成功 ${successCount}，失败 ${failedCount}`)
      return
    }
    ElMessage.success(`批量删除成功：${successCount} 个模板`)
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    templateBatchSubmitting.value = false
  }
}

const dictRowMap = computed(() => {
  const map = new Map()
  ;(dictRows.value || []).forEach((row) => {
    const code = String((row && row.fieldCode) || '').trim()
    if (code && !map.has(code)) {
      map.set(code, row)
    }
  })
  return map
})

function getDictRowByCode(code) {
  const fieldCode = String(code || '').trim()
  if (!fieldCode) return null
  return dictRowMap.value.get(fieldCode) || null
}

function fieldLabelByCode(code) {
  const fieldCode = String(code || '').trim()
  if (!fieldCode) return '-'
  const found = getDictRowByCode(fieldCode)
  if (!found) return fieldCode
  return `${found.fieldName || found.fieldCode} (${found.fieldCode})`
}

function fieldDataTypeByCode(code) {
  const fieldCode = String(code || '').trim()
  if (!fieldCode) return '未定义'
  const found = getDictRowByCode(fieldCode)
  return String((found && found.dataType) || 'string').trim() || 'string'
}

function fieldDescriptionByCode(code) {
  const fieldCode = String(code || '').trim()
  if (!fieldCode) return '暂无描述'
  const found = getDictRowByCode(fieldCode)
  const text = String((found && found.description) || '').trim()
  return text || '暂无描述'
}

const fieldSortFilteredRows = computed(() => {
  const keyword = String(fieldSortKeyword.value || '').trim().toLowerCase()
  if (!keyword) return fieldSortDraftRows.value
  return fieldSortDraftRows.value.filter((row) => {
    const label = String(fieldLabelByCode(row.code) || '').toLowerCase()
    const code = String(row.code || '').toLowerCase()
    return label.includes(keyword) || code.includes(keyword)
  })
})

const fieldSortButtonRows = computed(() => {
  if (fieldSortMode.value !== 'button') return fieldSortDraftRows.value
  return fieldSortFilteredRows.value
})

const fieldSortPageTotalRecords = computed(() => fieldSortButtonRows.value.length)

const fieldSortPageTotal = computed(() => {
  const total = Number(fieldSortPageTotalRecords.value || 0)
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  return Math.max(1, Math.ceil(total / size))
})

const fieldSortCenterRows = computed(() => {
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  const start = (Math.max(1, Number(fieldSortPage.value || 1)) - 1) * size
  return fieldSortButtonRows.value.slice(start, start + size)
})

const fieldSortIndexMap = computed(() => {
  const map = new Map()
  fieldSortDraftRows.value.forEach((item, idx) => {
    map.set(String((item && item.code) || ''), idx + 1)
  })
  return map
})

function syncOrderedFieldCodes() {
  orderedFieldCodes.value = toUniqueStrings(templateForm.fieldCodes)
}

function onTemplateFieldsChange(codes) {
  templateForm.fieldCodes = toUniqueStrings(codes)
  syncOrderedFieldCodes()
}

function resetFieldSortCenter() {
  clearFieldSortDragState()
  fieldSortDraftRows.value = fieldSortInitialRows.value.map((row, idx) => ({
    ...row,
    jumpTo: idx + 1
  }))
  fieldSortKeyword.value = ''
  fieldSortPage.value = 1
  refreshFieldSortPageRows()
}

function buildFieldSortDraft() {
  return orderedFieldCodes.value.map((code, idx) => ({
    code,
    jumpTo: idx + 1
  }))
}

function openFieldSortCenter() {
  if (!orderedFieldCodes.value.length) {
    ElMessage.warning('请先选择字段')
    return
  }
  fieldSortInitialRows.value = buildFieldSortDraft()
  fieldSortDraftRows.value = fieldSortInitialRows.value.map((row) => ({ ...row }))
  clearFieldSortDragState()
  fieldSortMode.value = 'drag'
  fieldSortPage.value = 1
  fieldSortPageSize.value = 20
  fieldSortKeyword.value = ''
  refreshFieldSortPageRows()
  fieldSortCenterVisible.value = true
}

function normalizeFieldSortDraftRows() {
  const list = Array.isArray(fieldSortDraftRows.value) ? fieldSortDraftRows.value : []
  const baseline = Array.isArray(fieldSortInitialRows.value) && fieldSortInitialRows.value.length
    ? fieldSortInitialRows.value
    : list
  const fallbackMap = new Map()
  baseline.forEach((item) => {
    const code = String((item && item.code) || '').trim()
    if (code && !fallbackMap.has(code)) {
      fallbackMap.set(code, item)
    }
  })
  list.forEach((item) => {
    const code = String((item && item.code) || '').trim()
    if (code && !fallbackMap.has(code)) {
      fallbackMap.set(code, item)
    }
  })
  const seen = new Set()
  const normalized = []
  list.forEach((item) => {
    const code = String((item && item.code) || '').trim()
    if (!code || seen.has(code)) return
    seen.add(code)
    normalized.push(item)
  })
  fallbackMap.forEach((item, code) => {
    if (seen.has(code)) return
    seen.add(code)
    normalized.push({ ...item })
  })
  if (
    normalized.length === list.length &&
    normalized.every(
      (item, idx) =>
        String((item && item.code) || '').trim() === String((list[idx] && list[idx].code) || '').trim()
    )
  ) {
    return
  }
  fieldSortDraftRows.value = normalized
}

function refreshFieldSortJumpNumbers() {
  normalizeFieldSortDraftRows()
  fieldSortDraftRows.value.forEach((row, idx) => {
    row.jumpTo = idx + 1
  })
  refreshFieldSortPageRows()
}

function cloneFieldSortRows(rows) {
  // Shallow copy is enough for reordering; avoid cloning large row objects repeatedly.
  return Array.isArray(rows) ? rows.slice() : []
}

function captureFieldSortDragSnapshot() {
  const snapshot = cloneFieldSortRows(fieldSortDraftRows.value)
  fieldSortDragStartSnapshot.value = snapshot
  fieldSortDragStartPage.value = Math.max(1, Number(fieldSortPage.value || 1))
  const draggingCode = String(fieldSortDraggingCode.value || '')
  const sourceIndex = draggingCode
    ? snapshot.findIndex((item) => String((item && item.code) || '') === draggingCode)
    : -1
  fieldSortDragSourceIndex.value = sourceIndex
  fieldSortDragSourceRow.value = sourceIndex >= 0 ? snapshot[sourceIndex] : null
}

function restoreFieldSortDragSnapshot() {
  if (!Array.isArray(fieldSortDragStartSnapshot.value) || !fieldSortDragStartSnapshot.value.length) return
  fieldSortDraftRows.value = cloneFieldSortRows(fieldSortDragStartSnapshot.value)
  fieldSortPage.value = Math.max(1, Number(fieldSortDragStartPage.value || 1))
  refreshFieldSortJumpNumbers()
}

function applyFieldSortSamePageDropByEvent(event) {
  const snapshot = cloneFieldSortRows(fieldSortDragStartSnapshot.value)
  if (!snapshot.length) return false
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  const startPage = Math.max(1, Number(fieldSortDragStartPage.value || 1))
  const start = (startPage - 1) * size
  const expectedCount = Math.min(size, Math.max(0, snapshot.length - start))
  if (expectedCount <= 1) {
    fieldSortDraftRows.value = snapshot
    return true
  }
  const oldIndex = Number(event && event.oldDraggableIndex)
  const newIndex = Number(event && event.newDraggableIndex)
  if (!Number.isInteger(oldIndex) || !Number.isInteger(newIndex)) {
    fieldSortDraftRows.value = snapshot
    return true
  }
  if (oldIndex < 0 || oldIndex >= expectedCount || newIndex < 0 || newIndex >= expectedCount) {
    fieldSortDraftRows.value = snapshot
    return true
  }
  if (oldIndex === newIndex) {
    fieldSortDraftRows.value = snapshot
    return true
  }
  const pageRows = snapshot.slice(start, start + expectedCount)
  const moved = pageRows.splice(oldIndex, 1)[0]
  pageRows.splice(newIndex, 0, moved)
  snapshot.splice(start, expectedCount, ...pageRows)
  fieldSortDraftRows.value = snapshot
  return true
}

function ensureFieldSortDraftIntegrityAfterDrag() {
  const snapshot = cloneFieldSortRows(fieldSortDragStartSnapshot.value)
  if (!snapshot.length) return
  const baselineCodes = snapshot.map((item) => String((item && item.code) || '').trim()).filter(Boolean)
  const list = Array.isArray(fieldSortDraftRows.value) ? fieldSortDraftRows.value : []
  const currentCodes = list.map((item) => String((item && item.code) || '').trim()).filter(Boolean)
  if (currentCodes.length !== baselineCodes.length) {
    fieldSortDraftRows.value = snapshot
    return
  }
  const seen = new Set()
  for (const code of currentCodes) {
    if (!code || seen.has(code)) {
      fieldSortDraftRows.value = snapshot
      return
    }
    seen.add(code)
  }
  for (const code of baselineCodes) {
    if (!seen.has(code)) {
      fieldSortDraftRows.value = snapshot
      return
    }
  }
}

function applyFieldSortCrossPageDropByPoint(point) {
  const draggingCode = String(fieldSortDraggingCode.value || '')
  const snapshot = cloneFieldSortRows(fieldSortDragStartSnapshot.value)
  if (!draggingCode || !snapshot.length) return false
  let from = Number(fieldSortDragSourceIndex.value)
  if (!Number.isInteger(from) || from < 0 || from >= snapshot.length) {
    from = snapshot.findIndex((item) => String((item && item.code) || '') === draggingCode)
  }
  if (from < 0) return false
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  const sourcePage = Math.max(1, Number(fieldSortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(fieldSortPage.value || sourcePage)))
  const list = snapshot
  const moved = list.splice(from, 1)[0]
  const targetStart = Math.max(0, Math.min(list.length, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(list.length, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcCrossPageInsertIndexByPoint(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    list.length
  )
  list.splice(insertIndex, 0, moved)
  fieldSortDraftRows.value = list
  return true
}

function buildFieldSortPageRowsFromList(rows, pageInput = fieldSortPage.value) {
  const source = Array.isArray(rows) ? rows : []
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  const pageNo = Math.max(1, Number(pageInput || 1))
  const start = (pageNo - 1) * size
  return source.slice(start, start + size)
}

function getFieldSortCrossPageBaseRows() {
  const snapshot = Array.isArray(fieldSortDragStartSnapshot.value) ? fieldSortDragStartSnapshot.value : []
  if (snapshot.length) {
    return buildFieldSortPageRowsFromList(snapshot)
  }
  return buildFieldSortPageRowsFromList(fieldSortDraftRows.value)
}

function buildFieldSortCrossPagePreviewPageRows(snapshot, from, insertIndex, targetPage, pageSize) {
  const source = Array.isArray(snapshot) ? snapshot : []
  const total = source.length
  const sourceIndex = Number(from)
  if (!total || sourceIndex < 0 || sourceIndex >= total) return []
  const size = Math.max(1, Number(pageSize || fieldSortPageSize.value || 10))
  const pageNo = Math.max(1, Number(targetPage || fieldSortPage.value || 1))
  const moved = fieldSortDragSourceRow.value || source[sourceIndex]
  const finalInsert = Math.max(0, Math.min(total - 1, Number(insertIndex || 0)))
  const pageStart = (pageNo - 1) * size
  const pageEnd = Math.min(total, pageStart + size)
  const rows = []
  for (let j = pageStart; j < pageEnd; j += 1) {
    if (j === finalInsert) {
      rows.push(moved)
      continue
    }
    const compactIndex = j < finalInsert ? j : j - 1
    const originalIndex = compactIndex < sourceIndex ? compactIndex : compactIndex + 1
    if (originalIndex >= 0 && originalIndex < total) {
      rows.push(source[originalIndex])
    }
  }
  return rows
}

function buildFieldSortCrossPagePreviewRows(point) {
  const snapshot = Array.isArray(fieldSortDragStartSnapshot.value) ? fieldSortDragStartSnapshot.value : []
  const from = Number(fieldSortDragSourceIndex.value)
  if (!snapshot.length || from < 0 || from >= snapshot.length) return getFieldSortCrossPageBaseRows()
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  const sourcePage = Math.max(1, Number(fieldSortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(fieldSortPage.value || sourcePage)))
  const reducedLength = Math.max(0, snapshot.length - 1)
  const targetStart = Math.max(0, Math.min(reducedLength, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(reducedLength, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcCrossPageInsertIndexByPoint(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    reducedLength
  )
  if (
    targetPage === fieldSortCrossPreviewLastPage &&
    insertIndex === fieldSortCrossPreviewLastInsertIndex &&
    Array.isArray(fieldSortDragPageRows.value) &&
    fieldSortDragPageRows.value.length
  ) {
    return fieldSortDragPageRows.value
  }
  fieldSortCrossPreviewLastPage = targetPage
  fieldSortCrossPreviewLastInsertIndex = insertIndex
  return buildFieldSortCrossPagePreviewPageRows(snapshot, from, insertIndex, targetPage, size)
}

function refreshFieldSortCrossPageDragRowsByPoint(point) {
  if (!fieldSortDragging.value || fieldSortPage.value === fieldSortDragStartPage.value) return false
  if (point && typeof point.clientY === 'number') {
    if (Number.isFinite(fieldSortCrossPreviewLastPointerY)) {
      fieldSortCrossPreviewMoveDown = point.clientY >= fieldSortCrossPreviewLastPointerY
    }
    fieldSortCrossPreviewLastPointerY = point.clientY
  }
  const pointKey = point && typeof point.clientY === 'number'
    ? `${fieldSortPage.value}:${Math.round((Number(point.clientX || 0)) / 8)}:${Math.round(point.clientY / 3)}`
    : `${fieldSortPage.value}:na`
  if (pointKey === fieldSortCrossPreviewLastPointKey) return true
  fieldSortCrossPreviewLastPointKey = pointKey
  setFieldSortDragPageRows(buildFieldSortCrossPagePreviewRows(point))
  return true
}

function clearFieldSortTurnTimer() {
  if (fieldSortTurnTimer) {
    clearTimeout(fieldSortTurnTimer)
    fieldSortTurnTimer = null
  }
}

function canTurnFieldSortPage(direction) {
  const dir = String(direction || '').trim()
  if (dir === 'prev') return fieldSortPage.value > 1
  if (dir === 'next') return fieldSortPage.value < fieldSortPageTotal.value
  return false
}

function scheduleFieldSortTurn(direction) {
  clearFieldSortTurnTimer()
  if (!canTurnFieldSortPage(direction)) return
  fieldSortTurnTimer = setTimeout(() => {
    if (!fieldSortDragging.value) return
    if (fieldSortTurnDirection.value !== direction) return
    if (!canTurnFieldSortPage(direction)) return
    turnFieldSortPage(direction, fieldSortTurnPendingPoint)
    fieldSortTurnConsumedDirection = direction
    clearFieldSortTurnTimer()
  }, 280)
}

function turnFieldSortPage(direction, point) {
  const dir = String(direction || '').trim()
  if (point && typeof point.clientX === 'number' && typeof point.clientY === 'number') {
    fieldSortTurnPendingPoint = {
      clientX: point.clientX,
      clientY: point.clientY
    }
  }
  let changed = false
  if (dir === 'prev') {
    if (fieldSortPage.value > 1) {
      fieldSortPage.value -= 1
      changed = true
    }
  } else if (dir === 'next') {
    if (fieldSortPage.value < fieldSortPageTotal.value) {
      fieldSortPage.value += 1
      changed = true
    }
  }
  if (changed) {
    refreshFieldSortPageRows()
  }
  if (fieldSortDragging.value) {
    fieldSortSuspendPageUpdate.value = fieldSortPage.value !== fieldSortDragStartPage.value
    if (fieldSortSuspendPageUpdate.value) {
      fieldSortVisitedCrossPageDuringDrag.value = true
      refreshFieldSortCrossPageDragRowsByPoint(fieldSortTurnPendingPoint)
    }
  }
}

function calcCrossPageInsertIndexByPoint(
  point,
  targetStart,
  targetEndExclusive,
  fallbackIndex,
  totalLengthInput = fieldSortDraftRows.value.length
) {
  const totalLength = Math.max(0, Number(totalLengthInput || 0))
  const fallback = Math.max(0, Math.min(totalLength, Number(fallbackIndex || 0)))
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return fallback
  const body = getFieldSortBodyEl()
  if (!body) return fallback
  const listEl = body.querySelector('.sort-drag-card-list')
  if (listEl && typeof listEl.querySelectorAll === 'function') {
    const listRect = typeof listEl.getBoundingClientRect === 'function' ? listEl.getBoundingClientRect() : null
    if (
      listRect &&
      listRect.width > 0 &&
      listRect.height > 0 &&
      (point.clientX < listRect.left || point.clientX > listRect.right)
    ) {
      if (Number.isInteger(fieldSortCrossPreviewLastInsertIndex) && fieldSortCrossPreviewLastInsertIndex >= 0) {
        return Math.max(0, Math.min(totalLength, fieldSortCrossPreviewLastInsertIndex))
      }
      return fallback
    }
    const draggingCode = String(fieldSortDraggingCode.value || '')
    const hovered = document.elementFromPoint(point.clientX, point.clientY)
    const hoverCard = hovered && hovered.closest ? hovered.closest('.drag-item') : null
    if (
      hoverCard &&
      listEl.contains(hoverCard) &&
      hoverCard.classList &&
      !hoverCard.classList.contains('drag-ghost') &&
      !hoverCard.classList.contains('drag-chosen') &&
      !hoverCard.classList.contains('drag-dragging') &&
      !hoverCard.classList.contains('sortable-ghost') &&
      !hoverCard.classList.contains('sortable-chosen') &&
      !hoverCard.classList.contains('sortable-drag')
    ) {
      const hoverCode = String((hoverCard && hoverCard.dataset && hoverCard.dataset.sortId) || '')
      if (hoverCode && (!draggingCode || hoverCode !== draggingCode)) {
        const rows = Array.isArray(fieldSortDragPageRows.value) ? fieldSortDragPageRows.value : []
        let compactIndex = -1
        let compactOffset = 0
        for (let i = 0; i < rows.length; i += 1) {
          const rowCode = String((rows[i] && rows[i].code) || '')
          if (!rowCode || (draggingCode && rowCode === draggingCode)) continue
          if (rowCode === hoverCode) {
            compactIndex = compactOffset
            break
          }
          compactOffset += 1
        }
        if (compactIndex >= 0) {
          const placeAfter = Boolean(fieldSortCrossPreviewMoveDown)
          const count = Math.max(0, targetEndExclusive - targetStart)
          const offset = Math.max(0, Math.min(count, compactIndex + (placeAfter ? 1 : 0)))
          return Math.max(0, Math.min(totalLength, targetStart + offset))
        }
      }
    }
    const cards = listEl.querySelectorAll('.drag-item')
    if (cards && cards.length) {
      const count = Math.max(0, targetEndExclusive - targetStart)
      let offset = 0
      let hasValidCard = false
      for (let i = 0; i < cards.length; i += 1) {
        const card = cards[i]
        if (!card || !card.classList) continue
        if (
          card.classList.contains('drag-ghost') ||
          card.classList.contains('drag-chosen') ||
          card.classList.contains('drag-dragging') ||
          card.classList.contains('sortable-ghost') ||
          card.classList.contains('sortable-chosen') ||
          card.classList.contains('sortable-drag')
        ) {
          continue
        }
        const rect = card.getBoundingClientRect()
        if (rect.height <= 0 || rect.width <= 0) continue
        hasValidCard = true
        const splitY = fieldSortCrossPreviewMoveDown ? rect.top : rect.bottom
        if (point.clientY < splitY) {
          break
        }
        const cardCode = String((card && card.dataset && card.dataset.sortId) || '')
        if (draggingCode && cardCode && cardCode === draggingCode) continue
        offset += 1
      }
      if (hasValidCard) {
        const normalizedOffset = Math.max(0, Math.min(count, offset))
        return Math.max(0, Math.min(totalLength, targetStart + normalizedOffset))
      }
    }
  }
  const rect = body.getBoundingClientRect()
  const top = rect.top + 8
  const bottom = rect.bottom - 8
  const height = Math.max(1, bottom - top)
  const y = Math.max(top, Math.min(bottom, point.clientY))
  const ratio = (y - top) / height
  const count = Math.max(0, targetEndExclusive - targetStart)
  const offset = Math.max(0, Math.min(count, Math.round(ratio * count)))
  return Math.max(0, Math.min(totalLength, targetStart + offset))
}

function clearFieldSortDragState() {
  clearFieldSortTurnTimer()
  if (fieldSortTurnRaf) {
    cancelAnimationFrame(fieldSortTurnRaf)
    fieldSortTurnRaf = 0
  }
  fieldSortTurnPendingPoint = null
  fieldSortTurnDirection.value = ''
  fieldSortDragging.value = false
  fieldSortDraggingCode.value = ''
  fieldSortSuspendPageUpdate.value = false
  fieldSortDragStartSnapshot.value = []
  fieldSortDragStartPage.value = 1
  fieldSortDragSourceIndex.value = -1
  fieldSortDragSourceRow.value = null
  fieldSortVisitedCrossPageDuringDrag.value = false
  fieldSortTurnConsumedDirection = ''
  fieldSortAutoScrollLastAt = 0
  fieldSortAutoScrollCarry = 0
  fieldSortAutoScrollDirection = 0
  fieldSortAutoScrollVelocity = 0
  fieldSortCrossPreviewLastPage = 0
  fieldSortCrossPreviewLastInsertIndex = -1
  fieldSortCrossPreviewLastPointKey = ''
  fieldSortCrossPreviewLastPointerY = NaN
  fieldSortCrossPreviewMoveDown = true
}

function resolveFieldSortTurnPoint(event) {
  if (!event) return null
  if (event.touches && event.touches[0]) return event.touches[0]
  if (event.changedTouches && event.changedTouches[0]) return event.changedTouches[0]
  if (event.originalEvent) return resolveFieldSortTurnPoint(event.originalEvent)
  if (typeof event.clientX === 'number' && typeof event.clientY === 'number') return event
  return null
}

function autoScrollSortBody(point) {
  const body = getFieldSortBodyEl()
  if (!body || !point || typeof point.clientY !== 'number') return
  const rect = body.getBoundingClientRect()
  const edgeThreshold = Math.max(48, Math.min(96, Math.round(rect.height * 0.16)))
  const minStep = 0.5
  const maxStep = 8.8
  const maxTickStep = 10
  const topEdge = rect.top + edgeThreshold
  const bottomEdge = rect.bottom - edgeThreshold
  let targetDirection = 0
  let depth = 0
  if (point.clientY < topEdge) {
    targetDirection = -1
    depth = topEdge - point.clientY
  } else if (point.clientY > bottomEdge) {
    targetDirection = 1
    depth = point.clientY - bottomEdge
  }
  if (!targetDirection || depth <= 0) {
    fieldSortAutoScrollLastAt = 0
    fieldSortAutoScrollCarry = 0
    fieldSortAutoScrollDirection = 0
    fieldSortAutoScrollVelocity = 0
    return
  }
  if (fieldSortAutoScrollDirection && fieldSortAutoScrollDirection !== targetDirection) {
    fieldSortAutoScrollCarry = 0
    fieldSortAutoScrollVelocity = 0
  }
  fieldSortAutoScrollDirection = targetDirection
  const now = Date.now()
  if (!fieldSortAutoScrollLastAt) {
    fieldSortAutoScrollLastAt = now - 16
  }
  const elapsed = Math.max(10, Math.min(40, now - fieldSortAutoScrollLastAt))
  if (elapsed < 12) return
  fieldSortAutoScrollLastAt = now
  const ratio = Math.max(0, Math.min(1, depth / edgeThreshold))
  const eased = Math.pow(ratio, 1.35)
  const targetVelocity = targetDirection * (minStep + (maxStep - minStep) * eased)
  const smooth = 0.3
  fieldSortAutoScrollVelocity += (targetVelocity - fieldSortAutoScrollVelocity) * smooth
  const deltaFloat = fieldSortAutoScrollVelocity * (elapsed / 16)
  fieldSortAutoScrollCarry += deltaFloat
  let delta = fieldSortAutoScrollCarry > 0 ? Math.floor(fieldSortAutoScrollCarry) : Math.ceil(fieldSortAutoScrollCarry)
  if (delta > maxTickStep) delta = maxTickStep
  if (delta < -maxTickStep) delta = -maxTickStep
  if (delta) {
    fieldSortAutoScrollCarry -= delta
  }
  if (!delta) return
  const maxScroll = Math.max(0, body.scrollHeight - body.clientHeight)
  const next = Math.max(0, Math.min(maxScroll, body.scrollTop + delta))
  if (next === body.scrollTop) {
    fieldSortAutoScrollCarry = 0
    fieldSortAutoScrollVelocity = 0
    fieldSortAutoScrollDirection = 0
    return
  }
  body.scrollTop = next
}

function applyFieldSortTurnByPoint(point) {
  if (!fieldSortDragging.value || !point) return
  autoScrollSortBody(point)
  const target = document.elementFromPoint(point.clientX, point.clientY)
  fieldSortSuspendPageUpdate.value = fieldSortPage.value !== fieldSortDragStartPage.value
  if (fieldSortSuspendPageUpdate.value) {
    refreshFieldSortCrossPageDragRowsByPoint(point)
  }
  const zone = target && target.closest ? target.closest('.sort-page-turn-zone') : null
  const rawDirection = zone && zone.getAttribute ? String(zone.getAttribute('data-turn') || '') : ''
  const direction = canTurnFieldSortPage(rawDirection) ? rawDirection : ''
  if (!direction) {
    fieldSortTurnDirection.value = ''
    fieldSortTurnConsumedDirection = ''
    clearFieldSortTurnTimer()
    return
  }
  if (direction !== fieldSortTurnDirection.value) {
    fieldSortTurnDirection.value = direction
    if (fieldSortTurnConsumedDirection !== direction) {
      scheduleFieldSortTurn(direction)
    }
    return
  }
  if (fieldSortTurnConsumedDirection === direction) return
  if (!fieldSortTurnTimer) {
    scheduleFieldSortTurn(direction)
  }
}

function detectFieldSortTurnZone(event) {
  if (!fieldSortDragging.value) return
  const point = resolveFieldSortTurnPoint(event)
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return
  fieldSortTurnPendingPoint = {
    clientX: point.clientX,
    clientY: point.clientY
  }
  if (fieldSortTurnRaf) return
  fieldSortTurnRaf = requestAnimationFrame(() => {
    fieldSortTurnRaf = 0
    applyFieldSortTurnByPoint(fieldSortTurnPendingPoint)
  })
}

function onFieldSortDragMove(evt, originalEvent) {
  detectFieldSortTurnZone(originalEvent || (evt && evt.originalEvent) || evt)
}

function onFieldSortDragStart(event) {
  fieldSortDragging.value = true
  fieldSortDraggingCode.value = String(
    (event && event.item && event.item.dataset && event.item.dataset.sortId) || ''
  )
  fieldSortTurnDirection.value = ''
  fieldSortTurnConsumedDirection = ''
  fieldSortAutoScrollLastAt = 0
  fieldSortAutoScrollCarry = 0
  fieldSortAutoScrollDirection = 0
  fieldSortAutoScrollVelocity = 0
  fieldSortCrossPreviewLastPage = 0
  fieldSortCrossPreviewLastInsertIndex = -1
  fieldSortCrossPreviewLastPointKey = ''
  fieldSortCrossPreviewLastPointerY = NaN
  fieldSortCrossPreviewMoveDown = true
  fieldSortSuspendPageUpdate.value = false
  fieldSortVisitedCrossPageDuringDrag.value = false
  captureFieldSortDragSnapshot()
  document.addEventListener('drag', detectFieldSortTurnZone)
  document.addEventListener('dragover', detectFieldSortTurnZone)
  document.addEventListener('mousemove', detectFieldSortTurnZone)
  document.addEventListener('touchmove', detectFieldSortTurnZone, { passive: true })
}

function resolveFieldSortIndex(row) {
  const code = String((row && row.code) || '')
  if (!code) return '-'
  return fieldSortIndexMap.value.get(code) ?? '-'
}

function setFieldSortDragPageRows(nextRows) {
  const next = Array.isArray(nextRows) ? nextRows : []
  const current = Array.isArray(fieldSortDragPageRows.value) ? fieldSortDragPageRows.value : []
  if (
    current.length === next.length &&
    current.every((item, idx) => String((item && item.code) || '') === String((next[idx] && next[idx].code) || ''))
  ) {
    return
  }
  fieldSortDragPageRows.value = next.slice()
}

function syncFieldSortPage() {
  normalizeFieldSortDraftRows()
  const maxPage = fieldSortPageTotal.value
  const nextPage = Math.max(1, Math.min(maxPage, Number(fieldSortPage.value || 1)))
  if (nextPage !== fieldSortPage.value) {
    fieldSortPage.value = nextPage
  }
}

function refreshFieldSortPageRows() {
  normalizeFieldSortDraftRows()
  syncFieldSortPage()
  if (fieldSortMode.value !== 'drag') {
    setFieldSortDragPageRows([])
    return
  }
  const size = Math.max(1, Number(fieldSortPageSize.value || 10))
  if (fieldSortDragging.value && fieldSortPage.value !== fieldSortDragStartPage.value) {
    refreshFieldSortCrossPageDragRowsByPoint(fieldSortTurnPendingPoint)
    return
  }
  const start = (Math.max(1, Number(fieldSortPage.value || 1)) - 1) * size
  setFieldSortDragPageRows(fieldSortDraftRows.value.slice(start, start + size))
}

function onFieldSortDragEnd(event) {
  clearFieldSortTurnTimer()
  if (fieldSortTurnRaf) {
    cancelAnimationFrame(fieldSortTurnRaf)
    fieldSortTurnRaf = 0
  }
  document.removeEventListener('drag', detectFieldSortTurnZone)
  document.removeEventListener('dragover', detectFieldSortTurnZone)
  document.removeEventListener('mousemove', detectFieldSortTurnZone)
  document.removeEventListener('touchmove', detectFieldSortTurnZone)
  try {
    const pointFromEvent = resolveFieldSortTurnPoint(event && event.originalEvent ? event.originalEvent : event)
    if (pointFromEvent && typeof pointFromEvent.clientX === 'number' && typeof pointFromEvent.clientY === 'number') {
      fieldSortTurnPendingPoint = {
        clientX: pointFromEvent.clientX,
        clientY: pointFromEvent.clientY
      }
    }
    const draggedAcrossPage = fieldSortPage.value !== fieldSortDragStartPage.value
    if (!draggedAcrossPage) {
      applyFieldSortSamePageDropByEvent(event)
    } else {
      const point = fieldSortTurnPendingPoint
      const applied = applyFieldSortCrossPageDropByPoint(point || null)
      if (!applied) {
        restoreFieldSortDragSnapshot()
      }
    }
    ensureFieldSortDraftIntegrityAfterDrag()
  } catch (error) {
    restoreFieldSortDragSnapshot()
  } finally {
    clearFieldSortDragState()
    refreshFieldSortJumpNumbers()
  }
}

function moveFieldSortRow(row, delta) {
  const code = String(row && row.code || '')
  if (!code) return
  const list = fieldSortDraftRows.value
  const from = list.findIndex((x) => String(x && x.code || '') === code)
  if (from < 0) return
  const to = from + Number(delta || 0)
  if (to < 0 || to >= list.length) return
  const moved = list.splice(from, 1)[0]
  list.splice(to, 0, moved)
  refreshFieldSortJumpNumbers()
}

function moveFieldSortTop(row) {
  const code = String(row && row.code || '')
  if (!code) return
  const list = fieldSortDraftRows.value
  const from = list.findIndex((x) => String(x && x.code || '') === code)
  if (from < 1) return
  const moved = list.splice(from, 1)[0]
  list.unshift(moved)
  refreshFieldSortJumpNumbers()
}

function moveFieldSortBottom(row) {
  const code = String(row && row.code || '')
  if (!code) return
  const list = fieldSortDraftRows.value
  const from = list.findIndex((x) => String(x && x.code || '') === code)
  if (from < 0 || from === list.length - 1) return
  const moved = list.splice(from, 1)[0]
  list.push(moved)
  refreshFieldSortJumpNumbers()
}

function applyFieldSortJump(row) {
  const code = String(row && row.code || '')
  if (!code) return
  const list = fieldSortDraftRows.value
  const from = list.findIndex((x) => String(x && x.code || '') === code)
  if (from < 0) return
  const target = Math.max(1, Math.min(list.length, Number(row.jumpTo || 1))) - 1
  if (target === from) return
  const moved = list.splice(from, 1)[0]
  list.splice(target, 0, moved)
  refreshFieldSortJumpNumbers()
}

function onFieldSortOpCommand(command, row) {
  const action = String(command || '').trim()
  if (action === 'top') {
    moveFieldSortTop(row)
    return
  }
  if (action === 'bottom') {
    moveFieldSortBottom(row)
  }
}

function onFieldSortPageChange(nextPage) {
  fieldSortPage.value = Number(nextPage || 1)
  refreshFieldSortPageRows()
}

function onFieldSortPageSizeChange(nextSize) {
  fieldSortPageSize.value = Number(nextSize || 10)
  fieldSortPage.value = 1
  refreshFieldSortPageRows()
}

function applyFieldSortResult() {
  const finalCodes = fieldSortDraftRows.value.map((row) => String(row.code || '').trim()).filter(Boolean)
  orderedFieldCodes.value = finalCodes
  templateForm.fieldCodes = [...finalCodes]
  fieldSortCenterVisible.value = false
}

async function loadDict(nextPage = dictPage.value) {
  loadingDict.value = true
  try {
    dictPage.value = Number(nextPage || 1)
    const data = await request.get('/admin/export-dicts', {
      params: {
        page: dictPage.value,
        pageSize: dictPageSize.value,
        moduleKey: moduleKey.value
      }
    })
    dictRows.value = (data && data.records) || []
    dictTotal.value = Number((data && data.total) || 0)
    clearDictSelection()
  } catch (e) {
    dictRows.value = []
    dictTotal.value = 0
    clearDictSelection()
    ElMessage.error(e.message || '字段字典加载失败')
  } finally {
    loadingDict.value = false
  }
}

async function loadTemplates(nextPage = templatePage.value) {
  loadingTemplates.value = true
  try {
    templatePage.value = Number(nextPage || 1)
    const data = await request.get('/admin/export-templates', {
      params: {
        page: templatePage.value,
        pageSize: templatePageSize.value,
        moduleKey: moduleKey.value
      }
    })
    templateRows.value = (data && data.records) || []
    templateTotal.value = Number((data && data.total) || 0)
    clearTemplateSelection()
  } catch (e) {
    templateRows.value = []
    templateTotal.value = 0
    clearTemplateSelection()
    ElMessage.error(e.message || '导出模板加载失败')
  } finally {
    loadingTemplates.value = false
  }
}

function onDictPageSizeChange(size) {
  dictPageSize.value = Number(size || 10)
  loadDict(1)
}

function onTemplatePageSizeChange(size) {
  templatePageSize.value = Number(size || 10)
  loadTemplates(1)
}

async function loadAll(reset = false) {
  if (reset) {
    await Promise.all([loadDict(1), loadTemplates(1)])
    return
  }
  await Promise.all([loadDict(dictPage.value), loadTemplates(templatePage.value)])
}

function openCreateDict() {
  dictEditMode.value = false
  dictForm.id = null
  dictForm.moduleKey = moduleKey.value
  dictForm.fieldCode = ''
  dictForm.fieldName = ''
  dictForm.dataType = 'string'
  dictForm.description = ''
  dictForm.exampleValue = ''
  dictDialogVisible.value = true
}

function openEditDict(row) {
  dictEditMode.value = true
  dictForm.id = row.id
  dictForm.moduleKey = row.moduleKey || moduleKey.value
  dictForm.fieldCode = row.fieldCode || ''
  dictForm.fieldName = row.fieldName || ''
  dictForm.dataType = row.dataType || 'string'
  dictForm.description = row.description || ''
  dictForm.exampleValue = row.exampleValue || ''
  dictDialogVisible.value = true
}

async function saveDict() {
  if (!String(dictForm.fieldCode || '').trim()) {
    ElMessage.warning('请填写字段编码')
    return
  }
  if (!String(dictForm.fieldName || '').trim()) {
    ElMessage.warning('请填写字段名称')
    return
  }
  savingDict.value = true
  try {
    const payload = {
      moduleKey: dictForm.moduleKey,
      fieldCode: dictForm.fieldCode.trim(),
      fieldName: dictForm.fieldName.trim(),
      dataType: dictForm.dataType || 'string',
      description: dictForm.description || null,
      exampleValue: dictForm.exampleValue || null
    }
    if (dictEditMode.value && dictForm.id) {
      await request.put(`/admin/export-dicts/${dictForm.id}`, payload)
    } else {
      await request.post('/admin/export-dicts', payload)
    }
    ElMessage.success('字段字典已保存')
    dictDialogVisible.value = false
    await loadDict(dictPage.value)
  } catch (e) {
    ElMessage.error(e.message || '字段字典保存失败')
  } finally {
    savingDict.value = false
  }
}

function openCreateTemplate() {
  templateEditMode.value = false
  templateForm.id = null
  templateForm.moduleKey = moduleKey.value
  templateForm.templateCode = moduleKey.value === 'farm' ? 'farm_records_standard' : 'seed_tests_standard'
  templateForm.templateName = moduleKey.value === 'farm' ? '农事记录标准模板' : '种子检测标准模板'
  templateForm.versionNo = 1
  templateForm.status = 'enabled'
  templateForm.remark = ''
  templateForm.fieldCodes = []
  syncOrderedFieldCodes()
  templateDialogVisible.value = true
}

function openEditTemplate(row) {
  templateEditMode.value = true
  templateForm.id = row.id
  templateForm.moduleKey = row.moduleKey || moduleKey.value
  templateForm.templateCode = row.templateCode || ''
  templateForm.templateName = row.templateName || ''
  templateForm.versionNo = Number(row.versionNo || 1)
  templateForm.status = row.status || 'enabled'
  templateForm.remark = row.remark || ''
  templateForm.fieldCodes = parseFieldsJson(row.fieldsJson)
  syncOrderedFieldCodes()
  templateDialogVisible.value = true
}

async function saveTemplate() {
  if (!String(templateForm.templateCode || '').trim()) {
    ElMessage.warning('请填写模板编码')
    return
  }
  if (!String(templateForm.templateName || '').trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  if (!Array.isArray(templateForm.fieldCodes) || !templateForm.fieldCodes.length) {
    ElMessage.warning('请至少选择一个字段')
    return
  }
  savingTemplate.value = true
  try {
    const finalFieldCodes = orderedFieldCodes.value.length ? [...orderedFieldCodes.value] : [...templateForm.fieldCodes]
    const payload = cleanParams({
      moduleKey: templateForm.moduleKey,
      templateCode: templateForm.templateCode.trim(),
      templateName: templateForm.templateName.trim(),
      versionNo: Number(templateForm.versionNo || 1),
      status: templateForm.status || 'enabled',
      remark: templateForm.remark || null,
      fieldsJson: JSON.stringify(finalFieldCodes)
    })
    if (templateEditMode.value && templateForm.id) {
      await request.put(`/admin/export-templates/${templateForm.id}`, payload)
    } else {
      await request.post('/admin/export-templates', payload)
    }
    ElMessage.success('导出模板已保存')
    templateDialogVisible.value = false
    await loadTemplates(templatePage.value)
  } catch (e) {
    ElMessage.error(e.message || '导出模板保存失败')
  } finally {
    savingTemplate.value = false
  }
}

watch(
  () => templateForm.fieldCodes,
  () => {
    if (!templateDialogVisible.value) return
    syncOrderedFieldCodes()
  }
)

watch(
  () => fieldSortMode.value,
  (mode) => {
    fieldSortPage.value = 1
    if (mode === 'drag') {
      fieldSortKeyword.value = ''
    }
    refreshFieldSortPageRows()
  }
)

watch(
  () => fieldSortKeyword.value,
  () => {
    if (fieldSortMode.value !== 'button') return
    fieldSortPage.value = 1
    syncFieldSortPage()
  }
)

watch(
  () => fieldSortPageTotalRecords.value,
  () => {
    syncFieldSortPage()
    if (fieldSortMode.value === 'drag') {
      refreshFieldSortPageRows()
    }
  }
)

watch(
  () => fieldSortCenterVisible.value,
  (visible) => {
    if (visible) return
    document.removeEventListener('drag', detectFieldSortTurnZone)
    document.removeEventListener('dragover', detectFieldSortTurnZone)
    document.removeEventListener('mousemove', detectFieldSortTurnZone)
    document.removeEventListener('touchmove', detectFieldSortTurnZone)
    clearFieldSortDragState()
  }
)

onMounted(() => loadAll(true))
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.field-hint {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.field-sort-toolbar {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-sort-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.sort-center-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.sort-center-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sort-center-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.sort-center-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
  color: var(--text-sub);
  font-size: 12px;
}

.sort-center-body {
  max-height: calc(100vh - 290px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
}

.drag-handle {
  cursor: move;
  color: var(--text-sub);
  font-size: 16px;
  line-height: 1;
  margin-top: 2px;
}

.drag-order {
  min-width: 20px;
  color: var(--text-sub);
  font-size: 12px;
  margin-top: 2px;
}

.drag-main {
  min-width: 0;
  flex: 1;
}

.drag-name {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
}

.drag-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 22px;
}

.drag-status {
  border-radius: 999px;
  padding: 0 8px;
  min-height: 20px;
  line-height: 20px;
  border: 1px solid rgba(22, 103, 183, 0.28);
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 12px;
}

.drag-sub {
  margin-top: 2px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-sub);
  font-size: 12px;
}

.drag-extra {
  margin-top: 2px;
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drag-meta {
  margin-left: auto;
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
}

.sort-op {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.jump-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sort-center-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>

