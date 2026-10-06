/**
 * 知识图谱平台 API
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== Types =====================

export interface GraphNode {
  id: string
  label: string
  type: string
  category?: string
  weight?: number
  level?: number
  status?: string
  metadata?: Record<string, any>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: string
  weight?: number
  confidence?: number
  metadata?: Record<string, any>
}

export interface GraphStats {
  nodeCount: number
  edgeCount: number
  postCount: number
  abilityCount: number
  evidenceCount: number
  evolutionCount: number
  knowledgeDomainCount: number
  knowledgeNodeCount: number
  prerequisiteCount: number
}

export interface GraphData {
  available: boolean
  nodes: GraphNode[]
  edges: GraphEdge[]
  stats: GraphStats
}

export interface GraphBuildResult {
  success: boolean
  nodeCount: number
  edgeCount: number
  nodeTypeCounts: Record<string, number>
  edgeTypeCounts: Record<string, number>
  message: string
}

export interface GraphBuildTaskStatus {
  taskCode: string
  taskStatus: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  result?: GraphBuildResult
  errorMessage?: string
  createdTime?: string
  startedTime?: string
  completedTime?: string
}

export interface GraphSnapshot {
  id: number
  snapshotCode: string
  snapshotName: string
  snapshotType: string
  nodeCount: number
  edgeCount: number
  snapshotJson: string
  createdBy: number
  createdTime: string
}

export interface Neo4jHealth {
  enabled: boolean
  status: string
  message?: string
}

export interface GraphBusinessAnalysis {
  available: boolean
  status?: string
  graphVersion?: string
  abilityCount?: number
  coveredCount?: number
  gapCount?: number
  evidenceCount?: number
  relationCount?: number
  postGraph?: GraphData
}

// ===================== Graph Build APIs =====================

/** 全量重建图谱 */
export function rebuildFullGraph(): Promise<ApiResponse<GraphBuildTaskStatus>> {
  return post<GraphBuildTaskStatus>('/kg/build/full')
}

export function getGraphBuildTask(taskCode: string): Promise<ApiResponse<GraphBuildTaskStatus>> {
  return get<GraphBuildTaskStatus>(`/kg/build/tasks/${taskCode}`)
}

/** Neo4j 图数据库连接状态 */
export function getNeo4jHealth(): Promise<ApiResponse<Neo4jHealth>> {
  return get<Neo4jHealth>('/kg/neo4j/health')
}

// ===================== Graph Query APIs =====================

/** 获取全景图谱 */
export function getPanorama(params?: {
  nodeTypes?: string[]
  keyword?: string
  category?: string
  limit?: number
}): Promise<ApiResponse<GraphData>> {
  return get<GraphData>('/kg/panorama', params)
}

/** 获取岗位中心图谱 */
export function getPostCenteredGraph(postId: number): Promise<ApiResponse<GraphData>> {
  return get<GraphData>(`/kg/post/${postId}`)
}

/** 获取员工中心图谱 */
export function getEmployeeCenteredGraph(empId: number): Promise<ApiResponse<GraphData>> {
  return get<GraphData>(`/kg/employee/${empId}`)
}

/** 获取能力差距路径 */
export function getAbilityGapPath(empId: number, postId: number): Promise<ApiResponse<GraphData>> {
  return get<GraphData>(`/kg/path/employee/${empId}/post/${postId}`)
}

export function getGraphBusinessAnalysis(employeeId?: number, postId?: number): Promise<ApiResponse<GraphBusinessAnalysis>> {
  return get<GraphBusinessAnalysis>('/kg/business-analysis', { employeeId, postId })
}

// ===================== Snapshot APIs =====================

/** 创建图谱快照 */
export function createSnapshot(data: {
  snapshotType: string
  snapshotName: string
  graphJson: string
  createdBy?: number
}): Promise<ApiResponse<GraphSnapshot>> {
  return post<GraphSnapshot>('/kg/snapshots', data.graphJson, {
    params: {
      snapshotType: data.snapshotType,
      snapshotName: data.snapshotName,
      createdBy: data.createdBy
    },
    headers: {
      'Content-Type': 'text/plain;charset=UTF-8'
    }
  })
}

