/**
 * 人岗匹配相关类型定义
 */

export interface MatchingExecuteDTO {
  mode?: 'SINGLE_EVAL' | 'EMP_TO_POST' | 'POST_TO_EMP'
  pairs?: MatchingPair[]
  matchStrategy?: string
  hardConditions?: HardCondition[]
  enableAiMatching?: boolean
  forceAiMatching?: boolean
  aiTopN?: number
  aiThreshold?: number
}

export interface MatchingScoringConfig {
  version: string
  abilityWeight: number
  semanticWeight: number
  evidenceWeight: number
  aiWeight: number
  whitelistBypassHardRules: boolean
  l2MatchingMode: 'LENIENT' | 'BALANCED' | 'STRICT'
  requiredSemanticThreshold: number
  coreSemanticThreshold: number
  optionalSemanticThreshold: number
  similarTagMinimumConfidence: number
  allowedLevelGap: number
  coreCoverageThreshold: number
  requiredCoverageThreshold: number
  l2PassThreshold: number
  aiTriggerThreshold: number
}

export interface MatchingScoringWeightUpdate {
  abilityWeight?: number
  semanticWeight?: number
  evidenceWeight?: number
  aiWeight?: number
  whitelistBypassHardRules?: boolean
  l2MatchingMode?: 'LENIENT' | 'BALANCED' | 'STRICT'
  requiredSemanticThreshold?: number
  coreSemanticThreshold?: number
  optionalSemanticThreshold?: number
  similarTagMinimumConfidence?: number
  allowedLevelGap?: number
  coreCoverageThreshold?: number
  requiredCoverageThreshold?: number
  l2PassThreshold?: number
  aiTriggerThreshold?: number
}

export interface MatchingPair {
  empId: number
  postId: number
}

export interface HardCondition {
  field: string
  operator: string
  value: string
  fieldType?: string
  valueRankJson?: string
  label?: string
}

export interface MatchingTask {
  id: number
  taskId: string
  postId?: number | null
  empIds?: string | null
  status: number
  progress: number
  totalCount: number
  processedCount: number
  resultMessage?: string | null
  errorMessage?: string | null
  createdTime: string
  updatedTime: string
}

export interface MatchingRecord {
  id: number
  batchNo: string
  empId: number
  empName?: string
  empCode?: string
  postId: number
  postName?: string
  aiMatchScore: number
  vectorScore?: number
  profileSemanticScore?: number
  evidenceScore?: number
  rankScore?: number
  qualityAdjustment?: number
  feedbackAdjustment?: number
  calibrationAdjustment?: number
  l2Score?: number
  aiScore?: number
  postModelScore?: number
  llmScore?: number
  modelQualityCoefficient?: number
  feedbackCalibration?: number
  finalMatchScore?: number
  matchStatus: number
  screeningLevel?: number
  hardConditionResult?: string
  quantitativeReport: string
  aiAnalysisReport: string
  manualRemark: string
  approvalStatus: number
  isLocked: number
  lockedBy: number
  lockedTime: string
  createdTime: string
}

export interface MatchingBlackWhiteList {
  id: number
  listType: number
  empId: number
  postId: number
  remark: string
  status: number
}

export interface MatchingApprovalFlow {
  id: number
  matchingRecordId: number
  nodeOrder: number
  nodeName: string
  approverId: number
  approvalStatus: number
  approvalRemark: string
  approvalTime: string
}

export interface QuantitativeReportData {
  abilityDetails?: Array<{
    tagId?: number
    tagName?: string
    abilityName?: string
    requiredLevel?: number
    actualLevel?: number | string
    weakEvidence?: boolean
  }>
  dimensionScores?: Array<{
    dimension?: string
    label?: string
    score?: number
    maxScore?: number
    details?: string[]
  }>
  overallSuggestions?: string[]
  empName?: string
  postName?: string
}

export interface AiReportData {
  aiScore?: number
  conclusion?: string
  confidence?: number
  strengths?: string[]
  gaps?: string[]
  suggestions?: string[]
  dimensionScores?: Record<string, unknown>[]
  scoreReasons?: Record<string, unknown>[]
  evidenceAnalysis?: Record<string, unknown>[]
  riskSignals?: string[]
  weakEvidenceFlags?: string[]
  humanAttentionPoints?: string[]
  historicalReferenceUsed?: string[]
  modelQualityNote?: string
  fallbackUsed?: boolean
  sourceRefs?: Array<{
    ref?: string
    refId?: string
    title?: string
  }>
}

export interface MatchingApprovalDTO {
  matchingRecordId: number
  approvalStatus: number
  approvalRemark?: string
}

export interface MatchingFeedbackDataset {
  id: number
  matchingRecordId: number
  empId: number
  postId: number
  aiMatchScore: number
  finalMatchScore: number
  finalMatchStatus: number
  adoptionStatus: number
  feedbackReasons?: string
  feedbackComment?: string
  exportEnabled: number
  calibrationSource?: string
  calibrationTemplateVersion?: string
  feedbackTime: string
}



export interface PostRecommendRequest {
  empId: number
  topK?: number
  enableHardConditionPreview?: boolean
  enableL2Preview?: boolean
}

export interface PostRecommendResponse {
  empId: number
  empName: string
  recommendations: PostRecommendation[]
}

export interface PostRecommendation {
  postId: number
  postName: string
  postCode: string
  departmentName: string
  postLevel: string
  recommendScore: number
  vectorScore: number
  l2PreviewScore: number
  hardConditionStatus: 'PASS' | 'RISK' | 'FAIL'
  hardConditionDetails: HardConditionDetail[]
  coreAbilityHitCount: number
  coreAbilityTotalCount: number
  coreAbilityHitRate: number
  evidenceConfidence: 'STRONG' | 'MEDIUM' | 'WEAK'
  gapSummary: string[]
  reason: string
  postModelComplete: boolean
}

export interface HardConditionDetail {
  field: string
  label: string
  operator: string
  expectedValue: string
  actualValue: string
  passed: boolean
  source: string
}
