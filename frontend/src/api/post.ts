/**
 * 岗位管理 API
 */
import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { PageParams } from '@/types/api'
import type {
  PostPost,
  PostCreateDTO,
  PostAbilityModel,
  PostAbilityModelVO,
  PostAbilityModelConfigDTO,
  PostHardConditionRule,
  PostHardConditionRuleDTO,
  PostModelTemplate,
  PostTemplateSaveDTO,
  PostTemplateCreateDTO,
  TemplateAbilityModel,
  JdAnalyzeResponse,
  JdAbilityItem,
  PageResultVO,
} from './types'
import type { AxiosRequestConfig } from 'axios'

// ===================== Post =====================

/** 分页查询岗位 */
export function pagePosts(params: PageParams, config?: AxiosRequestConfig): Promise<ApiResponse<PageResultVO<PostPost>>> {
  return get<PageResultVO<PostPost>>('/post/page', params, config)
}

/** 查询所有启用岗位 */
export function listEnabledPosts(): Promise<ApiResponse<PostPost[]>> {
  return get<PostPost[]>('/post/enabled')
}

/** 根据ID查询岗位 */
export function getPost(id: number): Promise<ApiResponse<PostPost>> {
  return get<PostPost>(`/post/${id}`)
}

/** 新增岗位 */
export function savePost(data: PostCreateDTO): Promise<ApiResponse<void>> {
  return post<void>('/post', data)
}

/** 更新岗位 */
export function updatePost(id: number, data: PostPost): Promise<ApiResponse<void>> {
  return put<void>(`/post/${id}`, data)
}

/** 删除岗位 */
export function deletePost(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/${id}`)
}

// ===================== Ability Model =====================

/** 获取岗位能力模型视图 */
export function getPostModel(postId: number): Promise<ApiResponse<PostAbilityModelVO>> {
  return get<PostAbilityModelVO>(`/post/ability-model/${postId}`)
}

/** 查询岗位能力模型配置列表 */
export function listPostModels(postId: number): Promise<ApiResponse<PostAbilityModel[]>> {
  return get<PostAbilityModel[]>(`/post/ability-model/list/${postId}`)
}

/** 查询已配置至少一项能力模型的岗位 ID。 */
export function listConfiguredPostIds(postIds: number[]): Promise<ApiResponse<number[]>> {
  return post<number[]>('/post/ability-model/configured-post-ids', postIds)
}

/** 新增岗位能力模型配置 */
export function saveModelConfig(data: PostAbilityModelConfigDTO): Promise<ApiResponse<void>> {
  return post<void>('/post/ability-model', data)
}

/** 更新岗位能力模型配置 */
export function updateModelConfig(id: number, data: PostAbilityModelConfigDTO): Promise<ApiResponse<void>> {
  return put<void>(`/post/ability-model/${id}`, data)
}

/** 批量保存岗位能力模型配置 */
export function batchModelConfig(data: PostAbilityModelConfigDTO[]): Promise<ApiResponse<void>> {
  return post<void>('/post/ability-model/batch', data)
}

/** 删除岗位能力模型配置 */
export function deleteModelConfig(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/ability-model/${id}`)
}

// ===================== Hard Condition Rule =====================

/** 查询岗位硬性条件规则 */
export function listPostHardConditionRules(postId: number): Promise<ApiResponse<PostHardConditionRule[]>> {
  return get<PostHardConditionRule[]>(`/post/hard-condition-rule/list/${postId}`)
}

/** 批量保存岗位硬性条件规则 */
export function batchPostHardConditionRules(
  postId: number,
  data: PostHardConditionRuleDTO[]
): Promise<ApiResponse<void>> {
  return post<void>(`/post/hard-condition-rule/batch/${postId}`, data)
}

// ===================== Model Template =====================

/** 分页查询能力模型模板 */
export function pageTemplates(params: PageParams): Promise<ApiResponse<PageResultVO<PostModelTemplate>>> {
  return get<PageResultVO<PostModelTemplate>>('/post/model-template/page', params)
}

/** 根据ID查询能力模型模板 */
export function getTemplate(id: number): Promise<ApiResponse<PostModelTemplate>> {
  return get<PostModelTemplate>(`/post/model-template/${id}`)
}

/** 新增能力模型模板 */
export function saveTemplate(data: PostTemplateCreateDTO): Promise<ApiResponse<void>> {
  return post<void>('/post/model-template', data)
}

/** 更新能力模型模板 */
export function updateTemplate(id: number, data: PostTemplateSaveDTO): Promise<ApiResponse<void>> {
  return put<void>(`/post/model-template/${id}`, data)
}

/** 删除能力模型模板 */
export function deleteTemplate(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/post/model-template/${id}`)
}

// ===================== Template Ability Model =====================

/** 获取模板能力要求列表 */
export function getTemplateAbilityModels(templateId: number): Promise<ApiResponse<TemplateAbilityModel[]>> {
  return get<TemplateAbilityModel[]>(`/post/model-template/${templateId}/ability-models`)
}

/** 保存模板能力要求列表 */
export function saveTemplateAbilityModels(templateId: number, data: TemplateAbilityModel[]): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-template/${templateId}/ability-models`, data)
}

/** 应用模板到岗位 */
export function applyTemplateToPost(templateId: number, postId: number): Promise<ApiResponse<void>> {
  return post<void>(`/post/model-template/${templateId}/apply/${postId}`)
}

// ===================== JD Import =====================

/** AI分析JD提取能力项 */
export function analyzeJd(postId: number, jdText: string): Promise<ApiResponse<JdAnalyzeResponse>> {
  return post<JdAnalyzeResponse>('/post/jd-import/analyze', { postId, jdText })
}

/** 确认并应用JD分析结果到岗位能力模型 */
export function confirmJdResult(postId: number, items: JdAbilityItem[]): Promise<ApiResponse<void>> {
  return post<void>('/post/jd-import/confirm', { postId, items })
}
