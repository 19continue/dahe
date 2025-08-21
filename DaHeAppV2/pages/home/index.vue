<template>
  <view class="tab-shell-page">
    <view class="tab-shell-body">
      <app-home-panel
        v-show="activeTab === 'home'"
        ref="homePanelRef"
        :elder-mode="elderMode"
      />
      <app-my-panel
        v-show="activeTab === 'my'"
        ref="myPanelRef"
        :elder-mode="elderMode"
      />
    </view>
    <app-tab-bar
      :value="activeTab"
      :elder-mode="elderMode"
      @change="handleTabChange"
    />
  </view>
</template>

<script>
import AppHomePanel from '../../components/app-home-panel.vue'
import AppMyPanel from '../../components/app-my-panel.vue'
import AppTabBar from '../../components/app-tab-bar.vue'
import { isApprovedUser } from '../../utils/auth'
import { isElderMode } from '../../utils/accessibility'
import { buildMiniappShareMessage } from '../../utils/share'

export default {
  components: {
    AppHomePanel,
    AppMyPanel,
    AppTabBar
  },
  data() {
    return {
      activeTab: 'home',
      elderMode: false
    }
  },
  onLoad(options) {
    this.applyRouteTab(options)
  },
  onShow() {
    if (!isApprovedUser()) {
      uni.reLaunch({ url: '/pages/auth/login' })
      return
    }
    if (typeof uni.showShareMenu === 'function') {
      uni.showShareMenu({
        menus: ['shareAppMessage']
      })
    }
    this.elderMode = isElderMode()
    this.$nextTick(() => {
      this.activateCurrentPanel(false)
    })
  },
  onShareAppMessage() {
    return buildMiniappShareMessage()
  },
  onHide() {
    this.deactivateCurrentPanel()
  },
  onUnload() {
    this.deactivateCurrentPanel()
  },
  methods: {
    applyRouteTab(options) {
      const nextTab = String((options && options.tab) || '').trim().toLowerCase()
      if (nextTab === 'home' || nextTab === 'my') {
        this.activeTab = nextTab
      }
    },
    getCurrentPanelRef() {
      if (this.activeTab === 'my') {
        return this.$refs.myPanelRef
      }
      return this.$refs.homePanelRef
    },
    activateCurrentPanel(forceRefresh = false) {
      const panel = this.getCurrentPanelRef()
      if (panel && typeof panel.handlePanelShow === 'function') {
        panel.handlePanelShow(!!forceRefresh)
      }
    },
    deactivateCurrentPanel() {
      const panel = this.getCurrentPanelRef()
      if (panel && typeof panel.handlePanelHide === 'function') {
        panel.handlePanelHide()
      }
    },
    handleTabChange(nextTab) {
      const value = String(nextTab || '').trim().toLowerCase()
      if (!value || value === this.activeTab) return
      this.deactivateCurrentPanel()
      this.activeTab = value
      this.$nextTick(() => {
        this.activateCurrentPanel(false)
      })
    }
  }
}
</script>

<style lang="scss">
.tab-shell-page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--dh-color-bg);
}

.tab-shell-body {
  flex: 1;
  min-height: 0;
  position: relative;
}
</style>
