/**
 * 智能匹配推荐 API
 */
import { post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PostRecommendRequest, PostRecommendResponse, PostRecommendation, HardConditionDetail } from './matching/types'

export type { PostRecommendRequest, PostRecommendResponse, PostRecommendation, HardConditionDetail }

/** 为员工推荐适配岗位 */
export function recommendPostsForEmployee(data: PostRecommendRequest): Promise<ApiResponse<PostRecommendResponse>> {
  return post<PostRecommendResponse>('/matching/recommend/posts-by-employee', data, { timeout: 60000 })
}

// ==================== 岗位推荐员工（岗找人） ====================

/** 岗位推荐员工请求 */
export interface EmployeeRecommendRequest {
  postId: number
  topK?: number
  enableHardConditionPreview?: boolean
  enableL2Preview?: boolean
}

/** 岗位推荐员工响应 */
export interface EmployeeRecommendResponse {
  postId: number
  postName: string
  recommendations: EmployeeRecommendation[]
}

/** 员工推荐卡片 */
export interface EmployeeRecommendation {
  empId: number
  empName: string
  empCode: string
  recommendScore: number
  vectorScore: number
  l2PreviewScore: number
  hardConditionStatus: 'PASS' | 'RISK' | 'FAIL'
  hardConditionDetails: HardConditionDetail[]
  coreAbilityHitCount: number
  coreAbilityTotalCount: number
  coreAbilityHitRate: number
  evidenceConfidence: 'STRONG' | 'MEDIUM' | 'WEAK'
  gapSummary: string[]
  reason: string
}

/** 为岗位推荐适配员工 */
export function recommendEmployeesForPost(data: EmployeeRecommendRequest): Promise<ApiResponse<EmployeeRecommendResponse>> {
  return post<EmployeeRecommendResponse>('/matching/recommend/employees-by-post', data, { timeout: 60000 })
}
