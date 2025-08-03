<template>
  <div class="miniapp-static-assets-page">
    <PageToolbar
      title="小程序静态资源"
      subtitle="这里管理小程序固定 URL 的静态资源，与普通业务资源分开；删除会同时删除数据库记录和服务器文件。"
      :collapsible="false"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="展示名称/保存名称/备注" clearable style="width: 240px" @keyup.enter="loadRows(1)" />
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openUploadDialog">上传静态资源</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never">
      <el-table :data="rows" border v-loading="loading">
        <el-table-column label="预览" width="96">
          <template #default="scope">
            <el-image
              v-if="scope.row.fileType === 'image'"
              :src="scope.row.fileUrl"
              fit="cover"
              style="width: 56px; height: 56px; border-radius: 8px"
              :preview-src-list="[scope.row.fileUrl]"
              preview-teleported
            />
            <el-tag v-else size="small" effect="plain" type="info">文件</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="展示名称" min-width="180">
          <template #default="scope">
            <div class="name-cell">
              <span class="main-name">{{ scope.row.displayName || scope.row.storageName || '-' }}</span>
              <span v-if="scope.row.remark" class="sub-remark">{{ scope.row.remark }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="保存名称" width="190">
          <template #default="scope">
            <span>{{ scope.row.storageName }}{{ scope.row.fileExt || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="访问地址" min-width="260">
          <template #default="scope">
            <div class="url-cell">
              <a :href="scope.row.fileUrl" target="_blank" rel="noopener noreferrer" class="url-link">
                {{ scope.row.fileUrl }}
              </a>
              <el-button link type="primary" @click="copyUrl(scope.row.fileUrl)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110" align="right">
          <template #default="scope">{{ formatSize(scope.row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="时间" width="190">
          <template #default="scope">
            <div class="time-cell">
              <span>创建：{{ formatDateTime(scope.row.createdAt) }}</span>
              <span>更新：{{ formatDateTime(scope.row.updatedAt) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" class-name="op-col">
          <template #default="scope">
            <div class="row-actions table-op-line compact-actions">
              <el-button size="small" type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="removeRow(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-foot">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="rowTotal"
          :page-size="rowPageSize"
          :current-page="rowPage"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="onPageSizeChange"
          @current-change="loadRows"
        />
      </div>
    </el-card>

    <el-dialog v-model="uploadDialogVisible" title="上传小程序静态资源" width="620px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="保存文件名称">
          <el-input v-model="uploadForm.storageName" placeholder="只填名称，不填后缀，例如：logo-main" />
          <div class="field-hint">文件会保存到服务器固定目录 `/assets`，最终 URL 由文件名和真实后缀组成。</div>
        </el-form-item>
        <el-form-item label="展示名称">
          <el-input v-model="uploadForm.displayName" placeholder="用于后台查看，可不填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="3" placeholder="例如：首页品牌 Logo" />
        </el-form-item>
        <el-form-item label="本地文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :show-file-list="true"
            :on-change="onUploadFileChange"
            :on-remove="onUploadFileRemove"
            :on-exceed="onUploadFileExceed"
          >
            <el-button type="primary" plain>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传并保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog.visible" title="编辑静态资源信息" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="展示名称">
          <el-input v-model="editDialog.form.displayName" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { getUser } from '../utils/auth'
import {
  deleteMiniappStaticAsset,
  listMiniappStaticAssets,
  updateMiniappStaticAsset,
  uploadMiniappStaticAsset
} from '../api/miniappStaticAssets'

const router = useRouter()
const currentUser = ref(getUser() || {})
const loading = ref(false)
const uploading = ref(false)
const uploadDialogVisible = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const rows = ref([])
const rowTotal = ref(0)
const rowPage = ref(1)
const rowPageSize = ref(20)

const filters = reactive({
  keyword: ''
})

const uploadForm = reactive({
  storageName: '',
  displayName: '',
  remark: ''
})

const editDialog = reactive({
  visible: false,
  saving: false,
  rowId: null,
  form: {
    displayName: '',
    remark: ''
  }
})

function ensureSuperAdmin() {
  if (Number((currentUser.value && currentUser.value.isSuperAdmin) || 0) === 1) {
    return true
  }
  ElMessage.error('仅超级管理员可管理小程序静态资源')
  router.replace('/dashboard')
  return false
}

async function loadRows(page = rowPage.value) {
  if (!ensureSuperAdmin()) return
  loading.value = true
  try {
    const res = await listMiniappStaticAssets({
      keyword: filters.keyword,
      page,
      pageSize: rowPageSize.value
    })
    rows.value = Array.isArray(res.records) ? res.records : []
    rowTotal.value = Number(res.total || 0)
    rowPage.value = Number(res.current || page)
  } catch (error) {
    ElMessage.error(error.message || '加载小程序静态资源失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  loadRows(1)
}

function onPageSizeChange(size) {
  rowPageSize.value = Number(size || 20)
  loadRows(1)
}

function openUploadDialog() {
  if (!ensureSuperAdmin()) return
  uploadForm.storageName = ''
  uploadForm.displayName = ''
  uploadForm.remark = ''
  selectedFile.value = null
  uploadDialogVisible.value = true
}

function onUploadFileChange(file) {
  selectedFile.value = file && file.raw ? file.raw : null
}

function onUploadFileRemove() {
  selectedFile.value = null
}

function onUploadFileExceed() {
  ElMessage.warning('一次只能上传一个文件')
}

async function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  if (!String(uploadForm.storageName || '').trim()) {
    ElMessage.warning('请填写保存文件名称')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('storageName', String(uploadForm.storageName || '').trim())
    formData.append('displayName', String(uploadForm.displayName || '').trim())
    formData.append('remark', String(uploadForm.remark || '').trim())
    await uploadMiniappStaticAsset(formData)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    loadRows(1)
  } catch (error) {
    ElMessage.error(error.message || '上传小程序静态资源失败')
  } finally {
    uploading.value = false
  }
}

function openEditDialog(row) {
  if (!row || !row.id) return
  editDialog.rowId = row.id
  editDialog.form.displayName = row.displayName || ''
  editDialog.form.remark = row.remark || ''
  editDialog.visible = true
}

async function submitEdit() {
  if (!editDialog.rowId) return
  editDialog.saving = true
  try {
    await updateMiniappStaticAsset(editDialog.rowId, {
      displayName: editDialog.form.displayName,
      remark: editDialog.form.remark
    })
    ElMessage.success('保存成功')
    editDialog.visible = false
    loadRows(rowPage.value)
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    editDialog.saving = false
  }
}

async function removeRow(row) {
  if (!row || !row.id) return
  try {
    const { value } = await ElMessageBox.prompt(
      `该静态资源会被永久删除，并同步删除服务器中的文件：${row.storageName || '-'}${row.fileExt || ''}`,
      '请输入超级管理员密码',
      {
        inputType: 'password',
        inputPlaceholder: '请输入当前超级管理员登录密码',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await deleteMiniappStaticAsset(row.id, value)
    ElMessage.success('删除成功')
    loadRows(rowTotal.value > (rowPage.value - 1) * rowPageSize.value + 1 ? rowPage.value : Math.max(1, rowPage.value - 1))
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.message || '删除失败')
  }
}

async function copyUrl(url) {
  try {
    await navigator.clipboard.writeText(String(url || ''))
    ElMessage.success('已复制资源地址')
  } catch (error) {
    ElMessage.error('复制失败，请手动复制')
  }
}

function formatDateTime(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

function formatSize(sizeBytes) {
  const value = Number(sizeBytes || 0)
  if (!value) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}

onMounted(() => {
  if (!ensureSuperAdmin()) return
  loadRows(1)
})
</script>

<style scoped>
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.main-name {
  font-weight: 600;
  color: #1f2937;
}

.sub-remark {
  font-size: 12px;
  color: #6b7280;
}

.url-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.url-link {
  color: #2563eb;
  word-break: break-all;
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #4b5563;
  font-size: 12px;
}

.table-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.field-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.compact-actions {
  gap: 8px;
}
</style>
