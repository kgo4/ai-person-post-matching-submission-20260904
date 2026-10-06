/**
 * Excel岗位批量导入 API
 */
import request, { del, get, post } from '@/utils/request'
import type { ApiResponse, PageResult } from '@/utils/request'
import type { JdAbilityItem } from './types'

export interface ExcelStructure {
  sheets: {
    sheetName: string
    headerRowIndex: number
    dataStartRowIndex: number
    columnInfos: {
      columnIndex: number
      columnName: string
      mappedField: string
    }[]
  }[]
}

export interface PostImportItemPreview {
  itemId: number
  rowIndex: number
  postName: string
  postDescription: string
  analysisStatus: number
  abilities: JdAbilityItem[]
  errorMessage?: string
}

export interface PostImportPreview {
  batchId: number
  fileName: string
  totalRows: number
  structure: ExcelStructure
  items: PostImportItemPreview[]
  importStatus: number
  successCount?: number | null
  failCount?: number | null
  errorMessage?: string | null
}

export interface PostImportConfirmDTO {
  batchId: number
  /** 人工选择：复用本批次已确认能力纳入市场发现，不重复AI分析。 */
  includeMarketJd?: boolean
  items: {
    itemId: number
    postName: string
    postDescription: string
    confirmed: boolean
    abilities?: JdAbilityItem[]
  }[]
}

export interface PostImportBatchVO {
  id: number
  fileName: string
  totalRows: number
  successCount: number | null
  failCount: number | null
  importStatus: number
  cancelFlag: number
  errorMessage: string | null
  createdTime: string
  updatedTime: string
  pendingCount: number
  analyzingCount: number
  successAnalyzedCount: number
  failedAnalyzedCount: number
}

/** 下载固定表头的岗位 JD 批量导入模板。 */
export function downloadPostImportTemplate(): Promise<Blob> {
  return request.get('/post/excel-import/template', { responseType: 'blob' }).then((response: any) => response.data)
}

/** 上传并解析Excel */
export function uploadAndAnalyze(file: File): Promise<ApiResponse<PostImportPreview>> {
  const formData = new FormData()
  formData.append('file', file)
  return post<PostImportPreview>('/post/excel-import/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 触发AI能力分析 */
export function analyzeBatch(batchId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/excel-import/analyze/${batchId}`)
}

/** 获取导入预览（含分析进度） */
export function getImportPreview(batchId: number): Promise<ApiResponse<PostImportPreview>> {
  return get<PostImportPreview>(`/post/excel-import/preview/${batchId}`)
}

/** 确认并批量导入 */
export function confirmImport(data: PostImportConfirmDTO): Promise<ApiResponse<void>> {
  return post<void>('/post/excel-import/confirm', data)
}

/** 将已完成导入批次纳入市场发现，不重新分析 JD。 */
export function includeBatchInMarketDiscovery(batchId: number): Promise<ApiResponse<number>> {
  return post<number>(`/post/excel-import/${batchId}/include-market-jd`)
}

/** 取消分析任务 */
export function cancelBatch(batchId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/excel-import/cancel/${batchId}`)
}

/** 分页查询导入批次列表 */
export function pageImportBatches(params: {
  current: number
  size: number
  importStatus?: number
}): Promise<ApiResponse<PageResult<PostImportBatchVO>>> {
  return get<PageResult<PostImportBatchVO>>('/post/excel-import/page', params)
}

/** 重试批次分析 */
export function retryBatch(batchId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/excel-import/retry/${batchId}`)
}

/** 删除导入批次（不影响已导入岗位） */
export function deleteImportBatch(batchId: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/excel-import/${batchId}`)
}
