/**
 * 竞赛评测 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

export interface ContestEvidenceItem {
  id: number
  evidenceCode: string
  sourceType: string
  sourceRefId: number
  sourceTitle: string
  sourceText: string
  targetType: string
  targetRefId: number
  abilityName: string
  tagId: number
  confidenceScore: number
  credibilityScore: number
  evidenceStatus: string
  reviewComment: string
  reviewedBy: number
  reviewedTime: string
  createdTime: string
}

export interface EvidenceCreateDTO {
  sourceType: string
  sourceRefId?: number
  sourceTitle?: string
  sourceText?: string
  targetType: string
  targetRefId?: number
  abilityName?: string
  tagId?: number
  confidenceScore?: number
  credibilityScore?: number
}

export interface EvidenceChainEvidence {
  id: number
  evidenceCode: string
  sourceType: string
  sourceRefId?: number
  sourceTitle?: string
  sourceText?: string
  abilityName?: string
  confidenceScore?: number
  credibilityScore?: number
  evidenceStatus?: string
  createdTime?: string
}

export interface EvidenceChainAbility {
  abilityId: number
  tagId?: number
  abilityName: string
  level?: number
  source?: string
  sourceWeight?: number
  evaluationDate?: string
  weight?: number
  required?: boolean
  core?: boolean
  modelVersion?: string
  remark?: string
  evidenceCount: number
  averageConfidence: number
  averageCredibility: number
  evidences: EvidenceChainEvidence[]
}

export interface EvidenceChain {
  subjectType: 'EMPLOYEE' | 'POST'
  subjectId: number
  subjectCode?: string
  subjectName: string
  abilityCount: number
  evidenceCount: number
  averageConfidence: number
  averageCredibility: number
  sourceTypeDistribution: Record<string, number>
  abilities: EvidenceChainAbility[]
}

// ===================== Evidence APIs =====================

/** 创建证据 */
export function createContestEvidence(data: EvidenceCreateDTO): Promise<ApiResponse<ContestEvidenceItem>> {
  return post<ContestEvidenceItem>('/contest/evidence', data)
}

/** 分页查询证据 */
export function pageContestEvidence(params: PageParams): Promise<ApiResponse<PageResultVO<ContestEvidenceItem>>> {
  return get<PageResultVO<ContestEvidenceItem>>('/contest/evidence/page', params)
}

/** 获取证据详情 */
export function getContestEvidence(id: number): Promise<ApiResponse<ContestEvidenceItem>> {
  return get<ContestEvidenceItem>(`/contest/evidence/${id}`)
}

/** 审核证据 */
export function reviewContestEvidence(id: number, data: { evidenceStatus: string; reviewComment?: string }): Promise<ApiResponse<void>> {
  return post<void>(`/contest/evidence/${id}/review`, data)
}

/** 获取证据统计摘要 */
export function getContestEvidenceSummary(): Promise<ApiResponse<any>> {
  return get<any>('/contest/evidence/summary')
}

/** 回填证据 */
/** 获取人员能力证据链 */
export function getEmployeeEvidenceChain(empId: number): Promise<ApiResponse<EvidenceChain>> {
  return get<EvidenceChain>(`/contest/evidence/employee/${empId}/chain`)
}

/** 获取岗位能力需求证据链 */
export function getPostEvidenceChain(postId: number): Promise<ApiResponse<EvidenceChain>> {
  return get<EvidenceChain>(`/contest/evidence/post/${postId}/chain`)
}

export function backfillContestEvidence(sourceType: string, limit: number = 100): Promise<ApiResponse<{ sourceType: string; created: number }>> {
  return post<{ sourceType: string; created: number }>('/contest/evidence/backfill', null, { params: { sourceType, limit } })
}

