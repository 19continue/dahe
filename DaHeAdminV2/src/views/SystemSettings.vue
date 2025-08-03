<template>
  <div class="settings-page">
    <PageToolbar
      title="系统设置中心"
      subtitle="把常用设置、日常检查和使用说明放在一个页面。"
      :collapsible="false"
    >
      <div class="actions">
        <el-button @click="reload">刷新数据</el-button>
        <el-button type="primary" @click="go('/admin-guide')">打开操作指引</el-button>
      </div>
    </PageToolbar>

    <el-card shadow="never" class="overview-card">
      <div class="overview-row">
        <div class="overview-item">
          <div class="label">高德剩余额度</div>
          <div class="value">{{ remainQuota }}</div>
        </div>
        <div class="overview-item">
          <div class="label">农事记录可编辑时长</div>
          <div class="value">{{ recordPolicy.editWindowHours || 0 }} 小时</div>
        </div>
        <div class="overview-item">
          <div class="label">资源回收站保留期</div>
          <div class="value">{{ assetPolicy.strictSourcePurgeRetainDays || 7 }} 天</div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="常用设置" name="common">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">农事记录设置</div>
                <div class="line">编辑时限：{{ recordPolicy.editWindowHours || 0 }} 小时</div>
                <div class="line">小程序用户可编辑：{{ yesNo(recordPolicy.allowOperatorUpdate) }}</div>
                <div class="line">小程序用户可删除：{{ yesNo(recordPolicy.allowOperatorDelete) }}</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/record-policy')">前往记录权限策略</el-button>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">资源上传设置</div>
                <div class="line">小程序上传审核：{{ yesNo(assetPolicy.miniappRequireReview) }}</div>
                <div class="line">后台上传审核：{{ yesNo(assetPolicy.adminRequireReview) }}</div>
                <div class="line">回收站保留期：{{ assetPolicy.strictSourcePurgeRetainDays || 7 }} 天</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/company-intro')">前往企业介绍管理</el-button>
                  <el-button size="small" @click="go('/asset-policy')">前往资源上传策略</el-button>
                  <el-button size="small" @click="go(ASSET_CENTER_ROUTE)">前往图片与资源管理</el-button>
                </div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="12" class="mt12">
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">种子检测设置</div>
                <div class="line">固定样本数：{{ yesNo(seedRule.fixedSampleSize) }}</div>
                <div class="line">默认样本数：{{ seedRule.defaultSampleSize || '-' }} 粒</div>
                <div class="line">备注：{{ seedRule.remark || '无' }}</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/seed-rules')">前往种子检测规则</el-button>
                  <el-button size="small" @click="go('/seed-manage')">前往种子批次管理</el-button>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">页面显示与设备适配</div>
                <div class="line">暗黑模式：点击页面右上角月亮/太阳图标即可切换。</div>
                <div class="line">手机端：筛选区和弹窗会自动适配窄屏，优先竖屏使用。</div>
                <div class="line">如果显示拥挤，可减少同屏筛选项数量。</div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="参数与模板入口" name="params">
          <div class="box">
            <div class="box-title">推荐维护顺序</div>
            <div class="line">1. 先维护流程模板，再维护参数模板，最后再创建业务数据。</div>
            <div class="line">2. 能用可视化配置就不用 JSON，降低维护难度。</div>
            <div class="line">3. 参数命名尽量使用业务口径，减少理解成本。</div>
          </div>

          <div class="quick-grid">
            <el-card shadow="never" class="quick-card">
              <div class="quick-title">流程模板</div>
              <div class="quick-desc">配置流程步骤和排序，用于种植计划与农事记录。</div>
              <el-button size="small" @click="go('/process-templates')">进入流程模板管理</el-button>
            </el-card>
            <el-card shadow="never" class="quick-card">
              <div class="quick-title">农事步骤参数模板</div>
              <div class="quick-desc">配置农事步骤中要填写的字段和可选项。</div>
              <el-button size="small" @click="go('/farm-step-dynamic-configs')">进入农事步骤参数模板</el-button>
            </el-card>
            <el-card shadow="never" class="quick-card">
              <div class="quick-title">种子参数模板</div>
              <div class="quick-desc">分别维护种子批次和种子检测的扩展字段。</div>
              <div class="link-row">
                <el-button size="small" @click="go('/seed-dynamic-configs/batch')">种子批次参数</el-button>
                <el-button size="small" @click="go('/seed-dynamic-configs/test')">种子检测参数</el-button>
              </div>
            </el-card>
            <el-card shadow="never" class="quick-card">
              <div class="quick-title">通用参数与模板</div>
              <div class="quick-desc">统一查看导出模板和术语词典，保持口径一致。</div>
              <div class="link-row">
                <el-button size="small" @click="go('/export-templates')">导出模板标准化</el-button>
                <el-button size="small" @click="go('/terminology-dict')">术语词典管理</el-button>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="日常检查" name="daily">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="24" :md="8">
              <div class="box">
                <div class="box-title">审核与通知</div>
                <div class="line">每天先看小程序用户审核和通知中心。</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/users')">小程序用户审核</el-button>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="24" :md="8">
              <div class="box">
                <div class="box-title">高德额度</div>
                <div class="line">总额度：{{ amapQuota.totalQuota || 0 }}</div>
                <div class="line">已使用：{{ amapQuota.usedQuota || 0 }}</div>
                <div class="line">剩余额度：{{ remainQuota }}</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/amap-audit')">前往高德额度审计</el-button>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="24" :md="8">
              <div class="box">
                <div class="box-title">日志与撤销</div>
                <div class="line">关键修改和删除都会记录日志。</div>
                <div class="line">若要撤销，请按日志顺序执行，避免链路断开。</div>
                <div class="link-row">
                  <el-button size="small" @click="go('/operation-logs')">前往系统操作日志</el-button>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="帮助说明" name="help">
          <el-alert
            title="这套系统按“人员权限 → 基础档案 → 参数模板 → 业务记录 → 导出与审计”的顺序使用最稳定。"
            type="success"
            :closable="false"
            show-icon
          />

          <el-row :gutter="12" class="mt12">
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">常见问题</div>
                <div class="qa-item">
                  <div class="q">Q：为什么有些图片不能被选择？</div>
                  <div class="a">A：待审核或驳回状态的图片只能查看，不能用于业务绑定。</div>
                </div>
                <div class="qa-item">
                  <div class="q">Q：为什么某些菜单看不到？</div>
                  <div class="a">A：菜单由后台角色权限控制，请在“角色与权限配置”里授权。</div>
                </div>
                <div class="qa-item">
                  <div class="q">Q：删除后还能恢复吗？</div>
                  <div class="a">A：支持回收站和日志撤销，建议先查日志再操作。</div>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="24" :md="12">
              <div class="box">
                <div class="box-title">建议培训顺序</div>
                <div class="line">1. 先培训审核员：用户审核、通知处理、账号禁用。</div>
                <div class="line">2. 再培训运营员：田块、作物、种植计划、农事记录。</div>
                <div class="line">3. 最后培训管理员：参数模板、导出模板、日志撤销。</div>
                <div class="link-row">
                  <el-button size="small" type="primary" @click="go('/admin-guide')">查看完整操作指引</el-button>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageToolbar from '../components/ui/PageToolbar.vue'
