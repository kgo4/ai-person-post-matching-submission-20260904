/**
 * 员工侧「我的学习路径」纯逻辑回归测试。
 *
 * 重点锁死四件事（每条都对应一个真实会误导员工的失败方式）：
 * 1. 目标岗位的选择顺序：**已有计划的排第一**（否则员工学到一半再进来会被重置）；
 * 2. 差距行必须由步骤派生且差距大的在前（两栏不同源会出现自相矛盾的展示）；
 * 3. 「随时可申请」的前提下，**四种学习状态的提示语必须两两不同** ——
 *    统一写「可提交」会让员工误以为附带的是完整证据；
 * 4. 学习记录摘要里的分数与资源数必须钳制/取整，不能出现「已学 5 / 3 个」。
 */
import assert from 'node:assert/strict'
import {
  buildGapRows,
  buildTargetCandidates,
  emptyPlanHint,
  emptyTargetHint,
  locateStepIndex,
  pickDefaultTarget,
  resolveImprovementEligibility,
  summarizeLearningRecord,
} from './my-learning-path.ts'

/* ---------------- 目标岗位候选 ---------------- */

const candidates = buildTargetCandidates([
  { matchingRecordId: 10, postId: 1, postName: 'Java 开发工程师', matchScore: 62.5, createdTime: '2026-09-01T10:00:00' },
  { matchingRecordId: 12, postId: 2, postName: '  ', matchScore: 80, createdTime: '2026-09-04T10:00:00' },
  { matchingRecordId: 11, postId: 3, postName: '前端工程师', matchScore: 75, createdTime: '2026-09-04T10:00:00' },
  { matchingRecordId: 12, postId: 2, postName: '重复项', matchScore: 80, createdTime: '2026-09-04T10:00:00' },
  null,
])
assert.equal(candidates.length, 3, '重复的 matchingRecordId 必须去重，脏元素必须跳过')
assert.deepEqual(candidates.map(c => c.matchingRecordId), [12, 11, 10], '新的在前（按 id 倒序）')
// 岗位名缺失不能渲染成空行，也不能被丢掉（丢了员工会以为记录不存在）
assert.equal(candidates[0].postName, '未命名岗位')
assert.equal(candidates[2].postName, 'Java 开发工程师')
assert.equal(buildTargetCandidates(null).length, 0)
assert.equal(buildTargetCandidates([]).length, 0)
assert.equal(buildTargetCandidates([{ matchingRecordId: null }]).length, 0)

/* ---------------- 默认目标：已有计划的优先 ---------------- */

// 有计划的排第一 —— 员工上次学到一半，再进来应当接着那条继续
assert.equal(pickDefaultTarget(candidates, [10]), 10)
// 次优先：分数最高
assert.equal(pickDefaultTarget(candidates, []), 12)
assert.equal(pickDefaultTarget(candidates, null), 12)
// 多条都有计划时，新的在前
assert.equal(pickDefaultTarget(candidates, [10, 12]), 12)
assert.equal(pickDefaultTarget([], [1]), null)
assert.equal(pickDefaultTarget(null, null), null)
// 分数缺失时不能因为 null 比较而出错，回落到第一条
assert.equal(
  pickDefaultTarget(buildTargetCandidates([{ matchingRecordId: 5, postName: 'A' }]), null),
  5,
)

/* ---------------- 差距行：由步骤派生 + 差距大的在前 ---------------- */

const steps = [
  { id: 1, abilityName: 'Spring 事务管理', currentLevel: 3, targetLevel: 4, priority: 'MEDIUM', gapType: 'LEVEL_GAP', status: 'PENDING' },
  { id: 2, abilityName: 'Java 并发编程', currentLevel: 2, targetLevel: 4, priority: 'HIGH', gapType: 'LEVEL_GAP', status: 'IN_PROGRESS' },
  { id: 3, abilityName: '分布式缓存', currentLevel: 0, targetLevel: 3, priority: 'HIGH', gapType: 'MISSING', status: 'PENDING' },
  { id: 4, abilityName: '', currentLevel: null, targetLevel: null, priority: 'UNKNOWN', gapType: null, status: null },
  null,
]

const gaps = buildGapRows(steps)
assert.equal(gaps.length, 4, '脏元素跳过，其余全部保留')
// 差距级数降序：分布式缓存差 3、Java 并发差 2、Spring 差 1
assert.deepEqual(gaps.map(g => g.stepId), [3, 2, 1, 4])
assert.equal(gaps[0].gapLevels, 3)
assert.equal(gaps[1].gapLevels, 2)
// 未知 priority 按最低处理，不会插到前面
assert.equal(gaps[3].priority, 'UNKNOWN')
// 等级缺失时 gapLevels 为 null，不能算成 0（0 会被读成「没有差距」）
assert.equal(gaps[3].gapLevels, null)
assert.equal(gaps[3].abilityName, '未命名能力')

// 同级差距时按优先级：都差 2 级时 HIGH 在前
const sameGap = buildGapRows([
  { id: 1, abilityName: 'A', currentLevel: 1, targetLevel: 3, priority: 'LOW' },
  { id: 2, abilityName: 'B', currentLevel: 1, targetLevel: 3, priority: 'HIGH' },
])
assert.deepEqual(sameGap.map(g => g.stepId), [2, 1])

