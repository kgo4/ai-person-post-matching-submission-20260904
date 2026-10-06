import assert from 'node:assert/strict'
import {
  MEETING_EMPLOYEE_RESPONSE,
  MEETING_INTERVIEW_RESULT,
  MEETING_INTERVIEW_STATUS,
  hasMeetingUrl,
  resolveMeetingAction,
} from './meeting-action.ts'

const URL = 'https://meeting.xfyun.cn/join/abc?pwd=1'

const STATUS = MEETING_INTERVIEW_STATUS
const RESULT = MEETING_INTERVIEW_RESULT
const RESPONSE = MEETING_EMPLOYEE_RESPONSE

/* ============ hasMeetingUrl ============ */
assert.equal(hasMeetingUrl(URL), true)
assert.equal(hasMeetingUrl(''), false)
assert.equal(hasMeetingUrl('   '), false, '只有空白不算有链接')
assert.equal(hasMeetingUrl(null), false)
assert.equal(hasMeetingUrl(undefined), false)

/* ============ 情形 3：待沟通 / 状态未知 ============ */

// 待沟通 + 有链接 + 未响应：正常可进会
let action = resolveMeetingAction({ status: STATUS.PENDING, meetingUrl: URL })
assert.equal(action.visible, true)
assert.equal(action.disabled, false)
assert.equal(action.label, '进入会议')
assert.equal(action.tone, 'primary')
assert.ok(action.reason.includes('尚未响应'), `未响应应提示去催员工，实际：${action.reason}`)
assert.equal(action.placeholder, '')

// 待沟通 + 员工已接受：换成 success 并在说明里体现「已确认」
action = resolveMeetingAction({
  status: STATUS.PENDING,
  employeeResponse: RESPONSE.ACCEPTED,
  meetingUrl: URL,
})
assert.equal(action.tone, 'success')
assert.ok(action.reason.includes('已接受'), `已接受应体现，实际：${action.reason}`)

// 待沟通 + 无链接：按钮保留但禁用（不能静默消失，否则 HR 以为功能坏了）
action = resolveMeetingAction({ status: STATUS.PENDING, meetingUrl: '' })
assert.equal(action.visible, true)
assert.equal(action.disabled, true)
assert.ok(action.reason.includes('会议链接'), `应说明缺链接，实际：${action.reason}`)

// 状态未知（null）+ 无链接：同样给禁用按钮而不是消失
action = resolveMeetingAction({ status: null, meetingUrl: null })
assert.equal(action.visible, true)
assert.equal(action.disabled, true)

/* ============ 放弃 / 取消：收起按钮 ============ */

// 员工已放弃，但状态码还没同步成「已取消」—— 必须按放弃处理（窗口期）
action = resolveMeetingAction({
  status: STATUS.PENDING,
  employeeResponse: RESPONSE.DECLINED,
  meetingUrl: URL,
})
assert.equal(action.visible, false, '员工放弃后不应再给「进入会议」')
assert.equal(action.placeholder, '员工已放弃')
assert.ok(action.reason.includes('放弃'), `应说明是员工放弃，实际：${action.reason}`)

// 已取消（非员工放弃，例如 HR 主动取消）
action = resolveMeetingAction({ status: STATUS.CANCELLED, meetingUrl: URL })
assert.equal(action.visible, false)
assert.equal(action.placeholder, '已取消')
assert.ok(action.reason.includes('已取消'), `实际：${action.reason}`)
// 两种取消必须能区分，否则 HR 不知道是自己取消的还是员工放弃了
assert.notEqual(
  resolveMeetingAction({ status: STATUS.CANCELLED, meetingUrl: URL }).placeholder,
  resolveMeetingAction({
    status: STATUS.CANCELLED,
    employeeResponse: RESPONSE.DECLINED,
    meetingUrl: URL,
  }).placeholder,
)

/* ============ 人工沟通后（已完成） ============ */

