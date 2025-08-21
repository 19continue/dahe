<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      toolbar
      title="田块分布"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="content-shell">
      <view class="toolbar card">
        <search-assist
          ref="searchAssist"
          class="field-search"
          :value="keyword"
          placeholder="搜索田块名、位置、作物品种"
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

        <view class="toolbar-head">
          <text class="toolbar-title">{{ toolbarTitle }}</text>
        </view>

        <view class="sort-bar">
          <view class="sort-item" :class="{ active: sortBy === 'default' }" @click="onSortTap('default')">
            <text class="sort-label">默认</text>
          </view>
          <view class="sort-item" :class="{ active: sortBy === 'distance' }" @click="onSortTap('distance')">
            <text class="sort-label">距离</text>
            <view class="sort-arrows">
              <text class="sort-arrow up" :class="{ active: sortBy === 'distance' && sortDirection === 'asc' }">&#9650;</text>
              <text class="sort-arrow down" :class="{ active: sortBy === 'distance' && sortDirection === 'desc' }">&#9660;</text>
            </view>
          </view>
          <view class="sort-item" :class="{ active: sortBy === 'area' }" @click="onSortTap('area')">
            <text class="sort-label">田块大小</text>
            <view class="sort-arrows">
              <text class="sort-arrow up" :class="{ active: sortBy === 'area' && sortDirection === 'asc' }">&#9650;</text>
              <text class="sort-arrow down" :class="{ active: sortBy === 'area' && sortDirection === 'desc' }">&#9660;</text>
            </view>
          </view>
        </view>

        <view class="filter-menu">
          <filter-dropdown-item
            class="filter-menu-item"
            ref="townshipDropdownItem"
            label="乡镇"
            :options="townshipDropdownOptions"
            :value="selectedTownshipValue"
            :elder-mode="elderMode"
            empty-text="暂无乡镇"
            @open="ensureTownshipOptions"
            @select="onTownshipSelect"
          />
          <filter-dropdown-item
            class="filter-menu-item"
            ref="cropDropdownItem"
            label="作物"
            :options="cropDropdownOptions"
            :value="selectedCropValue"
            :elder-mode="elderMode"
            empty-text="暂无作物"
            @open="ensureCropVarietyGroups"
            @select="onCropSelect"
          />
          <filter-dropdown-item
            class="filter-menu-item"
            ref="varietyDropdownItem"
            label="品种"
            :options="varietyDropdownOptions"
            :value="selectedVarietyValue"
            :elder-mode="elderMode"
            empty-text="暂无品种"
            @open="ensureCropVarietyGroups"
            @select="onVarietySelect"
          />
          <filter-dropdown-item
            class="filter-menu-item"
            ref="stageDropdownItem"
            label="阶段"
            :options="stageDropdownOptions"
            :value="status"
            :elder-mode="elderMode"
            @select="onStatusSelect"
          />
        </view>

        <view v-if="activeFilterItems.length" class="filter-summary">
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

      <scroll-view scroll-y class="list" :show-scrollbar="false" :enable-back-to-top="true" enhanced :fast-deceleration="true" :bounces="true" :lower-threshold="120" @scroll="dismissSearchSuggestions" @scrolltolower="loadMore">
        <dh-reveal-panel :loading="loading && !fields.length" class="list-reveal-panel">
          <template #skeleton>
            <view class="field-skeleton-stack">
              <view v-for="idx in 4" :key="`field-skeleton-${idx}`" class="field-card field-card-skeleton">
                <view class="field-cover field-cover-skeleton">
                  <t-skeleton theme="image" animation="flashed" />
                </view>
                <view class="field-body field-body-skeleton">
                  <t-skeleton theme="paragraph" animation="flashed" :row-col="fieldSkeletonRows" />
                </view>
              </view>
            </view>
          </template>

          <template v-if="fields.length">
            <view v-for="item in fields" :key="item.id" class="field-card" :class="{ matched: isCurrentMatched(item) }" @click="goDetail(item)">
              <view class="field-cover">
                <dh-smart-image
                  class="field-cover-img"
                  :src="resolveCoverImage(item)"
                  :preview-src="resolveCoverPreviewImage(item)"
                  mode="aspectFill"
                  :lazy-load="true"
                  :on-demand="true"
                  root-selector=".list"
                  loading-text="加载中..."
                  empty-text="田块"
                  error-text="图片异常"
                />
              </view>

              <view class="field-body">
                <view class="field-head">
                  <view class="field-title-wrap">
                    <text class="field-name">{{ item.name }}</text>
                    <text class="field-region">{{ formatRegionTown(item) || '未设置位置' }}</text>
                  </view>
                  <text class="stage-badge" :class="`stage-${item.stage || item.status || 'idle'}`">{{ formatStage(item.stage || item.status) }}</text>
                </view>

                <view class="field-crops" v-if="resolveCropLabels(item).length">
                  <t-tag
                    v-for="label in resolveCropLabels(item)"
                    :key="`${item.id}-${label}`"
                    size="small"
                    theme="success"
                    variant="light"
                  >
                    {{ label }}
                  </t-tag>
                </view>

                <text class="field-address" v-if="resolveAddress(item)">{{ resolveAddress(item) }}</text>

                <view class="field-foot">
                  <text class="field-area">{{ formatArea(item) }}</text>
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
          </template>
          <view v-else class="state-card empty-card">
            <text class="empty-title">暂无匹配田块</text>
            <text class="empty-desc">可以调整关键词或筛选条件后再试。</text>
          </view>
        </dh-reveal-panel>
        <view v-if="loading && fields.length" class="load-more-skeleton">
          <view class="field-skeleton-stack compact">
            <view v-for="idx in 2" :key="`field-more-skeleton-${idx}`" class="field-card field-card-skeleton">
              <view class="field-cover field-cover-skeleton">
                <t-skeleton theme="image" animation="flashed" />
              </view>
              <view class="field-body field-body-skeleton">
                <t-skeleton theme="paragraph" animation="flashed" :row-col="fieldSkeletonRows" />
              </view>
            </view>
          </view>
        </view>
        <view v-else-if="fields.length && !finished" class="state-tip">上拉加载更多</view>
        <view v-else-if="finished && fields.length" class="state-tip">已加载全部</view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isApprovedUser } from '../../utils/auth'
