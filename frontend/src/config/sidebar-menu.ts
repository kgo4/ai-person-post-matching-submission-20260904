import type { Component } from 'vue'
import { Briefcase, Collection, Connection, DataAnalysis, DataBoard, Reading, Setting, UserFilled, View } from '@element-plus/icons-vue'

export interface SidebarChild {
  label: string
  path: string
  beta?: boolean
}

export interface SidebarModule {
  key: string
  label: string
  summary: string
  path: string
  icon: Component
  children: SidebarChild[]
}

export const MODULE_ORDER = ['dashboard', 'employee', 'post', 'matching', 'learning', 'contest', 'knowledge-assets', 'ai-governance', 'system'] as const

export function getSidebarModules(): SidebarModule[] {
  return [
    {
      key: 'dashboard',
      label: '仪表盘',
      summary: '全局运行态势',
      path: '/dashboard',
      icon: DataBoard,
      children: [{ label: '仪表盘', path: '/dashboard' }],
    },
    {
      key: 'employee',
      label: '员工能力',
      summary: '能力采集、画像与档案',
      path: '/employee/ability-profile',
      icon: UserFilled,
      children: [
        { label: '能力画像', path: '/employee/ability-profile' },
        { label: '员工档案', path: '/employee/list' },
      ],
    },
    {
      key: 'post',
      label: '岗位模型',
      summary: '岗位建模、模板与能力配置',
      path: '/post/model-config',
      icon: Briefcase,
      children: [
        { label: '能力配置', path: '/post/model-config' },
        { label: '全景图谱', path: '/post/panorama' },
        { label: '模型发布记录', path: '/post/model-version' },
        { label: '岗位演化', path: '/post/evolution' },
        { label: '岗位档案', path: '/post/list' },
        { label: 'JD 批量导入', path: '/post/excel-import' },
        { label: '新兴岗位', path: '/post/emerging-post' },
      ],
    },
    {
      key: 'matching',
      label: '人岗匹配',
      summary: '匹配执行与差距诊断',
      path: '/matching/execute',
      icon: Connection,
      children: [
        { label: '发起匹配', path: '/matching/execute' },
        { label: '全局权重配置', path: '/matching/scoring-config' },
        { label: '匹配任务', path: '/matching/tasks' },
        { label: '匹配结果', path: '/matching/result' },
        { label: '差距诊断', path: '/matching/gap-diagnosis' },
        { label: '审批任务', path: '/matching/approval-tasks' },
        { label: '黑白名单', path: '/matching/black-white-list' },
        { label: '匹配校准数据', path: '/matching/calibration' },
      ],
    },
    {
      key: 'learning',
      label: '学习成长',
      summary: '学习路径与资源管理',
      path: '/learning/path',
      icon: Reading,
      children: [
        { label: '学习路径', path: '/learning/path' },
        { label: '资源管理', path: '/learning/resources' },
      ],
    },
    {
      key: 'contest',
      label: '可信数据',
      summary: '证据中心与标签治理',
      path: '/capability-brain/evidence',
      icon: DataAnalysis,
      children: [
        { label: '证据中心', path: '/capability-brain/evidence' },
        { label: '能力标签治理', path: '/system/ability-tag' },
      ],
    },
    {
      key: 'knowledge-assets',
      label: '知识资产',
      summary: 'AI 资料底库与关系图谱',
      path: '/rag/knowledge',
      icon: Collection,
      children: [
        { label: 'AI 知识资产', path: '/rag/knowledge' },
        { label: 'AI 常读图谱', path: '/kg/workbench' },
      ],
    },
    {
      key: 'ai-governance',
      label: 'AI 治理',
      summary: '岗位能力巡检',
      path: '/ai-governance/records',
      icon: View,
      children: [
        { label: '岗位能力巡检', path: '/ai-governance/records' },
        { label: '过滤规则配置', path: '/system/governance-filter-rules' },
      ],
    },
    {
      key: 'system',
      label: '系统工具',
      summary: '检索知识库与系统设置',
      path: '/system/extend-field',
      icon: Setting,
      children: [
        { label: '系统设置', path: '/system/extend-field' },
        { label: 'Agent 记忆管理', path: '/system/agent-memory' },
        { label: '企业 AI 模型配置', path: '/system/ai-model-config' },
        { label: '来源权重配置', path: '/system/source-weight' },
        { label: '用户管理', path: '/system/user' },
        { label: '角色权限', path: '/system/role' },
      ],
    },
  ]
}
