<template>
  <view class="farm-container" :class="{ 'elder-mode': elderMode }">
    <view
      class="fixed-header"
      :class="{ 'is-solid': headerSolid }"
    >
      <view class="fixed-header-content">
        <text class="fixed-header-title">大禾种业</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="page-scroll"
      :show-scrollbar="false"
      :enable-back-to-top="true"
      enhanced
      :fast-deceleration="true"
      :bounces="true"
      :upper-threshold="96"
      :lower-threshold="24"
      @touchstart="handlePullTouchStart"
      @touchmove="handlePullTouchMove"
      @touchend="handlePullTouchEnd"
      @touchcancel="handlePullTouchCancel"
      @scrolltoupper="handleScrollToUpper"
      @scroll="handleScroll"
    >
      <view class="page-scroll-body">
        <view
          class="pull-refresh-indicator"
          :class="{
            'is-visible': pullIndicatorVisible,
            'is-armed': pullIndicatorArmed,
            'is-refreshing': pullRefreshing,
            'is-dragging': pullDistance > 0 && !pullRefreshing
          }"
          :style="pullIndicatorStyle"
        >
          <view class="pull-refresh-indicator-track">
            <view class="pull-refresh-indicator-dots">
              <text class="pull-refresh-dot"></text>
              <text class="pull-refresh-dot"></text>
              <text class="pull-refresh-dot"></text>
            </view>
            <text class="pull-refresh-label">{{ pullRefreshLabel }}</text>
          </view>
        </view>
        <view style="height: 10rpx; background-color: transparent"></view>
        <view class="gradient-bg"></view>
        <view class="gradient-bg-2"></view>
        <view class="sun"></view>
        <view class="cloud"></view>

        <view class="content">
          <view class="header">
          <view class="greeting">
            <text class="time-greeting">{{ greeting }}！</text>
            <text class="company-name">大禾种业</text>
          </view>
          <text class="logining-greeting">欢迎登录！</text>
          <view class="weather-card">
            <view v-if="dashboardLoading" class="weather-data-skeleton">
              <t-skeleton theme="paragraph" animation="flashed" :row-col="homeStatsSkeletonRows" />
            </view>
            <view v-else class="weather-data">
              <view class="weather-item">
                <view class="weather-text">今日记录</view>
                <text class="weather-value">{{ todayCount }}</text>
              </view>
              <view class="weather-item">
                <view class="weather-text">田块总数</view>
                <text class="weather-value">{{ fieldTotal }}</text>
              </view>
              <view class="weather-item">
                <view class="weather-text">种子批次</view>
                <text class="weather-value">{{ seedTotal }}</text>
              </view>
              <view class="weather-item">
                <view class="weather-text">最近更新</view>
                <text class="weather-value">{{ latestUpdateTime }}</text>
              </view>
            </view>
            <view v-if="locationWeatherLoading" class="location-weather-skeleton">
              <t-skeleton theme="paragraph" animation="flashed" :row-col="homeLocationSkeletonRows" />
            </view>
            <view v-else class="location-weather">
              <view class="location-weather-main">
                <text class="lw-line">位置：{{ locationLabel }}</text>
                <text class="lw-line">天气：{{ weatherLabel }}</text>
              </view>
              <view
                class="weather-refresh-btn"
                data-interactive="weather-refresh"
                :class="{ 'is-refreshing': weatherRefreshing || pullRefreshing }"
                hover-class="weather-refresh-btn-hover"
                hover-stay-time="70"
                @touchstart.stop.prevent="handleWeatherRefreshTouchStart"
                @tap.stop="handleWeatherRefreshTap"
                @touchend.stop.prevent="handleWeatherRefreshTap"
              >
                <t-icon
                  class="weather-refresh-icon"
                  data-interactive="weather-refresh"
                  name="refresh"
                  size="34rpx"
                />
              </view>
            </view>
          </view>
        </view>

        <view class="function-menu">
          <view class="function-item" @click="navigateToFarmRecord">
            <view class="function-icon">农</view>
            <text class="function-name">农事管理</text>
          </view>
          <view class="function-item" @click="navigateToSeedList">
            <view class="function-icon">质</view>
            <text class="function-name">种子质量</text>
          </view>
          <view class="function-item" @click="navigateToFieldList">
            <view class="function-icon">田</view>
            <text class="function-name">田块分布</text>
          </view>
        </view>

        <view class="plan-card" @click="navigateToAllRecords">
          <view class="plan-title">
            <view class="title-icon">📅</view>
            <text class="plan-title-text">我的农事记录</text>
            <text class="plan-count">{{ farmWorkCount }}项</text>
          </view>
          <view class="germination-summary">
            <text>查看我提交的农事记录</text>
            <text class="arrow-right">›</text>
          </view>
        </view>

        <view class="plan-card" @click="navigateToSeedList">
          <view class="plan-title">
            <view class="title-icon">🌱</view>
            <text class="plan-title-text">种子质量档案</text>
            <text class="plan-count">{{ seedTotal }}项</text>
          </view>
          <view class="germination-summary">
            <text>查看更多批次检测</text>
            <text class="arrow-right">›</text>
          </view>
        </view>

        <view v-if="commonFieldsLoading || commonFields.length || nearbyFieldsLoading || nearbyFields.length" class="field-section">
          <view class="section-header">
            <text class="section-title">常用田块</text>
            <view class="search-button" @click="searchField">
              <text class="search-text">搜索田块</text>
            </view>
          </view>

          <view v-if="commonFieldsLoading" class="field-grid field-grid-skeleton">
            <view v-for="idx in 4" :key="`common-field-skeleton-${idx}`" class="field-item field-item-skeleton">
              <view class="field-image field-image-skeleton">
                <t-skeleton theme="image" animation="flashed" />
              </view>
              <view class="field-info field-info-skeleton">
                <t-skeleton theme="paragraph" animation="flashed" :row-col="homeFieldSkeletonRows" />
              </view>
            </view>
          </view>
          <view v-else-if="commonFields.length" class="field-grid">
            <view
              v-for="field in commonFields"
              :key="field.id"
              class="field-item"
              :class="{ 'is-current-field': isCurrentMatched(field) }"
              @click="handleFieldClick(field)"
            >
              <dh-smart-image
                class="field-image"
                :src="field.coverImageUrl"
                :preview-src="resolveCoverPreviewImage(field)"
                mode="aspectFill"
                :lazy-load="true"
                :on-demand="true"
                root-selector=".page-scroll"
                loading-text="田块加载中..."
                empty-text="田块"
                error-text="图片异常"
              />
              <view class="field-info">
                <text class="field-name">{{ field.name }}</text>
                <text class="field-variety">{{ formatCropVarietyPair(field) }}</text>
                <view
                  v-if="fieldLocationMetaText(field)"
                  class="field-location"
                  :class="{
                    matched: isCurrentMatched(field),
                    error: !isCurrentMatched(field) && isLocationAbnormal(field),
                    warning: !isCurrentMatched(field) && isWarningDistance(field),
                    far: !isCurrentMatched(field) && !isLocationAbnormal(field) && !isWarningDistance(field)
                  }"
                >
                  <text v-if="fieldRelationText(field)" class="field-relation">{{ fieldRelationText(field) }}</text>
                  <text class="field-distance" v-if="fieldDistanceText(field)">{{ fieldDistanceText(field) }}</text>
                </view>
              </view>
            </view>
          </view>
          <view v-else class="nearby-empty-card">
            <text class="nearby-empty-title">暂无常用田块</text>
            <text class="nearby-empty-desc">新增几次农事记录后，这里会优先展示你最常使用的田块。</text>
          </view>
        </view>

        <view class="field-section">
          <view class="section-header">
            <text class="section-title">附近田块</text>
          </view>

          <view v-if="nearbyFieldsLoading" class="field-grid field-grid-skeleton">
            <view v-for="idx in 4" :key="`nearby-field-skeleton-${idx}`" class="field-item field-item-skeleton">
              <view class="field-image field-image-skeleton">
                <t-skeleton theme="image" animation="flashed" />
              </view>
              <view class="field-info field-info-skeleton">
                <t-skeleton theme="paragraph" animation="flashed" :row-col="homeFieldSkeletonRows" />
              </view>
            </view>
          </view>
          <view class="field-grid" v-else-if="nearbyFields.length">
            <view
              v-for="item in nearbyFields"
              :key="item.id"
              class="field-item"
              :class="{ 'is-current-field': isCurrentMatched(item) }"
              @click="handleFieldClick(item)"
            >
              <dh-smart-image
                class="field-image"
                :src="item.coverImageUrl"
                :preview-src="resolveCoverPreviewImage(item)"
                mode="aspectFill"
                :lazy-load="true"
                :on-demand="true"
                root-selector=".page-scroll"
                loading-text="田块加载中..."
                empty-text="田块"
                error-text="图片异常"
              />
              <view class="field-info">
                <text class="field-name">{{ item.name }}</text>
                <text class="field-variety">{{ formatCropVarietyPair(item) }}</text>
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
          <view v-else-if="!showFieldFallback" class="nearby-empty-card">
            <text class="nearby-empty-title">暂无附近田块</text>
            <text class="nearby-empty-desc">可下拉刷新，或点击天气卡片右侧刷新图标后重试。</text>
          </view>
        </view>

        <view v-if="showFieldFallback" class="field-section">
          <view class="section-header">
            <text class="section-title">田块入口</text>
            <view class="search-button" @click="searchField">
              <text class="search-text">查看田块</text>
            </view>
          </view>
          <view class="field-fallback-card">
            <text class="field-fallback-title">暂未找到常用田块和附近田块</text>
            <text class="field-fallback-desc">新用户可先查看全部田块，定位开启后也会自动匹配附近田块。</text>
            <view class="field-fallback-actions">
              <view class="field-fallback-btn primary" @click="navigateToFieldList">查看田块分布</view>
            </view>
          </view>
        </view>

          <view class="tabbar-spacer"></view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../utils/request'
