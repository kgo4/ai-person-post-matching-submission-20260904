import { get, post, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===== 岗位模型生成中心 =====

/**
 * 从岗位原型生成能力模型草稿
 */
export function generateFromPrototype(postId: number, prototypeId: number, description?: string): Promise<ApiResponse<any>> {
  return post<any>('/post/model-generation/from-prototype', null, {
    params: { postId, prototypeId, description },
  })
}

/**
 * 从JD智能生成能力模型草稿
 */
export function generateFromJD(postId: number, jdText: string, description?: string): Promise<ApiResponse<any>> {
  return post<any>('/post/model-generation/from-jd', jdText, {
    params: { postId, description },
  })
}

/**
 * 从已有岗位复制能力模型草稿
 */
export function generateFromCopy(sourcePostId: number, targetPostId: number, description?: string): Promise<ApiResponse<any>> {
  return post<any>('/post/model-generation/from-copy', null, {
    params: { sourcePostId, targetPostId, description },
  })
}

// ===== 岗位模型版本管理 =====

/**
 * 创建草稿版本
 */
export function createDraftVersion(postId: number, sourceType: string, description?: string): Promise<ApiResponse<any>> {
  return post<any>('/post/model-version/draft', null, {
    params: { postId, sourceType, description },
  })
}

/**
 * 保存版本明细
 */
export function saveVersionItems(versionId: number, items: any[]): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-version/${versionId}/items`, items)
}

/**
 * 发布版本
 */
export function publishVersion(versionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-version/${versionId}/publish`)
}

/**
 * 回滚到指定版本
 */
export function rollbackVersion(versionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-version/${versionId}/rollback`)
}

/**
 * 获取岗位的版本列表
 */
export function listVersions(postId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/post/model-version/list/${postId}`)
}

/**
 * 获取版本详情
 */
export function getVersionDetail(versionId: number): Promise<ApiResponse<any>> {
  return get<any>(`/post/model-version/${versionId}`)
}

/**
 * 获取版本明细
 */
export function getVersionItems(versionId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/post/model-version/${versionId}/items`)
}

/**
 * 删除草稿版本
 */
export function deleteDraftVersion(versionId: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/model-version/${versionId}`)
}

// ===== 未匹配能力标签（M-07） =====

/**
 * 查询版本下 AI 提取但未匹配已有标签的能力列表
 */
export function getUnmatchedAbilities(versionId: number): Promise<ApiResponse<any[]>> {
  return get<any[]>(`/post/model-version/${versionId}/unmatched-abilities`)
}

/**
 * 绑定未匹配能力到已有标签
 */
export function bindUnmatchedAbility(versionId: number, id: number, tagId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-version/${versionId}/unmatched-abilities/${id}/bind`, { tagId })
}

/**
 * 忽略未匹配能力
 */
export function ignoreUnmatchedAbility(versionId: number, id: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-version/${versionId}/unmatched-abilities/${id}/ignore`)
}

// ===== 岗位模型导入 =====

/**
 * 解析Excel文件
 */
export function parseModelExcel(file: File): Promise<ApiResponse<any[]>> {
  const formData = new FormData()
  formData.append('file', file)
  return post<any[]>('/post/model-import/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 批量导入（模板B：直接导入权重）
 */
export function importTemplateB(rows: any[]): Promise<ApiResponse<Record<number, number>>> {
  return post<Record<number, number>>('/post/model-import/import/template-b', rows)
}

/**
 * 批量导入（模板A：AI补齐）
 */
export function importTemplateA(rows: any[]): Promise<ApiResponse<Record<number, number>>> {
  return post<Record<number, number>>('/post/model-import/import/template-a', rows)
}

/**
 * 一键归一化权重到100%
 */
export function normalizeWeights(postId: number): Promise<ApiResponse<any[]>> {
  return post<any[]>(`/post/model-import/normalize/${postId}`)
}

/**
 * 复制岗位模型
 */
export function copyPostModel(sourcePostId: number, targetPostId: number): Promise<ApiResponse<number>> {
  return post<number>('/post/model-import/copy', null, {
    params: { sourcePostId, targetPostId },
  })
}
