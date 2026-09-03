import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const configSource = readFileSync(join(currentDir, '../../config/sidebar-menu.ts'), 'utf8')
const layoutSource = readFileSync(join(currentDir, 'index.vue'), 'utf8')
const sidebarSource = readFileSync(join(currentDir, 'components/AppSidebar.vue'), 'utf8')

assert.equal(configSource.includes('export function getSidebarModules'), true)
assert.equal(configSource.includes("path: '/kg/workbench'"), true)
assert.equal(configSource.includes("path: '/post/panorama'"), true)
assert.equal(layoutSource.includes("import AppSidebar from './components/AppSidebar.vue'"), true)
assert.equal(sidebarSource.includes("import { getSidebarModules, MODULE_ORDER"), true)

console.log('layout compatibility tests passed')
