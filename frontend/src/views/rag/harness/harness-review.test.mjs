import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  abilityRequirementText,
  describeInspectionPost,
  describePersonCounts,
  inspectionAbilityEvidence,
  inspectionAbilityName,
  inspectionAbilitySourceLabel,
  isAiAcceptEligible,
  selectAiAcceptIds,
} from './harness-review.ts'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'index.vue'), 'utf8')

// ===== 批量通过白名单：四个条件缺一不可 =====
const eligibleBase = {
  id: 1,
  decision: 'PASS',
  riskLevel: 'LOW',
  isSelfEvidence: 0,
  reviewStatus: 'PENDING',
}
assert.equal(isAiAcceptEligible(eligibleBase), true)

assert.equal(
  isAiAcceptEligible({ ...eligibleBase, reviewStatus: 'AUTO_PASSED' }),
  false,
  '已自动通过的记录不能再走批量通过（重复提交会二次入库）',
)
assert.equal(isAiAcceptEligible({ ...eligibleBase, reviewStatus: 'ACCEPTED' }), false)
assert.equal(
  isAiAcceptEligible({ ...eligibleBase, decision: 'REVIEW' }),
  false,
  'AI 只给了 REVIEW 的结论必须人工逐条看',
)
assert.equal(isAiAcceptEligible({ ...eligibleBase, decision: 'BLOCK' }), false, 'AI 拦截不能被批量覆盖')
assert.equal(isAiAcceptEligible({ ...eligibleBase, riskLevel: 'HIGH' }), false, '高风险一律人工审核')
assert.equal(isAiAcceptEligible({ ...eligibleBase, riskLevel: 'MEDIUM' }), true, '非 HIGH 即可批量')
assert.equal(isAiAcceptEligible({ ...eligibleBase, isSelfEvidence: 1 }), false, '自证据不能批量通过')
assert.equal(isAiAcceptEligible({ ...eligibleBase, isSelfEvidence: undefined }), true, '字段缺失按非自证据')

assert.deepEqual(
  selectAiAcceptIds([
    eligibleBase,
    { ...eligibleBase, id: 2, riskLevel: 'HIGH' },
    { ...eligibleBase, id: 3 },
    { ...eligibleBase, id: 4, decision: 'BLOCK' },
  ]),
  [1, 3],
  '只挑出合格项，且保持原顺序',
)
assert.deepEqual(selectAiAcceptIds([]), [])
assert.equal(
  selectAiAcceptIds([eligibleBase, { ...eligibleBase, id: 3 }]).length,
  2,
  '按钮上的数字与实际提交条数同源',
)

// ===== 「要求」列文案 =====
assert.equal(abilityRequirementText({ minRequiredLevel: 3, weight: 12 }), 'L3 · 12 分')
assert.equal(abilityRequirementText({ minRequiredLevel: 4, weight: 7.5 }), 'L4 · 7.5 分')
assert.equal(abilityRequirementText({ minRequiredLevel: 2, weight: 33.333 }), 'L2 · 33.33 分')
assert.equal(abilityRequirementText({ weight: 10 }), '10 分')
assert.equal(abilityRequirementText({ minRequiredLevel: 5 }), 'L5')
assert.equal(abilityRequirementText({}), '—', '全缺失要给占位符，不能留空单元格')
assert.equal(abilityRequirementText({ minRequiredLevel: null, weight: null }), '—')
assert.equal(abilityRequirementText({ weight: 0 }), '0 分', '权重 0 是有效值，不能当成缺失')
assert.equal(abilityRequirementText({ minRequiredLevel: 1, weight: 0 }), 'L1 · 0 分')
assert.equal(abilityRequirementText({ weight: Number.NaN }), '—', 'NaN 不渲染成 "NaN 分"')

// ===== 文案 =====
assert.equal(
  describePersonCounts({ totalCount: 12, pendingCount: 3, blockCount: 1, passCount: 8 }),
  '能力 12 · 待审核 3 · 已拦截 1 · 已通过 8',
)
assert.equal(
  describeInspectionPost({ abilityCount: 20, riskyCount: 4 }),
  '4 项风险 / 20 项能力',
)
assert.equal(describeInspectionPost({}), '0 项风险 / 0 项能力', '统计缺失时不能渲染 undefined')

