<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="记录详情"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false" v-if="record">
      <view class="hero-card">
        <view class="hero-head">
          <view class="hero-main">
            <text class="hero-title">{{ stepName || recordTitle }}</text>
            <text class="hero-sub">{{ fieldName || ('田块#' + record.fieldId) }}</text>
          </view>
          <text class="hero-state" :class="canEdit || canDelete ? 'is-editable' : 'is-readonly'">
            {{ canEdit || canDelete ? '可编辑' : '只读' }}
          </text>
        </view>
        <view class="hero-grid">
          <view class="hero-item">
            <text class="hero-label">作业时间</text>
            <text class="hero-value">{{ formatDate(record.workDate) }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">执行人员</text>
            <text class="hero-value">{{ record.operatorName || '未填写' }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">天气</text>
            <text class="hero-value">{{ record.weather || '未记录' }}</text>
          </view>
          <view class="hero-item">
            <text class="hero-label">作业步骤</text>
            <text class="hero-value">{{ stepName || recordTitle }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="section-head">
          <text class="section-title">现场信息</text>
          <text class="section-sub">作业当时采集到的环境数据</text>
        </view>
        <view class="info-grid">
          <view class="info-item">
            <text class="info-label">天气位置</text>
            <text class="info-value">{{ record.weatherLocation || '未记录' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">温度</text>
            <text class="info-value">{{ record.temperature ? `${record.temperature}°C` : '未记录' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">湿度</text>
            <text class="info-value">{{ record.humidity ? `${record.humidity}%` : '未记录' }}</text>
          </view>
          <view class="info-item">
            <text class="info-label">风向风力</text>
            <text class="info-value">{{ weatherWindText }}</text>
          </view>
          <view class="info-item full">
            <text class="info-label">天气发布时间</text>
            <text class="info-value">{{ record.weatherReportTime || '未记录' }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="section-head">
          <text class="section-title">补充说明</text>
          <text class="section-sub">记录时填写的备注信息</text>
        </view>
        <text class="note">{{ record.notes || '无' }}</text>
      </view>

      <view class="card" v-if="extraEntries.length">
        <view class="section-head">
          <text class="section-title">步骤参数</text>
          <text class="section-sub">按流程步骤保存的业务数据</text>
        </view>
        <view class="info-grid">
          <view v-for="item in extraEntries" :key="item.key" class="info-item">
            <text class="info-label">{{ item.label }}</text>
            <text class="info-value">{{ item.value || '未填写' }}</text>
          </view>
        </view>
      </view>

      <view class="card" v-if="recordImageCards.length">
        <view class="section-head">
          <text class="section-title">记录图片</text>
          <text class="section-sub">{{ imageAssets.length ? '点击可预览原图' : '当前暂无可查看图片' }}</text>
        </view>
        <view class="photo-grid">
          <view
            v-for="(item, idx) in recordImageCards"
            :key="item.id || `${item.fileUrl}-${idx}`"
            class="photo-item"
            :class="{ 'is-pending': !item.fileUrl }"
          >
            <dh-smart-image
              v-if="item.fileUrl"
              class="photo-img"
              :src="item.fileUrl"
              :preview-src="item.previewUrl || item.thumbUrl || item.thumbnailUrl || item.fileUrl"
              mode="aspectFill"
              :lazy-load="true"
              loading-text="图片加载中..."
              empty-text="暂无图片"
              error-text="图片异常"
              @tap="previewImage(item.fileUrl)"
            />
            <view v-else class="photo-img photo-placeholder">
              <text class="photo-placeholder-state">{{ item.reviewStatusText || '待审核' }}</text>
              <text class="photo-placeholder-tip">{{ item.hintMessage || '该图片正在审核中，审核通过后可查看' }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="card photo-empty">暂无记录图片</view>

      <view class="action-card">
        <view class="action-row">
          <button class="edit-btn" :class="{ disabled: !canEdit }" @click="goEdit">编辑</button>
          <button class="del-btn" :class="{ disabled: !canDelete }" @click="removeRecord">删除</button>
        </view>
        <text class="perm-tip">仅可编辑或删除本人且仍在后台策略允许时限内的记录。</text>
      </view>
    </scroll-view>

    <view v-else class="loading-card">
      <dh-loading-state title="正在加载记录详情" desc="请稍候，正在同步农事记录..." />
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isElderMode } from '../../utils/accessibility'
import { hasDataChanged, markDataChanged, readRefreshMark, refreshTopics } from '../../utils/data-refresh'
import DhSmartImage from '../../components/dh-smart-image.vue'
import DhLoadingState from '../../components/dh-loading-state.vue'

export default {
  components: {
    DhSmartImage,
    DhLoadingState
  },
  data() {
    return {
      id: null,
      record: null,
      fieldName: '',
      stepName: '',
      imageAssets: [],
      pendingImageTips: [],
      extraEntries: [],
      elderMode: false,
      refreshMark: ''
    }
  },
  computed: {
    canEdit() {
      return !!(this.record && this.record.canEdit)
    },
    canDelete() {
      return !!(this.record && this.record.canDelete)
    },
    recordTitle() {
      if (!this.record) return '农事记录'
      const stepName = String(this.record.stepName || '').trim()
      if (stepName) return stepName
      return '农事记录'
    },
    weatherWindText() {
      const parts = [this.record && this.record.windDirection, this.record && this.record.windPower]
        .map((x) => String(x || '').trim())
        .filter(Boolean)
      return parts.join(' / ') || '未记录'
    },
    recordImageCards() {
      return [
        ...this.imageAssets.map((item) => ({
          ...item,
          reviewStatusText: '已通过',
          hintMessage: ''
        })),
        ...this.pendingImageTips
      ]
    }
  },
  async onLoad(options) {
    this.id = options.id
    this.elderMode = isElderMode()
    await this.fetchDetail()
  },
  onShow() {
    this.elderMode = isElderMode()
    if (this.id && hasDataChanged(refreshTopics.farmRecordDetail(this.id), this.refreshMark)) {
      this.fetchDetail().then(() => {
        this.refreshMark = readRefreshMark(refreshTopics.farmRecordDetail(this.id))
      })
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async fetchDetail() {
      if (!this.id) return
      try {
        const r = await api.get(`/miniapp/farm-records/${this.id}`)
        this.record = r
        let selectedStep = null
        if (r && r.fieldId) {
          const processParams = r.cycleId ? { cycleId: r.cycleId } : undefined
          const [f, process] = await Promise.all([
            api.get(`/miniapp/fields/${r.fieldId}`),
            api.get(`/miniapp/fields/${r.fieldId}/process`, processParams).catch(() => null)
          ])
          this.fieldName = (f && f.name) || ''
          const steps = (process && process.steps) || []
          selectedStep = steps.find((x) => x.id === r.stepId) || null
          this.stepName = selectedStep ? selectedStep.stepName || '' : ''
        }
        if (r && r.extraJson) {
          try {
            const parsed = JSON.parse(r.extraJson)
            this.extraEntries = this.buildExtraEntries(parsed, selectedStep ? selectedStep.formSchema : null)
          } catch (e) {
            this.extraEntries = []
          }
        } else {
          this.extraEntries = []
        }
        await this.loadImages()
        this.refreshMark = readRefreshMark(refreshTopics.farmRecordDetail(this.id))
      } catch (e) {
        console.error('加载记录详情失败', e)
      }
    },
    async loadImages() {
      if (!this.id) {
        this.imageAssets = []
        this.pendingImageTips = []
        return
      }
      try {
        const rows = await api.get(`/miniapp/farm-records/${this.id}/images`)
        const list = Array.isArray(rows) ? rows : []
        this.imageAssets = list
          .filter((x) => x && x.fileUrl)
          .map((x) => ({
            id: x.id,
            fileUrl: x.fileUrl,
            thumbUrl: x.thumbUrl || '',
            previewUrl: x.previewUrl || '',
            thumbnailUrl: x.thumbnailUrl || ''
          }))
        this.pendingImageTips = list
          .filter((x) => x && !x.fileUrl && String(x.reviewStatus || '').trim() === 'pending')
          .map((x) => ({
            id: x.id,
            reviewStatusText: x.reviewStatusText || '待审核',
            hintMessage: x.hintMessage || '该图片正在审核中，审核通过后可查看'
          }))
      } catch (e) {
        this.imageAssets = []
        this.pendingImageTips = []
      }
    },
    previewImage(currentUrl) {
      const urls = this.imageAssets.map((x) => x.fileUrl).filter(Boolean)
      if (!urls.length) return
      uni.previewImage({
        urls,
        current: currentUrl || urls[0]
      })
    },
    buildExtraEntries(extra, formSchemaText) {
      if (!extra || typeof extra !== 'object') return []
      const schema = this.parseSchema(formSchemaText)
      const fieldMap = {}
      schema.forEach((x) => {
        fieldMap[x.key] = x
      })
      return Object.keys(extra).map((key) => {
        const def = fieldMap[key] || {}
        const rawValue = extra[key]
        const value = this.formatSchemaValue(def, rawValue)
        return {
          key,
          label: def.label || key,
          value: def.unit && value ? `${value} ${def.unit}` : value
        }
      })
    },
    formatSchemaValue(def, rawValue) {
      if (rawValue == null) return ''
      const value = String(rawValue)
      const type = String((def && def.type) || '').toLowerCase()
      if (type !== 'select') return value
      const options = (def && def.options) || []
      const rows = Array.isArray(options) ? options : String(options).split(',').map((x) => x.trim()).filter(Boolean)
      const matched = rows.find((x) => {
        if (typeof x === 'object' && x) {
          return String(x.value || x.code || x.label || '') === value
        }
        return String(x) === value
      })
      if (!matched) return value
      if (typeof matched === 'object') {
        return String(matched.label || matched.name || matched.value || value)
      }
      return value
    },
    parseSchema(text) {
      if (!text) return []
      try {
        const rows = JSON.parse(text)
        if (!Array.isArray(rows)) return []
        return rows.filter((x) => x && x.key && x.label)
      } catch (e) {
        return []
      }
    },
    goEdit() {
      if (!this.id) return
      if (!this.canEdit) {
        uni.showToast({ title: '当前记录不可编辑', icon: 'none' })
        return
      }
      uni.navigateTo({ url: `/pages/home/record-edit?id=${this.id}` })
    },
    removeRecord() {
      if (!this.id) return
      if (!this.canDelete) {
        uni.showToast({ title: '当前记录不可删除', icon: 'none' })
        return
      }
      uni.showModal({
        title: '确认删除',
        content: '删除后不可恢复，是否继续？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await api.delete(`/miniapp/farm-records/${this.id}`)
            markDataChanged([
              refreshTopics.farmRecords(),
              refreshTopics.fieldDetail(this.record && this.record.fieldId),
              refreshTopics.farmRecordDetail(this.id)
            ])
            uni.showToast({ title: '已删除', icon: 'success' })
            setTimeout(() => uni.navigateBack(), 300)
          } catch (e) {
            console.error('删除失败', e)
          }
        }
      })
    },
    formatDate(v) {
      return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
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

.hero-card,
.card,
.action-card,
.loading-card {
  background: #fff;
  border-radius: 20rpx;
  border: 1rpx solid var(--dh-color-border);
}

.hero-card {
  padding: 20rpx;
  background: linear-gradient(180deg, #f7fbf2 0%, #ffffff 100%);
}

.card,
.action-card {
  margin-top: 14rpx;
  padding: 18rpx;
}

.loading-card {
  margin: 120rpx 24rpx 0;
  padding: 40rpx 24rpx;
  text-align: center;
  color: #83907d;
  font-size: 28rpx;
}

.hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-title {
  display: block;
  font-size: 34rpx;
  color: #2c3a26;
  font-weight: 700;
  line-height: 1.4;
}

.hero-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #64804c;
}

.hero-state {
  flex-shrink: 0;
  min-height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
}

.hero-state.is-editable {
  background: #edf7e7;
  color: #4d7a36;
}

.hero-state.is-readonly {
  background: #eff2ef;
  color: #6e7770;
}

.hero-grid,
.info-grid {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.hero-item,
.info-item {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f7faf6;
  border: 1rpx solid #ebf0e6;
}

.info-item.full {
  grid-column: 1 / -1;
}

.hero-label,
.info-label,
.section-sub {
  display: block;
  font-size: 22rpx;
  color: #7d8a76;
}

.hero-value,
.info-value {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #34412f;
  font-weight: 600;
  line-height: 1.45;
}

.section-head {
  margin-bottom: 10rpx;
}

.section-title {
  display: block;
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 700;
}

.section-sub {
  margin-top: 6rpx;
  line-height: 1.5;
}

.note {
  display: block;
  font-size: 26rpx;
  color: #475641;
  line-height: 1.7;
}

.photo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.photo-item {
  width: 196rpx;
  height: 196rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: var(--dh-color-brand-light);
}

.photo-img {
  width: 100%;
  height: 100%;
}

.photo-empty {
  padding: 28rpx 20rpx;
  color: var(--dh-color-text-sub);
  font-size: 24rpx;
  line-height: 1.5;
  text-align: center;
}

.photo-item.is-pending {
  background: transparent;
}

.photo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18rpx 16rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #fff8ed 0%, #fff3dd 100%);
  border: 1rpx solid #efd2a1;
}

.photo-placeholder-state {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: #b4731b;
  line-height: 1.4;
}

.photo-placeholder-tip {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #8a6a35;
  text-align: center;
}

.action-row {
  display: flex;
  gap: 12rpx;
}

.edit-btn,
.del-btn {
  flex: 1;
  height: 78rpx;
  line-height: 78rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.edit-btn {
  background: var(--dh-color-brand);
  color: #fff;
}

.del-btn {
  background: #fff2f0;
  color: #d54941;
  border: 1rpx solid rgba(213, 73, 65, 0.18);
}

.edit-btn.disabled,
.del-btn.disabled {
  opacity: 0.45;
}

.perm-tip {
  margin-top: 10rpx;
  display: block;
  text-align: center;
  color: #7b866f;
  font-size: 24rpx;
  line-height: 1.55;
}

@media screen and (max-width: 768rpx) {
  .hero-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}

.elder-mode {
  .hero-title,
  .section-title,
  .edit-btn,
  .del-btn,
  .loading-card {
    font-size: 36rpx;
  }

  .hero-sub,
  .hero-state,
  .hero-label,
  .hero-value,
  .info-label,
  .info-value,
  .section-sub,
  .note,
  .perm-tip {
    font-size: 30rpx;
  }

  .hero-card,
  .card,
  .action-card,
  .loading-card,
  .hero-item,
  .info-item,
  .photo-item {
    border-radius: 22rpx;
  }

  .edit-btn,
  .del-btn {
    height: 88rpx;
    line-height: 88rpx;
  }
}
</style>
