<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      title="消息通知"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="toolbar-wrap">
      <view class="toolbar">
        <view class="toolbar-left">
          <text class="toolbar-summary">未读 {{ unreadCount }} 条</text>
          <text v-if="selectMode" class="toolbar-summary">已选 {{ selectedIds.length }} 条</text>
        </view>
        <view class="toolbar-actions">
          <t-button
            v-if="!selectMode"
            theme="primary"
            variant="outline"
            :size="actionButtonSize"
            :disabled="syncing || !unreadCount"
            @click="markAllRead"
          >
            全部已读
          </t-button>
          <t-button
            v-if="!selectMode"
            theme="default"
            variant="outline"
            :size="actionButtonSize"
            :disabled="syncing || !rows.length"
            @click="enterSelectMode"
          >
            选择删除
          </t-button>
          <t-button
            v-if="selectMode"
            theme="danger"
            :size="actionButtonSize"
            :loading="syncing"
            :disabled="syncing || !selectedIds.length"
            @click="deleteSelected"
          >
            删除选中
          </t-button>
          <t-button
            v-if="selectMode"
            theme="default"
            variant="outline"
            :size="actionButtonSize"
            :disabled="syncing"
            @click="exitSelectMode"
          >
            完成
          </t-button>
        </view>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="list"
      :show-scrollbar="false"
      :enable-back-to-top="true"
      enhanced
      :fast-deceleration="true"
      :bounces="true"
      @scrolltolower="loadMore"
    >
      <view class="list-inner">
        <dh-reveal-panel :loading="loading && !rows.length" class="list-reveal-panel">
          <template #skeleton>
            <view class="list-card-skeleton">
              <dh-card-skeleton-list
                compact
                :count="5"
                item-min-height="156rpx"
                body-min-height="116rpx"
                item-padding="18rpx"
                item-radius="18rpx"
                :row-col="noticeSkeletonRows"
              />
            </view>
          </template>

          <template v-if="rows.length">
            <view
              v-for="item in rows"
              :key="item.id"
              class="card"
              :class="{
                unread: Number(item.isRead) !== 1,
                'is-selectable': selectMode,
                'is-selected': isSelected(item.id)
              }"
              @click="handleCardClick(item)"
            >
              <view class="line between head-line">
                <view class="card-head-main">
                  <text class="name">{{ item.title || '-' }}</text>
                  <text v-if="selectMode && isSelected(item.id)" class="selected-flag">已选</text>
                </view>
                <text class="type">{{ noticeTypeText(item.noticeType) }}</text>
              </view>
              <text class="content">{{ item.content || '暂无内容' }}</text>
              <view class="line between meta-line">
                <text class="time">{{ item.createdAt || '-' }}</text>
                <text v-if="Number(item.isRead) !== 1" class="dot">未读</text>
              </view>
            </view>
          </template>
          <view v-else class="empty">暂无消息</view>
        </dh-reveal-panel>

        <view v-if="loading && rows.length" class="foot foot-skeleton">
          <dh-card-skeleton-list compact :count="1" :row-col="[1]" />
        </view>
        <view v-else-if="rows.length && rows.length >= total" class="foot">没有更多了</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isApprovedUser } from '../../utils/auth'
import {
  getStoredNoticeUnreadCount,
  setStoredNoticeUnreadCount
} from '../../utils/notice'
import DhCardSkeletonList from '../../components/dh-card-skeleton-list.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import elderPageMixin from '../../utils/elder-page'

const NOTICE_SKELETON_ROWS = [
  [
    { width: '58%', height: '30rpx', borderRadius: '12rpx', marginRight: '16rpx' },
    { width: '18%', height: '24rpx', borderRadius: '10rpx' }
  ],
  { width: '92%', height: '24rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '22%', height: '24rpx', margin: '14rpx 18rpx 0 0', borderRadius: '10rpx' },
    { width: '14%', height: '24rpx', margin: '14rpx 0 0 0', borderRadius: '999rpx' }
  ]
]

