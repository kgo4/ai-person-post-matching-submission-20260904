/**
 * 新兴岗位定义 API
 */
import { del, get, post, put } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { JdAbilityItem } from './types'
import type { PostPrototypeVO } from './post-prototype'

// ===================== 新兴岗位发现 =====================

/** 热门能力标签 */
export interface HotAbility {
  abilityName: string
  mentionCount: number
  growthRate: number
  relatedPostCount: number
}

/** 技术趋势 */
export interface TechTrend {
  techName: string
  trendDirection: 'RISING' | 'STABLE' | 'DECLINING'
  heatScore: number
  typicalScenario: string
}

/** 市场洞察 */
export interface MarketInsight {
  hotAbilities: HotAbility[]
  techTrends: TechTrend[]
  lastUpdated: string
  analyzedJdCount: number
  candidateCount: number
  sourcePlatformCount?: number
  independentEmployerCount?: number
  sourceDiversityScore?: number
  companyDiversityScore?: number
  deduplicatedCount?: number
  noiseFilteredCount?: number
  /** 已索引且参与解析的知识资料数量 */
  indexedDocumentCount?: number
  /** 已形成技能文档的资料与 JD 样本总数 */
  matchedDocumentCount?: number
  /** 本次解析使用的岗位能力词表规模 */
  vocabularySize?: number
  /** 词表来源：POST_ABILITY_MODEL / ABILITY_TAG / SEED */
  vocabularySource?: string
  /** 从资料中识别出的能力词数量 */
  recognizedAbilityCount?: number
  /** 解析诊断说明；无候选时说明具体原因 */
  diagnosticMessage?: string
}

/** 新兴岗位发现结果 */
export interface EmergingPostDiscovery {
  /** 候选方向名称：由后端按能力社区自动生成，**不是**任何文档标题或文件名 */
  candidateName: string
  description: string
  coreAbilities: string[]
  /** 证据总条数 = marketJdCount + knowledgeDocumentCount，不要单独当「JD 条数」展示 */
  frequency: number
  /** 支撑该候选的市场 JD 条数 */
  marketJdCount?: number
  /** 支撑该候选的知识资料份数（非 JD） */
  knowledgeDocumentCount?: number
  /** 证据中出现的真实岗位名（取自市场 JD，不含资料标题） */
  evidencePostNames?: string[]
  /** 证据来源构成：MARKET_JD / OFFICIAL_POLICY / INDUSTRY_REPORT 等 */
  evidenceBreakdown?: string[]
  noveltyScore: number
  marketHeatScore: number
  relatedIndustries: string[]
  sourceSummary: string
  emergenceScore?: number
  trendGrowthScore?: number
  sourceDiversityScore?: number
  sourcePlatformCount?: number
  independentEmployerCount?: number
  companyDiversityScore?: number
  semanticNoveltyScore?: number
  evidenceCredibilityScore?: number
  cohesionScore?: number
  sourceRefs?: string[]
  harnessDecision?: 'PASS' | 'REVIEW' | 'BLOCK'
  reviewStatus?: 'OBSERVATION' | 'PENDING' | 'APPROVED' | 'REJECTED'
  relatedExistingPostIds?: number[]
  differentiationReason?: string
  discoveryMode?: 'OBSERVATION' | 'CANDIDATE' | 'DISCOVERY'
  recommendedAction?: 'POST_EVOLUTION' | 'EMERGING_POST_REVIEW'
  sourceTypes?: string[]
  /** 原始岗位称谓：只包含市场 JD 的岗位名 */
  rawTitles?: string[]
  /** 来源材料标题：可能来自上传文件名，仅作溯源，不是岗位名 */
  sourceTitles?: string[]
  evidenceSummary?: string
  riskFlags?: string[]
  policyValidated?: boolean
}

export interface EmergingPostRequest {
  postName?: string
  description?: string
  industry?: string
  keyResponsibilities?: string
  createPost?: boolean
  sourceTypes?: string[]
  documentIds?: number[]
  includeMarketJd?: boolean
}

/** 单条能力的证据来源 */
export interface AbilityEvidenceSource {
  sourceType: string
  sourceName?: string
  collectedAt?: string
  confidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  sampleCount?: number
}

