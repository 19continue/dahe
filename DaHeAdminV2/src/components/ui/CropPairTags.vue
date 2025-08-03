<template>
  <div class="crop-pair-tags">
    <template v-if="visiblePairs.length">
      <el-tag
        v-for="item in visiblePairs"
        :key="item.key"
        size="small"
        :type="tagType"
        effect="plain"
        class="crop-pair-tag"
      >
        {{ item.label }}
      </el-tag>
      <el-tag
        v-if="hiddenCount > 0"
        size="small"
        type="info"
        effect="plain"
        class="crop-pair-tag"
      >
        +{{ hiddenCount }}
      </el-tag>
    </template>
    <span v-else class="crop-pair-empty">{{ emptyText }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  pairs: {
    type: Array,
    default: () => []
  },
  cropVarietyGroups: {
    type: Array,
    default: () => []
  },
  cropType: {
    type: String,
    default: ''
  },
  cropVariety: {
    type: String,
    default: ''
  },
  emptyText: {
    type: String,
    default: '-'
  },
  tagType: {
    type: String,
    default: 'success'
  },
  max: {
    type: Number,
    default: 0
  }
})

function text(value) {
  return String(value || '').trim()
}

const COMPOUND_SPLIT_PATTERN = /[\/／|｜,，、;；]/g
const COMPOUND_DETECT_PATTERN = /[\/／|｜,，、;；]/

function splitCompound(value) {
  const raw = text(value)
  if (!raw) return []
  if (!COMPOUND_DETECT_PATTERN.test(raw)) return [raw]
  // Keep empty slots so crop/variety indexes can stay aligned.
  return raw.split(COMPOUND_SPLIT_PATTERN).map((item) => String(item || '').trim())
}

function buildLabel(cropType, cropVariety) {
  const crop = text(cropType)
  const variety = text(cropVariety)
  if (crop && variety) return `${crop}·${variety}`
  return crop || variety || ''
}

const normalizedPairs = computed(() => {
  const source = Array.isArray(props.pairs) && props.pairs.length
    ? props.pairs
    : (Array.isArray(props.cropVarietyGroups) && props.cropVarietyGroups.length
      ? props.cropVarietyGroups
      : [{ cropType: props.cropType, cropVariety: props.cropVariety }])
  const out = []
  const used = new Set()
  function pushPair(crop, variety, idx) {
    const label = buildLabel(crop, variety)
    if (!label) return
    const uniqKey = `${text(crop)}__${text(variety)}__${label}`
    if (used.has(uniqKey)) return
    used.add(uniqKey)
    out.push({
      key: `${uniqKey}__${idx}`,
      label
    })
  }
  source.forEach((item, idx) => {
    const crop = text(item && (item.cropType || item.cropName || item.name))
    const variety = text(item && (item.cropVariety || item.varietyName || item.variety))
    const cropParts = splitCompound(crop)
    const varietyParts = splitCompound(variety)
    const expanded = cropParts.length > 1 || varietyParts.length > 1
    if (expanded) {
      const cropCount = cropParts.length
      const varietyCount = varietyParts.length
      const bothMulti = cropCount > 1 && varietyCount > 1
      // Ambiguous pairing: avoid forcing wrong crop-variety mapping.
      if ((bothMulti && cropCount !== varietyCount) || (cropCount > 1 && varietyCount <= 1)) {
        pushPair(crop, variety, `${idx}_raw`)
        return
      }
      const count = Math.max(cropCount, varietyCount)
      for (let i = 0; i < count; i += 1) {
        const partCrop = cropCount === 1 ? (cropParts[0] || '') : (cropParts[i] || '')
        const partVariety = varietyCount === 1 ? (varietyParts[0] || '') : (varietyParts[i] || '')
        pushPair(partCrop, partVariety, `${idx}_${i}`)
      }
      return
    }
    if (crop || variety) {
      pushPair(crop, variety, idx)
      return
    }
    const label = text(item && item.label)
    if (!label) return
    const uniqKey = `label__${label}`
    if (used.has(uniqKey)) return
    used.add(uniqKey)
    out.push({
      key: `${uniqKey}__${idx}`,
      label
    })
  })
  return out
})

const visiblePairs = computed(() => {
  const limit = Number(props.max || 0)
  if (!(limit > 0)) return normalizedPairs.value
  return normalizedPairs.value.slice(0, limit)
})

const hiddenCount = computed(() => {
  const limit = Number(props.max || 0)
  if (!(limit > 0)) return 0
  return Math.max(0, normalizedPairs.value.length - limit)
})
</script>

<style scoped>
.crop-pair-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.crop-pair-tag {
  margin: 0;
}

.crop-pair-empty {
  color: var(--text-sub);
}
</style>
