/**
 * 岗位原型 API
 */
import { get, post, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'

export interface PostPrototype {
  id: number
  prototypeName: string
  industry: string
  category: string
  description: string
  status: number
  createdTime: string
}

export interface PostPrototypeTag {
  id: number
  tagId: number
  tagName: string
  tagCategory: string
  weight: number
  minRequiredLevel: number
  isCore: number
  isRequired: number
  sortOrder: number
}

export interface PostPrototypeVO {
  id: number
  prototypeName: string
  industry: string
  category: string
  description: string
  status: number
  createdTime: string
  tags: PostPrototypeTag[]
}

export interface PostPrototypeSaveDTO {
  id?: number
  prototypeName: string
  industry?: string
  category?: string
  description?: string
  status?: number
  tags?: {
    tagId: number
    weight: number
    minRequiredLevel: number
    isCore: number
    isRequired: number
    sortOrder?: number
  }[]
}

/** 分页查询原型 */
export function pagePrototypes(params: PageParams): Promise<ApiResponse<unknown>> {
  return get('/post/prototype/page', params)
}

/** 查询所有启用的原型 */
export function listEnabledPrototypes(): Promise<ApiResponse<PostPrototype[]>> {
  return get<PostPrototype[]>('/post/prototype/enabled')
}

/** 获取原型详情 */
export function getPrototype(id: number): Promise<ApiResponse<PostPrototypeVO>> {
  return get<PostPrototypeVO>(`/post/prototype/${id}`)
}

/** 保存原型 */
export function savePrototype(data: PostPrototypeSaveDTO): Promise<ApiResponse<void>> {
  return post<void>('/post/prototype', data)
}

/** 删除原型 */
export function deletePrototype(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/prototype/${id}`)
}

/** 向量召回相似原型 */
export function recallPrototypes(description: string, topN: number = 5): Promise<ApiResponse<PostPrototypeVO[]>> {
  return get<PostPrototypeVO[]>('/post/prototype/recall', { description, topN })
}

/** 应用原型到岗位 */
export function applyPrototypeToPost(prototypeId: number, postId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/prototype/${prototypeId}/apply/${postId}`)
}
