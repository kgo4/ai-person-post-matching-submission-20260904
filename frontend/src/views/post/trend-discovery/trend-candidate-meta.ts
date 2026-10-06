/**
 * 趋势候选的展示口径（纯函数，便于 node 侧直接测）。
 *
 * 抽出这一层的理由：徽标文案、颜色语义、批量确认的前置条件散在模板里，
 * 一旦后端枚举调整（例如新增风险等级），改一处漏一处就会出现
 * 「卡片说可批量、点了却被跳过」。这里集中定义，模板只做渲染。
 */
import type {
  ConfirmStatus,
  HarnessDecision,
  TrendAbilityItem,
  TrendBatchConfirmResult,
  TrendCandidateSummary,
} from '@/api/post-trend'

export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

/** 相似度展示：null 表示本次未取得比对结果，不能显示成 0（会让人误判为「完全不同」）。 */
export function similarityText(score?: number | null): string {
  if (score === null || score === undefined) return '未比对'
  return score.toFixed(2)
}

/** 材料强调度展示 */
export function emphasisText(score?: number | null): string {
  if (score === null || score === undefined) return '—'
  return score.toFixed(1)
}

/** 治理判定徽标。未知取值按「需人工」处理，不给绿色。 */
export function harnessBadge(decision?: HarnessDecision | string | null): { label: string; type: TagType } {
  switch (decision) {
    case 'PASS':
      return { label: '可批量确认', type: 'success' }
    case 'REVIEW':
      return { label: '待复核', type: 'warning' }
    case 'BLOCK':
      return { label: '已拦截', type: 'danger' }
    default:
      return { label: '未判定', type: 'info' }
  }
}

/** 确认状态文案 */
export function confirmStatusBadge(status?: ConfirmStatus | string | null): { label: string; type: TagType } {
  switch (status) {
    case 'PENDING':
      return { label: '待确认', type: 'primary' }
    case 'APPROVED':
      return { label: '已通过', type: 'success' }
    case 'REJECTED':
      return { label: '已驳回', type: 'info' }
    default:
      return { label: '未知', type: 'info' }
  }
}

/** 候选类型文案 */
export function candidateTypeLabel(type?: string | null): string {
  if (type === 'ABILITY_CHANGE') return '能力变更'
  if (type === 'NEW_POST') return '新岗位'
  return '候选'
}

/** 变更类型文案。绝不出现「删除」：材料没提到某项能力不等于岗位不再需要它。 */
export function changeTypeLabel(changeType?: string | null): string {
  switch (changeType) {
    case 'ADD':
      return '新增'
    case 'UPGRADE_LEVEL':
      return '要求提高'
    case 'DOWNGRADE_LEVEL':
      return '要求下调'
    case 'UPDATE_WEIGHT':
      return '权重调整'
    case 'UNCHANGED':
      return '无变化'
    default:
      return '—'
  }
}

export function changeTypeTagType(changeType?: string | null): TagType {
  switch (changeType) {
    case 'ADD':
      return 'success'
    case 'UPGRADE_LEVEL':
      return 'warning'
    case 'DOWNGRADE_LEVEL':
      return 'info'
    case 'UPDATE_WEIGHT':
      return 'primary'
    default:
      return 'info'
  }
}

/** 跨来源印证：≥2 类材料同时提到才显示徽标 */
export function coverageBadge(coverage?: number | null): string | null {
  if (coverage === null || coverage === undefined || coverage < 2) return null
  return `${coverage} 类材料印证`
}

/**
 * 批量确认的前置条件。
 * 返回 null 表示可以批量确认；否则返回不可批量确认的中文原因
 * （与后端 `PostTrendApiFacade.blockReason` 保持同一口径）。
 */
export function batchConfirmBlockReason(candidate: TrendCandidateSummary): string | null {
  if (candidate.confirmStatus !== 'PENDING') return '已处理'
  switch (candidate.harnessDecision) {
    case 'PASS':
      return null
    case 'REVIEW':
      return '治理判定为待复核，需逐条确认'
    case 'BLOCK':
      return '治理判定为已拦截，不允许落地'
    default:
      return '缺少治理判定结果，需人工确认'
  }
}

/** 从一批候选里挑出可批量确认的ID */
export function selectableForBatch(candidates: TrendCandidateSummary[]): number[] {
  return candidates.filter(item => batchConfirmBlockReason(item) === null).map(item => item.id)
}

/** 权重合计提示：与后端 batchConfig 的 95-105 校验同口径，提前在界面上暴露风险 */
export function weightTotalHint(items: Pick<TrendAbilityItem, 'suggestedWeight'>[]): string | null {
  const total = items.reduce((sum, item) => sum + (Number(item.suggestedWeight) || 0), 0)
  if (items.length === 0) return null
  if (total < 95 || total > 105) {
    return `当前权重合计 ${total.toFixed(1)}，落库时会按比例自动归一为 100`
  }
  return null
}

/** 批量确认结果的人类可读摘要，被跳过的一定要说清原因 */
export function describeBatchResult(result: TrendBatchConfirmResult): string {
  const landed = result.landed?.length ?? 0
  const skipped = result.skipped?.length ?? 0
  if (landed === 0 && skipped === 0) return '没有可落地的候选'
  const parts: string[] = []
  if (landed > 0) parts.push(`已落地 ${landed} 项`)
  if (skipped > 0) parts.push(`跳过 ${skipped} 项`)
  return parts.join('，')
}

/** 落地结果文案：区分「已创建」与「已更新」 */
export function describeLandResult(result: {
  postName: string
  createdNewPost: boolean
  abilityCount: number
}): string {
  const action = result.createdNewPost ? '已创建岗位' : '已更新岗位能力'
  return `${action}「${result.postName}」，写入 ${result.abilityCount} 项能力`
}

/** 任务是否仍在运行（决定是否继续轮询） */
export function isTaskRunning(taskStatus?: string | null): boolean {
  return taskStatus === 'PENDING' || taskStatus === 'RUNNING'
}

/** 任务是否等待人工确认 */
export function isTaskAwaitingConfirm(taskStatus?: string | null): boolean {
  return taskStatus === 'WAIT_CONFIRM'
}
