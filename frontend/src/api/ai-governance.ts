/**
 * AI 治理 API
 * <p>
 * 新版治理接口，旧 /rag/harness/* 保留兼容。
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageResultVO } from './types'

// ===================== Types =====================

export interface AiHarnessCheckLog {
  id: number
  checkCode: string
  scenario: string
  claimType: string
  claimText: string
  sourceType: string
  sourceRefId?: number
  evidenceText?: string
  ragChunkIds?: string
  sourceRefs?: string
  matchedTagId?: number
  similarTagId?: number
  supportScore?: number
  riskLevel: string
  decision: string
  isSelfEvidence: number
  reasonJson?: string
  reviewStatus?: string
  reviewComment?: string
  reviewedTime?: string
  businessApplyStatus?: string
  businessTargetType?: string
  businessTargetId?: number
  createdTime: string
  /** 归属员工；非人员治理记录不参与人员评估最终审核。 */
  empId?: number
  empName?: string
  empCode?: string
}

export interface AiHarnessSummary {
  passCount: number
  reviewCount: number
  blockCount: number
  totalCount: number
  highRiskCount: number
  mediumRiskCount: number
  selfEvidenceCount: number
  pendingCount?: number
}

export interface AiHarnessReviewUpdateDTO {
  reviewStatus: string
  reviewComment?: string
  /** 拒绝原因分类（仅 REJECTED 时使用） */
  rejectReasonCategory?: string
  /** 自动 BLOCK 的聚合人员能力：经二次确认后强制覆盖 */
  forceOverride?: boolean
}

export type AssessmentHarnessReviewView = 'PENDING' | 'HISTORY'

/** Final assessment Harness records collected under one employee. */
export interface AssessmentHarnessPersonGroup {
  empId?: number
  empName: string
  empCode?: string
  items: AiHarnessCheckLog[]
  totalCount: number
  pendingCount: number
  safeAiAcceptCount: number
}

export interface BatchHarnessReviewResult {
  successCount: number
  failedCount: number
  results: Array<{ id: number; success: boolean; reason?: string }>
}

/** 拒绝原因分类枚举 */
export type RejectReasonCategory =
  | 'EVIDENCE_INSUFFICIENT'      // 证据不足
  | 'INCONSISTENT_WITH_SOURCE'   // 与原文不符
  | 'SELF_EVIDENCE'              // 自证据
  | 'TAG_INACCURATE'             // 能力标签不准确
  | 'DUPLICATE'                  // 重复
  | 'OTHER'                      // 其他

/** 拒绝原因分类选项 */
export const REJECT_REASON_OPTIONS: { value: RejectReasonCategory; label: string }[] = [
  { value: 'EVIDENCE_INSUFFICIENT', label: '证据不足' },
  { value: 'INCONSISTENT_WITH_SOURCE', label: '与原文不符' },
  { value: 'SELF_EVIDENCE', label: '自证据' },
  { value: 'TAG_INACCURATE', label: '能力标签不准确' },
  { value: 'DUPLICATE', label: '重复' },
  { value: 'OTHER', label: '其他' },
]

// ===================== Governance APIs =====================

/** 分页查询治理记录 */
export function pageGovernanceChecks(params: {
  current?: number
  size?: number
  scenario?: string
  decision?: string
  riskLevel?: string
  claimType?: string
  reviewStatus?: string
  isSelfEvidence?: number
  /** true: 人员评估最终 Harness；false: 标签/数据治理记录 */
  assessmentOnly?: boolean
}): Promise<ApiResponse<PageResultVO<AiHarnessCheckLog>>> {
  return get<PageResultVO<AiHarnessCheckLog>>('/ai-governance/harness/checks/page', params)
}

/** 获取治理摘要统计 */
export function getGovernanceSummary(assessmentOnly?: boolean): Promise<ApiResponse<AiHarnessSummary>> {
  return get<AiHarnessSummary>('/ai-governance/harness/checks/summary', { assessmentOnly })
}

/** 更新审核状态（采纳/驳回/标记已处理） */
export function updateGovernanceReviewStatus(id: number, data: AiHarnessReviewUpdateDTO): Promise<ApiResponse<boolean>> {
  return post<boolean>(`/ai-governance/harness/checks/${id}/review`, data)
}

/**
 * The personnel assessment queue is deliberately not record-paginated: one
 * employee's conclusions must remain together for a single review decision.
 */
export function getAssessmentHarnessPersonGroups(
  view: AssessmentHarnessReviewView,
): Promise<ApiResponse<AssessmentHarnessPersonGroup[]>> {
  return get<AssessmentHarnessPersonGroup[]>('/ai-governance/harness/assessment/person-groups', { view })
}

export function batchReviewGovernanceChecks(data: {
  ids: number[]
  reviewStatus: 'ACCEPTED' | 'REJECTED'
  reviewComment?: string
  rejectReasonCategory?: string
}): Promise<ApiResponse<BatchHarnessReviewResult>> {
  return post<BatchHarnessReviewResult>('/ai-governance/harness/checks/batch-review', data)
}

/** 采纳并应用到业务数据 */
export function applyGovernanceToBusiness(id: number, reviewComment?: string): Promise<ApiResponse<boolean>> {
  return post<boolean>(`/ai-governance/harness/checks/${id}/apply`, { reviewComment })
}
