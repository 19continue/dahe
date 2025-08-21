<template>
  <view>
    <slot />

    <view v-if="showAuthLaunchOverlay" class="app-auth-launch-layer">
      <app-launch-splash :title="authLaunchTitle" :desc="authLaunchDesc" />
    </view>

    <t-overlay
      :visible="updateGuardVisible"
      :prevent-scroll-through="true"
      :z-index="12000"
    >
      <view class="update-guard-mask">
        <view class="update-guard-card">
          <t-loading theme="spinner" size="40rpx" />
          <text class="update-guard-title">正在准备新版本</text>
          <text class="update-guard-desc">{{ updateGuardMessage }}</text>
        </view>
      </view>
    </t-overlay>
  </view>
</template>

<script>
import AppLaunchSplash from './components/app-launch-splash.vue'
import { bindAppAuthOverlayListener } from './utils/app-auth-guard'
import { installMiniappUpdateGuard } from './utils/update-guard'

export default {
  components: {
    AppLaunchSplash
  },
  data() {
    return {
      authLaunchVisible: false,
      authLaunchTitle: '正在核验登录状态',
      authLaunchDesc: '正在连接大禾种业服务，请稍候…',
      updateGuardVisible: false,
      updateGuardMessage: '发现新版本，正在下载，请稍候…'
    }
  },
  computed: {
    showAuthLaunchOverlay() {
      return this.authLaunchVisible && !this.updateGuardVisible
    }
  },
  onLaunch() {
    bindAppAuthOverlayListener(this.handleAuthOverlayChange)
    this.initUpdateGuard()
  },
  methods: {
    handleAuthOverlayChange(patch = {}) {
      if (Object.prototype.hasOwnProperty.call(patch, 'visible')) {
        this.authLaunchVisible = !!patch.visible
      }
      if (patch.title) {
        this.authLaunchTitle = patch.title
      }
      if (patch.desc) {
        this.authLaunchDesc = patch.desc
      }
    },
    initUpdateGuard() {
      installMiniappUpdateGuard({
        onChecking: () => {
          this.updateGuardVisible = true
          this.updateGuardMessage = '发现新版本，正在下载，请稍候…'
        },
        onIdle: () => {
          this.updateGuardVisible = false
        },
        onReady: (manager) => {
          this.updateGuardVisible = true
          this.updateGuardMessage = '新版本已准备完成，正在更新…'
          uni.showModal({
            title: '发现新版本',
            content: '新版本已准备完成，请立即更新后继续使用。',
            showCancel: false,
            confirmText: '立即更新',
            success: () => {
              if (manager && typeof manager.applyUpdate === 'function') {
                manager.applyUpdate()
              }
            }
          })
        },
        onFailed: () => {
          this.updateGuardVisible = true
          this.updateGuardMessage = '新版本下载失败，请关闭小程序后重新进入。'
          uni.showModal({
            title: '更新失败',
            content: '新版本下载失败，请关闭小程序后重新进入后重试。',
            showCancel: false,
            confirmText: '知道了'
          })
        }
      })
    }
  }
}
</script>

<style lang="scss">
@import "tdesign-uniapp/common/style/theme/index.css";
@import "./static/tdesign-theme.scss";
@import "./static/theme.scss";

page {
  background: var(--dh-color-bg);
  color: var(--dh-color-text);
  font-family: var(--dh-font-family);
}

.app-auth-launch-layer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 11800;
}

.update-guard-mask {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  box-sizing: border-box;
}

.update-guard-card {
  width: 100%;
  max-width: 560rpx;
  border-radius: 28rpx;
  background: #ffffff;
  padding: 36rpx 32rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  box-shadow: 0 18rpx 48rpx rgba(31, 45, 22, 0.18);
}

.update-guard-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #24311d;
}

.update-guard-desc {
  font-size: 26rpx;
  line-height: 1.7;
  text-align: center;
  color: #66755f;
}
</style>
