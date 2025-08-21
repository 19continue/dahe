<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      toolbar
      title="种子质量"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="content-shell">
      <view class="toolbar card">
        <search-assist
          ref="searchAssist"
          class="batch-search"
          :value="keyword"
          placeholder="搜索批次号或品种名称"
          :helper-text="searchFeedbackText"
          :suggestions="keywordSuggestions"
          :history-items="keywordHistory"
          :elder-mode="elderMode"
          @change="onKeywordChange"
          @submit="onKeywordSubmit"
          @clear="onKeywordClear"
          @history-click="onHistoryClick"
          @history-clear="onHistoryClear"
          @history-remove="onHistoryRemove"
          @suggestion-click="onSuggestionClick"
        />

        <view class="toolbar-meta">
          <text class="toolbar-title">{{ toolbarTitle }}</text>
          <text class="toolbar-sub">{{ toolbarSubText }}</text>
        </view>

        <view class="filter-menu">
          <filter-dropdown-item
            class="filter-menu-item"
            ref="cropDropdownItem"
            label="作物"
            :options="cropDropdownOptions"
            :value="selectedCropName"
            :elder-mode="elderMode"
            empty-text="暂无作物"
            @open="ensureCropOptions"
            @select="onCropSelect"
          />
          <filter-dropdown-item
            class="filter-menu-item"
            ref="varietyDropdownItem"
            label="品种"
            :options="varietyDropdownOptions"
            :value="selectedVarietyName"
            :elder-mode="elderMode"
            empty-text="暂无品种"
            @open="ensureVarietyOptions"
            @select="onVarietySelect"
          />
        </view>

        <view v-if="activeFilterItems.length || canManage" class="toolbar-foot">
          <view class="filter-summary" v-if="activeFilterItems.length">
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
          <t-button
            v-if="canManage"
            class="manage-btn"
            theme="primary"
            variant="outline"
            shape="round"
            :size="elderMode ? 'large' : 'medium'"
            @click="goCreateBatch"
          >
            新增批次
          </t-button>
        </view>
      </view>

      <scroll-view scroll-y class="list" :show-scrollbar="false" :enable-back-to-top="true" enhanced :fast-deceleration="true" :bounces="true" :lower-threshold="120" @scroll="dismissSearchSuggestions" @scrolltolower="loadMore">
        <dh-reveal-panel :loading="loading && !batches.length" class="list-reveal-panel">
          <template #skeleton>
            <view class="batch-skeleton-stack">
              <view v-for="idx in 4" :key="`batch-skeleton-${idx}`" class="batch-card batch-card-skeleton">
                <t-skeleton theme="paragraph" animation="flashed" :row-col="batchSkeletonRows" />
              </view>
            </view>
          </template>

          <template v-if="batches.length">
            <view v-for="item in batches" :key="item.id" class="batch-card" @click="goDetail(item)">
              <view class="batch-head">
                <view class="batch-main">
                  <text class="code">{{ item.batchCode || '未设置批次号' }}</text>
                  <text class="batch-code">生产日期：{{ item.productionDate || '未填写' }}</text>
                  <text class="batch-note">{{ item.remark || '点击查看检测记录与批次详情' }}</text>
                </view>
                <t-tag v-if="cropVarietyText(item)" class="batch-pair-tag" size="small" theme="primary" variant="light">
                  {{ cropVarietyText(item) }}
                </t-tag>
              </view>
            </view>
          </template>
          <view v-else class="state-card empty-card">
            <text class="empty-title">暂无匹配批次</text>
            <text class="empty-desc">可以调整关键词、作物或品种后再试。</text>
          </view>
        </dh-reveal-panel>
        <view v-if="loading && batches.length" class="load-more-skeleton">
          <view class="batch-skeleton-stack compact">
            <view v-for="idx in 2" :key="`batch-more-skeleton-${idx}`" class="batch-card batch-card-skeleton">
              <t-skeleton theme="paragraph" animation="flashed" :row-col="batchSkeletonRows" />
            </view>
          </view>
        </view>
        <view v-else-if="batches.length && !finished" class="state-tip">上拉加载更多</view>
        <view v-else-if="finished && batches.length" class="state-tip">已加载全部</view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { canManageSeedBatch, isApprovedUser } from '../../utils/auth'
