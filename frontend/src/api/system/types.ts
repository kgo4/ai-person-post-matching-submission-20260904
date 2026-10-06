/**
 * 系统管理相关类型定义
 */

export interface SysUser {
  id: number
  username: string
  realName: string
  phone: string
  email: string
  departmentId: number
  status: number
  lastLoginTime: string
  createdTime: string
  updatedTime: string
}

export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  description: string
  dataScope: number
  status: number
  createdTime: string
}

export interface AbilityTag {
  id: number
  tagCode: string
  tagName: string
  parentId: number
  tagCategory: string
  tagLevel: number
  description: string
  sortOrder: number
  status: number
}

export interface SysExtendField {
  id: number
  businessModule: string
  fieldName: string
  fieldLabel: string
  fieldType: string
  selectOptions: string
  isRequired: number
  sortOrder: number
  status: number
}

export interface SysOperationLog {
  id: number
  userId: number
  realName: string
  operationModule: string
  operationType: string
  operationDesc: string
  requestMethod: string
  requestUrl: string
  operationIp: string
  operationTime: string
  costTime: number
}

export interface SourceWeightConfig {
  id: number
  sourceType: string
  sourceLabel: string
  weight: number
  isActive: number
  sortOrder: number
  remark: string
  createdTime: string
  updatedTime: string
}

export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  token: string
  userId: number
  /** 关联的人员档案 ID，员工“仅本人”链路的身份来源；未绑定人员档案时为 null */
  empId?: number | null
  username: string
  realName: string
  /** 头像访问路径；登录时一并下发，避免顶栏要等下一次 /current 才显示头像 */
  avatar?: string | null
  roles: string[]
  permissions: string[]
}

export interface UserSaveDTO {
  id?: number
  username: string
  password?: string
  realName: string
  phone?: string
  email?: string
  departmentId?: number
  status?: number
}

export interface UserVO {
  id: number
  /** 关联的人员档案 ID，员工“仅本人”链路的身份来源；未绑定人员档案时为 null */
  empId?: number | null
  username: string
  realName: string
  phone: string
  email: string
  /** 头像访问路径；为空时按姓名首字展示 */
  avatar?: string | null
  departmentId: number
  status: number
  lastLoginTime: string
  createdTime: string
  roles?: string[]
  permissions?: string[]
}

/**
 * 个人中心资料。
 *
 * 与 UserVO 的唯一差别是 phone **不脱敏** —— UserVO 用于管理端列表（手机号打码），
 * 而个人中心要让人编辑自己的手机号，打码后填不回原值。
 */
export interface MyProfileVO {
  id: number
  /** 用户名（只读：登录凭据） */
  username: string
  /** 真实姓名（只读：与人员档案映射，需管理端修改） */
  realName: string
  /** 手机号（未脱敏） */
  phone?: string | null
  email?: string | null
  /** 头像访问路径 */
  avatar?: string | null
  /** 关联的人员档案 ID；null 表示该账号未绑定人员档案 */
  empId?: number | null
  roles?: string[]
  lastLoginTime?: string | null
}

/**
 * 个人中心资料更新请求。
 *
 * 三个字段都可选：**不传（undefined）表示不修改，传空串表示清空**。
 * 这是刻意的语义区分 —— 否则「清空手机号/邮箱」会被当成「未修改」而静默失败。
 */
export interface MyProfileUpdateDTO {
  phone?: string | null
  email?: string | null
  avatar?: string | null
}

export interface RoleSaveDTO {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  dataScope?: number
  status?: number
}

export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  description: string
  dataScope: number
  status: number
  createdTime: string
}

export interface AbilityTagSaveDTO {
  id?: number
  tagCode: string
  tagName: string
  parentId?: number
  tagCategory: string
  tagLevel: number
  description?: string
  sortOrder?: number
}

export type AbilityTagCreateDTO = Omit<AbilityTagSaveDTO, 'tagCode'> & {
  tagCode?: string
}

export interface AbilityTagTreeVO {
  id: number
  tagCode: string
  tagName: string
  tagCategory: string
  tagLevel: number
  children: AbilityTagTreeVO[]
}

export interface SkillTaxonomyMap {
  id?: number
  skillName: string
  abilityTagId: number
  category?: string
  confidence?: number
  source?: string
  status?: number
  createdTime?: string
  updatedTime?: string
}

export interface ExtendFieldConfigDTO {
  id?: number
  businessModule: string
  fieldName: string
  fieldLabel: string
  fieldType: string
  selectOptions?: string
  isRequired?: number
  sortOrder?: number
  status?: number
}

export interface ExtendFieldVO {
  id: number
  businessModule: string
  fieldName: string
  fieldLabel: string
  fieldType: string
  selectOptions: string
  isRequired: number
  sortOrder: number
  status: number
}

export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
}

export type { PageResult as PageResultVO } from '@/types/common'
