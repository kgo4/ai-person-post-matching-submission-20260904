import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const page = readFileSync(join(currentDir, 'index.vue'), 'utf8')
const sidebar = readFileSync(join(currentDir, '../../../config/sidebar-menu.ts'), 'utf8')
const graphPage = readFileSync(join(currentDir, '../../kg/graph-atlas/index.vue'), 'utf8')
const evidencePage = readFileSync(join(currentDir, '../../capability-brain/evidence/index.vue'), 'utf8')

// 页面定位(业务化)
assert.equal(page.includes('AI 知识资产'), true)
assert.equal(page.includes('AI 回答问题的资料底库'), true)
assert.equal(page.includes('图谱看关系'), true)
// 指标与表格业务化
assert.equal(page.includes('资料总数'), true)
assert.equal(page.includes('已就绪'), true)
assert.equal(page.includes('待就绪'), true)
assert.equal(page.includes('资料片段'), true)
assert.equal(page.includes('资料名称'), true)
assert.equal(page.includes('业务资料'), true)
assert.equal(page.includes('知识文档'), true)
assert.equal(page.includes('分片/索引'), true)
assert.equal(page.includes('AI 场景引用'), true)
assert.equal(page.includes('docStatus: statusFilter'), true)
assert.equal(page.includes('pagination.current = 1'), true)
assert.equal(page.includes('未命名文档'), true)
assert.equal(page.includes('未知来源'), true)
assert.equal(page.includes(':data="documents"'), true)
// 检索演示
assert.equal(page.includes('试一试:AI 会引用哪些资料'), true)
assert.equal(page.includes('匹配分析时'), true)
// 高级运维折叠区
assert.equal(page.includes('高级运维'), true)
assert.equal(page.includes('el-collapse'), true)
// 菜单:旧入口名称不再出现
assert.equal(sidebar.includes('AI 检索知识库'), false)
assert.equal(sidebar.includes('AI 长读图谱'), false)
// 与图谱互引
assert.equal(graphPage.includes('管理知识资料'), true)
// 证据中心跳转直达新入口
assert.equal(evidencePage.includes("router.push('/rag/knowledge')"), true)

console.log('knowledge assets page tests passed')