import { isElderMode } from '../../utils/accessibility'
import { formatCropVarietyPair } from '../../utils/crop-variety'
import SearchAssist from '../../components/search-assist.vue'
import FilterDropdownItem from '../../components/filter-dropdown-item.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import { readSearchValue } from '../../utils/search-suggestion'
import { fetchRemoteSearchSuggestions, shouldFetchRemoteSearchSuggestions } from '../../utils/remote-search-suggestions'
import { readSearchHistory, pushSearchHistory, clearSearchHistory, removeSearchHistoryItem } from '../../utils/search-history'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'

const BATCH_CARD_SKELETON_ROWS = [
  [
    { width: '64%', height: '30rpx', borderRadius: '12rpx', marginRight: '16rpx' },
    { width: '24%', height: '28rpx', borderRadius: '999rpx' }
  ],
  { width: '42%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  { width: '82%', height: '22rpx', margin: '14rpx 0 0 0', borderRadius: '10rpx' }
]

export default {
  components: {
    SearchAssist,
    FilterDropdownItem,
    DhRevealPanel
  },
  data() {
    return {
      keyword: '',
      appliedKeyword: '',
      batches: [],
      total: 0,
      loading: true,
      canManage: false,
      elderMode: false,
      cropOptions: [{ value: '', label: '全部作物' }],
      varietyOptions: [{ value: '', label: '全部品种' }],
      selectedCropIndex: 0,
      selectedVarietyIndex: 0,
      cropsLoaded: false,
      varietiesLoaded: false,
      remoteSuggestions: [],
      keywordHistory: [],
      suggestionSeq: 0,
      suggestionTimer: null,
      page: 1,
      pageSize: 12,
      finished: false,
      initialized: false,
      refreshMark: '',
      batchSkeletonRows: BATCH_CARD_SKELETON_ROWS
    }
  },
  computed: {
    selectedCropName() {
      return this.cropOptions[this.selectedCropIndex]?.value || ''
    },
    selectedCropLabel() {
      return this.cropOptions[this.selectedCropIndex]?.label || '全部作物'
    },
    selectedVarietyName() {
      return this.varietyOptions[this.selectedVarietyIndex]?.value || ''
    },
    selectedVarietyLabel() {
      return this.varietyOptions[this.selectedVarietyIndex]?.label || '全部品种'
    },
    cropDropdownOptions() {
      return this.cropOptions
    },
    varietyDropdownOptions() {
      return this.varietyOptions
    },
    toolbarTitle() {
      if (this.appliedKeyword) {
        return `关键词“${this.appliedKeyword}”`
      }
      return `共 ${this.total || this.batches.length || 0} 个批次`
    },
    toolbarSubText() {
      const summary = []
      if (this.selectedCropName) summary.push(this.selectedCropLabel)
      if (this.selectedVarietyName) summary.push(this.selectedVarietyLabel)
      if (summary.length) {
        return `当前筛选：${summary.join(' / ')}`
      }
      return '搜索支持批次号和品种名称，筛选采用作物和品种联动'
    },
    searchFeedbackText() {
      if (!this.keyword) return ''
      if (this.keyword !== this.appliedKeyword) {
        return `输入了“${this.keyword}”，点击“搜索”后再更新列表`
      }
      if (!this.batches.length) {
        return `关键词“${this.appliedKeyword}”暂无匹配批次`
      }
      if (this.keywordSuggestions.length) {
        return `已匹配 ${this.batches.length} 个批次，可继续点下方提示词缩小范围`
      }
      return `关键词“${this.appliedKeyword}”匹配 ${this.batches.length} 个批次`
    },
    keywordSuggestions() {
      return Array.isArray(this.remoteSuggestions) ? this.remoteSuggestions : []
    },
    activeFilterItems() {
      const list = []
      if (this.appliedKeyword) {
        list.push({ key: 'keyword', label: `关键词：${this.appliedKeyword}` })
      }
      if (this.selectedCropName) {
        list.push({ key: 'crop', label: this.selectedCropLabel })
      }
      if (this.selectedVarietyName) {
        list.push({ key: 'variety', label: this.selectedVarietyLabel })
      }
      return list
    }
  },
  async onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    this.canManage = canManageSeedBatch()
    this.elderMode = isElderMode()
    this.loadKeywordHistory()
    const topic = refreshTopics.seedBatches()
    if (!this.initialized) {
      await this.refreshBatches()
      this.refreshMark = readRefreshMark(topic)
      this.initialized = true
      return
    }
    if (hasDataChanged(topic, this.refreshMark)) {
      await this.refreshBatches()
      this.refreshMark = readRefreshMark(topic)
    }
  },
  beforeDestroy() {
    if (this.suggestionTimer) {
      clearTimeout(this.suggestionTimer)
      this.suggestionTimer = null
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    dismissSearchSuggestions() {
      const searchAssist = this.$refs.searchAssist
      if (searchAssist && typeof searchAssist.hidePanelOnScroll === 'function') {
        searchAssist.hidePanelOnScroll()
      }
    },
    onKeywordChange(context) {
      this.keyword = readSearchValue(context)
      this.scheduleKeywordSuggestions()
    },
    onKeywordSubmit(context) {
      this.keyword = readSearchValue(context)
      const nextKeyword = String(this.keyword || '').trim()
      this.keyword = nextKeyword
      if (!nextKeyword) {
        uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
        return
      }
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(this.appliedKeyword)
      this.remoteSuggestions = []
      this.refreshBatches()
    },
    onKeywordClear() {
      const hadAppliedKeyword = !!this.appliedKeyword
      this.keyword = ''
      this.appliedKeyword = ''
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      if (hadAppliedKeyword) {
        this.refreshBatches()
      }
    },
    bumpSuggestionSeq() {
      this.suggestionSeq += 1
      return this.suggestionSeq
    },
    scheduleKeywordSuggestions() {
      if (this.suggestionTimer) {
        clearTimeout(this.suggestionTimer)
      }
      const keyword = this.keyword
      if (!shouldFetchRemoteSearchSuggestions(keyword)) {
        this.remoteSuggestions = []
        this.bumpSuggestionSeq()
        return
      }
      const requestSeq = this.bumpSuggestionSeq()
      this.suggestionTimer = setTimeout(() => {
        this.refreshKeywordSuggestions(requestSeq, keyword)
      }, 180)
    },
    async refreshKeywordSuggestions(requestSeq, keyword) {
      const rows = await fetchRemoteSearchSuggestions('seed-batch', keyword, 6)
      if (requestSeq !== this.suggestionSeq) {
        return
      }
      this.remoteSuggestions = rows
    },
    loadKeywordHistory() {
      this.keywordHistory = readSearchHistory('seed-batch', 6)
    },
    persistKeywordHistory(keyword) {
      const safeKeyword = String(keyword || '').trim()
      if (!safeKeyword) return
      this.keywordHistory = pushSearchHistory('seed-batch', safeKeyword, 6)
    },
    onHistoryClick(item) {
      const nextKeyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.refreshBatches()
    },
    onHistoryClear() {
      this.keywordHistory = clearSearchHistory('seed-batch')
    },
    onHistoryRemove(item) {
      const keyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      this.keywordHistory = removeSearchHistoryItem('seed-batch', keyword, 6)
    },
    async ensureCropOptions() {
      if (this.cropsLoaded) return
      try {
        const rows = await api.get('/miniapp/meta/options/crops', {})
        const uniqueRows = Array.from(new Set((Array.isArray(rows) ? rows : []).map((item) => String(item || '').trim()).filter(Boolean)))
        this.cropOptions = [{ value: '', label: '全部作物' }, ...uniqueRows.map((item) => ({ value: item, label: item }))]
        this.cropsLoaded = true
      } catch (e) {
        console.error('加载作物筛选失败', e)
      }
    },
    async ensureVarietyOptions() {
      try {
        const rows = await api.get('/miniapp/meta/options/varieties', {
          cropName: this.selectedCropName || undefined
        })
        const uniqueRows = Array.from(new Set((Array.isArray(rows) ? rows : []).map((item) => String(item || '').trim()).filter(Boolean)))
        this.varietyOptions = [{ value: '', label: '全部品种' }, ...uniqueRows.map((item) => ({ value: item, label: item }))]
        this.varietiesLoaded = true
      } catch (e) {
        console.error('加载品种筛选失败', e)
      }
    },
    async onCropChange(context) {
      const value = String((context && context.value) || '')
      const idx = this.cropOptions.findIndex((item) => String(item.value) === value)
      this.selectedCropIndex = idx >= 0 ? idx : 0
      this.selectedVarietyIndex = 0
      this.varietyOptions = [{ value: '', label: '全部品种' }]
      this.varietiesLoaded = false
      await this.ensureVarietyOptions()
      await this.refreshBatches()
    },
    async onVarietyChange(context) {
      if (!this.varietiesLoaded) {
        await this.ensureVarietyOptions()
      }
      const value = String((context && context.value) || '')
      const idx = this.varietyOptions.findIndex((item) => String(item.value) === value)
      this.selectedVarietyIndex = idx >= 0 ? idx : 0
      await this.refreshBatches()
    },
    async clearFilter(key) {
      const name = String(key || '').trim()
      if (name === 'keyword') {
        this.keyword = ''
        this.appliedKeyword = ''
      } else if (name === 'crop') {
        this.selectedCropIndex = 0
        this.selectedVarietyIndex = 0
        this.varietyOptions = [{ value: '', label: '全部品种' }]
        this.varietiesLoaded = false
        await this.ensureVarietyOptions()
      } else if (name === 'variety') {
        this.selectedVarietyIndex = 0
      }
      await this.refreshBatches()
    },
    async refreshBatches() {
      this.loading = true
      this.page = 1
      this.finished = false
      this.batches = []
      await this.fetchBatches(true)
    },
    async loadMore() {
      if (this.loading || this.finished) return
      this.page += 1
      await this.fetchBatches(false)
    },
    async fetchBatches(reset) {
      this.loading = true
      try {
        const res = await api.get('/miniapp/seed-batches', {
          page: this.page,
          pageSize: this.pageSize,
          keyword: this.appliedKeyword || undefined,
          cropType: this.selectedCropName || undefined,
          varietyName: this.selectedVarietyName || undefined,
          includeDisabled: this.canManage ? true : undefined
        })
        const rows = Array.isArray(res && res.records) ? res.records : []
        this.batches = reset ? rows : this.batches.concat(rows)
        this.total = Number((res && res.total) || this.batches.length)
        this.finished = this.batches.length >= this.total || rows.length < this.pageSize
      } catch (e) {
        console.error('加载种子批次失败', e)
      } finally {
        this.loading = false
      }
    },
    cropVarietyText(item) {
      return formatCropVarietyPair(item, '')
    },
    onSuggestionClick(item) {
      const nextKeyword = String(item && item.value ? item.value : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.refreshBatches()
    },
    closeDropdownItem(refName) {
      const target = Array.isArray(this.$refs[refName]) ? this.$refs[refName][0] : this.$refs[refName]
      if (target && typeof target.closePanel === 'function') {
        target.closePanel()
      }
    },
    async onCropSelect(option) {
      this.closeDropdownItem('cropDropdownItem')
      await this.onCropChange({ value: option && option.value })
    },
    async onVarietySelect(option) {
      this.closeDropdownItem('varietyDropdownItem')
      await this.onVarietyChange({ value: option && option.value })
    },
    goDetail(item) {
      uni.navigateTo({ url: `/pages/seed/batch-detail?id=${item.id}` })
    },
    goCreateBatch() {
      uni.navigateTo({ url: '/pages/seed/batch-edit' })
    }
  }
}
</script>

<style lang="scss">
.page {
  background: var(--dh-color-bg);
}

.content-shell {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 18rpx 24rpx 24rpx;
  display: flex;
  flex-direction: column;
}

.toolbar {
  padding: 18rpx;
  border-radius: 22rpx;
}

.batch-search {
  --dh-search-bg-color: #ffffff;
  --dh-search-placeholder-color: #98a394;
  --dh-search-text-color: #233021;
  --dh-search-height: 84rpx;
  --dh-search-radius: 18rpx;
  border-radius: 18rpx;
}

.toolbar-meta {
  margin-top: 14rpx;
}

.toolbar-title {
  display: block;
  font-size: 30rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.4;
}

.toolbar-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #6d7a68;
  line-height: 1.5;
}

.filter-menu {
  margin-top: 14rpx;
  border-radius: 18rpx;
  overflow: hidden;
  border: 1rpx solid var(--dh-color-border);
  background: #ffffff;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
}

.filter-menu-item {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
}

.filter-menu-item + .filter-menu-item {
  border-left: 1rpx solid var(--dh-color-border);
}

.toolbar-foot {
  margin-top: 12rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.filter-summary {
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

.manage-btn {
  flex-shrink: 0;
}

.list {
  flex: 1;
  min-height: 0;
  margin-top: 14rpx;
  box-sizing: border-box;
}

.batch-card {
  padding: 18rpx;
  min-height: 154rpx;
  margin-bottom: 12rpx;
  border-radius: 22rpx;
  background: #ffffff;
  border: 1rpx solid var(--dh-color-border);
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

.code {
  display: block;
  font-size: 31rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.38;
}

.batch-pair-tag {
  flex-shrink: 0;
  --td-tag-primary-color: #527647;
  --td-tag-primary-light-color: #edf6e7;
}

.batch-note {
  display: block;
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #6d7a68;
  line-height: 1.55;
}

.batch-code {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #82907b;
  line-height: 1.45;
}

.state-card {
  padding: 40rpx 24rpx;
  border-radius: 22rpx;
  background: #ffffff;
  border: 1rpx solid var(--dh-color-border);
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
}

.state-card-skeleton {
  padding: 14rpx;
}

.batch-skeleton-stack {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 2rpx 0 6rpx;
}

.batch-skeleton-stack.compact {
  padding: 6rpx 0 0;
}

.batch-card.batch-card-skeleton {
  overflow: hidden;
  --td-skeleton-bg-color: #d6e0d1;
  --td-skeleton-animation-flashed: rgba(168, 181, 162, 0.62);
}

.batch-card.batch-card-skeleton :deep(.t-skeleton) {
  width: 100%;
}

.load-more-skeleton {
  padding: 6rpx 0 12rpx;
}

.list-reveal-panel {
  display: block;
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

.elder-mode {

  .toolbar-title,
  .code,
  .empty-title {
    font-size: 36rpx;
  }

  .toolbar-sub,
  .batch-code,
  .batch-note,
  .state-card,
  .empty-desc,
  .state-tip {
    font-size: 30rpx;
  }

  .batch-search {
    --dh-search-height: 98rpx;
    --dh-search-font-size: 32rpx;
    --dh-search-radius: 22rpx;
  }

  .toolbar,
  .batch-card,
  .state-card {
    border-radius: 24rpx;
  }

  .t-tag,
  .t-tag__text {
    font-size: 28rpx !important;
    line-height: 1.4;
  }

  .filter-chip-tag {
    min-height: 66rpx;
  }
}
</style>
