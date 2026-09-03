/**
 * 学习路径重构 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

export interface LearningPathGenerateRequest {
  matchingRecordId: number
  targetScore?: number
  includeProjectTasks?: boolean
  forceRegenerate?: boolean
  /** 是否使用 AI 增强生成（LLM + 知识图谱 + RAG） */
  useAi?: boolean
}

export interface LearningProjectTask {
  id: number
  planId: number
  stepId: number
  abilityTagId?: number
  projectName: string
  projectUrl?: string
  taskTitle: string
  taskBackground?: string
  taskRequirements?: string
  acceptanceCriteria?: string
  difficultyLevel: string
  expectedOutput?: string
  status: string
  createdTime: string
  latestSubmissionId?: number
  latestSubmissionStatus?: string
}

export interface LearningPathStep {
  id: number
  planId: number
  abilityTagId?: number
  abilityName: string
  currentLevel?: number
  targetLevel?: number
  gapType: string
  priority: string
  stepTitle: string
  stepDescription?: string
  estimatedHours?: number
  status: string
  evidenceStatus?: string
  sortOrder: number
  createdTime: string
  /** 推荐学习资源ID（来自 learning_resource 表，abilityName 主关联） */
  resourceId?: number
  /** 推荐资源标题 */
  resourceTitle?: string
  /** 推荐资源链接 */
  resourceUrl?: string
  /** 推荐资源类型 */
  resourceType?: string
  /** 该能力匹配到的启用资源总数（0 表示暂无匹配资源） */
  resourceCount?: number
  projectTasks?: LearningProjectTask[]
}

export interface LearningPathPlan {
  id: number
  empId: number
  empName?: string
  postId: number
  postName?: string
  matchingRecordId?: number
  planTitle: string
  planStatus: string
  currentScore?: number
  targetScore?: number
  aiSummary?: string
  generatedByAi: number
  createdTime: string
  updatedTime: string
  steps: LearningPathStep[]
  totalStepCount: number
  completedStepCount: number
  projectTaskCount: number
  pendingSubmissionCount: number
}

export interface LearningProjectSubmitDTO {
  repoUrl?: string
  demoUrl?: string
  reportUrl?: string
  submissionText?: string
}

export interface LearningProjectReviewDTO {
  reviewStatus: string
  reviewComment?: string
  abilityLevelAfter?: number
}

export interface LearningProjectSubmission {
  id: number
  taskId: number
  planId: number
  stepId: number
  empId: number
  repoUrl?: string
  demoUrl?: string
  reportUrl?: string
  submissionText?: string
  reviewStatus: string
  reviewComment?: string
  evidenceId?: number
  reviewedBy?: number
  reviewedTime?: string
  createdTime: string
}

export interface LearningAssessmentItem {
  id: number
  planId: number
  stepId?: number
  abilityTagId?: number
  questionType: string
  questionText: string
  referenceAnswer?: string
  difficultyLevel: string
  source: string
  answerText?: string
  score?: number
  assessmentStatus?: 'PENDING' | 'PASSED' | 'NOT_PASSED'
  scoringFeedback?: string
  answeredTime?: string
  scoredTime?: string
  createdTime: string
}

export interface LearningAssessmentGenerateRequest {
  planId: number
  includeProjectReview?: boolean
}

export interface LearningAssessmentAnswerRequest {
  answerText: string
}

// ===================== Learning Path APIs =====================

/** 生成学习路径 */
export function generateLearningPath(data: LearningPathGenerateRequest): Promise<ApiResponse<LearningPathPlan>> {
  return post<LearningPathPlan>('/learning/path/generate', data)
}

/** 获取学习路径计划详情 */
export function getLearningPathPlan(id: number): Promise<ApiResponse<LearningPathPlan>> {
  return get<LearningPathPlan>(`/learning/path/${id}`)
}

/** 根据匹配记录获取学习路径 */
export function getLearningPathByMatch(matchingRecordId: number): Promise<ApiResponse<LearningPathPlan>> {
  return get<LearningPathPlan>(`/learning/path/by-match/${matchingRecordId}`)
}

/** 分页查询学习路径 */
export function pageLearningPaths(params: PageParams): Promise<ApiResponse<PageResultVO<LearningPathPlan>>> {
  return get<PageResultVO<LearningPathPlan>>('/learning/path/page', params)
}

/** 更新步骤状态 */
export function updateStepStatus(stepId: number, status: string): Promise<ApiResponse<void>> {
  // 状态更新不涉及模型调用，但线上代理/数据库偶发慢时不能让浏览器
  // 使用默认超时后直接报 Network Error；显式给出短而充足的请求窗口。
  return post<void>(`/learning/path/step/${stepId}/status`, { status }, { timeout: 60000 })
}

// ===================== Project Task APIs =====================

/** 分页查询项目任务 */
export function pageProjectTasks(params: PageParams): Promise<ApiResponse<PageResultVO<LearningProjectTask>>> {
  return get<PageResultVO<LearningProjectTask>>('/learning/project-task/page', params)
}

/** 获取项目任务详情 */
export function getProjectTask(id: number): Promise<ApiResponse<LearningProjectTask>> {
  return get<LearningProjectTask>(`/learning/project-task/${id}`)
}

/** 提交项目任务 */
export function submitProjectTask(id: number, data: LearningProjectSubmitDTO): Promise<ApiResponse<LearningProjectSubmission>> {
  return post<LearningProjectSubmission>(`/learning/project-task/${id}/submit`, data)
}

/** 审核项目提交 */
export function reviewProjectSubmission(id: number, data: LearningProjectReviewDTO): Promise<ApiResponse<LearningProjectSubmission>> {
  return post<LearningProjectSubmission>(`/learning/project-submission/${id}/review`, data)
}

// ===================== Assessment APIs =====================

/** 生成评估题目 */
export function generateAssessments(data: LearningAssessmentGenerateRequest): Promise<ApiResponse<LearningAssessmentItem[]>> {
  return post<LearningAssessmentItem[]>('/learning/assessment/generate', data)
}

/** 获取计划下的评估题目 */
export function getAssessmentsByPlan(planId: number): Promise<ApiResponse<LearningAssessmentItem[]>> {
  return get<LearningAssessmentItem[]>(`/learning/assessment/by-plan/${planId}`)
}

/** 提交学习测评答案并取得确定性评分。 */
export function answerAssessment(id: number, data: LearningAssessmentAnswerRequest): Promise<ApiResponse<LearningAssessmentItem>> {
  return post<LearningAssessmentItem>(`/learning/assessment/${id}/answer`, data)
}

/** 仅对已有通过测评的学习步骤确认能力提升。 */
export function confirmLearningAbilityImprovement(planId: number, stepId: number): Promise<ApiResponse<void>> {
  return post<void>('/learning/assessment/confirm-improvement', { planId, stepId })
}
