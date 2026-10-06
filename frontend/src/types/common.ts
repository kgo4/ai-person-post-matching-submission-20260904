/**
 * 自定义补充类型（非自动生成）
 * 自动生成的TS类型在 src/api/models.ts 中
 */

// 分页请求参数
export interface PageParams {
  current: number
  size: number
}

export interface PageResult<T = unknown> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

// 排序参数
export interface SortParams {
  prop: string
  order: 'ascending' | 'descending'
}

// 表单弹窗模式
export type DialogMode = 'add' | 'edit' | 'view'

// 树形节点
export interface TreeNode {
  id: string | number
  label: string
  children?: TreeNode[]
  disabled?: boolean
  isLeaf?: boolean
  [key: string]: any
}

// 通用键值对
export interface KeyValue<T = string> {
  label: string
  value: T
}

// 路由Meta
export interface RouteMeta {
  title: string
  icon?: string
  hidden?: boolean
  keepAlive?: boolean
  permission?: string | string[]
  roles?: string[]
  requiredRoles?: string[]
  requiredPermissions?: string[]
  breadcrumb?: boolean
}
