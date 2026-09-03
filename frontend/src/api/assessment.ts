/**
 * 能力评估工作流 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

/** 评估范围（简历声明 ∩ 岗位要求） */
export interface AssessmentScope {
  workflowId: number
  empId: number
  postId: number
  items: AssessmentScopeItem[]
  uncoveredRequirements: UncoveredPostRequirement[]
  scopeHash: string
}

/** 交集评估范围项（待核验能力） */
export interface AssessmentScopeItem {
  abilityTagId: number
  abilityName: string
  resumeClaimIds: number[]
  claimedLevel: number
  postRequirementId: number
  requiredLevel: number
  required: boolean
  core: boolean
  weight: number
  resumeEvidenceRefs: string[]
}

/** 岗位未覆盖要求（岗位差距，仅展示，不进入人员能力） */
export interface UncoveredPostRequirement {
  postRequirementId: number
  abilityTagId: number
  abilityName: string
  requiredLevel: number
  reason: string
}

/** 工作流视图 */
export interface WorkflowView {
  workflowId: number
  empId: number
  /** 工作流原始状态枚举 code */
  workflowStatus: string
  /** 兼容字段：工作流状态枚举 code（同 workflowStatus） */
  status: string
  /** 后端统一生成的展示状态，前端直接展示 */
  displayStatus: string
  currentStage: string
  activeStageRunId?: number
  workflowVersion: number
  startedAt?: string
  completedAt?: string
  failedReason?: string
  availableActions: string[]
  nextStepHint: string
  /** 当前阶段运行详情 */
  currentStageDetail?: CurrentStageView
  stageRuns?: StageRunView[]
  /** 证据结果：GROUNDED | NO_EVIDENCE | EXTRACTION_FAILED */
  evidenceOutcome?: string
  /** 证据失败代码 */
  evidenceFailureCode?: string
  /** 证据失败信息 */
  evidenceFailureMessage?: string
}

/** 当前阶段运行详情 */
export interface CurrentStageView {
  stageType: string
  /** PENDING/RUNNING/WAITING_USER/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL/CANCELLED */
  runStatus: string
  sourceRefId?: number
  updatedAt?: string
  failureMessage?: string
  retryable: boolean
}

/** 阶段运行视图 */
export interface StageRunView {
  stageRunId: number
  stageType: string
  status: string
  attemptCount: number
  startedAt?: string
  completedAt?: string
  failureCode?: string
  failureMessage?: string
}

/** 匹配资格预检结果 */
export interface EligibilityPrecheckResult {
  empId: number
  hasConfirmedAbilities: boolean
  hasProvisionalAbilities: boolean
  provisionalAbilityCount: number
  relatedProvisionalAbilities: ProvisionalAbilitySummary[]
  affectedRequirements: string[]
  riskFlags: string[]
  defaultAction: 'FORBIDDEN' | 'NORMAL_MATCH' | 'CONFIRMED_ONLY' | 'MANUAL_CONFIRM_REQUIRED'
}

export interface ProvisionalAbilitySummary {
  claimGroupId: number
  abilityName: string
  claimedLevel?: number
  evidenceCount: number
  evidenceStatus: string
  tagResolutionStatus: string
  riskLabel: string
}

/** 临时能力快照 */
export interface ProvisionalAbilitySnapshot {
  snapshotToken: string
  empId: number
  createdAt: string
  policyVersion: string
  abilities: { claimGroupId: number; tagId?: number; abilityName: string; claimedLevel: number; softWeightFactor: number }[]
  riskFlags: string[]
}

/** 简历能力证据 DTO */
export interface ResumeAbilityClaimDTO {
  abilityName: string
  normalizedAbilityName: string
  claimedLevel: number
  evidenceText: string
  sourceRefs: string[]
  sourceType: string
  sourceRefId: number
  confidenceScore?: number
  evidenceLocation?: string
}

/** 获取或创建员工活跃评估工作流 */
export function getOrCreateActiveWorkflow(empId: number): Promise<ApiResponse<WorkflowView>> {
  return post<WorkflowView>(`/employees/${empId}/capability-assessments/active`)
}

/** 查询员工活跃评估工作流 */
export function getActiveWorkflow(empId: number): Promise<ApiResponse<WorkflowView>> {
  return get<WorkflowView>(`/employees/${empId}/capability-assessments/active`)
}

/** 查询工作流详情 */
export function getWorkflow(workflowId: number): Promise<ApiResponse<WorkflowView>> {
  return get<WorkflowView>(`/capability-assessments/${workflowId}`)
}

/** 查询评估范围（简历声明 ∩ 岗位要求 + 未覆盖岗位能力） */
export function getAssessmentScope(workflowId: number): Promise<ApiResponse<AssessmentScope | null>> {
  return get<AssessmentScope | null>(`/capability-assessments/${workflowId}/scope`)
}

