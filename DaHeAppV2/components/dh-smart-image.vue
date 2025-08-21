<template>
  <view class="dh-smart-image" :class="stateClass">
    <image
      v-if="showPreviewLayer"
      class="dh-smart-image__preview"
      :src="previewDisplaySrc"
      :mode="mode"
      :lazy-load="lazyLoad"
    />

    <image
      v-if="shouldRequestImage && hasSrc"
      class="dh-smart-image__img"
      :src="normalizedSrc"
      :mode="mode"
      :lazy-load="lazyLoad"
      :show-menu-by-longpress="showMenuByLongpress"
      @load="handleLoad"
      @error="handleError"
    />

    <view v-if="showSkeleton" class="dh-smart-image__skeleton">
      <view class="dh-smart-image__skeleton-base"></view>
      <view class="dh-smart-image__skeleton-shimmer"></view>
      <view class="dh-smart-image__skeleton-hint">
        <view class="dh-smart-image__loading-text">
          <text
            v-for="(char, idx) in loadingChars"
            :key="`loading-char-${idx}`"
            class="dh-smart-image__loading-char"
            :style="{ animationDelay: `${idx * 90}ms` }"
          >
            {{ char }}
          </text>
        </view>
      </view>
    </view>

    <view v-else-if="showFallback" class="dh-smart-image__fallback">
      <text class="dh-smart-image__fallback-text">{{ fallbackText }}</text>
    </view>
  </view>
</template>

<script>
const IMAGE_MEMORY_CACHE = new Map()
const IMAGE_MEMORY_CACHE_MAX = 600

function normalizeCacheKey(url) {
  return String(url || '').trim()
}

function isImageCachedInMemory(url, ttlMs) {
  const key = normalizeCacheKey(url)
  if (!key) return false
  const cachedAt = Number(IMAGE_MEMORY_CACHE.get(key))
  if (!Number.isFinite(cachedAt)) return false
  const ttl = Math.max(0, Number(ttlMs) || 0)
  if (ttl <= 0) return true
  const valid = Date.now() - cachedAt <= ttl
  if (!valid) {
    IMAGE_MEMORY_CACHE.delete(key)
  }
  return valid
}

function markImageCachedInMemory(url) {
  const key = normalizeCacheKey(url)
  if (!key) return
  if (IMAGE_MEMORY_CACHE.has(key)) {
    IMAGE_MEMORY_CACHE.delete(key)
  }
  IMAGE_MEMORY_CACHE.set(key, Date.now())
  while (IMAGE_MEMORY_CACHE.size > IMAGE_MEMORY_CACHE_MAX) {
    const oldestKey = IMAGE_MEMORY_CACHE.keys().next().value
    if (!oldestKey) break
    IMAGE_MEMORY_CACHE.delete(oldestKey)
  }
}

