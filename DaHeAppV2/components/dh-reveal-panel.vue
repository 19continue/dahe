<template>
  <view class="dh-reveal-panel" :style="panelStyle">
    <view
      v-if="showContent"
      class="dh-reveal-panel__content"
      :class="{ 'is-visible': !loading }"
    >
      <slot />
    </view>
    <view
      v-if="showSkeleton"
      class="dh-reveal-panel__skeleton"
      :class="{
        'is-overlay': revealing,
        'is-fading': revealing
      }"
    >
      <slot name="skeleton" />
    </view>
  </view>
</template>

<script>
export default {
  name: 'DhRevealPanel',
  props: {
    loading: {
      type: Boolean,
      default: false
    },
    minLoadingMs: {
      type: Number,
      default: 360
    },
    duration: {
      type: Number,
      default: 220
    }
  },
  data() {
    return {
      revealing: false,
      revealTimer: null,
      holdTimer: null,
      holdSkeleton: false,
      loadingStartedAt: 0
    }
  },
  computed: {
    safeMinLoadingMs() {
      const value = Number(this.minLoadingMs)
      if (!Number.isFinite(value) || value < 0) {
        return 360
      }
      return Math.floor(value)
    },
    safeDuration() {
      const value = Number(this.duration)
      if (!Number.isFinite(value) || value <= 0) {
        return 220
      }
      return Math.floor(value)
    },
    panelStyle() {
      return {
        '--dh-reveal-min-loading': `${this.safeMinLoadingMs}ms`,
        '--dh-reveal-duration': `${this.safeDuration}ms`
      }
    },
    showSkeleton() {
      return this.loading || this.holdSkeleton || this.revealing
    },
    showContent() {
      return !this.loading && !this.holdSkeleton
    }
  },
  watch: {
    loading: {
      immediate: true,
      handler(nextValue) {
        if (nextValue) {
          this.clearTimers()
          this.loadingStartedAt = Date.now()
          this.holdSkeleton = false
          this.revealing = false
          return
        }
        this.scheduleReveal()
      }
    }
  },
  beforeDestroy() {
    this.clearTimers()
  },
  methods: {
    scheduleReveal() {
      this.clearTimers()
      const startedAt = Number(this.loadingStartedAt) || 0
      const elapsed = startedAt > 0 ? Date.now() - startedAt : this.safeMinLoadingMs
      const remain = Math.max(this.safeMinLoadingMs - elapsed, 0)
      if (remain > 0) {
        this.holdSkeleton = true
        this.holdTimer = setTimeout(() => {
          this.holdTimer = null
          this.startReveal()
        }, remain)
        return
      }
      this.startReveal()
    },
    startReveal() {
      this.clearHoldTimer()
      this.holdSkeleton = false
      this.$nextTick(() => {
        this.revealing = true
        this.revealTimer = setTimeout(() => {
          this.revealing = false
          this.revealTimer = null
        }, this.safeDuration)
      })
    },
    clearHoldTimer() {
      if (this.holdTimer) {
        clearTimeout(this.holdTimer)
        this.holdTimer = null
      }
    },
    clearRevealTimer() {
      if (this.revealTimer) {
        clearTimeout(this.revealTimer)
        this.revealTimer = null
      }
    },
    clearTimers() {
      this.clearHoldTimer()
      this.clearRevealTimer()
    }
  }
}
</script>

<style lang="scss">
.dh-reveal-panel {
  position: relative;
  width: 100%;
}

.dh-reveal-panel__content {
  opacity: 0;
  transform: translateY(6rpx);
  transition: opacity var(--dh-reveal-duration) ease, transform var(--dh-reveal-duration) ease;
  will-change: opacity, transform;
}

.dh-reveal-panel__content.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.dh-reveal-panel__skeleton {
  width: 100%;
  transition: opacity var(--dh-reveal-duration) ease;
  will-change: opacity;
}

.dh-reveal-panel__skeleton.is-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

.dh-reveal-panel__skeleton.is-fading {
  opacity: 0;
}
</style>