/** 交叉验证摘要 */
export interface CrossValidationSummary {
  /** 覆盖的数据源种类数 */
  sourceDiversity: number
  /** 一致性评分 0-100 */
  consistencyScore: number
  /** 各数据源覆盖情况 */
  sourceBreakdown: Array<{ sourceType: string; label: string; abilityCount: number }>
  /** 时效性等级 */
  freshnessLevel: 'FRESH' | 'RECENT' | 'STALE'
  /** 最新采集时间 */
  lastCollectedAt?: string
}

/** 拓展的能力推荐项 — 含证据来源和置信度 */
export interface EmergingAbilityItem extends JdAbilityItem {
  /** 多源证据列表 */
  evidenceSources?: AbilityEvidenceSource[]
  /** AI置信度分数 0-100 */
  confidenceScore?: number
  /** 幻觉风险标识 */
  hallucinationRisk?: boolean
}

export interface EmergingPostResponse {
  createdPostId?: number
  recommendedPrototypes: PostPrototypeVO[]
  recommendedAbilities: EmergingAbilityItem[]
  suggestedDescription?: string
  reasoning?: string
  /** 交叉验证摘要 */
  crossValidation?: CrossValidationSummary
  /** 数据源概览 */
  dataSources?: string[]
  /** 核心职责列表 */
  coreResponsibilities?: string[]
  /** 必备技能列表 */
  requiredSkills?: string[]
  /** 加分技能列表 */
  bonusSkills?: string[]
  /** 典型行业应用场景列表 */
  industryScenarios?: string[]
}

export interface EmergingPostConfirmDTO {
  postName: string
  description?: string
  abilities: JdAbilityItem[]
}

export interface MarketJdImportResult {
  imported: number
  batchNo: string
}

/** 分析新兴岗位 */
export function analyzeEmergingPost(data: EmergingPostRequest): Promise<ApiResponse<{ taskId: string; status: string }>> {
  return post<{ taskId: string; status: string }>('/post/emerging/analyze', data)
}

export interface EmergingAnalyzeTaskResponse {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'NOT_FOUND'
  result?: EmergingPostResponse
  error?: string
}

export function getEmergingAnalyzeTask(taskId: string): Promise<ApiResponse<EmergingAnalyzeTaskResponse>> {
  return get<EmergingAnalyzeTaskResponse>(`/post/emerging/analyze/tasks/${encodeURIComponent(taskId)}`)
}

/** 确认并创建新兴岗位 */
export function confirmEmergingPost(data: EmergingPostConfirmDTO): Promise<ApiResponse<number>> {
  return post<number>('/post/emerging/confirm', data, { timeout: 60000 })
}

/** 人工优化后重新分析 */
export function reanalyzeEmergingPost(data: EmergingPostRequest & { abilities?: JdAbilityItem[] }): Promise<ApiResponse<EmergingPostResponse>> {
  return post<EmergingPostResponse>('/post/emerging/reanalyze', data, { timeout: 120000 })
}

/** 发现新兴岗位 */
export function discoverEmergingPosts(limit: number = 10): Promise<ApiResponse<EmergingPostDiscovery[]>> {
  return get<EmergingPostDiscovery[]>('/post/emerging/discover', { limit })
}

/** 获取市场洞察 */
export function getMarketInsight(): Promise<ApiResponse<MarketInsight>> {
  return get<MarketInsight>('/post/emerging/market-insight')
}

export function importMarketJdTexts(jdTexts: string[], sourcePlatform: string): Promise<ApiResponse<MarketJdImportResult>> {
  return post<MarketJdImportResult>(`/post/evolution/market-jd/import-texts?sourcePlatform=${encodeURIComponent(sourcePlatform)}`, jdTexts)
}

/** 批量分析结果：治理 → Agent 提取能力 → Harness 准入的逐段计数 */
export interface MarketJdBatchAnalysisResult {
  batchNo: string
  totalCount: number
  skippedDuplicate: number
  skippedNoise: number
  governedCount: number
  extractedSuccess: number
  extractedFailed: number
  errors?: string[]
  autoAdmittedCount?: number
  harnessPassCount?: number
  harnessBlockedCount?: number
  reviewCandidateGroupCount?: number
  rejectedClaimCount?: number
}

