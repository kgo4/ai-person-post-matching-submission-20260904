/**
 * 增强版学习路径 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== Types =====================

export interface KnowledgeDomain {
  id: number
  domainCode: string
  domainName: string
  domainIcon: string
  domainColor: string
  domainWeight: number
  domainDescription: string
  parentId?: number
  sortOrder: number
  status: string
}

export interface KnowledgeNode {
  id: number
  nodeCode: string
  nodeName: string
  domainId: number
  parentId?: number
  nodeLevel: number
  nodeDescription: string
  learningObjectives?: string
  prerequisitesJson?: string
  sortOrder: number
  status: string
}

export interface LearningPathItemDTO {
  abilityName: string
  resourceId?: number
  title: string
  resourceType: string
  difficultyLevel: number
  url?: string
  description: string
  learningMethod?: string
  accessPath?: string
  hasResource?: boolean
}

export interface LearningPathRequestDTO {
  abilityNames: string[]
  currentLevel?: number
  targetLevel?: number
}

export interface WeakPoint {
  domainId: number
  domainName?: string
  masteryScore: number
  quizCount: number
}

export interface LearningPathRecommendation {
  tagId: number
  abilityName: string
  currentLevel: number
  requiredLevel: number
  levelGap: number
  masteryScore: number
  priority: number
  resourceCount?: number
  firstResourceId?: number
  firstResourceTitle?: string
}

export interface LearningQuiz {
  id: number
  quizCode: string
  questionText: string
  questionType: string
  optionsJson?: string
  referenceAnswer?: string
  answerExplanation?: string
  difficultyLevel: string
  domainId?: number
  nodeId?: number
  tagId?: number
  estimatedTime?: number
  score: number
  usageCount: number
  correctRate?: number
  status: string
}

export interface LearningQuizRecord {
  id?: number
  empId: number
  quizId: number
  planId?: number
  stepId?: number
  userAnswer?: string
  isCorrect?: number
  answerTime?: number
  answerScore?: number
  attemptCount?: number
  firstAttemptTime?: string
  lastAttemptTime?: string
  correctCount?: number
  isMastered?: number
  masteredTime?: string
}

// ===================== Learning Path APIs =====================

/** 基于知识图谱生成学习路径 */
export function generateLearningPathByKnowledgeGraph(data: LearningPathRequestDTO): Promise<ApiResponse<LearningPathItemDTO[]>> {
  return post<LearningPathItemDTO[]>('/learning/path-enhanced/generate-by-knowledge-graph', data)
}

/** 基于掌握度生成学习路径 */
export function getLearningPathByMastery(empId: number, postId: number): Promise<ApiResponse<LearningPathItemDTO[]>> {
  return get<LearningPathItemDTO[]>(`/learning/path-enhanced/generate-by-mastery?empId=${empId}&postId=${postId}`)
}

/** 获取学习路径推荐 */
export function getLearningPathRecommendations(empId: number, postId: number): Promise<ApiResponse<LearningPathRecommendation[]>> {
  return get<LearningPathRecommendation[]>(`/learning/path-enhanced/recommendations?empId=${empId}&postId=${postId}`)
}

// ===================== Mastery APIs =====================

/** 获取领域掌握度 */
export function getDomainMasteryScores(empId: number): Promise<ApiResponse<Record<number, number>>> {
  return get<Record<number, number>>(`/learning/path-enhanced/mastery/domains?empId=${empId}`)
}

/** 获取知识点掌握度 */
export function getNodeMasteryScores(empId: number, domainId: number): Promise<ApiResponse<Record<number, number>>> {
  return get<Record<number, number>>(`/learning/path-enhanced/mastery/nodes?empId=${empId}&domainId=${domainId}`)
}

/** 获取薄弱环节 */
export function getWeakPoints(empId: number, limit: number = 10): Promise<ApiResponse<WeakPoint[]>> {
  return get<WeakPoint[]>(`/learning/path-enhanced/weak-points?empId=${empId}&limit=${limit}`)
}

// ===================== Learning Order APIs =====================

/** 获取领域学习顺序 */
export function getDomainLearningOrder(empId: number, postId: number): Promise<ApiResponse<KnowledgeDomain[]>> {
  return get<KnowledgeDomain[]>(`/learning/path-enhanced/order/domains?empId=${empId}&postId=${postId}`)
}

/** 获取知识点学习顺序 */
export function getNodeLearningOrder(empId: number, domainId: number): Promise<ApiResponse<KnowledgeNode[]>> {
  return get<KnowledgeNode[]>(`/learning/path-enhanced/order/nodes?empId=${empId}&domainId=${domainId}`)
}

// ===================== Progress APIs =====================

/** 更新学习进度 */
export function updateLearningProgress(empId: number, nodeId: number, status: string): Promise<ApiResponse<void>> {
  return post<void>(`/learning/path-enhanced/progress/update?empId=${empId}&nodeId=${nodeId}&status=${status}`)
}

/** 获取学习进度概览 */
export function getLearningProgressOverview(empId: number): Promise<ApiResponse<Record<string, any>>> {
  return get<Record<string, any>>(`/learning/path-enhanced/progress/overview?empId=${empId}`)
}

// ===================== Domain APIs =====================

/** 获取所有知识领域 */
export function getAllDomains(): Promise<ApiResponse<KnowledgeDomain[]>> {
  return get<KnowledgeDomain[]>('/learning/path-enhanced/domains')
}

/** 根据ID获取知识领域 */
export function getDomainById(id: number): Promise<ApiResponse<KnowledgeDomain>> {
  return get<KnowledgeDomain>(`/learning/path-enhanced/domains/${id}`)
}

/** 获取领域下的知识点 */
export function getNodesByDomainId(domainId: number): Promise<ApiResponse<KnowledgeNode[]>> {
  return get<KnowledgeNode[]>(`/learning/path-enhanced/domains/${domainId}/nodes`)
}

// ===================== Quiz APIs =====================

/** 获取所有测验题目 */
export function getAllQuizzes(): Promise<ApiResponse<LearningQuiz[]>> {
  return get<LearningQuiz[]>('/learning/path-enhanced/quizzes')
}

/** 根据领域ID获取测验题目 */
export function getQuizzesByDomainId(domainId: number): Promise<ApiResponse<LearningQuiz[]>> {
  return get<LearningQuiz[]>(`/learning/path-enhanced/quizzes/domain/${domainId}`)
}

/** 根据知识点ID获取测验题目 */
export function getQuizzesByNodeId(nodeId: number): Promise<ApiResponse<LearningQuiz[]>> {
  return get<LearningQuiz[]>(`/learning/path-enhanced/quizzes/node/${nodeId}`)
}

/** 提交答题记录 */
export function submitQuizRecord(data: LearningQuizRecord): Promise<ApiResponse<LearningQuizRecord>> {
  return post<LearningQuizRecord>('/learning/path-enhanced/quizzes/submit', data)
}

/** 获取员工答题记录 */
export function getQuizRecordsByEmpId(empId: number): Promise<ApiResponse<LearningQuizRecord[]>> {
  return get<LearningQuizRecord[]>(`/learning/path-enhanced/quizzes/records/${empId}`)
}
