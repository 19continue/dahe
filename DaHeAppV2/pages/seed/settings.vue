<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="种子检测规则"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <dh-reveal-panel :loading="!loaded" class="content-reveal-panel">
      <template #skeleton>
        <view class="loading loading-skeleton">
          <dh-card-skeleton-list
            :count="3"
            item-min-height="148rpx"
            body-min-height="112rpx"
            item-padding="16rpx"
            item-radius="16rpx"
            :row-col="settingsSkeletonRows"
          />
        </view>
      </template>

      <view class="content">
        <view class="card">
          <view class="line between">
            <text class="label">固定样本数模式</text>
            <switch :checked="Number(rule.fixedSampleSize) === 1" @change="onFixedChange" color="#73AE52" :disabled="!isAdmin" />
          </view>
          <text class="hint">开启后，每次检测样本数自动使用默认值，不允许手动填写。</text>
        </view>

        <view class="card">
          <text class="label">默认样本数（粒）</text>
          <input class="input" type="number" v-model="rule.defaultSampleSize" :disabled="!isAdmin" placeholder="例如 100" />
        </view>

        <view class="card">
          <text class="label">备注</text>
          <textarea class="textarea" v-model="rule.remark" :disabled="!isAdmin" placeholder="可选" />
        </view>

        <button class="save" :disabled="saving || !isAdmin" @click="save">{{ saving ? '保存中...' : '保存规则' }}</button>
        <text class="not-admin" v-if="!isAdmin">仅管理员可修改规则</text>
      </view>
    </dh-reveal-panel>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isAdminUser, isApprovedUser } from '../../utils/auth'
import DhCardSkeletonList from '../../components/dh-card-skeleton-list.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'

import elderPageMixin from '../../utils/elder-page'

const SETTINGS_SKELETON_ROWS = [
  [
    { width: '46%', height: '26rpx', borderRadius: '12rpx', marginRight: '18rpx' },
    { width: '18%', height: '26rpx', borderRadius: '999rpx' }
  ],
  { width: '76%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' }
]

export default {
  components: {
    DhCardSkeletonList,
    DhRevealPanel
  },
  mixins: [elderPageMixin],
  data() {
    return {
      rule: {
        fixedSampleSize: 1,
        defaultSampleSize: 100,
        remark: ''
      },
      loaded: false,
      saving: false,
      isAdmin: false,
      settingsSkeletonRows: SETTINGS_SKELETON_ROWS
    }
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    this.isAdmin = isAdminUser()
    this.fetchRule()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async fetchRule() {
      this.loaded = false
      try {
        const data = await api.get('/miniapp/seed-settings')
        this.rule = {
          fixedSampleSize: Number((data && data.fixedSampleSize) || 1),
          defaultSampleSize: Number((data && data.defaultSampleSize) || 100),
          remark: (data && data.remark) || ''
        }
      } catch (e) {
        console.error('加载检测规则失败', e)
      } finally {
        this.loaded = true
      }
    },
    onFixedChange(e) {
      this.rule.fixedSampleSize = e.detail.value ? 1 : 0
    },
    async save() {
      if (!this.isAdmin) return
      const count = Number(this.rule.defaultSampleSize)
      if (!count || count <= 0) {
        uni.showToast({ title: '默认样本数必须大于0', icon: 'none' })
        return
      }
      this.saving = true
      try {
        const data = await api.put('/miniapp/seed-settings', {
          fixedSampleSize: Number(this.rule.fixedSampleSize) === 1 ? 1 : 0,
          defaultSampleSize: count,
          remark: this.rule.remark
        })
        this.rule = {
          fixedSampleSize: Number((data && data.fixedSampleSize) || 1),
          defaultSampleSize: Number((data && data.defaultSampleSize) || 100),
          remark: (data && data.remark) || ''
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
      } catch (e) {
        console.error('保存检测规则失败', e)
      } finally {
        this.saving = false
      }
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
  padding: 18rpx 24rpx;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 16rpx;
  margin-bottom: 10rpx;
}

.line {
  display: flex;
  align-items: center;
}

.between {
  justify-content: space-between;
}

.label {
  font-size: 26rpx;
  color: #34422d;
}

.hint {
  margin-top: 8rpx;
  display: block;
  font-size: 23rpx;
  color: #66755f;
}

.input {
  margin-top: 8rpx;
  height: 66rpx;
  border-radius: 12rpx;
  background: var(--dh-color-surface-soft);
  padding: 0 14rpx;
  font-size: 28rpx;
}

.textarea {
  margin-top: 8rpx;
  width: 100%;
  min-height: 120rpx;
  border-radius: 12rpx;
  background: var(--dh-color-surface-soft);
  padding: 10rpx 14rpx;
  font-size: 26rpx;
}

.save {
  margin-top: 12rpx;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand);
  color: #fff;
  font-size: 28rpx;
}

.save[disabled] {
  opacity: 0.6;
}

.not-admin {
  margin-top: 8rpx;
  display: block;
  text-align: center;
  color: #8b9583;
  font-size: 23rpx;
}

.loading {
  text-align: center;
  color: #84907d;
  font-size: 28rpx;
  padding: 120rpx 0;
}

.loading-skeleton {
  text-align: left;
  padding: 18rpx 24rpx;
}

.content-reveal-panel {
  display: block;
}
</style>
