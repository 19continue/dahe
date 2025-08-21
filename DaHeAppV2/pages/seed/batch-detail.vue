<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="批次详情"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="content-shell">
      <dh-reveal-panel :loading="batchLoading" class="batch-reveal-panel">
        <template #skeleton>
          <view class="batch-card batch-card-skeleton">
            <dh-card-skeleton-list
              :count="1"
              item-min-height="172rpx"
              body-min-height="136rpx"
              item-padding="18rpx"
              item-radius="22rpx"
              :row-col="batchHeaderSkeletonRows"
            />
          </view>
        </template>

        <view class="batch-card" v-if="batch">
          <view class="batch-head">
            <view class="batch-main">
              <text class="batch-title">{{ batch.batchCode || '未设置批次号' }}</text>
              <text class="batch-sub">生产日期：{{ batch.productionDate || '未填写' }}</text>
            </view>
            <t-tag v-if="batchTagText" class="batch-tag" size="small" theme="primary" variant="light">
              {{ batchTagText }}
            </t-tag>
          </view>

          <view v-if="batchDynamicPairs.length" class="batch-extra-wrap">
            <view v-for="item in batchDynamicPairs" :key="item.key" class="batch-extra-item">
              <text class="batch-extra-label">{{ item.label }}</text>
              <text class="batch-extra-value">{{ item.value }}</text>
            </view>
          </view>

          <view v-if="batch.remark" class="batch-remark">
            <text class="batch-remark-label">备注</text>
            <text class="batch-remark-value">{{ batch.remark }}</text>
          </view>
        </view>
      </dh-reveal-panel>

      <view class="section-header">
        <text class="section-title">检测记录</text>
        <view class="actions">
          <button class="add-btn light" v-if="canManage" @click="goEditBatch">编辑批次</button>
          <button class="add-btn" @click="goAddTest">新增检测</button>
        </view>
      </view>

      <scroll-view scroll-y class="list" :show-scrollbar="false">
        <dh-reveal-panel :loading="loading" class="list-reveal-panel">
          <template #skeleton>
            <view class="list-skeleton">
              <dh-card-skeleton-list
                :count="3"
                item-min-height="176rpx"
                body-min-height="140rpx"
                item-padding="18rpx"
                item-radius="18rpx"
                :row-col="testSkeletonRows"
              />
            </view>
          </template>

          <template v-if="tests.length">
            <view v-for="item in tests" :key="item.id" class="test-card">
              <view class="line1">
                <text class="date">{{ item.testDate }}</text>
                <text class="tester" v-if="item.testerName">检测员：{{ item.testerName }}</text>
              </view>
              <view class="line2">
                <view v-if="item.germinationRate != null" class="metric-pill metric-pill-primary">芽率 {{ item.germinationRate }}%</view>
                <view v-if="item.moisture != null" class="metric-pill">水分 {{ item.moisture }}%</view>
                <view v-if="item.purity != null" class="metric-pill">纯度 {{ item.purity }}%</view>
                <view v-if="item.cleanliness != null" class="metric-pill">净度 {{ item.cleanliness }}%</view>
              </view>
              <view class="line3" v-if="item.sampleCount != null || item.germinationCount != null">
                <text>芽率样本 {{ item.sampleCount != null ? item.sampleCount : '-' }} 粒</text>
                <text class="line3-sep">·</text>
                <text>发芽 {{ item.germinationCount != null ? item.germinationCount : '-' }} 粒</text>
              </view>
              <view class="line3" v-for="extra in testDynamicPairs(item)" :key="`${item.id}_${extra.key}`">
                {{ extra.label }}：{{ extra.value }}
              </view>
              <view class="line3" v-if="item.remark">备注：{{ item.remark }}</view>
              <view class="ops" v-if="canManage">
                <text class="op" @click="goEditTest(item)">编辑</text>
                <text class="op danger" @click="deleteTest(item)">删除</text>
              </view>
            </view>
          </template>
          <view v-else class="empty">暂无检测记录</view>
        </dh-reveal-panel>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { canManageSeedBatch, isApprovedUser } from '../../utils/auth'
import { formatCropVarietyPair } from '../../utils/crop-variety'
import DhCardSkeletonList from '../../components/dh-card-skeleton-list.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import elderPageMixin from '../../utils/elder-page'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'

const BATCH_HEADER_SKELETON_ROWS = [
  [
    { width: '62%', height: '30rpx', borderRadius: '12rpx', marginRight: '18rpx' },
    { width: '24%', height: '28rpx', borderRadius: '999rpx' }
  ],
  { width: '36%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '48%', height: '58rpx', margin: '16rpx 4% 0 0', borderRadius: '16rpx' },
    { width: '48%', height: '58rpx', margin: '16rpx 0 0 0', borderRadius: '16rpx' }
  ]
]

