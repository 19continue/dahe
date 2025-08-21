import App from './App'
import DhSmartImage from './components/dh-smart-image.vue'
import DhLoadingState from './components/dh-loading-state.vue'
import DhCardSkeletonList from './components/dh-card-skeleton-list.vue'

// #ifndef VUE3
import Vue from 'vue'
import { guardMiniappPageAccess } from './utils/app-auth-guard'
Vue.config.productionTip = false
App.mpType = 'app'

// 显式全局注册，避免不同页面/分包场景下 easycom 未命中导致组件空白
Vue.component('dh-smart-image', DhSmartImage)
Vue.component('dh-loading-state', DhLoadingState)
Vue.component('dh-card-skeleton-list', DhCardSkeletonList)

Vue.mixin({
  onShow() {
    const route = typeof this.route === 'string' ? this.route : ''
    if (!route) {
      return
    }
    guardMiniappPageAccess(route).catch(() => {})
  }
})

const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
export function createApp() {
  const app = createSSRApp(App)
  // VUE3 分支也要显式注册，确保微信代码依赖分析可追踪到组件依赖
  app.component('dh-smart-image', DhSmartImage)
  app.component('dh-loading-state', DhLoadingState)
  app.component('dh-card-skeleton-list', DhCardSkeletonList)
  return {
    app
  }
}
// #endif
