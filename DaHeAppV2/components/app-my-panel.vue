<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="我的"
      :fixed="true"
      :safe-area-inset-top="true"
      :elder-mode="elderMode"
    />

    <scroll-view
      scroll-y
      class="content"
      :show-scrollbar="false"
      :enable-back-to-top="true"
      enhanced
      :fast-deceleration="true"
      :bounces="true"
    >
      <view class="content-inner">
      <view class="profile-hero">
        <view class="profile-card hero-card">
          <view class="hero-card-head">
            <text class="hero-card-title">个人资料</text>
            <view class="hero-card-action" @click="openProfilePopup">
              <t-icon name="edit-1" size="28rpx" />
              <text class="hero-card-action-text">编辑资料</text>
            </view>
          </view>
          <view class="hero-main">
            <view class="avatar-wrap">
              <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill" />
              <text v-else class="avatar-text">禾</text>
            </view>
            <view class="profile-main">
              <view class="name-row">
                <text class="nickname">{{ displayName }}</text>
                <text class="status-pill" :class="statusClass">{{ statusText }}</text>
              </view>
              <text class="hero-sub">{{ summaryText }}</text>
              <text class="hero-sub">操作员：{{ operatorName || '未关联' }}</text>
            </view>
          </view>
          <view class="summary-grid" :style="summaryGridStyle">
            <view class="summary-item">
              <text class="summary-label">手机号</text>
              <text class="summary-value">{{ phoneText }}</text>
            </view>
            <view v-if="notificationsEnabled" class="summary-item">
              <text class="summary-label">未读通知</text>
              <text class="summary-value">{{ unreadNoticeCount }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">当前版本</text>
              <text class="summary-value">2.0.0</text>
            </view>
          </view>
        </view>
      </view>

      <view class="group" v-if="canConsole">
        <view class="item" @click="goConsole">
          <view class="item-main">
            <text class="item-title">进入控制台</text>
            <text class="item-desc">处理用户、田块和业务数据的管理工作</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>

      <view class="group">
        <view class="item" @click="goCompanyIntro">
          <view class="item-main">
            <text class="item-title">企业介绍</text>
            <text class="item-desc">查看企业概况、服务内容与联系方式</text>
          </view>
          <text class="arrow">›</text>
        </view>
        <view class="item" @click="goSettings">
          <view class="item-main">
            <text class="item-title">设置</text>
            <text class="item-desc">调整授权、通知、缓存等个人使用偏好</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>

      <view class="group">
        <view class="item" @click="goNotices">
          <view class="item-main">
            <text class="item-title">消息通知</text>
            <text class="item-desc">查看审核结果、系统提醒和操作消息</text>
          </view>
          <view class="item-right">
            <text v-if="notificationsEnabled && unreadNoticeCount > 0" class="badge">{{ unreadNoticeCount }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
        <view class="item" @click="goHelp">
          <view class="item-main">
            <text class="item-title">帮助中心</text>
            <text class="item-desc">查看常见问题、操作引导和使用说明</text>
          </view>
          <text class="arrow">›</text>
        </view>
        <view class="item" @click="openSharePopup">
          <view class="item-main">
            <text class="item-title">分享应用</text>
            <text class="item-desc">可转发小程序给微信好友，或查看并保存小程序码</text>
          </view>
          <text class="arrow">›</text>
        </view>
        <view class="item" @click="goAbout">
          <view class="item-main">
            <text class="item-title">关于我们</text>
            <text class="item-desc">了解小程序定位、隐私说明与联系方式</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>

      <button class="logout-btn" @click="logout">退出登录</button>
      <view class="version">大禾小程序 版本 2.0.0</view>
      </view>
    </scroll-view>

    <t-popup
      :visible="profilePopupVisible"
      placement="bottom"
      :prevent-scroll-through="true"
      @visible-change="onProfilePopupVisibleChange"
    >
      <view class="profile-sheet">
        <view class="profile-sheet-head">
          <view>
            <text class="profile-sheet-title">编辑个人资料</text>
            <text class="profile-sheet-desc">仅支持修改昵称、头像和手机号。</text>
          </view>
          <text class="profile-sheet-close" @click="closeProfilePopup">×</text>
        </view>

        <view class="profile-avatar-editor">
          <view class="profile-avatar-preview">
            <image
              v-if="profileDraftAvatar"
              class="profile-avatar-image"
              :src="profileDraftAvatar"
              mode="aspectFill"
            />
            <text v-else class="profile-avatar-placeholder">禾</text>
          </view>
          <view class="profile-avatar-main">
            <text class="profile-avatar-title">头像</text>
            <text class="profile-avatar-tip">可直接使用当前微信头像，保存后生效。</text>
            <button
              class="profile-avatar-btn"
              open-type="chooseAvatar"
              @chooseavatar="handleChooseWechatAvatar"
            >
              使用微信头像
            </button>
          </view>
        </view>

        <view class="profile-form">
          <view class="profile-form-card">
            <text class="profile-form-label">昵称</text>
            <t-input
              class="profile-input"
              :value="profileDraft.nickName"
              placeholder="请输入昵称"
              clearable
              @change="onProfileNickNameChange"
            />
          </view>

          <view class="profile-form-card">
            <text class="profile-form-label">手机号</text>
            <t-input
              class="profile-input"
              type="number"
              :value="profileDraft.phone"
              placeholder="请输入手机号"
              clearable
              :maxlength="11"
              @change="onProfilePhoneChange"
            />
          </view>
        </view>

        <view class="profile-sheet-actions">
          <t-button
            class="profile-sheet-action"
            theme="primary"
            size="large"
            block
            :loading="profileSaving"
            :disabled="profileSaving"
            @click="submitProfile"
          >
            {{ profileSaving ? '保存中...' : '保存资料' }}
          </t-button>
        </view>
      </view>
    </t-popup>

    <t-popup
      :visible="sharePopupVisible"
      placement="bottom"
      :prevent-scroll-through="true"
      @visible-change="onSharePopupVisibleChange"
    >
      <view class="share-sheet">
        <view class="share-sheet-head">
          <text class="share-sheet-title">分享应用</text>
          <text class="share-sheet-desc">选择分享方式</text>
        </view>
        <t-button
          class="share-sheet-btn"
          theme="primary"
          size="large"
          block
          open-type="share"
          @click="handleShareLinkClick"
        >
          转发小程序给好友
        </t-button>
        <t-button
          class="share-sheet-btn"
          theme="default"
          variant="outline"
          size="large"
          block
          @click="openShareQrPopup"
        >
          查看小程序码
        </t-button>
      </view>
    </t-popup>

    <t-popup
      :visible="shareQrPopupVisible"
      placement="center"
      :prevent-scroll-through="true"
      @visible-change="onShareQrPopupVisibleChange"
    >
      <view class="share-qr-dialog">
        <view class="share-qr-head">
          <text class="share-qr-title">小程序码</text>
          <text class="share-qr-desc">保存后即可发送给微信好友</text>
        </view>
        <view v-if="shareQrLoading" class="share-qr-loading">
          <t-loading theme="spinner" size="40rpx" />
          <text class="share-qr-loading-text">正在生成小程序码...</text>
        </view>
        <view v-else-if="shareQrTempPath" class="share-qr-body">
          <image class="share-qr-image" :src="shareQrTempPath" mode="aspectFit" />
          <text class="share-qr-tip">打开微信扫一扫也可直接进入</text>
        </view>
        <view v-else class="share-qr-empty">
          <text class="share-qr-empty-text">{{ shareQrError || '小程序码暂不可用' }}</text>
        </view>
        <view class="share-qr-actions">
          <t-button
            class="share-qr-action"
            theme="primary"
            size="large"
            block
            :disabled="!shareQrTempPath"
            @click="saveShareQrCode"
          >
            保存到相册
          </t-button>
          <t-button
            class="share-qr-action"
            theme="default"
            variant="outline"
            size="large"
            block
            @click="shareQrPopupVisible = false"
          >
            关闭
          </t-button>
        </view>
      </view>
    </t-popup>
  </view>
</template>

<script>
import AppPageHeader from './app-page-header.vue'
import api from '../utils/request'
import {
  buildMiniappShareMessage,
  fetchMiniappShareQrCode,
  syncMiniappShareConfig,
  writeBase64ImageToTempFile
} from '../utils/share'
import {
  getAuthUser,
  getOperatorNameFromUser,
  clearAuthSession,
  setAuthUser
} from '../utils/auth'
import {
  getStoredNoticeUnreadCount,
  onNoticeUnreadChange,
  offNoticeUnreadChange,
  setStoredNoticeUnreadCount
} from '../utils/notice'

export default {
  components: {
    AppPageHeader
  },
  props: {
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      user: null,
      avatarUrl: '',
      operatorName: '',
      unreadNoticeCount: getStoredNoticeUnreadCount(),
      notificationsEnabled: String(uni.getStorageSync('dahe.v2.notifications') || '1') !== '0',
      initialized: false,
      profilePopupVisible: false,
      profileSaving: false,
      profileDraft: {
        nickName: '',
        phone: '',
        avatarUrl: ''
      },
      profileAvatarPendingPath: '',
      sharePopupVisible: false,
      shareQrPopupVisible: false,
      shareQrLoading: false,
      shareQrTempPath: '',
      shareQrError: '',
      shareConfigLoaded: false
    }
  },
  mounted() {
    this._noticeUnreadHandler = (count) => {
      this.unreadNoticeCount = Number(count || 0)
    }
    onNoticeUnreadChange(this._noticeUnreadHandler)
  },
  beforeDestroy() {
    if (this._noticeUnreadHandler) {
      offNoticeUnreadChange(this._noticeUnreadHandler)
      this._noticeUnreadHandler = null
    }
  },
  computed: {
    displayName() {
      const u = this.user || {}
      return u.nickName || u.realName || '未登录'
    },
    statusText() {
      if (this.user && this.user.enabled != null && Number(this.user.enabled) !== 1) return '已停用'
      const s = String((this.user && this.user.status) || '').toLowerCase()
      if (s === 'approved') return '已通过'
      if (s === 'pending') return '审核中'
      if (s === 'rejected') return '未通过'
      if (s === 'revoked') return '资格已收回'
      if (s === 'blacklisted') return '黑名单'
      return '未登录'
    },
    statusClass() {
      const text = this.statusText
      if (text === '已通过') return 'is-ok'
      if (text === '审核中') return 'is-warn'
      if (text === '未通过' || text === '已停用' || text === '黑名单') return 'is-danger'
      if (text === '资格已收回') return 'is-warn'
      return 'is-muted'
    },
    summaryText() {
      if (!this.user) return '当前未获取到账号信息'
      const s = String((this.user && this.user.status) || '').toLowerCase()
      if (s === 'revoked') return '登录资格已被收回，可重新提交申请'
      if (s === 'blacklisted') return '当前账号没有申请资格，请联系管理员'
      return '已认证，可正常使用小程序业务功能'
    },
    canConsole() {
      return !!(this.user && Number(this.user.canConsole) === 1)
    },
    summaryGridStyle() {
      const columns = this.notificationsEnabled ? 3 : 2
      return `grid-template-columns: repeat(${columns}, minmax(0, 1fr));`
    },
    phoneText() {
      return this.formatPhone(this.user && this.user.phone)
    },
    profileDraftAvatar() {
      return this.profileAvatarPendingPath || this.profileDraft.avatarUrl || ''
    }
  },
  methods: {
    handlePanelShow(forceRefresh = false) {
      this.syncNavigationBarTheme()
      this.user = getAuthUser()
      this.operatorName = getOperatorNameFromUser()
      this.avatarUrl = this.resolveAvatar(this.user)
      this.notificationsEnabled = String(uni.getStorageSync('dahe.v2.notifications') || '1') !== '0'
      this.unreadNoticeCount = this.notificationsEnabled ? getStoredNoticeUnreadCount() : 0
      if (!this.initialized || forceRefresh) {
        this.initialized = true
        this.refreshMe()
      }
      this.loadNoticeCount()
    },
    syncNavigationBarTheme() {
      if (typeof uni.setNavigationBarColor !== 'function') return
      uni.setNavigationBarColor({
        frontColor: '#ffffff',
        backgroundColor: '#2F7D45',
        animation: {
          duration: 0,
          timingFunc: 'linear'
        }
      })
    },
    resolveAvatar(user) {
      const u = user || {}
      return this.resolveAssetUrl(u.avatarUrl || u.wxAvatarUrl || '')
    },
    resolveAssetUrl(url) {
      const raw = String(url || '').trim()
      if (!raw) return ''
      if (/^(https?:|data:|wxfile:|file:)/i.test(raw)) return raw
      const baseUrl = String(api.getBaseUrl() || '').replace(/\/api\/v2\/?$/i, '')
      if (!baseUrl) return raw
      if (raw.startsWith('/')) {
        return `${baseUrl}${raw}`
      }
      return `${baseUrl}/${raw}`
    },
    formatPhone(phone) {
      const raw = String(phone || '').replace(/\s+/g, '')
      if (!raw) return '未填写'
      if (/^1\d{10}$/.test(raw)) {
        return `${raw.slice(0, 3)}****${raw.slice(-4)}`
      }
      return raw
    },
    normalizeInputValue(event) {
      return String((event && event.detail && event.detail.value) || '').trim()
    },
    openProfilePopup() {
      this.syncProfileDraft()
      this.profilePopupVisible = true
    },
    closeProfilePopup() {
      if (this.profileSaving) return
      this.profilePopupVisible = false
    },
    onProfilePopupVisibleChange(context) {
      const nextVisible = !!(context && context.visible)
      this.profilePopupVisible = nextVisible
      if (nextVisible) {
        this.syncProfileDraft()
        return
      }
      this.resetProfileDraft()
    },
    syncProfileDraft() {
      const user = this.user || getAuthUser() || {}
      this.profileDraft = {
        nickName: String(user.nickName || '').trim(),
        phone: String(user.phone || '').trim(),
        avatarUrl: this.resolveAvatar(user)
      }
      this.profileAvatarPendingPath = ''
    },
    resetProfileDraft() {
      this.profileDraft = {
        nickName: '',
        phone: '',
        avatarUrl: ''
      }
      this.profileAvatarPendingPath = ''
    },
    onProfileNickNameChange(event) {
      this.profileDraft.nickName = this.normalizeInputValue(event)
    },
    onProfilePhoneChange(event) {
      this.profileDraft.phone = this.normalizeInputValue(event).replace(/[^\d]/g, '').slice(0, 11)
    },
    handleChooseWechatAvatar(event) {
      const avatarPath = String((event && event.detail && event.detail.avatarUrl) || '').trim()
      if (!avatarPath) {
        uni.showToast({ title: '未获取到微信头像', icon: 'none' })
        return
      }
      this.profileAvatarPendingPath = avatarPath
    },
    async submitProfile() {
      if (this.profileSaving) return
      const nickName = String(this.profileDraft.nickName || '').trim()
      const phone = String(this.profileDraft.phone || '').trim()
      const currentNickName = String((this.user && this.user.nickName) || '').trim()
      const currentPhone = String((this.user && this.user.phone) || '').trim()
      const currentAvatar = this.resolveAvatar(this.user)
      const hasProfileChange = nickName !== currentNickName || phone !== currentPhone
      const hasAvatarChange = !!this.profileAvatarPendingPath

      if (phone && !/^1\d{10}$/.test(phone)) {
        uni.showToast({ title: '请输入正确的11位手机号', icon: 'none' })
        return
      }
      if (!hasProfileChange && !hasAvatarChange) {
        this.closeProfilePopup()
        return
      }

      this.profileSaving = true
      try {
        let nextUser = {
          ...(this.user || {})
        }
        if (hasProfileChange) {
          const profileResp = await api.put('/miniapp/auth/me/profile', {
            nickName,
            phone
          })
          nextUser = {
            ...nextUser,
            ...(profileResp || {})
          }
        }
        if (hasAvatarChange) {
          const uploadResp = await api.upload('/miniapp/files/upload', this.profileAvatarPendingPath, {
            name: 'file',
            formData: {
              remark: 'miniapp-avatar'
            }
          })
          const avatarUrl = this.resolveAssetUrl(
            (uploadResp && (uploadResp.fileUrl || uploadResp.previewUrl || uploadResp.url)) || ''
          )
          if (!avatarUrl) {
            throw new Error('头像上传失败，请稍后重试')
          }
          const avatarResp = await api.put('/miniapp/auth/me/avatar', {
            avatarUrl,
            avatarSource: 'wx'
          })
          nextUser = {
            ...nextUser,
            ...(avatarResp || {}),
            avatarUrl,
            wxAvatarUrl: avatarUrl
          }
        } else if (currentAvatar) {
          nextUser.avatarUrl = currentAvatar
        }
        this.user = nextUser
        this.avatarUrl = this.resolveAvatar(nextUser)
        setAuthUser(nextUser)
        this.operatorName = getOperatorNameFromUser()
        this.profilePopupVisible = false
        this.resetProfileDraft()
        uni.showToast({ title: '资料已更新', icon: 'none' })
      } catch (e) {
        const message = String((e && e.message) || '').trim()
        uni.showToast({ title: message || '资料更新失败', icon: 'none' })
      } finally {
        this.profileSaving = false
      }
    },
    async refreshMe() {
      try {
        const data = await api.get('/miniapp/auth/me')
        const nextUser = (data && data.user) || null
        if (nextUser) {
          this.user = nextUser
          this.avatarUrl = this.resolveAvatar(nextUser)
          setAuthUser(nextUser)
          this.operatorName = getOperatorNameFromUser()
        }
      } catch (e) {
        console.error('refresh me failed', e)
      }
    },
    async loadNoticeCount() {
      if (!this.notificationsEnabled) {
        this.unreadNoticeCount = 0
        setStoredNoticeUnreadCount(0)
        return
      }
      try {
        const data = await api.get(
          '/miniapp/auth/me/notices',
          { page: 1, pageSize: 1, unreadOnly: true },
          { dedupeWindowMs: 0 }
        )
        this.unreadNoticeCount = setStoredNoticeUnreadCount((data && data.unreadCount) || 0)
      } catch (e) {
        this.unreadNoticeCount = getStoredNoticeUnreadCount()
      }
    },
    async ensureShareConfig() {
      if (this.shareConfigLoaded) {
        return buildMiniappShareMessage()
      }
      try {
        await syncMiniappShareConfig(api)
        this.shareConfigLoaded = true
      } catch (e) {
        console.error('load share config failed', e)
      }
      return buildMiniappShareMessage()
    },
    async openSharePopup() {
      await this.ensureShareConfig()
      this.sharePopupVisible = true
    },
    onSharePopupVisibleChange(context) {
      this.sharePopupVisible = !!(context && context.visible)
    },
    handleShareLinkClick() {
      setTimeout(() => {
        this.sharePopupVisible = false
      }, 80)
    },
    onShareQrPopupVisibleChange(context) {
      this.shareQrPopupVisible = !!(context && context.visible)
    },
    async openShareQrPopup() {
      this.sharePopupVisible = false
      this.shareQrPopupVisible = true
      if (this.shareQrLoading || this.shareQrTempPath) {
        return
      }
      this.shareQrLoading = true
      this.shareQrError = ''
      try {
        await this.ensureShareConfig()
        const payload = await fetchMiniappShareQrCode(api)
        const tempPath = await writeBase64ImageToTempFile(payload && payload.base64, 'dahe-miniapp-share.png')
        this.shareQrTempPath = tempPath
      } catch (e) {
        const message = String((e && e.message) || '').trim()
        this.shareQrError = message || '小程序码生成失败，请稍后重试'
      } finally {
        this.shareQrLoading = false
      }
    },
    async saveShareQrCode() {
      if (!this.shareQrTempPath) {
        uni.showToast({ title: this.shareQrError || '小程序码未准备完成', icon: 'none' })
        return
      }
      try {
        await new Promise((resolve, reject) => {
          uni.saveImageToPhotosAlbum({
            filePath: this.shareQrTempPath,
            success: resolve,
            fail: reject
          })
        })
        uni.showToast({ title: '已保存到相册', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '保存失败，请检查相册权限', icon: 'none' })
      }
    },
    goConsole() {
      uni.navigateTo({ url: '/pages/my/console' })
    },
    goCompanyIntro() {
      uni.navigateTo({ url: '/pages/company-intro/index' })
    },
    goSettings() {
      uni.navigateTo({ url: '/pages/my/settings' })
    },
    goHelp() {
      uni.navigateTo({ url: '/pages/my/help' })
    },
    goNotices() {
      uni.navigateTo({ url: '/pages/my/notices' })
    },
    goAbout() {
      uni.navigateTo({ url: '/pages/my/about' })
    },
    async logout() {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '确认退出登录',
          content: '退出后需要重新进行微信登录，是否继续？',
          confirmText: '退出登录',
          cancelText: '取消',
          success: (res) => resolve(!!(res && res.confirm)),
          fail: () => resolve(false)
        })
      })
      if (!confirmed) {
        return
      }
      try {
        await api.post('/miniapp/auth/logout-all', {})
      } catch (e) {
        try {
          await api.post('/miniapp/auth/logout', {})
        } catch (fallbackErr) {
          console.error('logout failed', fallbackErr)
        }
      }
      clearAuthSession()
      uni.showToast({ title: '已退出', icon: 'none' })
      setTimeout(() => uni.reLaunch({ url: '/pages/auth/login?mode=login' }), 250)
    }
  }
}
</script>

