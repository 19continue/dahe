<template>
  <view class="search-assist" :class="{ 'is-elder': elderMode }">
    <view class="search-assist-anchor" @tap="handleAnchorTap">
      <t-search
        ref="searchBar"
        class="search-assist-bar"
        :value="displayValue"
        :focus="inputFocused"
        :placeholder="placeholder"
        :result-list="[]"
        shape="round"
        clearable
        action="搜索"
        confirm-type="search"
        @change="handleChange"
        @submit="handleSubmit"
        @action-click="handleActionClick"
        @focus="handleFocus"
        @blur="handleBlur"
        @clear="handleClear"
      />
    </view>

    <view
      v-if="showDropdownPanel"
      class="search-assist-dropdown"
      :style="dropdownInlineStyle"
      @touchstart.stop="handleDropdownTouchStart"
      @touchend.stop="handleDropdownTouchEnd"
      @tap.stop
    >
      <scroll-view
        scroll-y
        class="search-assist-list"
        :style="dropdownListInlineStyle"
        :show-scrollbar="false"
      >
        <view v-if="showHistoryPanel" class="search-assist-history-head">
          <text class="search-assist-history-title">最近搜索</text>
          <view class="search-assist-history-actions">
            <view
              v-if="historyEditing"
              class="search-assist-history-action"
              @touchstart.stop="handleHistoryControlTouchStart"
              @touchend.stop="handleHistoryClear"
            >
              <text class="search-assist-history-action-text">清空</text>
            </view>
            <view
              v-if="historyEditing"
              class="search-assist-history-action"
              @touchstart.stop="handleHistoryControlTouchStart"
              @touchend.stop="handleHistoryDone"
            >
              <text class="search-assist-history-action-text">完成</text>
            </view>
            <view
              v-else
              class="search-assist-history-action search-assist-history-action-icon"
              @touchstart.stop="handleHistoryControlTouchStart"
              @touchend.stop="handleHistoryEditStart"
            >
              <t-icon name="delete-1-filled" size="32rpx" />
            </view>
          </view>
        </view>
        <view
          v-for="item in dropdownItems"
          :key="item.key"
          class="search-assist-option"
          hover-class="search-assist-option-hover"
        >
          <view class="search-assist-option-row">
            <view
              class="search-assist-option-main"
              @touchstart.stop="handleSuggestionTouchStart(item)"
              @touchend.stop="handleSuggestionTouchEnd(item)"
            >
              <t-tag
                class="search-assist-option-type"
                size="small"
                theme="default"
                variant="light"
              >
                {{ item.typeLabel }}
              </t-tag>
              <text
                class="search-assist-option-label"
                :class="{ 'is-history-editing': historyEditing && item.__history }"
              >
                <text
                  v-for="(segment, index) in resolveHighlightSegments(item)"
                  :key="`${item.key}-segment-${index}`"
                  :class="{ 'search-assist-option-highlight': segment.highlight }"
                >
                  {{ segment.text }}
                </text>
              </text>
            </view>
            <view
              v-if="item.__history && historyEditing"
              class="search-assist-option-remove"
              @touchstart.stop="handleHistoryControlTouchStart"
              @touchend.stop="handleHistoryRemove(item)"
            >
              <t-icon name="close-circle-filled" size="30rpx" />
            </view>
          </view>
        </view>
        <view v-if="showSuggestionPanel && !hasSuggestions" class="search-assist-empty">
          <text class="search-assist-empty-title">没有匹配提示</text>
          <text class="search-assist-empty-desc">可继续输入，或直接点击“搜索”。</text>
        </view>
      </scroll-view>
    </view>

    <view v-if="showFeedback" class="search-assist-feedback">
      <text class="search-assist-feedback-text">{{ helperText }}</text>
    </view>
  </view>
</template>

<script>
import { getWindowInfoSafe } from '../utils/system-info'

