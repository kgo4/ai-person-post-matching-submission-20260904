import type { RouteRecordRaw } from 'vue-router'
import { Camera, Collection, Document, Files, Guide, Monitor, Reading, Share, View } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const knowledgeRoutes: RouteRecordRaw[] = [
  {
    path: '/kg',
    component: Layout,
    redirect: '/kg/workbench',
    meta: { title: '图谱资产', icon: Share },
    children: [
      {
        path: 'workbench',
        name: 'KgWorkbench',
        component: () => import('@/views/kg/graph-atlas/index.vue'),
        // 2026-09-04：知识图谱改挂 POST:MANAGE，并按角色锁人员/岗位域
        // （HR 走 ASSESSMENT:MANAGE 的人员能力图谱，岗位管理员走 POST:MANAGE 的岗位知识图谱）。
        // ⚠️ 这里必须与 sidebar-menu 与 path-permissions 同源 ——
        // 上轮只改了那两处、漏了路由 meta，岗位管理员点「上传并索引资料」直接被守卫拦 403。
        meta: { title: 'AI 常读图谱', icon: Monitor, requiredPermissions: ['POST:MANAGE', 'ASSESSMENT:MANAGE'] },
      },
      {
        path: 'snapshot',
        name: 'KgSnapshot',
        component: () => import('@/views/kg/snapshot/index.vue'),
        meta: { title: '图谱快照', icon: Camera, requiredPermissions: ['POST:MANAGE'] },
      },
    ],
  },
  {
    path: '/rag',
    component: Layout,
    redirect: '/rag/knowledge',
    meta: { title: '知识资产', icon: Collection },
    children: [
      {
        path: 'knowledge',
        name: 'RagKnowledge',
        component: () => import('@/views/rag/knowledge/index.vue'),
        meta: { title: 'AI 知识资产', icon: Document, requiredPermissions: ['POST:MANAGE'] },
      },
      {
        path: 'logs',
        name: 'RagLogs',
        component: () => import('@/views/rag/logs/index.vue'),
        meta: { title: '检索日志', hidden: true, requiredPermissions: ['POST:MANAGE'] },
      },
    ],
  },
  {
    path: '/learning',
    component: Layout,
    redirect: '/learning/resources',
    meta: { title: '成长建议', icon: Reading },
    children: [
      {
        path: 'resources',
        name: 'LearningResources',
        component: () => import('@/views/learning/resources/index.vue'),
        meta: { title: '资源管理', icon: Files, requiredPermissions: ['ASSESSMENT:MANAGE'] },
      },
      {
        path: 'outcome-review',
        name: 'LearningOutcomeReview',
        component: () => import('@/views/learning/outcome-review/index.vue'),
        meta: { title: '学习成果复核', icon: Collection, requiredPermissions: ['ASSESSMENT:MANAGE'] },
      },
      {
        /**
         * 员工侧「我的学习路径」。
         *
         * 与下面的 `path`（HR 工作台）是两个页面：工作台第一步是「选择人岗匹配记录」，
         * 还能搜索人员姓名 —— 员工打开自己的学习路径时会看到一堆与自己无关的选人控件。
         * 本页人员范围固定为登录人本人，员工只需在自己的匹配记录里选目标岗位。
         */
        path: 'my-path',
        name: 'MyLearningPath',
        component: () => import('@/views/learning/my-path/index.vue'),
        meta: { title: '我的学习路径', icon: Guide, requiredPermissions: ['LEARNING:SELF'] },
      },
      {
        path: 'path',
        name: 'LearningPath',
        component: () => import('@/views/learning/path/index.vue'),
        meta: { title: '学习路径', icon: Guide, requiredPermissions: ['LEARNING:SELF'] },
      },
      {
        path: 'path/:id',
        name: 'LearningPathDetail',
        component: () => import('@/views/learning/path-detail/index.vue'),
        meta: { title: '学习路径详情', hidden: true, requiredPermissions: ['LEARNING:SELF'] },
      },
      {
        path: 'path-enhanced',
        name: 'LearningPathEnhanced',
        component: () => import('@/views/learning/path-enhanced/index.vue'),
        meta: { title: '知识图谱学习路径', icon: Share, requiredPermissions: ['LEARNING:SELF'] },
      },
    ],
  },
  {
    path: '/ai-governance',
    component: Layout,
    redirect: '/ai-governance/records',
    meta: { title: 'AI 治理', icon: View },
    children: [
      {
        path: 'records',
        name: 'AiGovernanceRecords',
        component: () => import('@/views/rag/harness/index.vue'),
        meta: { title: '岗位能力巡检', icon: Document, requiredPermissions: ['POST:MANAGE'] },
      },
      {
        path: 'assessment-harness',
        name: 'AssessmentFinalHarness',
        component: () => import('@/views/rag/harness/index.vue'),
        meta: { title: '人员评估最终审核', hidden: true, requiredPermissions: ['ASSESSMENT:MANAGE'] },
      },
    ],
  },
]

export default knowledgeRoutes
