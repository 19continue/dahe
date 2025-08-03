<template>
  <div class="admin-error-page">
    <div class="admin-error-bg admin-error-bg-left"></div>
    <div class="admin-error-bg admin-error-bg-right"></div>
    <div class="admin-error-card">
      <div class="admin-error-badge">{{ code }}</div>
      <div class="admin-error-icon-wrap">
        <el-icon class="admin-error-icon"><component :is="icon" /></el-icon>
      </div>
      <h1 class="admin-error-title">{{ title }}</h1>
      <p class="admin-error-desc">{{ description }}</p>
      <div class="admin-error-actions">
        <el-button @click="emit('back')">返回上一页</el-button>
        <el-button type="primary" @click="emit('home')">回到控制台</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  code: {
    type: String,
    default: '500'
  },
  title: {
    type: String,
    default: '页面异常'
  },
  description: {
    type: String,
    default: '当前页面暂时无法正常打开，请稍后再试。'
  },
  icon: {
    type: [Object, Function],
    required: true
  }
})

const emit = defineEmits(['back', 'home'])
</script>

<style scoped>
.admin-error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(600px 320px at 8% 10%, rgba(22, 103, 183, 0.08), transparent 60%),
    radial-gradient(540px 280px at 92% 16%, rgba(30, 64, 175, 0.06), transparent 62%),
    linear-gradient(180deg, #f7f9fc 0%, #f2f5fa 100%);
}

.admin-error-bg {
  position: absolute;
  border-radius: 999px;
  filter: blur(12px);
  opacity: 0.7;
}

.admin-error-bg-left {
  width: 240px;
  height: 240px;
  left: -64px;
  top: 48px;
  background: rgba(22, 103, 183, 0.08);
}

.admin-error-bg-right {
  width: 220px;
  height: 220px;
  right: -48px;
  bottom: 64px;
  background: rgba(15, 23, 42, 0.06);
}

.admin-error-card {
  position: relative;
  z-index: 1;
  width: min(560px, 100%);
  padding: 40px 36px;
  border-radius: 28px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.admin-error-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.admin-error-icon-wrap {
  width: 88px;
  height: 88px;
  margin: 22px auto 0;
  border-radius: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(22, 103, 183, 0.1), rgba(22, 103, 183, 0.04));
}

.admin-error-icon {
  font-size: 38px;
  color: #1667b7;
}

.admin-error-title {
  margin: 20px 0 10px;
  font-size: 28px;
  font-weight: 600;
  color: #111827;
}

.admin-error-desc {
  margin: 0 auto;
  max-width: 360px;
  line-height: 1.7;
  font-size: 14px;
  color: #6b7280;
}

.admin-error-actions {
  margin-top: 28px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
</style>