<style lang="scss">
.page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--dh-color-bg);
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.content::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.content-inner {
  min-height: 100%;
  padding: 20rpx 24rpx calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.elder-mode .content-inner {
  padding-top: 24rpx;
}

.profile-hero {
  margin-bottom: 16rpx;
}

.hero-card {
  padding: 22rpx;
  background: linear-gradient(180deg, #f7fbf2 0%, #ffffff 100%);
}

.hero-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.hero-card-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #6d7966;
}

.hero-card-action {
  min-height: 52rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: rgba(47, 125, 69, 0.08);
  color: var(--dh-color-brand);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.hero-card-action-text {
  font-size: 22rpx;
  font-weight: 600;
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.avatar-wrap {
  width: 116rpx;
  height: 116rpx;
  border-radius: 58rpx;
  background: #d9e7c9;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

.avatar-text {
  font-size: 44rpx;
  color: var(--dh-color-brand);
  font-weight: 600;
}

.profile-main {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.nickname {
  font-size: 36rpx;
  font-weight: 600;
  color: #283621;
}

.status-pill {
  min-height: 42rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
}

.status-pill.is-ok {
  background: #e8f6ea;
  color: #217a43;
}

.status-pill.is-warn {
  background: #fff5de;
  color: #b47613;
}

.status-pill.is-danger {
  background: #fff0ee;
  color: #bf4f43;
}

.status-pill.is-muted {
  background: #eef1ee;
  color: #73806d;
}

.hero-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6f7c67;
  line-height: 1.5;
}

.summary-grid {
  margin-top: 18rpx;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10rpx;
}

.summary-item {
  min-width: 0;
  padding: 14rpx 12rpx;
  border-radius: 16rpx;
  background: #f3f7ee;
  border: 1rpx solid #e2ebd7;
}

.summary-label {
  display: block;
  font-size: 22rpx;
  color: #74816d;
}

.summary-value {
  display: block;
  margin-top: 6rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #2d3b26;
}

.group {
  margin-top: 16rpx;
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
}

.item {
  min-height: 102rpx;
  padding: 18rpx 22rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14rpx;
  border-bottom: 1rpx solid var(--dh-color-border);
}

.item:last-child {
  border-bottom: none;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  display: block;
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 600;
  line-height: 1.4;
}

.item-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #74816d;
  line-height: 1.5;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.badge {
  min-width: 40rpx;
  height: 40rpx;
  border-radius: 999rpx;
  padding: 0 10rpx;
  background: #d9534f;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
}

.arrow {
  color: #a0ab99;
  font-size: 38rpx;
  flex-shrink: 0;
}

.logout-btn {
  margin-top: 22rpx;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  background: #fff2f0;
  color: #d54941;
  border: 1rpx solid rgba(213, 73, 65, 0.18);
  font-size: 28rpx;
  font-weight: 600;
}

.version {
  margin-top: 18rpx;
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
}

.profile-sheet {
  padding: 28rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
}

.profile-sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.profile-sheet-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #24311d;
}

.profile-sheet-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6d796d;
}

.profile-sheet-close {
  min-width: 56rpx;
  text-align: right;
  color: #95a290;
  font-size: 46rpx;
  line-height: 1;
}

.profile-avatar-editor {
  margin-top: 24rpx;
  padding: 22rpx;
  border-radius: 24rpx;
  background: #f6f9f2;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.profile-avatar-preview {
  width: 128rpx;
  height: 128rpx;
  border-radius: 64rpx;
  overflow: hidden;
  background: #d9e7c9;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile-avatar-image {
  width: 100%;
  height: 100%;
}

.profile-avatar-placeholder {
  font-size: 48rpx;
  color: var(--dh-color-brand);
  font-weight: 700;
}

.profile-avatar-main {
  flex: 1;
  min-width: 0;
}

.profile-avatar-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #24311d;
}

.profile-avatar-tip {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #6d796d;
}

.profile-avatar-btn {
  margin-top: 14rpx;
  margin-left: 0;
  width: auto;
  min-width: 208rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #2f7d45;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
}

.profile-avatar-btn::after {
  border: none;
}

.profile-form {
  margin-top: 18rpx;
}

.profile-form-card {
  padding: 18rpx 20rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid #e8efe0;
}

.profile-form-card + .profile-form-card {
  margin-top: 16rpx;
}

.profile-form-label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #51604d;
}

