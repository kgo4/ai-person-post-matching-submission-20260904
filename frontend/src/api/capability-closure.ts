import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { LearningPathItem } from './learning'

export interface ClosureGapItem {
  tagId?: number
  abilityName: string
  currentLevel?: number
  requiredLevel?: number
  weakEvidence?: boolean
  reason?: string
}

export interface MatchDiagnosisResult {
  matchingRecordId: number
  empId: number
  postId: number
  gaps: ClosureGapItem[]
  learningPath: LearningPathItem[]
}

export interface LearningOutcomeConfirmDTO {
  empId: number
  tagId?: number
  abilityName?: string
  completedResourceId?: number
  beforeLevel?: number
  confirmedLevel: number
  confirmationSource?: string
  note?: string
  // AI 追溯字段
  aiSuggestionId?: number
  ragChunkIds?: string
  aiSuggestionVersion?: string
}

export interface CapabilityClosureResult {
  eventType: string
  sourceType: string
  sourceRefId?: number
  businessKey: string
  closureStatus: string
  evidenceCount: number
  knowledgeDocCount: number
  graphRefreshStatus: string
  message?: string
}

export function getMatchDiagnosis(recordId: number): Promise<ApiResponse<MatchDiagnosisResult>> {
  return get<MatchDiagnosisResult>(`/capability-closure/matching/${recordId}/diagnosis`)
}

export function confirmLearningOutcome(data: LearningOutcomeConfirmDTO): Promise<ApiResponse<CapabilityClosureResult>> {
  return post<CapabilityClosureResult>('/capability-closure/learning/outcome', data)
}

export function getCapabilityClosureLog(businessKey: string): Promise<ApiResponse<CapabilityClosureResult>> {
  return get<CapabilityClosureResult>(`/capability-closure/logs/${encodeURIComponent(businessKey)}`)
}

// ===== 综合差距诊断 =====

/** 多维度分数快照 */
export interface DiagnosisScoreSnapshot {
  finalMatchScore?: number
  abilityScore?: number
  semanticScore?: number
  evidenceScore?: number
  llmScore?: number
  modelQualityScore?: number
  hardConditionScore?: number
  feedbackAdjustment?: number
  screeningLevel?: number
  matchStatus?: number
}

/** 硬条件事实 */
export interface HardConditionFact {
  field?: string
  label?: string
  operator?: string
  expectedValue?: string
  actualValue?: string
  passed: boolean
  source?: string
}

/** 证据来源 */
export interface EvidenceSource {
  source?: string
  level?: number
  credibility?: number
  timeFactor?: number
}

/** 能力差距事实 */
export interface AbilityGapFact {
  tagId?: number
  abilityName?: string
  currentLevel?: number
  requiredLevel?: number
  core: boolean
  required: boolean
  matchCoefficient?: number
  similarityScore?: number
  weakEvidence: boolean
  reason?: string
  evidenceSources: EvidenceSource[]
}

/** 证据风险事实 */
export interface EvidenceRiskFact {
  abilityName?: string
  riskType: string
  description?: string
  sourceCount: number
  primarySourceType?: string
  credibility?: number
}

/** 语义匹配信号 */
export interface SemanticSignal {
  vectorScore?: number
  profileSemanticScore?: number
  vectorAvailable: boolean
  employeeProfileSummary?: string
  postDescriptionSummary?: string
}

/** 反馈信号 */
export interface FeedbackSignal {
  feedbackCalibration?: number
  approvalStatus?: number
  manualRemark?: string
  feedbackReasons: string[]
}

/** 学习资源事实 */
export interface LearningResourceFact {
  abilityName?: string
  title?: string
  resourceType?: string
  difficultyLevel?: number
  url?: string
}

/** 事实诊断包 */
export interface ComprehensiveDiagnosisFact {
  recordId?: number
  empId?: number
  empName?: string
  postId?: number
  postName?: string
  postLevel?: string
  scores: DiagnosisScoreSnapshot
  hardConditions: HardConditionFact[]
  abilityGaps: AbilityGapFact[]
  evidenceRisks: EvidenceRiskFact[]
  semanticSignals: SemanticSignal
  feedbackSignals: FeedbackSignal
  availableLearningResources: LearningResourceFact[]
}

/** AI 维度诊断 */
export interface DimensionDiagnosis {
  dimension: string
  title: string
  severity: string
  facts: string[]
  analysis?: string
  sourceRefs: string[]
  suggestions: string[]
}

/** 优先动作 */
export interface PriorityAction {
  action: string
  reason: string
  sourceRefs: string[]
}

/** 被拦截的声明 */
export interface BlockedClaim {
  claim: string
  reason: string
  confidence: string
}

/** AI 综合分析结果 */
export interface AiDiagnosisAnalysis {
  overallConclusion: string
  riskLevel: string
  dimensions: DimensionDiagnosis[]
  priorityActions: PriorityAction[]
  blockedClaims: BlockedClaim[]
  generatedAt?: string
}

/** 综合诊断结果 */
export interface ComprehensiveDiagnosisResult {
  matchingRecordId: number
  empId?: number
  postId?: number
  factPackage: ComprehensiveDiagnosisFact
  aiAnalysis?: AiDiagnosisAnalysis
}

export function getComprehensiveDiagnosis(recordId: number): Promise<ApiResponse<ComprehensiveDiagnosisResult>> {
  return get<ComprehensiveDiagnosisResult>(`/capability-closure/matching/${recordId}/comprehensive-diagnosis`)
}
