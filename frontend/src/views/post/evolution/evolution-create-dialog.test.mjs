import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'index.vue'), 'utf8')

assert.equal(source.includes('pagePosts'), true)
assert.equal(source.includes('remote-method="searchPosts"'), true)
assert.equal(source.includes('agentForm.postId'), true)
assert.equal(source.includes('<el-input-number v-model="createForm.postId"'), false)

console.log('post evolution create dialog tests passed')
