/**
 * 图谱匹配 API
 */
import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type {
  MatchingExecuteDTO,
  MatchingRecord,
  MatchingTask,
  MatchingBlackWhiteList,
  MatchingApprovalFlow,
  MatchingApprovalDTO,
  MatchingFeedbackDataset,
  MatchingScoringConfig,
  MatchingScoringWeightUpdate,
  PageResultVO,
} from './types'

// ===================== Matching Scoring Config =====================

/** 获取全局匹配分层权重配置（仅管理员可访问） */
export function getMatchingScoringConfig(): Promise<ApiResponse<MatchingScoringConfig>> {
  return get<MatchingScoringConfig>('/matching/scoring-config')
}

/** 保存全局匹配评分权重配置（字段均可空，缺省表示不修改该项；仅管理员可访问） */
export function saveMatchingScoringConfig(
  data: MatchingScoringWeightUpdate,
): Promise<ApiResponse<void>> {
  return put<void>('/matching/scoring-config', data)
}

// ===================== Matching Record =====================

export interface MatchingExecuteResult {
  records: MatchingRecord[]
  candidateScope: string
  candidateCount: number
  totalActiveCount: number
  truncated: boolean
  taskId?: string | null
  async?: boolean
}

/** 执行图谱匹配（同步） */
export function executeMatching(data: MatchingExecuteDTO): Promise<ApiResponse<MatchingExecuteResult>> {
  return post<MatchingExecuteResult>('/matching/record/execute', data)
}

/** 提交异步匹配任务 */
export function executeMatchingAsync(data: MatchingExecuteDTO): Promise<ApiResponse<{ taskId: string }>> {
  return post<{ taskId: string }>('/matching/record/execute-async', data)
}

/** 查询匹配任务状态 */
export function getMatchingTaskStatus(taskId: string): Promise<ApiResponse<MatchingTask>> {
  return get<MatchingTask>(`/matching/record/task/${taskId}`)
}

/** 分页查询匹配任务列表（status 可空：0待执行/1执行中/2完成/3失败/4已取消） */
export function pageMatchingTasks(params: PageParams & { status?: number }): Promise<ApiResponse<PageResultVO<MatchingTask>>> {
  return get<PageResultVO<MatchingTask>>('/matching/record/task/page', params)
}

/** 取消匹配任务（仅待执行/执行中可取消） */
export function cancelMatchingTask(taskId: string): Promise<ApiResponse<void>> {
  return post<void>(`/matching/record/task/${taskId}/cancel`)
}

/** 删除匹配任务（连带删除该任务产生的匹配记录） */
export function deleteMatchingTask(taskId: string): Promise<ApiResponse<void>> {
  return del<void>(`/matching/record/task/${taskId}`)
}

/** 分页查询匹配记录 */
export function pageRecords(params: PageParams): Promise<ApiResponse<PageResultVO<MatchingRecord>>> {
  return get<PageResultVO<MatchingRecord>>('/matching/record/page', params)
}

export function getMatchingDashboardSummary(): Promise<ApiResponse<any>> {
  return get<any>('/matching/record/dashboard-summary')
}

/** 根据ID查询匹配记录 */
export function getRecord(id: number): Promise<ApiResponse<MatchingRecord>> {
  return get<MatchingRecord>(`/matching/record/${id}`)
}

/** 删除匹配记录 */
export function deleteRecord(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/matching/record/${id}`)
}

/** 人工修改匹配结果。字段与后端 ModifyResultRequest 保持一致。 */
export interface ModifyMatchingResultRequest {
  matchScore: number
  matchStatus: number
  remark?: string
}

export function modifyResult(id: number, data: ModifyMatchingResultRequest): Promise<ApiResponse<void>> {
  return put<void>(`/matching/record/${id}`, data)
}

/** 锁定匹配结果 */
export function lockResult(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/matching/record/${id}/lock`)
}

