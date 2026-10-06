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

/**
 * 员工能力画像视图（正式 + 待确立）。
 * 员工侧在能力评估流程全部走完（含人工审核）前，confirmed / provisional 均为空数组，
 * assessmentPending=true 表示评估进行中、结果尚未产生。
 */
export function getAssessmentProfile(
  empId: number,
): Promise<
  ApiResponse<{
    confirmed: any[]
    provisional: any[]
    /**
     * 结果未定稿（含「从未评估」与「审核未清空」两种情形）。
     * 只用于「能不能展示能力」，不要单独拿它判断文案 —— 请用 assessmentState。
     */
    assessmentPending?: boolean
    /**
     * 四态评估状态，用于选择正确的界面文案：
     * NOT_STARTED（从未发起，引导去评估）/ IN_PROGRESS（进行中）/ PENDING_REVIEW（流程走完待审核）/ READY（已定稿）。
     * 历史 bug：只有布尔 assessmentPending，导致从未评估的人被误报成「已提交，HR 正在审核」。
     */
    assessmentState?: 'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'READY'
  }>
> {
  return get<{
    confirmed: any[]
    provisional: any[]
    assessmentPending?: boolean
    assessmentState?: 'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'READY'
  }>(`/employees/${empId}/capability-assessments/profile`)
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
  /** READY / FAILED / GENERATING(报告行已落库但内容仍在生成) / null(未生成) */
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

/** 员工全部评估报告列表（倒序）
 *
 * 员工本人仅在评估流程全部结束（含人工审核）后才会拿到记录；
 * 管理端始终返回全部记录，不受此限制。
 */
export function listAssessmentReports(empId: number): Promise<ApiResponse<AssessmentReportListItem[]>> {
  return get<AssessmentReportListItem[]>(`/employees/${empId}/capability-assessments/reports`)
}

/** 单次评估报告详情
 *
 * 员工本人在评估流程未结束前返回 null，前端按“暂无报告”处理。
 */
export function getAssessmentReport(workflowId: number): Promise<ApiResponse<AssessmentReportDetail | null>> {
  return get<AssessmentReportDetail | null>(`/capability-assessments/${workflowId}/report`)
}

/* ==========================================================================
 * 全方位评估报告（能力评估域唯一的报告）
 *
 * 四部分（简历证据 / AI 测试 / AI 面试 / 最终等级）+ AI 综合洞察。
 * 数值全部来自既有链路，AI 只写文字 —— 因此本接口**没有**汇总分字段
 * （overallScore / postMatchScore 刻意不下发，报告顶部不展示汇总分）。
 * ========================================================================== */

/** 来源权重（只读展示，实际口径由后端「来源权重配置」唯一负责） */
export interface SourceWeightFact {
  sourceType: string
  sourceLabel: string
  weight: number
}

/** 全方位评估报告完整视图 */
export interface ComprehensiveAssessmentReportDetail {
  /** 是否可阅读；false 时看 unavailableReason（正常业务状态，不是错误） */
  available: boolean
  /** 不可阅读原因：流程未完成 / HR 审核未清空 / 报告主体未生成 */
  unavailableReason?: string

  workflowId?: number
  empId?: number
  empName?: string
  postId?: number
  postName?: string
  reportStatus?: string
  completedAt?: string
  generatedAt?: string

  /** ① 简历提取证据（能力声明明细 JSON 数组字符串） */
  resumeSummaryJson?: string
  /** ② AI 测试结果 */
  testSummaryJson?: string
  /** ③ AI 面试报告（原独立面试报告的全部内容：雷达/观察/优劣势/风险/建议/逐题） */
  interviewSummaryJson?: string
  /** 面试会话 ID：「查看面试过程记录」入口需要 */
  interviewSessionId?: number
  /** ④ 聚合审核结论 / 最终等级结论 */
  aggregateSummaryJson?: string
  levelSummaryJson?: string

  /** 面试侧结论与建议（历史字段） */
  conclusion?: string
  recommendation?: string

  /** ⑤ AI 综合洞察（无分数） */
  insightAvailable: boolean
  sectionInsightsJson?: string
  strengthsJson?: string
  weaknessesJson?: string
  riskSignalsJson?: string
  suggestionsJson?: string
  aiConclusion?: string
  /** AI / TEMPLATE */
  insightSource?: string
  insightGeneratedAt?: string
  insightConfidence?: number | null
  /** true 表示洞察文字由模板生成（数值不受影响） */
  insightFallbackUsed: boolean

  sourceWeights?: SourceWeightFact[]
}

/** 最近一次已完成评估的完整报告 */
export function getComprehensiveReportLatest(
  empId: number,
): Promise<ApiResponse<ComprehensiveAssessmentReportDetail | null>> {
  return get<ComprehensiveAssessmentReportDetail | null>(
    `/employee/${empId}/comprehensive-assessment-report/latest`,
  )
}

/** 按评估流程查询完整报告 */
export function getComprehensiveReportByWorkflow(
  empId: number,
  workflowId: number,
): Promise<ApiResponse<ComprehensiveAssessmentReportDetail | null>> {
  return get<ComprehensiveAssessmentReportDetail | null>(
    `/employee/${empId}/comprehensive-assessment-report/workflow/${workflowId}`,
  )
}

/** 重新生成 AI 综合洞察（仅 HR）：忽略事实指纹强制重算，只重写文字 */
export function regenerateComprehensiveReport(empId: number): Promise<ApiResponse<boolean>> {
  return post<boolean>(`/employee/${empId}/comprehensive-assessment-report/regenerate`)
}
