import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const configSource = readFileSync(join(currentDir, '../../config/sidebar-menu.ts'), 'utf8')
const sidebarSource = readFileSync(join(currentDir, 'components/AppSidebar.vue'), 'utf8')

// 2026-09-04：「数据看板」已并入工作台（匹配分析区块，仅 HR 渲染），不再作为独立导航模块。
for (const moduleKey of ["key: 'employee'", "key: 'post'", "key: 'matching'", "key: 'learning'", "key: 'contest'", "key: 'knowledge-assets'", "key: 'ai-governance'", "key: 'system'"]) {
  assert.equal(configSource.includes(moduleKey), true, `missing business nav module: ${moduleKey}`)
}
assert.equal(
  configSource.includes("key: 'dashboard'"),
  false,
  '「数据看板」应已并入工作台，不应再出现独立导航模块',
)

for (const path of [
  "path: '/employee/ability-profile'",
  "path: '/employee/list'",
  "path: '/post/model-config'",
  "path: '/post/panorama'",
  "path: '/post/model-version'",
  "path: '/post/evolution'",
  "path: '/post/list'",
  "path: '/post/excel-import'",
  "path: '/post/trend-discovery'",
  "path: '/post/market-jd-stats'",
  "path: '/matching/execute'",
  "path: '/matching/self-execute'",
  "path: '/matching/result'",
  "path: '/matching/gap-diagnosis'",
  "path: '/matching/black-white-list'",
  "path: '/matching/calibration'",
  "path: '/learning/path'",
  "path: '/learning/resources'",
  "path: '/capability-brain/evidence'",
  "path: '/kg/workbench'",
  "path: '/rag/knowledge'",
  "path: '/ai-governance/records'",
  // 【2026-09-04】孤儿页补入口：这 4 个页面此前有路由、有页面文件，
  // 但侧边栏没登记、也没有任何跳转指向它们（只能手敲 URL），
  // 连超级管理员在侧边栏里都看不到入口。补上并在这里锁死。
  "path: '/matching/feedback'",
  "path: '/learning/path-enhanced'",
  "path: '/kg/snapshot'",
]) {
  assert.equal(configSource.includes(path), true, `missing business nav path: ${path}`)
}

for (const hiddenOrRemovedPath of [
  "path: '/post/evolution/dashboard'",
  "path: '/post/prototype'",
  "path: '/matching/training'",
  // 2026-09-04：「审批任务」已下线（待办队列无生产者，恒为空），
  // 审批动作本身仍在匹配详情页，权限码 MATCHING:APPROVE 保留。
  "path: '/matching/approval-tasks'",
  "path: '/capability-brain/overview'",
  "path: '/capability-brain/evolution'",
  "path: '/capability-brain/learning/path'",
  "path: '/capability-brain/report'",
]) {
  assert.equal(configSource.includes(hiddenOrRemovedPath), false, `${hiddenOrRemovedPath} should not appear in the primary sidebar`)
}

const orderMatch = configSource.match(/export const MODULE_ORDER = \[(.*?)\] as const/s)
assert.ok(orderMatch, 'sidebar module order should be explicit')
assert.equal(orderMatch[1].includes("'contest'"), true, 'contest module should remain part of sidebar order')
assert.equal(configSource.includes('},,'), false, 'sidebar module list should not contain sparse array holes')

// 模块顺序由 config/sidebar-menu.filterSidebarModules 统一排序，侧边栏只消费过滤结果
assert.equal(sidebarSource.includes('filterSidebarModules'), true, 'sidebar should consume the shared module-order filter')
assert.equal(configSource.includes('(MODULE_ORDER as readonly string[]).indexOf(a.key)'), true, 'module order should be enforced in one place')
assert.equal(sidebarSource.includes('const activeChildren = computed'), true, 'sidebar should render active module children')
assert.equal(sidebarSource.includes('class="sidebar-subnav"'), true, 'sidebar should render active module child navigation')

console.log('capability nav tests passed')
