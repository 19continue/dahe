<template>
  <t-navbar
    class="dh-navbar dh-page-header"
    :class="{
      'dh-page-header--toolbar': toolbar,
      'dh-page-header--elder': elderMode
    }"
    :title="title"
    :fixed="fixed"
    :safe-area-inset-top="safeAreaInsetTop"
    :left-arrow="leftArrow"
    :delta="0"
    @go-back="handleGoBack"
  >
    <template v-if="$slots.right" #right>
      <slot name="right" />
    </template>
  </t-navbar>
</template>

<script>
export default {
  options: {
    virtualHost: true
  },
  name: 'AppPageHeader',
  props: {
    title: {
      type: String,
      default: ''
    },
    fixed: {
      type: Boolean,
      default: true
    },
    safeAreaInsetTop: {
      type: Boolean,
      default: true
    },
    leftArrow: {
      type: Boolean,
      default: false
    },
    toolbar: {
      type: Boolean,
      default: false
    },
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    handleGoBack() {
      this.$emit('go-back')
    }
  }
}
</script>

<style lang="scss" scoped>
.dh-page-header :deep(.t-navbar__content) {
  min-height: 88rpx;
}

.dh-page-header :deep(.t-navbar__title) {
  font-size: 32rpx;
  font-weight: 700;
}

.dh-page-header--toolbar :deep(.t-navbar__content) {
  min-height: 96rpx;
}

.dh-page-header--toolbar :deep(.t-navbar__title) {
  font-size: 34rpx;
}

.dh-page-header--elder :deep(.t-navbar__content) {
  min-height: 96rpx;
}

.dh-page-header--elder :deep(.t-navbar__title) {
  font-size: 34rpx;
}

.dh-page-header--toolbar.dh-page-header--elder :deep(.t-navbar__content) {
  min-height: 104rpx;
}

.dh-page-header--toolbar.dh-page-header--elder :deep(.t-navbar__title) {
  font-size: 36rpx;
}
</style>
