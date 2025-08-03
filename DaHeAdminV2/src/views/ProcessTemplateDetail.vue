<template>
  <div class="process-template-detail-page">
    <PageToolbar
      :title="`流程模板详情 · ${template.templateName || templateId}`"
      subtitle="在独立标签页中维护模板基础信息与步骤节点，支持上移/下移与步骤参数配置。"
      :collapsible="false"
    >
      <template #head-actions>
        <el-button @click="goBack">返回模板列表</el-button>
        <el-button @click="loadTemplateDetail">刷新</el-button>
        <el-button :type="Number(template.enabled) === 1 ? 'warning' : 'success'" @click="toggleTemplateEnabled">
          {{ Number(template.enabled) === 1 ? '禁用模板' : '启用模板' }}
        </el-button>
        <el-button type="primary" @click="openTemplateEdit">编辑模板</el-button>
        <el-button type="success" @click="openCreateStep">新增步骤</el-button>
      </template>
    </PageToolbar>

    <section class="template-summary" v-loading="loadingTemplate">
      <div class="summary-main">
        <div class="summary-title">
          <strong>{{ template.templateName || '-' }}</strong>
          <el-tag size="small" :type="template.bindScope === 'variety' ? 'warning' : 'info'">
            {{ template.bindScope === 'variety' ? '品种专属模板' : '作物通用模板' }}
          </el-tag>
          <el-tag v-if="Number(template.isDefault) === 1" size="small" type="success">默认模板</el-tag>
          <el-tag size="small" :type="Number(template.enabled) === 1 ? 'success' : 'info'">
            {{ Number(template.enabled) === 1 ? '启用中' : '已禁用' }}
          </el-tag>
        </div>
        <div class="summary-meta">
          <span>作物：{{ template.categoryName || '-' }}</span>
          <span>品种：{{ template.varietyName || '通用' }}</span>
          <span>模板ID：{{ template.id || '-' }}</span>
          <span>步骤数：{{ sortedSteps.length }}</span>
        </div>
      </div>
    </section>

    <section class="step-board" v-loading="loadingTemplate">
      <div class="step-board-head">
        <span>流程步骤节点</span>
        <span class="card-meta">共 {{ sortedSteps.length }} 个节点</span>
      </div>

      <div v-if="sortedSteps.length" class="step-flow">
        <article v-for="(step, idx) in sortedSteps" :key="step.id || idx" class="step-node">
          <div class="step-axis">
            <span class="step-dot">{{ idx + 1 }}</span>
            <span v-if="idx < sortedSteps.length - 1" class="step-line"></span>
          </div>

          <div class="step-panel">
            <header class="step-head">
              <div class="step-title-wrap">
                <strong class="step-title">{{ step.stepName || '-' }}</strong>
                <el-tag size="small">排序 {{ step.sortOrder || idx + 1 }}</el-tag>
                <el-tag size="small" type="warning">{{ growthStageLabel(step.growthStage) }}</el-tag>
              </div>
              <div class="step-actions">
                <el-button size="small" :disabled="idx === 0" @click="moveStep(step, -1)">上移</el-button>
                <el-button size="small" :disabled="idx === sortedSteps.length - 1" @click="moveStep(step, 1)">下移</el-button>
                <el-button size="small" type="primary" @click="openEditStep(step)">编辑</el-button>
              </div>
            </header>

            <div class="step-body">
              <div class="step-row">
                <span class="row-label">要求说明：</span>
                <span>{{ step.requirementDesc || '未填写' }}</span>
              </div>
              <div class="step-row">
                <span class="row-label">参数配置：</span>
                <el-tag size="small" :type="step.formConfigId ? 'success' : (step.formSchema ? 'warning' : 'info')">
                  {{ step.formConfigId ? `动态模板：${step.formConfigName || step.formConfigId}` : (step.formSchema ? '手工参数配置' : '未配置参数') }}
                </el-tag>
              </div>

              <div v-if="stepSchemaPreview(step).length" class="schema-preview-lines">
                <span v-for="item in stepSchemaPreview(step)" :key="`${step.id}-${item.key}`" class="schema-chip">
                  {{ item.label }} · {{ item.type }}
                </span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else description="当前模板暂无步骤，请新增步骤" :image-size="56">
        <el-button type="primary" @click="openCreateStep">新增步骤</el-button>
      </el-empty>
    </section>

    <el-dialog v-model="templateDialogVisible" title="编辑流程模板" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="作物">
          <el-select v-model="templateForm.categoryId" filterable style="width: 100%" @change="onTemplateCategoryChange">
            <el-option v-for="item in cropCategories" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-select
            v-model="templateForm.varietyId"
            clearable
            filterable
            placeholder="不选则为作物通用模板"
            style="width: 100%"
            @change="onTemplateVarietyChange"
          >
            <el-option v-for="item in templateVarietyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名">
          <el-input v-model="templateForm.templateName" />
        </el-form-item>
        <el-form-item label="默认模板">
          <el-switch v-model="templateForm.isDefault" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stepDialogVisible" :title="stepEditMode ? '编辑流程步骤' : '新增流程步骤'" width="980px" destroy-on-close>
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="9">
            <el-form-item label="步骤名">
              <el-input v-model="stepForm.stepName" placeholder="例如：追肥" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="排序">
              <el-input-number v-model="stepForm.sortOrder" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="生长阶段">
              <el-select v-model="stepForm.growthStage" clearable style="width: 100%">
                <el-option label="播种阶段" value="sowing" />
                <el-option label="生长阶段" value="growing" />
                <el-option label="收获阶段" value="harvesting" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="要求说明">
          <el-input v-model="stepForm.requirementDesc" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="参数模板">
          <el-select
            v-model="stepForm.formConfigId"
            clearable
            filterable
            placeholder="不选则使用手工参数"
            style="width: 100%"
            @change="onStepFormConfigChange"
          >
            <el-option v-for="item in dynamicConfigs" :key="item.id" :label="dynamicConfigLabel(item)" :value="item.id" />
          </el-select>
          <div class="field-hint">绑定动态模板后，手工参数编辑将锁定，按动态参数中心配置渲染。</div>
        </el-form-item>
      </el-form>

      <el-divider>步骤参数（节点表单）</el-divider>

      <el-tabs v-model="stepSchemaEditorMode">
        <el-tab-pane label="可视化编辑" name="visual">
          <div class="schema-toolbar">
            <el-button size="small" :disabled="schemaLocked" @click="addSchemaRow">添加参数</el-button>
            <el-button size="small" :disabled="schemaLocked" @click="addNumberPreset">数量参数</el-button>
            <el-button size="small" :disabled="schemaLocked" @click="addLocationPreset">位置参数</el-button>
            <el-button size="small" :disabled="schemaLocked" @click="addSelectPreset">下拉参数</el-button>
            <el-button size="small" :disabled="schemaLocked" @click="syncStepSchemaJsonByRows">同步到JSON</el-button>
          </div>

          <div class="schema-list">
            <div v-for="(row, idx) in schemaRows" :key="row.uid" class="schema-item">
              <div class="schema-item-index">{{ idx + 1 }}</div>
              <div class="schema-item-grid">
                <el-input
                  v-model="row.label"
                  :disabled="schemaLocked"
                  placeholder="参数名称，例如：追肥量(kg/亩)"
                  @input="onSchemaLabelInput(row)"
                />
                <el-input :model-value="row.key" readonly placeholder="字段Key自动生成" />
                <el-select v-model="row.type" :disabled="schemaLocked" placeholder="类型" @change="onSchemaTypeChange(row)">
                  <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-input v-model="row.placeholder" :disabled="schemaLocked" placeholder="占位提示" />
                <el-input v-model="row.defaultValue" :disabled="schemaLocked" placeholder="默认值" />
                <div class="schema-item-side">
                  <el-switch v-model="row.required" :disabled="schemaLocked" />
                  <el-button size="small" type="danger" :disabled="schemaLocked" @click="removeSchemaRow(idx)">删除</el-button>
                </div>
              </div>
              <div v-if="row.type === 'select'" class="schema-option-editor">
                <div class="schema-option-head">
                  <span>选项</span>
                  <el-button size="small" :disabled="schemaLocked" @click="addSchemaOption(row)">新增选项</el-button>
                </div>
                <div v-for="(opt, optIdx) in row.options" :key="opt.uid" class="schema-option-row">
                  <el-input
                    v-model="opt.label"
                    :disabled="schemaLocked"
                    placeholder="选项名称"
                    @input="onSchemaOptionLabelInput(row, opt, optIdx)"
                  />
                  <el-input :model-value="opt.value" readonly placeholder="选项值自动生成" />
                  <el-button size="small" type="danger" :disabled="schemaLocked" @click="removeSchemaOption(row, optIdx)">删除</el-button>
                </div>
                <el-empty v-if="!row.options.length" description="暂无选项，请新增" :image-size="36" />
              </div>
            </div>

            <el-empty v-if="!schemaRows.length" description="暂无参数，可添加" :image-size="42" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="JSON编辑（专业）" name="json">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="专业模式可直接编辑JSON；可视化模式会自动生成字段Key与选项Value。"
          />
          <el-input v-model="stepSchemaJson" type="textarea" :rows="11" :disabled="schemaLocked" />
          <div class="json-toolbar">
            <el-button :disabled="schemaLocked" @click="syncStepSchemaRowsByJson">从JSON同步可视化</el-button>
            <el-button :disabled="schemaLocked" @click="formatStepSchemaJson">格式化JSON</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="stepDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingStep" @click="saveStep">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
