import assert from 'node:assert/strict'
import { canViewTaskType, filterVisibleTasks } from './task-visibility.ts'
import { satisfiesPathPermission } from '../../router/path-permissions.ts'

// HR（含 ASSESSMENT:MANAGE：PMS 项目分析是 HR 专属功能）
const HR = ['EMPLOYEE:READ', 'ASSESSMENT:MANAGE', 'MATCHING:READ', 'MATCHING:EXECUTE']
const ARCHITECT = ['POST:READ', 'POST:MANAGE', 'POST:EVOLUTION']
const EMPLOYEE = ['EMPLOYEE:READ', 'ASSESSMENT:SELF']

/* ============ canViewTaskType ============ */
assert.equal(canViewTaskType('matching', HR), true)
assert.equal(canViewTaskType('matching', ARCHITECT), false)
assert.equal(canViewTaskType('matching', EMPLOYEE), false)
assert.equal(canViewTaskType('matching', []), false)

// PMS 分析任务：HR 专属（落点 /employee/pms-analysis 是 HR 页）
assert.equal(canViewTaskType('pms-analysis', HR), true)
assert.equal(canViewTaskType('pms-analysis', ARCHITECT), false)
assert.equal(canViewTaskType('pms-analysis', EMPLOYEE), false)
assert.equal(canViewTaskType('pms-analysis', []), false)

// 未登记的类型 = 本人任务，所有登录角色可见（包括空权限）
// 注意：本文件是 .mjs（纯 JS，由 node --experimental-strip-types 运行），
// 不能写 TS 语法（`as const` 会直接 SyntaxError）。
for (const type of ['video-analysis']) {
  assert.equal(canViewTaskType(type, EMPLOYEE), true, `${type} 应对员工可见`)
  assert.equal(canViewTaskType(type, ARCHITECT), true, `${type} 应可见`)
  assert.equal(canViewTaskType(type, []), true, `${type} 未登记，应放行`)
}

/* ============ 跨来源一致性：任务可见性 vs 路由落点 ============ */
// 顶栏任务条目的落点（NotificationBell 的 taskRouteMap）与被点开的路由守卫规则
// 必须用同一个权限码，否则就是「铃铛有红点 → 点进去 403」。
// 这里不硬编码权限码，而是把「任务可见性」与「路径可达性」在多个身份下逐一对齐，
// 任何一处漂移都会立刻失败。
const PMS_ROUTE = '/employee/pms-analysis'
for (const perms of [HR, ARCHITECT, EMPLOYEE, []]) {
  assert.equal(
    canViewTaskType('pms-analysis', perms),
    satisfiesPathPermission(PMS_ROUTE, perms),
    `pms-analysis 任务可见性与落点 ${PMS_ROUTE} 的可达性不一致：${JSON.stringify(perms)}`,
  )
}

/* ============ filterVisibleTasks ============ */
const tasks = [
  { id: 'm1', type: 'matching' },
  { id: 'v1', type: 'video-analysis' },
  { id: 'p1', type: 'pms-analysis' },
]

// 岗位管理员：匹配任务与 PMS 任务都被剔除，本人任务保留
assert.deepEqual(
  filterVisibleTasks(tasks, ARCHITECT).map(t => t.id),
  ['v1'],
)
// HR：全都保留
assert.deepEqual(
  filterVisibleTasks(tasks, HR).map(t => t.id),
  ['m1', 'v1', 'p1'],
)
// 员工：只保留本人任务（面试分析）；PMS 任务是 HR 的，不得出现
assert.deepEqual(
  filterVisibleTasks(tasks, EMPLOYEE).map(t => t.id),
  ['v1'],
)
// 空权限：只剩真正未登记的本人任务
assert.deepEqual(
  filterVisibleTasks(tasks, []).map(t => t.id),
  ['v1'],
)

// 不得修改入参（调用方常直接传 store 的响应式数组）
const snapshot = JSON.stringify(tasks)
filterVisibleTasks(tasks, ARCHITECT)
assert.equal(JSON.stringify(tasks), snapshot, 'filterVisibleTasks 不应修改入参')

// 空数组、无匹配项
assert.deepEqual(filterVisibleTasks([], HR), [])
assert.deepEqual(filterVisibleTasks([{ id: 'x', type: 'matching' }], []), [])

console.log('task visibility tests passed')
