<template>
  <div class="sort-center-toolbar">
    <div class="sort-center-left">
      <el-radio-group :model-value="mode" size="small" @update:model-value="(val) => emit('update:mode', val)">
        <el-radio-button label="drag">手动拖拽</el-radio-button>
        <el-radio-button label="button">按钮调整</el-radio-button>
      </el-radio-group>
      <el-input
        v-if="showKeyword"
        :model-value="keyword"
        clearable
        :disabled="disableKeywordWhenNotButton && mode !== 'button'"
        :placeholder="keywordPlaceholder"
        style="width: 300px"
        @update:model-value="(val) => emit('update:keyword', val)"
      />
    </div>
    <div class="sort-center-actions">
      <el-button @click="emit('reset')">{{ resetText }}</el-button>
      <el-button v-if="showSave" type="primary" :loading="saveLoading" @click="emit('save')">
        {{ saveText }}
      </el-button>
    </div>
  </div>

  <div class="sort-center-meta">
    <span>当前模式：{{ mode === 'drag' ? '手动拖拽' : '按钮调整' }}</span>
    <span>总条数：{{ total }}</span>
    <span>当前页：第 {{ page }} / {{ pageTotal }} 页</span>
  </div>

  <div ref="bodyRef" class="sort-center-body">
    <slot />
  </div>

  <div class="sort-center-foot">
    <slot name="foot" />
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  mode: {
    type: String,
    default: 'drag'
  },
  keyword: {
    type: String,
    default: ''
  },
  keywordPlaceholder: {
    type: String,
    default: '按钮模式下可搜索'
  },
  disableKeywordWhenNotButton: {
    type: Boolean,
    default: true
  },
  total: {
    type: Number,
    default: 0
  },
  page: {
    type: Number,
    default: 1
  },
  pageTotal: {
    type: Number,
    default: 1
  },
  saveLoading: {
    type: Boolean,
    default: false
  },
  resetText: {
    type: String,
    default: '恢复初始顺序'
  },
  saveText: {
    type: String,
    default: '保存排序'
  },
  showSave: {
    type: Boolean,
    default: true
  },
  showKeyword: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:mode', 'update:keyword', 'reset', 'save'])

const bodyRef = ref(null)

function getBodyEl() {
  return bodyRef.value
}

defineExpose({ getBodyEl })
</script>

<style scoped>
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

.sort-center-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
