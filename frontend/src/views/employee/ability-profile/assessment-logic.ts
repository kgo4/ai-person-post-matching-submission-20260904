/**
 * 评估页轮询规则
 * <p>
 * 页面只依据后端返回的状态做展示与轮询决策：
 * - PENDING/RUNNING：任务在途，需要轮询；
 * - WAITING_USER：等待候选人作答或 HR 操作，不轮询高频（返回页面/手动刷新/提交动作后刷新）。
 */
const POLL_WORKFLOW_STATUSES = new Set([
  'RESUME_PARSING',
  'TEST_GENERATING',
  'TEST_EVALUATING',
  'INTERVIEW_PREPARING',
  'INTERVIEW_ANALYZING',
  'AGGREGATE_HARNESS_RUNNING',
  'LEVEL_CONFIRMING',
])

/** 阶段运行活跃状态（需要轮询） */
const ACTIVE_RUN_STATUSES = new Set(['PENDING', 'RUNNING'])

/** 等待用户状态（不轮询高频） */
const WAITING_USER_RUN_STATUSES = new Set(['WAITING_USER'])

export function shouldPollWorkflowStatus(status?: string): boolean {
  return status != null && POLL_WORKFLOW_STATUSES.has(status)
}

/**
 * 依据阶段运行状态决定是否轮询：
 * WAITING_USER 不轮询高频，只在返回页面/手动刷新/提交动作后刷新。
 */
export function shouldPollByRunStatus(runStatus?: string): boolean {
  return runStatus != null && ACTIVE_RUN_STATUSES.has(runStatus)
}

export function isWaitingUser(runStatus?: string): boolean {
  return runStatus != null && WAITING_USER_RUN_STATUSES.has(runStatus)
}
