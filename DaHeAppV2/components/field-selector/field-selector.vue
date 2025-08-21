<template>
  <view class="field-selector" :class="{ 'is-elder': elderMode }">
    <t-cell
      v-if="!hideTrigger"
      class="field-trigger"
      :title="selectedField ? selectedFieldName : placeholder"
      :note="selectedField ? triggerNoteText : ''"
      arrow
      @click="openPopup"
    />

    <t-popup
      :visible="popupVisible"
      placement="bottom"
      :show-overlay="true"
      :close-on-overlay-click="true"
      :prevent-scroll-through="true"
      @visible-change="onPopupVisibleChange"
    >
      <view class="selector-panel">
        <view class="panel-header">
          <text class="panel-title">{{ title }}</text>
          <view class="close-btn" @tap="closePopup">
            <t-icon name="close" size="40rpx" />
          </view>
        </view>

        <view class="toolbar-card">
          <t-tabs
            ref="tabsRef"
            class="tab-bar"
            :value="activeTab"
            theme="tag"
            :space-evenly="false"
            @change="onTabChange"
          >
            <t-tab-panel value="common" label="常用" />
            <t-tab-panel value="nearby" label="附近" />
            <t-tab-panel value="all" label="全部" />
          </t-tabs>

          <view class="toolbar-main">
            <view class="search-wrap">
              <search-assist
                ref="searchAssist"
                :value="keyword"
                :placeholder="searchPlaceholder"
                :helper-text="searchFeedbackText"
                :suggestions="keywordSuggestions"
                :history-items="keywordHistory"
                :elder-mode="elderMode"
                @change="onSearchChange"
                @submit="onSearchSubmit"
                @clear="onSearchClear"
                @history-click="onHistoryClick"
                @history-clear="onHistoryClear"
                @history-remove="onHistoryRemove"
                @suggestion-click="onSuggestionClick"
              />
              <view v-if="!keyword" class="toolbar-tip">
                <text class="toolbar-tip-title">{{ modeSummaryTitle }}</text>
                <text class="toolbar-tip-text">{{ modeSummaryText }}</text>
              </view>
            </view>

            <view class="filter-box" v-if="activeTab === 'all'">
              <view class="filter-box-head">
                <text class="filter-box-label">乡镇筛选</text>
                <text class="filter-box-value">{{ townFilterText }}</text>
              </view>
              <view class="town-menu">
                <filter-dropdown-item
                  ref="townDropdownItem"
                  label="切换乡镇"
                  :options="townDropdownOptions"
                  :value="townFilter"
                  :elder-mode="elderMode"
                  empty-text="暂无乡镇"
                  @open="ensureTownOptions"
                  @select="onTownDropdownSelect"
                />
              </view>
            </view>
          </view>
        </view>

        <view class="status-row" v-if="activeTab === 'common' && !listLoading && !fieldRows.length">
          <text class="status-text">常用田块来自你已提交农事记录中使用频次更高的田块。</text>
        </view>
        <view class="status-row" v-if="activeTab === 'nearby' && locating">
          <text class="status-text">正在定位并计算附近田块，请稍候...</text>
        </view>
        <view class="status-row" v-if="activeTab === 'nearby' && !locating && !userLocation">
          <text class="status-text">未获取到定位，附近田块需要位置权限后才能显示。</text>
        </view>
        <view class="status-row" v-if="listLoading && !filteredRows.length">
          <text class="status-text">田块加载中，请稍候...</text>
        </view>

        <scroll-view
          scroll-y
          class="field-list"
          :show-scrollbar="false"
          :lower-threshold="80"
          @scroll="dismissSearchSuggestions"
          @scrolltolower="onListScrollToLower"
        >
          <view v-if="listLoading && !filteredRows.length" class="skeleton-list">
            <view v-for="idx in 4" :key="`field-picker-skeleton-${idx}`" class="field-card field-card-skeleton">
              <view class="field-main">
                <view class="field-cover field-cover-skeleton">
                  <t-skeleton theme="image" animation="flashed" />
                </view>
                <view class="field-content field-content-skeleton">
                  <t-skeleton theme="paragraph" animation="flashed" :row-col="fieldSkeletonRows" />
                </view>
              </view>
            </view>
          </view>

          <view v-else-if="filteredRows.length" class="field-list-inner">
            <view
              v-for="item in filteredRows"
              :key="fieldId(item)"
              class="field-card"
              :class="{ active: isSelected(item), matched: highlightMatched && isCurrentMatched(item) }"
              @tap="onPick(item)"
            >
              <view v-if="highlightMatched && isCurrentMatched(item)" class="field-card-highlight"></view>
              <view class="field-main">
                <view class="field-cover">
                  <dh-smart-image
                    class="field-cover-img"
                    :src="resolveCoverImage(item)"
                    :preview-src="resolveCoverPreviewImage(item)"
                    mode="aspectFill"
                    :lazy-load="true"
                    :on-demand="true"
                    root-selector=".field-list"
                    loading-text="加载中..."
                    empty-text="田块"
                    error-text="图片异常"
                  />
                </view>

                <view class="field-content">
                  <view class="field-head">
                    <text class="field-name">{{ fieldName(item) }}</text>
                    <t-tag size="small" theme="primary" variant="light">{{ resolvePlanName(item) }}</t-tag>
                  </view>

                  <view class="field-meta">
                    <text>{{ resolveTown(item) || '未设置乡镇' }}</text>
                    <text v-if="resolveArea(item)"> / {{ resolveArea(item) }}</text>
                  </view>

                  <view class="field-tags" v-if="resolveCropTags(item).length">
                    <t-tag
                      v-for="label in resolveCropTags(item)"
                      :key="`${fieldId(item)}-${label}`"
                      size="small"
                      theme="success"
                      variant="light"
                    >
                      {{ label }}
                    </t-tag>
                  </view>

                  <view class="field-foot">
                    <text class="field-address" v-if="resolveAddress(item)">{{ resolveAddress(item) }}</text>
                    <view
                      v-if="fieldLocationMetaText(item)"
                      class="field-location"
                      :class="{
                        matched: isCurrentMatched(item),
                        error: !isCurrentMatched(item) && isLocationAbnormal(item),
                        warning: !isCurrentMatched(item) && isWarningDistance(item),
                        far: !isCurrentMatched(item) && !isLocationAbnormal(item) && !isWarningDistance(item)
                      }"
                    >
                      <text v-if="fieldRelationText(item)" class="field-relation">{{ fieldRelationText(item) }}</text>
                      <text class="field-distance" v-if="fieldDistanceText(item)">{{ fieldDistanceText(item) }}</text>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <view class="list-footer" v-if="showListFooter">
              <text class="list-footer-text">{{ loadMoreText }}</text>
            </view>
          </view>

          <view v-else class="empty-wrap">
            <t-empty :description="emptyDescription" />
          </view>
        </scroll-view>
      </view>
    </t-popup>
  </view>