export default {
  name: 'DhSmartImage',
  props: {
    src: {
      type: String,
      default: ''
    },
    mode: {
      type: String,
      default: 'aspectFill'
    },
    lazyLoad: {
      type: Boolean,
      default: true
    },
    onDemand: {
      type: Boolean,
      default: false
    },
    rootSelector: {
      type: String,
      default: ''
    },
    preloadOffset: {
      type: Number,
      default: 180
    },
    enableMemoryCache: {
      type: Boolean,
      default: true
    },
    cacheTtlMs: {
      type: Number,
      default: 30 * 60 * 1000
    },
    cachedTransitionMs: {
      type: Number,
      default: 320
    },
    previewSrc: {
      type: String,
      default: ''
    },
    loadingText: {
      type: String,
      default: '加载中...'
    },
    emptyText: {
      type: String,
      default: '暂无图片'
    },
    errorText: {
      type: String,
      default: '图片加载失败'
    },
    showMenuByLongpress: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      normalizedSrc: '',
      normalizedPreviewSrc: '',
      loadState: 'empty',
      inViewport: true,
      pendingCachedLoad: false,
      cachedTransitionActive: false,
      loadingTimeoutId: null,
      loadFinishDelayId: null,
      imageObserver: null,
      observeFallbackId: null
    }
  },
  computed: {
    hasSrc() {
      return !!this.normalizedSrc
    },
    hasPreview() {
      return !!this.normalizedPreviewSrc
    },
    previewDisplaySrc() {
      return this.hasPreview ? this.normalizedPreviewSrc : this.normalizedSrc
    },
    shouldRequestImage() {
      return this.hasSrc && (!this.onDemand || this.inViewport)
    },
    showPreviewLayer() {
      return this.shouldRequestImage && this.hasPreview && (this.loadState === 'loading' || this.loadState === 'coarse')
    },
    showSkeleton() {
      return this.shouldRequestImage && this.loadState === 'loading'
    },
    showFallback() {
      return this.loadState === 'empty' || this.loadState === 'error'
    },
    fallbackText() {
      if (this.loadState === 'error') {
        return this.errorText
      }
      return this.emptyText
    },
    stateClass() {
      return [
        `state-${this.loadState}`,
        this.cachedTransitionActive ? 'cached-transition' : ''
      ]
    },
    loadingChars() {
      const text = String(this.loadingText || '加载中').trim() || '加载中'
      return text.split('')
    }
  },
  watch: {
    src: {
      immediate: true,
      handler(nextValue) {
        this.resetSource(nextValue)
      }
    },
    onDemand(nextValue) {
      if (!nextValue) {
        this.inViewport = true
        this.disconnectObserver()
        if (this.hasSrc && this.loadState === 'idle') {
          this.activateImageLoad()
        }
        return
      }
      if (this.loadState === 'loaded' || this.loadState === 'coarse' || this.loadState === 'loading') {
        return
      }
      this.inViewport = false
      this.disconnectObserver()
      this.$nextTick(() => {
        if (this.onDemand && this.hasSrc && this.loadState === 'idle' && !this.inViewport) {
          this.ensureObserver()
        }
      })
    },
    previewSrc(nextValue) {
      this.normalizedPreviewSrc = this.resolvePreviewSource(nextValue, this.normalizedSrc)
    }
  },
  methods: {
    clearLoadingTimeout() {
      if (!this.loadingTimeoutId) return
      clearTimeout(this.loadingTimeoutId)
      this.loadingTimeoutId = null
    },
    clearLoadFinishDelay() {
      if (!this.loadFinishDelayId) return
      clearTimeout(this.loadFinishDelayId)
      this.loadFinishDelayId = null
    },
    clearObserveFallback() {
      if (!this.observeFallbackId) return
      clearTimeout(this.observeFallbackId)
      this.observeFallbackId = null
    },
    scheduleLoadingTimeout() {
      this.clearLoadingTimeout()
      if (!this.hasSrc || (this.loadState !== 'loading' && this.loadState !== 'coarse')) return
      this.loadingTimeoutId = setTimeout(() => {
        if (this.loadState === 'loading' || this.loadState === 'coarse') {
          this.loadState = 'error'
        }
      }, 12000)
    },
    resolvePreviewSource(previewValue, mainValue) {
      const preview = String(previewValue || '').trim()
      if (preview) return preview
      return String(mainValue || '').trim()
    },
    isImageCached(url) {
      if (!this.enableMemoryCache) return false
      return isImageCachedInMemory(url, this.cacheTtlMs)
    },
    resetSource(nextValue) {
      const value = String(nextValue || '').trim()
      this.normalizedSrc = value
      this.normalizedPreviewSrc = this.resolvePreviewSource(this.previewSrc, value)
      this.clearLoadFinishDelay()
      this.clearLoadingTimeout()
      this.pendingCachedLoad = false
      this.cachedTransitionActive = false
      if (!value) {
        this.loadState = 'empty'
        this.inViewport = true
        this.disconnectObserver()
        this.clearObserveFallback()
        return
      }
      const cached = this.isImageCached(value)
      if (this.onDemand) {
        this.inViewport = false
        this.loadState = 'idle'
        this.pendingCachedLoad = cached
        this.disconnectObserver()
        this.clearObserveFallback()
        this.$nextTick(() => {
          if (this.onDemand && this.hasSrc && this.loadState === 'idle' && !this.inViewport) {
            this.ensureObserver()
          }
        })
      } else {
        this.inViewport = true
        if (cached) {
          this.cachedTransitionActive = true
          this.loadState = 'coarse'
          this.$nextTick(() => {
            if (this.hasSrc && this.loadState === 'coarse') {
              this.scheduleLoadedTransition(this.cachedTransitionMs)
            }
          })
        } else {
          this.loadState = 'loading'
          this.scheduleLoadingTimeout()
        }
      }
    },
    ensureObserver() {
      if (!this.onDemand || this.inViewport || !this.hasSrc) return
      if (this.imageObserver) return
      this.clearObserveFallback()
      const canObserve = typeof uni !== 'undefined' && typeof uni.createIntersectionObserver === 'function'
      if (!canObserve) {
        this.inViewport = true
        this.activateImageLoad()
        return
      }
      try {
        const componentScope = this.$scope || this
        const observer = uni.createIntersectionObserver(componentScope, {
          thresholds: [0, 0.01],
          nativeMode: true
        })
        const safeOffset = Math.max(0, Number(this.preloadOffset) || 0)
        // 在组件内部使用 relativeTo 外部选择器在部分端不稳定，这里统一使用 viewport，避免失效。
        observer.relativeToViewport({
          top: safeOffset,
          bottom: safeOffset
        })
        observer.observe('.dh-smart-image', (result) => {
          const ratio = Number(result && result.intersectionRatio)
          const height = Number(result && result.intersectionRect && result.intersectionRect.height)
          if (ratio > 0 || height > 0) {
            this.inViewport = true
            this.clearObserveFallback()
            this.disconnectObserver()
            this.activateImageLoad()
          }
        })
        this.imageObserver = observer
        // 防止观察器在某些端未触发导致永远不加载
        this.observeFallbackId = setTimeout(() => {
          if (this.loadState === 'idle' && this.hasSrc) {
            this.inViewport = true
            this.disconnectObserver()
            this.activateImageLoad()
          }
        }, 1500)
      } catch (e) {
        this.inViewport = true
        this.activateImageLoad()
      }
    },
    disconnectObserver() {
      if (!this.imageObserver) return
      try {
        this.imageObserver.disconnect()
      } catch (e) {}
      this.imageObserver = null
      this.clearObserveFallback()
    },
    activateImageLoad() {
      if (!this.hasSrc) return
      if (this.loadState === 'loading' || this.loadState === 'coarse' || this.loadState === 'loaded') return
      const cacheHit = this.pendingCachedLoad || this.isImageCached(this.normalizedSrc)
      this.pendingCachedLoad = false
      if (cacheHit) {
        this.cachedTransitionActive = true
        this.loadState = 'coarse'
        this.$nextTick(() => {
          if (this.hasSrc && this.loadState === 'coarse') {
            this.scheduleLoadedTransition(this.cachedTransitionMs)
          }
        })
        return
      }
      this.loadState = 'loading'
      this.scheduleLoadingTimeout()
    },
    scheduleLoadedTransition(delayMs, event) {
      this.clearLoadFinishDelay()
      const safeDelay = Math.max(0, Number(delayMs) || 0)
      this.loadFinishDelayId = setTimeout(() => {
        this.loadState = 'loaded'
        this.cachedTransitionActive = false
        this.$emit('load', event)
        this.loadFinishDelayId = null
      }, safeDelay)
    },
    handleLoad(event) {
      if (!this.hasSrc) return
      markImageCachedInMemory(this.normalizedSrc)
      if (this.hasPreview) {
        markImageCachedInMemory(this.normalizedPreviewSrc)
      }
      this.clearLoadingTimeout()
      this.clearLoadFinishDelay()

      // 资源已到达时立即结束 loading，先给用户一个模糊响应，再快速过渡到清晰
      this.cachedTransitionActive = false
      this.loadState = 'coarse'
      this.scheduleLoadingTimeout()
      this.scheduleLoadedTransition(90, event)
    },
    handleError(event) {
      if (!this.hasSrc) return
      this.clearLoadingTimeout()
      this.clearLoadFinishDelay()
      this.cachedTransitionActive = false
      this.loadState = 'error'
      this.$emit('error', event)
    }
  },
  mounted() {
    if (this.onDemand && this.hasSrc && !this.inViewport) {
      this.ensureObserver()
    }
  },
  beforeDestroy() {
    this.clearLoadingTimeout()
    this.clearLoadFinishDelay()
    this.clearObserveFallback()
    this.disconnectObserver()
  }
}
</script>

