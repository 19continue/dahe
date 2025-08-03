<template>
  <div class="assets-manage-page">
    <PageToolbar
      title="资源库"
      subtitle="这里负责正常资源的上传、登记、编辑、锁定与排序；审核中的资源请从“资源审核”入口处理。"
      collapsible
      :summary="[
        filters.keyword ? `关键词：${filters.keyword}` : '',
        filters.folderPath ? `文件夹：${filters.folderPath}` : '',
        filters.sourceType ? `来源：${formatSourceType(filters.sourceType)}` : '',
        filters.reviewStatus ? `审核：${formatReviewStatus(filters.reviewStatus)}` : '',
        filters.lockedFlag === 1 ? '仅看已锁定' : (filters.lockedFlag === 0 ? '仅看未锁定' : ''),
        filters.fileType ? `类型：${filters.fileType === 'image' ? '图片' : '文件'}` : ''
      ]"
    >
      <div class="actions">
        <el-input v-model="filters.keyword" placeholder="文件名/备注关键字" clearable style="width: 220px" @keyup.enter="loadRows(1)" />
        <el-select v-model="filters.folderPath" clearable filterable placeholder="文件夹" style="width: 220px">
          <el-option label="全部" value="" />
          <el-option v-for="item in folderDisplayOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.sourceType" clearable placeholder="来源" style="width: 140px">
          <el-option label="全部" value="" />
          <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.fileType" clearable placeholder="类型" style="width: 120px">
          <el-option label="全部" value="" />
          <el-option label="图片" value="image" />
          <el-option label="文件" value="file" />
        </el-select>
        <el-select v-model="filters.reviewStatus" clearable placeholder="审核状态" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option v-for="item in reviewStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.lockedFlag" clearable placeholder="资源锁" style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="已锁定" :value="1" />
          <el-option label="未锁定" :value="0" />
        </el-select>
        <el-button @click="loadRows(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="openUploadDialog">上传资源</el-button>
        <el-button type="primary" plain @click="openLinkDialog">登记外部链接</el-button>
        <el-button plain @click="goReviewPage">前往资源审核</el-button>
        <el-button plain @click="goRecyclePage">回收站</el-button>
        <el-button type="primary" plain @click="openFolderManage">文件夹管理</el-button>
        <el-button plain @click="router.push('/asset-policy')">上传策略</el-button>
      </div>
    </PageToolbar>

    <div class="stats-strip" v-loading="statsLoading">
      <div class="stats-item">
        <span class="stat-title">资源总数</span>
        <strong class="stat-value">{{ stats.totalCount || 0 }}</strong>
      </div>
      <div class="stats-item">
        <span class="stat-title">图片数量</span>
        <strong class="stat-value">{{ stats.imageCount || 0 }}</strong>
      </div>
      <div class="stats-item">
        <span class="stat-title">文件数量</span>
        <strong class="stat-value">{{ stats.fileCount || 0 }}</strong>
      </div>
      <div class="stats-item">
        <span class="stat-title">总大小</span>
        <strong class="stat-value">{{ formatSize(stats.totalSizeBytes) }}</strong>
      </div>
    </div>

    <el-row :gutter="12" class="assets-layout">
      <el-col v-if="false" :xs="24" :lg="9" :xl="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>上传资源</span>
            </div>
          </template>
          <el-form label-width="90px">
            <el-form-item label="文件名">
              <el-input v-model="uploadForm.fileName" placeholder="可选，不填则使用原文件名" />
            </el-form-item>
            <el-form-item label="文件夹">
              <el-select
                v-model="uploadForm.folderPath"
                filterable
                style="width: 100%"
                placeholder="选择文件夹"
                @change="onUploadFolderChange"
              >
                <el-option v-for="item in writableFolderOptions" :key="`upload-${item}`" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="本地文件">
              <el-upload
                ref="uploadSelectorRef"
                :auto-upload="false"
                :show-file-list="true"
                :limit="1"
                accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
                :on-change="onUploadFileChange"
                :on-remove="onUploadFileRemove"
                :on-exceed="onUploadFileExceed"
              >
                <el-button type="primary" plain>选择文件</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="uploading" @click="submitUpload">上传并保存</el-button>
            </el-form-item>
          </el-form>

          <el-divider>或登记外部链接</el-divider>
          <el-form label-width="90px">
            <el-form-item label="文件名">
              <el-input v-model="linkForm.fileName" placeholder="例如：玉米分类图-春播" />
            </el-form-item>
            <el-form-item label="链接地址">
              <el-input v-model="linkForm.fileUrl" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="文件夹">
              <el-select
                v-model="linkForm.folderPath"
                filterable
                style="width: 100%"
                placeholder="选择文件夹"
                @change="onLinkFolderChange"
              >
                <el-option v-for="item in writableFolderOptions" :key="`link-${item}`" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="linkForm.fileType" clearable style="width: 100%">
                <el-option label="图片" value="image" />
                <el-option label="文件" value="file" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="linkForm.remark" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="linkSaving" @click="saveLink">保存链接</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="24" :xl="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <span>资源列表</span>
                <span v-if="batchMode" class="card-meta">已选 {{ selectedRows.length }} 条</span>
                <span class="card-meta">共 {{ rowTotal }} 条</span>
              </div>
              <div class="actions table-actions">
                <div class="table-actions-toggle">
                  <el-button size="small" :type="batchMode ? 'primary' : 'default'" plain @click="toggleBatchMode">
                    {{ batchMode ? '退出多选' : '多选操作' }}
                  </el-button>
                </div>
                <div class="table-actions-batch">
                  <el-button
                    v-if="batchMode"
                    size="small"
                    type="danger"
                    plain
                    :disabled="!batchDeleteCount || batchDeleting"
                    :loading="batchDeleting"
                    @click="removeSelected"
                  >
                    删除已选（{{ batchDeleteCount }}）
                  </el-button>
                </div>
                <el-button size="small" :disabled="!sortRows.length" @click="openSortCenter">排序中心</el-button>
              </div>
            </div>
          </template>

          <el-table ref="assetTableRef" :data="rows" border v-loading="loading" @selection-change="onAssetSelectionChange">
            <el-table-column
              v-if="batchMode"
              type="selection"
              width="46"
              align="center"
              :selectable="isAssetRowSelectable"
            />
            <el-table-column prop="sortOrder" label="排序" width="70" />
            <el-table-column label="预览" width="92">
              <template #default="scope">
                <el-image
                  v-if="scope.row.fileType === 'image'"
                  :src="scope.row.fileUrl"
                  fit="cover"
                  style="width: 54px; height: 54px; border-radius: 8px"
                  :preview-src-list="[scope.row.fileUrl]"
                  preview-teleported
                />
                <el-tag v-else size="small" effect="plain" type="info">文件</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="文件名" min-width="210">
              <template #default="scope">
                <div class="asset-name-cell">
                  <span class="asset-name">{{ scope.row.fileName || '-' }}</span>
                  <span v-if="scope.row.remark" class="asset-remark">{{ shortText(scope.row.remark, 50) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="文件夹" min-width="170">
              <template #default="scope">
                <div class="folder-cell">
                  <el-tag size="small" effect="plain" type="info">{{ scope.row.folderPath || '/' }}</el-tag>
                  <span v-if="resolveFolderRemark(scope.row.folderPath)" class="folder-remark">
                    {{ resolveFolderRemark(scope.row.folderPath) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="110">
              <template #default="scope">
                <el-tag size="small" effect="plain" :type="sourceTypeTagType(scope.row.sourceType)">
                  {{ formatSourceType(scope.row.sourceType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="scope">
                <el-tag size="small" effect="plain" :type="fileTypeTagType(scope.row.fileType)">
                  {{ scope.row.fileType === 'image' ? '图片' : '文件' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="106" align="right">
              <template #default="scope">{{ formatSize(scope.row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column label="审核状态" width="110">
              <template #default="scope">
                <el-tag
                  size="small"
                  :type="reviewStatusTagType(scope.row.reviewStatus)"
                  effect="light"
                >
                  {{ formatReviewStatus(scope.row.reviewStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核备注" min-width="160">
              <template #default="scope">
                <span>{{ shortText(scope.row.reviewRemark || '-', 24) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="资源锁" width="150">
              <template #default="scope">
                <div class="lock-cell">
                  <el-tag
                    size="small"
                    effect="light"
                    :type="isAssetLocked(scope.row) ? 'warning' : 'info'"
                  >
                    {{ isAssetLocked(scope.row) ? '已锁定' : '未锁定' }}
                  </el-tag>
                  <span v-if="scope.row.lockRemark" class="lock-remark">{{ shortText(scope.row.lockRemark, 16) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createdByName" label="创建人" width="110" />
            <el-table-column label="时间" width="190">
              <template #default="scope">
                <div class="time-cell">
                  <span>创建：{{ formatDateTime(scope.row.createdAt) }}</span>
                  <span>更新：{{ formatDateTime(scope.row.updatedAt) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="地址" min-width="220">
              <template #default="scope">
                <div class="url-cell">
                  <a :href="scope.row.fileUrl" target="_blank" rel="noopener noreferrer" class="url-link">
                    {{ shortText(scope.row.fileUrl, 56) }}
                  </a>
                  <el-button link type="primary" @click="copyUrl(scope.row.fileUrl)">复制</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" :width="288" class-name="op-col" fixed="right">
              <template #default="scope">
                <div class="row-actions table-op-line">
                  <el-button size="small" type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
                  <el-button
                    v-if="isSuperAdmin"
                    size="small"
                    plain
                    @click="openLockDialog(scope.row)"
                  >
                    {{ isAssetLocked(scope.row) ? '解除资源锁' : '设置资源锁' }}
                  </el-button>
                  <el-button size="small" type="danger" plain @click="remove(scope.row)">删除</el-button>
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
              @size-change="onRowPageSizeChange"
              @current-change="loadRows"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="uploadDialogVisible" title="上传资源" width="620px" destroy-on-close align-center>
      <el-form label-width="90px">
        <el-form-item label="文件名">
          <el-input v-model="uploadForm.fileName" placeholder="可选，不填则使用原文件名" />
        </el-form-item>
        <el-form-item label="文件夹">
          <el-select
            v-model="uploadForm.folderPath"
            filterable
            style="width: 100%"
            placeholder="选择文件夹"
            @change="onUploadFolderChange"
          >
            <el-option v-for="item in writableFolderOptions" :key="`upload-dialog-${item}`" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="本地文件">
          <el-upload
            ref="uploadSelectorRef"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
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

    <el-dialog v-model="linkDialogVisible" title="登记外部链接" width="620px" destroy-on-close align-center>
      <el-form label-width="90px">
        <el-form-item label="文件名">
          <el-input v-model="linkForm.fileName" placeholder="例如：玉米分类图-春播" />
        </el-form-item>
        <el-form-item label="链接地址">
          <el-input v-model="linkForm.fileUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="文件夹">
          <el-select
            v-model="linkForm.folderPath"
            filterable
            style="width: 100%"
            placeholder="选择文件夹"
            @change="onLinkFolderChange"
          >
            <el-option v-for="item in writableFolderOptions" :key="`link-dialog-${item}`" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="linkForm.fileType" clearable style="width: 100%">
            <el-option label="图片" value="image" />
            <el-option label="文件" value="file" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="linkForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="linkSaving" @click="saveLink">保存链接</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assetEditDialog.visible" title="编辑资源" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="文件名">
          <el-input v-model="assetEditDialog.form.fileName" placeholder="请输入文件名" />
        </el-form-item>
        <el-form-item label="文件夹">
          <el-select v-model="assetEditDialog.form.folderPath" filterable style="width: 100%">
            <el-option v-for="item in writableFolderOptions" :key="`edit-folder-${item}`" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assetEditDialog.form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assetEditDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="assetEditDialog.saving" @click="saveAssetEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assetLockDialog.visible" :title="assetLockDialog.mode === 'lock' ? '设置资源锁' : '解除资源锁'" width="560px" destroy-on-close>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="assetLockDialog.fileName ? `资源：${assetLockDialog.fileName}` : '资源锁'"
        :description="assetLockDialog.mode === 'lock'
          ? '资源锁用于防止误删；全局密码请在“资源上传策略”中设置。'
          : '解除资源锁前，需要输入当前的全局资源锁密码。'"
      />
      <el-form label-width="90px" style="margin-top: 12px">
        <el-form-item v-if="assetLockDialog.mode === 'unlock'" label="解锁密码" required>
          <el-input
            v-model="assetLockDialog.form.unlockPassword"
            type="password"
            show-password
            placeholder="请输入当前全局资源锁密码"
          />
        </el-form-item>
        <el-form-item label="锁定备注">
          <el-input
            v-model="assetLockDialog.form.lockRemark"
            type="textarea"
            :rows="3"
            :placeholder="assetLockDialog.mode === 'lock'
              ? '例如：品牌主视觉，删除前请先确认替换资源'
              : '解除后将清空该资源的锁定备注'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assetLockDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="assetLockDialog.saving" @click="submitAssetLock">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assetReferenceDialog.visible"
      title="删除确认"
      width="640px"
      destroy-on-close
      @closed="handleAssetReferenceDialogClosed"
    >
      <div class="asset-reference-confirm">
        <div class="asset-reference-confirm-title">
          该资源已被以下模块使用，删除后会影响以下模块的功能，确定删除吗？
        </div>
        <div v-loading="assetReferenceDialog.loading" class="asset-reference-confirm-list">
          <template v-if="assetReferenceDialog.groups.length">
            <div
              v-for="group in assetReferenceDialog.groups"
              :key="group.moduleKey"
              class="asset-reference-confirm-group"
            >
              <div class="asset-reference-confirm-module">{{ group.moduleName }}</div>
              <div
                v-for="item in group.items"
                :key="`${group.moduleKey}-${item.bizId}-${item.summary}`"
                class="asset-reference-confirm-item"
              >
                <div class="asset-reference-confirm-summary">{{ item.summary }}</div>
              </div>
            </div>
            <div v-if="assetReferenceDialog.hasMore" class="asset-reference-confirm-more">
              <el-button size="small" @click="loadMoreAssetReferences">点击加载更多</el-button>
            </div>
          </template>
          <el-empty v-else description="当前资源暂无业务引用" :image-size="60" />
        </div>
      </div>
      <template #footer>
        <el-button @click="cancelAssetReferenceDialog">取消</el-button>
        <el-button type="danger" :loading="assetReferenceDialog.confirming" @click="confirmAssetReferenceDialog">确定删除</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sortCenterVisible" title="资源排序中心" width="960px" destroy-on-close align-center>
      <SortCenterShell
        ref="sortShellRef"
        :mode="sortMode"
        :keyword="sortKeyword"
        keyword-placeholder="按钮模式下可按文件名/文件夹搜索"
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
          @drag-start="onAssetSortDragStart"
          @drag-end="onAssetSortDragEnd"
          @drag-move="onAssetSortDragMove"
        >
          <template #item="{ row }">
            <span class="drag-handle">⋮⋮</span>
            <span class="drag-order">{{ resolveSortIndex(row) }}</span>
            <div class="drag-main">
              <div class="drag-title-row">
                <span class="drag-name">{{ row.fileName || '未命名资源' }}</span>
                <span class="drag-status">{{ row.fileType === 'image' ? '图片' : '文件' }}</span>
              </div>
              <div class="drag-sub">
                <span>{{ row.folderPath || '-' }}</span>
                <span>·</span>
                <span>{{ row.createdByName || '-' }}</span>
                <span>·</span>
                <span>{{ formatDateTime(row.createdAt) }}</span>
              </div>
              <div class="drag-extra">{{ row.fileUrl || '-' }}</div>
            </div>
            <span class="drag-meta">ID {{ row.id }}</span>
          </template>
        </SortDragBoard>

        <div v-show="sortMode !== 'drag'">
          <el-table :data="sortCenterRows" border>
            <el-table-column label="序号" width="72">
              <template #default="scope">{{ resolveSortIndex(scope.row) }}</template>
            </el-table-column>
            <el-table-column prop="fileName" label="文件名" min-width="220" />
            <el-table-column prop="folderPath" label="文件夹" width="180">
              <template #default="scope">{{ scope.row.folderPath || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源" width="120">
              <template #default="scope">{{ formatSourceType(scope.row.sourceType) }}</template>
            </el-table-column>
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
import { computed, nextTick, reactive, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { ASSET_FOLDERS_ROUTE, ASSET_RECYCLE_ROUTE, ASSET_REVIEW_ROUTE } from '../utils/adminRouteMap'
import SortCenterShell from '../components/ui/SortCenterShell.vue'
import SortDragBoard from '../components/ui/SortDragBoard.vue'
import { getUser, isAdmin as isAdminUser } from '../utils/auth'
import {
  listAdminAssets,
  listAssetFolders,
  uploadAssetFile,
  createAssetLink,
  updateAsset,
  removeAsset,
  restoreAsset,
  purgeAsset,
  reviewAsset,
  updateAssetLock,
  reorderAssets,
  getAssetStats,
  getAssetReferenceDetails
} from '../api/assets'

const router = useRouter()
const loading = ref(false)
const statsLoading = ref(false)
const savingSort = ref(false)
const linkSaving = ref(false)
const uploading = ref(false)
const uploadDialogVisible = ref(false)
const linkDialogVisible = ref(false)
const rows = ref([])
const rowPage = ref(1)
const rowPageSize = ref(10)
const rowTotal = ref(0)
const assetTableRef = ref(null)
const uploadSelectorRef = ref(null)
const batchMode = ref(false)
const selectedRows = ref([])
const batchDeleting = ref(false)
const sortRows = ref([])
const sortCenterVisible = ref(false)
const sortMode = ref('drag')
const sortKeyword = ref('')
const sortDraftRows = ref([])
const sortInitialRows = ref([])
const dragPageRows = ref([])
const sortShellRef = ref(null)
const sortPage = ref(1)
const sortPageSize = ref(10)
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

function getSortBodyEl() {
  if (!sortShellRef.value || typeof sortShellRef.value.getBodyEl !== 'function') return null
  return sortShellRef.value.getBodyEl()
}
const uploadRefKey = ref('')
const linkRefKey = ref('')
const stats = ref({
  totalCount: 0,
  imageCount: 0,
  fileCount: 0,
  totalSizeBytes: 0,
  moduleStats: []
})
const moduleOptions = ref(['field', 'crop_category', 'crop_variety', 'farm', 'seed', 'export', 'system', 'amap', 'auth'])
const sourceTypeOptions = [
  { label: '后台上传', value: 'admin_upload' },
  { label: '小程序用户上传', value: 'miniapp_upload' },
  { label: '系统上传', value: 'system_upload' }
]
const reviewStatusOptions = [
  { label: '待审核', value: 'pending' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' }
]
const currentUser = ref(getUser() || {})
const isAdmin = computed(() => isAdminUser())
const isSuperAdmin = computed(() => Number((currentUser.value && currentUser.value.isSuperAdmin) || 0) === 1)
const strictPurgeSourceTypes = new Set(['miniapp_upload', 'operator_upload'])
const DEFAULT_FOLDER_PATH = '/默认'
const MINIAPP_UPLOAD_FOLDER_PATH = '/小程序上传图片'
const FIELD_IMAGE_FOLDER_PATH = '/田块封面'
const CROP_COVER_FOLDER_PATH = '/作物封面'
const folderOptions = ref([DEFAULT_FOLDER_PATH, MINIAPP_UPLOAD_FOLDER_PATH, FIELD_IMAGE_FOLDER_PATH, CROP_COVER_FOLDER_PATH])
const folderRemarkMap = ref(new Map([
  [DEFAULT_FOLDER_PATH, '默认文件夹（受保护）'],
  [MINIAPP_UPLOAD_FOLDER_PATH, '小程序上传图片（受保护）'],
  [FIELD_IMAGE_FOLDER_PATH, '田块封面（田块封面、卡片与详情展示）'],
  [CROP_COVER_FOLDER_PATH, '作物封面（作物分类与品种封面）']
]))
const uploadFileRaw = ref(null)
const assetEditDialog = reactive({
  visible: false,
  saving: false,
  id: null,
  form: {
    fileName: '',
    folderPath: DEFAULT_FOLDER_PATH,
    remark: ''
  }
})
const assetReviewDialog = reactive({
  visible: false,
  saving: false,
  id: null,
  fileName: '',
  sourceType: '',
  statusOptions: [],
  form: {
    reviewStatus: 'approved',
    reviewRemark: ''
  }
})
const assetLockDialog = reactive({
  visible: false,
  saving: false,
  id: null,
  mode: 'lock',
  fileName: '',
  form: {
    locked: 1,
    unlockPassword: '',
    lockRemark: ''
  }
})
const assetReferenceDialog = reactive({
  visible: false,
  loading: false,
  confirming: false,
  row: null,
  total: 0,
  nextOffset: 0,
  hasMore: false,
  groups: [],
  resolver: null,
  rejecter: null
})
const filters = reactive({
  keyword: '',
  recycleFlag: 0,
  folderPath: '',
  sourceType: '',
  reviewStatus: '',
  lockedFlag: '',
  moduleKey: '',
  bizRefKey: '',
  bizId: null,
  fileType: ''
})

const uploadForm = reactive({
  fileName: '',
  moduleKey: '',
  bizId: null,
  folderPath: DEFAULT_FOLDER_PATH,
  remark: ''
})

const linkForm = reactive({
  fileName: '',
  fileUrl: '',
  fileType: 'image',
  moduleKey: '',
  bizId: null,
  folderPath: DEFAULT_FOLDER_PATH,
  remark: ''
})

const writableFolderOptions = computed(() => {
  const set = new Set([DEFAULT_FOLDER_PATH, MINIAPP_UPLOAD_FOLDER_PATH, FIELD_IMAGE_FOLDER_PATH, CROP_COVER_FOLDER_PATH])
  ;(folderOptions.value || []).forEach((item) => {
    const path = normalizeFolderPath(item)
    if (path) set.add(path)
  })
  return Array.from(set)
})

const folderDisplayOptions = computed(() => {
  return (folderOptions.value || [])
    .map((item) => normalizeFolderPath(item))
    .filter(Boolean)
    .filter((item, index, list) => list.indexOf(item) === index)
    .map((item) => {
      const remark = resolveFolderRemark(item)
      return {
        value: item,
        label: remark ? `${item}（${remark}）` : item
      }
    })
    .sort((a, b) => a.value.localeCompare(b.value, 'zh-CN'))
})

const sortFilteredRows = computed(() => {
  const keyword = String(sortKeyword.value || '').trim().toLowerCase()
  if (!keyword) return sortDraftRows.value
  return sortDraftRows.value.filter((row) => {
    const name = String(row.fileName || '').toLowerCase()
    const folderPath = String(row.folderPath || '').toLowerCase()
    return name.includes(keyword) || folderPath.includes(keyword)
  })
})

const sortButtonRows = computed(() => {
  if (sortMode.value !== 'button') return sortDraftRows.value
  return sortFilteredRows.value
})

const sortPageTotalRecords = computed(() => sortButtonRows.value.length)
const batchDeleteCount = computed(() => {
  return selectedRows.value.filter((row) => isAssetRowSelectable(row)).length
})
const batchRestoreCount = computed(() => {
  return selectedRows.value.filter((row) => isAssetRowSelectable(row)).length
})
const batchPurgeCount = computed(() => {
  return selectedRows.value.filter((row) => canPurgeAsset(row)).length
})

const sortPageTotal = computed(() => {
  const total = Number(sortPageTotalRecords.value || 0)
  const size = Math.max(1, Number(sortPageSize.value || 10))
  return Math.max(1, Math.ceil(total / size))
})

const sortCenterRows = computed(() => {
  const size = Math.max(1, Number(sortPageSize.value || 10))
  const start = (Math.max(1, Number(sortPage.value || 1)) - 1) * size
  return sortButtonRows.value.slice(start, start + size)
})

const sortIndexMap = computed(() => {
  const map = new Map()
  sortDraftRows.value.forEach((item, idx) => {
    map.set(toId(item && item.id), idx + 1)
  })
  return map
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

function isAssetRowSelectable(row) {
  if (!row) return false
  if (isAssetLocked(row) && !isSuperAdmin.value) {
    return false
  }
  return true
}

function normalizeSourceType(sourceType) {
  const key = String(sourceType || '').trim().toLowerCase()
  if (key === 'operator_upload') return 'miniapp_upload'
  return key
}

function normalizeFolderPath(value) {
  let text = String(value || '').trim()
  if (!text) return DEFAULT_FOLDER_PATH
  text = text.replace(/\\/g, '/')
  while (text.includes('//')) {
    text = text.replace(/\/\//g, '/')
  }
  if (!text.startsWith('/')) {
    text = `/${text}`
  }
  if (text.length > 1 && text.endsWith('/')) {
    text = text.slice(0, -1)
  }
  if (!text) return DEFAULT_FOLDER_PATH
  const lower = text.toLowerCase()
  if (lower === '/default') return DEFAULT_FOLDER_PATH
  if (lower === '/field' || text === '/田块图片') return FIELD_IMAGE_FOLDER_PATH
  if (lower === '/crop') return CROP_COVER_FOLDER_PATH
  return text
}

function resolveFolderRemark(path) {
  const key = normalizeFolderPath(path || DEFAULT_FOLDER_PATH)
  const map = folderRemarkMap.value instanceof Map ? folderRemarkMap.value : new Map()
  return String(map.get(key) || '').trim()
}

function openUploadDialog() {
  loadFolderOptions(Number(filters.recycleFlag || 0))
  uploadDialogVisible.value = true
}

function openLinkDialog() {
  loadFolderOptions(Number(filters.recycleFlag || 0))
  linkDialogVisible.value = true
}

function normalizeReviewStatus(status) {
  const text = String(status || '').trim().toLowerCase()
  if (text === 'pending') return 'pending'
  if (text === 'rejected') return 'rejected'
  return 'approved'
}

function isStrictPurgeSource(sourceType) {
  return strictPurgeSourceTypes.has(normalizeSourceType(sourceType))
}

function canPurgeAsset(row) {
  if (!row) return false
  if (Number(row.recycleFlag || filters.recycleFlag || 0) !== 1) return false
  const sourceType = normalizeSourceType(row.sourceType)
  if (isStrictPurgeSource(sourceType) && !isSuperAdmin.value) {
    return false
  }
  return true
}

function isAssetLocked(row) {
  return Number((row && row.lockedFlag) || 0) === 1
}

function purgeDisabledReason(row) {
  if (!row) return ''
  if (Number(row.recycleFlag || filters.recycleFlag || 0) !== 1) {
    return '请先移入回收站后再彻底删除'
  }
  const sourceType = normalizeSourceType(row.sourceType)
  if (isStrictPurgeSource(sourceType) && !isSuperAdmin.value) {
    return '小程序用户上传资源仅超级管理员可彻底删除'
  }
  return ''
}

function canReviewAsset(row) {
  if (!row) return false
  if (!isAdmin.value) return false
  if (Number(row.recycleFlag || filters.recycleFlag || 0) !== 0) return false
  const sourceType = normalizeSourceType(row.sourceType)
  if (sourceType === 'miniapp_upload') return true
  return normalizeReviewStatus(row.reviewStatus) !== 'approved'
}

function clearAssetSelection() {
  selectedRows.value = []
  nextTick(() => {
    if (assetTableRef.value && typeof assetTableRef.value.clearSelection === 'function') {
      assetTableRef.value.clearSelection()
    }
  })
}

function onAssetSelectionChange(rowsInput) {
  selectedRows.value = Array.isArray(rowsInput) ? rowsInput : []
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    clearAssetSelection()
  }
}

function mergeModuleOptions(rowsInput) {
  const merged = new Set((moduleOptions.value || []).map((x) => String(x || '').trim()).filter(Boolean))
  ;(rowsInput || []).forEach((row) => {
    const key = String((row && row.moduleKey) || '').trim()
    if (key) merged.add(key)
  })
  moduleOptions.value = Array.from(merged)
}

function referenceKey(row) {
  const moduleKey = String((row && row.moduleKey) || '').trim()
  const bizId = Number((row && row.bizId) || 0)
  if (!moduleKey || !(bizId > 0)) return ''
  return `${moduleKey}:${bizId}`
}

function parseReferenceKey(value) {
  const text = String(value || '').trim()
  if (!text || !text.includes(':')) {
    return { moduleKey: '', bizId: null }
  }
  const idx = text.indexOf(':')
  const moduleKey = text.slice(0, idx).trim()
  const bizId = Number(text.slice(idx + 1).trim())
  return {
    moduleKey,
    bizId: Number.isFinite(bizId) && bizId > 0 ? bizId : null
  }
}

function referenceLabel(row) {
  if (!row) return '-'
  const moduleText = formatModule(row.moduleKey)
  const bizLabel = String(row.bizLabel || '').trim() || `业务ID#${row.bizId || '-'}`
  return `${moduleText} · ${bizLabel}（${Number(row.assetCount || 0)}）`
}

async function loadRows(nextPage = rowPage.value) {
  loading.value = true
  try {
    rowPage.value = Number(nextPage || 1)
    const data = await listAdminAssets({
      page: rowPage.value,
      pageSize: rowPageSize.value,
      keyword: filters.keyword || undefined,
      recycleFlag: Number(filters.recycleFlag || 0),
      folderPath: filters.folderPath ? normalizeFolderPath(filters.folderPath) : undefined,
      sourceType: filters.sourceType || undefined,
      reviewStatus: filters.reviewStatus || undefined,
      lockedFlag: filters.lockedFlag === '' ? undefined : Number(filters.lockedFlag),
      fileType: filters.fileType || undefined
    })
    rows.value = (data && data.records) || []
    rowTotal.value = Number((data && data.total) || 0)
    const mergedFolders = new Set(folderOptions.value || [DEFAULT_FOLDER_PATH])
    ;(rows.value || []).forEach((item) => {
      const path = normalizeFolderPath(item && item.folderPath)
      if (path) mergedFolders.add(path)
    })
    folderOptions.value = Array.from(mergedFolders)
    sortRows.value = rows.value.map((item) => ({
      id: toId(item && item.id),
      fileName: item.fileName || '-',
      folderPath: item.folderPath || '',
      sourceType: item.sourceType || '',
      fileType: item.fileType || '',
      sortOrder: item.sortOrder
    }))
    mergeModuleOptions(rows.value)
    clearAssetSelection()
  } catch (e) {
    rows.value = []
    rowTotal.value = 0
    clearAssetSelection()
    ElMessage.error(e.message || '资源加载失败')
  } finally {
    loading.value = false
  }
}

function onRowPageSizeChange(size) {
  rowPageSize.value = Number(size || 10)
  loadRows(1)
}

function resetFilters() {
  filters.keyword = ''
  filters.recycleFlag = 0
  filters.folderPath = ''
  filters.sourceType = ''
  filters.reviewStatus = ''
  filters.lockedFlag = ''
  filters.moduleKey = ''
  filters.bizRefKey = ''
  filters.bizId = null
  filters.fileType = ''
  loadRows(1)
}

function goReviewPage() {
  router.push(ASSET_REVIEW_ROUTE)
}

function onFilterModuleChange() {
  filters.bizRefKey = ''
  filters.bizId = null
}

function onFilterBizRefChange(value) {
  const parsed = parseReferenceKey(value)
  filters.bizId = parsed.bizId
  if (!String(filters.moduleKey || '').trim() && parsed.moduleKey) {
    filters.moduleKey = parsed.moduleKey
  }
}

function onUploadModuleChange() {
  uploadRefKey.value = ''
  uploadForm.bizId = null
}

function onUploadRefChange(value) {
  const parsed = parseReferenceKey(value)
  uploadForm.bizId = parsed.bizId
  if (parsed.moduleKey) {
    uploadForm.moduleKey = parsed.moduleKey
  }
}

function onLinkModuleChange() {
  linkRefKey.value = ''
  linkForm.bizId = null
}

function onLinkRefChange(value) {
  const parsed = parseReferenceKey(value)
  linkForm.bizId = parsed.bizId
  if (parsed.moduleKey) {
    linkForm.moduleKey = parsed.moduleKey
  }
}

function applyReferenceFilter(row) {
  if (!row) return
  filters.moduleKey = row.moduleKey || ''
  filters.bizRefKey = referenceKey(row)
  filters.bizId = Number(row.bizId || 0) || null
  loadRows(1)
}

function applyReferenceToUpload(row) {
  if (!row) return
  uploadForm.moduleKey = row.moduleKey || ''
  uploadForm.bizId = Number(row.bizId || 0) || null
  uploadRefKey.value = referenceKey(row)
}

function onUploadFolderChange(value) {
  uploadForm.folderPath = normalizeFolderPath(value)
}

function onLinkFolderChange(value) {
  linkForm.folderPath = normalizeFolderPath(value)
}

async function loadFolderOptions(recycleFlag = Number(filters.recycleFlag || 0)) {
  try {
    let manageRows = []
    try {
      const rows = await listAssetFolderManageRows()
      manageRows = Array.isArray(rows) ? rows : []
    } catch (error) {
      manageRows = []
    }
    if (manageRows.length) {
      const set = new Set([DEFAULT_FOLDER_PATH, MINIAPP_UPLOAD_FOLDER_PATH, FIELD_IMAGE_FOLDER_PATH, CROP_COVER_FOLDER_PATH])
      const remarkMap = new Map([
        [DEFAULT_FOLDER_PATH, '默认文件夹（受保护）'],
        [MINIAPP_UPLOAD_FOLDER_PATH, '小程序上传图片（受保护）'],
        [FIELD_IMAGE_FOLDER_PATH, '田块封面（田块封面、卡片与详情展示）'],
        [CROP_COVER_FOLDER_PATH, '作物封面（作物分类与品种封面）']
      ])
      manageRows.forEach((row) => {
        const path = normalizeFolderPath(row && row.folderPath)
        if (!path) return
        set.add(path)
        const remark = String((row && row.remark) || '').trim()
        if (remark) {
          remarkMap.set(path, remark)
        }
      })
      folderOptions.value = Array.from(set)
      folderRemarkMap.value = remarkMap
      return
    }
    const rows = await listAssetFolders({ recycleFlag })
    const set = new Set([DEFAULT_FOLDER_PATH, MINIAPP_UPLOAD_FOLDER_PATH, FIELD_IMAGE_FOLDER_PATH, CROP_COVER_FOLDER_PATH])
    ;(Array.isArray(rows) ? rows : []).forEach((item) => {
      const path = normalizeFolderPath(item)
      if (path) set.add(path)
    })
    folderOptions.value = Array.from(set)
    folderRemarkMap.value = new Map([
      [DEFAULT_FOLDER_PATH, '默认文件夹（受保护）'],
      [MINIAPP_UPLOAD_FOLDER_PATH, '小程序上传图片（受保护）'],
      [FIELD_IMAGE_FOLDER_PATH, '田块封面（田块封面、卡片与详情展示）'],
      [CROP_COVER_FOLDER_PATH, '作物封面（作物分类与品种封面）']
    ])
  } catch (error) {
    folderOptions.value = [DEFAULT_FOLDER_PATH, MINIAPP_UPLOAD_FOLDER_PATH, FIELD_IMAGE_FOLDER_PATH, CROP_COVER_FOLDER_PATH]
    folderRemarkMap.value = new Map([
      [DEFAULT_FOLDER_PATH, '默认文件夹（受保护）'],
      [MINIAPP_UPLOAD_FOLDER_PATH, '小程序上传图片（受保护）'],
      [FIELD_IMAGE_FOLDER_PATH, '田块封面（田块封面、卡片与详情展示）'],
      [CROP_COVER_FOLDER_PATH, '作物封面（作物分类与品种封面）']
    ])
  }
}

function openFolderManage() {
  router.push(ASSET_FOLDERS_ROUTE)
}

function goRecyclePage() {
  router.push(ASSET_RECYCLE_ROUTE)
}

function onUploadFileChange(file) {
  uploadFileRaw.value = (file && (file.raw || file)) || null
}

function onUploadFileRemove() {
  uploadFileRaw.value = null
}

function onUploadFileExceed() {
  ElMessage.warning('一次仅支持选择一个文件')
}

async function submitUpload() {
  if (!uploadFileRaw.value) {
    ElMessage.warning('请先选择本地文件')
    return
  }
  const form = new FormData()
  form.append('file', uploadFileRaw.value)
  if (String(uploadForm.fileName || '').trim()) {
    form.append('displayName', String(uploadForm.fileName || '').trim())
  }
  form.append('folderPath', normalizeFolderPath(uploadForm.folderPath))
  if (uploadForm.remark) form.append('remark', uploadForm.remark)
  uploading.value = true
  try {
    await uploadAssetFile(form)
    ElMessage.success('上传成功')
    uploadFileRaw.value = null
    uploadForm.fileName = ''
    uploadForm.remark = ''
    uploadDialogVisible.value = false
    if (uploadSelectorRef.value && typeof uploadSelectorRef.value.clearFiles === 'function') {
      uploadSelectorRef.value.clearFiles()
    }
    await Promise.all([loadRows(rowPage.value), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function saveLink() {
  if (!String(linkForm.fileUrl || '').trim()) {
    ElMessage.warning('请填写链接地址')
    return
  }
  linkSaving.value = true
  try {
    await createAssetLink({
      fileName: linkForm.fileName || null,
      fileUrl: linkForm.fileUrl.trim(),
      fileType: linkForm.fileType || null,
      folderPath: normalizeFolderPath(linkForm.folderPath),
      remark: linkForm.remark || null
    })
    ElMessage.success('链接已保存')
    linkForm.fileName = ''
    linkForm.fileUrl = ''
    linkForm.folderPath = DEFAULT_FOLDER_PATH
    linkForm.remark = ''
    linkDialogVisible.value = false
    await Promise.all([loadRows(rowPage.value), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    linkSaving.value = false
  }
}

function openEditDialog(row) {
  if (!row) return
  assetEditDialog.id = row.id
  assetEditDialog.form.fileName = String(row.fileName || '').trim()
  assetEditDialog.form.folderPath = normalizeFolderPath(row.folderPath || DEFAULT_FOLDER_PATH)
  assetEditDialog.form.remark = String(row.remark || '').trim()
  assetEditDialog.visible = true
}

async function saveAssetEdit() {
  const id = assetEditDialog.id
  if (!id) return
  const fileName = String(assetEditDialog.form.fileName || '').trim()
  if (!fileName) {
    ElMessage.warning('请输入文件名')
    return
  }
  assetEditDialog.saving = true
  try {
    await updateAsset(id, {
      fileName,
      folderPath: normalizeFolderPath(assetEditDialog.form.folderPath || DEFAULT_FOLDER_PATH),
      remark: String(assetEditDialog.form.remark || '').trim() || null
    })
    assetEditDialog.visible = false
    ElMessage.success('资源已更新')
    await Promise.all([loadRows(rowPage.value), loadStats(), loadFolderOptions(Number(filters.recycleFlag || 0))])
  } catch (e) {
    ElMessage.error(e.message || '资源更新失败')
  } finally {
    assetEditDialog.saving = false
  }
}

async function resolveUnlockPassword(row, actionText) {
  if (!isAssetLocked(row)) {
    return ''
  }
  try {
    const { value } = await ElMessageBox.prompt(
      `资源“${row.fileName || '-'}”已被锁定，${actionText}前请输入全局解锁密码。`,
      '资源锁校验',
      {
        type: 'warning',
        inputType: 'password',
        inputPlaceholder: '请输入全局资源锁密码',
        inputValidator: (value) => {
          if (!String(value || '').trim()) {
            return '请输入全局资源锁密码'
          }
          return true
        }
      }
    )
    return String(value || '').trim()
  } catch (e) {
    return false
  }
}

async function remove(row) {
  const unlockPassword = await resolveUnlockPassword(row, '移入回收站')
  if (unlockPassword === false) {
    return
  }
  try {
    await confirmRemoveAsset(row)
    await removeAsset(row.id, unlockPassword ? { unlockPassword } : undefined)
    ElMessage.success('已移入回收站')
    await Promise.all([loadRows(rowPage.value), loadStats()])
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

async function confirmRemoveAsset(row) {
  try {
    const data = await getAssetReferenceDetails(row.id, { offset: 0, limit: 5 })
    const refs = Array.isArray(data && data.references) ? data.references : []
    if (!refs.length) {
      await ElMessageBox.confirm(`确认将资源“${row.fileName}”移入回收站吗？`, '删除确认', { type: 'warning' })
      return
    }
    await openAssetReferenceDialog(row, data)
  } catch (e) {
    if (e === 'cancel') {
      throw e
    }
    await ElMessageBox.confirm(`确认将资源“${row.fileName}”移入回收站吗？`, '删除确认', { type: 'warning' })
  }
}

function mergeReferenceGroups(items) {
  const nextGroups = Array.isArray(assetReferenceDialog.groups) ? [...assetReferenceDialog.groups] : []
  ;(Array.isArray(items) ? items : []).forEach((item) => {
    const moduleKey = String(item && item.moduleKey ? item.moduleKey : 'other').trim() || 'other'
    const moduleName = String(item && item.moduleName ? item.moduleName : '其他模块').trim() || '其他模块'
    const summary = String(item && item.summary ? item.summary : '').trim()
    if (!summary) return
    let group = nextGroups.find((entry) => entry.moduleKey === moduleKey)
    if (!group) {
      group = { moduleKey, moduleName, items: [] }
      nextGroups.push(group)
    }
    group.items.push({
      bizId: item && item.bizId ? String(item.bizId).trim() : '',
      summary
    })
  })
  assetReferenceDialog.groups = nextGroups
}

function resetAssetReferenceDialog() {
  assetReferenceDialog.visible = false
  assetReferenceDialog.loading = false
  assetReferenceDialog.confirming = false
  assetReferenceDialog.row = null
  assetReferenceDialog.total = 0
  assetReferenceDialog.nextOffset = 0
  assetReferenceDialog.hasMore = false
  assetReferenceDialog.groups = []
}

function handleAssetReferenceDialogClosed() {
  const rejecter = assetReferenceDialog.rejecter
  if (rejecter) {
    assetReferenceDialog.rejecter = null
    assetReferenceDialog.resolver = null
    rejecter('cancel')
  }
  resetAssetReferenceDialog()
}

function cancelAssetReferenceDialog() {
  const rejecter = assetReferenceDialog.rejecter
  assetReferenceDialog.rejecter = null
  assetReferenceDialog.resolver = null
  assetReferenceDialog.visible = false
  if (typeof rejecter === 'function') {
    rejecter('cancel')
  }
}

function confirmAssetReferenceDialog() {
  const resolver = assetReferenceDialog.resolver
  assetReferenceDialog.rejecter = null
  assetReferenceDialog.resolver = null
  assetReferenceDialog.visible = false
  if (typeof resolver === 'function') {
    resolver()
  }
}

function openAssetReferenceDialog(row, firstPage) {
  return new Promise((resolve, reject) => {
    assetReferenceDialog.row = row || null
    assetReferenceDialog.total = Number((firstPage && firstPage.total) || 0)
    assetReferenceDialog.nextOffset = Number((firstPage && firstPage.nextOffset) || 0)
    assetReferenceDialog.hasMore = !!(firstPage && firstPage.hasMore)
    assetReferenceDialog.groups = []
    mergeReferenceGroups(firstPage && firstPage.references)
    assetReferenceDialog.resolver = resolve
    assetReferenceDialog.rejecter = reject
    assetReferenceDialog.visible = true
  })
}

async function loadMoreAssetReferences() {
  if (!assetReferenceDialog.row || assetReferenceDialog.loading || !assetReferenceDialog.hasMore) {
    return
  }
  assetReferenceDialog.loading = true
  try {
    const data = await getAssetReferenceDetails(assetReferenceDialog.row.id, {
      offset: assetReferenceDialog.nextOffset,
      limit: 5
    })
    mergeReferenceGroups(data && data.references)
    assetReferenceDialog.total = Number((data && data.total) || assetReferenceDialog.total || 0)
    assetReferenceDialog.nextOffset = Number((data && data.nextOffset) || assetReferenceDialog.nextOffset || 0)
    assetReferenceDialog.hasMore = !!(data && data.hasMore)
  } catch (error) {
    ElMessage.error(error.message || '引用信息加载失败')
  } finally {
    assetReferenceDialog.loading = false
  }
}

async function restore(row) {
  try {
    await ElMessageBox.confirm(`确认恢复资源“${row.fileName}”吗？`, '恢复确认', { type: 'info' })
    await restoreAsset(row.id)
    ElMessage.success('恢复成功')
    await Promise.all([loadRows(rowPage.value), loadStats()])
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '恢复失败')
    }
  }
}

async function purge(row) {
  if (!canPurgeAsset(row)) {
    ElMessage.warning(purgeDisabledReason(row) || '当前资源不允许彻底删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认彻底删除资源“${row.fileName}”吗？该操作不可恢复。`, '彻底删除确认', { type: 'warning' })
    await purgeAsset(row.id)
    ElMessage.success('已彻底删除')
    await Promise.all([loadRows(rowPage.value), loadStats()])
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '彻底删除失败')
    }
  }
}

function openLockDialog(row) {
  if (!isSuperAdmin.value) {
    ElMessage.warning('仅超级管理员可设置或解除资源锁')
    return
  }
  assetLockDialog.id = row.id
  assetLockDialog.mode = isAssetLocked(row) ? 'unlock' : 'lock'
  assetLockDialog.fileName = row.fileName || ''
  assetLockDialog.form.locked = isAssetLocked(row) ? 0 : 1
  assetLockDialog.form.unlockPassword = ''
  assetLockDialog.form.lockRemark = String(row.lockRemark || '').trim()
  assetLockDialog.visible = true
}

function openReviewDialog(row) {
  if (!canReviewAsset(row)) {
    ElMessage.warning('当前资源不允许审核')
    return
  }
  const sourceType = normalizeSourceType(row.sourceType)
  assetReviewDialog.id = row.id
  assetReviewDialog.fileName = row.fileName || ''
  assetReviewDialog.sourceType = sourceType
  assetReviewDialog.statusOptions = sourceType === 'miniapp_upload'
    ? [...reviewStatusOptions]
    : reviewStatusOptions.filter((item) => item.value === 'approved')
  const currentStatus = normalizeReviewStatus(row.reviewStatus || 'pending')
  const allowedValues = new Set(assetReviewDialog.statusOptions.map((item) => item.value))
  assetReviewDialog.form.reviewStatus = allowedValues.has(currentStatus)
    ? currentStatus
    : (assetReviewDialog.statusOptions[0] ? assetReviewDialog.statusOptions[0].value : 'approved')
  assetReviewDialog.form.reviewRemark = String(row.reviewRemark || '').trim()
  assetReviewDialog.visible = true
}

async function submitAssetReview() {
  const id = assetReviewDialog.id
  if (!id) return
  const nextStatus = normalizeReviewStatus(assetReviewDialog.form.reviewStatus)
  const nextRemark = String(assetReviewDialog.form.reviewRemark || '').trim()
  if (nextStatus === 'rejected' && !nextRemark) {
    ElMessage.warning('驳回时请填写审核备注')
    return
  }
  assetReviewDialog.saving = true
  try {
    await reviewAsset(id, {
      reviewStatus: nextStatus,
      reviewRemark: nextRemark || null
    })
    assetReviewDialog.visible = false
    ElMessage.success(nextStatus === 'rejected' ? '已驳回并放入回收站' : '审核结果已提交')
    await loadRows(rowPage.value)
  } catch (e) {
    ElMessage.error(e.message || '审核失败')
  } finally {
    assetReviewDialog.saving = false
  }
}

async function submitAssetLock() {
  if (!assetLockDialog.id) return
  const nextLocked = Number(assetLockDialog.form.locked || 0)
  const unlockPassword = String(assetLockDialog.form.unlockPassword || '').trim()
  if (assetLockDialog.mode === 'unlock' && !unlockPassword) {
    ElMessage.warning('请输入全局资源锁密码')
    return
  }
  assetLockDialog.saving = true
  try {
    await updateAssetLock(assetLockDialog.id, {
      locked: nextLocked,
      unlockPassword: unlockPassword || null,
      lockRemark: String(assetLockDialog.form.lockRemark || '').trim() || null
    })
    assetLockDialog.visible = false
    ElMessage.success(nextLocked === 1 ? '资源锁已保存' : '资源锁已解除')
    await loadRows(rowPage.value)
  } catch (e) {
    ElMessage.error(e.message || '资源锁保存失败')
  } finally {
    assetLockDialog.saving = false
  }
}

function getSelectedAssetIds() {
  const ids = Array.from(new Set(
    selectedRows.value
      .filter((row) => isAssetRowSelectable(row))
      .map((row) => Number(row && row.id))
      .filter((id) => Number.isFinite(id) && id > 0)
  ))
  return ids
}

async function removeSelected() {
  const ids = getSelectedAssetIds()
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的资源')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除已选的 ${ids.length} 个资源吗？`, '批量删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量删除失败')
    }
    return
  }

  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => removeAsset(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    if (successCount > 0) {
      const remainAfterDelete = Math.max(0, rowTotal.value - successCount)
      const maxPage = Math.max(1, Math.ceil(remainAfterDelete / Number(rowPageSize.value || 10)))
      await Promise.all([loadRows(Math.min(rowPage.value, maxPage)), loadStats()])
    } else {
      clearAssetSelection()
    }
    if (failedCount > 0) {
      ElMessage.warning(`已移入回收站 ${successCount} 个，${failedCount} 个失败`)
      return
    }
    ElMessage.success(`已移入回收站 ${successCount} 个资源`)
  } catch (e) {
    ElMessage.error(e.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

async function restoreSelected() {
  const ids = getSelectedAssetIds()
  if (!ids.length) {
    ElMessage.warning('请先选择要恢复的资源')
    return
  }
  try {
    await ElMessageBox.confirm(`确认恢复已选的 ${ids.length} 个资源吗？`, '批量恢复确认', { type: 'info' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量恢复失败')
    }
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => restoreAsset(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await Promise.all([loadRows(rowPage.value), loadStats()])
    if (failedCount > 0) {
      ElMessage.warning(`已恢复 ${successCount} 个，${failedCount} 个恢复失败`)
      return
    }
    ElMessage.success(`已恢复 ${successCount} 个资源`)
  } catch (e) {
    ElMessage.error(e.message || '批量恢复失败')
  } finally {
    batchDeleting.value = false
  }
}

async function purgeSelected() {
  const selectedCount = selectedRows.value.length
  const purgeRows = selectedRows.value.filter((row) => canPurgeAsset(row))
  const ids = Array.from(new Set(
    purgeRows
      .map((row) => Number(row && row.id))
      .filter((id) => Number.isFinite(id) && id > 0)
  ))
  if (!ids.length) {
    ElMessage.warning('当前所选资源中没有可彻底删除的条目')
    return
  }
  try {
    await ElMessageBox.confirm(`确认彻底删除已选的 ${ids.length} 个资源吗？该操作不可恢复。`, '批量彻底删除确认', { type: 'warning' })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量彻底删除失败')
    }
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(ids.map((id) => purgeAsset(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled').length
    const failedCount = results.length - successCount
    await Promise.all([loadRows(rowPage.value), loadStats()])
    if (failedCount > 0) {
      ElMessage.warning(`已彻底删除 ${successCount} 个，${failedCount} 个失败`)
      return
    }
    const skippedCount = Math.max(0, selectedCount - ids.length)
    if (skippedCount > 0) {
      ElMessage.success(`已彻底删除 ${successCount} 个资源，${skippedCount} 个受控资源已跳过`)
      return
    }
    ElMessage.success(`已彻底删除 ${successCount} 个资源`)
  } catch (e) {
    ElMessage.error(e.message || '批量彻底删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function resetSortCenter() {
  clearSortDragState()
  sortDraftRows.value = sortInitialRows.value.map((row, idx) => ({
    ...row,
    jumpTo: idx + 1
  }))
  sortKeyword.value = ''
  sortPage.value = 1
  refreshSortPageRows()
}

function hasAssetFilter() {
  return !!(
    String(filters.keyword || '').trim() ||
    Number(filters.recycleFlag || 0) !== 0 ||
    String(filters.folderPath || '').trim() ||
    String(filters.sourceType || '').trim() ||
    String(filters.reviewStatus || '').trim() ||
    String(filters.lockedFlag || '').trim() ||
    String(filters.fileType || '').trim()
  )
}

async function loadSortRowsForCenter() {
  const merged = []
  let current = 1
  const fetchSize = 200
  let expectedTotal = 0
  while (current <= 200) {
    const data = await listAdminAssets({
      page: current,
      pageSize: fetchSize,
      recycleFlag: 0
    })
    const records = (data && data.records) || []
    expectedTotal = Number((data && data.total) || 0)
    merged.push(...records)
    if (!records.length || merged.length >= expectedTotal || records.length < fetchSize) {
      break
    }
    current += 1
  }
  sortRows.value = merged.map((item) => ({
    id: toId(item && item.id),
    fileName: item.fileName || '-',
    folderPath: item.folderPath || '',
    sourceType: item.sourceType || '',
    fileType: item.fileType || '',
    fileUrl: item.fileUrl || '',
    createdAt: item.createdAt || '',
    createdByName: item.createdByName || '',
    sortOrder: item.sortOrder
  }))
  sortInitialRows.value = sortRows.value.map((row) => ({
    ...row
  }))
  sortDraftRows.value = sortRows.value.map((row, idx) => ({
    ...row,
    jumpTo: idx + 1
  }))
  refreshSortPageRows()
}

async function openSortCenter() {
  if (hasAssetFilter()) {
    ElMessage.warning('请先清空筛选条件再进入排序中心')
    return
  }
  try {
    await loadSortRowsForCenter()
  } catch (e) {
    ElMessage.error(e.message || '排序数据加载失败')
    return
  }
  if (!sortRows.value.length) {
    ElMessage.warning('暂无可排序资源')
    return
  }
  clearSortDragState()
  sortMode.value = 'drag'
  sortPage.value = 1
  sortPageSize.value = 20
  sortKeyword.value = ''
  refreshSortPageRows()
  sortCenterVisible.value = true
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
  const sourceIndex = dragId ? snapshot.findIndex((item) => sameId(item && item.id, dragId)) : -1
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
    from = snapshot.findIndex((item) => sameId(item && item.id, dragId))
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
      if (hoverId && (!dragId || !sameId(hoverId, dragId))) {
        const rows = Array.isArray(dragPageRows.value) ? dragPageRows.value : []
        let compactIndex = -1
        let compactOffset = 0
        for (let i = 0; i < rows.length; i += 1) {
          const rowId = toId(rows[i] && rows[i].id)
          if (!rowId || (dragId && sameId(rowId, dragId))) continue
          if (sameId(rowId, hoverId)) {
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
        if (dragId && cardId && sameId(cardId, dragId)) continue
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

function onAssetSortDragMove(evt, originalEvent) {
  detectSortTurnZone(originalEvent || (evt && evt.originalEvent) || evt)
}

function onAssetSortDragStart(event) {
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

function resolveSortIndex(row) {
  const rowId = toId(row && row.id)
  if (!rowId) return '-'
  return sortIndexMap.value.get(rowId) ?? '-'
}

function setDragPageRows(nextRows) {
  const next = Array.isArray(nextRows) ? nextRows : []
  const current = Array.isArray(dragPageRows.value) ? dragPageRows.value : []
  if (
    current.length === next.length &&
    current.every((item, idx) => sameId(item && item.id, next[idx] && next[idx].id))
  ) {
    return
  }
  dragPageRows.value = next.slice()
}

function syncSortPage() {
  normalizeSortDraftRows()
  const maxPage = sortPageTotal.value
  const nextPage = Math.max(1, Math.min(maxPage, Number(sortPage.value || 1)))
  if (nextPage !== sortPage.value) {
    sortPage.value = nextPage
  }
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
  setDragPageRows(sortDraftRows.value.slice(start, start + size))
}

function onAssetSortDragEnd(event) {
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
  const rowId = toId(row && row.id)
  if (!rowId) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => sameId(x && x.id, rowId))
  if (from < 0) return
  const to = from + Number(delta || 0)
  if (to < 0 || to >= list.length) return
  const moved = list.splice(from, 1)[0]
  list.splice(to, 0, moved)
  refreshSortJumpNumbers()
}

function moveSortTop(row) {
  const rowId = toId(row && row.id)
  if (!rowId) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => sameId(x && x.id, rowId))
  if (from < 1) return
  const moved = list.splice(from, 1)[0]
  list.unshift(moved)
  refreshSortJumpNumbers()
}

function moveSortBottom(row) {
  const rowId = toId(row && row.id)
  if (!rowId) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => sameId(x && x.id, rowId))
  if (from < 0 || from === list.length - 1) return
  const moved = list.splice(from, 1)[0]
  list.push(moved)
  refreshSortJumpNumbers()
}

function applySortJump(row) {
  const rowId = toId(row && row.id)
  if (!rowId) return
  const list = sortDraftRows.value
  const from = list.findIndex((x) => sameId(x && x.id, rowId))
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

async function saveSort() {
  const sourceRows = sortDraftRows.value.length ? sortDraftRows.value : sortRows.value
  if (!sourceRows.length) return
  if (hasAssetFilter()) {
    ElMessage.warning('请先清空筛选条件后再保存排序')
    return
  }
  savingSort.value = true
  try {
    sortRows.value = sourceRows.map((row) => ({ ...row }))
    await reorderAssets(sourceRows.map((x) => x.id).filter(Boolean))
    ElMessage.success('资源排序已保存')
    sortCenterVisible.value = false
    await Promise.all([loadRows(), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '排序保存失败')
  } finally {
    savingSort.value = false
  }
}

function formatModule(value) {
  const text = String(value || '').trim()
  if (!text) return '未归类'
  const map = {
    field: '田块',
    crop_category: '作物分类',
    crop_variety: '作物品种',
    farm: '农事',
    seed: '种子',
    export: '导出',
    system: '系统',
    amap: '高德',
    auth: '认证'
  }
  return map[text] ? `${map[text]}（${text}）` : text
}

function formatSourceType(value) {
  const text = normalizeSourceType(value)
  if (!text) return '-'
  const map = {
    admin_upload: '后台上传',
    miniapp_upload: '小程序用户上传',
    system_upload: '系统上传'
  }
  return map[text] || text
}

function sourceTypeTagType(value) {
  const key = normalizeSourceType(value)
  if (key === 'admin_upload') return 'primary'
  if (key === 'miniapp_upload') return 'success'
  if (key === 'system_upload') return 'warning'
  return 'info'
}

function fileTypeTagType(value) {
  return String(value || '').trim().toLowerCase() === 'image' ? 'success' : 'info'
}

function formatReviewStatus(value) {
  const status = normalizeReviewStatus(value)
  if (status === 'pending') return '待审核'
  if (status === 'rejected') return '已驳回'
  return '已通过'
}

function formatDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  const normalized = text.includes('T') ? text : text.replace(/-/g, '/')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return text
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`
}

function shortText(value, max = 42) {
  const text = String(value || '').trim()
  if (!text) return '-'
  if (text.length <= max) return text
  return `${text.slice(0, max)}...`
}

async function copyUrl(url) {
  const text = String(url || '').trim()
  if (!text) {
    ElMessage.warning('链接为空，无法复制')
    return
  }
  try {
    if (navigator && navigator.clipboard && typeof navigator.clipboard.writeText === 'function' && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const input = document.createElement('textarea')
      input.value = text
      input.style.position = 'fixed'
      input.style.opacity = '0'
      input.style.pointerEvents = 'none'
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    ElMessage.success('链接已复制')
  } catch (error) {
    ElMessage.error('复制失败，请手动复制')
  }
}

function reviewStatusTagType(value) {
  const status = normalizeReviewStatus(value)
  if (status === 'pending') return 'warning'
  if (status === 'rejected') return 'danger'
  return 'success'
}

function formatSize(value) {
  const num = Number(value || 0)
  if (!(num > 0)) return '0 B'
  if (num < 1024) return `${num.toFixed(0)} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  if (num < 1024 * 1024 * 1024) return `${(num / (1024 * 1024)).toFixed(1)} MB`
  return `${(num / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

async function loadStats() {
  statsLoading.value = true
  try {
    const data = await getAssetStats()
    const moduleStats = Array.isArray(data && data.moduleStats) ? data.moduleStats : []
    stats.value = {
      totalCount: Number((data && data.totalCount) || 0),
      imageCount: Number((data && data.imageCount) || 0),
      fileCount: Number((data && data.fileCount) || 0),
      totalSizeBytes: Number((data && data.totalSizeBytes) || 0),
      moduleStats
    }
    const extraModules = moduleStats
      .map((x) => String((x && x.moduleKey) || '').trim())
      .filter(Boolean)
      .map((x) => ({ moduleKey: x }))
    mergeModuleOptions(extraModules)
  } catch (e) {
    stats.value = {
      totalCount: 0,
      imageCount: 0,
      fileCount: 0,
      totalSizeBytes: 0,
      moduleStats: []
    }
    ElMessage.error(e.message || '统计加载失败')
  } finally {
    statsLoading.value = false
  }
}

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

watch(
  () => filters.recycleFlag,
  async (next) => {
    await loadFolderOptions(Number(next || 0))
    const current = String(filters.folderPath || '').trim()
    if (!current) return
    const normalized = normalizeFolderPath(current)
    if (!(folderOptions.value || []).includes(normalized)) {
      filters.folderPath = ''
    }
  }
)

onMounted(async () => {
  await Promise.all([loadRows(1), loadStats(), loadFolderOptions(0)])
})
</script>

<style scoped>
.assets-manage-page {
  display: block;
}

.stats-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  margin-bottom: 12px;
  background: var(--bg-plain);
}

.stats-item {
  min-height: 76px;
  padding: 10px 12px;
  border-right: 1px dashed var(--border);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.stats-item:last-child {
  border-right: 0;
}

.stat-title {
  color: var(--text-sub);
  font-size: 12px;
}

.stat-value {
  margin-top: 6px;
  color: var(--text-main);
  font-size: 24px;
  font-weight: 700;
}

.module-stats-card {
  margin-bottom: 12px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-meta {
  color: var(--text-sub);
  font-size: 12px;
}

.folder-manage-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.assets-layout :deep(.el-card) {
  height: 100%;
}

.table-actions {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  column-gap: 8px;
  row-gap: 8px;
  min-width: 0;
}

.table-actions-toggle {
  justify-self: start;
}

.table-actions-batch {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 32px;
  min-width: 0;
}

.folder-cell {
  display: grid;
  gap: 4px;
}

.folder-remark {
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.2;
}

.asset-name-cell {
  display: grid;
  gap: 2px;
}

.asset-name {
  color: var(--text-main);
  font-weight: 600;
}

.asset-remark {
  color: var(--text-sub);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.time-cell {
  display: grid;
  gap: 2px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.3;
}

.url-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.url-link {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.lock-cell {
  display: grid;
  gap: 4px;
}

.lock-remark {
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.25;
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
  justify-content: flex-end;
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

:deep(.asset-reference-confirm) {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 520px;
}

:deep(.asset-reference-confirm-title) {
  color: var(--text-main);
  line-height: 1.6;
}

:deep(.asset-reference-confirm-list) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:deep(.asset-reference-confirm-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

:deep(.asset-reference-confirm-item) {
  padding: 0 0 0 12px;
  border-left: 3px solid var(--success);
}

:deep(.asset-reference-confirm-module) {
  color: var(--text-main);
  font-weight: 600;
}

:deep(.asset-reference-confirm-summary) {
  color: var(--text-sub);
  line-height: 1.5;
}

:deep(.asset-reference-confirm-more) {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

@media (max-width: 1080px) {
  .stats-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-strip {
    grid-template-columns: 1fr;
  }

  .stats-item {
    border-right: 0;
    border-bottom: 1px dashed var(--border);
  }

  .stats-item:last-child {
    border-bottom: 0;
  }

  .table-actions {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .table-actions-batch {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>