import { formatCropVarietyPair as formatCropVarietyPairText } from '../utils/crop-variety'
import { getWindowWidth } from '../utils/system-info'
import { writeFieldDetailSnapshot } from '../utils/field-detail-snapshot'
import DhSmartImage from './dh-smart-image.vue'
import {
  clearAutoWeatherSnapshotCache,
  getAutoWeatherSnapshot,
  getCurrentLocation,
  formatWeatherLabel,
  formatLocationLabel,
  formatDistance
} from '../utils/amap'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../utils/data-refresh'

const GUIDE_STORAGE_KEY = 'dahe.v2.homeGuideSeen'
const HOME_WEATHER_CACHE_TTL = 15 * 60 * 1000
const HEADER_SOLID_ENTER_RPX = 88
const HEADER_SOLID_LEAVE_RPX = 52
const HEADER_RESET_TOP_PX = 96
const HOME_PULL_TOP_ACTIVE_PX = 12
const HOME_PULL_TRIGGER_DISTANCE = 108
const HOME_PULL_MAX_DISTANCE = 86
const HOME_PULL_DAMP_FACTOR = 156
const HOME_PULL_INDICATOR_VISIBLE_DISTANCE = 22
const HOME_FIELD_SKELETON_MIN_MS = 480
const HOME_STATS_SKELETON_ROWS = [
  [
    { width: '18%', height: '22rpx', borderRadius: '10rpx', marginRight: '8%' },
    { width: '18%', height: '22rpx', borderRadius: '10rpx', marginRight: '8%' },
    { width: '18%', height: '22rpx', borderRadius: '10rpx', marginRight: '8%' },
    { width: '18%', height: '22rpx', borderRadius: '10rpx' }
  ],
  [
    { width: '16%', height: '34rpx', margin: '12rpx 10% 0 0', borderRadius: '12rpx' },
    { width: '16%', height: '34rpx', margin: '12rpx 10% 0 0', borderRadius: '12rpx' },
    { width: '16%', height: '34rpx', margin: '12rpx 10% 0 0', borderRadius: '12rpx' },
    { width: '16%', height: '34rpx', margin: '12rpx 0 0 0', borderRadius: '12rpx' }
  ]
]
const HOME_LOCATION_SKELETON_ROWS = [
  { width: '72%', height: '22rpx', borderRadius: '10rpx' },
  { width: '58%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' }
]
const HOME_FIELD_SKELETON_ROWS = [
  { width: '72%', height: '30rpx', borderRadius: '12rpx' },
  { width: '56%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  { width: '34%', height: '22rpx', margin: '44rpx 0 0 0', borderRadius: '10rpx' }
]

export default {
  components: {
    DhSmartImage
  },
  props: {
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      records: [],
      commonFieldList: [],
      nearbyFieldList: [],
      dashboardLoading: false,
      locationWeatherLoading: false,
      commonFieldsLoading: false,
      nearbyFieldsLoading: false,
      homeStatsSkeletonRows: HOME_STATS_SKELETON_ROWS,
      homeLocationSkeletonRows: HOME_LOCATION_SKELETON_ROWS,
      homeFieldSkeletonRows: HOME_FIELD_SKELETON_ROWS,
      fieldTotal: 0,
      seedTotal: 0,
      farmWorkCount: 0,
      weatherSnapshot: null,
      headerSolid: false,
      rpxToPx: 1,
      statusBarTheme: 'light',
      initialized: false,
      homeRefreshMarks: {
        farmRecords: '',
        fields: '',
        seedBatches: ''
      },
      commonFieldsRequestSeq: 0,
      nearbyFieldsRequestSeq: 0,
      weatherRefreshing: false,
      pullRefreshing: false,
      pullDistance: 0,
      pullReleaseReady: false,
      currentScrollTop: 0,
      pullTouchStartY: 0,
      pullGestureActive: false,
      pullGestureBlocked: false,
      pullTouchHolding: false,
    }
  },
  computed: {
    greeting() {
      const h = new Date().getHours()
      if (h < 12) return '早上好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    todayCount() {
      const d = new Date()
      const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      return this.records.filter((x) => String(x.workDate || '').slice(0, 10) === day).length
    },
    latestUpdateTime() {
      const first = this.records[0]
      if (!first || !first.workDate) return '--:--'
      const s = String(first.workDate).replace('T', ' ')
      return s.length >= 16 ? s.slice(11, 16) : '--:--'
    },
    commonFields() {
      return this.commonFieldList.slice(0, 6)
    },
    locationLabel() {
      return formatLocationLabel(this.weatherSnapshot)
    },
    weatherLabel() {
      return formatWeatherLabel(this.weatherSnapshot)
    },
    pullIndicatorVisible() {
      return this.pullRefreshing || this.pullDistance > HOME_PULL_INDICATOR_VISIBLE_DISTANCE
    },
    pullIndicatorArmed() {
      return this.pullReleaseReady && !this.pullRefreshing
    },
    pullRefreshLabel() {
      if (this.pullRefreshing) return '正在更新'
      if (this.pullReleaseReady) return '松手刷新'
      return '下拉刷新'
    },
    pullIndicatorStyle() {
      const distance = Math.max(0, Number(this.pullDistance) || 0)
      const visibleDistance = HOME_PULL_INDICATOR_VISIBLE_DISTANCE
      const visibleProgressBase = Math.max(0, distance - visibleDistance)
      const progress = this.pullRefreshing ? 1 : Math.min(visibleProgressBase / 60, 1)
      const opacity = this.pullRefreshing ? 1 : Math.min(Math.max(visibleProgressBase / 18, 0), 1)
      const translateY = this.pullRefreshing
        ? 62
        : Math.max(-42, -42 + visibleProgressBase)
      const scale = this.pullRefreshing
        ? 1
        : 0.92 + Math.min(progress, 1) * 0.08
      return `opacity:${opacity};transform: translate3d(-50%, ${translateY}px, 0) scale(${scale});`
    },
    nearbyFields() {
      return this.nearbyFieldList.slice(0, 6)
    },
    showFieldFallback() {
      return !this.commonFieldsLoading && !this.nearbyFieldsLoading && !this.commonFields.length && !this.nearbyFields.length
    }
  },
  methods: {
    syncHomeRefreshMarks() {
      this.homeRefreshMarks = {
        farmRecords: readRefreshMark(refreshTopics.farmRecords()),
        fields: readRefreshMark(refreshTopics.fields()),
        seedBatches: readRefreshMark(refreshTopics.seedBatches())
      }
    },
    hasHomeDataChanged() {
      return hasDataChanged(refreshTopics.farmRecords(), this.homeRefreshMarks.farmRecords)
        || hasDataChanged(refreshTopics.fields(), this.homeRefreshMarks.fields)
        || hasDataChanged(refreshTopics.seedBatches(), this.homeRefreshMarks.seedBatches)
    },
    async queryDashboardStats() {
      const [fieldRes, recordRes, seedRes] = await Promise.all([
        api.get('/miniapp/fields', { page: 1, pageSize: 1 }),
        api.get('/miniapp/farm-records', { page: 1, pageSize: 20, mineOnly: true }),
        api.get('/miniapp/seed-batches', { page: 1, pageSize: 1 })
      ])
      const records = (recordRes && recordRes.records) || []
      return {
        fieldTotal: fieldRes ? fieldRes.total || 0 : 0,
        records,
        farmWorkCount: recordRes ? recordRes.total || records.length : records.length,
        seedTotal: seedRes ? seedRes.total || 0 : 0
      }
    },
    applyDashboardStats(payload) {
      const next = payload || {}
      this.fieldTotal = Number(next.fieldTotal || 0)
      this.records = Array.isArray(next.records) ? next.records : []
      this.farmWorkCount = Number(next.farmWorkCount || this.records.length || 0)
      this.seedTotal = Number(next.seedTotal || 0)
    },
    async queryCommonFields(location) {
      const latitude = Number(location && location.latitude)
      const longitude = Number(location && location.longitude)
      const res = await api.get('/miniapp/fields/common', {
        page: 1,
        pageSize: 6,
        latitude: Number.isFinite(latitude) ? latitude : undefined,
        longitude: Number.isFinite(longitude) ? longitude : undefined
      })
      return Array.isArray(res && res.records) ? res.records : []
    },
    async queryNearbyFields(location) {
      const latitude = Number(location && location.latitude)
      const longitude = Number(location && location.longitude)
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return []
      }
      const res = await api.get('/miniapp/fields/nearby', {
        page: 1,
        pageSize: 6,
        latitude,
        longitude,
        radiusKm: 20
      })
      const rows = Array.isArray(res && res.records) ? res.records : []
      return rows.map((item) => ({
        ...item,
        distanceText: String(item && item.distanceText ? item.distanceText : '').trim() || this.resolveNearbyDistanceText(item, latitude, longitude)
      }))
    },
    async waitMinimumLoading(startedAt, minDurationMs) {
      const safeStartedAt = Number(startedAt || 0)
      const safeMin = Math.max(0, Number(minDurationMs) || 0)
      if (!safeStartedAt || safeMin <= 0) return
      const remain = Math.max(0, safeMin - (Date.now() - safeStartedAt))
      if (!remain) return
      await new Promise((resolve) => setTimeout(resolve, remain))
    },
    async handlePanelShow(forceRefresh = false) {
      this.initScrollScale()
      this.syncNavigationBarThemeBySolid(this.headerSolid)
      if (!this.initialized || forceRefresh) {
        await this.fetchAll()
        this.syncHomeRefreshMarks()
        this.initialized = true
      } else if (this.hasHomeDataChanged()) {
        await this.refreshHomeData({
          silent: true,
          showSuccess: false,
          refreshStats: true,
          refreshCommonFields: true
        })
        this.syncHomeRefreshMarks()
      }
      this.showGuideOnce()
    },
    handlePanelHide() {
      this.restoreDefaultNavigationBarTheme()
    },
    async fetchAll() {
      this.dashboardLoading = true
      this.locationWeatherLoading = true
      this.commonFieldsLoading = true
      this.nearbyFieldsLoading = true
      try {
        const dashboardStats = await this.queryDashboardStats()
        this.applyDashboardStats(dashboardStats)
        await this.ensureWeatherSnapshot()
        await this.fetchCommonFields()
        await this.fetchNearbyFields()
      } catch (e) {
        console.error('首页加载失败', e)
      } finally {
        this.dashboardLoading = false
        this.locationWeatherLoading = false
        this.commonFieldsLoading = false
        this.nearbyFieldsLoading = false
      }
    },
    async ensureWeatherSnapshot(force = false) {
      try {
        this.weatherSnapshot = await getAutoWeatherSnapshot({
          cacheTtlMs: HOME_WEATHER_CACHE_TTL,
          forceRefresh: force
        })
        return true
      } catch (e) {
        console.error('天气定位获取失败', e)
        return false
      }
    },
    async loadWeather() {
      return this.ensureWeatherSnapshot(true)
    },
    async fetchCommonFields() {
      const requestSeq = ++this.commonFieldsRequestSeq
      const loadingStartedAt = Date.now()
      this.commonFieldsLoading = true
      const location = await this.resolveNearbyRequestLocation()
      const latitude = Number(location && location.latitude)
      const longitude = Number(location && location.longitude)
      try {
        const res = await api.get('/miniapp/fields/common', {
          page: 1,
          pageSize: 6,
          latitude: Number.isFinite(latitude) ? latitude : undefined,
          longitude: Number.isFinite(longitude) ? longitude : undefined
        })
        if (requestSeq !== this.commonFieldsRequestSeq) return
        this.commonFieldList = Array.isArray(res && res.records) ? res.records : []
      } catch (e) {
        console.error('load common fields failed', e)
        if (requestSeq !== this.commonFieldsRequestSeq) return
        this.commonFieldList = []
      } finally {
        await this.waitMinimumLoading(loadingStartedAt, HOME_FIELD_SKELETON_MIN_MS)
        if (requestSeq !== this.commonFieldsRequestSeq) return
        this.commonFieldsLoading = false
      }
    },
    async fetchNearbyFields() {
      const requestSeq = ++this.nearbyFieldsRequestSeq
      const loadingStartedAt = Date.now()
      this.nearbyFieldsLoading = true
      const location = await this.resolveNearbyRequestLocation()
      const latitude = Number(location && location.latitude)
      const longitude = Number(location && location.longitude)
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        if (requestSeq !== this.nearbyFieldsRequestSeq) return
        this.nearbyFieldList = []
        await this.waitMinimumLoading(loadingStartedAt, HOME_FIELD_SKELETON_MIN_MS)
        if (requestSeq !== this.nearbyFieldsRequestSeq) return
        this.nearbyFieldsLoading = false
        return
      }
      try {
        const res = await api.get('/miniapp/fields/nearby', {
          page: 1,
          pageSize: 6,
          latitude,
          longitude,
          radiusKm: 20
        })
        const rows = Array.isArray(res && res.records) ? res.records : []
        if (requestSeq !== this.nearbyFieldsRequestSeq) return
        this.nearbyFieldList = rows.map((item) => ({
          ...item,
          distanceText: String(item && item.distanceText ? item.distanceText : '').trim() || this.resolveNearbyDistanceText(item, latitude, longitude)
        }))
      } catch (e) {
        console.error('加载附近田块失败', e)
        if (requestSeq !== this.nearbyFieldsRequestSeq) return
        this.nearbyFieldList = []
      } finally {
        await this.waitMinimumLoading(loadingStartedAt, HOME_FIELD_SKELETON_MIN_MS)
        if (requestSeq !== this.nearbyFieldsRequestSeq) return
        this.nearbyFieldsLoading = false
      }
    },
    async resolveNearbyRequestLocation() {
      try {
        const current = await getCurrentLocation()
        if (Number.isFinite(Number(current && current.latitude)) && Number.isFinite(Number(current && current.longitude))) {
          return current
        }
      } catch (e) {}
      const snapshot = this.weatherSnapshot || {}
      return {
        latitude: Number(snapshot.latitude),
        longitude: Number(snapshot.longitude)
      }
    },
    async refreshHomeData(options = {}) {
      const silent = !!(options && options.silent)
      const fromPull = !!(options && options.fromPull)
      const showSuccess = !!(options && options.showSuccess)
      const refreshStats = !!(options && options.refreshStats)
      const refreshCommonFields = !!(options && options.refreshCommonFields)
      if (this.weatherRefreshing) return
      this.weatherRefreshing = true
      if (!silent && refreshStats) {
        this.dashboardLoading = true
      }
      if (!silent) {
        this.locationWeatherLoading = true
      }
      if (!silent && refreshCommonFields) {
        this.commonFieldsLoading = true
      }
      if (!silent) {
        this.nearbyFieldsLoading = true
      }
      const refreshStartedAt = Date.now()
      try {
        const weatherReady = await this.ensureWeatherSnapshot(true)
        const location = await this.resolveNearbyRequestLocation()
        const [dashboardStats, commonFields, nearbyFields] = await Promise.all([
          refreshStats ? this.queryDashboardStats() : Promise.resolve(null),
          refreshCommonFields ? this.queryCommonFields(location) : Promise.resolve(null),
          this.queryNearbyFields(location)
        ])
        if (refreshStats && dashboardStats) {
          this.applyDashboardStats(dashboardStats)
        }
        if (refreshCommonFields && commonFields) {
          this.commonFieldList = commonFields
        }
        this.nearbyFieldList = nearbyFields
        if (showSuccess && weatherReady) {
          uni.showToast({
            title: refreshStats || refreshCommonFields ? '首页信息已刷新' : '已更新位置、天气和附近田块',
            icon: 'none'
          })
        }
        if (!silent && !weatherReady) {
          uni.showToast({ title: '定位或天气更新失败', icon: 'none' })
        }
      } catch (e) {
        if (!silent) {
          uni.showToast({ title: '刷新失败，请稍后重试', icon: 'none' })
        }
      } finally {
        await this.waitMinimumLoading(refreshStartedAt, 420)
        this.weatherRefreshing = false
        if (!silent && refreshStats) {
          this.dashboardLoading = false
        }
        if (!silent) {
          this.locationWeatherLoading = false
        }
        if (!silent && refreshCommonFields) {
          this.commonFieldsLoading = false
        }
        if (!silent) {
          this.nearbyFieldsLoading = false
        }
        if (fromPull) {
          this.pullRefreshing = false
          this.pullDistance = 0
          this.pullReleaseReady = false
        }
      }
    },
    extractTouchY(event) {
      const touches = (event && event.touches) || (event && event.changedTouches) || []
      const first = Array.isArray(touches) && touches.length ? touches[0] : null
      const value = Number(
        (first && (first.clientY || first.pageY))
        || (event && event.detail && event.detail.y)
        || 0
      )
      return Number.isFinite(value) ? value : 0
    },
    isInteractiveTouchTarget(event) {
      const dataset = (event && event.target && event.target.dataset) || {}
      return !!dataset.interactive
    },
    computeDampedPullDistance(rawDistance) {
      const safeRaw = Math.max(0, Number(rawDistance) || 0)
      const maxDistance = HOME_PULL_MAX_DISTANCE
      const dampFactor = HOME_PULL_DAMP_FACTOR
      return maxDistance * (1 - Math.exp(-safeRaw / dampFactor))
    },
    handlePullTouchStart(event) {
      if (this.weatherRefreshing || this.pullRefreshing) return
      this.pullTouchHolding = true
      this.pullGestureBlocked = this.isInteractiveTouchTarget(event)
      if (this.pullGestureBlocked) {
        this.pullGestureActive = false
        return
      }
      this.pullTouchStartY = this.extractTouchY(event)
      this.pullGestureActive = false
    },
    handlePullTouchMove(event) {
      if (this.weatherRefreshing || this.pullRefreshing) return
      if (this.pullGestureBlocked) return
      const currentY = this.extractTouchY(event)
      const deltaY = currentY - Number(this.pullTouchStartY || 0)
      if (this.currentScrollTop <= HEADER_RESET_TOP_PX && this.headerSolid) {
        this.headerSolid = false
        this.syncNavigationBarThemeBySolid(false)
      }
      if (this.currentScrollTop > HOME_PULL_TOP_ACTIVE_PX) {
        this.pullGestureActive = false
        this.pullDistance = 0
        this.pullReleaseReady = false
        return
      }
      if (deltaY <= 0 && !this.pullGestureActive) {
        this.pullDistance = 0
        this.pullReleaseReady = false
        return
      }
      const nextRawDistance = Math.max(0, deltaY)
      this.pullGestureActive = nextRawDistance > 0
      this.pullDistance = this.computeDampedPullDistance(nextRawDistance)
      this.pullReleaseReady = nextRawDistance >= HOME_PULL_TRIGGER_DISTANCE
    },
    async handlePullRefresh() {
      if (this.pullRefreshing || this.weatherRefreshing) {
        this.handlePullRefreshEnd()
        return
      }
      this.pullDistance = 50
      this.pullReleaseReady = true
      this.pullRefreshing = true
      await this.refreshHomeData({
        silent: true,
        fromPull: true,
        refreshStats: true,
        refreshCommonFields: true
      })
    },
    async handlePullTouchEnd() {
      if (this.pullRefreshing) return
      this.pullTouchHolding = false
      if (this.pullGestureBlocked) {
        this.pullGestureBlocked = false
        this.pullTouchStartY = 0
        return
      }
      const shouldRefresh = this.pullReleaseReady && this.pullGestureActive
      this.pullGestureActive = false
      if (shouldRefresh) {
        this.pullDistance = 50
        await this.handlePullRefresh()
        return
      }
      this.handlePullRefreshEnd()
    },
    handlePullTouchCancel() {
      if (this.pullRefreshing) return
      this.pullTouchHolding = false
      this.handlePullRefreshEnd()
      this.pullGestureActive = false
      this.pullGestureBlocked = false
      this.pullTouchStartY = 0
    },
    handlePullRefreshEnd() {
      if (this.pullRefreshing) return
      this.pullRefreshing = false
      this.pullDistance = 0
      this.pullReleaseReady = false
      this.pullTouchStartY = 0
      this.pullGestureActive = false
      this.pullGestureBlocked = false
    },
    handleWeatherRefreshTouchStart() {
      this.pullGestureBlocked = true
    },
    handleScrollToUpper() {
      this.currentScrollTop = 0
      if (this.headerSolid) {
        this.headerSolid = false
        this.syncNavigationBarThemeBySolid(false)
      }
    },
    async handleWeatherRefreshTap(event) {
      if (this.weatherRefreshing || this.pullRefreshing) return
      clearAutoWeatherSnapshotCache()
      await this.refreshHomeData({
        silent: false,
        showSuccess: true,
        refreshStats: false,
        refreshCommonFields: false
      })
    },
    showGuideOnce() {
      if (uni.getStorageSync(GUIDE_STORAGE_KEY)) return
      uni.showModal({
        title: '主页使用引导',
        content: '可在此快速进入农事记录、田块分布与种子质量模块。',
        showCancel: false
      })
      uni.setStorageSync(GUIDE_STORAGE_KEY, '1')
    },
    initScrollScale() {
      const width = Number(getWindowWidth())
      if (Number.isFinite(width) && width > 0) {
        this.rpxToPx = width / 750
      } else {
        this.rpxToPx = 1
      }
    },
    handleScroll(e) {
      const detail = (e && e.detail) || {}
      const scrollTop = detail.scrollTop || 0
      const nextScrollTop = Math.max(0, Number(scrollTop) || 0)
      this.currentScrollTop = nextScrollTop
      if (nextScrollTop <= HEADER_RESET_TOP_PX) {
        if (this.headerSolid) {
          this.headerSolid = false
          this.syncNavigationBarThemeBySolid(false)
        }
        return
      }
      const safeScale = Number(this.rpxToPx) > 0 ? Number(this.rpxToPx) : 1
      const scrollTopRpx = scrollTop / safeScale
      const shouldSolid = this.headerSolid
        ? scrollTopRpx >= HEADER_SOLID_LEAVE_RPX
        : scrollTopRpx >= HEADER_SOLID_ENTER_RPX
      if (shouldSolid !== this.headerSolid) {
        this.headerSolid = shouldSolid
        this.syncNavigationBarThemeBySolid(shouldSolid)
      }
    },
    syncNavigationBarThemeBySolid(isSolid) {
      const nextTheme = isSolid ? 'dark' : 'light'
      if (this.statusBarTheme === nextTheme) return
      this.statusBarTheme = nextTheme
      if (typeof uni.setNavigationBarColor !== 'function') return
      uni.setNavigationBarColor({
        frontColor: nextTheme === 'dark' ? '#000000' : '#ffffff',
        backgroundColor: nextTheme === 'dark' ? '#ffffff' : '#2F7D45',
        animation: {
          duration: 0,
          timingFunc: 'linear'
        }
      })
    },
    restoreDefaultNavigationBarTheme() {
      this.statusBarTheme = 'light'
      if (typeof uni.setNavigationBarColor !== 'function') return
      uni.setNavigationBarColor({
        frontColor: '#ffffff',
        backgroundColor: '#2F7D45',
        animation: {
          duration: 0,
          timingFunc: 'linear'
        }
      })
    },
    async openPage(url) {
      const targetUrl = String(url || '').trim()
      if (!targetUrl) return
      uni.navigateTo({
        url: targetUrl
      })
    },
    openFieldDetail(fieldId) {
      const id = String(fieldId == null ? '' : fieldId).trim()
      if (!id) return
      this.openPage(`/pages/field/detail?id=${encodeURIComponent(id)}`)
    },
    openHomeTabRecordList(mineOnly = false) {
      this.openPage(mineOnly ? '/pages/home/record-list?mine=1' : '/pages/home/record-list')
    },
    openHomeTabTarget(url) {
      this.openPage(url)
    },
    navigateToFarmRecord() {
      this.openHomeTabRecordList(false)
    },
    navigateToSeedList() {
      this.openHomeTabTarget('/pages/seed/index')
    },
    navigateToFieldList() {
      this.openHomeTabTarget('/pages/field/index')
    },
    navigateToAllRecords() {
      this.openHomeTabRecordList(true)
    },
    searchField() {
      this.openHomeTabTarget('/pages/field/index')
    },
    formatCropVarietyPair(row) {
      return formatCropVarietyPairText(row, '未设置作物品种')
    },
    isCurrentMatched(field) {
      return Number(field && field.currentMatched) === 1 || (field && field.currentMatched === true)
    },
    fieldRelationText(field) {
      if (this.isCurrentMatched(field)) {
        return '您正处于该田块中'
      }
      return ''
    },
    resolvedFieldDistanceMeters(field) {
      const backendMeters = Number(field && field.distanceMeters)
      if (Number.isFinite(backendMeters) && backendMeters >= 0) {
        return backendMeters
      }
      return NaN
    },
    fieldDistanceText(field) {
      const distance = String((field && field.distanceText) || '').trim()
      if (distance === '位置异常' || distance === '未知') {
        return distance
      }
      if (!distance || this.isCurrentMatched(field)) {
        return ''
      }
      return `距此 ${distance}`
    },
    isLocationAbnormal(field) {
      return String((field && field.distanceText) || '').trim() === '位置异常'
    },
    isLocationUnknown(field) {
      return String((field && field.distanceText) || '').trim() === '未知'
    },
    isWarningDistance(field) {
      if (this.isLocationAbnormal(field) || this.isLocationUnknown(field)) {
        return false
      }
      const meters = this.resolvedFieldDistanceMeters(field)
      return Number.isFinite(meters) && meters <= 3000
    },
    fieldLocationMetaText(field) {
      return this.fieldRelationText(field) || this.fieldDistanceText(field)
    },
    resolveNearbyDistanceText(field, latitude, longitude) {
      const lat = Number(field && field.locationLat)
      const lng = Number(field && field.locationLng)
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
        return ''
      }
      const distanceMeters = Math.round(this.haversineDistanceMeters(latitude, longitude, lat, lng))
      return formatDistance(distanceMeters)
    },
    haversineDistanceMeters(lat1, lng1, lat2, lng2) {
      const earthRadius = 6371000
      const deltaLat = ((lat2 - lat1) * Math.PI) / 180
      const deltaLng = ((lng2 - lng1) * Math.PI) / 180
      const a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
        + Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180)
        * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
      return earthRadius * c
    },
    handleFieldClick(field) {
      writeFieldDetailSnapshot(field)
      this.openFieldDetail(field && field.id)
    },
    resolveCoverPreviewImage(item) {
      if (!item) return ''
      const candidate = [
        item.coverThumbUrl,
        item.thumbUrl,
        item.thumbnailUrl,
        item.previewUrl,
        item.coverImageUrl
      ].find((value) => String(value || '').trim())
      return String(candidate || '').trim()
    }
  },
}
</script>

