import assert from 'node:assert/strict'
import {
  deriveReviewNote,
  evaluationStatusText,
  overallScoreText,
  reportStatusMeta,
  summarizeEvaluationRecords,
} from './assessment-records.ts'

/* ---------------- 流程状态文案 ---------------- */

assert.equal(evaluationStatusText('COMPLETED'), '已完成')
assert.equal(evaluationStatusText('REVIEW_REQUIRED'), '待人工复核')
assert.equal(evaluationStatusText(null), '进行中')
assert.equal(evaluationStatusText(undefined), '进行中')
// 未知状态码回显原码，不伪装成「未知」
assert.equal(evaluationStatusText('SOME_NEW_STATUS'), 'SOME_NEW_STATUS')

/* ---------------- 报告状态 ---------------- */

assert.deepEqual(reportStatusMeta('READY'), { text: '已生成', tagType: 'success', viewable: true })
assert.deepEqual(reportStatusMeta('FAILED'), { text: '生成失败', tagType: 'danger', viewable: false })
assert.equal(reportStatusMeta('READY').viewable, true)
assert.equal(reportStatusMeta('FAILED').viewable, false, '生成失败的报告没有内容可看，按钮必须不可点')

// 【2026-09-04 核心回归】「审核中」只在流程确实停在 HR 环节时出现。
// 用户在「我的评估记录」里看到「刚上传完简历就显示 HR 在审核」就是这个坑：
// 报告未生成 ≠ HR 在审核，简历/测试/面试三步 HR 根本没参与。
assert.deepEqual(
  reportStatusMeta(null, 'AGGREGATE_HARNESS_RUNNING'),
  { text: '审核中', tagType: 'info', viewable: false },
  '聚合审核阶段才是 HR 真的在审',
)
assert.equal(reportStatusMeta(null, 'LEVEL_CONFIRMING').text, '审核中')
assert.equal(reportStatusMeta(null, 'REVIEW_REQUIRED').text, '审核中')
assert.equal(reportStatusMeta(null, 'RESUME_REQUIRED').text, '评估中', '还没上传简历，HR 不可能在审')
assert.equal(reportStatusMeta(null, 'RESUME_PARSING').text, '评估中')
assert.equal(reportStatusMeta(null, 'TEST_IN_PROGRESS').text, '评估中')
assert.equal(reportStatusMeta(null, 'INTERVIEW_ANALYZING').text, '评估中')
// 拿不到流程状态时不许凭空捏造审核进度 → 落「评估中」
assert.equal(reportStatusMeta(null).text, '评估中')
assert.equal(reportStatusMeta(undefined, undefined).text, '评估中')
assert.equal(reportStatusMeta(null, 'RESUME_PARSING').viewable, false, '未生成的报告一律不可点')

// 报告行已落库但内容仍在生成（后端 STATUS_GENERATING）：不能显示「已生成」，
// 否则员工点开只看到一句「内容仍在生成中」——一个打着成功标记却打不开的按钮。
assert.deepEqual(
  reportStatusMeta('GENERATING', 'COMPLETED'),
  { text: '生成中', tagType: 'warning', viewable: false },
  '内容未生成完的报告不能以「已生成」示人',
)
assert.equal(reportStatusMeta('GENERATING', 'AGGREGATE_HARNESS_RUNNING').viewable, false)

/* ---------------- 记录汇总 ---------------- */

const empty = summarizeEvaluationRecords([])
assert.deepEqual(empty, { total: 0, completed: 0, reportReady: 0, inProgress: 0 })

const summary = summarizeEvaluationRecords([
  { workflowId: 3, workflowStatus: 'COMPLETED', reportStatus: 'READY' },
  { workflowId: 2, workflowStatus: 'COMPLETED', reportStatus: 'FAILED' },
  { workflowId: 1, workflowStatus: 'INTERVIEW_IN_PROGRESS', reportStatus: null },
])
assert.equal(summary.total, 3)
assert.equal(summary.completed, 2)
assert.equal(summary.reportReady, 1)
assert.equal(summary.inProgress, 1, '未完成的评估次数 = 总数 - 已完成')

/* ---------------- 能力审核说明（历史 bug 回归） ---------------- */

// 未加载完不下结论
const loading = deriveReviewNote({
  recordsLoaded: false,
  hasRecords: false,
  assessmentPending: true,
  reportReady: false,
})
assert.equal(loading.tone, 'loading')
assert.equal(loading.showProfileButton, false)

// 核心回归：从未评估过的员工 —— 后端此时会返回 assessmentPending=true
//（闸门把「从未评估」也算作未定稿），页面绝不能因此显示「能力审核已完成」，
// 也不能假称「HR 正在审核你的能力项」。
const neverAssessed = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: false,
  assessmentPending: true,
  reportReady: false,
})
assert.equal(neverAssessed.tone, 'empty')
assert.equal(neverAssessed.title, '你还没有进行过能力评估')
assert.ok(!neverAssessed.title.includes('已完成'), '未评估时不得出现「已完成」字样')
assert.ok(!neverAssessed.desc.includes('评估报告已生成'), '未评估时不得声称报告已生成')
assert.equal(neverAssessed.showProfileButton, false, '空画像页不该给入口')

