/**
 * 人员能力治理 API
 * <p>
 * 人工修改最终入库能力标签、等级，记录治理事件，生成Agent记忆。
 */
import { get, post, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageResult } from '@/types/common'

// ===================== Types =====================

/** 治理事件 */
export interface PersonAbilityGovernanceEvent {
  id: number
  empId: number
  oldTagId: number
  oldTagName: string
  newTagId: number
  newTagName: string
  oldLevel: number
  newLevel: number
  oldConfidence: number
  newConfidence: number
  sourceBreakdownJson: string
  evidenceSnapshotJson: string
  modifyType: string
  modifyReason: string
  templatePayloadJson: string
  generateMemory: number
  memoryId: number
  createdBy: number
  createdTime: string
}

/** Agent 记忆 */
export interface AgentMemory {
  id: number
  memoryType: string
  title: string
  content: string
  triggerExpressionsJson: string
  applicableScope: string
  priority: number
  status: string
  sourceEventId: number
  useCount: number
  lastUsedTime: string
  expireTime: string
  createdBy: number
  createdTime: string
  updatedTime: string
}

/** 能力证据 */
export interface AbilityEvidence {
  id: number
  sourceType: string
  sourceRefId: number
  abilityName: string
  evidenceText: string
  harnessDecision: string
  harnessScore: number
  extractedTime: string
}

/** 人员能力画像详情 */
export interface PersonAbilityProfile {
  profileId: number
  empId: number
  tagId: number
  tagName: string
  tagCategory: string
  level: number
  confidence: number
  sourceBreakdown: SourceBreakdown[]
  evidenceCount: number
  lastUpdatedTime: string
  humanReviewed: boolean
  reviewStatus: string
}

/** 来源分解 */
export interface SourceBreakdown {
  sourceType: string
  count: number
  latestTime: string
}

/** 标签替换请求 */
export interface TagReplaceRequest {
  empId: number
  oldTagId: number
  newTagId: number
  reason: string
  keepOldAsAlias: boolean
  applicableSources: string[]
  positiveExamples: string[]
  negativeExamples: string[]
  sourceWeightAdvice: string
}

/** 等级修正请求 */
export interface LevelChangeRequest {
  empId: number
  tagId: number
  newLevel: number
  reason: string
  supportEvidence: string[]
  counterEvidence: string[]
  sourceWeightAdvice: string
}

/** 标签删除请求 */
export interface TagRemoveRequest {
  empId: number
  tagId: number
  reason: string
  misjudgedSource: string
  addToRejectRule: boolean
  replacementSuggestion: string
}

// ===================== API =====================

/**
 * 查询人员能力画像详情
 */
export function getPersonAbilityProfile(empId: number): Promise<ApiResponse<PersonAbilityProfile[]>> {
  return get<PersonAbilityProfile[]>(`/ability/governance/profile/${empId}`)
}

/**
 * 查询能力证据
 */
export function getAbilityEvidence(empId: number, tagId: number): Promise<ApiResponse<AbilityEvidence[]>> {
  return get<AbilityEvidence[]>(`/ability/governance/evidence/${empId}/${tagId}`)
}

/**
 * 替换能力标签
 */
export function replaceTag(data: TagReplaceRequest): Promise<ApiResponse<PersonAbilityGovernanceEvent>> {
  return post<PersonAbilityGovernanceEvent>('/ability/governance/replace-tag', data)
}

/**
 * 修改能力等级
 */
export function changeLevel(data: LevelChangeRequest): Promise<ApiResponse<PersonAbilityGovernanceEvent>> {
  return post<PersonAbilityGovernanceEvent>('/ability/governance/change-level', data)
}

/**
 * 删除能力标签
 */
export function removeTag(data: TagRemoveRequest): Promise<ApiResponse<PersonAbilityGovernanceEvent>> {
  return post<PersonAbilityGovernanceEvent>('/ability/governance/remove-tag', data)
}

/**
 * 重命名标签（影响所有引用）
 */
export function renameTag(tagId: number, newName: string, reason: string): Promise<ApiResponse<PersonAbilityGovernanceEvent[]>> {
  return post<PersonAbilityGovernanceEvent[]>('/ability/governance/rename-tag', { tagId, newName, reason })
}

/**
 * 查询员工治理历史
 */
export function getGovernanceHistory(empId: number): Promise<ApiResponse<PersonAbilityGovernanceEvent[]>> {
  return get<PersonAbilityGovernanceEvent[]>(`/ability/governance/history/${empId}`)
}

