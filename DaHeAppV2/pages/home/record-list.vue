<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      toolbar
      :title="navbarTitle"
      :fixed="true"
      :safe-area-inset-top="true"
      :elder-mode="elderMode"
      left-arrow
      @go-back="goBack"
    />

    <view class="content-shell">
      <view class="toolbar card">
        <view class="toolbar-head">
          <view>
            <text class="toolbar-title">{{ toolbarTitle }}</text>
            <text class="toolbar-desc">{{ toolbarDesc }}</text>
          </view>
          <text class="toolbar-total">共 {{ total }} 条</text>
        </view>

        <view class="filter-row">
          <field-selector
            ref="fieldSelectorRef"
            class="field-filter"
            :hide-trigger="true"
            :value="selectedFieldId || null"
            :selected-field-info="selectedFieldInfo"
            :include-disabled="includeDisabledFields"
            :elder-mode="elderMode"
            title="筛选田块"
            placeholder="全部田块"
            @change="onFieldSelectorChange"
          />
          <view class="field-filter-entry" @tap="openFieldSelectorPopup">
            <text class="field-filter-entry-text">{{ selectedFieldId ? '更换田块' : '选择田块' }}</text>
            <t-icon name="chevron-right" size="32rpx" />
          </view>
        </view>

        <view v-if="selectedFieldId" class="pick-card filter-pick-card" @tap="openFieldSelectorPopup">
          <view class="pick-main">
            <view class="field-cover">
              <image
                v-if="selectedFieldCoverUrl"
                class="field-cover-image"
                :src="selectedFieldCoverUrl"
                mode="aspectFill"
              />
              <view v-else class="field-cover-placeholder">{{ selectedFieldAreaLabel }}</view>
            </view>
            <view class="pick-info">
              <text class="pick-title">{{ selectedFieldLabel }}</text>
              <text class="pick-meta">{{ selectedFieldSubText }}</text>
              <text class="pick-meta" v-if="selectedFieldLocationText">{{ selectedFieldLocationText }}</text>
              <view class="pick-tags" v-if="selectedFieldCropTags.length">
                <t-tag
                  v-for="label in selectedFieldCropTags"
                  :key="`record-filter-${label}`"
                  size="small"
                  theme="success"
                  variant="light"
                >
                  {{ label }}
                </t-tag>
              </view>
            </view>
          </view>
          <view class="pick-side">
            <t-button class="pick-change-btn" theme="primary" variant="text" :size="elderMode ? 'medium' : 'small'" @click.stop="openFieldSelectorPopup">
              更换
            </t-button>
            <view class="pick-clear-btn" @tap.stop="clearFieldFilter">
              <t-icon name="close" size="30rpx" />
            </view>
          </view>
        </view>

        <view v-if="activeFilterItems.length" class="filter-tags">
          <t-tag
            v-for="item in activeFilterItems"
            :key="item.key"
            class="filter-chip-tag"
            :size="elderMode ? 'extra-large' : 'large'"
            theme="primary"
            variant="light"
            shape="round"
            closable
            @close="clearFilter(item.key)"
          >
            {{ item.label }}
          </t-tag>
        </view>
      </view>

      <view class="guide-card">
        <text class="guide-text">{{ guideText }}</text>
      </view>

      <scroll-view
        scroll-y
        class="list"
        :show-scrollbar="false"
        :enable-back-to-top="true"
        enhanced
        :fast-deceleration="true"
        :bounces="true"
        :lower-threshold="120"
        @scrolltolower="loadMore"
      >
        <dh-reveal-panel :loading="loading && !records.length" class="list-reveal-panel">
          <template #skeleton>
            <view class="record-skeleton-stack">
              <view v-for="idx in 4" :key="`record-skeleton-${idx}`" class="field-card record-card record-card-skeleton">
                <t-skeleton theme="paragraph" animation="flashed" :row-col="recordSkeletonRows" />
              </view>
            </view>
          </template>

          <template v-if="records.length">
            <view v-for="item in records" :key="item.id" class="field-card record-card" @click="goDetail(item.id)">
              <view class="record-head">
                <view class="record-head-main">
                  <text class="type">{{ recordTitle(item) }}</text>
                  <text class="record-field">{{ recordFieldName(item) }}</text>
                </view>
                <text class="perm" :class="(item.canEdit || item.canDelete) ? 'yes' : 'no'">
                  {{ (item.canEdit || item.canDelete) ? '可编辑' : '只读' }}
                </text>
              </view>

              <view class="meta-grid">
                <view class="meta-item">
                  <text class="meta-label">作业时间</text>
                  <text class="meta-value">{{ formatDate(item.workDate) }}</text>
                </view>
                <view class="meta-item">
                  <text class="meta-label">执行人员</text>
                  <text class="meta-value">{{ item.operatorName || '未填操作员' }}</text>
                </view>
                <view class="meta-item">
                  <text class="meta-label">天气</text>
                  <text class="meta-value">{{ item.weather || '未记录' }}</text>
                </view>
                <view class="meta-item">
                  <text class="meta-label">作业步骤</text>
                  <text class="meta-value">{{ recordTitle(item) }}</text>
                </view>
              </view>
            </view>
          </template>
          <view v-else class="state-card empty-card">
            <text class="empty-title">{{ emptyTitle }}</text>
            <text class="empty-desc">{{ emptyDesc }}</text>
          </view>
        </dh-reveal-panel>
        <view v-if="loading && records.length" class="load-more-skeleton">
          <view class="record-skeleton-stack compact">
            <view v-for="idx in 2" :key="`record-more-skeleton-${idx}`" class="field-card record-card record-card-skeleton">
              <t-skeleton theme="paragraph" animation="flashed" :row-col="recordSkeletonRows" />
            </view>
          </view>
        </view>
        <view v-else-if="records.length && !finished" class="state-tip">上拉加载更多</view>
        <view v-else-if="finished && records.length" class="state-tip">已加载全部</view>
      </scroll-view>
    </view>

    <view class="action-wrap">
      <t-button class="add-btn" theme="primary" block @click="goCreate">新建农事</t-button>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isElderMode } from '../../utils/accessibility'
