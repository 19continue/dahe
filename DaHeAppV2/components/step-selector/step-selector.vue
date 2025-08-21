<template>
  <view class="step-selector" :class="{ 'is-disabled': disabled, 'is-elder': elderMode }">
    <t-cell
      v-if="!hideTrigger"
      class="step-trigger"
      :class="{ disabled: disabled, ready: !disabled }"
      :title="triggerTitle"
      :note="triggerNote"
      :arrow="!disabled"
      @click="openPopup"
    />

    <t-popup
      :visible="popupVisible"
      placement="bottom"
      :show-overlay="true"
      :close-on-overlay-click="true"
      :prevent-scroll-through="true"
      @visible-change="onPopupVisibleChange"
    >
      <view class="step-panel">
        <view class="head-row">
          <text class="title">选择作业步骤</text>
          <view class="close-btn" @tap="closePopup">
            <t-icon name="close" size="40rpx" />
          </view>
        </view>

        <view v-if="segments.length > 1" class="segment-block">
          <text class="segment-label">品种分段</text>
          <scroll-view
            scroll-x
            class="segment-scroll"
            :show-scrollbar="false"
          >
            <view class="segment-row">
              <t-check-tag
                v-for="item in segments"
                :key="item.segmentKey"
                :checked="String(segmentValue || '') === String(item.segmentKey || '')"
                @change="onSegmentCheck(item, $event)"
              >
                {{ segmentName(item) || '阶段' }}
              </t-check-tag>
            </view>
          </scroll-view>
        </view>

        <view v-if="loading" class="loading-wrap">
          <t-skeleton theme="paragraph" animation="flashed" :row-col="[1, 1, 1]" />
        </view>

        <scroll-view
          v-else-if="steps.length"
          scroll-y
          class="step-node-scroll"
          :class="{ 'with-segment': segments.length > 1 }"
          :show-scrollbar="false"
        >
          <view class="step-node-list">
            <view
              v-for="(step, idx) in steps"
              :key="step.id || idx"
              class="step-node-item"
              :class="{ active: isStepChecked(step) }"
              @tap="onStepPick(step)"
            >
              <view class="step-node-axis">
                <view class="step-node-index">{{ idx + 1 }}</view>
                <view v-if="idx < steps.length - 1" class="step-node-line" />
              </view>
              <view class="step-node-card">
                <view class="step-node-head">
                  <text class="step-name">{{ step.stepName || `步骤 ${idx + 1}` }}</text>
                  <t-tag size="small" theme="warning" variant="light" v-if="stepStageLabel(step, idx)">
                    {{ stepStageLabel(step, idx) }}
                  </t-tag>
                </view>
                <text class="step-desc" v-if="step.requirementDesc">{{ step.requirementDesc }}</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <view v-else class="empty-wrap">
          <t-empty description="所关联的流程中没有创建任何步骤" />
        </view>
      </view>
    </t-popup>
  </view>
</template>

