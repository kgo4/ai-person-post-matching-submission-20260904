import type { RouteRecordRaw } from 'vue-router'
import { Bell, ChatDotRound, Clock, Connection, DataAnalysis, Document, List, Setting, TrendCharts, VideoPlay } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const matchingRoutes: RouteRecordRaw[] = [
  {
    path: '/matching',
    component: Layout,
    redirect: '/matching/execute',
    meta: { title: '图谱匹配', icon: Connection },
    children: [
      {
        path: 'execute',
        name: 'MatchingExecute',
        component: () => import('@/views/matching/execute/index.vue'),
        meta: { title: '发起匹配', icon: VideoPlay },
      },
      {
        path: 'scoring-config',
        name: 'MatchingScoringConfig',
        component: () => import('@/views/matching/scoring-config/index.vue'),
        meta: { title: '全局权重配置', icon: Setting, requiredRoles: ['ADMIN'] },
      },
      {
        path: 'tasks',
        name: 'MatchingTasks',
        component: () => import('@/views/matching/tasks/index.vue'),
        meta: { title: '匹配任务', icon: Clock },
      },
      {
        path: 'result',
        name: 'MatchingResult',
        component: () => import('@/views/matching/result/index.vue'),
        meta: { title: '匹配结果', icon: Document },
      },
      {
        path: 'gap-diagnosis',
        name: 'MatchingGapDiagnosis',
        component: () => import('@/views/matching/gap-diagnosis/index.vue'),
        meta: { title: '综合差距诊断', icon: TrendCharts },
      },
      {
        path: 'detail/:id',
        name: 'MatchingDetail',
        component: () => import('@/views/matching/detail/index.vue'),
        meta: { title: '匹配详情', hidden: true },
      },
      {
        path: 'black-white-list',
        name: 'BlackWhiteList',
        component: () => import('@/views/matching/black-white-list/index.vue'),
        meta: { title: '黑白名单', icon: List },
      },
      {
        path: 'approval-tasks',
        name: 'ApprovalTasks',
        component: () => import('@/views/matching/approval-tasks/index.vue'),
        meta: { title: '待办任务', icon: Bell },
      },
      {
        path: 'approval-history',
        name: 'ApprovalHistory',
        component: () => import('@/views/matching/approval-history/index.vue'),
        meta: { title: '审批历史', icon: Clock },
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/matching/feedback/index.vue'),
        meta: { title: '反馈数据', icon: ChatDotRound },
      },
    ],
    },
  {
    path: '/matching/calibration',
    component: Layout,
    meta: { title: '匹配校准数据', icon: DataAnalysis },
    children: [
      {
        path: '',
        name: 'MatchingCalibration',
        component: () => import('@/views/matching/calibration/index.vue'),
        meta: { title: '匹配校准数据', icon: DataAnalysis },
      },
    ],
  },
]

export default matchingRoutes
