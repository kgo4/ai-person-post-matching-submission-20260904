import type { MatchingRecord } from '@/api'

export function parseJson(raw: string) {
  try { return JSON.parse(raw) } catch { return null }
}

export function parseReport(raw: unknown) {
  if (!raw) return null
  if (typeof raw === 'object') return raw
  return parseJson(raw as string)
}

export function formatScore(score?: number | null) {
  return score == null ? '-' : Number(score).toFixed(2)
}

export function formatScorePart(part: { value?: number | null; emptyText?: string }) {
  return part.value == null ? (part.emptyText || '-') : Number(part.value).toFixed(2)
}

export function getAiScoreEmptyText(record: MatchingRecord) {
  if (record.screeningLevel == null || record.screeningLevel < 3) return '未触发'
  return '-'
}

export function getScoreColor(score?: number | null) {
  if (score == null) return '#64748b'
  if (score >= 90) return '#059669'
  if (score >= 75) return '#2563eb'
  if (score >= 60) return '#d97706'
  return '#dc2626'
}

export function formatPercentWeight(weight?: number | null) {
  if (weight == null) return '-'
  return `${Math.round(Number(weight) * 100)}%`
}

export function getMatchStatusText(status?: number) {
  const map: Record<number, string> = { 0: '待审核', 1: '强匹配', 2: '匹配', 3: '待观察', 4: '不匹配' }
  return status == null ? '-' : map[status] || '未知'
}

export function getMatchStatusType(status?: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'primary'
  if (status === 3) return 'warning'
  if (status === 4) return 'danger'
  return 'info'
}

export function getApprovalStatusText(status?: number) {
  const map: Record<number, string> = { 0: '未发起', 1: '审批中', 2: '已通过', 3: '已驳回' }
  return status == null ? '-' : map[status] || '未知'
}

export function getApprovalStatusType(status?: number) {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 1) return 'warning'
  return 'info'
}

export function getScreeningLevelText(level?: number) {
  const map: Record<number, string> = { 1: 'L1 硬性条件', 2: 'L2 能力模型', 3: 'L3 AI 深度分析' }
  return level == null ? '-' : map[level] || '-'
}

export function isPassed(row: Record<string, unknown>) {
  return row?.status === '达标' || row?.status === '通过' || Number(row?.actualLevel || 0) >= Number(row?.requiredLevel || 0)
}

export function getGapText(row: Record<string, unknown>) {
  const actual = Number(row.actualLevel || 0)
  const required = Number(row.requiredLevel || 0)
  if (actual >= required) return actual > required ? `已达标，超出 ${actual - required} 级` : '刚好达标'
  return `差 ${required - actual} 级`
}

export function getRiskLevelType(level: string) {
  switch (level) {
    case 'HIGH': return 'danger'
    case 'MEDIUM': return 'warning'
    case 'LOW': return 'info'
    default: return 'info'
  }
}
