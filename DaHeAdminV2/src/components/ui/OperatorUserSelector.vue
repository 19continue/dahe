<template>
  <div class="operator-user-selector">
    <el-select
      :model-value="selectedValue"
      filterable
      remote
      clearable
      reserve-keyword
      :disabled="disabled"
      :loading="loading"
      :placeholder="placeholder"
      style="width: 100%"
      @visible-change="onVisibleChange"
      @clear="clearSelection"
      @change="onSelectChange"
      @blur="flushQuery"
      @focus="onFocus"
      :remote-method="onQueryChange"
    >
      <el-option
        v-for="item in mergedOptions"
        :key="item.value"
        :label="item.label"
        :value="item.value"
      >
        <div class="option-main">{{ item.label }}</div>
        <div class="option-sub">{{ item.desc || '-' }}</div>
      </el-option>
    </el-select>
    <div v-if="total > mergedOptions.length" class="option-foot">
      当前显示 {{ mergedOptions.length }} / {{ total }}，可继续输入关键词筛选
    </div>
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
  placeholder: {
    type: String,
    default: '按姓名/昵称/手机号检索操作员'
  },
  pageSize: {
    type: Number,
    default: 30
  },
  disabled: {
    type: Boolean,
    default: false
  },
  fetcher: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'update:modelLabel', 'change'])

const loading = ref(false)
const options = ref([])
const total = ref(0)
const currentQuery = ref('')
const selectedValue = ref(toId(props.modelValue))
const cache = new Map()
let requestSeq = 0
let queryTimer = null

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function rowLabel(row) {
  if (!row || typeof row !== 'object') return ''
  const displayName = String((row && row.displayName) || '').trim()
  if (displayName) return displayName
  const realName = String((row && row.realName) || '').trim()
  if (realName) return realName
  const nickName = String((row && row.nickName) || '').trim()
  if (nickName) return nickName
  return String((row && row.phone) || '').trim() || '-'
}

function rowDesc(row) {
  if (!row || typeof row !== 'object') return ''
  const displayDesc = String((row && row.displayDesc) || '').trim()
  if (displayDesc) return displayDesc
  const phone = String((row && row.phone) || '').trim()
  const role = String((row && row.roleCode) || '').trim()
  if (phone && role) return `${phone} / ${role}`
  return phone || role || ''
}

function normalizeRows(records) {
  return (Array.isArray(records) ? records : [])
    .map((row) => {
      const value = toId(row && row.id)
      if (!value) return null
      return {
        value,
        label: rowLabel(row),
        desc: rowDesc(row),
        row
      }
    })
    .filter(Boolean)
}

const mergedOptions = computed(() => {
  const map = new Map()
  options.value.forEach((item) => {
    if (!item || !item.value) return
    map.set(item.value, item)
  })
  const value = toId(props.modelValue)
  const label = String(props.modelLabel || '').trim()
  if (value && label && !map.has(value)) {
    map.set(value, {
      value,
      label,
      desc: '',
      row: null
    })
  }
  return Array.from(map.values())
})

watch(
  () => props.modelValue,
  (next) => {
    selectedValue.value = toId(next)
  },
  { immediate: true }
)

function clearQueryTimer() {
  if (!queryTimer) return
  clearTimeout(queryTimer)
  queryTimer = null
}

function flushQuery() {
  clearQueryTimer()
}

function readCache(query) {
  const key = String(query || '').trim()
  return cache.has(key) ? cache.get(key) : null
}

function writeCache(query, payload) {
  const key = String(query || '').trim()
  if (!cache.has(key) && cache.size >= 80) {
    const first = cache.keys().next()
    if (first && !first.done) cache.delete(first.value)
  }
  cache.set(key, payload)
}

async function loadOptions(query = '', force = false) {
  const key = String(query || '').trim()
  const cached = force ? null : readCache(key)
  if (cached) {
    options.value = cached.options
    total.value = cached.total
    return
  }
  const seq = ++requestSeq
  loading.value = true
  try {
    const data = await props.fetcher({
      keyword: key,
      page: 1,
      pageSize: Number(props.pageSize || 30)
    })
    if (seq !== requestSeq) return
    const normalizedOptions = normalizeRows(data && data.records)
    const nextPayload = {
      options: normalizedOptions,
      total: Number((data && data.total) || normalizedOptions.length)
    }
    options.value = nextPayload.options
    total.value = nextPayload.total
    writeCache(key, nextPayload)
  } catch (error) {
    if (seq !== requestSeq) return
    options.value = []
    total.value = 0
    ElMessage.error((error && error.message) || '操作员选项加载失败')
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

function onQueryChange(query) {
  currentQuery.value = String(query || '').trim()
  clearQueryTimer()
  queryTimer = setTimeout(() => {
    loadOptions(currentQuery.value)
  }, 240)
}

function onVisibleChange(visible) {
  if (!visible) return
  loadOptions(currentQuery.value)
}

function onFocus() {
  if (!mergedOptions.value.length) {
    loadOptions(currentQuery.value)
  }
}

function clearSelection() {
  selectedValue.value = ''
  emit('update:modelValue', '')
  emit('update:modelLabel', '')
  emit('change', null)
}

function onSelectChange(value) {
  const id = toId(value)
  if (!id) {
    clearSelection()
    return
  }
  selectedValue.value = id
  const hit = mergedOptions.value.find((item) => item.value === id) || null
  const label = hit ? hit.label : String(props.modelLabel || '').trim()
  emit('update:modelValue', id)
  emit('update:modelLabel', label)
  emit('change', {
    value: id,
    label,
    row: hit ? hit.row : null
  })
}

onBeforeUnmount(() => {
  clearQueryTimer()
})
</script>

<style scoped>
.operator-user-selector {
  display: grid;
  gap: 6px;
}

.option-main {
  font-size: 13px;
  line-height: 1.4;
  color: var(--text-main);
}

.option-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.3;
  color: var(--text-sub);
}

.option-foot {
  font-size: 12px;
  color: var(--text-sub);
}
</style>
