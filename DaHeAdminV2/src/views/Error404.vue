<template>
  <AdminErrorState
    code="404"
    title="页面不存在"
    description="你访问的地址不存在，或者页面已经被迁移。可以返回上一页，或回到控制台重新进入。"
    :icon="DocumentDelete"
    @back="goBack"
    @home="goHome"
  />
</template>

<script setup>
import { DocumentDelete } from '@element-plus/icons-vue'
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
