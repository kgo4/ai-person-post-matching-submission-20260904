import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const configSource = readFileSync(join(currentDir, '../../config/sidebar-menu.ts'), 'utf8')
const sidebarSource = readFileSync(join(currentDir, 'components/AppSidebar.vue'), 'utf8')

for (const moduleKey of ["key: 'dashboard'", "key: 'employee'", "key: 'post'", "key: 'matching'", "key: 'learning'", "key: 'contest'", "key: 'knowledge-assets'", "key: 'ai-governance'", "key: 'system'"]) {
  assert.equal(configSource.includes(moduleKey), true, `missing business nav module: ${moduleKey}`)
}

for (const path of [
  "path: '/dashboard'",
  "path: '/employee/ability-profile'",
  "path: '/employee/list'",
  "path: '/post/model-config'",
  "path: '/post/panorama'",
  "path: '/post/model-version'",
  "path: '/post/evolution'",
  "path: '/post/list'",
  "path: '/post/excel-import'",
  "path: '/post/emerging-post'",
  "path: '/matching/execute'",
  "path: '/matching/result'",
  "path: '/matching/gap-diagnosis'",
  "path: '/matching/approval-tasks'",
  "path: '/matching/black-white-list'",
  "path: '/matching/calibration'",
  "path: '/learning/path'",
  "path: '/learning/resources'",
  "path: '/system/ability-tag'",
  "path: '/capability-brain/evidence'",
  "path: '/kg/workbench'",
  "path: '/rag/knowledge'",
  "path: '/ai-governance/records'",
]) {
  assert.equal(configSource.includes(path), true, `missing business nav path: ${path}`)
}

for (const hiddenOrRemovedPath of [
  "path: '/post/evolution/dashboard'",
  "path: '/post/prototype'",
  "path: '/matching/training'",
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

assert.equal(sidebarSource.includes('const orderedModules = computed'), true, 'sidebar should render the explicit module order')
assert.equal(sidebarSource.includes('const activeChildren = computed'), true, 'sidebar should render active module children')
assert.equal(sidebarSource.includes('class="sidebar-subnav"'), true, 'sidebar should render active module child navigation')

console.log('capability nav tests passed')
