/**
 * 员工管理 API
 */
import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type {
  EmpEmployee,
  EmpEmployeeCreateDTO,
  EmpAbility,
  EmpAbilitySaveDTO,
  EmpAbilityProfileVO,
  PendingAbilityClaim,
  PageResultVO,
} from './types'
import type { AxiosRequestConfig } from 'axios'

// ===================== Employee =====================

/** 分页查询员工 */
export function pageEmployees(params: PageParams, config?: AxiosRequestConfig): Promise<ApiResponse<PageResultVO<EmpEmployee>>> {
  return get<PageResultVO<EmpEmployee>>('/employee/page', params, config)
}

/** 根据ID查询员工 */
export function getEmployee(id: number): Promise<ApiResponse<EmpEmployee>> {
  return get<EmpEmployee>(`/employee/${id}`)
}

/**
 * 查询当前登录账号关联的人员档案（本人身份入口）。
 * 未绑定人员档案时后端返回 null，调用方需按“无员工身份”处理，不要伪造数据。
 */
export function getMyEmployee(): Promise<ApiResponse<EmpEmployee | null>> {
  return get<EmpEmployee | null>('/employee/me')
}

/** 新增员工 */
export function saveEmployee(data: EmpEmployeeCreateDTO): Promise<ApiResponse<void>> {
  return post<void>('/employee', data)
}

/** 更新员工 */
export function updateEmployee(id: number, data: EmpEmployee): Promise<ApiResponse<void>> {
  return put<void>(`/employee/${id}`, data)
}

/**
 * 启用 / 禁用员工（HR 对员工档案唯一的“下线”手段）。
 * 不删除任何历史数据，只改变在册状态，可随时切回。
 */
export function updateEmployeeStatus(id: number, status: number): Promise<ApiResponse<void>> {
  return put<void>(`/employee/${id}/status`, { status })
}

/**
 * 作废员工档案（逻辑删除）。
 *
 * 仅用于误建档案纠错，在职人员必须先「禁用」；作废后档案从列表隐藏，
 * 但历史业务数据（能力、匹配、学习记录）全部保留，可在数据库层面恢复。
 * 取代原先会级联物理删除业务数据的 deleteEmployee。
 */
export function archiveEmployee(id: number, reason: string): Promise<ApiResponse<void>> {
  return del<void>(`/employee/${id}`, { reason })
}

/** 锁定员工 */
export function lockEmployee(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/employee/${id}/lock`)
}

/** 解锁员工 */
export function unlockEmployee(id: number): Promise<ApiResponse<void>> {
  return put<void>(`/employee/${id}/unlock`)
}

/** 员工统计（总数、启用数、锁定数） */
export function getEmployeeStats(): Promise<ApiResponse<{ total: number; enabled: number; locked: number }>> {
  return get('/employee/stats')
}

/** 批量导入员工 */
export function batchImport(data: EmpEmployee[]): Promise<ApiResponse<number>> {
  return post<number>('/employee/batch-import', data)
}

/** Excel 导入员工 */
export function importEmployeesExcel(file: File): Promise<ApiResponse<number>> {
  const formData = new FormData()
  formData.append('file', file)
  return post<number>('/employee/import-excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 导出员工 Excel（返回二进制 Blob） */
export function exportEmployeesExcel(): Promise<ApiResponse<Blob>> {
  return get<Blob>('/employee/export-excel', undefined, { responseType: 'blob' })
}

/** 下载员工导入模板（返回二进制 Blob） */
export function downloadEmployeeTemplate(): Promise<ApiResponse<Blob>> {
  return get<Blob>('/employee/template', undefined, { responseType: 'blob' })
}

// ===================== Ability =====================

/** 获取员工能力画像 */
export function getAbilityProfile(empId: number): Promise<ApiResponse<EmpAbilityProfileVO>> {
  return get<EmpAbilityProfileVO>(`/employee/ability/profile/${empId}`)
}

/** 查询员工能力列表 */
export function listAbilities(empId: number): Promise<ApiResponse<EmpAbility[]>> {
  return get<EmpAbility[]>(`/employee/ability/${empId}`)
}

/** 获取尚未融合到正式画像的 Harness 待审核能力声明 */
export function listPendingAbilityClaims(empId: number): Promise<ApiResponse<PendingAbilityClaim[]>> {
  return get<PendingAbilityClaim[]>(`/employee/ability/pending/${empId}`)
}

/** 新增员工能力 */
export function saveAbility(data: EmpAbilitySaveDTO): Promise<ApiResponse<void>> {
  return post<void>('/employee/ability', data)
}

/** 更新员工能力 */
export function updateAbility(id: number, data: EmpAbilitySaveDTO): Promise<ApiResponse<void>> {
  return put<void>(`/employee/ability/${id}`, data)
}

/** 批量保存员工能力 */
export function batchSaveAbilities(data: EmpAbilitySaveDTO[]): Promise<ApiResponse<void>> {
  return post<void>('/employee/ability/batch', data)
}

/** 删除员工能力 */
export function deleteAbility(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/employee/ability/${id}`)
}

// ===================== 全面能力分析报告（HR 匹配闭环 P2） =====================

export interface CapabilityAnalysisReport {
  id: number
  empId: number
  versionNo: number
  autoPassedJson: string | null
  manualConfirmedJson: string | null
  manualRejectedJson: string | null
  finalLevelsJson: string | null
  summary: string | null
  createdBy: number | null
  createdTime: string | null
}

export interface CapabilityAnalysisItem {
  abilityName: string | null
  finalLevel: number | null
  finalConfidence: number | null
  decisionStatus: string | null
  reviewedBy: number | null
  reviewedTime: string | null
  comment: string | null
}

/** 查询报告全部版本（版本号倒序） */
export function listCapabilityReports(empId: number): Promise<ApiResponse<CapabilityAnalysisReport[]>> {
  return get<CapabilityAnalysisReport[]>(`/employee/${empId}/capability-analysis-report`)
}

/** 查询最新版报告 */
export function getLatestCapabilityReport(empId: number): Promise<ApiResponse<CapabilityAnalysisReport | null>> {
  return get<CapabilityAnalysisReport | null>(`/employee/${empId}/capability-analysis-report/latest`)
}

/**
 * 手动生成/补生成全面能力分析报告（HR 兜底）。
 * 自动生成依赖审核事件的 AFTER_COMMIT 通知，漏触发时报告不会落库；
 * 仍有待审内容时后端返回带具体数量的错误信息。
 */
export function generateCapabilityReport(empId: number): Promise<ApiResponse<number>> {
  return post<number>(`/employee/${empId}/capability-analysis-report/generate`)
}

// ===================== 已通过岗位（闭环设计 P3） =====================

/**
 * 已通过岗位条目。
 * 口径：HR 审核通过且已推送（publishStatus=1）的匹配记录中，匹配状态为「强适配 / 适配」的岗位，
 * 按岗位去重取最新一条。
 */
export interface PassedPost {
  postId: number
  postName: string | null
  matchStatus: number | null
  matchStatusName: string | null
  matchScore: number | null
  updatedTime: string | null
}

/** 查询指定人员的「已通过岗位」（HR 视角；员工传他人 empId 会被拒绝） */
export function getEmployeePassedPosts(empId: number): Promise<ApiResponse<PassedPost[]>> {
  return get<PassedPost[]>(`/employee/${empId}/passed-posts`)
}

/** 查询本人的「已通过岗位」（员工视角，人员范围由登录身份固定） */
export function getMyPassedPosts(): Promise<ApiResponse<PassedPost[]>> {
  return get<PassedPost[]>('/employee/me/passed-posts')
}
