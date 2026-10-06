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

// 默认落在「动态概览」；重构后 ref 带上了 <string> 泛型，所以用正则而不是字面量匹配。
assert.match(workbench, /const activeTab = ref<[^>]*>\('overview'\)/, '默认 Tab 必须是动态概览')
assert.equal(workbench.includes("v-show=\"activeTab === 'overview'\""), true)
assert.equal(workbench.includes('<EvolutionOverview @review-task="openReviewTask" />'), true)

// 简洁化重构的契约：撤销「资料输入」Tab、删除死代码 dashboard.vue。
// 这两条是本次重构的核心承诺，必须由测试锁住，否则下次很容易又被加回来。
assert.equal(workbench.includes("{ key: 'sources'"), false, '「资料输入」Tab 已撤销，不应再出现')
assert.equal(existsSync(join(currentDir, 'dashboard.vue')), false, 'dashboard.vue 是死代码，已删除')
for (const key of ['overview', 'agent', 'review', 'schedule']) {
  assert.equal(workbench.includes(`{ key: '${key}',`), true, `缺少 Tab: ${key}`)
}
assert.equal(existsSync(overviewPath), true)
const overview = readFileSync(overviewPath, 'utf8')
assert.equal(overview.includes("emit('review-task', event.taskId)"), true)
assert.equal(sidebar.includes("{ label: '岗位动态演化', path: '/post/evolution/dashboard' }"), false)
assert.equal(sidebar.includes("{ label: '能力更新', path: '/post/evolution' }"), false)
// 不再用整行字面量匹配：菜单项后来补了 permission / roles 字段，精确字符串会随之失效。
// 这里只锁「标签 + 路径」这一对核心口径，字段增减不影响断言。
assert.match(sidebar, /\{ label: '岗位演化', path: '\/post\/evolution'/, '岗位演化入口必须留在侧边栏')
assert.match(postRoutes, /path: 'evolution\/dashboard',[\s\S]*?redirect: '\/post\/evolution'/)

console.log('post evolution unified flow tests passed')
