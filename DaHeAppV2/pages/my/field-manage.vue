<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="田块定位与种植计划"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <scroll-view scroll-y class="content" :show-scrollbar="false">
      <view class="card">
        <text class="label">田块选择</text>
        <picker mode="selector" :range="fields" range-key="name" @change="onFieldChange">
          <view class="picker">
            <text v-if="selectedField">{{ selectedField.name }}</text>
            <text v-else class="placeholder">请选择田块</text>
          </view>
        </picker>
        <view class="row mt8">
          <button class="btn minor" @click="refreshFields">刷新田块</button>
          <button class="btn" @click="saveField" :disabled="!selectedField || saving">{{ saving ? '保存中...' : '保存田块信息' }}</button>
        </view>
      </view>

      <view class="card" v-if="selectedField">
        <text class="label">作物与检索信息</text>
        <view class="picker">{{ formatCropVarietyPair(form) }}</view>
        <view class="picker">{{ regionDisplayText || '未设置省市区' }}</view>

        <text class="tiny mt8">省份</text>
        <picker mode="selector" :range="provinceOptions" range-key="label" @change="onProvinceChange">
          <view class="picker">{{ selectedProvinceLabel }}</view>
        </picker>
        <text class="tiny mt8">城市</text>
        <picker mode="selector" :range="cityOptions" range-key="label" @change="onCityChange">
          <view class="picker">{{ selectedCityLabel }}</view>
        </picker>
        <text class="tiny mt8">区县</text>
        <picker mode="selector" :range="districtOptions" range-key="label" @change="onDistrictChange">
          <view class="picker">{{ selectedDistrictLabel }}</view>
        </picker>
        <text class="tiny mt8">乡镇</text>
        <picker mode="selector" :range="townshipOptions" range-key="label" @change="onTownshipChange">
          <view class="picker">{{ selectedTownshipLabel }}</view>
        </picker>
        <view class="sub-tip mt8">作物和品种由当前种植计划自动维护；田块基础信息维护省市区、乡镇与阶段。</view>

        <text class="label mt8">田块阶段</text>
        <picker mode="selector" :range="statusOptions" range-key="label" @change="onStatusChange">
          <view class="picker">{{ statusLabel }}</view>
        </picker>
      </view>

      <view class="card" v-if="selectedField">
        <view class="line between">
          <text class="label">定位信息</text>
          <text class="locate" @click="fillCurrentLocation">使用当前位置</text>
        </view>
        <input class="input" v-model="form.locationLat" placeholder="纬度" />
        <input class="input" v-model="form.locationLng" placeholder="经度" />
        <input class="input" v-model="form.locationDesc" placeholder="详细位置描述（可选）" />
      </view>

      <view class="card" v-if="selectedField">
        <view class="line between">
          <text class="label">田块封面图</text>
          <text class="locate" @click="loadCoverImageOptions">刷新图库</text>
        </view>
        <picker mode="selector" :range="coverImageOptions" range-key="label" @change="onCoverImageChange">
          <view class="picker">{{ selectedCoverImageLabel }}</view>
        </picker>
        <view class="row mt8">
          <button class="btn minor" @click="loadCoverImageOptions">重新加载图片</button>
          <button class="btn minor" @click="clearCoverImage">清空封面</button>
        </view>
        <image v-if="form.coverImageUrl" :src="form.coverImageUrl" class="cover-preview" mode="aspectFill" />
        <view class="sub-tip mt8">可在后台“图片与资源管理”中上传模块为 field 的图片后在此选择。</view>
      </view>

      <view class="card" v-if="selectedField">
        <view class="line between">
          <text class="label">种植计划</text>
          <text class="locate" @click="loadCycles">刷新计划</text>
        </view>
        <view v-if="!cycles.length" class="empty">暂无种植计划记录</view>
        <view v-for="c in cycles" :key="c.id" class="cycle-item">
          <view class="line between">
            <text class="cycle-name">{{ c.planName || c.cycleName }}</text>
            <text class="tag" :class="Number(c.isCurrent) === 1 ? 'current' : 'normal'">
              {{ cycleStatusText(c) }}
            </text>
          </view>
          <text class="meta">模式：{{ c.planModeText || formatPlanMode(c.planMode) }}</text>
          <text class="meta">作物品种：{{ formatCycleCrops(c) }}</text>
          <text class="meta">时间：{{ c.startDate || '-' }} ~ {{ c.endDate || '进行中' }}</text>
          <button class="small" v-if="Number(c.isCurrent) !== 1 && c.status !== 'completed'" @click="setCurrentCycle(c)">切换为当前计划</button>
        </view>
      </view>

      <view class="card" v-if="selectedField">
        <text class="label">新增种植计划</text>
        <input class="input" v-model="newCycle.cycleName" placeholder="计划名称（如：2026春玉米计划）" />
        <view class="sub-tip">建议每年建立一条计划；到期后自动结束，田块自动转为空闲。</view>

        <text class="tiny mt8">计划年份</text>
        <picker mode="selector" :range="planYearOptions" @change="onPlanYearPick">
          <view class="picker">{{ selectedPlanYearLabel }}</view>
        </picker>
        <view class="quick-row">
          <view class="quick-chip" @click="applyPlanYear('this')">今年</view>
          <view class="quick-chip" @click="applyPlanYear('next')">明年</view>
          <view class="quick-chip" @click="applyPlanYear('after')">后年</view>
        </view>

        <text class="tiny mt8">种植模式</text>
        <picker mode="selector" :range="planModeOptions" range-key="label" @change="onPlanModeChange">
          <view class="picker">{{ selectedPlanModeLabel }}</view>
        </picker>

        <text class="tiny mt8">计划作物（可多条）</text>
        <view class="plan-crop-list">
          <view class="plan-crop-row" v-for="(crop, idx) in newCycle.crops" :key="`crop-${idx}`">
            <picker mode="selector" :range="cropOptions" range-key="label" @change="onCycleCropPick($event, idx)">
              <view class="picker">{{ cycleCropLabel(crop) }}</view>
            </picker>
            <picker
              mode="selector"
              :range="cycleVarietyOptions(crop.cropType)"
              range-key="label"
              @change="onCycleVarietyPick($event, idx)"
            >
              <view class="picker">{{ cycleVarietyLabel(crop) }}</view>
            </picker>
            <picker
              mode="selector"
              :range="cycleTemplateOptions(crop)"
              range-key="label"
              @change="onCycleTemplatePick($event, idx)"
            >
              <view class="picker">{{ cycleTemplateLabel(crop) }}</view>
            </picker>
            <button class="small remove-btn" :disabled="newCycle.crops.length <= 1" @click="removeCycleCrop(idx)">删除</button>
          </view>
        </view>
        <button class="btn minor add-btn" @click="addCycleCrop">+ 添加作物</button>

        <view class="line gap">
          <view class="half">
            <text class="tiny">开始日期</text>
            <picker mode="date" :value="newCycle.startDate" @change="onStartDateChange">
              <view class="picker">{{ newCycle.startDate || '选择日期' }}</view>
            </picker>
          </view>
          <view class="half">
            <text class="tiny">结束日期</text>
            <picker mode="date" :value="newCycle.endDate" @change="onEndDateChange">
              <view class="picker">{{ newCycle.endDate || '可空' }}</view>
            </picker>
          </view>
        </view>
        <view class="quick-row">
          <view class="quick-chip" @click="setPlanDateQuick('start_today')">开始=今天</view>
          <view class="quick-chip" @click="setPlanDateQuick('end_year')">结束=年末</view>
          <view class="quick-chip" @click="setPlanDateQuick('clear_end')">清空结束</view>
        </view>

        <view class="line between mt8">
          <text class="tiny">创建后设为当前计划</text>
          <switch :checked="newCycle.isCurrent" @change="onCycleCurrentChange" color="#73AE52" />
        </view>

        <button class="btn" @click="createCycle">创建计划</button>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../../utils/request'
