import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { DataBoard } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

import systemRoutes from './modules/system'
import employeeRoutes from './modules/employee'
import postRoutes from './modules/post'
import matchingRoutes from './modules/matching'
import knowledgeRoutes from './modules/knowledge'

// ============================================================================
// Legacy Route Compatibility Table
// ============================================================================
// Old URL Pattern                        → New Location                      Target Removal
// ----------------------------------------------------------------------------
// /contest/overview                      → /capability-brain/evidence        v2.0
// /contest/report                        → /capability-brain/evidence        v2.0
// /contest/report/detail/:id             → /capability-brain/evidence        v2.0
// /contest/evidence                     → /capability-brain/evidence        v2.0
// /contest/* (catch-all)                → /capability-brain/evidence        v2.0
// /capability-brain/overview            → /capability-brain/evidence        v2.0
// /capability-brain/report              → /capability-brain/evidence        v2.0
// /capability-brain/rag*                → /rag/*                             v2.0
// /capability-brain/harness             → /ai-governance/records            v2.0
// /capability-brain/evolution*          → /post/evolution*                  v2.0
// /capability-brain/kg*                 → /kg/*                             v2.0
// /capability-brain/learning*           → /learning/*                       v2.0
//
// Old API concept names (contest) → new domain (capability-brain):
//   contest evidence  → 能力证据中心 (capability-brain/evidence)
//   contest cockpit   → REMOVED (2024 migration)
//
// TODO: remove by v2.0 — all /contest/* redirects and legacy capability-brain
//       redirects should be dropped once no external links point to them.
// ============================================================================

const Layout = () => import('@/views/layout/index.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/register.vue'),
    meta: { title: '注册', hidden: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: DataBoard },
      },
    ],
  },
  {
    path: '/capability-brain',
    component: Layout,
    redirect: '/capability-brain/evidence',
    meta: { hidden: true },
    children: [
      {
        path: 'evidence',
        name: 'CapabilityBrainEvidence',
        component: () => import('@/views/capability-brain/evidence/index.vue'),
        meta: { title: '证据中心', hidden: true },
      },
      // legacy redirects (TODO: remove by v2.0)
      { path: 'overview', redirect: '/capability-brain/evidence', meta: { hidden: true } },
      { path: 'rag/knowledge', redirect: '/rag/knowledge', meta: { hidden: true } },
      { path: 'rag/logs', redirect: '/rag/knowledge', meta: { hidden: true } },
      { path: 'harness', redirect: '/ai-governance/records', meta: { hidden: true } },
      { path: 'evolution', redirect: '/post/evolution', meta: { hidden: true } },
      { path: 'evolution/detail/:id', redirect: '/post/evolution', meta: { hidden: true } },
      { path: 'kg/workbench', redirect: '/kg/workbench', meta: { hidden: true } },
      { path: 'kg/snapshot', redirect: '/kg/snapshot', meta: { hidden: true } },
      { path: 'learning/resources', redirect: '/learning/resources', meta: { hidden: true } },
      { path: 'learning/path', redirect: '/learning/path', meta: { hidden: true } },
      { path: 'report', redirect: '/capability-brain/evidence', meta: { hidden: true } },
      { path: 'report/detail/:id', redirect: '/capability-brain/evidence', meta: { hidden: true } },
    ],
  },
  systemRoutes,
  employeeRoutes,
  postRoutes,
  ...matchingRoutes,
  // /contest/* → /capability-brain/*（旧 URL 兼容，2024 年竞赛中心已迁移至能力大脑）
  {
    path: '/contest/:pathMatch(.*)*',
    redirect: '/capability-brain/evidence',
    meta: { hidden: true },
  },
  ...knowledgeRoutes,
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '403', hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})


// 路由守卫
router.beforeEach(async (to, _from, next) => {

  const userStore = useUserStore()

  if (to.path === '/login') {
    if (userStore.token) {
      next('/')
    } else {
      next()
    }
    return
  }

  if (!userStore.token) {
    next(`/login?redirect=${to.path}`)
    return
  }

  // 加载用户身份信息（如果尚未加载）
  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch {
      userStore.logout()
      next(`/login?redirect=${to.path}`)
      return
    }
  }

  // 检查路由元信息中的角色和权限要求
  const requiredRoles = (to.meta.requiredRoles as string[] | undefined) ?? []
  const requiredPermissions = (to.meta.requiredPermissions as string[] | undefined) ?? []

  if (requiredRoles.length > 0) {
    const normalizedRoles = userStore.roles
    const hasRequiredRole = requiredRoles.some(r =>
      normalizedRoles.includes(r.toUpperCase())
    )
    if (!hasRequiredRole) {
      next('/403')
      return
    }
  }

  if (requiredPermissions.length > 0) {
    const hasRequiredPerm = requiredPermissions.some(p =>
      userStore.permissions.includes(p)
    )
    if (!hasRequiredPerm) {
      next('/403')
      return
    }
  }

  next()
})

export default router
