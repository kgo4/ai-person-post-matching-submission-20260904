import type { TaskType } from './task'

/**
 * 任务类型 → 查看该任务所需的权限。
 *
 * **未登记的类型视为「本人任务」**：所有登录角色都能看到自己的。
 * `video-analysis`（面试分析）落点是 `/employee/ability-profile/live-interview`，
 * 该路径对所有登录角色开放（能力画像页内按角色锁域），因此刻意不登记。
 *
 * `matching` 不同：它是 HR 面向全量人员/岗位发起的**批量**任务，
 * 接口侧要求 `MATCHING:READ`（见 `SecurityConfig` 的
 * `/api/matching/record/task/**` 规则）。前端必须用**同一个权限码**收口，
 * 否则会出现「无权限角色看到红点、点进去 403」——
 * 这正是 2026-09-04「匹配完成通知所有角色都收到」的成因。
 *
 * `pms-analysis` 同理（2026-09-04 调整）：PMS 项目分析已独立成 **HR 专属**功能，
 * 任务落点从原先全员可达的 `/employee/ability-profile/pms-analysis` 改为
 * `/employee/pms-analysis`（路由 meta 与 `path-permissions.ts` 都要求 ASSESSMENT:MANAGE）。
 * 本 store 持久化在 localStorage、**同一浏览器换账号登录会被原样恢复**，
 * 所以不登记就会让员工/岗位管理员看到 HR 残留的 PMS 任务、点进去被守卫拦下 ——
 * 与上面 matching 是同一种失败方式。此处的权限码必须与路由落点保持一致。
 *
 * 把规则收在这一个文件，是为了避免「后端接口一个码、前端写入侧一个码、
 * 前端渲染侧又一个码」三处漂移。
 */
const TASK_TYPE_PERMISSION: Partial<Record<TaskType, string>> = {
  matching: 'MATCHING:READ',
  // 与 /employee/pms-analysis 的路由 meta、PATH_PERMISSIONS 同码
  'pms-analysis': 'ASSESSMENT:MANAGE',
}

/** 当前权限能否查看该类型的任务 */
export function canViewTaskType(type: TaskType, permissions: readonly string[]): boolean {
  const required = TASK_TYPE_PERMISSION[type]
  return required === undefined || permissions.includes(required)
}

/** 过滤出当前权限可见的任务（不修改入参） */
export function filterVisibleTasks<T extends { type: TaskType }>(
  tasks: readonly T[],
  permissions: readonly string[],
): T[] {
  return tasks.filter(task => canViewTaskType(task.type, permissions))
}