<style lang="scss">
@import '../static/theme.scss';

.farm-container {
  height: 100%;
  min-height: 0;
  background-color: #f2f6ed;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}

.page-scroll-body {
  position: relative;
}

.pull-refresh-indicator {
  position: fixed;
  top: calc(var(--status-bar-height) + 10rpx);
  left: 50%;
  z-index: 1205;
  pointer-events: none;
  opacity: 0;
  transform: translate3d(-50%, -34px, 0) scale(0.94);
  transition: opacity 120ms linear, transform 180ms cubic-bezier(0.2, 0.78, 0.2, 1);
}

.pull-refresh-indicator-track {
  min-width: 212rpx;
  height: 62rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.86);
  border: 1rpx solid rgba(103, 140, 87, 0.18);
  box-shadow: 0 10rpx 26rpx rgba(58, 91, 56, 0.08);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  backdrop-filter: blur(16rpx);
}

.pull-refresh-indicator-dots {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}

.pull-refresh-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: rgba(47, 125, 69, 0.32);
  transform: scale(0.9);
}

.pull-refresh-label {
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: #5d6c58;
}

.pull-refresh-indicator.is-visible .pull-refresh-dot:nth-child(1) {
  animation: home-pull-dot 1.05s ease-in-out infinite;
}

.pull-refresh-indicator.is-visible .pull-refresh-dot:nth-child(2) {
  animation: home-pull-dot 1.05s ease-in-out 0.12s infinite;
}

