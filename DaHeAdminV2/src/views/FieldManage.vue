<template>
  <div class="field-manage-page">
    <PageToolbar
      title="田块管理"
      subtitle="仅保留田块主数据列表；编辑通过弹窗完成，种植计划在独立页面管理。"
      collapsible
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.township ? `乡镇：${filters.township}` : '',
        filters.cropFilterKey ? `作物品种：${resolveVarietyFilterText(filters.cropFilterKey)}` : '',
        filters.stage ? `阶段：${fieldStageText(filters.stage)}` : '',
        filters.enabled !== '' ? `可用性：${fieldEnabledText(filters.enabled)}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="田块名/地址关键字" clearable style="width: 220px" />
        <el-select v-model="filters.township" clearable filterable placeholder="乡镇" style="width: 170px">
          <el-option label="全部" value="" />
          <el-option v-for="item in townshipOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.cropFilterKey" clearable filterable placeholder="作物/品种" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option-group v-for="group in varietyGroupOptions" :key="group.cropType" :label="group.cropType">
            <el-option
              v-for="item in group.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-option-group>
        </el-select>
        <el-select v-model="filters.stage" clearable placeholder="阶段" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="播种" value="sowing" />
          <el-option label="生长" value="growing" />
          <el-option label="收获" value="harvesting" />
          <el-option label="空闲" value="idle" />
          <el-option label="休耕" value="fallow" />
        </el-select>
        <el-select v-model="filters.enabled" clearable placeholder="可用性" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button @click="loadFields(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-head">
          <div>
            <span>田块列表</span>
            <span v-if="batchMode" class="card-meta">已选 {{ selectedRows.length }} 条</span>
            <span class="card-meta">共 {{ total }} 条</span>
          </div>
          <div class="actions">
            <el-button :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
              {{ batchMode ? '退出多选' : '多选操作' }}
            </el-button>
            <el-button
              v-if="batchMode"
              type="danger"
              plain
              :disabled="!batchDeleteCount || batchDeleting || !isAdmin"
              :loading="batchDeleting"
              @click="onBatchDelete"
            >
              删除已选（{{ batchDeleteCount }}）
            </el-button>
            <el-button @click="openSortCenter">排序中心</el-button>
            <el-button type="primary" @click="openCreate">新增田块</el-button>
          </div>
        </div>
      </template>

      <el-table ref="tableRef" :data="rows" border @selection-change="onSelectionChange">
        <el-table-column
          v-if="batchMode"
          type="selection"
          width="46"
          align="center"
          :selectable="isRowSelectable"
        />
        <el-table-column prop="sortOrder" label="排序" width="76" />
        <el-table-column prop="name" label="田块名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="图片" width="96">
          <template #default="scope">
            <el-image
              v-if="scope.row.coverImageUrl"
              :src="scope.row.coverImageUrl"
              fit="cover"
              class="list-cover"
              :preview-src-list="[scope.row.coverImageUrl]"
              preview-teleported
            />
            <span v-else class="text-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="township" label="乡镇" width="120" show-overflow-tooltip />
        <el-table-column label="作物品种" min-width="180">
          <template #default="scope">
            <div class="variety-group-cell">
              <CropPairTags
                :crop-variety-groups="scope.row.cropVarietyGroups"
                :crop-type="scope.row.cropType"
                :crop-variety="scope.row.cropVariety"
                empty-text="未配置"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="areaMu" label="面积(亩)" width="110" />
        <el-table-column label="阶段" width="96">
          <template #default="scope">{{ fieldStageText(scope.row.stage || scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="可用性" width="90">
          <template #default="scope">
            <el-tag size="small" :type="isFieldDisabled(scope.row) ? 'info' : 'success'">
              {{ isFieldDisabled(scope.row) ? '禁用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="260">
          <template #default="scope">
            <div class="address-cell">
              <div class="address-main">{{ formatAddressPath(scope.row) || '未填写' }}</div>
              <div v-if="formatAddressDetail(scope.row)" class="address-detail">{{ formatAddressDetail(scope.row) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="264" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="table-op-line">
              <el-button
                size="small"
                :type="isFieldDisabled(scope.row) ? 'success' : 'warning'"
                plain
                :disabled="!isAdmin"
                @click="toggleFieldEnabled(scope.row)"
              >
                {{ isFieldDisabled(scope.row) ? '启用' : '禁用' }}
              </el-button>
              <el-button size="small" type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button size="small" @click="openFieldPlans(scope.row)">种植计划</el-button>
              <el-button size="small" type="danger" plain :disabled="!isAdmin" @click="removeField(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onPageSizeChange"
          @current-change="loadFields"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editMode ? '编辑田块' : '新增田块'" width="760px" destroy-on-close>
      <el-form label-width="100px">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="田块名称">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(亩)">
              <el-input-number v-model="form.areaMu" :min="0.1" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="8">
            <el-form-item label="省">
              <el-select
                v-model="form.province"
                clearable
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="onFormProvinceChange"
              >
                <el-option v-for="item in formProvinceOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="市">
              <el-select
                v-model="form.city"
                clearable
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="onFormCityChange"
              >
                <el-option v-for="item in formCityOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县">
              <el-select
                v-model="form.district"
                clearable
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="onFormDistrictChange"
              >
                <el-option v-for="item in formDistrictOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="乡镇">
              <el-select v-model="form.township" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="item in formTownshipOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="详细地址">
              <el-input v-model="form.formattedAddress" placeholder="可填写完整地址" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="form.locationLat" :precision="7" :step="0.000001" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="form.locationLng" :precision="7" :step="0.000001" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面图片">
          <ImageAssetPicker
            v-model="form.coverImageUrl"
            @change="onCoverImageChange"
            upload-module-key="field_cover"
            :upload-biz-id="form.id || ''"
            upload-folder-path="/田块封面"
            placeholder="请选择或上传田块封面图"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sortCenterVisible" title="田块排序中心" width="960px" destroy-on-close align-center>
      <SortCenterShell
        ref="sortShellRef"
        :mode="sortMode"
        :keyword="sortKeyword"
        keyword-placeholder="按钮模式下可按田块名称/乡镇搜索"
        :total="sortMode === 'drag' ? sortDraftRows.length : sortFilteredRows.length"
        :page="sortPage"
        :page-total="sortPageTotal"
        :save-loading="savingSort"
        @update:mode="(val) => (sortMode = val)"
        @update:keyword="(val) => (sortKeyword = val)"
        @reset="resetSortCenter"
        @save="saveSort"
      >
        <SortDragBoard
          v-show="sortMode === 'drag'"
          :model-value="dragPageRows"
          item-key="id"
          sort-id-key="id"
          :dragging="sortDragging"
          :suspend-update="sortSuspendPageUpdate"
          :turn-direction="sortTurnDirection"
          :page="sortPage"
          :page-total="sortPageTotal"
          :resolve-index="resolveSortIndex"
          @turn-page="turnSortPage"
          @drag-start="onSortDragStart"
          @drag-end="onSortDragEnd"
          @drag-move="onSortDragMove"
        >
          <template #item="{ row }">
            <span class="drag-handle">⋮⋮</span>
            <span class="drag-order">{{ resolveSortIndex(row) }}</span>
            <div class="drag-main">
              <div class="drag-title-row">
                <span class="drag-name">{{ row.name || '未命名田块' }}</span>
                <span class="drag-status">{{ fieldStageText(row.stage || row.status) }}</span>
              </div>
              <div class="drag-sub">
                <span>乡镇：{{ row.township || '-' }}</span>
                <span class="drag-crops">
                  <span>作物品种：</span>
                  <CropPairTags
                    :crop-variety-groups="row.cropVarietyGroups"
                    :crop-type="row.cropType"
                    :crop-variety="row.cropVariety"
                    empty-text="未配置"
                  />
                </span>
                <span>{{ formatArea(row.areaMu) }}</span>
              </div>
              <div class="drag-extra">{{ formatAddressPath(row) || '未填写地址信息' }}</div>
            </div>
            <span class="drag-meta">ID {{ row.id }}</span>
          </template>
        </SortDragBoard>

        <div v-show="sortMode !== 'drag'">
          <el-table :data="sortCenterRows" border>
            <el-table-column label="序号" width="72">
              <template #default="scope">{{ resolveSortIndex(scope.row) }}</template>
            </el-table-column>
            <el-table-column prop="name" label="田块名称" min-width="180" />
            <el-table-column prop="township" label="乡镇" width="120" />
            <el-table-column label="移动到序号" width="210">
              <template #default="scope">
                <div class="jump-box">
                  <el-input-number v-model="scope.row.jumpTo" :min="1" :max="Math.max(1, sortDraftRows.length)" size="small" />
                  <el-button size="small" @click="applySortJump(scope.row)">跳转</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" class-name="op-col">
              <template #default="scope">
                <div class="sort-op">
                  <el-button-group>
                    <el-button size="small" @click="moveSortRow(scope.row, -1)">上移</el-button>
                    <el-button size="small" @click="moveSortRow(scope.row, 1)">下移</el-button>
                  </el-button-group>
                  <el-dropdown trigger="click" @command="(command) => onSortOpCommand(command, scope.row)">
                    <el-button size="small">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="top">置顶</el-dropdown-item>
                        <el-dropdown-item command="bottom">置底</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <template #foot>
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="sortPageTotalRecords"
            :page-size="sortPageSize"
            :current-page="sortPage"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="onSortPageSizeChange"
            @current-change="onSortPageChange"
          />
        </template>
      </SortCenterShell>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import SortCenterShell from '../components/ui/SortCenterShell.vue'
import SortDragBoard from '../components/ui/SortDragBoard.vue'
import CropPairTags from '../components/ui/CropPairTags.vue'
import ImageAssetPicker from '../components/ui/ImageAssetPicker.vue'
import request from '../utils/request'
import { isAdmin as isAdminUser } from '../utils/auth'

const router = useRouter()
const isAdmin = computed(() => isAdminUser())

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchDeleting = ref(false)

const filters = reactive({
  keyword: '',
  township: '',
  cropFilterKey: '',
  stage: '',
  enabled: ''
})

const dialogVisible = ref(false)
const editMode = ref(false)
const form = reactive({
  id: null,
  name: '',
  areaMu: 1,
  province: '',
  city: '',
  district: '',
  township: '',
  formattedAddress: '',
  locationLat: null,
  locationLng: null,
  coverImageUrl: '',
  coverImageAssetId: null,
  remark: '',
  enabled: true
})

const VARIETY_FILTER_DELIMITER = '||'
const varietyGroupOptions = ref([])
const townshipOptions = ref([])
const formProvinceOptions = ref([])
const formCityOptions = ref([])
const formDistrictOptions = ref([])
const formTownshipOptions = ref([])
const provinceAdcodeMap = ref(new Map())
const cityAdcodeMap = ref(new Map())
const districtAdcodeMap = ref(new Map())

const sortCenterVisible = ref(false)
const sortMode = ref('drag')
const sortKeyword = ref('')
const sortDraftRows = ref([])
const sortInitialRows = ref([])
const dragPageRows = ref([])
const sortShellRef = ref(null)
const sortPage = ref(1)
const sortPageSize = ref(10)
const savingSort = ref(false)
const sortDragging = ref(false)
const sortDraggingId = ref('')
const sortSuspendPageUpdate = ref(false)
const sortDragStartSnapshot = ref([])
const sortDragStartPage = ref(1)
const sortDragSourceIndex = ref(-1)
const sortDragSourceRow = ref(null)
const sortVisitedCrossPageDuringDrag = ref(false)
const sortTurnDirection = ref('')
let sortTurnTimer = null
let sortTurnRaf = 0
let sortTurnPendingPoint = null
let sortTurnConsumedDirection = ''
let sortAutoScrollLastAt = 0
let sortAutoScrollCarry = 0
let sortAutoScrollDirection = 0
let sortAutoScrollVelocity = 0
let sortCrossPreviewLastPage = 0
let sortCrossPreviewLastInsertIndex = -1
let sortCrossPreviewLastPointKey = ''
let sortCrossPreviewLastPointerY = NaN
let sortCrossPreviewMoveDown = true

const batchDeleteCount = computed(() => {
  return selectedRows.value.filter((row) => isRowSelectable(row)).length
})

function getSortBodyEl() {
  if (!sortShellRef.value || typeof sortShellRef.value.getBodyEl !== 'function') return null
  return sortShellRef.value.getBodyEl()
}

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function toUniqueStrings(rowsInput) {
  return Array.from(new Set((Array.isArray(rowsInput) ? rowsInput : []).map((x) => String(x || '').trim()).filter(Boolean)))
}

function parseRegionRows(rowsInput) {
  return (Array.isArray(rowsInput) ? rowsInput : [])
    .map((x) => {
      if (typeof x === 'string') {
        return {
          name: x.trim(),
          adcode: ''
        }
      }
      const row = x || {}
      return {
        name: String(row.name || row.label || row.value || row.text || '').trim(),
        adcode: String(row.adcode || '').trim()
      }
    })
    .filter((x) => x.name)
}

function buildRegionNameAndAdcode(rowsInput) {
  const names = []
  const adcodeMap = new Map()
  parseRegionRows(rowsInput).forEach((item) => {
    if (!names.includes(item.name)) {
      names.push(item.name)
    }
    if (item.adcode && !adcodeMap.has(item.name)) {
      adcodeMap.set(item.name, item.adcode)
    }
  })
  return { names, adcodeMap }
}

function cleanParams(input) {
  const out = {}
  Object.keys(input || {}).forEach((key) => {
    const value = input[key]
    if (value === null || value === undefined || value === '') return
    out[key] = value
  })
  return out
}

function fieldStageText(status) {
  const map = {
    sowing: '播种',
    growing: '生长',
    harvesting: '收获',
    idle: '空闲',
    fallow: '休耕'
  }
  return map[String(status || '').toLowerCase()] || '未知'
}

function fieldEnabledText(enabled) {
  return Number(enabled) === 0 ? '禁用' : '启用'
}

function isFieldDisabled(row) {
  return Number(row && row.enabled) === 0
}

function formatArea(areaMu) {
  const num = Number(areaMu || 0)
  if (!(num > 0)) return '面积未录入'
  return `${num.toFixed(2)} 亩`
}

function makeVarietyFilterValue(cropType, cropVariety) {
  return `${String(cropType || '').trim()}${VARIETY_FILTER_DELIMITER}${String(cropVariety || '').trim()}`
}

function parseVarietyFilterValue(value) {
  const text = String(value || '').trim()
  if (!text.includes(VARIETY_FILTER_DELIMITER)) {
    return { cropType: '', cropVariety: '' }
  }
  const [cropType, ...rest] = text.split(VARIETY_FILTER_DELIMITER)
  return {
    cropType: String(cropType || '').trim(),
    cropVariety: String(rest.join(VARIETY_FILTER_DELIMITER) || '').trim()
  }
}

function resolveVarietyFilterText(value) {
  const parsed = parseVarietyFilterValue(value)
  if (parsed.cropType && parsed.cropVariety) {
    return `${parsed.cropType}·${parsed.cropVariety}`
  }
  if (parsed.cropType) return parsed.cropType
  if (parsed.cropVariety) return parsed.cropVariety
  return '-'
}

function joinAddressSegments(...values) {
  return values
    .map((x) => String(x || '').trim())
    .filter(Boolean)
    .join('-')
}

function formatAddressPath(row) {
  return joinAddressSegments(row && row.province, row && row.city, row && row.district, row && row.township)
}

function formatAddressDetail(row) {
  const full = String((row && row.formattedAddress) || '').trim()
  if (!full) return ''
  const path = formatAddressPath(row)
  if (!path) return full
  if (full === path) return ''
  if (full.startsWith(`${path}-`)) return full.slice(path.length + 1).trim()
  return full
}

function fillForm(row) {
  form.id = row && row.id ? row.id : null
  form.name = (row && row.name) || ''
  form.areaMu = Number((row && row.areaMu) || 1)
  form.province = (row && row.province) || ''
  form.city = (row && row.city) || ''
  form.district = (row && row.district) || ''
  form.township = (row && row.township) || ''
  form.formattedAddress = (row && row.formattedAddress) || ''
  form.locationLat = row && row.locationLat != null ? Number(row.locationLat) : null
  form.locationLng = row && row.locationLng != null ? Number(row.locationLng) : null
  form.coverImageUrl = (row && row.coverImageUrl) || ''
  form.coverImageAssetId = row && row.coverImageAssetId != null ? String(row.coverImageAssetId).trim() || null : null
  form.remark = (row && row.remark) || ''
  form.enabled = row && row.enabled != null ? Number(row.enabled) !== 0 : true
}

function onCoverImageChange(row) {
  form.coverImageAssetId = row && row.id ? String(row.id).trim() : null
}

function resetFilters() {
  filters.keyword = ''
  filters.township = ''
  filters.cropFilterKey = ''
  filters.stage = ''
  filters.enabled = ''
  loadFields(1)
}

function onPageSizeChange(size) {
  pageSize.value = Number(size || 10)
  loadFields(1)
}

async function loadMetaOptions() {
  try {
    const [townships, cropTree] = await Promise.all([
      request.get('/meta/options/townships'),
      request.get('/meta/options/crop-tree')
    ])
    townshipOptions.value = toUniqueStrings(townships)
    formTownshipOptions.value = [...townshipOptions.value]

    varietyGroupOptions.value = (Array.isArray(cropTree) ? cropTree : [])
      .map((item) => {
        const cropType = String((item && item.categoryName) || '').trim()
        if (!cropType) return null
        const options = [
          {
            label: `${cropType}`,
            value: makeVarietyFilterValue(cropType, ''),
            cropType,
            cropVariety: ''
          },
          ...toUniqueStrings((Array.isArray(item && item.varieties) ? item.varieties : []).map((v) => v && v.name)).map((variety) => ({
            label: `${cropType}·${variety}`,
            value: makeVarietyFilterValue(cropType, variety),
            cropType,
            cropVariety: variety
          }))
        ]
        return { cropType, options }
      })
      .filter(Boolean)
  } catch (error) {
    varietyGroupOptions.value = []
    townshipOptions.value = []
    formTownshipOptions.value = []
  }
}

function ensureOptionContains(optionsRef, value) {
  const text = String(value || '').trim()
  if (!text) return
  if (!Array.isArray(optionsRef.value)) optionsRef.value = []
  if (!optionsRef.value.includes(text)) {
    optionsRef.value = [text, ...optionsRef.value]
  }
}

async function loadFormProvinceOptions() {
  try {
    const rows = await request.get('/admin/amap/regions/provinces', {
      params: {
        limit: 80
      }
    })
    const parsed = buildRegionNameAndAdcode(rows)
    formProvinceOptions.value = parsed.names
    provinceAdcodeMap.value = parsed.adcodeMap
    if (!formProvinceOptions.value.length) {
      const fallback = await request.get('/meta/options/provinces')
      formProvinceOptions.value = toUniqueStrings(fallback)
      provinceAdcodeMap.value = new Map()
    }
  } catch (error) {
    try {
      const fallback = await request.get('/meta/options/provinces')
      formProvinceOptions.value = toUniqueStrings(fallback)
      provinceAdcodeMap.value = new Map()
    } catch (ignored) {
      formProvinceOptions.value = []
      provinceAdcodeMap.value = new Map()
    }
  }
  ensureOptionContains(formProvinceOptions, form.province)
}

async function loadFormCityOptions() {
  const provinceName = String(form.province || '').trim()
  if (!provinceName) {
    formCityOptions.value = []
    cityAdcodeMap.value = new Map()
    return
  }
  const provinceQuery = provinceAdcodeMap.value.get(provinceName) || provinceName
  try {
    const rows = await request.get('/admin/amap/regions/cities', {
      params: cleanParams({
        province: provinceQuery,
        limit: 120
      })
    })
    const parsed = buildRegionNameAndAdcode(rows)
    formCityOptions.value = parsed.names
    cityAdcodeMap.value = parsed.adcodeMap
    if (!formCityOptions.value.length) {
      const fallback = await request.get('/meta/options/cities', {
        params: cleanParams({ province: provinceName })
      })
      formCityOptions.value = toUniqueStrings(fallback)
      cityAdcodeMap.value = new Map()
    }
  } catch (error) {
    try {
      const fallback = await request.get('/meta/options/cities', {
        params: cleanParams({ province: provinceName })
      })
      formCityOptions.value = toUniqueStrings(fallback)
      cityAdcodeMap.value = new Map()
    } catch (ignored) {
      formCityOptions.value = []
      cityAdcodeMap.value = new Map()
    }
  }
  ensureOptionContains(formCityOptions, form.city)
}

async function loadFormDistrictOptions() {
  const cityName = String(form.city || '').trim()
  if (!cityName) {
    formDistrictOptions.value = []
    districtAdcodeMap.value = new Map()
    return
  }
  const cityQuery = cityAdcodeMap.value.get(cityName) || cityName
  try {
    const rows = await request.get('/admin/amap/regions/districts', {
      params: cleanParams({
        city: cityQuery,
        limit: 160
      })
    })
    const parsed = buildRegionNameAndAdcode(rows)
    formDistrictOptions.value = parsed.names
    districtAdcodeMap.value = parsed.adcodeMap
    if (!formDistrictOptions.value.length) {
      const fallback = await request.get('/meta/options/districts', {
        params: cleanParams({
          province: form.province,
          city: cityName
        })
      })
      formDistrictOptions.value = toUniqueStrings(fallback)
      districtAdcodeMap.value = new Map()
    }
  } catch (error) {
    try {
      const fallback = await request.get('/meta/options/districts', {
        params: cleanParams({
          province: form.province,
          city: cityName
        })
      })
      formDistrictOptions.value = toUniqueStrings(fallback)
      districtAdcodeMap.value = new Map()
    } catch (ignored) {
      formDistrictOptions.value = []
      districtAdcodeMap.value = new Map()
    }
  }
  ensureOptionContains(formDistrictOptions, form.district)
}

async function loadFormTownshipOptions() {
  const districtName = String(form.district || '').trim()
  let loaded = false
  if (districtName) {
    const districtQuery = districtAdcodeMap.value.get(districtName) || districtName
    try {
      const rows = await request.get('/admin/amap/regions/townships', {
        params: cleanParams({
          district: districtQuery,
          limit: 300
        })
      })
      const parsed = buildRegionNameAndAdcode(rows)
      formTownshipOptions.value = parsed.names
      loaded = formTownshipOptions.value.length > 0
    } catch (error) {
      loaded = false
    }
  }
  if (!loaded) {
    try {
      const rows = await request.get('/meta/options/townships', {
        params: cleanParams({
          province: form.province,
          city: form.city,
          district: form.district
        })
      })
      formTownshipOptions.value = toUniqueStrings(rows)
      if (!formTownshipOptions.value.length) {
        const fallback = await request.get('/meta/options/townships')
        formTownshipOptions.value = toUniqueStrings(fallback)
      }
    } catch (error) {
      formTownshipOptions.value = toUniqueStrings(townshipOptions.value)
    }
  }
  ensureOptionContains(formTownshipOptions, form.township)
}

async function syncFormRegionOptions() {
  await loadFormProvinceOptions()
  await loadFormCityOptions()
  await loadFormDistrictOptions()
  await loadFormTownshipOptions()
}

async function onFormProvinceChange() {
  form.city = ''
  form.district = ''
  form.township = ''
  cityAdcodeMap.value = new Map()
  districtAdcodeMap.value = new Map()
  await loadFormCityOptions()
  await loadFormDistrictOptions()
  await loadFormTownshipOptions()
}

async function onFormCityChange() {
  form.district = ''
  form.township = ''
  districtAdcodeMap.value = new Map()
  await loadFormDistrictOptions()
  await loadFormTownshipOptions()
}

async function onFormDistrictChange() {
  form.township = ''
  await loadFormTownshipOptions()
}

async function loadFields(nextPage = page.value) {
  loading.value = true
  try {
    page.value = Number(nextPage || 1)
    const varietyFilter = parseVarietyFilterValue(filters.cropFilterKey)
    const data = await request.get('/fields', {
      params: cleanParams({
        page: page.value,
        pageSize: pageSize.value,
        keyword: filters.keyword,
        township: filters.township,
        cropType: varietyFilter.cropType || null,
        cropVariety: varietyFilter.cropVariety || null,
        stage: filters.stage,
        enabled: filters.enabled,
        includeDisabled: true
      })
    })
    rows.value = (data && data.records) || []
    total.value = Number((data && data.total) || 0)
    clearTableSelection()
  } catch (error) {
    rows.value = []
    total.value = 0
    clearTableSelection()
    ElMessage.error(error.message || '田块列表加载失败')
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  editMode.value = false
  fillForm(null)
  form.areaMu = 1
  await syncFormRegionOptions()
  dialogVisible.value = true
}

async function openEdit(row) {
  editMode.value = true
  fillForm(row)
  await syncFormRegionOptions()
  dialogVisible.value = true
}

async function saveField() {
  const name = String(form.name || '').trim()
  if (!name) {
    ElMessage.warning('请填写田块名称')
    return
  }
  if (!(Number(form.areaMu) > 0)) {
    ElMessage.warning('面积必须大于 0')
    return
  }

  saving.value = true
  try {
    const payload = {
      name,
      areaMu: Number(form.areaMu),
      province: form.province || null,
      city: form.city || null,
      district: form.district || null,
      township: form.township || null,
      formattedAddress: form.formattedAddress || null,
      locationLat: form.locationLat != null ? Number(form.locationLat) : null,
      locationLng: form.locationLng != null ? Number(form.locationLng) : null,
      coverImageUrl: form.coverImageUrl || null,
      coverImageAssetId: form.coverImageAssetId ? Number(form.coverImageAssetId) : null,
      remark: form.remark || null,
      enabled: !!form.enabled
    }

    if (editMode.value && form.id) {
      await request.put(`/fields/${form.id}`, payload)
    } else {
      await request.post('/fields', payload)
    }

    dialogVisible.value = false
    ElMessage.success('田块保存成功')
    await loadFields(page.value)
  } catch (error) {
    ElMessage.error(error.message || '田块保存失败')
  } finally {
    saving.value = false
  }
}

function openFieldPlans(row) {
  const id = toId(row && row.id)
  if (!id) return
  router.push({
    path: `/field-cycles/field/${id}`,
    query: {
      fieldName: String((row && row.name) || '').trim() || undefined
    }
  })
}

function isRowSelectable(row) {
  return !!row && isAdmin.value
}

function clearTableSelection() {
  selectedRows.value = []
  nextTick(() => {
    if (tableRef.value && typeof tableRef.value.clearSelection === 'function') {
      tableRef.value.clearSelection()
    }
  })
}

function onSelectionChange(rowsInput) {
  selectedRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearTableSelection()
  }
}

async function onBatchDelete() {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可批量删除田块')
    return
  }
  const ids = Array.from(new Set(
    selectedRows.value
      .filter((row) => isRowSelectable(row))
      .map((row) => toId(row && row.id))
      .filter(Boolean)
  ))
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的田块')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 个田块吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
    return
  }

  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => request.delete(`/fields/${id}`)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (successCount > 0) {
      const remainAfterDelete = Math.max(0, total.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
      await loadFields(Math.min(page.value, maxPage))
    }
    clearTableSelection()
    if (failedCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 个，${failedCount} 个删除失败`)
      return
    }
    ElMessage.success(`已删除 ${successCount} 个田块`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

async function removeField(row) {
  const id = toId(row && row.id)
  if (!id) return
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可删除田块')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除田块“${row.name || id}”吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/fields/${id}`)
    ElMessage.success('删除成功')
    const remainAfterDelete = Math.max(0, total.value - 1)
    const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(pageSize.value || 10)))
    await loadFields(Math.min(page.value, maxPage))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

