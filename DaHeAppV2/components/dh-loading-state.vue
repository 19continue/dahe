<template>
  <view class="dh-loading-state" :class="{ compact }">
    <view class="dh-loading-state__skeleton">
      <t-skeleton theme="paragraph" animation="flashed" :row-col="safeRowCol" />
    </view>
    <text class="dh-loading-state__title">{{ title }}</text>
    <text v-if="desc" class="dh-loading-state__desc">{{ desc }}</text>
  </view>
</template>

<script>
export default {
  name: 'DhLoadingState',
  props: {
    title: {
      type: String,
      default: '加载中...'
    },
    desc: {
      type: String,
      default: ''
    },
    compact: {
      type: Boolean,
      default: false
    },
    rowCol: {
      type: Array,
      default: () => [1, 1, 1]
    }
  },
  computed: {
    safeRowCol() {
      return Array.isArray(this.rowCol) && this.rowCol.length ? this.rowCol : [1, 1, 1]
    }
  }
}
</script>

<style lang="scss">
.dh-loading-state {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 24rpx 20rpx;
  box-sizing: border-box;
}

.dh-loading-state.compact {
  padding: 16rpx 12rpx;
  gap: 8rpx;
}

.dh-loading-state__skeleton {
  width: 88%;
  max-width: 440rpx;
  border-radius: 16rpx;
  overflow: hidden;
}

.dh-loading-state__title {
  font-size: 25rpx;
  color: #465443;
  font-weight: 700;
}

.dh-loading-state__desc {
  font-size: 22rpx;
  color: #74816f;
  text-align: center;
  line-height: 1.5;
}
</style>
