<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="田块详情"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="hero-card hero-card-skeleton" v-if="detailLoading">
        <view class="hero-media-skeleton">
          <t-skeleton theme="image" animation="flashed" />
        </view>
        <view class="hero-head-skeleton">
          <t-skeleton theme="paragraph" animation="flashed" :row-col="heroTitleSkeletonRows" />
        </view>
        <view class="hero-grid hero-grid-skeleton">
          <view v-for="idx in 5" :key="`hero-grid-skeleton-${idx}`" class="hero-item hero-item-skeleton">
            <t-skeleton theme="paragraph" animation="flashed" :row-col="heroGridSkeletonRows" />
          </view>
        </view>
      </view>

      <view class="hero-card" v-else-if="field">
        <view class="hero-media" @tap="previewFieldImages">
          <view class="cover-banner">
            <dh-smart-image
              class="cover-banner-image"
              :src="resolveCoverImage(field)"
              :preview-src="resolveCoverPreviewImage(field)"
              mode="aspectFill"
              :lazy-load="true"
              loading-text="封面加载中..."
              empty-text="暂无封面"
              error-text="封面异常"
            />
          </view>
          <view class="hero-media-badge" v-if="fieldPreviewCount">
            <text class="hero-media-badge-text">大图</text>
            <text class="hero-media-badge-text" v-if="fieldPreviewCount > 1">共{{ fieldPreviewCount }}张</text>
          </view>
        </view>
        <view class="hero-head">
          <view class="hero-main">
            <text class="name">{{ field.name }}</text>
            <text class="hero-sub">{{ formatAddress(field) }}</text>
            <text v-if="fieldRelationText" class="hero-relation">{{ fieldRelationText }}</text>
          </view>
          <text class="status" :class="`s-${field.stage || field.status || 'idle'}`">
            {{ formatStage(field.stage || field.status) }}
          </text>
        </view>
        <view class="hero-grid">
          <view class="hero-item">
            <text class="hero-label">面积</text>
            <text class="hero-value">{{ field.areaMu || '-' }} 亩</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">作物品种</text>
            <text class="hero-value">{{ formatCropVarietyPair(field) }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">省市区</text>
            <text class="hero-value">{{ formatRegion(field) || '未填写' }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">乡镇</text>
            <text class="hero-value">{{ field.township || '未填写' }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">距此</text>
            <text
              class="hero-value"
              :class="{
                'is-location-error': fieldDistanceText === '位置异常',
                'is-location-unknown': fieldDistanceText === '未知'
              }"
            >{{ fieldDistanceText || '定位后显示' }}</text>
          </view>
        </view>
      </view>

      <view v-if="segmentOptions.length > 1" class="section-head">
        <text class="section-title">作物分段</text>
        <text class="section-sub">{{ selectedSegmentName }}</text>
      </view>
      <scroll-view scroll-x class="segment-scroll" :show-scrollbar="false" v-if="segmentOptions.length > 1">
        <view class="segment-row">
          <view
            v-for="item in segmentOptions"
            :key="item.segmentKey"
            class="segment-chip"
            :class="{ active: selectedSegmentKey === item.segmentKey }"
            @click="onSegmentSelect(item)"
          >
            <text>{{ formatSegmentName(item.segmentName) }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="section-head">
        <text class="section-title">流程进度</text>
        <text class="section-sub" v-if="processLoading">加载中...</text>
        <text class="section-sub" v-else-if="orderedSteps.length">{{ doneStepCount }} / {{ orderedSteps.length }} 已记录</text>
      </view>

      <view v-if="processLoading" class="flow-track flow-track-skeleton">
        <view class="flow-skeleton-card">
          <t-skeleton theme="paragraph" animation="flashed" :row-col="flowSkeletonRows" />
        </view>
      </view>
      <view class="flow-track" v-else-if="orderedSteps.length">
        <view
          v-for="(step, idx) in orderedSteps"
          :key="step.id"
          class="flow-item"
          @click="goAddStepRecord(step)"
        >
          <view class="flow-left">
            <view class="dot" :class="stepStatus(step)">{{ stepOrderMap[step.id] || idx + 1 }}</view>
            <view v-if="idx < orderedSteps.length - 1" class="connector" :class="{ done: isDone(step) }"></view>
          </view>

          <view class="flow-main" :class="stepStatus(step)">
            <view class="flow-top">
              <text class="flow-name">{{ stepTitle(step) }}</text>
              <text class="flow-count">{{ step.doneCount || 0 }} 条记录</text>
            </view>
            <view class="flow-meta">
              <text>最近作业：{{ step.lastWorkDate ? formatDate(step.lastWorkDate) : '-' }}</text>
            </view>
            <view class="flow-tip" v-if="step.requirementDesc">{{ step.requirementDesc }}</view>
          </view>
        </view>
      </view>
      <view v-else class="state-card empty-card">当前没有进行中的种植计划</view>

      <view class="section-head mt" v-if="selectedCycleId || timelineLoading">
        <text class="section-title">农事时间轴</text>
        <text class="section-more" v-if="!timelineLoading && selectedCycleId" @click="goRecordList">查看全部</text>
      </view>

      <view v-if="timelineLoading" class="timeline timeline-skeleton">
        <view v-for="idx in 3" :key="`timeline-skeleton-${idx}`" class="timeline-skeleton-item">
          <view class="timeline-skeleton-time">
            <t-skeleton theme="paragraph" animation="flashed" :row-col="timelineTimeSkeletonRows" />
          </view>
          <view class="timeline-skeleton-node-col">
            <view class="timeline-skeleton-node"></view>
          </view>
          <view class="timeline-skeleton-card">
            <t-skeleton theme="paragraph" animation="flashed" :row-col="timelineSkeletonRows" />
          </view>
        </view>
      </view>
      <view v-else-if="recordTimeline.length" class="timeline">
        <view class="timeline-day" v-for="group in recordTimeline" :key="group.date">
          <view class="day-tag">{{ group.dateLabel }}</view>
          <view class="timeline-item" v-for="item in group.items" :key="item.id" @click="goRecordDetail(item.id)">
            <view class="time-col">
              <text class="time">{{ formatTime(item.workDate) }}</text>
            </view>
            <view class="node-col">
              <view class="node"></view>
            </view>
            <view class="content-col">
              <view class="r1">
                <text class="type">{{ recordStepTitle(item) }}</text>
                <text class="step">{{ item.weather || '未记天气' }}</text>
              </view>
              <view class="r2 r2-summary" v-if="item.dynamicSummary">{{ item.dynamicSummary }}</view>
              <view class="r3" v-if="item.notes">{{ item.notes }}</view>
              <view class="r4 r4-operator">
                <text>{{ recordOperatorText(item) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view v-else-if="selectedCycleId" class="timeline-empty-card">
        <view class="timeline-empty-icon">
          <view class="timeline-empty-dot"></view>
        </view>
        <text class="timeline-empty-title">暂无农事记录</text>
        <text class="timeline-empty-desc">{{ timelineEmptyDesc }}</text>
        <t-button theme="primary" variant="outline" size="small" @click="goAddFirstRecord">新增农事</t-button>
      </view>
      <view v-if="selectedCycleId" class="timeline-tail-space"></view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isElderMode } from '../../utils/accessibility'
import { formatCropVarietyPair as formatCropVarietyPairText } from '../../utils/crop-variety'
import { hasDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'
import { getCurrentLocation } from '../../utils/amap'
import { readFieldDetailSnapshot, writeFieldDetailSnapshot } from '../../utils/field-detail-snapshot'
import DhSmartImage from '../../components/dh-smart-image.vue'

export default {
  components: {
    DhSmartImage
  },
  data() {
    return {
      id: null,
      field: null,
      stepItems: [],
      selectedCycleId: null,
      segmentOptions: [],
      selectedSegmentKey: '',
      recentRecords: [],
      fieldDistanceText: '',
      elderMode: false,
      refreshMark: '',
      recentRequestSeq: 0,
      detailRequestSeq: 0,
      pageLoading: false,
      pageReadyForRefresh: false,
      detailLoading: false,
      processLoading: true,
      timelineLoading: false,
      heroTitleSkeletonRows: [
        { width: '34%', height: '38rpx', borderRadius: '14rpx' },
        { width: '72%', height: '24rpx', margin: '14rpx 0 0 0', borderRadius: '10rpx' },
        { width: '46%', height: '24rpx', margin: '14rpx 0 0 0', borderRadius: '10rpx' }
      ],
      heroGridSkeletonRows: [
        { width: '38%', height: '22rpx', borderRadius: '10rpx' },
        { width: '68%', height: '26rpx', margin: '12rpx 0 0 0', borderRadius: '12rpx' }
      ],
      flowSkeletonRows: [
        { width: '44%', height: '24rpx', borderRadius: '12rpx' },
        { width: '22%', height: '20rpx', margin: '8rpx 0 0 0', borderRadius: '10rpx' },
        { width: '58%', height: '20rpx', margin: '10rpx 0 0 0', borderRadius: '10rpx' }
      ],
      timelineTimeSkeletonRows: [
        { width: '100%', height: '22rpx', borderRadius: '10rpx' }
      ],
      timelineSkeletonRows: [
        { width: '40%', height: '26rpx', borderRadius: '12rpx' },
        { width: '28%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
        { width: '78%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
        { width: '34%', height: '22rpx', margin: '12rpx 0 0 auto', borderRadius: '10rpx' }
      ]
    }
  },
  computed: {
    fieldRelationText() {
      if (!this.field) return ''
      const relationText = String(this.field.relationText || '').trim()
      if (Number(this.field.currentMatched) === 1 || this.field.currentMatched === true) {
        return relationText || '您正处于该田块中'
      }
      return ''
    },
    selectedSegment() {
      if (!this.segmentOptions.length) return null
      return this.segmentOptions.find((x) => x.segmentKey === this.selectedSegmentKey) || this.segmentOptions[0]
    },
    selectedSegmentName() {
      const row = this.selectedSegment
      return row ? this.formatSegmentName(row.segmentName) : '全部步骤'
    },
    selectedSegmentStepIds() {
      if (!this.selectedSegment || !Array.isArray(this.selectedSegment.steps)) {
        return []
      }
      const ids = this.selectedSegment.steps
        .map((item) => this.normalizeId(item && item.id))
        .filter(Boolean)
      return Array.from(new Set(ids))
    },
    selectedSegmentStepIdsCsv() {
      return this.selectedSegmentStepIds.join(',')
    },
    orderedSteps() {
      const rows = this.selectedSegment && Array.isArray(this.selectedSegment.steps)
        ? this.selectedSegment.steps
        : this.stepItems
      return [...rows].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    },
    stepOrderMap() {
      const map = {}
      this.orderedSteps.forEach((x, idx) => {
        map[this.normalizeId(x && x.id)] = idx + 1
      })
      return map
    },
    doneStepCount() {
      return this.orderedSteps.filter((x) => (x.doneCount || 0) > 0).length
    },
    stepNameMap() {
      const map = {}
      this.orderedSteps.forEach((x) => {
        map[this.normalizeId(x && x.id)] = x.stepName || `步骤${x.sortOrder || ''}`
      })
      return map
    },
    stepSchemaMap() {
      const map = {}
      this.orderedSteps.forEach((x) => {
        const key = this.normalizeId(x && x.id)
        if (!key) return
        map[key] = x && x.formSchema ? x.formSchema : ''
      })
      return map
    },
    fieldPreviewCount() {
      return this.resolveFieldPreviewUrls(this.field).length
    },
    recordTimeline() {
      const map = {}
      this.recentRecords.forEach((item) => {
        const dateKey = String(item.workDate || '').slice(0, 10) || '未知日期'
        if (!map[dateKey]) {
          map[dateKey] = {
            date: dateKey,
            dateLabel: this.formatDateLabel(dateKey),
            items: []
          }
        }
        map[dateKey].items.push({
          ...item,
          dynamicSummary: this.recordDynamicSummary(item)
        })
      })
      const groups = Object.values(map)
      groups.sort((a, b) => (a.date < b.date ? 1 : -1))
      groups.forEach((g) => {
        g.items.sort((a, b) => (String(a.workDate || '') < String(b.workDate || '') ? 1 : -1))
      })
      return groups
    },
    timelineEmptyDesc() {
      if (!this.selectedSegment || this.segmentOptions.length <= 1) {
        return '当前计划下还没有记录，完成一次农事作业后会在这里按时间展示。'
      }
      return `${this.selectedSegmentName}下还没有农事记录，完成一次相关作业后会在这里按时间展示。`
    }
  },
  async onLoad(query) {
    this.id = this.normalizeId(query && query.id)
    this.elderMode = isElderMode()
    this.refreshMark = readRefreshMark(refreshTopics.fieldDetail(this.id))
    this.pageReadyForRefresh = false
    this.hydrateFieldSnapshot()
    await this.reloadPageData()
    this.refreshMark = readRefreshMark(refreshTopics.fieldDetail(this.id))
    this.pageReadyForRefresh = true
  },
  onShow() {
    this.elderMode = isElderMode()
    this.refreshPageDataIfNeeded()
  },
  methods: {
    hydrateFieldSnapshot() {
      if (!this.id) return
      const snapshot = readFieldDetailSnapshot(this.id)
      if (!snapshot) return
      this.field = snapshot
      this.fieldDistanceText = String(snapshot.distanceText || '').trim()
    },
    async reloadPageData(options = {}) {
      if (!this.id || this.pageLoading) return
      const silent = !!(options && options.silent)
      this.pageLoading = true
      try {
        if (!silent) {
          this.detailLoading = true
          this.processLoading = true
          this.timelineLoading = true
        }
        await this.fetchDetail({ silent })
        await this.fetchProcess({ silent })
        await this.fetchRecent({ silent })
      } finally {
        this.pageLoading = false
      }
    },
    async refreshPageDataIfNeeded() {
      if (!this.id || !this.pageReadyForRefresh || this.pageLoading) {
        return
      }
      const topic = refreshTopics.fieldDetail(this.id)
      if (!hasDataChanged(topic, this.refreshMark)) {
        return
      }
      this.pageReadyForRefresh = false
      try {
        await this.reloadPageData({ silent: true })
        this.refreshMark = readRefreshMark(topic)
      } finally {
        this.pageReadyForRefresh = true
      }
    },
    goBack() {
      uni.navigateBack()
    },
    async fetchDetail(options = {}) {
      if (!this.id) return
      const silent = !!(options && options.silent)
      const requestSeq = ++this.detailRequestSeq
      const currentField = this.field && typeof this.field === 'object' ? this.field : {}
      if (!silent) {
        this.detailLoading = true
      }
      try {
        const params = await this.buildDetailLocationParams()
        const response = await api.get('/miniapp/fields/' + this.id, params)
        if (requestSeq !== this.detailRequestSeq) {
          return
        }
        const nextField = response && typeof response === 'object' ? response : {}
        const mergedField = {
          ...currentField,
          ...nextField
        }
        const mediaFields = ['coverImageUrl', 'imageUrl', 'coverUrl', 'cover', 'photoUrl', 'banner', 'picture', 'thumbUrl', 'fileUrl']
        mediaFields.forEach((fieldKey) => {
          const nextValue = String(mergedField[fieldKey] || '').trim()
          const currentValue = String(currentField[fieldKey] || '').trim()
          if (!nextValue && currentValue) {
            mergedField[fieldKey] = currentField[fieldKey]
          }
        })
        const mediaArrayFields = ['images', 'imageList', 'photos']
        mediaArrayFields.forEach((fieldKey) => {
          const nextValue = mergedField[fieldKey]
          const hasArrayValue = Array.isArray(nextValue) ? nextValue.length > 0 : String(nextValue || '').trim().length > 0
          if (!hasArrayValue && currentField[fieldKey]) {
            mergedField[fieldKey] = currentField[fieldKey]
          }
        })
        this.field = mergedField
        this.fieldDistanceText = String((this.field && this.field.distanceText) || '').trim()
        writeFieldDetailSnapshot(this.field)
      } catch (e) {
        console.error('load field detail failed', e)
      } finally {
        if (!silent && requestSeq === this.detailRequestSeq) {
          this.detailLoading = false
        }
      }
    },
    async fetchProcess(options = {}) {
      if (!this.id) return
      const silent = !!(options && options.silent)
      const previousSegmentKey = this.selectedSegmentKey
      if (!silent) {
        this.processLoading = true
      }
      try {
        const data = await api.get(`/miniapp/fields/${this.id}/process`, {})
        const rawCycles = (data && data.cycles) || []
        const activeCycles = rawCycles.filter((x) => String((x && x.status) || '').toLowerCase() === 'active')
        if (!activeCycles.length) {
          this.selectedCycleId = null
          this.stepItems = []
          this.segmentOptions = []
          this.selectedSegmentKey = ''
          this.timelineLoading = false
          return
        }
        let currentCycleId = this.normalizeId(data && data.selectedCycleId)
        if (!activeCycles.some((x) => this.sameId(x && x.id, currentCycleId))) {
          const current = activeCycles.find((x) => Number(x.isCurrent) === 1)
          currentCycleId = this.normalizeId(current ? current.id : activeCycles[0].id)
        }
        this.selectedCycleId = currentCycleId
        this.stepItems = (data && data.steps) || []
        this.segmentOptions = (data && data.segments) || []
        if (!this.segmentOptions.length) {
          this.selectedSegmentKey = ''
        } else {
          const keepPrevious = previousSegmentKey && this.segmentOptions.some((x) => x.segmentKey === previousSegmentKey)
          const candidate = keepPrevious ? previousSegmentKey : (data.selectedSegmentKey || this.segmentOptions[0].segmentKey)
          const matched = this.segmentOptions.find((x) => x.segmentKey === candidate)
          this.selectedSegmentKey = matched ? matched.segmentKey : this.segmentOptions[0].segmentKey
        }
      } catch (e) {
        console.error('加载流程失败', e)
        if (!silent) {
          this.timelineLoading = false
        }
      } finally {
        if (!silent) {
          this.processLoading = false
        }
      }
    },
    async fetchRecent(options = {}) {
      const silent = !!(options && options.silent)
      if (!this.id || !this.selectedCycleId) {
        this.recentRecords = []
        if (!silent) {
          this.timelineLoading = false
        }
        return
      }
      const requestSeq = ++this.recentRequestSeq
      if (!silent) {
        this.timelineLoading = true
      }
      try {
        const params = {
          limit: 30,
          cycleId: this.selectedCycleId
        }
        if (this.selectedSegmentStepIdsCsv) {
          params.stepIds = this.selectedSegmentStepIdsCsv
        }
        const rows = (await api.get(`/miniapp/fields/${this.id}/farm-records/recent`, params)) || []
        if (requestSeq !== this.recentRequestSeq) {
          return
        }
        this.recentRecords = rows
      } catch (e) {
        console.error('加载最近农事失败', e)
        if (requestSeq !== this.recentRequestSeq) {
          return
        }
        this.recentRecords = []
      } finally {
        if (!silent && requestSeq === this.recentRequestSeq) {
          this.timelineLoading = false
        }
      }
    },
    async buildDetailLocationParams() {
      try {
        const current = await getCurrentLocation()
        const currentLat = Number(current && current.latitude)
        const currentLng = Number(current && current.longitude)
        if (!Number.isFinite(currentLat) || !Number.isFinite(currentLng)) {
          return {}
        }
        return {
          latitude: currentLat,
          longitude: currentLng
        }
      } catch (e) {
        return {}
      }
    },
    normalizeId(value) {
      const text = String(value == null ? '' : value).trim()
      return text || ''
    },
    sameId(left, right) {
      return this.normalizeId(left) === this.normalizeId(right)
    },
    async onSegmentSelect(item) {
      if (!item || !item.segmentKey) return
      if (item.segmentKey === this.selectedSegmentKey) return
      this.selectedSegmentKey = item.segmentKey
      await this.fetchRecent()
    },
    stepTitle(step) {
      if (!step) return '-'
      return step.stepName || '-'
    },
    stepStatus(step) {
      if (!step) return 'wait'
      if ((step.doneCount || 0) > 0) return 'done'
      return 'wait'
    },
    isDone(step) {
      return this.stepStatus(step) === 'done'
    },
    goRecordList() {
      if (!this.id || !this.selectedCycleId) return
      uni.navigateTo({ url: `/pages/home/record-list?fieldId=${this.id}&cycleId=${this.selectedCycleId}` })
    },
    goRecordDetail(id) {
      uni.navigateTo({ url: `/pages/home/record-detail?id=${id}` })
    },
    previewFieldImages() {
      const urls = this.resolveFieldPreviewUrls(this.field)
      if (!urls.length) return
      uni.previewImage({
        urls,
        current: urls[0]
      })
    },
    goAddFirstRecord() {
      if (!this.id) return
      const cycleQuery = this.selectedCycleId ? `&cycleId=${this.selectedCycleId}` : ''
      uni.navigateTo({ url: `/pages/home/record-edit?fieldId=${this.id}${cycleQuery}` })
    },
    goAddStepRecord(step) {
      if (!this.id || !step || !step.id || !this.selectedCycleId) return
      uni.navigateTo({ url: `/pages/home/record-edit?fieldId=${this.id}&stepId=${step.id}&cycleId=${this.selectedCycleId}` })
    },
    formatDate(v) {
      return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
    },
    formatTime(v) {
      const s = v ? String(v).replace('T', ' ') : ''
      return s.length >= 16 ? s.slice(11, 16) : '--:--'
    },
    formatDateLabel(v) {
      if (!v || v.length < 10) return v || '未知日期'
      return `${v.slice(5, 7)}月${v.slice(8, 10)}日`
    },
    recordStepTitle(item) {
      if (!item) return '农事记录'
      const directName = String(item.stepName || '').trim()
      if (directName) return directName
      const byMap = this.stepNameMap[this.normalizeId(item.stepId)]
      if (byMap) return byMap
      const stepId = item.stepId != null ? String(item.stepId).trim() : ''
      if (stepId) return `步骤#${stepId}`
      return '农事记录'
    },
    recordOperatorText(item) {
      const operatorName = String((item && item.operatorName) || '').trim()
      return `记录人:${operatorName || '未填写'}`
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
    formatSegmentName(name) {
      const text = String(name || '').trim()
      if (!text) return '全部步骤'
      return text.replace(/[·•]/g, ' · ')
    },
    formatAddress(row) {
      const src = row || {}
      const region = [
        String(src.province || '').trim(),
        String(src.city || '').trim(),
        String(src.district || '').trim(),
        String(src.township || '').trim()
      ].filter(Boolean).join('-')
      const detail = String(src.formattedAddress || src.locationDesc || '').trim()
      if (region && detail) return `${region}-${detail}`
      return region || detail || '未填写'
    },
    formatRegion(row) {
      const src = row || {}
      const values = [
        String(src.province || '').trim(),
        String(src.city || '').trim(),
        String(src.district || '').trim()
      ].filter(Boolean)
      return values.join('-')
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
      const host = this.resolveAssetHost()
      if (/^https?:/i.test(url)) {
        const legacyMatch = url.match(/^https?:\/\/(?:127\.0\.0\.1|localhost)(?::\d+)?(\/.*)$/i)
        if (legacyMatch && host) {
          return `${host}${legacyMatch[1]}`
        }
        return url
      }
      if (/^(data:|wxfile:|file:)/i.test(url)) return url
      if (url.startsWith('//')) return `https:${url}`
      if (!host) return url
      if (url.startsWith('/')) return `${host}${url}`
      return `${host}/${url}`
    },
    resolveCoverImage(item) {
      if (!item) return ''
      const directCandidate = [
        item.coverImageUrl,
        item.imageUrl,
        item.coverUrl,
        item.cover,
        item.photoUrl,
        item.banner,
        item.picture,
        item.thumbUrl,
        item.fileUrl
      ].find((value) => String(value || '').trim())
      if (directCandidate) {
        return this.resolveAssetUrl(directCandidate)
      }

      const arrayCandidate = this.resolveImageFromArray(item.images)
        || this.resolveImageFromArray(item.imageList)
        || this.resolveImageFromArray(item.photos)
      return this.resolveAssetUrl(arrayCandidate)
    },
    resolveCoverPreviewImage(item) {
      if (!item) return ''
      const directCandidate = [
        item.coverThumbUrl,
        item.thumbUrl,
        item.thumbnailUrl,
        item.previewUrl,
        item.coverImageUrl,
        item.imageUrl
      ].find((value) => String(value || '').trim())
      if (directCandidate) {
        return this.resolveAssetUrl(directCandidate)
      }
      const arrayCandidate = this.resolveImageFromArray(item.images)
        || this.resolveImageFromArray(item.imageList)
        || this.resolveImageFromArray(item.photos)
      return this.resolveAssetUrl(arrayCandidate)
    },
    resolveImageFromArray(rawValue) {
      if (!rawValue) return ''
      let rows = rawValue
      if (typeof rows === 'string') {
        try {
          rows = JSON.parse(rows)
        } catch (e) {
          return ''
        }
      }
      if (!Array.isArray(rows) || !rows.length) return ''
      const first = rows[0]
      if (!first) return ''
      if (typeof first === 'string') {
        return String(first).trim()
      }
      return String(first.url || first.fileUrl || first.imageUrl || '').trim()
    },
    resolveImageUrls(rawValue) {
      if (!rawValue) return []
      let rows = rawValue
      if (typeof rows === 'string') {
        try {
          rows = JSON.parse(rows)
        } catch (e) {
          return []
        }
      }
      if (!Array.isArray(rows) || !rows.length) return []
      return rows
        .map((item) => {
          if (!item) return ''
          if (typeof item === 'string') return String(item).trim()
          return String(item.url || item.fileUrl || item.imageUrl || item.previewUrl || '').trim()
        })
        .filter(Boolean)
    },
    resolveFieldPreviewUrls(item) {
      if (!item) return []
      const urls = [
        item.coverImageUrl,
        item.imageUrl,
        item.coverUrl,
        item.cover,
        item.photoUrl,
        item.banner,
        item.picture,
        item.thumbUrl,
        item.fileUrl,
        ...this.resolveImageUrls(item.images),
        ...this.resolveImageUrls(item.imageList),
        ...this.resolveImageUrls(item.photos)
      ]
        .map((value) => this.resolveAssetUrl(value))
        .filter(Boolean)
      return Array.from(new Set(urls))
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
    parseSchema(text) {
      if (!text) return []
      try {
        const rows = JSON.parse(text)
        return Array.isArray(rows) ? rows.filter((item) => item && item.key) : []
      } catch (e) {
        return []
      }
    },
    formatSchemaValue(def, rawValue) {
      if (rawValue == null) return ''
      const value = String(rawValue).trim()
      if (!value) return ''
      const type = String((def && def.type) || '').toLowerCase()
      if (type !== 'select') {
        return def && def.unit ? `${value}${def.unit ? ` ${def.unit}` : ''}` : value
      }
      const rows = Array.isArray(def && def.options) ? def.options : []
      const matched = rows.find((item) => {
        if (!item || typeof item !== 'object') return false
        return String(item.value || item.code || item.label || '').trim() === value
      })
      const label = matched
        ? String(matched.label || matched.name || matched.value || value).trim()
        : value
      return def && def.unit ? `${label}${def.unit ? ` ${def.unit}` : ''}` : label
    },
    buildExtraEntries(extra, formSchemaText) {
      if (!extra || typeof extra !== 'object') return []
      const schemaMap = {}
      this.parseSchema(formSchemaText).forEach((item) => {
        schemaMap[item.key] = item
      })
      return Object.keys(extra)
        .map((key) => {
          const def = schemaMap[key] || {}
          const value = this.formatSchemaValue(def, extra[key])
          if (!value) return null
          return {
            key,
            label: String(def.label || key).trim() || key,
            value
          }
        })
        .filter(Boolean)
    },
    recordDynamicSummary(item) {
      const extra = this.parseJson(item && item.extraJson)
      if (!extra) return ''
      const schemaText = this.stepSchemaMap[this.normalizeId(item && item.stepId)] || ''
      const entries = this.buildExtraEntries(extra, schemaText)
      if (!entries.length) return ''
      return entries
        .map((entry) => `${entry.label}：${entry.value}`)
        .join(' · ')
    },
    formatCropVarietyPair(row) {
      return formatCropVarietyPairText(row, '未设置')
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
  padding: 18rpx 24rpx 24rpx;
  box-sizing: border-box;
}

.content::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.hero-card {
  background: #fff;
  border-radius: 20rpx;
  border: 1rpx solid var(--dh-color-border);
  padding: 18rpx;
}

.hero-card-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.hero-media-skeleton {
  width: 100%;
  height: 360rpx;
  overflow: hidden;
  border-radius: 18rpx;
}

.hero-head-skeleton {
  padding-top: 2rpx;
}

.hero-grid-skeleton {
  margin-top: 0;
}

.hero-item-skeleton {
  min-height: 114rpx;
}

.hero-media {
  position: relative;
}

.cover-banner {
  width: 100%;
  height: 360rpx;
  margin-bottom: 14rpx;
  overflow: hidden;
  border-radius: 18rpx;
}

.cover-banner-image {
  width: 100%;
  height: 100%;
}

.hero-media-badge {
  position: absolute;
  right: 18rpx;
  bottom: 30rpx;
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(31, 42, 26, 0.54);
  backdrop-filter: blur(10rpx);
}

.hero-media-badge-text {
  font-size: 22rpx;
  color: #ffffff;
  font-weight: 600;
}

.hero-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12rpx;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.name {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: #2c3a26;
}

.hero-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6f7c67;
  line-height: 1.5;
}

.hero-relation {
  display: inline-flex;
  align-items: center;
  margin-top: 10rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #edf7e7;
  color: #4d7a36;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.35;
}

.status {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  flex-shrink: 0;
  font-weight: 600;
}

.s-idle,
.s-fallow {
  background: #f0f3f1;
  color: #6d796d;
}

.s-sowing {
  background: #eef5ed;
  color: #577c49;
}

.s-growing {
  background: #ebf7ef;
  color: #4f7f5a;
}

.s-harvesting {
  background: #f8f3e8;
  color: #8b6c2a;
}

.hero-grid {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.hero-item,
.state-card {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f7faf6;
  border: 1rpx solid #ebf0e6;
}

.hero-label {
  display: block;
  font-size: 22rpx;
  color: #81907c;
}

.hero-value {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #394734;
  font-weight: 600;
  line-height: 1.45;
}

.hero-value.is-location-error {
  color: #d64646;
}

.hero-value.is-location-unknown {
  color: #8a9384;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin: 16rpx 2rpx 10rpx;
}

.mt {
  margin-top: 18rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2c3a26;
}

.section-sub,
.section-more {
  font-size: 24rpx;
  color: #5f8145;
}

.segment-scroll {
  white-space: nowrap;
  margin-bottom: 6rpx;
}

.segment-row {
  display: inline-flex;
  gap: 10rpx;
}

.segment-chip {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  border: 1rpx solid #dde6d7;
  background: #f6f8f5;
  color: #677462;
  font-size: 24rpx;
}

.segment-chip.active {
  border-color: #b7d0aa;
  background: #edf7e7;
  color: #4d7a36;
  font-weight: 600;
}

.empty-card {
  text-align: center;
  color: #8c9686;
  font-size: 25rpx;
}

.timeline-empty-card {
  margin-top: 8rpx;
  padding: 32rpx 26rpx;
  border-radius: 22rpx;
  background: linear-gradient(180deg, #ffffff 0%, #f7faf4 100%);
  border: 1rpx solid var(--dh-color-border);
  box-shadow: var(--dh-shadow-soft);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.timeline-empty-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 44rpx;
  border: 1rpx solid #d7e2cf;
  background: #f3f8ee;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18rpx;
}

.timeline-empty-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 9rpx;
  background: var(--dh-color-brand);
  box-shadow: 0 0 0 10rpx rgba(47, 125, 69, 0.12);
}

.timeline-empty-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #24311d;
}

.timeline-empty-desc {
  margin-top: 10rpx;
  margin-bottom: 20rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: var(--dh-color-text-soft);
}

.timeline-tail-space {
  height: calc(40rpx + env(safe-area-inset-bottom));
}

.flow-track {
  background: #fff;
  border-radius: 18rpx;
  padding: 14rpx;
  border: 1rpx solid var(--dh-color-border);
}

.flow-item {
  display: flex;
}

.flow-left {
  width: 56rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dot {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  font-size: 20rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #9aa89a;
}

.dot.done {
  background: #2d9c52;
}

.connector {
  width: 4rpx;
  flex: 1;
  margin-top: 6rpx;
  background: var(--dh-color-brand-light);
}

.connector.done {
  background: var(--dh-color-brand);
}

.flow-main {
  flex: 1;
  margin-left: 10rpx;
  margin-bottom: 12rpx;
  border: 1rpx solid #e3edd8;
  border-radius: 16rpx;
  padding: 12rpx 14rpx;
  background: #fff;
}

.flow-main.done {
  border-color: #b9ddb0;
  background: #f8fcf3;
}

.flow-top {
  display: flex;
  justify-content: space-between;
  gap: 10rpx;
}

.flow-name {
  font-size: 28rpx;
  color: #2c3a26;
  font-weight: 600;
}

.flow-count,
.flow-meta,
.flow-tip {
  font-size: 23rpx;
  color: #66755f;
  line-height: 1.5;
}

.flow-meta,
.flow-tip {
  margin-top: 8rpx;
}

.timeline-day {
  margin-bottom: 12rpx;
}

.day-tag {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e8f2dc;
  color: #4a642f;
  font-size: 23rpx;
  margin-bottom: 8rpx;
}

.timeline-item {
  display: grid;
  grid-template-columns: 90rpx 30rpx 1fr;
  gap: 8rpx;
  margin-bottom: 10rpx;
}

.time-col {
  padding-top: 8rpx;
  text-align: right;
}

.time {
  font-size: 24rpx;
  color: #6c7a66;
}

.node-col {
  position: relative;
  display: flex;
  justify-content: center;
}

.node-col::after {
  content: '';
  position: absolute;
  top: 16rpx;
  bottom: -20rpx;
  width: 2rpx;
  background: #dde8d2;
}

.timeline-item:last-child .node-col::after {
  display: none;
}

.node {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--dh-color-brand);
  margin-top: 12rpx;
}

.content-col {
  background: #fff;
  border-radius: 16rpx;
  padding: 12rpx;
  border: 1rpx solid var(--dh-color-border);
}

.r1 {
  display: flex;
  justify-content: space-between;
  gap: 10rpx;
}

.type {
  font-size: 27rpx;
  font-weight: 600;
  color: #2c3a26;
}

.flow-track-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.flow-skeleton-card {
  border-radius: 16rpx;
  padding: 12rpx 14rpx;
  background: #fff;
  border: 1rpx solid #e3edd8;
}

.timeline-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.timeline-skeleton-item {
  display: grid;
  grid-template-columns: 90rpx 30rpx 1fr;
  gap: 8rpx;
}

.timeline-skeleton-time {
  padding-top: 8rpx;
}

.timeline-skeleton-node-col {
  position: relative;
  display: flex;
  justify-content: center;
}

.timeline-skeleton-node-col::after {
  content: '';
  position: absolute;
  top: 16rpx;
  bottom: -18rpx;
  width: 2rpx;
  background: #dde8d2;
}

.timeline-skeleton-item:last-child .timeline-skeleton-node-col::after {
  display: none;
}

.timeline-skeleton-node {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: rgba(47, 125, 69, 0.28);
  margin-top: 12rpx;
}

.timeline-skeleton-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 12rpx;
  border: 1rpx solid var(--dh-color-border);
}

.step,
.r2,
.r3,
.r4 {
  font-size: 24rpx;
  color: #63715d;
}

.r2,
.r3,
.r4 {
  margin-top: 8rpx;
}

.r2-summary {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.r4-operator {
  display: flex;
  justify-content: flex-end;
  color: #73806e;
}

@media screen and (max-width: 768rpx) {
  .hero-grid {
    grid-template-columns: 1fr;
  }
}

.elder-mode {
  .name,
  .section-title,
  .flow-name,
  .type {
    font-size: 36rpx;
  }

  .hero-sub,
  .hero-relation,
  .status,
  .hero-label,
  .hero-value,
  .section-sub,
  .section-more,
  .segment-chip,
  .empty-card,
  .flow-count,
  .flow-meta,
  .flow-tip,
  .day-tag,
  .time,
  .step,
  .r2,
  .r3,
  .timeline-empty-desc {
    font-size: 30rpx;
  }

  .timeline-empty-title {
    font-size: 36rpx;
  }

  .hero-card,
  .cover-banner,
  .hero-item,
  .flow-track,
  .flow-main,
  .content-col,
  .state-card,
  .timeline-empty-card {
    border-radius: 22rpx;
  }

  .cover-banner {
    height: 420rpx;
  }

  .dot {
    width: 40rpx;
    height: 40rpx;
    font-size: 24rpx;
  }
}
</style>
