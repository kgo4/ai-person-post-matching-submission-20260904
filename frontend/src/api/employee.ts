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

/** 新增员工 */
export function saveEmployee(data: EmpEmployeeCreateDTO): Promise<ApiResponse<void>> {
  return post<void>('/employee', data)
}

/** 更新员工 */
export function updateEmployee(id: number, data: EmpEmployee): Promise<ApiResponse<void>> {
  return put<void>(`/employee/${id}`, data)
}

/** 删除员工 */
export function deleteEmployee(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/employee/${id}`)
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