export default {
  name: 'SearchAssist',
  props: {
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '请输入关键词'
    },
    helperText: {
      type: String,
      default: ''
    },
    suggestions: {
      type: Array,
      default: () => []
    },
    historyItems: {
      type: Array,
      default: () => []
    },
    elderMode: {
      type: Boolean,
      default: false
    },
    minSuggestionLength: {
      type: Number,
      default: 1
    }
  },
  data() {
    return {
      focused: false,
      inputFocused: false,
      blurTimer: null,
      dropdownPressing: false,
      displayValue: String(this.value || ''),
      panelDismissedByScroll: false,
      historyEditing: false,
      ignoreBlurUntil: 0,
      dropdownLayout: {
        left: 0,
        width: 0,
        top: null,
        bottom: null,
        maxHeight: 360
      }
    }
  },
  computed: {
    normalizedDisplayValue() {
      return String(this.displayValue || '').trim()
    },
    hasKeyword() {
      return this.normalizedDisplayValue.length > 0
    },
    canShowSuggestions() {
      return this.normalizedDisplayValue.length >= Math.max(1, Number(this.minSuggestionLength) || 1)
    },
    hasSuggestions() {
      return Array.isArray(this.suggestions) && this.suggestions.length > 0
    },
    normalizedHistoryItems() {
      return (Array.isArray(this.historyItems) ? this.historyItems : [])
        .map((item, index) => {
          const value = String(item && item.value != null ? item.value : item).trim()
          if (!value) return null
          return {
            key: `history-${index}-${value}`,
            typeLabel: '历史',
            label: value,
            value,
            __history: true
          }
        })
        .filter(Boolean)
    },
    hasHistoryItems() {
      return this.normalizedHistoryItems.length > 0
    },
    dropdownReady() {
      return Number(this.dropdownLayout.width || 0) > 0
    },
    showSuggestionPanel() {
      return this.focused && this.canShowSuggestions && this.dropdownReady && !this.panelDismissedByScroll
    },
    showHistoryPanel() {
      return this.focused && !this.hasKeyword && this.hasHistoryItems && this.dropdownReady && !this.panelDismissedByScroll
    },
    showDropdownPanel() {
      return this.showSuggestionPanel || this.showHistoryPanel
    },
    dropdownItems() {
      return this.showHistoryPanel ? this.normalizedHistoryItems : this.suggestions
    },
    showFeedback() {
      return !this.focused && this.hasKeyword && String(this.helperText || '').trim()
    },
    dropdownInlineStyle() {
      const style = [
        `left:${Number(this.dropdownLayout.left || 0)}px`,
        `width:${Number(this.dropdownLayout.width || 0)}px`,
        `max-height:${Number(this.dropdownLayout.maxHeight || 360)}px`
      ]
      if (this.dropdownLayout.top !== null && this.dropdownLayout.top !== undefined) {
        style.push(`top:${Number(this.dropdownLayout.top)}px`)
      }
      if (this.dropdownLayout.bottom !== null && this.dropdownLayout.bottom !== undefined) {
        style.push(`bottom:${Number(this.dropdownLayout.bottom)}px`)
      }
      return style.join(';')
    },
    dropdownListInlineStyle() {
      return `max-height:${Number(this.dropdownLayout.maxHeight || 360)}px`
    }
  },
  watch: {
    value(nextValue) {
      const safeValue = String(nextValue || '').trim()
      if (safeValue !== this.displayValue) {
        this.displayValue = safeValue
        this.syncSearchBarValue(safeValue)
      }
      this.scheduleDropdownPosition()
    },
    suggestions() {
      this.scheduleDropdownPosition()
    },
    historyItems() {
      this.scheduleDropdownPosition()
    },
    focused() {
      this.scheduleDropdownPosition()
    },
    showHistoryPanel(nextValue) {
      if (!nextValue) {
        this.historyEditing = false
      }
    }
  },
  beforeDestroy() {
    this.clearBlurTimer()
  },
  methods: {
    clearBlurTimer() {
      if (!this.blurTimer) return
      clearTimeout(this.blurTimer)
      this.blurTimer = null
    },
    syncSearchBarValue(nextValue) {
      this.$nextTick(() => {
        const searchBar = this.$refs.searchBar
        if (!searchBar) return
        const safeValue = String(nextValue || '')
        searchBar.dataValue = safeValue
        if (typeof searchBar.setData === 'function') {
          searchBar.setData({
            dataValue: safeValue,
            showClearIcon: !!safeValue
          })
        }
        if (searchBar.$scope && typeof searchBar.$scope.setData === 'function') {
          searchBar.$scope.setData({
            dataValue: safeValue,
            showClearIcon: !!safeValue
          })
        }
        if (typeof searchBar.updateClearIconVisible === 'function') {
          searchBar.updateClearIconVisible(true)
        }
      })
    },
    handleChange(context) {
      this.displayValue = String(context && context.value ? context.value : '').trim()
      this.panelDismissedByScroll = false
      this.historyEditing = false
      this.$emit('change', context)
    },
    handleSubmit(context) {
      this.dismissPanel()
      this.$emit('submit', { value: this.displayValue })
    },
    handleActionClick() {
      this.dismissPanel()
      this.$emit('submit', { value: this.displayValue })
    },
    handleFocus() {
      this.clearBlurTimer()
      this.focused = true
      this.inputFocused = true
      this.panelDismissedByScroll = false
      this.scheduleDropdownPosition()
    },
    handleBlur() {
      this.clearBlurTimer()
      this.blurTimer = setTimeout(() => {
        if (this.dropdownPressing || Date.now() < this.ignoreBlurUntil) return
        this.focused = false
        this.inputFocused = false
      }, 180)
    },
    handleClear() {
      this.clearBlurTimer()
      this.dropdownPressing = false
      this.displayValue = ''
      this.historyEditing = false
      this.dismissPanel()
      this.$emit('clear')
    },
    handleDropdownTouchStart() {
      this.clearBlurTimer()
      this.dropdownPressing = true
    },
    handleDropdownTouchEnd() {
      setTimeout(() => {
        this.dropdownPressing = false
      }, 0)
    },
    handleHistoryControlTouchStart() {
      this.clearBlurTimer()
      this.dropdownPressing = true
      this.ignoreBlurUntil = Date.now() + 360
    },
    handleSuggestionTouchStart() {
      this.clearBlurTimer()
      this.dropdownPressing = true
    },
    handleSuggestionTouchEnd(item) {
      if (this.historyEditing && item && item.__history) {
        setTimeout(() => {
          this.dropdownPressing = false
        }, 0)
        return
      }
      this.handleSuggestionTap(item)
    },
    handleSuggestionTap(item) {
      const nextValue = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      this.clearBlurTimer()
      this.dropdownPressing = false
      this.dismissPanel()
      if (!nextValue) {
        this.$emit('suggestion-click', item)
        return
      }
      this.displayValue = nextValue
      const applySelection = () => {
        this.syncSearchBarValue(nextValue)
        this.$emit('change', { value: nextValue })
        this.$emit(item && item.__history ? 'history-click' : 'suggestion-click', item)
      }
      if (typeof uni.hideKeyboard === 'function') {
        uni.hideKeyboard({
          complete: () => {
            setTimeout(applySelection, 40)
          }
        })
        return
      }
      setTimeout(applySelection, 40)
    },
    handleAnchorTap() {
      this.panelDismissedByScroll = false
      this.focused = true
      this.inputFocused = true
      this.scheduleDropdownPosition()
    },
    handleHistoryClear() {
      this.clearBlurTimer()
      this.ignoreBlurUntil = Date.now() + 360
      this.historyEditing = false
      this.focused = true
      this.panelDismissedByScroll = false
      setTimeout(() => {
        this.dropdownPressing = false
      }, 120)
      this.$emit('history-clear')
    },
    handleHistoryRemove(item) {
      this.clearBlurTimer()
      this.ignoreBlurUntil = Date.now() + 360
      this.focused = true
      this.panelDismissedByScroll = false
      setTimeout(() => {
        this.dropdownPressing = false
      }, 120)
      this.$emit('history-remove', item)
    },
    handleHistoryEditStart() {
      this.clearBlurTimer()
      this.ignoreBlurUntil = Date.now() + 360
      this.focused = true
      this.panelDismissedByScroll = false
      this.historyEditing = true
      setTimeout(() => {
        this.dropdownPressing = false
      }, 120)
    },
    handleHistoryDone() {
      this.clearBlurTimer()
      this.ignoreBlurUntil = Date.now() + 360
      this.focused = true
      this.panelDismissedByScroll = false
      this.historyEditing = false
      setTimeout(() => {
        this.dropdownPressing = false
      }, 120)
    },
    dismissPanel() {
      this.focused = false
      this.inputFocused = false
      this.panelDismissedByScroll = false
      this.historyEditing = false
      this.dropdownLayout = {
        left: 0,
        width: 0,
        top: null,
        bottom: null,
        maxHeight: 360
      }
    },
    hidePanelOnScroll() {
      this.clearBlurTimer()
      this.dropdownPressing = false
      this.inputFocused = false
      this.panelDismissedByScroll = true
      this.historyEditing = false
      this.dropdownLayout = {
        left: 0,
        width: 0,
        top: null,
        bottom: null,
        maxHeight: 360
      }
    },
    scheduleDropdownPosition() {
      const canOpen = this.hasKeyword ? this.canShowSuggestions : this.hasHistoryItems
      if (!this.focused || !canOpen || this.panelDismissedByScroll) return
      this.$nextTick(() => {
        this.updateDropdownPosition()
      })
    },
    updateDropdownPosition() {
      const query = uni.createSelectorQuery().in(this.$scope || this)
      query.select('.search-assist-anchor').boundingClientRect()
      query.exec((result) => {
        const rect = Array.isArray(result) ? result[0] : null
        if (!rect || !rect.width) return
        const systemInfo = getWindowInfoSafe()
        const windowHeight = Number(systemInfo && systemInfo.windowHeight) || 667
        const spacing = 8
        const minHeight = 156
        const preferHeight = 360
        const availableBelow = Math.max(0, windowHeight - rect.bottom - 16)
        const availableAbove = Math.max(0, rect.top - 16)
        const openUpward = availableBelow < minHeight && availableAbove > availableBelow
        const nextLayout = {
          left: rect.left,
          width: rect.width,
          top: null,
          bottom: null,
          maxHeight: Math.max(minHeight, Math.min(preferHeight, openUpward ? availableAbove - spacing : availableBelow - spacing))
        }
        if (openUpward) {
          nextLayout.bottom = Math.max(0, windowHeight - rect.top + spacing)
        } else {
          nextLayout.top = rect.bottom + spacing
        }
        this.dropdownLayout = nextLayout
      })
    },
    resolveHighlightSegments(item) {
      const label = String(item && item.label ? item.label : '').trim()
      const keyword = String(this.normalizedDisplayValue || '').trim()
      if (!label) {
        return []
      }
      const serverRanges = this.normalizeHighlightRanges(item && item.highlightRanges, label.length)
      if (serverRanges.length) {
        const segments = []
        let cursor = 0
        serverRanges.forEach((range) => {
          if (range.start > cursor) {
            segments.push({
              text: label.slice(cursor, range.start),
              highlight: false
            })
          }
          if (range.end > range.start) {
            segments.push({
              text: label.slice(range.start, range.end),
              highlight: true
            })
          }
          cursor = range.end
        })
        if (cursor < label.length) {
          segments.push({
            text: label.slice(cursor),
            highlight: false
          })
        }
        return segments
      }
      if (!keyword) {
        return [{ text: label, highlight: false }]
      }
      const lowerLabel = label.toLowerCase()
      const lowerKeyword = keyword.toLowerCase()
      const segments = []
      let cursor = 0
      const flags = new Array(label.length).fill(false)
      while (cursor < label.length) {
        const nextIndex = lowerLabel.indexOf(lowerKeyword, cursor)
        if (nextIndex < 0) {
          break
        }
        for (let i = nextIndex; i < nextIndex + keyword.length; i += 1) {
          flags[i] = true
        }
        cursor = nextIndex + keyword.length
      }
      if (!flags.some(Boolean)) {
        this.applyBrokenHighlight(flags, lowerLabel, lowerKeyword)
      }
      let buffer = ''
      let current = flags[0]
      for (let i = 0; i < label.length; i += 1) {
        if (i === 0) {
          buffer = label[i]
          continue
        }
        if (flags[i] === current) {
          buffer += label[i]
          continue
        }
        segments.push({
          text: buffer,
          highlight: current
        })
        buffer = label[i]
        current = flags[i]
      }
      if (buffer) {
        segments.push({
          text: buffer,
          highlight: current
        })
      }
      return segments.length ? segments : [{ text: label, highlight: false }]
    },
    applyBrokenHighlight(flags, label, keyword) {
      if (!Array.isArray(flags) || !label || !keyword) {
        return
      }
      let cursor = 0
      let matched = 0
      for (let i = 0; i < keyword.length; i += 1) {
        const char = keyword[i]
        const nextIndex = label.indexOf(char, cursor)
        if (nextIndex < 0) {
          continue
        }
        flags[nextIndex] = true
        cursor = nextIndex + 1
        matched += 1
      }
      if (!matched) {
        return
      }
    },
    normalizeHighlightRanges(ranges, labelLength) {
      if (!Array.isArray(ranges) || !labelLength) {
        return []
      }
      const normalized = ranges
        .map((range) => ({
          start: Math.max(0, Math.min(labelLength, Number(range && range.start))),
          end: Math.max(0, Math.min(labelLength, Number(range && range.end)))
        }))
        .filter((range) => Number.isFinite(range.start) && Number.isFinite(range.end) && range.end > range.start)
        .sort((left, right) => left.start - right.start)
      if (!normalized.length) {
        return []
      }
      const merged = [normalized[0]]
      for (let i = 1; i < normalized.length; i += 1) {
        const current = normalized[i]
        const last = merged[merged.length - 1]
        if (current.start <= last.end) {
          last.end = Math.max(last.end, current.end)
          continue
        }
        merged.push(current)
      }
      return merged
    }
  }
}
</script>

