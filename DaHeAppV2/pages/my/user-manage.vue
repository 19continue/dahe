<template>
  <view class="page record-style-page" :class="{ 'elder-mode': elderMode }">
    <app-page-header
      class="dh-navbar"
      toolbar
      title="账号审核与角色"
      :fixed="true"
      :safe-area-inset-top="true"
      left-arrow
      @go-back="goBack"
    />

    <view class="content-shell">
      <view class="toolbar card">
        <view class="row row-search">
          <search-assist
            ref="searchAssist"
            class="search-box"
            :value="keyword"
            placeholder="搜索姓名、昵称、手机号、账号"
            :helper-text="searchFeedbackText"
            :suggestions="keywordSuggestions"
            :history-items="keywordHistory"
            :elder-mode="elderMode"
            @change="onKeywordChange"
            @submit="onKeywordSubmit"
            @clear="onKeywordClear"
            @history-click="onHistoryClick"
            @history-clear="onHistoryClear"
            @history-remove="onHistoryRemove"
            @suggestion-click="onSuggestionClick"
          />
          <picker mode="selector" :range="statusOptions" range-key="label" @change="onStatusChange">
            <view class="picker">{{ currentStatusLabel }}</view>
          </picker>
        </view>

        <view class="row">
          <picker mode="selector" :range="enabledOptions" range-key="label" @change="onEnabledChange">
            <view class="picker">{{ currentEnabledLabel }}</view>
          </picker>
        </view>

        <view class="row">
          <button class="btn minor" @click="search">查询</button>
          <button class="btn" @click="refresh">刷新</button>
        </view>

        <text class="pending">待审核：{{ pendingCount }}</text>
      </view>

      <scroll-view
        scroll-y
        class="list"
        :show-scrollbar="false"
        :enable-back-to-top="true"
        enhanced
        :fast-deceleration="true"
        :bounces="true"
        :lower-threshold="120"
        @scroll="dismissSearchSuggestions"
        @scrolltolower="loadMore"
      >
        <dh-reveal-panel :loading="loading && !users.length" class="list-reveal-panel">
          <template #skeleton>
            <view class="list-card-skeleton">
              <dh-card-skeleton-list
                compact
                :count="5"
                item-min-height="236rpx"
                body-min-height="200rpx"
                item-padding="14rpx"
                item-radius="16rpx"
                :row-col="userSkeletonRows"
              />
            </view>
          </template>

          <template v-if="users.length">
            <view v-for="user in users" :key="user.id" class="card">
              <view class="line between">
                <text class="name">{{ user.realName || user.nickName || '未命名用户' }}</text>
                <text class="tag" :class="statusClass(user)">{{ statusText(user) }}</text>
              </view>
              <text class="meta">昵称：{{ user.nickName || '-' }}</text>
              <text class="meta">手机号：{{ user.phone || '-' }}</text>
              <text class="meta">角色：{{ roleText(user.roleCode) }} · 控制台：{{ Number(user.canConsole) === 1 ? '已开通' : '未开通' }}</text>
              <text class="meta">申请说明：{{ user.applyReason || '-' }}</text>

              <view class="actions" v-if="String(user.status || '').toLowerCase() === 'pending'">
                <button class="small approve" @click="approveUser(user, 'operator', false)">通过(操作员)</button>
                <button class="small console" @click="approveUser(user, 'operator', true)">通过+控制台</button>
                <button class="small reject" @click="rejectUser(user)">驳回</button>
              </view>

              <view class="actions" v-else>
                <button class="small" @click="toggleConsole(user)">
                  {{ Number(user.canConsole) === 1 ? '关闭控制台' : '开通控制台' }}
                </button>
                <button v-if="canToggleDisabled(user)" class="small reject" @click="toggleDisabled(user)">
                  {{ Number(user.enabled) === 0 ? '解除禁用' : '禁用' }}
                </button>
              </view>
            </view>
          </template>
          <view v-else class="empty">暂无用户记录</view>
        </dh-reveal-panel>

        <view v-if="loading && users.length" class="footer-skeleton">
          <dh-card-skeleton-list compact :count="1" :row-col="[1]" />
        </view>
        <view v-else-if="showFooter" class="state-tip">{{ footerText }}</view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import api from '../../utils/request'
import { isApprovedUser, isAdminUser } from '../../utils/auth'
import SearchAssist from '../../components/search-assist.vue'
import DhCardSkeletonList from '../../components/dh-card-skeleton-list.vue'
import DhRevealPanel from '../../components/dh-reveal-panel.vue'
import elderPageMixin from '../../utils/elder-page'
import { readSearchValue } from '../../utils/search-suggestion'
import { readSearchHistory, pushSearchHistory, clearSearchHistory, removeSearchHistoryItem } from '../../utils/search-history'

