/**
 * 系统管理 API
 */
import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type {
  LoginDTO,
  LoginVO,
  UserVO,
  UserSaveDTO,
  ChangePasswordDTO,
  RoleVO,
  RoleSaveDTO,
  AbilityTag,
  AbilityTagSaveDTO,
  AbilityTagCreateDTO,
  AbilityTagTreeVO,
  SkillTaxonomyMap,
  ExtendFieldVO,
  ExtendFieldConfigDTO,
  SysOperationLog,
  PageResultVO,
} from './types'

// ===================== Login =====================

/** 用户登录 */
export function login(data: LoginDTO): Promise<ApiResponse<LoginVO>> {
  return post<LoginVO>('/system/user/login', data)
}

/** 用户注册 */
export function register(data: UserSaveDTO): Promise<ApiResponse<LoginVO>> {
  return post<LoginVO>('/system/user/register', data)
}

// ===================== User =====================

/** 分页查询用户 */
export function pageUsers(params: PageParams): Promise<ApiResponse<PageResultVO<UserVO>>> {
  return get<PageResultVO<UserVO>>('/system/user/page', params)
}

/** 获取当前登录用户 */
export function getCurrentUser(): Promise<ApiResponse<UserVO>> {
  return get<UserVO>('/system/user/current')
}

/** 新增用户 */
export function saveUser(data: UserSaveDTO): Promise<ApiResponse<void>> {
  return post<void>('/system/user', data)
}

/** 更新用户 */
export function updateUser(id: number, data: UserSaveDTO): Promise<ApiResponse<void>> {
  return put<void>(`/system/user/${id}`, data)
}

/** 删除用户 */
export function deleteUser(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/system/user/${id}`)
}

/** 修改密码 */
export function changePassword(data: ChangePasswordDTO): Promise<ApiResponse<void>> {
  return put<void>('/system/user/change-password', data)
}

/** 重置用户密码 */
export function resetPassword(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/system/user/${id}/reset-password`)
}

/** 更新用户状态 */
export function updateUserStatus(id: number, status: number): Promise<ApiResponse<void>> {
  return put<void>(`/system/user/${id}/status`, { status })
}

// ===================== Role =====================

/** 分页查询角色 */
export function pageRoles(params: PageParams): Promise<ApiResponse<PageResultVO<RoleVO>>> {
  return get<PageResultVO<RoleVO>>('/system/role/page', params)
}

/** 查询所有启用角色 */
export function listEnabledRoles(): Promise<ApiResponse<RoleVO[]>> {
  return get<RoleVO[]>('/system/role/enabled')
}

/** 新增角色 */
export function saveRole(data: RoleSaveDTO): Promise<ApiResponse<void>> {
  return post<void>('/system/role', data)
}

/** 更新角色 */
export function updateRole(id: number, data: RoleSaveDTO): Promise<ApiResponse<void>> {
  return put<void>(`/system/role/${id}`, data)
}

/** 删除角色 */
export function deleteRole(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/system/role/${id}`)
}

/** 给用户分配角色 */
export function assignRoles(userId: number, roleIds: number[]): Promise<ApiResponse<void>> {
  return put<void>(`/system/role/assign/${userId}`, roleIds)
}

/** 获取用户拥有的角色ID列表 */
export function getUserRoleIds(userId: number): Promise<ApiResponse<number[]>> {
  return get<number[]>(`/system/role/user-roles/${userId}`)
}

// ===================== Ability Tag =====================

/** 获取能力标签树 */
export function getTagTree(): Promise<ApiResponse<AbilityTagTreeVO[]>> {
  return get<AbilityTagTreeVO[]>('/system/ability-tag/tree')
}

/** 按分类获取能力标签树 */
export function getTagTreeByCategory(category: string): Promise<ApiResponse<AbilityTagTreeVO[]>> {
  return get<AbilityTagTreeVO[]>(`/system/ability-tag/tree/${category}`)
}

/** 分页查询能力标签 */
export function pageTags(params: PageParams): Promise<ApiResponse<PageResultVO<AbilityTag>>> {
  return get<PageResultVO<AbilityTag>>('/system/ability-tag/page', params)
}

/** 根据ID查询能力标签 */
export function getTagById(id: number): Promise<ApiResponse<AbilityTag>> {
  return get<AbilityTag>(`/system/ability-tag/${id}`)
}

/** 新增能力标签 */
export function saveTag(data: AbilityTagCreateDTO): Promise<ApiResponse<void>> {
  return post<void>('/system/ability-tag', data)
}

/** 更新能力标签 */
export function updateTag(id: number, data: AbilityTagSaveDTO): Promise<ApiResponse<void>> {
  return put<void>(`/system/ability-tag/${id}`, data)
}

/** 更新能力标签状态 */
export function updateTagStatus(id: number, status: number): Promise<ApiResponse<void>> {
  return put<void>(`/system/ability-tag/${id}/status`, { status })
}

/** 删除能力标签 */
export function deleteTag(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/system/ability-tag/${id}`)
}

