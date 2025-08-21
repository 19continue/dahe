<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="账号认证"
      :fixed="true"
      :safe-area-inset-top="true"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="hero-card">
        <text class="hero-title">{{ pageMode === 'login' ? '微信登录' : '账号认证' }}</text>
        <text class="hero-desc">{{ heroDesc }}</text>
      </view>

      <view v-if="showPendingSummary" class="card submitted-card">
        <view class="submitted-icon">✓</view>
        <text class="submitted-title">已提交</text>
        <text class="submitted-desc">当前认证资料已提交审核。若需补充或修正，可重新提交并修改信息。</text>
        <button class="minor submitted-action" :disabled="submitting || checkingEntry" @click="openApplyEditor">
          重新提交（或修改提交信息）
        </button>
      </view>

      <view v-else-if="showRejectedSummary" class="card rejected-card">
        <view class="rejected-icon">!</view>
        <text class="submitted-title">未通过</text>
        <text class="submitted-desc">{{ rejectedReasonText }}</text>
        <button class="minor submitted-action" :disabled="submitting || checkingEntry" @click="openApplyEditor">
          重新提交（或修改提交信息）
        </button>
      </view>

      <view v-else-if="showDisabledSummary" class="card disabled-card">
        <view class="disabled-icon">×</view>
        <text class="submitted-title">账号已停用</text>
        <text class="submitted-desc">当前账号已停用，无法继续登录或提交认证，请联系管理员处理。</text>
      </view>

      <view v-else-if="showRevokedSummary" class="card revoked-card">
        <view class="revoked-icon">!</view>
        <text class="submitted-title">资格已收回</text>
        <text class="submitted-desc">您的用户登录资格已被收回。如需继续使用，请重新提交认证申请。</text>
        <button class="minor submitted-action" :disabled="submitting || checkingEntry" @click="openApplyEditor">
          重新申请
        </button>
      </view>

      <view v-else-if="showBlacklistedSummary" class="card blacklisted-card">
        <view class="blacklisted-icon">!</view>
        <text class="submitted-title">无法申请</text>
        <text class="submitted-desc">您没有申请资格，请联系管理员。</text>
      </view>

      <view v-else-if="pageMode === 'apply'" class="card apply-card">
        <view class="section-head">
          <text class="section-title">认证信息</text>
          <text class="section-sub">请填写真实、可识别的信息，审核通过后才能登录小程序。</text>
        </view>

        <view class="form-list">
          <view class="form-row">
            <text class="label">姓名</text>
            <input class="input" v-model="realName" placeholder="请输入真实姓名" />
          </view>

          <view class="form-row">
            <text class="label">昵称</text>
            <input class="input" v-model="nickName" placeholder="请输入昵称（可选）" />
          </view>

          <view class="form-row">
            <text class="label">手机号</text>
            <input class="input" v-model="phone" type="number" maxlength="11" placeholder="请输入手机号（可选）" />
          </view>

          <view class="form-row">
            <text class="label">申请说明</text>
            <textarea class="textarea" v-model="applyReason" placeholder="例如：负责东区田块日常农事记录" />
          </view>
        </view>
      </view>

      <view v-else class="card login-card">
        <view class="login-user">
          <image v-if="avatarUrl" class="avatar-preview" :src="avatarUrl" mode="aspectFill" />
          <view v-else class="avatar-placeholder">头像</view>
          <view class="login-user-main">
            <text class="login-name">{{ realName || nickName || '已认证用户' }}</text>
            <text class="login-meta" v-if="phone">手机号：{{ phone }}</text>
            <text class="login-meta" v-else>该微信账号已完成认证</text>
          </view>
          <text class="login-status">已认证</text>
        </view>
        <text class="login-note">请点击下方按钮使用微信登录，登录后进入小程序首页。头像可在登录后到个人资料中修改。</text>
      </view>

      <view v-if="pageMode === 'apply'" class="card notice-card">
        <text class="notice-title">审核说明</text>
        <text class="notice-text">同一个微信账号只对应一条认证记录。再次提交时，会在原记录上更新资料，不会重复创建新账号。</text>
      </view>

      <view v-if="!showDisabledSummary && !showBlacklistedSummary" class="card agreement-card">
        <view class="agreement-row" @tap="toggleAgreement">
          <t-checkbox
            :checked="agreementAccepted"
            icon="rectangle"
            borderless
            @change="handleAgreementChange"
          />
          <view class="agreement-copy">
            <view class="agreement-line">
              <text class="agreement-text">我已阅读并同意</text>
              <text class="agreement-link" @tap.stop="openUserAgreement">《用户服务协议》</text>
              <text class="agreement-text">和</text>
              <text class="agreement-link" @tap.stop="openPrivacyPolicy">《隐私政策》</text>
            </view>
          </view>
        </view>
        <text class="agreement-hint">未勾选前不可提交认证或进行微信登录。</text>
      </view>

      <view class="footer-actions">
        <button v-if="showPrimarySubmit" class="submit" :disabled="submitting || checkingEntry" @click="submitAction">
          {{ submitting ? '处理中...' : submitButtonText }}
        </button>
        <button v-if="pageMode !== 'login'" class="minor" :disabled="checkingEntry || submitting" @click="refreshEntryStatus">
          {{ checkingEntry ? '查询中...' : '重新检查认证状态' }}
        </button>

        <view v-if="statusMessage && !showPendingSummary && !showRejectedSummary && !showDisabledSummary && !showRevokedSummary && !showBlacklistedSummary" class="status-card">
          <view class="status-head">
            <text class="status-title">当前状态</text>
            <text class="status-badge">{{ statusBadgeText }}</text>
          </view>
          <text class="status-text">{{ statusMessage }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="showLoginStateOverlay" class="login-state-overlay" :class="`is-${loginVisualState}`">
      <view class="login-state-shell">
        <view class="login-state-orb-wrap">
          <view class="login-state-ring ring-1"></view>
          <view class="login-state-ring ring-2"></view>
          <view class="login-state-orb">
            <text class="login-state-brand">禾</text>
          </view>
        </view>
        <text class="login-state-title">{{ loginStateTitle }}</text>
        <text class="login-state-desc">{{ loginStateDesc }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import {
  getAuthUser,
  saveAuthSession,
  setAuthUser,
  getOperatorNameFromUser,
  setAccessToken,
  setTokenExpiresAt
} from '../../utils/auth'
import elderPageMixin from '../../utils/elder-page'
import { buildDeviceName, buildUserAgent } from '../../utils/system-info'

const OPERATOR_STORAGE_KEY = 'dahe.v2.operatorName'
const DEVICE_ID_KEY = 'dahe.v2.deviceId'
const LEGAL_ACCEPTED_KEY = 'dahe.v2.legalAccepted'

export default {
  mixins: [elderPageMixin],
  data() {
    return {
      pageMode: 'apply',
      realName: '',
      nickName: '',
      phone: '',
      avatarUrl: '',
      applyReason: '',
      statusMessage: '',
      statusBadgeText: '未申请',
      editingPendingApplication: false,
      submitting: false,
      checkingEntry: false,
      agreementAccepted: String(uni.getStorageSync(LEGAL_ACCEPTED_KEY) || '0') === '1',
      loginVisualState: 'idle'
    }
  },
  computed: {
    heroDesc() {
      if (this.pageMode === 'login') {
        return '该微信账号已完成认证，可直接使用微信登录进入小程序。'
      }
      return '系统会自动识别当前微信账号的认证状态。未完成认证时，请先提交资料并等待审核。'
    },
    submitButtonText() {
      if (this.pageMode === 'login') {
        return '微信授权登录'
      }
      const badge = String(this.statusBadgeText || '').trim()
      if (badge === '审核中') return '重新提交（或修改提交信息）'
      if (badge === '未通过') return '重新提交认证'
      return '提交认证申请'
    },
    showPendingSummary() {
      return this.pageMode === 'apply' && this.statusBadgeText === '审核中' && !this.editingPendingApplication
    },
    showRejectedSummary() {
      return this.pageMode === 'apply' && this.statusBadgeText === '未通过' && !this.editingPendingApplication
    },
    showDisabledSummary() {
      return this.pageMode === 'apply' && this.statusBadgeText === '已停用'
    },
    showRevokedSummary() {
      return this.pageMode === 'apply' && this.statusBadgeText === '资格已收回' && !this.editingPendingApplication
    },
    showBlacklistedSummary() {
      return this.pageMode === 'apply' && this.statusBadgeText === '黑名单'
    },
    showPrimarySubmit() {
      return this.pageMode === 'login'
        || (this.pageMode === 'apply'
          && !this.showPendingSummary
          && !this.showRejectedSummary
          && !this.showDisabledSummary
          && !this.showRevokedSummary
          && !this.showBlacklistedSummary)
    },
    showLoginStateOverlay() {
      return this.pageMode === 'login' && (this.loginVisualState === 'loading' || this.loginVisualState === 'success')
    },
    loginStateTitle() {
      return this.loginVisualState === 'success' ? '登录成功' : '微信安全登录中'
    },
    loginStateDesc() {
      return this.loginVisualState === 'success' ? '正在进入主页' : '正在校验账号与会话，请稍候'
    },
    rejectedReasonText() {
      return this.statusMessage || '认证未通过，请补充或修正资料后重新提交。'
    }
  },
  onLoad(options) {
    this.applyLaunchMode(options)
    this.hydrateFromStoredUser()
  },
  onShow() {
    this.hydrateFromStoredUser()
  },
  methods: {
    applyLaunchMode(options = {}) {
      const rawMode = String((options && options.mode) || '').trim().toLowerCase()
      if (rawMode === 'login') {
        this.pageMode = 'login'
        return
      }
      this.pageMode = 'apply'
    },
    hydrateFromStoredUser() {
      /*
       * 页面初始化时，先根据本地缓存的 user 粗恢复 UI 状态。
       *
       * 注意这里恢复的是“展示状态”，不是最终准入结论。
       * 真正的准入结果仍以后端 /miniapp/auth/entry 返回为准。
       * 这么做只是为了减少页面白屏和状态闪烁。
       */
      const user = getAuthUser() || {}
      this.realName = user.realName || this.realName
      this.nickName = user.nickName || this.nickName
      this.phone = user.phone || this.phone
      this.applyReason = user.applyReason || this.applyReason
      this.avatarUrl = user.avatarUrl || user.wxAvatarUrl || this.avatarUrl
      if (this.isApprovedProfile(user)) {
        this.pageMode = 'login'
        this.editingPendingApplication = false
        this.statusBadgeText = '已认证'
        this.statusMessage = '该微信账号已完成认证，请使用微信登录进入小程序。'
        return
      }
      if (user.status) {
        this.pageMode = 'apply'
        this.editingPendingApplication = false
        this.statusBadgeText = this.resolveStatusBadge(user)
        this.statusMessage = this.formatStatusMessage(user)
        return
      }
      if (this.pageMode === 'login') {
        this.editingPendingApplication = false
        this.statusBadgeText = '已认证'
        this.statusMessage = '请点击下方按钮重新使用微信登录进入小程序。'
        return
      }
      this.pageMode = 'apply'
      this.statusBadgeText = '未申请'
      this.statusMessage = '请先提交认证申请。'
    },
    async refreshEntryStatus(showToast = true) {
      /*
       * 重新查询“小程序当前微信账号是否允许进入系统”。
       *
       * 这个接口只查状态，不负责真正登录。
       * 它对应后端的 miniappEntry：先认证、后登录里的“认证状态识别”阶段。
       */
      if (this.checkingEntry) return
      this.checkingEntry = true
      try {
        const code = await this.fetchWechatCode()
        const resp = await api.post('/miniapp/auth/entry', {
          code,
          loginScene: 'task_center',
          deviceContext: this.buildDeviceContext()
        })
        this.applyEntryResult(resp)
        if (showToast) {
          uni.showToast({
            title: this.pageMode === 'login' ? '已切换到微信登录' : '已同步认证状态',
            icon: 'none'
          })
        }
      } catch (e) {
        console.error('查询小程序准入状态失败', e)
        if (showToast) {
          uni.showToast({ title: '查询失败，请稍后重试', icon: 'none' })
        }
      } finally {
        this.checkingEntry = false
      }
    },
    async fetchWechatCode() {
      const loginRes = await new Promise((resolve, reject) => {
        uni.login({
          provider: 'weixin',
          success: resolve,
          fail: reject
        })
      })
      const code = String((loginRes && loginRes.code) || '').trim()
      if (!code) {
        throw new Error('wechat login code missing')
      }
      return code
    },
    applyEntryResult(resp) {
      /*
       * 这是前端准入状态机的核心方法。
       *
       * 后端会返回 user + approved + message 等信息，
       * 前端根据这些字段把页面切成两类：
       * - apply：还不能登录，只能申请或查看状态；
       * - login：已经被业务放行，可以点击微信登录。
       *
       * 这里特意把“能看到用户资料”与“已经登录成功”区分开：
       * 有 user 不代表有 session，只有 accessToken 下发后才算真正登录。
       */
      const user = resp && resp.user ? resp.user : null
      const approved = this.isApprovedEntry(resp)
      if (!approved) {
        setAccessToken('')
        setTokenExpiresAt('')
      }
      if (user) {
        setAuthUser(user)
        this.realName = user.realName || this.realName
        this.nickName = user.nickName || this.nickName
        this.phone = user.phone || this.phone
        this.applyReason = user.applyReason || this.applyReason
        this.avatarUrl = user.avatarUrl || user.wxAvatarUrl || this.avatarUrl
      } else {
        setAuthUser(null)
      }

      if (approved) {
        this.pageMode = 'login'
        this.editingPendingApplication = false
        this.statusBadgeText = '已认证'
        this.statusMessage = '该微信账号已完成认证，请使用微信登录进入小程序。'
        return
      }

      this.pageMode = 'apply'
      this.editingPendingApplication = false
      if (user) {
        this.statusBadgeText = this.resolveStatusBadge(user)
        this.statusMessage = this.formatStatusMessage(user)
      } else {
        this.statusBadgeText = '未申请'
        this.statusMessage = (resp && resp.message) || '请先提交认证申请。'
      }
    },
    isApprovedEntry(resp) {
      if (!resp || !resp.user) return false
      return this.isApprovedProfile(resp.user)
    },
    isApprovedProfile(user) {
      if (!user) return false
      const enabled = user.enabled == null ? true : Number(user.enabled) === 1
      return enabled && String(user.status || '').toLowerCase() === 'approved'
    },
    async submitAction() {
      if (!this.ensureAgreementAccepted()) {
        return
      }
      if (this.pageMode === 'login') {
        await this.submitWechatLogin()
        return
      }
      await this.submitApply()
    },
    openApplyEditor() {
      this.editingPendingApplication = true
    },
    handleAgreementChange(context) {
      const checked = !!(context && context.checked)
      this.agreementAccepted = checked
      uni.setStorageSync(LEGAL_ACCEPTED_KEY, checked ? '1' : '0')
    },
    toggleAgreement() {
      this.handleAgreementChange({ checked: !this.agreementAccepted })
    },
    ensureAgreementAccepted() {
      if (this.agreementAccepted) {
        return true
      }
      uni.showToast({
        title: '请先阅读并同意《用户服务协议》和《隐私政策》',
        icon: 'none'
      })
      return false
    },
    openUserAgreement() {
      this.openLegalPage('/pages/legal/user-agreement')
    },
    openPrivacyPolicy() {
      this.openLegalPage('/pages/legal/privacy-policy')
    },
    openLegalPage(url) {
      uni.navigateTo({
        url,
        fail: () => {
          uni.showToast({
            title: '页面打开失败，请稍后重试',
            icon: 'none'
          })
        }
      })
    },
    async submitApply() {
      /*
       * 提交认证资料。
       *
       * 这一步的语义是“提交或更新申请”，不是“提交后一定立刻进入系统”。
       * 如果后端判定当前状态还不能发 session，这里仍然会留在 apply 模式。
       */
      if (!this.realName.trim()) {
        uni.showToast({ title: '请先填写姓名', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        const code = await this.fetchWechatCode()
        const resp = await api.post('/miniapp/auth/apply', {
          code,
          loginScene: 'task_center',
          deviceContext: this.buildDeviceContext(),
          realName: this.realName.trim(),
          nickName: this.nickName.trim(),
          phone: this.phone.trim(),
          applyReason: this.applyReason.trim()
        })
        if (resp && resp.user) {
          setAuthUser(resp.user)
        }
        this.applyEntryResult(resp)
        if (this.pageMode === 'login') {
          uni.showToast({ title: '认证已通过，请微信登录', icon: 'none' })
          return
        }
        this.editingPendingApplication = false
        uni.showToast({ title: '认证信息已提交/更新', icon: 'none' })
      } catch (e) {
        console.error('认证申请失败', e)
        uni.showToast({ title: (e && e.message) || '认证提交失败，请稍后重试', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
    async submitWechatLogin() {
      /*
       * 真正的小程序登录动作。
       *
       * 只有在 applyEntryResult 已经把页面切到 login 模式后，用户才应该走这里。
       * 这与后端 miniappWechatLogin 对应，强调的是：
       * “已通过准入校验的用户，才允许真正创建 session 并进入首页”。
       */
      this.submitting = true
      this.loginVisualState = 'loading'
      try {
        const code = await this.fetchWechatCode()
        const resp = await api.post('/miniapp/auth/wechat-login', {
          code,
          loginScene: 'task_center',
          deviceContext: this.buildDeviceContext()
        })

        if (resp && resp.user) {
          setAuthUser(resp.user)
        }
        if (resp && resp.approved && resp.accessToken) {
          saveAuthSession(resp)
          const operatorName = getOperatorNameFromUser()
          if (operatorName) {
            uni.setStorageSync(OPERATOR_STORAGE_KEY, operatorName)
          }
          this.loginVisualState = 'success'
          await this.wait(280)
          uni.reLaunch({ url: '/pages/home/index' })
          return
        }
        this.loginVisualState = 'idle'
        this.applyEntryResult(resp)
        uni.showToast({ title: '当前账号尚未可登录，请检查认证状态', icon: 'none' })
      } catch (e) {
        this.loginVisualState = 'idle'
        console.error('微信登录失败', e)
        uni.showToast({ title: (e && e.message) || '微信登录失败，请稍后重试', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
    formatStatusMessage(user) {
      /*
       * 把后端状态码翻译成用户能理解的提示文案。
       * 面试时可以把它理解成“前端对认证状态机的展示层映射”。
       */
      if (user && user.enabled != null && Number(user.enabled) !== 1) {
        return '账号已停用，请联系管理员。'
      }
      const s = String(user.status || '').toLowerCase()
      if (s === 'approved') return '该微信账号已完成认证，请使用微信登录进入小程序。'
      if (s === 'pending') return '审核中，请等待管理员审批。'
      if (s === 'rejected') return `未通过：${user.rejectReason || '请联系管理员'}`
      if (s === 'revoked') return '您的用户登录资格已被收回，可重新提交认证申请。'
      if (s === 'blacklisted') return '您没有申请资格，请联系管理员。'
      return '请先提交认证申请。'
    },
    resolveStatusBadge(user) {
      // badge 是更短的状态枚举文案，和 formatStatusMessage 一起构成 UI 层状态表达。
      if (user && user.enabled != null && Number(user.enabled) !== 1) return '已停用'
      const s = String((user && user.status) || '').toLowerCase()
      if (s === 'approved') return '已认证'
      if (s === 'pending') return '审核中'
      if (s === 'rejected') return '未通过'
      if (s === 'revoked') return '资格已收回'
      if (s === 'blacklisted') return '黑名单'
      return '未申请'
    },
    buildDeviceContext() {
      /*
       * 设备上下文会跟随 entry/apply/login 一起提交给后端，
       * 供 session 创建和后续问题排查使用。
       */
      return {
        deviceId: this.getOrCreateDeviceId(),
        deviceName: this.resolveDeviceName(),
        userAgent: this.resolveUserAgent()
      }
    },
    getOrCreateDeviceId() {
      let saved = String(uni.getStorageSync(DEVICE_ID_KEY) || '').trim()
      if (saved) {
        return saved
      }
      saved = `miniapp-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
      uni.setStorageSync(DEVICE_ID_KEY, saved)
      return saved
    },
    resolveDeviceName() {
      return buildDeviceName()
    },
    resolveUserAgent() {
      return buildUserAgent()
    },
    wait(ms) {
      return new Promise((resolve) => setTimeout(resolve, Math.max(0, Number(ms) || 0)))
    }
  }
}
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background: var(--dh-color-bg);
}

.content {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 20rpx 24rpx 28rpx;
  box-sizing: border-box;
}

.content::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.login-state-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  background:
    radial-gradient(circle at 50% 26%, rgba(90, 138, 72, 0.14), transparent 34%),
    rgba(246, 249, 244, 0.9);
  backdrop-filter: blur(18rpx);
}

.login-state-shell {
  width: 100%;
  max-width: 520rpx;
  padding: 52rpx 40rpx 44rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.88);
  border: 1rpx solid rgba(168, 191, 154, 0.18);
  box-shadow: 0 20rpx 60rpx rgba(56, 78, 47, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.login-state-orb-wrap {
  position: relative;
  width: 184rpx;
  height: 184rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-state-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2rpx solid rgba(84, 132, 66, 0.14);
}

.ring-1 {
  animation: login-state-breathe 1.8s ease-in-out infinite;
}

.ring-2 {
  inset: 18rpx;
  animation: login-state-breathe 1.8s ease-in-out infinite 0.24s;
}

.login-state-orb {
  width: 116rpx;
  height: 116rpx;
  border-radius: 58rpx;
  background: linear-gradient(180deg, #f1f8ec 0%, #dcefd0 100%);
  box-shadow: 0 14rpx 34rpx rgba(79, 127, 54, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: login-state-float 2.2s ease-in-out infinite;
}

.login-state-brand {
  font-size: 50rpx;
  font-weight: 600;
  color: #416b2c;
}

.login-state-title {
  margin-top: 18rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #2c3a26;
}

.login-state-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #728068;
}

.login-state-overlay.is-success .login-state-orb {
  background: linear-gradient(180deg, #eff8e8 0%, #d3ebc4 100%);
}

@keyframes login-state-float {
  0%, 100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(0, -8rpx, 0) scale(1.02);
  }
}

@keyframes login-state-breathe {
  0%, 100% {
    transform: scale(0.96);
    opacity: 0.34;
  }
  50% {
    transform: scale(1.04);
    opacity: 0.16;
  }
}

.hero-card,
.card,
.status-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
  border: 1rpx solid var(--dh-color-border);
}

.hero-card {
  background: linear-gradient(180deg, #f5fbf0 0%, #ffffff 100%);
}

.card,
.status-card {
  margin-top: 16rpx;
}

.hero-title,
.section-title,
.status-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #2c3a26;
}

.hero-desc,
.section-sub,
.status-text,
.notice-text,
.login-note {
  margin-top: 8rpx;
  display: block;
  font-size: 24rpx;
  color: #73806a;
  line-height: 1.6;
}

.section-head {
  margin-bottom: 12rpx;
}

.apply-card {
  padding-top: 24rpx;
}

.form-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.form-row {
  padding: 18rpx 18rpx 20rpx;
  border-radius: 18rpx;
  background: #f8faf5;
  border: 1rpx solid rgba(162, 182, 152, 0.18);
}

.label {
  display: block;
  font-size: 24rpx;
  color: #60705b;
  font-weight: 500;
}

.input {
  margin-top: 8rpx;
  height: 74rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1rpx solid var(--dh-color-border);
  padding: 0 18rpx;
  font-size: 28rpx;
}

.textarea {
  margin-top: 8rpx;
  width: 100%;
  min-height: 132rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1rpx solid var(--dh-color-border);
  padding: 16rpx 18rpx;
  font-size: 27rpx;
  box-sizing: border-box;
}

.login-user {
  margin-top: 10rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.avatar-preview,
.avatar-placeholder {
  width: 108rpx;
  height: 108rpx;
  border-radius: 54rpx;
  flex: 0 0 auto;
}

.avatar-placeholder {
  background: var(--dh-color-brand-light);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7f8b72;
  font-size: 24rpx;
}

.mini-btn {
  min-width: 186rpx;
  height: 68rpx;
  line-height: 68rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
  font-size: 24rpx;
  border: 1rpx solid rgba(115, 174, 82, 0.2);
}

.login-card {
  padding-top: 24rpx;
}

.submitted-card,
.rejected-card,
.disabled-card,
.revoked-card,
.blacklisted-card {
  padding: 44rpx 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.submitted-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef8e8;
  color: #4b9419;
  font-size: 58rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 36rpx rgba(75, 148, 25, 0.12);
}

.rejected-icon,
.disabled-icon,
.revoked-icon,
.blacklisted-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 58rpx;
  font-weight: 700;
}

.rejected-icon {
  background: #fff5e8;
  color: #d9822b;
  box-shadow: 0 18rpx 36rpx rgba(217, 130, 43, 0.12);
}

.disabled-icon {
  background: #faecec;
  color: #cc4c4c;
  box-shadow: 0 18rpx 36rpx rgba(204, 76, 76, 0.12);
}

.revoked-icon {
  background: #eef5e6;
  color: #4f7f36;
  box-shadow: 0 18rpx 36rpx rgba(79, 127, 54, 0.12);
}

.blacklisted-icon {
  background: #f5efef;
  color: #7d3d3d;
  box-shadow: 0 18rpx 36rpx rgba(125, 61, 61, 0.12);
}

.submitted-title {
  margin-top: 20rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #2c3a26;
}

.submitted-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #73806a;
}

.submitted-action {
  margin-top: 24rpx;
  min-width: 360rpx;
}

.login-user-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.login-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2c3a26;
}

.login-meta {
  font-size: 24rpx;
  color: #73806a;
}

.login-status,
.status-badge {
  min-height: 40rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: #eef5e6;
  color: #4f7f36;
  font-size: 22rpx;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}

.notice-card {
  background: #fcfdf9;
}

.agreement-card {
  background: #ffffff;
}

.agreement-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-height: 40rpx;
}

.agreement-copy {
  flex: 1;
  min-width: 0;
}

.agreement-row :deep(.t-checkbox) {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
}

.agreement-row :deep(.t-checkbox__icon-wrap) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.agreement-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
  min-width: 0;
}

.agreement-text,
.agreement-hint {
  font-size: 23rpx;
  line-height: 1.5;
  color: #73806a;
}

.agreement-link {
  font-size: 23rpx;
  line-height: 1.5;
  color: var(--dh-color-brand);
  font-weight: 600;
}

.agreement-hint {
  display: block;
  margin-top: 10rpx;
}

.footer-actions {
  padding-bottom: calc(64rpx + env(safe-area-inset-bottom));
}

.notice-title {
  display: block;
  font-size: 28rpx;
  color: #33422d;
  font-weight: 600;
}

.submit,
.minor {
  margin-top: 16rpx;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.submit {
  background: var(--dh-color-brand);
  color: #fff;
}

.minor {
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
}

.status-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.elder-mode {
  .hero-title,
  .section-title,
  .status-title,
  .login-name {
    font-size: 38rpx;
  }

  .hero-desc,
  .section-sub,
  .status-text,
  .notice-text,
  .label,
  .agreement-text,
  .agreement-link,
  .agreement-hint,
  .mini-btn,
  .status-badge,
  .login-meta,
  .login-note,
  .login-status,
  .login-state-desc,
  .submitted-desc {
    font-size: 30rpx;
  }

  .login-state-title {
    font-size: 36rpx;
  }

  .input,
  .textarea,
  .submit,
  .minor {
    font-size: 32rpx;
  }

  .input,
  .submit,
  .minor,
  .mini-btn {
    height: 86rpx;
    line-height: 86rpx;
  }

  .hero-card,
  .card,
  .status-card {
    border-radius: 24rpx;
    padding: 24rpx;
  }

  .submitted-title {
    font-size: 40rpx;
  }

  .submitted-icon,
  .rejected-icon,
  .disabled-icon,
  .revoked-icon,
  .blacklisted-icon {
    width: 128rpx;
    height: 128rpx;
    border-radius: 64rpx;
    font-size: 64rpx;
  }
}
</style>
