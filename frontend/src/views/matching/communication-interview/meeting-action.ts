/**
 * 视频终面「进入会议」按钮的状态判定（HR 追踪页与员工「我的评估流程」共用同一份判据）。
 *
 * 为什么必须抽出来：
 * 两个页面对「这场终面还能不能进会」的答案本该一致，但此前各写各的 ——
 * HR 页是**无条件渲染**「进入会议」，员工页只判了 `status !== CANCELLED`。
 * 结果是终面已经**录入结论（通过 / 不通过）**、或者**员工已放弃、HR 已取消**之后，
 * 按钮还是亮着：点进去是一个早已结束（甚至从未开始）的会议室。这既误导 HR，
 * 也让员工以为自己漏了什么没做。
 *
 * 会议链接是**入会凭证**而不是「记录详情页」，所以判定口径是：
 * 只有当「这场会还可能有下一步」时才给按钮，其余情况一律收起按钮、改用一句中文占位说明
 * （用户要的是「隐藏或者其他提示」，占位文案就是那个「提示」——直接消失会让人以为界面漏渲染）。
 *
 * ⚠️ 状态码必须与后端 `CommunicationInterview` 保持一致；这里刻意不 import
 * `@/api/communication-interview`，因为本模块要能在 `node --experimental-strip-types`
 * 下被单测直接加载（`@/` 别名不参与 node 解析）。取值变动时两边一起改。
 */

/** 终面状态：0 待沟通 / 1 已完成 / 2 已取消 */
export const MEETING_INTERVIEW_STATUS = {
  PENDING: 0,
  FINISHED: 1,
  CANCELLED: 2,
} as const

/** 终面结论：1 通过 / 2 不通过 / 3 待定 */
export const MEETING_INTERVIEW_RESULT = {
  PASS: 1,
  FAIL: 2,
  UNDECIDED: 3,
} as const

/** 员工响应：1 接受 / 2 放弃；null = 尚未响应 */
export const MEETING_EMPLOYEE_RESPONSE = {
  ACCEPTED: 1,
  DECLINED: 2,
} as const

/** 按钮语义色（与 el-button `type` 取值一致） */
export type MeetingActionTone = 'primary' | 'success' | 'warning'

/** 判定入参：只取必要字段，两个页面各自的完整实体都能直接传进来 */
export interface MeetingActionInput {
  /** 终面状态（0/1/2），未知传 null */
  status?: number | null
  /** 终面结论（1/2/3），未录入传 null */
  result?: number | null
  /** 员工响应（1/2），未响应传 null */
  employeeResponse?: number | null
  /** 会议链接原文（可能是讯飞邀请多行文本里抽出的链接） */
  meetingUrl?: string | null
}

export interface MeetingAction {
  /** 是否渲染按钮 */
  visible: boolean
  /** 按钮文案 */
  label: string
  /** 是否禁用（可见但不可点：多为没有可用会议链接） */
  disabled: boolean
  /** 按钮语义色 */
  tone: MeetingActionTone
  /**
   * 状态说明（中文，永远非空）。
   * 按钮可见时作 tooltip，不可见时与 `placeholder` 配合解释原因。
   */
  reason: string
  /** 按钮不可见时在操作列显示的占位文案；空串表示该状态下操作列不显示任何提示 */
  placeholder: string
}

/** 会议链接是否可用（后端可能给空串或只有空白） */
export function hasMeetingUrl(url?: string | null): boolean {
  return typeof url === 'string' && url.trim().length > 0
}

/**
 * 判定「进入会议」按钮的呈现方式。
 *
 * 判定顺序是有意的，不要随意调整：
 * 1. **放弃 / 取消优先**：员工放弃会把该场直接置为已取消，此时即使状态码还没同步过来
 *    （`status` 仍是待沟通），也必须按「放弃」处理 —— 逆序会漏掉这个窗口期。
 * 2. **已完成**：人工沟通之后，会议已经结束了，「进入会议」不再有意义。
 *    唯一的例外是结论「待定」：这是一种「还没谈完、可能再约一次」的状态，
 *    保留按钮（文案改成「再次进入会议」）比收起更贴合 HR 的实际动作。
 * 3. **待沟通 / 状态未知**：这才是按钮的正常场景，只受「有没有链接」约束。
 */
export function resolveMeetingAction(row: MeetingActionInput): MeetingAction {
  const { status, result, employeeResponse } = row

  // 1. 员工放弃 / 已取消 —— 会议室已不存在，收起按钮
  if (employeeResponse === MEETING_EMPLOYEE_RESPONSE.DECLINED || status === MEETING_INTERVIEW_STATUS.CANCELLED) {
    const declined = employeeResponse === MEETING_EMPLOYEE_RESPONSE.DECLINED
    return {
      visible: false,
      label: '进入会议',
      disabled: true,
      tone: 'warning',
      reason: declined
        ? '员工已放弃该场终面，会议已取消，无需进入'
        : '该场终面已取消，无需进入',
      placeholder: declined ? '员工已放弃' : '已取消',
    }
  }

  // 2. 已完成（人工沟通后）
  if (status === MEETING_INTERVIEW_STATUS.FINISHED) {
    if (result === MEETING_INTERVIEW_RESULT.FAIL) {
      return {
        visible: false,
        label: '进入会议',
        disabled: true,
        tone: 'warning',
        reason: '该场终面结论为「不通过」，会议已结束，无需进入',
        placeholder: '未通过',
      }
    }
    if (result === MEETING_INTERVIEW_RESULT.UNDECIDED) {
      // 待定 = 可能改期再谈，是「已完成」里唯一还需要能进会的分支
      return hasMeetingUrl(row.meetingUrl)
        ? {
            visible: true,
            label: '再次进入会议',
            disabled: false,
            tone: 'warning',
            reason: '上次沟通结论为「待定」，如需改期再谈可再次进入会议',
            placeholder: '',
          }
        : {
            visible: true,
            label: '再次进入会议',
            disabled: true,
            tone: 'warning',
            reason: '上次沟通结论为「待定」，但该场终面没有可用的会议链接',
            placeholder: '',
          }
    }
    // 通过 / 结论缺失（历史数据）：会议已结束，收起按钮并说明结论
    const passed = result === MEETING_INTERVIEW_RESULT.PASS
    return {
      visible: false,
      label: '进入会议',
      disabled: true,
      tone: 'warning',
      reason: passed
        ? '该场终面结论为「通过」，会议已结束，无需进入'
        : '该场终面已完成，会议已结束，无需进入',
      placeholder: passed ? '已通过' : '已完成',
    }
  }

  // 3. 待沟通 / 状态未知 —— 按钮正常场景，只受链接缺失约束
  if (!hasMeetingUrl(row.meetingUrl)) {
    return {
      visible: true,
      label: '进入会议',
      disabled: true,
      tone: 'primary',
      reason: '该场终面没有可用的会议链接；如需重开会话，请先取消再重新发起',
      placeholder: '',
    }
  }
  if (employeeResponse === MEETING_EMPLOYEE_RESPONSE.ACCEPTED) {
    return {
      visible: true,
      label: '进入会议',
      disabled: false,
      tone: 'success',
      reason: '员工已接受邀请，可在约定时间进入会议',
      placeholder: '',
    }
  }
  return {
    visible: true,
    label: '进入会议',
    disabled: false,
    tone: 'primary',
    reason: '员工尚未响应邀请；可先进入会议等候，或提醒员工确认',
    placeholder: '',
  }
}
