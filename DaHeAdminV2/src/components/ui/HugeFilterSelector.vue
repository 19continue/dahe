<template>
  <div class="huge-filter-selector">
    <div class="trigger-row">
      <el-input
        :model-value="displayLabel"
        :placeholder="placeholder"
        readonly
        :disabled="disabled"
        @click="openDialog"
      />
      <el-button :disabled="disabled" @click="openDialog">选择</el-button>
      <el-button v-if="clearable && hasValue && !disabled" @click="clearSelection">清空</el-button>
    </div>

    <el-dialog v-model="visible" :title="title" width="760px" append-to-body destroy-on-close>
      <div class="query-row">
        <el-input
          v-model="keywordDraft"
          :placeholder="queryPlaceholder"
          clearable
          @keyup.enter="search(1)"
        />
        <el-button :loading="loading" @click="search(1)">查询</el-button>
      </div>

      <el-table
        :data="rows"
        border
        height="360"
        v-loading="loading"
        highlight-current-row
        row-key="__valueKey"
        @row-click="onRowClick"
      >
        <el-table-column type="index" width="60" />
        <el-table-column label="名称" min-width="220">
          <template #default="scope">{{ rowLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column v-if="descKey" label="说明" min-width="260">
          <template #default="scope">{{ rowDesc(scope.row) || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSizeLocal"
          :current-page="page"
          :page-sizes="pageSizes"
          @current-change="search"
          @size-change="onPageSizeChange"
        />
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!currentRow" @click="confirmSelection">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  modelLabel: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    default: '选择项'
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  queryPlaceholder: {
    type: String,
    default: '请输入关键词过滤'
  },
  valueKey: {
    type: String,
    default: 'id'
  },
  labelKey: {
    type: String,
    default: 'name'
  },
  descKey: {
    type: String,
    default: ''
  },
  clearable: {
    type: Boolean,
    default: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  pageSize: {
    type: Number,
    default: 10
  },
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  autoSearch: {
    type: Boolean,
    default: true
  },
  searchDebounce: {
    type: Number,
    default: 260
  },
  cacheable: {
    type: Boolean,
    default: true
  },
  maxCacheEntries: {
    type: Number,
    default: 80
  },
  fetcher: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'update:modelLabel', 'change'])

const visible = ref(false)
const loading = ref(false)
const rows = ref([])
const page = ref(1)
const pageSizeLocal = ref(Number(props.pageSize || 10))
const total = ref(0)
const keyword = ref('')
const keywordDraft = ref('')
const currentRow = ref(null)
const selectedLabel = ref(String(props.modelLabel || ''))
const cacheMap = new Map()
let requestSeq = 0
let keywordDebounceTimer = null

const hasValue = computed(() => {
  const value = String(props.modelValue == null ? '' : props.modelValue).trim()
  return !!value
})

const displayLabel = computed(() => {
  const fromProp = String(props.modelLabel || '').trim()
  if (fromProp) return fromProp
  const fromLocal = String(selectedLabel.value || '').trim()
  return fromLocal
})

watch(
  () => props.modelLabel,
  (next) => {
    const text = String(next || '').trim()
    if (text) {
      selectedLabel.value = text
      return
    }
    if (!hasValue.value) {
      selectedLabel.value = ''
    }
  }
)

watch(
  () => props.modelValue,
  (next) => {
    const value = String(next == null ? '' : next).trim()
    if (!value) {
      currentRow.value = null
      if (!String(props.modelLabel || '').trim()) {
        selectedLabel.value = ''
      }
    }
  }
)

watch(visible, (next) => {
  if (next) return
  clearKeywordDebounce()
})

watch(keywordDraft, () => {
  if (!visible.value || !props.autoSearch) return
  triggerKeywordSearch()
})

