import type { MatchingApprovalFlow, MatchingRecord } from '@/api/matching/types'
import type { Component } from 'vue'
import { Connection, DataAnalysis, DocumentChecked, MagicStick, ScaleToOriginal } from '@element-plus/icons-vue'

export interface ScorePart {
  label: string
  value: number | null | undefined
  emptyText?: string
  desc: string
  icon: Component
  color: string
}

export interface HardConditionDetail {
  [key: string]: unknown
  name?: string
  passed?: boolean
  result?: string
  description?: string
}

export function parseJson<T = unknown>(raw?: string | null): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function getAiScoreEmptyText(record?: MatchingRecord | null): string {
  if (!record) return 'AI 评分不可用'
  if (record.aiScore != null || record.llmScore != null) return ''
  if (record.screeningLevel == null || record.screeningLevel < 3) return '未触发'
  return '-'
}

export function getScoreColor(score: number | null | undefined): string {
  if (score == null) return '#8b949e'
  if (score >= 80) return '#16a34a'
  if (score >= 60) return '#2563eb'
  if (score >= 40) return '#d97706'
  return '#dc2626'
}

export function formatScore(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '-'
  return Number(value).toFixed(1)
}

export function formatScorePart(part: ScorePart): string {
  return formatScore(part.value)
}

export function formatPercentWeight(value?: number | null): string {
  if (value == null) return '-'
  return `${Number(value).toFixed(1)}%`
}

export function getMatchStatusText(status?: number | null): string {
  if (status == null) return '-'
  if (status >= 90) return '高度匹配'
  if (status >= 70) return '匹配'
  if (status >= 50) return '部分匹配'
  return '不匹配'
}

export function getMatchStatusType(status?: number | null): string {
  if (status == null) return 'info'
  if (status >= 70) return 'success'
  if (status >= 50) return 'warning'
  return 'danger'
}

export function getApprovalStatusText(status?: string | null): string {
  if (!status) return '待审批'
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已拒绝'
  if (status === 'PENDING') return '待审批'
  return status
}

export function getApprovalStatusType(status?: string | null): string {
  if (!status || status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  return 'danger'
}

export function getScreeningLevelText(level?: number | null): string {
  if (level == null) return '-'
  if (level === 1) return '硬条件筛选'
  if (level === 2) return '量化评分'
  if (level === 3) return 'AI 深度评分'
  return `L${level}`
}

export function isPassed(item: { status?: string | number | null; passed?: boolean }): boolean {
  if (item.passed != null) return Boolean(item.passed)
  return item.status === 'PASS' || item.status === '1'
}

export function getGapText(gap: number | null | undefined): string {
  if (gap == null) return '-'
  if (gap <= 0) return '已达标'
  return `差距 ${gap} 级`
}

export function getRiskLevelType(level?: string | null): string {
  if (!level) return 'info'
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

/**
 * 构建分数面板数据：Milvus 不可用时语义分显示占位文本。
 */
export function buildScoreParts(record?: MatchingRecord | null): ScorePart[] {
  if (!record) return []
  const semanticScore = record.profileSemanticScore ?? record.vectorScore
  return [
    {
      label: '能力模型分',
      value: record.postModelScore,
      desc: '标签命中、等级、权重、必填/核心能力的逐项加权分。',
      icon: ScaleToOriginal,
      color: '#2563eb',
    },
    {
      label: '整体语义分',
      value: semanticScore,
      emptyText: semanticScore == null ? 'Milvus 不可用' : undefined,
      desc: '整人×整岗向量语义相似度（Milvus）。缺失时权重自动分摊。',
      icon: Connection,
      color: '#7c3aed',
    },
    {
      label: '证据可信度',
      value: record.evidenceScore,
      desc: '能力来源可信度 × 时间衰减，综合评估证据可靠性。',
      icon: DataAnalysis,
      color: '#0891b2',
    },
    {
      label: 'AI 建议分',
      value: record.aiScore ?? record.llmScore,
      emptyText: getAiScoreEmptyText(record),
      desc: 'L3 阶段由大模型结合证据生成的建议分。',
      icon: MagicStick,
      color: '#d97706',
    },
    {
      label: '最终分',
      value: record.finalMatchScore ?? record.aiMatchScore,
      desc: 'MatchScoreEngine 合成：能力 + 语义 + 证据 + 质量 + 反馈 + LLM。',
      icon: DocumentChecked,
      color: getScoreColor(record.finalMatchScore ?? record.aiMatchScore),
    },
  ]
}

/**
 * 解析硬条件检查详情 JSON。
 */
export function parseHardConditionDetails(
  hardConditionResult?: string | null,
): HardConditionDetail[] {
  const parsed = parseJson<{ details?: HardConditionDetail[] }>(hardConditionResult)
  return parsed?.details || []
}

/**
 * 生成审批请求体：拒绝时强制要求备注。
 */
export function buildApprovalPayload(
  flow: MatchingApprovalFlow,
  approved: boolean,
  remark: string,
): { flow: MatchingApprovalFlow; approved: boolean; remark: string } {
  return { flow, approved, remark }
}
