<template>
  <AdminErrorState
    code="403"
    title="没有访问权限"
    description="当前账号没有打开这个页面的权限。请返回控制台，或联系超级管理员调整权限。"
    :icon="Lock"
    @back="goBack"
    @home="goHome"
  />
</template>

<script setup>
import { Lock } from '@element-plus/icons-vue'
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