import { ASSET_CENTER_ROUTE } from '../utils/adminRouteMap'
import request from '../utils/request'

const router = useRouter()
const activeTab = ref('common')

const recordPolicy = reactive({
  editWindowHours: 48,
  allowOperatorUpdate: 1,
  allowOperatorDelete: 1
})

const seedRule = reactive({
  fixedSampleSize: 1,
  defaultSampleSize: 100,
  remark: ''
})

const assetPolicy = reactive({
  miniappRequireReview: 0,
  adminRequireReview: 0,
  strictSourcePurgeRetainDays: 7
})

const amapQuota = reactive({
  totalQuota: 0,
  usedQuota: 0
})

const remainQuota = computed(() => {
  const total = Number(amapQuota.totalQuota || 0)
  const used = Number(amapQuota.usedQuota || 0)
  return Math.max(0, total - used)
})

function yesNo(value) {
  return Number(value) === 1 ? '是' : '否'
}

function go(path) {
  router.push(path)
}

async function loadRecordPolicy() {
  try {
    const row = await request.get('/record-policy')
    if (!row) return
    recordPolicy.editWindowHours = row.editWindowHours != null ? Number(row.editWindowHours) : 48
    recordPolicy.allowOperatorUpdate = Number((row.allowMiniappUpdate ?? row.allowOperatorUpdate) || 0)
    recordPolicy.allowOperatorDelete = Number((row.allowMiniappDelete ?? row.allowOperatorDelete) || 0)
  } catch (e) {}
}

async function loadSeedRule() {
  try {
    const row = await request.get('/seed-settings')
    if (!row) return
    seedRule.fixedSampleSize = Number(row.fixedSampleSize || 1)
    seedRule.defaultSampleSize = Number(row.defaultSampleSize || 100)
    seedRule.remark = row.remark || ''
  } catch (e) {}
}

async function loadAssetPolicy() {
  try {
    const row = await request.get('/admin/asset-policy')
    if (!row) return
    assetPolicy.miniappRequireReview = Number((row.miniappRequireReview ?? row.operatorRequireReview) || 0)
    assetPolicy.adminRequireReview = Number(row.adminRequireReview || 0)
    assetPolicy.strictSourcePurgeRetainDays = Number(row.strictSourcePurgeRetainDays || 7)
  } catch (e) {}
}

async function loadAmapQuota() {
  try {
    const row = await request.get('/admin/amap/quota')
    if (!row) return
    amapQuota.totalQuota = Number(row.totalQuota || 0)
    amapQuota.usedQuota = Number(row.usedQuota || 0)
  } catch (e) {}
}

async function reload() {
  try {
    await Promise.all([loadRecordPolicy(), loadSeedRule(), loadAssetPolicy(), loadAmapQuota()])
  } catch (e) {
    ElMessage.error('系统设置刷新失败')
  }
}

onMounted(reload)
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview-card {
  border: 1px solid var(--border);
}

.overview-row {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.overview-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 10px 12px;
}

.label {
  font-size: 12px;
  color: var(--text-sub);
}

.value {
  margin-top: 6px;
  font-size: 14px;
  color: var(--text-main);
  font-weight: 600;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.code {
  font-family: Consolas, Monaco, monospace;
}

.box {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-soft);
  padding: 10px 12px;
  min-height: 156px;
}

.box-title {
  color: var(--text-main);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}

.line {
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.9;
}

.link-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-card {
  border: 1px solid var(--border);
  background: var(--bg-soft);
}

.quick-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 6px;
}

.quick-desc {
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.8;
  margin-bottom: 8px;
}

.qa-item + .qa-item {
  margin-top: 8px;
}

.q {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 600;
}

.a {
  margin-top: 2px;
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.8;
}

.mt12 {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .overview-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .overview-row {
    grid-template-columns: 1fr;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