</template>

<script>
import api from '../../utils/request'
import { haversineDistanceMeters } from '../../utils/amap'
import { resolveCropVarietyLabels } from '../../utils/crop-variety'
import SearchAssist from '../search-assist.vue'
import FilterDropdownItem from '../filter-dropdown-item.vue'
import DhSmartImage from '../dh-smart-image.vue'
import { readSearchValue } from '../../utils/search-suggestion'
import { fetchRemoteSearchSuggestions, shouldFetchRemoteSearchSuggestions } from '../../utils/remote-search-suggestions'
import { readSearchHistory, pushSearchHistory, clearSearchHistory, removeSearchHistoryItem } from '../../utils/search-history'

const REMOTE_PAGE_SIZE = 12
const NEARBY_RADIUS_KM = 20
const LOCATION_ERROR_TEXT = '位置异常'
const LOCATION_UNKNOWN_TEXT = '未知'
const FIELD_SELECTOR_SKELETON_MIN_MS = 480
const FIELD_PICKER_SKELETON_ROWS = [
  [
    { width: '62%', height: '28rpx', borderRadius: '12rpx', marginRight: '12rpx' },
    { width: '24%', height: '24rpx', borderRadius: '999rpx' }
  ],
  { width: '46%', height: '22rpx', margin: '10rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '28%', height: '22rpx', margin: '14rpx 10rpx 0 0', borderRadius: '999rpx' },
    { width: '22%', height: '22rpx', margin: '14rpx 0 0 0', borderRadius: '999rpx' }
  ],
  [
    { width: '50%', height: '22rpx', margin: '18rpx 12rpx 0 0', borderRadius: '10rpx' },
    { width: '24%', height: '22rpx', margin: '18rpx 0 0 0', borderRadius: '10rpx' }
  ]
]

