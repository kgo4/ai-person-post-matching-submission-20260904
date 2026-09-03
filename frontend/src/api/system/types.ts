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
  username: string
  realName: string
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
  username: string
  realName: string
  phone: string
  email: string
  departmentId: number
  status: number
  lastLoginTime: string
  createdTime: string
  roles?: string[]
  permissions?: string[]
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
