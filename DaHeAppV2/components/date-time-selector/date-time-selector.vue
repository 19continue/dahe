<template>
  <view class="date-time-selector" :class="{ 'is-elder': elderMode }">
    <text class="dt-label">{{ label }}</text>
    <t-cell
      class="picker-cell"
      :title="displayTitle"
      arrow
      :bordered="false"
      @click="openPicker"
    />

    <t-date-time-picker
      :visible="pickerVisible"
      :title="resolvedTitle"
      :mode="pickerMode"
      :format="pickerFormat"
      :value="pickerValue"
      @confirm="onPickerConfirm"
      @cancel="pickerVisible = false"
      @close="pickerVisible = false"
      @update:visible="pickerVisible = $event"
    >
      <template #footer>
        <view class="picker-footer">
          <t-button
            class="now-btn"
            theme="primary"
            variant="text"
            :size="elderMode ? 'medium' : 'small'"
            @click="pickNowFromPicker"
          >
            {{ isDateOnly ? '今天' : '现在' }}
          </t-button>
        </view>
      </template>
    </t-date-time-picker>
  </view>
</template>

<script>
export default {
  name: 'DateTimeSelector',
  props: {
    label: {
      type: String,
      default: '作业时间 *'
    },
    title: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: ''
    },
    mode: {
      type: String,
      default: 'minute'
    },
    date: {
      type: String,
      default: ''
    },
    time: {
      type: String,
      default: ''
    },
    elderMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      pickerVisible: false,
      pickerValue: ''
    }
  },
  computed: {
    isDateOnly() {
      return String(this.mode || '').toLowerCase() === 'date'
    },
    pickerMode() {
      return this.isDateOnly ? 'date' : 'minute'
    },
    pickerFormat() {
      return this.isDateOnly ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm'
    },
    resolvedTitle() {
      if (this.title) return this.title
      return this.isDateOnly ? '选择日期' : '选择作业时间'
    },
    displayTitle() {
      const date = this.normalizeDate(this.date, '')
      const time = this.normalizeTime(this.time, '')
      if (this.isDateOnly) return date || this.placeholder || '请选择日期'
      if (date && time) return `${date} ${time}`
      if (date) return date
      if (time) return time
      return this.placeholder || '请选择作业时间'
    }
  },
  watch: {
    date: {
      handler() {
        this.syncPickerValue()
      },
      immediate: true
    },
    time: {
      handler() {
        this.syncPickerValue()
      },
      immediate: true
    }
  },
  methods: {
    syncPickerValue() {
      this.pickerValue = this.combineDateTime(this.date, this.time)
    },
    openPicker() {
      this.syncPickerValue()
      this.pickerVisible = true
    },
    onPickerConfirm(context) {
      const combined = this.normalizeDateTime(context && context.value, this.combineDateTime(this.date, this.time))
      const [date, time] = combined.split(' ')
      this.applyValue(date || '', this.isDateOnly ? '' : (time || ''))
      this.pickerVisible = false
    },
    pickNowFromPicker() {
      const now = new Date()
      const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
      const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      this.applyValue(date, this.isDateOnly ? '' : time)
      this.pickerVisible = false
    },
    applyValue(date, time) {
      this.pickerValue = `${date} ${time}`.trim()
      this.$emit('date-change', date)
      this.$emit('time-change', time)
      this.$emit('change', { date, time })
    },
    combineDateTime(dateRaw, timeRaw) {
      const now = new Date()
      const fallbackDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
      const fallbackTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      const date = this.normalizeDate(dateRaw, fallbackDate)
      const time = this.normalizeTime(timeRaw, fallbackTime)
      return `${date} ${time}`
    },
    normalizeDateTime(raw, fallback) {
      const fallbackDate = this.normalizeDate(String(fallback || '').slice(0, 10), '')
      const fallbackTime = this.normalizeTime(String(fallback || '').slice(11, 16), '')
      if (raw == null) return `${fallbackDate} ${fallbackTime}`.trim()
      if (typeof raw === 'number' || /^[0-9]{10,13}$/.test(String(raw))) {
        const date = new Date(Number(raw))
        if (!Number.isNaN(date.getTime())) {
          const d = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
          const t = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
          return `${d} ${t}`
        }
      }
      const text = String(raw).trim().replace('T', ' ')
      const dateMatch = text.match(/(\d{4}-\d{2}-\d{2})/)
      const timeMatch = text.match(/(\d{2}:\d{2})/)
      const date = dateMatch ? dateMatch[1] : fallbackDate
      const time = timeMatch ? timeMatch[1] : fallbackTime
      return `${date} ${time}`.trim()
    },
    normalizeDate(raw, fallback = '') {
      if (raw == null) return fallback
      if (typeof raw === 'number' || /^[0-9]{10,13}$/.test(String(raw))) {
        const date = new Date(Number(raw))
        if (!Number.isNaN(date.getTime())) {
          return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
        }
      }
      const text = String(raw).trim().replace(/\//g, '-')
      const matched = text.match(/(\d{4}-\d{2}-\d{2})/)
      return matched ? matched[1] : (text || fallback)
    },
    normalizeTime(raw, fallback = '') {
      if (raw == null) return fallback
      if (typeof raw === 'number' || /^[0-9]{10,13}$/.test(String(raw))) {
        const date = new Date(Number(raw))
        if (!Number.isNaN(date.getTime())) {
          return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
        }
      }
      const text = String(raw).trim()
      const matched = text.match(/(\d{2}:\d{2})/)
      return matched ? matched[1] : (text || fallback)
    }
  }
}
</script>

<style lang="scss">
.date-time-selector {
  --dt-border: #dde4d6;
  --dt-surface: #f6f8f4;
  --dt-text: #1f2a21;
  --dt-sub: #62705f;
  --dt-accent: #73ae52;
  --dt-accent-soft: #edf7e7;
  --td-brand-color: #73ae52;
  --td-brand-color-active: #5e9240;
  --td-brand-color-light: #edf7e7;
}

.dt-label {
  display: block;
  font-size: 24rpx;
  color: var(--dt-sub);
  margin-bottom: 10rpx;
}

.picker-cell {
  border-radius: 14rpx;
  overflow: hidden;
  border: 1rpx solid var(--dt-border);
  background: var(--dt-surface);
  box-shadow: none;
}

.picker-cell .t-cell {
  min-height: 76rpx;
  padding-top: 14rpx;
  padding-bottom: 14rpx;
}

.picker-footer {
  padding: 12rpx 18rpx 16rpx;
  border-top: 1rpx solid var(--dt-border);
  display: flex;
  justify-content: flex-end;
  background: var(--dh-color-surface);
}

.now-btn {
  --td-button-text-color: var(--dt-accent);
  --td-button-text-active-bg-color: var(--dt-accent-soft);
  --td-button-text-active-color: var(--dh-color-brand-press);
  border-radius: 999rpx;
  padding: 0 12rpx;
  font-weight: 600;
}

.date-time-selector.is-elder {
  .dt-label,
  .picker-cell .t-cell__title-text {
    font-size: 30rpx;
  }

  .picker-cell {
    border-radius: 18rpx;
  }

  .picker-cell .t-cell {
    min-height: 92rpx;
    padding-top: 18rpx;
    padding-bottom: 18rpx;
  }

  .picker-footer {
    padding: 16rpx 20rpx 20rpx;
  }

  .now-btn {
    min-height: 64rpx;
    font-size: 28rpx;
    padding: 0 18rpx;
  }
}
</style>
