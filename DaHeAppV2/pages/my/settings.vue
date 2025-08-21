<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="设置"
      :fixed="true"
      :safe-area-inset-top="true"
      :elder-mode="elderMode"
      left-arrow
      @go-back="goBack"
    />

    <view class="content">
      <view class="group">
        <view class="item" @click="clearCache">
          <text>清除缓存</text>
          <text class="sub">{{ cacheSize }}</text>
        </view>
        <view class="item">
          <text>消息提醒</text>
          <switch :checked="notificationsEnabled" @change="toggleNotifications" color="#73AE52" />
        </view>
        <view class="item">
          <text>大字模式</text>
          <switch :checked="elderMode" @change="toggleElderModeSwitch" color="#73AE52" />
        </view>
      </view>

      <view class="group">
        <view class="item" @click="resetGuide">
          <text>重置主页引导</text>
          <text class="sub">下次进入重新展示</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { isElderMode, setElderMode } from '../../utils/accessibility'

const GUIDE_STORAGE_KEY = 'dahe.v2.homeGuideSeen'

export default {
  data() {
    return {
      cacheSize: '0 KB',
      notificationsEnabled: uni.getStorageSync('dahe.v2.notifications') !== '0',
      elderMode: false
    }
  },
  onShow() {
    this.calculateCacheSize()
    this.elderMode = isElderMode()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    calculateCacheSize() {
      try {
        const info = uni.getStorageInfoSync()
        this.cacheSize = `${Math.max(1, info.currentSize || 0)} KB`
      } catch (error) {
        this.cacheSize = '0 KB'
      }
    },
    clearCache() {
      uni.showModal({
        title: '确认清除缓存',
        content: '将清除本地缓存的筛选条件、草稿和引导状态。',
        success: (res) => {
          if (!res.confirm) return
          const keepKeys = [
            'dahe.v2.userInfo',
            'dahe.v2.notifications',
            'dahe.v2.accessToken',
            'dahe.v2.authUser',
            'dahe.v2.elderMode'
          ]
          const keys = uni.getStorageInfoSync().keys || []
          keys.forEach((key) => {
            if (!keepKeys.includes(key)) {
              uni.removeStorageSync(key)
            }
          })
          this.calculateCacheSize()
          uni.showToast({ title: '已清除', icon: 'success' })
        }
      })
    },
    toggleNotifications(e) {
      this.notificationsEnabled = !!e.detail.value
      uni.setStorageSync('dahe.v2.notifications', this.notificationsEnabled ? '1' : '0')
      uni.showToast({ title: this.notificationsEnabled ? '已开启提醒' : '已关闭提醒', icon: 'none' })
    },
    toggleElderModeSwitch(e) {
      this.elderMode = setElderMode(!!(e && e.detail && e.detail.value))
      uni.showToast({ title: this.elderMode ? '已切换大字模式' : '已切换普通模式', icon: 'none' })
    },
    resetGuide() {
      uni.removeStorageSync(GUIDE_STORAGE_KEY)
      uni.showToast({ title: '已重置', icon: 'success' })
    }
  }
}
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background: var(--dh-color-bg);
}

.topbar {
  padding-top: var(--status-bar-height);
  height: calc(var(--status-bar-height) + 88rpx);
  background: var(--dh-color-brand);
  display: flex;
  align-items: center;
}

.back,
.right {
  width: 88rpx;
  text-align: center;
  color: #fff;
  font-size: 48rpx;
}

.title {
  flex: 1;
  text-align: center;
  color: #fff;
  font-size: 34rpx;
  font-weight: 700;
}

.content {
  padding: 20rpx 24rpx;
}

.group {
  margin-bottom: 14rpx;
  border-radius: 18rpx;
  overflow: hidden;
  background: #fff;
}

.item {
  min-height: 94rpx;
  padding: 0 20rpx;
  border-bottom: 1rpx solid var(--dh-color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 30rpx;
  color: #2c3a26;
}

.item:last-child {
  border-bottom: none;
}

.sub {
  font-size: 24rpx;
  color: #74836d;
  max-width: 420rpx;
  text-align: right;
  word-break: break-all;
}

.elder-mode {
  .item {
    min-height: 118rpx;
    font-size: 36rpx;
  }

  .sub {
    font-size: 28rpx;
  }
}
</style>