<script>
export default {
  name: 'StepSelector',
  props: {
    steps: {
      type: Array,
      default: () => []
    },
    segments: {
      type: Array,
      default: () => []
    },
    segmentValue: {
      type: [String, Number],
      default: ''
    },
    value: {
      type: [String, Number],
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    },
    hideTrigger: {
      type: Boolean,
      default: false
    },
    disabled: {
      type: Boolean,
      default: false
    },
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      popupVisible: false
    }
  },
  computed: {
    selectedStep() {
      if (!Array.isArray(this.steps) || !this.steps.length) return null
      return this.steps.find((step) => String((step && step.id) || '') === String(this.value == null ? '' : this.value)) || null
    },
    triggerTitle() {
      if (this.disabled) return '请先选择作业田块'
      if (!this.selectedStep) return '请选择作业步骤'
      return String(this.selectedStep.stepName || '').trim() || '已选择作业步骤'
    },
    selectedStepSequenceLabel() {
      if (!this.selectedStep) return ''
      const idx = this.steps.findIndex((step) => String((step && step.id) || '') === String((this.selectedStep && this.selectedStep.id) || ''))
      if (idx < 0) return ''
      return `序号 ${idx + 1}`
    },
    triggerNote() {
      if (this.disabled) return '先选择田块'
      if (this.loading) return '步骤加载中...'
      if (!this.selectedStep && this.steps.length) return '请选择'
      if (!this.steps.length) return '所关联的流程中没有创建任何步骤'
      const parts = []
      const stageText = this.stepStageLabel(this.selectedStep, this.steps.findIndex((step) => String((step && step.id) || '') === String((this.selectedStep && this.selectedStep.id) || '')))
      if (stageText) parts.push(stageText)
      if (this.selectedStepSequenceLabel) parts.push(this.selectedStepSequenceLabel)
      return parts.join(' · ')
    }
  },
  methods: {
    openPopup() {
      if (this.disabled) return
      this.popupVisible = true
      this.$emit('popup-visible-change', true)
    },
    closePopup() {
      this.popupVisible = false
      this.$emit('popup-visible-change', false)
    },
    onPopupVisibleChange(context) {
      this.popupVisible = !!(context && context.visible)
      this.$emit('popup-visible-change', this.popupVisible)
    },
    segmentName(item) {
      if (!item) return ''
      const name = this.formatStageLabel(item.segmentName)
      if (name) return name
      return this.formatStageLabel(item.segmentKey)
    },
    stepStageLabel(step, index) {
      const rows = Array.isArray(this.steps) ? this.steps : []
      if (!rows.length) return ''
      let cursor = Number(index)
      if (!Number.isInteger(cursor) || cursor < 0) {
        cursor = rows.findIndex((item) => String((item && item.id) || '') === String((step && step.id) || ''))
      }
      if (cursor < 0) cursor = 0
      for (let i = cursor; i >= 0; i -= 1) {
        const current = rows[i]
        const label = this.formatStageLabel(current && current.growthStage)
        if (label) return label
      }
      return cursor === 0 ? '播种期' : ''
    },
    isStepChecked(step) {
      return String((step && step.id) || '') === String(this.value == null ? '' : this.value)
    },
    formatStageLabel(rawValue) {
      const text = String(rawValue || '').trim()
      if (!text) return ''
      if (/[\u4e00-\u9fa5]/.test(text)) {
        if (/(播|种|芽|苗|移栽)/.test(text)) return '播种期'
        if (/(生长|分蘖|伸长|拔节|抽穗|开花|授粉|结果|坐果|果实|灌浆)/.test(text)) return '生长期'
        if (/(成熟|采收|收获|采后|休耕)/.test(text)) return '收获期'
        return ''
      }
      const key = text.toLowerCase().replace(/[\s_-]/g, '')
      if (/^(seed|sow|sowing|seeding|planting|germination|bud|budding|sprout|emergence|seedling|nursery|nurseryperiod|transplant|transplanting)$/.test(key)) {
        return '播种期'
      }
      if (/^(vegetative|growth|growing|tillering|elongation|jointing|heading|flowering|bloom|blossoming|pollination|fruiting|fruit|fruitset|fruitsetting|fruitdevelopment|filling|grainfilling)$/.test(key)) {
        return '生长期'
      }
      if (/^(maturity|mature|ripening|harvest|harvesting|postharvest|postharvesting|afterharvest|resting)$/.test(key)) {
        return '收获期'
      }
      return ''
    },
    onSegmentCheck(item, context) {
      if (!context || context.checked !== true) return
      this.$emit('segment-change', item)
    },
    onStepPick(step) {
      this.$emit('change', step)
      this.popupVisible = false
      this.$emit('popup-visible-change', false)
    }
  }
}
</script>

<style lang="scss">
.step-selector {
  --step-surface: #f7faf6;
  --step-border: #e3e7df;
  --step-border-active: #73ae52;
  --step-text-main: #1f2a21;
  --step-text-sub: #62705f;
  --step-shadow: none;
  --step-shadow-active: none;
  --td-brand-color: #73ae52;
  --td-brand-color-active: #5e9240;
  --td-brand-color-light: #edf7e7;
  --td-text-color-brand: #73ae52;
  width: 100%;
}

.step-trigger {
  border-radius: 16rpx;
  overflow: hidden;
  border: 1rpx solid var(--step-border);
  background: var(--step-surface);
  box-shadow: var(--step-shadow);
}

.step-trigger.ready {
  border-color: #d6dfcd;
}

.step-trigger.disabled {
  opacity: 0.72;
}

