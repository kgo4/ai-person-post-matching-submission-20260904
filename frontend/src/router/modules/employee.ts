import type { RouteRecordRaw } from 'vue-router'
import { List, TrendCharts, UserFilled, VideoCamera } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const employeeRoutes: RouteRecordRaw = {
  path: '/employee',
  component: Layout,
  redirect: '/employee/list',
  meta: { title: '人员库', icon: UserFilled },
  children: [
    {
      path: 'list',
      name: 'EmployeeList',
      component: () => import('@/views/employee/list/index.vue'),
      meta: { title: '人员列表', icon: List },
    },
    {
      path: 'detail/:id',
      name: 'EmployeeDetail',
      component: () => import('@/views/employee/detail/index.vue'),
      meta: { title: '人员详情', hidden: true },
    },
    {
      path: 'ability-profile',
      name: 'AbilityProfile',
      component: () => import('@/views/employee/ability-profile/index.vue'),
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
    {
      path: 'ability-profile/pms-analysis',
      name: 'PmsAnalysis',
      component: () => import('@/views/employee/ability-profile/pms-analysis.vue'),
      meta: { title: '项目数据分析', hidden: true },
    },
  ],
}

export default employeeRoutes