// ===== 巡检能力卡片（2026-09-04 由表格改卡片）=====
assert.equal(inspectionAbilityName({ abilityName: 'Python' }), 'Python')
assert.equal(inspectionAbilityName({ abilityName: '  ' }), '(空)', '空白名要显式标注，不能渲染成空白卡片')
assert.equal(inspectionAbilityName({ abilityName: null }), '(空)')
assert.equal(inspectionAbilityName({}), '(空)')

assert.equal(inspectionAbilitySourceLabel({ aiSource: true }), 'AI生成')
// 后端该字段出现过 boolean 与 tinyint 两种形态，写死 === 1 会把 true 误判成「人工」
assert.equal(inspectionAbilitySourceLabel({ aiSource: 1 }), 'AI生成')
assert.equal(inspectionAbilitySourceLabel({ aiSource: false }), '人工')
assert.equal(inspectionAbilitySourceLabel({}), '人工', '字段缺失按人工，不得渲染 undefined')

assert.equal(inspectionAbilityEvidence({ evidenceText: '工单 #12' }), '工单 #12')
assert.equal(
  inspectionAbilityEvidence({ evidenceText: '   ' }),
  '未提供证据',
  '证据缺失要给占位：留空会被读成「这项能力没有依据」',
)
assert.equal(inspectionAbilityEvidence({}), '未提供证据')

// ===== 布局回归：这次遮挡问题的根因必须不再出现 =====
assert.equal(source.includes('min-width: 1480px'), false, '内层子表 1480px 是双层滚动的根因')
assert.equal(source.includes('min-width: 760px'), false, '外层强推 760px 会让真实滚动落在祖先容器上')
assert.equal(source.includes('.harness-table-body { padding: 0; overflow-x: auto; }'), false)
assert.equal(source.includes('harness-subtable'), false, '内嵌子表已被抽屉卡片列表取代')
assert.equal(source.includes('harness-action-cluster'), false, 'min-width 260px 的按钮簇已被卡片操作栏取代')
assert.equal(source.includes('type="expand"'), false, '展开行已移除，明细走抽屉')
// 这两个外层列表（岗位巡检 / 人员评估）仍是表格，操作列必须固定右侧；
// 巡检的「能力明细」已在 2026-09-04 由表格改为卡片列表（见下方断言）。
assert.equal(source.includes('fixed="right"'), true, '外层列表的操作列必须固定在右侧才不会被横向滚动藏掉')
assert.equal(source.includes('grid-template-columns: repeat(auto-fit, minmax(120px, 1fr))'), true)
assert.equal(source.includes('repeat(7, 1fr)'), false, '固定 7 列会把 5 个统计卡挤变形')
assert.equal(source.includes('.hp-card__actions'), true, '卡片操作栏必须在，且允许换行')
assert.equal(source.includes('harness-person-drawer'), true)

// ===== 巡检明细抽屉：7 列表格 → 卡片列表 =====
// 最小宽度合计 942px（170+126+64+76+190+200+116）超过抽屉可用约 880px，
// 固定右列会压在证据列上 —— 这是「查看能力信息被遮挡」的根因。
assert.equal(source.includes('class="inspect-risk-tags"'), false, '风险标签不再是表格单元格')
assert.equal(source.includes('inspect-requirement'), false, '「要求」不再是 126px 的窄列')
assert.equal(
  source.includes('inspectionAbilityName(row)')
  && source.includes('inspectionAbilitySourceLabel(row)')
  && source.includes('inspectionAbilityEvidence(row)'),
  true,
  '卡片必须走 harness-review 的纯函数，规则不能散落在模板里',
)
assert.equal(
  source.includes('min(860px, 96vw)'),
  true,
  '抽屉宽度改为 860px 后仍小于原表格 942px 的最小宽度 —— 这正是不能保留表格的原因',
)

console.log('harness review layout & rules tests passed')
