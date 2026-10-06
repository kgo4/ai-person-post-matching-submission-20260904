/**
 * 「综合评分只在 HR 审核全部完成后可见」的源码回归断言。
 *
 * 用 stripSfcComments（本项目约定：断言源码文本前必须先剥注释）剥掉说明文字，
 * 再断言**被禁止的旧写法**确实已经消失 —— 否则本次修复会被下一次顺手改回去。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { stripSfcComments } from '../../../utils/sfc-source.ts'

const currentDir = dirname(fileURLToPath(import.meta.url))
const read = (name) => stripSfcComments(readFileSync(join(currentDir, name), 'utf8'))

const assessment = read('assessment.vue')
const workbench = read('../../workbench/employee-workbench-logic.ts')
const liveInterview = read('live-interview.vue')

// 1) 「我的评估记录」的综合评分列必须走闸门函数，不能裸渲染分数
assert.equal(
  assessment.includes("row.overallScore ?? '--'"),
  false,
  '裸渲染 row.overallScore 会让 HR 未审核完的分数直接显示给员工',
)
assert.equal(
  assessment.includes('overallScoreText(row.reportStatus, row.overallScore, row.workflowStatus)'),
  true,
  '综合评分列必须经由 overallScoreText 判定是否已定稿，并且必须带上流程状态：'
    + '不带流程状态就无法区分「HR 审核中」与「流程还在推进」，会重现「刚上传简历就显示 HR 在审核」',
)
assert.equal(
  assessment.includes('reportStatusMeta(row.reportStatus, row.workflowStatus)'),
  true,
  '报告状态列同样必须带上流程状态（HR 审核状态只在聚合审核之后出现）',
)
assert.equal(
  assessment.includes('latestWorkflowStatus'),
  true,
  '「能力审核说明」卡片需按最近一次评估的流程状态选择文案',
)

// 2) 工作台趋势图不能把「后端未下发分数」画成 0 分
assert.equal(
  workbench.includes('Number(report.overallScore ?? 0)'),
  true,
  '已定稿的报告仍按原口径取值',
)
assert.equal(
  workbench.includes('report.overallScore != null || report.postMatchScore != null'),
  true,
  '必须先把无可画分数的报告过滤掉，否则 `?? 0` 会把「审核中」画成 0 分',
)
assert.equal(
  workbench.includes('const trend = scoredReports.length'),
  true,
  '趋势分支要基于「有分数的报告」而不是「有报告的记录数」',
)

// 3) AI 面试结束页不得把面试结果直接推给员工
assert.equal(
  liveInterview.includes("interviewResultEntry === 'HANDOFF'"),
  true,
  '面试完成页必须先判 interviewResultEntry；员工侧只能看到「等 HR 审核」的交接说明',
)
assert.equal(
  liveInterview.includes('label="面试综合得分"'),
  true,
  '该分数是**面试环节**的分（EmpVideoInterviewSession.overallScore），标签不能写成「综合得分」，'
    + '否则会被读成人员评估的综合分',
)

console.log('overall score gating tests passed')
