<template>
  <view class="app-tab-bar" :class="{ 'is-elder': elderMode }">
    <t-tab-bar
      t-class="dh-app-tabbar__root"
      :value="value"
      :fixed="false"
      :safe-area-inset-bottom="true"
      :bordered="true"
      :split="false"
      theme="tag"
      @change="handleChange"
    >
      <t-tab-bar-item value="home" :icon="{ name: value === 'home' ? 'home-filled' : 'home' }">首页</t-tab-bar-item>
      <t-tab-bar-item value="my" :icon="{ name: value === 'my' ? 'user-1-filled' : 'user-1' }">我的</t-tab-bar-item>
    </t-tab-bar>
  </view>
</template>

<script>
export default {
  options: {
    virtualHost: true
  },
  props: {
    value: {
      type: String,
      default: 'home'
    },
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    resolveNextValue(context) {
      if (context && typeof context === 'object' && context.value != null) {
        return String(context.value)
      }
      return String(context || '')
    },
    handleChange(context) {
      const nextValue = this.resolveNextValue(context)
      if (!nextValue || nextValue === this.value) return
      this.$emit('change', nextValue)
    }
  }
}
</script>

<style lang="scss" scoped>
.app-tab-bar {
  flex-shrink: 0;
  position: relative;
  z-index: 3900;
}

.app-tab-bar :deep(.dh-app-tabbar__root) {
  --td-tab-bar-bg-color: rgba(255, 255, 255, 0.98);
  --td-tab-bar-color: #6b7768;
  --td-tab-bar-active-color: #2f7d45;
  --td-tab-bar-active-bg: #edf6e9;
  --td-tab-bar-height: 92rpx;
  --td-tab-bar-border-color: #dfe7da;
  box-shadow: 0 -10rpx 30rpx rgba(35, 61, 37, 0.08);
  backdrop-filter: blur(16px);
}

.app-tab-bar :deep(.t-tab-bar-item) {
  margin: 8rpx 0;
  padding: 0 12rpx;
}

.app-tab-bar :deep(.t-tab-bar-item__content) {
  gap: 8rpx;
  border-radius: 18rpx;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.app-tab-bar :deep(.t-tab-bar-item__content--checked) {
  font-weight: 700;
}

.app-tab-bar :deep(.t-tab-bar-item__text) {
  font-size: 22rpx;
  line-height: 1.2;
}

.app-tab-bar :deep(.t-icon) {
  font-size: 42rpx !important;
}

.app-tab-bar.is-elder :deep(.dh-app-tabbar__root) {
  --td-tab-bar-height: 118rpx;
}

.app-tab-bar.is-elder :deep(.t-tab-bar-item) {
  margin: 10rpx 0;
  padding: 0 18rpx;
}

.app-tab-bar.is-elder :deep(.t-tab-bar-item__content) {
  gap: 12rpx;
  border-radius: 22rpx;
  min-height: 82rpx;
}

.app-tab-bar.is-elder :deep(.t-tab-bar-item__text) {
  font-size: 32rpx;
  line-height: 1.3;
}

.app-tab-bar.is-elder :deep(.t-icon) {
  font-size: 56rpx !important;
}
</style>