const TEST_CARD_SKELETON_ROWS = [
  [
    { width: '28%', height: '26rpx', borderRadius: '10rpx', marginRight: '18rpx' },
    { width: '36%', height: '22rpx', borderRadius: '10rpx' }
  ],
  [
    { width: '26%', height: '28rpx', margin: '14rpx 12rpx 0 0', borderRadius: '999rpx' },
    { width: '24%', height: '28rpx', margin: '14rpx 12rpx 0 0', borderRadius: '999rpx' },
    { width: '24%', height: '28rpx', margin: '14rpx 0 0 0', borderRadius: '999rpx' }
  ],
  { width: '76%', height: '22rpx', margin: '16rpx 0 0 0', borderRadius: '10rpx' },
  { width: '58%', height: '22rpx', margin: '10rpx 0 0 0', borderRadius: '10rpx' }
]

export default {
  components: {
    DhCardSkeletonList,
    DhRevealPanel
  },
  mixins: [elderPageMixin],
  data() {
    return {
      batchId: null,
      batch: null,
      batchLoading: true,
      batchHeaderSkeletonRows: BATCH_HEADER_SKELETON_ROWS,
      tests: [],
      loading: true,
      canManage: false,
      testSkeletonRows: TEST_CARD_SKELETON_ROWS,
      initialized: false,
      refreshMark: ''
    }
  },
  computed: {
    batchDynamicPairs() {
      return this.resolveDynamicPairs(this.batch)
    },
    batchTagText() {
      return this.batch ? formatCropVarietyPair(this.batch, '') : ''
    }
  },
  onLoad(query) {
    if (query && query.id) this.batchId = query.id
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    this.canManage = canManageSeedBatch()
    if (!this.batchId) return
    const topic = refreshTopics.seedBatchDetail(this.batchId)
    if (!this.initialized) {
      this.initialized = true
      this.reloadPageData().then(() => {
        this.refreshMark = readRefreshMark(topic)
      })
      return
    }
    if (hasDataChanged(topic, this.refreshMark)) {
      this.reloadPageData({ silent: true }).then(() => {
        this.refreshMark = readRefreshMark(topic)
      })
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async fetchBatch(options = {}) {
      const silent = !!(options && options.silent)
      if (!silent) {
        this.batchLoading = true
      }
      try {
        this.batch = await api.get(`/miniapp/seed-batches/${this.batchId}`)
      } catch (error) {
        console.error('加载批次详情失败', error)
        this.batch = null
      } finally {
        if (!silent) {
          this.batchLoading = false
        }
      }
    },
    async fetchTests(options = {}) {
      const silent = !!(options && options.silent)
      if (!silent) {
        this.loading = true
      }
      try {
        this.tests = (await api.get(`/miniapp/seed-batches/${this.batchId}/tests`)) || []
      } catch (error) {
        console.error('加载检测记录失败', error)
        this.tests = []
      } finally {
        if (!silent) {
          this.loading = false
        }
      }
    },
    async reloadPageData(options = {}) {
      const silent = !!(options && options.silent)
      if (!silent) {
        this.batch = null
        this.tests = []
        this.batchLoading = true
        this.loading = true
      }
      await Promise.all([
        this.fetchBatch({ silent }),
        this.fetchTests({ silent })
      ])
    },
    resolveDynamicPairs(row) {
      if (!row) return []
      const schema = this.parseSchema(row.formSchema)
      const values = this.parseJson(row.extraJson)
      if (!schema.length || !values) return []
      return schema
        .map((field) => {
          const value = values[field.key]
          if (value == null || String(value).trim() === '') return null
          return {
            key: field.key,
            label: field.label || field.key,
            value: String(value)
          }
        })
        .filter(Boolean)
    },
    parseSchema(text) {
      if (!text) return []
      try {
        const rows = JSON.parse(text)
        return Array.isArray(rows) ? rows.filter((x) => x && x.key) : []
      } catch (e) {
        return []
      }
    },
    parseJson(text) {
      if (!text) return null
      try {
        const data = JSON.parse(text)
        return data && typeof data === 'object' ? data : null
      } catch (e) {
        return null
      }
    },
    testDynamicPairs(item) {
      return this.resolveDynamicPairs(item)
    },
    goEditBatch() {
      if (!this.batchId) return
      uni.navigateTo({ url: `/pages/seed/batch-edit?id=${this.batchId}` })
    },
    goAddTest() {
      uni.navigateTo({ url: `/pages/seed/test-edit?batchId=${this.batchId}` })
    },
    goEditTest(item) {
      if (!item || !item.id) return
      uni.navigateTo({ url: `/pages/seed/test-edit?batchId=${this.batchId}&testId=${item.id}` })
    },
    deleteTest(item) {
      if (!item || !item.id) return
      uni.showModal({
        title: '确认删除',
        content: `确定删除 ${item.testDate || ''} 的检测记录吗？`,
        success: async (res) => {
          if (!res.confirm) return
          try {
            await api.delete(`/miniapp/seed-batches/${this.batchId}/tests/${item.id}`, {})
            uni.showToast({ title: '已删除', icon: 'success' })
            await this.fetchTests()
          } catch (e) {
            console.error('删除检测失败', e)
          }
        }
      })
    },
  }
}
</script>

<style lang="scss">
.page {
  background: var(--dh-color-bg);
}

.content-shell {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 16rpx 24rpx 24rpx;
  display: flex;
  flex-direction: column;
}

.batch-card {
  background: #fff;
  border-radius: 22rpx;
  border: 1rpx solid var(--dh-color-border);
  padding: 18rpx;
  min-height: 172rpx;
}

.batch-card-skeleton {
  padding: 14rpx;
}

.batch-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14rpx;
}

