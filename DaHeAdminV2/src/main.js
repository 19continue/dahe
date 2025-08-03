import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'dayjs/locale/zh-cn'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)
router.onError(() => {
  if (router.currentRoute.value.path !== '/500') {
    router.replace('/500')
  }
})
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