const templateId = ref(String(route.params.templateId || '').trim())

const loadingTemplate = ref(false)
const savingTemplate = ref(false)
const savingStep = ref(false)

const template = reactive({
  id: null,
  cropId: null,
  categoryId: null,
  categoryName: '',
  varietyId: null,
  varietyName: '',
  bindScope: 'category',
  templateName: '',
  isDefault: 0,
  enabled: 1,
  steps: []
})

const cropCategories = ref([])
const allVarieties = ref([])
const varietyToCategory = ref(new Map())
const dynamicConfigs = ref([])

const templateDialogVisible = ref(false)
const templateForm = reactive({
  categoryId: null,
  varietyId: null,
  templateName: '',
  isDefault: false,
  enabled: true
})

const stepDialogVisible = ref(false)
const stepEditMode = ref(false)
const stepForm = reactive({
  id: null,
  stepName: '',
  sortOrder: 1,
  growthStage: '',
  requirementDesc: '',
  formConfigId: null
})

const typeOptions = [
  { label: '文本', value: 'text' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '时间', value: 'time' },
  { label: '下拉选择', value: 'select' },
  { label: '位置', value: 'location' },
  { label: '多行文本', value: 'textarea' }
]

let schemaUid = 1
let schemaOptionUid = 1
const schemaRows = ref([])
const stepSchemaEditorMode = ref('visual')
const stepSchemaJson = ref('[]')