/** 查询快照列表 */
export function pageSnapshots(params?: {
  snapshotType?: string
  page?: number
  size?: number
}): Promise<ApiResponse<{ records: GraphSnapshot[]; total: number }>> {
  return get<{ records: GraphSnapshot[]; total: number }>('/kg/snapshots/page', params)
}

/** 获取快照详情 */
export function getSnapshotById(id: number): Promise<ApiResponse<GraphSnapshot>> {
  return get<GraphSnapshot>(`/kg/snapshots/${id}`)
}

// ===================== Memory Graph APIs =====================

/** 获取治理记忆图谱 */
export function getMemoryGraph(params?: {
  limit?: number
}): Promise<ApiResponse<GraphData>> {
  return get<GraphData>('/kg/memory-graph', params)
}

// ===================== Timeline APIs =====================

export interface TimelineEvent {
  eventType: 'NODE_ADDED' | 'EDGE_ADDED'
  timestamp: string
  nodeKey?: string
  nodeType?: string
  label?: string
  category?: string
  edgeKey?: string
  edgeType?: string
  source?: string
  target?: string
}

/** 获取图谱变化时间线 */
export function getTimeline(params?: {
  limit?: number
}): Promise<ApiResponse<{ events: TimelineEvent[]; total: number }>> {
  return get<{ events: TimelineEvent[]; total: number }>('/kg/timeline', params)
}

// ===================== Graph Context Types =====================

export type GraphContextStatus = 'AVAILABLE' | 'EMPLOYEE_NOT_FOUND' | 'POST_NOT_FOUND' | 'ABILITY_NOT_FOUND' | 'GRAPH_DATA_UNAVAILABLE'
export type GraphMatchState = 'SATISFIED' | 'LEVEL_GAP' | 'MISSING' | 'BONUS'

export interface GraphEvidenceContext {
  evidenceId: number | null
  label: string
  relationType: string
  confidence: number | null
  reviewStatus: string | null
  sourceRefs: string[]
  graphVersion: string | null
  createdTime: string | null
}

export interface GraphMatchAbilityContext {
  abilityId: number
  abilityName: string
  weight: number
  requiredLevel: number | null
  employeeMasteryLevel: number | null
  required: boolean
  core: boolean
  state: GraphMatchState
  evidence: GraphEvidenceContext[]
}

export interface GraphMatchContext {
  status: GraphContextStatus
  employeeId: number
  employeeName: string | null
  postId: number
  postName: string | null
  graphVersion: string | null
  refreshedAt: string | null
  abilities: GraphMatchAbilityContext[]
}

export interface GraphAbilityEvidenceContext {
  abilityId: number
  abilityName: string | null
  evidence: GraphEvidenceContext[]
}

export interface GraphLearningPrerequisiteContext {
  abilityIds: number[]
  prerequisites: {
    abilityId: number
    abilityName: string
    prerequisiteAbilityId: number
    prerequisiteAbilityName: string
    relationType: string
    sourceRefs: string[]
    graphVersion: string | null
  }[]
}

// ===================== Graph Context APIs =====================

/** 获取人岗匹配图谱上下文 */
export function getMatchGraphContext(empId: number, postId: number): Promise<ApiResponse<GraphMatchContext>> {
  return get<GraphMatchContext>(`/kg/context/match/employee/${empId}/post/${postId}`)
}

/** 获取能力证据上下文 */
export function getAbilityEvidenceContext(abilityId: number, employeeId?: number): Promise<ApiResponse<GraphAbilityEvidenceContext>> {
  return get<GraphAbilityEvidenceContext>(`/kg/context/ability/${abilityId}/evidence`, employeeId ? { employeeId } : undefined)
}

/** 获取学习路径前置条件上下文 */
export function getLearningPrerequisiteContext(abilityIds: number[]): Promise<ApiResponse<GraphLearningPrerequisiteContext>> {
  return get<GraphLearningPrerequisiteContext>('/kg/context/learning/prerequisites', { abilityIds })
}