.batch-main {
  flex: 1;
  min-width: 0;
}

.batch-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #223021;
  line-height: 1.38;
}

.batch-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6d7a68;
  line-height: 1.5;
}

.batch-tag {
  flex-shrink: 0;
  --td-tag-primary-color: #527647;
  --td-tag-primary-light-color: #edf6e7;
}

.batch-extra-wrap {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.batch-extra-item {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f6f8f4;
  border: 1rpx solid #e1e7dc;
}

.batch-extra-label {
  display: block;
  font-size: 22rpx;
  color: #84917d;
  line-height: 1.35;
}

.batch-extra-value {
  display: block;
  margin-top: 6rpx;
  font-size: 25rpx;
  color: #223021;
  font-weight: 600;
  line-height: 1.45;
}

.batch-remark {
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #e6ece0;
}

.batch-remark-label {
  display: block;
  font-size: 22rpx;
  color: #8b9585;
}

.batch-remark-value {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #4d5a46;
  line-height: 1.6;
}

.section-header {
  padding: 14rpx 2rpx 10rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  gap: 10rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2c3a26;
}

.add-btn {
  height: 58rpx;
  line-height: 58rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: var(--dh-color-brand);
  background: #fff;
  border: 1rpx solid var(--dh-color-brand);
}

.add-btn.light {
  border-color: #b7c7a8;
  color: #5a6e4a;
}

.list {
  flex: 1;
  min-height: 0;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.list::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.test-card {
  background: #fff;
  border-radius: 18rpx;
  border: 1rpx solid var(--dh-color-border);
  padding: 18rpx;
  min-height: 176rpx;
  margin-bottom: 10rpx;
}

.line1 {
  display: flex;
  justify-content: space-between;
}

.date {
  font-size: 27rpx;
  font-weight: 700;
  color: #2c3a26;
}

.tester {
  font-size: 22rpx;
  color: #6c7a65;
}

.line2 {
  margin-top: 8rpx;
  display: flex;
  gap: 10rpx;
  flex-wrap: wrap;
}

.metric-pill {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #5e6e57;
  background: #f4f6f2;
  border: 1rpx solid #e2e7dc;
}

.metric-pill-primary {
  color: #315c2c;
  background: #eef7e9;
  border-color: #d1e5c7;
}

.line3 {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #77846f;
}

.line3-sep {
  padding: 0 8rpx;
}

.ops {
  margin-top: 10rpx;
  display: flex;
  gap: 20rpx;
}

.op {
  font-size: 24rpx;
  color: var(--dh-color-brand);
}

.op.danger {
  color: #d64545;
}

.empty {
  text-align: center;
  padding: 100rpx 0;
  color: #87937f;
}

.list-skeleton {
  padding-top: 4rpx;
}

.batch-reveal-panel,
.list-reveal-panel {
  display: block;
}

.elder-mode {
  .batch-title,
  .section-title,
  .date {
    font-size: 36rpx;
  }

  .batch-sub,
  .batch-extra-label,
  .batch-extra-value,
  .batch-remark-label,
  .batch-remark-value,
  .tester,
  .metric-pill,
  .line3,
  .empty {
    font-size: 30rpx;
  }

  .batch-card,
  .test-card,
  .batch-extra-item {
    border-radius: 24rpx;
  }
}
</style>