// 记录加载失败 ≠ 没有记录：不能对着一堆真实存在的记录说「你还没有进行过能力评估」
const loadFailed = deriveReviewNote({
  recordsLoaded: true,
  recordsLoadFailed: true,
  hasRecords: false,
  assessmentPending: false,
  reportReady: false,
})
assert.equal(loadFailed.tone, 'unavailable')
assert.ok(!loadFailed.title.includes('还没有进行过'), '拉取失败不得降级成空态结论')
assert.equal(loadFailed.showProfileButton, false)

// 有记录 + 闸门未放行 + 流程已进 HR 环节 → 审核中
const pending = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: true,
  reportReady: false,
  latestWorkflowStatus: 'AGGREGATE_HARNESS_RUNNING',
})
assert.equal(pending.tone, 'pending')
assert.equal(pending.title, 'HR 正在审核你的能力项')
assert.equal(pending.showProfileButton, true)

// 【2026-09-04 核心回归】闸门未放行，但流程还停在简历阶段 —— HR 尚未参与。
// 用户反馈：能力评估刚上传完简历、连面试都还没走，页面就显示「HR 在审核中」。
const earlyStage = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: true,
  reportReady: false,
  latestWorkflowStatus: 'RESUME_PARSING',
})
assert.equal(earlyStage.tone, 'in-progress')
assert.equal(earlyStage.title, '本次评估进行中')
assert.ok(!earlyStage.title.includes('HR'), 'HR 没参与时标题不得提到 HR')
assert.ok(!earlyStage.desc.includes('正在审核'), '简历阶段不得声称 HR 正在审核')
assert.equal(earlyStage.showProfileButton, false, '结果没定稿，别把员工引到空画像页')

// 测试 / 面试阶段同理，一律「评估中」而不是「审核中」
for (const status of ['TEST_IN_PROGRESS', 'INTERVIEW_ANALYZING']) {
  const note = deriveReviewNote({
    recordsLoaded: true,
    hasRecords: true,
    assessmentPending: true,
    reportReady: false,
    latestWorkflowStatus: status,
  })
  assert.equal(note.tone, 'in-progress', `${status} 阶段不该说 HR 在审核`)
}

// 拿不到流程状态时宁可说「进行中」，也不凭空捏造一个审核进度
const unknownStage = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: true,
  reportReady: false,
})
assert.equal(unknownStage.tone, 'in-progress')

// 审核放行 + 报告已生成 → 已完成
const done = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: false,
  reportReady: true,
})
assert.equal(done.tone, 'done')
assert.equal(done.title, '能力审核已完成')
assert.ok(done.desc.includes('可在「我的能力画像」查看'))
assert.equal(done.showProfileButton, true)

// 有记录但流程未走完 → 进行中（不谎报已完成）
const inProgress = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: false,
  reportReady: false,
})
assert.equal(inProgress.tone, 'in-progress')
assert.ok(!inProgress.title.includes('已完成'))
assert.equal(inProgress.showProfileButton, false)

// pending 优先于 reportReady：后端闸门是权威口径，不能被前端推断的「有报告」覆盖
const pendingWins = deriveReviewNote({
  recordsLoaded: true,
  hasRecords: true,
  assessmentPending: true,
  reportReady: true,
  latestWorkflowStatus: 'LEVEL_CONFIRMING',
})
assert.equal(pendingWins.tone, 'pending')
assert.equal(pendingWins.title, 'HR 正在审核你的能力项')

/* ---------------- 综合评分文案（HR 未审完不得露出分数） ---------------- */

// 已定稿：正常显示分数
assert.equal(overallScoreText('READY', 82), '82')
assert.equal(overallScoreText('READY', 0), '0', '0 分是真实分数，必须原样显示')

// 未定稿：后端不下发分数（null），此时必须说明原因而不是 `--`
assert.equal(
  overallScoreText(null, null, 'AGGREGATE_HARNESS_RUNNING'),
  '审核中',
  'HR 审核期间说明「审核中」，不能渲染成 `--`（会被读成「考了 0 分」）',
)
assert.equal(
  overallScoreText(null, null, 'RESUME_PARSING'),
  '评估中',
  '流程还在简历阶段时说「审核中」是假信息 —— 与报告状态列同一口径',
)
assert.equal(overallScoreText(null, null), '评估中', '拿不到流程状态时保守落「评估中」，不捏造审核进度')
assert.equal(
  overallScoreText(null, 82, 'LEVEL_CONFIRMING'),
  '审核中',
  '防御：即使响应里意外带了分数，未定稿状态也不能把它露出来',
)
assert.equal(
  overallScoreText(null, 82, 'INTERVIEW_ANALYZING'),
  '评估中',
  '同上，面试阶段也不得露出分数',
)

// 生成失败是终态失败，不能拿「审核中」搪塞，也不该编造分数
assert.equal(overallScoreText('FAILED', null), '--')
assert.equal(overallScoreText('FAILED', 82), '--')

// READY 但分数缺失：这是真的没有分，用 `--` 而不是「审核中」
assert.equal(overallScoreText('READY', null), '--')
assert.equal(overallScoreText('READY', undefined), '--')

// 内容仍在生成：明确说「生成中」，不借用「审核中」
assert.equal(overallScoreText('GENERATING', null, 'COMPLETED'), '生成中')

console.log('assessment records logic tests passed')
