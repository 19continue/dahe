<template>
  <div ref="boardRef" class="drag-board" :class="{ dragging }">
    <div class="drag-board-head">
      <span>{{ title }}</span>
      <div class="drag-board-head-right">
        <span class="drag-board-tip">
          {{ dragging ? draggingTip : idleTip }}
        </span>
        <div class="sort-drag-turner">
          <button
            type="button"
            class="sort-page-turn-zone"
            data-turn="prev"
            :disabled="page <= 1"
            :class="{ active: activeDirection === 'prev' }"
            @click="emit('turn-page', 'prev')"
          >
            上一页
          </button>
          <button
            type="button"
            class="sort-page-turn-zone"
            data-turn="next"
            :disabled="page >= pageTotal"
            :class="{ active: activeDirection === 'next' }"
            @click="emit('turn-page', 'next')"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
    <div class="sort-drag-card-area">
      <VueDraggable
        :key="draggableRenderKey"
        :model-value="innerRows"
        class="sort-drag-card-list"
        :item-key="itemKey"
        :custom-update="handleCustomUpdate"
        @update:model-value="onDraggableModelUpdate"
        handle=".drag-handle"
        :animation="animation"
        easing="cubic-bezier(0.22, 1, 0.36, 1)"
        :scroll="true"
        :scroll-sensitivity="scrollSensitivity"
        :scroll-speed="scrollSpeed"
        ghost-class="drag-ghost"
        chosen-class="drag-chosen"
        drag-class="drag-dragging"
        :on-move="onMove"
        @start="onStart"
        @end="onEnd"
      >
        <div
          v-for="(row, index) in innerRows"
          :key="resolveRowKey(row, index)"
          :data-sort-id="resolveSortId(row)"
          class="drag-item"
        >
          <slot name="item" :row="row" :index="index" :resolve-index="resolveIndex" />
        </div>
      </VueDraggable>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  itemKey: {
    type: String,
    default: 'id'
  },
  sortIdKey: {
    type: String,
    default: ''
  },
  dragging: {
    type: Boolean,
    default: false
  },
  turnDirection: {
    type: String,
    default: ''
  },
  page: {
    type: Number,
    default: 1
  },
  pageTotal: {
    type: Number,
    default: 1
  },
  title: {
    type: String,
    default: '拖拽排序'
  },
  idleTip: {
    type: String,
    default: '拖动左侧把手排序；分页后可跨页保存完整顺序'
  },
  draggingTip: {
    type: String,
    default: '拖拽中可将鼠标悬停在“上一页/下一页”以自动翻页'
  },
  animation: {
    type: Number,
    default: 220
  },
  scrollSensitivity: {
    type: Number,
    default: 92
  },
  scrollSpeed: {
    type: Number,
    default: 14
  },
  suspendUpdate: {
    type: Boolean,
    default: false
  },
  resolveIndex: {
    type: Function,
    default: () => '-'
  }
})

const emit = defineEmits([
  'update:modelValue',
  'turn-page',
  'drag-start',
  'drag-end',
  'drag-move',
  'drag-update'
])

const innerRows = ref([])
const draggableRenderKey = ref(0)
const boardRef = ref(null)
let flipSeq = 0
const DRAG_FLIP_MAX_ITEMS = 80
const DRAG_FLIP_MIN_GAP_MS = 34
let lastFlipAt = 0

function resolveRowSortId(row) {
  const key = props.sortIdKey || props.itemKey
  const value = row && row[key]
  if (value === null || value === undefined) return ''
  return String(value)
}

function sameRowOrder(a, b) {
  const left = Array.isArray(a) ? a : []
  const right = Array.isArray(b) ? b : []
  if (left.length !== right.length) return false
  for (let i = 0; i < left.length; i += 1) {
    if (resolveRowSortId(left[i]) !== resolveRowSortId(right[i])) return false
  }
  return true
}