.profile-input {
  --td-input-bg-color: #f7faf4;
  --td-input-border-color: transparent;
}

.profile-sheet-actions {
  margin-top: 24rpx;
}

.share-sheet {
  padding: 28rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
}

.share-sheet-head {
  margin-bottom: 18rpx;
}

.share-sheet-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #24311d;
}

.share-sheet-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6d796d;
}

.share-sheet-btn + .share-sheet-btn {
  margin-top: 14rpx;
}

.share-qr-dialog {
  width: 620rpx;
  max-width: calc(100vw - 64rpx);
  border-radius: 28rpx;
  background: #fff;
  padding: 30rpx 28rpx;
  box-sizing: border-box;
}

.share-qr-head {
  margin-bottom: 18rpx;
  text-align: center;
}

.share-qr-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #24311d;
}

.share-qr-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6d796d;
}

.share-qr-loading,
.share-qr-empty {
  min-height: 520rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
}

.share-qr-loading-text,
.share-qr-empty-text,
.share-qr-tip {
  font-size: 24rpx;
  line-height: 1.6;
  color: #6d796d;
  text-align: center;
}

.share-qr-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.share-qr-image {
  width: 420rpx;
  height: 420rpx;
  border-radius: 24rpx;
  background: #f5f8f2;
}

.share-qr-tip {
  margin-top: 14rpx;
}