function toId(value) {
  if (value === null || value === undefined) return null
  const raw = String(value).trim()
  if (!raw || raw === '0') return null
  if (!/^\d+$/.test(raw)) return null
  const normalized = raw.replace(/^0+/, '')
  return normalized || null
}

const sortedSteps = computed(() => {
  const rows = Array.isArray(template.steps) ? template.steps : []
  return [...rows].sort((a, b) => {
    const sa = Number((a && a.sortOrder) || 0)
    const sb = Number((b && b.sortOrder) || 0)
    if (sa !== sb) return sa - sb
    return String((a && a.id) || '').localeCompare(String((b && b.id) || ''))
  })
})

const templateVarietyOptions = computed(() => {
  const categoryId = toId(templateForm.categoryId)
  if (!categoryId) return allVarieties.value
  return allVarieties.value.filter((item) => toId(item && item.categoryId) === categoryId)
})

const schemaLocked = computed(() => !!stepForm.formConfigId)

function growthStageLabel(value) {
  const map = {
    sowing: '播种阶段',
    growing: '生长阶段',
    harvesting: '收获阶段'
  }
  return map[String(value || '').toLowerCase()] || '通用阶段'
}

function dynamicConfigLabel(row) {
  if (!row) return '-'
  const version = row.versionNo ? `v${row.versionNo}` : 'v1'
  return `${row.configName} (${version})`
}

function parseSchema(formSchema) {
  if (!formSchema) return []
  try {
    const rows = JSON.parse(formSchema)
    return Array.isArray(rows) ? rows : []
  } catch (error) {
    return []
  }
}

