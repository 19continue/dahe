<template>
  <div class="layout">
    <div v-if="isMobileViewport && mobileMenuVisible" class="sider-mask" @click="closeMobileMenu"></div>
    <aside
      :class="[
        'sider',
        {
          collapsed: !isMobileViewport && menuCollapsed,
          mobile: isMobileViewport,
          'mobile-open': isMobileViewport && mobileMenuVisible
        }
      ]"
    >
      <div class="brand" @click="goHome">
        <span class="brand-dot"></span>
        <div class="brand-text">
          <div class="brand-main">DaHe Admin V2</div>
          <div class="brand-sub">农业生产管理后台</div>
        </div>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="activeMenuPath"
          :collapse="isMobileViewport ? false : menuCollapsed"
          :collapse-transition="false"
          router
          class="menu-root"
          @select="onMenuSelect"
        >
          <template v-for="item in visibleMenus" :key="item.index">
            <el-menu-item v-if="item.type === 'item'" :index="item.index">
              <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
              <span class="menu-label">{{ item.label }}</span>
            </el-menu-item>

            <el-sub-menu v-else :index="item.index">
              <template #title>
                <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
                <span class="menu-label">{{ item.label }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
                <el-icon class="menu-icon"><component :is="child.icon" /></el-icon>
                <span class="menu-label">{{ child.label }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <div class="main-wrap">
      <header class="topbar">
        <div class="top-left">
          <el-button class="top-icon-btn" text @click="toggleMenu">
            <el-icon><component :is="isMobileViewport ? (mobileMenuVisible ? Fold : Expand) : (menuCollapsed ? Expand : Fold)" /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentGroupLabel">{{ currentGroupLabel }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="top-right">
          <el-tooltip content="刷新当前页" placement="bottom">
            <el-button class="top-icon-btn" text @click="refreshCurrentPage">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </el-tooltip>

          <el-tooltip content="全屏" placement="bottom">
            <el-button class="top-icon-btn" text @click="toggleFullScreen">
              <el-icon><FullScreen /></el-icon>
            </el-button>
          </el-tooltip>

          <el-tooltip :content="layoutSettings.darkMode ? '切换浅色模式' : '切换暗黑模式'" placement="bottom">
            <el-button class="top-icon-btn" text @click="toggleDarkMode">
              <el-icon><component :is="layoutSettings.darkMode ? Sunny : Moon" /></el-icon>
            </el-button>
          </el-tooltip>

          <el-popover
            v-model:visible="noticeCenterVisible"
            placement="bottom-end"
            :width="320"
            trigger="click"
            popper-class="notice-center-popper"
            :popper-options="noticePopoverOptions"
            @show="onNoticePopoverShow"
          >
            <template #reference>
              <el-button class="top-icon-btn notify-btn" text>
                <el-icon><Bell /></el-icon>
                <span v-if="unreadCount" class="notify-dot">{{ unreadCount }}</span>
              </el-button>
            </template>
            <div class="notify-panel">
              <div class="notify-header">
                <div class="notify-title">通知中心</div>
                <el-button link size="small" :disabled="!unreadCount" @click.stop="markAllNoticesRead">全部已读</el-button>
              </div>
              <div class="notify-list">
                <div v-if="noticeLoading" class="notify-empty">加载中...</div>
                <div v-else-if="!noticeList.length" class="notify-empty">暂无通知</div>
                <div
                  v-for="item in noticeList"
                  :key="item.id || `${item.title}-${item.time}`"
                  class="notify-item"
                  :class="{ unread: !item.isRead }"
                  @click="onNoticeClick(item)"
                >
                  <div class="notify-item-title">{{ item.title }}</div>
                  <div v-if="item.content" class="notify-item-content">{{ item.content }}</div>
                  <div class="notify-item-time">{{ item.time }}</div>
                </div>
              </div>
              <div class="notify-footer notify-footer-link">
                <el-button link size="small" @click.stop="router.push('/message-station')">更多</el-button>
              </div>
            </div>
          </el-popover>

          <el-tooltip content="布局设置" placement="bottom">
            <el-button class="top-icon-btn" text @click="settingsVisible = true">
              <el-icon><Setting /></el-icon>
            </el-button>
          </el-tooltip>

          <el-dropdown trigger="click">
            <div class="user-entry">
              <img v-if="userAvatarUrl" class="user-avatar user-avatar-img" :src="userAvatarUrl" alt="avatar" />
              <span v-else class="user-avatar">{{ userInitial }}</span>
              <span class="user-name">{{ userName }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item @click="goHome">回到首页</el-dropdown-item>
                <el-dropdown-item @click="goProfile">个人信息</el-dropdown-item>
                <el-dropdown-item @click="goMessageStation">消息通知站</el-dropdown-item>
                <el-dropdown-item @click="goPassword">修改密码</el-dropdown-item>
                <el-dropdown-item @click="goSystemSettings">系统设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section
        v-if="layoutSettings.showTabs"
        ref="routeTabsWrapRef"
        class="route-tabs-wrap"
        @contextmenu.prevent="onTabsAreaContextMenu"
        @dragover="onTabsAreaDragOver"
        @drop="onTabsAreaDrop"
      >
        <button
          class="tab-edge-btn is-prev"
          :class="{ 'is-disabled': !tabScroll.scrollable || !tabScroll.canPrev }"
          type="button"
          @click.stop="onTabEdgeScroll('prev')"
          @mousedown.left.prevent="onTabEdgeHoldStart('prev')"
          @touchstart.prevent="onTabEdgeHoldStart('prev')"
          @mouseup="onTabEdgeHoldEnd"
          @mouseleave="onTabEdgeHoldEnd"
          @touchend.prevent="onTabEdgeHoldEnd"
          @touchcancel="onTabEdgeHoldEnd"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <el-tabs
          v-model="activeTabPath"
          type="card"
          class="route-tabs"
          @tab-click="onRouteTabClick"
          @tab-remove="removeRouteTab"
          @mousedown="onRouteTabsMouseDown"
        >
          <el-tab-pane
            v-for="tab in routeTabs"
            :key="tab.path"
            :name="tab.path"
            :closable="tab.closable"
          >
            <template #label>
              <span
                class="route-tab-label"
                :class="{
                  'is-draggable': tab.closable,
                  'is-dragging': tabDragState.sourcePath === tab.path,
                  'is-drag-over-before':
                    tabDragState.overPath === tab.path &&
                    tabDragState.sourcePath !== tab.path &&
                    tabDragState.overPlacement === 'before',
                  'is-drag-over-after':
                    tabDragState.overPath === tab.path &&
                    tabDragState.sourcePath !== tab.path &&
                    tabDragState.overPlacement === 'after'
                }"
                :data-tab-path="tab.path"
                :draggable="tab.closable"
                @mousedown="onTabDragLabelMouseDown"
                @mouseup="onTabDragLabelMouseUp"
                @dragstart="onTabDragStart($event, tab)"
                @dragend="onTabDragEnd"
              >
                <span class="route-tab-title" :title="tab.title">{{ tab.title }}</span>
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
        <button
          class="tab-edge-btn is-next"
          :class="{ 'is-disabled': !tabScroll.scrollable || !tabScroll.canNext }"
          type="button"
          @click.stop="onTabEdgeScroll('next')"
          @mousedown.left.prevent="onTabEdgeHoldStart('next')"
          @touchstart.prevent="onTabEdgeHoldStart('next')"
          @mouseup="onTabEdgeHoldEnd"
          @mouseleave="onTabEdgeHoldEnd"
          @touchend.prevent="onTabEdgeHoldEnd"
          @touchcancel="onTabEdgeHoldEnd"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>
        <el-dropdown trigger="click" @command="handleTabCommand">
          <el-button size="small" class="tab-manage-btn">页签管理</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="openSortCenter">页签排序</el-dropdown-item>
              <el-dropdown-item divided command="closeLeft">关闭左侧</el-dropdown-item>
              <el-dropdown-item command="closeCurrent">关闭当前</el-dropdown-item>
              <el-dropdown-item command="closeOthers">关闭其他</el-dropdown-item>
              <el-dropdown-item command="closeRight">关闭右侧</el-dropdown-item>
              <el-dropdown-item command="closeAll">关闭全部</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </section>

      <div
        v-show="tabContext.visible"
        class="tab-context-menu"
        :style="{ left: `${tabContext.x}px`, top: `${tabContext.y}px` }"
        @click.stop
        @contextmenu.prevent
      >
        <button class="tab-context-item" type="button" @click="applyTabContextCommand('activate')">切换到该页</button>
        <button
          class="tab-context-item"
          type="button"
          :disabled="isFixedTab(tabContext.path)"
          @click="applyTabContextCommand('closeCurrent')"
        >
          关闭当前
        </button>
        <button class="tab-context-item" type="button" @click="applyTabContextCommand('closeLeft')">关闭左侧</button>
        <button class="tab-context-item" type="button" @click="applyTabContextCommand('closeOthers')">关闭其他</button>
        <button class="tab-context-item" type="button" @click="applyTabContextCommand('closeRight')">关闭右侧</button>
        <button class="tab-context-item danger" type="button" @click="applyTabContextCommand('closeAll')">关闭全部</button>
      </div>

      <el-dialog
        v-model="tabSortCenterVisible"
        title="页签排序"
        width="960px"
        destroy-on-close
        align-center
        class="tab-sort-center-dialog"
      >
        <SortCenterShell
          ref="tabSortShellRef"
          :mode="tabSortMode"
          :keyword="tabSortKeyword"
          keyword-placeholder="按钮模式下可按页签标题或路由地址搜索"
          :total="tabSortMode === 'drag' ? tabSortDraftRows.length : tabSortFilteredRows.length"
          :page="tabSortPage"
          :page-total="tabSortPageTotal"
          @update:mode="(val) => (tabSortMode = val)"
          @update:keyword="(val) => (tabSortKeyword = val)"
          @reset="resetTabSortCenter"
          @save="applyTabSortCenter"
        >
          <SortDragBoard
            v-show="tabSortMode === 'drag'"
            :model-value="tabSortDragPageRows"
            item-key="path"
            sort-id-key="path"
            :dragging="tabSortDragging"
            :suspend-update="tabSortSuspendPageUpdate"
            :turn-direction="tabSortTurnDirection"
            :page="tabSortPage"
            :page-total="tabSortPageTotal"
            :resolve-index="resolveTabSortIndex"
            @turn-page="turnTabSortPage"
            @drag-start="onTabSortDragStart"
            @drag-end="onTabSortDragEnd"
            @drag-move="onTabSortDragMove"
          >
            <template #item="{ row }">
              <div class="tab-sort-item-content" :class="{ 'is-current': isCurrentTabSortRow(row) }">
                <span class="drag-handle">☰⋯</span>
                <span class="drag-order">{{ resolveTabSortIndex(row) }}</span>
                <div class="drag-main">
                  <div class="drag-title-row">
                    <span class="drag-name">{{ row.title || row.routeName || '未命名页签' }}</span>
                    <el-tag
                      v-if="isCurrentTabSortRow(row)"
                      size="small"
                      effect="plain"
                      type="success"
                      class="tab-current-tag"
                    >
                      当前页
                    </el-tag>
                  </div>
                  <div class="drag-sub">
                    <span>{{ row.path }}</span>
                  </div>
                </div>
              </div>
            </template>
          </SortDragBoard>

          <div v-show="tabSortMode !== 'drag'">
            <el-table :data="tabSortCenterRows" border :row-class-name="tabSortTableRowClassName">
              <el-table-column label="序号" width="72">
                <template #default="scope">{{ resolveTabSortIndex(scope.row) }}</template>
              </el-table-column>
              <el-table-column prop="title" label="页签标题" min-width="220">
                <template #default="scope">
                  <div class="tab-sort-title-cell">
                    <span>{{ scope.row.title || scope.row.routeName || '-' }}</span>
                    <el-tag
                      v-if="isCurrentTabSortRow(scope.row)"
                      size="small"
                      effect="plain"
                      type="success"
                      class="tab-current-tag"
                    >
                      当前页
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="path" label="路由地址" min-width="280" />
              <el-table-column label="移动到序号" width="210">
                <template #default="scope">
                  <div class="jump-box">
                    <el-input-number
                      v-model="scope.row.jumpTo"
                      :min="1"
                      :max="Math.max(1, tabSortDraftRows.length)"
                      size="small"
                    />
                    <el-button size="small" @click="applyTabSortJump(scope.row)">跳转</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="scope">
                  <div class="sort-op">
                    <el-button-group>
                      <el-button size="small" @click="moveTabSortRow(scope.row, -1)">上移</el-button>
                      <el-button size="small" @click="moveTabSortRow(scope.row, 1)">下移</el-button>
                    </el-button-group>
                    <el-dropdown trigger="click" @command="(command) => onTabSortOpCommand(command, scope.row)">
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
              :total="tabSortPageTotalRecords"
              :page-size="tabSortPageSize"
              :current-page="tabSortPage"
              :page-sizes="[10, 20, 50, 100]"
              @size-change="onTabSortPageSizeChange"
              @current-change="onTabSortPageChange"
            />
          </template>
        </SortCenterShell>
      </el-dialog>

      <main class="content">
        <router-view v-slot="{ Component, route: currentRoute }">
          <transition :name="layoutSettings.routeAnim ? 'route-fade' : 'route-none'" mode="out-in">
            <keep-alive :include="keepAliveNames">
              <component :is="Component" :key="currentRoute.path" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>

    <el-drawer
      v-model="settingsVisible"
      title="布局设置"
      direction="rtl"
      size="320px"
      append-to-body
    >
      <el-form label-width="88px" class="layout-form">
        <el-form-item label="显示标签栏">
          <el-switch v-model="layoutSettings.showTabs" />
        </el-form-item>
        <el-form-item label="页面动效">
          <el-switch v-model="layoutSettings.routeAnim" />
        </el-form-item>
        <el-form-item label="暗黑模式">
          <el-switch v-model="layoutSettings.darkMode" />
        </el-form-item>
        <el-form-item label="主题主色">
          <el-color-picker v-model="layoutSettings.primaryColor" />
        </el-form-item>
      </el-form>

      <div class="settings-actions">
        <el-button @click="resetSettings">恢复默认</el-button>
        <el-button type="primary" @click="settingsVisible = false">完成</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Expand,
  Fold,
  ArrowLeft,
  ArrowRight,
  RefreshRight,
  FullScreen,
  Bell,
  Setting,
  Moon,
  Sunny,
  DataBoard,
  User,
  Place,
  Grid,
  Calendar,
  Memo,
  Tickets,
  Document,
  Guide,
  Tools,
  SetUp,
  Picture,
  Files
} from '@element-plus/icons-vue'
import SortCenterShell from '../components/ui/SortCenterShell.vue'
import SortDragBoard from '../components/ui/SortDragBoard.vue'
import { clearSession, getUser } from '../utils/auth'
import request from '../utils/request'
import { setCustomPhraseEntries } from '../utils/schemaKeyGenerator'
import { ASSET_CENTER_ROUTE, ASSET_REVIEW_ROUTE, normalizeAdminRoutePath, resolveAdminPermissionPath } from '../utils/adminRouteMap'

const router = useRouter()
const route = useRoute()

const DEFAULT_TAB = '/dashboard'
const FIXED_TABS = new Set([DEFAULT_TAB])
const MENU_COLLAPSED_KEY = 'dahe.admin.v2.menuCollapsed'
const LAYOUT_SETTINGS_KEY = 'dahe.admin.v2.layoutSettings'
const ROUTE_TABS_KEY = 'dahe.admin.v2.routeTabs'
const ACTIVE_TAB_PATH_KEY = 'dahe.admin.v2.activeTabPath'
const TERMINOLOGY_SYNC_AT_KEY = 'dahe.admin.v2.terminology.syncAt'
const TERMINOLOGY_SYNC_VERSION_KEY = 'dahe.admin.v2.terminology.syncVersion'
const TERMINOLOGY_SYNC_INTERVAL_MS = 5 * 60 * 1000
const TERMINOLOGY_SYNC_LIMIT = 5000
const TAB_REORDER_ANIMATION_MS = 220
const TAB_DRAG_AUTO_SCROLL_INTERVAL = 220
const TAB_DRAG_AUTO_SCROLL_STALL_LIMIT = 4
const TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY = 0.06
const TAB_DRAG_OUTSIDE_SCROLL_DISTANCE = 180
const TAB_DRAG_OUTSIDE_VERTICAL_TOLERANCE = 16
const TAB_EDGE_SCROLL_MIN_STEP = 36
const TAB_EDGE_SCROLL_MAX_STEP = 84
const TAB_EDGE_HOLD_START_DELAY = 260
const TAB_EDGE_HOLD_INTERVAL = 90
const TAB_DRAG_SCROLL_MIN_STEP = 10
const TAB_DRAG_SCROLL_MAX_STEP = 24
const TAB_DRAG_PREVIEW_REORDER_INTERVAL = 70
const TAB_DRAG_PREVIEW_HOVER_DELAY = 120
const TAB_DRAG_PREVIEW_REARM_DISTANCE = 18
const TAB_REORDER_ANIMATION_NODE_LIMIT = 200
const TABS_NAV_CACHE_TTL_MS = 48
const ROUTE_TABS_PERSIST_DELAY_MS = 160
const TAB_SORT_PAGE_SIZE_DEFAULT = 20
const TAB_SORT_TURN_DELAY = 280
const MOBILE_BREAKPOINT = 820

const routeTabs = ref(loadRouteTabsState())
const activeTabPath = ref(loadActiveTabPath())
const routeTabsWrapRef = ref(null)
const tabsWheelTarget = ref(null)
const tabScroll = reactive({
  scrollable: false,
  canPrev: false,
  canNext: false
})
const tabContext = reactive({
  visible: false,
  x: 0,
  y: 0,
  path: DEFAULT_TAB
})
const tabDragState = reactive({
  sourcePath: '',
  overPath: '',
  overPlacement: 'before'
})
const tabClickSuppressUntil = ref(0)
const tabActivateTimer = ref(0)
const tabEdgeHoldDirection = ref('')
const tabDragAutoDirection = ref('')
const tabDragAutoTimer = ref(0)
const tabDragAutoStallTicks = ref(0)
const tabDragAutoIntensity = ref(0.5)
const tabDragPreviewKey = ref('')
const tabDragPreviewAt = ref(0)
const tabDragPreviewMoved = ref(false)
const tabDragHoverPath = ref('')
const tabDragHoverAt = ref(0)
const tabDragDropHandled = ref(false)
const tabDragPreviewNeedPointerMove = ref(false)
const tabDragLastPreviewPointer = reactive({ x: 0, y: 0 })
const tabSortCenterVisible = ref(false)
const tabSortMode = ref('drag')
const tabSortKeyword = ref('')
const tabSortDraftRows = ref([])
const tabSortInitialRows = ref([])
const tabSortDragPageRows = ref([])
const tabSortShellRef = ref(null)
const tabSortPage = ref(1)
const tabSortPageSize = ref(TAB_SORT_PAGE_SIZE_DEFAULT)
const tabSortDragging = ref(false)
const tabSortDraggingPath = ref('')
const tabSortSuspendPageUpdate = ref(false)
const tabSortDragStartSnapshot = ref([])
const tabSortDragStartPage = ref(1)
const tabSortDragSourceIndex = ref(-1)
const tabSortDragSourceRow = ref(null)
const tabSortVisitedCrossPageDuringDrag = ref(false)
const tabSortTurnDirection = ref('')
let tabSortTurnTimer = null
let tabSortTurnRaf = 0
let tabSortTurnPendingPoint = null
let tabSortTurnConsumedDirection = ''
let tabSortAutoScrollLastAt = 0
let tabSortAutoScrollCarry = 0
let tabSortAutoScrollDirection = 0
let tabSortAutoScrollVelocity = 0
let tabSortCrossPreviewLastPage = 0
let tabSortCrossPreviewLastInsertIndex = -1
let tabSortCrossPreviewLastPointKey = ''
let tabSortCrossPreviewLastPointerY = NaN
let tabSortCrossPreviewMoveDown = true
let tabActiveScrollTaskId = 0
let tabEdgeHoldStartTimer = 0
let tabEdgeHoldTickTimer = 0
let tabEdgeHoldGlobalBound = false
let tabDragSourceVisualNode = null
let tabsNavItemsCache = []
let tabsNavItemsCacheAt = 0
let tabsNavCacheVersion = 0
let tabsNavCacheLastVersion = -1
let tabsNodePathCache = new WeakMap()
let tabsPathNodeCache = new Map()
let tabsScrollSyncRafId = 0
let tabsScrollSyncTimer = 0
let routeTabsPersistTimer = 0
let routeTabsDirtyDuringDrag = false
let resizeRafId = 0
let tabsNavElement = null
const menuCollapsed = ref(localStorage.getItem(MENU_COLLAPSED_KEY) === '1')
const isMobileViewport = ref(false)
const mobileMenuVisible = ref(false)
const settingsVisible = ref(false)
const user = ref(getUser() || {})
const isSuperAdminUser = computed(() => Number((user.value && user.value.isSuperAdmin) || 0) === 1)
const menuPermissionSet = computed(() => {
  const rows = Array.isArray(user.value && user.value.menuPermissions) ? user.value.menuPermissions : []
  return new Set(
    rows
      .map((item) => String(item || '').trim())
      .filter(Boolean)
  )
})

const noticeCenterVisible = ref(false)
const noticeList = ref([])
const noticeLoading = ref(false)
const noticePageSize = 3
const noticeLastLoadedAt = ref(0)
const unreadCount = ref(0)
const NOTICE_UPDATED_EVENT = 'dahe-admin-notice-updated'
const terminologySyncing = ref(false)
const noticePopoverOptions = {
  strategy: 'fixed',
  modifiers: [
    {
      name: 'computeStyles',
      options: {
        adaptive: false
      }
    }
  ]
}
const routeTabPathIndexMap = computed(() => {
  const map = new Map()
  routeTabs.value.forEach((item, index) => {
    const path = String(item && item.path ? item.path : '').trim()
    if (!path || map.has(path)) return
    map.set(path, index)
  })
  return map
})

const defaultLayoutSettings = {
  showTabs: true,
  routeAnim: true,
  darkMode: false,
  primaryColor: '#1667b7'
}

const layoutSettings = reactive(loadLayoutSettings())

const roleLabel = computed(() => {
  const explicit = String((user.value && user.value.roleName) || '').trim()
  if (explicit) return explicit
  const raw = String((user.value && (user.value.effectiveRoleCode || user.value.roleCode)) || '').trim()
  if (raw) return raw
  return '后台用户'
})

const userName = computed(() => {
  const x = user.value || {}
  return String(x.realName || x.nickName || '管理员')
})

const userInitial = computed(() => {
  const text = userName.value.trim()
  return text ? text.slice(0, 1).toUpperCase() : 'A'
})

const userAvatarUrl = computed(() => {
  const x = user.value || {}
  const avatar = String(x.avatarUrl || x.wxAvatarUrl || '').trim()
  return avatar || ''
})

function noticeTimeText(value) {
  const text = String(value || '').trim()
  if (!text) return '刚刚'
  const normalized = text.includes('T') ? text : text.replace(/-/g, '/')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return text
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

function normalizeNoticeId(value) {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  if (!text || text === '0') return ''
  if (/[eE]/.test(text)) return ''
  return text
}

function normalizeNotice(item) {
  const row = item || {}
  const id = normalizeNoticeId(row.id || row.noticeId)
  return {
    id,
    title: String(row.title || '系统通知').trim() || '系统通知',
    content: String(row.content || '').trim(),
    noticeType: String(row.noticeType || row.notice_type || '').trim().toLowerCase(),
    routeCode: String(row.routeCode || row.route_code || '').trim(),
    isRead: Number(row.isRead) === 1 || Number(row.is_read) === 1,
    time: noticeTimeText(row.createdAt || row.created_at)
  }
}

async function loadNoticeCenter(options = {}) {
  const reset = options && options.reset === false ? false : true
  const silentReset = Boolean(options && options.silentReset)
  if (!reset && noticeLoading.value) {
    return
  }
  noticeLoading.value = true
  try {
    const data = await request.get('/admin/auth/me/notices', {
      params: {
        page: 1,
        pageSize: noticePageSize,
        unreadOnly: true
      }
    })
    const records = Array.isArray(data && data.records) ? data.records : []
    const mapped = records.map((item) => normalizeNotice(item))
    noticeList.value = mapped
    unreadCount.value = Number((data && data.unreadCount) || 0)
    noticeLastLoadedAt.value = Date.now()
  } catch (error) {
    if (reset && !silentReset) {
      noticeList.value = []
      unreadCount.value = 0
    }
  } finally {
    noticeLoading.value = false
  }
}

async function markNoticeRead(item) {
  const row = item || {}
  const id = normalizeNoticeId(row.id)
  if (!id || row.isRead) {
    return
  }
  try {
    await request.put(`/admin/auth/me/notices/${encodeURIComponent(id)}/read`, {})
    row.isRead = true
    unreadCount.value = Math.max(0, Number(unreadCount.value || 0) - 1)
  } catch (error) {
    // ignore read failure in notification list
  }
}

async function onNoticeClick(item) {
  const row = item || {}
  await markNoticeRead(row)
  const noticeType = String(row.noticeType || '').toLowerCase()
  const routeCode = String(row.routeCode || '').trim()
  if (noticeType === 'review_apply') {
    router.push({
      path: normalizeAdminRoutePath(routeCode || '/users'),
      query: {
        status: 'pending',
        enabled: '1',
        fromNotice: 'review_apply',
        noticeAt: String(Date.now())
      }
    })
    return
  }
  if (routeCode) {
    router.push(normalizeAdminRoutePath(routeCode))
    return
  }
  router.push('/message-station')
}

async function markAllNoticesRead() {
  if (!Number(unreadCount.value || 0)) {
    return
  }
  try {
    await request.put('/admin/auth/me/notices/read-all', {})
    await loadNoticeCenter({ reset: true })
  } catch (error) {
    // ignore read-all failure in topbar
  }
}

function onNoticePopoverShow() {
  const now = Date.now()
  const shouldRefresh = !noticeList.value.length || now - Number(noticeLastLoadedAt.value || 0) > 15000
  if (!shouldRefresh) return
  loadNoticeCenter({ reset: true, silentReset: true })
}

function onAdminNoticeUpdated() {
  loadNoticeCenter({ reset: true, silentReset: true })
}

function normalizeTerminologyPairs(rows) {
  const out = []
  const used = new Set()
  ;(Array.isArray(rows) ? rows : []).forEach((item) => {
    const source = String(item && item.source ? item.source : '').trim()
    const target = String(item && item.target ? item.target : '').trim()
    if (!source || !target || used.has(source)) return
    used.add(source)
    out.push([source, target])
  })
  return out
}

async function syncTerminologyCache(force = false) {
  if (terminologySyncing.value) return
  const now = Date.now()
  const lastSyncAt = Number(localStorage.getItem(TERMINOLOGY_SYNC_AT_KEY) || 0)
  if (!force && now - lastSyncAt < TERMINOLOGY_SYNC_INTERVAL_MS) return
  terminologySyncing.value = true
  try {
    const cachedVersion = String(localStorage.getItem(TERMINOLOGY_SYNC_VERSION_KEY) || '').trim()
    const syncMeta = await request.get('/admin/terminology-dict/sync-meta', {
      params: cachedVersion ? { version: cachedVersion } : {}
    })
    const currentVersion = String((syncMeta && syncMeta.version) || '').trim()
    const changed = force || !!(syncMeta && syncMeta.changed)
    if (!changed) {
      localStorage.setItem(TERMINOLOGY_SYNC_AT_KEY, String(Date.now()))
      if (currentVersion) {
        localStorage.setItem(TERMINOLOGY_SYNC_VERSION_KEY, currentVersion)
      }
      return
    }
    const rows = await request.get('/admin/terminology-dict/all', {
      params: {
        limit: TERMINOLOGY_SYNC_LIMIT
      }
    })
    setCustomPhraseEntries(normalizeTerminologyPairs(rows))
    localStorage.setItem(TERMINOLOGY_SYNC_AT_KEY, String(Date.now()))
    if (currentVersion) {
      localStorage.setItem(TERMINOLOGY_SYNC_VERSION_KEY, currentVersion)
    }
  } catch (error) {
    // keep local cache when remote sync fails
  } finally {
    terminologySyncing.value = false
  }
}

const menuSchema = [
  { type: 'item', index: '/dashboard', label: '控制台概览', icon: DataBoard },
  {
    type: 'group',
    index: 'biz-user',
    label: '用户与权限',
    icon: User,
    children: [
      { index: '/users', label: '小程序用户审核', icon: User },
      { index: '/users/manage', label: '小程序用户管理', icon: User },
      { index: '/admin-users', label: '后台用户管理', icon: User },
      { index: '/roles', label: '角色与权限配置', icon: SetUp }
    ]
  },
  {
    type: 'group',
    index: 'biz-production',
    label: '生产管理',
    icon: Place,
    children: [
      { index: '/field-manage', label: '田块管理', icon: Grid },
      { index: '/field-cycles', label: '田块种植计划', icon: Calendar },
      { index: '/farm-records-manage', label: '农事记录管理', icon: Memo },
      { index: '/crop-manage', label: '作物品种管理', icon: Tickets }
    ]
  },
  {
    type: 'group',
    index: 'biz-seed',
    label: '质量与批次',
    icon: Files,
    children: [
      { index: '/seed-manage', label: '种子批次管理', icon: Files },
      { index: '/seed-rules', label: '种子检测规则', icon: SetUp }
    ]
  },
  {
    type: 'group',
    index: 'biz-params',
    label: '参数与模板',
    icon: SetUp,
    children: [
      { index: '/process-templates', label: '流程模板管理', icon: Memo },
      { index: '/farm-step-dynamic-configs', label: '农事步骤参数模板', icon: SetUp },
      { index: '/seed-dynamic-configs/batch', label: '种子批次参数', icon: SetUp },
      { index: '/seed-dynamic-configs/test', label: '种子检测参数', icon: SetUp },
      { index: '/export-templates', label: '导出模板标准化', icon: Grid },
      { index: '/terminology-dict', label: '术语词典管理', icon: Tickets }
    ]
  },
  {
    type: 'group',
    index: 'biz-resource',
    label: '资源与导出',
    icon: Picture,
    children: [
      { index: ASSET_CENTER_ROUTE, label: '图片与资源管理', icon: Picture },
      { index: ASSET_REVIEW_ROUTE, label: '资源审核', icon: Picture },
      { index: '/miniapp-static-assets', label: '小程序静态资源', icon: Picture, requiresSuperAdmin: true },
      { index: '/exports', label: '记录导出', icon: Document }
    ]
  },
  {
    type: 'group',
    index: 'biz-system',
    label: '系统与配置',
    icon: Tools,
    children: [
      { index: '/record-policy', label: '记录权限策略', icon: SetUp },
      { index: '/asset-policy', label: '资源上传策略', icon: SetUp },
      { index: '/amap-audit', label: '高德额度审计', icon: Tools },
      { index: '/messages', label: '消息发布', icon: Bell },
      { index: '/operation-logs', label: '系统操作日志', icon: Tools },
      { index: '/company-intro', label: '企业介绍管理', icon: Setting },
      { index: '/system-settings', label: '系统设置中心', icon: Setting },
      { index: '/admin-guide', label: '操作指引', icon: Guide }
    ]
  }
]

const visibleMenus = computed(() => {
  const menuPermissions = menuPermissionSet.value
  return menuSchema
    .map((item) => {
      if (item.type !== 'group') {
        return canAccess(item, menuPermissions, isSuperAdminUser.value) ? item : null
      }
      const children = (item.children || []).filter((child) => canAccess(child, menuPermissions, isSuperAdminUser.value))
      if (!children.length) return null
      return { ...item, children }
    })
    .filter(Boolean)
})

const keepAliveNames = computed(() => {
  return Array.from(
    new Set(
      routeTabs.value
        .map((item) => String(item.routeName || '').trim())
        .filter(Boolean)
    )
  )
})

const currentPageTitle = computed(() => {
  const current = routeTabs.value.find((item) => item.path === activeTabPath.value)
  return current ? current.title : resolveRouteTitle(route)
})

const activeMenuPath = computed(() => {
  const value = route && route.meta ? String(route.meta.activeMenu || '').trim() : ''
  return normalizeAdminRoutePath(value || route.path)
})

const currentGroupLabel = computed(() => {
  const path = activeMenuPath.value
  const hit = visibleMenus.value.find((item) => {
    if (item.type === 'item') return item.index === path
    return (item.children || []).some((child) => child.index === path)
  })
  return hit && hit.type === 'group' ? hit.label : ''
})

const tabSortFilteredRows = computed(() => {
  const keyword = String(tabSortKeyword.value || '').trim().toLowerCase()
  if (!keyword) return tabSortDraftRows.value
  return tabSortDraftRows.value.filter((row) => {
    const title = String(row && row.title ? row.title : '').toLowerCase()
    const routeName = String(row && row.routeName ? row.routeName : '').toLowerCase()
    const path = String(row && row.path ? row.path : '').toLowerCase()
    return title.includes(keyword) || routeName.includes(keyword) || path.includes(keyword)
  })
})

const tabSortButtonRows = computed(() => {
  if (tabSortMode.value !== 'button') return tabSortDraftRows.value
  return tabSortFilteredRows.value
})

const tabSortPageTotalRecords = computed(() => tabSortButtonRows.value.length)

const tabSortPageTotal = computed(() => {
  const total = Math.max(0, Number(tabSortPageTotalRecords.value || 0))
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  return Math.max(1, Math.ceil(total / size))
})

const tabSortCenterRows = computed(() => {
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const start = (Math.max(1, Number(tabSortPage.value || 1)) - 1) * size
  return tabSortButtonRows.value.slice(start, start + size)
})

const tabSortIndexMap = computed(() => {
  const map = new Map()
  tabSortDraftRows.value.forEach((item, idx) => {
    const path = toTabSortKey(item && item.path)
    if (!path) return
    map.set(path, idx + 1)
  })
  return map
})

function loadActiveTabPath() {
  const saved = String(localStorage.getItem(ACTIVE_TAB_PATH_KEY) || '').trim()
  return saved || DEFAULT_TAB
}

function normalizeTabPath(path) {
  return String(path || '')
    .split('?')[0]
    .trim()
}

function resolveTabIdentity(path) {
  return normalizeTabPath(path)
}

function compareStableText(a, b) {
  const left = String(a || '')
  const right = String(b || '')
  if (left === right) return 0
  return left < right ? -1 : 1
}

function resolveTabQuerySignature(path) {
  const raw = String(path || '').trim()
  const queryStart = raw.indexOf('?')
  if (queryStart < 0) {
    return { hasQuery: false, signature: '' }
  }
  const hashStart = raw.indexOf('#', queryStart + 1)
  const queryEnd = hashStart >= 0 ? hashStart : raw.length
  const queryRaw = raw.slice(queryStart + 1, queryEnd).trim()
  if (!queryRaw) {
    return { hasQuery: false, signature: '' }
  }
  const search = new URLSearchParams(queryRaw)
  const entries = []
  search.forEach((value, key) => {
    entries.push([String(key || ''), String(value || '')])
  })
  if (!entries.length) {
    return { hasQuery: false, signature: '' }
  }
  entries.sort((a, b) => {
    const keyCompare = compareStableText(a[0], b[0])
    if (keyCompare) return keyCompare
    return compareStableText(a[1], b[1])
  })
  return {
    hasQuery: true,
    signature: entries.map((entry) => `${entry[0]}=${entry[1]}`).join('&')
  }
}

function upsertTabByPolicy(tabs, nextTab) {
  if (!Array.isArray(tabs)) return -1
  const tab = nextTab && typeof nextTab === 'object' ? { ...nextTab } : null
  if (!tab || !tab.path) return -1
  const nextPath = String(tab.path || '').trim()
  const nextIdentity = resolveTabIdentity(nextPath)
  if (!nextIdentity) return -1
  const nextQuery = resolveTabQuerySignature(nextPath)
  let sameQueryIndex = -1
  let replaceIndex = -1

  for (let idx = 0; idx < tabs.length; idx += 1) {
    const item = tabs[idx]
    const itemPath = String((item && item.path) || '').trim()
    if (!itemPath) continue
    if (itemPath === nextPath) {
      tabs[idx] = tab
      return idx
    }
    if (resolveTabIdentity(itemPath) !== nextIdentity) continue
    const itemQuery = resolveTabQuerySignature(itemPath)
    if (sameQueryIndex < 0 && itemQuery.signature === nextQuery.signature) {
      sameQueryIndex = idx
      continue
    }
    if (replaceIndex < 0 && itemQuery.hasQuery && nextQuery.hasQuery && itemQuery.signature !== nextQuery.signature) {
      replaceIndex = idx
    }
  }

  if (replaceIndex >= 0) {
    tabs[replaceIndex] = tab
    return replaceIndex
  }
  if (sameQueryIndex >= 0) {
    return sameQueryIndex
  }
  tabs.push(tab)
  return tabs.length - 1
}

function restoreTabItem(rawTab) {
  if (!rawTab || typeof rawTab !== 'object') return null
  const rawPath = String(rawTab.path || rawTab.fullPath || '').trim()
  if (!rawPath || normalizeTabPath(rawPath) === '/login') return null
  const matched = resolveRoute(rawPath)
  const fallbackPath = String(rawPath || '').trim()
  const resolvedFullPath = String((matched && matched.fullPath) || '').trim()
  const path = resolvedFullPath || fallbackPath
  if (!path || normalizeTabPath(path) === '/login') return null
  const savedTitle = String(rawTab.title || '').trim()
  const title = savedTitle || resolveRouteTitle(matched || rawPath)
  return {
    path,
    title,
    routeName: matched && matched.name ? String(matched.name) : '',
    closable: !FIXED_TABS.has(normalizeTabPath(path))
  }
}

function loadRouteTabsState() {
  try {
    const saved = JSON.parse(localStorage.getItem(ROUTE_TABS_KEY) || '[]')
    const source = Array.isArray(saved) ? saved : []
    const restored = []
    source.forEach((item) => {
      const restoredItem = restoreTabItem(item)
      if (!restoredItem) return
      upsertTabByPolicy(restored, restoredItem)
    })
    if (!restored.some((item) => resolveTabIdentity(item.path) === DEFAULT_TAB)) {
      restored.unshift(toTab(DEFAULT_TAB))
    }
    return restored.length ? restored : [toTab(DEFAULT_TAB)]
  } catch (error) {
    return [toTab(DEFAULT_TAB)]
  }
}

function persistRouteTabsState() {
  const records = routeTabs.value.map((item) => ({
    path: item.path,
    title: item.title,
    routeName: item.routeName
  }))
  localStorage.setItem(ROUTE_TABS_KEY, JSON.stringify(records))
}

function clearRouteTabsPersistTimer() {
  if (!routeTabsPersistTimer) return
  window.clearTimeout(routeTabsPersistTimer)
  routeTabsPersistTimer = 0
}

function flushRouteTabsStatePersist() {
  clearRouteTabsPersistTimer()
  persistRouteTabsState()
}

function scheduleRouteTabsStatePersist(immediate = false) {
  if (immediate) {
    flushRouteTabsStatePersist()
    return
  }
  clearRouteTabsPersistTimer()
  routeTabsPersistTimer = window.setTimeout(() => {
    routeTabsPersistTimer = 0
    persistRouteTabsState()
  }, ROUTE_TABS_PERSIST_DELAY_MS)
}

function invalidateTabsNavCache() {
  tabsNavItemsCache = []
  tabsNavItemsCacheAt = 0
  tabsNavCacheLastVersion = -1
  tabsNodePathCache = new WeakMap()
  tabsPathNodeCache = new Map()
}

function markTabsNavCacheDirty() {
  tabsNavCacheVersion += 1
  invalidateTabsNavCache()
}

function hasMenuPermission(index, menuPermissions) {
  const key = String(index || '').trim()
  if (!key || !key.startsWith('/')) return false
  if (!(menuPermissions instanceof Set) || !menuPermissions.size) return false
  if (menuPermissions.has('*') || menuPermissions.has(key)) return true
  for (const code of menuPermissions) {
    const text = String(code || '').trim()
    if (!text || text === '*') continue
    if (key.startsWith(`${text}/`)) return true
  }
  return false
}

function canAccess(item, menuPermissions, isSuperAdmin) {
  if (!item || typeof item !== 'object') return false
  if (item.requiresSuperAdmin && !isSuperAdmin) return false
  const hasMenuConfig = menuPermissions instanceof Set && menuPermissions.size > 0
  const index = String(item.index || '').trim()
  const permissionPath = resolveAdminPermissionPath(index)
  if (permissionPath.startsWith('/')) {
    if (hasMenuConfig) return hasMenuPermission(permissionPath, menuPermissions)
    return permissionPath === '/dashboard'
  }
  if (hasMenuPermission(permissionPath, menuPermissions)) return true
  if (hasMenuConfig) return false
  return permissionPath === '/dashboard'
}

function queryText(query, key) {
  const raw = query && query[key]
  const value = Array.isArray(raw) ? raw[0] : raw
  return String(value || '').trim()
}

function resolveRoute(routeLike) {
  if (typeof routeLike === 'string') {
    return router.resolve(routeLike)
  }
  if (routeLike && typeof routeLike === 'object') {
    const payload = {}
    if (routeLike.path) payload.path = routeLike.path
    if (routeLike.name) payload.name = routeLike.name
    if (routeLike.params) payload.params = routeLike.params
    if (routeLike.query) payload.query = routeLike.query
    return router.resolve(payload)
  }
  return router.resolve(DEFAULT_TAB)
}

function resolveRouteTitle(routeLike) {
  const matched = resolveRoute(routeLike)
  const routeName = matched && matched.name ? String(matched.name) : ''
  if (routeName === 'field-cycles-detail') {
    const fieldId = matched && matched.params ? String(matched.params.fieldId || '').trim() : ''
    const fieldName = queryText(matched && matched.query, 'fieldName')
    if (fieldName && fieldId) return `田块种植计划｜${fieldName}（ID ${fieldId}）`
    if (fieldName) return `田块种植计划｜${fieldName}`
    return fieldId ? `田块种植计划｜田块ID ${fieldId}` : '田块种植计划详情'
  }
  if (routeName === 'process-template-detail') {
    const templateId = matched && matched.params ? String(matched.params.templateId || '').trim() : ''
    const templateName = queryText(matched && matched.query, 'templateName')
    if (templateName && templateId) return `流程模板详情｜${templateName}（ID ${templateId}）`
    if (templateName) return `流程模板详情｜${templateName}`
    return templateId ? `流程模板详情｜模板ID ${templateId}` : '流程模板详情'
  }
  if (routeName === 'farm-records-field-detail') {
    const fieldId = matched && matched.params ? String(matched.params.fieldId || '').trim() : ''
    const fieldName = queryText(matched && matched.query, 'fieldName')
    if (fieldName && fieldId) return `农事记录详情｜${fieldName}（ID ${fieldId}）`
    if (fieldName) return `农事记录详情｜${fieldName}`
    return fieldId ? `农事记录详情｜田块ID ${fieldId}` : '农事记录详情'
  }
  const title = matched && matched.meta ? String(matched.meta.title || '').trim() : ''
  if (title) return title
  return String((matched && matched.name) || '工作台')
}

function toTab(routeLike) {
  const matched = resolveRoute(routeLike)
  const resolvedPath = String((matched && matched.fullPath) || (matched && matched.path) || '').trim()
  const fallbackPath = String((routeLike && routeLike.fullPath) || (routeLike && routeLike.path) || routeLike || DEFAULT_TAB).trim()
  const path = resolvedPath || fallbackPath || DEFAULT_TAB
  return {
    path,
    title: resolveRouteTitle(matched),
    routeName: matched && matched.name ? String(matched.name) : '',
    closable: !FIXED_TABS.has(normalizeTabPath(path))
  }
}

function replaceTabByIdentity(nextTab) {
  const current = Array.isArray(routeTabs.value) ? routeTabs.value : []
  const merged = current.slice()
  const replacedIndex = upsertTabByPolicy(merged, nextTab)
  if (replacedIndex < 0) return -1
  routeTabs.value = merged
  return replacedIndex
}

function ensureTab(routeLike) {
  const matched = resolveRoute(routeLike)
  const tabPath = String((matched && matched.fullPath) || (matched && matched.path) || '').trim()
  if (!tabPath || normalizeTabPath(tabPath) === '/login') return
  const tabIndex = replaceTabByIdentity(toTab(matched))
  const focusedPath =
    tabIndex >= 0 && tabIndex < routeTabs.value.length ? String((routeTabs.value[tabIndex] && routeTabs.value[tabIndex].path) || '').trim() : ''
  activeTabPath.value = focusedPath || tabPath
}

function syncTabsWithRoute(routeLike) {
  if (!routeTabs.value.length) {
    routeTabs.value = [toTab(DEFAULT_TAB)]
  } else if (!routeTabs.value.some((item) => normalizeTabPath(item.path) === DEFAULT_TAB)) {
    routeTabs.value.unshift(toTab(DEFAULT_TAB))
  }
  ensureTab(routeLike || DEFAULT_TAB)
  scrollActiveTabIntoView()
}

function onRouteTabClick(tab) {
  if (Date.now() < tabClickSuppressUntil.value) return
  const targetPath = String((tab && tab.paneName) || activeTabPath.value || '')
  activateTabByPath(targetPath)
  scrollTabIntoViewByPath(targetPath, { margin: 10, mode: 'click', maxRetries: 3, retryCount: 0 })
  window.setTimeout(() => {
    scrollTabIntoViewByPath(targetPath, { margin: 10, mode: 'click', maxRetries: 1, retryCount: 0 })
  }, 120)
}

function activateTabByPath(path) {
  const targetPath = String(path || '').trim()
  if (!targetPath) return
  activeTabPath.value = targetPath
  if (route.fullPath !== targetPath) {
    router.push(targetPath)
  }
}

function scheduleTabActivation(path, delay = 0) {
  if (tabActivateTimer.value) {
    window.clearTimeout(tabActivateTimer.value)
    tabActivateTimer.value = 0
  }
  const wait = Math.max(0, Number(delay || 0))
  if (!wait) {
    activateTabByPath(path)
    return
  }
  tabActivateTimer.value = window.setTimeout(() => {
    tabActivateTimer.value = 0
    activateTabByPath(path)
  }, wait)
}

function isFixedTab(path) {
  return FIXED_TABS.has(normalizeTabPath(path))
}

function removeRouteTab(targetPath) {
  const path = String(targetPath || '').trim()
  if (!path || isFixedTab(path)) {
    return
  }
  const tabs = routeTabs.value
  const index = tabs.findIndex((item) => item.path === path)
  if (index < 0) {
    return
  }
  const wasActive = activeTabPath.value === path
  tabs.splice(index, 1)
  if (!tabs.length) {
    routeTabs.value = [toTab(DEFAULT_TAB)]
  }
  if (!wasActive) {
    return
  }
  const next = tabs[index] || tabs[index - 1] || routeTabs.value[0]
  const nextPath = next ? next.path : DEFAULT_TAB
  activeTabPath.value = nextPath
  if (route.fullPath !== nextPath) {
    router.push(nextPath)
  }
}

function closeOtherTabs() {
  closeOtherTabsByPath(activeTabPath.value || DEFAULT_TAB)
}

function closeRightTabs() {
  closeRightTabsByPath(activeTabPath.value || DEFAULT_TAB)
}

function closeOtherTabsByPath(path) {
  const focus = String(path || '').trim() || DEFAULT_TAB
  routeTabs.value = routeTabs.value.filter((item) => isFixedTab(item.path) || item.path === focus)
  activeTabPath.value = focus
  if (route.fullPath !== focus) {
    router.push(focus)
  }
}

function closeRightTabsByPath(path) {
  const focus = String(path || '').trim() || DEFAULT_TAB
  const activeIndex = routeTabs.value.findIndex((item) => item.path === focus)
  if (activeIndex < 0) return
  routeTabs.value = routeTabs.value.filter((item, index) => isFixedTab(item.path) || index <= activeIndex)
  if (!routeTabs.value.some((item) => item.path === activeTabPath.value)) {
    activeTabPath.value = focus
    if (route.fullPath !== focus) {
      router.push(focus)
    }
  }
}

function closeLeftTabsByPath(path) {
  const focus = String(path || '').trim() || DEFAULT_TAB
  const focusIndex = routeTabs.value.findIndex((item) => item.path === focus)
  if (focusIndex < 0) return
  routeTabs.value = routeTabs.value.filter((item, index) => isFixedTab(item.path) || index >= focusIndex)
  if (!routeTabs.value.some((item) => item.path === activeTabPath.value)) {
    activeTabPath.value = focus
    if (route.fullPath !== focus) {
      router.push(focus)
    }
  }
}

function closeAllTabs() {
  routeTabs.value = [toTab(DEFAULT_TAB)]
  activeTabPath.value = DEFAULT_TAB
  if (route.fullPath !== DEFAULT_TAB) {
    router.push(DEFAULT_TAB)
  }
}

function toTabSortKey(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function getTabSortBodyEl() {
  if (!tabSortShellRef.value || typeof tabSortShellRef.value.getBodyEl !== 'function') return null
  return tabSortShellRef.value.getBodyEl()
}

function buildTabSortRows() {
  const rows = routeTabs.value
    .filter((item) => item && item.closable)
    .map((item, idx) => ({
      path: String(item.path || '').trim(),
      title: String(item.title || '').trim(),
      routeName: String(item.routeName || '').trim(),
      jumpTo: idx + 1
    }))
    .filter((item) => item.path)
  tabSortDraftRows.value = rows.map((item) => ({ ...item }))
  tabSortInitialRows.value = rows.map((item) => ({ ...item }))
}

function resolveTabSortIndex(row) {
  const path = toTabSortKey(row && row.path)
  if (!path) return '-'
  return tabSortIndexMap.value.get(path) ?? '-'
}

function setTabSortDragPageRows(nextRows) {
  const next = Array.isArray(nextRows) ? nextRows : []
  const current = Array.isArray(tabSortDragPageRows.value) ? tabSortDragPageRows.value : []
  if (
    current.length === next.length &&
    current.every((item, idx) => toTabSortKey(item && item.path) === toTabSortKey(next[idx] && next[idx].path))
  ) {
    return
  }
  tabSortDragPageRows.value = next.slice()
}

function isCurrentTabSortRow(row) {
  const rowPath = toTabSortKey(row && row.path)
  const currentPath = toTabSortKey(activeTabPath.value)
  if (!rowPath || !currentPath) return false
  return rowPath === currentPath
}

function tabSortTableRowClassName({ row }) {
  return isCurrentTabSortRow(row) ? 'is-current-tab-row' : ''
}

function normalizeTabSortDraftRows() {
  const list = Array.isArray(tabSortDraftRows.value) ? tabSortDraftRows.value : []
  const baseline = Array.isArray(tabSortInitialRows.value) && tabSortInitialRows.value.length
    ? tabSortInitialRows.value
    : list
  const fallbackMap = new Map()
  baseline.forEach((item) => {
    const path = toTabSortKey(item && item.path)
    if (path && !fallbackMap.has(path)) {
      fallbackMap.set(path, item)
    }
  })
  list.forEach((item) => {
    const path = toTabSortKey(item && item.path)
    if (path && !fallbackMap.has(path)) {
      fallbackMap.set(path, item)
    }
  })
  const seen = new Set()
  const normalized = []
  list.forEach((item) => {
    const path = toTabSortKey(item && item.path)
    if (!path || seen.has(path)) return
    seen.add(path)
    normalized.push(item)
  })
  fallbackMap.forEach((item, path) => {
    if (seen.has(path)) return
    seen.add(path)
    normalized.push({ ...item })
  })
  if (
    normalized.length === list.length &&
    normalized.every((item, idx) => toTabSortKey(item && item.path) === toTabSortKey(list[idx] && list[idx].path))
  ) {
    return
  }
  tabSortDraftRows.value = normalized
}

function syncTabSortPage() {
  normalizeTabSortDraftRows()
  const maxPage = Math.max(1, Number(tabSortPageTotal.value || 1))
  const nextPage = Math.max(1, Math.min(maxPage, Number(tabSortPage.value || 1)))
  if (nextPage !== tabSortPage.value) {
    tabSortPage.value = nextPage
  }
}

function refreshTabSortPageRows() {
  normalizeTabSortDraftRows()
  syncTabSortPage()
  if (tabSortMode.value !== 'drag') {
    setTabSortDragPageRows([])
    return
  }
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  if (tabSortDragging.value && tabSortPage.value !== tabSortDragStartPage.value) {
    refreshTabSortCrossPageDragRowsByPoint(tabSortTurnPendingPoint)
    return
  }
  const start = (Math.max(1, Number(tabSortPage.value || 1)) - 1) * size
  setTabSortDragPageRows(tabSortDraftRows.value.slice(start, start + size))
}

function refreshTabSortJumpNumbers() {
  normalizeTabSortDraftRows()
  tabSortDraftRows.value.forEach((row, idx) => {
    row.jumpTo = idx + 1
  })
  refreshTabSortPageRows()
}

function cloneTabSortRows(rows) {
  // Shallow copy is enough for reordering; avoid cloning large row objects repeatedly.
  return Array.isArray(rows) ? rows.slice() : []
}

function captureTabSortDragSnapshot() {
  const snapshot = cloneTabSortRows(tabSortDraftRows.value)
  tabSortDragStartSnapshot.value = snapshot
  tabSortDragStartPage.value = Math.max(1, Number(tabSortPage.value || 1))
  const dragPath = toTabSortKey(tabSortDraggingPath.value)
  const sourceIndex = dragPath
    ? snapshot.findIndex((item) => toTabSortKey(item && item.path) === dragPath)
    : -1
  tabSortDragSourceIndex.value = sourceIndex
  tabSortDragSourceRow.value = sourceIndex >= 0 ? snapshot[sourceIndex] : null
}

function restoreTabSortDragSnapshot() {
  if (!Array.isArray(tabSortDragStartSnapshot.value) || !tabSortDragStartSnapshot.value.length) return
  tabSortDraftRows.value = cloneTabSortRows(tabSortDragStartSnapshot.value)
  tabSortPage.value = Math.max(1, Number(tabSortDragStartPage.value || 1))
  refreshTabSortJumpNumbers()
}

function applyTabSortSamePageDropByEvent(event) {
  const snapshot = cloneTabSortRows(tabSortDragStartSnapshot.value)
  if (!snapshot.length) return false
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const startPage = Math.max(1, Number(tabSortDragStartPage.value || 1))
  const start = (startPage - 1) * size
  const expectedCount = Math.min(size, Math.max(0, snapshot.length - start))
  if (expectedCount <= 1) {
    tabSortDraftRows.value = snapshot
    return true
  }
  const oldIndex = Number(event && event.oldDraggableIndex)
  const newIndex = Number(event && event.newDraggableIndex)
  if (!Number.isInteger(oldIndex) || !Number.isInteger(newIndex)) {
    tabSortDraftRows.value = snapshot
    return true
  }
  if (oldIndex < 0 || oldIndex >= expectedCount || newIndex < 0 || newIndex >= expectedCount) {
    tabSortDraftRows.value = snapshot
    return true
  }
  if (oldIndex === newIndex) {
    tabSortDraftRows.value = snapshot
    return true
  }
  const pageRows = snapshot.slice(start, start + expectedCount)
  const moved = pageRows.splice(oldIndex, 1)[0]
  pageRows.splice(newIndex, 0, moved)
  snapshot.splice(start, expectedCount, ...pageRows)
  tabSortDraftRows.value = snapshot
  return true
}

function ensureTabSortDraftIntegrityAfterDrag() {
  const snapshot = cloneTabSortRows(tabSortDragStartSnapshot.value)
  if (!snapshot.length) return
  const baselinePaths = snapshot.map((item) => toTabSortKey(item && item.path)).filter(Boolean)
  const list = Array.isArray(tabSortDraftRows.value) ? tabSortDraftRows.value : []
  const currentPaths = list.map((item) => toTabSortKey(item && item.path)).filter(Boolean)
  if (currentPaths.length !== baselinePaths.length) {
    tabSortDraftRows.value = snapshot
    return
  }
  const seen = new Set()
  for (const path of currentPaths) {
    if (!path || seen.has(path)) {
      tabSortDraftRows.value = snapshot
      return
    }
    seen.add(path)
  }
  for (const path of baselinePaths) {
    if (!seen.has(path)) {
      tabSortDraftRows.value = snapshot
      return
    }
  }
}

function clearTabSortTurnTimer() {
  if (tabSortTurnTimer) {
    clearTimeout(tabSortTurnTimer)
    tabSortTurnTimer = null
  }
}

function canTurnTabSortPage(direction) {
  const dir = String(direction || '').trim()
  if (dir === 'prev') return tabSortPage.value > 1
  if (dir === 'next') return tabSortPage.value < tabSortPageTotal.value
  return false
}

function scheduleTabSortTurn(direction) {
  clearTabSortTurnTimer()
  if (!canTurnTabSortPage(direction)) return
  tabSortTurnTimer = setTimeout(() => {
    if (!tabSortDragging.value) return
    if (tabSortTurnDirection.value !== direction) return
    if (!canTurnTabSortPage(direction)) return
    turnTabSortPage(direction, tabSortTurnPendingPoint)
    tabSortTurnConsumedDirection = direction
    clearTabSortTurnTimer()
  }, TAB_SORT_TURN_DELAY)
}

function calcTabSortCrossPageInsertIndex(
  point,
  targetStart,
  targetEndExclusive,
  fallbackIndex,
  totalLengthInput = tabSortDraftRows.value.length
) {
  const totalLength = Math.max(0, Number(totalLengthInput || 0))
  const fallback = Math.max(0, Math.min(totalLength, Number(fallbackIndex || 0)))
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return fallback
  const body = getTabSortBodyEl()
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
      if (Number.isInteger(tabSortCrossPreviewLastInsertIndex) && tabSortCrossPreviewLastInsertIndex >= 0) {
        return Math.max(0, Math.min(totalLength, tabSortCrossPreviewLastInsertIndex))
      }
      return fallback
    }
    const dragPath = toTabSortKey(tabSortDraggingPath.value)
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
      const hoverPath = toTabSortKey(hoverCard && hoverCard.dataset ? hoverCard.dataset.sortId : '')
      if (hoverPath && (!dragPath || hoverPath !== dragPath)) {
        const rows = Array.isArray(tabSortDragPageRows.value) ? tabSortDragPageRows.value : []
        let compactIndex = -1
        let compactOffset = 0
        for (let i = 0; i < rows.length; i += 1) {
          const rowPath = toTabSortKey(rows[i] && rows[i].path)
          if (!rowPath || (dragPath && rowPath === dragPath)) continue
          if (rowPath === hoverPath) {
            compactIndex = compactOffset
            break
          }
          compactOffset += 1
        }
        if (compactIndex >= 0) {
          const placeAfter = Boolean(tabSortCrossPreviewMoveDown)
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
        const splitY = tabSortCrossPreviewMoveDown ? rect.top : rect.bottom
        if (point.clientY < splitY) {
          break
        }
        const cardPath = toTabSortKey(card && card.dataset ? card.dataset.sortId : '')
        if (dragPath && cardPath === dragPath) continue
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

function applyTabSortCrossPageDropByPoint(point) {
  const dragPath = toTabSortKey(tabSortDraggingPath.value)
  const snapshot = cloneTabSortRows(tabSortDragStartSnapshot.value)
  if (!dragPath || !snapshot.length) return false
  let from = Number(tabSortDragSourceIndex.value)
  if (!Number.isInteger(from) || from < 0 || from >= snapshot.length) {
    from = snapshot.findIndex((item) => toTabSortKey(item && item.path) === dragPath)
  }
  if (from < 0) return false
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const sourcePage = Math.max(1, Number(tabSortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(tabSortPage.value || sourcePage)))
  const list = snapshot
  const moved = list.splice(from, 1)[0]
  const targetStart = Math.max(0, Math.min(list.length, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(list.length, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcTabSortCrossPageInsertIndex(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    list.length
  )
  list.splice(insertIndex, 0, moved)
  tabSortDraftRows.value = list
  return true
}

function buildTabSortPageRowsFromList(rows, pageInput = tabSortPage.value) {
  const source = Array.isArray(rows) ? rows : []
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const pageNo = Math.max(1, Number(pageInput || 1))
  const start = (pageNo - 1) * size
  return source.slice(start, start + size)
}

function getTabSortCrossPageBaseRows() {
  const snapshot = Array.isArray(tabSortDragStartSnapshot.value) ? tabSortDragStartSnapshot.value : []
  if (snapshot.length) {
    return buildTabSortPageRowsFromList(snapshot)
  }
  return buildTabSortPageRowsFromList(tabSortDraftRows.value)
}

function buildTabSortCrossPagePreviewPageRows(snapshot, from, insertIndex, targetPage, pageSize) {
  const source = Array.isArray(snapshot) ? snapshot : []
  const total = source.length
  const sourceIndex = Number(from)
  if (!total || sourceIndex < 0 || sourceIndex >= total) return []
  const size = Math.max(1, Number(pageSize || tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const pageNo = Math.max(1, Number(targetPage || tabSortPage.value || 1))
  const moved = tabSortDragSourceRow.value || source[sourceIndex]
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

function buildTabSortCrossPagePreviewRows(point) {
  const snapshot = Array.isArray(tabSortDragStartSnapshot.value) ? tabSortDragStartSnapshot.value : []
  const from = Number(tabSortDragSourceIndex.value)
  if (!snapshot.length || from < 0 || from >= snapshot.length) return getTabSortCrossPageBaseRows()
  const size = Math.max(1, Number(tabSortPageSize.value || TAB_SORT_PAGE_SIZE_DEFAULT))
  const sourcePage = Math.max(1, Number(tabSortDragStartPage.value || 1))
  const maxPage = Math.max(1, Math.ceil(snapshot.length / size))
  const targetPage = Math.max(1, Math.min(maxPage, Number(tabSortPage.value || sourcePage)))
  const reducedLength = Math.max(0, snapshot.length - 1)
  const targetStart = Math.max(0, Math.min(reducedLength, (targetPage - 1) * size))
  const targetEndExclusive = Math.min(reducedLength, targetStart + size)
  const fallbackIndex = targetPage > sourcePage ? targetStart : targetEndExclusive
  const insertIndex = calcTabSortCrossPageInsertIndex(
    point,
    targetStart,
    targetEndExclusive,
    fallbackIndex,
    reducedLength
  )
  if (
    targetPage === tabSortCrossPreviewLastPage &&
    insertIndex === tabSortCrossPreviewLastInsertIndex &&
    Array.isArray(tabSortDragPageRows.value) &&
    tabSortDragPageRows.value.length
  ) {
    return tabSortDragPageRows.value
  }
  tabSortCrossPreviewLastPage = targetPage
  tabSortCrossPreviewLastInsertIndex = insertIndex
  return buildTabSortCrossPagePreviewPageRows(snapshot, from, insertIndex, targetPage, size)
}

function refreshTabSortCrossPageDragRowsByPoint(point) {
  if (!tabSortDragging.value || tabSortPage.value === tabSortDragStartPage.value) return false
  if (point && typeof point.clientY === 'number') {
    if (Number.isFinite(tabSortCrossPreviewLastPointerY)) {
      tabSortCrossPreviewMoveDown = point.clientY >= tabSortCrossPreviewLastPointerY
    }
    tabSortCrossPreviewLastPointerY = point.clientY
  }
  const pointKey = point && typeof point.clientY === 'number'
    ? `${tabSortPage.value}:${Math.round((Number(point.clientX || 0)) / 8)}:${Math.round(point.clientY / 3)}`
    : `${tabSortPage.value}:na`
  if (pointKey === tabSortCrossPreviewLastPointKey) return true
  tabSortCrossPreviewLastPointKey = pointKey
  setTabSortDragPageRows(buildTabSortCrossPagePreviewRows(point))
  return true
}

function turnTabSortPage(direction, point) {
  const dir = String(direction || '').trim()
  if (point && typeof point.clientX === 'number' && typeof point.clientY === 'number') {
    tabSortTurnPendingPoint = {
      clientX: point.clientX,
      clientY: point.clientY
    }
  }
  let changed = false
  if (dir === 'prev') {
    if (tabSortPage.value > 1) {
      tabSortPage.value -= 1
      changed = true
    }
  } else if (dir === 'next') {
    if (tabSortPage.value < tabSortPageTotal.value) {
      tabSortPage.value += 1
      changed = true
    }
  }
  if (changed) {
    refreshTabSortPageRows()
  }
  if (tabSortDragging.value) {
    tabSortSuspendPageUpdate.value = tabSortPage.value !== tabSortDragStartPage.value
    if (tabSortSuspendPageUpdate.value) {
      tabSortVisitedCrossPageDuringDrag.value = true
      refreshTabSortCrossPageDragRowsByPoint(tabSortTurnPendingPoint)
    }
  }
}

function clearTabSortDragState() {
  clearTabSortTurnTimer()
  if (tabSortTurnRaf) {
    cancelAnimationFrame(tabSortTurnRaf)
    tabSortTurnRaf = 0
  }
  tabSortTurnPendingPoint = null
  tabSortTurnDirection.value = ''
  tabSortDragging.value = false
  tabSortDraggingPath.value = ''
  tabSortSuspendPageUpdate.value = false
  tabSortDragStartSnapshot.value = []
  tabSortDragStartPage.value = 1
  tabSortDragSourceIndex.value = -1
  tabSortDragSourceRow.value = null
  tabSortVisitedCrossPageDuringDrag.value = false
  tabSortTurnConsumedDirection = ''
  tabSortAutoScrollLastAt = 0
  tabSortAutoScrollCarry = 0
  tabSortAutoScrollDirection = 0
  tabSortAutoScrollVelocity = 0
  tabSortCrossPreviewLastPage = 0
  tabSortCrossPreviewLastInsertIndex = -1
  tabSortCrossPreviewLastPointKey = ''
  tabSortCrossPreviewLastPointerY = NaN
  tabSortCrossPreviewMoveDown = true
}

function resolveTabSortTurnPoint(event) {
  if (!event) return null
  if (event.touches && event.touches[0]) return event.touches[0]
  if (event.changedTouches && event.changedTouches[0]) return event.changedTouches[0]
  if (event.originalEvent) return resolveTabSortTurnPoint(event.originalEvent)
  if (typeof event.clientX === 'number' && typeof event.clientY === 'number') return event
  return null
}

function autoScrollTabSortBody(point) {
  const body = getTabSortBodyEl()
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
    tabSortAutoScrollLastAt = 0
    tabSortAutoScrollCarry = 0
    tabSortAutoScrollDirection = 0
    tabSortAutoScrollVelocity = 0
    return
  }
  if (tabSortAutoScrollDirection && tabSortAutoScrollDirection !== targetDirection) {
    tabSortAutoScrollCarry = 0
    tabSortAutoScrollVelocity = 0
  }
  tabSortAutoScrollDirection = targetDirection
  const now = Date.now()
  if (!tabSortAutoScrollLastAt) {
    tabSortAutoScrollLastAt = now - 16
  }
  const elapsed = Math.max(10, Math.min(40, now - tabSortAutoScrollLastAt))
  if (elapsed < 12) return
  tabSortAutoScrollLastAt = now
  const ratio = Math.max(0, Math.min(1, depth / edgeThreshold))
  const eased = Math.pow(ratio, 1.35)
  const targetVelocity = targetDirection * (minStep + (maxStep - minStep) * eased)
  const smooth = 0.3
  tabSortAutoScrollVelocity += (targetVelocity - tabSortAutoScrollVelocity) * smooth
  const deltaFloat = tabSortAutoScrollVelocity * (elapsed / 16)
  tabSortAutoScrollCarry += deltaFloat
  let delta = tabSortAutoScrollCarry > 0 ? Math.floor(tabSortAutoScrollCarry) : Math.ceil(tabSortAutoScrollCarry)
  if (delta > maxTickStep) delta = maxTickStep
  if (delta < -maxTickStep) delta = -maxTickStep
  if (delta) {
    tabSortAutoScrollCarry -= delta
  }
  if (!delta) return
  const maxScroll = Math.max(0, body.scrollHeight - body.clientHeight)
  const next = Math.max(0, Math.min(maxScroll, body.scrollTop + delta))
  if (next === body.scrollTop) {
    tabSortAutoScrollCarry = 0
    tabSortAutoScrollVelocity = 0
    tabSortAutoScrollDirection = 0
    return
  }
  body.scrollTop = next
}

function applyTabSortTurnByPoint(point) {
  if (!tabSortDragging.value || !point) return
  autoScrollTabSortBody(point)
  const target = document.elementFromPoint(point.clientX, point.clientY)
  tabSortSuspendPageUpdate.value = tabSortPage.value !== tabSortDragStartPage.value
  if (tabSortSuspendPageUpdate.value) {
    refreshTabSortCrossPageDragRowsByPoint(point)
  }
  const zone = target && target.closest ? target.closest('.sort-page-turn-zone') : null
  const rawDirection = zone && zone.getAttribute ? String(zone.getAttribute('data-turn') || '') : ''
  const direction = canTurnTabSortPage(rawDirection) ? rawDirection : ''
  if (!direction) {
    tabSortTurnDirection.value = ''
    tabSortTurnConsumedDirection = ''
    clearTabSortTurnTimer()
    return
  }
  if (direction !== tabSortTurnDirection.value) {
    tabSortTurnDirection.value = direction
    if (tabSortTurnConsumedDirection !== direction) {
      scheduleTabSortTurn(direction)
    }
    return
  }
  if (tabSortTurnConsumedDirection === direction) return
  if (!tabSortTurnTimer) {
    scheduleTabSortTurn(direction)
  }
}

function detectTabSortTurnZone(event) {
  if (!tabSortDragging.value) return
  const point = resolveTabSortTurnPoint(event)
  if (!point || typeof point.clientX !== 'number' || typeof point.clientY !== 'number') return
  tabSortTurnPendingPoint = {
    clientX: point.clientX,
    clientY: point.clientY
  }
  if (tabSortTurnRaf) return
  tabSortTurnRaf = requestAnimationFrame(() => {
    tabSortTurnRaf = 0
    applyTabSortTurnByPoint(tabSortTurnPendingPoint)
  })
}

function onTabSortDragMove(evt, originalEvent) {
  detectTabSortTurnZone(originalEvent || (evt && evt.originalEvent) || evt)
}

function onTabSortDragStart(event) {
  tabSortDragging.value = true
  tabSortDraggingPath.value = toTabSortKey(event && event.item && event.item.dataset ? event.item.dataset.sortId : '')
  tabSortTurnDirection.value = ''
  tabSortTurnConsumedDirection = ''
  tabSortAutoScrollLastAt = 0
  tabSortAutoScrollCarry = 0
  tabSortAutoScrollDirection = 0
  tabSortAutoScrollVelocity = 0
  tabSortCrossPreviewLastPage = 0
  tabSortCrossPreviewLastInsertIndex = -1
  tabSortCrossPreviewLastPointKey = ''
  tabSortCrossPreviewLastPointerY = NaN
  tabSortCrossPreviewMoveDown = true
  tabSortSuspendPageUpdate.value = false
  tabSortVisitedCrossPageDuringDrag.value = false
  captureTabSortDragSnapshot()
  document.addEventListener('drag', detectTabSortTurnZone)
  document.addEventListener('dragover', detectTabSortTurnZone)
  document.addEventListener('mousemove', detectTabSortTurnZone)
  document.addEventListener('touchmove', detectTabSortTurnZone, { passive: true })
}

function onTabSortDragEnd(event) {
  clearTabSortTurnTimer()
  if (tabSortTurnRaf) {
    cancelAnimationFrame(tabSortTurnRaf)
    tabSortTurnRaf = 0
  }
  document.removeEventListener('drag', detectTabSortTurnZone)
  document.removeEventListener('dragover', detectTabSortTurnZone)
  document.removeEventListener('mousemove', detectTabSortTurnZone)
  document.removeEventListener('touchmove', detectTabSortTurnZone)
  try {
    const pointFromEvent = resolveTabSortTurnPoint(event && event.originalEvent ? event.originalEvent : event)
    if (pointFromEvent && typeof pointFromEvent.clientX === 'number' && typeof pointFromEvent.clientY === 'number') {
      tabSortTurnPendingPoint = {
        clientX: pointFromEvent.clientX,
        clientY: pointFromEvent.clientY
      }
    }
    const draggedAcrossPage = tabSortPage.value !== tabSortDragStartPage.value
    if (!draggedAcrossPage) {
      applyTabSortSamePageDropByEvent(event)
    } else {
      const point = tabSortTurnPendingPoint
      const applied = applyTabSortCrossPageDropByPoint(point || null)
      if (!applied) {
        restoreTabSortDragSnapshot()
      }
    }
    ensureTabSortDraftIntegrityAfterDrag()
  } catch (error) {
    restoreTabSortDragSnapshot()
  } finally {
    clearTabSortDragState()
    refreshTabSortJumpNumbers()
  }
}

function moveTabSortRow(row, delta) {
  const path = toTabSortKey(row && row.path)
  if (!path) return
  const list = tabSortDraftRows.value
  const from = list.findIndex((item) => toTabSortKey(item && item.path) === path)
  if (from < 0) return
  const to = from + Number(delta || 0)
  if (to < 0 || to >= list.length) return
  const moved = list.splice(from, 1)[0]
  list.splice(to, 0, moved)
  refreshTabSortJumpNumbers()
}

function moveTabSortTop(row) {
  const path = toTabSortKey(row && row.path)
  if (!path) return
  const list = tabSortDraftRows.value
  const from = list.findIndex((item) => toTabSortKey(item && item.path) === path)
  if (from < 1) return
  const moved = list.splice(from, 1)[0]
  list.unshift(moved)
  refreshTabSortJumpNumbers()
}

function moveTabSortBottom(row) {
  const path = toTabSortKey(row && row.path)
  if (!path) return
  const list = tabSortDraftRows.value
  const from = list.findIndex((item) => toTabSortKey(item && item.path) === path)
  if (from < 0 || from === list.length - 1) return
  const moved = list.splice(from, 1)[0]
  list.push(moved)
  refreshTabSortJumpNumbers()
}

function applyTabSortJump(row) {
  const path = toTabSortKey(row && row.path)
  if (!path) return
  const list = tabSortDraftRows.value
  const from = list.findIndex((item) => toTabSortKey(item && item.path) === path)
  if (from < 0) return
  const target = Math.max(1, Math.min(list.length, Number(row.jumpTo || 1))) - 1
  if (target === from) return
  const moved = list.splice(from, 1)[0]
  list.splice(target, 0, moved)
  refreshTabSortJumpNumbers()
}

function onTabSortOpCommand(command, row) {
  const action = String(command || '').trim()
  if (action === 'top') {
    moveTabSortTop(row)
    return
  }
  if (action === 'bottom') {
    moveTabSortBottom(row)
  }
}

function onTabSortPageChange(nextPage) {
  tabSortPage.value = Number(nextPage || 1)
  refreshTabSortPageRows()
}

function onTabSortPageSizeChange(nextSize) {
  tabSortPageSize.value = Number(nextSize || TAB_SORT_PAGE_SIZE_DEFAULT)
  tabSortPage.value = 1
  refreshTabSortPageRows()
}

function resetTabSortCenter() {
  clearTabSortDragState()
  tabSortDraftRows.value = tabSortInitialRows.value.map((item, idx) => ({
    ...item,
    jumpTo: idx + 1
  }))
  tabSortKeyword.value = ''
  tabSortPage.value = 1
  refreshTabSortPageRows()
}

function openTabSortCenter() {
  buildTabSortRows()
  if (!tabSortDraftRows.value.length) {
    ElMessage.warning('当前没有可排序的页签')
    return
  }
  clearTabSortDragState()
  tabSortMode.value = 'drag'
  tabSortKeyword.value = ''
  tabSortPage.value = 1
  tabSortPageSize.value = TAB_SORT_PAGE_SIZE_DEFAULT
  refreshTabSortPageRows()
  tabSortCenterVisible.value = true
}

function applyTabSortCenter() {
  if (!tabSortDraftRows.value.length) {
    tabSortCenterVisible.value = false
    return
  }
  const fixedTabs = routeTabs.value.filter((item) => item && !item.closable)
  const closableTabs = routeTabs.value.filter((item) => item && item.closable)
  const closableMap = new Map(
    closableTabs.map((item) => [toTabSortKey(item.path), item]).filter((item) => item && item[0])
  )
  const orderedPaths = tabSortDraftRows.value.map((item) => toTabSortKey(item && item.path)).filter(Boolean)
  const used = new Set()
  const orderedClosableTabs = orderedPaths
    .map((path) => {
      const hit = closableMap.get(path)
      if (hit) {
        used.add(path)
      }
      return hit || null
    })
    .filter(Boolean)
  const fallbackTabs = closableTabs.filter((item) => !used.has(toTabSortKey(item && item.path)))
  routeTabs.value = [...fixedTabs, ...orderedClosableTabs, ...fallbackTabs]
  tabSortCenterVisible.value = false
  nextTick(() => {
    scrollActiveTabIntoView()
    scheduleTabsScrollStateSync()
  })
  ElMessage.success('页签排序已更新')
}

function handleTabCommand(command, targetPath = activeTabPath.value || DEFAULT_TAB) {
  if (command === 'openSortCenter') {
    openTabSortCenter()
    return
  }
  const path = String(targetPath || '').trim() || DEFAULT_TAB
  if (command === 'activate') {
    activeTabPath.value = path
    if (route.fullPath !== path) {
      router.push(path)
    }
    return
  }
  if (command === 'closeCurrent') {
    removeRouteTab(path)
    return
  }
  if (command === 'closeLeft') {
    closeLeftTabsByPath(path)
    return
  }
  if (command === 'closeOthers') {
    closeOtherTabsByPath(path)
    return
  }
  if (command === 'closeRight') {
    closeRightTabsByPath(path)
    return
  }
  if (command === 'closeAll') {
    closeAllTabs()
  }
}

function hideTabContextMenu() {
  tabContext.visible = false
}

function resolveTabByPointerEvent(event) {
  const rawTarget = event && event.target
  if (!(rawTarget instanceof HTMLElement)) return null
  const navItem = rawTarget.closest('.route-tabs .el-tabs__item[role="tab"]')
  if (!navItem) return null
  const nav = navItem.closest('.el-tabs__nav')
  const tabNodes = Array.from((nav && nav.querySelectorAll('.el-tabs__item[role="tab"]')) || [])
  const index = tabNodes.findIndex((item) => item === navItem)
  if (index < 0 || index >= routeTabs.value.length) return null
  return routeTabs.value[index]
}

function onTabsAreaContextMenu(event) {
  if (!tabDragState.sourcePath) {
    setTabDraggingCursor(false)
  }
  const tab = resolveTabByPointerEvent(event)
  if (!tab) {
    hideTabContextMenu()
    return
  }
  openTabContextMenu(event, tab)
}

function onRouteTabsMouseDown(event) {
  if (!event || event.button !== 1) return
  const tab = resolveTabByPointerEvent(event)
  if (!tab) return
  event.preventDefault()
  if (isFixedTab(tab.path)) return
  removeRouteTab(tab.path)
}

function onTabDragLabelMouseDown(event) {
  if (!event || event.button !== 0) {
    if (!tabDragState.sourcePath) {
      setTabDraggingCursor(false)
    }
    return
  }
  setTabDraggingCursor(true)
}

function onTabDragLabelMouseUp() {
  if (!tabDragState.sourcePath) {
    setTabDraggingCursor(false)
  }
}

function openTabContextMenu(event, tab) {
  const path = String((tab && tab.path) || '').trim()
  if (!path) return
  const menuWidth = 172
  const menuHeight = 222
  const maxX = Math.max(8, window.innerWidth - menuWidth - 8)
  const maxY = Math.max(8, window.innerHeight - menuHeight - 8)
  tabContext.path = path
  tabContext.x = Math.max(8, Math.min(event.clientX, maxX))
  tabContext.y = Math.max(8, Math.min(event.clientY, maxY))
  tabContext.visible = true
}

function applyTabContextCommand(command) {
  const path = tabContext.path || activeTabPath.value || DEFAULT_TAB
  hideTabContextMenu()
  handleTabCommand(command, path)
}

async function logout() {
  try {
    await request.post('/admin/auth/logout-all', {})
  } catch (e) {
    console.warn('admin logout-all failed', e)
  } finally {
    clearSession()
    localStorage.removeItem(ROUTE_TABS_KEY)
    localStorage.removeItem(ACTIVE_TAB_PATH_KEY)
    router.replace('/login')
  }
}

function refreshCurrentPage() {
  persistRouteTabsState()
  localStorage.setItem(ACTIVE_TAB_PATH_KEY, String(activeTabPath.value || DEFAULT_TAB))
  window.location.reload()
}

function toggleMenu() {
  if (isMobileViewport.value) {
    mobileMenuVisible.value = !mobileMenuVisible.value
    return
  }
  menuCollapsed.value = !menuCollapsed.value
}

function closeMobileMenu() {
  mobileMenuVisible.value = false
}

function onMenuSelect() {
  if (isMobileViewport.value) {
    mobileMenuVisible.value = false
  }
}

function goHome() {
  if (route.path !== DEFAULT_TAB) {
    router.push(DEFAULT_TAB)
  }
}

function goProfile() {
  router.push('/profile')
}

function goMessageStation() {
  router.push('/message-station')
}

function goPassword() {
  router.push({ path: '/profile', query: { tab: 'password' } })
}

function goSystemSettings() {
  router.push('/system-settings')
}

function onUserUpdated(event) {
  const detail = event && event.detail ? event.detail : null
  if (detail && typeof detail === 'object') {
    user.value = detail
    return
  }
  user.value = getUser() || {}
}

function resolveTabsNavWrapElement() {
  const root = routeTabsWrapRef.value
  if (!(root instanceof HTMLElement)) return null
  const navWrap = root.querySelector('.route-tabs .el-tabs__nav-wrap')
  return navWrap instanceof HTMLElement ? navWrap : null
}

function resolveTabsHeaderElement() {
  const root = routeTabsWrapRef.value
  if (!(root instanceof HTMLElement)) return null
  const header = root.querySelector('.route-tabs .el-tabs__header')
  return header instanceof HTMLElement ? header : null
}

function resolveTabsNavElement() {
  const root = routeTabsWrapRef.value
  if (!(root instanceof HTMLElement)) return null
  const nav = root.querySelector('.route-tabs .el-tabs__nav')
  return nav instanceof HTMLElement ? nav : null
}

function parseTranslateX(transformValue) {
  const text = String(transformValue || '').trim()
  if (!text || text === 'none') return 0
  if (text.startsWith('matrix3d(')) {
    const values = text
      .slice(9, -1)
      .split(',')
      .map((x) => Number(String(x).trim()))
    return Number.isFinite(values[12]) ? values[12] : 0
  }
  if (text.startsWith('matrix(')) {
    const values = text
      .slice(7, -1)
      .split(',')
      .map((x) => Number(String(x).trim()))
    return Number.isFinite(values[4]) ? values[4] : 0
  }
  return 0
}

function readTabsNavMetrics() {
  const host = resolveTabsNavWrapElement()
  const nav = resolveTabsNavElement()
  if (!(host instanceof HTMLElement) || !(nav instanceof HTMLElement)) return null
  const hostRect = host.getBoundingClientRect()
  const navRect = nav.getBoundingClientRect()
  const hostWidth = Math.max(0, hostRect.width)
  const navWidth = Math.max(0, navRect.width)
  const maxOffset = Math.max(0, navWidth - hostWidth)
  const translateX = parseTranslateX(window.getComputedStyle(nav).transform)
  const offset = Math.max(0, Math.min(maxOffset, -translateX))
  const nativeOffset = Math.max(0, Number(host.scrollLeft || 0))
  const nativeMaxOffset = Math.max(0, Number(host.scrollWidth || 0) - Number(host.clientWidth || 0))
  const epsilon = 0.25
  const leftOffset = Math.max(offset, nativeOffset)
  const rightRemain = Math.max(maxOffset - offset, nativeMaxOffset - nativeOffset)
  return {
    host,
    nav,
    hostWidth,
    navWidth,
    maxOffset,
    offset,
    nativeOffset,
    nativeMaxOffset,
    scrollable: maxOffset > epsilon || nativeMaxOffset > epsilon,
    canPrev: leftOffset > epsilon,
    canNext: rightRemain > epsilon
  }
}

function updateTabsScrollState() {
  const header = resolveTabsHeaderElement()
  const metrics = readTabsNavMetrics()
  const classScrollable = header instanceof HTMLElement && header.classList.contains('is-scrollable')
  const scrollable = Boolean((metrics && metrics.scrollable) || classScrollable)
  tabScroll.scrollable = scrollable
  if (!scrollable || !metrics) {
    tabScroll.canPrev = false
    tabScroll.canNext = false
    return
  }
  tabScroll.canPrev = metrics.canPrev
  tabScroll.canNext = metrics.canNext
}

function canTabNavScroll(direction) {
  const metrics = readTabsNavMetrics()
  if (!metrics) return false
  return direction === 'prev' ? metrics.canPrev : metrics.canNext
}

function collectTabsNavItems() {
  const root = routeTabsWrapRef.value
  if (!(root instanceof HTMLElement)) return []
  return Array.from(root.querySelectorAll('.route-tabs .el-tabs__item[role="tab"]')).filter(
    (item) => item instanceof HTMLElement
  )
}

function resolveTabNodeByPath(path) {
  const targetPath = String(path || '').trim()
  if (!targetPath) return null
  const nodes = collectTabsNavItems()
  const index = routeTabs.value.findIndex((item) => item.path === targetPath)
  if (index < 0 || index >= nodes.length) return null
  const node = nodes[index]
  return node instanceof HTMLElement ? node : null
}

function computeTabSideReserve(path, side, hostWidth) {
  const targetPath = String(path || '').trim()
  if (!targetPath) return 0
  const nodes = collectTabsNavItems()
  const index = routeTabs.value.findIndex((item) => item.path === targetPath)
  if (index < 0 || index >= nodes.length) return 0
  const direction = side === 'left' ? -1 : 1
  let i = index + direction
  let count = 0
  let widthTotal = 0
  while (i >= 0 && i < nodes.length) {
    const node = nodes[i]
    if (!(node instanceof HTMLElement)) break
    const rect = node.getBoundingClientRect()
    widthTotal += rect.width + 8
    count += 1
    if (count >= 4 || widthTotal >= hostWidth * 0.78) break
    i += direction
  }
  if (!count || widthTotal < 0.5) return 0
  const widthFactor = Math.max(0, Math.min(1, widthTotal / Math.max(1, hostWidth)))
  const countFactor = Math.max(0, Math.min(1, count / 4))
  const reserveRatio = 0.08 + widthFactor * 0.18 + countFactor * 0.1
  return Math.max(0, Math.min(hostWidth * 0.34, hostWidth * reserveRatio))
}

function computeTabsStepByVisibleWidth(direction, hostRect, mode = 'edge') {
  const nodes = collectTabsNavItems()
  if (!nodes.length) return 0
  if (direction === 'next') {
    for (const node of nodes) {
      if (!(node instanceof HTMLElement)) continue
      const rect = node.getBoundingClientRect()
      if (rect.right <= hostRect.right + 0.5) continue
      const overflow = Math.max(0, rect.right - hostRect.right)
      const reveal = Math.max(0, Math.min(rect.width * 0.45, 26))
      return overflow + reveal
    }
    return 0
  }
  for (let i = nodes.length - 1; i >= 0; i -= 1) {
    const node = nodes[i]
    if (!(node instanceof HTMLElement)) continue
    const rect = node.getBoundingClientRect()
    if (rect.left >= hostRect.left - 0.5) continue
    const overflow = Math.max(0, hostRect.left - rect.left)
    const reveal = Math.max(0, Math.min(rect.width * 0.45, 26))
    return overflow + reveal
  }
  if (mode === 'drag') {
    return Math.max(TAB_DRAG_SCROLL_MIN_STEP, Math.round(hostRect.width * 0.08))
  }
  return 0
}

function dispatchTabsWheelDelta(deltaX) {
  const nav = resolveTabsNavElement()
  if (!(nav instanceof HTMLElement)) return false
  const delta = Number(deltaX || 0)
  if (Math.abs(delta) < 0.5) return false
  nav.dispatchEvent(
    new WheelEvent('wheel', {
      deltaX: delta,
      deltaY: 0,
      bubbles: true,
      cancelable: true
    })
  )
  return true
}

function scrollTabsToOffset(targetOffset) {
  const metrics = readTabsNavMetrics()
  if (!metrics || !metrics.scrollable) return false
  const goal = Math.max(0, Math.min(metrics.maxOffset, Number(targetOffset || 0)))
  const delta = goal - metrics.offset
  if (Math.abs(delta) < 0.5) return false
  const moved = dispatchTabsWheelDelta(delta)
  if (moved) {
    scheduleTabsScrollStateSync()
  }
  return moved
}

function scrollTabsBySmallStep(direction, mode = 'edge', intensity = 1) {
  const metrics = readTabsNavMetrics()
  if (!metrics || !metrics.scrollable) return false
  if (direction === 'prev' && !metrics.canPrev) return false
  if (direction === 'next' && !metrics.canNext) return false
  const hostRect = metrics.host.getBoundingClientRect()
  const computedStep = computeTabsStepByVisibleWidth(direction, hostRect, mode)
  const speed = Math.max(0, Math.min(1, Number(intensity || 0)))
  const minStep = mode === 'drag' ? TAB_DRAG_SCROLL_MIN_STEP : TAB_EDGE_SCROLL_MIN_STEP
  const maxStep = mode === 'drag' ? TAB_DRAG_SCROLL_MAX_STEP : TAB_EDGE_SCROLL_MAX_STEP
  const fallbackRaw = mode === 'drag' ? metrics.hostWidth * 0.08 : metrics.hostWidth * 0.22
  const dynamicStep = minStep + (maxStep - minStep) * speed
  let step = 0
  if (mode === 'drag') {
    step = Math.max(minStep, Math.min(maxStep, Math.round(dynamicStep)))
  } else {
    const baseStep = computedStep > 0 ? computedStep : fallbackRaw
    step = Math.max(minStep, Math.min(maxStep, Math.round(Math.max(baseStep, dynamicStep))))
  }
  const targetOffset = direction === 'prev' ? metrics.offset - step : metrics.offset + step
  return scrollTabsToOffset(targetOffset)
}

function triggerTabNavScroll(direction, intensity = tabDragAutoIntensity.value) {
  return scrollTabsBySmallStep(direction, 'drag', intensity)
}

function clearTabsScrollStateSync() {
  if (tabsScrollSyncRafId) {
    window.cancelAnimationFrame(tabsScrollSyncRafId)
    tabsScrollSyncRafId = 0
  }
  if (tabsScrollSyncTimer) {
    window.clearTimeout(tabsScrollSyncTimer)
    tabsScrollSyncTimer = 0
  }
}

function scheduleTabsScrollStateSync() {
  updateTabsScrollState()
  window.requestAnimationFrame(updateTabsScrollState)
  window.setTimeout(updateTabsScrollState, 90)
  window.setTimeout(updateTabsScrollState, 180)
  window.setTimeout(updateTabsScrollState, 320)
}

function handleTabsNavMotion() {
  scheduleTabsScrollStateSync()
}

function stopTabDragAutoScroll() {
  tabDragAutoDirection.value = ''
  tabDragAutoStallTicks.value = 0
  tabDragAutoIntensity.value = 0.5
  if (tabDragAutoTimer.value) {
    window.clearInterval(tabDragAutoTimer.value)
    tabDragAutoTimer.value = 0
  }
}

function applyTabDragAutoScroll() {
  if (!tabDragAutoDirection.value) {
    stopTabDragAutoScroll()
    return
  }
  const effectiveIntensity = Math.max(TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY, tabDragAutoIntensity.value)
  const ok = triggerTabNavScroll(tabDragAutoDirection.value, effectiveIntensity)
  if (!ok) {
    tabDragAutoStallTicks.value += 1
  } else {
    tabDragAutoStallTicks.value = 0
  }
  if (tabDragAutoStallTicks.value >= TAB_DRAG_AUTO_SCROLL_STALL_LIMIT) {
    stopTabDragAutoScroll()
    return
  }
  updateTabsScrollState()
}

function startTabDragAutoScroll(direction, intensity = 0.5) {
  const value = direction === 'prev' || direction === 'next' ? direction : ''
  if (!value) {
    stopTabDragAutoScroll()
    return
  }
  const nextIntensity = Math.max(0, Math.min(1, Number(intensity || 0)))
  tabDragAutoDirection.value = value
  tabDragAutoIntensity.value = nextIntensity
  tabDragAutoStallTicks.value = 0
  if (!tabDragAutoTimer.value) {
    tabDragAutoTimer.value = window.setInterval(applyTabDragAutoScroll, TAB_DRAG_AUTO_SCROLL_INTERVAL)
  }
  applyTabDragAutoScroll()
}

function updateTabDragAutoScrollByPointer(clientX, clientY) {
  if (!tabDragState.sourcePath) {
    stopTabDragAutoScroll()
    return
  }
  const host = tabsWheelTarget.value || resolveTabsNavWrapElement()
  if (!(host instanceof HTMLElement)) {
    stopTabDragAutoScroll()
    return
  }
  const rect = host.getBoundingClientRect()
  const x = Number(clientX || 0)
  const y = Number(clientY || 0)
  const insideVerticalBand =
    y >= rect.top - TAB_DRAG_OUTSIDE_VERTICAL_TOLERANCE &&
    y <= rect.bottom + TAB_DRAG_OUTSIDE_VERTICAL_TOLERANCE
  if (!insideVerticalBand) {
    stopTabDragAutoScroll()
    return
  }
  const canPrev = canTabNavScroll('prev')
  const canNext = canTabNavScroll('next')
  const leftOutsideDistance = Math.max(0, rect.left - x)
  if (leftOutsideDistance > 0 && canPrev) {
    const ratio = Math.max(0, Math.min(1, leftOutsideDistance / TAB_DRAG_OUTSIDE_SCROLL_DISTANCE))
    const intensity =
      TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY + (1 - TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY) * Math.pow(ratio, 1.15)
    startTabDragAutoScroll('prev', intensity)
    return
  }
  const rightOutsideDistance = Math.max(0, x - rect.right)
  if (rightOutsideDistance > 0 && canNext) {
    const ratio = Math.max(0, Math.min(1, rightOutsideDistance / TAB_DRAG_OUTSIDE_SCROLL_DISTANCE))
    const intensity =
      TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY + (1 - TAB_DRAG_AUTO_SCROLL_MIN_INTENSITY) * Math.pow(ratio, 1.15)
    startTabDragAutoScroll('next', intensity)
    return
  }
  stopTabDragAutoScroll()
}

function isPointerInsideTabsVisibleArea(clientX, clientY) {
  const host = tabsWheelTarget.value || resolveTabsNavWrapElement()
  if (!(host instanceof HTMLElement)) return true
  const rect = host.getBoundingClientRect()
  const x = Number(clientX || 0)
  const y = Number(clientY || 0)
  return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
}

function onTabEdgeScroll(direction) {
  updateTabsScrollState()
  const moved = scrollTabsBySmallStep(direction, 'edge', 1)
  scheduleTabsScrollStateSync()
  if (!moved) {
    updateTabsScrollState()
  }
}

function clearTabEdgeHoldTimers() {
  if (tabEdgeHoldStartTimer) {
    window.clearTimeout(tabEdgeHoldStartTimer)
    tabEdgeHoldStartTimer = 0
  }
  if (tabEdgeHoldTickTimer) {
    window.clearInterval(tabEdgeHoldTickTimer)
    tabEdgeHoldTickTimer = 0
  }
}

function handleTabEdgeHoldGlobalRelease() {
  onTabEdgeHoldEnd()
}

function bindTabEdgeHoldGlobalRelease() {
  if (tabEdgeHoldGlobalBound) return
  document.addEventListener('mouseup', handleTabEdgeHoldGlobalRelease)
  document.addEventListener('touchend', handleTabEdgeHoldGlobalRelease)
  document.addEventListener('touchcancel', handleTabEdgeHoldGlobalRelease)
  window.addEventListener('blur', handleTabEdgeHoldGlobalRelease)
  tabEdgeHoldGlobalBound = true
}

function unbindTabEdgeHoldGlobalRelease() {
  if (!tabEdgeHoldGlobalBound) return
  document.removeEventListener('mouseup', handleTabEdgeHoldGlobalRelease)
  document.removeEventListener('touchend', handleTabEdgeHoldGlobalRelease)
  document.removeEventListener('touchcancel', handleTabEdgeHoldGlobalRelease)
  window.removeEventListener('blur', handleTabEdgeHoldGlobalRelease)
  tabEdgeHoldGlobalBound = false
}

function applyTabEdgeHoldScroll() {
  const direction = String(tabEdgeHoldDirection.value || '')
  if (direction !== 'prev' && direction !== 'next') {
    onTabEdgeHoldEnd()
    return
  }
  updateTabsScrollState()
  const moved = scrollTabsBySmallStep(direction, 'edge', 1)
  scheduleTabsScrollStateSync()
  if (!moved) {
    onTabEdgeHoldEnd()
  }
}

function onTabEdgeHoldStart(direction) {
  const dir = String(direction || '').trim()
  if (dir !== 'prev' && dir !== 'next') return
  updateTabsScrollState()
  if (!canTabNavScroll(dir)) return
  tabEdgeHoldDirection.value = dir
  clearTabEdgeHoldTimers()
  bindTabEdgeHoldGlobalRelease()
  tabEdgeHoldStartTimer = window.setTimeout(() => {
    tabEdgeHoldStartTimer = 0
    applyTabEdgeHoldScroll()
    if (tabEdgeHoldDirection.value !== dir) return
    tabEdgeHoldTickTimer = window.setInterval(() => {
      applyTabEdgeHoldScroll()
    }, TAB_EDGE_HOLD_INTERVAL)
  }, TAB_EDGE_HOLD_START_DELAY)
}

function onTabEdgeHoldEnd() {
  tabEdgeHoldDirection.value = ''
  clearTabEdgeHoldTimers()
  unbindTabEdgeHoldGlobalRelease()
}

function bindTabsWheel() {
  unbindTabsWheel()
  const navWrap = resolveTabsNavWrapElement()
  if (!(navWrap instanceof HTMLElement)) return
  tabsWheelTarget.value = navWrap
  const nav = resolveTabsNavElement()
  if (nav instanceof HTMLElement) {
    tabsNavElement = nav
    tabsNavElement.addEventListener('wheel', handleTabsNavMotion, { passive: true })
    tabsNavElement.addEventListener('transitionend', handleTabsNavMotion)
  }
  markTabsNavCacheDirty()
  updateTabsScrollState()
  scheduleTabsScrollStateSync()
}

function unbindTabsWheel() {
  if (tabsNavElement instanceof HTMLElement) {
    tabsNavElement.removeEventListener('wheel', handleTabsNavMotion)
    tabsNavElement.removeEventListener('transitionend', handleTabsNavMotion)
  }
  tabsNavElement = null
  clearTabsScrollStateSync()
  onTabEdgeHoldEnd()
  stopTabDragAutoScroll()
  tabsWheelTarget.value = null
  markTabsNavCacheDirty()
  tabScroll.scrollable = false
  tabScroll.canPrev = false
  tabScroll.canNext = false
}

function updateDragSourceTabVisual() {
  const nodes = collectTabsNavItems()
  nodes.forEach((node) => {
    if (node instanceof HTMLElement) {
      node.classList.remove('is-drag-source')
    }
  })
  if (!tabDragState.sourcePath) return
  const sourceNode = resolveTabNodeByPath(tabDragState.sourcePath)
  if (sourceNode instanceof HTMLElement) {
    sourceNode.classList.add('is-drag-source')
  }
}

function setTabDraggingCursor(active) {
  const root = document.documentElement
  const body = document.body
  if (active) {
    if (root) root.classList.add('tab-dragging-cursor')
    if (body) body.classList.add('tab-dragging-cursor')
    return
  }
  if (root) root.classList.remove('tab-dragging-cursor')
  if (body) body.classList.remove('tab-dragging-cursor')
}

function clearTabDragState() {
  const shouldPersistAfterClear = routeTabsDirtyDuringDrag
  stopTabDragAutoScroll()
  setTabDraggingCursor(false)
  tabDragState.sourcePath = ''
  tabDragState.overPath = ''
  tabDragState.overPlacement = 'before'
  tabDragPreviewKey.value = ''
  tabDragPreviewAt.value = 0
  tabDragPreviewMoved.value = false
  tabDragHoverPath.value = ''
  tabDragHoverAt.value = 0
  tabDragDropHandled.value = false
  tabDragPreviewNeedPointerMove.value = false
  tabDragLastPreviewPointer.x = 0
  tabDragLastPreviewPointer.y = 0
  updateDragSourceTabVisual()
  if (shouldPersistAfterClear) {
    routeTabsDirtyDuringDrag = false
    scheduleRouteTabsStatePersist(true)
  }
}

function collectTabRectsByPath() {
  const root = routeTabsWrapRef.value
  if (!(root instanceof HTMLElement)) return new Map()
  const nodes = collectTabsNavItems()
  const records = new Map()
  nodes.forEach((node, index) => {
    if (!(node instanceof HTMLElement)) return
    const tab = routeTabs.value[index]
    if (!tab || !tab.path) return
    const rect = node.getBoundingClientRect()
    records.set(String(tab.path), { left: rect.left, top: rect.top })
  })
  return records
}

function animateTabReorder(previousRects) {
  if (!(previousRects instanceof Map) || !previousRects.size) return
  nextTick(() => {
    const nodes = collectTabsNavItems()
    if (!nodes.length) return
    nodes.forEach((node, index) => {
      if (!(node instanceof HTMLElement)) return
      const tab = routeTabs.value[index]
      if (!tab || !tab.path) return
      const before = previousRects.get(String(tab.path))
      if (!before) return
      const rect = node.getBoundingClientRect()
      const deltaX = before.left - rect.left
      const deltaY = before.top - rect.top
      if (Math.abs(deltaX) < 0.5 && Math.abs(deltaY) < 0.5) return
      if (typeof node.animate === 'function') {
        node.animate(
          [
            { transform: `translate(${Math.round(deltaX)}px, ${Math.round(deltaY)}px)` },
            { transform: 'translate(0, 0)' }
          ],
          { duration: TAB_REORDER_ANIMATION_MS, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' }
        )
        return
      }
      node.style.transition = 'none'
      node.style.transform = `translate(${deltaX}px, ${deltaY}px)`
      void node.offsetWidth
      node.style.transition = `transform ${TAB_REORDER_ANIMATION_MS}ms cubic-bezier(0.22, 1, 0.36, 1)`
      node.style.transform = 'translate(0, 0)'
      window.setTimeout(() => {
        node.style.transition = ''
        node.style.transform = ''
      }, TAB_REORDER_ANIMATION_MS + 20)
    })
    updateTabsScrollState()
  })
}

function reorderTabsByPath(sourcePath, targetPath, placement = 'before') {
  const from = String(sourcePath || '').trim()
  const to = String(targetPath || '').trim()
  if (!from || !to || from === to) return false
  const shouldAnimate = routeTabs.value.length <= TAB_REORDER_ANIMATION_NODE_LIMIT
  const previousRects = shouldAnimate ? collectTabRectsByPath() : null
  const records = routeTabs.value.slice()
  const sourceIndex = routeTabPathIndexMap.value.get(from)
  const targetIndex = routeTabPathIndexMap.value.get(to)
  if (!Number.isInteger(sourceIndex) || !Number.isInteger(targetIndex) || sourceIndex < 0 || targetIndex < 0) {
    return false
  }
  if (!records[sourceIndex] || !records[sourceIndex].closable) return false
  if (!records[targetIndex] || !records[targetIndex].closable) return false
  let insertIndex = targetIndex
  if (placement === 'after') {
    insertIndex = targetIndex + 1
  }
  const [current] = records.splice(sourceIndex, 1)
  if (!current) return false
  if (sourceIndex < insertIndex) {
    insertIndex -= 1
  }
  const safeIndex = Math.max(0, Math.min(records.length, insertIndex))
  if (safeIndex === sourceIndex) {
    return false
  }
  records.splice(safeIndex, 0, current)
  if (tabDragState.sourcePath) {
    routeTabsDirtyDuringDrag = true
  }
  routeTabs.value = records
  markTabsNavCacheDirty()
  if (shouldAnimate) {
    animateTabReorder(previousRects)
  } else {
    scheduleTabsScrollStateSync()
  }
  return true
}

function setTabDragGhost(event, tab) {
  if (!event || !tab) return
  const transfer = event.dataTransfer
  if (!transfer || typeof transfer.setDragImage !== 'function') return
  const rawTarget = event.target
  const sourceTabNode =
    rawTarget instanceof HTMLElement ? rawTarget.closest('.route-tabs .el-tabs__item[role="tab"]') : null
  const titleText = String(tab.title || '').trim()
  let width = 160
  let height = 32
  let ghost = null
  if (sourceTabNode instanceof HTMLElement) {
    const rect = sourceTabNode.getBoundingClientRect()
    width = Math.max(1, Math.round(rect.width))
    height = Math.max(1, Math.round(rect.height))
    ghost = sourceTabNode.cloneNode(true)
    if (ghost instanceof HTMLElement) {
      const sourceStyle = window.getComputedStyle(sourceTabNode)
      ghost.classList.add('tab-drag-ghost', 'is-tab-node-ghost')
      ghost.style.width = `${width}px`
      ghost.style.height = `${height}px`
      ghost.style.boxSizing = 'border-box'
      ghost.style.border = sourceStyle.border
      ghost.style.borderRadius = sourceStyle.borderRadius
      ghost.style.backgroundColor = sourceStyle.backgroundColor
      ghost.style.color = sourceStyle.color
      ghost.style.transform = 'none'
    }
  }
  if (!(ghost instanceof HTMLElement)) {
    width = Math.max(160, Math.min(420, Math.round(titleText.length * 12 + 44)))
    height = 32
    ghost = document.createElement('div')
    ghost.className = 'tab-drag-ghost'
    ghost.style.width = `${width}px`
    ghost.style.height = `${height}px`
    const title = document.createElement('span')
    title.className = 'tab-drag-ghost-title'
    title.textContent = titleText
    ghost.appendChild(title)
  }
  document.body.appendChild(ghost)
  const offsetX = Math.max(8, Math.min(width - 8, Math.round(width * 0.22)))
  const offsetY = Math.max(8, Math.min(height - 8, Math.round(height * 0.48)))
  transfer.setDragImage(ghost, offsetX, offsetY)
  window.setTimeout(() => {
    if (ghost.parentNode) {
      ghost.parentNode.removeChild(ghost)
    }
  }, 0)
}

function onTabDragStart(event, tab) {
  if (!tab || !tab.closable || !event) return
  setTabDraggingCursor(true)
  routeTabsDirtyDuringDrag = false
  tabDragState.sourcePath = String(tab.path || '')
  tabDragState.overPath = ''
  tabDragState.overPlacement = 'before'
  tabDragPreviewKey.value = ''
  tabDragPreviewAt.value = 0
  tabDragPreviewMoved.value = false
  tabDragHoverPath.value = ''
  tabDragHoverAt.value = 0
  tabDragDropHandled.value = false
  tabDragPreviewNeedPointerMove.value = false
  tabDragLastPreviewPointer.x = Number(event.clientX || 0)
  tabDragLastPreviewPointer.y = Number(event.clientY || 0)
  const transfer = event.dataTransfer
  if (transfer) {
    transfer.effectAllowed = 'move'
    transfer.setData('text/plain', tabDragState.sourcePath)
  }
  setTabDragGhost(event, tab)
  nextTick(updateDragSourceTabVisual)
}

function onTabDragEnd() {
  const sourcePath = String(tabDragState.sourcePath || '')
  const didPreviewReorder = tabDragPreviewMoved.value
  const dropHandled = tabDragDropHandled.value
  clearTabDragState()
  if (!dropHandled && didPreviewReorder && sourcePath) {
    tabClickSuppressUntil.value = Date.now() + TAB_REORDER_ANIMATION_MS + 40
    window.setTimeout(() => {
      scrollTabIntoViewByPath(sourcePath, { margin: 10 })
    }, TAB_REORDER_ANIMATION_MS + 16)
    scheduleTabActivation(sourcePath, 0)
  }
}

function resolveTabDropTargetByPoint(clientX, clientY) {
  const tabRecords = collectTabsNavItems()
    .map((item, index) => ({ item, tab: routeTabs.value[index] }))
    .filter((record) => record.item instanceof HTMLElement && record.tab && record.tab.closable)
  if (!tabRecords.length) return null
  const x = Number(clientX || 0)
  const y = Number(clientY || 0)
  for (const record of tabRecords) {
    const rect = record.item.getBoundingClientRect()
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
      const sourceIndex = routeTabs.value.findIndex((item) => item.path === tabDragState.sourcePath)
      const targetIndex = routeTabs.value.findIndex((item) => item.path === String(record.tab.path || ''))
      const placement = sourceIndex >= 0 && targetIndex >= 0 && sourceIndex < targetIndex ? 'after' : 'before'
      return {
        path: String(record.tab.path || ''),
        placement
      }
    }
  }
  const firstRect = tabRecords[0].item.getBoundingClientRect()
  const lastRect = tabRecords[tabRecords.length - 1].item.getBoundingClientRect()
  if (x <= firstRect.left) {
    return { path: String(tabRecords[0].tab.path || ''), placement: 'before' }
  }
  if (x >= lastRect.right) {
    return { path: String(tabRecords[tabRecords.length - 1].tab.path || ''), placement: 'after' }
  }

  let closest = tabRecords[0]
  let minDistance = Number.POSITIVE_INFINITY
  for (const record of tabRecords) {
    const rect = record.item.getBoundingClientRect()
    const center = rect.left + rect.width / 2
    const distance = Math.abs(x - center)
    if (distance < minDistance) {
      minDistance = distance
      closest = record
    }
  }
  const sourceIndex = routeTabs.value.findIndex((item) => item.path === tabDragState.sourcePath)
  const closestIndex = routeTabs.value.findIndex((item) => item.path === String(closest.tab.path || ''))
  const closestPlacement = sourceIndex >= 0 && closestIndex >= 0 && sourceIndex < closestIndex ? 'after' : 'before'
  return {
    path: String(closest.tab.path || ''),
    placement: closestPlacement
  }
}

function resolveDragPlacementByOrder(sourcePath, targetPath) {
  const from = String(sourcePath || '').trim()
  const to = String(targetPath || '').trim()
  if (!from || !to || from === to) return ''
  const sourceIndex = routeTabs.value.findIndex((item) => item.path === from)
  const targetIndex = routeTabs.value.findIndex((item) => item.path === to)
  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return ''
  return sourceIndex < targetIndex ? 'after' : 'before'
}

function resolveHoverTabPathByPoint(clientX, clientY) {
  const tabRecords = collectTabsNavItems()
    .map((item, index) => ({ item, tab: routeTabs.value[index] }))
    .filter((record) => record.item instanceof HTMLElement && record.tab && record.tab.closable)
  if (!tabRecords.length) return ''
  const x = Number(clientX || 0)
  const y = Number(clientY || 0)
  for (const record of tabRecords) {
    const rect = record.item.getBoundingClientRect()
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
      return String(record.tab.path || '')
    }
  }
  return ''
}

function maybeApplyTabPreviewReorder(targetPath, pointerX, pointerY) {
  const target = String(targetPath || '').trim()
  if (!target || !tabDragState.sourcePath || target === tabDragState.sourcePath) return false
  if (tabDragPreviewNeedPointerMove.value) {
    const hasPointer = Number.isFinite(Number(pointerX)) && Number.isFinite(Number(pointerY))
    if (!hasPointer) return false
    const x = Number(pointerX)
    const y = Number(pointerY)
    const dx = x - Number(tabDragLastPreviewPointer.x || 0)
    const dy = y - Number(tabDragLastPreviewPointer.y || 0)
    if (Math.hypot(dx, dy) < TAB_DRAG_PREVIEW_REARM_DISTANCE) {
      return false
    }
    tabDragPreviewNeedPointerMove.value = false
  }
  const now = Date.now()
  if (tabDragHoverPath.value !== target) {
    tabDragHoverPath.value = target
    tabDragHoverAt.value = now
    return false
  }
  if (now - tabDragHoverAt.value < TAB_DRAG_PREVIEW_HOVER_DELAY) {
    return false
  }
  const placement = resolveDragPlacementByOrder(tabDragState.sourcePath, target)
  if (!placement) return false
  const key = `${tabDragState.sourcePath}->${target}:${placement}`
  if (tabDragPreviewKey.value === key && now - tabDragPreviewAt.value < TAB_DRAG_PREVIEW_REORDER_INTERVAL) {
    return false
  }
  if (now - tabDragPreviewAt.value < TAB_DRAG_PREVIEW_REORDER_INTERVAL) {
    return false
  }
  tabDragPreviewAt.value = now
  tabDragPreviewKey.value = key
  const didReorder = reorderTabsByPath(tabDragState.sourcePath, target, placement)
  if (didReorder) {
    tabDragPreviewMoved.value = true
    tabDragPreviewNeedPointerMove.value = true
    tabDragLastPreviewPointer.x = Number(pointerX || 0)
    tabDragLastPreviewPointer.y = Number(pointerY || 0)
    tabClickSuppressUntil.value = Date.now() + TAB_REORDER_ANIMATION_MS + 40
    tabDragState.overPath = ''
    tabDragHoverPath.value = ''
    tabDragHoverAt.value = 0
    nextTick(updateDragSourceTabVisual)
  }
  return didReorder
}

function onTabsAreaDragOver(event) {
  if (!tabDragState.sourcePath || !event) return
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  updateTabDragAutoScrollByPointer(event.clientX, event.clientY)
  if (!isPointerInsideTabsVisibleArea(event.clientX, event.clientY)) {
    tabDragState.overPath = ''
    tabDragState.overPlacement = 'before'
    tabDragHoverPath.value = ''
    tabDragHoverAt.value = 0
    return
  }
  const hoverPath = resolveHoverTabPathByPoint(event.clientX, event.clientY)
  if (!hoverPath || hoverPath === tabDragState.sourcePath) {
    tabDragState.overPath = ''
    tabDragState.overPlacement = 'before'
    tabDragHoverPath.value = ''
    tabDragHoverAt.value = 0
    return
  }
  tabDragState.overPath = hoverPath
  tabDragState.overPlacement = resolveDragPlacementByOrder(tabDragState.sourcePath, hoverPath) === 'after' ? 'after' : 'before'
  maybeApplyTabPreviewReorder(hoverPath, event.clientX, event.clientY)
}

function onTabsAreaDrop(event) {
  if (event) {
    event.preventDefault()
  }
  if (!tabDragState.sourcePath) {
    clearTabDragState()
    return
  }
  tabDragDropHandled.value = true
  const sourcePath = tabDragState.sourcePath
  tabClickSuppressUntil.value = Date.now() + TAB_REORDER_ANIMATION_MS + 40
  const target = event ? resolveTabDropTargetByPoint(event.clientX, event.clientY) : null
  let didReorder = tabDragPreviewMoved.value
  if (!didReorder && target && target.path && target.path !== tabDragState.sourcePath) {
    const finalReorder = reorderTabsByPath(tabDragState.sourcePath, target.path, target.placement)
    didReorder = didReorder || finalReorder
  }
  clearTabDragState()
  if (didReorder) {
    window.setTimeout(() => {
      scrollTabIntoViewByPath(sourcePath, { margin: 10 })
    }, TAB_REORDER_ANIMATION_MS + 16)
  }
  scheduleTabActivation(sourcePath, 0)
}

function scrollTabIntoViewByPath(path, options = {}) {
  const targetPath = String(path || '').trim()
  if (!targetPath) return
  const mode = String(options.mode || 'active')
  const margin = Math.max(6, Number(options.margin || 10))
  const maxRetries = Math.max(0, Number(options.maxRetries || 0))
  const retryCount = Math.max(0, Number(options.retryCount || 0))
  const retryDelay = Math.max(60, Number(options.retryDelay || 90))
  const taskId = Number(options.taskId || 0)
  nextTick(() => {
    if (taskId && taskId !== tabActiveScrollTaskId) return
    const scheduleRetry = () => {
      if (taskId && taskId !== tabActiveScrollTaskId) return
      if (retryCount >= maxRetries) return
      window.setTimeout(() => {
        if (taskId && taskId !== tabActiveScrollTaskId) return
        scrollTabIntoViewByPath(targetPath, {
          ...options,
          retryCount: retryCount + 1
        })
      }, retryDelay + retryCount * 70)
    }
    const resolveVisibleRange = (hostRect, hostWidth) => {
      const leftReserve = mode === 'click' ? computeTabSideReserve(targetPath, 'left', hostWidth) : 0
      const rightReserve = mode === 'click' ? computeTabSideReserve(targetPath, 'right', hostWidth) : 0
      const reserveTotal = Math.max(0, leftReserve + rightReserve)
      const reserveScale = reserveTotal > hostWidth * 0.52 ? (hostWidth * 0.52) / reserveTotal : 1
      const leftGap = leftReserve * reserveScale
      const rightGap = rightReserve * reserveScale
      let visibleLeft = hostRect.left + margin + leftGap
      let visibleRight = hostRect.right - margin - rightGap
      if (visibleRight - visibleLeft < 16) {
        const center = (hostRect.left + hostRect.right) / 2
        visibleLeft = center - 8
        visibleRight = center + 8
      }
      return { visibleLeft, visibleRight }
    }
    const tabNode = resolveTabNodeByPath(targetPath)
    const metrics = readTabsNavMetrics()
    if (!(tabNode instanceof HTMLElement) || !metrics || !metrics.scrollable) {
      scheduleRetry()
      scheduleTabsScrollStateSync()
      return
    }
    const hostRect = metrics.host.getBoundingClientRect()
    const { visibleLeft, visibleRight } = resolveVisibleRange(hostRect, metrics.hostWidth)
    const tabRect = tabNode.getBoundingClientRect()
    const overflowLeft = visibleLeft - tabRect.left
    const overflowRight = tabRect.right - visibleRight
    let targetOffset = metrics.offset
    if (overflowLeft > 0.5) {
      targetOffset = metrics.offset - overflowLeft
    } else if (overflowRight > 0.5) {
      targetOffset = metrics.offset + overflowRight
    }
    const needMove = overflowLeft > 0.5 || overflowRight > 0.5
    if (needMove) {
      scrollTabsToOffset(targetOffset)
    }
    if (retryCount < maxRetries || needMove) {
      window.setTimeout(() => {
        if (taskId && taskId !== tabActiveScrollTaskId) return
        const latestNode = resolveTabNodeByPath(targetPath)
        const latestMetrics = readTabsNavMetrics()
        if (!(latestNode instanceof HTMLElement) || !latestMetrics || !latestMetrics.scrollable) {
          scheduleRetry()
          return
        }
        const latestHostRect = latestMetrics.host.getBoundingClientRect()
        const latestVisibleRange = resolveVisibleRange(latestHostRect, latestMetrics.hostWidth)
        const latestRect = latestNode.getBoundingClientRect()
        const stillHidden =
          latestRect.left < latestVisibleRange.visibleLeft - 0.5 ||
          latestRect.right > latestVisibleRange.visibleRight + 0.5
        if (stillHidden) {
          scheduleRetry()
        }
      }, Math.max(90, retryDelay))
    }
    scheduleTabsScrollStateSync()
  })
}

function scrollActiveTabIntoView() {
  const targetPath = String(activeTabPath.value || '').trim()
  if (!targetPath) {
    scheduleTabsScrollStateSync()
    return
  }
  tabActiveScrollTaskId += 1
  scrollTabIntoViewByPath(targetPath, {
    margin: 10,
    mode: 'active',
    maxRetries: 8,
    retryDelay: 100,
    retryCount: 0,
    taskId: tabActiveScrollTaskId
  })
}

function loadLayoutSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(LAYOUT_SETTINGS_KEY) || '{}')
    return { ...defaultLayoutSettings, ...saved }
  } catch (error) {
    return { ...defaultLayoutSettings }
  }
}

function colorChannel(hex, start) {
  return parseInt(hex.slice(start, start + 2), 16)
}

function toHex(value) {
  return Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')
}

function mixColor(hex, targetHex, ratio) {
  const x = String(hex || '').replace('#', '')
  const y = String(targetHex || '').replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(x) || !/^[0-9a-fA-F]{6}$/.test(y)) {
    return '#1667b7'
  }
  const r = colorChannel(x, 0) * (1 - ratio) + colorChannel(y, 0) * ratio
  const g = colorChannel(x, 2) * (1 - ratio) + colorChannel(y, 2) * ratio
  const b = colorChannel(x, 4) * (1 - ratio) + colorChannel(y, 4) * ratio
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`
}

function applyThemeVars(primary) {
  const color = typeof primary === 'string' ? primary : defaultLayoutSettings.primaryColor
  const root = document.documentElement
  root.style.setProperty('--primary', color)
  root.style.setProperty('--primary-strong', mixColor(color, '#000000', 0.24))
  root.style.setProperty('--primary-soft', mixColor(color, '#ffffff', 0.88))
  root.style.setProperty('--el-color-primary', color)
}

function applyThemeMode(darkMode) {
  const root = document.documentElement
  root.setAttribute('data-theme', darkMode ? 'dark' : 'light')
}

function toggleDarkMode() {
  layoutSettings.darkMode = !layoutSettings.darkMode
}

function resetSettings() {
  layoutSettings.showTabs = defaultLayoutSettings.showTabs
  layoutSettings.routeAnim = defaultLayoutSettings.routeAnim
  layoutSettings.darkMode = defaultLayoutSettings.darkMode
  layoutSettings.primaryColor = defaultLayoutSettings.primaryColor
  applyThemeMode(layoutSettings.darkMode)
  applyThemeVars(layoutSettings.primaryColor)
}

function handleFullscreenChange() {
  // reserved for future indicator
}

function handleBeforeUnload() {
  flushRouteTabsStatePersist()
  localStorage.setItem(ACTIVE_TAB_PATH_KEY, String(activeTabPath.value || DEFAULT_TAB))
}

function handleWindowResize() {
  hideTabContextMenu()
  const nextMobile = window.innerWidth <= MOBILE_BREAKPOINT
  if (nextMobile !== isMobileViewport.value) {
    isMobileViewport.value = nextMobile
    mobileMenuVisible.value = false
  }
  if (resizeRafId) {
    window.cancelAnimationFrame(resizeRafId)
  }
  resizeRafId = window.requestAnimationFrame(() => {
    resizeRafId = 0
    updateTabsScrollState()
    scrollActiveTabIntoView()
  })
}

function handleGlobalMouseUp() {
  if (!tabDragState.sourcePath) {
    setTabDraggingCursor(false)
  }
}

async function toggleFullScreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await document.documentElement.requestFullscreen()
    }
  } catch (error) {
    ElMessage.warning('当前环境不支持全屏切换')
  }
}

watch(
  () => route.fullPath,
  () => {
    syncTabsWithRoute(route)
    hideTabContextMenu()
    if (isMobileViewport.value) {
      mobileMenuVisible.value = false
    }
  },
  { immediate: true }
)

watch(
  () => routeTabs.value,
  () => {
    markTabsNavCacheDirty()
    if (tabDragState.sourcePath) {
      routeTabsDirtyDuringDrag = true
      nextTick(() => {
        scheduleTabsScrollStateSync()
        updateDragSourceTabVisual()
      })
      return
    }
    routeTabsDirtyDuringDrag = false
    scheduleRouteTabsStatePersist(false)
    nextTick(() => {
      bindTabsWheel()
      scrollActiveTabIntoView()
    })
  },
  { deep: true }
)

watch(
  () => activeTabPath.value,
  (next) => {
    localStorage.setItem(ACTIVE_TAB_PATH_KEY, String(next || DEFAULT_TAB))
    scrollActiveTabIntoView()
  },
  { immediate: true }
)

watch(
  () => layoutSettings.showTabs,
  (visible) => {
    if (!visible) {
      onTabEdgeHoldEnd()
      unbindTabsWheel()
      clearTabDragState()
      hideTabContextMenu()
      return
    }
    nextTick(() => {
      bindTabsWheel()
      scrollActiveTabIntoView()
    })
  },
  { immediate: true }
)

watch(
  () => menuCollapsed.value,
  (next) => {
    localStorage.setItem(MENU_COLLAPSED_KEY, next ? '1' : '0')
  }
)

watch(
  () => layoutSettings.primaryColor,
  (next) => {
    applyThemeVars(next)
  },
  { immediate: true }
)

watch(
  () => layoutSettings.darkMode,
  (next) => {
    applyThemeMode(!!next)
  },
  { immediate: true }
)

watch(
  () => ({ ...layoutSettings }),
  (next) => {
    localStorage.setItem(LAYOUT_SETTINGS_KEY, JSON.stringify(next))
  },
  { deep: true }
)

watch(
  () => tabSortMode.value,
  (mode) => {
    tabSortPage.value = 1
    if (mode === 'drag') {
      tabSortKeyword.value = ''
    }
    refreshTabSortPageRows()
  }
)

watch(
  () => tabSortKeyword.value,
  () => {
    if (tabSortMode.value !== 'button') return
    tabSortPage.value = 1
    syncTabSortPage()
  }
)

watch(
  () => tabSortPageTotalRecords.value,
  () => {
    syncTabSortPage()
    if (tabSortMode.value === 'drag') {
      refreshTabSortPageRows()
    }
  }
)

watch(
  () => tabSortCenterVisible.value,
  (visible) => {
    if (visible) return
    document.removeEventListener('drag', detectTabSortTurnZone)
    document.removeEventListener('dragover', detectTabSortTurnZone)
    document.removeEventListener('mousemove', detectTabSortTurnZone)
    document.removeEventListener('touchmove', detectTabSortTurnZone)
    clearTabSortDragState()
  }
)

onMounted(() => {
  isMobileViewport.value = window.innerWidth <= MOBILE_BREAKPOINT
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('click', hideTabContextMenu)
  document.addEventListener('mouseup', handleGlobalMouseUp)
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('resize', handleWindowResize)
  window.addEventListener('dahe-user-updated', onUserUpdated)
  window.addEventListener(NOTICE_UPDATED_EVENT, onAdminNoticeUpdated)
  loadNoticeCenter()
  syncTerminologyCache(false)
  nextTick(() => {
    bindTabsWheel()
    handleWindowResize()
    window.setTimeout(handleWindowResize, 120)
  })
})

onUnmounted(() => {
  onTabEdgeHoldEnd()
  clearRouteTabsPersistTimer()
  clearTabsScrollStateSync()
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('click', hideTabContextMenu)
  document.removeEventListener('mouseup', handleGlobalMouseUp)
  document.removeEventListener('dragover', detectTabSortTurnZone)
  document.removeEventListener('mousemove', detectTabSortTurnZone)
  document.removeEventListener('touchmove', detectTabSortTurnZone)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('resize', handleWindowResize)
  window.removeEventListener('dahe-user-updated', onUserUpdated)
  window.removeEventListener(NOTICE_UPDATED_EVENT, onAdminNoticeUpdated)
  if (tabActivateTimer.value) {
    window.clearTimeout(tabActivateTimer.value)
    tabActivateTimer.value = 0
  }
  if (resizeRafId) {
    window.cancelAnimationFrame(resizeRafId)
    resizeRafId = 0
  }
  unbindTabsWheel()
  clearTabDragState()
  clearTabSortDragState()
})
</script>