import {
  getCurrentLocation,
  fetchProvinceOptions,
  fetchCityOptions,
  fetchDistrictOptions,
  fetchTownshipOptions
} from '../../utils/amap'
import { canUseConsole, isApprovedUser } from '../../utils/auth'
import { formatCropVarietyPair as formatCropVarietyPairText } from '../../utils/crop-variety'
import { markDataChanged, refreshTopics } from '../../utils/data-refresh'

import elderPageMixin from '../../utils/elder-page'
const DEFAULT_PROVINCES = [
  '北京市', '天津市', '上海市', '重庆市',
  '河北省', '山西省', '辽宁省', '吉林省', '黑龙江省',
  '江苏省', '浙江省', '安徽省', '福建省', '江西省', '山东省',
  '河南省', '湖北省', '湖南省', '广东省', '海南省',
  '四川省', '贵州省', '云南省', '陕西省', '甘肃省', '青海省',
  '内蒙古自治区', '广西壮族自治区', '西藏自治区', '宁夏回族自治区', '新疆维吾尔自治区',
  '香港特别行政区', '澳门特别行政区', '台湾省'
]

const emptyForm = () => ({
  name: '',
  areaMu: '',
  cropType: '',
  cropVariety: '',
  province: '',
  city: '',
  district: '',
  township: '',
  status: 'idle',
  locationLat: '',
  locationLng: '',
  locationDesc: '',
  coverImageUrl: '',
  remark: ''
})