export function analyzeMarketJdBatch(batchNo: string): Promise<ApiResponse<MarketJdBatchAnalysisResult>> {
  return post<MarketJdBatchAnalysisResult>(
    `/post/evolution/market-jd/analyze-batch?batchNo=${encodeURIComponent(batchNo)}`,
    undefined,
    { timeout: 120000 },
  )
}

/**
 * 单条 JD 解析结论（对应后端 `MarketJdImportService.SingleAnalysisResult`）。
 *
 * 后端刻意不返回「已分析」这类中文状态文案 —— 行级状态码 → 文案的映射在前端
 * `views/post/crawler-status.ts` 里是唯一来源，后端再回一份就会出现两个口径。
 */
export interface MarketJdSingleAnalysisResult {
  id: number
  /** 落库后的状态：0 待分析（基础设施失败，可重试）/ 1 已分析 / 2 跳过 */
  analysisStatus: number
  acceptedTagCount: number
  recommendedTagCount: number
  rejectedClaimCount: number
  /** true = 基础设施失败，未产生结果且不改动既有标签 */
  infraFailed: boolean
  /** 中文结论，含「为什么没有结果」，可直接展示 */
  message: string
}

/**
 * 解析单条市场 JD。
 *
 * 走完整「清洗 → 能力提取 → 准入」链路，与批次解析同口径，因此耗时与单条 AI 调用相当，
 * 超时按批次解析的 120s 设置。
 */
export function analyzeMarketJd(id: number): Promise<ApiResponse<MarketJdSingleAnalysisResult>> {
  return post<MarketJdSingleAnalysisResult>(
    `/post/evolution/market-jd/${id}/analyze`,
    undefined,
    { timeout: 120000 },
  )
}

/** 能力标签引用（后端已把 skill_tags 里的 ID 反解成名称） */
export interface MarketJdTagRef {
  tagId: number | null
  tagName: string | null
  tagCode: string | null
  tagCategory: string | null
  tagLevel: number | null
}

/**
 * AI 原始提取的能力项（对应后端 `MarketJdDetailResponse.AiTag`）。
 *
 * 与 `MarketJdTagRef` 的关键区别是「有没有进正式能力库」：
 * `MarketJdTagRef` 一定有 tagId（来自系统能力字典），而这里的是 AI 读 JD 得到的**建议**，
 * `matchStatus` 为 `NEW` 或 `null` 时它只有名称与证据，没有 tagId。
 */
export interface MarketJdAiTag {
  /** AI 建议的能力名称 */
  name?: string | null
  /** 所属技术栈，如 Java、Spring、MySQL */
  techStack?: string | null
  /** 能力分类：TECHNICAL / SOFT / BUSINESS */
  category?: string | null
  /** 能力类型：TECHNICAL / BUSINESS / SOFT / QUALIFICATION */
  abilityType?: string | null
  /** 建议最低要求等级：1–5 */
  level?: number | null
  /** 建议权重占比 */
  weight?: number | null
  /** 是否核心项：0 否 / 1 是 */
  isCore?: number | null
  /** 是否必填：0 否 / 1 是 */
  isRequired?: number | null
  /** AI 置信度：0–100 */
  confidence?: number | null
  /** 与系统标签库的匹配状态：MATCHED / SIMILAR / NEW；未匹配到任何标签时为 null */
  matchStatus?: string | null
  /** 匹配到的系统标签 ID（仅 MATCHED / SIMILAR 有值） */
  matchedTagId?: number | null
  /** 匹配到的系统标签名称（仅 MATCHED / SIMILAR 有值） */
  matchedTagName?: string | null
  /** 与系统标签的相似度（仅 SIMILAR 有值） */
  similarityScore?: number | null
  /** 支持该能力主张的 JD 原文片段 */
  evidence?: string | null
  /** AI 的推理依据 */
  reasoning?: string | null
}

