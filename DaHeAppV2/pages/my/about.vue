<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="关于我们"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="hero-card">
        <text class="hero-title">大禾种业 V2</text>
        <text class="hero-sub">服务田间管理、农事记录与种子质量追溯</text>
        <text class="hero-version">版本 2.0.0</text>
      </view>

      <view class="section-card">
        <text class="section-title">小程序功能介绍</text>
        <text class="section-content">
          大禾种业小程序围绕“田块、农事、批次、检测”四条主线展开，帮助一线用户把现场信息及时记录下来，并在后续追溯、复盘和协同中快速找到对应数据。
        </text>
        <view class="feature-list">
          <view v-for="item in featureList" :key="item.title" class="feature-item">
            <text class="feature-title">{{ item.title }}</text>
            <text class="feature-desc">{{ item.desc }}</text>
          </view>
        </view>
        <text class="section-subtitle">为什么开发这个小程序</text>
        <text class="section-content">
          农事现场常常分散在不同田块和不同时间点，传统口头沟通与纸面记录容易遗漏，也难以追踪。这个小程序的目标是把田间执行、位置天气、图片证据和种子质量信息串成一条可持续维护的业务链路，让现场操作更清晰、责任更明确、复盘更方便。
        </text>
      </view>

      <view class="section-card">
        <text class="section-title">隐私政策</text>
        <view class="policy-list">
          <view v-for="item in privacyPolicies" :key="item.title" class="policy-item">
            <text class="policy-title">{{ item.title }}</text>
            <text class="policy-desc">{{ item.desc }}</text>
          </view>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">公司简单信息和联系方式</text>
        <view class="info-list">
          <view class="info-row">
            <text class="info-label">公司名称</text>
            <text class="info-value">{{ companyInfo.companyName || '大禾种业' }}</text>
          </view>
          <view class="info-row" v-if="companyInfo.introduction">
            <text class="info-label">公司简介</text>
            <text class="info-value multiline">{{ companyInfo.introduction }}</text>
          </view>
          <view class="info-row" v-if="contacts.length === 0 && !loading">
            <text class="info-label">联系方式</text>
            <text class="info-value">暂未配置</text>
          </view>
          <view class="info-row" v-for="(contact, index) in contacts" :key="`${contact.contactLabel}-${index}`">
            <text class="info-label">{{ contact.contactLabel || '联系方式' }}</text>
            <text class="info-value multiline">{{ contact.contactValue || '-' }}</text>
          </view>
        </view>
        <view class="footer">
          <text>Copyright © 2026 大禾种业</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import request from '../../utils/request'
import elderPageMixin from '../../utils/elder-page'

export default {
  mixins: [elderPageMixin],
  data() {
    return {
      loading: false,
      companyInfo: {
        companyName: '大禾种业',
        introduction: ''
      },
      contacts: [],
      featureList: [
        {
          title: '田块管理更直观',
          desc: '按乡镇、作物、品种、阶段和距离查看田块，快速确认当前要处理的地块。'
        },
        {
          title: '农事记录更完整',
          desc: '围绕流程步骤录入时间、参数、图片、天气和备注，减少现场遗漏。'
        },
        {
          title: '种子质量更好追溯',
          desc: '围绕批次查看检测历史和关键信息，方便现场核对与后续复盘。'
        }
      ],
      privacyPolicies: [
        {
          title: '仅采集业务必要信息',
          desc: '小程序只在完成认证、记录农事、定位田块、上传图片等业务环节采集必要数据，不做与业务无关的额外采集。'
        },
        {
          title: '位置和图片仅用于业务记录',
          desc: '位置、天气和图片信息用于辅助还原田间现场情况，帮助后续追溯、审核和复盘。'
        },
        {
          title: '账号准入与权限受控',
          desc: '只有通过审核的用户才可登录小程序，后台和小程序权限分离，避免无关人员访问业务数据。'
        },
        {
          title: '如需更正可联系企业管理员',
          desc: '如果认证信息、联系方式或业务数据需要更正，可通过企业提供的联系方式联系处理。'
        }
      ]
    }
  },
  onLoad() {
    this.loadCompanySummary()
  },
  methods: {
    async loadCompanySummary() {
      try {
        this.loading = true
        const data = await request.get('/miniapp/public/company-intro')
        const nextCompany = (data && data.companyInfo) || {}
        this.companyInfo = {
          companyName: nextCompany.companyName || '大禾种业',
          introduction: String(nextCompany.introduction || '').trim()
        }
        this.contacts = Array.isArray(data && data.contacts) ? data.contacts : []
      } catch (error) {
        console.error('load about company summary failed', error)
        this.companyInfo = {
          companyName: '大禾种业',
          introduction: ''
        }
        this.contacts = []
      } finally {
        this.loading = false
      }
    },
    goBack() {
      uni.navigateBack()
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
  box-sizing: border-box;
  padding: 18rpx 24rpx calc(36rpx + env(safe-area-inset-bottom));
}

.content::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.hero-card,
.section-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx;
  box-sizing: border-box;
}

.hero-card {
  background: linear-gradient(180deg, #eef7ea 0%, #f8fbf6 100%);
}

.section-card {
  margin-top: 18rpx;
}

.hero-title,
.section-title,
.feature-title,
.policy-title,
.info-label {
  color: #20311b;
}

.hero-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
}

.hero-sub,
.section-content,
.feature-desc,
.policy-desc,
.info-value,
.hero-version,
.footer text {
  color: #5f7358;
}

.hero-sub {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  line-height: 1.7;
}

.hero-version {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
}

.section-subtitle {
  display: block;
  margin-top: 22rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #2f4728;
}

.section-content {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.85;
}

.feature-list,
.policy-list,
.info-list {
  margin-top: 16rpx;
}

.feature-item,
.policy-item,
.info-row {
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(46, 79, 38, 0.08);
}

.feature-item:last-child,
.policy-item:last-child,
.info-row:last-child {
  border-bottom: none;
}

.feature-title,
.policy-title,
.info-label {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
}

.feature-desc,
.policy-desc,
.info-value {
  display: block;
  margin-top: 8rpx;
  font-size: 25rpx;
  line-height: 1.8;
}

.info-value.multiline {
  white-space: pre-wrap;
}

.footer {
  margin-top: 18rpx;
  padding-top: 18rpx;
}

.footer text {
  font-size: 23rpx;
}

.elder-mode {
  .hero-title {
    font-size: 44rpx;
  }

  .hero-sub,
  .section-content,
  .feature-desc,
  .policy-desc,
  .info-value,
  .hero-version,
  .footer text {
    font-size: 30rpx;
  }

  .section-title,
  .section-subtitle,
  .feature-title,
  .policy-title,
  .info-label {
    font-size: 34rpx;
  }
}
</style>
