/**
 * 岗位演化 API（增强版）
 */
import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

export interface PostEvolutionTask {
  id: number
  taskCode: string
  postId: number
  taskName: string
  baselineVersion: string
  newJdText: string
  taskStatus: string
  summaryJson: string
  errorMessage: string
  triggerType?: string
  industry?: string
  businessDomain?: string
  sourceType?: string
  progressStatus?: string
  progressPercent?: number
  agentTrace?: string
  harnessSummary?: string
  sourceDocumentIds?: number[]
  createdTime: string
  updatedTime: string
}

export interface EvolutionEvidenceSource {
  sourceType: string
  sourceName?: string
  collectedAt?: string
  confidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  sampleCount?: number
}

export interface PostEvolutionEvidence {
  id: number
  taskId: number
  changeItemId?: number
  sourceType: string
  sourceId?: number
  sourceTitle?: string
  sourceUrl?: string
  evidenceText: string
  publishedTime?: string
  collectedTime: string
  sourceWeight?: number
  similarityScore: number
  trustScore: number
  sourceRef: string
  createdTime?: string
}

export interface PostEvolutionEvidenceSummary {
  sourceCount: number
  maxTrustScore: number
  averageTrustScore: number
  crossSourceVerified: boolean
}

export interface PostEvolutionChangeItem {
  id: number
  taskId: number
  changeType: string
  tagId: number
  abilityName: string
  oldLevel: number
  newLevel: number
  oldWeight: number
  newWeight: number
  oldIsCore: number
  newIsCore: number
  supportScore: number
  confirmStatus: string
  reviewComment: string
  /** AI置信度分数 0-100 */
  confidenceScore?: number
  /** 幻觉风险标识 */
  hallucinationRisk?: boolean
  /** 证据来源列表 */
  evidenceSources?: EvolutionEvidenceSource[]
  /** 变更理由说明 */
  changeReason?: string
  /** 数据来源类型 */
  sourceType?: string
  /** 数据来源引用 */
  sourceRef?: string
  /** 数据来源详情 */
  sourceDetail?: string
  /** Harness 决策 */
  harnessDecision?: string
  /** 风险等级 */
  riskLevel?: string
  /** 证据文本 */
  evidenceText?: string
  /** 多来源引用JSON */
  sourceRefsJson?: string
  /** 已落库且与当前变更项关联的真实证据 */
  evidenceItems: PostEvolutionEvidence[]
  /** 基于 evidenceItems 计算的真实证据汇总；无关联证据时为空 */
  evidenceSummary?: PostEvolutionEvidenceSummary
}

export interface PostEvolutionTaskCreateDTO {
  postId: number
  taskName: string
  newJdText: string
}

export interface PostEvolutionReviewDTO {
  confirmStatus: string
  reviewComment?: string
}

/** Agent 请求 */
export interface PostEvolutionAgentRequest {
  postId: number
  postName?: string
  industry?: string
  businessDomain?: string
  sourceDocumentIds?: number[]
  sourceTypes?: string[]
  triggerType?: string
  includeMarketJd?: boolean
  includeZhihu?: boolean
  includeCloudKnowledge?: boolean
  includeWhitepaper?: boolean
}

/** Agent 结果 */
export interface PostEvolutionAgentResult {
  postId: number
  postName: string
  summary: string
  harnessSummary?: {
    pass: number
    review: number
    block: number
    total: number
  }
}

/** Agent 执行进度 */
export interface AgentProgressVO {
  taskId: number
  currentStep: string
  percent: number
  steps: {
    name: string
    status: string
  }[]
  errorMessage?: string
}

/** 资料上传 DTO */
export interface EvolutionSourceUploadDTO {
  title: string
  industry?: string
  businessDomain?: string
  sourceCategory?: string
  documentType?: string
  trustLevel?: string
  evolutionEnabled?: boolean
}

/** 云知识库同步请求 */
export interface CloudSyncRequest {
  knowledgeBaseCode: string
  businessDomain?: string
  sourceTypes?: string[]
}

/** 定时配置 */
export interface PostEvolutionScheduleConfig {
  id: number
  postId: number
  enabled: number
  cronExpression: string
  industry?: string
  businessDomain?: string
  includeWhitepaper: number
  includeCloudKnowledge: number
  includeMarketJd: number
  lastRunTime?: string
  nextRunTime?: string
  lastTaskId?: number
  runCount: number
  createdTime: string
  updatedTime: string
}

/** 定时配置 DTO */
export interface EvolutionScheduleConfigDTO {
  postId: number
  enabled?: number
  cronExpression?: string
  industry?: string
  businessDomain?: string
  includeWhitepaper?: number
  includeCloudKnowledge?: number
  includeMarketJd?: number
}

