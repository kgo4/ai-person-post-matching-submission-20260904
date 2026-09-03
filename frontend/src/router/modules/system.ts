import type { RouteRecordRaw } from 'vue-router'
import { Collection, Cpu, Document, Grid, Lock, PriceTag, Setting, TrendCharts, User } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const systemRoutes: RouteRecordRaw = {
  path: '/system',
  component: Layout,
  redirect: '/system/ability-tag',
  meta: { title: '系统管理', icon: Setting },
  children: [
    {
      path: 'ability-tag',
      name: 'AbilityTag',
      component: () => import('@/views/system/ability-tag/index.vue'),
      meta: { title: '能力标签治理', icon: PriceTag },
    },
    {
      path: 'extend-field',
      name: 'ExtendField',
      component: () => import('@/views/system/extend-field/index.vue'),
      meta: { title: '扩展字段配置', icon: Grid },
    },
    {
      path: 'extend-field/config',
      name: 'ExtendFieldConfig',
      component: () => import('@/views/system/extend-field/config.vue'),
      meta: { title: '字段配置', hidden: true, keepAlive: true },
    },
    {
      path: 'user',
      name: 'SystemUser',
      component: () => import('@/views/system/user/index.vue'),
      meta: { title: '用户管理', icon: User },
    },
    {
      path: 'role',
      name: 'SystemRole',
      component: () => import('@/views/system/role/index.vue'),
      meta: { title: '角色权限', icon: Lock },
    },
    {
      path: 'operation-log',
      name: 'OperationLog',
      component: () => import('@/views/system/operation-log/index.vue'),
      meta: { title: '操作日志', icon: Document },
    },
    {
      path: 'tag-governance',
      name: 'TagGovernance',
      redirect: '/system/ability-tag',
      meta: { title: '标签治理中心', hidden: true },
    },
    {
      path: 'ai-model-config',
      name: 'AiModelConfig',
      component: () => import('@/views/system/ai-model-config/index.vue'),
      meta: { title: '企业 AI 模型配置', icon: Cpu },
    },
    {
      path: 'agent-memory',
      name: 'AgentMemory',
      component: () => import('@/views/employee/ability-profile/agent-memory.vue'),
      meta: { title: 'Agent 记忆管理', icon: Collection },
    },
    {
      path: 'source-weight',
      name: 'SourceWeight',
      component: () => import('@/views/system/source-weight/index.vue'),
      meta: { title: '来源权重配置', icon: TrendCharts },
    },
    {
      path: 'governance-filter-rules',
      name: 'GovernanceFilterRules',
      component: () => import('@/views/system/governance-filter-rules/index.vue'),
      meta: { title: '数据治理规则', icon: Setting },
    },
  ],
}

export default systemRoutes
