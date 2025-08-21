<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      :title="batchId ? '编辑批次' : '新增批次'"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="form-scroll" :show-scrollbar="false">
      <view class="form">
        <view class="card">
          <text class="label">批次号 *</text>
          <t-input
            class="td-input"
            :value="form.batchCode"
            placeholder="如：DH-2026-CORN-009"
            clearable
            @change="onBatchCodeChange"
          />
        </view>

        <view class="card">
          <text class="label">作物 *</text>
          <picker mode="selector" :range="cropOptions" range-key="label" @change="onCropChange">
            <view class="picker">
              <text>{{ selectedCropLabel }}</text>
            </view>
          </picker>
          <text class="sub-tip">作物统一从后台维护的选项中选择。</text>
        </view>

        <view class="card">
          <text class="label">品种名称 *</text>
          <picker mode="selector" :range="varietyOptions" range-key="label" @change="onVarietyChange">
            <view class="picker">
              <text>{{ selectedVarietyLabel }}</text>
            </view>
          </picker>
          <text class="sub-tip">品种统一从后台维护的选项中选择；若缺少请先在后台新增。</text>
        </view>

        <view class="card">
          <text class="label">生产日期</text>
          <picker mode="date" :value="form.productionDate" @change="onDateChange">
            <view class="picker">
              <text v-if="form.productionDate">{{ form.productionDate }}</text>
              <text v-else class="placeholder">请选择日期</text>
            </view>
          </picker>
          <view class="quick-row">
            <view class="quick-chip" @click="setProductionDateQuick('today')">今天</view>
            <view class="quick-chip" @click="setProductionDateQuick('yesterday')">昨天</view>
            <view class="quick-chip" @click="setProductionDateQuick('clear')">清空</view>
          </view>
        </view>

        <view class="card" v-if="dynamicFields.length">
          <view class="line between">
            <text class="label">批次扩展字段</text>
            <text class="meta" v-if="dynamicConfigId">模板#{{ dynamicConfigId }}</text>
          </view>
          <view class="dynamic-item" v-for="item in dynamicFields" :key="item.key">
            <text class="dynamic-label">{{ item.label }}<text class="required" v-if="item.required"> *</text></text>
            <picker
              v-if="item.type === 'date'"
              mode="date"
              :value="dynamicValues[item.key] || form.productionDate || ''"
              @change="onDynamicPick(item.key, $event)"
            >
              <view class="picker">
                <text v-if="dynamicValues[item.key]">{{ dynamicValues[item.key] }}</text>
                <text v-else class="placeholder">{{ item.placeholder || ('请选择' + item.label) }}</text>
              </view>
            </picker>
            <picker
              v-else-if="item.type === 'select'"
              mode="selector"
              :range="resolveSelectOptions(item)"
              range-key="label"
              @change="onDynamicSelect(item, $event)"
            >
              <view class="picker">
                <text v-if="dynamicValues[item.key]">{{ resolveSelectLabel(item, dynamicValues[item.key]) }}</text>
                <text v-else class="placeholder">{{ item.placeholder || ('请选择' + item.label) }}</text>
              </view>
            </picker>
            <textarea
              v-else-if="item.type === 'textarea'"
              class="td-textarea"
              :value="dynamicValues[item.key] || ''"
              :placeholder="item.placeholder || ('请输入' + item.label)"
              @change="onDynamicChange(item.key, $event)"
            />
            <t-input
              v-else
              class="td-input"
              :type="resolveInputType(item.type)"
              :value="dynamicValues[item.key] || ''"
              :placeholder="item.placeholder || ('请输入' + item.label)"
              clearable
              @change="onDynamicChange(item.key, $event)"
            />
          </view>
        </view>

        <view class="card">
          <text class="label">备注</text>
          <t-textarea
            class="td-textarea"
            :value="form.remark"
            placeholder="可选：批次说明"
            :maxlength="300"
            indicator
            @change="onRemarkChange"
          />
        </view>

        <view class="submit-wrap">
          <t-button
            class="submit-btn"
            theme="primary"
            size="large"
            block
            :loading="saving"
            :disabled="saving || loading"
            @click="submit"
          >
            {{ saving ? '保存中...' : '保存批次' }}
          </t-button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { canManageSeedBatch, isApprovedUser } from '../../utils/auth'
