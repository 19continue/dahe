<template>
  <div class="crop-manage-page">
    <PageToolbar
      title="作物管理"
      subtitle="分类与品种分层管理：左侧分类卡片，右侧品种表格，点击查看后进入分栏模式。"
      collapsible
      :summary="[
        filters.keyword ? `分类关键词：${filters.keyword}` : '分类列表',
        detailVisible && selectedCategory ? `当前分类：${selectedCategory.name}` : '',
        detailVisible && varietyKeyword ? `品种关键词：${varietyKeyword}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" clearable placeholder="分类关键字" style="width: 220px" @keyup.enter="loadCategories(1)" />
        <el-button @click="loadCategories(1)">查询分类</el-button>
        <el-button type="primary" @click="openCreateCategory">新增作物分类</el-button>
        <el-button type="primary" plain :disabled="!selectedCategory" @click="openCreateVariety">新增品种</el-button>
      </div>
    </PageToolbar>

    <div :class="['crop-workbench', { 'split-active': detailVisible }]">
      <section class="crop-panel category-panel" v-loading="loadingCategories">
        <header class="panel-head">
          <div>
            <span>作物分类</span>
            <span class="card-meta">共 {{ categoryTotal }} 条</span>
          </div>
          <el-button v-if="detailVisible" size="small" @click="collapseDetail">收起右栏</el-button>
        </header>

        <div :class="['category-list', { expanded: detailVisible }]">
          <article
            v-for="row in categories"
            :key="row.id"
            :class="['category-card', { active: sameId(row.id, selectedCategoryId) }]"
            @click="openDetail(row)"
          >
            <div class="category-main">
              <el-image v-if="row.imageUrl" :src="row.imageUrl" fit="cover" class="category-thumb" />
              <div v-else class="category-thumb placeholder">{{ row.name ? row.name.slice(0, 1) : '?' }}</div>
              <div class="category-content">
                <div class="category-title">{{ row.name || '-' }}</div>
                <div class="category-meta">排序 {{ row.sortOrder || 0 }}</div>
              </div>
            </div>

            <div class="category-actions">
              <el-button size="small" type="primary" @click.stop="openEditCategory(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click.stop="removeCategory(row)">删除</el-button>
            </div>
          </article>

          <el-empty v-if="!categories.length" description="暂无作物分类" :image-size="56" />
        </div>

        <div class="table-foot">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="categoryTotal"
            :page-size="categoryPageSize"
            :current-page="categoryPage"
            :page-sizes="[12, 15, 18, 24, 30]"
            @size-change="onCategoryPageSizeChange"
            @current-change="loadCategories"
          />
        </div>
      </section>

      <transition name="detail-slide">
        <section v-show="detailVisible" class="crop-panel variety-panel" v-loading="loadingVarieties">
          <header class="panel-head">
            <div>
              <span>品种列表</span>
              <span class="card-meta">{{ selectedCategory ? `${selectedCategory.name} · 共 ${varietyTotal} 条` : '请选择分类' }}</span>
            </div>
            <div class="actions">
              <el-input v-model="varietyKeyword" clearable placeholder="品种关键字" style="width: 180px" @keyup.enter="loadVarieties(1)" />
              <el-button @click="loadVarieties(1)">查询</el-button>
              <el-button type="primary" :disabled="!selectedCategory" @click="openCreateVariety">新增品种</el-button>
              <el-button :type="varietyBatchMode ? 'primary' : 'default'" plain :disabled="!selectedCategory" @click="toggleVarietyBatchMode">
                {{ varietyBatchMode ? '退出多选' : '多选操作' }}
              </el-button>
              <el-button
                v-if="varietyBatchMode"
                type="danger"
                plain
                :disabled="!varietyDeleteCount || varietyBatchDeleting"
                :loading="varietyBatchDeleting"
                @click="removeSelectedVarieties"
              >
                删除已选（{{ varietyDeleteCount }}）
              </el-button>
            </div>
          </header>

          <el-table ref="varietyTableRef" :data="varieties" border @selection-change="onVarietySelectionChange">
            <el-table-column
              v-if="varietyBatchMode"
              type="selection"
              width="46"
              align="center"
            />
            <el-table-column label="图片" width="86">
              <template #default="scope">
                <el-image v-if="scope.row.imageUrl" :src="scope.row.imageUrl" fit="cover" class="thumb" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="varietyName" label="品种名" min-width="180" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column label="操作" width="152" fixed="right" class-name="op-col">
              <template #default="scope">
                <div class="table-op-line">
                  <el-button size="small" type="primary" @click="openEditVariety(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click="removeVariety(scope.row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-foot">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="varietyTotal"
              :page-size="varietyPageSize"
              :current-page="varietyPage"
              :page-sizes="[12, 24, 36, 48]"
              @size-change="onVarietyPageSizeChange"
              @current-change="loadVarieties"
            />
          </div>
        </section>
      </transition>
    </div>

    <el-dialog v-model="categoryDialog.visible" :title="categoryDialog.editMode ? '编辑作物分类' : '新增作物分类'" width="620px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="分类名称">
          <el-input v-model="categoryDialog.form.name" placeholder="例如：玉米" />
        </el-form-item>
        <el-form-item label="分类图片">
          <ImageAssetPicker
            v-model="categoryDialog.form.imageUrl"
            upload-module-key="crop_category"
            :upload-biz-id="categoryDialog.form.id || ''"
            upload-folder-path="/作物封面"
            placeholder="请选择或上传分类图片"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="categoryDialog.saving" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="varietyDialog.visible" :title="varietyDialog.editMode ? '编辑品种' : '新增品种'" width="620px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="所属分类">
          <el-select v-model="varietyDialog.form.parentId" filterable style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种名称">
          <el-input v-model="varietyDialog.form.variety" placeholder="例如：先玉335" />
        </el-form-item>
        <el-form-item label="品种图片">
          <ImageAssetPicker
            v-model="varietyDialog.form.imageUrl"
            upload-module-key="crop_variety"
            :upload-biz-id="varietyDialog.form.id || ''"
            upload-folder-path="/作物封面"
            placeholder="请选择或上传品种图片"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varietyDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="varietyDialog.saving" @click="saveVariety">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageAssetPicker from '../components/ui/ImageAssetPicker.vue'
import PageToolbar from '../components/ui/PageToolbar.vue'
import request from '../utils/request'

const filters = reactive({
  keyword: ''
})

const loadingCategories = ref(false)
const loadingVarieties = ref(false)
const detailVisible = ref(false)

const categories = ref([])
const categoryPage = ref(1)
const categoryPageSize = ref(12)
const categoryTotal = ref(0)
const selectedCategoryId = ref(null)
const selectedCategory = ref(null)

const varieties = ref([])
const varietyKeyword = ref('')
const varietyPage = ref(1)
const varietyPageSize = ref(12)
const varietyTotal = ref(0)
const varietyTableRef = ref(null)
const varietyBatchMode = ref(false)
const selectedVarietyRows = ref([])
const varietyBatchDeleting = ref(false)

const categoryOptions = ref([])
const varietyDeleteCount = computed(() => {
  return selectedVarietyRows.value.length
})

const categoryDialog = reactive({
  visible: false,
  editMode: false,
  saving: false,
  form: {
    id: null,
    name: '',
    imageUrl: ''
  }
})

const varietyDialog = reactive({
  visible: false,
  editMode: false,
  saving: false,
  form: {
    id: null,
    parentId: null,
    variety: '',
    imageUrl: ''
  }
})

function toId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function sameId(a, b) {
  const aid = toId(a)
  const bid = toId(b)
  return !!aid && !!bid && aid === bid
}

function mapCategory(row) {
  return {
    id: toId(row && row.id),
    name: (row && row.name) || '',
    imageUrl: (row && row.imageUrl) || '',
    sortOrder: Number((row && row.sortOrder) || 0)
  }
}

function mapVariety(row) {
  return {
    id: toId(row && row.id),
    parentId: toId(row && row.parentId),
    categoryName: (row && row.name) || '',
    varietyName: (row && (row.variety || row.name)) || '',
    imageUrl: (row && row.imageUrl) || '',
    sortOrder: Number((row && row.sortOrder) || 0)
  }
}

async function loadCategoryOptions() {
  try {
    const pageSize = 120
    let nextPage = 1
    let expectedTotal = Number.MAX_SAFE_INTEGER
    const merged = []
    while ((nextPage - 1) * pageSize < expectedTotal) {
      const data = await request.get('/crops', {
        params: {
          page: nextPage,
          pageSize,
          nodeType: 'category'
        }
      })
      const records = ((data && data.records) || []).map(mapCategory)
      merged.push(...records)
      expectedTotal = Number((data && data.total) || merged.length || 0)
      if (!records.length || records.length < pageSize) break
      nextPage += 1
      if (nextPage > 30) break
    }
    categoryOptions.value = merged
  } catch (error) {
    categoryOptions.value = []
  }
}

async function ensureCategoryOptions(force = false) {
  if (!force && categoryOptions.value.length) return
  await loadCategoryOptions()
}

async function refreshCategoryOptionsIfLoaded() {
  if (!categoryOptions.value.length) return
  await loadCategoryOptions()
}

async function loadCategories(nextPage = categoryPage.value) {
  loadingCategories.value = true
  try {
    categoryPage.value = Number(nextPage || 1)
    const data = await request.get('/crops', {
      params: {
        page: categoryPage.value,
        pageSize: categoryPageSize.value,
        nodeType: 'category',
        keyword: filters.keyword || undefined
      }
    })
    const records = (data && data.records) || []
    categories.value = records.map(mapCategory)
    categoryTotal.value = Number((data && data.total) || 0)

    if (selectedCategoryId.value) {
      const hit = categories.value.find((item) => sameId(item && item.id, selectedCategoryId.value))
      if (hit) {
        selectedCategory.value = hit
      }
    }

    if (!selectedCategory.value && categories.value.length) {
      selectedCategory.value = categories.value[0]
      selectedCategoryId.value = selectedCategory.value.id
    }
  } catch (error) {
    categories.value = []
    categoryTotal.value = 0
    ElMessage.error(error.message || '作物分类加载失败')
  } finally {
    loadingCategories.value = false
  }
}

async function loadVarieties(nextPage = varietyPage.value) {
  if (!selectedCategoryId.value) {
    varieties.value = []
    varietyTotal.value = 0
    clearVarietySelection()
    return
  }
  loadingVarieties.value = true
  try {
    varietyPage.value = Number(nextPage || 1)
    const data = await request.get('/crops', {
      params: {
        page: varietyPage.value,
        pageSize: varietyPageSize.value,
        nodeType: 'variety',
        parentId: selectedCategoryId.value,
        keyword: varietyKeyword.value || undefined
      }
    })
    varieties.value = ((data && data.records) || []).map(mapVariety)
    varietyTotal.value = Number((data && data.total) || 0)
    clearVarietySelection()
  } catch (error) {
    varieties.value = []
    varietyTotal.value = 0
    clearVarietySelection()
    ElMessage.error(error.message || '品种列表加载失败')
  } finally {
    loadingVarieties.value = false
  }
}

function onCategoryPageSizeChange(size) {
  categoryPageSize.value = Number(size || 12)
  loadCategories(1)
}

function onVarietyPageSizeChange(size) {
  varietyPageSize.value = Number(size || 12)
  loadVarieties(1)
}

function clearVarietySelection() {
  selectedVarietyRows.value = []
  nextTick(() => {
    if (varietyTableRef.value && typeof varietyTableRef.value.clearSelection === 'function') {
      varietyTableRef.value.clearSelection()
    }
  })
}

function onVarietySelectionChange(rowsInput) {
  selectedVarietyRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function toggleVarietyBatchMode() {
  varietyBatchMode.value = !varietyBatchMode.value
  if (!varietyBatchMode.value) {
    clearVarietySelection()
  }
}

function selectCategory(row) {
  selectedCategoryId.value = toId(row && row.id) || null
  selectedCategory.value = row || null
}

function openDetail(row) {
  selectCategory(row)
  detailVisible.value = true
  loadVarieties(1)
}

function collapseDetail() {
  detailVisible.value = false
}

function resetCategoryForm() {
  categoryDialog.form.id = null
  categoryDialog.form.name = ''
  categoryDialog.form.imageUrl = ''
}

function openCreateCategory() {
  categoryDialog.editMode = false
  resetCategoryForm()
  categoryDialog.visible = true
}

function openEditCategory(row) {
  categoryDialog.editMode = true
  categoryDialog.form.id = row.id
  categoryDialog.form.name = row.name || ''
  categoryDialog.form.imageUrl = row.imageUrl || ''
  categoryDialog.visible = true
}

async function saveCategory() {
  const name = String(categoryDialog.form.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入作物分类名称')
    return
  }
  categoryDialog.saving = true
  try {
    const payload = {
      nodeType: 'category',
      name,
      imageUrl: categoryDialog.form.imageUrl ? categoryDialog.form.imageUrl.trim() : null
    }
    let saved = null
    if (categoryDialog.editMode && categoryDialog.form.id) {
      saved = await request.put(`/crops/${categoryDialog.form.id}`, payload)
    } else {
      saved = await request.post('/crops', payload)
    }
    categoryDialog.visible = false
    await loadCategories(categoryPage.value)
    await refreshCategoryOptionsIfLoaded()
    if (saved && toId(saved.id)) {
      const id = toId(saved.id)
      const hit = categories.value.find((item) => sameId(item && item.id, id))
      if (hit) {
        selectedCategoryId.value = id
        selectedCategory.value = hit
      }
    }
    ElMessage.success('分类保存成功')
  } catch (error) {
    ElMessage.error(error.message || '分类保存失败')
  } finally {
    categoryDialog.saving = false
  }
}

async function removeCategory(row) {
  try {
    await ElMessageBox.confirm(`确认删除分类“${row.name}”吗？其下品种将一并删除。`, '删除确认', { type: 'warning' })
    await request.delete(`/crops/${row.id}`)
    ElMessage.success('分类已删除')
    if (sameId(row && row.id, selectedCategoryId.value)) {
      selectedCategoryId.value = null
      selectedCategory.value = null
      detailVisible.value = false
      varieties.value = []
      varietyTotal.value = 0
    }
    await loadCategories(1)
    await refreshCategoryOptionsIfLoaded()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function resetVarietyForm() {
  varietyDialog.form.id = null
  varietyDialog.form.parentId = toId(selectedCategoryId.value) || null
  varietyDialog.form.variety = ''
  varietyDialog.form.imageUrl = ''
}

async function openCreateVariety() {
  if (!selectedCategory.value) {
    ElMessage.warning('请先选择作物分类')
    return
  }
  await ensureCategoryOptions()
  varietyDialog.editMode = false
  resetVarietyForm()
  varietyDialog.visible = true
}

async function openEditVariety(row) {
  await ensureCategoryOptions()
  varietyDialog.editMode = true
  varietyDialog.form.id = toId(row && row.id) || null
  varietyDialog.form.parentId = toId(row && row.parentId) || toId(selectedCategoryId.value) || null
  varietyDialog.form.variety = row.varietyName || ''
  varietyDialog.form.imageUrl = row.imageUrl || ''
  varietyDialog.visible = true
}

async function saveVariety() {
  const parentId = toId(varietyDialog.form.parentId)
  const variety = String(varietyDialog.form.variety || '').trim()
  if (!parentId) {
    ElMessage.warning('请选择所属分类')
    return
  }
  if (!variety) {
    ElMessage.warning('请输入品种名称')
    return
  }

  varietyDialog.saving = true
  try {
    const payload = {
      nodeType: 'variety',
      parentId,
      variety,
      imageUrl: varietyDialog.form.imageUrl ? varietyDialog.form.imageUrl.trim() : null
    }
    if (varietyDialog.editMode && varietyDialog.form.id) {
      await request.put(`/crops/${varietyDialog.form.id}`, payload)
    } else {
      await request.post('/crops', payload)
    }
    varietyDialog.visible = false
    selectedCategoryId.value = parentId
    const hit = categories.value.find((item) => sameId(item && item.id, parentId))
    if (hit) selectedCategory.value = hit
    detailVisible.value = true
    await loadVarieties(varietyPage.value)
    ElMessage.success('品种保存成功')
  } catch (error) {
    ElMessage.error(error.message || '品种保存失败')
  } finally {
    varietyDialog.saving = false
  }
}

async function removeVariety(row) {
  try {
    await ElMessageBox.confirm(`确认删除品种“${row.varietyName}”吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/crops/${row.id}`)
    ElMessage.success('品种已删除')
    await loadVarieties(varietyPage.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

async function removeSelectedVarieties() {
  if (!selectedCategoryId.value) return
  const ids = Array.from(new Set(
    selectedVarietyRows.value
      .map((row) => toId(row && row.id))
      .filter(Boolean)
  ))
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的品种')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 个品种吗？`, '批量删除确认', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
    return
  }

  varietyBatchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => request.delete(`/crops/${id}`)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (successCount > 0) {
      const remainAfterDelete = Math.max(0, varietyTotal.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(varietyPageSize.value || 12)))
      await loadVarieties(Math.min(varietyPage.value, maxPage))
    } else {
      clearVarietySelection()
    }
    if (failedCount > 0) {
      ElMessage.warning(`已删除 ${successCount} 个，${failedCount} 个删除失败`)
      return
    }
    ElMessage.success(`已删除 ${successCount} 个品种`)
  } catch (error) {
    ElMessage.error(error.message || '批量删除失败')
  } finally {
    varietyBatchDeleting.value = false
  }
}

