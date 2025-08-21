<template>
  <view class="dh-dropdown-panel" :class="{ 'is-elder': elderMode }">
    <scroll-view
      scroll-y
      class="dh-dropdown-panel__scroll"
      :style="scrollInlineStyle"
      :show-scrollbar="false"
      @scroll="handleScroll"
      @scrolltolower="handleScrollToLower"
    >
      <view class="dh-dropdown-panel__content">
        <view
          v-for="(item, index) in visibleOptions"
          :key="optionKey(item, index)"
          class="dh-dropdown-panel__option"
          :class="{
            'is-active': isActive(item),
            'is-disabled': !!item.disabled
          }"
          @tap="handleSelect(item)"
        >
          <text class="dh-dropdown-panel__label">{{ item.label }}</text>
          <t-icon v-if="isActive(item)" class="dh-dropdown-panel__check" name="check" :size="elderMode ? '40rpx' : '34rpx'" />
        </view>

        <view v-if="!normalizedOptions.length" class="dh-dropdown-panel__empty">
          <text>{{ emptyText }}</text>
        </view>

        <view v-if="normalizedOptions.length" class="dh-dropdown-panel__tail-space" :class="{ 'has-hint': showDownHint }" />
      </view>
    </scroll-view>

    <view v-if="showDownHint" class="dh-dropdown-panel__hint">
      <view class="dh-dropdown-panel__hint-chip">
        <t-icon name="chevron-down" :size="elderMode ? '34rpx' : '28rpx'" />
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'DropdownOptionPanel',
  props: {
    options: {
      type: Array,
      default: () => []
    },
    value: {
      type: [String, Number],
      default: ''
    },
    elderMode: {
      type: Boolean,
      default: false
    },
    emptyText: {
      type: String,
      default: '暂无可选项'
    },
    maxHeight: {
      type: Number,
      default: 0
    },
    maxVisibleCount: {
      type: Number,
      default: 5
    }
  },
  data() {
    return {
      isAtBottom: false,
      lastScrollTop: 0,
      contentOverflow: false,
      renderCount: 0
    }
  },
  computed: {
    normalizedOptions() {
      return (Array.isArray(this.options) ? this.options : []).map((item) => ({
        value: item && Object.prototype.hasOwnProperty.call(item, 'value') ? item.value : '',
        label: String((item && item.label) || '').trim(),
        disabled: !!(item && item.disabled)
      }))
    },
    renderBatchSize() {
      return this.elderMode ? 28 : 36
    },
    visibleOptions() {
      return this.normalizedOptions.slice(0, this.renderCount)
    },
    hasMoreOptions() {
      return this.renderCount < this.normalizedOptions.length
    },
    estimatedOverflow() {
      return this.normalizedOptions.length > this.maxVisibleCount || this.hasMoreOptions
    },
    showDownHint() {
      return (this.contentOverflow || this.estimatedOverflow) && !this.isAtBottom
    },
    scrollInlineStyle() {
      const fallback = this.elderMode ? '600rpx' : '520rpx'
      const height = Number(this.maxHeight || 0)
      if (height > 0) {
        return `max-height:${height}px;`
      }
      return `max-height:${fallback};`
    }
  },
  watch: {
    options: {
      handler() {
        this.resetRenderWindow()
        this.resetScrollState()
        this.refreshMetrics()
      },
      immediate: true
    },
    elderMode() {
      this.scheduleMeasure()
    },
    maxHeight() {
      this.refreshMetrics()
    }
  },
  mounted() {
    this.refreshMetrics()
  },
  methods: {
    optionKey(item, index) {
      const value = item && Object.prototype.hasOwnProperty.call(item, 'value') ? item.value : ''
      return `${String(value)}-${index}`
    },
    isActive(item) {
      return String(item && item.value) === String(this.value)
    },
    handleSelect(item) {
      if (!item || item.disabled) return
      this.$emit('select', item)
    },
    resetRenderWindow() {
      const total = this.normalizedOptions.length
      this.renderCount = Math.min(total, this.renderBatchSize)
    },
    appendRenderWindow() {
      if (!this.hasMoreOptions) return false
      this.renderCount = Math.min(this.normalizedOptions.length, this.renderCount + this.renderBatchSize)
      return true
    },
    resetScrollState() {
      this.lastScrollTop = 0
      this.isAtBottom = !(this.contentOverflow || this.estimatedOverflow)
    },
    scheduleMeasure(delay = 0) {
      setTimeout(() => {
        const query = uni.createSelectorQuery().in(this.$scope || this)
        query.select('.dh-dropdown-panel__scroll').boundingClientRect()
        query.select('.dh-dropdown-panel__content').boundingClientRect()
        query.exec((result) => {
          const scrollRect = Array.isArray(result) ? result[0] : null
          const contentRect = Array.isArray(result) ? result[1] : null
          if (!scrollRect || !contentRect) return
          const overflow = Number(contentRect.height || 0) > Number(scrollRect.height || 0) + 2
          this.contentOverflow = overflow
          if (!overflow && this.hasMoreOptions) {
            const expanded = this.appendRenderWindow()
            if (expanded) {
              this.scheduleMeasure(24)
              return
            }
          }
          this.isAtBottom = !overflow && !this.hasMoreOptions
        })
      }, delay)
    },
    refreshMetrics() {
      this.resetScrollState()
      this.scheduleMeasure(0)
      this.scheduleMeasure(40)
      this.scheduleMeasure(120)
    },
    handleScroll(event) {
      if (!this.contentOverflow && !this.hasMoreOptions) {
        this.isAtBottom = true
        return
      }
      const detail = (event && event.detail) || {}
      const scrollTop = Number(detail.scrollTop || 0)
      const scrollHeight = Number(detail.scrollHeight || 0)
      const clientHeight = Number(detail.clientHeight || detail.height || 0)
      const remain = scrollHeight > 0 && clientHeight > 0 ? scrollHeight - (scrollTop + clientHeight) : Number.MAX_SAFE_INTEGER
      if (remain <= 120 && this.hasMoreOptions) {
        const expanded = this.appendRenderWindow()
        if (expanded) {
          this.scheduleMeasure(24)
        }
      }
      if (scrollHeight > 0 && clientHeight > 0) {
        this.isAtBottom = remain <= 8 && !this.hasMoreOptions
      }
      this.lastScrollTop = scrollTop
    },
    handleScrollToLower() {
      if (this.hasMoreOptions) {
        const expanded = this.appendRenderWindow()
        if (expanded) {
          this.scheduleMeasure(24)
          return
        }
      }
      this.isAtBottom = true
    }
  }
}
</script>

