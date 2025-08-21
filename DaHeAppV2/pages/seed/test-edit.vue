<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      :title="testId ? '编辑检测' : '新增检测'"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="form-scroll" :show-scrollbar="false">
      <view class="form">
        <view v-if="batchInfo" class="summary-card card">
          <view class="summary-head">
            <view class="summary-main">
              <text class="summary-title">{{ batchInfo.batchCode || '当前批次' }}</text>
              <text class="summary-sub">{{ batchSummarySub }}</text>
            </view>
            <t-tag v-if="batchTagText" class="summary-tag" size="small" theme="primary" variant="light">
              {{ batchTagText }}
            </t-tag>
          </view>
          <view v-if="batchSummaryItems.length" class="summary-grid">
            <view v-for="item in batchSummaryItems" :key="item.label" class="summary-item">
              <text class="summary-label">{{ item.label }}</text>
              <text class="summary-value">{{ item.value }}</text>
            </view>
          </view>
          <view v-if="batchExtraPairs.length" class="summary-extra-wrap">
            <view v-for="item in batchExtraPairs" :key="item.key" class="summary-extra-item">
              <text class="summary-extra-label">{{ item.label }}</text>
              <text class="summary-extra-value">{{ item.value }}</text>
            </view>
          </view>
          <view v-if="batchRemarkText" class="summary-remark">
            <text class="summary-remark-label">备注</text>
            <text class="summary-remark-value">{{ batchRemarkText }}</text>
          </view>
        </view>

        <view class="progress-card card">
          <view class="progress-row">
            <view class="progress-left">
              <text v-if="submitReady" class="progress-ready">待提交</text>
              <template v-else>
                <text class="progress-label">必填未完成：</text>
                <text class="progress-value">{{ requiredMissingCount }} 项</text>
              </template>
            </view>
            <view class="progress-right">
              <text class="progress-label">已补充：</text>
              <text class="progress-plain">{{ optionalFilledCount }} 项</text>
            </view>
          </view>
        </view>

        <view class="section-block">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">1</view>
              <text class="section-title">检测信息</text>
            </view>
          </view>
          <view class="section-card card">
            <view class="form-item-block">
              <date-time-selector
                label="检测日期 *"
                mode="date"
                title="选择检测日期"
                placeholder="请选择检测日期"
                :date="date"
                :elder-mode="elderMode"
                @date-change="onDateSelectorChange"
              />
            </view>

            <view class="germination-card">
              <view class="germination-head">
                <text class="germination-title">芽率</text>
                <text class="germination-tip">{{ sampleCountHint }}</text>
              </view>

              <view class="metrics-grid germination-grid">
                <view class="metric-item metric-item-required">
                  <text class="field-label field-label-required">发芽数量（粒）</text>
                  <t-input
                    class="td-input td-input-editable"
                    type="digit"
                    :value="germinationCount"
                    placeholder="请输入发芽数量"
                    clearable
                    @change="onGerminationCountChange"
                  />
                </view>

                <view v-if="!sampleCountReadonly" class="metric-item metric-item-inline metric-item-full">
                  <view class="sample-inline-row">
                    <text class="sample-inline-text">按</text>
                    <t-input
                      class="td-input td-input-editable td-input-inline"
                      type="digit"
                      :value="sampleCount"
                      placeholder="100"
                      clearable
                      @change="onSampleCountChange"
                    />
                    <text class="sample-inline-text">粒样本自动计算</text>
                  </view>
                </view>

                <view class="metric-item metric-item-readonly">
                  <text class="field-label">芽率</text>
                  <view class="germination-rate-inline">
                    <text class="germination-rate-value">{{ germinationRateView }}</text>
                    <text class="germination-rate-tip">根据{{ resolvedSampleCount }}粒样本自动计算</text>
                  </view>
                </view>
              </view>
            </view>

            <view class="metrics-grid metrics-grid-secondary">
              <view class="metric-item">
                <text class="field-label">水分(%)</text>
                <t-input class="td-input td-input-editable" type="digit" :value="moisture" placeholder="可选" clearable @change="onMoistureChange" />
              </view>

              <view class="metric-item">
                <text class="field-label">纯度(%)</text>
                <t-input class="td-input td-input-editable" type="digit" :value="purity" placeholder="可选" clearable @change="onPurityChange" />
              </view>

              <view class="metric-item metric-item-full">
                <text class="field-label">净度(%)</text>
                <t-input
                  class="td-input td-input-editable"
                  type="digit"
                  :value="cleanliness"
                  placeholder="可选"
                  clearable
                  @change="onCleanlinessChange"
                />
              </view>
            </view>
          </view>
        </view>

        <view class="section-block form-end-gap">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">2</view>
              <text class="section-title">信息补充</text>
            </view>
          </view>

          <view class="section-card card">
            <view v-if="dynamicFields.length">
              <view v-for="item in dynamicFields" :key="item.key" class="dynamic-field-card" :class="{ 'is-required': item.required }">
                <view class="dynamic-head">
                  <view v-if="item.required" class="required-dot"></view>
                  <text class="field-label" :class="{ 'field-label-required': item.required }">{{ item.label }}</text>
                </view>

                <view v-if="item.type === 'date' || item.type === 'time' || item.type === 'select'" class="picker-trigger" @click="openDynamicPicker(item)">
                  <t-input
                    class="td-input td-input-picker"
                    readonly
                    :placeholder="item.placeholder || ('请选择' + item.label)"
                    :value="dynamicDisplayValue(item)"
                  />
                  <view class="picker-arrow"><t-icon name="chevron-right" size="32rpx" /></view>
                </view>

                <t-textarea
                  v-else-if="item.type === 'textarea'"
                  class="td-textarea td-input-editable"
                  :value="dynamicValues[item.key] || ''"
                  :placeholder="item.placeholder || ('请输入' + item.label)"
                  :maxlength="300"
                  indicator
                  @change="onDynamicChange(item.key, $event)"
                />

                <t-input
                  v-else
                  class="td-input td-input-editable"
                  :type="resolveInputType(item.type)"
                  :value="dynamicValues[item.key] || ''"
                  :placeholder="item.placeholder || ('请输入' + item.label)"
                  clearable
                  @change="onDynamicChange(item.key, $event)"
                />
              </view>
            </view>
            <view v-else class="empty-card">当前没有额外信息补充项</view>

            <view class="form-item-block">
              <text class="field-label">检测员</text>
              <t-input class="td-input td-input-editable" :value="testerName" placeholder="默认使用当前登录用户" clearable @change="onTesterNameChange" />
            </view>

            <view class="form-item-block">
              <text class="field-label">备注</text>
              <t-textarea class="td-textarea td-input-editable" :value="remark" placeholder="可选：记录抽样方式、环境信息等" :maxlength="300" indicator @change="onRemarkChange" />
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="submit-wrap">
      <text class="submit-brief">{{ submitBrief }}</text>
      <t-button class="submit-btn" theme="primary" size="large" block :loading="submitting" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : (testId ? '保存修改' : '提交检测') }}
      </t-button>
    </view>
    <t-picker
      :visible="dynamicSelectPickerVisible"
      :title="dynamicSelectTitle || '请选择'"
      :value="dynamicSelectPickerValue"
      @confirm="onDynamicSelectPickerConfirm"
      @cancel="dynamicSelectPickerVisible = false"
      @close="dynamicSelectPickerVisible = false"
      @update:visible="dynamicSelectPickerVisible = $event"
    >
      <t-picker-item :options="dynamicSelectOptions" />
    </t-picker>

    <t-date-time-picker
      :visible="dynamicDatePickerVisible"
      :title="dynamicDatePickerTitle || '请选择'"
      :mode="dynamicDatePickerMode"
      :format="dynamicDatePickerFormat"
      :value="dynamicDatePickerValue"
      @confirm="onDynamicDatePickerConfirm"
      @cancel="dynamicDatePickerVisible = false"
      @close="dynamicDatePickerVisible = false"
      @update:visible="dynamicDatePickerVisible = $event"
    />
  </view>
