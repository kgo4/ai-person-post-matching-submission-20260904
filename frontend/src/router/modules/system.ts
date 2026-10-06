import type { RouteRecordRaw } from 'vue-router'
import { Collection, Cpu, DataAnalysis, Document, Grid, Lock, Setting, TrendCharts, User } from '@element-plus/icons-vue'

const Layout = () => import('@/views/layout/index.vue')

const systemRoutes: RouteRecordRaw = {
  path: '/system',
  component: Layout,
  // 原 redirect 指向 /system/ability-tag（能力标签治理）；该页已下线，
  // 落到同模块的入口页 /system/extend-field，避免进入死路由。
  redirect: '/system/extend-field',
  meta: { title: '系统管理', icon: Setting },
  children: [
    {
      path: 'extend-field',
      name: 'ExtendField',
      component: () => import('@/views/system/extend-field/index.vue'),
      meta: { title: '扩展字段配置', icon: Grid, requiredPermissions: ['ASSESSMENT:CONFIG'] },
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
      meta: { title: '用户管理', icon: User, requiredPermissions: ['USER:MANAGE'] },
    },
    {
      path: 'role',
      name: 'SystemRole',
      component: () => import('@/views/system/role/index.vue'),
      meta: { title: '角色权限', icon: Lock, requiredPermissions: ['ROLE:MANAGE'] },
    },
    {
      path: 'operation-log',
      name: 'OperationLog',
      component: () => import('@/views/system/operation-log/index.vue'),
      meta: { title: '操作日志', icon: Document, requiredPermissions: ['AUDIT:READ'] },
    },
    {
      // 运行审计：AI 运行时的 token 消耗 / 响应时间 / Prompt 与 RAG 调用明细。
      // 与操作日志同权限码（AUDIT:READ），同属「审计」域，都归 PLATFORM_ADMIN。
      path: 'runtime-audit',
      name: 'RuntimeAudit',
      component: () => import('@/views/system/runtime-audit/index.vue'),
      meta: { title: '运行审计', icon: DataAnalysis, requiredPermissions: ['AUDIT:READ'] },
    },
    {
      path: 'ai-model-config',
      name: 'AiModelConfig',
      component: () => import('@/views/system/ai-model-config/index.vue'),
      meta: { title: '企业 AI 模型配置', icon: Cpu, requiredPermissions: ['AI:CONFIG'] },
    },
    {
      path: 'agent-memory',
      name: 'AgentMemory',
      component: () => import('@/views/employee/ability-profile/agent-memory.vue'),
      meta: { title: 'Agent 记忆管理', icon: Collection, requiredPermissions: ['AI:CONFIG'] },
    },
    {
      path: 'source-weight',
      name: 'SourceWeight',
      component: () => import('@/views/system/source-weight/index.vue'),
      meta: { title: '来源权重配置', icon: TrendCharts, requiredPermissions: ['MATCHING:CONFIG'] },
    },
    {
      path: 'governance-filter-rules',
      name: 'GovernanceFilterRules',
      component: () => import('@/views/system/governance-filter-rules/index.vue'),
      meta: { title: '数据治理规则', icon: Setting, requiredPermissions: ['POST:MANAGE'] },
    },
  ],
}

export default systemRoutes