<style lang="scss">
.search-assist {
  display: flex;
  flex-direction: column;
  --dh-search-bg-color: #ffffff;
  --dh-search-placeholder-color: #98a394;
  --dh-search-text-color: #233021;
  --dh-search-height: 84rpx;
  --dh-search-radius: 18rpx;
  --dh-search-font-size: 28rpx;
}

.search-assist-anchor {
  position: relative;
  z-index: 1;
}

.search-assist-bar {
  position: relative;
  z-index: 32;
  --td-search-bg-color: var(--dh-search-bg-color);
  --td-search-placeholder-color: var(--dh-search-placeholder-color);
  --td-search-text-color: var(--dh-search-text-color);
  --td-search-height: var(--dh-search-height);
  --td-search-square-radius: var(--dh-search-radius);
  --td-search-font-size: var(--dh-search-font-size);
  --td-search-action-color: #5d8a45;
  border-radius: var(--dh-search-radius);
}

.search-assist-dropdown {
  position: fixed;
  z-index: 12060;
  border: 1rpx solid #e5ebe1;
  border-radius: 14rpx;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 18rpx 40rpx rgba(36, 48, 34, 0.1);
}

.search-assist-list {
  max-height: inherit;
  scrollbar-width: none;
}

.search-assist-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  padding: 18rpx 24rpx 10rpx;
  border-bottom: 1rpx solid #edf1ea;
  background: #fbfcfa;
}

