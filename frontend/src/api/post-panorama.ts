/**
 * 岗位全景图谱 API
 */
import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== Types =====================

export interface PanoramaPost {
  id: number
  postName: string
  postCode: string
  level?: string
  abilityCount: number
}

export interface PanoramaAbility {
  id: number | string
  postId?: number
  modelId?: number
  tagName: string
  tagCode: string
  category?: string
  tagLevel?: number
  parentId?: number
  requiredLevel?: number
  weight?: number
  isCore?: boolean
}

export interface PanoramaSkillPoint {
  id: number
  tagName: string
  parentId: number
  parentName?: string
}

export interface PanoramaEdge {
  source: number
  target: number
  type: string
  label?: string
}

export interface PanoramaOverview {
  posts: PanoramaPost[]
  abilities: PanoramaAbility[]
  skillPoints: PanoramaSkillPoint[]
  edges: PanoramaEdge[]
  stats: {
    postCount: number
    abilityCount: number
    skillPointCount: number
  }
}

export interface PanoramaFilters {
  levels: string[]
  abilityCategories: string[]
  techStacks: string[]
}

export interface PostAbilityDetail {
  postId: number
  postName: string
  abilities: {
    modelId?: number
    abilityTagId: number | null
    abilityName: string
    requiredLevel: number
    weight: number
    isCore: boolean
    category?: string
    skillPoints?: string[]
    evidenceCount?: number
  }[]
}

export interface AbilityPanoramaDetail {
  abilityId: number
  abilityName: string
  category?: string
  skillPoints: string[]
  postCount: number
  posts: { postId: number; postName: string; requiredLevel: number; weight: number }[]
  learningResources?: { id: number; title: string; resourceType: string }[]
}


// ===================== APIs =====================

/** 获取岗位全景图谱概览 */
export function getPostPanoramaOverview(params?: {
  level?: string
  techStack?: string
  keyword?: string
}): Promise<ApiResponse<PanoramaOverview>> {
  return get<PanoramaOverview>('/post/panorama/overview', params)
}

/** 获取筛选项 */
export function getPostPanoramaFilters(): Promise<ApiResponse<PanoramaFilters>> {
  return get<PanoramaFilters>('/post/panorama/filters')
}

/** 获取某个岗位的能力和技能点详情 */
export function getPostPanoramaDetail(postId: number): Promise<ApiResponse<PostAbilityDetail>> {
  return get<PostAbilityDetail>(`/post/panorama/post/${postId}`)
}

/** 获取某个能力的岗位分布和技能点 */
export function getAbilityPanoramaDetail(abilityId: number): Promise<ApiResponse<AbilityPanoramaDetail>> {
  return get<AbilityPanoramaDetail>(`/post/panorama/ability/${abilityId}`)
}

// ===================== Graph API =====================

export interface PanoramaGraphData {
  available: boolean
  nodes: Array<{
    id: string
    type: string
    label: string
    category?: string
    level?: number
    weight?: number
    status?: string
    meta?: Record<string, any>
  }>
  edges: Array<{
    id: string
    source: string
    target: string
    type: string
    label?: string
    weight?: number
    metadata?: Record<string, any>
  }>
  stats: {
    nodeCount?: number
    edgeCount?: number
    postCount: number
    abilityCount?: number
    skillPointCount?: number
    factCount?: number
    unnormalizedFactCount?: number
  }
}

/** 获取岗位全景图谱（标准图结构） */
export function getPostPanoramaGraph(params?: {
  postId?: number
  level?: string
  techStack?: string
  requiredLevel?: number
  coreOnly?: boolean
  keyword?: string
  limit?: number
}): Promise<ApiResponse<PanoramaGraphData>> {
  return get<PanoramaGraphData>('/post/panorama/graph', params)
}

/** 岗位能力事实图谱：每个节点对应一条岗位能力表记录，未归一能力不会被隐藏。 */
export function getPostAbilityFactGraph(params?: {
  postId?: number
  level?: string
  keyword?: string
  limit?: number
}): Promise<ApiResponse<PanoramaGraphData>> {
  return get<PanoramaGraphData>('/post/panorama/fact-graph', params)
}
