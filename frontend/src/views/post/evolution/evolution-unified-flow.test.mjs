import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const root = resolve(currentDir, '../../../..')
const workbench = readFileSync(join(currentDir, 'index.vue'), 'utf8')
const sidebar = readFileSync(join(root, 'src/config/sidebar-menu.ts'), 'utf8')
const postRoutes = readFileSync(join(root, 'src/router/modules/post.ts'), 'utf8')
const overviewPath = join(currentDir, 'EvolutionOverview.vue')

assert.equal(workbench.includes("const activeTab = ref('overview')"), true)
assert.equal(workbench.includes("v-show=\"activeTab === 'overview'\""), true)
assert.equal(workbench.includes('<EvolutionOverview @review-task="openReviewTask" />'), true)
assert.equal(existsSync(overviewPath), true)
const overview = readFileSync(overviewPath, 'utf8')
assert.equal(overview.includes("emit('review-task', event.taskId)"), true)
assert.equal(sidebar.includes("{ label: '岗位动态演化', path: '/post/evolution/dashboard' }"), false)
assert.equal(sidebar.includes("{ label: '能力更新', path: '/post/evolution' }"), false)
assert.equal(sidebar.includes("{ label: '岗位演化', path: '/post/evolution' }"), true)
assert.match(postRoutes, /path: 'evolution\/dashboard',[\s\S]*?redirect: '\/post\/evolution'/)

console.log('post evolution unified flow tests passed')
