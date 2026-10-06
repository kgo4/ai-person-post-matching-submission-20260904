/**
 * 员工自助发起匹配的准入闸门（纯逻辑，无框架依赖）。
 *
 * 口径来源（用户 2026-09-04 明确）：**员工必须等该员工全部能力项都被 HR 审核完成后**
 * 才能发起匹配，且**匹配对象只能是员工本人**（不能选人）。
 *
 * 这三道闸门与后端同口径：
 *   ① `empId` 必须存在 —— 服务端 `executeSelfMatching` 未绑定档案时抛 403；
 *   ② 能力评估流程全部走完（含人工审核）—— `assessmentPending`；
 *   ③ 能力分析报告已生成 —— 报告是「全部能力项审核完成」的产物，
 *      后端 `assertCapabilityReportReady` 有同口径强校验。
 *
 * 为什么把文案抽到这里：页面既要「入口常驻可见」（不能因为没有权限就把按钮藏掉），
 * 又要「说清楚还差什么」。之前只有 hover tooltip 一句话，员工读到的信息量不足，
 * 甚至会把「审核中」误读成「没有入口/没有权限」。抽成纯函数后文案可被 .mjs 测试锁住。
 */

export interface ApplyGateInput {
  /** 当前账号绑定的人员档案 ID；未绑定为 null */
  empId: number | null
  /** 能力评估是否尚未全部完成（含人工审核未清空） */
  assessmentPending: boolean
  /** 全面能力分析报告是否已生成 */
  reportReady: boolean
}

export interface ApplyGateResult {
  /** 是否允许发起匹配 */
  canApply: boolean
  /** 不可发起的原因；可发起时为空串 */
  reason: string
  /** 补齐动作的按钮文案；无可引导动作时为 null */
  actionLabel: string | null
  /** 补齐动作的跳转路径；无可引导动作时为 null */
  actionPath: string | null
}

/** 员工查看本人评估进度 / 补做评估的入口 */
const ASSESSMENT_PATH = '/employee/ability-profile/assessment'

export function resolveApplyGate(input: ApplyGateInput): ApplyGateResult {
  if (input.empId == null) {
    return {
      canApply: false,
      reason: '当前账号尚未绑定人员档案，请联系 HR 或权限管理员完成绑定后再发起匹配。',
      actionLabel: null,
      actionPath: null,
    }
  }
  if (input.assessmentPending) {
    return {
      canApply: false,
      reason: '你的能力评估尚未全部完成。需要先完成简历提交、AI 测试与 AI 面试，'
        + '并由 HR 完成全部能力项审核后，才能发起匹配。',
      actionLabel: '去完成能力评估',
      actionPath: ASSESSMENT_PATH,
    }
  }
  if (!input.reportReady) {
    return {
      canApply: false,
      reason: '你的能力评估已提交，正在等待 HR 完成全部能力项审核。'
        + '审核完成后系统会自动生成能力分析报告，届时即可发起匹配。',
      actionLabel: '查看评估进度',
      actionPath: ASSESSMENT_PATH,
    }
  }
  return { canApply: true, reason: '', actionLabel: null, actionPath: null }
}

/**
 * 匹配对象说明（固定为本人）。
 *
 * 员工侧不允许选人：请求体里的 empId 一律被服务端忽略，人员范围由登录身份固定。
 * 页面上必须把这件事写死在文案里，避免员工以为「没得选 = 功能坏了」。
 */
export const SELF_MATCH_TARGET_TEXT = '匹配对象固定为你本人，你不能选择或修改匹配人员。'