onMounted(async () => {
  await loadCategories(1)
})
</script>

<style scoped>
.crop-manage-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.crop-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 0;
  gap: 12px;
  transition: grid-template-columns 0.28s ease;
}

.crop-workbench.split-active {
  grid-template-columns: minmax(300px, 34%) minmax(0, 66%);
}

.crop-panel {
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  min-height: 440px;
  padding: 10px 8px;
}

.panel-head {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.category-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.category-list.expanded {
  grid-template-columns: 1fr;
}

.category-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-soft);
  overflow: hidden;
  cursor: pointer;
}

.category-card:hover {
  background: rgba(22, 103, 183, 0.05);
}

.category-card.active {
  border-color: rgba(22, 103, 183, 0.45);
  box-shadow: 0 0 0 2px rgba(22, 103, 183, 0.08);
}

.category-main {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
}

.category-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  flex-shrink: 0;
}

.category-thumb.placeholder {
  border: 1px dashed var(--border-strong);
  background: var(--bg-panel);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  font-size: 16px;
  font-weight: 700;
}

.category-content {
  min-width: 0;
}

.category-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.category-meta {
  margin-top: 4px;
  color: var(--text-sub);
  font-size: 12px;
}

.category-actions {
  padding: 0 10px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
}

.detail-slide-enter-active,
.detail-slide-leave-active {
  transition: opacity 0.22s ease, transform 0.26s ease;
}

.detail-slide-enter-from,
.detail-slide-leave-to {
  opacity: 0;
  transform: translateX(16px);
}

@media (max-width: 1180px) {
  .crop-workbench,
  .crop-workbench.split-active {
    grid-template-columns: 1fr;
  }

  .category-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .category-list.expanded {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .category-list {
    grid-template-columns: 1fr;
  }
}
</style>