async function toggleFieldEnabled(row) {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可操作田块启停')
    return
  }
  const id = toId(row && row.id)
  if (!id) return
  const nextEnabled = isFieldDisabled(row)
  const actionText = nextEnabled ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${actionText}田块“${row.name || id}”吗？`, `${actionText}确认`, { type: 'warning' })
    await request.put(`/fields/${id}/enabled`, { enabled: nextEnabled })
    ElMessage.success(`田块已${actionText}`)
    await loadFields(page.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `田块${actionText}失败`)
    }
  }
}

function hasFilter() {
  const hasEnabledFilter = filters.enabled !== '' && filters.enabled !== null && filters.enabled !== undefined
  return !!(
    String(filters.keyword || '').trim() ||
    String(filters.township || '').trim() ||
    String(filters.cropFilterKey || '').trim() ||
    String(filters.stage || '').trim() ||
    hasEnabledFilter
  )
}

async function loadSortRows() {
  const merged = []
  let current = 1
  const fetchSize = 200
  let expectedTotal = 0
  while (current <= 200) {
    const data = await request.get('/fields', {
      params: {
        page: current,
        pageSize: fetchSize,
        includeDisabled: true
      }
    })
    const records = (data && data.records) || []
    expectedTotal = Number((data && data.total) || 0)
    merged.push(...records)
    if (!records.length || merged.length >= expectedTotal || records.length < fetchSize) {
      break
    }
    current += 1
  }
  sortDraftRows.value = merged.map((item, idx) => ({
    id: item.id,
    name: item.name || '',
    township: item.township || '',
    status: item.status || '',
    stage: item.stage || item.status || '',
    cropType: item.cropType || '',
    cropVariety: item.cropVariety || '',
    cropVarietyGroups: Array.isArray(item.cropVarietyGroups) ? item.cropVarietyGroups : [],
    areaMu: item.areaMu,
    formattedAddress: item.formattedAddress || '',
    province: item.province || '',
    city: item.city || '',
    district: item.district || '',
    jumpTo: idx + 1
  }))
  sortInitialRows.value = sortDraftRows.value.map((item) => ({ ...item }))
  refreshSortPageRows()
}

function resetSortCenter() {
  clearSortDragState()
  sortDraftRows.value = sortInitialRows.value.map((item, idx) => ({
    ...item,
    jumpTo: idx + 1
  }))
  sortKeyword.value = ''
  sortPage.value = 1
  refreshSortPageRows()
}

const sortFilteredRows = computed(() => {
  const keyword = String(sortKeyword.value || '').trim().toLowerCase()
  if (!keyword) return sortDraftRows.value
  return sortDraftRows.value.filter((row) => {
    const name = String(row.name || '').toLowerCase()
    const township = String(row.township || '').toLowerCase()
    return name.includes(keyword) || township.includes(keyword)
  })
})

const sortButtonRows = computed(() => {
  if (sortMode.value !== 'button') return sortDraftRows.value
  return sortFilteredRows.value
})

const sortPageTotalRecords = computed(() => sortButtonRows.value.length)

const sortPageTotal = computed(() => {
  const total = Number(sortPageTotalRecords.value || 0)
  const size = Math.max(1, Number(sortPageSize.value || 10))
  return Math.max(1, Math.ceil(total / size))
})

const sortCenterRows = computed(() => {
  const start = (Math.max(1, Number(sortPage.value || 1)) - 1) * Math.max(1, Number(sortPageSize.value || 10))
  const end = start + Math.max(1, Number(sortPageSize.value || 10))
  return sortButtonRows.value.slice(start, end)
})

const sortIndexMap = computed(() => {
  const map = new Map()
  sortDraftRows.value.forEach((item, idx) => {
    map.set(toId(item && item.id), idx + 1)
  })
  return map
})

function resolveSortIndex(row) {
  const id = toId(row && row.id)
  if (!id) return '-'
  return sortIndexMap.value.get(id) ?? '-'
}

function setDragPageRows(nextRows) {
  const next = Array.isArray(nextRows) ? nextRows : []
  const current = Array.isArray(dragPageRows.value) ? dragPageRows.value : []
  if (
    current.length === next.length &&
    current.every((item, idx) => toId(item && item.id) === toId(next[idx] && next[idx].id))
  ) {
    return
  }
  dragPageRows.value = next.slice()
}

function normalizeSortDraftRows() {
  const list = Array.isArray(sortDraftRows.value) ? sortDraftRows.value : []
  const baseline = Array.isArray(sortInitialRows.value) && sortInitialRows.value.length
    ? sortInitialRows.value
    : list
  const fallbackMap = new Map()
  baseline.forEach((item) => {
    const id = toId(item && item.id)
    if (id && !fallbackMap.has(id)) {
      fallbackMap.set(id, item)
    }
  })
  list.forEach((item) => {
    const id = toId(item && item.id)
    if (id && !fallbackMap.has(id)) {
      fallbackMap.set(id, item)
    }
  })
  const seen = new Set()
  const normalized = []
  list.forEach((item) => {
    const id = toId(item && item.id)
    if (!id || seen.has(id)) return
    seen.add(id)
    normalized.push(item)
  })
  fallbackMap.forEach((item, id) => {
    if (seen.has(id)) return
    seen.add(id)
    normalized.push({ ...item })
  })
  if (
    normalized.length === list.length &&
    normalized.every((item, idx) => toId(item && item.id) === toId(list[idx] && list[idx].id))
  ) {
    return
  }
  sortDraftRows.value = normalized
}

function refreshSortPageRows() {
  normalizeSortDraftRows()
  syncSortPage()
  if (sortMode.value !== 'drag') {
    setDragPageRows([])
    return
  }
  const size = Math.max(1, Number(sortPageSize.value || 10))
  if (sortDragging.value && sortPage.value !== sortDragStartPage.value) {
    refreshCrossPageDragRowsByPoint(sortTurnPendingPoint)
    return
  }
  const start = (Math.max(1, Number(sortPage.value || 1)) - 1) * size
  const end = start + size
  setDragPageRows(sortDraftRows.value.slice(start, end))
}

function syncSortPage() {
  const maxPage = sortPageTotal.value
  const nextPage = Math.max(1, Math.min(maxPage, Number(sortPage.value || 1)))
  if (nextPage !== sortPage.value) {
    sortPage.value = nextPage
  }
}

function refreshSortJumpNumbers() {
  normalizeSortDraftRows()
  sortDraftRows.value.forEach((row, idx) => {
    row.jumpTo = idx + 1
  })
  refreshSortPageRows()
}

function cloneSortRows(rows) {
  // Shallow copy is enough for reordering; avoid cloning large row objects repeatedly.
  return Array.isArray(rows) ? rows.slice() : []
}

function captureSortDragSnapshot() {
  const snapshot = cloneSortRows(sortDraftRows.value)
  sortDragStartSnapshot.value = snapshot
  sortDragStartPage.value = Math.max(1, Number(sortPage.value || 1))
  const dragId = toId(sortDraggingId.value)
  const sourceIndex = dragId ? snapshot.findIndex((item) => toId(item && item.id) === dragId) : -1
  sortDragSourceIndex.value = sourceIndex
  sortDragSourceRow.value = sourceIndex >= 0 ? snapshot[sourceIndex] : null
}

function restoreSortDragSnapshot() {
  if (!Array.isArray(sortDragStartSnapshot.value) || !sortDragStartSnapshot.value.length) return
  sortDraftRows.value = cloneSortRows(sortDragStartSnapshot.value)
  sortPage.value = Math.max(1, Number(sortDragStartPage.value || 1))
  refreshSortJumpNumbers()
}

function applySamePageDropByEvent(event) {
  const snapshot = cloneSortRows(sortDragStartSnapshot.value)
  if (!snapshot.length) return false
  const size = Math.max(1, Number(sortPageSize.value || 10))
  const startPage = Math.max(1, Number(sortDragStartPage.value || 1))
  const start = (startPage - 1) * size
  const expectedCount = Math.min(size, Math.max(0, snapshot.length - start))
  if (expectedCount <= 1) {
    sortDraftRows.value = snapshot
    return true
  }
  const oldIndex = Number(event && event.oldDraggableIndex)
  const newIndex = Number(event && event.newDraggableIndex)
  if (!Number.isInteger(oldIndex) || !Number.isInteger(newIndex)) {
    sortDraftRows.value = snapshot
    return true
  }
  if (oldIndex < 0 || oldIndex >= expectedCount || newIndex < 0 || newIndex >= expectedCount) {
    sortDraftRows.value = snapshot
    return true
  }
  if (oldIndex === newIndex) {
    sortDraftRows.value = snapshot
    return true
  }
  const pageRows = snapshot.slice(start, start + expectedCount)
  const moved = pageRows.splice(oldIndex, 1)[0]
  pageRows.splice(newIndex, 0, moved)
  snapshot.splice(start, expectedCount, ...pageRows)
  sortDraftRows.value = snapshot
  return true
}

function ensureSortDraftIntegrityAfterDrag() {
  const snapshot = cloneSortRows(sortDragStartSnapshot.value)
  if (!snapshot.length) return
  const baselineIds = snapshot.map((item) => toId(item && item.id)).filter(Boolean)
  const list = Array.isArray(sortDraftRows.value) ? sortDraftRows.value : []
  const currentIds = list.map((item) => toId(item && item.id)).filter(Boolean)
  if (currentIds.length !== baselineIds.length) {
    sortDraftRows.value = snapshot
    return
  }
  const seen = new Set()
  for (const id of currentIds) {
    if (!id || seen.has(id)) {
      sortDraftRows.value = snapshot
      return
    }
    seen.add(id)
  }
  for (const id of baselineIds) {
    if (!seen.has(id)) {
      sortDraftRows.value = snapshot
      return
    }
  }
}

function applyCrossPageDropByPoint(point) {
  const dragId = toId(sortDraggingId.value)
  const snapshot = cloneSortRows(sortDragStartSnapshot.value)
  if (!dragId || !snapshot.length) return false
  let from = Number(sortDragSourceIndex.value)
  if (!Number.isInteger(from) || from < 0 || from >= snapshot.length) {
    from = snapshot.findIndex((item) => toId(item && item.id) === dragId)
  }
  if (from < 0) return false
  const size = Math.max(1, Number(sortPageSize.value || 10))
  const sourcePage = Math.max(1, Number(sortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(sortPage.value || sourcePage)))
  const list = snapshot
  const moved = list.splice(from, 1)[0]
  const targetStart = Math.max(0, Math.min(list.length, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(list.length, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcCrossPageInsertIndexByPoint(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    list.length
  )
  list.splice(insertIndex, 0, moved)
  sortDraftRows.value = list
  return true
}

function buildSortPageRowsFromList(rows, pageInput = sortPage.value) {
  const source = Array.isArray(rows) ? rows : []
  const size = Math.max(1, Number(sortPageSize.value || 10))
  const pageNo = Math.max(1, Number(pageInput || 1))
  const start = (pageNo - 1) * size
  return source.slice(start, start + size)
}

function getCrossPageSortBaseRows() {
  const snapshot = Array.isArray(sortDragStartSnapshot.value) ? sortDragStartSnapshot.value : []
  if (snapshot.length) {
    return buildSortPageRowsFromList(snapshot)
  }
  return buildSortPageRowsFromList(sortDraftRows.value)
}

function buildCrossPagePreviewPageRowsFromSnapshot(snapshot, from, insertIndex, targetPage, pageSize) {
  const source = Array.isArray(snapshot) ? snapshot : []
  const total = source.length
  const sourceIndex = Number(from)
  if (!total || sourceIndex < 0 || sourceIndex >= total) return []
  const size = Math.max(1, Number(pageSize || sortPageSize.value || 10))
  const pageNo = Math.max(1, Number(targetPage || sortPage.value || 1))
  const moved = sortDragSourceRow.value || source[sourceIndex]
  const finalInsert = Math.max(0, Math.min(total - 1, Number(insertIndex || 0)))
  const pageStart = (pageNo - 1) * size
  const pageEnd = Math.min(total, pageStart + size)
  const rows = []
  for (let j = pageStart; j < pageEnd; j += 1) {
    if (j === finalInsert) {
      rows.push(moved)
      continue
    }
    const compactIndex = j < finalInsert ? j : j - 1
    const originalIndex = compactIndex < sourceIndex ? compactIndex : compactIndex + 1
    if (originalIndex >= 0 && originalIndex < total) {
      rows.push(source[originalIndex])
    }
  }
  return rows
}

function buildCrossPagePreviewRows(point) {
  const snapshot = Array.isArray(sortDragStartSnapshot.value) ? sortDragStartSnapshot.value : []
  const from = Number(sortDragSourceIndex.value)
  if (!snapshot.length || from < 0 || from >= snapshot.length) return getCrossPageSortBaseRows()
  const size = Math.max(1, Number(sortPageSize.value || 10))
  const sourcePage = Math.max(1, Number(sortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(sortPage.value || sourcePage)))
  const reducedLength = Math.max(0, snapshot.length - 1)
  const targetStart = Math.max(0, Math.min(reducedLength, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(reducedLength, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcCrossPageInsertIndexByPoint(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    reducedLength
  )
  if (
    targetPage === sortCrossPreviewLastPage &&
    insertIndex === sortCrossPreviewLastInsertIndex &&
    Array.isArray(dragPageRows.value) &&
    dragPageRows.value.length
  ) {
    return dragPageRows.value
  }
  sortCrossPreviewLastPage = targetPage
  sortCrossPreviewLastInsertIndex = insertIndex
  return buildCrossPagePreviewPageRowsFromSnapshot(snapshot, from, insertIndex, targetPage, size)
}

function refreshCrossPageDragRowsByPoint(point) {
  if (!sortDragging.value || sortPage.value === sortDragStartPage.value) return false
  if (point && typeof point.clientY === 'number') {
    if (Number.isFinite(sortCrossPreviewLastPointerY)) {
      sortCrossPreviewMoveDown = point.clientY >= sortCrossPreviewLastPointerY
    }
    sortCrossPreviewLastPointerY = point.clientY
  }
  const pointKey = point && typeof point.clientY === 'number'
    ? `${sortPage.value}:${Math.round((Number(point.clientX || 0)) / 8)}:${Math.round(point.clientY / 3)}`
    : `${sortPage.value}:na`
  if (pointKey === sortCrossPreviewLastPointKey) return true
  sortCrossPreviewLastPointKey = pointKey
  setDragPageRows(buildCrossPagePreviewRows(point))
  return true
}

function clearSortTurnTimer() {
  if (sortTurnTimer) {
    clearTimeout(sortTurnTimer)
    sortTurnTimer = null
  }
}

function canTurnSortPage(direction) {
  const dir = String(direction || '').trim()
  if (dir === 'prev') return sortPage.value > 1
  if (dir === 'next') return sortPage.value < sortPageTotal.value
  return false
}

function scheduleSortTurn(direction) {
  clearSortTurnTimer()
  if (!canTurnSortPage(direction)) return
  sortTurnTimer = setTimeout(() => {
    if (!sortDragging.value) return
    if (sortTurnDirection.value !== direction) return
    if (!canTurnSortPage(direction)) return
    turnSortPage(direction, sortTurnPendingPoint)
    sortTurnConsumedDirection = direction
    clearSortTurnTimer()
  }, 280)
}

function resolveSortTurnDirectionByPoint(point, target) {
  const fromTarget = target && target.closest ? target.closest('.sort-page-turn-zone') : null
  const fromTargetDir = fromTarget && fromTarget.getAttribute ? String(fromTarget.getAttribute('data-turn') || '') : ''
  if (canTurnSortPage(fromTargetDir)) return fromTargetDir
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return ''
  const zones = Array.from(document.querySelectorAll('.sort-page-turn-zone[data-turn]'))
  for (const zone of zones) {
    if (!zone || typeof zone.getBoundingClientRect !== 'function') continue
    const dir = zone.getAttribute ? String(zone.getAttribute('data-turn') || '') : ''
    if (!canTurnSortPage(dir)) continue
    const rect = zone.getBoundingClientRect()
    if (
      point.clientX >= rect.left &&
      point.clientX <= rect.right &&
      point.clientY >= rect.top &&
      point.clientY <= rect.bottom
    ) {
      return dir
    }
  }
  return ''
}

function turnSortPage(direction, point) {
  const dir = String(direction || '').trim()
  if (point && typeof point.clientX === 'number' && typeof point.clientY === 'number') {
    sortTurnPendingPoint = {
      clientX: point.clientX,
      clientY: point.clientY
    }
  }
  let changed = false
  if (dir === 'prev') {
    if (sortPage.value > 1) {
      sortPage.value -= 1
      changed = true
    }
  } else if (dir === 'next') {
    if (sortPage.value < sortPageTotal.value) {
      sortPage.value += 1
      changed = true
    }
  }
  if (changed) {
    refreshSortPageRows()
  }
  if (sortDragging.value) {
    sortSuspendPageUpdate.value = sortPage.value !== sortDragStartPage.value
    if (sortSuspendPageUpdate.value) {
      sortVisitedCrossPageDuringDrag.value = true
      refreshCrossPageDragRowsByPoint(sortTurnPendingPoint)
    }
  }
}

function calcCrossPageInsertIndexByPoint(
  point,
  targetStart,
  targetEndExclusive,
  fallbackIndex,
  totalLengthInput = sortDraftRows.value.length
) {
  const totalLength = Math.max(0, Number(totalLengthInput || 0))
  const fallback = Math.max(0, Math.min(totalLength, Number(fallbackIndex || 0)))
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return fallback
  const body = getSortBodyEl()
  if (!body) return fallback
  const listEl = body.querySelector('.sort-drag-card-list')
  if (listEl && typeof listEl.querySelectorAll === 'function') {
    const listRect = typeof listEl.getBoundingClientRect === 'function' ? listEl.getBoundingClientRect() : null
    if (
      listRect &&
      listRect.width > 0 &&
      listRect.height > 0 &&
      (point.clientX < listRect.left || point.clientX > listRect.right)
    ) {
      if (Number.isInteger(sortCrossPreviewLastInsertIndex) && sortCrossPreviewLastInsertIndex >= 0) {
        return Math.max(0, Math.min(totalLength, sortCrossPreviewLastInsertIndex))
      }
      return fallback
    }
    const dragId = toId(sortDraggingId.value)
    const hovered = document.elementFromPoint(point.clientX, point.clientY)
    const hoverCard = hovered && hovered.closest ? hovered.closest('.drag-item') : null
    if (
      hoverCard &&
      listEl.contains(hoverCard) &&
      hoverCard.classList &&
      !hoverCard.classList.contains('drag-ghost') &&
      !hoverCard.classList.contains('drag-chosen') &&
      !hoverCard.classList.contains('drag-dragging') &&
      !hoverCard.classList.contains('sortable-ghost') &&
      !hoverCard.classList.contains('sortable-chosen') &&
      !hoverCard.classList.contains('sortable-drag')
    ) {
      const hoverId = toId(hoverCard && hoverCard.dataset ? hoverCard.dataset.sortId : '')
      if (hoverId && (!dragId || hoverId !== dragId)) {
        const rows = Array.isArray(dragPageRows.value) ? dragPageRows.value : []
        let compactIndex = -1
        let compactOffset = 0
        for (let i = 0; i < rows.length; i += 1) {
          const rowId = toId(rows[i] && rows[i].id)
          if (!rowId || (dragId && rowId === dragId)) continue
          if (rowId === hoverId) {
            compactIndex = compactOffset
            break
          }
          compactOffset += 1
        }
        if (compactIndex >= 0) {
          const placeAfter = Boolean(sortCrossPreviewMoveDown)
          const count = Math.max(0, targetEndExclusive - targetStart)
          const offset = Math.max(0, Math.min(count, compactIndex + (placeAfter ? 1 : 0)))
          return Math.max(0, Math.min(totalLength, targetStart + offset))
        }
      }
    }
    const cards = listEl.querySelectorAll('.drag-item')
    if (cards && cards.length) {
      const count = Math.max(0, targetEndExclusive - targetStart)
      let offset = 0
      let hasValidCard = false
      for (let i = 0; i < cards.length; i += 1) {
        const card = cards[i]
        if (!card || !card.classList) continue
        if (
          card.classList.contains('drag-ghost') ||
          card.classList.contains('drag-chosen') ||
          card.classList.contains('drag-dragging') ||
          card.classList.contains('sortable-ghost') ||
          card.classList.contains('sortable-chosen') ||
          card.classList.contains('sortable-drag')
        ) {
          continue
        }
        const rect = card.getBoundingClientRect()
        if (rect.height <= 0 || rect.width <= 0) continue
        hasValidCard = true
        const splitY = sortCrossPreviewMoveDown ? rect.top : rect.bottom
        if (point.clientY < splitY) {
          break
        }
        const cardId = toId(card && card.dataset ? card.dataset.sortId : '')
        if (dragId && cardId === dragId) continue
        offset += 1
      }
      if (hasValidCard) {
        const normalizedOffset = Math.max(0, Math.min(count, offset))
        return Math.max(0, Math.min(totalLength, targetStart + normalizedOffset))
      }
    }
  }
  const rect = body.getBoundingClientRect()
  const top = rect.top + 8
  const bottom = rect.bottom - 8
  const height = Math.max(1, bottom - top)
  const y = Math.max(top, Math.min(bottom, point.clientY))
  const ratio = (y - top) / height
  const count = Math.max(0, targetEndExclusive - targetStart)
  const offset = Math.max(0, Math.min(count, Math.round(ratio * count)))
  return Math.max(0, Math.min(totalLength, targetStart + offset))
}

function clearSortDragState() {
  clearSortTurnTimer()
  if (sortTurnRaf) {
    cancelAnimationFrame(sortTurnRaf)
    sortTurnRaf = 0
  }
  sortTurnPendingPoint = null
  sortTurnDirection.value = ''
  sortDragging.value = false
  sortDraggingId.value = ''
  sortSuspendPageUpdate.value = false
  sortDragStartSnapshot.value = []
  sortDragStartPage.value = 1
  sortDragSourceIndex.value = -1
  sortDragSourceRow.value = null
  sortVisitedCrossPageDuringDrag.value = false
  sortTurnConsumedDirection = ''
  sortAutoScrollLastAt = 0
  sortAutoScrollCarry = 0
  sortAutoScrollDirection = 0
  sortAutoScrollVelocity = 0
  sortCrossPreviewLastPage = 0
  sortCrossPreviewLastInsertIndex = -1
  sortCrossPreviewLastPointKey = ''
  sortCrossPreviewLastPointerY = NaN
  sortCrossPreviewMoveDown = true
}

function resolveSortTurnPoint(event) {
  if (!event) return null
  if (event.touches && event.touches[0]) return event.touches[0]
  if (event.changedTouches && event.changedTouches[0]) return event.changedTouches[0]
  if (event.originalEvent) return resolveSortTurnPoint(event.originalEvent)
  if (typeof event.clientX === 'number' && typeof event.clientY === 'number') return event
  return null
}

function autoScrollSortBody(point) {
  const body = getSortBodyEl()
  if (!body || !point || typeof point.clientY !== 'number') return
  const rect = body.getBoundingClientRect()
  const edgeThreshold = Math.max(48, Math.min(96, Math.round(rect.height * 0.16)))
  const minStep = 0.5
  const maxStep = 8.8
  const maxTickStep = 10
  const topEdge = rect.top + edgeThreshold
  const bottomEdge = rect.bottom - edgeThreshold
  let targetDirection = 0
  let depth = 0
  if (point.clientY < topEdge) {
    targetDirection = -1
    depth = topEdge - point.clientY
  } else if (point.clientY > bottomEdge) {
    targetDirection = 1
    depth = point.clientY - bottomEdge
  }
  if (!targetDirection || depth <= 0) {
    sortAutoScrollLastAt = 0
    sortAutoScrollCarry = 0
    sortAutoScrollDirection = 0
    sortAutoScrollVelocity = 0
    return
  }
  if (sortAutoScrollDirection && sortAutoScrollDirection !== targetDirection) {
    sortAutoScrollCarry = 0
    sortAutoScrollVelocity = 0
  }
  sortAutoScrollDirection = targetDirection
  const now = Date.now()
  if (!sortAutoScrollLastAt) {
    sortAutoScrollLastAt = now - 16
  }
  const elapsed = Math.max(10, Math.min(40, now - sortAutoScrollLastAt))
  if (elapsed < 12) return
  sortAutoScrollLastAt = now
  const ratio = Math.max(0, Math.min(1, depth / edgeThreshold))
  const eased = Math.pow(ratio, 1.35)
  const targetVelocity = targetDirection * (minStep + (maxStep - minStep) * eased)
  const smooth = 0.3
  sortAutoScrollVelocity += (targetVelocity - sortAutoScrollVelocity) * smooth
  const deltaFloat = sortAutoScrollVelocity * (elapsed / 16)
  sortAutoScrollCarry += deltaFloat
  let delta = sortAutoScrollCarry > 0 ? Math.floor(sortAutoScrollCarry) : Math.ceil(sortAutoScrollCarry)
  if (delta > maxTickStep) delta = maxTickStep
  if (delta < -maxTickStep) delta = -maxTickStep
  if (delta) {
    sortAutoScrollCarry -= delta
  }
  if (!delta) return
  const maxScroll = Math.max(0, body.scrollHeight - body.clientHeight)
  const next = Math.max(0, Math.min(maxScroll, body.scrollTop + delta))
  if (next === body.scrollTop) {
    sortAutoScrollCarry = 0
    sortAutoScrollVelocity = 0
    sortAutoScrollDirection = 0
    return
  }
  body.scrollTop = next
}

function applySortTurnByPoint(point) {
  if (!sortDragging.value || !point) return
  autoScrollSortBody(point)
  const target = document.elementFromPoint(point.clientX, point.clientY)
  sortSuspendPageUpdate.value = sortPage.value !== sortDragStartPage.value
  if (sortSuspendPageUpdate.value) {
    refreshCrossPageDragRowsByPoint(point)
  }
  const direction = resolveSortTurnDirectionByPoint(point, target)
  if (!direction) {
    sortTurnDirection.value = ''
    sortTurnConsumedDirection = ''
    clearSortTurnTimer()
    return
  }
  if (direction !== sortTurnDirection.value) {
    sortTurnDirection.value = direction
    if (sortTurnConsumedDirection !== direction) {
      scheduleSortTurn(direction)
    }
    return
  }
  if (sortTurnConsumedDirection === direction) return
  if (!sortTurnTimer) {
    scheduleSortTurn(direction)
  }
}

function detectSortTurnZone(event) {
  if (!sortDragging.value) return
  const point = resolveSortTurnPoint(event)
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return
  sortTurnPendingPoint = {
    clientX: point.clientX,
    clientY: point.clientY
  }
  if (sortTurnRaf) return
  sortTurnRaf = requestAnimationFrame(() => {
    sortTurnRaf = 0
    applySortTurnByPoint(sortTurnPendingPoint)
  })
}

function onSortDragMove(evt, originalEvent) {
  detectSortTurnZone(originalEvent || (evt && evt.originalEvent) || evt)
}

function onSortDragStart(event) {
  sortDragging.value = true
  sortDraggingId.value = toId(event && event.item && event.item.dataset ? event.item.dataset.sortId : '')
  sortTurnDirection.value = ''
  sortTurnConsumedDirection = ''
  sortAutoScrollLastAt = 0
  sortAutoScrollCarry = 0
  sortAutoScrollDirection = 0
  sortAutoScrollVelocity = 0
  sortCrossPreviewLastPage = 0
  sortCrossPreviewLastInsertIndex = -1
  sortCrossPreviewLastPointKey = ''
  sortCrossPreviewLastPointerY = NaN
  sortCrossPreviewMoveDown = true
  sortSuspendPageUpdate.value = false
  sortVisitedCrossPageDuringDrag.value = false
  captureSortDragSnapshot()
  document.addEventListener('drag', detectSortTurnZone)
  document.addEventListener('dragover', detectSortTurnZone)
  document.addEventListener('mousemove', detectSortTurnZone)
  document.addEventListener('touchmove', detectSortTurnZone, { passive: true })
}

function onSortDragEnd(event) {
  clearSortTurnTimer()
  if (sortTurnRaf) {
    cancelAnimationFrame(sortTurnRaf)
    sortTurnRaf = 0
  }
  document.removeEventListener('drag', detectSortTurnZone)
  document.removeEventListener('dragover', detectSortTurnZone)
  document.removeEventListener('mousemove', detectSortTurnZone)
  document.removeEventListener('touchmove', detectSortTurnZone)
  try {
    const pointFromEvent = resolveSortTurnPoint(event && event.originalEvent ? event.originalEvent : event)
    if (pointFromEvent && typeof pointFromEvent.clientX === 'number' && typeof pointFromEvent.clientY === 'number') {
      sortTurnPendingPoint = {
        clientX: pointFromEvent.clientX,
        clientY: pointFromEvent.clientY
      }
    }
    const draggedAcrossPage = sortPage.value !== sortDragStartPage.value
    if (!draggedAcrossPage) {
      applySamePageDropByEvent(event)
    } else {
      const point = sortTurnPendingPoint
      const applied = applyCrossPageDropByPoint(point || null)
      if (!applied) {
        restoreSortDragSnapshot()
      }
    }
    ensureSortDraftIntegrityAfterDrag()
  } catch (error) {
    restoreSortDragSnapshot()
  } finally {
    clearSortDragState()
    refreshSortJumpNumbers()
  }
}

function moveSortRow(row, delta) {
  const id = toId(row && row.id)
  if (!id) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => toId(x && x.id) === id)
  if (from < 0) return
  const to = from + Number(delta || 0)
  if (to < 0 || to >= list.length) return
  const moved = list.splice(from, 1)[0]
  list.splice(to, 0, moved)
  refreshSortJumpNumbers()
}

function moveSortTop(row) {
  const id = toId(row && row.id)
  if (!id) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => toId(x && x.id) === id)
  if (from < 1) return
  const moved = list.splice(from, 1)[0]
  list.unshift(moved)
  refreshSortJumpNumbers()
}

function moveSortBottom(row) {
  const id = toId(row && row.id)
  if (!id) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => toId(x && x.id) === id)
  if (from < 0 || from === list.length - 1) return
  const moved = list.splice(from, 1)[0]
  list.push(moved)
  refreshSortJumpNumbers()
}

function applySortJump(row) {
  const id = toId(row && row.id)
  if (!id) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => toId(x && x.id) === id)
  if (from < 0) return
  const target = Math.max(1, Math.min(list.length, Number(row.jumpTo || 1))) - 1
  if (target === from) return
  const moved = list.splice(from, 1)[0]
  list.splice(target, 0, moved)
  refreshSortJumpNumbers()
}

function onSortOpCommand(command, row) {
  const action = String(command || '').trim()
  if (action === 'top') {
    moveSortTop(row)
    return
  }
  if (action === 'bottom') {
    moveSortBottom(row)
  }
}

function onSortPageChange(nextPage) {
  sortPage.value = Number(nextPage || 1)
  refreshSortPageRows()
}

function onSortPageSizeChange(nextSize) {
  sortPageSize.value = Number(nextSize || 10)
  sortPage.value = 1
  refreshSortPageRows()
}

async function openSortCenter() {
  if (hasFilter()) {
    ElMessage.warning('请先清空筛选条件后再调整排序')
    return
  }
  try {
    await loadSortRows()
    clearSortDragState()
    sortMode.value = 'drag'
    sortPage.value = 1
    sortPageSize.value = 20
    sortKeyword.value = ''
    refreshSortPageRows()
    sortCenterVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '排序数据加载失败')
  }
}

async function saveSort() {
  if (!sortDraftRows.value.length) return
  if (hasFilter()) {
    ElMessage.warning('请先清空筛选条件后再保存排序')
    return
  }
  savingSort.value = true
  try {
    await request.post('/fields/reorder', {
      ids: sortDraftRows.value.map((item) => item.id).filter(Boolean)
    })
    ElMessage.success('排序已保存')
    sortCenterVisible.value = false
    await loadFields(page.value)
  } catch (error) {
    ElMessage.error(error.message || '排序保存失败')
  } finally {
    savingSort.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadMetaOptions()])
  await loadFields(1)
})

watch(
  () => sortMode.value,
  (mode) => {
    sortPage.value = 1
    if (mode === 'drag') {
      sortKeyword.value = ''
    }
    refreshSortPageRows()
  }
)

watch(
  () => sortKeyword.value,
  () => {
    if (sortMode.value !== 'button') return
    sortPage.value = 1
    syncSortPage()
  }
)

watch(
  () => sortPageTotalRecords.value,
  () => {
    syncSortPage()
    if (sortMode.value === 'drag') {
      refreshSortPageRows()
    }
  }
)

watch(
  () => sortCenterVisible.value,
  (visible) => {
    if (visible) return
    document.removeEventListener('drag', detectSortTurnZone)
    document.removeEventListener('dragover', detectSortTurnZone)
    document.removeEventListener('mousemove', detectSortTurnZone)
    document.removeEventListener('touchmove', detectSortTurnZone)
    clearSortDragState()
  }
)
</script>

<style scoped>
.field-manage-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.list-cover {
  width: 56px;
  height: 56px;
  border-radius: 8px;
}

.variety-group-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.variety-pair-tag {
  max-width: 100%;
}

.address-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.address-main {
  color: var(--text-main);
  line-height: 1.4;
}

.address-detail {
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.3;
}

.text-sub {
  color: var(--text-sub);
}

.sort-center-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.sort-center-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sort-center-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-center-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
  color: var(--text-sub);
  font-size: 12px;
}

.sort-center-body {
  max-height: calc(100vh - 290px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
}

.drag-handle {
  cursor: move;
  color: var(--text-sub);
  font-size: 16px;
  line-height: 1;
  margin-top: 2px;
}

.drag-order {
  min-width: 20px;
  color: var(--text-sub);
  font-size: 12px;
  margin-top: 2px;
}

.drag-main {
  min-width: 0;
  flex: 1;
}

.drag-name {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
}

.drag-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 22px;
}

.drag-status {
  border-radius: 999px;
  padding: 0 8px;
  min-height: 20px;
  line-height: 20px;
  border: 1px solid rgba(22, 103, 183, 0.28);
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 12px;
}

.drag-sub {
  margin-top: 2px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-sub);
  font-size: 12px;
}

.drag-crops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.drag-extra {
  margin-top: 2px;
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drag-meta {
  margin-left: auto;
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
}

.sort-op {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.jump-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sort-center-foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>


