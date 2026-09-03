/**
 * 来源证据权重配置 API
 */
import { get, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { SourceWeightConfig } from './types'

/** 获取所有权重配置 */
export function listSourceWeights(): Promise<ApiResponse<SourceWeightConfig[]>> {
  return get<SourceWeightConfig[]>('/system/source-weight/list')
}

/** 批量更新权重配置 */
export function batchUpdateSourceWeights(configs: Partial<SourceWeightConfig>[]): Promise<ApiResponse<SourceWeightConfig[]>> {
  return put<SourceWeightConfig[]>('/system/source-weight/batch-update', configs)
}
