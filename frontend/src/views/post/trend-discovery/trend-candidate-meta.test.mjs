/**
 * 趋势候选展示口径单测（node 直跑）。
 *
 * 重点覆盖三类「静默误导」：
 *   1. 相似度取不到时显示成 0（会被读成「完全不相似」）→ 必须是「未比对」；
 *   2. 治理判定出现未知取值时给绿灯 → 必须落回「未判定」并禁止批量；
 *   3. 变更类型出现「删除」措辞 → 材料没提到某项能力不等于岗位不再需要它。
 */
import assert from 'node:assert/strict'
import {
  batchConfirmBlockReason,
  candidateTypeLabel,
  changeTypeLabel,
  changeTypeTagType,
  confirmStatusBadge,
  coverageBadge,
  describeBatchResult,
  describeLandResult,
  emphasisText,
  harnessBadge,
  isTaskAwaitingConfirm,
  isTaskRunning,
  selectableForBatch,
  similarityText,
  weightTotalHint,
} from './trend-candidate-meta.ts'

/* ============================ 相似度 / 强调度 ============================ */

assert.equal(similarityText(0.41), '0.41')
assert.equal(similarityText(0), '0.00')
assert.equal(similarityText(null), '未比对', 'null 不能显示成 0')
assert.equal(similarityText(undefined), '未比对')

assert.equal(emphasisText(8.53), '8.5')
assert.equal(emphasisText(null), '—')
assert.equal(emphasisText(undefined), '—')

/* ============================ 治理判定徽标 ============================ */

assert.deepEqual(harnessBadge('PASS'), { label: '可批量确认', type: 'success' })
assert.deepEqual(harnessBadge('REVIEW'), { label: '待复核', type: 'warning' })
assert.deepEqual(harnessBadge('BLOCK'), { label: '已拦截', type: 'danger' })
// 后端新增枚举值时不能默认绿灯
assert.deepEqual(harnessBadge('SOMETHING_NEW'), { label: '未判定', type: 'info' })
assert.deepEqual(harnessBadge(null), { label: '未判定', type: 'info' })

/* ============================ 确认状态 / 候选类型 ============================ */

assert.deepEqual(confirmStatusBadge('PENDING'), { label: '待确认', type: 'primary' })
assert.deepEqual(confirmStatusBadge('APPROVED'), { label: '已通过', type: 'success' })
assert.deepEqual(confirmStatusBadge('REJECTED'), { label: '已驳回', type: 'info' })
assert.deepEqual(confirmStatusBadge('WHATEVER'), { label: '未知', type: 'info' })

assert.equal(candidateTypeLabel('NEW_POST'), '新岗位')
assert.equal(candidateTypeLabel('ABILITY_CHANGE'), '能力变更')
assert.equal(candidateTypeLabel(null), '候选')

/* ============================ 变更类型：绝不出现「删除」 ============================ */

const changeTypes = ['ADD', 'UPGRADE_LEVEL', 'DOWNGRADE_LEVEL', 'UPDATE_WEIGHT', 'UNCHANGED', null]
for (const changeType of changeTypes) {
  const label = changeTypeLabel(changeType)
  assert.equal(label.includes('删除'), false, `${changeType} 的文案不得出现「删除」`)
  assert.equal(label.includes('移除'), false, `${changeType} 的文案不得出现「移除」`)
}
assert.equal(changeTypeLabel('ADD'), '新增')
assert.equal(changeTypeLabel('UPGRADE_LEVEL'), '要求提高')
assert.equal(changeTypeLabel('DOWNGRADE_LEVEL'), '要求下调')
assert.equal(changeTypeLabel('UPDATE_WEIGHT'), '权重调整')
assert.equal(changeTypeLabel('UNCHANGED'), '无变化')
assert.equal(changeTypeLabel(null), '—')
// 未知变更类型不得被渲染成「无变化」（会让人以为没动过）
assert.equal(changeTypeLabel('REMOVED'), '—')

assert.equal(changeTypeTagType('ADD'), 'success')
assert.equal(changeTypeTagType('UPGRADE_LEVEL'), 'warning')
assert.equal(changeTypeTagType('UNCHANGED'), 'info')

/* ============================ 跨来源印证 ============================ */

assert.equal(coverageBadge(3), '3 类材料印证')
assert.equal(coverageBadge(2), '2 类材料印证')
assert.equal(coverageBadge(1), null, '单一类别不构成印证')
assert.equal(coverageBadge(0), null)
assert.equal(coverageBadge(null), null)

