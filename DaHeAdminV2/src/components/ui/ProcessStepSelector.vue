<template>
  <div class="process-step-selector">
    <div v-if="showSegments" class="segment-row">
      <el-tag
        v-for="segment in normalizedSegments"
        :key="segment.segmentKey"
        class="segment-tag"
        :type="segment.segmentKey === currentSegmentKey ? 'success' : 'info'"
        effect="light"
        @click="pickSegment(segment.segmentKey)"
      >
        {{ segment.displayName }}
      </el-tag>
    </div>

    <div v-if="visibleSteps.length" class="step-card-grid">
      <button
        v-for="(step, idx) in visibleSteps"
        :key="toId(step.id) || `step-${idx}`"
        type="button"
        class="step-card"
        :class="{ active: toId(modelValue) === toId(step.id) }"
        :disabled="disabled"
        @click="pickStep(step)"
      >
        <div class="step-order">排序 {{ Number(step.sortOrder || 0) > 0 ? Number(step.sortOrder) : (idx + 1) }}</div>
        <div class="step-template">{{ step.templateName || '默认模板' }}</div>
        <div class="step-name">{{ step.stepName || fallbackStepName(step, idx) }}</div>
      </button>
    </div>
    <div v-else class="empty-tip">{{ emptyText }}</div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  segmentKey: {
    type: [String, Number],
    default: ''
  },
  segments: {
    type: Array,
    default: () => []
  },
  steps: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  },
  autoPickFirst: {
    type: Boolean,
    default: true
  },
  emptyText: {
    type: String,
    default: '当前计划没有可选步骤，请先在流程模板中配置步骤。'
  }
})

const emit = defineEmits(['update:modelValue', 'update:segmentKey', 'change'])

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function normalizeSegmentLabel(segment) {
  const cropCategory = String((segment && segment.cropCategory) || '').trim()
  const cropVariety = String((segment && segment.cropVariety) || '').trim()
  const segmentName = String((segment && segment.segmentName) || '').trim()
  if (cropCategory && cropVariety) return `${cropCategory} + ${cropVariety}`
  return cropCategory || cropVariety || segmentName || '默认分组'
}

const normalizedSegments = computed(() => {
  return (Array.isArray(props.segments) ? props.segments : [])
    .map((segment) => ({
      ...(segment || {}),
      segmentKey: toId(segment && segment.segmentKey),
      displayName: normalizeSegmentLabel(segment)
    }))
    .filter((segment) => segment.segmentKey)
})

const showSegments = computed(() => normalizedSegments.value.length > 1)

const currentSegmentKey = computed(() => {
  const key = toId(props.segmentKey)
  if (key) return key
  return normalizedSegments.value.length ? normalizedSegments.value[0].segmentKey : ''
})

const visibleSteps = computed(() => {
  if (normalizedSegments.value.length) {
    const hit = normalizedSegments.value.find((segment) => segment.segmentKey === currentSegmentKey.value)
    return Array.isArray(hit && hit.steps) ? hit.steps : []
  }
  return Array.isArray(props.steps) ? props.steps : []
})

function fallbackStepName(step, index) {
  const stepId = toId(step && step.id)
  return stepId ? `步骤#${stepId}` : `步骤#${index + 1}`
}

function pickSegment(segmentKey) {
  if (props.disabled) return
  const key = toId(segmentKey)
  if (!key || key === currentSegmentKey.value) return
  emit('update:segmentKey', key)
}

function pickStep(step) {
  if (props.disabled) return
  const stepId = toId(step && step.id)
  if (!stepId || stepId === toId(props.modelValue)) return
  emit('update:modelValue', stepId)
  emit('change', step)
}

function ensureState() {
  if (normalizedSegments.value.length) {
    const hasSegment = normalizedSegments.value.some((segment) => segment.segmentKey === currentSegmentKey.value)
    if (!hasSegment) {
      emit('update:segmentKey', normalizedSegments.value[0].segmentKey)
      return
    }
  } else if (toId(props.segmentKey)) {
    emit('update:segmentKey', '')
  }

  if (!props.autoPickFirst) return
  const currentStepId = toId(props.modelValue)
  const hasStep = visibleSteps.value.some((step) => toId(step && step.id) === currentStepId)
  if (hasStep) return
  if (!visibleSteps.value.length) {
    if (currentStepId) emit('update:modelValue', '')
    return
  }
  emit('update:modelValue', toId(visibleSteps.value[0] && visibleSteps.value[0].id))
}

watch(
  () => [props.segmentKey, props.modelValue, props.segments, props.steps],
  () => {
    ensureState()
  },
  { immediate: true, deep: true }
)
</script>

<style scoped>
.process-step-selector {
  display: grid;
  gap: 10px;
  width: 100%;
}

.segment-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segment-tag {
  cursor: pointer;
}

.step-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 8px;
}

.step-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px;
  text-align: left;
  background: var(--bg-soft);
  color: var(--text-main);
  cursor: pointer;
}

.step-card:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.step-card.active {
  border-color: var(--primary);
  background: #f2f8ee;
}

.step-order {
  font-size: 12px;
  color: var(--text-sub);
}

.step-template {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
}

.step-name {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-main);
  font-weight: 600;
}

.empty-tip {
  color: var(--text-sub);
  font-size: 12px;
}
</style>