/**
 * 查询标签治理历史
 */
export function getGovernanceByTag(tagId: number): Promise<ApiResponse<PersonAbilityGovernanceEvent[]>> {
  return get<PersonAbilityGovernanceEvent[]>(`/ability/governance/tag-history/${tagId}`)
}

/**
 * 查询 Agent 记忆列表
 */
export function getAgentMemories(scope?: string): Promise<ApiResponse<AgentMemory[]>> {
  return get<AgentMemory[]>('/ability/governance/memories', { scope: scope || 'ALL' })
}

/**
 * 搜索 Agent 记忆
 */
export function searchAgentMemories(text: string, scope?: string): Promise<ApiResponse<AgentMemory[]>> {
  return get<AgentMemory[]>('/ability/governance/memories/search', { text, scope: scope || 'ALL' })
}

/**
 * 更新 Agent 记忆状态
 */
export function updateMemoryStatus(id: number, status: string): Promise<ApiResponse<void>> {
  return put<void>(`/ability/governance/memories/${id}/status`, { status })
}

/**
 * 更新 Agent 记忆优先级
 */
export function updateMemoryPriority(id: number, priority: number): Promise<ApiResponse<void>> {
  return put<void>(`/ability/governance/memories/${id}/priority`, { priority })
}

// ===================== 治理中心 API =====================

/** 治理模板 */
export interface GovernanceTemplate {
  modifyType: string
  reason: string
  oldAbilityName?: string
  newAbilityName?: string
  // 标签替换相关
  oldTagId?: number
  oldTagName?: string
  newTagId?: number
  newTagName?: string
  keepOldAsAlias?: boolean
  rememberResumeNameCorrection?: boolean
  triggerExpressions?: string[]
  negativeExpressions?: string[]
  // 等级修改相关
  oldLevel?: number
  newLevel?: number
  supportEvidence?: string
  counterEvidence?: string
  mainEvidenceSources?: string[]
  // 删除相关
  deleteReason?: string
  misjudgedSource?: string
  addToRejectRule?: boolean
  replacementSuggestion?: string
  // 证据修改相关
  addedEvidence?: string
  removedEvidence?: string
  // 通用
  sourceWeightAdvice?: string
  additionalNotes?: string
  // 变更摘要
  changeSummary?: string
  // 等级变更标识
  hasLevelChange?: boolean
}

/** 分页查询参数 */
export interface PageParams {
  pageNum?: number
  pageSize?: number
  status?: string
  memoryType?: string
  scope?: string
  keyword?: string
  modifyType?: string
  empId?: number
  tagId?: number
  [key: string]: unknown
}

/** 分页结果 */
/**
 * 分页查询Agent记忆（治理中心）
 */
export function pageAgentMemories(params: PageParams): Promise<ApiResponse<PageResult<AgentMemory>>> {
  return get<PageResult<AgentMemory>>('/governance/agent-memory/page', params)
}

/**
 * 获取Agent记忆详情
 */
export function getAgentMemoryById(id: number): Promise<ApiResponse<AgentMemory>> {
  return get<AgentMemory>(`/governance/agent-memory/${id}`)
}

/**
 * 更新Agent记忆
 */
export function updateAgentMemory(id: number, data: Partial<AgentMemory>): Promise<ApiResponse<void>> {
  return put<void>(`/governance/agent-memory/${id}`, data)
}

/**
 * 启用Agent记忆
 */
export function enableAgentMemory(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/governance/agent-memory/${id}/enable`)
}

/**
 * 禁用Agent记忆
 */
export function disableAgentMemory(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/governance/agent-memory/${id}/disable`)
}

/**
 * 过期Agent记忆
 */
export function expireAgentMemory(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/governance/agent-memory/${id}/expire`)
}

/**
 * 分页查询治理事件（治理中心）
 */
export function pageGovernanceEvents(params: PageParams): Promise<ApiResponse<PageResult<PersonAbilityGovernanceEvent>>> {
  return get<PageResult<PersonAbilityGovernanceEvent>>('/governance/agent-memory/events/page', params)
}

/**
 * 获取治理事件详情
 */
export function getGovernanceEventById(id: number): Promise<ApiResponse<PersonAbilityGovernanceEvent>> {
  return get<PersonAbilityGovernanceEvent>(`/governance/agent-memory/events/${id}`)
}
