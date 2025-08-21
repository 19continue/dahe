<template>
  <page-meta :page-style="pageMetaStyle" />
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="record-navbar"
      :title="recordId ? '编辑农事记录' : '新建农事记录'"
      :fixed="true"
      :safe-area-inset-top="true"
      :elder-mode="elderMode"
      left-arrow
      @go-back="goBack"
    />

    <view v-if="isProgressPinned" class="progress-fixed-shell" :style="{ top: `${stickyOffsetTop}px` }">
      <view class="progress-card pinned">
        <view class="progress-head">
          <view class="progress-stat-left">
            <text v-if="submitReady" class="progress-ready-text">待提交</text>
            <template v-else>
              <text class="progress-stat-label">必填未完成：</text>
              <text class="progress-stat-value">{{ requiredMissingCount }}项</text>
            </template>
          </view>
          <view class="progress-stat-right">
            <text class="progress-stat-label">已补充：</text>
            <text class="progress-stat-plain">{{ optionalFilledCount }}项</text>
          </view>
        </view>
        <view class="record-steps-pinned">
          <view
            v-for="(item, index) in editProgressSteps"
            :key="`pinned-${item.key}`"
            class="record-steps-pinned-item"
            :class="pinnedStepItemClass(item.key)"
            @tap="onProgressStepTap(item.key)"
          >
            <text class="record-steps-pinned-label">{{ index + 1 }} {{ item.label }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="form-shell">
      <scroll-view
        scroll-y
        class="form-scroll"
        :show-scrollbar="false"
        :scroll-top="formScrollTargetTop"
        :scroll-with-animation="true"
        @scroll="onFormScroll"
      >
        <view class="form">
          <view v-if="isProgressPinned" id="anchor-progress" class="progress-placeholder" :style="{ height: `${progressStickyHeight}px` }"></view>
          <view v-else class="progress-sticky-shell" id="anchor-progress">
            <view class="progress-card">
              <view class="progress-head">
                <view class="progress-stat-left">
                  <text v-if="submitReady" class="progress-ready-text">待提交</text>
                  <template v-else>
                    <text class="progress-stat-label">必填未完成：</text>
                    <text class="progress-stat-value">{{ requiredMissingCount }}项</text>
                  </template>
                </view>
                <view class="progress-stat-right">
                  <text class="progress-stat-label">已补充：</text>
                  <text class="progress-stat-plain">{{ optionalFilledCount }}项</text>
                </view>
              </view>

              <t-steps class="record-steps" :current="currentProgressStep - 1" @change="onStepsChange">
                <t-step-item v-for="item in editProgressSteps" :key="item.key" :title="item.label" />
              </t-steps>
            </view>
          </view>

          <view class="section-block" id="anchor-field">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">1</view>
              <text class="section-title">选择田块</text>
            </view>
          </view>
          <view class="pick-card" @tap="openFieldSelectorPopup">
            <view class="pick-main">
              <view class="field-cover">
                <dh-smart-image
                  class="field-cover-image"
                  :src="selectedFieldCoverUrl"
                  :preview-src="selectedFieldCoverPreviewUrl"
                  mode="aspectFill"
                  :lazy-load="true"
                  loading-text="田块加载中..."
                  :empty-text="selectedFieldAreaLabel || '田块'"
                  error-text="图片异常"
                />
              </view>
              <view class="pick-info">
                <text v-if="fieldInitializing" class="pick-loading-text">正在自动识别田块，请稍候...</text>
                <view class="pick-title-row">
                  <text class="pick-title">{{ fieldInitializing ? '田块加载中' : (selectedField ? selectedField.name : '请选择田块') }}</text>
                  <text class="pick-title-relation" v-if="selectedFieldRelationText">（{{ selectedFieldRelationText }}）</text>
                </view>
                <text class="pick-meta">{{ fieldInitializing ? '系统会结合当前位置尝试自动选中当前田块' : (selectedField ? selectedFieldSubText : '请选择一个作业田块') }}</text>
                <text class="pick-meta" v-if="selectedField && selectedFieldLocationText">{{ selectedFieldLocationText }}</text>
                <view class="pick-tags" v-if="selectedFieldCropTags.length">
                  <t-tag
                    v-for="label in selectedFieldCropTags"
                    :key="`selected-${label}`"
                    size="small"
                    theme="success"
                    variant="light"
                  >
                    {{ label }}
                  </t-tag>
                </view>
              </view>
            </view>
            <t-button class="pick-change-btn" theme="primary" variant="text" :size="elderMode ? 'medium' : 'small'" :loading="fieldInitializing" @click.stop="openFieldSelectorPopup">
              {{ fieldInitializing ? '识别中' : (selectedField ? '更换' : '选择') }}
            </t-button>
          </view>
          <field-selector
            ref="fieldSelectorRef"
            class="selector-anchor"
            :hide-trigger="true"
            :value="selectedField ? selectedField.id : null"
            :selected-field-info="selectedField"
            :highlight-matched="true"
            :include-disabled="forceIncludeDisabledFields"
            :elder-mode="elderMode"
            title="选择田块"
            placeholder="请选择田块"
            @popup-visible-change="onSelectorPopupVisibleChange"
            @change="onFieldSelectorChange"
          />
        </view>

          <view class="section-block" id="anchor-step">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">2</view>
              <text class="section-title">选择作业步骤</text>
            </view>
          </view>
          <view class="pick-card step-pick-card" :class="{ disabled: !selectedField }" @tap="openStepSelectorPopup">
            <view class="pick-info">
              <text class="pick-meta">{{ stepCardHintText }}</text>
              <text class="pick-title">{{ selectedStep ? (selectedStep.stepName || '已选择作业步骤') : (selectedField ? '请选择作业步骤' : '请先选择田块') }}</text>
              <text class="pick-meta" v-if="selectedStepStageText">{{ selectedStepStageText }}</text>
            </view>
            <t-button
              class="pick-change-btn"
              theme="primary"
              variant="text"
              :size="elderMode ? 'medium' : 'small'"
              :disabled="!selectedField"
              @click.stop="openStepSelectorPopup"
            >
              {{ selectedStep ? '更换' : '选择' }}
            </t-button>
          </view>
          <step-selector
            ref="stepSelectorRef"
            class="selector-anchor"
            :hide-trigger="true"
            :loading="processLoading"
            :segments="segmentOptions"
            :segment-value="selectedSegmentKey"
            :steps="visibleStepOptions"
            :value="selectedStepId"
            :disabled="!selectedField"
            :elder-mode="elderMode"
            @popup-visible-change="onSelectorPopupVisibleChange"
            @segment-change="onSegmentSelect"
            @change="onStepSelect"
          />
          <view class="warn-tip" v-if="selectedStep && selectedStep.requirementDesc">
            <text class="warn-text">{{ selectedStep.requirementDesc }}</text>
          </view>
        </view>

          <view class="section-block" id="anchor-params">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">3</view>
              <text class="section-title">填写参数</text>
            </view>
            <text class="section-meta" v-if="dynamicFields.length">{{ dynamicRequiredMissingCount > 0 ? ('还需 ' + dynamicRequiredMissingCount + ' 项') : '已填写完整' }}</text>
          </view>
          <view class="card section-card" v-if="dynamicFields.length">
            <view v-for="item in dynamicFields" :key="item.key" class="dynamic-item dynamic-field-card" :class="{ 'is-required': item.required }">
              <view class="dynamic-label-row">
                <view v-if="item.required" class="required-dot"></view>
                <text class="dynamic-label" :class="{ 'is-required': item.required }">{{ item.label }}</text>
              </view>
              <view
                v-if="item.type === 'date' || item.type === 'time' || item.type === 'select'"
                class="dynamic-picker-trigger"
                @click="onDynamicPickerClick(item)"
              >
                <t-input
                  class="td-input td-input-picker"
                  :class="{ 'is-error': !!dynamicFieldErrors[item.key] }"
                  readonly
                  :placeholder="item.placeholder || ('请选择' + item.label)"
                  :value="dynamicDisplayValue(item)"
                />
                <view class="dynamic-picker-arrow">
                  <t-icon name="chevron-right" size="32rpx" />
                </view>
              </view>
              <view v-else-if="item.type === 'location'" class="location-input-wrap">
                <t-input
                  class="td-input td-input-editable"
                  :class="{ 'is-error': !!dynamicFieldErrors[item.key] }"
                  type="text"
                  :placeholder="item.placeholder || ('请输入' + item.label)"
                  :value="dynamicValues[item.key] || ''"
                  :confirm-hold="true"
                  :hold-keyboard="true"
                  clearable
                  @change="onDynamicChange(item.key, $event)"
                />
                <view class="locate-btn-wrap">
                  <t-button theme="primary" variant="outline" :size="elderMode ? 'medium' : 'small'" @click="fillDynamicLocation(item.key)">定位填充</t-button>
                </view>
              </view>
              <t-input
                v-else-if="item.type !== 'textarea'"
                class="td-input td-input-editable"
                :class="{ 'is-error': !!dynamicFieldErrors[item.key] }"
                :type="resolveInputType(item.type)"
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :value="dynamicValues[item.key] || ''"
                :confirm-hold="true"
                :hold-keyboard="true"
                clearable
                @change="onDynamicChange(item.key, $event)"
              />
              <t-textarea
                v-else
                class="td-textarea td-input-editable"
                :class="{ 'is-error': !!dynamicFieldErrors[item.key] }"
                :placeholder="item.placeholder || ('请输入' + item.label)"
                :value="dynamicValues[item.key] || ''"
                :confirm-hold="true"
                :hold-keyboard="true"
                :maxlength="300"
                indicator
                @change="onDynamicChange(item.key, $event)"
              />
              <text v-if="dynamicFieldErrors[item.key]" class="field-error">{{ dynamicFieldErrors[item.key] }}</text>
            </view>
          </view>
          <view class="card section-card empty-card" v-else>当前步骤暂无必填参数</view>
        </view>

          <view class="section-block" id="anchor-submit">
          <view class="section-head">
            <view class="section-title-wrap">
              <view class="section-index">4</view>
              <text class="section-title">确认与补充信息</text>
            </view>
            <text class="section-meta">已补充 {{ optionalFilledCount }} 项</text>
          </view>
          <view class="card section-card merged-section">
            <view class="sub-block operator-block">
              <text class="dynamic-label">执行人员</text>
              <view v-if="lockOperator" class="readonly readonly-display">{{ operatorName || '当前登录账号' }}</view>
              <t-input
                v-else
                class="td-input td-input-editable"
                :value="operatorName"
                placeholder="请输入执行人员"
                :confirm-hold="true"
                :hold-keyboard="true"
                clearable
                @change="onFormInput('operatorName', $event)"
              />
            </view>

            <view class="sub-block">
              <view class="sub-block-head">
                <text class="sub-block-title">作业时间</text>
              </view>
              <date-time-selector
                label=""
                :date="date"
                :time="time"
                :elder-mode="elderMode"
                @date-change="onDateSelectorChange"
                @time-change="onTimeSelectorChange"
              />
            </view>

            <view class="sub-block">
              <view class="sub-block-head weather-head">
                <text class="sub-block-title">现场天气</text>
				<view class="weather-refresh-btn">
					<t-button
					  theme="primary"
					  variant="outline"
					  :size="elderMode ? 'medium' : 'small'"
					  @click="autoFillWeather"
					>
					  自动获取
					</t-button>
				</view>
              </view>
              <view class="weather-brief">
                <text class="weather-brief-text">{{ weatherBriefText }}</text>
              </view>
              <view class="weather-detail">
                <view class="weather-main-input weather-edit-card">
                  <text class="dynamic-label">天气</text>
                  <t-input class="td-input td-input-editable" :value="weather" placeholder="如：多云" :confirm-hold="true" :hold-keyboard="true" clearable @change="onFormInput('weather', $event)" />
                </view>
                <view class="weather-display-grid weather-readonly-grid">
                  <view class="weather-display-item weather-display-item-readonly">
                    <text class="weather-display-label">天气位置</text>
                    <text class="weather-display-value">{{ weatherLocation || '未获取' }}</text>
                  </view>
                  <view class="weather-display-item weather-display-item-readonly">
                    <text class="weather-display-label">温度</text>
                    <text class="weather-display-value">{{ temperature ? `${temperature}°C` : '未获取' }}</text>
                  </view>
                  <view class="weather-display-item weather-display-item-readonly">
                    <text class="weather-display-label">湿度</text>
                    <text class="weather-display-value">{{ humidity ? `${humidity}%` : '未获取' }}</text>
                  </view>
                  <view class="weather-display-item weather-display-item-readonly">
                    <text class="weather-display-label">风向风力</text>
                    <text class="weather-display-value">{{ weatherWindText }}</text>
                  </view>
                  <view class="weather-display-item weather-display-item-full weather-display-item-readonly">
                    <text class="weather-display-label">发布时间</text>
                    <text class="weather-display-value">{{ weatherReportTime || '未获取' }}</text>
                  </view>
                </view>
              </view>
            </view>

            <view class="sub-block">
              <view class="sub-block-head">
                <text class="sub-block-title">现场图片（可选）</text>
                <text class="sub-tip">最多 6 张</text>
              </view>
              <view v-if="recordImageCards.length" class="record-photo-grid">
                <view
                  v-for="(item, idx) in recordImageCards"
                  :key="item.cardKey"
                  class="record-photo-item"
                  :class="{ 'is-pending': item.isReviewBlocked }"
                >
                  <dh-smart-image
                    v-if="item.previewable"
                    class="record-photo-img"
                    :src="item.previewUrl"
                    :preview-src="item.previewSource"
                    mode="aspectFill"
                    :lazy-load="true"
                    loading-text="图片加载中..."
                    empty-text="暂无图片"
                    error-text="图片异常"
                    @tap="previewRecordImage(item.previewUrl)"
                  />
                  <view v-else class="record-photo-img record-photo-placeholder">
                    <text class="record-photo-placeholder-state">{{ item.reviewStatusText }}</text>
                    <text class="record-photo-placeholder-tip">{{ item.hintMessage }}</text>
                  </view>
                  <view class="record-photo-remove" @tap.stop="removeUploadedImage(idx)">
                    <t-icon name="close" size="28rpx" color="#fff" />
                  </view>
                </view>
              </view>
              <t-upload
                class="record-upload"
                :files="uploadFiles"
                :max="6"
                :media-type="['image']"
                :grid-config="{ column: 3, width: 196, height: 196 }"
                :request-method="handleRecordImageUpload"
                :preview="true"
                :remove-btn="true"
                :add-btn="canAddRecordImage"
                @select-change="onRecordUploadSelectChange"
                @update:files="onRecordUploadFilesUpdate"
                @success="onRecordUploadSuccess"
                @remove="onRecordUploadRemove"
                @fail="onRecordUploadFail"
              />
              <view class="sub-tip" v-if="imageUploading">图片上传中...</view>
            </view>

            <view class="sub-block">
              <view class="sub-block-head">
                <text class="sub-block-title">补充说明（可选）</text>
              </view>
              <t-textarea
                class="td-textarea td-input-editable"
                :value="notes"
                placeholder="请输入补充说明"
                :confirm-hold="true"
                :hold-keyboard="true"
                :maxlength="300"
                indicator
                @change="onFormInput('notes', $event)"
              />
            </view>
          </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="submit-wrap">
      <text class="submit-brief" v-if="submitSummaryLine">{{ submitSummaryLine }}</text>
      <t-button class="submit-btn" theme="primary" size="large" block :loading="submitting" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : (recordId ? '保存修改' : '提交记录') }}
      </t-button>
    </view>

    <t-picker
      :visible="dynamicSelectPickerVisible"
      :title="dynamicSelectTitle || '请选择'"
      :value="dynamicSelectPickerValue"
      @confirm="onDynamicSelectPickerConfirm"
      @cancel="dynamicSelectPickerVisible = false"
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
import { isElderMode } from '../../utils/accessibility'
import { getOperatorNameFromUser } from '../../utils/auth'
import {
  getAutoWeatherSnapshot,
  getCurrentLocation,
  formatLocationLabel
} from '../../utils/amap'
import { resolveCropVarietyLabels } from '../../utils/crop-variety'
import { markDataChanged, refreshTopics } from '../../utils/data-refresh'
import { getStatusBarHeight } from '../../utils/system-info'
import FieldSelector from '../../components/field-selector/field-selector.vue'
import StepSelector from '../../components/step-selector/step-selector.vue'
import DateTimeSelector from '../../components/date-time-selector/date-time-selector.vue'
import DhSmartImage from '../../components/dh-smart-image.vue'