import { isElderMode } from '../../utils/accessibility'
import { getCurrentLocation, haversineDistanceMeters, formatDistance } from '../../utils/amap'
import { resolveCropVarietyLabels } from '../../utils/crop-variety'
import SearchAssist from '../../components/search-assist.vue'
import FilterDropdownItem from '../../components/filter-dropdown-item.vue'
import DhSmartImage from '../../components/dh-smart-image.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import { writeFieldDetailSnapshot } from '../../utils/field-detail-snapshot'
import { readSearchValue } from '../../utils/search-suggestion'
import { fetchRemoteSearchSuggestions, shouldFetchRemoteSearchSuggestions } from '../../utils/remote-search-suggestions'
import { readSearchHistory, pushSearchHistory, clearSearchHistory, removeSearchHistoryItem } from '../../utils/search-history'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'

const FIELD_CARD_SKELETON_ROWS = [
  [
    { width: '68%', height: '30rpx', borderRadius: '12rpx', marginRight: '12rpx' },
    { width: '20%', height: '24rpx', borderRadius: '999rpx' }
  ],
  { width: '54%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '24%', height: '24rpx', margin: '14rpx 10rpx 0 0', borderRadius: '999rpx' },
    { width: '20%', height: '24rpx', margin: '14rpx 0 0 0', borderRadius: '999rpx' }
  ],
  [
    { width: '24%', height: '22rpx', margin: '42rpx 12rpx 0 0', borderRadius: '10rpx' },
    { width: '30%', height: '22rpx', margin: '42rpx 0 0 0', borderRadius: '10rpx' }
  ]
]