export default {
  name: 'FieldSelector',
  components: {
    SearchAssist,
    FilterDropdownItem,
    DhSmartImage
  },
  beforeDestroy() {
    if (this.suggestionTimer) {
      clearTimeout(this.suggestionTimer)
      this.suggestionTimer = null
    }
  },
  props: {
    value: {
      type: [Number, String],
      default: null
    },
    selectedFieldInfo: {
      type: Object,
      default: null
    },
    includeDisabled: {
      type: Boolean,
      default: false
    },
    hideTrigger: {
      type: Boolean,
      default: false
    },
    elderMode: {
      type: Boolean,
      default: false
    },
    highlightMatched: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: '选择田块'
    },
    placeholder: {
      type: String,
      default: '请选择田块'
    }
  },
  data() {
    return {
      popupVisible: false,
      keyword: '',
      appliedKeyword: '',
      activeTab: 'common',
      townFilter: 'all',
      locating: false,
      listLoading: false,
      loadingMore: false,
      userLocation: null,
      currentMatchedField: null,
      fieldRows: [],
      townOptions: [],
      townLoading: false,
      remoteSuggestions: [],
      keywordHistory: [],
      suggestionSeq: 0,
      suggestionTimer: null,
      pageNo: 0,
      pageSize: REMOTE_PAGE_SIZE,
      total: 0,
      finished: false,
      requestSerial: 0
    }
  },
  computed: {
    selectedField() {
      const targetId = String(this.value == null ? '' : this.value)
      if (!targetId) return null
      if (this.selectedFieldInfo && String(this.fieldId(this.selectedFieldInfo)) === targetId) {
        return this.selectedFieldInfo
      }
      return this.fieldRows.find((item) => String(this.fieldId(item)) === targetId) || null
    },
    selectedFieldName() {
      return this.selectedField ? this.fieldName(this.selectedField) : ''
    },
    triggerNoteText() {
      if (!this.selectedField) return ''
      const parts = []
      const relation = this.fieldRelationText(this.selectedField)
      const town = this.resolveTown(this.selectedField)
      const crop = this.resolveCropTags(this.selectedField)[0]
      const plan = this.resolvePlanName(this.selectedField)
      if (relation) parts.push(relation)
      if (town) parts.push(town)
      if (crop) parts.push(crop)
      if (plan) parts.push(plan)
      return parts.join(' / ')
    },
    pinnedMatchedField() {
      if (!this.highlightMatched || this.activeTab !== 'common') return null
      if (this.currentMatchedField && this.isCurrentMatched(this.currentMatchedField)) {
        return this.currentMatchedField
      }
      const candidate = this.selectedFieldInfo || this.selectedField
      if (candidate && this.isCurrentMatched(candidate)) {
        return candidate
      }
      return null
    },
    filteredRows() {
      const rows = Array.isArray(this.fieldRows) ? this.fieldRows : []
      const pinned = this.pinnedMatchedField
      if (!pinned) return rows
      const pinnedId = String(this.fieldId(pinned))
      if (!pinnedId) return rows
      const rest = rows.filter((item) => String(this.fieldId(item)) !== pinnedId)
      return [{ ...pinned, currentMatched: true }, ...rest]
    },
    townDropdownOptions() {
      const values = Array.from(new Set((Array.isArray(this.townOptions) ? this.townOptions : []).filter(Boolean)))
      const options = [{ label: '全部乡镇', value: 'all' }]
      values.forEach((town) => {
        options.push({ label: town, value: town })
      })
      if (this.townFilter !== 'all' && !values.includes(this.townFilter)) {
        options.push({ label: this.townFilter, value: this.townFilter })
      }
      return options
    },
    searchPlaceholder() {
      if (this.activeTab === 'common') return '搜索常用田块'
      if (this.activeTab === 'nearby') return '搜索 20km 内附近田块'
      return '搜索田块名、乡镇、地址、作物品种'
    },
    modeSummaryTitle() {
      if (this.activeTab === 'common') return '常用田块'
      if (this.activeTab === 'nearby') return '附近田块'
      return '全部田块'
    },
    modeSummaryText() {
      if (this.activeTab === 'common') {
        return '按你已提交农事记录的使用频次排序，提交越多越靠前。'
      }
      if (this.activeTab === 'nearby') {
        return '仅展示 20km 内田块，并按距离从近到远排序。'
      }
      return '可通过关键词和乡镇快速缩小范围，再上拉继续加载。'
    },
    townFilterText() {
      return this.townFilter === 'all' ? '全部乡镇' : this.townFilter
    },
    searchFeedbackText() {
      if (!this.keyword) return ''
      if (this.keyword !== this.appliedKeyword) {
        return `输入了“${this.keyword}”，点击“搜索”后再更新列表`
      }
      if (!this.filteredRows.length) {
        return `关键词“${this.appliedKeyword}”暂无匹配田块`
      }
      if (this.keywordSuggestions.length) {
        return `已匹配 ${this.filteredRows.length} 个田块，可继续点下面提示词缩小范围`
      }
      return `关键词“${this.appliedKeyword}”匹配 ${this.filteredRows.length} 个田块`
    },
    keywordSuggestions() {
      return Array.isArray(this.remoteSuggestions) ? this.remoteSuggestions : []
    },
    emptyDescription() {
      if (this.activeTab === 'nearby' && !this.userLocation) {
        return '请先允许定位，再查看附近田块'
      }
      if (this.activeTab === 'nearby') {
        return '20km 内暂无匹配田块'
      }
      if (this.activeTab === 'common') {
        return this.keyword ? '常用田块中没有匹配结果' : '还没有形成常用田块'
      }
      return '没有匹配到田块'
    },
    showListFooter() {
      return !!this.filteredRows.length
    },
    loadMoreText() {
      if (this.loadingMore) return '正在加载更多田块...'
      if (this.finished) return '没有更多田块了'
      return '上拉加载更多'
    },
    fieldSkeletonRows() {
      return FIELD_PICKER_SKELETON_ROWS
    }
  },
  methods: {
    waitMinimumLoading(startedAt, minDurationMs) {
      const safeStartedAt = Number(startedAt || 0)
      const safeMin = Math.max(0, Number(minDurationMs) || 0)
      if (!safeStartedAt || safeMin <= 0) return Promise.resolve()
      const remain = Math.max(0, safeMin - (Date.now() - safeStartedAt))
      if (!remain) return Promise.resolve()
      return new Promise((resolve) => setTimeout(resolve, remain))
    },
    openPopup() {
      this.popupVisible = true
      this.$emit('popup-visible-change', true)
      this.refreshTabsTrack()
      this.ensurePopupData()
    },
    closePopup() {
      this.popupVisible = false
      this.$emit('popup-visible-change', false)
    },
    onPopupVisibleChange(context) {
      const visible = !!(context && Object.prototype.hasOwnProperty.call(context, 'visible') ? context.visible : false)
      this.popupVisible = visible
      this.$emit('popup-visible-change', visible)
      if (visible) {
        this.refreshTabsTrack()
        this.ensurePopupData()
      }
    },
    refreshTabsTrack() {
      setTimeout(() => {
        const tabs = this.$refs.tabsRef
        if (tabs && typeof tabs.setTrack === 'function') {
          tabs.setTrack()
        }
      }, 60)
    },
    async ensurePopupData() {
      this.loadKeywordHistory()
      const needMatchedContext = this.highlightMatched && this.activeTab === 'common'
      if (needMatchedContext) {
        await this.ensureUserLocation(false)
        await this.ensureCurrentMatchedField()
      } else {
        this.currentMatchedField = null
        this.ensureUserLocation()
      }
      if (this.activeTab === 'all') {
        this.ensureTownOptions()
      }
      if (!this.fieldRows.length && !this.listLoading) {
        this.reloadFields()
      }
    },
    onSearchChange(context) {
      this.keyword = readSearchValue(context)
      this.scheduleKeywordSuggestions()
    },
    onSearchClear() {
      const hadAppliedKeyword = !!this.appliedKeyword
      this.keyword = ''
      this.appliedKeyword = ''
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      if (hadAppliedKeyword) {
        this.reloadFields()
      }
    },
    onSearchSubmit() {
      const nextKeyword = String(this.keyword || '').trim()
      this.keyword = nextKeyword
      if (!nextKeyword) {
        uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
        return
      }
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(this.appliedKeyword)
      this.remoteSuggestions = []
      this.reloadFields()
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
      const rows = await fetchRemoteSearchSuggestions('field-picker', keyword, 6)
      if (requestSeq !== this.suggestionSeq) {
        return
      }
      this.remoteSuggestions = rows
    },
    loadKeywordHistory() {
      this.keywordHistory = readSearchHistory('field-picker', 6)
    },
    persistKeywordHistory(keyword) {
      const safeKeyword = String(keyword || '').trim()
      if (!safeKeyword) return
      this.keywordHistory = pushSearchHistory('field-picker', safeKeyword, 6)
    },
    onHistoryClick(item) {
      const nextKeyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.reloadFields()
    },
    onHistoryClear() {
      this.keywordHistory = clearSearchHistory('field-picker')
    },
    onHistoryRemove(item) {
      const keyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      this.keywordHistory = removeSearchHistoryItem('field-picker', keyword, 6)
    },
    onTabChange(context) {
      const value = String((context && context.value) || 'common')
      this.activeTab = value || 'common'
      if (this.activeTab !== 'all') {
        this.townFilter = 'all'
      }
      if (this.activeTab === 'all') {
        this.ensureTownOptions()
      }
      if (this.activeTab === 'nearby') {
        this.ensureUserLocation()
      }
      if (this.highlightMatched && this.activeTab === 'common') {
        this.ensureUserLocation(false)
          .then(() => this.ensureCurrentMatchedField())
          .finally(() => {
            this.reloadFields()
          })
        return
      }
      this.currentMatchedField = null
      this.reloadFields()
    },
    onTownDropdownSelect(option) {
      const value = String((option && option.value) || 'all')
      this.townFilter = value || 'all'
      this.closeDropdownItem('townDropdownItem')
      this.reloadFields()
    },
    onListScrollToLower() {
      this.fetchFields({ reset: false })
    },
    reloadFields() {
      this.pageNo = 0
      this.total = 0
      this.finished = false
      return this.fetchFields({ reset: true })
    },
    async fetchFields({ reset = false } = {}) {
      if ((!reset && this.finished) || this.listLoading || this.loadingMore) return
      if (['common', 'nearby', 'all'].includes(this.activeTab) && !this.userLocation) {
        await this.ensureUserLocation(false)
      }
      if (this.activeTab === 'nearby' && !this.userLocation) {
        if (reset) {
          this.fieldRows = []
          this.pageNo = 0
          this.total = 0
          this.finished = true
        }
        return
      }
      const currentSerial = ++this.requestSerial
      const loadingStartedAt = Date.now()
      const nextPage = reset ? 1 : this.pageNo + 1
      if (reset) {
        this.listLoading = true
        this.fieldRows = []
      } else {
        this.loadingMore = true
      }
      try {
        const res = await this.requestFieldPage(nextPage)
        if (currentSerial !== this.requestSerial) return
        const rows = Array.isArray(res && res.records) ? res.records : []
        this.fieldRows = reset ? rows : this.mergeFieldRows(this.fieldRows, rows)
        this.pageNo = nextPage
        this.total = Number((res && res.total) || 0)
        if (this.total > 0) {
          this.finished = this.fieldRows.length >= this.total
        } else {
          this.finished = rows.length < this.pageSize
        }
      } catch (error) {
        if (currentSerial !== this.requestSerial) return
        if (reset) {
          this.fieldRows = []
          this.pageNo = 0
          this.total = 0
          this.finished = true
        }
      } finally {
        if (currentSerial === this.requestSerial) {
          if (reset) {
            await this.waitMinimumLoading(loadingStartedAt, FIELD_SELECTOR_SKELETON_MIN_MS)
          }
        }
        if (currentSerial === this.requestSerial) {
          this.listLoading = false
          this.loadingMore = false
        }
      }
    },
    requestFieldPage(nextPage) {
      const params = {
        page: nextPage,
        pageSize: this.pageSize
      }
      if (this.includeDisabled) {
        params.includeDisabled = true
      }
      if (this.appliedKeyword) {
        params.keyword = this.appliedKeyword
      }
      if (this.activeTab === 'common') {
        return api.get('/miniapp/fields/common', {
          ...params,
          latitude: this.userLocation ? this.userLocation.lat : undefined,
          longitude: this.userLocation ? this.userLocation.lng : undefined
        })
      }
      if (this.activeTab === 'nearby') {
        return api.get('/miniapp/fields/nearby', {
          ...params,
          latitude: this.userLocation ? this.userLocation.lat : undefined,
          longitude: this.userLocation ? this.userLocation.lng : undefined,
          radiusKm: NEARBY_RADIUS_KM
        })
      }
      if (this.townFilter !== 'all') {
        params.township = this.townFilter
      }
      if (this.userLocation) {
        params.latitude = this.userLocation.lat
        params.longitude = this.userLocation.lng
      }
      return api.get('/miniapp/fields', params)
    },
    mergeFieldRows(source, incoming) {
      const map = new Map()
      ;(Array.isArray(source) ? source : []).forEach((item) => {
        map.set(String(this.fieldId(item)), item)
      })
      ;(Array.isArray(incoming) ? incoming : []).forEach((item) => {
        map.set(String(this.fieldId(item)), item)
      })
      return Array.from(map.values())
    },
    async ensureTownOptions() {
      if (this.townLoading || this.townOptions.length) return
      this.townLoading = true
      try {
        const rows = await api.get('/miniapp/meta/options/townships')
        this.townOptions = Array.isArray(rows) ? rows.filter(Boolean) : []
      } catch (error) {
        this.townOptions = []
      } finally {
        this.townLoading = false
      }
    },
    async ensureUserLocation(reloadOnResolve = true) {
      if (this.userLocation) return this.userLocation
      if (this.locating) {
        for (let i = 0; i < 20; i += 1) {
          await new Promise((resolve) => setTimeout(resolve, 100))
          if (!this.locating) {
            return this.userLocation
          }
        }
        return this.userLocation
      }
      this.locating = true
      try {
        const location = await new Promise((resolve, reject) => {
          uni.getLocation({
            type: 'gcj02',
            success: resolve,
            fail: reject
          })
        })
        this.userLocation = {
          lat: Number(location.latitude),
          lng: Number(location.longitude)
        }
        if (reloadOnResolve && this.popupVisible && ['common', 'nearby', 'all'].includes(this.activeTab)) {
          this.reloadFields()
        }
        return this.userLocation
      } catch (error) {
        this.userLocation = null
        return null
      } finally {
        this.locating = false
      }
    },
    async ensureCurrentMatchedField() {
      if (!this.highlightMatched || this.activeTab !== 'common') {
        this.currentMatchedField = null
        return null
      }
      const location = this.userLocation || await this.ensureUserLocation(false)
      if (!location) {
        this.currentMatchedField = null
        return null
      }
      try {
        const matched = await api.get('/miniapp/fields/current-match', {
          latitude: location.lat,
          longitude: location.lng
        })
        this.currentMatchedField = matched && matched.id ? { ...matched, currentMatched: true } : null
        return this.currentMatchedField
      } catch (error) {
        this.currentMatchedField = null
        return null
      }
    },
    fieldId(item) {
      return item && item.id != null ? item.id : ''
    },
    fieldName(item) {
      return String((item && item.name) || '').trim() || `田块 #${this.fieldId(item)}`
    },
    isSelected(item) {
      return String(this.fieldId(item)) === String(this.value == null ? '' : this.value)
    },
    isCurrentMatched(item) {
      return Number(item && item.currentMatched) === 1 || (item && item.currentMatched === true)
    },
    hasValidFieldCoordinate(item) {
      const lat = Number(item && (item.locationLat || item.latitude || item.lat))
      const lng = Number(item && (item.locationLng || item.longitude || item.lng))
      return Number.isFinite(lat)
        && Number.isFinite(lng)
        && lat !== 0
        && lng !== 0
        && lat >= -90
        && lat <= 90
        && lng >= -180
        && lng <= 180
    },
    distanceMeters(item) {
      const backendMeters = Number(item && item.distanceMeters)
      if (Number.isFinite(backendMeters) && backendMeters >= 0) return backendMeters
      if (!this.userLocation || !item || !this.hasValidFieldCoordinate(item)) return NaN
      const lat = Number(item.locationLat || item.latitude || item.lat)
      const lng = Number(item.locationLng || item.longitude || item.lng)
      return haversineDistanceMeters(this.userLocation.lat, this.userLocation.lng, lat, lng)
    },
    distanceText(item) {
      const backendText = String((item && item.distanceText) || '').trim()
      if (backendText) return backendText
      const meters = this.distanceMeters(item)
      if (!Number.isFinite(meters)) return ''
      if (meters < 1000) return `${Math.round(meters)}m`
      return `${(meters / 1000).toFixed(1)}km`
    },
    fieldRelationText(item) {
      if (this.isCurrentMatched(item)) {
        return '您正处于该田块中'
      }
      return ''
    },
    fieldDistanceText(item) {
      const distance = this.distanceText(item)
      if (distance === LOCATION_ERROR_TEXT || distance === LOCATION_UNKNOWN_TEXT) return distance
      if (!distance || this.isCurrentMatched(item)) return ''
      return `距此 ${distance}`
    },
    isLocationAbnormal(item) {
      return this.distanceText(item) === LOCATION_ERROR_TEXT
    },
    isLocationUnknown(item) {
      return this.distanceText(item) === LOCATION_UNKNOWN_TEXT
    },
    isWarningDistance(item) {
      if (this.isLocationAbnormal(item) || this.isLocationUnknown(item)) return false
      const meters = this.distanceMeters(item)
      return Number.isFinite(meters) && meters <= 3000
    },
    fieldLocationMetaText(item) {
      return this.fieldRelationText(item) || this.fieldDistanceText(item)
    },
    resolveTown(item) {
      return String((item && (item.town || item.townName || item.township)) || '').trim()
    },
    resolveArea(item) {
      if (!item) return ''
      const area = Number(item.areaMu != null ? item.areaMu : item.area)
      if (!Number.isFinite(area) || area <= 0) return ''
      return `${area}亩`
    },
    resolveAddress(item) {
      return String(
        (item && (item.fullAddress || item.address || item.detailAddress || item.formattedAddress || item.locationDesc)) || ''
      ).trim()
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
    resolveCoverImage(item) {
      if (!item) return ''
      const candidate = [
        item.coverImageUrl,
        item.imageUrl,
        item.coverUrl,
        item.cover,
        item.thumbUrl,
        item.fileUrl
      ].find((value) => String(value || '').trim())
      return this.resolveAssetUrl(candidate)
    },
    resolveCoverPreviewImage(item) {
      if (!item) return ''
      const candidate = [
        item.coverThumbUrl,
        item.thumbUrl,
        item.thumbnailUrl,
        item.previewUrl,
        item.coverImageUrl,
        item.imageUrl
      ].find((value) => String(value || '').trim())
      return this.resolveAssetUrl(candidate)
    },
    resolvePlanName(item) {
      if (!item) return '暂无计划'
      const fallback = item.currentPlanName || item.currentCycleName || item.planName || item.cycleName
      return String(fallback || '').trim() || '暂无进行中计划'
    },
    resolveCropTags(item) {
      return resolveCropVarietyLabels(item, 6)
    },
    onSuggestionClick(item) {
      const nextKeyword = String(item && item.value ? item.value : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.reloadFields()
    },
    closeDropdownItem(refName) {
      const target = Array.isArray(this.$refs[refName]) ? this.$refs[refName][0] : this.$refs[refName]
      if (target && typeof target.closePanel === 'function') {
        target.closePanel()
      }
    },
    dismissSearchSuggestions() {
      const searchAssist = this.$refs.searchAssist
      if (searchAssist && typeof searchAssist.hidePanelOnScroll === 'function') {
        searchAssist.hidePanelOnScroll()
      }
    },
    onPick(item) {
      const id = this.fieldId(item)
      if (!id) return
      this.$emit('input', id)
      this.$emit('change', item)
      this.closePopup()
    }
  }
}
</script>

<style lang="scss">
.field-selector {
  --field-surface: #f7faf6;
  --field-panel-bg: #f3f4f6;
  --field-border: #e3e7df;
  --field-border-strong: #73ae52;
  --field-primary: #73ae52;
  --field-text-main: #1f2a21;
  --field-text-sub: #62705f;
  --field-text-soft: #8d988c;
  --field-success: #67914c;
  --field-success-light: #edf5e8;
  --td-brand-color: #73ae52;
  --td-brand-color-active: #5e9240;
  --td-brand-color-light: #edf7e7;
  --td-text-color-brand: #73ae52;
}

.field-trigger {
  border-radius: 16rpx;
  overflow: hidden;
  border: 1rpx solid var(--field-border);
  background: var(--field-surface);
}

.selector-panel {
  position: relative;
  background: var(--field-panel-bg);
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  border-top: 1rpx solid var(--field-border);
  padding: 20rpx 18rpx 24rpx;
  height: 78vh;
  max-height: 78vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.panel-header {
  position: relative;
  min-height: 56rpx;
  display: flex;
  align-items: center;
  padding-right: 72rpx;
}

.panel-title {
  font-size: 32rpx;
  color: var(--field-text-main);
  font-weight: 700;
  letter-spacing: 0.4rpx;
}

.close-btn {
  position: absolute;
  top: -2rpx;
  right: -2rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--field-text-main);
}

.toolbar-card {
  margin-top: 14rpx;
  padding: 14rpx;
  border-radius: 20rpx;
  border: 1rpx solid var(--field-border);
  background: #ffffff;
  flex-shrink: 0;
}

.tab-bar {
  margin-top: -4rpx;
}

.toolbar-main {
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.search-wrap {
  display: flex;
  flex-direction: column;
}

.toolbar-tip {
  margin-top: 8rpx;
  min-height: 30rpx;
}

.toolbar-tip-text {
  font-size: 22rpx;
  color: var(--field-text-sub);
  line-height: 1.45;
}

.toolbar-tip-title {
  display: block;
  font-size: 24rpx;
  color: var(--field-text-main);
  font-weight: 700;
  line-height: 1.4;
}

.filter-box {
  padding: 12rpx;
  border-radius: 16rpx;
  background: #f7faf6;
  border: 1rpx solid #e7eee2;
}

.filter-box-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.filter-box-label {
  font-size: 22rpx;
  color: var(--field-text-sub);
}

.filter-box-value {
  font-size: 22rpx;
  color: var(--field-primary);
  font-weight: 700;
}

.town-menu {
  border-radius: 14rpx;
  overflow: hidden;
  background: #ffffff;
  border: 1rpx solid #e7eee2;
}

.status-row {
  margin-top: 10rpx;
}

.status-text {
  font-size: 22rpx;
  color: var(--field-text-sub);
}

.field-list {
  margin-top: 12rpx;
  flex: 1;
  min-height: 0;
  scrollbar-width: none;
}

.field-list::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding-bottom: 10rpx;
}

.field-card.field-card-skeleton {
  overflow: hidden;
  min-height: 178rpx;
}

.field-card.field-card-skeleton .field-cover,
.field-card.field-card-skeleton .field-cover-img {
  width: 144rpx;
  height: 144rpx;
}

.field-cover.field-cover-skeleton {
  background: #eef2ea;
  --td-skeleton-bg-color: #e3eadf;
  --td-skeleton-animation-flashed: rgba(198, 209, 193, 0.55);
}

.field-content.field-content-skeleton :deep(.t-skeleton) {
  width: 100%;
}

.field-content.field-content-skeleton {
  --td-skeleton-bg-color: #d6e0d1;
  --td-skeleton-animation-flashed: rgba(168, 181, 162, 0.62);
}

.field-list-inner {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding-bottom: 10rpx;
}

.field-card {
  position: relative;
  background: #ffffff;
  border: 1rpx solid var(--field-border);
  border-radius: 16rpx;
  padding: 14rpx;
  overflow: visible;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
  box-sizing: border-box;
}

.field-card.active {
  border-color: var(--field-border-strong);
  background: #f7faf6;
}

.field-card-highlight {
  position: absolute;
  inset: 4rpx;
  border-radius: inherit;
  border: 4rpx solid #4b9419;
  background: linear-gradient(180deg, rgba(107, 176, 52, 0.14) 0%, rgba(107, 176, 52, 0.06) 100%);
  box-shadow: inset 0 0 0 2rpx rgba(75, 148, 25, 0.12), 0 0 30rpx rgba(75, 148, 25, 0.22), 0 18rpx 36rpx rgba(75, 148, 25, 0.18);
  pointer-events: none;
}

.field-card.matched {
  transform: scale(1.012);
  z-index: 1;
  margin: 8rpx 10rpx;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.field-card.matched .field-name {
  font-size: 34rpx;
  color: #3f6d17;
}

.field-main {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
}

.field-cover {
  width: 152rpx;
  height: 152rpx;
  border-radius: 20rpx;
  overflow: hidden;
  flex-shrink: 0;
  border: 1rpx solid var(--field-border);
  background: #f2f5ef;
}

.field-cover-img {
  width: 152rpx;
  height: 152rpx;
  display: block;
}

.field-card.matched .field-cover,
.field-card.matched .field-cover-img {
  width: 188rpx;
  height: 188rpx;
  border-radius: 22rpx;
}

.field-content {
  flex: 1;
  min-width: 0;
}

.field-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10rpx;
}

.field-name {
  flex: 1;
  min-width: 0;
  font-size: 29rpx;
  color: var(--field-text-main);
  font-weight: 700;
  line-height: 1.38;
}

.field-meta {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: var(--field-text-sub);
  line-height: 1.5;
}

.field-tags {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.field-tags .t-tag {
  --td-tag-primary-color: var(--field-primary);
  --td-tag-primary-light-color: #edf7e7;
  --td-tag-success-color: var(--field-success);
  --td-tag-success-light-color: var(--field-success-light);
  border-radius: 999rpx;
}

.field-foot {
  margin-top: 10rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12rpx;
}

.field-address {
  flex: 1;
  min-width: 0;
  font-size: 22rpx;
  color: var(--field-text-soft);
  line-height: 1.45;
}

.field-location {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
  min-width: 0;
}

.field-distance {
  flex-shrink: 0;
  color: #879183;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.4;
  text-align: right;
}

.field-relation {
  display: inline-flex;
  align-items: center;
  color: #5f9138;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.35;
}

.field-location.error .field-distance {
  color: #d64646;
}

.field-location.warning .field-distance {
  color: #d28a20;
}

.field-location.far .field-distance {
  color: #8a9384;
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10rpx 0 4rpx;
}

.list-footer-text {
  font-size: 22rpx;
  color: var(--field-text-soft);
}

.empty-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28rpx 0 18rpx;
}

.field-selector.is-elder {
  .selector-panel {
    padding: 24rpx 22rpx 30rpx;
    height: 82vh;
    max-height: 82vh;
  }

  .panel-header,
  .head-row {
    min-height: 72rpx;
    padding-right: 90rpx;
  }

  .close-btn {
    width: 72rpx;
    height: 72rpx;
    top: -4rpx;
    right: -4rpx;
  }

  .toolbar-card,
  .filter-box,
  .field-card,
  .skeleton-card {
    padding: 18rpx;
    border-radius: 22rpx;
  }

  .field-cover,
  .field-cover-img {
    width: 184rpx;
    height: 184rpx;
    border-radius: 22rpx;
  }

  .field-card.matched .field-cover,
  .field-card.matched .field-cover-img {
    width: 224rpx;
    height: 224rpx;
  }

  .field-card.field-card-skeleton {
    min-height: 206rpx;
  }

  .field-card.field-card-skeleton .field-cover,
  .field-card.field-card-skeleton .field-cover-img {
    width: 172rpx;
    height: 172rpx;
  }

  .field-main {
    gap: 18rpx;
  }

  .field-list-inner {
    gap: 14rpx;
  }

  .panel-title,
  .toolbar-tip-title,
  .field-name {
    font-size: 34rpx;
  }

  .field-card.matched .field-name {
    font-size: 38rpx;
  }

  .status-text,
  .toolbar-tip-text,
  .filter-box-label,
  .filter-box-value,
  .field-relation,
  .field-meta,
  .field-address,
  .field-distance,
  .list-footer-text {
    font-size: 30rpx;
  }

  .t-tag,
  .t-tag__text {
    font-size: 28rpx !important;
    line-height: 1.4;
  }

  .tab-bar .t-tabs__text,
  .tab-bar .t-tab-panel {
    font-size: 30rpx;
  }
}
</style>
