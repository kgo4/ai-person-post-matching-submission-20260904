/**
 * 向量搜索 API
 */
import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

/** 向量搜索人员 */
export function searchEmployees(postText: string, topK?: number): Promise<ApiResponse<any[]>> {
  return get<any[]>('/vector/search-employees', { postText, topK })
}

/** 向量搜索岗位 */
export function searchPosts(empText: string, topK?: number): Promise<ApiResponse<any[]>> {
  return get<any[]>('/vector/search-posts', { empText, topK })
}
