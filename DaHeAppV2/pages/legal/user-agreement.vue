<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="用户服务协议"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="hero-card">
        <text class="hero-title">用户服务协议</text>
        <text class="hero-desc">请在提交认证或登录前仔细阅读本协议，确认你了解本小程序的服务范围、使用规则和用户责任。</text>
      </view>

      <view class="meta-card">
        <text class="meta-label">运营主体</text>
        <text class="meta-value">{{ companyName }}</text>
        <text class="meta-label">生效日期</text>
        <text class="meta-value">2026-03-11</text>
      </view>

      <view class="section-card" v-for="section in sections" :key="section.title">
        <text class="section-title">{{ section.title }}</text>
        <view class="paragraph-list">
          <text class="paragraph" v-for="(item, index) in section.content" :key="`${section.title}-${index}`">{{ item }}</text>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">联系与反馈</text>
        <view class="contact-list">
          <view class="contact-row" v-if="contacts.length === 0">
            <text class="contact-label">联系方式</text>
            <text class="contact-value">暂未配置，请联系企业管理员。</text>
          </view>
          <view class="contact-row" v-for="(contact, index) in contacts" :key="`${contact.contactLabel}-${index}`">
            <text class="contact-label">{{ contact.contactLabel || '联系方式' }}</text>
            <text class="contact-value">{{ contact.contactValue || '-' }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import elderPageMixin from '../../utils/elder-page'
import { buildUserAgreementSections, loadLegalCompanyProfile } from '../../utils/legal-content'

export default {
  mixins: [elderPageMixin],
  data() {
    return {
      companyName: '大禾种业',
      contacts: [],
      sections: buildUserAgreementSections('大禾种业')
    }
  },
  async onLoad() {
    const profile = await loadLegalCompanyProfile()
    this.companyName = profile.companyName
    this.contacts = profile.contacts
    this.sections = buildUserAgreementSections(this.companyName)
  },
  methods: {
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
.meta-card,
.section-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 26rpx;
  box-sizing: border-box;
}

.hero-card {
  background: linear-gradient(180deg, #f4faf0 0%, #ffffff 100%);
}

.meta-card,
.section-card {
  margin-top: 18rpx;
}

.hero-title,
.section-title,
.meta-label,
.contact-label {
  color: #20311b;
}

.hero-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
}

.hero-desc,
.meta-value,
.paragraph,
.contact-value {
  color: #5f7358;
}

.hero-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  line-height: 1.7;
}

.meta-label,
.contact-label {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
}

.meta-value,
.contact-value {
  display: block;
  margin-top: 8rpx;
  font-size: 25rpx;
  line-height: 1.7;
}

.meta-value + .meta-label {
  margin-top: 18rpx;
}

.section-title {
  display: block;
  font-size: 29rpx;
  font-weight: 700;
}

.paragraph-list,
.contact-list {
  margin-top: 14rpx;
}

.paragraph,
.contact-row {
  display: block;
}

.paragraph {
  font-size: 25rpx;
  line-height: 1.85;
}

.paragraph + .paragraph,
.contact-row + .contact-row {
  margin-top: 14rpx;
}

.elder-mode {
  .hero-title {
    font-size: 42rpx;
  }

  .hero-desc,
  .meta-label,
  .meta-value,
  .section-title,
  .paragraph,
  .contact-label,
  .contact-value {
    font-size: 30rpx;
  }
}
</style>
