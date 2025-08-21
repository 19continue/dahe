<template>
  <view class="dh-card-skeleton-list" :class="{ compact }" :style="listStyle">
    <view
      v-for="idx in safeCount"
      :key="`dh-card-skeleton-${idx}`"
      class="dh-card-skeleton-item"
      :class="{
        'is-horizontal': horizontal,
        'has-media': withImage
      }"
      :style="itemStyle"
    >
      <view v-if="withImage" class="dh-card-skeleton-media" :style="mediaStyle">
        <t-skeleton theme="image" :animation="animation" />
      </view>
      <view class="dh-card-skeleton-body" :style="bodyStyle">
        <t-skeleton
          theme="paragraph"
          :animation="animation"
          :row-col="safeRowCol"
        />
      </view>
    </view>
  </view>
</template>

<script>
export default {
  name: 'DhCardSkeletonList',
  props: {
    count: {
      type: Number,
      default: 3
    },
    withImage: {
      type: Boolean,
      default: false
    },
    horizontal: {
      type: Boolean,
      default: false
    },
    compact: {
      type: Boolean,
      default: false
    },
    animation: {
      type: String,
      default: 'flashed'
    },
    imageWidth: {
      type: String,
      default: '200rpx'
    },
    imageHeight: {
      type: String,
      default: '150rpx'
    },
    rowCol: {
      type: Array,
      default: () => [1, 1, 1]
    },
    gap: {
      type: String,
      default: ''
    },
    itemMinHeight: {
      type: String,
      default: ''
    },
    bodyMinHeight: {
      type: String,
      default: ''
    },
    itemPadding: {
      type: String,
      default: ''
    },
    itemRadius: {
      type: String,
      default: ''
    },
    mediaRadius: {
      type: String,
      default: ''
    }
  },
  computed: {
    safeCount() {
      const value = Number(this.count)
      if (!Number.isFinite(value) || value <= 0) return 1
      return Math.floor(value)
    },
    safeRowCol() {
      return Array.isArray(this.rowCol) && this.rowCol.length ? this.rowCol : [1, 1, 1]
    },
    listStyle() {
      const style = {}
      const gap = this.gap || (this.compact ? '10rpx' : '12rpx')
      if (gap) {
        style.gap = gap
      }
      return style
    },
    itemStyle() {
      const style = {}
      const padding = this.itemPadding || (this.compact ? '14rpx' : '16rpx')
      const radius = this.itemRadius || (this.compact ? '18rpx' : '20rpx')
      if (padding) {
        style.padding = padding
      }
      if (radius) {
        style.borderRadius = radius
      }
      if (this.itemMinHeight) {
        style.minHeight = this.itemMinHeight
      }
      return style
    },
    mediaStyle() {
      const style = {
        width: this.imageWidth,
        height: this.imageHeight
      }
      const radius = this.mediaRadius || '16rpx'
      if (radius) {
        style.borderRadius = radius
      }
      return style
    },
    bodyStyle() {
      const style = {}
      if (this.bodyMinHeight) {
        style.minHeight = this.bodyMinHeight
      }
      return style
    }
  }
}
</script>

<style lang="scss">
.dh-card-skeleton-list {
  display: flex;
  flex-direction: column;
}

.dh-card-skeleton-item {
  background: #ffffff;
  border: 1rpx solid var(--dh-color-border);
  box-sizing: border-box;
  overflow: hidden;
}

.dh-card-skeleton-item.is-horizontal.has-media {
  display: flex;
  gap: 14rpx;
  align-items: stretch;
}

.dh-card-skeleton-media {
  flex-shrink: 0;
  border-radius: 16rpx;
  overflow: hidden;
  background: #eef2ea;
  --td-skeleton-bg-color: #e4ebdf;
  --td-skeleton-animation-flashed: rgba(197, 208, 192, 0.55);
}

.dh-card-skeleton-body {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  --td-skeleton-bg-color: #d7e0d1;
  --td-skeleton-animation-flashed: rgba(167, 180, 161, 0.62);
}

.dh-card-skeleton-body :deep(.t-skeleton) {
  width: 100%;
}
</style>