/** 解锁匹配结果 */
export function unlockResult(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/matching/record/${id}/unlock`)
}

/** 获取匹配结果报告 */
export function getReport(id: number): Promise<ApiResponse<string>> {
  return get<string>(`/matching/record/${id}/report`)
}

/** 获取AI分析报告 */
export function getAiReport(id: number): Promise<ApiResponse<string>> {
  return get<string>(`/matching/record/${id}/ai-report`)
}

// ===================== Black/White List =====================

/** 分页查询黑白名单 */
export function pageBWList(params: PageParams): Promise<ApiResponse<PageResultVO<MatchingBlackWhiteList>>> {
  return get<PageResultVO<MatchingBlackWhiteList>>('/matching/black-white-list/page', params)
}

/** 新增黑白名单条目 */
export function saveBWEntry(data: MatchingBlackWhiteList): Promise<ApiResponse<void>> {
  return post<void>('/matching/black-white-list', data)
}

/** 更新黑白名单条目 */
export function updateBWEntry(id: number, data: MatchingBlackWhiteList): Promise<ApiResponse<void>> {
  return put<void>(`/matching/black-white-list/${id}`, data)
}

/** 删除黑白名单条目 */
export function deleteBWEntry(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/matching/black-white-list/${id}`)
}

// ===================== Approval Flow =====================

/** 发起审批流程 */
export function initiateApproval(matchingRecordId: number, adminApproverId: number): Promise<ApiResponse<void>> {
  return post<void>(`/matching/approval-flow/initiate/${matchingRecordId}`, null, {
    params: { adminApproverId }
  })
}

/** 审批 */
export function approve(data: MatchingApprovalDTO): Promise<ApiResponse<void>> {
  return post<void>('/matching/approval-flow/approve', data)
}

/** 查询审批流程节点 */
export function listApprovalFlows(matchingRecordId: number): Promise<ApiResponse<MatchingApprovalFlow[]>> {
  return get<MatchingApprovalFlow[]>(`/matching/approval-flow/${matchingRecordId}`)
}

/** 查询待办任务 */
export function pendingTasks(userId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/matching/approval-flow/pending/${userId}`)
}

// ===================== Feedback =====================

/** 提交反馈数据 */
export function submitFeedback(data: MatchingFeedbackDataset): Promise<ApiResponse<void>> {
  return post<void>('/matching/feedback', data)
}

/** 分页查询反馈数据 */
export function pageFeedback(params: PageParams): Promise<ApiResponse<PageResultVO<MatchingFeedbackDataset>>> {
  return get<PageResultVO<MatchingFeedbackDataset>>('/matching/feedback/page', params)
}

/** 获取反馈统计摘要 */
export function getFeedbackSummary(limit: number = 100): Promise<ApiResponse<any>> {
  return get<any>('/matching/feedback/summary', { limit })
}

/** 获取反馈样本 */
export function getFeedbackExamples(limit: number = 5): Promise<ApiResponse<string[]>> {
  return get<string[]>('/matching/feedback/examples', { limit })
}

export function exportFeedback(exportEnabled?: number): Promise<any> {
  const params = exportEnabled !== undefined ? { exportEnabled } : {}
  return get<any>('/matching/feedback/export', params, { responseType: 'blob' })
}

export function batchUpdateExportStatus(ids: number[], exportEnabled: number): Promise<ApiResponse<void>> {
  return post<void>('/matching/feedback/batch-update-export-status', ids, { params: { exportEnabled } })
}
/** 导出反馈数据 */

/** 获取反馈趋势统计 */
export function getFeedbackTrend(days: number = 30): Promise<ApiResponse<any>> {
  return get<any>('/matching/feedback/trend', { days })
}

/** 获取偏差分布统计 */
export function getDeviationDistribution(limit: number = 100): Promise<ApiResponse<any>> {
  return get<any>('/matching/feedback/deviation-distribution', { limit })
}

/** 获取校准回放摘要 */
export function getCalibrationReplay(limit: number = 100): Promise<ApiResponse<any>> {
  return get<any>('/matching/feedback/calibration-replay', { limit })
}

export function pageCalibration(params: Record<string, any>): Promise<ApiResponse<any>> {
  return get<any>('/matching/calibration', params)
}

export function exportCalibration(params: Record<string, any>): Promise<any> {
  return get<any>('/matching/calibration/export', params, { responseType: 'blob' })
}

// ===================== Export =====================

/** 导出匹配结果Excel */
export function exportMatchResults(postId?: number): Promise<any> {
  const params = postId !== undefined ? { postId } : {}
  return get<any>('/matching/record/export-excel', params, { responseType: 'blob' })
}
