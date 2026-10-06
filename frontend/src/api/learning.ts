/**
 * 学习资源 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

export interface LearningResource {
  id: number
  resourceCode: string
  abilityName: string
  tagId: number
  title: string
  resourceType: string
  difficultyLevel: number
  url: string
  description: string
  platform: string
  platformIcon: string
  coverImageUrl: string
  duration: string
  sortOrder: number
  status: number
  createdTime: string
  updatedTime: string
}

export interface LearningResourceSaveDTO {
  id?: number
  abilityName: string
  tagId?: number
  title: string
  resourceType: string
  difficultyLevel?: number
  url?: string
  description?: string
  platform?: string
  platformIcon?: string
  coverImageUrl?: string
  duration?: string
  sortOrder?: number
}

export interface LearningPathItem {
  abilityName: string
  tagId?: number
  resourceId?: number
  title: string
  resourceType?: string
  difficultyLevel?: number
  url?: string
  description?: string
  platform?: string
  platformIcon?: string
  coverImageUrl?: string
  duration?: string
}

// ===================== Learning APIs =====================

/** 保存学习资源 */
export function saveLearningResource(data: LearningResourceSaveDTO): Promise<ApiResponse<LearningResource>> {
  return post<LearningResource>('/learning/resources', data)
}

/** 上传资源封面，返回可访问的封面 URL（填入 coverImageUrl 保存） */
export function uploadResourceCover(file: File): Promise<ApiResponse<string>> {
  const formData = new FormData()
  formData.append('file', file)
  return post<string>('/learning/resources/cover-upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 分页查询学习资源 */
export function pageLearningResources(params: PageParams): Promise<ApiResponse<PageResultVO<LearningResource>>> {
  return get<PageResultVO<LearningResource>>('/learning/resources/page', params)
}

/** 获取资源详情 */
export function getLearningResource(id: number): Promise<ApiResponse<LearningResource>> {
  return get<LearningResource>(`/learning/resources/${id}`)
}

/** 删除学习资源 */
export function deleteLearningResource(id: number): Promise<ApiResponse<void>> {
  return post<void>(`/learning/resources/delete/${id}`)
}

/** 更新资源状态（0禁用，1启用） */
export function updateLearningResourceStatus(id: number, status: number): Promise<ApiResponse<void>> {
  return post<void>(`/learning/resources/${id}/status`, null, { params: { status } })
}

/** 批量更新资源状态（0禁用，1启用） */
export function batchUpdateLearningResourceStatus(ids: number[], status: number): Promise<ApiResponse<void>> {
  return post<void>('/learning/resources/batch-status', ids, { params: { status } })
}

/** 批量删除学习资源 */
export function batchDeleteLearningResources(ids: number[]): Promise<ApiResponse<void>> {
  return post<void>('/learning/resources/batch-delete', ids)
}

/** 生成学习路径 */
export function getLearningPath(params: { abilityNames: string[]; tagIds?: number[]; currentLevel?: number; targetLevel?: number }): Promise<ApiResponse<LearningPathItem[]>> {
  return get<LearningPathItem[]>('/learning/path', {
    abilityNames: params.abilityNames.join(','),
    tagIds: params.tagIds ? params.tagIds.join(',') : undefined,
    currentLevel: params.currentLevel,
    targetLevel: params.targetLevel
  })
}
