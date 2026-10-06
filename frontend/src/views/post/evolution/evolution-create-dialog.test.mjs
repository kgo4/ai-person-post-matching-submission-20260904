import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 契约：选择「目标岗位」必须走远程搜索下拉，不能让人手填岗位 ID。
 *
 * 【2026-09-04 组件拆分】原先断言读的是 index.vue；「运行演化」被拆成
 * EvolutionAgentPanel.vue 后，断言目标随之移动。口径没变：
 *   · 岗位选项通过 pagePosts 拉取（不是写死的 id 输入框）
 *   · 下拉走 remote-method="searchPosts"
 *   · 绑定的是 targetPostId（由 useEvolutionTarget 共享），不是手填数字
 */
const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'EvolutionAgentPanel.vue'), 'utf8')

assert.equal(source.includes('pagePosts'), true, '目标岗位选项必须来自 pagePosts 接口')
assert.equal(source.includes('remote-method="searchPosts"'), true, '岗位下拉必须支持远程搜索')
assert.equal(source.includes('targetPostId'), true, '岗位必须绑定到共享的 targetPostId')
assert.equal(source.includes('<el-input-number'), false, '不允许用手填数字的方式选择岗位 ID')

console.log('post evolution create dialog tests passed')
