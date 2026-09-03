/**
 * RAG知识库 API
 */
import { get, post, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'
import type { AiHarnessCheckLog, AiHarnessSummary } from './ai-governance'

export type { AiHarnessCheckLog, AiHarnessSummary }

// ===================== Types =====================

export interface RagKnowledgeDocument {
  id: number
  docCode: string
  sourceType: string
  sourceRefId: number
  title: string
  content: string
  metadataJson: string
  docStatus: string
  chunkCount: number
  lastIndexedTime: string
  createdBy: number
  createdTime: string
  updatedTime: string
}

export interface KnowledgeDocumentSaveDTO {
  id?: number
  sourceType: string
  sourceRefId?: number
  title: string
  content: string
  metadataJson?: string
}

export interface KnowledgeChunkResult {
  chunkId: number
  documentId: number
  documentTitle: string
  sourceType: string
  chunkText: string
  score: number
  chunkIndex: number
}

export interface RagQueryLog {
  id: number
  queryCode: string
  scenario: string
  queryText: string
  topK: number
  retrievedChunkIds: string
  contextText: string
  promptSnapshot: string
  responseSnapshot: string
  latencyMs: number
  createdTime: string
}

export interface AiHarnessReviewUpdateDTO {
  reviewStatus: string
  reviewComment?: string
}

export interface CloudKnowledgeSyncResult {
  enabled: boolean
  dryRun: boolean
  sourceType?: string
  scanned: number
  created: number
  skipped: number
  failed: number
  samplePayloads?: any[]
}

export interface KnowledgeBatchIndexResult {
  documentCount: number
  chunkCount: number
}

// ===================== Knowledge Document APIs =====================

/** 创建知识文档 */
export function createKnowledgeDocument(data: KnowledgeDocumentSaveDTO): Promise<ApiResponse<RagKnowledgeDocument>> {
  return post<RagKnowledgeDocument>('/rag/knowledge/documents', data)
}

/** 分页查询知识文档 */
export function pageKnowledgeDocuments(params: PageParams): Promise<ApiResponse<PageResultVO<RagKnowledgeDocument>>> {
  return get<PageResultVO<RagKnowledgeDocument>>('/rag/knowledge/documents/page', params)
}

/** 获取文档详情 */
export function getKnowledgeDocument(id: number): Promise<ApiResponse<RagKnowledgeDocument>> {
  return get<RagKnowledgeDocument>(`/rag/knowledge/documents/${id}`)
}

/** 索引文档 */
export function indexKnowledgeDocument(id: number): Promise<ApiResponse<{ documentId: number; chunkCount: number }>> {
  return post<{ documentId: number; chunkCount: number }>(`/rag/knowledge/documents/${id}/index`)
}

/** 批量索引文档 */
export function batchIndexKnowledgeDocuments(params: {
  sourceType?: string
  onlyUnindexed?: boolean
  limit?: number
}): Promise<ApiResponse<KnowledgeBatchIndexResult>> {
  return post<KnowledgeBatchIndexResult>('/rag/knowledge/documents/index', null, { params })
}

/** 回填知识文档 */
export function backfillKnowledgeDocuments(sourceType: string, limit: number = 100): Promise<ApiResponse<{ sourceType: string; created: number }>> {
  return post<{ sourceType: string; created: number }>('/rag/knowledge/documents/backfill', null, { params: { sourceType, limit } })
}

/** 搜索知识分块 */
export function searchKnowledgeChunks(params: { queryText: string; scenario?: string; topK?: number; sourceTypes?: string[] }): Promise<ApiResponse<KnowledgeChunkResult[]>> {
  return get<KnowledgeChunkResult[]>('/rag/knowledge/chunks/search', params)
}

/** 同步系统知识到火山在线知识库 */
export function syncCloudKnowledge(params: {
  sourceType?: string
  limit?: number
  dryRun?: boolean
}): Promise<ApiResponse<CloudKnowledgeSyncResult>> {
  return post<CloudKnowledgeSyncResult>('/rag/cloud/sync', null, { params })
}

/** 获取云端知识库状态 */
export function getCloudKnowledgeStatus(): Promise<ApiResponse<{
  enabled: boolean
  usable: boolean
  providerMode: string
  resourceId: string
  collectionName: string
  endpoint: string
  hasCredentials: boolean
  hasCollectionTarget: boolean
  scenarios: { key: string; name: string; allowCloud: boolean }[]
}>> {
  return get('/rag/cloud/status')
}

export function updateCloudKnowledgeConfig(data: {
  enabled: boolean
  endpoint?: string
  region?: string
  accessKey?: string
  secretKey?: string
  resourceId?: string
  collectionName?: string
  providerMode?: string
}): Promise<ApiResponse<Record<string, unknown>>> {
  return put('/rag/cloud/config', data)
}

/** 云端检索测试 */
export function searchCloudKnowledge(params: {
  queryText: string
  scenario?: string
}): Promise<ApiResponse<{
  queryText: string
  scenario: string
  providerMode: string
  fallbackUsed: boolean
  allowCloud: boolean
  hits: { chunkId: number; documentId: number; sourceType: string; title: string; content: string; score: number }[]
  hitCount: number
  latencyMs: number
}>> {
  return get('/rag/cloud/search', params)
}

// ===================== RAG Log APIs =====================

/** 分页查询RAG日志 */
export function pageRagLogs(params: PageParams): Promise<ApiResponse<PageResultVO<RagQueryLog>>> {
  return get<PageResultVO<RagQueryLog>>('/rag/logs/page', params)
}

/** 获取日志详情 */
export function getRagLog(id: number): Promise<ApiResponse<RagQueryLog>> {
  return get<RagQueryLog>(`/rag/logs/${id}`)
}

// ===================== AI Harness Audit APIs =====================

/** 分页查询AI Harness判定日志 */
export function pageHarnessChecks(params: PageParams): Promise<ApiResponse<PageResultVO<AiHarnessCheckLog>>> {
  return get<PageResultVO<AiHarnessCheckLog>>('/rag/harness/checks/page', params)
}

/** 获取AI Harness判定摘要 */
export function getHarnessSummary(): Promise<ApiResponse<AiHarnessSummary>> {
  return get<AiHarnessSummary>('/rag/harness/checks/summary')
}

/** 更新AI Harness人工处理状态 */
export function updateHarnessReviewStatus(id: number, data: AiHarnessReviewUpdateDTO): Promise<ApiResponse<boolean>> {
  return post<boolean>(`/rag/harness/checks/${id}/review`, data)
}