.pull-refresh-indicator.is-visible .pull-refresh-dot:nth-child(3) {
  animation: home-pull-dot 1.05s ease-in-out 0.24s infinite;
}

.pull-refresh-indicator.is-armed .pull-refresh-indicator-track {
  background: rgba(245, 252, 246, 0.95);
  border-color: rgba(47, 125, 69, 0.22);
}

.pull-refresh-indicator.is-dragging {
  transition: none;
}

.pull-refresh-indicator.is-armed .pull-refresh-label,
.pull-refresh-indicator.is-refreshing .pull-refresh-label {
  color: #2f7d45;
}

.fixed-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: calc(var(--status-bar-height) + 76rpx);
  padding-top: calc(var(--status-bar-height) + 6rpx);
  background: rgba(255, 255, 255, 0);
  box-shadow: none;
  transition: background-color 180ms ease, box-shadow 180ms ease;
  pointer-events: none;

  .fixed-header-content {
    pointer-events: auto;
    transition: opacity 0.3s ease, color 0.3s ease;
    height: 70rpx;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 20rpx;

    .fixed-header-title {
      font-size: 30rpx;
      font-weight: bold;
      color: #24311d;
      text-align: center;
      opacity: 0;
      transition: opacity 180ms ease;
    }
  }
}

.fixed-header.is-solid {
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.05);

  .fixed-header-content .fixed-header-title {
    opacity: 1;
  }
}