// 目标低于当前时，差距记 0 而不是负数（负数会在界面上显示成「-1 级」）
const negative = buildGapRows([{ id: 9, abilityName: 'X', currentLevel: 4, targetLevel: 3 }])
assert.equal(negative[0].gapLevels, 0)

assert.deepEqual(buildGapRows(null), [])
assert.deepEqual(buildGapRows([]), [])

/* ---------------- 点差距 → 定位步骤 ---------------- */

assert.equal(locateStepIndex(steps, 3), 2)
assert.equal(locateStepIndex(steps, 1), 0)
// 找不到必须回 -1，调用方据此提示，而不是静默不动
assert.equal(locateStepIndex(steps, 999), -1)
assert.equal(locateStepIndex(steps, null), -1)
assert.equal(locateStepIndex(null, 1), -1)

/* ---------------- 申请资格：随时可申请 + 如实告知 ---------------- */

const eligibleComplete = resolveImprovementEligibility({
  stepCompleted: true, assessmentStatus: 'PASSED', assessmentScore: 88,
})
const eligibleNotPassed = resolveImprovementEligibility({
  stepCompleted: true, assessmentStatus: 'NOT_PASSED', assessmentScore: 45,
})
const eligibleStepUndone = resolveImprovementEligibility({ stepCompleted: false, assessmentStatus: null })
const eligibleBothBad = resolveImprovementEligibility({
  stepCompleted: false, assessmentStatus: 'NOT_PASSED',
})

// 需求：随时可以申请 —— allowed 必须恒为 true
for (const item of [eligibleComplete, eligibleNotPassed, eligibleStepUndone, eligibleBothBad]) {
  assert.equal(item.allowed, true, '「随时可以申请」是不变式')
}
assert.equal(eligibleComplete.complete, true)
assert.equal(eligibleNotPassed.complete, false)

// 四种提示语必须两两不同，且都要点出 HR 会看到什么
const hints = [eligibleComplete.hint, eligibleNotPassed.hint, eligibleStepUndone.hint, eligibleBothBad.hint]
assert.equal(new Set(hints).size, 4, '四种学习状态的提示语必须各自不同')
assert.ok(eligibleNotPassed.hint.includes('评估题未通过'))
assert.ok(eligibleStepUndone.hint.includes('尚未学完'))
assert.ok(eligibleBothBad.hint.includes('尚未学完') && eligibleBothBad.hint.includes('评估题未通过'),
  '两个条件都不满足时必须同时点出，只说一半会让 HR 看到意料之外的记录')
// 空输入不能崩，也不能说成「记录完整」
const eligibleEmpty = resolveImprovementEligibility(null)
assert.equal(eligibleEmpty.allowed, true)
assert.equal(eligibleEmpty.complete, false)

/* ---------------- 学习记录摘要：两边共用同一份实现 ---------------- */

const recordComplete = summarizeLearningRecord({
  stepCompleted: true, assessmentStatus: 'PASSED', assessmentScore: 87.6,
  completedResourceCount: 3, resourceCount: 4,
})
assert.equal(recordComplete.text, '学习记录完整')
assert.ok(recordComplete.items.some(i => i.includes('已通过') && i.includes('88 分')), '分数必须取整，不能出现 87.6 分')
assert.ok(recordComplete.items.some(i => i.includes('已学 3 / 4 个')))

const recordPartial = summarizeLearningRecord({
  stepCompleted: true, assessmentStatus: 'NOT_PASSED', assessmentScore: 40,
  completedResourceCount: 5, resourceCount: 3,
})
assert.equal(recordPartial.text, '学习记录不完整（HR 会看到明细）')
// 已学数不能超过总数（「已学 5 / 3 个」是明显的坏数据展示）
assert.ok(recordPartial.items.some(i => i.includes('已学 3 / 3 个')), '已学数必须钳制到资源总数以内')
assert.ok(recordPartial.items.some(i => i.includes('未通过')))

const recordEmpty = summarizeLearningRecord(null)
assert.ok(recordEmpty.items.some(i => i.includes('未学完')))
assert.ok(recordEmpty.items.some(i => i.includes('未作答')))
// 没有资源数时不渲染资源行，避免出现「已学 0 / 0 个」这种噪声
assert.equal(recordEmpty.items.length, 2)

assert.ok(summarizeLearningRecord({ assessmentStatus: 'PENDING' }).items.some(i => i.includes('待判分')))

/* ---------------- 空态文案：必须说清为什么 ---------------- */

assert.ok(emptyTargetHint(false).includes('联系 HR'))
assert.ok(emptyTargetHint(true).includes('未发布'))
assert.notEqual(emptyTargetHint(false), emptyTargetHint(true))

assert.ok(emptyPlanHint(false).includes('生成学习计划'))
assert.ok(emptyPlanHint(true).includes('重新生成'))
assert.notEqual(emptyPlanHint(false), emptyPlanHint(true))

console.log('my learning path logic tests passed')