function stepSchemaPreview(step) {
  const rows = parseSchema(step && step.formSchema)
  return rows
    .map((item) => ({
      key: String((item && item.key) || '').trim(),
      label: String((item && item.label) || '').trim(),
      type: String((item && item.type) || 'text').trim() || 'text'
    }))
    .filter((item) => item.key || item.label)
    .slice(0, 6)
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
  const rows = Array.isArray(optionsInput) ? optionsInput : []
  return rows
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

function parseSchemaJsonToRows(formSchema) {
  const parsed = parseSchema(formSchema)
  return parsed.map((item, idx) => {
    const label = normalizeText(item && item.label)
    const key = normalizeText(item && item.key) || generateFieldKey(label, idx + 1)
    const options = parseSchemaOptions(item && item.options)
    return createSchemaRow({
      key,
      label,
      type: normalizeText(item && item.type) || 'text',
      required: !!(item && item.required),
      placeholder: normalizeText(item && item.placeholder),
      defaultValue: item && item.defaultValue != null ? String(item.defaultValue) : '',
      options
    })
  })
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

function addSchemaRow() {
  const next = createSchemaRow()
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function removeSchemaRow(index) {
  schemaRows.value.splice(index, 1)
}

function addNumberPreset() {
  const next = createSchemaRow({
    label: '数量',
    type: 'number',
    required: true
  })
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function addLocationPreset() {
  const next = createSchemaRow({
    label: '作业位置',
    type: 'location',
    required: false
  })
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
}

function addSelectPreset() {
  const next = createSchemaRow({
    label: '类型',
    type: 'select',
    required: true,
    options: [createSchemaOption({ label: '选项A', value: 'optionA' }), createSchemaOption({ label: '选项B', value: 'optionB' })]
  })
  schemaRows.value.push(next)
  normalizeSchemaRowKey(next, schemaRows.value.length)
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

function normalizeSchemaRows(strict) {
  const usedKeys = new Set()
  const rows = []

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
    if (!label) return { rows: [], error: `第${i + 1}个参数缺少名称` }
    if (!key) return { rows: [], error: `第${i + 1}个参数缺少字段Key` }
    if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(key)) return { rows: [], error: `参数 key 不合法：${key}` }

    const item = {
      key,
      label,
      type,
      required: !!row.required
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
        return { rows: [], error: `参数 ${label} 为下拉类型，至少需要一个选项` }
      }
      if (options.length) item.options = options
    }

    rows.push(item)
  }

  return { rows, error: null }
}

async function normalizeSchemaRowsWithRemote(strict) {
  const usedKeys = new Set()
  const rows = []

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
    if (!label) return { rows: [], error: `第${i + 1}个参数缺少名称` }
    if (!key) return { rows: [], error: `第${i + 1}个参数缺少字段Key` }
    if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(key)) return { rows: [], error: `参数 key 不合法：${key}` }

    const item = {
      key,
      label,
      type,
      required: !!row.required
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
        return { rows: [], error: `参数 ${label} 为下拉类型，至少需要一个选项` }
      }
      if (options.length) item.options = options
    }

    rows.push(item)
  }

  return { rows, error: null }
}

function parseSchemaJsonText(text) {
  const raw = String(text || '').trim()
  if (!raw) return { rows: [], error: null }
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return { rows: [], error: 'JSON必须是数组格式' }
    }
    return { rows: parsed, error: null }
  } catch (error) {
    return { rows: [], error: 'JSON格式不合法' }
  }
}

function syncStepSchemaJsonByRows(strict = false, showMessage = false) {
  const normalized = normalizeSchemaRows(strict)
  if (normalized.error) {
    if (showMessage) ElMessage.warning(normalized.error)
    return false
  }
  stepSchemaJson.value = JSON.stringify(normalized.rows, null, 2)
  return true
}

function syncStepSchemaRowsByJson(showMessage = true) {
  const parsed = parseSchemaJsonText(stepSchemaJson.value)
  if (parsed.error) {
    if (showMessage) ElMessage.warning(parsed.error)
    return false
  }
  schemaRows.value = parseSchemaJsonToRows(JSON.stringify(parsed.rows))
  if (showMessage) ElMessage.success('已从JSON同步到可视化')
  return true
}

function formatStepSchemaJson() {
  const parsed = parseSchemaJsonText(stepSchemaJson.value)
  if (parsed.error) {
    ElMessage.warning(parsed.error)
    return
  }
  stepSchemaJson.value = JSON.stringify(parsed.rows, null, 2)
}

function applyDynamicConfigSchema(formConfigId) {
  const targetId = toId(formConfigId)
  if (!targetId) return false
  const found = dynamicConfigs.value.find((item) => toId(item && item.id) === targetId)
  if (!found || !found.schemaJson) return false
  schemaRows.value = parseSchemaJsonToRows(found.schemaJson)
  syncStepSchemaJsonByRows(false, false)
  return true
}

