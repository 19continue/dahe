<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="控制台"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="content">
      <view class="card admin" v-if="isAdmin" @click="goUserManage">
        <text class="name">账号审核与角色</text>
        <text class="desc">审核新用户申请，分配角色与控制台权限</text>
      </view>

      <view class="card" @click="goFieldManage">
        <text class="name">田块定位与种植计划</text>
        <text class="desc">补录田块定位，更新作物信息并切换当前种植计划</text>
      </view>

      <view class="card" @click="goSeedManage">
        <text class="name">种子批次管理</text>
        <text class="desc">维护种子批次与检测记录，支持回溯追踪</text>
      </view>

      <view class="card admin" v-if="isAdmin" @click="goSeedRule">
        <text class="name">种子检测规则</text>
        <text class="desc">配置样本数规则：固定样本数或每次填写</text>
      </view>

    </view>
  </view>
</template>

<script>
import { canUseConsole, isAdminUser, isApprovedUser } from '../../utils/auth'

import elderPageMixin from '../../utils/elder-page'
export default {
  mixins: [elderPageMixin],
  data() {
    return {
      isAdmin: false
    }
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    if (!canUseConsole()) {
      uni.showToast({ title: '当前账号未开通控制台权限', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 220)
      return
    }
    this.isAdmin = isAdminUser()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    goUserManage() {
      uni.navigateTo({ url: '/pages/my/user-manage' })
    },
    goFieldManage() {
      uni.navigateTo({ url: '/pages/my/field-manage' })
    },
    goSeedManage() {
      uni.navigateTo({ url: '/pages/seed/index?console=1' })
    },
    goSeedRule() {
      uni.navigateTo({ url: '/pages/seed/settings' })
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
  padding: 24rpx;
}

.card {
  background: #fff;
  border-radius: 18rpx;
  padding: 18rpx;
  margin-bottom: 12rpx;
}

.card.admin {
  border: 1rpx solid #e4d184;
  background: #fff9e7;
}

.name {
  display: block;
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 700;
}

.desc {
  margin-top: 8rpx;
  display: block;
  font-size: 24rpx;
  color: #63715d;
}
</style>