<style lang="scss">
.dh-dropdown-panel {
  position: relative;
  width: 100%;
  margin: 0;
  background: #ffffff;
  border-radius: 18rpx;
  overflow: hidden;
  box-sizing: border-box;
}

.dh-dropdown-panel__scroll {
  max-height: 520rpx;
}

.dh-dropdown-panel__content {
  min-height: 100%;
}

.dh-dropdown-panel__option {
  min-height: 92rpx;
  padding: 0 28rpx 0 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  background: #ffffff;
  border-bottom: 1rpx solid #eef2ea;
  box-sizing: border-box;
}

.dh-dropdown-panel__option:last-child {
  border-bottom: none;
}

.dh-dropdown-panel__option.is-active {
  background: #f3f8ef;
}

.dh-dropdown-panel__option.is-disabled {
  opacity: 0.48;
}

.dh-dropdown-panel__label {
  flex: 1;
  min-width: 0;
  font-size: 27rpx;
  line-height: 1.5;
  color: #253221;
}

.dh-dropdown-panel__check {
  flex-shrink: 0;
  color: #5f8748;
}

.dh-dropdown-panel__empty {
  min-height: 160rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #8a9485;
  text-align: center;
}

.dh-dropdown-panel__tail-space {
  height: 20rpx;
  flex-shrink: 0;
}

.dh-dropdown-panel__tail-space.has-hint {
  height: 72rpx;
}

.dh-dropdown-panel__hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16rpx;
  display: flex;
  justify-content: center;
  pointer-events: none;
}

.dh-dropdown-panel__hint-chip {
  min-width: 56rpx;
  height: 42rpx;
  padding: 0 12rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #567947;
  background: rgba(244, 249, 240, 0.94);
  border: 1rpx solid rgba(115, 152, 93, 0.22);
  border-radius: 18rpx;
  box-shadow: 0 8rpx 16rpx rgba(42, 66, 35, 0.08);
}

.is-elder {
  .dh-dropdown-panel {
    border-radius: 20rpx;
  }

  .dh-dropdown-panel__scroll {
    max-height: 600rpx;
  }

  .dh-dropdown-panel__option {
    min-height: 108rpx;
    padding: 0 32rpx 0 36rpx;
  }

  .dh-dropdown-panel__label {
    font-size: 32rpx;
  }

  .dh-dropdown-panel__empty {
    font-size: 28rpx;
  }

  .dh-dropdown-panel__hint-chip {
    height: 48rpx;
    min-width: 64rpx;
  }

  .dh-dropdown-panel__tail-space {
    height: 24rpx;
  }

  .dh-dropdown-panel__tail-space.has-hint {
    height: 84rpx;
  }
}
</style>
