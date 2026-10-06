import assert from 'node:assert/strict'
import {
  assessmentStageIndexOf,
  assessmentStagePercent,
  buildEmployeeWorkbench,
  employeePaths,
  isWorkflowBlocked,
} from './employee-workbench-logic.ts'

/* ------------------------------ 阶段映射 ------------------------------ */

assert.equal(assessmentStageIndexOf('RESUME_REQUIRED'), 0)
assert.equal(assessmentStageIndexOf('TEST_IN_PROGRESS'), 1)
assert.equal(assessmentStageIndexOf('INTERVIEW_ANALYZING'), 2)
assert.equal(assessmentStageIndexOf('AGGREGATE_HARNESS_RUNNING'), 3)
assert.equal(assessmentStageIndexOf('COMPLETED'), 4)
assert.equal(assessmentStageIndexOf('SOMETHING_UNKNOWN'), null, '未知状态必须返回 null，不能猜阶段')

assert.equal(assessmentStagePercent('RESUME_REQUIRED'), 20)
assert.equal(assessmentStagePercent('COMPLETED'), 100)
assert.equal(assessmentStagePercent(undefined), 0)

assert.equal(isWorkflowBlocked('FAILED'), true)
assert.equal(isWorkflowBlocked('RECOVERY_REQUIRED'), true)
assert.equal(isWorkflowBlocked('REVIEW_REQUIRED'), true)
assert.equal(isWorkflowBlocked('TEST_IN_PROGRESS'), false)

const paths = employeePaths(42)
assert.equal(paths.assessment, '/employee/ability-profile/assessment?empId=42')
assert.equal(paths.result, '/matching/my-result', '员工匹配结果走只读页，人员范围由服务端按登录身份收口')
assert.equal(employeePaths(null).assessment, '/employee/ability-profile/assessment')

/* ------------------------------ 未绑定档案 ------------------------------ */

const noIdentity = buildEmployeeWorkbench({
  hasEmployeeIdentity: false,
  empId: null,
  abilityCount: null,
  abilityOverallScore: null,
  confirmedCount: null,
  provisionalCount: null,
  workflow: null,
  reports: [],
  matchingTotal: null,
  matchingRecent: [],
  plans: [],
})
assert.equal(noIdentity.todos.length, 1)
assert.equal(noIdentity.todos[0].urgent, true)
assert.equal(noIdentity.todos[0].path, '', '未绑定档案的待办不应指向无法使用的页面')
assert.equal(noIdentity.primaryActionLabel, '', '未绑定档案时不展示主行动按钮')
assert.equal(noIdentity.stats.length, 4, '待确立能力指标卡已移除，员工工作台保留 4 张指标卡')
for (const stat of noIdentity.stats) {
  assert.ok(stat.value === '--' || stat.value === '0', `${stat.label} 缺失数据时必须是 --，实际 ${stat.value}`)
}

/* ------------------------------ 有数据的正常态 ------------------------------ */

const normal = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 12,
  abilityOverallScore: 82.4,
  confirmedCount: 12,
  provisionalCount: 3,
  workflow: { status: 'TEST_IN_PROGRESS', displayStatus: '阶段 2：测试进行中', nextStepHint: '完成 AI 测试' },
  reports: [
    { workflowId: 1, overallScore: 61, postMatchScore: 58, startedAt: '2026-01-05 10:00:00', reportStatus: 'READY' },
    { workflowId: 2, overallScore: 78, postMatchScore: 74, startedAt: '2026-03-08 10:00:00', reportStatus: 'READY' },
  ],
  matchingTotal: 4,
  matchingRecent: [
    { aiMatchScore: 88.2, finalMatchScore: 85.5, createdTime: '2026-03-10 09:00:00', postName: '后端工程师' },
  ],
  plans: [{ planTitle: '后端能力提升', totalStepCount: 8, completedStepCount: 3 }],
})

// 【2026-09-04】「待确立能力」指标卡已移除：能力项只有 HR 审核全部完成后才对员工可见，
// 审核期的能力数量不再出现在员工工作台。
assert.equal(normal.stats.length, 4)
assert.equal(normal.stats[0].value, '12')
assert.equal(normal.stats[0].hint, '综合得分 82')
assert.equal(normal.stats[1].value, '2', '评估报告数量取自报告列表')
assert.equal(normal.stats[2].value, '4', '匹配结果数量')
assert.equal(normal.stats[3].value, '85.5', '最高匹配分优先取最终分')
assert.ok(!normal.stats.some(s => s.key === 'provisional'), '不应再出现待确立能力指标卡')

assert.equal(normal.trend.points.length, 2)
assert.equal(normal.trend.primaryName, '评估总分')
assert.equal(normal.trend.points[0].primary, 61, '趋势按时间升序，最早的在最左')
assert.equal(normal.trend.points[1].primary, 78)

const todoTitles = normal.todos.map(todo => todo.title)
assert.ok(todoTitles.includes('继续能力评估'), '进行中的工作流必须给出继续评估待办')
assert.ok(!todoTitles.some(t => t.includes('待确立')), '审核期能力不应出现在员工待办')
assert.ok(todoTitles.some(title => title.startsWith('继续学习')), '未完成的学习计划必须给出继续学习待办')
assert.equal(normal.todos[0].urgent, false, '正常进行中的流程不应被标记为紧急')
assert.equal(normal.primaryActionLabel, '+ 继续能力评估')

