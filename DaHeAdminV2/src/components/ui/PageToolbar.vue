<template>
  <section class="page-toolbar">
    <div class="page-toolbar-head">
      <div class="page-toolbar-title-wrap">
        <h2 class="page-toolbar-title">{{ title }}</h2>
        <p v-if="subtitle" class="page-toolbar-subtitle">{{ subtitle }}</p>
      </div>

      <div class="page-toolbar-right">
        <slot name="head-actions" />
        <el-button
          v-if="collapsible && hasBody"
          class="page-toolbar-toggle"
          text
          @click="expanded = !expanded"
        >
          {{ expanded ? '收起筛选' : '展开筛选' }}
        </el-button>
      </div>
    </div>

    <transition name="toolbar-expand">
      <div v-show="!collapsible || expanded" v-if="hasBody" class="page-toolbar-body">
        <slot />
      </div>
    </transition>

    <div v-if="collapsible && !expanded && normalizedSummary.length" class="page-toolbar-summary">
      <span v-for="item in normalizedSummary" :key="item" class="page-toolbar-summary-item">
        {{ item }}
      </span>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, useSlots } from 'vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: ''
  },
  collapsible: {
    type: Boolean,
    default: false
  },
  defaultExpanded: {
    type: Boolean,
    default: true
  },
  summary: {
    type: [Array, String],
    default: () => []
  }
})

const slots = useSlots()
const expanded = ref(props.defaultExpanded)

const hasBody = computed(() => Boolean(slots.default))
const normalizedSummary = computed(() => {
  if (Array.isArray(props.summary)) {
    return props.summary.map((x) => String(x || '').trim()).filter(Boolean)
  }
  const text = String(props.summary || '').trim()
  return text ? [text] : []
})
</script>

<style scoped>
.page-toolbar {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.92));
  box-shadow: 0 4px 14px rgba(16, 42, 67, 0.05);
}

.page-toolbar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-toolbar-title-wrap {
  min-width: 0;
}

.page-toolbar-title {
  margin: 0;
  font-size: 19px;
  line-height: 1.2;
  letter-spacing: 0.1px;
}

.page-toolbar-subtitle {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}

.page-toolbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.page-toolbar-toggle {
  padding-left: 8px;
  padding-right: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text-sub);
  background: rgba(255, 255, 255, 0.75);
}

.page-toolbar-body {
  margin-top: 10px;
}

.page-toolbar-summary {
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.page-toolbar-summary-item {
  font-size: 12px;
  color: var(--text-sub);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 2px 9px;
  background: #fff;
}

.toolbar-expand-enter-active,
.toolbar-expand-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease, max-height 0.22s ease;
  overflow: hidden;
  max-height: 220px;
}

.toolbar-expand-enter-from,
.toolbar-expand-leave-to {
  opacity: 0;
  transform: translateY(-4px);
  max-height: 0;
}

@media (max-width: 768px) {
  .page-toolbar {
    padding: 10px;
  }

  .page-toolbar-head {
    flex-direction: column;
    align-items: stretch;
  }

  .page-toolbar-right {
    justify-content: flex-start;
  }
}
</style>