function rowValue(row) {
  if (!row) return ''
  const value = row[props.valueKey]
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function rowLabel(row) {
  if (!row) return ''
  const text = row[props.labelKey]
  if (text === null || text === undefined) return ''
  return String(text).trim()
}

function rowDesc(row) {
  if (!row || !props.descKey) return ''
  const text = row[props.descKey]
  if (text === null || text === undefined) return ''
  return String(text).trim()
}

function normalizeRows(inputRows) {
  return (Array.isArray(inputRows) ? inputRows : [])
    .map((row) => {
      if (!row || typeof row !== 'object') return null
      const value = rowValue(row)
      if (!value) return null
      return {
        ...row,
        __valueKey: value
      }
    })
    .filter(Boolean)
}

function buildCacheKey(keywordText, targetPage, targetPageSize) {
  return `${String(keywordText || '').trim()}::${Number(targetPage || 1)}::${Number(targetPageSize || 10)}`
}

function getCachedResult(cacheKey) {
  if (!props.cacheable) return null
  return cacheMap.has(cacheKey) ? cacheMap.get(cacheKey) : null
}

function setCachedResult(cacheKey, data) {
  if (!props.cacheable) return
  if (!cacheMap.has(cacheKey) && cacheMap.size >= Number(props.maxCacheEntries || 80)) {
    const firstKey = cacheMap.keys().next()
    if (firstKey && !firstKey.done) {
      cacheMap.delete(firstKey.value)
    }
  }
  cacheMap.set(cacheKey, data)
}

function syncRows(data) {
  const records = Array.isArray(data) ? data : ((data && data.records) || [])
  rows.value = normalizeRows(records)
  total.value = Number(Array.isArray(data) ? rows.value.length : ((data && data.total) || rows.value.length))
  const currentValue = String(props.modelValue == null ? '' : props.modelValue).trim()
  currentRow.value = currentValue ? (rows.value.find((x) => rowValue(x) === currentValue) || null) : null
}

function clearKeywordDebounce() {
  if (keywordDebounceTimer) {
    clearTimeout(keywordDebounceTimer)
    keywordDebounceTimer = null
  }
}

function triggerKeywordSearch() {
  clearKeywordDebounce()
  const delay = Math.max(0, Number(props.searchDebounce || 0))
  keywordDebounceTimer = setTimeout(() => {
    search(1)
  }, delay)
}

async function search(nextPage = page.value, options = {}) {
  const force = !!(options && options.force)
  loading.value = true
  const seq = ++requestSeq
  try {
    page.value = Number(nextPage || 1)
    keyword.value = String(keywordDraft.value || '').trim()
    const cacheKey = buildCacheKey(keyword.value, page.value, pageSizeLocal.value)
    const cached = force ? null : getCachedResult(cacheKey)
    if (cached) {
      if (seq !== requestSeq) return
      syncRows(cached)
      return
    }
    const data = await props.fetcher({
      keyword: keyword.value,
      page: page.value,
      pageSize: pageSizeLocal.value
    })
    if (seq !== requestSeq) return
    setCachedResult(cacheKey, data)
    syncRows(data)
  } catch (error) {
    if (seq !== requestSeq) return
    rows.value = []
    total.value = 0
    ElMessage.error((error && error.message) || '选项加载失败')
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

function onRowClick(row) {
  currentRow.value = row || null
}

function onPageSizeChange(size) {
  pageSizeLocal.value = Number(size || props.pageSize || 10)
  search(1, { force: true })
}

async function openDialog() {
  if (props.disabled) return
  visible.value = true
  await search(1)
}

function confirmSelection() {
  if (!currentRow.value) return
  const value = rowValue(currentRow.value)
  const label = rowLabel(currentRow.value)
  selectedLabel.value = label
  emit('update:modelValue', value)
  emit('update:modelLabel', label)
  emit('change', {
    value,
    label,
    row: currentRow.value
  })
  visible.value = false
}

function clearSelection() {
  currentRow.value = null
  selectedLabel.value = ''
  emit('update:modelValue', '')
  emit('update:modelLabel', '')
  emit('change', null)
}

onBeforeUnmount(() => {
  clearKeywordDebounce()
})
</script>

<style scoped>
.huge-filter-selector {
  width: 100%;
}

.trigger-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
}

.query-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