</template>

<script>
import api from '../../utils/request'
import elderPageMixin from '../../utils/elder-page'
import { formatCropVarietyPair } from '../../utils/crop-variety'
import { getOperatorNameFromUser } from '../../utils/auth'
import DateTimeSelector from '../../components/date-time-selector/date-time-selector.vue'
import { markDataChanged, refreshTopics } from '../../utils/data-refresh'

function normalizeType(type) {
  const raw = String(type || 'text').toLowerCase().trim()
  const supported = ['text', 'number', 'date', 'time', 'select', 'textarea', 'location']
  return supported.includes(raw) ? raw : 'text'
}

export default {
  mixins: [elderPageMixin],
  components: {
    DateTimeSelector
  },
  data() {
    const now = new Date()
    const yyyy = now.getFullYear()
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    const dd = String(now.getDate()).padStart(2, '0')
    return {
      batchId: null,
      testId: null,
      batchInfo: null,
      date: `${yyyy}-${mm}-${dd}`,
      sampleCount: '',
      seedRule: {
        fixedSampleSize: 1,
        defaultSampleSize: 100
      },
      germinationCount: '',
      moisture: '',
      purity: '',
      cleanliness: '',
      testerName: '',
      remark: '',
      submitting: false,
      dynamicConfigId: null,
      dynamicFields: [],
      dynamicValues: {},
      dynamicSelectPickerVisible: false,
      dynamicSelectPickerValue: [],
      dynamicSelectOptions: [],
      dynamicSelectTitle: '',
      dynamicSelectKey: '',
      dynamicDatePickerVisible: false,
      dynamicDatePickerMode: 'date',
      dynamicDatePickerFormat: 'YYYY-MM-DD',
      dynamicDatePickerValue: '',
      dynamicDatePickerTitle: '',
      dynamicDatePickerKey: ''
    }
  },
  computed: {
    batchTagText() {
      return this.batchInfo ? formatCropVarietyPair(this.batchInfo, '') : ''
    },
    batchSummarySub() {
      return this.batchInfo ? '检测记录将归档到该种子批次' : '本页用于补充该批次的检测记录'
    },
    batchSummaryItems() {
      if (!this.batchInfo) return []
      const list = []
      const push = (label, value) => {
        const text = String(value || '').trim()
        if (!text) return
        list.push({ label, value: text })
      }
      push('作物', this.batchInfo.cropType)
      push('品种', this.batchInfo.varietyName)
      push('生产日期', this.batchInfo.productionDate)
      return list
    },
    batchExtraPairs() {
      if (!this.batchInfo) return []
      const schema = this.parseDynamicFields(this.batchInfo.formSchema || this.batchInfo.schemaJson)
      const values = this.parseJson(this.batchInfo.extraJson)
      if (!schema.length) return []
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
    batchRemarkText() {
      return this.batchInfo ? String(this.batchInfo.remark || '').trim() : ''
    },
    sampleCountReadonly() {
      return Number(this.seedRule.fixedSampleSize) === 1
    },
    sampleCountHint() {
      if (this.sampleCountReadonly) {
        return `根据${this.seedRule.defaultSampleSize}粒样本自动计算`
      }
      return `当前按${this.resolvedSampleCount}粒样本自动计算`
    },
    resolvedSampleCount() {
      const num = Number(this.sampleCount)
      if (num > 0) return num
      return Number(this.seedRule.defaultSampleSize || 100)
    },
    germinationRateView() {
      const sample = Number(this.resolvedSampleCount || 0)
      const count = Number(this.germinationCount || 0)
      if (!(sample > 0) || !(count >= 0)) return '-'
      return `${((count * 100) / sample).toFixed(2)}%`
    },
    requiredMissingCount() {
      let count = 0
      if (!String(this.date || '').trim()) count += 1
      if (!this.sampleCountReadonly && !(Number(this.sampleCount) > 0)) count += 1
      if (!(Number(this.germinationCount) >= 0)) count += 1
      this.dynamicFields.forEach((item) => {
        if (!item.required) return
        if (!String(this.dynamicValues[item.key] || '').trim()) count += 1
      })
      return count
    },
    optionalFilledCount() {
      let count = 0
      if (String(this.moisture || '').trim()) count += 1
      if (String(this.purity || '').trim()) count += 1
      if (String(this.cleanliness || '').trim()) count += 1
      if (String(this.testerName || '').trim()) count += 1
      if (String(this.remark || '').trim()) count += 1
      this.dynamicFields.forEach((item) => {
        if (item.required) return
        if (String(this.dynamicValues[item.key] || '').trim()) count += 1
      })
      return count
    },
    submitReady() {
      return this.requiredMissingCount === 0
    },
    submitBrief() {
      const sample = this.resolvedSampleCount
      const germ = String(this.germinationCount || '').trim() || '-'
      return `芽率样本 ${sample} 粒，发芽 ${germ} 粒，芽率 ${this.germinationRateView}`
    }
  },
  async onLoad(query) {
    if (query && query.batchId) this.batchId = String(query.batchId)
    if (query && query.testId) this.testId = String(query.testId)
    await Promise.all([this.fetchSeedRule(), this.loadDynamicConfig(), this.loadBatchInfo()])
    if (this.testId) {
      await this.loadTestDetail()
    } else {
      this.applyDynamicDefaults()
      this.applyTesterDefault()
      if (this.sampleCountReadonly || !this.sampleCount) {
        this.sampleCount = String(this.seedRule.defaultSampleSize)
      }
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    applyTesterDefault() {
      if (String(this.testerName || '').trim()) return
      const operatorName = getOperatorNameFromUser()
      if (operatorName) {
        this.testerName = operatorName
      }
    },
    async loadBatchInfo() {
      if (!this.batchId) return
      try {
        this.batchInfo = await api.get(`/miniapp/seed-batches/${this.batchId}`)
      } catch (e) {
        console.error('加载批次信息失败', e)
      }
    },
    async fetchSeedRule() {
      try {
        const data = await api.get('/miniapp/seed-settings')
        this.seedRule = {
          fixedSampleSize: Number((data && data.fixedSampleSize) || 1),
          defaultSampleSize: Number((data && data.defaultSampleSize) || 100)
        }
        if (this.sampleCountReadonly || !this.sampleCount) {
          this.sampleCount = String(this.seedRule.defaultSampleSize)
        }
      } catch (e) {
        console.error('加载检测规则失败', e)
        if (!this.sampleCount) this.sampleCount = '100'
      }
    },
    async loadDynamicConfig() {
      try {
        const row = await api.get('/miniapp/dynamic-configs/current', {
          moduleKey: 'seed',
          sceneKey: 'test_fields',
          status: 'enabled'
        })
        if (!row) return
        this.dynamicConfigId = row.id || null
        this.dynamicFields = this.parseDynamicFields(row.schemaJson)
      } catch (e) {
        this.dynamicConfigId = null
        this.dynamicFields = []
      }
    },
    parseDynamicFields(schemaJson) {
      if (!schemaJson) return []
      try {
        const parsed = JSON.parse(schemaJson)
        if (!Array.isArray(parsed)) return []
        return parsed
          .filter((item) => item && item.key && item.label)
          .map((item) => ({
            ...item,
            type: normalizeType(item.type)
          }))
      } catch (e) {
        return []
      }
    },
    parseJson(text) {
      if (!text) return {}
      try {
        const parsed = JSON.parse(text)
        return parsed && typeof parsed === 'object' ? parsed : {}
      } catch (e) {
        return {}
      }
    },
    applyDynamicDefaults(extra = null) {
      const next = {}
      this.dynamicFields.forEach((item) => {
        if (extra && extra[item.key] != null) {
          next[item.key] = String(extra[item.key])
        } else if (item.defaultValue != null) {
          next[item.key] = String(item.defaultValue)
        } else {
          next[item.key] = ''
        }
      })
      this.dynamicValues = next
    },
    async loadTestDetail() {
      if (!this.batchId || !this.testId) return
      try {
        const row = await api.get(`/miniapp/seed-batches/${this.batchId}/tests/${this.testId}`)
        if (!row) return
        this.date = row.testDate || this.date
        this.sampleCount = row.sampleCount != null ? String(row.sampleCount) : this.sampleCount
        if (row.germinationCount != null) {
          this.germinationCount = String(row.germinationCount)
        } else if (row.germinationRate != null && Number(this.sampleCount) > 0) {
          const count = Math.round((Number(row.germinationRate) * Number(this.sampleCount)) / 100)
          this.germinationCount = String(count)
        }
        this.moisture = row.moisture != null ? String(row.moisture) : ''
        this.purity = row.purity != null ? String(row.purity) : ''
        this.cleanliness = row.cleanliness != null ? String(row.cleanliness) : ''
        this.testerName = row.testerName || ''
        this.remark = row.remark || ''
        if (row.formConfigId) {
          this.dynamicConfigId = row.formConfigId
        }
        if (row.formSchema) {
          this.dynamicFields = this.parseDynamicFields(row.formSchema)
        }
        this.applyDynamicDefaults(this.parseJson(row.extraJson))
        this.applyTesterDefault()
      } catch (e) {
        console.error('加载检测详情失败', e)
      }
    },
    onDateSelectorChange(value) {
      this.date = String(value || this.date)
    },
    eventValue(event) {
      if (event && Object.prototype.hasOwnProperty.call(event, 'value')) {
        return String(event.value == null ? '' : event.value)
      }
      if (event && event.detail && Object.prototype.hasOwnProperty.call(event.detail, 'value')) {
        return String(event.detail.value == null ? '' : event.detail.value)
      }
      if (event && Object.prototype.hasOwnProperty.call(event, 'detail')) {
        return String(event.detail == null ? '' : event.detail)
      }
      if (event == null) return ''
      return String(event)
    },
    resolveInputType(type) {
      return String(type || '').toLowerCase() === 'number' ? 'digit' : 'text'
    },
    onSampleCountChange(event) {
      this.sampleCount = this.eventValue(event)
    },
    onGerminationCountChange(event) {
      this.germinationCount = this.eventValue(event)
    },
    onPurityChange(event) {
      this.purity = this.eventValue(event)
    },
    onMoistureChange(event) {
      this.moisture = this.eventValue(event)
    },
    onCleanlinessChange(event) {
      this.cleanliness = this.eventValue(event)
    },
    onTesterNameChange(event) {
      this.testerName = this.eventValue(event)
    },
    onRemarkChange(event) {
      this.remark = this.eventValue(event)
    },
    onDynamicChange(key, event) {
      this.dynamicValues = {
        ...this.dynamicValues,
        [key]: this.eventValue(event)
      }
    },
    resolveSelectOptions(item) {
      const rows = Array.isArray(item && item.options) ? item.options : []
      return rows
        .map((row) => {
          if (!row || typeof row !== 'object') return null
          const label = String(row.label || row.name || row.value || '').trim()
          const value = String(row.value || row.code || row.label || '').trim()
          if (!label || !value) return null
          return { label, value }
        })
        .filter(Boolean)
    },
    resolveSelectLabel(item, value) {
      const target = String(value || '').trim()
      if (!target) return ''
      const found = this.resolveSelectOptions(item).find((row) => row.value === target)
      return found ? found.label : target
    },
    dynamicDisplayValue(item) {
      const value = String(this.dynamicValues[item.key] || '').trim()
      if (!value) return ''
      if (item.type === 'select') return this.resolveSelectLabel(item, value)
      return value
    },
    openDynamicPicker(item) {
      if (!item) return
      if (item.type === 'select') {
        this.dynamicSelectTitle = item.label || '请选择'
        this.dynamicSelectKey = item.key
        this.dynamicSelectOptions = this.resolveSelectOptions(item)
        this.dynamicSelectPickerValue = [String(this.dynamicValues[item.key] || '')]
        this.dynamicSelectPickerVisible = true
        return
      }
      this.dynamicDatePickerTitle = item.label || '请选择'
      this.dynamicDatePickerKey = item.key
      this.dynamicDatePickerMode = item.type === 'time' ? 'time' : 'date'
      this.dynamicDatePickerFormat = item.type === 'time' ? 'HH:mm' : 'YYYY-MM-DD'
      this.dynamicDatePickerValue = String(this.dynamicValues[item.key] || (item.type === 'time' ? '08:00' : this.date))
      this.dynamicDatePickerVisible = true
    },
    onDynamicSelectPickerConfirm(context) {
      this.dynamicSelectPickerVisible = false
      const value = Array.isArray(context && context.value) ? context.value[0] : ''
      if (!this.dynamicSelectKey) return
      this.dynamicValues = {
        ...this.dynamicValues,
        [this.dynamicSelectKey]: String(value || '')
      }
    },
    onDynamicDatePickerConfirm(context) {
      this.dynamicDatePickerVisible = false
      const value = String((context && context.value) || '')
      if (!this.dynamicDatePickerKey) return
      this.dynamicValues = {
        ...this.dynamicValues,
        [this.dynamicDatePickerKey]: value
      }
    },
    async submit() {
      if (!this.batchId) {
        uni.showToast({ title: '缺少批次信息', icon: 'none' })
        return
      }
      const sample = Number(this.resolvedSampleCount)
      if (!(sample > 0)) {
        uni.showToast({ title: '请输入有效的芽率样本数', icon: 'none' })
        return
      }
      const germCount = Number(this.germinationCount)
      if (!(germCount >= 0)) {
        uni.showToast({ title: '请输入发芽数量', icon: 'none' })
        return
      }
      if (germCount > sample) {
        uni.showToast({ title: '发芽数量不能大于芽率样本数', icon: 'none' })
        return
      }
      for (const item of this.dynamicFields) {
        if (!item.required) continue
        if (!String(this.dynamicValues[item.key] || '').trim()) {
          uni.showToast({ title: `请填写${item.label}`, icon: 'none' })
          return
        }
      }

      const extra = {}
      Object.keys(this.dynamicValues || {}).forEach((key) => {
        const value = String(this.dynamicValues[key] || '').trim()
        if (value) extra[key] = value
      })

      this.submitting = true
      try {
        const testerName = String(this.testerName || '').trim() || getOperatorNameFromUser() || ''
        const payload = {
          testDate: this.date,
          sampleCount: this.sampleCountReadonly ? null : sample,
          germinationCount: germCount,
          moisture: this.moisture ? Number(this.moisture) : null,
          purity: this.purity ? Number(this.purity) : null,
          cleanliness: this.cleanliness ? Number(this.cleanliness) : null,
          testerName: testerName || null,
          remark: this.remark,
          formConfigId: this.dynamicConfigId || null,
          extraJson: Object.keys(extra).length ? JSON.stringify(extra) : null
        }
        if (this.testId) {
          await api.put(`/miniapp/seed-batches/${this.batchId}/tests/${this.testId}`, payload)
        } else {
          await api.post(`/miniapp/seed-batches/${this.batchId}/tests`, payload)
        }
        markDataChanged(refreshTopics.seedBatchDetail(this.batchId))
        uni.showToast({ title: this.testId ? '已保存' : '已提交', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 300)
      } catch (e) {
        console.error('提交检测失败', e)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss">
.page {
  background: var(--dh-color-bg);
}

.form-scroll {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
}

.form {
  padding: 20rpx 24rpx calc(212rpx + env(safe-area-inset-bottom));
}

.card {
  background: #ffffff;
  border-radius: 20rpx;
  border: 1rpx solid var(--dh-color-border);
}

.summary-card,
.progress-card,
.section-card {
  padding: 18rpx;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.summary-main {
  flex: 1;
  min-width: 0;
}

.summary-title {
  display: block;
  font-size: 31rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.38;
}

.summary-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6d7a68;
  line-height: 1.5;
}

.summary-tag {
  flex-shrink: 0;
  --td-tag-primary-color: #5d7f4b;
  --td-tag-primary-light-color: #edf6e7;
  border-radius: 999rpx;
}

.summary-grid {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.summary-extra-wrap {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.summary-item {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f6f8f4;
  border: 1rpx solid #e1e7dc;
}

.summary-label {
  display: block;
  font-size: 22rpx;
  color: #8b9585;
  line-height: 1.35;
}

.summary-value {
  display: block;
  margin-top: 6rpx;
  font-size: 25rpx;
  color: #223021;
  font-weight: 600;
  line-height: 1.45;
}

.summary-extra-item {
  min-width: 0;
  padding: 14rpx;
  border-radius: 16rpx;
  background: #f6f8f4;
  border: 1rpx solid #e1e7dc;
}

.summary-extra-label {
  display: block;
  font-size: 22rpx;
  color: #8b9585;
  line-height: 1.35;
}

.summary-extra-value {
  display: block;
  margin-top: 6rpx;
  font-size: 25rpx;
  color: #223021;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-all;
}

.summary-remark {
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #e6ece0;
}

.summary-remark-label {
  display: block;
  font-size: 22rpx;
  color: #8b9585;
  line-height: 1.35;
}

.summary-remark-value {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #4d5a46;
  line-height: 1.6;
}

.progress-card {
  margin-top: 14rpx;
}

.progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10rpx;
}

.progress-left,
.progress-right {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.progress-label {
  font-size: 22rpx;
  color: #70806a;
}

.progress-value {
  font-size: 26rpx;
  color: #d35b4b;
  font-weight: 700;
}

.progress-plain {
  font-size: 26rpx;
  color: #223021;
  font-weight: 600;
}

.progress-ready {
  font-size: 27rpx;
  color: #2f8f4c;
  font-weight: 700;
}

.section-block {
  margin-top: 20rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 12rpx;
  padding: 0 2rpx;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.section-index {
  width: 34rpx;
  height: 34rpx;
  flex-shrink: 0;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
  background: var(--dh-color-brand);
}

.section-title {
  font-size: 34rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.3;
}

.section-meta {
  font-size: 23rpx;
  color: #70806a;
}

.form-item-block + .form-item-block,
.dynamic-field-card + .dynamic-field-card {
  margin-top: 18rpx;
}

.field-label {
  display: block;
  font-size: 24rpx;
  color: #66755f;
  line-height: 1.4;
  margin-bottom: 8rpx;
}

.field-label-required {
  color: #223021;
  font-weight: 700;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.germination-card {
  padding: 16rpx;
  border-radius: 18rpx;
  border: 1rpx solid #ecd4ce;
  background: #fff8f5;
}

.germination-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.germination-title {
  font-size: 28rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.35;
}

.germination-tip {
  font-size: 22rpx;
  color: #8b6b61;
  line-height: 1.45;
}

.germination-grid {
  margin-top: 0;
}

.metrics-grid-secondary {
  margin-top: 18rpx;
}

.metric-item-inline {
  padding: 14rpx 16rpx;
  border-radius: 18rpx;
  border: 1rpx solid #e2e7dc;
  background: #ffffff;
}

.sample-inline-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex-wrap: wrap;
}

.sample-inline-text {
  font-size: 24rpx;
  color: #66755f;
  line-height: 1.45;
}

.td-input-inline {
  width: 168rpx;
  flex-shrink: 0;
}

.metric-item {
  min-width: 0;
}

.metric-item-full {
  grid-column: 1 / -1;
}

.metric-item-required {
  padding: 14rpx;
  border-radius: 18rpx;
  background: #ffffff;
  border: 1rpx solid #ecd4ce;
}

.metric-item-readonly {
  padding: 0;
  background: transparent;
  border: 0;
}

.germination-rate-inline {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  padding-top: 6rpx;
}

.germination-rate-value {
  font-size: 38rpx;
  color: #223021;
  font-weight: 700;
  line-height: 1.15;
}

.germination-rate-tip {
  font-size: 22rpx;
  color: #738070;
  line-height: 1.45;
}

.td-input {
  --td-input-bg-color: #f6f8f4;
  --td-input-border-color: #dde4d6;
  --td-input-text-color: #223021;
  --td-input-placeholder-text-color: #98a394;
  --td-input-vertical-padding: 14rpx;
  --td-input-horizontal-padding: 18rpx;
  border-radius: 16rpx !important;
  border: 1rpx solid #dde4d6 !important;
  background: #f6f8f4 !important;
}

.td-input-editable {
  background: #f6f8f4 !important;
}

.td-input-picker {
  --td-input-bg-color: #ffffff;
  --td-input-border-color: #c3d2b7;
  background: #ffffff !important;
}

.td-input.t-input,
.td-input .t-input {
  min-height: 82rpx;
  border: 1rpx solid #dde4d6 !important;
  border-radius: 16rpx !important;
  background: #f6f8f4 !important;
}

.td-input-picker.t-input,
.td-input-picker .t-input {
  border-style: dashed !important;
  border-color: #c3d2b7 !important;
  background: #ffffff !important;
}

.td-input.t-input--border::after,
.td-input .t-input--border::after {
  display: none !important;
}

.td-textarea {
  --td-textarea-background-color: #f6f8f4;
  --td-textarea-border-color: #dde4d6;
  --td-textarea-text-color: #223021;
  --td-textarea-placeholder-color: #98a394;
  --td-textarea-vertical-padding: 14rpx;
  --td-textarea-horizontal-padding: 18rpx;
  border-radius: 16rpx !important;
  border: 1rpx solid #dde4d6 !important;
  background: #f6f8f4 !important;
}

.td-textarea.t-textarea,
.td-textarea .t-textarea {
  border: 1rpx solid #dde4d6 !important;
  border-radius: 16rpx !important;
  background: #f6f8f4 !important;
}

.td-textarea.t-textarea--border::after,
.td-textarea .t-textarea--border::after {
  display: none !important;
}

.picker-trigger {
  position: relative;
}

.picker-trigger .td-input,
.picker-trigger .t-input {
  pointer-events: none;
}

.picker-arrow {
  position: absolute;
  top: 50%;
  right: 18rpx;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8ea488;
  pointer-events: none;
}

.quick-row {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.dynamic-field-card {
  padding: 14rpx;
  border-radius: 18rpx;
  border: 1rpx solid #e2e7dc;
  background: #ffffff;
}

.dynamic-field-card.is-required {
  border-color: #ecd4ce;
  background: #fff9f7;
}

.dynamic-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.required-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 999rpx;
  background: #d35b4b;
  flex-shrink: 0;
}

.empty-card {
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
}

.form-end-gap {
  margin-bottom: 10rpx;
}

.submit-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  z-index: 120;
  background: rgba(243, 244, 246, 0.98);
  border-top: 1rpx solid var(--dh-color-border);
}

.submit-brief {
  display: block;
  margin-bottom: 8rpx;
  font-size: 22rpx;
  color: #6d7a68;
  line-height: 1.4;
}

.submit-btn {
  --td-button-primary-bg-color: var(--dh-color-brand);
  --td-button-primary-border-color: var(--dh-color-brand);
  --td-button-primary-active-bg-color: var(--dh-color-brand-press);
  --td-button-primary-active-border-color: var(--dh-color-brand-press);
  --td-button-large-height: 86rpx;
  border-radius: 16rpx;
}

@media screen and (max-width: 768rpx) {
  .summary-grid,
  .summary-extra-wrap,
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}

.elder-mode {
  .summary-title,
  .section-title,
  .germination-rate-value,
  .germination-title {
    font-size: 36rpx;
  }

  .summary-sub,
  .summary-label,
  .summary-value,
  .summary-extra-label,
  .summary-extra-value,
  .summary-remark-label,
  .summary-remark-value,
  .sample-inline-text,
  .progress-label,
  .progress-value,
  .progress-plain,
  .progress-ready,
  .field-label,
  .germination-rate-tip,
  .germination-tip,
  .section-meta,
  .empty-card,
  .submit-brief {
    font-size: 30rpx;
  }

  .summary-card,
  .progress-card,
  .section-card,
  .dynamic-field-card,
  .metric-item-inline,
  .metric-item-required,
  .metric-item-readonly {
    border-radius: 24rpx;
  }

  .td-input,
  .td-textarea {
    font-size: 32rpx;
  }

  .td-input.t-input,
  .td-input .t-input {
    min-height: 96rpx;
  }

  .submit-btn {
    --td-button-large-height: 108rpx;
    font-size: 36rpx;
  }

  .section-index {
    width: 48rpx;
    height: 48rpx;
    font-size: 28rpx;
  }

  .t-tag,
  .t-tag__text {
    font-size: 28rpx !important;
    line-height: 1.4;
  }
}
</style>