/* ============================ 批量确认前置条件 ============================ */

const candidate = overrides => ({
  id: 1,
  confirmStatus: 'PENDING',
  harnessDecision: 'PASS',
  ...overrides,
})

assert.equal(batchConfirmBlockReason(candidate({})), null)
assert.equal(batchConfirmBlockReason(candidate({ harnessDecision: 'REVIEW' })), '治理判定为待复核，需逐条确认')
assert.equal(batchConfirmBlockReason(candidate({ harnessDecision: 'BLOCK' })), '治理判定为已拦截，不允许落地')
assert.equal(batchConfirmBlockReason(candidate({ harnessDecision: null })), '缺少治理判定结果，需人工确认')
assert.equal(batchConfirmBlockReason(candidate({ harnessDecision: 'NEW_VALUE' })), '缺少治理判定结果，需人工确认')
assert.equal(batchConfirmBlockReason(candidate({ confirmStatus: 'APPROVED' })), '已处理')
assert.equal(batchConfirmBlockReason(candidate({ confirmStatus: 'REJECTED' })), '已处理')

assert.deepEqual(
  selectableForBatch([
    { id: 1, confirmStatus: 'PENDING', harnessDecision: 'PASS' },
    { id: 2, confirmStatus: 'PENDING', harnessDecision: 'REVIEW' },
    { id: 3, confirmStatus: 'PENDING', harnessDecision: 'PASS' },
    { id: 4, confirmStatus: 'APPROVED', harnessDecision: 'PASS' },
    { id: 5, confirmStatus: 'PENDING', harnessDecision: 'BLOCK' },
  ]),
  [1, 3],
  '只有 PASS + PENDING 才可选',
)
assert.deepEqual(selectableForBatch([]), [])

/* ============================ 权重合计提示 ============================ */

assert.equal(weightTotalHint([{ suggestedWeight: 40 }, { suggestedWeight: 30 }, { suggestedWeight: 30 }]), null)
// 95 与 105 是闭区间边界（与后端 batchConfig 校验一致），不提示
assert.equal(weightTotalHint([{ suggestedWeight: 94 }, { suggestedWeight: 1 }]), null)
assert.equal(weightTotalHint([{ suggestedWeight: 94 }]), '当前权重合计 94.0，落库时会按比例自动归一为 100')
assert.equal(weightTotalHint([{ suggestedWeight: 90 }]), '当前权重合计 90.0，落库时会按比例自动归一为 100')
assert.equal(weightTotalHint([{ suggestedWeight: 106 }]), '当前权重合计 106.0，落库时会按比例自动归一为 100')
assert.equal(weightTotalHint([{ suggestedWeight: 105 }]), null)
assert.equal(weightTotalHint([]), null)
// 空名字段不能算成 NaN
assert.equal(weightTotalHint([{ suggestedWeight: null }, { suggestedWeight: 100 }]), null)

/* ============================ 结果文案 ============================ */

assert.equal(describeBatchResult({ landed: [{}, {}], skipped: [{}] }), '已落地 2 项，跳过 1 项')
assert.equal(describeBatchResult({ landed: [{}], skipped: [] }), '已落地 1 项')
assert.equal(describeBatchResult({ landed: [], skipped: [{}] }), '跳过 1 项')
assert.equal(describeBatchResult({ landed: [], skipped: [] }), '没有可落地的候选')
assert.equal(describeBatchResult({}), '没有可落地的候选')

assert.equal(
  describeLandResult({ postName: 'AIGC 内容审核师', createdNewPost: true, abilityCount: 6 }),
  '已创建岗位「AIGC 内容审核师」，写入 6 项能力',
)
assert.equal(
  describeLandResult({ postName: '数据标注员', createdNewPost: false, abilityCount: 4 }),
  '已更新岗位能力「数据标注员」，写入 4 项能力',
)

/* ============================ 任务状态 ============================ */

assert.equal(isTaskRunning('PENDING'), true)
assert.equal(isTaskRunning('RUNNING'), true)
assert.equal(isTaskRunning('WAIT_CONFIRM'), false)
assert.equal(isTaskRunning('COMPLETED'), false)
assert.equal(isTaskRunning(null), false)

assert.equal(isTaskAwaitingConfirm('WAIT_CONFIRM'), true)
assert.equal(isTaskAwaitingConfirm('RUNNING'), false)
assert.equal(isTaskAwaitingConfirm(undefined), false)

console.log('trend candidate meta tests passed')
