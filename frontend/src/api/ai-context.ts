/**
 * AI上下文包 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== Types =====================

export interface AiContextSourceRef {
  ref: string
  refType: string
  refId: string
  title?: string
  snippet?: string
  sourceType?: string
  confidenceScore?: number
  credibilityScore?: number
  reviewStatus?: string
}

export interface AiContextAbility {
  abilityTagId: number
  abilityName: string
  currentLevel?: number
  requiredLevel?: number
  weight?: number
  required?: boolean
  core?: boolean
  source?: string
  credibility?: number
  evidenceCount?: number
  sourceRefs?: string[]
}

export interface AiContextGap {
  abilityTagId: number
  abilityName: string
  currentLevel?: number
  requiredLevel?: number
  gap: number
  gapType: string
  priority: string
  core?: boolean
  sourceRefs?: string[]
}

export interface AiContextEvidence {
  evidenceId: number
  evidenceCode?: string
  sourceType?: string
  sourceTitle?: string
  sourceSnippet?: string
  abilityName?: string
  tagId?: number
  confidenceScore?: number
  credibilityScore?: number
  evidenceStatus?: string
  sourceRef?: string
}

export interface AiContextScoreBreakdown {
  dimension: string
  score?: number
  weight?: number
  description?: string
}

export interface AiContextRiskSignal {
  riskType: string
  riskLevel: string
  message: string
  sourceRefs?: string[]
}

export interface AiContextGraphSummary {
  nodeCount?: number
  edgeCount?: number
  abilityCount?: number
  evidenceCount?: number
  keyAbilityNodes?: string[]
  keyPaths?: string[]
}

export interface AiContextPackage {
  scenario: string

  empId?: number
  empName?: string
  empCode?: string
  empLevel?: string

  postId?: number
  postName?: string
  postCode?: string
  postLevel?: string

  matchingRecordId?: number
  matchScore?: number

  employeeAbilities?: AiContextAbility[]
  postRequirements?: AiContextAbility[]
  gaps?: AiContextGap[]
  scoreBreakdown?: AiContextScoreBreakdown[]
  evidences?: AiContextEvidence[]
  riskSignals?: AiContextRiskSignal[]
  sourceRefs?: AiContextSourceRef[]

  graphSummary?: AiContextGraphSummary

  feedbackSignals?: Record<string, any>
  metadata?: Record<string, any>

  tokenEstimate?: number
  contextHash?: string
}

export interface AiContextPackageSnapshot {
  id: number
  scenario: string
  businessKey: string
  contextHash: string
  tokenEstimate?: number
  sourceRefCount?: number
  packageJson: string
  createdTime: string
}

// ===================== APIs =====================

/** 获取匹配上下文包 */
export function getMatchingAiContext(matchingRecordId: number): Promise<ApiResponse<AiContextPackage>> {
  return get<AiContextPackage>(`/ai-context/matching/${matchingRecordId}`)
}

/** 获取最近快照 */
export function getLatestAiContextSnapshot(matchingRecordId: number): Promise<ApiResponse<AiContextPackageSnapshot>> {
  return get<AiContextPackageSnapshot>(`/ai-context/matching/${matchingRecordId}/snapshot/latest`)
}

/** 获取来源详情 */
export function getSourceRefDetail(ref: string): Promise<ApiResponse<AiContextSourceRef>> {
  return get<AiContextSourceRef>('/ai-context/source-ref/detail', { ref })
}

/** 校验来源引用 */
export function validateSourceRefs(data: { contextHash: string; sourceRefs: string[] }): Promise<ApiResponse<{ validRefs: string[]; invalidRefs: string[] }>> {
  return post<{ validRefs: string[]; invalidRefs: string[] }>('/ai-context/source-ref/validate', data)
}
