import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(currentDir, '../../..')
const matchingRoute = readFileSync(join(frontendRoot, 'router/modules/matching.ts'), 'utf8')
const sidebarConfig = readFileSync(join(frontendRoot, 'config/sidebar-menu.ts'), 'utf8')

assert.equal(sidebarConfig.includes("path: '/matching/gap-diagnosis'"), true)
assert.equal(matchingRoute.includes("path: 'gap-diagnosis'"), true)
assert.equal(matchingRoute.includes("@/views/matching/gap-diagnosis/index.vue"), true)
assert.equal(existsSync(join(currentDir, 'index.vue')), true)

console.log('gap diagnosis route tests passed')
