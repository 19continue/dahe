<template>
  <view class="launch-page">
    <app-launch-splash />
  </view>
</template>

<script>
import AppLaunchSplash from '../../components/app-launch-splash.vue'
import { resolveMiniappEntryRoute } from '../../utils/app-auth-guard'

const MIN_LAUNCH_STAY_MS = 1200
const FALLBACK_ROUTE = '/pages/auth/login?mode=apply'

export default {
  components: {
    AppLaunchSplash
  },
  data() {
    return {
      booting: false
    }
  },
  onLoad() {
    this.bootstrap()
  },
  onShow() {
    this.bootstrap()
  },
  methods: {
    wait(ms) {
      return new Promise((resolve) => {
        setTimeout(resolve, Math.max(0, Number(ms) || 0))
      })
    },
    async bootstrap() {
      if (this.booting) return
      this.booting = true
      try {
        const startedAt = Date.now()
        let target = FALLBACK_ROUTE
        try {
          target = await resolveMiniappEntryRoute()
        } catch (error) {
          console.error('resolve miniapp entry route failed', error)
        }
        const elapsed = Date.now() - startedAt
        await this.wait(MIN_LAUNCH_STAY_MS - elapsed)
        uni.reLaunch({ url: target || FALLBACK_ROUTE })
      } finally {
        this.booting = false
      }
    }
  }
}
</script>

<style lang="scss">
.launch-page {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
