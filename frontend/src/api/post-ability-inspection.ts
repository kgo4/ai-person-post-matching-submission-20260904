import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

/** 岗位能力巡检 - 岗位聚合项 */
export interface PostAbilityInspectionPost {
  postId: number
  postName: string
  postCode?: string
  abilityCount: number
  riskyCount: number
  highCount: number
  aiSourceCount: number
}

/** 岗位能力巡检 - 单项能力明细 */
export interface PostAbilityInspectionItem {
  id: number
  postId: number
  abilityName: string
  tagId?: number
  techStack?: string
  minRequiredLevel?: number
  weight?: number
  isRequired?: number
  isCore?: number
  sourceType?: string
  modelVersion?: string
  remark?: string
  createdTime: string
  /** NORMAL / WARN / HIGH */
  riskLevel: 'NORMAL' | 'WARN' | 'HIGH'
  riskTags: string[]
  aiSource?: boolean
  harnessDecision?: string
  harnessRiskLevel?: string
  harnessReason?: string
  harnessCheckCode?: string
  groundingStatus?: string
  groundingReason?: string
  evidenceText?: string
}

/** 全岗位巡检汇总 */
export interface PostAbilityInspectionSummary {
  postCount: number
  abilityCount: number
  riskyCount: number
  highCount: number
  aiSourceCount: number
}

/** 分页查询岗位聚合列表 */
export function pagePostInspection(params: {
  current: number
  size: number
  keyword?: string
  onlyRisky?: boolean
  onlyAi?: boolean
}): Promise<ApiResponse<{ records: PostAbilityInspectionPost[]; total: number }>> {
  // request.get 已经会将第二个参数作为 Axios query params，不能再次嵌套 params。
  return get('/system/post-ability-inspection/posts', params)
}

/** 查询岗位能力明细（含风险标注） */
export function listPostInspectionAbilities(postId: number): Promise<ApiResponse<PostAbilityInspectionItem[]>> {
  return get(`/system/post-ability-inspection/${postId}/abilities`)
}

/** 全岗位巡检汇总 */
export function getPostInspectionSummary(): Promise<ApiResponse<PostAbilityInspectionSummary>> {
  return get('/system/post-ability-inspection/summary')
}