export interface ExternalTrendResource {
  title: string
  contentType?: string
  contentId?: string
  summary: string
  url: string
  commentCount?: number
  voteUpCount?: number
  sourceType: 'ZHIHU_TREND'
  verifiedEvidence: false
  jdFact: false
}

export interface ExternalTrendResourceResult {
  available: boolean
  degraded: boolean
  reason?: string
  sourceType: 'ZHIHU_TREND'
  items: ExternalTrendResource[]
  filteredCount: number
  deduplicatedCount: number
  noiseRemovedCount: number
}

// ===================== 资料入口 API =====================

/** 上传行业白皮书 */
export function uploadIndustryWhitepaper(file: File, data: EvolutionSourceUploadDTO): Promise<ApiResponse<{
  documentId: number
  title: string
  sourceType: string
  sourceCategory: string
  chunkCount: number
  status: string
}>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', data.title)
  if (data.industry) formData.append('industry', data.industry)
  if (data.businessDomain) formData.append('businessDomain', data.businessDomain)
  if (data.trustLevel) formData.append('trustLevel', data.trustLevel)
  if (data.evolutionEnabled !== undefined) formData.append('evolutionEnabled', String(data.evolutionEnabled))

  return post('/post/evolution/sources/industry-whitepaper', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 上传内部资料 */
export function uploadInternalDocument(file: File, data: EvolutionSourceUploadDTO): Promise<ApiResponse<{
  documentId: number
  title: string
  sourceType: string
  sourceCategory: string
  chunkCount: number
  status: string
}>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', data.title)
  if (data.sourceCategory) formData.append('sourceCategory', data.sourceCategory)
  if (data.businessDomain) formData.append('businessDomain', data.businessDomain)
  if (data.industry) formData.append('industry', data.industry)
  if (data.trustLevel) formData.append('trustLevel', data.trustLevel)
  if (data.evolutionEnabled !== undefined) formData.append('evolutionEnabled', String(data.evolutionEnabled))

  return post('/post/evolution/sources/internal-document', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 查询岗位演化外部趋势资源；仅作为解释辅助，不是 JD 事实。 */
export function searchEvolutionExternalResources(params: { query: string; count?: number }): Promise<ApiResponse<ExternalTrendResourceResult>> {
  return get<ExternalTrendResourceResult>('/post/evolution/external-resources', params)
}

/** 同步云知识库到演化知识源 */
export function syncEvolutionCloudKnowledge(data: CloudSyncRequest): Promise<ApiResponse<{
  syncedCount: number
  knowledgeBaseCode: string
}>> {
  return post('/post/evolution/sources/cloud-sync', data)
}

/** 索引知识源文档 */
export function indexKnowledgeSource(documentId: number): Promise<ApiResponse<{
  documentId: number
  chunkCount: number
}>> {
  return post(`/post/evolution/sources/${documentId}/index`)
}

// ===================== Agent API =====================

/** 运行岗位演化 Agent */
export function runEvolutionAgent(data: PostEvolutionAgentRequest): Promise<ApiResponse<{
  taskId: number
  taskStatus: string
  taskCode: string
  summary?: {
    totalProposals?: number
    savedChangeItems?: number
    evidenceCount?: number
    signalCount?: number
    harnessPass?: number
    harnessReview?: number
    harnessBlock?: number
    aiRawSuggestions?: number
    aiAcceptedSuggestions?: number
    ruleProposalCount?: number
    ruleFallback?: boolean
    fallbackReason?: string | null
    addCount?: number
    updateCount?: number
    removeCount?: number
  }
}>> {
  return post('/post/evolution/agent/run', data)
}

/** 获取 Agent 执行进度 */
export function getAgentProgress(taskId: number): Promise<ApiResponse<AgentProgressVO>> {
  return get(`/post/evolution/tasks/${taskId}/progress`)
}

// ===================== 定时配置 API =====================

/** 创建定时配置 */
export function createSchedule(data: EvolutionScheduleConfigDTO): Promise<ApiResponse<PostEvolutionScheduleConfig>> {
  return post('/post/evolution/schedules', data)
}

/** 更新定时配置 */
export function updateSchedule(id: number, data: EvolutionScheduleConfigDTO): Promise<ApiResponse<PostEvolutionScheduleConfig>> {
  return put(`/post/evolution/schedules/${id}`, data)
}

/** 分页查询定时配置 */
export function pageSchedules(params: { current?: number; size?: number; postId?: number }): Promise<ApiResponse<PageResultVO<PostEvolutionScheduleConfig>>> {
  return get('/post/evolution/schedules/page', params)
}

/** 获取定时配置详情 */
export function getSchedule(id: number): Promise<ApiResponse<PostEvolutionScheduleConfig>> {
  return get(`/post/evolution/schedules/${id}`)
}

/** 删除定时配置 */
export function deleteSchedule(id: number): Promise<ApiResponse<void>> {
  return del(`/post/evolution/schedules/${id}`)
}

/** 立即执行定时任务 */
export function runScheduleNow(id: number): Promise<ApiResponse<{ scheduleId: number; taskId: number }>> {
  return post(`/post/evolution/schedules/${id}/run-now`)
}

// ===================== 原有 Evolution APIs =====================

/** 创建演化任务 */
export function createEvolutionTask(data: PostEvolutionTaskCreateDTO): Promise<ApiResponse<PostEvolutionTask>> {
  return post<PostEvolutionTask>('/post/evolution/tasks', data)
}

/** 执行演化分析 */
export function analyzeEvolutionTask(id: number): Promise<ApiResponse<PostEvolutionTask>> {
  return post<PostEvolutionTask>(`/post/evolution/tasks/${id}/analyze`)
}

/** 分页查询演化任务 */
export function pageEvolutionTasks(params: PageParams): Promise<ApiResponse<PageResultVO<PostEvolutionTask>>> {
  return get<PageResultVO<PostEvolutionTask>>('/post/evolution/tasks/page', params)
}

/** 获取任务详情 */
export function getEvolutionTask(id: number): Promise<ApiResponse<PostEvolutionTask>> {
  return get<PostEvolutionTask>(`/post/evolution/tasks/${id}`)
}

/** 删除演化任务及其关联数据 */
export function deleteEvolutionTask(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/evolution/tasks/${id}`)
}

/** 查询变更项 */
export function pageEvolutionChangeItems(taskId: number, params: PageParams): Promise<ApiResponse<PageResultVO<PostEvolutionChangeItem>>> {
  return get<PageResultVO<PostEvolutionChangeItem>>(`/post/evolution/tasks/${taskId}/items`, params)
}

/** 查询任务证据 */
export function getTaskEvidence(taskId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/post/evolution/tasks/${taskId}/evidence`)
}

/** 审核变更项 */
export function reviewEvolutionChangeItem(taskId: number, itemId: number, data: PostEvolutionReviewDTO): Promise<ApiResponse<void>> {
  return post<void>(`/post/evolution/tasks/${taskId}/items/${itemId}/review`, data)
}

/** 批量审核变更项 */
export function batchReviewEvolutionChangeItems(taskId: number, data: { itemIds: number[]; confirmStatus: string; reviewComment?: string }): Promise<ApiResponse<{ reviewed: number }>> {
  return post<{ reviewed: number }>(`/post/evolution/tasks/${taskId}/items/batch-review`, data)
}

/** 应用已审核变更 */
export function applyEvolutionChanges(taskId: number): Promise<ApiResponse<{ taskId: number; applied: number }>> {
  return post<{ taskId: number; applied: number }>(`/post/evolution/tasks/${taskId}/apply`)
}

// ===================== Dashboard APIs =====================

export interface EvolutionTimelineEvent {
  id: string
  time: string
  title: string
  description: string
  type: string
  icon: string
  taskId: number
  taskCode: string
  postId: number
  abilityName: string
  changeType: string
  confidence?: number
  abilities: string[]
}

export interface EvolutionDashboardStats {
  totalTasks: number
  completedTasks: number
  pendingChanges: number
  highRiskChanges: number
}

export interface EvolutionTrends {
  added: number
  removed: number
  updated: number
  total: number
  monthly: Record<string, Record<string, number>>
}

export interface EvolutionGraphNode {
  id: string
  label: string
  type: string
  size: number
  level?: number
  weight?: number
  changeType?: string
}

export interface EvolutionGraphEdge {
  source: string
  target: string
  weight?: number
  type?: string
  changeType?: string
}

export interface EvolutionGraph {
  nodes: EvolutionGraphNode[]
  edges: EvolutionGraphEdge[]
  postId: number
}

/** 获取演化时间线 */
export function getEvolutionTimeline(params?: { postId?: number; range?: string; limit?: number }): Promise<ApiResponse<EvolutionTimelineEvent[]>> {
  return get<EvolutionTimelineEvent[]>('/post/evolution/timeline', params)
}

/** 获取仪表盘统计 */
export function getEvolutionDashboardStats(params?: { range?: string }): Promise<ApiResponse<EvolutionDashboardStats>> {
  return get<EvolutionDashboardStats>('/post/evolution/dashboard/stats', params)
}

/** 获取演化趋势 */
export function getEvolutionTrends(params?: { range?: string }): Promise<ApiResponse<EvolutionTrends>> {
  return get<EvolutionTrends>('/post/evolution/dashboard/trends', params)
}

/** 获取演化图谱 */
export function getEvolutionGraph(postId: number, params?: { timePoint?: string }): Promise<ApiResponse<EvolutionGraph>> {
  return get<EvolutionGraph>(`/post/evolution/graph/${postId}`, params)
}