import FieldSelector from '../../components/field-selector/field-selector.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import { resolveCropVarietyLabels } from '../../utils/crop-variety'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'

const RECORD_CARD_SKELETON_ROWS = [
  [
    { width: '56%', height: '30rpx', borderRadius: '12rpx', marginRight: '16rpx' },
    { width: '22%', height: '28rpx', borderRadius: '999rpx' }
  ],
  { width: '38%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '48%', height: '76rpx', margin: '16rpx 4% 0 0', borderRadius: '16rpx' },
    { width: '48%', height: '76rpx', margin: '16rpx 0 0 0', borderRadius: '16rpx' }
  ],
  [
    { width: '48%', height: '76rpx', margin: '10rpx 4% 0 0', borderRadius: '16rpx' },
    { width: '48%', height: '76rpx', margin: '10rpx 0 0 0', borderRadius: '16rpx' }
  ]
]

export default {
  components: {
    FieldSelector,
    DhRevealPanel
  },
  data() {
    return {
      loading: true,
      records: [],
      selectedFieldInfo: null,
      page: 1,
      pageSize: 15,
      total: 0,
      finished: false,
      recordSkeletonRows: RECORD_CARD_SKELETON_ROWS,
      prefillFieldId: null,
      prefillCycleId: null,
      includeDisabledFields: false,
      mineOnly: false,
      elderMode: false,
      initialized: false,
      refreshMark: ''
    }
  },
  computed: {
    selectedFieldId() {
      return this.selectedFieldInfo && this.selectedFieldInfo.id != null ? this.normalizeId(this.selectedFieldInfo.id) : ''
    },
    selectedFieldLabel() {
      return String((this.selectedFieldInfo && this.selectedFieldInfo.name) || '').trim() || '全部田块'
    },
    navbarTitle() {
      return this.mineOnly ? '我的农事记录' : '农事管理'
    },
    toolbarTitle() {
      return this.mineOnly ? '我的农事记录' : '农事记录'
    },
    toolbarDesc() {
      if (this.mineOnly) {
        return '只查看本人已提交的农事记录，并可继续补录当前田块记录。'
      }
      return '按田块查看农事记录，并支持继续新增当前田块的记录。'
    },
    guideText() {
      if (this.mineOnly) {
        return '提示：这里只展示你自己的农事记录；满足时间窗口和策略条件时才允许编辑或删除。'
      }
      return '提示：点击记录可查看详情；仅满足条件的本人记录才允许编辑或删除。'
    },
    emptyTitle() {
      return this.mineOnly ? '暂无我的农事记录' : '暂无匹配记录'
    },
    emptyDesc() {
      if (this.mineOnly) {
        return '可以切换田块重新查询，或直接新建一条自己的农事记录。'
      }
      return '可以切换田块重新查询，或直接新建一条记录。'
    },
    activeFilterItems() {
      const list = []
      if (this.prefillCycleId) {
        list.push({ key: 'cycle', label: '当前种植计划' })
      }
      return list
    },
    selectedFieldCoverUrl() {
      return this.resolveFieldCoverUrl(this.selectedFieldInfo)
    },
    selectedFieldAreaLabel() {
      const area = Number((this.selectedFieldInfo && this.selectedFieldInfo.areaMu) || 0)
      if (Number.isFinite(area) && area > 0) {
        return `${area}亩`
      }
      return '田块'
    },
    selectedFieldSubText() {
      if (!this.selectedFieldInfo) return '请选择田块'
      const planName = String(
        (this.selectedFieldInfo.currentPlanName || this.selectedFieldInfo.currentCycleName || this.selectedFieldInfo.planName || this.selectedFieldInfo.cycleName || '')
      ).trim()
      const area = Number(this.selectedFieldInfo.areaMu || 0)
      const parts = []
      if (planName) parts.push(planName)
      if (Number.isFinite(area) && area > 0) parts.push(`${area}亩`)
      return parts.join(' / ') || '已选择作业田块'
    },
    selectedFieldLocationText() {
      if (!this.selectedFieldInfo) return ''
      return String(
        this.selectedFieldInfo.locationDesc
        || this.selectedFieldInfo.formattedAddress
        || [this.selectedFieldInfo.province, this.selectedFieldInfo.city, this.selectedFieldInfo.district, this.selectedFieldInfo.township]
          .map((item) => String(item || '').trim())
          .filter(Boolean)
          .join(' · ')
      ).trim()
    },
    selectedFieldCropTags() {
      return this.selectedFieldInfo ? resolveCropVarietyLabels(this.selectedFieldInfo, 6) : []
    }
  },
  async onLoad(query) {
    this.prefillFieldId = this.normalizeId(query && query.fieldId)
    this.prefillCycleId = this.normalizeId(query && query.cycleId)
    this.mineOnly = String((query && (query.mine || query.mineOnly)) || '').trim() === '1'
      || String((query && (query.mine || query.mineOnly)) || '').trim().toLowerCase() === 'true'
    this.includeDisabledFields = !!this.prefillFieldId
  },
  async onShow() {
    this.elderMode = isElderMode()
    const topic = refreshTopics.farmRecords()
    if (!this.initialized) {
      await this.ensureSelectedFieldInfo()
      await this.refresh()
      this.refreshMark = readRefreshMark(topic)
      this.initialized = true
      return
    }
    if (hasDataChanged(topic, this.refreshMark)) {
      await this.ensureSelectedFieldInfo()
      await this.refresh()
      this.refreshMark = readRefreshMark(topic)
    }
  },
  methods: {
    normalizeId(value) {
      const text = String(value == null ? '' : value).trim()
      return text || ''
    },
    sameId(left, right) {
      return this.normalizeId(left) === this.normalizeId(right)
    },
    goBack() {
      uni.navigateBack()
    },
    openFieldSelectorPopup() {
      const ref = this.$refs.fieldSelectorRef
      if (ref && typeof ref.openPopup === 'function') {
        ref.openPopup()
      }
    },
    goCreate() {
      const query = []
      if (this.selectedFieldId) query.push(`fieldId=${this.selectedFieldId}`)
      if (this.prefillCycleId) query.push(`cycleId=${this.prefillCycleId}`)
      uni.navigateTo({ url: `/pages/home/record-edit${query.length ? `?${query.join('&')}` : ''}` })
    },
    goDetail(id) {
      uni.navigateTo({ url: `/pages/home/record-detail?id=${id}` })
    },
    async ensureSelectedFieldInfo() {
      if (!this.prefillFieldId || this.selectedFieldInfo) return
      try {
        const params = this.includeDisabledFields ? { includeDisabled: true } : {}
        const detail = await api.get(`/miniapp/fields/${this.prefillFieldId}`, params)
        if (detail && detail.id != null) {
          this.selectedFieldInfo = detail
          return
        }
      } catch (e) {
        console.error('加载预设田块失败', e)
      }
      this.selectedFieldInfo = {
        id: this.prefillFieldId,
        name: `田块#${this.prefillFieldId}`
      }
    },
    async onFieldSelectorChange(field) {
      if (!field || field.id == null) return
      this.selectedFieldInfo = {
        ...(field || {})
      }
      await this.refresh()
    },
    async clearFieldFilter() {
      this.selectedFieldInfo = null
      this.prefillFieldId = null
      this.includeDisabledFields = false
      await this.refresh()
    },
    async clearFilter(key) {
      const name = String(key || '').trim()
      if (name === 'cycle') {
        this.prefillCycleId = null
      }
      await this.refresh()
    },
    async refresh() {
      this.loading = true
      this.page = 1
      this.finished = false
      this.records = []
      await this.fetchRecords(true)
    },
    async loadMore() {
      if (this.loading || this.finished) return
      this.page += 1
      await this.fetchRecords(false)
    },
    async fetchRecords(reset) {
      this.loading = true
      try {
        const res = await api.get('/miniapp/farm-records', {
          page: this.page,
          pageSize: this.pageSize,
          fieldId: this.selectedFieldId || undefined,
          cycleId: this.prefillCycleId || undefined,
          mineOnly: this.mineOnly ? true : undefined
        })
        const rows = (res && res.records) || []
        this.total = res ? res.total || 0 : 0
        this.records = reset ? rows : this.records.concat(rows)
        this.finished = this.records.length >= this.total || rows.length < this.pageSize
      } catch (e) {
        console.error('加载农事记录失败', e)
      } finally {
        this.loading = false
      }
    },
    formatDate(v) {
      return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
    },
    recordTitle(item) {
      const stepName = String((item && item.stepName) || '').trim()
      if (stepName) return stepName
      return '农事记录'
    },
    recordFieldName(item) {
      const fieldName = String((item && item.fieldName) || '').trim()
      if (fieldName) return fieldName
      if (this.selectedFieldId && this.sameId(item && item.fieldId, this.selectedFieldId)) {
        return this.selectedFieldLabel
      }
      const fieldId = this.normalizeId(item && item.fieldId)
      return fieldId ? `田块#${fieldId}` : '未关联田块'
    },
    resolveAssetHost() {
      const base = String(api.getBaseUrl() || '').trim().replace(/\/+$/, '')
      if (!base) return ''
      return base
        .replace(/\/api\/v2\/miniapp$/i, '')
        .replace(/\/api\/v2\/admin$/i, '')
        .replace(/\/api\/v2$/i, '')
        .replace(/\/api$/i, '')
    },
    resolveAssetUrl(rawUrl) {
      const url = String(rawUrl || '').trim()
      if (!url) return ''
      if (/^(https?:|data:|wxfile:|file:)/i.test(url)) return url
      if (url.startsWith('//')) return `https:${url}`
      const host = this.resolveAssetHost()
      if (!host) return url
      if (url.startsWith('/')) return `${host}${url}`
      return `${host}/${url}`
    },
    resolveFieldCoverUrl(field) {
      if (!field) return ''
      const candidate = [
        field.coverImageUrl,
        field.imageUrl,
        field.coverUrl,
        field.cover,
        field.thumbUrl,
        field.fileUrl
      ].find((value) => String(value || '').trim())
      return this.resolveAssetUrl(candidate)
    }
  }
}
</script>

