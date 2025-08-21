<template>
  <view class="tab-bridge-page"></view>
</template>

<script>
export default {
  data() {
    return {
      redirected: false
    }
  },
  onLoad() {
    this.redirectToShell()
  },
  onShow() {
    this.redirectToShell()
  },
  methods: {
    redirectToShell() {
      if (this.redirected) return
      this.redirected = true
      uni.reLaunch({ url: '/pages/home/index?tab=my' })
    }
  }
}
</script>

<style lang="scss">
.tab-bridge-page {
  min-height: 100vh;
  background: #f3f5f2;
}
</style>
