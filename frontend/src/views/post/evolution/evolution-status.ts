/**
 * 岗位演化任务的状态/触发方式映射。
 *
 * 抽成纯 ts 的原因是它有两个消费方：「运行演化」的结果卡与「变更审核」的任务列表。
 * 两处若各写一套，同一个 WAIT_CONFIRM 可能在一处显示「待确认」、另一处显示「待审核」，
 * 颜色的含义也会漂移。这里集中一份，并配 `.test.mjs` 锁死取值。
 */

/** 任务状态 → el-tag 类型 */
export const STATUS_TAG_TYPE: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  PENDING: 'info',
  RUNNING: 'warning',
  WAIT_CONFIRM: 'primary',
  APPLIED: 'success',
  FAILED: 'danger',
}

/** 任务状态 → 中文名 */
export const STATUS_LABEL: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '运行中',
  WAIT_CONFIRM: '待确认',
  APPLIED: '已应用',
  FAILED: '失败',
}

/** 触发方式 → 中文名 */
export const TRIGGER_TYPE_LABEL: Record<string, string> = {
  MANUAL_RUN: '手动运行',
  MANUAL_UPLOAD: '资料上传',
  SCHEDULED: '定时执行',
  CLOUD_SYNC: '云知识库同步',
  MARKET_DISCOVERY: '市场发现线索',
}

/** 未知状态回退 info，避免新增状态时页面直接渲染成空白 */
export function statusTagType(status?: string | null) {
  return STATUS_TAG_TYPE[status || ''] || 'info'
}

/** 未知状态回显原值：显示「SOMETHING_NEW」总比显示空字符串好排查 */
export function statusText(status?: string | null) {
  if (!status) return '—'
  return STATUS_LABEL[status] || status
}

export function triggerTypeText(triggerType?: string | null) {
  if (!triggerType) return '—'
  return TRIGGER_TYPE_LABEL[triggerType] || triggerType
}

/**
 * 任务是否可删除。
 *
 * RUNNING 的任务不允许删除：后端仍在写它的变更项与证据，删掉会留下孤儿数据。
 * progressStatus 也要看 —— 任务状态可能已经推进，但进度还在跑。
 */
export function canDeleteTask(task: { taskStatus?: string | null; progressStatus?: string | null }) {
  return task.taskStatus !== 'RUNNING' && task.progressStatus !== 'RUNNING'
}