<style lang="scss">
.dh-smart-image {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #edf2e9;
}

.dh-smart-image__preview,
.dh-smart-image__img {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: block;
}

.dh-smart-image__preview {
  opacity: 0;
  transform: scale(1.08);
  filter: blur(18rpx) saturate(0.9);
  transition: opacity 220ms ease, transform 360ms ease, filter 360ms ease;
}

.dh-smart-image__img {
  opacity: 0;
  transform: scale(1.04);
  filter: blur(14rpx) saturate(0.92);
  transition: opacity 260ms ease, transform 360ms ease, filter 420ms ease;
}

.dh-smart-image.cached-transition .dh-smart-image__img {
  transition: opacity 340ms ease, transform 420ms ease, filter 520ms ease;
}

.dh-smart-image.state-loading .dh-smart-image__preview {
  opacity: 0.9;
}

.dh-smart-image.state-coarse .dh-smart-image__preview {
  opacity: 0.3;
  transform: scale(1.03);
  filter: blur(10rpx) saturate(0.94);
}

.dh-smart-image.state-coarse .dh-smart-image__img {
  opacity: 0.78;
  transform: scale(1.015);
  filter: blur(8rpx) saturate(0.96);
}

.dh-smart-image.state-coarse.cached-transition .dh-smart-image__img {
  opacity: 0.72;
  transform: scale(1.03);
  filter: blur(16rpx) saturate(0.9);
}