function onStepFormConfigChange(value) {
  if (value) {
    applyDynamicConfigSchema(value)
    return
  }
  if (!syncStepSchemaRowsByJson(false)) {
    schemaRows.value = []
    stepSchemaJson.value = '[]'
  }
}

function fillTemplate(data) {
  const row = data || {}
  template.id = row.id || null
  template.cropId = row.cropId || null
  template.categoryId = row.categoryId || null
  template.categoryName = row.categoryName || ''
  template.varietyId = row.varietyId || null
  template.varietyName = row.varietyName || ''
  template.bindScope = row.bindScope || 'category'
  template.templateName = row.templateName || ''
  template.isDefault = Number(row.isDefault || 0)
  template.enabled = Number(row.enabled == null ? 1 : row.enabled)
  template.steps = Array.isArray(row.steps) ? row.steps : []
}

function goBack() {
  router.push('/process-templates')
}

function rebuildCropOptions(treeRows) {
  const categories = []
  const varieties = []
  const v2c = new Map()
  ;(Array.isArray(treeRows) ? treeRows : []).forEach((item) => {
    const categoryId = toId(item && item.categoryId)
    const categoryName = String((item && item.categoryName) || '').trim()
    if (!categoryId || !categoryName) return
    categories.push({ value: categoryId, label: categoryName })
    ;(Array.isArray(item && item.varieties) ? item.varieties : []).forEach((v) => {
      const varietyId = toId(v && v.id)
      const varietyName = String((v && v.name) || '').trim()
      if (!varietyId || !varietyName) return
      varieties.push({
        value: varietyId,
        label: varietyName,
        categoryId,
        categoryName
      })
      v2c.set(varietyId, categoryId)
    })
  })
  cropCategories.value = categories
  allVarieties.value = varieties
  varietyToCategory.value = v2c
}

async function loadCropTree() {
  try {
    const data = await request.get('/meta/options/crop-tree')
    rebuildCropOptions(data)
  } catch (error) {
    cropCategories.value = []
    allVarieties.value = []
    varietyToCategory.value = new Map()
  }
}

async function loadDynamicConfigs() {
  try {
    const data = await request.get('/admin/dynamic-configs', {
      params: {
        page: 1,
        pageSize: 200,
        moduleKey: 'farm',
        sceneKey: 'step_fields',
        status: 'enabled'
      }
    })
    dynamicConfigs.value = (data && data.records) || []
  } catch (error) {
    dynamicConfigs.value = []
  }
}

async function loadTemplateDetail() {
  if (!templateId.value) return
  loadingTemplate.value = true
  try {
    const data = await request.get(`/farm-process/templates/${templateId.value}`, {
      params: {
        includeSteps: true
      }
    })
    fillTemplate(data)
    handleRouteMode()
  } catch (error) {
    ElMessage.error(error.message || '模板详情加载失败')
  } finally {
    loadingTemplate.value = false
  }
}

function handleRouteMode() {
  const mode = String(route.query.mode || '').trim().toLowerCase()
  if (mode !== 'edit') return
  openTemplateEdit()
  const nextQuery = { ...route.query }
  delete nextQuery.mode
  const hasQuery = Object.keys(nextQuery).length > 0
  router.replace({ path: route.path, query: hasQuery ? nextQuery : undefined })
}

function openTemplateEdit() {
  templateForm.categoryId = toId(template.categoryId)
  templateForm.varietyId = toId(template.varietyId)
  templateForm.templateName = template.templateName || ''
  templateForm.isDefault = Number(template.isDefault) === 1
  templateForm.enabled = Number(template.enabled) === 1
  templateDialogVisible.value = true
}

function onTemplateCategoryChange() {
  const categoryId = toId(templateForm.categoryId)
  if (!categoryId) return
  const varietyId = toId(templateForm.varietyId)
  if (!varietyId) return
  const hitCategoryId = varietyToCategory.value.get(varietyId)
  if (!hitCategoryId || toId(hitCategoryId) !== categoryId) {
    templateForm.varietyId = null
  }
}

function onTemplateVarietyChange(value) {
  const varietyId = toId(value)
  if (!varietyId) return
  const categoryId = varietyToCategory.value.get(varietyId)
  if (categoryId) templateForm.categoryId = categoryId
}