function getDragItemElements() {
  const root = boardRef.value
  if (!root || typeof root.querySelectorAll !== 'function') return []
  return Array.from(root.querySelectorAll('.sort-drag-card-list .drag-item'))
}

function captureRectsBySortId() {
  const map = new Map()
  getDragItemElements().forEach((el) => {
    const key = String((el.dataset && el.dataset.sortId) || '')
    if (!key) return
    map.set(key, el.getBoundingClientRect())
  })
  return map
}

async function applyModelRows(rows, animate = false) {
  const nextRows = Array.isArray(rows) ? rows.slice() : []
  if (sameRowOrder(innerRows.value, nextRows)) return
  const beforeRects = animate ? captureRectsBySortId() : null
  innerRows.value = nextRows
  if (!animate || !beforeRects || !beforeRects.size) return
  const seq = ++flipSeq
  await nextTick()
  if (seq !== flipSeq) return
  getDragItemElements().forEach((el) => {
    const key = String((el.dataset && el.dataset.sortId) || '')
    if (!key) return
    const before = beforeRects.get(key)
    if (!before) return
    const after = el.getBoundingClientRect()
    const dx = before.left - after.left
    const dy = before.top - after.top
    if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return
    el.style.transition = 'none'
    el.style.transform = `translate(${dx}px, ${dy}px)`
    // Force reflow so the transition starts from translated position.
    el.getBoundingClientRect()
    el.style.transition = 'transform 180ms cubic-bezier(0.22, 1, 0.36, 1)'
    el.style.transform = 'translate(0, 0)'
    const cleanup = () => {
      el.style.transition = ''
      el.removeEventListener('transitionend', cleanup)
    }
    el.addEventListener('transitionend', cleanup, { once: true })
  })
}

watch(
  () => props.modelValue,
  (rows) => {
    const nextRows = Array.isArray(rows) ? rows : []
    const now = Date.now()
    const canFlip =
      Boolean(props.dragging && props.suspendUpdate) &&
      nextRows.length > 0 &&
      nextRows.length <= DRAG_FLIP_MAX_ITEMS &&
      now - lastFlipAt >= DRAG_FLIP_MIN_GAP_MS
    if (canFlip) {
      lastFlipAt = now
    }
    applyModelRows(nextRows, canFlip)
  },
  { immediate: true }
)

watch(
  () => props.dragging,
  (dragging, prevDragging) => {
    if (!dragging) {
      lastFlipAt = 0
    }
    // Recreate Sortable instance after each drag cycle to clear stale DOM artifacts.
    if (prevDragging) {
      draggableRenderKey.value += 1
    }
  }
)

const activeDirection = computed(() => (props.dragging ? String(props.turnDirection || '') : ''))

function resolveRowKey(row, index) {
  const key = props.itemKey
  const value = row && row[key]
  if (value === null || value === undefined || value === '') return `row-${index}`
  return value
}

function resolveSortId(row) {
  const key = props.sortIdKey || props.itemKey
  const value = row && row[key]
  if (value === null || value === undefined) return ''
  return String(value)
}

function handleCustomUpdate(event) {
  emit('drag-update', event)
  if (props.suspendUpdate) return
  const list = Array.isArray(innerRows.value) ? [...innerRows.value] : []
  const oldIndex = Number(event && event.oldDraggableIndex)
  const newIndex = Number(event && event.newDraggableIndex)
  if (!Number.isInteger(oldIndex) || !Number.isInteger(newIndex)) return
  if (oldIndex < 0 || oldIndex >= list.length) return
  if (newIndex < 0 || newIndex >= list.length) return
  if (oldIndex === newIndex) return
  const moved = list.splice(oldIndex, 1)[0]
  list.splice(newIndex, 0, moved)
  innerRows.value = list
  emit('update:modelValue', list)
}

function onDraggableModelUpdate() {
  // Ignore VueDraggable internal auto sync to avoid cross-page preview polluting source data.
}

function onMove(evt, originalEvent) {
  emit('drag-move', evt, originalEvent)
  // Cross-page preview is driven by parent-provided model rows.
  // Block Sortable's own in-list reordering in this phase to avoid drift.
  if (props.suspendUpdate) return false
  return true
}

