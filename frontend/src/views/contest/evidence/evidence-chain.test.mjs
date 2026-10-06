import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const page = readFileSync(join(currentDir, '../../capability-brain/evidence/index.vue'), 'utf8')
const evidenceCenter = readFileSync(join(currentDir, '../../../composables/useEvidenceCenter.ts'), 'utf8')
const api = readFileSync(join(currentDir, '../../../api/contest.ts'), 'utf8')
const flowGraph = readFileSync(join(currentDir, '../../capability-brain/evidence/EvidenceFlowGraph.vue'), 'utf8')
const utils = readFileSync(join(currentDir, '../../capability-brain/evidence/utils.ts'), 'utf8')

for (const token of [
  'chainMode',
  'remoteSearchSubjects',
  'loadEvidenceChain',
  'selectedAbility',
  'EvidenceFlowGraph',
  'abilityKeyword',
  'activeTab',
  'getVerifiedEvidenceCount',
  'summaryStats',
]) {
  assert.equal(page.includes(token), true, `evidence center should include ${token}`)
}

// 已移除对知识图谱 API 的死调用（graphData 从未被渲染）
assert.equal(page.includes('graphData'), false, 'evidence center should not reference unused graphData')
assert.equal(evidenceCenter.includes('getEmployeeCenteredGraph'), false, 'composable should not fetch unused kg graph')

for (const token of [
  'pageEmployees',
  'pagePosts',
  'getEmployeeEvidenceChain',
  'getPostEvidenceChain',
]) {
  assert.equal(evidenceCenter.includes(token), true, `evidence center composable should include ${token}`)
}

for (const label of ['能力证据中心', '查看证据链', '来源渠道分布', '证据质量', '证据链追溯', '证据管理', '证据总数', '平均可信度']) {
  assert.equal(page.includes(label), true, `missing visible label: ${label}`)
}

// 证据流可视化组件：主体 → 能力 → 证据 三层结构
for (const token of ['chainData', 'select-ability', 'SUPPORTED_BY', 'buildFlowGraph']) {
  assert.equal(flowGraph.includes(token), true, `flow graph should include ${token}`)
}

// 工具函数：强度判定基于已验证证据数 + 目标类型中文映射
assert.equal(utils.includes('getVerifiedEvidenceCount'), true, 'utils should define verified evidence count')
assert.equal(utils.includes('getTargetTypeText'), true, 'utils should map target type to Chinese')

assert.equal(api.includes('export interface EvidenceChain'), true, 'contest api should define EvidenceChain')
assert.equal(api.includes('getEmployeeEvidenceChain'), true, 'contest api should expose employee chain endpoint')
assert.equal(api.includes('getPostEvidenceChain'), true, 'contest api should expose post chain endpoint')

console.log('evidence chain page tests passed')