/** 保存简历能力证据 */
export function submitResumeEvidence(empId: number, resumeParseId: number, claims: ResumeAbilityClaimDTO[]): Promise<ApiResponse<number>> {
  return post<number>(`/employees/${empId}/capability-assessments/resume`, claims, { params: { resumeParseId } })
}

/** 生成验证测试响应 */
export interface GenerateVerificationTestResponse {
  stageRun: StageRunView
  testId: number
  postId: number
}

/** 创建面试响应 */
export interface CreateAssessmentInterviewResponse {
  stageRun: StageRunView
  sessionId: number
  postId: number
}

/** 生成验证测试（基于简历能力与目标岗位） */
export function generateVerificationTest(workflowId: number, postId?: number): Promise<ApiResponse<GenerateVerificationTestResponse>> {
  return post<GenerateVerificationTestResponse>(`/capability-assessments/${workflowId}/test/generate`, undefined, { params: { postId } })
}

/** 提交测试答案 */
export function submitTest(workflowId: number, testId: number, answers: Record<string, unknown>): Promise<ApiResponse<any>> {
  return post<any>(`/capability-assessments/${workflowId}/test/${testId}/submit`, { answers })
}

/** 创建 AI 面试 */
export function createInterview(workflowId: number): Promise<ApiResponse<CreateAssessmentInterviewResponse>> {
  return post<CreateAssessmentInterviewResponse>(`/capability-assessments/${workflowId}/interview/create`)
}

/** 结束 AI 面试并推进聚合审核 */
export function finishInterview(workflowId: number, sessionId: number): Promise<ApiResponse<any>> {
  return post<any>(`/capability-assessments/${workflowId}/interview/${sessionId}/finish`)
}

/** 重试失败阶段 */
export function retryStage(workflowId: number, stageType: string): Promise<ApiResponse<void>> {
  return post<void>(`/capability-assessments/${workflowId}/retry-stage`, undefined, { params: { stageType } })
}

/** 查询聚合 Harness 审核结果 */
export function getHarnessResults(workflowId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/capability-assessments/${workflowId}/harness`)
}

/** 查询等级决策记录 */
export function listDecisions(workflowId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/capability-assessments/${workflowId}/decisions`)
}

/** 人工确认等级 */
export function confirmDecision(decisionId: number, finalLevel: number, reason?: string): Promise<ApiResponse<any>> {
  return post<any>(`/capability-assessments/decisions/${decisionId}/confirm`, undefined, { params: { finalLevel, reason } })
}

/** 员工能力画像视图（正式 + 待确立） */
export function getAssessmentProfile(empId: number): Promise<ApiResponse<{ confirmed: any[]; provisional: any[] }>> {
  return get<{ confirmed: any[]; provisional: any[] }>(`/employees/${empId}/capability-assessments/profile`)
}

/** 匹配资格预检 */
export function precheckCapabilityEligibility(empIds: number[], postIds: number[]): Promise<ApiResponse<EligibilityPrecheckResult[]>> {
  return post<EligibilityPrecheckResult[]>('/matching/precheck-capability-eligibility', { empIds, postIds })
}

/** 构建强制匹配临时能力快照 */
export function buildProvisionalSnapshot(empId: number, acknowledged: boolean): Promise<ApiResponse<ProvisionalAbilitySnapshot>> {
  return post<ProvisionalAbilitySnapshot>(`/employees/${empId}/capability-assessments/provisional-snapshot`, undefined, { params: { acknowledged } })
}

/** 评估报告列表项（一次评估流程 + 报告状态） */
export interface AssessmentReportListItem {
  workflowId: number
  workflowStatus: string
  startedAt?: string
  completedAt?: string
  /** READY / FAILED / null(未生成) */
  reportStatus?: string | null
  overallScore?: number
  postMatchScore?: number
}

/** 评估报告详情 */
export interface AssessmentReportDetail {
  workflowId: number
  empId: number
  postId?: number
  sessionId?: number
  status: string
  overallScore?: number
  postMatchScore?: number
  resumeSummaryJson?: string
  testSummaryJson?: string
  interviewSummaryJson?: string
  aggregateSummaryJson?: string
  levelSummaryJson?: string
  conclusion?: string
  recommendation?: string
}

/** 员工全部评估报告列表（倒序） */
export function listAssessmentReports(empId: number): Promise<ApiResponse<AssessmentReportListItem[]>> {
  return get<AssessmentReportListItem[]>(`/employees/${empId}/capability-assessments/reports`)
}

/** 单次评估报告详情 */
export function getAssessmentReport(workflowId: number): Promise<ApiResponse<AssessmentReportDetail>> {
  return get<AssessmentReportDetail>(`/capability-assessments/${workflowId}/report`)
}
