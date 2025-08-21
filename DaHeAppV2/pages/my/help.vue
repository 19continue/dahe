<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="帮助中心"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="hero-card">
        <text class="hero-title">围绕真实农事场景设计</text>
        <text class="hero-desc">
          这个小程序主要用于田块管理、农事记录、种子质量追溯和位置天气辅助，目标是让田间操作有记录、可追溯、能协同。
        </text>
      </view>

      <view class="section-card">
        <text class="section-title">核心使用路径</text>
        <view class="guide-list">
          <view v-for="item in guideList" :key="item.title" class="guide-item">
            <view class="guide-index">{{ item.index }}</view>
            <view class="guide-body">
              <text class="guide-title">{{ item.title }}</text>
              <text class="guide-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="section-card">
        <text class="section-title">常见问题</text>
        <view
          v-for="(item, index) in faqList"
          :key="item.question"
          class="faq-item"
          @click="toggleFaq(index)"
        >
          <view class="faq-head">
            <text class="faq-question">{{ item.question }}</text>
            <text class="faq-arrow">{{ item.expanded ? '收起' : '展开' }}</text>
          </view>
          <text v-if="item.expanded" class="faq-answer">{{ item.answer }}</text>
        </view>
      </view>

      <view class="section-card safety-card">
        <text class="section-title">遇到问题先看这里</text>
        <view class="tip-row" v-for="item in tips" :key="item.title">
          <text class="tip-title">{{ item.title }}</text>
          <text class="tip-desc">{{ item.desc }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import elderPageMixin from '../../utils/elder-page'

export default {
  mixins: [elderPageMixin],
  data() {
    return {
      guideList: [
        {
          index: '01',
          title: '先完成认证，再进入微信登录',
          desc: '未通过审核的用户会停留在认证页；审核通过后，使用微信授权即可进入小程序。'
        },
        {
          index: '02',
          title: '按田块组织生产信息',
          desc: '田块分布页支持按乡镇、作物、品种、阶段和距离查看，方便快速定位目标田块。'
        },
        {
          index: '03',
          title: '围绕流程录入农事记录',
          desc: '新增农事时先选田块，再选步骤，系统会协助带出天气、位置和必填参数。'
        },
        {
          index: '04',
          title: '从种子批次一直追到检测结果',
          desc: '种子质量页可查看批次、检测记录和时间信息，用于追溯与现场核对。'
        }
      ],
      faqList: [
        {
          question: '为什么我还不能直接登录？',
          answer: '小程序账号采用“先认证、后登录”的准入方式。未提交认证、审核中或审核未通过时，都不会放行到业务页面。',
          expanded: true
        },
        {
          question: '搜索不到田块怎么办？',
          answer: '先检查是否输入了完整关键词，再配合作物、品种、乡镇或阶段筛选。空关键词不会触发搜索；如果田块刚改过信息，返回列表后会自动刷新。',
          expanded: false
        },
        {
          question: '农事记录里图片、天气和位置是做什么的？',
          answer: '图片用于补充现场证据，天气和位置用于还原记录发生时的环境背景，便于后续核查和复盘。',
          expanded: false
        },
        {
          question: '为什么批次和检测记录要分开看？',
          answer: '批次是种子生产与流转的主线，检测记录是某次质量检查的结果。两者拆开后，既方便追踪历史，也便于继续补录新检测。',
          expanded: false
        }
      ],
      tips: [
        {
          title: '登录异常',
          desc: '先确认认证状态是否已通过，再重新进入微信授权页。'
        },
        {
          title: '定位异常',
          desc: '请检查小程序位置权限，必要时在系统设置中重新授权。'
        },
        {
          title: '数据没更新',
          desc: '返回上一页后页面会按变更标记刷新；如果刚刚改的是首页相关数据，回到首页稍等片刻即可完成静默更新。'
        }
      ]
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    toggleFaq(index) {
      this.faqList = this.faqList.map((item, itemIndex) => ({
        ...item,
        expanded: itemIndex === index ? !item.expanded : item.expanded
      }))
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
.guide-title,
.tip-title,
.faq-question {
  color: #20311b;
}

.hero-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
}

.hero-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #5f7358;
}

.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
}

.guide-list,
.safety-card {
  margin-top: 14rpx;
}

.guide-item {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(46, 79, 38, 0.08);
}

.guide-item:last-child,
.faq-item:last-child {
  border-bottom: none;
}

.guide-index {
  width: 68rpx;
  min-width: 68rpx;
  height: 68rpx;
  border-radius: 20rpx;
  background: #ebf5e6;
  color: #2d6c2f;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.guide-body {
  flex: 1;
}

.guide-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}

.guide-desc,
.tip-desc,
.faq-answer {
  display: block;
  margin-top: 8rpx;
  font-size: 25rpx;
  line-height: 1.8;
  color: #5f7358;
}

.faq-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid rgba(46, 79, 38, 0.08);
}

.faq-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.faq-question {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.6;
}

.faq-arrow {
  color: #6b8663;
  font-size: 24rpx;
}

.tip-row {
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(46, 79, 38, 0.08);
}

.tip-row:last-child {
  border-bottom: none;
}

.tip-title {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
}

.elder-mode {
  .hero-title {
    font-size: 40rpx;
  }

  .hero-desc,
  .guide-desc,
  .tip-desc,
  .faq-answer {
    font-size: 30rpx;
  }

  .section-title,
  .guide-title,
  .faq-question,
  .tip-title {
    font-size: 34rpx;
  }

  .faq-arrow,
  .guide-index {
    font-size: 28rpx;
  }

  .guide-index {
    width: 80rpx;
    min-width: 80rpx;
    height: 80rpx;
  }
}
</style>