// 不通过 → 收起
action = resolveMeetingAction({ status: STATUS.FINISHED, result: RESULT.FAIL, meetingUrl: URL })
assert.equal(action.visible, false)
assert.equal(action.placeholder, '未通过')
assert.ok(action.reason.includes('不通过'), `实际：${action.reason}`)

// 通过 → 收起
action = resolveMeetingAction({ status: STATUS.FINISHED, result: RESULT.PASS, meetingUrl: URL })
assert.equal(action.visible, false)
assert.equal(action.placeholder, '已通过')

// 待定 → 唯一保留按钮的「已完成」，文案改为「再次进入会议」
action = resolveMeetingAction({ status: STATUS.FINISHED, result: RESULT.UNDECIDED, meetingUrl: URL })
assert.equal(action.visible, true)
assert.equal(action.disabled, false)
assert.equal(action.label, '再次进入会议')
assert.equal(action.tone, 'warning')

// 待定但没有链接 → 仍可见但禁用
action = resolveMeetingAction({ status: STATUS.FINISHED, result: RESULT.UNDECIDED, meetingUrl: '' })
assert.equal(action.visible, true)
assert.equal(action.disabled, true)

// 已完成但结论缺失（历史数据）→ 收起，不能说「已通过」
action = resolveMeetingAction({ status: STATUS.FINISHED, result: null, meetingUrl: URL })
assert.equal(action.visible, false)
assert.equal(action.placeholder, '已完成')
assert.equal(action.reason.includes('通过'), false, '结论缺失时不得说成通过')

/* ============ 全局不变量（用全组合扫描，防止新增分支破坏约定） ============ */

const statuses = [null, STATUS.PENDING, STATUS.FINISHED, STATUS.CANCELLED, 99]
const results = [null, RESULT.PASS, RESULT.FAIL, RESULT.UNDECIDED, 88]
const responses = [null, RESPONSE.ACCEPTED, RESPONSE.DECLINED, 77]
const urls = [URL, '', null, '  ']

const LABELS = ['进入会议', '再次进入会议']

for (const status of statuses) {
  for (const result of results) {
    for (const employeeResponse of responses) {
      for (const meetingUrl of urls) {
        const row = { status, result, employeeResponse, meetingUrl }
        const a = resolveMeetingAction(row)
        const where = JSON.stringify(row)

        assert.ok(LABELS.includes(a.label), `${where} 文案必须是已知值，实际 ${a.label}`)
        assert.ok(a.reason.length > 0, `${where} 说明不能为空（按钮 tooltip 靠它）`)
        assert.ok(['primary', 'success', 'warning'].includes(a.tone), `${where} tone 非法`)
        // 不变量 1：不可见 ⇔ 一定不可点，避免出现「隐藏了但语义上还能点」的矛盾态
        if (!a.visible) {
          assert.equal(a.disabled, true, `${where} 不可见时必须 disabled`)
        }
        // 不变量 2：不可见时必须有占位文案，否则操作列会空白一片让人以为漏渲染
        assert.ok(
          a.visible || a.placeholder.length > 0,
          `${where} 收起按钮时必须给出占位文案`,
        )
        // 不变量 3：可见时占位文案必须为空（避免按钮与占位同时出现）
        assert.equal(a.visible ? a.placeholder : '', '', `${where} 可见时不应有占位文案`)
        // 不变量 4：只有「有链接」才可能允许点击
        if (!a.disabled) {
          assert.equal(hasMeetingUrl(meetingUrl), true, `${where} 无链接却可点击`)
        }
      }
    }
  }
}

// 只要没有链接，就永远不可能出现「可点击」的按钮（跨状态扫描的收敛结论）
for (const status of statuses) {
  for (const result of results) {
    for (const employeeResponse of responses) {
      const a = resolveMeetingAction({ status, result, employeeResponse, meetingUrl: null })
      assert.equal(a.disabled, true, `无链接时不应可点击：${JSON.stringify({ status, result, employeeResponse })}`)
    }
  }
}

console.log('meeting action tests passed')
