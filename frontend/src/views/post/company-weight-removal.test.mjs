import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const root = join(currentDir, '../..')
const routes = readFileSync(join(root, 'router/modules/post.ts'), 'utf8')
const sidebar = readFileSync(join(root, 'config/sidebar-menu.ts'), 'utf8')
const postModelApi = readFileSync(join(root, 'api/post-model.ts'), 'utf8')
const materialDialog = readFileSync(join(currentDir, 'model-config/jd-import-dialog.vue'), 'utf8')
const modelVersion = readFileSync(join(currentDir, 'model-version/index.vue'), 'utf8')

assert.equal(existsSync(join(currentDir, 'model-generation/index.vue')), false)
assert.equal(routes.includes("path: 'model-generation'"), false)
assert.equal(sidebar.includes("path: '/post/model-generation'"), false)
assert.equal(postModelApi.includes('company-post-weight'), false)
assert.equal(postModelApi.includes('/post/company-weight'), false)
assert.equal(postModelApi.includes('generateFromJD'), true)
assert.equal(materialDialog.includes('analyzeJd'), true)
assert.equal(materialDialog.includes('从JD智能分析能力项'), true)
assert.equal(modelVersion.includes('个性化岗位能力权重'), false)
assert.equal(modelVersion.includes('前往岗位能力配置'), true)

console.log('company weight removal tests passed')
