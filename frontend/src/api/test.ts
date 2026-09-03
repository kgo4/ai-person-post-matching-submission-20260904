/**
 * 测试 API
 */
import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

/** 测试 Milvus 连接 */
export function testMilvus(): Promise<ApiResponse<any>> {
  return get<any>('/test/milvus')
}

/** 测试所有连接 */
export function testAll(): Promise<ApiResponse<any>> {
  return get<any>('/test/all')
}
