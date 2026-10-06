/**
 * 运行审计 API（平台管理员 / AUDIT:READ）
 *
 * 三类数据源：
 *  1. Agent 运行时指标 —— token 消耗、LLM/工具调用次数与响应时间（Micrometer 进程级累计）
 *  2. prompt_invocation_log —— 逐次 LLM 调用的响应时间/重试/缓存/降级明细
 *  3. rag_query_log —— RAG 检索延迟与命中数（审计精简版，不含长文本快照）
 *
 * 后端控制器：controller/system/AdminRuntimeAuditController（/api/admin/runtime-audit）
 * 权限：/api/admin/** 由 SecurityConfig 统一收口到 AUDIT:READ。
 */
import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

/** Agent 运行时指标快照（⚠️ 进程级累计，应用重启归零） */
export interface RuntimeMetrics {
  inputTokens: number
  outputTokens: number
  totalTokens: number
  llmCallCount: number
  llmErrorCount: number
  llmAvgMs: number
  llmMaxMs: number
  toolCallCount: number
  toolErrorCount: number
  toolCacheHitCount: number
  toolAvgMs: number
  toolMaxMs: number
  jsonGuardCount: number
  note?: string
}

/** Prompt 调用明细（逐次） */
export interface PromptInvocationLog {
  id: number
  promptName: string
  promptVersion: string
  scenario: string
  modelName: string
  latencyMs: number
  toolLatencyMs: number
  queueWaitMs: number
  modelRounds: number
  retryCount: number
  cacheHit: boolean
  success: boolean
  fallbackUsed: boolean
  inputChars: number
  outputChars: number
  userId: number
  traceId: string
  feedbackScore: number
  createdTime: string
}

/** RAG 检索日志（审计精简版） */
export interface RagAuditLog {
  id: number
  queryCode: string
  scenario: string
  queryText: string
  topK: number
  hitCount: number
  latencyMs: number
  isDegraded: boolean
  fallbackReason: string
  contextTokenEstimate: number
  createdBy: number
  createdTime: string
}

/* ------------------------------------------------------------------ 图表聚合 */

/** 时段标签只到小时（如 09:00）——窗口是"最近 N 小时"，服务端不返回日期 */
export interface LatencyPoint {
  hour: string
  callCount: number
  avgLatencyMs: number
  maxLatencyMs: number
  failedCount: number
  cacheHitCount: number
}

export interface PromptPoint {
  promptName: string
  callCount: number
  avgLatencyMs: number
  maxLatencyMs: number
  failedCount: number
}

export interface RagTrendPoint {
  hour: string
  queryCount: number
  avgLatencyMs: number
  maxLatencyMs: number
  avgHitCount: number
  degradedCount: number
}

export interface RagScenarioPoint {
  scenario: string
  queryCount: number
  avgLatencyMs: number
  avgHitCount: number
}

/** 运行审计图表聚合数据（服务端按小时/按维度聚合，前端只画图） */
export interface RuntimeAuditAggregate {
  windowHours: number
  latencyTrend: LatencyPoint[]
  promptBreakdown: PromptPoint[]
  ragLatencyTrend: RagTrendPoint[]
  ragScenarioBreakdown: RagScenarioPoint[]
}

/** Prompt 实验效果（按 Prompt × 版本聚合的成功率 / 平均耗时 / 平均评分） */
export interface PromptExperimentGroup {
  promptName: string
  version: string
  totalCalls: number
  avgLatencyMs: number
  successRate: number
  avgFeedbackScore: number
  feedbackCount: number
}
export interface PromptExperimentResult {
  since?: string
  days?: number
  totalCalls?: number
  message?: string
  groups?: Record<string, PromptExperimentGroup>
}

/** 运行时指标快照 */
export function getRuntimeMetrics(): Promise<ApiResponse<RuntimeMetrics>> {
  return get<RuntimeMetrics>('/admin/runtime-audit/metrics')
}

/** Prompt 调用明细分页 */
export function pagePromptInvocationLogs(
  params: PageParams & { promptName?: string; success?: boolean },
): Promise<ApiResponse<PageResultVO<PromptInvocationLog>>> {
  return get<PageResultVO<PromptInvocationLog>>('/admin/runtime-audit/prompt-logs/page', params)
}

/** RAG 检索日志分页（审计精简版） */
export function pageRagAuditLogs(
  params: PageParams & { scenario?: string },
): Promise<ApiResponse<PageResultVO<RagAuditLog>>> {
  return get<PageResultVO<RagAuditLog>>('/admin/runtime-audit/rag-logs/page', params)
}

/** 审计统计图表聚合（服务端聚合，页面直接画图） */
export function getRuntimeAuditAggregate(hours = 24): Promise<ApiResponse<RuntimeAuditAggregate>> {
  return get<RuntimeAuditAggregate>('/admin/runtime-audit/aggregate', { hours })
}

/** Prompt 实验效果（按 Prompt × 版本聚合的成功率 / 平均耗时 / 平均评分） */
export function getPromptExperiments(
  days = 7,
  promptName?: string,
): Promise<ApiResponse<PromptExperimentResult>> {
  return get<PromptExperimentResult>('/admin/prompts/experiments', { days, promptName })
}

/** 死信队列摘要 */
export interface DlqSummary {
  messageCount: number
  checkedAt: string
  alertThreshold: number
  alerting: boolean
}

/**
 * 死信队列摘要（后端 /api/system/dlq/summary，同样由 AUDIT:READ 收口）。
 *
 * 这是审计域里**另一个有接口、却一直没有前端展示**的数据：DLQ 早就支持查看/重放/丢弃，
 * 但没有任何页面能看到积压情况。这里只做**只读展示**，重放/丢弃仍走原有页面入口，
 * 不在审计页里放变更性操作。
 */
export function getDlqSummary(): Promise<ApiResponse<DlqSummary>> {
  return get<DlqSummary>('/system/dlq/summary')
}
