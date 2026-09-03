import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const configSource = readFileSync(join(currentDir, '../../config/sidebar-menu.ts'), 'utf8')
const source = readFileSync(join(currentDir, 'components/AppSidebar.vue'), 'utf8')

assert.equal(source.includes('const modules = computed'), true)
assert.equal(source.includes('getSidebarModules()'), true)
assert.equal(source.includes('{{ item.summary }}'), true)
assert.equal(source.includes('class="sidebar-intro"'), true)
assert.equal(source.includes('const activeChildren = computed'), true)
assert.equal(source.includes('class="sidebar-subnav"'), true)

const employeeModule = configSource.slice(configSource.indexOf("key: 'employee'"), configSource.indexOf("key: 'post'"))
assert.equal(employeeModule.includes("path: '/employee/ability-profile'"), true)
assert.equal(employeeModule.includes("path: '/employee/list'"), true)

assert.match(source, /\.layout-sidebar \{[^}]*background:[^}]*backdrop-filter: blur\(18px\)/)
assert.match(source, /\.layout-sidebar \{[^}]*width: 300px/)
assert.match(source, /\.layout-sidebar--collapsed \{ width: 88px/)
assert.match(source, /\.sidebar-logo-icon \{[^}]*width: 42px[^}]*height: 42px/)

console.log('enterprise sidebar tests passed')