async function saveTemplate() {
  if (!template.id) return
  const categoryId = toId(templateForm.categoryId)
  const varietyId = toId(templateForm.varietyId)
  const templateName = String(templateForm.templateName || '').trim()

  if (!categoryId && !varietyId) {
    ElMessage.warning('请选择作物')
    return
  }
  if (!templateName) {
    ElMessage.warning('请输入模板名')
    return
  }
  if (!templateForm.enabled && templateForm.isDefault) {
    ElMessage.warning('禁用模板不能设为默认模板')
    return
  }

  savingTemplate.value = true
  try {
    await request.put(`/farm-process/templates/${template.id}`, {
      cropId: varietyId || categoryId,
      categoryId,
      varietyId,
      templateName,
      isDefault: !!templateForm.isDefault,
      enabled: !!templateForm.enabled
    })
    templateDialogVisible.value = false
    await loadTemplateDetail()
    ElMessage.success('模板已更新')
  } catch (error) {
    ElMessage.error(error.message || '模板更新失败')
  } finally {
    savingTemplate.value = false
  }
}

async function toggleTemplateEnabled() {
  if (!template.id) return
  const nextEnabled = Number(template.enabled) !== 1
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await request.put(`/farm-process/templates/${template.id}/enabled`, { enabled: nextEnabled })
    await loadTemplateDetail()
    ElMessage.success(`模板已${actionText}`)
  } catch (error) {
    ElMessage.error(error.message || `模板${actionText}失败`)
  }
}

function resetStepForm() {
  stepForm.id = null
  stepForm.stepName = ''
  const currentMaxSort = Number((sortedSteps.value[sortedSteps.value.length - 1] && sortedSteps.value[sortedSteps.value.length - 1].sortOrder) || 0)
  stepForm.sortOrder = Math.max(1, currentMaxSort + 1)
  stepForm.growthStage = ''
  stepForm.requirementDesc = ''
  stepForm.formConfigId = null
  schemaRows.value = []
  stepSchemaJson.value = '[]'
  stepSchemaEditorMode.value = 'visual'
}

function openCreateStep() {
  if (!template.id) return
  stepEditMode.value = false
  resetStepForm()
  stepDialogVisible.value = true
}

function openEditStep(row) {
  stepEditMode.value = true
  stepForm.id = row.id
  stepForm.stepName = row.stepName || ''
  stepForm.sortOrder = Math.max(1, Number(row.sortOrder || 1))
  stepForm.growthStage = row.growthStage || ''
  stepForm.requirementDesc = row.requirementDesc || ''
  stepForm.formConfigId = row.formConfigId || null
  if (!applyDynamicConfigSchema(stepForm.formConfigId)) {
    schemaRows.value = parseSchemaJsonToRows(row.formSchema)
    stepSchemaJson.value = JSON.stringify(parseSchema(row.formSchema), null, 2)
  } else {
    stepSchemaJson.value = JSON.stringify(parseSchema((row && row.formSchema) || '[]'), null, 2)
  }
  if (!stepForm.formConfigId && !String(stepSchemaJson.value || '').trim()) {
    stepSchemaJson.value = '[]'
  }
  stepSchemaEditorMode.value = 'visual'
  stepDialogVisible.value = true
}

async function saveStep() {
  if (!template.id) return
  const stepName = String(stepForm.stepName || '').trim()
  if (!stepName) {
    ElMessage.warning('请输入步骤名')
    return
  }

  let formSchemaPayload = null
  if (!stepForm.formConfigId) {
    if (stepSchemaEditorMode.value === 'json') {
      const parsed = parseSchemaJsonText(stepSchemaJson.value)
      if (parsed.error) {
        ElMessage.warning(parsed.error)
        return
      }
      formSchemaPayload = parsed.rows.length ? JSON.stringify(parsed.rows) : null
      schemaRows.value = parseSchemaJsonToRows(formSchemaPayload || '[]')
    } else {
      const normalized = await normalizeSchemaRowsWithRemote(true)
      if (normalized.error) {
        ElMessage.warning(normalized.error)
        return
      }
      stepSchemaJson.value = JSON.stringify(normalized.rows, null, 2)
      formSchemaPayload = normalized.rows.length ? JSON.stringify(normalized.rows) : null
    }
  }

  savingStep.value = true
  try {
    const payload = {
      stepName,
      sortOrder: Math.max(1, Number(stepForm.sortOrder || 1)),
      growthStage: stepForm.growthStage || null,
      requirementDesc: stepForm.requirementDesc || null,
      formConfigId: stepForm.formConfigId || null,
      formSchema: stepForm.formConfigId ? null : formSchemaPayload
    }
    if (stepEditMode.value && stepForm.id) {
      await request.put(`/farm-process/steps/${stepForm.id}`, payload)
    } else {
      await request.post(`/farm-process/templates/${template.id}/steps`, payload)
    }
    stepDialogVisible.value = false
    await loadTemplateDetail()
    ElMessage.success('步骤已保存')
  } catch (error) {
    ElMessage.error(error.message || '步骤保存失败')
  } finally {
    savingStep.value = false
  }
}