.page-scroll {
  flex: 1;
  min-height: 0;
  width: 100%;

  &::-webkit-scrollbar {
    width: 0 !important;
    height: 0 !important;
  }
}

.page-scroll-body {
  min-height: 100%;
  position: relative;
  box-sizing: border-box;
  padding-bottom: 40rpx;
}

.gradient-bg {
  width: 100%;
  height: 1100rpx;
  position: absolute;
  top: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(to bottom, #79ab4c, #f2f6ed);
}

.gradient-bg-2 {
  width: 100%;
  height: 700rpx;
  position: absolute;
  top: 0;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(40deg, rgba(143, 189, 98, 0), 80%, #93bb63);
}

.sun {
  position: absolute;
  width: 170rpx;
  height: 170rpx;
  top: 90rpx;
  right: 120rpx;
  z-index: 50;
  pointer-events: none;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.4));
}

.cloud {
  position: absolute;
  width: 450rpx;
  height: 260rpx;
  top: 160rpx;
  right: -135rpx;
  z-index: 50;
  pointer-events: none;
  opacity: 0.45;
  border-radius: 130rpx;
  background: rgba(255, 255, 255, 0.7);
}

.content {
  position: relative;
  z-index: 60;
  width: 100%;
  box-sizing: border-box;
  padding-bottom: 0;
}

