import type { RouteRecordRaw } from 'vue-router'
import { ChatDotRound, Clock, Connection, DataAnalysis, Document, List, Setting, TrendCharts, VideoPlay } from '@element-plus/icons-vue'
import { MANAGEMENT_ROLES } from '@/config/sidebar-menu'

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
        meta: { title: '发起匹配', icon: VideoPlay, requiredPermissions: ['MATCHING:EXECUTE'] },
      },
      {
        /*
         * 员工自助发起匹配（2026-09-04 独立成页）。
         *
         * 与 HR 的 /matching/execute 是**两个页面、两个权限码**，不能合并：
         *   · 人员范围：HR 可多选人员，员工固定为本人（服务端按登录身份收口，请求体不带 empId）；
         *   · 岗位数量：HR 可多岗，员工一次只能选一个；
         *   · 准入条件：员工必须先走完全部能力项审核（能力分析报告已生成）。
         * 此前该动作内嵌在「我的匹配结果」的弹窗里，员工既找不到入口，
         * 也会撞上 HR 页面的 MATCHING:EXECUTE 而收到 403。
         */
        path: 'self-execute',
        name: 'MatchingSelfExecute',
        component: () => import('@/views/matching/self-execute/index.vue'),
        meta: { title: '发起匹配', icon: VideoPlay, requiredPermissions: ['MATCHING:SELF'] },
      },
      {
        path: 'scoring-config',
        name: 'MatchingScoringConfig',
        component: () => import('@/views/matching/scoring-config/index.vue'),
        meta: { title: '全局权重配置', icon: Setting, requiredPermissions: ['MATCHING:CONFIG'] },
      },
      {
        path: 'tasks',
        name: 'MatchingTasks',
        component: () => import('@/views/matching/tasks/index.vue'),
        meta: { title: '匹配任务', icon: Clock, requiredPermissions: ['MATCHING:READ'] },
      },
      {
        // 管理端匹配结果控制台：含审批、锁定、改分、导出等动作，只对管理端角色开放。
        // 员工侧入口见 /matching/my-result。
        path: 'result',
        name: 'MatchingResult',
        component: () => import('@/views/matching/result/index.vue'),
        meta: { title: '匹配结果', icon: Document, requiredPermissions: ['NOTIFICATION:SELF'], requiredRoles: MANAGEMENT_ROLES },
      },
      {
        // 员工本人只读视图：管理端 /matching/result 含审批、锁定、改分、导出等动作，
        // 员工调用一律 403，因此员工入口单独指向本页。
        path: 'my-result',
        name: 'MatchingMyResult',
        component: () => import('@/views/matching/my-result/index.vue'),
        meta: { title: '我的匹配结果', icon: Document, requiredPermissions: ['NOTIFICATION:SELF'] },
      },
      {
        path: 'gap-diagnosis',
        name: 'MatchingGapDiagnosis',
        component: () => import('@/views/matching/gap-diagnosis/index.vue'),
        meta: { title: '综合差距诊断', icon: TrendCharts, requiredPermissions: ['NOTIFICATION:SELF'] },
      },
      {
        path: 'communication-interview',
        name: 'CommunicationInterview',
        component: () => import('@/views/matching/communication-interview/index.vue'),
        meta: { title: '视频终面', icon: VideoPlay, requiredPermissions: ['MATCHING:READ'] },
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
      // 【2026-09-04 下线】「审批任务」(`approval-tasks`) 与「审批历史」(`approval-history`)
      // 两条路由均已删除。原因见 config/sidebar-menu.ts：审批流唯一的写入方
      // `POST /approval-flow/initiate/{id}` 已无调用方，`approval_flow` 表恒空，
      // 两个页面都只能显示空状态；匹配结果不需要审批。
      // 后端端点 / 数据 / MATCHING:APPROVE 权限码保留，日后要恢复需连路由页面一起重建。
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/matching/feedback/index.vue'),
        meta: { title: '反馈数据', icon: ChatDotRound, requiredPermissions: ['MATCHING:READ'] },
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