.share-qr-actions {
  margin-top: 20rpx;
}

.share-qr-action + .share-qr-action {
  margin-top: 12rpx;
}

.elder-mode {
  .hero-card-title,
  .hero-card-action-text,
  .profile-form-label,
  .profile-sheet-desc,
  .profile-avatar-tip {
    font-size: 28rpx;
  }

  .profile-sheet-title,
  .profile-avatar-title {
    font-size: 36rpx;
  }

  .nickname {
    font-size: 42rpx;
  }

  .status-pill,
  .summary-label,
  .hero-sub,
  .item-desc,
  .version {
    font-size: 28rpx;
  }

  .summary-value,
  .item-title,
  .logout-btn,
  .share-sheet-title,
  .share-qr-title {
    font-size: 34rpx;
  }

  .item {
    min-height: 124rpx;
    padding-top: 22rpx;
    padding-bottom: 22rpx;
  }

  .summary-item,
  .hero-card {
    border-radius: 22rpx;
  }

  .profile-form-card,
  .profile-avatar-editor {
    border-radius: 26rpx;
  }

  .arrow {
    font-size: 44rpx;
  }

  .share-sheet-desc,
  .share-qr-desc,
  .share-qr-loading-text,
  .share-qr-empty-text,
  .share-qr-tip {
    font-size: 28rpx;
  }
}
</style>