.search-assist-history-title {
  font-size: 22rpx;
  line-height: 1.4;
  color: #7a8674;
  font-weight: 600;
}

.search-assist-history-actions {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}

.search-assist-history-action {
  min-width: 56rpx;
  height: 48rpx;
  padding: 0 12rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #5d8a45;
}

.search-assist-history-action-icon {
  width: 56rpx;
  padding: 0;
  background: #f1f6ee;
}

.search-assist-history-action-text {
  font-size: 22rpx;
  line-height: 1.4;
  color: inherit;
}

.search-assist-list::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.search-assist-option {
  padding: 0 12rpx;
  background: #ffffff;
}

.search-assist-option + .search-assist-option {
  border-top: 1rpx solid #edf1ea;
}

.search-assist-option-hover {
  background: #f6f8f4;
}

.search-assist-option-row {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding-left: 14rpx;
  padding-right: 12rpx;
}

.search-assist-option-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.search-assist-option-type {
  flex-shrink: 0;
  margin-right: 12rpx;
  --td-tag-default-light-color: #f6f8f4;
  --td-tag-default-color: #5a6d51;
  border-radius: 6rpx;
}

.search-assist-option-label {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  line-height: 1.42;
  color: #243022;
  font-weight: 600;
}

.search-assist-option-label.is-history-editing {
  color: #65705f;
}

