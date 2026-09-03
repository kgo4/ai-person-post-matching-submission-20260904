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
        meta: { title: 'AI 常读图谱', icon: Monitor },
      },
      {
        path: 'snapshot',
        name: 'KgSnapshot',
        component: () => import('@/views/kg/snapshot/index.vue'),
        meta: { title: '图谱快照', icon: Camera },
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
        meta: { title: 'AI 知识资产', icon: Document },
      },
      {
        path: 'logs',
        name: 'RagLogs',
        component: () => import('@/views/rag/logs/index.vue'),
        meta: { title: '检索日志', hidden: true },
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
        meta: { title: '资源管理', icon: Files },
      },
      {
        path: 'path',
        name: 'LearningPath',
        component: () => import('@/views/learning/path/index.vue'),
        meta: { title: '学习路径', icon: Guide },
      },
      {
        path: 'path/:id',
        name: 'LearningPathDetail',
        component: () => import('@/views/learning/path-detail/index.vue'),
        meta: { title: '学习路径详情', hidden: true },
      },
      {
        path: 'path-enhanced',
        name: 'LearningPathEnhanced',
        component: () => import('@/views/learning/path-enhanced/index.vue'),
        meta: { title: '知识图谱学习路径', icon: Share },
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
        meta: { title: '岗位能力巡检', icon: Document },
      },
      {
        path: 'assessment-harness',
        name: 'AssessmentFinalHarness',
        component: () => import('@/views/rag/harness/index.vue'),
        meta: { title: '人员评估最终审核', hidden: true },
      },
    ],
  },
]

export default knowledgeRoutes