async function moveStep(row, delta) {
  if (!template.id || !row || !row.id) return
  const ordered = [...sortedSteps.value]
  const rowId = toId(row.id)
  const from = ordered.findIndex((item) => toId(item && item.id) === rowId)
  if (from < 0) return
  const to = from + Number(delta || 0)
  if (to < 0 || to >= ordered.length) return

  const moved = ordered.splice(from, 1)[0]
  ordered.splice(to, 0, moved)
  const stepIds = ordered.map((item) => toId(item && item.id)).filter(Boolean)
  if (!stepIds.length) return

  try {
    await request.put(`/farm-process/templates/${template.id}/steps/sort`, { stepIds })
    await loadTemplateDetail()
    ElMessage.success('排序已更新')
  } catch (error) {
    ElMessage.error(error.message || '步骤排序失败')
  }
}

watch(
  () => route.params.templateId,
  async (next) => {
    templateId.value = String(next || '').trim()
    await loadTemplateDetail()
  }
)

watch(
  () => stepSchemaEditorMode.value,
  (next, prev) => {
    if (next === prev) return
    if (next === 'json' && prev === 'visual') {
      syncStepSchemaJsonByRows(false, false)
      return
    }
    if (next === 'visual' && prev === 'json') {
      syncStepSchemaRowsByJson(false)
    }
  }
)

onMounted(async () => {
  await Promise.all([loadCropTree(), loadDynamicConfigs()])
  await loadTemplateDetail()
})
</script>

<style scoped>
.process-template-detail-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.template-summary {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 12px 8px;
  margin-bottom: 12px;
}

.summary-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--text-sub);
  font-size: 13px;
}

.step-board {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 10px 8px 12px;
}

.step-board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.step-flow {
  display: grid;
  gap: 10px;
}

.step-node {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 8px;
}

.step-axis {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.step-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid rgba(22, 103, 183, 0.34);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.step-line {
  flex: 1;
  width: 1px;
  background: var(--border-strong);
  margin-top: 4px;
}

.step-panel {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-soft);
}

.step-head {
  min-height: 48px;
  padding: 10px;
  border-bottom: 1px dashed var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step-title-wrap {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.step-title {
  color: var(--text-main);
}

.step-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-body {
  padding: 10px;
  display: grid;
  gap: 8px;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-sub);
  font-size: 13px;
}

.row-label {
  color: var(--text-main);
  font-weight: 600;
}

.schema-preview-lines {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.schema-chip {
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg-panel);
  color: var(--text-sub);
  font-size: 12px;
}

.field-hint {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.schema-toolbar {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.schema-list {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 8px;
  display: grid;
  gap: 8px;
}

.schema-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-panel);
  padding: 8px;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 8px;
}

.schema-item-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  font-size: 12px;
}

.schema-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.schema-item-side {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.schema-option-editor {
  grid-column: 2 / -1;
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 8px;
}

.schema-option-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-sub);
  font-size: 12px;
}

.schema-option-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 76px;
  gap: 8px;
  margin-bottom: 6px;
}

.json-toolbar {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.schema-item-grid :deep(.el-input),
.schema-item-grid :deep(.el-select),
.schema-option-row :deep(.el-input) {
  width: 100%;
}

.json-toolbar :deep(.el-button),
.schema-toolbar :deep(.el-button) {
  margin-left: 0;
}

.schema-option-head > span {
  color: var(--text-sub);
  font-size: 12px;
}

@media (max-width: 1080px) {
  .step-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .schema-item {
    grid-template-columns: 1fr;
  }

  .schema-item-grid {
    grid-template-columns: 1fr;
  }

  .schema-item-side {
    justify-content: flex-start;
  }

  .schema-option-editor {
    grid-column: 1 / -1;
  }

  .schema-option-row {
    grid-template-columns: 1fr;
  }
}
</style>