const PAGE_SIZE = 20
const USER_SKELETON_ROWS = [
  [
    { width: '46%', height: '28rpx', borderRadius: '12rpx', marginRight: '16rpx' },
    { width: '18%', height: '22rpx', borderRadius: '999rpx' }
  ],
  { width: '52%', height: '22rpx', margin: '12rpx 0 0 0', borderRadius: '10rpx' },
  { width: '62%', height: '22rpx', margin: '10rpx 0 0 0', borderRadius: '10rpx' },
  { width: '74%', height: '22rpx', margin: '10rpx 0 0 0', borderRadius: '10rpx' },
  [
    { width: '30%', height: '24rpx', margin: '16rpx 10rpx 0 0', borderRadius: '999rpx' },
    { width: '30%', height: '24rpx', margin: '16rpx 10rpx 0 0', borderRadius: '999rpx' },
    { width: '24%', height: '24rpx', margin: '16rpx 0 0 0', borderRadius: '999rpx' }
  ]
]

export default {
  components: {
    SearchAssist,
    DhCardSkeletonList,
    DhRevealPanel
  },
  mixins: [elderPageMixin],
  data() {
    return {
      loading: true,
      users: [],
      pendingCount: 0,
      keyword: '',
      appliedKeyword: '',
      page: 1,
      pageSize: PAGE_SIZE,
      total: 0,
      finished: false,
      statusOptions: [
        { value: '', label: '全部状态' },
        { value: 'pending', label: '待审核' },
        { value: 'approved', label: '已通过' },
        { value: 'rejected', label: '已驳回' }
      ],
      statusIndex: 1,
      enabledOptions: [
        { value: '', label: '全部可用性' },
        { value: '1', label: '启用' },
        { value: '0', label: '禁用' }
      ],
      enabledIndex: 0,
      remoteSuggestions: [],
      keywordHistory: [],
      suggestionSeq: 0,
      initialized: false,
      userSkeletonRows: USER_SKELETON_ROWS
    }
  },
  computed: {
    currentStatusLabel() {
      return this.statusOptions[this.statusIndex].label
    },
    currentStatusValue() {
      return this.statusOptions[this.statusIndex].value
    },
    currentEnabledLabel() {
      return this.enabledOptions[this.enabledIndex].label
    },
    currentEnabledValue() {
      return this.enabledOptions[this.enabledIndex].value
    },
    searchFeedbackText() {
      if (!this.keyword) return ''
      if (this.keyword !== this.appliedKeyword) {
        return `输入了“${this.keyword}”，点击“查询”后再更新列表`
      }
      if (!this.users.length) {
        return `关键词“${this.appliedKeyword}”暂无匹配用户`
      }
      if (this.keywordSuggestions.length) {
        return `已匹配 ${this.users.length} 个用户，可继续点下方提示词缩小范围`
      }
      return `关键词“${this.appliedKeyword}”匹配 ${this.users.length} 个用户`
    },
    keywordSuggestions() {
      return Array.isArray(this.remoteSuggestions) ? this.remoteSuggestions : []
    },
    showFooter() {
      return !!this.users.length
    },
    footerText() {
      if (this.loading) return '正在加载更多...'
      if (this.finished) return '已加载全部'
      return '上拉加载更多'
    }
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    if (!isAdminUser()) {
      uni.showToast({ title: '仅管理员可访问', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 220)
      return
    }
    this.loadKeywordHistory()
    if (!this.initialized) {
      this.initialized = true
      this.refresh()
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    dismissSearchSuggestions() {
      const searchAssist = this.$refs.searchAssist
      if (searchAssist && typeof searchAssist.hidePanelOnScroll === 'function') {
        searchAssist.hidePanelOnScroll()
      }
    },
    onKeywordChange(context) {
      this.keyword = readSearchValue(context)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
    },
    onKeywordSubmit(context) {
      this.keyword = readSearchValue(context)
      const nextKeyword = String(this.keyword || '').trim()
      this.keyword = nextKeyword
      if (!nextKeyword) {
        uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
        return
      }
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(this.appliedKeyword)
      this.remoteSuggestions = []
      this.search()
    },
    onKeywordClear() {
      const hadAppliedKeyword = !!this.appliedKeyword
      this.keyword = ''
      this.appliedKeyword = ''
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      if (hadAppliedKeyword) {
        this.search()
      }
    },
    bumpSuggestionSeq() {
      this.suggestionSeq += 1
      return this.suggestionSeq
    },
    loadKeywordHistory() {
      this.keywordHistory = readSearchHistory('console-user', 6)
    },
    persistKeywordHistory(keyword) {
      const safeKeyword = String(keyword || '').trim()
      if (!safeKeyword) return
      this.keywordHistory = pushSearchHistory('console-user', safeKeyword, 6)
    },
    onHistoryClick(item) {
      const nextKeyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.search()
    },
    onHistoryClear() {
      this.keywordHistory = clearSearchHistory('console-user')
    },
    onHistoryRemove(item) {
      const keyword = String(item && item.value ? item.value : item && item.label ? item.label : '').trim()
      this.keywordHistory = removeSearchHistoryItem('console-user', keyword, 6)
    },
    onSuggestionClick(item) {
      const nextKeyword = String(item && item.value ? item.value : '').trim()
      if (!nextKeyword) return
      this.keyword = nextKeyword
      this.appliedKeyword = nextKeyword
      this.persistKeywordHistory(nextKeyword)
      this.remoteSuggestions = []
      this.bumpSuggestionSeq()
      this.search()
    },
    statusText(user) {
      if (user && user.enabled != null && Number(user.enabled) === 0) return '已停用'
      const status = String((user && user.status) || '').toLowerCase()
      if (status === 'approved') return '已通过'
      if (status === 'pending') return '待审核'
      if (status === 'rejected') return '已驳回'
      return '未知'
    },
    statusClass(user) {
      if (user && user.enabled != null && Number(user.enabled) === 0) return 'gray'
      const status = String((user && user.status) || '').toLowerCase()
      if (status === 'approved') return 'ok'
      if (status === 'pending') return 'wait'
      if (status === 'rejected') return 'bad'
      return 'gray'
    },
    roleText(role) {
      const value = String(role || '').toLowerCase()
      if (value === 'admin') return '管理员'
      if (value === 'supervisor') return '主管'
      if (value === 'operator') return '操作员'
      return '未分配'
    },
    isApproved(user) {
      return String((user && user.status) || '').toLowerCase() === 'approved'
    },
    canToggleDisabled(user) {
      if (Number((user && user.enabled) || 0) === 0) return true
      return this.isApproved(user)
    },
    onStatusChange(e) {
      this.statusIndex = Number(e.detail.value)
    },
    onEnabledChange(e) {
      this.enabledIndex = Number(e.detail.value)
    },
    async search() {
      this.loading = true
      this.appliedKeyword = this.keyword
      this.page = 1
      this.total = 0
      this.finished = false
      this.users = []
      await this.fetchUsers(true)
    },
    async refresh() {
      this.loading = true
      this.page = 1
      this.total = 0
      this.finished = false
      this.users = []
      await Promise.all([this.loadPendingCount(), this.fetchUsers(true)])
    },
    async loadMore() {
      if (this.loading || this.finished) return
      this.page += 1
      await this.fetchUsers(false)
    },
    async loadPendingCount() {
      try {
        const res = await api.get('/miniapp/console/users/pending-count')
        this.pendingCount = Number((res && res.pendingCount) || 0)
      } catch (e) {
        console.error('加载待审核数量失败', e)
      }
    },
    async fetchUsers(reset) {
      this.loading = true
      try {
        const data = await api.get('/miniapp/console/users', {
          keyword: this.appliedKeyword || undefined,
          status: this.currentStatusValue || undefined,
          enabled: this.currentEnabledValue === '' ? undefined : Number(this.currentEnabledValue),
          reviewOnly: true,
          page: this.page,
          pageSize: this.pageSize
        })
        const rows = Array.isArray(data && data.records) ? data.records : []
        const total = Number((data && data.total) || 0)
        this.total = total
        this.users = reset ? rows : this.users.concat(rows)
        this.finished = this.users.length >= total || rows.length < this.pageSize
      } catch (e) {
        console.error('加载用户列表失败', e)
      } finally {
        this.loading = false
      }
    },
    async approveUser(user, roleCode, canConsole) {
      if (!user || !user.id) return
      try {
        await api.put(`/miniapp/console/users/${user.id}/approve`, {
          approve: true,
          roleCode,
          canConsole
        })
        uni.showToast({ title: '已通过', icon: 'success' })
        await this.refresh()
      } catch (e) {
        console.error('审核通过失败', e)
      }
    },
    rejectUser(user) {
      if (!user || !user.id) return
      uni.showModal({
        title: '确认驳回',
        content: '将把该申请标记为驳回，用户可修改信息后再次提交。',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await api.put(`/miniapp/console/users/${user.id}/approve`, {
              approve: false,
              roleCode: user.roleCode || 'operator',
              canConsole: Number(user.canConsole) === 1,
              rejectReason: '资料不完整，请补充后重试'
            })
            uni.showToast({ title: '已驳回', icon: 'none' })
            await this.refresh()
          } catch (e) {
            console.error('驳回失败', e)
          }
        }
      })
    },
    async toggleConsole(user) {
      if (!user || !user.id) return
      try {
        await api.put(`/miniapp/console/users/${user.id}/role`, {
          canConsole: Number(user.canConsole) !== 1
        })
        uni.showToast({ title: '权限已更新', icon: 'success' })
        await this.search()
      } catch (e) {
        console.error('更新控制台权限失败', e)
      }
    },
    async toggleDisabled(user) {
      if (!user || !user.id) return
      if (Number(user.enabled) !== 0 && !this.isApproved(user)) {
        uni.showToast({ title: '仅已通过用户可禁用', icon: 'none' })
        return
      }
      const enabled = Number(user.enabled) === 0
      try {
        await api.put(`/miniapp/console/users/${user.id}/enabled`, {
          enabled
        })
        uni.showToast({ title: '禁用状态已更新', icon: 'none' })
        await this.refresh()
      } catch (e) {
        console.error('更新禁用状态失败', e)
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

.content-shell {
  height: calc(100vh - var(--status-bar-height) - 88rpx);
  padding: 12rpx 16rpx 20rpx;
  display: flex;
  flex-direction: column;
}

.toolbar {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 14rpx;
}

.row {
  display: flex;
  gap: 10rpx;
  margin-bottom: 10rpx;
}

.row:last-child {
  margin-bottom: 0;
}

.row-search {
  align-items: flex-start;
}

.search-box {
  flex: 1;
  --dh-search-bg-color: var(--dh-color-surface-soft);
  --dh-search-placeholder-color: #8c9687;
  --dh-search-text-color: #233021;
  --dh-search-height: 72rpx;
  --dh-search-radius: 14rpx;
  --dh-search-font-size: 27rpx;
}

.picker {
  width: 220rpx;
  height: 72rpx;
  border-radius: 14rpx;
  background: var(--dh-color-surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #2d3d21;
}

.btn {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
  background: var(--dh-color-brand);
  color: #ffffff;
  font-size: 26rpx;
}

.btn.minor {
  background: var(--dh-color-brand-light);
  color: var(--dh-color-brand);
}

.pending {
  font-size: 24rpx;
  color: #6e7f62;
}

.list {
  flex: 1;
  min-height: 0;
  margin-top: 12rpx;
}

.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 14rpx;
  margin-bottom: 10rpx;
}

.line {
  display: flex;
  align-items: center;
}

.line.between {
  justify-content: space-between;
}

.name {
  font-size: 30rpx;
  font-weight: 700;
  color: #2c3a26;
}

.meta {
  margin-top: 6rpx;
  display: block;
  font-size: 24rpx;
  color: #6b7863;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
}

.tag.ok {
  background: var(--dh-color-brand-light);
  color: #4a7b2f;
}

.tag.wait {
  background: #fff4db;
  color: #8f661c;
}

.tag.bad {
  background: #fde7e7;
  color: #b13a3a;
}

.tag.gray {
  background: #eceff1;
  color: #6d7780;
}

.actions {
  margin-top: 10rpx;
  display: flex;
  gap: 8rpx;
  flex-wrap: wrap;
}

.small {
  min-width: 172rpx;
  height: 58rpx;
  line-height: 58rpx;
  border-radius: 999rpx;
  background: #eef5e5;
  color: var(--dh-color-brand);
  font-size: 23rpx;
}

.small.approve {
  background: var(--dh-color-brand);
  color: #ffffff;
}

.small.console {
  background: #3c9b78;
  color: #ffffff;
}

.small.reject {
  background: #d9534f;
  color: #ffffff;
}

.empty {
  text-align: center;
  padding: 40rpx 20rpx;
  color: #74836d;
  font-size: 26rpx;
}

.empty-skeleton {
  padding: 12rpx 0;
}

.list-card-skeleton {
  padding: 2rpx 0 8rpx;
}

.state-tip {
  text-align: center;
  color: #85927f;
  font-size: 24rpx;
  padding: 18rpx 0 12rpx;
}

.footer-skeleton {
  padding: 10rpx 0 8rpx;
}

.list-reveal-panel {
  display: block;
}

.elder-mode {

  .search-box {
    --dh-search-height: 92rpx;
    --dh-search-radius: 20rpx;
    --dh-search-font-size: 32rpx;
  }

  .picker,
  .btn,
  .pending,
  .meta,
  .empty,
  .state-tip,
  .small {
    font-size: 30rpx;
  }

  .name {
    font-size: 36rpx;
  }
}
</style>