function applyCustomDragPreview(event) {
  const nativeEvent = event && event.originalEvent
  const dataTransfer = nativeEvent && nativeEvent.dataTransfer
  const source = event && event.item
  if (!dataTransfer || typeof dataTransfer.setDragImage !== 'function' || !source) return
  const rect = source.getBoundingClientRect()
  const preview = source.cloneNode(true)
  preview.classList.add('drag-preview')
  preview.style.width = `${Math.max(220, Math.round(rect.width))}px`
  preview.style.maxWidth = 'min(640px, 86vw)'
  preview.style.position = 'fixed'
  preview.style.left = '-3000px'
  preview.style.top = '-3000px'
  preview.style.pointerEvents = 'none'
  preview.style.zIndex = '2147483647'
  document.body.appendChild(preview)
  const offsetX = Math.max(14, Math.min(36, Math.round(rect.width * 0.18)))
  const offsetY = Math.max(14, Math.min(28, Math.round(rect.height * 0.38)))
  dataTransfer.setDragImage(preview, offsetX, offsetY)
  requestAnimationFrame(() => {
    preview.remove()
  })
}

function onStart(event) {
  applyCustomDragPreview(event)
  emit('drag-start', event)
}

function onEnd(event) {
  emit('drag-end', event)
}
</script>

<style scoped>
.drag-board {
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px;
  background: var(--bg-soft);
}

.drag-board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  gap: 10px;
  flex-wrap: wrap;
  position: sticky;
  top: 0;
  z-index: 5;
  padding-bottom: 8px;
  background: linear-gradient(180deg, var(--bg-soft) 78%, rgba(255, 255, 255, 0));
  backdrop-filter: blur(1.5px);
}

.drag-board-head-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.drag-board-tip {
  color: var(--text-sub);
}

.sort-drag-turner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.sort-page-turn-zone {
  border: 1px solid var(--border);
  border-radius: 999px;
  min-height: 26px;
  padding: 0 10px;
  background: var(--bg-panel);
  color: var(--text-sub);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.sort-page-turn-zone:hover:not(:disabled),
.sort-page-turn-zone.active {
  border-color: rgba(22, 103, 183, 0.5);
  color: var(--primary);
  background: var(--primary-soft);
}

.sort-page-turn-zone:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.sort-drag-card-area {
  min-height: 12px;
}

.sort-drag-card-list {
  min-height: 8px;
}

.drag-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 64px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg-panel);
  padding: 10px;
  margin-bottom: 8px;
  transition: border-color 0.22s ease, box-shadow 0.22s ease;
}

.drag-item:last-child {
  margin-bottom: 0;
}

.drag-item:hover {
  border-color: rgba(22, 103, 183, 0.48);
  box-shadow: 0 10px 20px rgba(15, 79, 145, 0.12);
}

.drag-board.dragging .drag-item {
  cursor: grabbing;
}

.drag-board.dragging .drag-item:hover {
  border-color: var(--border);
  box-shadow: none;
}

.drag-preview {
  border: 2px solid rgba(22, 103, 183, 0.92) !important;
  border-radius: 10px !important;
  box-shadow: 0 16px 30px rgba(15, 79, 145, 0.3) !important;
  background: rgba(250, 253, 255, 0.98) !important;
  opacity: 0.98 !important;
}

.drag-ghost {
  opacity: 0.24 !important;
  border-style: dashed !important;
  border-width: 2px !important;
  border-color: rgba(22, 103, 183, 0.54) !important;
  background: var(--primary-soft);
  box-shadow: none !important;
}

.drag-chosen {
  border-color: var(--primary) !important;
  box-shadow: 0 12px 24px rgba(15, 79, 145, 0.18);
}

.drag-dragging {
  cursor: grabbing !important;
  box-shadow: 0 14px 26px rgba(15, 79, 145, 0.22) !important;
}

</style>
