<template>
  <AdminErrorState
    code="500"
    title="页面加载失败"
    description="当前页面在加载过程中出现异常。你可以返回上一页重试，或先回到控制台。"
    :icon="WarningFilled"
    @back="goBack"
    @home="goHome"
  />
</template>

<script setup>
import { WarningFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import AdminErrorState from '../components/ui/AdminErrorState.vue'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.replace('/dashboard')
}

function goHome() {
  router.replace('/dashboard')
}
</script>
