/**
 * AI 学习建议 API
 *
 * AI 只能基于系统检索到的资源生成学习建议，不能凭空编造资源或能力。
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== Types =====================

/** AI 学习建议请求 */
export interface AiLearningSuggestionRequest {
  matchingRecordId?: number
  empId?: number
  postId?: number
  gaps?: AiLearningGapInput[]
}

/** 能力差距输入 */
export interface AiLearningGapInput {
  tagId?: number
  abilityName?: string
  currentLevel?: number
  requiredLevel?: number
  weakEvidence?: boolean
  reason?: string
}

/** AI 学习建议响应 */
export interface AiLearningSuggestionResponse {
  matchingRecordId?: number
  empId?: number
  postId?: number
  suggestions: AbilitySuggestion[]
  validation?: ValidationSummary
  hasInsufficientEvidence: boolean
  ragChunkIds?: number[]
}

/** 单个能力的AI建议 */
export interface AbilitySuggestion {
  abilityName: string
  tagId?: number
  riskLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  reason?: string
  currentLevel?: number
  requiredLevel?: number
  steps: LearningStep[]
  insufficientEvidence: boolean
  suggestionSource?: string
}

/** 学习步骤 */
export interface LearningStep {
  resourceId?: number
  title?: string
  resourceType?: string
  url?: string
  difficultyLevel?: number
  why?: string
  action?: string
  sourceRefs?: string[]
  validated: boolean
  validationFailureReason?: string
}

/** 校验摘要 */
export interface ValidationSummary {
  totalSteps: number
  validatedSteps: number
  filteredSteps: number
  hasInsufficientEvidence: boolean
  details?: string[]
}

// ===================== API =====================

/** 生成AI学习建议 */
export function generateAiLearningSuggestions(
  data: AiLearningSuggestionRequest,
): Promise<ApiResponse<AiLearningSuggestionResponse>> {
  return post<AiLearningSuggestionResponse>('/learning/ai-suggestions', data)
}

/** 获取已缓存的AI学习建议 */
export function getCachedAiLearningSuggestions(
  matchingRecordId: number,
): Promise<ApiResponse<AiLearningSuggestionResponse[]>> {
  return get<AiLearningSuggestionResponse[]>(`/learning/ai-suggestions/${matchingRecordId}`)
}
