/**
 * 学习成果 HR 复核闭环 API（人岗匹配闭环设计 P4）。
 *
 * 核心口径：员工提交学习成果后进入「待复核」，**不立即回写能力证据**；
 * HR 复核通过后才走既有回写链路，能力画像随之更新；驳回则附理由退回，可修改后重新提交。
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

/** 复核状态：0待复核 1通过 2驳回 */
export const LEARNING_OUTCOME_STATUS = {
  PENDING: 0,
  APPROVED: 1,
  REJECTED: 2,
} as const

/** 学习成果提交单 */
export interface LearningOutcomeSubmission {
  id: number
  empId: number
  /** 员工姓名（后端已批量补全，HR 列表直接展示） */
  empName: string | null
  matchingRecordId: number | null
  tagId: number | null
  abilityName: string | null
  completedResourceId: number | null
  beforeLevel: number | null
  confirmedLevel: number | null
  note: string | null
  reviewStatus: number | null
  reviewComment: string | null
  reviewedBy: number | null
  reviewedTime: string | null
  closureBusinessKey: string | null
  createdTime: string | null
}

/** 提交学习成果入参（不含 empId：人员范围由服务端按登录身份固定为本人） */
export interface LearningOutcomeSubmitDTO {
  matchingRecordId?: number
  tagId?: number
  abilityName?: string
  completedResourceId?: number
  beforeLevel?: number
  confirmedLevel: number
  note?: string
  aiSuggestionId?: number
  ragChunkIds?: string
  aiSuggestionVersion?: string
}

/** 复核状态文案 */
export function learningOutcomeStatusText(status: number | null | undefined): string {
  switch (status) {
    case LEARNING_OUTCOME_STATUS.PENDING:
      return '待复核'
    case LEARNING_OUTCOME_STATUS.APPROVED:
      return '已通过'
    case LEARNING_OUTCOME_STATUS.REJECTED:
      return '已驳回'
    default:
      return '--'
  }
}

/** 复核状态标签类型（Element Plus tag type） */
export function learningOutcomeStatusTagType(status: number | null | undefined): 'warning' | 'success' | 'danger' | 'info' {
  switch (status) {
    case LEARNING_OUTCOME_STATUS.PENDING:
      return 'warning'
    case LEARNING_OUTCOME_STATUS.APPROVED:
      return 'success'
    case LEARNING_OUTCOME_STATUS.REJECTED:
      return 'danger'
    default:
      return 'info'
  }
}

// ===================== API =====================

/** 员工提交学习成果：进入待复核，不立即回写能力证据 */
export function submitLearningOutcome(data: LearningOutcomeSubmitDTO): Promise<ApiResponse<number>> {
  return post<number>('/learning-outcomes', data)
}

/** 本人的学习成果提交记录（含复核状态与驳回理由） */
export function pageMyLearningOutcomes(
  params: PageParams,
): Promise<ApiResponse<PageResultVO<LearningOutcomeSubmission>>> {
  return get<PageResultVO<LearningOutcomeSubmission>>('/learning-outcomes/my', params)
}

/** HR 复核列表（后端按待复核优先排序） */
export function pageLearningOutcomeReviews(
  params: PageParams & { reviewStatus?: number },
): Promise<ApiResponse<PageResultVO<LearningOutcomeSubmission>>> {
  return get<PageResultVO<LearningOutcomeSubmission>>('/learning-outcomes', params)
}

/** 学习路径里的一步（提交单上下文里用） */
export interface LearningOutcomeContextStep {
  stepId: number
  abilityName: string | null
  currentLevel: number | null
  targetLevel: number | null
  gapType: string | null
  priority: string | null
  status: string | null
  evidenceStatus: string | null
  resourceCount: number | null
  /** 是否为本次申请对应的步骤（前端据此高亮，HR 不必自己找） */
  applied: boolean | null
}

/** 本次申请对应能力的评估题记录 */
export interface LearningOutcomeContextAssessment {
  id: number
  questionType: string | null
  difficultyLevel: string | null
  assessmentStatus: 'PENDING' | 'PASSED' | 'NOT_PASSED' | null
  score: number | null
  answerText: string | null
  scoringFeedback: string | null
  answeredTime: string | null
}

/**
 * 项目材料（员工自提，未经独立复核）。
 *
 * 项目材料已不再单独复核 —— 它只证明「员工做了练习并交了东西」，
 * 能力等级由 HR 在复核能力提升申请时判断。这里是 HR 判断的主要佐证。
 */
export interface LearningOutcomeContextMaterial {
  submissionId: number
  taskTitle: string | null
  stepId: number | null
  stepAbilityName: string | null
  repoUrl: string | null
  demoUrl: string | null
  reportUrl: string | null
  submissionText: string | null
  submittedTime: string | null
  /** 是否属于本次申请的能力对应步骤（前端据此把关键材料排在前面） */
  belongsToAppliedStep: boolean | null
}

/**
 * 一条提交单对应的学习路径与学习情况。
 *
 * 🔴 可见性闸门：HR **默认看不到**员工的学习路径，**提交单是唯一入口** ——
 * 员工发起能力提升申请后，HR 才能看到该员工在这条匹配下的路径进展与该项能力的学习记录。
 * 所以这个接口**只接受提交单 ID**，不存在「按员工查学习路径」的 HR 入口。
 */
export interface LearningOutcomeContext {
  submissionId: number
  empId: number | null
  empName: string | null
  matchingRecordId: number | null
  postName: string | null
  abilityName: string | null
  beforeLevel: number | null
  confirmedLevel: number | null
  planId: number | null
  planTitle: string | null
  planStatus: string | null
  totalStepCount: number | null
  completedStepCount: number | null
  /** 本次申请对应步骤；为 null 表示该能力在当前计划里没有对应步骤 */
  appliedStepId: number | null
  steps: LearningOutcomeContextStep[]
  /** 只含本次申请那一步的评估题 */
  assessments: LearningOutcomeContextAssessment[]
  /** 该员工在这份学习计划下提交过的项目材料（本次申请步骤的排前面） */
  projectMaterials: LearningOutcomeContextMaterial[]
}

/**
 * 查看某条提交单对应的学习路径与学习情况（HR）。
 * 没有提交单就没有入口 —— 这是可见性闸门本身，不要为「方便」再加按员工查询的接口。
 */
export function getLearningOutcomeContext(
  id: number,
): Promise<ApiResponse<LearningOutcomeContext>> {
  return get<LearningOutcomeContext>(`/learning-outcomes/${id}/learning-context`)
}

/** 待复核数量（HR 侧待办角标） */
export function getPendingLearningOutcomeCount(): Promise<ApiResponse<number>> {
  return get<number>('/learning-outcomes/pending-count')
}

/** HR 复核通过：走既有回写链路，能力证据升级 */
export function approveLearningOutcome(id: number, comment?: string): Promise<ApiResponse<void>> {
  return post<void>(`/learning-outcomes/${id}/approve`, { comment })
}

/** HR 复核驳回：理由必填，员工可修改后重新提交 */
export function rejectLearningOutcome(id: number, comment: string): Promise<ApiResponse<void>> {
  return post<void>(`/learning-outcomes/${id}/reject`, { comment })
}
