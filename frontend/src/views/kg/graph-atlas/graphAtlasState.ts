/**
 * 图谱页面 - 状态辅助纯函数
 */

/**
 * 从路由 query 解析员工/岗位选择（仅接受纯数字字符串）
 */
export function readGraphRouteSelection(query: Record<string, unknown>): {
  employeeId: number | undefined
  postId: number | undefined
} {
  const parse = (value: unknown) =>
    typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : undefined
  return { employeeId: parse(query.employeeId), postId: parse(query.postId) }
}

/**
 * 判断构建轮询是否超时
 */
export function isBuildPollingExpired(elapsedMs: number, timeoutMs = 30_000): boolean {
  return elapsedMs > timeoutMs
}