/** 单条市场 JD 的解析结果详情（对应后端 `MarketJdDetailResponse`） */
export interface MarketJdDetail {
  id: number
  batchNo?: string
  postName?: string
  companyName?: string
  city?: string
  salaryRange?: string
  jobDescription?: string
  requirements?: string
  sourcePlatform?: string
  publishedTime?: string
  /** 0 待分析 / 1 已分析 / 2 跳过 */
  analysisStatus?: number
  isDuplicate?: number
  qualityScore?: number
  noiseScore?: number
  freshnessScore?: number
  matchedPostId?: number
  matchedPostName?: string
  ingestChannel?: string
  ingestChannelText?: string
  /** 已准入标签 ID 的原始 JSON，用于核对「标签被删除所以反解不出名字」 */
  rawSkillTags?: string
  acceptedTags?: MarketJdTagRef[]
  rawRecommendedSkillTags?: string
  recommendedTags?: MarketJdTagRef[]
  /** AI 原始提取结果的原始 JSON（未经标签库匹配与准入） */
  rawAiSkillTags?: string
  /**
   * AI 原始提取的能力项。
   *
   * 这是「解析跑过了、AI 到底读出了什么」的唯一依据：`acceptedTags` 为空而这里是满的，
   * 说明 AI 提取成功、只是没有一项通过标签匹配与准入门禁（市场 JD 场景下属常态）。
   */
  aiTags?: MarketJdAiTag[]
  createdTime?: string
}

/** 查看单条市场 JD 的解析结果（含 skill_tags 反解出的可读标签） */
export function getMarketJdDetail(id: number): Promise<ApiResponse<MarketJdDetail>> {
  return get<MarketJdDetail>(`/post/evolution/market-jd/${id}/detail`)
}

// ===================== 市场 JD 池（爬虫抓取结果浏览） =====================

/** 市场 JD 记录（对应后端 MarketJdResponse） */
export interface MarketJdItem {
  id: number
  batchNo?: string
  /** CRAWLER / MANUAL_UPLOAD / POST_IMPORT */
  ingestChannel?: string
  /** 通道中文名，后端已给出 */
  ingestChannelText?: string
  postName?: string
  companyName?: string
  city?: string
  salaryRange?: string
  jobDescription?: string
  requirements?: string
  skillTags?: string
  sourcePlatform?: string
  publishedTime?: string
  textHash?: string
  similarityGroupId?: string
  qualityScore?: number
  /** 1 = 重复 */
  isDuplicate?: number
  canonicalDocumentId?: number
  lastSeenTime?: string
  freshnessScore?: number
  noiseScore?: number
  companyDiversityKey?: string
  matchedPostId?: number
  /** 0 = 待分析 */
  analysisStatus?: number
  createdTime?: string
  /**
   * 爬虫推送的源站岗位 ID（对应后端 `MarketJdData.externalId`）。
   * 「查看推送原文」时用它核对「这条是不是我推的那条」—— 只有岗位名/公司名不足以区分。
   */
  externalId?: string
  /** 原始 JD 页面链接（爬虫推送体里的 sourceUrl） */
  sourceUrl?: string
}

export interface MarketJdPageResult {
  records: MarketJdItem[]
  total: number
  current: number
  size: number
  pages: number
}

/** 批次统计（对应后端 MarketJdImportService.BatchStatistics） */
export interface MarketJdBatchStatistics {
  batchNo: string
  totalCount: number
  duplicateCount: number
  analyzedCount: number
  matchedCount: number
}

export interface MarketJdPageQuery {
  current?: number
  size?: number
  postName?: string
  batchNo?: string
}

/** 分页查询市场 JD 池 */
export function pageMarketJds(query: MarketJdPageQuery = {}): Promise<ApiResponse<MarketJdPageResult>> {
  return get<MarketJdPageResult>('/post/evolution/market-jd/page', { ...query })
}

/** 查询指定批次的市场 JD 统计 */
export function getMarketJdBatchStatistics(batchNo: string): Promise<ApiResponse<MarketJdBatchStatistics>> {
  return get<MarketJdBatchStatistics>('/post/evolution/market-jd/statistics', { batchNo })
}

