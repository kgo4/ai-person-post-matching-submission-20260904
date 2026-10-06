/**
 * 视频终面 API（人岗匹配闭环设计 P5）。
 *
 * 路线：HR 手动发起真实视频沟通作为入职前最终确认。媒体走讯飞会议，平台不做媒体，
 * 只管「发起邀请 → 通知 → 留痕 → 定论」。
 *
 * 2026-09-04 补充：发起时系统会**异步向员工档案邮箱发一封 QQ 邮件邀请**（另附站内通知），
 * 员工可在本人能力画像页「接受 / 放弃」；员工响应回推到发起 HR 的站内通知，
 * 放弃会直接把该场终面置为已取消。`respondInterview` 是员工侧唯一写接口。
 * 平台仍无法感知员工是否真的入会，终面完成与否以 HR 手动录入结论为准。
 */
import { get, post, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type { PageResultVO } from './types'

// ===================== Types =====================

/** 终面状态：0待沟通 1已完成 2已取消 */
export const INTERVIEW_STATUS = {
  PENDING: 0,
  FINISHED: 1,
  CANCELLED: 2,
} as const

/** 终面结论：1通过 2不通过 3待定 */
export const INTERVIEW_RESULT = {
  PASS: 1,
  FAIL: 2,
  UNDECIDED: 3,
} as const

/**
 * 员工对终面邀请的响应：1接受 2放弃；`null` = 尚未响应。
 *
 * 「待响应」是一个需要 HR 去催的真实状态，不要把它和 `null`（无数据）混为一谈：
 * 列表里未响应显示为「待响应」而不是「--」。
 */
export const INTERVIEW_RESPONSE = {
  ACCEPTED: 1,
  DECLINED: 2,
} as const

/** 邀请邮件状态：0未发送 1已发送 2发送失败 3已跳过（未配置发信账号/员工无邮箱） */
export const INVITE_MAIL_STATUS = {
  NOT_SENT: 0,
  SENT: 1,
  FAILED: 2,
  SKIPPED: 3,
} as const

export interface CommunicationInterview {
  id: number
  empId: number
  empName: string | null
  postId: number | null
  postName: string | null
  matchingRecordId: number | null
  meetingUrl: string
  meetingSource: string | null
  scheduledTime: string | null
  status: number | null
  statusName: string | null
  result: number | null
  resultName: string | null
  /** HR 沟通纪要与评价（员工侧可读原文） */
  comment: string | null
  /** 员工响应：1接受 2放弃；null=尚未响应（列表显示「待响应」） */
  employeeResponse: number | null
  /** 员工响应名称（后端已给中文，未响应为「待响应」） */
  employeeResponseName: string | null
  /** 员工放弃终面时填写的说明 */
  employeeResponseComment: string | null
  employeeRespondedTime: string | null
  /** 邀请邮件状态：0未发送 1已发送 2发送失败 3已跳过 */
  inviteMailStatus: number | null
  /** 邀请邮件状态名称（只表示「发没发出去」，具体原因见 inviteMailError） */
  inviteMailStatusName: string | null
  /** 邀请邮件的收件邮箱 —— 取自员工档案里的个人邮箱，与平台发信邮箱无关 */
  employeeEmail: string | null
  /**
   * 邀请邮件未发出的原因（发送失败或跳过时的可读说明；发送成功为 null）。
   *
   * 必须透出：只给一个「发送失败」标签，HR 无法判断该去改员工邮箱、还是找管理员配发信账号。
   */
  inviteMailError: string | null
  createdBy: number | null
  createdTime: string | null
  finishedTime: string | null
}

/**
 * 可邀约候选条目。
 *
 * 2026-09-04 口径变更：候选池 = **全部匹配记录**（不再限定「已推送 + 匹配通过」），
 * 因此 matchStatus 可能是 待观察/不适配 甚至 null（尚未评分）。
 * `recommended`（匹配通过）只用于排序与提示，**不影响能否发起**。
 */
export interface InterviewCandidate {
  empId: number
  empName: string | null
  postId: number | null
  postName: string | null
  matchingRecordId: number
  matchStatus: number | null
  matchStatusName: string | null
  /** 是否推荐（匹配通过：强适配/适配）；后端已把推荐项排在前 */
  recommended: boolean | null
  matchScore: number | null
  hasPendingInterview: boolean | null
  latestResult: number | null
}

/** 沟通要点文档（四区） */
export interface InterviewBriefing {
  empId: number
  empName: string | null
  postId: number | null
  postName: string | null
  ability: {
    summary: string | null
    reportVersion: number | null
    autoPassedCount: number
    manualConfirmedCount: number
    manualRejectedCount: number
    rejectedAbilities: string[]
  }
  match: {
    finalMatchScore: number | null
    aiMatchScore: number | null
    matchStatus: number | null
    matchStatusName: string | null
  }
  gapAndLearning: {
    gapCount: number
    gapAbilities: string[]
    approvedOutcomeCount: number
    pendingOutcomeCount: number
    rejectedOutcomeCount: number
  }
  talkingPoints: string[]
  /** 一键复制用纯文本 */
  plainText: string
}

export interface CreateInterviewDTO {
  empId: number
  postId?: number
  matchingRecordId?: number
  /**
   * 会议链接或**讯飞会议「复制邀请信息」的整段多行文本**。
   * 服务层会从中抽取域名命中白名单的链接（优先含 /join 的入会链接），
   * 因此前端不必也不应替用户裁剪成裸链接。
   */
  meetingUrl: string
  scheduledTime?: string
}

// ===================== API =====================

/** 可发起沟通的推荐池（已推送且强适配/适配；平台不自动发起，由 HR 手选） */
export function listInterviewCandidates(limit = 100): Promise<ApiResponse<InterviewCandidate[]>> {
  return get<InterviewCandidate[]>('/communication-interviews/candidates', { limit })
}

/** 沟通要点文档：实时聚合不落库 */
export function getInterviewBriefing(
  empId: number,
  matchingRecordId?: number,
): Promise<ApiResponse<InterviewBriefing>> {
  return get<InterviewBriefing>('/communication-interviews/briefing', { empId, matchingRecordId })
}

/** 待沟通数量（HR 待办角标） */
export function getPendingInterviewCount(): Promise<ApiResponse<number>> {
  return get<number>('/communication-interviews/pending-count')
}

/** HR 发起视频终面 */
export function createInterview(data: CreateInterviewDTO): Promise<ApiResponse<number>> {
  return post<number>('/communication-interviews', data)
}

/** 终面记录分页（HR 追踪） */
export function pageInterviews(
  params: PageParams & { empId?: number; status?: number },
): Promise<ApiResponse<PageResultVO<CommunicationInterview>>> {
  return get<PageResultVO<CommunicationInterview>>('/communication-interviews', params)
}

/** 我的终面记录（员工只读，含 HR 评价原文） */
export function pageMyInterviews(
  params: PageParams,
): Promise<ApiResponse<PageResultVO<CommunicationInterview>>> {
  return get<PageResultVO<CommunicationInterview>>('/communication-interviews/my', params)
}

/** HR 取消（仅待沟通） */
export function cancelInterview(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/communication-interviews/${id}/cancel`)
}

/**
 * 员工响应终面邀请（**员工侧唯一写接口**）。
 *
 * 一次性：响应过就不可再改（否则给 HR 的响应通知会撞防重唯一键、状态也会反复无意义），
 * 需要变更只能由 HR 重新发起一场。`ACCEPTED` 只记录意愿（状态仍为待沟通）；
 * `DECLINED` 会直接把该场终面置为已取消，`comment` 作为放弃原因转给发起 HR。
 */
export function respondInterview(
  id: number,
  response: number,
  comment?: string,
): Promise<ApiResponse<void>> {
  return put<void>(`/communication-interviews/${id}/respond`, { response, comment })
}

/** HR 录入结论：1通过 2不通过 3待定 */
export function recordInterviewResult(
  id: number,
  result: number,
  comment?: string,
): Promise<ApiResponse<void>> {
  return put<void>(`/communication-interviews/${id}/result`, { result, comment })
}