<style lang="scss">
.page {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: var(--dh-color-bg);
}

.content-shell {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 18rpx 24rpx calc(146rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.toolbar {
  padding: 18rpx;
  border-radius: 20rpx;
}

.toolbar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.toolbar-title {
  display: block;
  font-size: 30rpx;
  color: #2b3825;
  font-weight: 700;
}

.toolbar-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #74816d;
  line-height: 1.5;
}

.toolbar-total {
  flex-shrink: 0;
  font-size: 24rpx;
  color: #5e8245;
  font-weight: 700;
}

.filter-row {
  margin-top: 14rpx;
  display: block;
}

.field-filter {
  height: 0;
  overflow: hidden;
}

.field-filter-entry {
  min-height: 76rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  border: 1rpx solid var(--dh-color-border);
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.field-filter-entry-text {
  font-size: 26rpx;
  color: #2f4029;
  font-weight: 600;
}

.filter-pick-card {
  margin-top: 12rpx;
  padding: 16rpx;
  border-radius: 18rpx;
  border: 1rpx solid var(--dh-color-border);
  background: #ffffff;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.filter-tags {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.filter-chip-tag {
  --td-tag-primary-color: #527647;
  --td-tag-primary-light-color: #edf6e7;
  min-height: 56rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
}

.pick-main {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 12rpx;
}

.field-cover {
  width: 176rpx;
  height: 132rpx;
  border-radius: 12rpx;
  border: 1rpx solid var(--dh-color-border);
  background: #eef3ea;
  overflow: hidden;
  flex-shrink: 0;
}

.field-cover-image {
  width: 100%;
  height: 100%;
  display: block;
}

.field-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #62705f;
  font-size: 24rpx;
  font-weight: 600;
}

.pick-info {
  flex: 1;
  min-width: 0;
  min-height: 132rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.pick-title {
  display: block;
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 700;
  line-height: 1.38;
}

.pick-meta {
  margin-top: 6rpx;
  display: block;
  font-size: 23rpx;
  color: #62705f;
  line-height: 1.45;
}

.pick-tags {
  margin-top: 9rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.pick-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
  flex-shrink: 0;
}

.pick-change-btn {
  --td-button-text-color: var(--dh-color-brand);
  --td-button-text-active-color: #4d7a36;
  --td-button-text-active-bg-color: #dcefd9;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #e7f3e5;
}

.pick-clear-btn {
  width: 44rpx;
  height: 44rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eff2ef;
  color: #6e7770;
}

.guide-card {
  margin-top: 12rpx;
  padding: 14rpx 16rpx;
  border-radius: 16rpx;
  background: #fcfdf9;
  border: 1rpx solid var(--dh-color-border);
}

.guide-text {
  display: block;
  font-size: 23rpx;
  color: #6f7c67;
  line-height: 1.55;
}

.list {
  flex: 1;
  min-height: 0;
  margin-top: 14rpx;
  box-sizing: border-box;
}

.list::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.record-card {
  margin-bottom: 12rpx;
  padding: 18rpx;
  min-height: 286rpx;
  border-radius: 20rpx;
}

.record-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.record-head-main {
  flex: 1;
  min-width: 0;
}

.type {
  display: block;
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 700;
  line-height: 1.4;
}

.record-field {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #5f8145;
}

.perm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.perm.yes {
  background: var(--dh-color-brand-light);
  color: #4d7a36;
}

.perm.no {
  background: #eff2ef;
  color: #6e7770;
}

.meta-grid {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.meta-item {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f7faf6;
  border: 1rpx solid #ebf0e6;
}

.meta-label {
  display: block;
  font-size: 22rpx;
  color: #81907c;
}

.meta-value {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #394734;
  font-weight: 600;
  line-height: 1.45;
}

.state-card {
  margin-top: 6rpx;
  padding: 36rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1rpx solid var(--dh-color-border);
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
}

.state-card-skeleton {
  padding: 14rpx;
}

.record-skeleton-stack {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 2rpx 0 6rpx;
}

.record-skeleton-stack.compact {
  padding: 6rpx 0 0;
}

.record-card.record-card-skeleton {
  overflow: hidden;
  --td-skeleton-bg-color: #d6e0d1;
  --td-skeleton-animation-flashed: rgba(168, 181, 162, 0.62);
}

.record-card.record-card-skeleton :deep(.t-skeleton) {
  width: 100%;
}

.load-more-skeleton {
  padding: 6rpx 0 12rpx;
}

.list-reveal-panel {
  display: block;
}

.empty-card {
  margin-top: 0;
}

.empty-title {
  display: block;
  font-size: 28rpx;
  color: #36412f;
  font-weight: 700;
}

.empty-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #7b8774;
  line-height: 1.55;
}

.state-tip {
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
  padding: 18rpx 0 calc(32rpx + env(safe-area-inset-bottom));
}

.action-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12rpx 24rpx calc(26rpx + env(safe-area-inset-bottom));
  z-index: 120;
  background: rgba(243, 244, 246, 0.98);
  border-top: 1rpx solid var(--dh-color-border);
}

.add-btn {
  --td-button-primary-bg-color: var(--dh-color-brand);
  --td-button-primary-border-color: var(--dh-color-brand);
  --td-button-primary-active-bg-color: #5e9240;
  --td-button-primary-active-border-color: #5e9240;
  --td-button-large-height: 86rpx;
  border-radius: 16rpx;
}

@media screen and (max-width: 768rpx) {
  .meta-grid {
    grid-template-columns: 1fr;
  }
}

.elder-mode {

  .toolbar-title,
  .type,
  .empty-title {
    font-size: 36rpx;
  }

  .toolbar-desc,
  .toolbar-total,
  .field-filter-entry-text,
  .guide-text,
  .record-field,
  .perm,
  .meta-label,
  .meta-value,
  .state-card,
  .empty-desc,
  .state-tip,
  .pick-meta {
    font-size: 30rpx;
  }

  .pick-title {
    font-size: 36rpx;
  }

  .meta-item,
  .toolbar,
  .record-card,
  .state-card {
    border-radius: 22rpx;
  }

  .filter-pick-card,
  .field-cover {
    border-radius: 22rpx;
  }

  .field-cover {
    width: 198rpx;
    height: 152rpx;
  }

  .filter-chip-tag {
    min-height: 66rpx;
  }
}
</style>
