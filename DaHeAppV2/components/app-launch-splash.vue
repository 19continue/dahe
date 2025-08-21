<template>
  <view class="launch-screen">
    <view class="launch-background">
      <view class="launch-halo halo-left" />
      <view class="launch-halo halo-right" />
      <view class="launch-ripple ripple-a" />
      <view class="launch-ripple ripple-b" />
    </view>

    <view class="launch-main">
      <view class="brand-core-shell">
        <view class="brand-aura aura-inner" />
        <view class="brand-aura aura-outer" />
        <view class="brand-core">
          <text class="brand-fallback" :class="{ hidden: logoLoaded }">禾</text>
          <image
            v-if="showLogoImage"
            class="brand-logo"
            :src="safeLogoSrc"
            mode="aspectFit"
            @load="handleLogoLoad"
            @error="handleLogoError"
          />
        </view>
      </view>

      <view class="brand-copy">
        <text class="brand-name">大禾种业</text>
        <text class="brand-sub">DAHE SEEDS</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    title: {
      type: String,
      default: '正在准备小程序'
    },
    desc: {
      type: String,
      default: '正在连接大禾种业服务，请稍候…'
    },
    logoSrc: {
      type: String,
      default: '/static/images/logo.png'
    }
  },
  data() {
    return {
      logoLoaded: false,
      logoFailed: false
    }
  },
  computed: {
    safeLogoSrc() {
      return String(this.logoSrc || '').trim()
    },
    showLogoImage() {
      return !!this.safeLogoSrc && !this.logoFailed
    }
  },
  methods: {
    handleLogoLoad() {
      this.logoLoaded = true
      this.logoFailed = false
    },
    handleLogoError() {
      this.logoLoaded = false
      this.logoFailed = true
    }
  }
}
</script>

<style lang="scss">
.launch-screen {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(64rpx + env(safe-area-inset-top)) 40rpx calc(52rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: linear-gradient(180deg, #f5faf3 0%, #eef7ec 52%, #f8fbf6 100%);
}

.launch-background {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
}

.launch-halo {
  position: absolute;
  border-radius: 50%;
  filter: blur(22rpx);
}

.halo-left {
  top: 10%;
  left: -14%;
  width: 360rpx;
  height: 360rpx;
  background: radial-gradient(circle, rgba(110, 181, 105, 0.24) 0%, rgba(110, 181, 105, 0.08) 58%, rgba(110, 181, 105, 0) 100%);
  animation: haloFloatLeft 10s ease-in-out infinite;
}

.halo-right {
  right: -12%;
  bottom: 14%;
  width: 380rpx;
  height: 380rpx;
  background: radial-gradient(circle, rgba(218, 201, 149, 0.2) 0%, rgba(218, 201, 149, 0.06) 58%, rgba(218, 201, 149, 0) 100%);
  animation: haloFloatRight 11s ease-in-out infinite;
}

.launch-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  border: 1rpx solid rgba(61, 129, 70, 0.1);
  transform: translate(-50%, -50%);
}

.ripple-a {
  width: 320rpx;
  height: 320rpx;
  animation: rippleSpread 3.6s ease-out infinite;
}

.ripple-b {
  width: 420rpx;
  height: 420rpx;
  animation: rippleSpread 3.6s ease-out 1.2s infinite;
}

.launch-main {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 520rpx;
  text-align: center;
}

.brand-core-shell {
  position: relative;
  width: 220rpx;
  height: 220rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-aura {
  position: absolute;
  border-radius: 50%;
}

.aura-inner {
  width: 176rpx;
  height: 176rpx;
  background: radial-gradient(circle, rgba(59, 132, 69, 0.14) 0%, rgba(59, 132, 69, 0.04) 62%, rgba(59, 132, 69, 0) 100%);
  animation: auraPulseInner 3.2s ease-in-out infinite;
}

.aura-outer {
  width: 216rpx;
  height: 216rpx;
  background: radial-gradient(circle, rgba(59, 132, 69, 0.08) 0%, rgba(59, 132, 69, 0.02) 64%, rgba(59, 132, 69, 0) 100%);
  animation: auraPulseOuter 3.8s ease-in-out infinite;
}

.brand-core {
  position: relative;
  width: 136rpx;
  height: 136rpx;
  border-radius: 40rpx;
  background: linear-gradient(180deg, #ffffff 0%, #eef7ea 100%);
  border: 1rpx solid rgba(48, 118, 58, 0.12);
  box-shadow:
    inset 0 1rpx 0 rgba(255, 255, 255, 0.92),
    0 16rpx 34rpx rgba(39, 72, 35, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: coreFloat 4.6s ease-in-out infinite;
}

.brand-fallback {
  font-size: 70rpx;
  font-weight: 700;
  color: #2e7a43;
  transition: opacity 160ms ease;
}

.brand-fallback.hidden {
  opacity: 0;
}

.brand-logo {
  position: absolute;
  width: 94rpx;
  height: 94rpx;
}

.brand-copy {
  margin-top: 34rpx;
}

.brand-name {
  display: block;
  font-size: 44rpx;
  line-height: 1.24;
  font-weight: 700;
  color: #183a24;
  letter-spacing: 1rpx;
}

.brand-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 4rpx;
  color: #748672;
}

@keyframes haloFloatLeft {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(12rpx, 16rpx, 0) scale(1.05);
  }
}

@keyframes haloFloatRight {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(-14rpx, -12rpx, 0) scale(1.04);
  }
}

@keyframes rippleSpread {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.86);
  }
  25% {
    opacity: 0.42;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.08);
  }
}

@keyframes auraPulseInner {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.82;
  }
  50% {
    transform: scale(1.06);
    opacity: 0.46;
  }
}

@keyframes auraPulseOuter {
  0%,
  100% {
    transform: scale(0.98);
    opacity: 0.72;
  }
  50% {
    transform: scale(1.05);
    opacity: 0.34;
  }
}

@keyframes coreFloat {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-8rpx) scale(1.02);
  }
}
</style>