export default {
  components: {
    SearchAssist,
    FilterDropdownItem,
    DhSmartImage,
    DhRevealPanel
  },
  data() {
    return {
      keyword: '',
      appliedKeyword: '',
      status: '',
      loading: true,
      elderMode: false,
      fields: [],
      townshipOptions: [{ value: '', label: '全部乡镇' }],
      cropOptions: [{ value: '', label: '全部作物' }],
      varietyOptions: [{ value: '', label: '全部品种' }],
      varietyGroups: [],
      sortBy: 'default',
      sortDirection: 'asc',
      remoteSuggestions: [],
      keywordHistory: [],
      suggestionSeq: 0,
      suggestionTimer: null,
      page: 1,
      pageSize: 12,
      total: 0,
      finished: false,
      initialized: false,
      refreshMark: '',
      userLocation: null,
      locating: false,
      townshipsLoaded: false,
      varietyGroupsLoaded: false,
      selectedTownshipIndex: 0,
      selectedCropIndex: 0,
      selectedVarietyIndex: 0,
      fieldSkeletonRows: FIELD_CARD_SKELETON_ROWS,
      stageOptions: [
        { value: '', label: '全部阶段' },
        { value: 'idle', label: '空闲' },
        { value: 'sowing', label: '播种' },
        { value: 'growing', label: '生长' },
        { value: 'harvesting', label: '收获' },
        { value: 'fallow', label: '休耕' }
      ]
    }
  },
  computed: {
    selectedTownshipValue() {
      return this.townshipOptions[this.selectedTownshipIndex]?.value || ''
    },
    selectedTownshipLabel() {
      return this.townshipOptions[this.selectedTownshipIndex]?.label || '全部乡镇'
    },
    selectedCropValue() {
      return this.cropOptions[this.selectedCropIndex]?.value || ''
    },
    selectedCropLabel() {
      return this.cropOptions[this.selectedCropIndex]?.label || '全部作物'
    },
    selectedVarietyValue() {
      return this.varietyOptions[this.selectedVarietyIndex]?.value || ''
    },
    selectedVarietyLabel() {
      return this.varietyOptions[this.selectedVarietyIndex]?.label || '全部品种'
    },
    townshipDropdownOptions() {
      return this.townshipOptions
    },
    cropDropdownOptions() {
      return this.cropOptions
    },
    varietyDropdownOptions() {
      return this.varietyOptions
    },
    stageDropdownOptions() {
      return this.stageOptions
    },
    toolbarTitle() {
      const total = this.total || this.fields.length || 0
      const sortMap = {
        default: '默认排序',
        distance: `距离${this.sortDirection === 'desc' ? '由远到近' : '由近到远'}`,
        area: `田块大小${this.sortDirection === 'asc' ? '由小到大' : '由大到小'}`
      }
      return `共 ${total} 个田块 · ${sortMap[this.sortBy] || sortMap.default}`
    },
    keywordSuggestions() {
      return Array.isArray(this.remoteSuggestions) ? this.remoteSuggestions : []
    },
    activeFilterItems() {
      const list = []
      if (this.appliedKeyword) {
        list.push({ key: 'keyword', label: `关键词：${this.appliedKeyword}` })
      }
      if (this.selectedTownshipValue) {
        list.push({ key: 'township', label: this.selectedTownshipLabel })
      }
      if (this.selectedCropValue) {
        list.push({ key: 'crop', label: this.selectedCropLabel })
      }
      if (this.selectedVarietyValue) {
        list.push({ key: 'variety', label: this.selectedVarietyLabel })
      }
      if (this.status) {
        const stage = this.stageOptions.find((item) => String(item.value) === String(this.status))
        if (stage) {
          list.push({ key: 'status', label: stage.label })
        }
      }
      return list
    }
  },

  async onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    this.elderMode = isElderMode()
    this.loadKeywordHistory()
    const topic = refreshTopics.fields()
    if (!this.initialized) {
      await this.ensureUserLocation()
      await this.refreshFields()
      this.refreshMark = readRefreshMark(topic)
      this.initialized = true
      return
    }
    if (hasDataChanged(topic, this.refreshMark)) {
      await this.ensureUserLocation()
      await this.refreshFields()
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
      this.refreshFields()
    },
    onKeywordClear() {
      const hadAppliedKeyword = !!this.appliedKeyword
      this.keyword = ''
      this.appliedKeyword = ''
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      if (hadAppliedKeyword) {
        this.refreshFields()
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
      const rows = await fetchRemoteSearchSuggestions('field', keyword, 6)
      if (requestSeq !== this.suggestionSeq) {
        return
      }
      this.remoteSuggestions = rows
    },
    loadKeywordHistory() {
      this.keywordHistory = readSearchHistory('field', 6)
    },
    persistKeywordHistory(keyword) {
      const safeKeyword = String(keyword || '').trim()
      if (!safeKeyword) return
      this.keywordHistory = pushSearchHistory('field', safeKeyword, 6)
    },
    onHistoryClick(item) {
      const nextKeyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.refreshFields()
    },
    onHistoryClear() {
      this.keywordHistory = clearSearchHistory('field')
    },
    onHistoryRemove(item) {
      const keyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      this.keywordHistory = removeSearchHistoryItem('field', keyword, 6)
    },
    async ensureUserLocation() {
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
        const location = await getCurrentLocation()
        this.userLocation = {
          lat: Number(location.latitude),
          lng: Number(location.longitude)
        }
        return this.userLocation
      } catch (e) {
        this.userLocation = null
        return null
      } finally {
        this.locating = false
      }
    },
    async ensureTownshipOptions() {
      if (this.townshipsLoaded) return
      try {
        const rows = await api.get('/miniapp/meta/options/townships', {})
        const uniqueRows = Array.from(new Set((Array.isArray(rows) ? rows : []).map((item) => String(item || '').trim()).filter(Boolean)))
        this.townshipOptions = [{ value: '', label: '全部乡镇' }, ...uniqueRows.map((item) => ({ value: item, label: item }))]
        this.townshipsLoaded = true
      } catch (e) {
        console.error('加载乡镇筛选失败', e)
      }
    },
    async ensureCropVarietyGroups() {
      if (this.varietyGroupsLoaded) return
      try {
        const groups = await api.get('/miniapp/meta/options/variety-groups', {})
        const normalizedGroups = (Array.isArray(groups) ? groups : [])
          .map((group) => ({
            cropType: String((group && group.cropType) || '').trim(),
            varieties: Array.from(
              new Set((Array.isArray(group && group.varieties) ? group.varieties : []).map((item) => String(item || '').trim()).filter(Boolean))
            )
          }))
          .filter((group) => group.cropType)
        this.cropOptions = [
          { value: '', label: '全部作物' },
          ...normalizedGroups.map((group) => ({
            value: group.cropType,
            label: group.cropType
          }))
        ]
        this.varietyGroups = normalizedGroups
        this.varietyGroupsLoaded = true
        this.syncVarietyOptions()
      } catch (e) {
        console.error('加载作物品种筛选失败', e)
      }
    },
    syncVarietyOptions() {
      if (!this.varietyGroupsLoaded) {
        this.varietyOptions = [{ value: '', label: '全部品种' }]
        return
      }
      const currentCrop = this.selectedCropValue
      const rows = []
      this.varietyGroups
        .filter((group) => !currentCrop || group.cropType === currentCrop)
        .forEach((group) => {
          group.varieties.forEach((variety) => {
            rows.push({
              value: `${group.cropType}||${variety}`,
              label: currentCrop ? variety : `${group.cropType} · ${variety}`
            })
          })
        })
      this.varietyOptions = [{ value: '', label: '全部作物' }, ...rows]
    },
    async clearFilter(key) {
      const name = String(key || '').trim()
      if (name === 'keyword') {
        this.keyword = ''
        this.appliedKeyword = ''
      } else if (name === 'township') {
        this.selectedTownshipIndex = 0
      } else if (name === 'crop') {
        this.selectedCropIndex = 0
        this.selectedVarietyIndex = 0
        this.syncVarietyOptions()
      } else if (name === 'variety') {
        this.selectedVarietyIndex = 0
      } else if (name === 'status') {
        this.status = ''
      }
      await this.refreshFields()
    },
    async refreshFields() {
      this.loading = true
      this.page = 1
      this.finished = false
      this.fields = []
      await this.fetchFields(true)
    },
    async loadMore() {
      if (this.loading || this.finished) return
      this.page += 1
      await this.fetchFields(false)
    },
    async fetchFields(reset) {
      this.loading = true
      try {
        const varietyFilter = this.parseVarietyFilterValue(this.selectedVarietyValue)
        const location = this.userLocation
        const res = await api.get('/miniapp/fields', {
          page: this.page,
          pageSize: this.pageSize,
          keyword: this.appliedKeyword || undefined,
          township: this.selectedTownshipValue || undefined,
          stage: this.status || undefined,
          cropType: varietyFilter.cropType || this.selectedCropValue || undefined,
          cropVariety: varietyFilter.cropVariety || undefined,
          sortBy: this.sortBy,
          sortDirection: this.sortBy === 'default' ? undefined : this.sortDirection,
          latitude: location ? location.lat : undefined,
          longitude: location ? location.lng : undefined
        })
        const rows = Array.isArray(res && res.records) ? res.records : []
        this.total = Number((res && res.total) || 0)
        this.fields = reset ? rows : this.fields.concat(rows)
        this.finished = this.fields.length >= this.total || rows.length < this.pageSize
      } catch (e) {
        console.error('加载田块失败', e)
      } finally {
        this.loading = false
      }
    },
    parseVarietyFilterValue(value) {
      const text = String(value || '').trim()
      if (!text || !text.includes('||')) {
        return { cropType: '', cropVariety: '' }
      }
      const [cropType, ...rest] = text.split('||')
      return {
        cropType: String(cropType || '').trim(),
        cropVariety: String(rest.join('||') || '').trim()
      }
    },
    resolveCropLabels(row) {
      return resolveCropVarietyLabels(row, 4)
    },
    resolveAddress(row) {
      const item = row || {}
      return String(item.locationDesc || item.formattedAddress || '').trim()
    },
    formatArea(row) {
      const area = Number((row && row.areaMu) || 0)
      if (!Number.isFinite(area) || area <= 0) return '面积待补充'
      return `${area} 亩`
    },
    formatRegionTown(row) {
      const src = row || {}
      return [src.province, src.city, src.district, src.township]
        .map((item) => String(item || '').trim())
        .filter(Boolean)
        .join(' · ')
    },
    formatStage(status) {
      const key = String(status || '').trim().toLowerCase()
      const map = {
        idle: '空闲',
        sowing: '播种',
        growing: '生长',
        harvesting: '收获',
        fallow: '休耕'
      }
      return map[key] || '未知'
    },
    distanceText(item) {
      if (!item) return ''
      if (item.distanceText) return String(item.distanceText).trim()
      if (!this.userLocation || !this.hasValidFieldCoordinate(item)) return ''
      const lat = Number(item.locationLat || item.latitude || item.lat)
      const lng = Number(item.locationLng || item.longitude || item.lng)
      const meters = haversineDistanceMeters(this.userLocation.lat, this.userLocation.lng, lat, lng)
      return formatDistance(meters)
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
    isCurrentMatched(item) {
      return Number(item && item.currentMatched) === 1 || (item && item.currentMatched === true)
    },
    fieldRelationText(item) {
      if (this.isCurrentMatched(item)) {
        return '您正处于该田块中'
      }
      return ''
    },
    fieldDistanceText(item) {
      const distance = this.distanceText(item)
      if (distance === '位置异常' || distance === '未知') return distance
      if (!distance || this.isCurrentMatched(item)) return ''
      return `距此 ${distance}`
    },
    isLocationAbnormal(item) {
      return this.distanceText(item) === '位置异常'
    },
    isLocationUnknown(item) {
      return this.distanceText(item) === '未知'
    },
    isWarningDistance(item) {
      if (this.isLocationAbnormal(item) || this.isLocationUnknown(item)) return false
      const meters = this.distanceMeters(item)
      return Number.isFinite(meters) && meters <= 3000
    },
    fieldLocationMetaText(item) {
      return this.fieldRelationText(item) || this.fieldDistanceText(item)
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
      const candidate = [item.coverImageUrl, item.imageUrl, item.coverUrl, item.cover, item.thumbUrl, item.fileUrl].find((value) => String(value || '').trim())
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
    onSuggestionClick(item) {
      const nextKeyword = String(item && item.value ? item.value : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.refreshFields()
    },
    async onSortTap(nextSortBy) {
      const sortBy = String(nextSortBy || 'default').trim() || 'default'
      if (sortBy === 'distance') {
        const location = await this.ensureUserLocation()
        if (!location) {
          uni.showToast({
            title: '未获取到当前位置',
            icon: 'none'
          })
          return
        }
      }
      if (sortBy === 'default') {
        this.sortBy = 'default'
        this.sortDirection = 'asc'
      } else if (this.sortBy === sortBy) {
        this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc'
      } else {
        this.sortBy = sortBy
        this.sortDirection = sortBy === 'area' ? 'desc' : 'asc'
      }
      await this.refreshFields()
    },
    closeDropdownItem(refName) {
      const target = Array.isArray(this.$refs[refName]) ? this.$refs[refName][0] : this.$refs[refName]
      if (target && typeof target.closePanel === 'function') {
        target.closePanel()
      }
    },
    async onTownshipSelect(option) {
      const value = String((option && option.value) || '')
      const idx = this.townshipOptions.findIndex((item) => String(item.value) === value)
      this.selectedTownshipIndex = idx >= 0 ? idx : 0
      this.closeDropdownItem('townshipDropdownItem')
      await this.refreshFields()
    },
    async onCropSelect(option) {
      const value = String((option && option.value) || '')
      const idx = this.cropOptions.findIndex((item) => String(item.value) === value)
      this.selectedCropIndex = idx >= 0 ? idx : 0
      this.selectedVarietyIndex = 0
      await this.ensureCropVarietyGroups()
      this.syncVarietyOptions()
      this.closeDropdownItem('cropDropdownItem')
      await this.refreshFields()
    },
    async onVarietySelect(option) {
      const value = String((option && option.value) || '')
      const idx = this.varietyOptions.findIndex((item) => String(item.value) === value)
      this.selectedVarietyIndex = idx >= 0 ? idx : 0
      this.closeDropdownItem('varietyDropdownItem')
      await this.refreshFields()
    },
    async onStatusSelect(option) {
      this.status = String((option && option.value) || '')
      this.closeDropdownItem('stageDropdownItem')
      await this.refreshFields()
    },
    goDetail(item) {
      writeFieldDetailSnapshot(item)
      const id = String(item && item.id != null ? item.id : '').trim()
      if (!id) return
      uni.navigateTo({ url: `/pages/field/detail?id=${id}` })
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

.field-search {
  --dh-search-bg-color: #ffffff;
  --dh-search-placeholder-color: #98a394;
  --dh-search-text-color: #233021;
  --dh-search-height: 84rpx;
  --dh-search-radius: 18rpx;
  border-radius: 18rpx;
}

.toolbar-head {
  margin-top: 14rpx;
}

.toolbar-title {
  display: block;
  font-size: 27rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.4;
}

.sort-bar {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.sort-item {
  flex: 1 1 0;
  min-width: 0;
  height: 68rpx;
  padding: 0 18rpx;
  border-radius: 18rpx;
  border: 1rpx solid #dde6d8;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
}

.sort-item.active {
  border-color: #6e8d5f;
  background: #f2f8ed;
}

.sort-label {
  font-size: 25rpx;
  color: #4f5d49;
  font-weight: 600;
  line-height: 1;
}

.sort-item.active .sort-label {
  color: #36512d;
}

.sort-arrows {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
}

.sort-arrow {
  font-size: 16rpx;
  line-height: 1;
  color: #b1b9ac;
}

.sort-arrow.active {
  color: #5c7a4d;
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

.filter-summary {
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

.list {
  flex: 1;
  min-height: 0;
  margin-top: 14rpx;
  box-sizing: border-box;
}

.field-card {
  display: flex;
  gap: 16rpx;
  padding: 18rpx;
  min-height: 204rpx;
  margin-bottom: 12rpx;
  border-radius: 22rpx;
  background: #ffffff;
  border: 1rpx solid var(--dh-color-border);
}

.field-card.matched {
  border-color: #7ea75d;
  background: #f8fbf4;
  box-shadow: 0 10rpx 24rpx rgba(96, 136, 62, 0.1);
}

.field-cover {
  width: 214rpx;
  height: 168rpx;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 18rpx;
  border: 1rpx solid #e2e8dc;
  background: #eef2ea;
}

.field-cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.field-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.field-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.field-title-wrap {
  flex: 1;
  min-width: 0;
}

.field-name {
  display: block;
  font-size: 31rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.38;
}

.field-region {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6f7d6a;
  line-height: 1.45;
}

.stage-badge {
  flex-shrink: 0;
  min-height: 38rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eff2ee;
  color: #6e796c;
  font-size: 21rpx;
  font-weight: 600;
}

.stage-sowing,
.stage-growing {
  background: #eef5eb;
  color: #5e7f50;
}

.stage-harvesting {
  background: #f6f1e7;
  color: #856d35;
}

.field-crops {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.field-crops .t-tag {
  --td-tag-success-color: #597d46;
  --td-tag-success-light-color: #edf6e7;
  border-radius: 999rpx;
}

.field-address {
  margin-top: 10rpx;
  display: block;
  font-size: 22rpx;
  color: #8b9585;
  line-height: 1.45;
}

.field-foot {
  margin-top: auto;
  padding-top: 12rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12rpx;
}

.field-area {
  font-size: 23rpx;
  color: #5a6855;
  font-weight: 600;
}

.field-location {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
  min-width: 0;
}

.field-relation {
  display: inline-flex;
  align-items: center;
  color: #5f9138;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.35;
}

.field-distance {
  font-size: 23rpx;
  color: #8a9384;
  font-weight: 600;
  text-align: right;
}

.field-location.error .field-distance {
  color: #d64646;
}

.field-location.warning .field-distance {
  color: #d28a20;
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

.field-skeleton-stack {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 2rpx 0 6rpx;
}

.field-skeleton-stack.compact {
  padding: 6rpx 0 0;
}

.field-card.field-card-skeleton {
  overflow: hidden;
  min-height: 192rpx;
}

.field-card.field-card-skeleton .field-cover {
  height: 156rpx;
}

.field-cover.field-cover-skeleton {
  background: #eef2ea;
  --td-skeleton-bg-color: #e3eadf;
  --td-skeleton-animation-flashed: rgba(198, 209, 193, 0.55);
}

.field-body.field-body-skeleton :deep(.t-skeleton) {
  width: 100%;
}

.field-body.field-body-skeleton {
  --td-skeleton-bg-color: #d6e0d1;
  --td-skeleton-animation-flashed: rgba(168, 181, 162, 0.62);
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
  .field-name,
  .empty-title {
    font-size: 36rpx;
  }

  .sort-item {
    height: 82rpx;
    padding: 0 20rpx;
  }

  .sort-label {
    font-size: 30rpx;
  }

  .sort-arrow {
    font-size: 18rpx;
  }

  .field-region,
  .field-relation,
  .field-address,
  .field-area,
  .field-distance,
  .state-card,
  .empty-desc,
  .state-tip {
    font-size: 30rpx;
  }

  .field-search {
    --dh-search-height: 98rpx;
    --dh-search-font-size: 32rpx;
    --dh-search-radius: 22rpx;
  }

  .toolbar,
  .field-card,
  .state-card,
  .field-cover {
    border-radius: 24rpx;
  }

  .field-cover {
    width: 238rpx;
    height: 188rpx;
  }

  .field-card.field-card-skeleton {
    min-height: 206rpx;
  }

  .field-card.field-card-skeleton .field-cover {
    height: 176rpx;
  }

  .stage-badge {
    min-height: 48rpx;
    font-size: 28rpx;
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
