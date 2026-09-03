/**
 * 能力标签治理 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse, PageResult } from '@/utils/request'
import type { PageParams } from '@/types/api'

export interface AbilityTagUsageStat {
  id: number
  tagId: number
  tagName?: string
  tagCategory?: string
  usedByPostCount: number
  usedByEmpCount: number
  heatScore: number
  statDate: string
}

export interface AbilityTagRelation {
  id: number
  sourceTagId: number
  targetTagId: number
  relationType: string
  similarityScore: number
  status: string
  evidenceSource: string
  remark: string
  createdTime: string
  // 关联字段（前端额外查询填充）
  sourceTagName?: string
  targetTagName?: string
}

/** 计算标签使用统计 */
export function computeUsageStats(): Promise<ApiResponse<void>> {
  return post<void>('/system/tag-governance/stats/compute')
}

/** 后台回填岗位能力到系统标签库，返回本批发布的治理数量。 */
export function backfillPostAbilities(): Promise<ApiResponse<number>> {
  return post<number>('/system/tag-governance/backfill-post-abilities')
}

/** 获取标签使用统计 */
export function getUsageStats(topN: number = 50): Promise<ApiResponse<AbilityTagUsageStat[]>> {
  return get<AbilityTagUsageStat[]>('/system/tag-governance/stats', { topN })
}

/** 分页查询标签关系 */
export function pageRelations(params: {
  pageNum?: number
  pageSize?: number
  sourceTagId?: number
  targetTagId?: number
  relationType?: string
  status?: string
}): Promise<ApiResponse<PageResult<AbilityTagRelation>>> {
  return get<PageResult<AbilityTagRelation>>('/system/tag-governance/relations', params)
}

/** 审核通过标签关系 */
export function approveRelation(id: number): Promise<ApiResponse<void>> {
  return post<void>(`/system/tag-governance/relations/${id}/approve`)
}

/** 审核拒绝标签关系 */
export function rejectRelation(id: number): Promise<ApiResponse<void>> {
  return post<void>(`/system/tag-governance/relations/${id}/reject`)
}

/** 自动发现标签关系（向量相似度） */
export function discoverRelations(threshold: number = 0.7): Promise<ApiResponse<number>> {
  return post<number>('/system/tag-governance/relations/discover', null, { params: { threshold } })
}

/** 创建标签关系（手动） */
export function createRelation(params: {
  sourceTagId: number
  targetTagId: number
  relationType: string
  similarityScore?: number
  remark?: string
}): Promise<ApiResponse<AbilityTagRelation>> {
  return post<AbilityTagRelation>('/system/tag-governance/relations', params)
}

/** 立即执行标签自动归并 */
export function executeMerge(threshold?: number): Promise<ApiResponse<{
  foundPairs: number
  mergedCount: number
  totalTags: number
  tagsWithVector: number
  details: Array<{
    mergeTag: string
    keepTag: string
    similarity: number
  }>
}>> {
  return post('/system/tag-governance/merge/execute', null, { params: { threshold } })
}

/** 设置定时归并任务 */
export function scheduleMerge(threshold: number, scheduledTime: string): Promise<ApiResponse<{
  taskId: string
  scheduledTime: string
  threshold: number
}>> {
  return post('/system/tag-governance/merge/schedule', null, { params: { threshold, scheduledTime } })
}

/** 取消等待中的定时归并任务 */
export function cancelMerge(taskId: string): Promise<ApiResponse<void>> {
  return post('/system/tag-governance/merge/cancel', null, { params: { taskId } })
}

/** 查询等待中的定时归并任务 */
export function listPendingMerges(): Promise<ApiResponse<Array<{
  taskId: string
  scheduledTime: string
  threshold: number
}>>> {
  return get('/system/tag-governance/merge/pending')
}

export interface TagMergeNotification {
  taskId: string
  scheduledTime: string
  threshold: number
  status: 'COMPLETED' | 'FAILED'
  completedTime?: string
  resultSummary?: string
  errorMessage?: string
}

/** 查询当前用户最近的定时归并结果 */
export function listMergeNotifications(): Promise<ApiResponse<TagMergeNotification[]>> {
  return get('/system/tag-governance/merge/notifications')
}