export default {
  components: {
    DhCardSkeletonList,
    DhRevealPanel
  },
  mixins: [elderPageMixin],
  data() {
    return {
      loading: true,
      syncing: false,
      rows: [],
      page: 1,
      pageSize: 20,
      total: 0,
      unreadCount: getStoredNoticeUnreadCount(),
      selectMode: false,
      selectedIds: [],
      optimisticReadIds: [],
      noticeSkeletonRows: NOTICE_SKELETON_ROWS
    }
  },
  computed: {
    actionButtonSize() {
      return 'small'
    }
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/launch/index' })
      return
    }
    this.refresh(true)
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    noticeTypeText(type) {
      const value = String(type || '').trim().toLowerCase()
      if (value === 'review' || value === 'review_apply') return '审核'
      if (value === 'status') return '状态'
      if (value === 'message') return '消息'
      return '系统'
    },
    isSelected(id) {
      const target = String(id || '').trim()
      return this.selectedIds.includes(target)
    },
    enterSelectMode() {
      this.selectMode = true
      this.selectedIds = []
    },
    exitSelectMode() {
      this.selectMode = false
      this.selectedIds = []
    },
    toggleSelect(id) {
      const target = String(id || '').trim()
      if (!target) return
      if (this.isSelected(target)) {
        this.selectedIds = this.selectedIds.filter((item) => item !== target)
        return
      }
      this.selectedIds = this.selectedIds.concat(target)
    },
    handleCardClick(item) {
      if (!this.selectMode) {
        if (item && Number(item.isRead) !== 1) {
          this.markRead(item)
        }
        return
      }
      this.toggleSelect(item && item.id)
    },
    syncUnreadCount(nextCount) {
      this.unreadCount = setStoredNoticeUnreadCount(nextCount)
    },
    normalizeRows(records) {
      return (Array.isArray(records) ? records : []).map((item) => ({
        ...item,
        id: String(item && item.id ? item.id : '').trim(),
        isRead: Number(
          item && item.is_read != null
            ? item.is_read
            : item && item.isRead != null
              ? item.isRead
              : 0
        )
      }))
    },
    fetchNoticePage(targetPage) {
      return api.get(
        '/miniapp/auth/me/notices',
        {
          page: targetPage,
          pageSize: this.pageSize
        },
        {
          dedupeWindowMs: 0
        }
      )
    },
    applyPageResult(data, append = false) {
      const normalized = this.normalizeRows((data && data.records) || [])
      const optimisticReadSet = new Set((this.optimisticReadIds || []).map((id) => String(id || '').trim()).filter(Boolean))
      let optimisticUnreadCount = 0
      const unresolvedOptimisticIds = new Set()
      normalized.forEach((item) => {
        const id = String(item && item.id ? item.id : '').trim()
        if (!id || !optimisticReadSet.has(id)) return
        if (Number(item.isRead) !== 1) {
          optimisticUnreadCount += 1
          unresolvedOptimisticIds.add(id)
        }
        item.isRead = 1
      })
      this.rows = append ? this.rows.concat(normalized) : normalized
      this.total = Number((data && data.total) || 0)
      const serverUnreadCount = Number((data && data.unreadCount) || 0)
      this.syncUnreadCount(Math.max(0, serverUnreadCount - optimisticUnreadCount))
      if (!append && optimisticReadSet.size) {
        this.optimisticReadIds = this.optimisticReadIds.filter((id) => unresolvedOptimisticIds.has(String(id || '').trim()))
      }
    },
    async refresh(showSkeleton = false) {
      this.page = 1
      this.exitSelectMode()
      if (showSkeleton) {
        this.loading = true
        this.rows = []
      } else {
        this.syncing = true
      }
      try {
        const data = await this.fetchNoticePage(1)
        this.applyPageResult(data, false)
      } catch (e) {
        console.error('加载消息失败', e)
        uni.showToast({ title: e.message || '加载消息失败', icon: 'none' })
      } finally {
        this.loading = false
        this.syncing = false
      }
    },
    async loadMore() {
      if (this.loading || this.syncing) return
      if (this.rows.length >= this.total) return
      this.loading = true
      try {
        const nextPage = this.page + 1
        const data = await this.fetchNoticePage(nextPage)
        this.page = nextPage
        this.applyPageResult(data, true)
      } catch (e) {
        console.error('加载更多消息失败', e)
        uni.showToast({ title: e.message || '加载更多失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async reloadCurrentPage(options = {}) {
      const silent = !!(options && options.silent)
      if (!silent) {
        this.syncing = true
      }
      try {
        const data = await this.fetchNoticePage(this.page)
        const records = this.normalizeRows((data && data.records) || [])
        const safePage = records.length || this.page <= 1 ? this.page : this.page - 1
        if (safePage !== this.page) {
          this.page = safePage
          const prevData = await this.fetchNoticePage(this.page)
          this.applyPageResult(prevData, false)
          return
        }
        this.applyPageResult(data, false)
      } finally {
        if (!silent) {
          this.syncing = false
        }
      }
    },
    async markRead(item) {
      if (!item || !item.id || Number(item.isRead) === 1 || item.__reading) return
      const itemId = String(item.id || '').trim()
      if (!itemId) return
      const previousRead = Number(item.isRead) === 1 ? 1 : 0
      if (previousRead !== 1) {
        item.isRead = 1
        this.syncUnreadCount(Math.max(0, Number(this.unreadCount || 0) - 1))
        if (!this.optimisticReadIds.includes(itemId)) {
          this.optimisticReadIds = this.optimisticReadIds.concat(itemId)
        }
      }
      item.__reading = true
      try {
        await api.put(`/miniapp/auth/me/notices/${item.id}/read`, {}, { dedupe: false })
        await this.reloadCurrentPage({ silent: true })
      } catch (e) {
        if (previousRead !== 1) {
          item.isRead = previousRead
          this.syncUnreadCount(Number(this.unreadCount || 0) + 1)
          this.optimisticReadIds = this.optimisticReadIds.filter((id) => id !== itemId)
        }
        console.error('标记已读失败', e)
        uni.showToast({ title: e.message || '标记已读失败', icon: 'none' })
      } finally {
        item.__reading = false
      }
    },
    async deleteOne(item) {
      if (!item || !item.id || this.syncing) return
      this.syncing = true
      try {
        await api.delete(`/miniapp/auth/me/notices/${item.id}`)
        await this.reloadCurrentPage()
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (e) {
        this.syncing = false
        console.error('删除消息失败', e)
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
    async deleteSelected() {
      if (!this.selectedIds.length || this.syncing) {
        if (!this.selectedIds.length) {
          uni.showToast({ title: '请先选择消息', icon: 'none' })
        }
        return
      }
      this.syncing = true
      try {
        await Promise.all(this.selectedIds.map((id) => api.delete(`/miniapp/auth/me/notices/${id}`)))
        this.selectedIds = []
        this.selectMode = false
        await this.reloadCurrentPage()
        uni.showToast({ title: '已删除选中消息', icon: 'success' })
      } catch (e) {
        this.syncing = false
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
    async markAllRead() {
      if (!this.unreadCount || this.syncing) {
        if (!this.unreadCount) {
          uni.showToast({ title: '没有未读消息', icon: 'none' })
        }
        return
      }
      this.syncing = true
      try {
        await api.put('/miniapp/auth/me/notices/read-all', {})
        await this.reloadCurrentPage()
        uni.showToast({ title: '已全部标记为已读', icon: 'success' })
      } catch (e) {
        this.syncing = false
        console.error('全部已读失败', e)
        uni.showToast({ title: e.message || '全部已读失败', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--dh-color-bg);
}

.toolbar-wrap {
  padding: 4rpx 24rpx 6rpx;
}

.toolbar {
  padding: 12rpx 16rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.toolbar-left,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-wrap: wrap;
}

.toolbar-summary {
  font-size: 24rpx;
  color: #6e7b67;
}

.list {
  flex: 1;
  min-height: 0;
  padding: 0 24rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.list-inner {
  min-height: 100%;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 18rpx;
  margin-bottom: 12rpx;
  border: 2rpx solid transparent;
  transition: border-color 160ms ease, transform 160ms ease;
}

.card.unread {
  border-color: #d7e8c2;
}

.card.is-selectable {
  border-color: rgba(47, 125, 69, 0.1);
}

.card.is-selected {
  border-color: rgba(47, 125, 69, 0.56);
  transform: scale(0.992);
}

.line {
  display: flex;
  align-items: center;
}

.between {
  justify-content: space-between;
  gap: 16rpx;
}

.head-line {
  align-items: flex-start;
}

.meta-line {
  margin-top: 12rpx;
}

.card-head-main {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
}

.name {
  font-size: 30rpx;
  color: #2c3a26;
  font-weight: 600;
}

.selected-flag {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(47, 125, 69, 0.12);
  color: var(--dh-color-brand);
  font-size: 22rpx;
}

.type {
  font-size: 22rpx;
  color: #74836d;
  flex-shrink: 0;
}

.content {
  display: block;
  margin-top: 10rpx;
  font-size: 25rpx;
  color: #5b6655;
  line-height: 1.6;
}

.time {
  font-size: 22rpx;
  color: #8f9b8a;
}

.dot {
  font-size: 22rpx;
  color: #c64545;
  flex-shrink: 0;
}

.empty,
.foot {
  text-align: center;
  color: #77846f;
  font-size: 26rpx;
  padding: 42rpx 0;
}

.list-card-skeleton {
  padding: 4rpx 0 8rpx;
}

.foot-skeleton {
  padding-top: 12rpx;
}

.list-reveal-panel {
  display: block;
}

.elder-mode .toolbar-wrap {
  padding-top: 6rpx;
}

.elder-mode .toolbar {
  padding: 14rpx 18rpx;
}

.elder-mode .toolbar-summary {
  font-size: 28rpx;
}

.elder-mode .name {
  font-size: 34rpx;
}

.elder-mode .content {
  font-size: 28rpx;
}

</style>
