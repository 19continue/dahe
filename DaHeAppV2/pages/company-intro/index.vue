<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="企业介绍"
      :fixed="true"
      :safe-area-inset-top="true"
      :elder-mode="elderMode"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <dh-reveal-panel :loading="loading" class="content-reveal-panel">
        <template #skeleton>
          <view class="company-banner-skeleton">
            <t-skeleton theme="image" animation="flashed" />
          </view>
          <view class="company-name company-name-skeleton">
            <view class="logo-skeleton">
              <t-skeleton theme="image" animation="flashed" />
            </view>
            <view class="company-name-copy">
              <t-skeleton theme="paragraph" animation="flashed" :row-col="companyNameSkeletonRows" />
            </view>
          </view>
          <view class="section section-skeleton" v-for="idx in 3" :key="`intro-skeleton-${idx}`">
            <t-skeleton theme="paragraph" animation="flashed" :row-col="introSkeletonRows" />
          </view>
        </template>

        <image class="company-banner" :src="companyInfo.banner" mode="aspectFill"></image>

        <view class="company-name">
          <image class="logo" :src="companyInfo.logo" mode="aspectFit"></image>
          <text class="name">{{ companyInfo.companyName || '大禾种业' }}</text>
        </view>

        <view class="section" v-if="companyInfo.introduction">
          <text class="section-title">公司简介</text>
          <text class="section-content">{{ companyInfo.introduction }}</text>
        </view>

        <view class="section" v-if="companyInfo.mission">
          <text class="section-title">企业使命</text>
          <text class="section-content">{{ companyInfo.mission }}</text>
        </view>

        <view class="section" v-if="products.length > 0">
          <text class="section-title">核心产品</text>
          <view class="product-list">
            <view class="product-item" v-for="(product, index) in products" :key="index">
              <image class="product-image" :src="product.image" mode="aspectFill"></image>
              <view class="product-info">
                <text class="product-name">{{ product.name }}</text>
                <text class="product-desc">{{ product.description || product.desc }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="section" v-if="honors.length > 0">
          <text class="section-title">荣誉资质</text>
          <scroll-view scroll-x class="honors-scroll">
            <view class="honors-list">
              <view class="honor-item" v-for="(honor, index) in honors" :key="index" @click="previewImage(honor.image)">
                <image :src="honor.image" mode="aspectFill"></image>
                <text class="honor-name">{{ honor.name }}</text>
              </view>
            </view>
          </scroll-view>
        </view>

        <view class="section" v-if="contacts.length > 0">
          <text class="section-title">联系我们</text>
          <view class="contact-item" v-for="(contact, index) in contacts" :key="index">
            <text class="contact-label">{{ contact.contactLabel }}：</text>
            <text class="contact-value">{{ contact.contactValue }}</text>
          </view>
        </view>

        <view class="footer" v-if="companyInfo.copyright">
          <text>{{ companyInfo.copyright }}</text>
        </view>
      </dh-reveal-panel>
    </scroll-view>
  </view>
</template>

<script>
import request from '../../utils/request'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import elderPageMixin from '../../utils/elder-page'

const INTRO_SKELETON_ROWS = [
  { width: '34%', height: '28rpx', borderRadius: '12rpx' },
  { width: '88%', height: '22rpx', margin: '18rpx 0 0 0', borderRadius: '10rpx' },
  { width: '92%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  { width: '74%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' }
]
const COMPANY_NAME_SKELETON_ROWS = [
  { width: '46%', height: '30rpx', borderRadius: '12rpx' },
  { width: '30%', height: '22rpx', margin: '14rpx 0 0 0', borderRadius: '10rpx' }
]

export default {
  components: {
    DhRevealPanel
  },
  mixins: [elderPageMixin],
  data() {
    return {
      companyInfo: {
        companyName: '大禾种业',
        logo: '/static/images/farm.png',
        banner: '/static/images/farm.png',
        introduction: '',
        mission: '',
        copyright: ''
      },
      products: [],
      honors: [],
      contacts: [],
      loading: true,
      introSkeletonRows: INTRO_SKELETON_ROWS,
      companyNameSkeletonRows: COMPANY_NAME_SKELETON_ROWS
    }
  },
  onLoad() {
    this.loadCompanyIntro()
  },
  methods: {
    async loadCompanyIntro() {
      try {
        this.loading = true
        const data = await request.get('/miniapp/public/company-intro')
        if (data) {
          if (data.companyInfo) {
            this.companyInfo = {
              companyName: data.companyInfo.companyName || '大禾种业',
              logo: data.companyInfo.logo || '/static/images/farm.png',
              banner: data.companyInfo.banner || '/static/images/farm.png',
              introduction: data.companyInfo.introduction || '',
              mission: data.companyInfo.mission || '',
              copyright: data.companyInfo.copyright || ''
            }
          }
          this.products = (data.products || []).map((product) => ({
            name: product.name,
            description: product.description,
            desc: product.description,
            image: product.image || '/static/images/farm.png'
          }))
          this.honors = (data.honors || []).map((honor) => ({
            name: honor.name,
            image: honor.image || '/static/images/farm.png'
          }))
          this.contacts = data.contacts || []
        } else {
          uni.showToast({
            title: '加载失败',
            icon: 'none'
          })
        }
      } catch (error) {
        console.error('加载公司介绍失败:', error)
        uni.showToast({
          title: '加载失败，请重试',
          icon: 'none'
        })
      } finally {
        this.loading = false
      }
    },
    goBack() {
      uni.navigateBack()
    },
    previewImage(image) {
      uni.previewImage({
        urls: [image]
      })
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

.company-banner {
  width: 100%;
  height: 360rpx;
  border-radius: 22rpx;
}

.company-banner-skeleton {
  width: 100%;
  height: 360rpx;
  border-radius: 22rpx;
  overflow: hidden;
  background: #ffffff;
}

.company-name {
  display: flex;
  align-items: center;
  background-color: white;
  margin-top: 18rpx;
  padding: 30rpx;
  border-radius: 22rpx;

  .logo {
    width: 120rpx;
    height: 120rpx;
    border-radius: 60rpx;
    margin-right: 30rpx;
    border: 4rpx solid #8fc31f;
  }

  .name {
    font-size: 40rpx;
    font-weight: 600;
    color: #333;
  }
}

.section {
  background-color: white;
  margin-top: 20rpx;
  padding: 30rpx;
  border-radius: 22rpx;

  .section-title {
    font-size: 34rpx;
    font-weight: 600;
    color: #333;
    margin-bottom: 20rpx;
    display: block;
    position: relative;
    padding-left: 20rpx;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 6rpx;
      width: 8rpx;
      height: 34rpx;
      background-color: #8fc31f;
      border-radius: 4rpx;
    }
  }

  .section-content {
    font-size: 30rpx;
    color: #666;
    line-height: 1.8;
    text-align: justify;
  }

  .product-list {
    .product-item {
      display: flex;
      align-items: center;
      margin-bottom: 30rpx;
      border-bottom: 1rpx solid #f0f0f0;
      padding-bottom: 30rpx;

      &:last-child {
        margin-bottom: 0;
        border-bottom: none;
        padding-bottom: 0;
      }

      .product-image {
        width: 160rpx;
        height: 160rpx;
        border-radius: 12rpx;
        margin-right: 20rpx;
      }

      .product-info {
        flex: 1;

        .product-name {
          font-size: 32rpx;
          font-weight: 600;
          color: #333;
          margin-bottom: 10rpx;
          display: block;
        }

        .product-desc {
          font-size: 28rpx;
          color: #666;
          line-height: 1.6;
        }
      }
    }
  }

  .honors-scroll {
    white-space: nowrap;

    .honors-list {
      display: inline-flex;
      padding: 20rpx 0;

      .honor-item {
        margin-right: 30rpx;
        width: 220rpx;

        &:last-child {
          margin-right: 0;
        }

        image {
          width: 220rpx;
          height: 160rpx;
          border-radius: 12rpx;
          margin-bottom: 10rpx;
        }

        .honor-name {
          font-size: 26rpx;
          color: #666;
          display: block;
          text-align: center;
          white-space: normal;
        }
      }
    }
  }

  .contact-item {
    margin-bottom: 20rpx;

    &:last-child {
      margin-bottom: 0;
    }

    .contact-label {
      font-size: 28rpx;
      color: #333;
      font-weight: 600;
      margin-right: 10rpx;
    }

    .contact-value {
      font-size: 28rpx;
      color: #666;
    }
  }
}

.footer {
  background-color: #f8f8f8;
  margin-top: 20rpx;
  padding: 40rpx;
  border-radius: 22rpx;
  text-align: center;

  text {
    font-size: 24rpx;
    color: #999;
  }
}

.company-name-skeleton {
  margin-top: 18rpx;
}

.logo-skeleton {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  overflow: hidden;
  flex: 0 0 auto;
}

.company-name-copy {
  flex: 1;
  min-width: 0;
}

.section-skeleton {
  margin-top: 20rpx;
}

.content-reveal-panel {
  display: block;
}

.elder-mode {
  .company-name {
    .name {
      font-size: 46rpx;
    }
  }

  .section {
    .section-title {
      font-size: 40rpx;
    }

    .section-content,
    .contact-label,
    .contact-value,
    .product-desc,
    .honor-name {
      font-size: 32rpx;
    }

    .product-name {
      font-size: 36rpx;
    }
  }

  .footer text {
    font-size: 28rpx;
  }
}
</style>