.header {
  padding: calc(var(--status-bar-height) + 34rpx) 30rpx 22rpx;
  color: #fff;

  .greeting {
    font-size: 40rpx;

    .time-greeting {
      font-size: 48rpx;
    }

    .company-name {
      margin-left: 40rpx;
    }
  }

  .logining-greeting {
    margin-top: 15rpx;
    margin-left: 5rpx;
    font-size: 34rpx;
  }

  .weather-card {
    width: 90%;
    margin: 30rpx auto 0;
    border-radius: 20rpx;
    padding: 20rpx 30rpx;
    min-height: 202rpx;
    background: transparent;
    box-shadow: none;
    backdrop-filter: none;

    .weather-data {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 88rpx;
      background: transparent;

      .weather-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        background: transparent;

        .weather-text {
          font-size: 24rpx;
        }

        .weather-value {
          margin-top: 8rpx;
          font-size: 34rpx;
          font-weight: 700;
        }
      }
    }

    .weather-data-skeleton,
    .location-weather-skeleton {
      border-radius: 16rpx;
      overflow: hidden;
      background: transparent;
      box-sizing: border-box;
    }

    .weather-data-skeleton :deep(.t-skeleton),
    .location-weather-skeleton :deep(.t-skeleton) {
      width: 100%;
    }

    .location-weather-skeleton {
      margin-top: 16rpx;
      min-height: 52rpx;
    }

    .location-weather {
      margin-top: 16rpx;
      border-top: 1rpx solid rgba(255, 255, 255, 0.16);
      padding-top: 12rpx;
      min-height: 52rpx;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20rpx;
      background: transparent;

      .location-weather-main {
        flex: 1;
        min-width: 0;
      }

      .lw-line {
        display: block;
        font-size: 24rpx;
        line-height: 1.6;
      }

      .weather-refresh-btn {
        width: 56rpx;
        height: 56rpx;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: #ffffff;
        opacity: 0.92;
        flex-shrink: 0;
        border-radius: 50%;
        background: transparent;
        padding: 0;
        margin: 0;
        border: none;
        outline: none;
      }

      .weather-refresh-icon {
        pointer-events: none;
      }

      .weather-refresh-btn::after {
        border: none;
      }

      .weather-refresh-btn-hover {
        opacity: 1;
        background: transparent;
      }

      .weather-refresh-btn.is-refreshing {
        animation: home-refresh-spin 0.9s linear infinite;
      }
    }
  }
}