.dh-smart-image.state-loaded .dh-smart-image__preview {
  opacity: 0;
}

.dh-smart-image.state-loaded .dh-smart-image__img {
  opacity: 1;
  transform: scale(1);
  filter: blur(0) saturate(1);
}

.dh-smart-image__skeleton,
.dh-smart-image__fallback {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

.dh-smart-image__skeleton-base {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(180deg, #eef4ea 0%, #e8efe3 100%);
}

.dh-smart-image__skeleton-shimmer {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -150%;
  width: 70%;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.68) 50%, rgba(255, 255, 255, 0) 100%);
  animation: dh-smart-image-shimmer 1.2s linear infinite;
}

.dh-smart-image__skeleton-hint,
.dh-smart-image__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
}

.dh-smart-image__skeleton-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 2;
  transform: translate(-50%, -50%);
}

.dh-smart-image__loading-text {
  display: inline-flex;
  align-items: flex-end;
  gap: 2rpx;
  padding: 8rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.54);
}

.dh-smart-image__loading-char {
  color: #6f9e57;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
  animation: dh-smart-image-char-jump 700ms ease-in-out infinite;
}

.dh-smart-image__fallback-text {
  font-size: 22rpx;
  color: #7f8c77;
}

.dh-smart-image__fallback {
  background: linear-gradient(180deg, #f1f5ed 0%, #edf3e8 100%);
}

@keyframes dh-smart-image-shimmer {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(380%);
  }
}

@keyframes dh-smart-image-char-jump {
  0% {
    transform: translateY(0);
    opacity: 0.72;
  }
  45% {
    transform: translateY(-8rpx);
    opacity: 1;
  }
  100% {
    transform: translateY(0);
    opacity: 0.72;
  }
}
</style>