/** 批量生成标签向量（为缺少向量的标签补全嵌入） */
export function batchGenerateTagVectors(): Promise<ApiResponse<number>> {
  return post<number>('/system/ability-tag/batch-generate-vectors')
}

// ===================== Skill Taxonomy Map =====================

/** 分页查询技能归层规则 */
export function pageSkillTaxonomyRules(
  params: PageParams & { keyword?: string; abilityTagId?: number },
): Promise<ApiResponse<PageResultVO<SkillTaxonomyMap>>> {
  return get<PageResultVO<SkillTaxonomyMap>>('/system/skill-taxonomy/page', params)
}

/** 新增技能归层规则 */
export function createSkillTaxonomyRule(data: SkillTaxonomyMap): Promise<ApiResponse<SkillTaxonomyMap>> {
  return post<SkillTaxonomyMap>('/system/skill-taxonomy', data)
}

/** 更新技能归层规则 */
export function updateSkillTaxonomyRule(id: number, data: SkillTaxonomyMap): Promise<ApiResponse<SkillTaxonomyMap>> {
  return put<SkillTaxonomyMap>(`/system/skill-taxonomy/${id}`, data)
}

/** 启用/停用规则 */
export function updateSkillTaxonomyRuleStatus(id: number, status: number): Promise<ApiResponse<void>> {
  return put<void>(`/system/skill-taxonomy/${id}/status`, null, { params: { status } })
}

/** 删除技能归层规则 */
export function deleteSkillTaxonomyRule(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/system/skill-taxonomy/${id}`)
}

// ===================== Extend Field =====================

/** 按业务模块查询扩展字段 */
export function listFieldsByModule(module: string): Promise<ApiResponse<ExtendFieldVO[]>> {
  return get<ExtendFieldVO[]>(`/system/extend-field/module/${module}`)
}

/** 分页查询扩展字段 */
export function pageFields(params: PageParams): Promise<ApiResponse<PageResultVO<ExtendFieldVO>>> {
  return get<PageResultVO<ExtendFieldVO>>('/system/extend-field/page', params)
}

/** 新增扩展字段 */
export function saveField(data: ExtendFieldConfigDTO): Promise<ApiResponse<void>> {
  return post<void>('/system/extend-field', data)
}

/** 查询扩展字段详情 */
export function getFieldById(id: number): Promise<ApiResponse<ExtendFieldVO>> {
  return get<ExtendFieldVO>(`/system/extend-field/${id}`)
}

/** 更新扩展字段 */
export function updateField(id: number, data: ExtendFieldConfigDTO): Promise<ApiResponse<void>> {
  return put<void>(`/system/extend-field/${id}`, data)
}

/** 删除扩展字段 */
export function deleteField(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/system/extend-field/${id}`)
}

// ===================== Operation Log =====================

/** 分页查询操作日志 */
export function pageLogs(params: PageParams): Promise<ApiResponse<PageResultVO<SysOperationLog>>> {
  return get<PageResultVO<SysOperationLog>>('/system/operation-log/page', params)
}

// ===================== Enterprise AI Model Config =====================

export interface SystemAiModelConfig {
  id: number
  enabled: boolean
  baseUrl?: string
  modelName?: string
  apiKey?: string
  apiKeyConfigured?: boolean
  timeoutSeconds?: number
  temperature?: number
  testQuestionCount?: number
  interviewQuestionCount?: number
  updatedBy?: number
  updatedTime?: string
}

export function getAiModelConfig(): Promise<ApiResponse<SystemAiModelConfig>> {
  return get<SystemAiModelConfig>('/system/ai-model-config')
}

export function saveAiModelConfig(data: SystemAiModelConfig): Promise<ApiResponse<SystemAiModelConfig>> {
  return put<SystemAiModelConfig>('/system/ai-model-config', data)
}

export function healthCheckAiModel(): Promise<ApiResponse<Record<string, any>>> {
  return post<Record<string, any>>('/system/ai-model-config/health-check')
}