/** 对指定批次执行去重处理 */
export function deduplicateMarketJd(batchNo: string): Promise<ApiResponse<Record<string, unknown>>> {
  return post<Record<string, unknown>>(
    `/post/evolution/market-jd/deduplicate?batchNo=${encodeURIComponent(batchNo)}`,
    undefined,
    { timeout: 120000 },
  )
}

// ===================== 爬虫采集（采集命令队列） =====================
//
// 【2026-09-04 方向改造】原「主系统反向代理本地爬虫 8081」的 /trigger、/task/{id}、
// /task/{id}/retry-push 三个端点已按对接文档 §1.1 默认下线（返回 410）。
// 现方向为：管理端创建命令 → 本地爬虫每 10–30 秒轮询领取 → 本地执行 → 本地推送 JD。
// 主系统全程不主动访问本地电脑地址。

/** 采集来源平台代码，与本地爬虫 SOURCE 代码一致 */
export type CrawlerSource = 'jd' | 'remoteok' | 'themuse' | 'arbeitnow'

/** 数据源下拉选项（页面与抽屉共用，避免两处各写一份） */
export const CRAWLER_SOURCE_OPTIONS: Array<{ value: CrawlerSource; label: string }> = [
  { value: 'jd', label: '京东 / 招聘 JD' },
  { value: 'remoteok', label: 'RemoteOK' },
  { value: 'themuse', label: 'The Muse' },
  { value: 'arbeitnow', label: 'Arbeitnow' },
]

/** 创建采集命令请求体 */
export interface CrawlerCommandCreateRequest {
  sources: string[]
  keywords?: string[]
  cities?: string[]
  maxItems?: number
  /** 指定执行实例；留空表示任意在线爬虫实例均可领取 */
  agentId?: string
}

/** 采集命令视图（管理端列表 / 详情） */
export interface CrawlerCommandView {
  commandId: string
  commandType?: string
  agentId?: string | null
  sources?: string[]
  keywords?: string[]
  cities?: string[]
  maxItems?: number
  /** PENDING / DISPATCHED / RUNNING / SUCCEEDED / FAILED / EXPIRED / CANCELLED */
  status?: string
  /** 状态中文，后端已给出，前端直接用 */
  statusText?: string
  resultJson?: string | null
  errorMessage?: string | null
  createdTime?: string
  dispatchedTime?: string | null
  finishedTime?: string | null
  expireTime?: string | null
}

/** 本地爬虫实例在线状态 */
export interface CrawlerAgentView {
  agentId: string
  agentName?: string
  hostInfo?: string
  agentVersion?: string
  lastHeartbeatTime?: string
  lastCommandId?: string
  online: boolean
  secondsSinceHeartbeat?: number | null
}

/**
 * 批次登记视图（爬虫推送 / 人工上传 / 岗位导入共用一张统一台账）。
 *
 * 改造前本表只登记爬虫批次，人工上传的批次在列表里根本看不到；
 * 现在两类批次同表，靠 `ingestChannel` 区分来源。
 */
export interface CrawlerBatchLogView {
  id?: number
  batchNo: string
  /** CRAWLER / MANUAL_UPLOAD / POST_IMPORT */
  ingestChannel?: string
  /** 通道中文名，后端已给出，前端不自行造词 */
  ingestChannelText?: string
  sourcePlatform?: string
  requestIp?: string
  itemCount?: number
  imported?: number
  updated?: number
  duplicate?: number
  failed?: number
  httpStatus?: number
  /** OK / PARTIAL_FAILED / REJECTED / UNAUTHORIZED / HISTORICAL */
  resultStatus?: string
  /** NOT_TRIGGERED / QUEUED / RUNNING / SUCCEEDED / FAILED / SKIPPED */
  analysisState?: string
  analysisNote?: string
  costMillis?: number
  createdTime?: string
  resultStatusText?: string
  analysisStateText?: string
}

/** 批次删除结果（各表实际删除行数） */
export interface CrawlerBatchDeleteResult {
  batchNo: string
  marketJdRows: number
  versionSnapshots: number
  batchLogs: number
}