.search-assist-option-highlight {
  color: #2f7d45;
}

.search-assist-option-remove {
  width: 56rpx;
  height: 56rpx;
  margin-left: 8rpx;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #9aa591;
  flex-shrink: 0;
}

.search-assist-empty {
  padding: 22rpx 26rpx 24rpx;
}

.search-assist-empty-title {
  display: block;
  font-size: 26rpx;
  line-height: 1.4;
  color: #31402f;
  font-weight: 600;
}

.search-assist-empty-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #74806f;
}

.search-assist-feedback {
  margin-top: 8rpx;
}

.search-assist-feedback-text {
  display: block;
  font-size: 22rpx;
  color: #62705f;
  line-height: 1.45;
}

.search-assist.is-elder {
  --dh-search-height: 98rpx;
  --dh-search-font-size: 32rpx;
  --dh-search-radius: 22rpx;

  .search-assist-dropdown {
    border-radius: 18rpx;
  }

  .search-assist-history-title,
  .search-assist-history-action-text {
    font-size: 28rpx;
  }

  .search-assist-history-action {
    min-width: 68rpx;
    height: 60rpx;
    padding: 0 16rpx;
  }

  .search-assist-option-row {
    min-height: 102rpx;
    padding-left: 18rpx;
  }

  .search-assist-option-label {
    font-size: 32rpx;
  }

  .search-assist-option-type {
    margin-right: 14rpx;
  }

  .search-assist-option-remove {
    width: 68rpx;
    height: 68rpx;
  }

  .search-assist-feedback-text {
    font-size: 30rpx;
  }

  .search-assist-empty-title {
    font-size: 30rpx;
  }

  .search-assist-empty-desc {
    font-size: 28rpx;
  }
}
</style>
