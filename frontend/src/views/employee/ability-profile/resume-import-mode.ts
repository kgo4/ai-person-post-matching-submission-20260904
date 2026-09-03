export const DEFAULT_RESUME_AUTO_IMPORT = true

export function canManuallyImport(autoImport?: boolean, importStatus?: string): boolean {
  return autoImport !== true || importStatus === 'FAILED'
}

export function importStatusLabel(status?: string): string {
  const labels: Record<string, string> = {
    NOT_REQUESTED: '等待手动导入',
    PENDING: '等待自动导入',
    SUCCEEDED: '已导入',
    REVIEW_REQUIRED: '等待审核',
    BLOCKED: '已拦截',
    NO_CLAIMS: '未提取到能力',
    FAILED: '导入失败',
  }
  return labels[status || ''] || '-'
}

export function importStatusType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED' || status === 'BLOCKED') return 'danger'
  if (status === 'REVIEW_REQUIRED') return 'warning'
  return 'info'
}