.function-menu {
  display: flex;
  justify-content: space-around;
  padding: 40rpx 20rpx;
  background-color: rgba(255, 255, 255, 0.8);
  margin: 0 30rpx;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

  .function-item {
    display: flex;
    flex-direction: column;
    align-items: center;

    .function-icon {
      width: 100rpx;
      height: 100rpx;
      background-color: #6ea046;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16rpx;
      font-size: 36rpx;
      color: #fff;
      font-weight: 700;
    }

    .function-name {
      font-size: 30rpx;
      font-weight: bold;
      color: #333;
    }
  }
}

.plan-card {
  margin: 20rpx 30rpx;
  background-color: #fff;
  border-radius: 20rpx;
  padding: 30rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);

  .plan-title {
    display: flex;
    align-items: center;

    .title-icon {
      font-size: 40rpx;
      margin-right: 16rpx;
    }

    .plan-title-text {
      font-size: 32rpx;
      font-weight: bold;
    }

    .plan-count {
      margin-left: auto;
      color: #6ea046;
      font-size: 32rpx;
      font-weight: normal;
    }
  }

  .germination-summary {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 16rpx;
    color: #666;
    font-size: 28rpx;

    .arrow-right {
      font-size: 44rpx;
      color: #6ea046;
    }
  }
}