const assessmentProgress = normal.progresses.find(item => item.label === '能力评估流程')
assert.equal(assessmentProgress.value, 40, 'TEST 阶段 = 2/5')
const planProgress = normal.progresses.find(item => item.label === '学习计划完成度')
assert.equal(planProgress.value, 38)
assert.ok(!normal.progresses.some(item => item.label === '能力确立率'), '能力确立率依赖审核期数据，已移除')

assert.ok(normal.notices.some(notice => notice.title === '评估报告已生成'))
assert.ok(normal.notices.some(notice => notice.title.includes('新增匹配结果')))
assert.ok(normal.notices.every(notice => notice.time !== '--'), '通知必须带时间或状态文案')

/* ------------------------------ 流程失败 ------------------------------ */

const blocked = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 1,
  abilityOverallScore: 50,
  confirmedCount: 1,
  provisionalCount: 0,
  workflow: { status: 'RECOVERY_REQUIRED', displayStatus: '流程待恢复', failedReason: '简历解析超时' },
  reports: [{ workflowId: 1, overallScore: 50, reportStatus: 'FAILED', startedAt: '2026-02-01 10:00:00' }],
  matchingTotal: 0,
  matchingRecent: [],
  plans: [],
})
assert.equal(blocked.todos[0].urgent, true)
assert.equal(blocked.todos[0].title, '处理中断的评估流程')
assert.ok(blocked.notices.some(notice => notice.kind === 'warning' && notice.title === '评估流程需要处理'))
assert.ok(blocked.notices.some(notice => notice.title === '评估报告生成失败'))
assert.ok(!blocked.todos.some(todo => todo.title.includes('查看匹配结果')), '没有匹配结果时不应出现查看入口')

/* ------------------------------ 无报告时回退匹配趋势 ------------------------------ */

const fallback = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 0,
  abilityOverallScore: null,
  confirmedCount: 0,
  provisionalCount: 0,
  workflow: null,
  reports: [],
  matchingTotal: 2,
  // dashboard-summary 的 recent 是倒序（最新在前），与后端返回保持一致
  matchingRecent: [
    { aiMatchScore: 80, finalMatchScore: 76, createdTime: '2026-03-12 09:00:00' },
    { aiMatchScore: 70, finalMatchScore: 66, createdTime: '2026-03-10 09:00:00' },
  ],
  plans: [],
})
assert.equal(fallback.trend.primaryName, 'AI 匹配分')
assert.equal(fallback.trend.points.length, 2)
assert.equal(fallback.trend.points[0].label, '03-10', '匹配记录回退时按时间升序，最早的在最左')
assert.equal(fallback.trend.points[1].primary, 80)
assert.equal(fallback.primaryActionLabel, '+ 开始能力评估')
assert.ok(fallback.todos.some(todo => todo.title === '开始能力评估'))

/* ------------------------------ 待办必须是真实待办 ------------------------------ */
// 【2026-09-04】「查看匹配结果 / 查看能力差距诊断 / 生成学习路径」曾被打包塞进待办，
// 使得没有任何匹配结果、也没有学习计划的人也会看到"该去学习了"——待办面板因此像假的。
// 这三项本质是入口/建议，已全部移除（入口由 actions 承载），这里做回归锁定。

const noMatching = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 0,
  abilityOverallScore: null,
  confirmedCount: 0,
  provisionalCount: 0,
  workflow: { status: 'COMPLETED', displayStatus: '已完成' },
  reports: [],
  matchingTotal: 0,
  matchingRecent: [],
  plans: [],
})
assert.deepEqual(
  noMatching.todos.map(todo => todo.title),
  [],
  '评估已完成、无匹配结果、无学习计划时，不应有任何"假待办"',
)
assert.ok(noMatching.actions.some(action => action.label === '学习路径'), '学习路径应仍作为快捷入口提供')

const withMatching = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 3,
  abilityOverallScore: 70,
  confirmedCount: 3,
  provisionalCount: 0,
  workflow: { status: 'COMPLETED', displayStatus: '已完成' },
  reports: [],
  matchingTotal: 5,
  matchingRecent: [{ aiMatchScore: 80, finalMatchScore: 78 }],
  plans: [],
})
assert.deepEqual(
  withMatching.todos.map(todo => todo.title),
  [],
  '仅有匹配结果并不是待办：查看结果/生成学习路径都是入口，不是必须处理的事项',
)

// 未完成的学习计划才是真待办
const withPlan = buildEmployeeWorkbench({
  hasEmployeeIdentity: true,
  empId: 7,
  abilityCount: 3,
  abilityOverallScore: 70,
  confirmedCount: 3,
  provisionalCount: 0,
  workflow: { status: 'COMPLETED', displayStatus: '已完成' },
  reports: [],
  matchingTotal: 5,
  matchingRecent: [],
  plans: [{ planTitle: '后端能力提升', totalStepCount: 8, completedStepCount: 3 }],
})
assert.deepEqual(withPlan.todos.map(todo => todo.title), ['继续学习：后端能力提升'])

console.log('employee workbench logic tests passed')