.step-panel {
  position: relative;
  background: #f3f4f6;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  border-top: 1rpx solid var(--step-border);
  padding: 20rpx 18rpx 24rpx;
  height: 76vh;
  max-height: 76vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.head-row {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 56rpx;
  padding-right: 72rpx;
}

.title {
  font-size: 32rpx;
  color: var(--step-text-main);
  font-weight: 700;
  letter-spacing: 0.4rpx;
}

.close-btn {
  position: absolute;
  top: -2rpx;
  right: -2rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--step-text-main);
}

.segment-scroll {
  margin-top: 10rpx;
  white-space: nowrap;
  flex-shrink: 0;
}

.segment-block {
  margin-top: 14rpx;
  padding: 12rpx;
  border-radius: 16rpx;
  border: 1rpx solid #e7eee2;
  background: #f7faf6;
  flex-shrink: 0;
}

.segment-label {
  display: block;
  font-size: 22rpx;
  color: var(--step-text-sub);
  line-height: 1.4;
}

.segment-row {
  display: inline-flex;
  gap: 10rpx;
  align-items: center;
  padding-bottom: 2rpx;
}

.loading-wrap {
  margin-top: 14rpx;
  border-radius: 16rpx;
  background: #ffffff;
  border: 1rpx solid var(--step-border);
  padding: 14rpx 12rpx;
  flex-shrink: 0;
}

.step-node-scroll {
  margin-top: 12rpx;
  flex: 1;
  min-height: 0;
}

.step-node-scroll.with-segment {
  margin-top: 18rpx;
}

.step-node-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-right: 2rpx;
  padding-bottom: 8rpx;
}

.step-node-item {
  display: flex;
  align-items: stretch;
  gap: 12rpx;
}

.step-node-axis {
  width: 42rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.step-node-index {
  width: 34rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: #e7efe2;
  color: var(--step-text-sub);
  border: 1rpx solid var(--step-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21rpx;
  font-weight: 700;
}

.step-node-line {
  width: 2rpx;
  flex: 1;
  margin-top: 6rpx;
  background: #d6dfcd;
}

.step-node-card {
  flex: 1;
  background: #ffffff;
  border: 1rpx solid var(--step-border);
  border-radius: 16rpx;
  padding: 14rpx;
  box-sizing: border-box;
  box-shadow: var(--step-shadow);
}

.step-node-item.active .step-node-card {
  background: #f7faf6;
  border-color: var(--step-border-active);
  box-shadow: var(--step-shadow-active);
}

.step-node-item.active .step-node-index {
  background: #73ae52;
  border-color: #73ae52;
  color: #ffffff;
}

.step-node-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.step-name {
  margin-top: 0;
  display: block;
  font-size: 28rpx;
  color: var(--step-text-main);
  font-weight: 700;
  line-height: 1.4;
  flex: 1;
  min-width: 0;
}

.step-desc {
  margin-top: 8rpx;
  display: block;
  font-size: 22rpx;
  color: var(--step-text-sub);
  line-height: 1.5;
}

.empty-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0 8rpx;
}

.step-selector.is-elder {
  .step-panel {
    padding: 24rpx 22rpx 30rpx;
    height: 80vh;
    max-height: 80vh;
  }

  .head-row {
    min-height: 72rpx;
    padding-right: 90rpx;
  }

  .close-btn {
    width: 72rpx;
    height: 72rpx;
    top: -4rpx;
    right: -4rpx;
  }

  .segment-block,
  .loading-wrap,
  .step-node-card {
    padding: 18rpx;
    border-radius: 20rpx;
  }

  .segment-row {
    gap: 12rpx;
  }

  .step-node-list {
    gap: 16rpx;
  }

  .step-node-item {
    gap: 16rpx;
  }

  .step-node-axis {
    width: 50rpx;
  }

  .step-node-index {
    width: 42rpx;
    height: 42rpx;
  }

  .step-node-line {
    margin-top: 8rpx;
  }

  .title {
    font-size: 36rpx;
  }

  .step-name {
    font-size: 32rpx;
  }

  .segment-label,
  .step-node-index,
  .step-desc {
    font-size: 30rpx;
  }

  .t-tag,
  .t-tag__text {
    font-size: 28rpx !important;
    line-height: 1.4;
  }

  .segment-row .t-tag,
  .segment-row .t-check-tag,
  .segment-row .t-tag__text {
    font-size: 30rpx !important;
  }
}
</style>
