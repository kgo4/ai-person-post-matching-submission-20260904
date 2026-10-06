import assert from 'node:assert/strict'
import { SELF_MATCH_TARGET_TEXT, resolveApplyGate } from './gate.ts'

/**
 * 员工自助发起匹配的准入闸门回归。
 *
 * 背景（2026-09-04 用户口径）：员工必须等**本人全部能力项都被 HR 审核完成**后才能发起匹配，
 * 且匹配对象只能是员工本人。原先这段判断藏在「我的匹配结果」的弹窗里，
 * 不达标时只给一句 tooltip，员工会把「审核中」读成「没有权限 / 没有入口」。
 * 现在闸门抽成纯函数、入口常驻可见，因此三档原因与跳转必须被锁死。
 */

const ASSESSMENT_PATH = '/employee/ability-profile/assessment'

/* ① 未绑定人员档案：无法自救，因此不给跳转按钮 */
const noIdentity = resolveApplyGate({ empId: null, assessmentPending: false, reportReady: true })
assert.equal(noIdentity.canApply, false, '未绑定档案不得放行')
assert.match(noIdentity.reason, /尚未绑定人员档案/, '必须说明是账号没绑定档案')
assert.equal(noIdentity.actionLabel, null, '未绑定档案时没有可跳转的自救页面')
assert.equal(noIdentity.actionPath, null)

/* ② 评估流程没走完（含 HR 未审完）：引导去能力评估 */
const pending = resolveApplyGate({ empId: 7, assessmentPending: true, reportReady: false })
assert.equal(pending.canApply, false, '评估未完成不得放行')
assert.match(pending.reason, /能力评估尚未全部完成/)
assert.match(pending.reason, /HR 完成全部能力项审核/, '必须点明“HR 审核”这一环，否则员工会去重复做测试')
assert.equal(pending.actionLabel, '去完成能力评估')
assert.equal(pending.actionPath, ASSESSMENT_PATH)

/* ③ 评估已提交、HR 还没审完（报告未生成）：说明是“等待审核”而不是“你还没做” */
const reviewing = resolveApplyGate({ empId: 7, assessmentPending: false, reportReady: false })
assert.equal(reviewing.canApply, false, '报告未生成不得放行')
assert.match(reviewing.reason, /等待 HR 完成全部能力项审核/)
assert.equal(reviewing.actionLabel, '查看评估进度')
assert.equal(reviewing.actionPath, ASSESSMENT_PATH)
assert.notEqual(reviewing.reason, pending.reason, '“没做评估”与“审核中”必须是两句不同的话')

/* ④ 全部能力项审核完成：放行 */
const ready = resolveApplyGate({ empId: 7, assessmentPending: false, reportReady: true })
assert.equal(ready.canApply, true, '审核完成必须放行')
assert.equal(ready.reason, '', '可发起时不应有原因文案')
assert.equal(ready.actionLabel, null)
assert.equal(ready.actionPath, null)

/* ⑤ 判据优先级：未绑定档案 > 评估未完成 > 报告未生成 */
assert.equal(
  resolveApplyGate({ empId: null, assessmentPending: true, reportReady: false }).reason,
  noIdentity.reason,
  '多处不满足时先说最根因的那条（账号没绑定档案）',
)

/* ⑥ 匹配对象固定为本人 —— 员工侧不允许选人 */
assert.match(SELF_MATCH_TARGET_TEXT, /本人/, '必须明确写出匹配对象是本人')
assert.match(SELF_MATCH_TARGET_TEXT, /不能选择|不能修改/, '必须明确写出不可选人，避免被当成功能坏了')

console.log('self matching apply gate tests passed')