const OPERATOR_STORAGE_KEY = 'dahe.v2.operatorName'
const AUTO_FIELD_MATCH_RADIUS_KM = 2

export default {
  components: {
    FieldSelector,
    StepSelector,
    DateTimeSelector,
    DhSmartImage
  },
  data() {
    const now = new Date()
    const yyyy = now.getFullYear()
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    const dd = String(now.getDate()).padStart(2, '0')
    const hh = String(now.getHours()).padStart(2, '0')
    const mi = String(now.getMinutes()).padStart(2, '0')

    return {
      selectedFieldInfo: null,
      fieldLoading: false,
      fieldInitializing: false,
      selectedCycleId: null,
      processInfo: null,
      processLoading: false,
      stepOptions: [],
      segmentOptions: [],
      selectedSegmentKey: '',
      selectedStepId: null,
      date: `${yyyy}-${mm}-${dd}`,
      time: `${hh}:${mi}`,
      operatorName: '',
      notes: '',
      weather: '',
      temperature: '',
      weatherLocation: '',
      humidity: '',
      windDirection: '',
      windPower: '',
      weatherReportTime: '',
      imageAssets: [],
      uploadFiles: [],
      imageUploading: false,
      submitting: false,
      recordId: null,
      originalFieldId: '',
      dynamicValues: {},
      dynamicFieldDefs: [],
      dynamicFieldErrors: {},
      elderMode: false,
      lockOperator: false,
      forceIncludeDisabledFields: false,
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
      dynamicDatePickerKey: '',
      stickyOffsetTop: 84,
      progressStickyHeight: 120,
      formScrollTop: 0,
      formScrollTargetTop: 0,
      progressPinned: false,
      selectorPopupVisible: false
    }
  },
  computed: {
    pageMetaStyle() {
      return 'overflow: hidden; height: 100vh;'
    },
    selectedField() {
      return this.selectedFieldInfo || null
    },
    selectedSegment() {
      if (!this.segmentOptions.length) return null
      return this.segmentOptions.find((x) => x.segmentKey === this.selectedSegmentKey) || this.segmentOptions[0]
    },
    visibleStepOptions() {
      const segmentSteps = this.selectedSegment && Array.isArray(this.selectedSegment.steps)
        ? this.selectedSegment.steps
        : null
      const rows = segmentSteps && segmentSteps.length ? segmentSteps : this.stepOptions
      return Array.isArray(rows) ? rows : []
    },
    selectedStep() {
      const rows = this.visibleStepOptions
      if (!rows.length) return null
      const picked = rows.find((x) => this.sameId(x && x.id, this.selectedStepId))
      return picked || rows[0]
    },
    editProgressSteps() {
      return [
        { key: 'field', label: '田块' },
        { key: 'step', label: '步骤' },
        { key: 'params', label: '参数' },
        { key: 'submit', label: '补充' }
      ]
    },
    selectedFieldCropTags() {
      return this.resolveFieldCropTags(this.selectedField)
    },
    selectedFieldAreaLabel() {
      if (!this.selectedField) return '田块'
      const area = this.selectedField.areaMu != null ? String(this.selectedField.areaMu).trim() : ''
      if (!area) return '田块'
      return `${area}亩`
    },
    selectedFieldCoverUrl() {
      if (!this.selectedField) return ''
      return this.resolveFieldCover(this.selectedField)
    },
    selectedFieldCoverPreviewUrl() {
      if (!this.selectedField) return ''
      return this.resolveFieldCoverPreview(this.selectedField)
    },
    selectedFieldSubText() {
      if (!this.selectedField) return ''
      const town = String(this.selectedField.town || this.selectedField.townName || this.selectedField.township || '').trim()
      const area = this.selectedField.areaMu != null ? `${this.selectedField.areaMu}亩` : ''
      const parts = []
      if (town) parts.push(town)
      if (area) parts.push(area)
      return parts.join(' · ') || '已选择田块'
    },
    selectedFieldRelationText() {
      if (!this.selectedField) return ''
      const relationText = String(this.selectedField.relationText || '').trim()
      if (Number(this.selectedField.currentMatched) === 1 || this.selectedField.currentMatched === true) {
        return relationText || '您正处于该田块中'
      }
      return ''
    },
    selectedFieldLocationText() {
      if (!this.selectedField) return ''
      return String(
        this.selectedField.locationDesc
          || this.selectedField.fullAddress
          || this.selectedField.address
          || this.selectedField.detailAddress
          || this.selectedField.formattedAddress
          || ''
      ).trim()
    },
    selectedStepStageText() {
      if (!this.selectedStep) return ''
      return this.resolveStepStageLabel(this.selectedStep, this.visibleStepOptions.findIndex((item) => this.sameId(item && item.id, this.selectedStepId)))
    },
    stepCardHintText() {
      if (!this.selectedField) return '请先选择田块'
      if (this.processLoading) return '步骤加载中...'
      if (this.selectedStep) return '已匹配步骤'
      if (!this.visibleStepOptions.length) return '暂无可用步骤'
      return '请选择作业步骤'
    },
    dynamicFields() {
      return Array.isArray(this.dynamicFieldDefs) ? this.dynamicFieldDefs : []
    },
    stepReady() {
      return !this.stepOptions.length || !!this.selectedStep
    },
    progressDoneMap() {
      const hasDate = String(this.date || '').trim()
      const hasTime = String(this.time || '').trim()
      const fieldDone = !!this.selectedField
      const stepDone = fieldDone && (!this.stepOptions.length || !!this.selectedStep)
      const paramsDone = stepDone && this.dynamicRequiredMissingCount === 0
      const submitDone = paramsDone && !!hasDate && !!hasTime
      return {
        field: fieldDone,
        step: stepDone,
        params: paramsDone,
        submit: submitDone
      }
    },
    progressCurrentKey() {
      const keys = ['field', 'step', 'params', 'submit']
      for (const key of keys) {
        if (!this.progressDoneMap[key]) return key
      }
      return 'submit'
    },
    currentProgressStep() {
      const idx = this.editProgressSteps.findIndex((item) => item.key === this.progressCurrentKey)
      return idx >= 0 ? idx + 1 : 1
    },
    submitReady() {
      return !!this.progressDoneMap.submit
    },
    recordImageCards() {
      const rows = Array.isArray(this.imageAssets) ? this.imageAssets : []
      return rows.map((item, idx) => {
        const reviewStatus = String(item && item.reviewStatus || 'approved').trim() || 'approved'
        const reviewStatusText = String(item && item.reviewStatusText || '').trim()
          || (reviewStatus === 'pending' ? '待审核' : (reviewStatus === 'rejected' ? '未通过' : '已通过'))
        const hintMessage = String(item && item.hintMessage || '').trim()
          || (reviewStatus === 'pending'
            ? '该图片正在审核中，审核通过后可查看'
            : (reviewStatus === 'rejected' ? '该图片审核未通过，请删除后重新上传' : ''))
        const previewUrl = String((item && (item.localPreviewUrl || item.fileUrl)) || '').trim()
        const previewSource = String((item && (item.localPreviewUrl || item.thumbUrl || item.previewUrl || item.thumbnailUrl || item.fileUrl)) || '').trim()
        return {
          ...item,
          cardKey: String((item && item.id) || `${reviewStatus}-${idx}`),
          reviewStatus,
          reviewStatusText,
          hintMessage,
          previewUrl,
          previewSource,
          isReviewBlocked: reviewStatus !== 'approved',
          previewable: reviewStatus === 'approved' && !!previewUrl
        }
      })
    },
    currentImageCount() {
      const uploaded = Array.isArray(this.imageAssets) ? this.imageAssets.length : 0
      const uploading = Array.isArray(this.uploadFiles) ? this.uploadFiles.length : 0
      return uploaded + uploading
    },
    canAddRecordImage() {
      return !this.imageUploading && this.currentImageCount < 6
    },
    isProgressPinned() {
      return !!this.progressPinned
    },
    dynamicRequiredMissingCount() {
      if (!Array.isArray(this.dynamicFields) || !this.dynamicFields.length) return 0
      let missing = 0
      this.dynamicFields.forEach((field) => {
        if (!field || !field.required) return
        const value = String(this.dynamicValues[field.key] || '').trim()
        if (!value) missing += 1
      })
      return missing
    },
    requiredMissingCount() {
      let count = 0
      if (!this.selectedField) count += 1
      if (this.stepOptions.length && !this.selectedStep) count += 1
      const hasDate = String(this.date || '').trim()
      const hasTime = String(this.time || '').trim()
      if (!hasDate || !hasTime) count += 1
      count += this.dynamicRequiredMissingCount
      return count
    },
    weatherHasContent() {
      const values = [
        this.weatherLocation,
        this.weather,
        this.temperature,
        this.humidity,
        this.windDirection,
        this.windPower,
        this.weatherReportTime
      ]
      return values.some((x) => String(x || '').trim())
    },
    optionalFilledCount() {
      let count = 0
      if (this.weatherHasContent) count += 1
      if (Array.isArray(this.imageAssets) && this.imageAssets.length) count += 1
      if (String(this.notes || '').trim()) count += 1
      return count
    },
    weatherBriefText() {
      if (!this.weatherHasContent) {
        return '未获取天气信息'
      }
      const weather = String(this.weather || '').trim()
      const temp = String(this.temperature || '').trim()
      const wind = String(this.windDirection || '').trim()
      const power = String(this.windPower || '').trim()
      const parts = []
      if (weather) parts.push(weather)
      if (temp) parts.push(`${temp}°C`)
      if (wind || power) parts.push(`${wind}${wind && power ? '·' : ''}${power}`)
      return parts.join('  ') || '已补充天气信息'
    },
    weatherWindText() {
      const wind = String(this.windDirection || '').trim()
      const power = String(this.windPower || '').trim()
      if (wind && power) return `${wind} · ${power}`
      return wind || power || '未获取'
    },
    submitSummaryLine() {
      const parts = []
      if (this.selectedField && this.selectedField.name) parts.push(String(this.selectedField.name))
      if (this.selectedStep && this.selectedStep.stepName) parts.push(String(this.selectedStep.stepName))
      if (this.date && this.time) parts.push(`${this.date} ${this.time}`)
      return parts.join(' · ')
    }
  },
    async onLoad(options) {
      this.elderMode = isElderMode()
      this.recordId = this.normalizeId(options && options.id)
    const prefillFieldId = this.normalizeId(options && options.fieldId)
    const prefillStepId = this.normalizeId(options && options.stepId)
    this.forceIncludeDisabledFields = !!this.recordId || !!prefillFieldId
    this.operatorName = uni.getStorageSync(OPERATOR_STORAGE_KEY) || ''
    const operatorFromLogin = getOperatorNameFromUser()
    if (operatorFromLogin) {
      this.operatorName = operatorFromLogin
      this.lockOperator = true
    }

    if (this.recordId) {
      await this.loadRecordDetail()
    } else {
      this.fieldInitializing = true
      let currentLocation = null
      try {
        if (!prefillFieldId) {
          currentLocation = await this.resolveEntryLocation()
        }
        if (prefillFieldId) {
          await this.fetchFieldDetail(prefillFieldId, this.forceIncludeDisabledFields)
          await this.fetchProcess(prefillStepId || null)
        } else {
          await this.autoSelectCurrentField(currentLocation)
        }
      } finally {
        this.fieldInitializing = false
      }
      await this.autoFillWeather(currentLocation)
    }
    this.$nextTick(() => {
      this.measureStickyMetrics()
    })
  },
  onReady() {
    this.measureStickyMetrics()
  },
  onShow() {
    this.elderMode = isElderMode()
    this.$nextTick(() => {
      this.measureStickyMetrics()
    })
  },
  methods: {
    normalizeId(value) {
      const text = String(value == null ? '' : value).trim()
      return text || ''
    },
    sameId(left, right) {
      return this.normalizeId(left) === this.normalizeId(right)
    },
    goBack() {
      uni.navigateBack()
    },
    measureStickyMetrics() {
      const statusBarHeight = getStatusBarHeight()
      const fallbackTop = statusBarHeight + 44
      const query = uni.createSelectorQuery().in(this.$scope || this)
      query.select('.record-navbar').boundingClientRect()
      query.select('.progress-card').boundingClientRect()
      query.exec((res) => {
        const navRect = res && res[0]
        const progressRect = res && res[1]
        const navHeight = Math.max(
          Number((navRect && navRect.height) || 0),
          statusBarHeight + 44
        )
        const safeTop = navHeight > 0 ? Math.ceil(navHeight) : Math.ceil(fallbackTop)
        this.stickyOffsetTop = Math.max(0, safeTop - 4)
        this.progressStickyHeight = progressRect && progressRect.height ? Math.ceil(progressRect.height) : 120
      })
    },
    onFormScroll(event) {
      const detail = (event && event.detail) || {}
      const nextTop = Number(detail.scrollTop || 0)
      this.formScrollTop = nextTop
      this.updateProgressPinned(nextTop)
    },
    updateProgressPinned(scrollTop) {
      const top = Number(scrollTop || 0)
      // 滞回阈值：避免轻微滑动导致吸顶态反复切换
      const enterThreshold = 16
      const leaveThreshold = 8
      const nextPinned = this.progressPinned ? top >= leaveThreshold : top >= enterThreshold
      if (nextPinned !== this.progressPinned) {
        this.progressPinned = nextPinned
      }
    },
    scrollFormTo(targetTop) {
      const nextTop = Math.max(0, Math.round(Number(targetTop || 0)))
      if (nextTop === this.formScrollTargetTop) {
        this.formScrollTargetTop = Math.max(0, nextTop - 1)
        this.$nextTick(() => {
          this.formScrollTargetTop = nextTop
        })
        return
      }
      this.formScrollTargetTop = nextTop
    },
    sectionAnchorId(stepKey) {
      const map = {
        field: 'anchor-field',
        step: 'anchor-step',
        params: 'anchor-params',
        submit: 'anchor-submit'
      }
      return map[String(stepKey || '').trim()] || ''
    },
    scrollToAnchor(stepKey) {
      const anchorId = this.sectionAnchorId(stepKey)
      if (!anchorId) return
      const selector = `#${anchorId}`
      const query = uni.createSelectorQuery().in(this.$scope || this)
      query.select('.form-scroll').boundingClientRect()
      query.select(selector).boundingClientRect()
      query.exec((res) => {
        const scrollRect = res && res[0]
        const targetRect = res && res[1]
        if (!scrollRect || !targetRect) {
          return
        }
        const offset = Number(this.progressStickyHeight || 0) + 16
        const targetTop = Math.max(
          0,
          Number(this.formScrollTop || 0) + Number(targetRect.top || 0) - Number(scrollRect.top || 0) - offset
        )
        this.scrollFormTo(targetTop)
      })
    },
    onStepsChange(context) {
      const idx = Number(context && context.current)
      if (!Number.isFinite(idx) || idx < 0) return
      const item = this.editProgressSteps[idx]
      if (!item) return
      this.onProgressStepTap(item.key)
    },
    onProgressStepTap(stepKey) {
      this.scrollToAnchor(stepKey)
    },
    pinnedStepItemClass(stepKey) {
      const active = stepKey === this.progressCurrentKey
      return {
        active,
        done: !active && !!this.progressDoneMap[stepKey]
      }
    },
    openFieldSelectorPopup() {
      if (this.fieldInitializing) return
      const ref = this.$refs.fieldSelectorRef
      if (ref && typeof ref.openPopup === 'function') {
        ref.openPopup()
      }
    },
    openStepSelectorPopup() {
      if (!this.selectedField) return
      const ref = this.$refs.stepSelectorRef
      if (ref && typeof ref.openPopup === 'function') {
        ref.openPopup()
      }
    },
    async fetchFieldDetail(fieldId, includeDisabled = false) {
      const safeFieldId = this.normalizeId(fieldId)
      if (!safeFieldId) {
        this.selectedFieldInfo = null
        return null
      }
      this.fieldLoading = true
      try {
        const params = includeDisabled ? { includeDisabled: true } : {}
        try {
          const location = await getCurrentLocation()
          const latitude = Number(location && location.latitude)
          const longitude = Number(location && location.longitude)
          if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
            params.latitude = latitude
            params.longitude = longitude
          }
        } catch (locationError) {}
        const detail = await api.get(`/miniapp/fields/${safeFieldId}`, params)
        this.selectedFieldInfo = detail || null
        return this.selectedFieldInfo
      } catch (e) {
        console.error('加载田块详情失败', e)
        this.selectedFieldInfo = null
        return null
      } finally {
        this.fieldLoading = false
      }
    },
    async fetchProcess(preferredStepId = null) {
      if (!this.selectedField || !this.selectedField.id) {
        this.processInfo = null
        this.stepOptions = []
        this.segmentOptions = []
        this.selectedSegmentKey = ''
        this.selectedStepId = null
        this.syncDynamicFieldDefs(null)
        this.initDynamicValues()
        return
      }
      this.processLoading = true
      this.processInfo = null
      this.stepOptions = []
      this.segmentOptions = []
      this.selectedSegmentKey = ''
      this.selectedStepId = null
      try {
        const params = this.selectedCycleId ? { cycleId: this.selectedCycleId } : {}
        const data = await api.get(`/miniapp/fields/${this.selectedField.id}/process`, params)
        this.processInfo = data || null
        const cycleRows = Array.isArray(data && data.cycles) ? data.cycles : []
        const activeCycles = cycleRows.filter((item) => String((item && item.status) || '').toLowerCase() === 'active')
        const pickedCycle = activeCycles.find((item) => Number(item.isCurrent) === 1) || activeCycles[0] || null
        this.selectedCycleId = data && data.selectedCycleId ? data.selectedCycleId : (pickedCycle ? pickedCycle.id : null)
        if (!this.selectedCycleId && !this.recordId) {
          this.processInfo = null
          this.stepOptions = []
          this.segmentOptions = []
          this.selectedSegmentKey = ''
          this.selectedStepId = null
          this.syncDynamicFieldDefs(null)
          this.initDynamicValues()
          return
        }
        this.stepOptions = (data && data.steps) || []
        this.segmentOptions = this.normalizeSegmentOptions((data && data.segments) || [], this.stepOptions)
        if (this.segmentOptions.length > 1) {
          let segmentKey = data && data.selectedSegmentKey ? data.selectedSegmentKey : ''
          if (!segmentKey && data && data.currentStepId) {
            const currentStepSegment = this.segmentOptions.find((segment) =>
              Array.isArray(segment.steps) && segment.steps.some((step) => this.sameId(step && step.id, data.currentStepId))
            )
            if (currentStepSegment && currentStepSegment.segmentKey) {
              segmentKey = currentStepSegment.segmentKey
            }
          }
          if (!segmentKey) {
            const fieldCropText = this.selectedFieldCropTags.join(' ')
            const matchedByCrop = this.segmentOptions.find((segment) => {
              const cropName = String((segment && segment.cropName) || '').trim()
              const cropVariety = String((segment && segment.cropVariety) || '').trim()
              const tag = `${cropName} ${cropVariety}`.trim()
              if (!tag) return false
              return fieldCropText.includes(cropName) || fieldCropText.includes(cropVariety) || fieldCropText.includes(tag)
            })
            if (matchedByCrop && matchedByCrop.segmentKey) {
              segmentKey = matchedByCrop.segmentKey
            }
          }
          if (preferredStepId) {
            const hitSegment = this.segmentOptions.find((segment) =>
              Array.isArray(segment.steps) && segment.steps.some((step) => this.sameId(step && step.id, preferredStepId))
            )
            if (hitSegment && hitSegment.segmentKey) {
              segmentKey = hitSegment.segmentKey
            }
          }
          const picked = this.segmentOptions.find((segment) => segment.segmentKey === segmentKey)
          this.selectedSegmentKey = picked ? picked.segmentKey : this.segmentOptions[0].segmentKey
        } else {
          this.selectedSegmentKey = this.segmentOptions.length ? this.segmentOptions[0].segmentKey : ''
        }
        const resolvedPreferredStepId = preferredStepId || (data && data.currentStepId ? data.currentStepId : null)
        this.syncSelectedStep(resolvedPreferredStepId)
      } catch (e) {
        console.error('加载流程失败', e)
      } finally {
        this.processLoading = false
      }
    },
    async resolveEntryLocation() {
      try {
        const location = await getCurrentLocation()
        const latitude = Number(location && location.latitude)
        const longitude = Number(location && location.longitude)
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
          return null
        }
        return {
          latitude,
          longitude
        }
      } catch (e) {
        return null
      }
    },
    async autoSelectCurrentField(location = null) {
      if (this.selectedField) return false
      const latitude = Number(location && location.latitude)
      const longitude = Number(location && location.longitude)
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return false
      }
      try {
        const matched = await api.get('/miniapp/fields/current-match', {
          latitude,
          longitude,
          radiusKm: AUTO_FIELD_MATCH_RADIUS_KM
        })
        if (!matched || !matched.id) {
          return false
        }
        this.selectedFieldInfo = {
          ...(matched || {})
        }
        this.selectedCycleId = null
        await this.fetchProcess()
        return true
      } catch (e) {
        console.error('自动匹配当前位置田块失败', e)
        return false
      }
    },
    async autoFillWeather(location = null) {
      try {
        const requestOptions = location && Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude))
          ? { location }
          : {}
        const snap = await getAutoWeatherSnapshot(requestOptions)
        this.weather = String(snap.weather || '').trim()
        this.temperature = String(snap.temperature || '').trim()
        this.humidity = String(snap.humidity || '').trim()
        this.windDirection = String(snap.windDirection || '').trim()
        this.windPower = String(snap.windPower || '').trim()
        this.weatherReportTime = String(snap.reportTime || '').trim()
        this.weatherLocation = String(formatLocationLabel(snap) || '').trim()
      } catch (e) {
        console.error('自动获取天气失败', e)
      }
    },
    async onFieldSelectorChange(field) {
      if (!field || field.id == null) return
      const previousId = this.normalizeId(this.selectedField && this.selectedField.id)
      this.selectedFieldInfo = {
        ...(field || {})
      }
      this.selectedCycleId = null
      if (previousId && this.sameId(previousId, field.id) && this.stepOptions.length) return
      await this.fetchProcess()
    },
    onSegmentSelect(item) {
      if (!item || !item.segmentKey || item.segmentKey === this.selectedSegmentKey) return
      this.selectedSegmentKey = item.segmentKey
      this.syncSelectedStep()
    },
    onStepSelect(step) {
      if (!step || !step.id) return
      this.selectedStepId = step.id
      this.syncDynamicFieldDefs(step)
      this.initDynamicValues()
    },
    normalizeSegmentOptions(source, fallbackSteps) {
      const rows = Array.isArray(source) ? source.filter((item) => item && item.segmentKey) : []
      if (!rows.length) {
        return [{
          segmentKey: 'segment-default',
          segmentName: '默认分段',
          steps: Array.isArray(fallbackSteps) ? fallbackSteps : []
        }]
      }
      return rows.map((item) => ({
        ...item,
        steps: Array.isArray(item.steps) ? item.steps : []
      }))
    },
    syncSelectedStep(preferredStepId = null) {
      const rows = this.visibleStepOptions
      if (!rows.length) {
        this.selectedStepId = null
        this.syncDynamicFieldDefs(null)
        this.initDynamicValues()
        return
      }
      if (preferredStepId) {
        const hit = rows.find((item) => this.sameId(item && item.id, preferredStepId))
        if (hit) {
          this.selectedStepId = hit.id
          this.syncDynamicFieldDefs(hit)
          this.initDynamicValues()
          return
        }
      }
      const keep = rows.find((item) => this.sameId(item && item.id, this.selectedStepId))
      this.selectedStepId = keep ? keep.id : rows[0].id
      this.syncDynamicFieldDefs(keep || rows[0])
      this.initDynamicValues()
    },
    onDateSelectorChange(value) {
      this.date = String(value || '').trim()
    },
    onTimeSelectorChange(value) {
      this.time = String(value || '').trim()
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
    onFormInput(field, e) {
      const key = String(field || '').trim()
      if (!key) return
      this[key] = this.eventValue(e)
    },
    onSelectorPopupVisibleChange(visible) {
      this.selectorPopupVisible = !!visible
      if (this.selectorPopupVisible) return
      this.$nextTick(() => {
        this.measureStickyMetrics()
      })
    },
    formatStageLabel(rawValue) {
      const text = String(rawValue || '').trim()
      if (!text) return ''
      if (/[\u4e00-\u9fa5]/.test(text)) {
        if (/(播|种|芽|苗|移栽)/.test(text)) return '播种期'
        if (/(生长|分蘖|伸长|拔节|抽穗|开花|授粉|结果|坐果|果实|灌浆)/.test(text)) return '生长期'
        if (/(成熟|采收|收获|采后|休耕)/.test(text)) return '收获期'
        return ''
      }
      const key = text.toLowerCase().replace(/[\s_-]/g, '')
      if (/^(seed|sow|sowing|seeding|planting|germination|bud|budding|sprout|emergence|seedling|nursery|nurseryperiod|transplant|transplanting)$/.test(key)) {
        return '播种期'
      }
      if (/^(vegetative|growth|growing|tillering|elongation|jointing|heading|flowering|bloom|blossoming|pollination|fruiting|fruit|fruitset|fruitsetting|fruitdevelopment|filling|grainfilling)$/.test(key)) {
        return '生长期'
      }
      if (/^(maturity|mature|ripening|harvest|harvesting|postharvest|postharvesting|afterharvest|resting)$/.test(key)) {
        return '收获期'
      }
      return ''
    },
    resolveStepStageLabel(step, index) {
      const rows = Array.isArray(this.visibleStepOptions) ? this.visibleStepOptions : []
      if (!rows.length) return ''
      let cursor = Number(index)
      if (!Number.isInteger(cursor) || cursor < 0) {
        cursor = rows.findIndex((item) => this.sameId(item && item.id, step && step.id))
      }
      if (cursor < 0) cursor = 0
      for (let i = cursor; i >= 0; i -= 1) {
        const current = rows[i]
        const label = this.formatStageLabel(current && current.growthStage)
        if (label) return label
      }
      return cursor === 0 ? '播种期' : ''
    },
    resolveFieldCropTags(field) {
      return resolveCropVarietyLabels(field, 8)
    },
    resolveFieldCover(field) {
      if (!field) return ''
      const candidate = [
        field.coverImageUrl,
        field.imageUrl,
        field.coverUrl,
        field.cover,
        field.thumbUrl
      ].find((x) => String(x || '').trim())
      return this.resolveAssetUrl(candidate)
    },
    resolveFieldCoverPreview(field) {
      if (!field) return ''
      const candidate = [
        field.coverThumbUrl,
        field.thumbUrl,
        field.thumbnailUrl,
        field.previewUrl,
        field.coverImageUrl,
        field.imageUrl
      ].find((x) => String(x || '').trim())
      return this.resolveAssetUrl(candidate)
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
    normalizeImageAssetRow(row) {
      if (!row) return null
      const rawFileUrl = String(row.fileUrl || '').trim()
      const rawThumbUrl = String(row.thumbUrl || row.previewUrl || row.thumbnailUrl || '').trim()
      return {
        id: row.id,
        fileName: row.fileName || '',
        fileUrl: this.resolveAssetUrl(rawFileUrl),
        thumbUrl: this.resolveAssetUrl(rawThumbUrl),
        previewUrl: this.resolveAssetUrl(String(row.previewUrl || '').trim()),
        thumbnailUrl: this.resolveAssetUrl(String(row.thumbnailUrl || '').trim()),
        rawFileUrl,
        localPreviewUrl: '',
        reviewStatus: String(row.reviewStatus || 'approved').trim() || 'approved',
        reviewStatusText: String(row.reviewStatusText || '').trim(),
        hintMessage: String(row.hintMessage || '').trim(),
        reviewRemark: String(row.reviewRemark || '').trim()
      }
    },
    resolveInputType(type) {
      return String(type || '').toLowerCase() === 'number' ? 'digit' : 'text'
    },
    onDynamicChange(key, e) {
      const nextValue = this.eventValue(e)
      this.$set(this.dynamicValues, key, nextValue)
      this.validateDynamicFieldByKey(key)
    },
    dynamicDisplayValue(item) {
      if (!item || !item.key) return ''
      const raw = String(this.dynamicValues[item.key] || '').trim()
      if (!raw) return ''
      if (item.type === 'select') {
        return this.resolveSelectLabel(item, raw)
      }
      return raw
    },
    onDynamicPickerClick(item) {
      if (!item || !item.key) return
      if (item.type === 'select') {
        this.openDynamicSelectPicker(item)
        return
      }
      if (item.type === 'date' || item.type === 'time') {
        this.openDynamicDatePicker(item)
      }
    },
    openDynamicSelectPicker(item) {
      const options = this.resolveSelectOptions(item)
      if (!options.length) {
        uni.showToast({ title: '暂无可选项', icon: 'none' })
        return
      }
      const current = String(this.dynamicValues[item.key] || options[0].value)
      this.dynamicSelectKey = item.key
      this.dynamicSelectTitle = String(item.label || '请选择')
      this.dynamicSelectOptions = options.map((x) => ({
        label: String(x.label),
        value: String(x.value)
      }))
      this.dynamicSelectPickerValue = [current]
      this.dynamicSelectPickerVisible = true
    },
    onDynamicSelectPickerConfirm(context) {
      const values = (context && context.value) || []
      const picked = String(values[0] || '').trim()
      const key = this.dynamicSelectKey
      if (this.dynamicSelectKey && picked) {
        this.$set(this.dynamicValues, this.dynamicSelectKey, picked)
      }
      this.dynamicSelectPickerVisible = false
      this.dynamicSelectKey = ''
      if (key) this.validateDynamicFieldByKey(key)
    },
    openDynamicDatePicker(item) {
      this.dynamicDatePickerKey = item.key
      this.dynamicDatePickerMode = item.type === 'time' ? 'time' : 'date'
      this.dynamicDatePickerFormat = item.type === 'time' ? 'HH:mm' : 'YYYY-MM-DD'
      this.dynamicDatePickerTitle = `选择${item.label}`
      this.dynamicDatePickerValue = String(this.dynamicValues[item.key] || (item.type === 'time' ? this.time : this.date))
      this.dynamicDatePickerVisible = true
    },
    onDynamicDatePickerConfirm(context) {
      if (!this.dynamicDatePickerKey) {
        this.dynamicDatePickerVisible = false
        return
      }
      const raw = context && context.value
      const value = this.dynamicDatePickerMode === 'time'
        ? this.normalizeTimeValue(raw, '')
        : this.normalizeDateValue(raw, '')
      const key = this.dynamicDatePickerKey
      this.$set(this.dynamicValues, this.dynamicDatePickerKey, value)
      this.dynamicDatePickerVisible = false
      this.dynamicDatePickerKey = ''
      if (key) this.validateDynamicFieldByKey(key)
    },
    normalizeDateValue(raw, fallback = '') {
      if (raw == null) return fallback
      if (typeof raw === 'number' || /^[0-9]{10,13}$/.test(String(raw))) {
        const date = new Date(Number(raw))
        if (!Number.isNaN(date.getTime())) {
          return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
        }
      }
      const text = String(raw).trim()
      const matched = text.match(/(\d{4}-\d{2}-\d{2})/)
      return matched ? matched[1] : (text || fallback)
    },
    normalizeTimeValue(raw, fallback = '') {
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
    },
    initDynamicValues(initial = null) {
      /*
       * 根据当前步骤的 schema 初始化动态字段值。
       *
       * 动态字段不是写死在页面里的，而是由 selectedStep.formSchema 决定。
       * 所以每次步骤变化、详情回填或重新编辑时，都需要重新同步 dynamicValues。
       */
      this.syncDynamicFieldDefs()
      const next = {}
      const nextErrors = {}
      this.dynamicFields.forEach((item) => {
        if (initial && initial[item.key] != null) {
          next[item.key] = String(initial[item.key])
          nextErrors[item.key] = ''
          return
        }
        if (item.defaultValue != null) {
          next[item.key] = String(item.defaultValue)
          nextErrors[item.key] = ''
          return
        }
        next[item.key] = ''
        nextErrors[item.key] = ''
      })
      this.dynamicValues = next
      this.dynamicFieldErrors = nextErrors
    },
    normalizeFieldType(type) {
      const raw = String(type || 'text').toLowerCase().trim()
      const supported = ['text', 'number', 'textarea', 'date', 'time', 'select', 'location']
      return supported.includes(raw) ? raw : 'text'
    },
    buildDynamicFieldDefs(step = null) {
      /*
       * 把步骤上的 formSchema 转成前端真正可渲染的字段定义。
       * 这一步就是“流程步骤驱动动态表单”的前端落点。
       */
      const targetStep = step || this.selectedStep
      if (!targetStep || !targetStep.formSchema) return []
      try {
        const parsed = JSON.parse(targetStep.formSchema)
        if (!Array.isArray(parsed)) return []
        return parsed
          .filter((x) => x && x.key && x.label)
          .map((x) => ({
            ...x,
            type: this.normalizeFieldType(x.type)
          }))
      } catch (e) {
        return []
      }
    },
    syncDynamicFieldDefs(step = null) {
      // 统一收口字段定义同步，避免页面到处直接解析 formSchema。
      this.dynamicFieldDefs = this.buildDynamicFieldDefs(step)
    },
    resolveSelectOptions(item) {
      const raw = (item && item.options) || []
      const arr = Array.isArray(raw) ? raw : String(raw).split(',').map((x) => x.trim()).filter(Boolean)
      return arr.map((x) => {
        if (typeof x === 'object' && x) {
          const label = String(x.label || x.name || x.value || '').trim()
          const value = String(x.value || x.code || x.label || '').trim()
          return {
            label: label || value,
            value: value || label
          }
        }
        const value = String(x || '').trim()
        return { label: value, value }
      }).filter((x) => x.label && x.value)
    },
    resolveSelectLabel(item, value) {
      const v = String(value || '').trim()
      if (!v) return ''
      const row = this.resolveSelectOptions(item).find((x) => x.value === v)
      return row ? row.label : v
    },
    async fillDynamicLocation(key) {
      try {
        const snap = await getAutoWeatherSnapshot()
        const value = `${formatLocationLabel(snap)} (${Number(snap.latitude).toFixed(6)},${Number(snap.longitude).toFixed(6)})`
        this.$set(this.dynamicValues, key, value)
        this.validateDynamicFieldByKey(key)
      } catch (e) {
        console.error('dynamic location failed', e)
        uni.showToast({ title: '定位失败，请手动填写', icon: 'none' })
      }
    },
    readNumberConstraint(field, keys = []) {
      if (!field || !keys.length) return null
      for (const key of keys) {
        if (!Object.prototype.hasOwnProperty.call(field, key)) continue
        const raw = field[key]
        if (raw == null || raw === '') continue
        const value = Number(raw)
        if (Number.isFinite(value)) return value
      }
      return null
    },
    validateDynamicField(field) {
      /*
       * 前端先做一轮轻校验，尽量在提交前就把明显问题拦住。
       * 真正的最终校验仍以后端 StepFormSchemaValidator 为准。
       */
      if (!field || !field.key) return ''
      const label = String(field.label || field.key).trim()
      const value = String(this.dynamicValues[field.key] || '').trim()
      const fieldType = String(field.type || '').toLowerCase()
      if (field.required && !value) {
        return `请填写${label}`
      }
      if (!value) return ''

      if (fieldType === 'number') {
        if (!/^[-+]?(\d+(\.\d+)?|\.\d+)$/.test(value)) {
          return `${label}需为数字`
        }
        const num = Number(value)
        const min = this.readNumberConstraint(field, ['min', 'minimum'])
        const max = this.readNumberConstraint(field, ['max', 'maximum'])
        if (Number.isFinite(min) && num < min) {
          return `${label}不能小于${min}`
        }
        if (Number.isFinite(max) && num > max) {
          return `${label}不能大于${max}`
        }
      }
      if (fieldType === 'date' && !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return `${label}格式应为 YYYY-MM-DD`
      }
      if (fieldType === 'time' && !/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) {
        return `${label}格式应为 HH:mm`
      }
      if (fieldType === 'select') {
        const options = this.resolveSelectOptions(field)
        if (options.length && !options.some((row) => String(row.value) === value)) {
          return `${label}不在可选项内`
        }
      }
      return ''
    },
    validateDynamicFieldByKey(key) {
      const item = this.dynamicFields.find((row) => String(row.key) === String(key))
      if (!item) return true
      const message = this.validateDynamicField(item)
      this.$set(this.dynamicFieldErrors, item.key, message)
      return !message
    },
    validateDynamicFields() {
      // 提交前批量校验所有动态字段，并返回首个错误，方便页面定位和提示。
      const nextErrors = {}
      let firstKey = ''
      let firstMessage = ''
      this.dynamicFields.forEach((item) => {
        if (!item || !item.key) return
        const message = this.validateDynamicField(item)
        nextErrors[item.key] = message
        if (!firstMessage && message) {
          firstKey = item.key
          firstMessage = message
        }
      })
      this.dynamicFieldErrors = nextErrors
      return {
        valid: !firstMessage,
        firstKey,
        firstMessage
      }
    },
    syncUploadFilesFromAssets() {
      this.uploadFiles = []
    },
    syncImageAssetsFromUploadFiles() {
      this.imageAssets = (Array.isArray(this.imageAssets) ? this.imageAssets : []).filter((item) => {
        const assetId = String((item && item.id) || '').trim()
        return /^\d+$/.test(assetId)
      })
    },
    extractUploadEventFiles(context) {
      if (Array.isArray(context)) return context
      const directFiles = context && context.files
      if (Array.isArray(directFiles)) return directFiles
      const detailFiles = context && context.detail && context.detail.files
      if (Array.isArray(detailFiles)) return detailFiles
      return []
    },
    extractSelectedUploadFiles(context) {
      const directFiles = context && context.currentSelectedFiles
      if (Array.isArray(directFiles) && Array.isArray(directFiles[0])) return directFiles[0]
      const detailFiles = context && context.detail && context.detail.currentSelectedFiles
      if (Array.isArray(detailFiles) && Array.isArray(detailFiles[0])) return detailFiles[0]
      return []
    },
    buildUploadPreviewFile(file, idx) {
      const remoteRawUrl = String(file && (file.rawFileUrl || file.remoteUrl) || '').trim()
      const localPreviewUrl = String(file && (file.localPreviewUrl || file.localUrl || file.thumb) || '').trim()
      const fallbackUrl = String(file && file.url || '').trim()
      const previewUrl = this.resolveAssetUrl(localPreviewUrl || fallbackUrl)
      const remoteUrl = this.resolveAssetUrl(remoteRawUrl || fallbackUrl)
      return {
        ...file,
        url: previewUrl || remoteUrl,
        thumb: previewUrl || remoteUrl,
        remoteUrl: remoteRawUrl || fallbackUrl,
        rawFileUrl: remoteRawUrl || fallbackUrl,
        name: (file && file.name) || `现场图片${idx + 1}`,
        type: 'image',
        status: String((file && file.status) || 'done').trim() || 'done',
        percent: Number(file && file.percent) > 0 ? Number(file.percent) : 100
      }
    },
    getUploadPreviewIdentityKeys(file) {
      const rawKeys = [
        file && file.assetId,
        file && file.id,
        file && file.localPreviewUrl,
        file && file.localUrl,
        file && file.url,
        file && file.thumb,
        file && file.remoteUrl,
        file && file.rawFileUrl
      ]
      const out = []
      rawKeys.forEach((raw) => {
        const key = String(raw || '').trim()
        if (!key || out.includes(key)) return
        out.push(key)
      })
      return out
    },
    isSameUploadPreviewFile(left, right) {
      const leftKeys = this.getUploadPreviewIdentityKeys(left)
      const rightKeys = this.getUploadPreviewIdentityKeys(right)
      if (!leftKeys.length || !rightKeys.length) return false
      return leftKeys.some((key) => rightKeys.includes(key))
    },
    mergeUploadPreviewFileRow(current, incoming) {
      const currentPreview = String((current && (current.localPreviewUrl || current.localUrl || current.thumb || current.url)) || '').trim()
      const incomingPreview = String((incoming && (incoming.localPreviewUrl || incoming.localUrl || incoming.thumb || incoming.url)) || '').trim()
      const currentRemote = String((current && (current.rawFileUrl || current.remoteUrl)) || '').trim()
      const incomingRemote = String((incoming && (incoming.rawFileUrl || incoming.remoteUrl)) || '').trim()
      const currentAssetId = String((current && (current.assetId || current.id)) || '').trim()
      const incomingAssetId = String((incoming && (incoming.assetId || incoming.id)) || '').trim()
      const merged = {
        ...(current || {}),
        ...(incoming || {})
      }
      const previewSource = incomingPreview || currentPreview
      const remoteSource = incomingRemote || currentRemote
      const resolvedPreviewUrl = this.resolveAssetUrl(previewSource)
      const resolvedRemoteUrl = this.resolveAssetUrl(remoteSource)
      const status = remoteSource || incomingAssetId || currentAssetId
        ? 'done'
        : String((incoming && incoming.status) || (current && current.status) || 'loading').trim() || 'loading'
      merged.assetId = incomingAssetId || currentAssetId || ''
      merged.localPreviewUrl = previewSource || ''
      merged.localUrl = String((incoming && incoming.localUrl) || (current && current.localUrl) || previewSource || '').trim()
      merged.remoteUrl = remoteSource || ''
      merged.rawFileUrl = remoteSource || ''
      merged.url = resolvedPreviewUrl || resolvedRemoteUrl || ''
      merged.thumb = resolvedPreviewUrl || resolvedRemoteUrl || merged.url
      merged.status = status
      merged.percent = status === 'done'
        ? 100
        : Math.max(Number((incoming && incoming.percent) || 0), Number((current && current.percent) || 0), 0)
      return merged
    },
    normalizeUploadPreviewFiles(files) {
      const normalized = []
      ;(Array.isArray(files) ? files : []).forEach((raw, idx) => {
        const nextFile = this.buildUploadPreviewFile(raw, idx)
        const matchIndex = normalized.findIndex((row) => this.isSameUploadPreviewFile(row, nextFile))
        if (matchIndex >= 0) {
          normalized.splice(matchIndex, 1, this.mergeUploadPreviewFileRow(normalized[matchIndex], nextFile))
        } else {
          normalized.push(this.mergeUploadPreviewFileRow(null, nextFile))
        }
      })
      return normalized.slice(0, 6)
    },
    normalizeUploadQueueFiles(files) {
      return this.normalizeUploadPreviewFiles(files).filter((file) => {
        const status = String((file && file.status) || '').trim() || 'loading'
        return status !== 'done'
      })
    },
    mergeUploadPreviewFiles(incomingFiles) {
      const current = Array.isArray(this.uploadFiles) ? this.uploadFiles : []
      this.uploadFiles = this.normalizeUploadQueueFiles([...(current || []), ...((Array.isArray(incomingFiles) ? incomingFiles : []))])
    },
    upsertImageAssetRow(row) {
      if (!row || !row.id) return
      const rows = Array.isArray(this.imageAssets) ? [...this.imageAssets] : []
      const hit = rows.findIndex((item) => this.sameId(item && item.id, row.id))
      if (hit >= 0) {
        rows.splice(hit, 1, {
          ...rows[hit],
          ...row
        })
      } else {
        rows.push(row)
      }
      this.imageAssets = rows.slice(0, 6)
    },
    removeUploadPreviewFile(target) {
      this.uploadFiles = (Array.isArray(this.uploadFiles) ? this.uploadFiles : []).filter((row) => !this.isSameUploadPreviewFile(row, target))
    },
    onRecordUploadSelectChange(context) {
      const files = this.extractSelectedUploadFiles(context)
      if (!files.length) return
      const remaining = Math.max(0, 6 - this.currentImageCount)
      if (!(remaining > 0)) {
        uni.showToast({ title: '最多上传 6 张图片', icon: 'none' })
        return
      }
      const acceptedFiles = files.slice(0, remaining)
      if (acceptedFiles.length < files.length) {
        uni.showToast({ title: '最多上传 6 张图片', icon: 'none' })
      }
      this.mergeUploadPreviewFiles(acceptedFiles.map((item) => ({
        ...item,
        localPreviewUrl: item.url || '',
        localUrl: item.url || '',
        thumb: item.url || '',
        status: 'loading',
        percent: Number(item.percent || 0)
      })))
    },
    onRecordUploadFilesUpdate(files) {
      if (!Array.isArray(files)) return
      this.uploadFiles = this.normalizeUploadQueueFiles(files)
    },
    async handleRecordImageUpload(files) {
      if (!Array.isArray(files) || !files.length) return
      this.imageUploading = true
      try {
        for (const file of files) {
          const tempPath = String(file && file.url ? file.url : '').trim()
          if (!tempPath) continue
          const uploaded = await this.uploadRecordImage(tempPath)
          const mapped = this.normalizeImageAssetRow(uploaded)
          if (!mapped) {
            throw new Error('invalid uploaded file')
          }
          this.upsertImageAssetRow({
            ...mapped,
            fileName: mapped.fileName || file.name || '现场图片',
            localPreviewUrl: tempPath
          })
          this.removeUploadPreviewFile(file)
          if (mapped.reviewStatus === 'pending') {
            uni.showToast({ title: '图片已提交审核，审核通过后可查看', icon: 'none' })
          }
        }
      } catch (e) {
        console.error('上传农事图片失败', e)
        throw e
      } finally {
        this.imageUploading = false
      }
    },
    onRecordUploadSuccess(context) {
      const files = this.extractUploadEventFiles(context)
      if (files.length) {
        this.uploadFiles = this.normalizeUploadQueueFiles(this.uploadFiles)
      }
    },
    onRecordUploadRemove(context) {
      const index = Number(context && context.index)
      if (!(index >= 0 && index < this.uploadFiles.length)) return
      this.uploadFiles = this.uploadFiles.filter((_, idx) => idx !== index)
    },
    previewRecordImage(currentUrl) {
      const current = String(currentUrl || '').trim()
      const urls = this.recordImageCards
        .filter((item) => item && item.previewable && item.previewUrl)
        .map((item) => item.previewUrl)
      if (!current || !urls.length) return
      uni.previewImage({
        current,
        urls
      })
    },
    removeUploadedImage(index) {
      if (!(index >= 0 && index < this.imageAssets.length)) return
      this.imageAssets = this.imageAssets.filter((_, idx) => idx !== index)
    },
    onRecordUploadFail(context) {
      const message = String((context && (context.message || context.errMsg || (context.error && context.error.message))) || '').trim()
      uni.showToast({ title: message || '图片上传失败', icon: 'none' })
    },
    async uploadRecordImage(filePath) {
      return api.upload('/miniapp/files/upload', filePath, {
        name: 'file',
        formData: {
          remark: 'farm-record-image'
        }
      })
    },
    async loadRecordImages() {
      if (!this.recordId) {
        this.imageAssets = []
        this.uploadFiles = []
        return
      }
      try {
        const rows = await api.get(`/miniapp/farm-records/${this.recordId}/images`)
        const list = Array.isArray(rows) ? rows : []
        this.imageAssets = list
          .map((x) => this.normalizeImageAssetRow(x))
          .filter((x) => x && x.id)
        this.syncUploadFilesFromAssets()
      } catch (e) {
        this.imageAssets = []
        this.uploadFiles = []
      }
    },
    async loadRecordDetail() {
      if (!this.recordId) return
      try {
        const detail = await api.get(`/miniapp/farm-records/${this.recordId}`)
        if (!detail) return
        this.originalFieldId = this.normalizeId(detail.fieldId)
        if (detail.canEdit === false) {
          uni.showToast({ title: '该记录已超可编辑范围', icon: 'none' })
          setTimeout(() => uni.navigateBack(), 320)
          return
        }
        await this.fetchFieldDetail(detail.fieldId, true)
        await this.fetchProcess(detail.stepId || null)

        const dt = String(detail.workDate || '').replace('T', ' ')
        if (dt.length >= 16) {
          this.date = dt.slice(0, 10)
          this.time = dt.slice(11, 16)
        }
        if (!this.lockOperator) {
          this.operatorName = detail.operatorName || ''
        }
        this.notes = detail.notes || ''
        this.weather = detail.weather || ''
        this.temperature = detail.temperature || ''
        this.weatherLocation = detail.weatherLocation || ''
        this.humidity = detail.humidity || ''
        this.windDirection = detail.windDirection || ''
        this.windPower = detail.windPower || ''
        this.weatherReportTime = detail.weatherReportTime || ''
        await this.loadRecordImages()

        if (detail.extraJson) {
          try {
            this.initDynamicValues(JSON.parse(detail.extraJson))
          } catch (e) {
            this.initDynamicValues()
          }
        }
      } catch (e) {
        console.error('加载记录详情失败', e)
      }
    },
    async submit() {
      /*
       * 农事记录提交时，extraJson 由 dynamicValues 动态组装出来。
       *
       * 这正是当前项目“固定字段 + extraJson 扩展字段”的体现：
       * - 固定字段直接放在 payload 顶层；
       * - 步骤特有字段收敛进 extraJson；
       * - 后端再按 stepId 对 extraJson 做 schema 校验。
       */
      if (!this.selectedField) {
        uni.showToast({ title: '请选择田块', icon: 'none' })
        this.scrollToAnchor('field')
        return
      }
      if (this.stepOptions.length && !this.selectedStep) {
        uni.showToast({ title: '请选择流程步骤', icon: 'none' })
        this.scrollToAnchor('step')
        return
      }
      const dynamicCheck = this.validateDynamicFields()
      if (!dynamicCheck.valid) {
        uni.showToast({ title: dynamicCheck.firstMessage || '请完善参数填写', icon: 'none' })
        this.scrollToAnchor('params')
        return
      }
      const hasDate = String(this.date || '').trim()
      const hasTime = String(this.time || '').trim()
      if (!hasDate || !hasTime) {
        uni.showToast({ title: '请确认作业时间', icon: 'none' })
        this.scrollToAnchor('submit')
        return
      }
      this.submitting = true
      try {
        const extra = {}
        Object.keys(this.dynamicValues).forEach((key) => {
          const value = (this.dynamicValues[key] || '').toString().trim()
          if (value) extra[key] = value
        })
        const payload = {
          fieldId: this.selectedField.id,
          cycleId: this.selectedCycleId || null,
          stepId: this.selectedStep ? this.selectedStep.id : null,
          workDate: `${this.date} ${this.time}:00`,
          operatorName: this.operatorName || '',
          notes: this.notes,
          weather: this.weather || '',
          temperature: this.temperature || '',
          weatherLocation: this.weatherLocation || '',
          humidity: this.humidity || '',
          windDirection: this.windDirection || '',
          windPower: this.windPower || '',
          weatherReportTime: this.weatherReportTime || '',
          extraJson: Object.keys(extra).length ? JSON.stringify(extra) : null,
          imageAssetIds: this.imageAssets
            .map((x) => String((x && x.id) || '').trim())
            .filter((x) => /^\d+$/.test(x))
        }
        let savedRow = null
        if (this.recordId) {
          savedRow = await api.put(`/miniapp/farm-records/${this.recordId}`, payload)
        } else {
          savedRow = await api.post('/miniapp/farm-records', payload)
        }
        const nextRecordId = this.normalizeId((savedRow && savedRow.id) || this.recordId)
        const nextFieldId = this.normalizeId(this.selectedField && this.selectedField.id)
        const refreshKeys = [refreshTopics.farmRecords()]
        if (nextRecordId) {
          refreshKeys.push(refreshTopics.farmRecordDetail(nextRecordId))
        }
        if (nextFieldId) {
          refreshKeys.push(refreshTopics.fieldDetail(nextFieldId))
        }
        if (this.originalFieldId && this.originalFieldId !== nextFieldId) {
          refreshKeys.push(refreshTopics.fieldDetail(this.originalFieldId))
        }
        markDataChanged(refreshKeys)
        uni.setStorageSync(OPERATOR_STORAGE_KEY, this.operatorName || '')
        uni.showToast({ title: this.recordId ? '已保存' : '已提交', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (e) {
        console.error('提交失败', e)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss">
.page.page.record-style-page {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  scrollbar-width: none;
}

.page {
  --record-primary: #73ae52;
  --record-primary-press: #5e9240;
  --record-primary-soft: #edf7e7;
  --record-bg: #f3f4f6;
  --record-surface: #ffffff;
  --record-surface-soft: #f7faf6;
  --record-border: #e3e7df;
  --record-text: #1f2a21;
  --record-text-sub: #62705f;
  --record-text-soft: #8d988c;
  --record-warn-bg: #fff2e3;
  --record-warn-text: #c57a21;
  --record-submit-bg: rgba(243, 244, 246, 0.98);
  --record-photo-del-bg: rgba(32, 42, 34, 0.56);

  --dh-color-brand: var(--record-primary);
  --dh-color-brand-press: var(--record-primary-press);
  --dh-color-brand-light: var(--record-primary-soft);
  --dh-color-bg: var(--record-bg);
  --dh-color-surface: var(--record-surface);
  --dh-color-surface-soft: var(--record-surface-soft);
  --dh-color-surface-muted: var(--record-primary-soft);
  --dh-color-border: var(--record-border);
  --dh-color-text: var(--record-text);
  --dh-color-text-sub: var(--record-text-sub);
  --dh-color-text-soft: var(--record-text-soft);
  --td-brand-color: var(--record-primary);
  --td-brand-color-active: var(--record-primary-press);
  --td-brand-color-light: var(--record-primary-soft);
  --td-text-color-brand: var(--record-primary);

  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--record-bg);
  overflow: hidden;
}

.record-navbar {
  --td-navbar-bg-color: var(--dh-color-brand);
  --td-navbar-background: var(--dh-color-brand);
  --td-navbar-color: #ffffff;
  --td-navbar-capsule-border-color: rgba(255, 255, 255, 0.36);
  --td-navbar-title-font-size: 34rpx;
  --td-navbar-title-font-weight: 700;
  border-bottom: 0;
  z-index: 220;
}

.progress-fixed-shell {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 210;
}

.progress-card.pinned {
  border-radius: 0;
  border-left: 0;
  border-right: 0;
  border-top: 0;
  box-shadow: 0 10rpx 24rpx rgba(31, 42, 33, 0.08);
}

.form-shell {
  flex: 1;
  min-height: 0;
}

.form-scroll {
  height: 100%;
  background: var(--record-bg);
  overflow: hidden;
  scrollbar-width: none;
}

.form-scroll::-webkit-scrollbar {
  width: 0 !important;
  height: 0 !important;
  display: none !important;
}

.form {
  padding: 8rpx 24rpx calc(214rpx + env(safe-area-inset-bottom));
  scrollbar-width: none;
}

.progress-sticky-shell {
  margin-top: 14rpx;
  margin-bottom: 2rpx;
  background: var(--record-bg);
}

.progress-placeholder {
  margin-bottom: 2rpx;
}

.progress-card {
  border-radius: 18rpx;
  background: var(--record-surface);
  border: 1rpx solid var(--record-border);
  padding: 16rpx 16rpx 12rpx;
}

.progress-card.pinned {
  padding: 28rpx 16rpx 12rpx;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10rpx;
}

.progress-stat-left,
.progress-stat-right {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.progress-stat-label {
  font-size: 22rpx;
  color: var(--record-text-sub);
}

.progress-stat-value {
  font-size: 26rpx;
  color: #d05a4d;
  font-weight: 700;
}

.progress-ready-text {
  font-size: 27rpx;
  color: #2f8f4c;
  font-weight: 700;
}

.progress-stat-plain {
  font-size: 26rpx;
  color: var(--record-text);
  font-weight: 600;
}

.record-steps {
  margin-top: 14rpx;
  --td-step-item-circle-size: 34rpx;
  --td-step-item-circle-font-size: 20rpx;
  --td-step-item-process-circle-bg: var(--record-primary);
  --td-step-item-process-circle-color: #ffffff;
  --td-step-item-process-title-color: var(--record-primary);
  --td-step-item-finish-circle-bg: var(--record-primary-soft);
  --td-step-item-finish-circle-color: var(--record-primary);
  --td-step-item-finish-line-color: var(--record-primary);
  --td-step-item-default-circle-bg: #edf2ea;
  --td-step-item-default-circle-color: var(--record-text-soft);
  --td-step-item-default-title-color: var(--record-text-sub);
  --td-step-item-line-color: #d5dfcf;
}

.record-steps-pinned {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8rpx;
}

.record-steps-pinned-item {
  min-width: 0;
  padding: 10rpx 8rpx 12rpx;
  border-radius: 14rpx;
  border-bottom: 4rpx solid transparent;
  background: #f5f8f2;
}

.record-steps-pinned-item.done {
  background: #edf7e7;
  border-bottom-color: #98c27f;
}

.record-steps-pinned-item.active {
  background: #e6f3df;
  border-bottom-color: var(--record-primary);
}

.record-steps-pinned-label {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
  font-size: 23rpx;
  color: var(--record-text-sub);
  font-weight: 600;
}

.record-steps-pinned-item.done .record-steps-pinned-label {
  color: #5f8f4f;
}

.record-steps-pinned-item.active .record-steps-pinned-label {
  color: var(--record-primary);
  font-weight: 700;
}

.card {
  background: var(--record-surface);
  border-radius: 18rpx;
  border: 1rpx solid var(--record-border);
  box-shadow: none;
}

.section-block {
  margin-top: 20rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10rpx;
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
  border: 1rpx solid var(--record-primary);
  background: var(--record-primary);
}

.section-title {
  font-size: 34rpx;
  color: var(--record-text);
  font-weight: 700;
  line-height: 1.3;
}

.section-meta {
  font-size: 23rpx;
  color: var(--record-text-sub);
}

.pick-card {
  border-radius: 18rpx;
  border: 1rpx solid var(--record-border);
  background: var(--record-surface);
  padding: 16rpx;
  min-height: 154rpx;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.pick-main {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 12rpx;
}

.field-cover {
  width: 188rpx;
  height: 144rpx;
  border-radius: 12rpx;
  border: 1rpx solid var(--record-border);
  background: #eef3ea;
  overflow: hidden;
  flex-shrink: 0;
}

.field-cover-image {
  width: 100%;
  height: 100%;
  display: block;
}

.pick-info {
  flex: 1;
  min-width: 0;
  min-height: 144rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.pick-loading-text {
  display: block;
  margin-bottom: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #6f8068;
}

.pick-title-row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6rpx;
}

.pick-title {
  font-size: 31rpx;
  color: var(--record-text);
  font-weight: 700;
  line-height: 1.38;
}

.pick-title-relation {
  font-size: 27rpx;
  color: #5f9138;
  font-weight: 700;
  line-height: 1.38;
}

.pick-meta {
  margin-top: 6rpx;
  display: block;
  font-size: 24rpx;
  color: var(--record-text-sub);
  line-height: 1.45;
}

.pick-tags {
  margin-top: 9rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.pick-change-btn {
  --td-button-text-color: var(--record-primary);
  --td-button-text-active-color: var(--record-primary-press);
  --td-button-text-active-bg-color: #dcefd9;
  margin-top: 2rpx;
  flex-shrink: 0;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #e7f3e5;
}

.step-pick-card.disabled {
  opacity: 0.68;
}

.step-pick-card .pick-info {
  min-height: 144rpx;
}

.warn-tip {
  margin-top: 10rpx;
  border-radius: 12rpx;
  border: 1rpx solid #efd8b8;
  background: var(--record-warn-bg);
  padding: 10rpx 12rpx;
}

.warn-text {
  display: block;
  font-size: 23rpx;
  line-height: 1.45;
  color: var(--record-warn-text);
}

.selector-anchor {
  height: 0;
}

.section-card {
  padding: 18rpx;
}

.merged-section {
  padding: 0;
  overflow: hidden;
}

.sub-block {
  padding: 18rpx;
}

.sub-block + .sub-block {
  border-top: 1rpx solid var(--record-border);
}

.sub-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10rpx;
  margin-bottom: 10rpx;
}

.sub-block-title {
  font-size: 26rpx;
  color: var(--record-text);
  font-weight: 700;
  line-height: 1.35;
}

.weather-head {
  align-items: center;
}

.weather-refresh-btn {
  margin-left: auto;
  margin-right: 0;
  flex-shrink: 0;
}
.empty-card {
  text-align: center;
  font-size: 24rpx;
  color: var(--record-text-sub);
  padding-top: 24rpx;
  padding-bottom: 24rpx;
}

.meta {
  font-size: 23rpx;
  color: var(--record-text-sub);
}

.readonly {
  min-height: 76rpx;
  display: flex;
  align-items: center;
  padding: 0 18rpx;
  font-size: 27rpx;
  color: var(--record-text);
  font-weight: 600;
  border-radius: 14rpx;
  border: 1rpx solid #dde4d6;
  background: #f6f8f4;
}

.readonly-display {
  border-color: #d8ded3;
  background: #eef1ec;
  color: #5f695e;
}

.td-input {
  --td-input-bg-color: #f6f8f4;
  --td-input-border-color: #dde4d6;
  --td-input-text-color: var(--record-text);
  --td-input-placeholder-text-color: var(--record-text-soft);
  --td-input-vertical-padding: 14rpx;
  --td-input-horizontal-padding: 18rpx;
  border-radius: 16rpx !important;
  border: 1rpx solid #dde4d6 !important;
  background: #f6f8f4 !important;
  box-shadow: none;
}

.td-input-editable {
  --td-input-bg-color: #f6f8f4;
  --td-input-border-color: #d7dfd0;
  background: #f6f8f4 !important;
}

.td-input-picker {
  --td-input-bg-color: #ffffff;
  --td-input-border-color: #bcd0b2;
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
  border-color: #bcd0b2 !important;
  background: #ffffff !important;
}

.td-input .t-input__content {
  min-height: 52rpx;
}

.td-input-picker .t-input__content {
  padding-right: 30rpx;
}

.td-input .t-input__inner {
  outline: none !important;
  box-shadow: none !important;
}

.td-input.t-input--border::after,
.td-input .t-input--border::after {
  display: none !important;
}

.td-input:focus-within {
  border-color: var(--record-primary) !important;
  box-shadow: 0 0 0 2rpx rgba(115, 174, 82, 0.14) !important;
}

.td-input.is-error,
.td-input.is-error.t-input,
.td-input.is-error .t-input {
  border-color: var(--dh-color-danger) !important;
  box-shadow: 0 0 0 2rpx rgba(215, 75, 75, 0.12) !important;
}

.td-textarea {
  --td-textarea-background-color: #f6f8f4;
  --td-textarea-border-color: #dde4d6;
  --td-textarea-text-color: var(--record-text);
  --td-textarea-placeholder-color: var(--record-text-soft);
  --td-textarea-vertical-padding: 14rpx;
  --td-textarea-horizontal-padding: 18rpx;
  border-radius: 16rpx !important;
  border: 1rpx solid #dde4d6 !important;
  background: #f6f8f4 !important;
  box-shadow: none;
}

.td-textarea.t-textarea,
.td-textarea .t-textarea {
  border: 1rpx solid #dde4d6 !important;
  border-radius: 16rpx !important;
  background: #f6f8f4 !important;
}

.td-textarea .t-textarea__wrapper-inner {
  outline: none !important;
  box-shadow: none !important;
  min-height: 120rpx;
  line-height: 1.5;
}

.td-textarea.t-textarea--border::after,
.td-textarea .t-textarea--border::after {
  display: none !important;
}

.td-textarea:focus-within {
  border-color: var(--record-primary) !important;
  box-shadow: 0 0 0 2rpx rgba(115, 174, 82, 0.14) !important;
}

.td-textarea.is-error,
.td-textarea.is-error.t-textarea,
.td-textarea.is-error .t-textarea {
  border-color: var(--dh-color-danger) !important;
  box-shadow: 0 0 0 2rpx rgba(215, 75, 75, 0.12) !important;
}

.dynamic-item {
  margin-top: 18rpx;
}

.dynamic-item:first-child {
  margin-top: 0;
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

.dynamic-label-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.dynamic-label {
  display: block;
  font-size: 24rpx;
  color: var(--record-text-sub);
  margin-bottom: 8rpx;
}

.dynamic-label-row .dynamic-label {
  margin-bottom: 0;
}

.dynamic-label.is-required {
  color: var(--record-text);
  font-weight: 700;
}

.required-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
  background: var(--dh-color-danger);
}

.dynamic-picker-trigger {
  position: relative;
}

.dynamic-picker-trigger .td-input,
.dynamic-picker-trigger .t-input {
  pointer-events: none;
}

.dynamic-picker-arrow {
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

.field-error {
  margin-top: 6rpx;
  display: block;
  font-size: 22rpx;
  line-height: 1.4;
  color: var(--dh-color-danger);
}

.sub-tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--record-text-soft);
}

.weather-brief {
  margin-top: 6rpx;
  padding: 12rpx 14rpx;
  border-radius: 14rpx;
  background: var(--record-surface-soft);
  border: 1rpx solid var(--record-border);
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10rpx;
}

.weather-brief-text {
  flex: 1;
  font-size: 24rpx;
  color: var(--record-text-sub);
  line-height: 1.45;
}

.weather-detail {
  margin-top: 12rpx;
}

.weather-main-input {
  margin-bottom: 12rpx;
}

.weather-edit-card {
  padding: 14rpx;
  border: 1rpx solid #bcd0b2;
  border-radius: 16rpx;
  background: #ffffff;
}

.weather-display-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
}

.weather-display-item {
  min-width: 0;
  border: 1rpx solid #d9dfd3;
  border-radius: 14rpx;
  background: #f1f4ef;
  padding: 12rpx 14rpx;
}

.weather-display-item-readonly {
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.72);
}

.weather-display-item-full {
  grid-column: 1 / -1;
}

.weather-display-label {
  display: block;
  font-size: 22rpx;
  color: var(--record-text-soft);
  line-height: 1.35;
}

.weather-display-value {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #566455;
  line-height: 1.45;
  font-weight: 600;
}

.locate-btn-wrap {
  margin-top: 8rpx;
  display: flex;
}

.mt8 {
  margin-top: 8rpx;
}

.location-input-wrap {
  display: flex;
  flex-direction: column;
}

.record-photo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 14rpx;
}

.record-photo-item {
  position: relative;
  width: 196rpx;
  height: 196rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: var(--dh-color-brand-light);
}

.record-photo-item.is-pending {
  background: transparent;
}

.record-photo-img {
  width: 100%;
  height: 100%;
}

.record-photo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18rpx 16rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #fff8ed 0%, #fff3dd 100%);
  border: 1rpx solid #efd2a1;
}

.record-photo-placeholder-state {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: #b4731b;
  line-height: 1.4;
}

.record-photo-placeholder-tip {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #8a6a35;
  text-align: center;
}

.record-photo-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 42rpx;
  height: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top-right-radius: 16rpx;
  border-bottom-left-radius: 16rpx;
  background: rgba(15, 23, 42, 0.56);
  z-index: 2;
}

.record-upload {
  margin-top: 4rpx;
  --td-upload-radius: 14rpx;
  --td-upload-add-bg-color: #f6f8f4;
  --td-upload-add-color: #8d988c;
}

.record-upload .t-upload__wrapper {
  border: 1rpx solid var(--record-border);
}

.submit-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  z-index: 120;
  background: var(--record-submit-bg);
  border-top: 1rpx solid var(--record-border);
  box-shadow: none;
}

.submit-brief {
  display: block;
  margin-bottom: 8rpx;
  font-size: 22rpx;
  color: var(--record-text-sub);
  line-height: 1.35;
}

.submit-btn {
  --td-button-primary-bg-color: var(--record-primary);
  --td-button-primary-border-color: var(--record-primary);
  --td-button-primary-active-bg-color: var(--record-primary-press);
  --td-button-primary-active-border-color: var(--record-primary-press);
  --td-button-large-height: 86rpx;
  border-radius: 16rpx;
}

@media screen and (max-width: 768rpx) {
  .weather-display-grid {
    grid-template-columns: 1fr;
    gap: 10rpx;
  }
}

.elder-mode {
  .section-title {
    font-size: 40rpx;
  }

  .pick-title {
    font-size: 36rpx;
  }

  .pick-title-relation {
    font-size: 32rpx;
  }

  .sub-block-title {
    font-size: 34rpx;
  }

  .progress-stat-label,
  .progress-stat-value,
  .progress-stat-plain,
  .progress-ready-text,
  .record-steps .t-steps-item__title,
  .record-steps-pinned-label,
  .pick-meta,
  .dynamic-label,
  .weather-brief-text,
  .weather-display-label,
  .weather-display-value,
  .field-error,
  .meta,
  .sub-tip,
  .submit-brief,
  .readonly {
    font-size: 32rpx;
  }

  .t-tag,
  .t-tag__text {
    font-size: 30rpx !important;
    line-height: 1.45;
  }

  .td-input,
  .td-textarea {
    font-size: 32rpx;
  }

  .pick-card,
  .section-card {
    padding: 24rpx;
  }

  .pick-change-btn,
  .weather-refresh-btn,
  .locate-btn-wrap .t-button {
    min-height: 76rpx;
    font-size: 30rpx;
  }

  .weather-brief,
  .weather-edit-card,
  .weather-display-item {
    padding: 20rpx;
    border-radius: 20rpx;
  }

  .weather-display-grid {
    gap: 16rpx;
  }

  .record-photo-placeholder-state {
    font-size: 30rpx;
  }

  .record-photo-placeholder-tip {
    font-size: 28rpx;
  }

  .submit-btn {
    --td-button-large-height: 108rpx;
    font-size: 36rpx;
  }

  .record-navbar {
    --td-navbar-title-font-size: 44rpx;
  }

  .section-index {
    width: 48rpx;
    height: 48rpx;
    font-size: 28rpx;
  }
}
</style>
