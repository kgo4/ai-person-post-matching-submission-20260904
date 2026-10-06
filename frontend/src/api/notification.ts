/**
 * 站内通知 API（HR 匹配闭环 P1）。
 *
 * 发送侧只暴露「提醒评估」；查收侧全部按登录身份收口。
 */
import { get, put, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

export interface NotificationItem {
  id: number
  type: string
  title: string
  content: string | null
  bizType: string | null
  bizId: number | null
  readStatus: number
  createdTime: string | null
  readTime: string | null
}

export interface NotificationPage {
  records: NotificationItem[]
  total: number
  current: number
  size: number
  pages: number
}

/** 我的通知分页（未读优先，其余按发送时间倒序） */
export function pageMyNotifications(current: number, size: number): Promise<ApiResponse<NotificationPage>> {
  return get<NotificationPage>('/notifications/my', { current, size })
}

/** 我的未读数（顶栏红点轮询） */
export function getUnreadCount(): Promise<ApiResponse<number>> {
  return get<number>('/notifications/unread-count')
}

/** 标记单条已读 */
export function markNotificationRead(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/notifications/${id}/read`)
}

/** 全部标记已读 */
export function markAllNotificationsRead(): Promise<ApiResponse<void>> {
  return put<void>('/notifications/read-all')
}

/** HR 提醒员工进行能力评估（重复提醒由服务端幂等拦截） */
export function sendAssessmentReminder(empId: number): Promise<ApiResponse<void>> {
  return post<void>('/notifications/assessment-reminder', { empId })
}
