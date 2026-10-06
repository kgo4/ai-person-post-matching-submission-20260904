import type { RouteRecordRaw } from 'vue-router'
import { List, TrendCharts, UserFilled, VideoCamera } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const employeeRoutes: RouteRecordRaw = {
  path: '/employee',
  component: Layout,
  redirect: '/employee/ability-profile',
  meta: { title: '人员库', icon: UserFilled },
  children: [
    {
      path: 'list',
      name: 'EmployeeList',
      component: () => import('@/views/employee/list/index.vue'),
      meta: { title: '人员列表', icon: List, requiredPermissions: ['EMPLOYEE:READ'] },
    },
    {
      path: 'detail/:id',
      name: 'EmployeeDetail',
      component: () => import('@/views/employee/detail/index.vue'),
      meta: { title: '人员详情', hidden: true, requiredPermissions: ['EMPLOYEE:READ'] },
    },
    {
      path: 'ability-profile',
      name: 'AbilityProfile',
      // 角色分流入口：管理端角色→员工档案列表，员工→本人能力画像
      component: () => import('@/views/employee/ability-profile/entry.vue'),
      meta: { title: '能力画像', icon: TrendCharts },
    },
    {
      path: 'ability-profile/assessment',
      name: 'AbilityAssessment',
      component: () => import('@/views/employee/ability-profile/assessment.vue'),
      meta: { title: '能力评估流程', hidden: true },
    },
    {
      path: 'ability-profile/edit',
      name: 'AbilityProfileEdit',
      component: () => import('@/views/employee/ability-profile/ability-edit.vue'),
      meta: { title: '能力编辑', hidden: true },
    },
    {
      path: 'ability-profile/extend',
      name: 'AbilityProfileExtend',
      component: () => import('@/views/employee/ability-profile/extend-info.vue'),
      meta: { title: '扩展信息', hidden: true },
    },
    {
      path: 'ability-profile/resume-parse',
      name: 'ResumeParse',
      component: () => import('@/views/employee/ability-profile/resume-parse.vue'),
      meta: { title: '简历解析', hidden: true },
    },
    {
      path: 'ability-profile/ai-test',
      name: 'AiTest',
      component: () => import('@/views/employee/ability-profile/ai-test.vue'),
      meta: { title: 'AI能力测试', hidden: true },
    },
    {
      path: 'ability-profile/live-interview',
      name: 'LiveInterview',
      component: () => import('@/views/employee/ability-profile/live-interview.vue'),
      meta: { title: 'AI面试', icon: VideoCamera },
    },
    // PMS 项目分析是 HR 的独立功能，不在员工档案的逐行按钮上：
    // 它管理的是「在 PMS 平台已有数据、但可能还没在本系统注册」的人。
    // 因此路径挂在人员库下（/employee/pms-analysis）而不是 ability-profile 子路由 ——
    // 后者被 isAssessmentProfilePath 判为「能力画像」，会走 SELF/MANAGE 二选一放行，
    // 与 HR 专属语义不符。
    {
      path: 'pms-analysis',
      name: 'PmsRosterAnalysis',
      component: () => import('@/views/employee/pms-analysis/index.vue'),
      meta: { title: 'PMS 项目分析', hidden: true, requiredPermissions: ['ASSESSMENT:MANAGE'] },
    },
  ],
}

export default employeeRoutes