/** 单条 JD 删除结果 */
export interface MarketJdDeleteResult {
  id: number
  marketJdRows: number
  versionSnapshots: number
}

/** 批量 JD 删除结果 */
export interface MarketJdBatchDeleteResult {
  /** 请求删除的 id 数（后端已去重） */
  requested: number
  marketJdRows: number
  versionSnapshots: number
  /** 库中不存在的 id，前端据此提示「有 N 条已被他人删除」 */
  missingIds: number[]
}

/** 采集通道可用性 */
export interface CrawlerAvailabilityView {
  /** 命令队列是否接入（已配置 api-key） */
  queueAvailable: boolean
  /** 在线本地爬虫实例数 */
  agentsOnline: number
  lastHeartbeatTime?: string | null
  /** 过渡期反向代理开关；true 表示主系统仍可能反向访问本地地址 */
  legacyProxyEnabled: boolean
}

/** 采集通道可用性：判断「下发采集命令」是否可点、是否有在线爬虫 */
export function getCrawlerAvailability(): Promise<ApiResponse<CrawlerAvailabilityView>> {
  return get<CrawlerAvailabilityView>('/post/evolution/crawler/availability')
}

/** 创建采集命令（PENDING），等待本地爬虫轮询领取；返回 commandId 供轮询 */
export function createCrawlerCommand(
  data: CrawlerCommandCreateRequest,
): Promise<ApiResponse<CrawlerCommandView>> {
  return post<CrawlerCommandView>('/post/evolution/crawler/commands', data, { timeout: 30000 })
}

/** 最近采集命令（倒序），用于列表轮询 */
export function listCrawlerCommands(limit = 20): Promise<ApiResponse<CrawlerCommandView[]>> {
  return get<CrawlerCommandView[]>('/post/evolution/crawler/commands', { limit })
}

/** 查询单条命令状态 */
export function getCrawlerCommand(commandId: string): Promise<ApiResponse<CrawlerCommandView>> {
  return get<CrawlerCommandView>(`/post/evolution/crawler/commands/${encodeURIComponent(commandId)}`)
}

/** 取消尚未被领取的命令（仅 PENDING 可取消） */
export function cancelCrawlerCommand(commandId: string): Promise<ApiResponse<CrawlerCommandView>> {
  return post<CrawlerCommandView>(`/post/evolution/crawler/commands/${encodeURIComponent(commandId)}/cancel`)
}

/** 本地爬虫实例在线状态 */
export function listCrawlerAgents(): Promise<ApiResponse<CrawlerAgentView[]>> {
  return get<CrawlerAgentView[]>('/post/evolution/crawler/agents')
}

/**
 * 最近接收批次（入库统计 + 解析状态；爬虫与人工导入的统一台账）。
 *
 * @param channel 入库通道筛选：CRAWLER / MANUAL_UPLOAD / POST_IMPORT；留空为全部。
 *                筛选走服务端——列表本身有条数上限，前端本地过滤会在
 *                「最近 N 条恰好都不是该来源」时给出假的空结果。
 */
export function listCrawlerBatches(
  limit = 20,
  channel = '',
): Promise<ApiResponse<CrawlerBatchLogView[]>> {
  const params: Record<string, unknown> = { limit }
  if (channel) {
    params.channel = channel
  }
  return get<CrawlerBatchLogView[]>('/post/evolution/crawler/batches', params)
}

/**
 * 删除某批次：市场 JD 数据 + 历史版本快照 + 批次登记行一并删除。
 *
 * 爬虫推送的批次与人工上传的批次走同一条路径 —— 后端的登记表是统一的，
 * 前端不需要按来源分支调用不同接口。
 */
export function deleteCrawlerBatch(batchNo: string): Promise<ApiResponse<CrawlerBatchDeleteResult>> {
  return del<CrawlerBatchDeleteResult>(`/post/evolution/crawler/batches/${encodeURIComponent(batchNo)}`)
}

/**
 * 删除单条市场 JD（数据治理入口）。
 *
 * 与「删除批次」的区别只在范围：批次删除适合整批推错，单条删除适合池子里混了几条脏数据
 * —— 用批次删会连同批次的正常数据一起删掉。
 */