.field-section {
  margin: 30rpx;
  padding-bottom: 20rpx;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .section-title {
      font-size: 36rpx;
      font-weight: bold;
      color: #333;
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
        background-color: #6ea046;
        border-radius: 4rpx;
      }
    }

    .search-button {
      display: flex;
      align-items: center;
      background-color: #6ea046;
      color: white;
      padding: 10rpx 20rpx;
      border-radius: 16rpx;

      .search-text {
        font-size: 28rpx;
      }
    }

  }

  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 30rpx;

    .field-item {
      background-color: white;
      border-radius: 20rpx;
      overflow: hidden;
      box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.08);
      border: 1rpx solid rgba(82, 112, 63, 0.08);
      min-height: 364rpx;

      &.is-current-field {
        border-color: rgba(98, 140, 58, 0.3);
        box-shadow: 0 8rpx 20rpx rgba(96, 136, 62, 0.16);
      }

      .field-image {
        width: 100%;
        height: 180rpx;
        display: block;
        background-color: #f2f6ed;
      }

      .field-info {
        padding: 20rpx;
        min-height: 164rpx;
        display: flex;
        flex-direction: column;

        .field-name {
          font-size: 32rpx;
          font-weight: bold;
          display: block;
          margin-bottom: 8rpx;
          color: #303133;
        }

        .field-variety {
          font-size: 26rpx;
          color: #999;
        }

        .field-location {
          margin-top: auto;
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          gap: 8rpx;
        }

        .field-relation {
          display: inline-flex;
          align-items: center;
          color: #5f9138;
          font-size: 24rpx;
          font-weight: 700;
        }

        .field-distance {
          display: block;
          font-size: 24rpx;
          color: #8a9384;
          font-weight: 700;
          text-align: right;
        }

        .field-location.error .field-distance {
          color: #d64646;
        }

        .field-location.warning .field-distance {
          color: #d28a20;
        }
      }
    }

    .field-item.field-item-skeleton {
      box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
      border: 1rpx solid rgba(82, 112, 63, 0.08);
    }

    .field-image.field-image-skeleton {
      height: 180rpx;
      overflow: hidden;
    }

    .field-info.field-info-skeleton {
      min-height: 164rpx;
      padding: 20rpx;
      box-sizing: border-box;
    }

    .field-info.field-info-skeleton :deep(.t-skeleton) {
      width: 100%;
    }
  }
}

.field-fallback-card {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 20rpx;
  padding: 26rpx 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}

.nearby-empty-card {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 20rpx;
  padding: 24rpx 22rpx;
  border: 1rpx solid #e5eddc;
}

.nearby-empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #31402a;
}

.nearby-empty-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6f7f68;
}

.field-fallback-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #2d3b26;
}

.field-fallback-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #6f7f68;
}

.field-fallback-actions {
  margin-top: 18rpx;
  display: flex;
  gap: 12rpx;
}

.field-fallback-btn {
  flex: 1;
  min-height: 72rpx;
  border-radius: 16rpx;
  border: 1rpx solid #d8e4cf;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f7f4d;
  font-size: 26rpx;
  font-weight: 700;
}

.field-fallback-btn.primary {
  background: #6ea046;
  border-color: #6ea046;
  color: #ffffff;
}

.tabbar-spacer {
  height: 0;
}

::-webkit-scrollbar {
  width: 0 !important;
  height: 0 !important;
}

.elder-mode {
  .fixed-header .fixed-header-content .fixed-header-title {
    font-size: 42rpx;
  }

  .header .greeting .time-greeting {
    font-size: 56rpx;
  }

  .header .greeting .company-name,
  .header .logining-greeting {
    font-size: 40rpx;
  }

  .header .weather-card .weather-data .weather-item .weather-text,
  .header .weather-card .location-weather .lw-line,
  .pull-refresh-label,
  .field-fallback-desc,
  .nearby-empty-desc {
    font-size: 30rpx;
  }

  .header .weather-card .weather-data .weather-item text,
  .function-menu .function-item .function-name,
  .plan-card .plan-title text,
  .plan-card .plan-title .plan-count,
  .field-section .section-header .section-title,
  .field-section .field-grid .field-item .field-info .field-name,
  .field-section .field-grid .field-item .field-info .field-relation,
  .field-section .field-grid .field-item .field-info .field-distance,
  .field-fallback-title,
  .nearby-empty-title {
    font-size: 36rpx;
  }

  .field-section .field-grid .field-item .field-info .field-variety,
  .field-fallback-btn {
    font-size: 30rpx;
  }
}
</style>
@keyframes home-pull-dot {
  0%, 80%, 100% {
    transform: scale(0.88);
    opacity: 0.35;
  }

  40% {
    transform: scale(1.12);
    opacity: 1;
  }
}

@keyframes home-refresh-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
