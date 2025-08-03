<template>
  <div class="guide-page">
    <PageToolbar
      title="操作指引"
      subtitle="给非专业用户的日常使用手册。"
      :collapsible="false"
    >
      <div class="actions">
        <el-button @click="go('/dashboard')">返回控制台</el-button>
        <el-button type="primary" @click="go('/system-settings')">打开系统设置中心</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" class="intro-card">
      <el-alert
        title="建议按“人员与权限 → 基础资料 → 参数模板 → 日常记录 → 导出审计”的顺序使用平台。"
        type="success"
        :closable="false"
        show-icon
      />
      <div class="intro-grid">
        <div class="intro-item">
          <div class="title">适合谁看</div>
          <div class="text">后台管理员、运营负责人、审核人员、录入复核人员。</div>
        </div>
        <div class="intro-item">
          <div class="title">你会学会什么</div>
          <div class="text">如何开通账号、维护田块、配置模板、录入记录、查看导出和日志。</div>
        </div>
        <div class="intro-item">
          <div class="title">推荐学习时长</div>
          <div class="text">首次学习 30~45 分钟，之后按岗位查阅对应模块。</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="guide-block">
      <template #header>
        <div class="block-head">一、按岗位找到页面</div>
      </template>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="24" :md="8">
          <div class="role-card">
            <div class="role-title">审核与账号人员</div>
            <div class="line">处理小程序用户申请、开通控制台、管理后台账号。</div>
            <div class="btn-row">
              <el-button size="small" @click="go('/users')">小程序用户审核</el-button>
              <el-button size="small" @click="go('/admin-users')">后台用户管理</el-button>
              <el-button size="small" @click="go('/roles')">角色与权限配置</el-button>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="8">
          <div class="role-card">
            <div class="role-title">生产运营人员</div>
            <div class="line">维护田块、作物、种植计划和农事记录。</div>
            <div class="btn-row">
              <el-button size="small" @click="go('/field-manage')">田块管理</el-button>
              <el-button size="small" @click="go('/crop-manage')">作物品种管理</el-button>
              <el-button size="small" @click="go('/field-cycles')">田块种植计划</el-button>
              <el-button size="small" @click="go('/farm-records-manage')">农事记录管理</el-button>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="8">
          <div class="role-card">
            <div class="role-title">质检与运维人员</div>
            <div class="line">维护种子批次、导出数据、查看配额与日志。</div>
            <div class="btn-row">
              <el-button size="small" @click="go('/seed-manage')">种子批次管理</el-button>
              <el-button size="small" @click="go('/exports')">记录导出</el-button>
              <el-button size="small" @click="go('/amap-audit')">高德额度审计</el-button>
              <el-button size="small" @click="go('/operation-logs')">系统操作日志</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="guide-block">
      <template #header>
        <div class="block-head">二、参数与模板怎么用</div>
      </template>
      <div class="line">先建模板，再做业务数据。模板改动后，后续记录会更统一。</div>
      <div class="line">如果不熟悉技术术语，优先使用可视化表单，不需要直接写 JSON。</div>
      <div class="btn-row mt8">
        <el-button size="small" @click="go('/process-templates')">流程模板管理</el-button>
        <el-button size="small" @click="go('/farm-step-dynamic-configs')">农事步骤参数模板</el-button>
        <el-button size="small" @click="go('/seed-dynamic-configs/batch')">种子批次参数</el-button>
        <el-button size="small" @click="go('/seed-dynamic-configs/test')">种子检测参数</el-button>
        <el-button size="small" @click="go('/export-templates')">导出模板标准化</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="guide-block">
      <template #header>
        <div class="block-head">三、每天建议检查</div>
      </template>
      <div class="check-grid">
        <div class="check-item">
          <div class="check-title">1. 看通知与审核</div>
          <div class="line">先处理用户审核和关键通知，避免流程卡住。</div>
        </div>
        <div class="check-item">
          <div class="check-title">2. 看资源状态</div>
          <div class="line">确认上传图片是否通过审核，避免业务页面无法选图。</div>
        </div>
        <div class="check-item">
          <div class="check-title">3. 看高德额度</div>
          <div class="line">额度不足会影响定位与天气数据。</div>
        </div>
        <div class="check-item">
          <div class="check-title">4. 看日志与撤销</div>
          <div class="line">出现误操作时，先查日志，再按顺序撤销。</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="guide-block">
      <template #header>
        <div class="block-head">四、常见问题</div>
      </template>
      <div class="qa">
        <div class="q">Q：为什么有些菜单我看不到？</div>
        <div class="a">A：菜单由角色权限控制，请联系管理员在“角色与权限配置”中授权。</div>
      </div>
      <div class="qa">
        <div class="q">Q：删除后还能找回吗？</div>
        <div class="a">A：优先从回收站恢复；若为关键数据，可在系统操作日志中执行撤销。</div>
      </div>
      <div class="qa">
        <div class="q">Q：怎么切换暗黑模式？手机能用吗？</div>
        <div class="a">A：点击右上角月亮/太阳图标即可切换；手机端已做适配，建议竖屏使用。</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'

const router = useRouter()

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.guide-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.intro-card,
.guide-block {
  border: 1px solid var(--border);
}

.intro-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.intro-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 10px 12px;
}

.title {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.text {
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.8;
}

.block-head {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.role-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 10px 12px;
  min-height: 160px;
}

.role-title {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.line {
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.9;
}

.btn-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.check-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 10px 12px;
}

.check-title {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.qa + .qa {
  margin-top: 10px;
}

.q {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 700;
}

.a {
  margin-top: 2px;
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.8;
}

.mt8 {
  margin-top: 8px;
}

@media (max-width: 1200px) {
  .intro-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .intro-grid,
  .check-grid {
    grid-template-columns: 1fr;
  }
}
</style>