export function deleteMarketJd(id: number): Promise<ApiResponse<MarketJdDeleteResult>> {
  return del<MarketJdDeleteResult>(`/post/evolution/market-jd/${id}`)
}

/**
 * 批量删除市场 JD（前端勾选后提交）。
 *
 * 去重与级联清理都在后端做，前端只管把 id 原样提交；重复 id 不会造成重复计数。
 */
export function batchDeleteMarketJds(ids: number[]): Promise<ApiResponse<MarketJdBatchDeleteResult>> {
  return post<MarketJdBatchDeleteResult>('/post/evolution/market-jd/batch-delete', ids)
}

/** 重新解析某批次（解析失败或跳过后的补偿入口） */
export function reanalyzeCrawlerBatch(batchNo: string): Promise<ApiResponse<CrawlerBatchLogView>> {
  return post<CrawlerBatchLogView>(
    `/post/evolution/crawler/batches/${encodeURIComponent(batchNo)}/reanalyze`,
    undefined,
    { timeout: 120000 },
  )
}

// ===================== 推送批次：自动解析开关 / 去重重算 =====================

/**
 * 推送批次的「自动解析」开关状态（对应后端 `CrawlerAutoAnalyzeView`）。
 *
 * 口径：**默认关闭** —— 爬虫推来的批次只入库、不解析，由人按批次决定何时解析。
 * 页面上的开关是**运行期临时覆盖**，不落库，服务重启后回到配置默认值（`configuredDefault`）。
 * 这也是为什么必须有 `overridden` 字段：用户需要知道「我现在看到的这个值是我刚点的，还是配置里的」。
 */
export interface CrawlerAutoAnalyzeView {
  /** 当前生效值（覆盖优先，否则配置默认） */
  enabled: boolean
  /** 配置默认值（application.yml / 环境变量） */
  configuredDefault: boolean
  /** 是否处于「页面临时覆盖」状态 */
  overridden: boolean
  /** 面向用户的中文说明（含「重启会回落」的提示），直接展示，不要前端再造词 */
  message: string
}

/** 读取自动解析开关当前状态 */
export function getCrawlerAutoAnalyze(): Promise<ApiResponse<CrawlerAutoAnalyzeView>> {
  return get<CrawlerAutoAnalyzeView>('/post/evolution/crawler/auto-analyze')
}

/**
 * 设置自动解析开关（运行期临时覆盖，不落库）。
 *
 * `enabled` 必须是 boolean：后端 DTO 用包装类型 + `@NotNull`，漏传会得到 400 而不是静默变成 false。
 */
export function updateCrawlerAutoAnalyze(enabled: boolean): Promise<ApiResponse<CrawlerAutoAnalyzeView>> {
  return put<CrawlerAutoAnalyzeView>('/post/evolution/crawler/auto-analyze', { enabled })
}

/** 按批次「重算去重」的结果（对应后端 `MarketJdImportService.DedupeResetResult`） */
export interface CrawlerDedupeResetResult {
  batchNo: string
  /** 扫描到的行数 */
  scannedRows: number
  /** 重算了去重键的行数（仅爬虫来源需要重算，其余来源的键口径不同） */
  hashRecomputed: number
  /** 按正确口径判出的重复行数；为 0 说明这批本来就没有重复 */
  duplicateHits: number
}

/**
 * 重新判定某批次的重复情况。
 *
 * 用于修复历史误判：早期治理流程会把去重键改写成「只按正文」，导致不同岗位/不同公司
 * 只要正文一样就被标成「重复跳过」，而重复判定又只处理未被标记的行 —— 误判会永久固化。
 * 本接口按当前正确口径重算去重键并重判；**真重复仍会被重新标出**（看 `duplicateHits`）。
 */
export function resetCrawlerBatchDedupe(
  batchNo: string,
): Promise<ApiResponse<CrawlerDedupeResetResult>> {
  return post<CrawlerDedupeResetResult>(
    `/post/evolution/crawler/batches/${encodeURIComponent(batchNo)}/reset-dedupe`,
    undefined,
    { timeout: 120000 },
  )
}

