<template>
  <view class="dh-filter-dropdown" :class="{ 'is-elder': elderMode, 'is-open': visible, 'is-active': active }">
    <view class="dh-filter-dropdown__trigger" @tap="togglePanel">
      <text class="dh-filter-dropdown__label">{{ label }}</text>
      <t-icon class="dh-filter-dropdown__icon" name="caret-down-small" :size="elderMode ? '34rpx' : '28rpx'" />
    </view>

    <t-overlay
      v-if="visible"
      :visible="visible"
      :z-index="11880"
      background-color="rgba(0, 0, 0, 0)"
      :prevent-scroll-through="true"
      custom-style="overflow: visible;"
      @click="closePanel"
    >
      <view class="dh-filter-dropdown__overlay">
        <view
          v-if="panelReady"
          class="dh-filter-dropdown__panel"
          :style="panelInlineStyle"
          @click.stop
          @tap.stop
        >
          <dropdown-option-panel
            ref="panelRef"
            :options="options"
            :value="value"
            :elder-mode="elderMode"
            :empty-text="emptyText"
            :max-height="layout.maxHeight"
            @select="handleSelect"
          />
        </view>
      </view>
    </t-overlay>
  </view>
</template>

<script>
import DropdownOptionPanel from './dropdown-option-panel.vue'

export default {
  name: 'FilterDropdownItem',
  components: {
    DropdownOptionPanel
  },
  props: {
    label: {
      type: String,
      default: ''
    },
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
    }
  },
  data() {
    return {
      visible: false,
      panelReady: false,
      opening: false,
      layout: {
        left: 0,
        top: 0,
        width: 240,
        maxHeight: 320
      }
    }
  },
  computed: {
    active() {
      return this.visible || (this.value !== '' && this.value !== null && this.value !== undefined)
    },
    panelInlineStyle() {
      return [
        `left:${this.layout.left}px`,
        `top:${this.layout.top}px`,
        `width:${this.layout.width}px`,
        `max-height:${this.layout.maxHeight}px`
      ].join(';')
    }
  },
  watch: {
    elderMode() {
      if (this.visible) this.scheduleLayout()
    },
    options() {
      if (this.visible) this.scheduleLayout()
    }
  },
  methods: {
    togglePanel() {
      if (this.visible) {
        this.closePanel()
        return
      }
      this.openPanel()
    },
    openPanel() {
      if (this.opening) return
      this.opening = true
      this.panelReady = false
      this.$emit('open')
      this.scheduleLayout((ready) => {
        if (!ready) {
          this.opening = false
          this.panelReady = false
          return
        }
        this.visible = true
        this.panelReady = true
        this.opening = false
        this.$nextTick(() => {
          this.refreshPanelMetrics()
        })
      })
    },
    closePanel() {
      if (!this.visible && !this.opening) return
      this.opening = false
      this.panelReady = false
      if (!this.visible) return
      this.visible = false
      this.$emit('close')
    },
    scheduleLayout(callback) {
      this.$nextTick(() => {
        this.syncLayout(callback)
      })
    },
    syncLayout(callback) {
      const query = uni.createSelectorQuery().in(this.$scope || this)
      query.select('.dh-filter-dropdown__trigger').boundingClientRect()
      query.selectViewport().boundingClientRect()
      query.exec((result) => {
        const triggerRect = Array.isArray(result) ? result[0] : null
        const viewportRect = Array.isArray(result) ? result[1] : null
        if (!triggerRect || !viewportRect) {
          if (typeof callback === 'function') callback(false)
          return
        }
        const viewportWidth = Number(viewportRect.width || 375)
        const viewportHeight = Number(viewportRect.height || 667)
        const horizontalGap = 16
        const minWidth = this.elderMode ? 250 : 220
        const preferredWidth = Math.max(Number(triggerRect.width || 0) + 32, minWidth)
        const width = Math.min(viewportWidth - horizontalGap * 2, preferredWidth)
        const left = Math.min(
          Math.max(Number(triggerRect.left || 0), horizontalGap),
          Math.max(horizontalGap, viewportWidth - width - horizontalGap)
        )
        const top = Number(triggerRect.bottom || 0) + 8
        const maxHeight = Math.max(180, viewportHeight - top - 24)
        this.layout = {
          left,
          top,
          width,
          maxHeight
        }
        if (typeof callback === 'function') callback(true)
      })
    },
    handleSelect(option) {
      this.$emit('select', option)
      this.closePanel()
    },
    refreshPanelMetrics() {
      const panel = this.$refs.panelRef
      if (!panel || typeof panel.refreshMetrics !== 'function') return
      panel.refreshMetrics()
    }
  }
}
</script>

<style lang="scss">
.dh-filter-dropdown {
  position: relative;
  flex: 1;
  width: 100%;
  min-width: 0;
}

.dh-filter-dropdown__trigger {
  width: 100%;
  min-height: 88rpx;
  padding: 0 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  color: #2a3526;
  background: #ffffff;
}

.dh-filter-dropdown__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 27rpx;
  line-height: 1.3;
  font-weight: 600;
}

.dh-filter-dropdown__icon {
  flex-shrink: 0;
  color: #6d7a68;
  transition: transform 180ms ease;
}

.dh-filter-dropdown.is-active .dh-filter-dropdown__label,
.dh-filter-dropdown.is-open .dh-filter-dropdown__label {
  color: #5f8748;
}

.dh-filter-dropdown.is-open .dh-filter-dropdown__icon {
  color: #5f8748;
  transform: rotate(180deg);
}

.dh-filter-dropdown__overlay {
  position: relative;
  width: 100%;
  height: 100%;
}

.dh-filter-dropdown__panel {
  position: absolute;
  z-index: 1;
}

.is-elder {
  .dh-filter-dropdown__trigger {
    min-height: 104rpx;
    padding: 0 18rpx;
    gap: 8rpx;
  }

  .dh-filter-dropdown__label {
    font-size: 32rpx;
  }
}
</style>
