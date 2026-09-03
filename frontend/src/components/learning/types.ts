/**
 * Learning Path Workbench shared types
 */

// ===== Step status =====
export type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'SUBMITTED' | 'REVISION_REQUIRED'

export const STATUS_META: Record<StepStatus, { label: string; color: string; bg: string }> = {
  PENDING:            { label: '未开始', color: '#6b7280', bg: '#f3f4f6' },
  IN_PROGRESS:        { label: '进行中', color: '#2563eb', bg: '#dbeafe' },
  COMPLETED:          { label: '已完成', color: '#059669', bg: '#d1fae5' },
  SKIPPED:            { label: '已跳过', color: '#9ca3af', bg: '#f3f4f6' },
  SUBMITTED:          { label: '待验证', color: '#d97706', bg: '#fef3c7' },
  REVISION_REQUIRED:  { label: '需修改', color: '#dc2626', bg: '#fee2e2' },
}

export function getStatusMeta(status: string) {
  return STATUS_META[status as StepStatus] || { label: status, color: '#6b7280', bg: '#f3f4f6' }
}

// ===== Priority =====
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

export const PRIORITY_META: Record<Priority, { label: string; color: string; bg: string }> = {
  HIGH:   { label: '高', color: '#dc2626', bg: '#fee2e2' },
  MEDIUM: { label: '中', color: '#d97706', bg: '#fef3c7' },
  LOW:    { label: '低', color: '#6b7280', bg: '#f3f4f6' },
}

export function getPriorityMeta(priority: string) {
  return PRIORITY_META[priority as Priority] || { label: priority, color: '#6b7280', bg: '#f3f4f6' }
}

// ===== Difficulty =====
export const DIFFICULTY_META: Record<string, { label: string; color: string }> = {
  EASY:   { label: '简单', color: '#059669' },
  MEDIUM: { label: '中等', color: '#d97706' },
  HARD:   { label: '困难', color: '#dc2626' },
}

export function getDifficultyMeta(difficulty: string) {
  return DIFFICULTY_META[difficulty] || { label: difficulty, color: '#6b7280' }
}

// ===== Gap filter =====
export type GapFilter = 'ALL' | 'HIGH' | 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

// ===== Computed helpers =====
export function formatScore(score?: number | null) {
  return score == null ? '-' : Number(score).toFixed(1)
}

export function progressPercent(completed: number, total: number) {
  if (total === 0) return 0
  return Math.round((completed / total) * 100)
}