import elderPageMixin from '../../utils/elder-page'
import { markDataChanged, refreshTopics } from '../../utils/data-refresh'
function normalizeType(type) {
  const raw = String(type || 'text').toLowerCase().trim()
  const supported = ['text', 'number', 'date', 'select', 'textarea', 'location']
  return supported.includes(raw) ? raw : 'text'
}

export default {
  mixins: [elderPageMixin],
  data() {
    return {
      batchId: null,
      loading: false,
      saving: false,
      dynamicConfigId: null,
      dynamicFields: [],
      dynamicValues: {},
      cropOptions: [{ value: '', label: '请选择已有作物' }],
      varietyOptions: [{ value: '', label: '请选择已有品种' }],
      selectedCropIndex: 0,
      selectedVarietyIndex: 0,
      form: {
        batchCode: '',
        cropType: '',
        varietyName: '',
        productionDate: '',
        remark: ''
      }
    }
  },
  computed: {
    selectedCropLabel() {
      if (this.form.cropType) return this.form.cropType
      const row = this.cropOptions[this.selectedCropIndex]
      return row ? row.label : '请选择已有作物'
    },
    selectedVarietyLabel() {
      if (this.form.varietyName) return this.form.varietyName
      const row = this.varietyOptions[this.selectedVarietyIndex]
      return row ? row.label : '请选择已有品种'
    }
  },
  async onLoad(query) {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    if (!canManageSeedBatch()) {
      uni.showToast({ title: '当前账号无批次管理权限', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 220)
      return
    }
    this.batchId = query && query.id ? String(query.id) : null
    await Promise.all([this.loadDynamicConfig(), this.loadCropOptions(), this.loadVarietyOptions()])
    if (this.batchId) {
      await this.loadDetail()
    } else {
      this.applyDynamicDefaults()
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async loadCropOptions() {
      try {
        const rows = await api.get('/miniapp/meta/options/crops', {})
        const unique = Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
        this.cropOptions = [{ value: '', label: '请选择已有作物' }, ...unique.map((x) => ({ value: x, label: x }))]
      } catch (e) {
        console.error('加载作物列表失败', e)
        this.cropOptions = [{ value: '', label: '请选择已有作物' }]
      }
    },
    async loadVarietyOptions() {
      try {
        const rows = await api.get('/miniapp/meta/options/varieties', {
          cropName: this.form.cropType || undefined
        })
        const unique = Array.from(new Set((Array.isArray(rows) ? rows : []).map((x) => String(x || '').trim()).filter(Boolean)))
        this.varietyOptions = [{ value: '', label: '请选择已有品种' }, ...unique.map((x) => ({ value: x, label: x }))]
      } catch (e) {
        console.error('加载品种列表失败', e)
        this.varietyOptions = [{ value: '', label: '请选择已有品种' }]
      }
    },
    async onCropChange(e) {
      const idx = Number(e.detail.value)
      this.selectedCropIndex = idx
      const row = this.cropOptions[idx]
      this.form.cropType = row && row.value ? row.value : ''
      this.form.varietyName = ''
      this.selectedVarietyIndex = 0
      await this.loadVarietyOptions()
    },
    onVarietyChange(e) {
      const idx = Number(e.detail.value)
      this.selectedVarietyIndex = idx
      const row = this.varietyOptions[idx]
      this.form.varietyName = row ? row.value || '' : ''
    },
    syncCropIndex() {
      const target = String(this.form.cropType || '').trim()
      if (!target) {
        this.selectedCropIndex = 0
        return
      }
      const idx = this.cropOptions.findIndex((x) => x.value === target)
      this.selectedCropIndex = idx >= 0 ? idx : 0
    },
    syncVarietyIndex() {
      const target = String(this.form.varietyName || '').trim()
      if (!target) {
        this.selectedVarietyIndex = 0
        return
      }
      const idx = this.varietyOptions.findIndex((x) => x.value === target)
      this.selectedVarietyIndex = idx >= 0 ? idx : 0
    },
    async loadDynamicConfig() {
      try {
        const row = await api.get('/miniapp/dynamic-configs/current', {
          moduleKey: 'seed',
          sceneKey: 'batch_fields',
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
          .filter((x) => x && x.key && x.label)
          .map((x) => ({
            ...x,
            type: normalizeType(x.type)
          }))
      } catch (e) {
        return []
      }
    },
    applyDynamicDefaults() {
      const next = {}
      this.dynamicFields.forEach((item) => {
        next[item.key] = item.defaultValue != null ? String(item.defaultValue) : ''
      })
      this.dynamicValues = next
    },
    async loadDetail() {
      this.loading = true
      try {
        const row = await api.get(`/miniapp/seed-batches/${this.batchId}`)
        if (!row) return
        this.form.batchCode = row.batchCode || ''
        this.form.cropType = row.cropType || ''
        this.syncCropIndex()
        await this.loadVarietyOptions()
        this.form.varietyName = row.varietyName || ''
        this.form.productionDate = row.productionDate || ''
        this.form.remark = row.remark || ''
        this.syncVarietyIndex()
        if (row.formConfigId) {
          this.dynamicConfigId = row.formConfigId
        }
        if (row.formSchema) {
          this.dynamicFields = this.parseDynamicFields(row.formSchema)
        }
        const extra = this.parseExtraJson(row.extraJson)
        this.dynamicValues = {}
        this.dynamicFields.forEach((item) => {
          if (extra[item.key] != null) {
            this.dynamicValues[item.key] = String(extra[item.key])
          } else if (item.defaultValue != null) {
            this.dynamicValues[item.key] = String(item.defaultValue)
          } else {
            this.dynamicValues[item.key] = ''
          }
        })
      } catch (e) {
        console.error('加载批次详情失败', e)
      } finally {
        this.loading = false
      }
    },
    parseExtraJson(text) {
      if (!text) return {}
      try {
        const parsed = JSON.parse(text)
        return parsed && typeof parsed === 'object' ? parsed : {}
      } catch (e) {
        return {}
      }
    },
    onDateChange(e) {
      this.form.productionDate = e.detail.value
    },
    onBatchCodeChange(e) {
      this.form.batchCode = this.eventValue(e)
    },
    onRemarkChange(e) {
      this.form.remark = this.eventValue(e)
    },
    eventValue(e) {
      if (e && Object.prototype.hasOwnProperty.call(e, 'value')) {
        return String(e.value == null ? '' : e.value)
      }
      if (e && e.detail && Object.prototype.hasOwnProperty.call(e.detail, 'value')) {
        return String(e.detail.value == null ? '' : e.detail.value)
      }
      if (e && Object.prototype.hasOwnProperty.call(e, 'detail')) {
        return String(e.detail == null ? '' : e.detail)
      }
      if (e == null) return ''
      return String(e)
    },
    resolveInputType(type) {
      return String(type || '').toLowerCase() === 'number' ? 'digit' : 'text'
    },
    setProductionDateQuick(type) {
      if (type === 'clear') {
        this.form.productionDate = ''
        return
      }
      const date = new Date()
      if (type === 'yesterday') {
        date.setDate(date.getDate() - 1)
      }
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      this.form.productionDate = `${y}-${m}-${d}`
    },
    onDynamicChange(key, e) {
      this.dynamicValues = {
        ...this.dynamicValues,
        [key]: this.eventValue(e)
      }
    },
    onDynamicPick(key, e) {
      this.dynamicValues = {
        ...this.dynamicValues,
        [key]: e.detail.value
      }
    },
    onDynamicSelect(item, e) {
      const options = this.resolveSelectOptions(item)
      const idx = Number(e.detail.value)
      if (idx < 0 || idx >= options.length) return
      this.dynamicValues = {
        ...this.dynamicValues,
        [item.key]: options[idx].value
      }
    },
    resolveSelectOptions(item) {
      const raw = item && item.options
      const arr = Array.isArray(raw) ? raw : []
      return arr
        .map((x) => {
          if (!x || typeof x !== 'object') return null
          const label = String(x.label || x.name || x.value || '').trim()
          const value = String(x.value || x.code || x.label || '').trim()
          if (!label || !value) return null
          return { label, value }
        })
        .filter(Boolean)
    },
    resolveSelectLabel(item, value) {
      const v = String(value || '').trim()
      if (!v) return ''
      const found = this.resolveSelectOptions(item).find((x) => x.value === v)
      return found ? found.label : v
    },
    async submit() {
      if (!this.form.batchCode.trim()) {
        uni.showToast({ title: '请填写批次号', icon: 'none' })
        return
      }
      if (!this.form.cropType.trim()) {
        uni.showToast({ title: '请选择作物', icon: 'none' })
        return
      }
      if (!this.form.varietyName.trim()) {
        uni.showToast({ title: '请填写品种名称', icon: 'none' })
        return
      }
      for (const field of this.dynamicFields) {
        if (!field.required) continue
        const value = String(this.dynamicValues[field.key] || '').trim()
        if (!value) {
          uni.showToast({ title: `请填写${field.label}`, icon: 'none' })
          return
        }
      }

      const extra = {}
      Object.keys(this.dynamicValues || {}).forEach((key) => {
        const value = String(this.dynamicValues[key] || '').trim()
        if (value) extra[key] = value
      })

      const payload = {
        batchCode: this.form.batchCode.trim(),
        cropType: this.form.cropType.trim(),
        varietyName: this.form.varietyName.trim(),
        productionDate: this.form.productionDate || null,
        remark: this.form.remark ? this.form.remark.trim() : '',
        formConfigId: this.dynamicConfigId || null,
        extraJson: Object.keys(extra).length ? JSON.stringify(extra) : null
      }

      this.saving = true
      try {
        let row = null
        if (this.batchId) {
          row = await api.put(`/miniapp/seed-batches/${this.batchId}`, payload)
        } else {
          row = await api.post('/miniapp/seed-batches', payload)
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        const id = (row && row.id) || this.batchId
        markDataChanged([
          refreshTopics.seedBatches(),
          refreshTopics.seedBatchDetail(id)
        ])
        setTimeout(() => {
          if (id) {
            uni.redirectTo({ url: `/pages/seed/batch-detail?id=${id}` })
          } else {
            uni.navigateBack()
          }
        }, 220)
      } catch (e) {
        console.error('保存批次失败', e)
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

.form-scroll {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
}

.form-scroll::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.form {
  padding: 16rpx 24rpx 20rpx;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 14rpx 16rpx;
  margin-bottom: 10rpx;
}

.line {
  display: flex;
  align-items: center;
}

.between {
  justify-content: space-between;
}

.meta {
  font-size: 22rpx;
  color: #65804a;
}

.label {
  display: block;
  font-size: 24rpx;
  color: #6a7764;
  margin-bottom: 8rpx;
}

.input,
.picker {
  min-height: 62rpx;
  display: flex;
  align-items: center;
  font-size: 28rpx;
  color: #2c3a26;
}

.placeholder {
  color: #9aa695;
}

.dynamic-item + .dynamic-item {
  margin-top: 8rpx;
}

.dynamic-label {
  display: block;
  font-size: 24rpx;
  color: #5f6f59;
  margin-bottom: 6rpx;
}

.sub-tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7a8573;
}

.quick-row {
  margin-top: 8rpx;
  display: flex;
  gap: 10rpx;
}

.quick-chip {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
  font-size: 22rpx;
}

.required {
  color: #dd4b4b;
}

.textarea {
  width: 100%;
  min-height: 120rpx;
  font-size: 27rpx;
}

.mt8 {
  margin-top: 8rpx;
}

.td-input {
  --td-input-bg-color: var(--dh-color-surface-soft);
  --td-input-text-color: #2c3a26;
  --td-input-placeholder-text-color: #9aa695;
  --td-input-vertical-padding: 10rpx;
  --td-input-horizontal-padding: 16rpx;
  border-radius: 12rpx;
}

.td-textarea {
  --td-textarea-bg-color: var(--dh-color-surface-soft);
  --td-textarea-text-color: #2c3a26;
  --td-textarea-placeholder-color: #9aa695;
  --td-textarea-vertical-padding: 12rpx;
  --td-textarea-horizontal-padding: 14rpx;
  border-radius: 12rpx;
}

.submit-wrap {
  margin-top: 8rpx;
}

.submit-btn {
  --td-button-primary-bg-color: var(--dh-color-brand);
  --td-button-primary-border-color: var(--dh-color-brand);
  --td-button-primary-active-bg-color: var(--dh-color-brand-press);
  --td-button-primary-active-border-color: var(--dh-color-brand-press);
  --td-button-primary-disabled-bg-color: #b2c7a4;
  --td-button-primary-disabled-border-color: #b2c7a4;
}
</style>
