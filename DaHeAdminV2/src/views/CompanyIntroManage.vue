<template>
  <div class="page">
    <PageToolbar
      title="企业介绍管理"
      subtitle="维护企业基础信息、核心产品、荣誉资质与联系方式。"
      :collapsible="false"
    >
      <div class="actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-header">
          <span>企业基础信息</span>
          <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存信息</el-button>
        </div>
      </template>

      <el-form label-width="110px">
        <el-form-item label="公司名称">
          <el-input v-model="infoForm.companyName" placeholder="请输入公司名称" />
        </el-form-item>
        <el-form-item label="Logo 图片">
          <ImageAssetPicker
            v-model="infoForm.logo"
            upload-module-key="company_logo"
            :upload-biz-id="infoForm.id || ''"
            upload-folder-path="/默认"
            placeholder="请选择或上传 Logo"
          />
        </el-form-item>
        <el-form-item label="Banner 图片">
          <ImageAssetPicker
            v-model="infoForm.banner"
            upload-module-key="company_banner"
            :upload-biz-id="infoForm.id || ''"
            upload-folder-path="/默认"
            placeholder="请选择或上传 Banner"
          />
        </el-form-item>
        <el-form-item label="公司简介">
          <el-input v-model="infoForm.introduction" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="企业使命">
          <el-input v-model="infoForm.mission" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="版权信息">
          <el-input v-model="infoForm.copyright" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-header">
          <span>核心产品</span>
          <el-button type="primary" @click="openProductCreate">新增产品</el-button>
        </div>
      </template>
      <el-table :data="productRows" border>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="image" label="图片URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="Number(scope.row.status) === 1 ? 'success' : 'info'">
              {{ Number(scope.row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openProductEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="toggleProductEnabled(scope.row)">
              {{ Number(scope.row.status) === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteProduct(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-header">
          <span>荣誉资质</span>
          <el-button type="primary" @click="openHonorCreate">新增荣誉</el-button>
        </div>
      </template>
      <el-table :data="honorRows" border>
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="image" label="图片URL" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="Number(scope.row.status) === 1 ? 'success' : 'info'">
              {{ Number(scope.row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openHonorEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="toggleHonorEnabled(scope.row)">
              {{ Number(scope.row.status) === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteHonor(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-header">
          <span>联系方式</span>
          <el-button type="primary" @click="openContactCreate">新增联系方式</el-button>
        </div>
      </template>
      <el-table :data="contactRows" border>
        <el-table-column prop="contactType" label="类型" width="120" />
        <el-table-column prop="contactLabel" label="标签" width="140" />
        <el-table-column prop="contactValue" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="Number(scope.row.status) === 1 ? 'success' : 'info'">
              {{ Number(scope.row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openContactEdit(scope.row)">编辑</el-button>
            <el-button size="small" @click="toggleContactEnabled(scope.row)">
              {{ Number(scope.row.status) === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteContact(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="productDialog.visible" :title="productDialog.id ? '编辑产品' : '新增产品'" width="680px">
      <el-form label-width="90px">
        <el-form-item label="产品名称">
          <el-input v-model="productDialog.form.name" />
        </el-form-item>
        <el-form-item label="产品描述">
          <el-input v-model="productDialog.form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="产品图片">
          <ImageAssetPicker
            v-model="productDialog.form.image"
            upload-module-key="company_product"
            :upload-biz-id="productDialog.id || ''"
            upload-folder-path="/默认"
            placeholder="请选择或上传产品图片"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="productDialog.form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="productDialogEnabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="productDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="productDialog.saving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="honorDialog.visible" :title="honorDialog.id ? '编辑荣誉' : '新增荣誉'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="荣誉名称">
          <el-input v-model="honorDialog.form.name" />
        </el-form-item>
        <el-form-item label="荣誉图片">
          <ImageAssetPicker
            v-model="honorDialog.form.image"
            upload-module-key="company_honor"
            :upload-biz-id="honorDialog.id || ''"
            upload-folder-path="/默认"
            placeholder="请选择或上传荣誉图片"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="honorDialog.form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="honorDialogEnabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="honorDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="honorDialog.saving" @click="saveHonor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contactDialog.visible" :title="contactDialog.id ? '编辑联系方式' : '新增联系方式'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="联系方式类型">
          <el-select v-model="contactDialog.form.contactType" style="width: 100%">
            <el-option label="电话" value="phone" />
            <el-option label="邮箱" value="email" />
            <el-option label="地址" value="address" />
            <el-option label="网址" value="website" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示标签">
          <el-input v-model="contactDialog.form.contactLabel" />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="contactDialog.form.contactValue" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="contactDialog.form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="contactDialogEnabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="contactDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="contactDialog.saving" @click="saveContact">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import ImageAssetPicker from '../components/ui/ImageAssetPicker.vue'
import request from '../utils/request'

const loading = ref(false)
const savingInfo = ref(false)

const infoForm = reactive({
  id: null,
  companyName: '',
  logo: '',
  banner: '',
  introduction: '',
  mission: '',
  copyright: ''
})

const productRows = ref([])
const honorRows = ref([])
const contactRows = ref([])

const productDialog = reactive({
  visible: false,
  id: null,
  saving: false,
  form: {
    name: '',
    description: '',
    image: '',
    sortOrder: 1,
    status: 1
  }
})

const honorDialog = reactive({
  visible: false,
  id: null,
  saving: false,
  form: {
    name: '',
    image: '',
    sortOrder: 1,
    status: 1
  }
})

const contactDialog = reactive({
  visible: false,
  id: null,
  saving: false,
  form: {
    contactType: 'phone',
    contactLabel: '',
    contactValue: '',
    sortOrder: 1,
    status: 1
  }
})

const productDialogEnabled = computed({
  get: () => Number(productDialog.form.status) === 1,
  set: (val) => {
    productDialog.form.status = val ? 1 : 0
  }
})

const honorDialogEnabled = computed({
  get: () => Number(honorDialog.form.status) === 1,
  set: (val) => {
    honorDialog.form.status = val ? 1 : 0
  }
})

const contactDialogEnabled = computed({
  get: () => Number(contactDialog.form.status) === 1,
  set: (val) => {
    contactDialog.form.status = val ? 1 : 0
  }
})

function normalizeText(value) {
  const text = String(value == null ? '' : value).trim()
  return text || null
}

function applyInfo(row) {
  infoForm.id = row && row.id ? row.id : null
  infoForm.companyName = (row && row.companyName) || '大禾种业'
  infoForm.logo = (row && row.logo) || ''
  infoForm.banner = (row && row.banner) || ''
  infoForm.introduction = (row && row.introduction) || ''
  infoForm.mission = (row && row.mission) || ''
  infoForm.copyright = (row && row.copyright) || ''
}

async function loadAll() {
  loading.value = true
  try {
    const data = (await request.get('/admin/company-intro')) || {}
    applyInfo(data.companyInfo || null)
    productRows.value = Array.isArray(data.products) ? data.products : []
    honorRows.value = Array.isArray(data.honors) ? data.honors : []
    contactRows.value = Array.isArray(data.contacts) ? data.contacts : []
  } catch (error) {
    ElMessage.error(error.message || '加载企业介绍数据失败')
  } finally {
    loading.value = false
  }
}

async function saveInfo() {
  const companyName = normalizeText(infoForm.companyName)
  if (!companyName) {
    ElMessage.warning('公司名称不能为空')
    return
  }
  savingInfo.value = true
  try {
    const payload = {
      id: infoForm.id,
      companyName,
      logo: normalizeText(infoForm.logo),
      banner: normalizeText(infoForm.banner),
      introduction: normalizeText(infoForm.introduction),
      mission: normalizeText(infoForm.mission),
      copyright: normalizeText(infoForm.copyright)
    }
    const row = await request.put('/admin/company-intro/info', payload)
    applyInfo(row || payload)
    ElMessage.success('企业信息已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    savingInfo.value = false
  }
}

function openProductCreate() {
  productDialog.id = null
  productDialog.form.name = ''
  productDialog.form.description = ''
  productDialog.form.image = ''
  productDialog.form.sortOrder = productRows.value.length + 1
  productDialog.form.status = 1
  productDialog.visible = true
}

function openProductEdit(row) {
  productDialog.id = row.id
  productDialog.form.name = row.name || ''
  productDialog.form.description = row.description || ''
  productDialog.form.image = row.image || ''
  productDialog.form.sortOrder = Number(row.sortOrder || 0)
  productDialog.form.status = Number(row.status || 0) === 1 ? 1 : 0
  productDialog.visible = true
}

async function saveProduct() {
  const name = normalizeText(productDialog.form.name)
  if (!name) {
    ElMessage.warning('产品名称不能为空')
    return
  }
  productDialog.saving = true
  try {
    const payload = {
      name,
      description: normalizeText(productDialog.form.description),
      image: normalizeText(productDialog.form.image),
      sortOrder: Number(productDialog.form.sortOrder || 0),
      status: Number(productDialog.form.status || 0) === 1 ? 1 : 0
    }
    if (productDialog.id) {
      await request.put(`/admin/company-intro/products/${productDialog.id}`, payload)
    } else {
      await request.post('/admin/company-intro/products', payload)
    }
    productDialog.visible = false
    ElMessage.success('产品保存成功')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '产品保存失败')
  } finally {
    productDialog.saving = false
  }
}

async function toggleProductEnabled(row) {
  try {
    await request.put(`/admin/company-intro/products/${row.id}/enabled`, {
      enabled: Number(row.status || 0) !== 1
    })
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '更新产品状态失败')
  }
}

async function deleteProduct(row) {
  try {
    await ElMessageBox.confirm(`确认删除产品「${row.name || '-'}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/admin/company-intro/products/${row.id}`)
    ElMessage.success('产品已删除')
    await loadAll()
  } catch (error) {
    if (String(error || '').toLowerCase().includes('cancel')) return
    ElMessage.error(error.message || '删除产品失败')
  }
}

function openHonorCreate() {
  honorDialog.id = null
  honorDialog.form.name = ''
  honorDialog.form.image = ''
  honorDialog.form.sortOrder = honorRows.value.length + 1
  honorDialog.form.status = 1
  honorDialog.visible = true
}

function openHonorEdit(row) {
  honorDialog.id = row.id
  honorDialog.form.name = row.name || ''
  honorDialog.form.image = row.image || ''
  honorDialog.form.sortOrder = Number(row.sortOrder || 0)
  honorDialog.form.status = Number(row.status || 0) === 1 ? 1 : 0
  honorDialog.visible = true
}

async function saveHonor() {
  const name = normalizeText(honorDialog.form.name)
  if (!name) {
    ElMessage.warning('荣誉名称不能为空')
    return
  }
  honorDialog.saving = true
  try {
    const payload = {
      name,
      image: normalizeText(honorDialog.form.image),
      sortOrder: Number(honorDialog.form.sortOrder || 0),
      status: Number(honorDialog.form.status || 0) === 1 ? 1 : 0
    }
    if (honorDialog.id) {
      await request.put(`/admin/company-intro/honors/${honorDialog.id}`, payload)
    } else {
      await request.post('/admin/company-intro/honors', payload)
    }
    honorDialog.visible = false
    ElMessage.success('荣誉保存成功')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '荣誉保存失败')
  } finally {
    honorDialog.saving = false
  }
}

async function toggleHonorEnabled(row) {
  try {
    await request.put(`/admin/company-intro/honors/${row.id}/enabled`, {
      enabled: Number(row.status || 0) !== 1
    })
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '更新荣誉状态失败')
  }
}

async function deleteHonor(row) {
  try {
    await ElMessageBox.confirm(`确认删除荣誉「${row.name || '-'}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/admin/company-intro/honors/${row.id}`)
    ElMessage.success('荣誉已删除')
    await loadAll()
  } catch (error) {
    if (String(error || '').toLowerCase().includes('cancel')) return
    ElMessage.error(error.message || '删除荣誉失败')
  }
}

function openContactCreate() {
  contactDialog.id = null
  contactDialog.form.contactType = 'phone'
  contactDialog.form.contactLabel = ''
  contactDialog.form.contactValue = ''
  contactDialog.form.sortOrder = contactRows.value.length + 1
  contactDialog.form.status = 1
  contactDialog.visible = true
}

function openContactEdit(row) {
  contactDialog.id = row.id
  contactDialog.form.contactType = row.contactType || 'phone'
  contactDialog.form.contactLabel = row.contactLabel || ''
  contactDialog.form.contactValue = row.contactValue || ''
  contactDialog.form.sortOrder = Number(row.sortOrder || 0)
  contactDialog.form.status = Number(row.status || 0) === 1 ? 1 : 0
  contactDialog.visible = true
}

async function saveContact() {
  const contactType = normalizeText(contactDialog.form.contactType)
  const contactLabel = normalizeText(contactDialog.form.contactLabel)
  const contactValue = normalizeText(contactDialog.form.contactValue)
  if (!contactType || !contactLabel || !contactValue) {
    ElMessage.warning('联系方式类型、标签和内容都不能为空')
    return
  }
  contactDialog.saving = true
  try {
    const payload = {
      contactType,
      contactLabel,
      contactValue,
      sortOrder: Number(contactDialog.form.sortOrder || 0),
      status: Number(contactDialog.form.status || 0) === 1 ? 1 : 0
    }
    if (contactDialog.id) {
      await request.put(`/admin/company-intro/contacts/${contactDialog.id}`, payload)
    } else {
      await request.post('/admin/company-intro/contacts', payload)
    }
    contactDialog.visible = false
    ElMessage.success('联系方式保存成功')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '联系方式保存失败')
  } finally {
    contactDialog.saving = false
  }
}

async function toggleContactEnabled(row) {
  try {
    await request.put(`/admin/company-intro/contacts/${row.id}/enabled`, {
      enabled: Number(row.status || 0) !== 1
    })
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '更新联系方式状态失败')
  }
}

async function deleteContact(row) {
  try {
    await ElMessageBox.confirm(`确认删除联系方式「${row.contactLabel || '-'}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/admin/company-intro/contacts/${row.id}`)
    ElMessage.success('联系方式已删除')
    await loadAll()
  } catch (error) {
    if (String(error || '').toLowerCase().includes('cancel')) return
    ElMessage.error(error.message || '删除联系方式失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.actions {
  display: flex;
  gap: 8px;
}

.card {
  border: 1px solid var(--border);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
</style>
