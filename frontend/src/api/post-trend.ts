/**
 * 岗位趋势发现 API。
 *
 * 一次解析只有两个人工动作：上传权威材料、审核候选。中间全部自动：
 *   上传材料 → 自动索引 → 发起解析（异步） → 轮询进度 → 出两类候选 → 管理员确认 → 同事务落地
 *
 * 材料上传复用演化链路的资料入口：`/api/post/evolution/sources/internal-document` 是**唯一**
 * 能显式指定 `sourceCategory` 的上传接口（白皮书入口的类别由后端写死）。类别直接决定
 * 「跨来源印证」信号——同一岗位被 POLICY_DOCUMENT 与 MARKET_REPORT 同时提到才说明趋势成立，
 * 所以必须由上传者如实选择。
 * 后端该接口落 `sourceType=CLOUD_KNOWLEDGE_INTERNAL`，已登记在 `SourceRefConstants` 中，
 * Harness 校验来源引用时能正常解析。
 */
import { del, get, post, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// ===================== 枚举 =====================

/** 材料类别：与后端 KnowledgeSourceDocument.sourceCategory 口径一致 */
export const MATERIAL_CATEGORIES = [
  { value: 'POLICY_DOCUMENT', label: '政策文件', hint: '政府红头文件、部委通知' },
  { value: 'MARKET_REPORT', label: '市场职业报告', hint: '招聘平台或咨询机构报告' },
  { value: 'OCCUPATION_STANDARD', label: '职业标准', hint: '国家职业分类大典、行业标准' },
  { value: 'INDUSTRY_WHITEPAPER', label: '行业白皮书', hint: '产业研究白皮书' },
] as const

export type CandidateType = 'NEW_POST' | 'ABILITY_CHANGE'
export type ConfirmStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type HarnessDecision = 'PASS' | 'REVIEW' | 'BLOCK'

// ===================== 任务 =====================

export interface TrendTaskVO {
  id: number
  taskCode: string
  taskName: string
  taskStatus: string
  progressStatus: string
  progressPercent: number
  candidateCount: number
  newPostCount: number
  changeCount: number
  /** 无候选时必须说明原因，界面常驻展示 */
  diagnostics?: string
  errorMessage?: string
  createdTime: string
  finishedTime?: string
}

export interface TrendProgressStep {
  name: string
  status: string
}

export interface TrendProgressVO {
  taskId: number
  taskStatus: string
  currentStep: string
  percent: number
  steps: TrendProgressStep[]
  errorMessage?: string
  diagnostics?: string
}

export interface TrendTaskCreateRequest {
  taskName?: string
  sourceDocumentIds: number[]
  sourceCategories?: string[]
}

/** 材料上传结果（复用演化资料入口的返回结构） */
export interface MaterialUploadResult {
  documentId: number
  title: string
  sourceType: string
  sourceCategory: string
  chunkCount: number
  status: string
  /** 是否仅试算材料：不进材料库，解析结束后自动清理 */
  ephemeral?: boolean
  /** 命中重复材料时为 true —— 此时**没有**重复建索引，沿用原有索引 */
  reused?: boolean
  /** 后端组装的结果说明，已区分「新建 / 复用 / 复用并补索引」 */
  message?: string
}

/**
 * 上传权威材料并同步完成索引。
 *
 * @param ephemeral true = 仅试算：只想看看这份材料能解析出什么岗位，材料不长期保留
 */
export function uploadAuthorityMaterial(
  file: File,
  data: {
    title: string
    sourceCategory: string
    businessDomain?: string
    industry?: string
    ephemeral?: boolean
  }
): Promise<ApiResponse<MaterialUploadResult>> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', data.title)
  formData.append('sourceCategory', data.sourceCategory)
  // 权威材料默认高可信；线上口径由后端 authorityScore 换算，这里只声明信任等级
  formData.append('trustLevel', 'HIGH')
  formData.append('evolutionEnabled', 'true')
  formData.append('ephemeral', data.ephemeral ? 'true' : 'false')
  if (data.businessDomain) formData.append('businessDomain', data.businessDomain)
  if (data.industry) formData.append('industry', data.industry)

  return post('/post/evolution/sources/internal-document', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ===================== 材料库管理 =====================

export type MaterialIndexStatus = 'PENDING' | 'INDEXED' | 'FAILED'

/** 材料库里的一份权威材料 */
export interface AuthorityMaterial {
  documentId: number
  title: string
  sourceCategory: string
  /** 后端维护的类别中文名：避免下拉与列表各维护一份而错位 */
  sourceCategoryLabel: string
  chunkCount: number
  status: string
  indexStatus: MaterialIndexStatus | string
  /** 仅试算材料：默认不出现在材料库列表里 */
  ephemeral: boolean
  uploadedTime?: string
  lastIndexedTime?: string
  /** 有切片且没失败 → 才能参与解析 */
  readyForAnalysis: boolean
}

/**
 * 材料库查询条件。
 *
 * 刻意用 `type` 而不是 `interface`：`interface` 不会获得隐式索引签名，
 * 传给 `get(url, params: Record<string, unknown>)` 会报 TS2345；类型别名会。
 */
export type AuthorityMaterialPageQuery = {
  current?: number
  size?: number
  sourceCategory?: string
  keyword?: string
  /** 是否把「仅试算」材料也列出来（默认不列） */
  includeEphemeral?: boolean
}

export function pageAuthorityMaterials(
  params: AuthorityMaterialPageQuery = {}
): Promise<
  ApiResponse<{
    records: AuthorityMaterial[]
    total: number
    current: number
    size: number
    pages: number
  }>
> {
  return get('/post/evolution/sources/page', params)
}

/** 删除一份材料，并级联清理其检索片段与向量。已落地候选里的证据原文是快照，不受影响。 */
export function deleteAuthorityMaterial(documentId: number): Promise<ApiResponse<{ documentId: number; message: string }>> {
  return del(`/post/evolution/sources/${documentId}`)
}

/** 对材料重建检索索引。索引失败后用它重试，不需要重新上传。 */
export function reindexAuthorityMaterial(documentId: number): Promise<ApiResponse<{ documentId: number; chunkCount: number }>> {
  return post(`/post/evolution/sources/${documentId}/index`)
}

/** 发起趋势解析（异步）。 */
export function createTrendTask(data: TrendTaskCreateRequest): Promise<ApiResponse<TrendTaskVO>> {
  return post('/post/trend/tasks', data)
}

export function pageTrendTasks(params: { current?: number; size?: number } = {}): Promise<
  ApiResponse<{ records: TrendTaskVO[]; total: number; current: number; size: number; pages: number }>
> {
  return get('/post/trend/tasks/page', params)
}

export function getTrendTask(taskId: number): Promise<ApiResponse<TrendTaskVO>> {
  return get(`/post/trend/tasks/${taskId}`)
}

export function getTrendProgress(taskId: number): Promise<ApiResponse<TrendProgressVO>> {
  return get(`/post/trend/tasks/${taskId}/progress`)
}

export function cancelTrendTask(taskId: number): Promise<ApiResponse<boolean>> {
  return post(`/post/trend/tasks/${taskId}/cancel`)
}

// ===================== 候选 =====================

/** 卡片摘要：刻意只承载「一眼判断要不要点进去」的字段 */
export interface TrendCandidateSummary {
  id: number
  taskId: number
  candidateType: CandidateType
  postName: string
  postDescription?: string
  matchedPostId?: number
  matchedPostName?: string
  /** 0-1；null 表示本次未取得（向量检索不可用），界面显示「未比对」而不是 0 */
  similarityScore?: number | null
  emphasisScore?: number | null
  sourceCoverage?: number | null
  harnessDecision?: HarnessDecision | string | null
  riskLevel?: string | null
  confirmStatus: ConfirmStatus
  createdPostId?: number | null
  abilityCount: number
  previewAbilities: string[]
  unresolvedAbilityCount: number
  changeCount: number
  createdTime?: string
}

export interface TrendAbilityItem {
  abilityName: string
  tagId?: number | null
  matchedTagName?: string | null
  resolved: boolean
  similarTagId?: number | null
  similarTagName?: string | null
  similarity?: number | null
  tagCandidateId?: number | null
  suggestedLevel?: number | null
  suggestedWeight?: number | null
  isCore?: number | null
  evidenceRef?: number | null
  sourceRef?: string | null
  changeType?: string | null
  existingLevel?: number | null
  existingWeight?: number | null
}

export interface TrendCandidatePayload {
  responsibilities: string[]
  businessScenarios: string[]
  llmEmphasisScore?: number | null
  emphasisReason?: string | null
  effectiveEmphasisScore?: number | null
  mentionCount?: number | null
  sourceCoverage?: number | null
  harnessReason?: string | null
  /** 既有但材料未提及的能力，**仅供人工参考，不构成删除建议** */
  unmatchedExistingAbilities: string[]
  abilities: TrendAbilityItem[]
}

export interface TrendCandidateDetail {
  summary: TrendCandidateSummary
  payload: TrendCandidatePayload
  evidenceText?: string
  sourceRefs: string[]
  sourceTitles: string[]
  newTagCandidateIds: number[]
}

export interface NewTagCandidate {
  id: number
  candidateName: string
  similarTagId?: number | null
  similarTagName?: string | null
  similarityScore?: number | null
  evidenceText?: string | null
  status: string
  occurrenceCount?: number | null
  createdTime?: string
}

export interface TrendLandResult {
  candidateId: number
  candidateType: CandidateType
  postId: number
  postName: string
  abilityCount: number
  createdNewPost: boolean
}

export interface TrendBatchConfirmResult {
  landed: TrendLandResult[]
  skipped: { candidateId: number; postName?: string | null; reason: string }[]
}

export function pageTrendCandidates(params: {
  taskId: number
  current?: number
  size?: number
  candidateType?: CandidateType | ''
  confirmStatus?: ConfirmStatus | ''
  harnessDecision?: HarnessDecision | ''
}): Promise<
  ApiResponse<{ records: TrendCandidateSummary[]; total: number; current: number; size: number; pages: number }>
> {
  const { taskId, ...rest } = params
  return get(`/post/trend/tasks/${taskId}/candidates`, rest)
}

export function getTrendCandidateDetail(candidateId: number): Promise<ApiResponse<TrendCandidateDetail>> {
  return get(`/post/trend/candidates/${candidateId}`)
}

/** 人工调整后的能力项：只提交界面真正可编辑的字段 */
export interface TrendAbilityUpdateItem {
  abilityName: string
  tagId?: number | null
  suggestedLevel?: number | null
  suggestedWeight?: number | null
  isCore?: number | null
}

export function updateTrendCandidatePayload(
  candidateId: number,
  data: { postName?: string; postDescription?: string; abilities: TrendAbilityUpdateItem[] }
): Promise<ApiResponse<void>> {
  return put(`/post/trend/candidates/${candidateId}/payload`, data)
}

/** 仅改状态、不落地：APPROVED 先挂着 / REJECTED 驳回 */
export function reviewTrendCandidate(
  candidateId: number,
  data: { confirmStatus: ConfirmStatus; reviewComment?: string }
): Promise<ApiResponse<boolean>> {
  return post(`/post/trend/candidates/${candidateId}/review`, data)
}

/** 落地：新岗位候选建岗位并写能力画像；能力变更候选改写既有岗位能力画像 */
export function confirmTrendCandidate(
  candidateId: number,
  reviewComment?: string
): Promise<ApiResponse<TrendLandResult>> {
  return post(`/post/trend/candidates/${candidateId}/confirm`, { reviewComment })
}

export function batchConfirmTrendCandidates(
  candidateIds: number[],
  reviewComment?: string
): Promise<ApiResponse<TrendBatchConfirmResult>> {
  return post('/post/trend/candidates/batch-confirm', { candidateIds, reviewComment })
}

// ===================== 待审汇总（工作台待办） =====================

/** 跨任务的待人工审核汇总；工作台用它决定「审核岗位趋势候选」这条待办是否出现 */
export interface TrendPendingSummary {
  pendingCandidateCount: number
  pendingNewPostCount: number
  pendingChangeCount: number
  awaitingTaskCount: number
}

export function getTrendPendingSummary(): Promise<ApiResponse<TrendPendingSummary>> {
  return get('/post/trend/pending-summary')
}

// ===================== 新能力标签 =====================

export function listNewTagCandidates(taskId?: number): Promise<ApiResponse<NewTagCandidate[]>> {
  return get('/post/trend/tags/new-candidates', taskId ? { taskId } : {})
}

export function adoptNewTagCandidate(
  candidateId: number,
  tagId: number,
  comment?: string
): Promise<ApiResponse<void>> {
  return post(`/post/trend/tags/new-candidates/${candidateId}/adopt`, { tagId, comment })
}

export function ignoreNewTagCandidate(candidateId: number, comment?: string): Promise<ApiResponse<void>> {
  return post(`/post/trend/tags/new-candidates/${candidateId}/ignore`, { comment })
}
