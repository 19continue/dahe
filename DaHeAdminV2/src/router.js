import { createRouter, createWebHistory } from 'vue-router'
import { getMenuPermissions, getUser, isLoggedIn } from './utils/auth'
import {
  ASSET_CENTER_ROUTE,
  ASSET_FOLDERS_ROUTE,
  ASSET_LIBRARY_ROUTE,
  ASSET_RECYCLE_ROUTE,
  ASSET_REVIEW_ROUTE,
  normalizeAdminRoutePath,
  resolveAdminPermissionPath
} from './utils/adminRouteMap'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/Login.vue')
  },
  {
    path: '/403',
    name: 'error-403',
    meta: { title: '没有访问权限' },
    component: () => import('./views/Error403.vue')
  },
  {
    path: '/404',
    name: 'error-404',
    meta: { title: '页面不存在' },
    component: () => import('./views/Error404.vue')
  },
  {
    path: '/500',
    name: 'error-500',
    meta: { title: '页面加载失败' },
    component: () => import('./views/Error500.vue')
  },
  {
    path: '/',
    component: () => import('./layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'dashboard',
        meta: { title: '控制台概览' },
        component: () => import('./views/Dashboard.vue')
      },
      {
        path: '/users',
        name: 'users',
        meta: { title: '小程序用户审核' },
        component: () => import('./views/UserReview.vue')
      },
      {
        path: '/users/manage',
        name: 'users-manage',
        meta: { title: '小程序用户管理' },
        component: () => import('./views/MiniappUserManage.vue')
      },
      {
        path: '/admin-users',
        name: 'admin-users',
        meta: { title: '后台用户管理' },
        component: () => import('./views/UserManage.vue')
      },
      {
        path: '/roles',
        name: 'roles',
        meta: { title: '角色与权限配置' },
        component: () => import('./views/RoleManage.vue')
      },
      {
        path: '/field-manage',
        name: 'field-manage',
        meta: { title: '田块管理' },
        component: () => import('./views/FieldManage.vue')
      },
      {
        path: '/crop-manage',
        name: 'crop-manage',
        meta: { title: '作物品种管理' },
        component: () => import('./views/CropManage.vue')
      },
      {
        path: '/field-cycles',
        name: 'field-cycles',
        meta: { title: '田块种植计划' },
        component: () => import('./views/FieldCycles.vue')
      },
      {
        path: '/field-cycles/field/:fieldId',
        name: 'field-cycles-detail',
        meta: { title: '田块计划详情', activeMenu: '/field-cycles' },
        component: () => import('./views/FieldCycleDetail.vue')
      },
      {
        path: '/process-templates',
        name: 'process-templates',
        meta: { title: '流程模板管理' },
        component: () => import('./views/ProcessTemplates.vue')
      },
      {
        path: '/process-templates/:templateId',
        name: 'process-template-detail',
        meta: { title: '模板详情', activeMenu: '/process-templates' },
        component: () => import('./views/ProcessTemplateDetail.vue')
      },
      {
        path: '/farm-step-dynamic-configs',
        name: 'farm-step-dynamic-configs',
        meta: { title: '农事步骤参数模板' },
        component: () => import('./views/FarmStepDynamicConfigs.vue')
      },
      {
        path: '/terminology-dict',
        name: 'terminology-dict',
        meta: { title: '术语词典管理' },
        component: () => import('./views/TerminologyDict.vue')
      },
      {
        path: '/seed-dynamic-configs',
        meta: { title: '种子参数配置' },
        redirect: '/seed-dynamic-configs/batch'
      },
      {
        path: '/seed-dynamic-configs/:configType',
        name: 'seed-dynamic-configs',
        meta: { title: '种子参数配置' },
        component: () => import('./views/SeedDynamicConfigs.vue')
      },
      {
        path: '/seed-manage',
        name: 'seed-manage',
        meta: { title: '种子批次管理' },
        component: () => import('./views/SeedManage.vue')
      },
      {
        path: '/farm-records-manage',
        name: 'farm-records-manage',
        meta: { title: '农事记录管理' },
        component: () => import('./views/FarmRecordsManage.vue')
      },
      {
        path: '/farm-records-manage/field/:fieldId',
        name: 'farm-records-field-detail',
        meta: { title: '田块记录详情', activeMenu: '/farm-records-manage' },
        component: () => import('./views/FarmRecordFieldDetail.vue')
      },
      {
        path: '/seed-rules',
        name: 'seed-rules',
        meta: { title: '种子检测规则' },
        component: () => import('./views/SeedRules.vue')
      },
      {
        path: '/amap-audit',
        name: 'amap-audit',
        meta: { title: '高德运维中心' },
        component: () => import('./views/AmapAudit.vue')
      },
      {
        path: '/operation-logs',
        name: 'operation-logs',
        meta: { title: '系统操作日志' },
        component: () => import('./views/OperationLogs.vue')
      },
      {
        path: '/messages',
        name: 'messages',
        meta: { title: '消息发布' },
        component: () => import('./views/AdminMessages.vue')
      },
      {
        path: '/message-station',
        name: 'message-station',
        meta: { title: '消息通知站' },
        component: () => import('./views/AdminMessageStation.vue')
      },
      {
        path: ASSET_CENTER_ROUTE,
        name: 'assets',
        meta: { title: '图片与资源管理' },
        component: () => import('./views/AssetsOverview.vue')
      },
      {
        path: ASSET_LIBRARY_ROUTE,
        name: 'assets-library',
        meta: { title: '资源库', activeMenu: ASSET_CENTER_ROUTE },
        component: () => import('./views/AssetsManage.vue')
      },
      {
        path: ASSET_REVIEW_ROUTE,
        name: 'assets-review',
        meta: { title: '资源审核' },
        component: () => import('./views/AssetReviewManage.vue')
      },
      {
        path: ASSET_RECYCLE_ROUTE,
        name: 'assets-recycle',
        meta: { title: '资源回收站', activeMenu: ASSET_CENTER_ROUTE },
        component: () => import('./views/AssetRecycleManage.vue')
      },
      {
        path: ASSET_FOLDERS_ROUTE,
        name: 'assets-folders',
        meta: { title: '资源目录管理', activeMenu: ASSET_CENTER_ROUTE },
        component: () => import('./views/AssetFolderManage.vue')
      },
      {
        path: '/miniapp-static-assets',
        name: 'miniapp-static-assets',
        meta: { title: '小程序静态资源', requiresSuperAdmin: true },
        component: () => import('./views/MiniappStaticAssets.vue')
      },
      {
        path: '/exports',
        name: 'exports',
        meta: { title: '记录导出' },
        component: () => import('./views/Exports.vue')
      },
      {
        path: '/export-templates',
        name: 'export-templates',
        meta: { title: '导出模板标准化' },
        component: () => import('./views/ExportTemplates.vue')
      },
      {
        path: '/record-policy',
        name: 'record-policy',
        meta: { title: '记录权限策略' },
        component: () => import('./views/RecordPolicy.vue')
      },
      {
        path: '/asset-policy',
        name: 'asset-policy',
        meta: { title: '资源上传策略' },
        component: () => import('./views/AssetPolicy.vue')
      },
      {
        path: '/company-intro',
        name: 'company-intro',
        meta: { title: '企业介绍管理' },
        component: () => import('./views/CompanyIntroManage.vue')
      },
      {
        path: '/admin-guide',
        name: 'admin-guide',
        meta: { title: '操作指引' },
        component: () => import('./views/AdminGuide.vue')
      },
      {
        path: '/system-settings',
        name: 'system-settings',
        meta: { title: '系统设置' },
        component: () => import('./views/SystemSettings.vue')
      },
      {
        path: '/profile',
        name: 'profile',
        meta: { title: '个人信息' },
        component: () => import('./views/AdminProfile.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function hasRoutePermission(menuPermissions, targetPath) {
  const target = String(targetPath || '').trim()
  if (!target) return false
  const rows = Array.isArray(menuPermissions) ? menuPermissions : []
  if (rows.includes('*')) return true
  return rows.some((item) => {
    const code = String(item || '').trim()
    if (!code) return false
    return code === target || target.startsWith(`${code}/`)
  })
}

router.beforeEach((to) => {
  const normalizedPath = normalizeAdminRoutePath(to.path)
  if (normalizedPath && normalizedPath !== to.path) {
    return {
      path: normalizedPath,
      query: to.query,
      hash: to.hash,
      replace: true
    }
  }
  if (to.path === '/login') return true
  if (to.path === '/403' || to.path === '/404' || to.path === '/500') return true
  if (!isLoggedIn()) return '/login'
  const user = getUser()
  const userType = String((user && user.userType) || '').toLowerCase()
  if (userType !== 'admin') return '/login'
  if (to.meta && to.meta.requiresSuperAdmin && Number((user && user.isSuperAdmin) || 0) !== 1) {
    return '/403'
  }
  const menuPermissions = getMenuPermissions()
  const accessPath = resolveAdminPermissionPath(String((to.meta && to.meta.activeMenu) || to.path || '').trim())
  if (accessPath === '/profile' || accessPath === '/message-station') return true
  if (!menuPermissions.length) {
    return accessPath === '/dashboard' ? true : '/403'
  }
  return hasRoutePermission(menuPermissions, accessPath) ? true : '/403'
})

export default router

