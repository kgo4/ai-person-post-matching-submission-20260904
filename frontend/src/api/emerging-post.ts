/**
 * 新兴岗位定义 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { JdAbilityItem } from './types'
import type { PostPrototypeVO } from './post-prototype'

// ===================== 新兴岗位发现 =====================

/** 热门能力标签 */
export interface HotAbility {
  abilityName: string
  mentionCount: number
  growthRate: number
  relatedPostCount: number
}

/** 技术趋势 */
export interface TechTrend {
  techName: string
  trendDirection: 'RISING' | 'STABLE' | 'DECLINING'
  heatScore: number
  typicalScenario: string
}

/** 市场洞察 */
export interface MarketInsight {
  hotAbilities: HotAbility[]
  techTrends: TechTrend[]
  lastUpdated: string
  analyzedJdCount: number
  candidateCount: number
  sourcePlatformCount?: number
  independentEmployerCount?: number
  sourceDiversityScore?: number
  companyDiversityScore?: number
  deduplicatedCount?: number
  noiseFilteredCount?: number
}

/** 新兴岗位发现结果 */
export interface EmergingPostDiscovery {
  candidateName: string
  description: string
  coreAbilities: string[]
  frequency: number
  noveltyScore: number
  marketHeatScore: number
  relatedIndustries: string[]
  sourceSummary: string
  emergenceScore?: number
  trendGrowthScore?: number
  sourceDiversityScore?: number
  sourcePlatformCount?: number
  independentEmployerCount?: number
  companyDiversityScore?: number
  semanticNoveltyScore?: number
  evidenceCredibilityScore?: number
  cohesionScore?: number
  sourceRefs?: string[]
  harnessDecision?: 'PASS' | 'REVIEW' | 'BLOCK'
  reviewStatus?: 'OBSERVATION' | 'PENDING' | 'APPROVED' | 'REJECTED'
  relatedExistingPostIds?: number[]
  differentiationReason?: string
  discoveryMode?: 'OBSERVATION' | 'CANDIDATE' | 'DISCOVERY'
  recommendedAction?: 'POST_EVOLUTION' | 'EMERGING_POST_REVIEW'
}

export interface EmergingPostRequest {
  postName: string
  description?: string
  industry?: string
  keyResponsibilities?: string
  createPost?: boolean
}

/** 单条能力的证据来源 */
export interface AbilityEvidenceSource {
  sourceType: string
  sourceName?: string
  collectedAt?: string
  confidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  sampleCount?: number
}

/** 交叉验证摘要 */
export interface CrossValidationSummary {
  /** 覆盖的数据源种类数 */
  sourceDiversity: number
  /** 一致性评分 0-100 */
  consistencyScore: number
  /** 各数据源覆盖情况 */
  sourceBreakdown: Array<{ sourceType: string; label: string; abilityCount: number }>
  /** 时效性等级 */
  freshnessLevel: 'FRESH' | 'RECENT' | 'STALE'
  /** 最新采集时间 */
  lastCollectedAt?: string
}

/** 拓展的能力推荐项 — 含证据来源和置信度 */
export interface EmergingAbilityItem extends JdAbilityItem {
  /** 多源证据列表 */
  evidenceSources?: AbilityEvidenceSource[]
  /** AI置信度分数 0-100 */
  confidenceScore?: number
  /** 幻觉风险标识 */
  hallucinationRisk?: boolean
}

export interface EmergingPostResponse {
  createdPostId?: number
  recommendedPrototypes: PostPrototypeVO[]
  recommendedAbilities: EmergingAbilityItem[]
  suggestedDescription?: string
  reasoning?: string
  /** 交叉验证摘要 */
  crossValidation?: CrossValidationSummary
  /** 数据源概览 */
  dataSources?: string[]
  /** 核心职责列表 */
  coreResponsibilities?: string[]
  /** 必备技能列表 */
  requiredSkills?: string[]
  /** 加分技能列表 */
  bonusSkills?: string[]
  /** 典型行业应用场景列表 */
  industryScenarios?: string[]
}

export interface EmergingPostConfirmDTO {
  postName: string
  description?: string
  abilities: JdAbilityItem[]
}

export interface MarketJdImportResult {
  imported: number
  batchNo: string
}

/** 分析新兴岗位 */
export function analyzeEmergingPost(data: EmergingPostRequest): Promise<ApiResponse<{ taskId: string; status: string }>> {
  return post<{ taskId: string; status: string }>('/post/emerging/analyze', data)
}

export interface EmergingAnalyzeTaskResponse {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'NOT_FOUND'
  result?: EmergingPostResponse
  error?: string
}

export function getEmergingAnalyzeTask(taskId: string): Promise<ApiResponse<EmergingAnalyzeTaskResponse>> {
  return get<EmergingAnalyzeTaskResponse>(`/post/emerging/analyze/tasks/${encodeURIComponent(taskId)}`)
}

/** 确认并创建新兴岗位 */
export function confirmEmergingPost(data: EmergingPostConfirmDTO): Promise<ApiResponse<number>> {
  return post<number>('/post/emerging/confirm', data, { timeout: 60000 })
}

/** 人工优化后重新分析 */
export function reanalyzeEmergingPost(data: EmergingPostRequest & { abilities?: JdAbilityItem[] }): Promise<ApiResponse<EmergingPostResponse>> {
  return post<EmergingPostResponse>('/post/emerging/reanalyze', data, { timeout: 120000 })
}

/** 发现新兴岗位 */
export function discoverEmergingPosts(limit: number = 10): Promise<ApiResponse<EmergingPostDiscovery[]>> {
  return get<EmergingPostDiscovery[]>('/post/emerging/discover', { limit })
}

/** 获取市场洞察 */
export function getMarketInsight(): Promise<ApiResponse<MarketInsight>> {
  return get<MarketInsight>('/post/emerging/market-insight')
}

export function importMarketJdTexts(jdTexts: string[], sourcePlatform: string): Promise<ApiResponse<MarketJdImportResult>> {
  return post<MarketJdImportResult>(`/post/evolution/market-jd/import-texts?sourcePlatform=${encodeURIComponent(sourcePlatform)}`, jdTexts)
}

export function analyzeMarketJdBatch(batchNo: string): Promise<ApiResponse<void>> {
  return post<void>(`/post/evolution/market-jd/analyze-batch?batchNo=${encodeURIComponent(batchNo)}`, undefined, { timeout: 120000 })
}