export default {
  mixins: [elderPageMixin],
  data() {
    const nowYear = new Date().getFullYear()
    return {
      fields: [],
      selectedFieldIndex: -1,
      form: emptyForm(),
      cycles: [],
      saving: false,
      cropTree: [],
      cropOptions: [{ value: '', label: '请选择作物' }],
      varietyOptions: [{ value: '', label: '请选择品种' }],
      allVarietyOptions: [{ value: '', label: '请选择品种' }],
      cropNameToCategoryId: {},
      varietyNameToCropName: {},
      varietyNameToCategoryId: {},
      varietyNameToId: {},
      metaTownshipOptions: [],
      provinceOptions: [{ value: '', label: '请选择省份' }, ...DEFAULT_PROVINCES.map((x) => ({ value: x, label: x }))],
      cityOptions: [{ value: '', label: '请选择城市' }],
      districtOptions: [{ value: '', label: '请选择区县' }],
      townshipOptions: [{ value: '', label: '请选择乡镇' }],
      coverImageOptions: [{ value: '', label: '不设置封面图' }],
      cycleVarietyOptionsByCrop: {},
      selectedCropIndex: 0,
      selectedVarietyIndex: 0,
      selectedProvinceIndex: 0,
      selectedCityIndex: 0,
      selectedDistrictIndex: 0,
      selectedTownshipIndex: 0,
      selectedCoverImageIndex: 0,
      templateOptions: [],
      statusOptions: [
        { value: 'sowing', label: '播种阶段' },
        { value: 'growing', label: '生长阶段' },
        { value: 'harvesting', label: '收获阶段' },
        { value: 'idle', label: '空闲阶段' },
        { value: 'fallow', label: '休耕阶段' }
      ],
      planModeOptions: [
        { value: 'single', label: '单作' },
        { value: 'rotation', label: '轮作' },
        { value: 'intercropping', label: '间作' },
        { value: 'relay', label: '套作' },
        { value: 'mixed', label: '混作' },
        { value: 'fallow', label: '休耕' },
        { value: 'custom', label: '自定义' }
      ],
      planYearOptions: [nowYear - 1, nowYear, nowYear + 1, nowYear + 2],
      selectedPlanYearIndex: 1,
      selectedPlanModeIndex: 0,
      newCycle: {
        cycleName: '',
        crops: [{ cropType: '', cropVariety: '', templateId: null }],
        startDate: `${nowYear}-01-01`,
        endDate: `${nowYear}-12-31`,
        isCurrent: true,
        planMode: 'single',
        planYear: nowYear
      }
    }
  },
  computed: {
    selectedField() {
      if (this.selectedFieldIndex < 0) return null
      return this.fields[this.selectedFieldIndex] || null
    },
    selectedCropLabel() {
      if (this.form.cropType) return this.form.cropType
      return this.cropOptions[this.selectedCropIndex]?.label || '请选择作物'
    },
    selectedVarietyLabel() {
      if (this.form.cropVariety) return this.form.cropVariety
      return this.varietyOptions[this.selectedVarietyIndex]?.label || '请选择品种'
    },
    selectedProvinceLabel() {
      if (this.form.province) return this.form.province
      return this.provinceOptions[this.selectedProvinceIndex]?.label || '请选择省份'
    },
    selectedCityLabel() {
      if (this.form.city) return this.form.city
      return this.cityOptions[this.selectedCityIndex]?.label || '请选择城市'
    },
    selectedDistrictLabel() {
      if (this.form.district) return this.form.district
      return this.districtOptions[this.selectedDistrictIndex]?.label || '请选择区县'
    },
    selectedTownshipLabel() {
      if (this.form.township) return this.form.township
      return this.townshipOptions[this.selectedTownshipIndex]?.label || '请选择乡镇'
    },
    selectedCoverImageLabel() {
      if (!this.form.coverImageUrl) {
        return this.coverImageOptions[0]?.label || '不设置封面图'
      }
      const hit = this.coverImageOptions.find((x) => x.value === this.form.coverImageUrl)
      if (hit) return hit.label
      return '当前封面图（未在图库中）'
    },
    statusLabel() {
      const row = this.statusOptions.find((x) => x.value === this.form.status)
      return row ? row.label : '请选择阶段'
    },
    selectedPlanModeLabel() {
      const row = this.planModeOptions[this.selectedPlanModeIndex]
      return row ? row.label : '单作'
    },
    selectedPlanYearLabel() {
      const year = this.newCycle.planYear || this.planYearOptions[this.selectedPlanYearIndex]
      return year ? `${year}年` : '请选择年份'
    },
    regionDisplayText() {
      const values = [
        this.form.province,
        this.form.city,
        this.form.district,
        this.form.township
      ]
        .map((x) => String(x || '').trim())
        .filter(Boolean)
      return values.join('-')
    }
  },
  async onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    if (!canUseConsole()) {
      uni.showToast({ title: '当前账号未开通控制台权限', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 220)
      return
    }
    await this.refreshFields()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    async loadMetaOptions() {
      try {
        const [treeRows, townships] = await Promise.all([
          api.get('/miniapp/meta/options/crop-tree', {}),
          api.get('/miniapp/meta/options/townships', {})
        ])
        const tree = Array.isArray(treeRows) ? treeRows : []
        const cropRows = []
        const varietyRows = []
        const cropNameToCategoryId = {}
        const varietyNameToCropName = {}
        const varietyNameToCategoryId = {}
        const varietyNameToId = {}
        const cycleVarietyOptionsByCrop = {}

        tree.forEach((item) => {
          const cropName = String((item && item.categoryName) || '').trim()
          const categoryId = Number((item && item.categoryId) || 0)
          if (!cropName) return
          cropRows.push(cropName)
          if (categoryId > 0) {
            cropNameToCategoryId[cropName] = categoryId
          }
          const child = Array.isArray(item && item.varieties) ? item.varieties : []
          const names = []
          child.forEach((row) => {
            const varietyName = String((row && row.name) || '').trim()
            const varietyId = Number((row && row.id) || 0)
            if (!varietyName) return
            names.push(varietyName)
            varietyRows.push(varietyName)
            if (!varietyNameToCropName[varietyName]) {
              varietyNameToCropName[varietyName] = cropName
            }
            if (!varietyNameToCategoryId[varietyName] && categoryId > 0) {
              varietyNameToCategoryId[varietyName] = categoryId
            }
            if (!varietyNameToId[varietyName] && varietyId > 0) {
              varietyNameToId[varietyName] = varietyId
            }
          })
          const uniqueNames = Array.from(new Set(names))
          cycleVarietyOptionsByCrop[cropName] = uniqueNames.length
            ? [{ value: '', label: '请选择品种' }, ...uniqueNames.map((x) => ({ value: x, label: x }))]
            : [{ value: '', label: '请选择品种' }]
        })

        const townshipRows = Array.from(new Set((Array.isArray(townships) ? townships : []).map((x) => String(x || '').trim()).filter(Boolean)))
        const uniqueCrops = Array.from(new Set(cropRows))
        const uniqueVarieties = Array.from(new Set(varietyRows))
        this.cropTree = tree
        this.cropNameToCategoryId = cropNameToCategoryId
        this.varietyNameToCropName = varietyNameToCropName
        this.varietyNameToCategoryId = varietyNameToCategoryId
        this.varietyNameToId = varietyNameToId
        this.cycleVarietyOptionsByCrop = cycleVarietyOptionsByCrop
        this.cropOptions = [{ value: '', label: '请选择作物' }, ...uniqueCrops.map((x) => ({ value: x, label: x }))]
        this.allVarietyOptions = [{ value: '', label: '请选择品种' }, ...uniqueVarieties.map((x) => ({ value: x, label: x }))]
        this.varietyOptions = [...this.allVarietyOptions]
        this.metaTownshipOptions = townshipRows.map((x) => ({ value: x, label: x }))
        this.townshipOptions = [{ value: '', label: '请选择乡镇' }, ...this.metaTownshipOptions]
        await this.syncRegionOptionsByForm()
      } catch (e) {
        console.error('加载元选项失败', e)
        this.metaTownshipOptions = []
        this.provinceOptions = [{ value: '', label: '请选择省份' }, ...DEFAULT_PROVINCES.map((x) => ({ value: x, label: x }))]
        this.cityOptions = [{ value: '', label: '请选择城市' }]
        this.districtOptions = [{ value: '', label: '请选择区县' }]
        this.townshipOptions = [{ value: '', label: '请选择乡镇' }]
      }
    },
    async loadVarietyOptions(cropName) {
      const crop = String(cropName || '').trim()
      if (!crop) {
        this.varietyOptions = [...this.allVarietyOptions]
        return
      }
      const rows = this.cycleVarietyOptionsByCrop[crop]
      this.varietyOptions = Array.isArray(rows) && rows.length ? [...rows] : [...this.allVarietyOptions]
    },
    async loadTemplateOptions() {
      try {
        const data = await api.get('/miniapp/farm-process/templates', {
          page: 1,
          pageSize: 500,
          includeSteps: false
        })
        this.templateOptions = (data && data.records) || []
      } catch (e) {
        console.error('加载流程模板失败', e)
        this.templateOptions = []
      }
    },
    async loadCoverImageOptions() {
      const fallback = [{ value: '', label: '不设置封面图' }]
      try {
        const [fieldAssets, commonAssets] = await Promise.all([
          api.get('/miniapp/assets', { moduleKey: 'field', fileType: 'image', page: 1, pageSize: 300 }),
          api.get('/miniapp/assets', { fileType: 'image', page: 1, pageSize: 300 })
        ])
        const merged = []
        const exists = new Set()
        const pushRow = (row) => {
          const url = String((row && row.fileUrl) || '').trim()
          if (!url || exists.has(url)) return
          exists.add(url)
          const name = String((row && row.fileName) || '').trim() || `图片${exists.size}`
          const moduleKey = String((row && row.moduleKey) || '').trim()
          const label = moduleKey ? `${name}（${this.formatAssetModuleLabel(moduleKey)}）` : name
          merged.push({ value: url, label })
        }
        ;((fieldAssets && fieldAssets.records) || []).forEach(pushRow)
        ;((commonAssets && commonAssets.records) || []).forEach(pushRow)
        this.coverImageOptions = fallback.concat(merged)
      } catch (e) {
        console.error('加载封面图库失败', e)
        this.coverImageOptions = fallback
      }
      this.syncCoverImageIndex()
    },
    formatAssetModuleLabel(moduleKey) {
      const key = String(moduleKey || '').trim()
      const map = {
        field: '田块',
        farm: '农事',
        seed: '种子',
        export: '导出',
        system: '系统',
        amap: '高德',
        auth: '认证'
      }
      if (!key) return '未归类'
      return map[key] ? `${map[key]}-${key}` : key
    },
    mergeOptionRows(firstRows, secondRows = []) {
      const out = []
      const exists = new Set()
      const push = (row) => {
        if (!row) return
        const value = String((row && row.value) || '').trim()
        if (!value || exists.has(value)) return
        exists.add(value)
        out.push({
          value,
          label: String((row && row.label) || value).trim() || value
        })
      }
      ;(Array.isArray(firstRows) ? firstRows : []).forEach(push)
      ;(Array.isArray(secondRows) ? secondRows : []).forEach(push)
      return out
    },
    buildOptions(rows, placeholder) {
      const merged = this.mergeOptionRows(rows, [])
      return [{ value: '', label: placeholder }, ...merged]
    },
    ensureDynamicOption(options, value) {
      const text = String(value || '').trim()
      if (!text) return options
      const list = Array.isArray(options) ? [...options] : []
      const hit = list.some((item) => String((item && item.value) || '').trim() === text)
      if (!hit) {
        list.push({ value: text, label: text })
      }
      return list
    },
    findOptionIndex(options, value) {
      const target = String(value || '').trim()
      const list = Array.isArray(options) ? options : []
      if (!target) return 0
      const idx = list.findIndex((x) => String((x && x.value) || '').trim() === target)
      return idx >= 0 ? idx : 0
    },
    async loadProvinceOptions() {
      let rows = []
      try {
        rows = await fetchProvinceOptions('')
      } catch (e) {
        rows = []
      }
      let options = this.buildOptions(rows, '请选择省份')
      options = this.ensureDynamicOption(options, this.form.province)
      this.provinceOptions = options
      this.selectedProvinceIndex = this.findOptionIndex(options, this.form.province)
    },
    async loadCityOptions() {
      const province = String(this.form.province || '').trim()
      let rows = []
      if (province) {
        try {
          rows = await fetchCityOptions(province)
        } catch (e) {
          rows = []
        }
      }
      let options = this.buildOptions(rows, '请选择城市')
      options = this.ensureDynamicOption(options, this.form.city)
      this.cityOptions = options
      this.selectedCityIndex = this.findOptionIndex(options, this.form.city)
    },
    async loadDistrictOptions() {
      const city = String(this.form.city || '').trim()
      let rows = []
      if (city) {
        try {
          rows = await fetchDistrictOptions(city)
        } catch (e) {
          rows = []
        }
      }
      let options = this.buildOptions(rows, '请选择区县')
      options = this.ensureDynamicOption(options, this.form.district)
      this.districtOptions = options
      this.selectedDistrictIndex = this.findOptionIndex(options, this.form.district)
    },
    async loadTownshipOptions() {
      const district = String(this.form.district || '').trim()
      let amapRows = []
      if (district) {
        try {
          amapRows = await fetchTownshipOptions(district)
        } catch (e) {
          amapRows = []
        }
      }
      const merged = this.mergeOptionRows(amapRows, this.metaTownshipOptions)
      let options = this.buildOptions(merged, '请选择乡镇')
      options = this.ensureDynamicOption(options, this.form.township)
      this.townshipOptions = options
      this.selectedTownshipIndex = this.findOptionIndex(options, this.form.township)
    },
    async syncRegionOptionsByForm() {
      await this.loadProvinceOptions()
      await this.loadCityOptions()
      await this.loadDistrictOptions()
      await this.loadTownshipOptions()
    },
    async refreshFields() {
      try {
        await Promise.all([this.loadMetaOptions(), this.loadTemplateOptions(), this.loadCoverImageOptions()])
        const data = await api.get('/miniapp/fields', { page: 1, pageSize: 500, includeDisabled: true })
        this.fields = (data && data.records) || []
        if (this.fields.length) {
          if (this.selectedFieldIndex < 0 || this.selectedFieldIndex >= this.fields.length) {
            this.selectedFieldIndex = 0
          }
          await this.fillFieldForm(this.fields[this.selectedFieldIndex])
          await this.loadCycles()
        }
      } catch (e) {
        console.error('加载田块失败', e)
      }
    },
    async onFieldChange(e) {
      this.selectedFieldIndex = Number(e.detail.value)
      await this.fillFieldForm(this.selectedField)
      await this.loadCycles()
    },
    async fillFieldForm(field) {
      if (!field) {
        this.form = emptyForm()
        return
      }
      const groups = Array.isArray(field.cropVarietyGroups) ? field.cropVarietyGroups : []
      const primary = groups.length ? (groups[0] || {}) : {}
      const primaryCropType = String((primary.cropType || primary.cropName || primary.name || '')).trim()
      const primaryCropVariety = String((primary.cropVariety || primary.varietyName || primary.variety || '')).trim()
      this.form = {
        name: field.name || '',
        areaMu: field.areaMu != null ? String(field.areaMu) : '',
        cropType: primaryCropType || field.cropType || '',
        cropVariety: primaryCropVariety || field.cropVariety || '',
        province: field.province || '',
        city: field.city || '',
        district: field.district || '',
        township: field.township || '',
        status: field.status || 'idle',
        locationLat: field.locationLat != null ? String(field.locationLat) : '',
        locationLng: field.locationLng != null ? String(field.locationLng) : '',
        locationDesc: field.locationDesc || '',
        coverImageUrl: field.coverImageUrl || '',
        remark: field.remark || ''
      }
      if (!this.form.cropType && this.form.cropVariety) {
        this.form.cropType = this.resolveCropNameByVariety(this.form.cropVariety)
      }
      this.syncMetaIndexes()
      this.loadVarietyOptions(this.form.cropType || null)
      const presetCrop = this.form.cropType || ''
      const presetVariety = this.form.cropVariety || ''
      this.newCycle.crops = [{
        cropType: presetCrop,
        cropVariety: presetVariety,
        templateId: this.defaultCycleTemplateId(presetCrop, presetVariety)
      }]
      await this.syncRegionOptionsByForm()
    },
    syncMetaIndexes() {
      const cropIdx = this.cropOptions.findIndex((x) => x.value === this.form.cropType)
      const varietyIdx = this.varietyOptions.findIndex((x) => x.value === this.form.cropVariety)
      this.selectedCropIndex = cropIdx >= 0 ? cropIdx : 0
      this.selectedVarietyIndex = varietyIdx >= 0 ? varietyIdx : 0
      this.selectedProvinceIndex = this.findOptionIndex(this.provinceOptions, this.form.province)
      this.selectedCityIndex = this.findOptionIndex(this.cityOptions, this.form.city)
      this.selectedDistrictIndex = this.findOptionIndex(this.districtOptions, this.form.district)
      this.selectedTownshipIndex = this.findOptionIndex(this.townshipOptions, this.form.township)
      this.syncCoverImageIndex()
    },
    syncCoverImageIndex() {
      const target = String(this.form.coverImageUrl || '').trim()
      const idx = this.coverImageOptions.findIndex((x) => String((x && x.value) || '') === target)
      this.selectedCoverImageIndex = idx >= 0 ? idx : 0
    },
    async onCropChange(e) {
      const idx = Number(e.detail.value)
      this.selectedCropIndex = idx
      const row = this.cropOptions[idx]
      this.form.cropType = row ? row.value : ''
      await this.loadVarietyOptions(this.form.cropType || null)
      if (this.form.cropVariety) {
        const exists = this.varietyOptions.some((x) => x.value === this.form.cropVariety)
        if (!exists) {
          this.form.cropVariety = ''
          this.selectedVarietyIndex = 0
        }
      }
    },
    onVarietyChange(e) {
      const idx = Number(e.detail.value)
      this.selectedVarietyIndex = idx
      const row = this.varietyOptions[idx]
      const variety = row ? row.value : ''
      this.form.cropVariety = variety
      if (variety) {
        const hitCrop = this.resolveCropNameByVariety(variety)
        if (hitCrop && hitCrop !== this.form.cropType) {
          this.form.cropType = hitCrop
          this.loadVarietyOptions(hitCrop)
          this.syncMetaIndexes()
        }
      }
    },
    async onProvinceChange(e) {
      const idx = Number(e.detail.value)
      this.selectedProvinceIndex = idx
      const row = this.provinceOptions[idx]
      this.form.province = row ? row.value : ''
      this.form.city = ''
      this.form.district = ''
      this.form.township = ''
      await this.loadCityOptions()
      await this.loadDistrictOptions()
      await this.loadTownshipOptions()
    },
    async onCityChange(e) {
      const idx = Number(e.detail.value)
      this.selectedCityIndex = idx
      const row = this.cityOptions[idx]
      this.form.city = row ? row.value : ''
      this.form.district = ''
      this.form.township = ''
      await this.loadDistrictOptions()
      await this.loadTownshipOptions()
    },
    async onDistrictChange(e) {
      const idx = Number(e.detail.value)
      this.selectedDistrictIndex = idx
      const row = this.districtOptions[idx]
      this.form.district = row ? row.value : ''
      this.form.township = ''
      await this.loadTownshipOptions()
    },
    onTownshipChange(e) {
      const idx = Number(e.detail.value)
      this.selectedTownshipIndex = idx
      const row = this.townshipOptions[idx]
      this.form.township = row ? row.value : ''
    },
    onCoverImageChange(e) {
      const idx = Number(e.detail.value)
      this.selectedCoverImageIndex = idx
      const row = this.coverImageOptions[idx]
      this.form.coverImageUrl = row ? row.value : ''
    },
    clearCoverImage() {
      this.form.coverImageUrl = ''
      this.syncCoverImageIndex()
    },
    onStatusChange(e) {
      const idx = Number(e.detail.value)
      const row = this.statusOptions[idx]
      if (row) this.form.status = row.value
    },
    ensureTownshipOption(value) {
      const text = String(value || '').trim()
      if (!text) return
      this.townshipOptions = this.ensureDynamicOption(this.townshipOptions, text)
      this.form.township = text
      this.selectedTownshipIndex = this.findOptionIndex(this.townshipOptions, text)
    },
    async fillCurrentLocation() {
      try {
        const location = await getCurrentLocation()
        this.form.locationLat = String(location.latitude || '')
        this.form.locationLng = String(location.longitude || '')
        uni.showToast({ title: '已填入当前位置经纬度', icon: 'none' })
      } catch (e) {
        console.error('获取定位失败', e)
        uni.showToast({ title: '定位失败，请检查定位授权', icon: 'none' })
      }
    },
    async saveField() {
      if (!this.selectedField) return
      this.saving = true
      try {
        const cropType = String(this.form.cropType || '').trim()
        const cropVariety = String(this.form.cropVariety || '').trim()
        const hasCropSelection = !!(cropType || cropVariety)
        const payload = {
          name: this.form.name,
          areaMu: this.form.areaMu ? Number(this.form.areaMu) : 0,
          cropType: hasCropSelection ? cropType : null,
          cropVariety: hasCropSelection ? cropVariety : null,
          cropVarietyGroups: hasCropSelection ? [{ cropType, cropVariety }] : null,
          province: this.form.province || '',
          city: this.form.city || '',
          district: this.form.district || '',
          township: this.form.township,
          status: this.form.status || 'idle',
          locationLat: this.form.locationLat ? Number(this.form.locationLat) : null,
          locationLng: this.form.locationLng ? Number(this.form.locationLng) : null,
          locationDesc: this.form.locationDesc,
          coverImageUrl: this.form.coverImageUrl,
          remark: this.form.remark
        }
        await api.put(`/miniapp/fields/${this.selectedField.id}`, payload)
        markDataChanged([
          refreshTopics.fields(),
          refreshTopics.fieldDetail(this.selectedField.id)
        ])
        uni.showToast({ title: '保存成功', icon: 'success' })
        await this.refreshFields()
      } catch (e) {
        console.error('保存田块失败', e)
      } finally {
        this.saving = false
      }
    },
    async loadCycles() {
      if (!this.selectedField) return
      try {
        this.cycles = await api.get(`/miniapp/fields/${this.selectedField.id}/cycles`)
      } catch (e) {
        console.error('加载种植计划失败', e)
        this.cycles = []
      }
    },
    cycleStatusText(cycle) {
      if (!cycle) return '-'
      const status = String(cycle.status || '').toLowerCase()
      if (status === 'completed') return Number(cycle.isCurrent) === 1 ? '当前计划（已结束）' : '已结束'
      return Number(cycle.isCurrent) === 1 ? '当前计划（进行中）' : '进行中'
    },
    async setCurrentCycle(cycle) {
      if (!this.selectedField || !cycle || !cycle.id) return
      try {
        await api.put(`/miniapp/fields/${this.selectedField.id}/cycles/${cycle.id}/current`, {})
        markDataChanged(refreshTopics.fieldDetail(this.selectedField.id))
        uni.showToast({ title: '已切换当前计划', icon: 'success' })
        await this.loadCycles()
      } catch (e) {
        console.error('切换计划失败', e)
      }
    },
    formatPlanMode(mode) {
      const row = this.planModeOptions.find((x) => x.value === mode)
      return row ? row.label : '单作'
    },
    formatCropVarietyPair(row) {
      return formatCropVarietyPairText(row, '当前计划未设置作物品种')
    },
    parseCycleCrops(cycle) {
      const text = String((cycle && cycle.cropsJson) || '').trim()
      if (!text) return []
      try {
        const rows = JSON.parse(text)
        if (!Array.isArray(rows)) return []
        return rows
          .map((item) => ({
            name: String((item && item.name) || '').trim(),
            variety: String((item && item.variety) || '').trim()
          }))
          .filter((item) => item.name || item.variety)
      } catch (error) {
        return []
      }
    },
    formatCycleCrops(cycle) {
      const rows = this.parseCycleCrops(cycle)
      if (rows.length) {
        return rows
          .map((item) => {
            if (item.name && item.variety) return `${item.name} · ${item.variety}`
            return item.name || item.variety
          })
          .filter(Boolean)
          .join('、')
      }
      const text = String((cycle && cycle.cropsText) || '').trim()
      if (!text) return '-'
      return text.replace(/[·•]/g, ' · ').replace(/\//g, '、')
    },
    resolveCropNameByVariety(varietyName) {
      const key = String(varietyName || '').trim()
      return key ? String(this.varietyNameToCropName[key] || '').trim() : ''
    },
    resolveCategoryId(cropName, varietyName) {
      const vKey = String(varietyName || '').trim()
      if (vKey && Number(this.varietyNameToCategoryId[vKey] || 0) > 0) {
        return Number(this.varietyNameToCategoryId[vKey])
      }
      const cKey = String(cropName || '').trim()
      if (cKey && Number(this.cropNameToCategoryId[cKey] || 0) > 0) {
        return Number(this.cropNameToCategoryId[cKey])
      }
      return 0
    },
    resolveVarietyId(varietyName) {
      const key = String(varietyName || '').trim()
      if (!key) return 0
      return Number(this.varietyNameToId[key] || 0)
    },
    isTemplateMatched(tpl, cropType, cropVariety) {
      if (!tpl) return false
      const selectedCrop = String(cropType || '').trim()
      const selectedVariety = String(cropVariety || '').trim()
      const selectedCategoryId = this.resolveCategoryId(selectedCrop, selectedVariety)
      const selectedVarietyId = this.resolveVarietyId(selectedVariety)
      const tplCategoryId = Number((tpl && tpl.categoryId) || 0)
      const tplVarietyId = Number((tpl && tpl.varietyId) || 0)
      const tplCategoryName = String((tpl && (tpl.categoryName || tpl.cropName)) || '').trim()
      const tplVarietyName = String((tpl && tpl.varietyName) || '').trim()

      if (selectedCategoryId > 0) {
        if (tplCategoryId > 0 && tplCategoryId !== selectedCategoryId) {
          return false
        }
        if (!(tplCategoryId > 0) && selectedCrop && tplCategoryName && tplCategoryName !== selectedCrop) {
          return false
        }
      } else if (selectedCrop && tplCategoryName && tplCategoryName !== selectedCrop) {
        return false
      }

      if (selectedVarietyId > 0) {
        return !(tplVarietyId > 0) || tplVarietyId === selectedVarietyId
      }
      if (selectedVariety) {
        return !tplVarietyName || tplVarietyName === selectedVariety
      }
      return !(tplVarietyId > 0) && !tplVarietyName
    },
    cycleTemplateOptions(crop) {
      const cropType = String((crop && crop.cropType) || '').trim()
      const cropVariety = String((crop && crop.cropVariety) || '').trim()
      if (!cropType && !cropVariety) return []
      const target = (this.templateOptions || []).filter((tpl) => this.isTemplateMatched(tpl, cropType, cropVariety))
      return target.map((tpl) => ({
        value: Number(tpl.id),
        label: this.templateOptionLabel(tpl)
      }))
    },
    templateOptionLabel(tpl) {
      if (!tpl) return '-'
      const category = String((tpl.categoryName || tpl.cropName || '')).trim() || '作物'
      const variety = String((tpl.varietyName || '')).trim()
      const bind = variety ? `${category} · ${variety}` : `${category}（通用）`
      const defaultText = Number(tpl.isDefault) === 1 ? '（默认）' : ''
      return `${tpl.templateName || '未命名模板'} / ${bind}${defaultText}`
    },
    cycleTemplateLabel(crop) {
      const templateId = Number((crop && crop.templateId) || 0)
      if (!(templateId > 0)) return '请选择流程模板'
      const hit = (this.templateOptions || []).find((tpl) => Number(tpl.id) === templateId)
      return this.templateOptionLabel(hit || null)
    },
    defaultCycleTemplateId(cropName, cropVariety) {
      const rows = this.cycleTemplateOptions({
        cropType: cropName,
        cropVariety
      })
      if (!rows.length) return null
      const optionIds = rows.map((x) => Number(x.value)).filter((x) => Number.isFinite(x) && x > 0)
      if (!optionIds.length) return null
      const preferred = optionIds.find((id) => {
        const hit = (this.templateOptions || []).find((tpl) => Number(tpl.id) === id)
        return Number((hit && hit.isDefault) || 0) === 1
      })
      return preferred || optionIds[0]
    },
    cycleCropLabel(crop) {
      if (crop && crop.cropType) return crop.cropType
      return '请选择作物'
    },
    cycleVarietyLabel(crop) {
      if (crop && crop.cropVariety) return crop.cropVariety
      return '请选择品种'
    },
    cycleVarietyOptions(cropName) {
      const name = String(cropName || '').trim()
      if (!name) return this.allVarietyOptions
      const rows = this.cycleVarietyOptionsByCrop[name]
      return Array.isArray(rows) && rows.length ? rows : this.allVarietyOptions
    },
    onCycleCropPick(e, idx) {
      const pick = Number(e.detail.value)
      const row = this.cropOptions[pick]
      const cropType = row ? row.value : ''
      const next = [...(this.newCycle.crops || [])]
      const current = next[idx] || { cropType: '', cropVariety: '', templateId: null }
      current.cropType = cropType
      current.cropVariety = ''
      current.templateId = this.defaultCycleTemplateId(cropType, '')
      next.splice(idx, 1, current)
      this.newCycle.crops = next
    },
    onCycleVarietyPick(e, idx) {
      const row = this.newCycle.crops[idx]
      if (!row) return
      const options = this.cycleVarietyOptions(row.cropType)
      const pick = Number(e.detail.value)
      const option = options[pick]
      const cropVariety = option ? option.value : ''
      let cropType = row.cropType
      if (cropVariety) {
        const hitCrop = this.resolveCropNameByVariety(cropVariety)
        if (hitCrop) {
          cropType = hitCrop
        }
      }
      const next = [...(this.newCycle.crops || [])]
      next[idx] = {
        ...row,
        cropType,
        cropVariety,
        templateId: this.defaultCycleTemplateId(cropType, cropVariety)
      }
      this.newCycle.crops = next
    },
    onCycleTemplatePick(e, idx) {
      const row = this.newCycle.crops[idx]
      if (!row) return
      const options = this.cycleTemplateOptions(row)
      const pick = Number(e.detail.value)
      const option = options[pick]
      const next = [...(this.newCycle.crops || [])]
      next[idx] = { ...row, templateId: option ? Number(option.value) : null }
      this.newCycle.crops = next
    },
    addCycleCrop() {
      this.newCycle.crops = [...(this.newCycle.crops || []), { cropType: '', cropVariety: '', templateId: null }]
    },
    removeCycleCrop(idx) {
      const rows = [...(this.newCycle.crops || [])]
      if (rows.length <= 1) return
      rows.splice(idx, 1)
      this.newCycle.crops = rows
    },
    firstCycleCropName() {
      const rows = this.newCycle.crops || []
      for (const item of rows) {
        const name = String((item && item.cropType) || '').trim()
        if (name) return name
        const variety = String((item && item.cropVariety) || '').trim()
        if (variety) {
          const cropByVariety = this.resolveCropNameByVariety(variety)
          if (cropByVariety) return cropByVariety
        }
      }
      return ''
    },
    onStartDateChange(e) {
      this.newCycle.startDate = e.detail.value
    },
    onEndDateChange(e) {
      this.newCycle.endDate = e.detail.value
    },
    setPlanDateQuick(type) {
      const now = new Date()
      const y = now.getFullYear()
      const m = String(now.getMonth() + 1).padStart(2, '0')
      const d = String(now.getDate()).padStart(2, '0')
      if (type === 'start_today') {
        this.newCycle.startDate = `${y}-${m}-${d}`
        return
      }
      if (type === 'end_year') {
        const year = Number(this.newCycle.planYear || y)
        this.newCycle.endDate = `${year}-12-31`
        if (!this.newCycle.startDate) {
          this.newCycle.startDate = `${year}-01-01`
        }
        return
      }
      if (type === 'clear_end') {
        this.newCycle.endDate = ''
      }
    },
    onCycleCurrentChange(e) {
      this.newCycle.isCurrent = !!e.detail.value
    },
    onPlanYearPick(e) {
      const idx = Number(e.detail.value)
      this.selectedPlanYearIndex = idx
      const year = this.planYearOptions[idx]
      if (!year) return
      this.newCycle.planYear = year
      this.newCycle.startDate = `${year}-01-01`
      this.newCycle.endDate = `${year}-12-31`
      if (!String(this.newCycle.cycleName || '').trim()) {
        const crop = this.form.cropType || this.firstCycleCropName() || '作物'
        this.newCycle.cycleName = `${year}年${crop}种植计划`
      }
    },
    applyPlanYear(type) {
      const base = new Date().getFullYear()
      let year = base
      if (type === 'next') year = base + 1
      if (type === 'after') year = base + 2
      const idx = this.planYearOptions.findIndex((x) => Number(x) === Number(year))
      if (idx >= 0) {
        this.selectedPlanYearIndex = idx
      }
      this.newCycle.planYear = year
      this.newCycle.startDate = `${year}-01-01`
      this.newCycle.endDate = `${year}-12-31`
      if (!String(this.newCycle.cycleName || '').trim()) {
        const crop = this.form.cropType || this.firstCycleCropName() || '作物'
        this.newCycle.cycleName = `${year}年${crop}种植计划`
      }
    },
    onPlanModeChange(e) {
      const idx = Number(e.detail.value)
      this.selectedPlanModeIndex = idx
      const row = this.planModeOptions[idx]
      this.newCycle.planMode = row ? row.value : 'single'
    },
    async createCycle() {
      if (!this.selectedField) return
      const cycleName = this.newCycle.cycleName.trim()
      if (!cycleName) {
        uni.showToast({ title: '请先填写计划名称', icon: 'none' })
        return
      }
      const cropsPayload = (this.newCycle.crops || [])
        .map((item) => {
          const variety = String((item && item.cropVariety) || '').trim()
          let name = String((item && item.cropType) || '').trim()
          if (!name && variety) {
            name = this.resolveCropNameByVariety(variety)
          }
          const selectedTemplateId = Number((item && item.templateId) || 0)
          const defaultTemplateId = Number(this.defaultCycleTemplateId(name, variety) || 0)
          return {
            name,
            variety,
            templateId: selectedTemplateId > 0 ? selectedTemplateId : defaultTemplateId
          }
        })
        .filter((item) => item.name || item.variety || item.templateId > 0)
      if (!cropsPayload.length && this.newCycle.planMode !== 'fallow') {
        uni.showToast({ title: '请至少选择一个计划作物', icon: 'none' })
        return
      }
      const finalCrops = cropsPayload.filter((item) => item.name)
      if (!finalCrops.length && this.newCycle.planMode !== 'fallow') {
        uni.showToast({ title: '请至少选择一个计划作物', icon: 'none' })
        return
      }
      if (this.newCycle.planMode !== 'fallow' && finalCrops.some((item) => !(item.templateId > 0))) {
        uni.showToast({ title: '请为每条作物选择流程模板', icon: 'none' })
        return
      }
      const rowsPayload = finalCrops.map((item) => ({
        name: item.name,
        variety: item.variety || '',
        templateId: item.templateId > 0 ? item.templateId : null,
        templateIds: item.templateId > 0 ? [item.templateId] : []
      }))
      const templateIds = Array.from(
        new Set(rowsPayload.map((item) => Number(item.templateId)).filter((x) => Number.isFinite(x) && x > 0))
      )
      if (this.newCycle.planMode !== 'fallow' && !templateIds.length) {
        uni.showToast({ title: '请至少选择一个流程模板', icon: 'none' })
        return
      }

      try {
        await api.post(`/miniapp/fields/${this.selectedField.id}/cycles`, {
          cycleName,
          cropsJson: rowsPayload.length ? JSON.stringify(rowsPayload) : null,
          templateIdsJson: templateIds.length ? JSON.stringify(templateIds) : null,
          startDate: this.newCycle.startDate || null,
          endDate: this.newCycle.endDate || null,
          status: 'active',
          isCurrent: this.newCycle.isCurrent,
          planMode: this.newCycle.planMode || 'single'
        })
        markDataChanged(refreshTopics.fieldDetail(this.selectedField.id))
        uni.showToast({ title: '种植计划已创建', icon: 'success' })
        this.newCycle = {
          cycleName: '',
          crops: [{ cropType: '', cropVariety: '', templateId: null }],
          startDate: `${this.newCycle.planYear || new Date().getFullYear()}-01-01`,
          endDate: `${this.newCycle.planYear || new Date().getFullYear()}-12-31`,
          isCurrent: true,
          planMode: 'single',
          planYear: this.newCycle.planYear || new Date().getFullYear()
        }
        this.selectedPlanModeIndex = 0
        await this.loadCycles()
      } catch (e) {
        console.error('创建计划失败', e)
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

.content {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 12rpx 16rpx 20rpx;
  box-sizing: border-box;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 14rpx;
  margin-bottom: 10rpx;
}

.label {
  display: block;
  font-size: 24rpx;
  color: #657360;
  margin-bottom: 8rpx;
}

.input,
.picker {
  margin-top: 8rpx;
  min-height: 64rpx;
  border-radius: 10rpx;
  background: var(--dh-color-surface-soft);
  padding: 0 12rpx;
  display: flex;
  align-items: center;
  font-size: 27rpx;
  color: #273122;
}

.placeholder {
  color: #97a28f;
}

.line {
  display: flex;
  align-items: center;
}

.line.between {
  justify-content: space-between;
}

.line.gap {
  gap: 10rpx;
}

.row {
  display: flex;
  gap: 10rpx;
}

.half {
  flex: 1;
}

.mt8 {
  margin-top: 8rpx;
}

.locate {
  font-size: 24rpx;
  color: var(--dh-color-brand);
}

.tiny {
  font-size: 22rpx;
  color: #758170;
}
.sub-tip {
  margin-top: 6rpx;
  color: #6d7c68;
  font-size: 22rpx;
}

.quick-row {
  margin-top: 8rpx;
  display: flex;
  gap: 8rpx;
  flex-wrap: wrap;
}

.quick-chip {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
  font-size: 22rpx;
}

.plan-crop-list {
  margin-top: 8rpx;
}

.plan-crop-row {
  display: flex;
  gap: 8rpx;
  align-items: center;
  margin-bottom: 8rpx;
}

.plan-crop-row picker {
  flex: 1;
}

.plan-crop-row .picker {
  margin-top: 0;
}

.remove-btn {
  flex: none;
  width: 120rpx;
  margin-top: 0;
}

.add-btn {
  margin-top: 4rpx;
}

.chip-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 8rpx;
}

.chip {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--dh-color-border);
  font-size: 23rpx;
  color: #60705b;
  background: #fff;
}

.chip.active {
  background: var(--dh-color-brand);
  color: #fff;
  border-color: var(--dh-color-brand);
}

.btn,
.small {
  flex: 1;
  margin-top: 10rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand);
  color: #fff;
  font-size: 25rpx;
}

.btn.minor,
.small {
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
}

.cycle-item {
  background: #f6f9f1;
  border-radius: 12rpx;
  padding: 10rpx 12rpx;
  margin-top: 8rpx;
}

.cycle-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #2c3a26;
}

.meta {
  margin-top: 6rpx;
  display: block;
  font-size: 23rpx;
  color: #6f7b67;
}

.tag {
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
}

.tag.current {
  background: #e4f3d5;
  color: #4a7b2f;
}

.tag.normal {
  background: #eceff1;
  color: #67737a;
}

.empty {
  font-size: 24rpx;
  color: #8d9787;
}

.cover-preview {
  width: 100%;
  height: 280rpx;
  border-radius: 12rpx;
  margin-top: 8rpx;
}
</style>
