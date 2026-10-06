import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

import systemRoutes from './modules/system'
import employeeRoutes from './modules/employee'
import postRoutes from './modules/post'
import matchingRoutes from './modules/matching'
import knowledgeRoutes from './modules/knowledge'
import { isAssessmentProfilePath, satisfiesPathPermission } from './path-permissions'
import { isSuperAdmin } from '@/utils/super-admin'
import { installChunkLoadGuards } from '@/utils/chunk-reload-guard'

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
    redirect: '/workbench',
    children: [
      {
        // 2026-09-04：独立「数据看板」已并入工作台的「匹配分析」区块（仅 HR 渲染）。
        // 这里保留重定向而不是直接删路由：历史书签与外部链接仍能落到工作台，不会 404。
        path: 'dashboard',
        name: 'Dashboard',
        redirect: '/workbench',
        meta: { title: '工作台', hidden: true },
      },
    ],
  },
  {
    path: '/workbench',
    component: Layout,
    name: 'RoleWorkbench',
    meta: { title: '工作台' },
    children: [{
      path: '',
      name: 'RoleWorkbenchHome',
      component: () => import('@/views/workbench/RoleWorkbench.vue'),
      meta: { title: '工作台' },
    }],
  },
  {
    /**
     * 个人中心：顶栏右上角账号入口的落地页。
     *
     * 刻意**不是**侧边栏模块（hidden）：它属于「账号」而不是某个业务域，
     * 所有角色共用，无需权限码 —— 后端 /api/system/user/profile 也只要求登录，
     * 因为只能操作当前登录人自己。所以这里不能挂 requiredPermissions，
     * 否则员工进不去自己的个人中心。
     */
    path: '/profile',
    component: Layout,
    name: 'PersonalCenter',
    meta: { title: '个人中心', hidden: true },
    children: [{
      path: '',
      name: 'PersonalCenterHome',
      component: () => import('@/views/profile/index.vue'),
      meta: { title: '个人中心', hidden: true },
    }],
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
    // 与 404 分开：复用 404 组件会把「权限不足」误报为「页面不存在」
    component: () => import('@/views/error/403.vue'),
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

// 懒加载 chunk 拉取失败兜底（部署后旧标签页引用已覆盖的旧 chunk → 404）。
// 必须在这里装：早于 main.ts 的 app.use(router) 触发的首次导航。
installChunkLoadGuards(router)


/**
 * 无需登录即可访问的页面。
 *
 * 为什么抽成常量而不是散在守卫里写 `to.path === '...'`：漏加一个路径的表现是
 * **「这个页面永远进不去」**，而且没有任何报错 —— 只有把这些路径集中列出来，
 * 新增公开页时才可能被发现漏了。
 */
const PUBLIC_PATHS = ['/login', '/register']

// 路由守卫
router.beforeEach(async (to, _from, next) => {

  const userStore = useUserStore()

  // 公开页面：未登录也要能进。
  //
  // ⚠️ 这里必须是**白名单**，且注册页一定要在名单里：
  // 原先只放行了 /login，/register 会落到下面的 token 检查 →
  // 「未登录点注册 → 被重定向回登录页」，注册页永远进不去（自相矛盾）。
  // 新增公开页时记得同时加进这个集合。
  if (PUBLIC_PATHS.includes(to.path)) {
    // 已登录用户再点「登录/注册」没有意义，直接回首页；未登录则放行。
    // ⚠️ 不能写 `next(token ? '/' : undefined)` —— vue-router 的类型定义里
    // `next(undefined)` 不匹配任何重载（会报 TS2769）。必须显式分支调用。
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

  // 超级管理员：可访问所有角色的所有功能 —— 直接放行，不再逐项判定角色与权限码。
  // 放在这里（身份已加载、其余判定之前）而不是散在各处，是为了保证「超管一定进得去」，
  // 否则新增一条带 requiredRoles/requiredPermissions 的路由就会把超管挡在门外。
  if (isSuperAdmin(userStore.roles)) {
    next()
    return
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

  // 「路径 → 权限码」规则收敛在 path-permissions.ts，
  // 与侧边栏显隐、回归测试共用同一份规则，避免三方漂移。
  const assessmentPath = isAssessmentProfilePath(to.path)
  const assessmentAllowed = userStore.permissions.includes('ASSESSMENT:SELF') || userStore.permissions.includes('ASSESSMENT:MANAGE')
  if (assessmentPath && !assessmentAllowed) {
    next('/403')
    return
  }
  if (!satisfiesPathPermission(to.path, userStore.permissions)) {
    next('/403')
    return
  }

  next()
})

export default router
