<script setup lang="ts">
/**
 * 市场 JD 采集（爬虫配合功能）
 *
 * 【2026-09-04 方向改造】对齐爬虫对接文档 §1.1 与 §9：
 *   ① 主系统**不再反向访问本地电脑 8081**（旧的 /crawler/trigger 已默认返回 410）；
 *   ② 抓取改由「管理端下发采集命令 → 本地爬虫每 10–30 秒轮询领取」驱动；
 *   ③ 爬虫抓到的 JD 由**本地主动推送**到 `POST /api/internal/market-jd/crawler-import`，
 *      入池后自动进入解析链路（治理 → 去重 → 能力提取 → 准入）。
 *
 * 因此本页面对应的三段闭环变成：
 *   下发命令（看命令状态） → 接收批次（看入库/解析结果） → 市场 JD 池（浏览 + 按批次解析）
 *
 * 【2026-09-04 补充】市场 JD 池补上「单条解析」与「结果查看」：
 *   此前解析只有批次级入口，而解析结果（skill_tags / recommended_skill_tags）**没有任何展示位置**，
 *   池表格只渲染一个状态标签 —— 用户点完「解析该批次」只能看到「待分析/已分析」，看不到产出。
 *   现在行操作有「解析」（单条，复用同一条后端链路）与「结果」（抽屉里按分类列出反解后的能力标签）。
 *
 * 权限沿用 POST:EVOLUTION（命令、批次、市场 JD 池在后端同属 `/api/post/evolution/**`），
 * 不新造权限码。
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, MagicStick, Promotion, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import {
  CRAWLER_SOURCE_OPTIONS,
  analyzeMarketJd,
  analyzeMarketJdBatch,
  batchDeleteMarketJds,
  cancelCrawlerCommand,
  createCrawlerCommand,
  deduplicateMarketJd,
  deleteCrawlerBatch,
  deleteMarketJd,
  getCrawlerAutoAnalyze,
  getCrawlerAvailability,
  getMarketJdBatchStatistics,
  getMarketJdDetail,
  listCrawlerAgents,
  listCrawlerBatches,
  listCrawlerCommands,
  pageMarketJds,
  reanalyzeCrawlerBatch,
  resetCrawlerBatchDedupe,
  updateCrawlerAutoAnalyze,
} from '@/api/emerging-post'
import type {
  CrawlerAgentView,
  CrawlerAutoAnalyzeView,
  CrawlerAvailabilityView,
  CrawlerBatchLogView,
  CrawlerCommandView,
  MarketJdAiTag,
  MarketJdBatchStatistics,
  MarketJdDetail,
  MarketJdItem,
} from '@/api/emerging-post'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { CRAWLER_COMMAND_QUEUE_ENABLED } from '@/config/feature-flags'
import {
  BATCH_CHANNEL_OPTIONS,
  analysisTagType,
  batchResultTagType,
  channelLabel,
  channelTagType,
  commandTagType,
  describeBatchDelete,
  describeBatchJdDelete,
  describeSingleJdDelete,
  isCommandActive,
  isAnalysisActive,
  marketJdAnalysisStatusMeta,
  sourceLabel,
  sourceLabels,
  summarizeCommands,
} from '../crawler-status'
import {
  aiTagDisplayName,
  aiTagMatchLabel,
  aiTagSummaryText,
  aiTagTone,
  emptyTagHint,
  groupTagsByCategory,
  singleAnalysisNotice,
  singleAnalyzeBlockReason,
  summarizeAiTags,
  tagCategoryLabel,
  tagLevelLabel,
  tagRefLabel,
} from '../market-jd-detail'
import {
  autoAnalyzeDetail,
  autoAnalyzeOverrideNote,
  autoAnalyzeSummary,
  dedupeResetConfirmText,
  describeAutoAnalyzeChange,
  describeDedupeReset,
  emptyRawHint,
  rawDrawerTitle,
  renderRawJdText,
} from '../crawler-batch-ops'

/**
 * 「采集命令队列」入口开关。
 *
 * 本地爬虫尚未实现轮询领命令，下发命令只会停在「待爬虫领取」，因此整块采集下发 UI 默认隐藏
 * （实现代码保留，开关置 true 即恢复）。详见 `@/config/feature-flags`。
 */
const commandQueueEnabled = CRAWLER_COMMAND_QUEUE_ENABLED

const POLL_INTERVAL_MS = 5000

// ---------- 采集通道状态 ----------
const availability = ref<CrawlerAvailabilityView | null>(null)
const availabilityChecked = ref(false)
const agents = ref<CrawlerAgentView[]>([])
const commands = ref<CrawlerCommandView[]>([])
const batches = ref<CrawlerBatchLogView[]>([])
/**
 * 批次来源通道筛选（空串 = 全部）。
 *
 * 筛选走服务端：批次列表有条数上限，本地过滤会在「最近 N 条恰好都不是该来源」时
 * 给出一个假的空列表，让用户以为人工批次根本没进来。
 */
const batchChannel = ref('')
const channelError = ref('')

const onlineAgents = computed(() => agents.value.filter(item => item.online))
const commandSummary = computed(() => summarizeCommands(commands.value))

/** 有命令未结束时保持轮询；采集命令入口未开放时永不轮询 */
const hasActiveCommand = computed(
  () => commandQueueEnabled && commands.value.some(item => isCommandActive(item.status)),
)

/**
 * 有批次正在解析时也要轮询。
 *
 * 修 bug 前这里只有 `hasActiveCommand`，而 `commandQueueEnabled` 恒为 false
 * （采集命令入口默认关闭，见 `@/config/feature-flags`），于是**永远不会轮询**：
 * 爬虫自己推完数据、解析状态从「解析中」变「成功」时页面一无所知，
 * 标签一直停在「解析中」，用户必须手动刷新才看到结果。
 */
const hasPendingAnalysis = computed(
  () => batches.value.some(item => isAnalysisActive(item.analysisState)),
)

/** 页面是否需要保持轮询 */
const shouldPoll = computed(() => hasActiveCommand.value || hasPendingAnalysis.value)

async function loadChannels() {
  channelError.value = ''
  try {
    // 接收批次是「统一批次台账」，始终可用，与采集命令入口是否存在无关。
    const batchesRes = await listCrawlerBatches(20, batchChannel.value)
    batches.value = batchesRes.data ?? []
    if (!commandQueueEnabled) return
    const [availabilityRes, agentsRes, commandsRes] = await Promise.all([
      getCrawlerAvailability(),
      listCrawlerAgents(),
      listCrawlerCommands(20),
    ])
    availability.value = availabilityRes.data ?? null
    agents.value = agentsRes.data ?? []
    commands.value = commandsRes.data ?? []
  } catch (error) {
    channelError.value = resolveApiErrorMessage(error, '加载采集通道状态失败')
  } finally {
    availabilityChecked.value = true
  }
}

async function refreshChannels() {
  // 轮询路径刻意不复用 loadChannels：轮询失败不应把整页弹成错误态
  try {
    const batchesRes = await listCrawlerBatches(20, batchChannel.value)
    batches.value = batchesRes.data ?? batches.value
    if (!commandQueueEnabled) return
    const [availabilityRes, commandsRes, agentsRes] = await Promise.all([
      getCrawlerAvailability(),
      listCrawlerCommands(20),
      listCrawlerAgents(),
    ])
    availability.value = availabilityRes.data ?? availability.value
    commands.value = commandsRes.data ?? commands.value
    agents.value = agentsRes.data ?? agents.value
  } catch {
    // 静默：网络抖动时不打断用户
  }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    void refreshChannels()
  }, POLL_INTERVAL_MS)
}

watch(shouldPoll, active => {
  if (active) {
    startPolling()
  } else {
    stopPolling()
    // 命令结束 / 批次解析收尾：立刻同步一次，把爬虫随后推送的批次与最新状态刷出来
    void refreshChannels()
  }
})

// ---------- 下发采集命令 ----------
const form = reactive({
  sources: ['jd'] as string[],
  keywordsText: '',
  citiesText: '',
  maxItems: 50,
})

const submitting = ref(false)
const submitError = ref('')

const keywords = computed(() =>
  form.keywordsText
    .split(/[\n,，;；]/)
    .map(item => item.trim())
    .filter(Boolean),
)
const cities = computed(() =>
  form.citiesText
    .split(/[\n,，;；]/)
    .map(item => item.trim())
    .filter(Boolean),
)

const canSubmit = computed(() => Boolean(availability.value?.queueAvailable))

async function handleSubmitCommand() {
  submitError.value = ''
  if (!form.sources.length) {
    submitError.value = '请至少选择一个数据源。'
    return
  }
  if (!keywords.value.length) {
    submitError.value = '请填写至少一个搜索关键词，每行一个。'
    return
  }
  submitting.value = true
  try {
    const res = await createCrawlerCommand({
      sources: form.sources,
      keywords: keywords.value,
      cities: cities.value,
      maxItems: form.maxItems,
    })
    ElMessage.success(
      onlineAgents.value.length
        ? '采集命令已下发，本地爬虫轮询到后会自动开始抓取'
        : '采集命令已排队：当前没有在线爬虫，启动本地爬虫后会自动领取',
    )
    await refreshChannels()
    if (res.data?.commandId) startPolling()
  } catch (error) {
    submitError.value = resolveApiErrorMessage(error, '下发采集命令失败')
  } finally {
    submitting.value = false
  }
}

const cancellingId = ref('')

async function handleCancelCommand(command: CrawlerCommandView) {
  cancellingId.value = command.commandId
  try {
    await cancelCrawlerCommand(command.commandId)
    ElMessage.success('命令已取消')
    await refreshChannels()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '取消命令失败'))
  } finally {
    cancellingId.value = ''
  }
}

// ---------- 接收批次 ----------
const reanalyzingBatch = ref('')

async function handleReanalyzeBatch(row: CrawlerBatchLogView) {
  reanalyzingBatch.value = row.batchNo
  try {
    const res = await reanalyzeCrawlerBatch(row.batchNo)
    ElMessage.success(res.data?.analysisNote || '已触发重新解析')
    await refreshChannels()
    if (poolQuery.batchNo === row.batchNo) {
      await Promise.all([loadPool(), loadStatistics()])
    }
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '重新解析失败'))
  } finally {
    reanalyzingBatch.value = ''
  }
}

const poolSectionRef = ref<HTMLElement | null>(null)

function focusBatch(batchNo: string) {
  poolQuery.batchNo = batchNo
  poolQuery.current = 1
  void Promise.all([loadPool(), loadStatistics()])
  // 池子在批次台账的下方：只改筛选条件，用户很可能没意识到已经跳过去了，
  // 会得出「点查看没反应、只能整批重解析」的结论。
  void nextTick(() => {
    poolSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

/** 切换来源通道筛选：必须重新向后端取数，不能在前端过滤。 */
function handleChannelChange() {
  void refreshChannels()
}

const deletingBatch = ref('')

async function handleDeleteBatch(row: CrawlerBatchLogView) {
  const channelName = row.ingestChannelText || channelLabel(row.ingestChannel)
  try {
    await ElMessageBox.confirm(
      `确认删除「${channelName}」批次 ${row.batchNo}？`
      + '该批次的市场 JD 数据、历史版本与批次登记会一并删除，且无法恢复。',
      '删除批次',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消：这是正常路径，不弹错误
    return
  }

  deletingBatch.value = row.batchNo
  try {
    const res = await deleteCrawlerBatch(row.batchNo)
    ElMessage.success(describeBatchDelete(res.data))
    // 正在浏览的批次被删了：清掉筛选，否则会停在一个已不存在的批次上反复报错
    if (poolQuery.batchNo === row.batchNo) {
      poolQuery.batchNo = ''
      poolQuery.current = 1
      statistics.value = null
    }
    await Promise.all([refreshChannels(), loadPool()])
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '删除批次失败'))
  } finally {
    deletingBatch.value = ''
  }
}

// ---------- 市场 JD 池 ----------
const poolQuery = reactive({
  current: 1,
  size: 20,
  postName: '',
  batchNo: '',
})
const poolLoading = ref(false)
const poolRecords = ref<MarketJdItem[]>([])
const poolTotal = ref(0)
const poolError = ref('')

/** 勾选中的 JD（批量删除用）。表格开了 reserve-selection，翻页后仍保留已选项。 */
const poolSelection = ref<MarketJdItem[]>([])
const poolDeleting = ref(false)

const statistics = ref<MarketJdBatchStatistics | null>(null)
const statsLoading = ref(false)

const analyzing = ref(false)
const deduplicating = ref(false)

/*
 * 行级分析状态的文案与颜色一律走 `crawler-status.ts` 的 `marketJdAnalysisStatusMeta`。
 *
 * 这里原本有一份页面内的 `analysisStatusLabel/Type`，用的是 Excel 导入（PostImportItem）
 * 那套编码 1=分析中 / 2=成功 / 3=失败，而市场 JD（MarketJdData）的真实口径是
 * 1=已分析 / 2=跳过 —— 于是每条分析成功的 JD 都被显示成「分析中」，
 * 用户点完解析看到的就是「一直卡在解析中」。口径已收进共享纯函数并加了测试。
 */
const jdAnalysisMeta = (row: { analysisStatus?: number; isDuplicate?: number }) =>
  marketJdAnalysisStatusMeta(row.analysisStatus, row.isDuplicate)

async function loadPool() {
  poolLoading.value = true
  poolError.value = ''
  try {
    const res = await pageMarketJds({
      current: poolQuery.current,
      size: poolQuery.size,
      postName: poolQuery.postName || undefined,
      batchNo: poolQuery.batchNo || undefined,
    })
    poolRecords.value = res.data?.records ?? []
    poolTotal.value = res.data?.total ?? 0
  } catch (error) {
    poolRecords.value = []
    poolTotal.value = 0
    poolError.value = resolveApiErrorMessage(error, '加载市场 JD 池失败')
  } finally {
    poolLoading.value = false
  }
}

async function loadStatistics() {
  if (!poolQuery.batchNo) {
    statistics.value = null
    return
  }
  statsLoading.value = true
  try {
    const res = await getMarketJdBatchStatistics(poolQuery.batchNo)
    statistics.value = res.data ?? null
  } catch {
    // 统计是「附加信息」，失败不打断浏览；表格仍可用
    statistics.value = null
  } finally {
    statsLoading.value = false
  }
}

async function refreshPool() {
  poolQuery.current = 1
  await Promise.all([loadPool(), loadStatistics()])
}

// ---------- JD 级治理（单条 / 批量删除） ----------

function handlePoolSelectionChange(rows: MarketJdItem[]) {
  poolSelection.value = rows
}

/**
 * 删完最后一页的最后一条时回退一页。
 * 否则会停在一个空的页码上，用户会以为数据被清空了。
 */
async function reloadAfterDelete() {
  if (poolRecords.value.length === 0 && poolQuery.current > 1) {
    poolQuery.current -= 1
  }
  poolSelection.value = []
  await Promise.all([loadPool(), loadStatistics()])
}

async function handleDeleteJd(row: MarketJdItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除 JD「${row.postName || '未命名'}」${row.companyName ? `（${row.companyName}）` : ''}？`
      + '该条 JD 及其历史版本快照会一并删除，且无法恢复。',
      '删除 JD',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消：正常路径，不弹错误
    return
  }

  poolDeleting.value = true
  try {
    const res = await deleteMarketJd(row.id)
    ElMessage.success(describeSingleJdDelete(res.data))
    await reloadAfterDelete()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '删除 JD 失败'))
  } finally {
    poolDeleting.value = false
  }
}

async function handleBatchDeleteJds() {
  const ids = poolSelection.value.map(row => row.id).filter((id): id is number => typeof id === 'number')
  if (!ids.length) {
    ElMessage.warning('请先勾选要删除的 JD')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${ids.length} 条市场 JD？`
      + '这些 JD 及其历史版本快照会一并删除，且无法恢复。',
      '批量删除 JD',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  poolDeleting.value = true
  try {
    const res = await batchDeleteMarketJds(ids)
    ElMessage.success(describeBatchJdDelete(res.data))
    await reloadAfterDelete()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '批量删除 JD 失败'))
  } finally {
    poolDeleting.value = false
  }
}

function resetFilters() {
  poolQuery.postName = ''
  poolQuery.batchNo = ''
  poolQuery.current = 1
  statistics.value = null
  void loadPool()
}

function handlePageChange(page: number) {
  poolQuery.current = page
  void loadPool()
}

function handleSizeChange(size: number) {
  poolQuery.size = size
  poolQuery.current = 1
  void loadPool()
}

async function handleAnalyzeBatch() {
  if (!poolQuery.batchNo) {
    ElMessage.warning('请先填写或选择一个批次号')
    return
  }
  analyzing.value = true
  try {
    const res = await analyzeMarketJdBatch(poolQuery.batchNo)
    const data = res.data
    if (data) {
      ElMessage.success(
        `批次解析已提交：治理 ${data.governedCount ?? 0} 条，`
        + `提取成功 ${data.extractedSuccess ?? 0} 条，失败 ${data.extractedFailed ?? 0} 条`,
      )
    } else {
      ElMessage.success('批次解析已提交')
    }
    await Promise.all([loadPool(), loadStatistics(), refreshChannels()])
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '批次解析失败，请稍后重试'))
  } finally {
    analyzing.value = false
  }
}

async function handleDeduplicate() {
  if (!poolQuery.batchNo) {
    ElMessage.warning('请先填写或选择一个批次号')
    return
  }
  deduplicating.value = true
  try {
    await deduplicateMarketJd(poolQuery.batchNo)
    ElMessage.success('去重处理已完成')
    await Promise.all([loadPool(), loadStatistics()])
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '去重处理失败，请稍后重试'))
  } finally {
    deduplicating.value = false
  }
}

// ---------- 单条解析 ----------
//
// 批次解析适合「整批一起跑」，但池子里常常只有几条需要重跑（或想先跑一条看看效果），
// 整批重跑既慢又会把已成功的行一起再烧一遍 AI。单条解析与批次解析走同一条后端链路，
// 差别只在范围。
//
// ⚠️ 单条解析**不会新建能力标签**：新能力需 ≥3 条 JD 且 ≥2 家公司互相印证
// （后端 `new-ability-min-jd-count/company-count`）。这个限制由后端放在返回 message 里，
// 页面必须原样展示 —— 否则用户会把设计约束读成「解析坏了」。

/** 单条解析按钮的入参形状：池行与详情对象都满足，避免两处各写一套类型 */
type AnalysableJdRow = {
  id: number
  postName?: string
  analysisStatus?: number
  isDuplicate?: number
}

const analyzingJdId = ref<number | null>(null)

async function handleAnalyzeJd(row: AnalysableJdRow) {
  const blocked = singleAnalyzeBlockReason(row)
  if (blocked) {
    ElMessage.warning(blocked)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认单独解析「${row.postName || '未命名岗位'}」这一条 JD？`
      + '将走一遍完整的「清洗 → 能力提取 → 准入」，耗时与一次 AI 调用相当。',
      '解析单条 JD',
      { type: 'info', confirmButtonText: '开始解析', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消：正常路径，不弹错误
    return
  }

  analyzingJdId.value = row.id
  try {
    const res = await analyzeMarketJd(row.id)
    const notice = singleAnalysisNotice(res.data, '解析已提交')
    // 结论文案较长（含「为什么没有结果」），必须给足展示时间并允许手动关闭
    ElMessage({ type: notice.type, message: notice.text, duration: 9000, showClose: true })
    await Promise.all([loadPool(), loadStatistics()])
    if (detailVisible.value && detail.value?.id === row.id) {
      await loadDetail(row.id)
    }
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '单条解析失败，请稍后重试'))
  } finally {
    analyzingJdId.value = null
  }
}

// ---------- 单条解析结果查看 ----------
//
// 明细一律用抽屉 + 卡片列表，绝不在表格行里嵌第二张表（版式铁律：嵌套表格会让
// 祖先容器承担横向滚动，固定列随之错位）。

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<MarketJdDetail | null>(null)

const detailTagGroups = computed(() => groupTagsByCategory(detail.value?.acceptedTags))
const detailCandidateTags = computed(() => detail.value?.recommendedTags ?? [])
/**
 * AI 原始提取结果。
 *
 * 「已准入能力」为空而这里非空是最常见的情形（市场 JD 的表述往往对不上既有正式标签），
 * 因此这一块是用户回答「解析到底跑出什么了」的唯一依据，不能因为准入为空就一起收起。
 */
const detailAiTags = computed<MarketJdAiTag[]>(() => detail.value?.aiTags ?? [])
const detailAiSummary = computed(() => aiTagSummaryText(summarizeAiTags(detail.value?.aiTags)))
const detailEmptyHint = computed(() => {
  if (!detail.value) return null
  return emptyTagHint({
    analysisStatus: detail.value.analysisStatus,
    isDuplicate: detail.value.isDuplicate,
    acceptedCount: detail.value.acceptedTags?.length ?? 0,
    candidateCount: detail.value.recommendedTags?.length ?? 0,
    aiTagCount: detail.value.aiTags?.length ?? 0,
  })
})

async function loadDetail(id: number) {
  detailLoading.value = true
  detailError.value = ''
  try {
    const res = await getMarketJdDetail(id)
    detail.value = res.data ?? null
  } catch (error) {
    detail.value = null
    detailError.value = resolveApiErrorMessage(error, '加载解析结果失败')
  } finally {
    detailLoading.value = false
  }
}

async function openJdResult(row: MarketJdItem) {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  await loadDetail(row.id)
}

// ---------- 推送批次的自动解析开关 ----------
//
// 2026-09-04 起，**推送的批次默认只入库、不解析**（此前是入池即自动解析）。
// 页面上的开关是运行期临时覆盖（不落库），所以必须同时展示「配置默认值」与「重启会回落」，
// 否则用户会把它当成一个持久化设置。
//
// 文案口径全部在 `../crawler-batch-ops.ts`（可测），本文件只负责取数与调用。

const autoAnalyze = ref<CrawlerAutoAnalyzeView | null>(null)
const autoAnalyzeLoading = ref(false)
const autoAnalyzeSaving = ref(false)
const autoAnalyzeError = ref('')
const autoAnalyzeEnabled = computed(() => autoAnalyze.value?.enabled === true)
const autoAnalyzeHint = computed(() => autoAnalyzeDetail(autoAnalyze.value))
const autoAnalyzeNote = computed(() => autoAnalyzeOverrideNote(autoAnalyze.value))

async function loadAutoAnalyze() {
  autoAnalyzeLoading.value = true
  autoAnalyzeError.value = ''
  try {
    const res = await getCrawlerAutoAnalyze()
    autoAnalyze.value = res.data ?? null
  } catch (error) {
    autoAnalyze.value = null
    autoAnalyzeError.value = resolveApiErrorMessage(error, '加载自动解析开关失败')
  } finally {
    autoAnalyzeLoading.value = false
  }
}

/**
 * 切换自动解析开关。
 *
 * 刻意用 `:model-value` + `@change` 而不是 `v-model`：调用失败时开关必须停回原值，
 * 用 v-model 会先本地翻转再请求，失败后控件显示的状态与后端不一致 —— 用户会以为设置生效了。
 */
async function handleAutoAnalyzeChange(value: boolean | string | number) {
  const next = Boolean(value)
  if (autoAnalyzeSaving.value || next === autoAnalyzeEnabled.value) return
  autoAnalyzeSaving.value = true
  autoAnalyzeError.value = ''
  try {
    const res = await updateCrawlerAutoAnalyze(next)
    autoAnalyze.value = res.data ?? autoAnalyze.value
    const notice = describeAutoAnalyzeChange(res.data)
    ElMessage({ type: notice.type, message: notice.text, duration: 6000, showClose: true })
  } catch (error) {
    autoAnalyzeError.value = resolveApiErrorMessage(error, '自动解析设置失败')
    // 失败后重新拉一次真实状态，避免控件停在一个「看起来改了但没改」的位置
    await loadAutoAnalyze()
  } finally {
    autoAnalyzeSaving.value = false
  }
}

// ---------- 重算去重（修复历史误判） ----------
//
// 「批次去重」是常规幂等去重；「重算去重」是修复入口：早期治理流程会把去重键改写成
// 「只按正文」，导致不同岗位/不同公司只要正文一样就被标成「重复跳过」，而重复判定只处理
// 未被标记的行 —— 误判会永久固化。本动作按当前正确口径重新判定，真重复仍会被标出。

const resettingDedupe = ref(false)

async function handleResetDedupe() {
  if (!poolQuery.batchNo) {
    ElMessage.warning('请先填写或选择一个批次号')
    return
  }
  try {
    await ElMessageBox.confirm(
      dedupeResetConfirmText(poolQuery.batchNo),
      '重算去重',
      { type: 'warning', confirmButtonText: '开始重算', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消：正常路径
    return
  }

  resettingDedupe.value = true
  try {
    const res = await resetCrawlerBatchDedupe(poolQuery.batchNo)
    const notice = describeDedupeReset(res.data)
    // 结论较长且是用户最关心的信息（「是不是真的重复」），给足展示时间
    ElMessage({ type: notice.type, message: notice.text, duration: 9000, showClose: true })
    await Promise.all([loadPool(), loadStatistics(), refreshChannels()])
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '重算去重失败，请稍后重试'))
  } finally {
    resettingDedupe.value = false
  }
}

// ---------- 推送原文（按批次查看爬虫推来的原始字段） ----------
//
// 「解析结果」抽屉回答的是「AI 读出了什么」；这里回答的是「爬虫到底推了什么」。
// 两者必须分开：核对推送内容时如果只有解析结果，字段被清洗/改写后就无从对账。
// 明细一律用抽屉 + 卡片列表（不在表格行里嵌第二张表）。

const rawVisible = ref(false)
const rawBatchNo = ref('')
const rawChannelText = ref('')
const rawLoading = ref(false)
const rawError = ref('')
const rawRecords = ref<MarketJdItem[]>([])
const rawTotal = ref(0)
const rawQuery = reactive({ current: 1, size: 10 })

const rawTitle = computed(() => rawDrawerTitle(rawBatchNo.value, rawChannelText.value))
const rawEmptyText = computed(() => emptyRawHint(rawLoading.value, rawTotal.value))

async function loadRaw() {
  rawLoading.value = true
  rawError.value = ''
  try {
    // 刻意不带 postName 过滤：原文核对要看到整批推来的全部数据
    const res = await pageMarketJds({
      current: rawQuery.current,
      size: rawQuery.size,
      batchNo: rawBatchNo.value,
    })
    rawRecords.value = res.data?.records ?? []
    rawTotal.value = res.data?.total ?? 0
  } catch (error) {
    rawRecords.value = []
    rawTotal.value = 0
    rawError.value = resolveApiErrorMessage(error, '加载推送原文失败')
  } finally {
    rawLoading.value = false
  }
}

async function openRaw(row: CrawlerBatchLogView) {
  rawBatchNo.value = row.batchNo
  rawChannelText.value = row.ingestChannelText || channelLabel(row.ingestChannel)
  rawQuery.current = 1
  rawVisible.value = true
  await loadRaw()
}

function handleRawPageChange(page: number) {
  rawQuery.current = page
  void loadRaw()
}

function handleRawSizeChange(size: number) {
  rawQuery.size = size
  rawQuery.current = 1
  void loadRaw()
}

function resetRaw() {
  rawRecords.value = []
  rawTotal.value = 0
  rawError.value = ''
  rawQuery.current = 1
  rawBatchNo.value = ''
  rawChannelText.value = ''
}

/** 复制单条原文为纯文本（内容与页面展示一致，统一由纯函数渲染） */
async function copyRawJd(row: MarketJdItem) {
  const text = renderRawJdText(row)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('该条推送原文已复制为纯文本')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

// ---------- 生命周期 ----------
onMounted(async () => {
  await Promise.all([loadChannels(), refreshPool(), loadAutoAnalyze()])
})

onUnmounted(stopPolling)
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Crawler &amp; Market JD</div>
        <h1 class="page-hero__title">市场 JD 采集</h1>
        <p class="page-hero__desc">
          主系统不访问本地电脑：本地爬虫主动推送抓到的 JD 到主系统。推送的批次<b>默认只入库、不解析</b>，
          需要时按批次手动解析（也可在批次台账上临时打开「自动解析」）。
          本页用于查看推送结果、推送原文与市场 JD 池。
        </p>
        <div class="hero-chip-row">
          <span v-if="commandQueueEnabled" class="hero-chip">
            采集通道：
            <template v-if="!availabilityChecked">检测中</template>
            <template v-else-if="availability?.queueAvailable">已接入</template>
            <template v-else>未接入</template>
          </span>
          <span v-if="commandQueueEnabled" class="hero-chip">在线爬虫：{{ availability?.agentsOnline ?? 0 }} 台</span>
          <span class="hero-chip">权限：POST:EVOLUTION</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-button :loading="poolLoading || statsLoading" @click="loadChannels(); refreshPool()">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="channelError"
      type="error"
      :closable="false"
      show-icon
      :title="channelError"
    />

    <template v-if="commandQueueEnabled">
      <el-alert
        v-if="availabilityChecked && !availability?.queueAvailable"
        type="warning"
        :closable="false"
        show-icon
        title="采集通道未接入"
      >
        <template #default>
          请配置 <code>MARKET_JD_CRAWLER_API_KEY</code>（与爬虫侧 <code>MAIN_SYSTEM_TOKEN</code> 一致）后刷新。
          未接入时主系统的市场 JD 池浏览与批次解析仍可正常使用。
        </template>
      </el-alert>

      <el-alert
        v-else-if="availabilityChecked && !onlineAgents.length"
        type="info"
        :closable="false"
        show-icon
        title="当前没有在线的本地爬虫"
      >
        <template #default>
          命令会先进入队列等待；本地爬虫启动后会每 10–30 秒轮询一次并自动领取执行，
          无需人工干预。如需指定某台实例执行，可在命令中填写 agentId（当前版本页面未开放）。
        </template>
      </el-alert>

      <el-alert
        v-if="availability?.legacyProxyEnabled"
        type="warning"
        :closable="false"
        show-icon
        title="过渡期反向代理仍处于开启状态"
        class="cj-legacy-alert"
      >
        <template #default>
          上游验收要求「主系统全程不访问本地电脑地址」，请将
          <code>CRAWLER_SYSTEM_LEGACY_PROXY_ENABLED</code> 置为 <code>false</code>。
        </template>
      </el-alert>
    </template>

    <div v-if="commandQueueEnabled" class="cj-grid">
      <section class="glass-card cj-card">
        <div class="cj-card__head">
          <div>
            <div class="section-title">采集参数</div>
            <div class="section-desc">下发一条采集命令，本地爬虫领取后开始抓取并推送。</div>
          </div>
        </div>
        <div class="cj-card__body">
          <div class="cj-field">
            <label>数据源</label>
            <el-checkbox-group v-model="form.sources">
              <el-checkbox v-for="opt in CRAWLER_SOURCE_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="cj-field">
            <label>搜索关键词</label>
            <el-input
              v-model="form.keywordsText"
              type="textarea"
              :rows="3"
              placeholder="每行一个关键词，例如：Java开发工程师"
            />
          </div>
          <div class="cj-field">
            <label>城市（可选）</label>
            <el-input v-model="form.citiesText" placeholder="每行一个城市，例如：北京" />
          </div>
          <div class="cj-field">
            <label>最大抓取数量</label>
            <el-input-number v-model="form.maxItems" :min="1" :max="500" :step="10" />
          </div>

          <el-alert
            v-if="submitError"
            type="error"
            :closable="false"
            show-icon
            :title="submitError"
          />

          <div class="cj-actions">
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="!canSubmit"
              @click="handleSubmitCommand"
            >
              <el-icon><Promotion /></el-icon> 下发采集命令
            </el-button>
            <span class="cj-hint">
              {{ onlineAgents.length ? `当前 ${onlineAgents.length} 台爬虫在线，将立即领取` : '暂无在线爬虫，命令会排队等待' }}
            </span>
          </div>
        </div>
      </section>

      <section class="glass-card cj-card">
        <div class="cj-card__head">
          <div>
            <div class="section-title">本地爬虫实例</div>
            <div class="section-desc">按最近心跳倒序；超过阈值未上报即视为离线。</div>
          </div>
          <el-button size="small" text type="primary" :icon="RefreshRight" @click="refreshChannels">
            刷新
          </el-button>
        </div>
        <div class="cj-card__body">
          <el-empty v-if="!agents.length" description="暂无爬虫实例上报过心跳" :image-size="72" />
          <div v-else class="cj-agent-list">
            <div v-for="agent in agents" :key="agent.agentId" class="cj-agent">
              <div class="cj-agent__main">
                <span class="cj-agent__id">{{ agent.agentName || agent.agentId }}</span>
                <span v-if="agent.agentVersion" class="cj-agent__meta">v{{ agent.agentVersion }}</span>
                <span v-if="agent.hostInfo" class="cj-agent__meta">{{ agent.hostInfo }}</span>
              </div>
              <div class="cj-agent__side">
                <el-tag size="small" :type="agent.online ? 'success' : 'info'" effect="light">
                  {{ agent.online ? '在线' : '离线' }}
                </el-tag>
                <span class="cj-agent__meta">
                  {{ agent.lastHeartbeatTime ? `心跳 ${agent.lastHeartbeatTime}` : '从未上报' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <section v-if="commandQueueEnabled" class="glass-card cj-card">
      <div class="cj-card__head">
        <div>
          <div class="section-title">最近采集命令</div>
          <div class="section-desc">
            共 {{ commandSummary.total }} 条 · 进行中 {{ commandSummary.active }} 条 ·
            成功 {{ commandSummary.succeeded }} 条 · 失败 {{ commandSummary.failed }} 条
          </div>
        </div>
      </div>
      <div class="cj-card__body">
        <el-table
          :data="commands"
          size="small"
          border
          empty-text="还没有采集命令，先在上方配置并「下发采集命令」"
        >
          <el-table-column prop="commandId" label="命令ID" min-width="200" show-overflow-tooltip />
          <el-table-column label="数据源" min-width="140">
            <template #default="{ row }">
              {{ sourceLabels(row.sources).join('、') || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="关键词" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ (row.keywords || []).join('、') || '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="maxItems" label="上限" width="80" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="commandTagType(row.status)" effect="light">
                {{ row.statusText || row.status || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行实例" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.agentId || '—' }}</template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170" />
          <el-table-column label="说明" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" class="cj-error-text">{{ row.errorMessage }}</span>
              <span v-else class="cj-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING'"
                size="small"
                text
                type="danger"
                :loading="cancellingId === row.commandId"
                @click="handleCancelCommand(row)"
              >
                取消
              </el-button>
              <span v-else class="cj-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="glass-card cj-card">
      <div class="cj-card__head">
        <div>
          <div class="section-title">批次台账</div>
          <div class="section-desc">
            爬虫推送与人工上传的批次都在这里，按来源通道分类；说明「推了多少、去重多少、解析到什么程度」。
          </div>
        </div>
        <div class="toolbar-group">
          <!--
            自动解析开关：这是**运行期临时覆盖**（不落库），所以必须把
            「当前值是我刚点的 / 还是配置默认的」与「重启会回落」一并写出来，
            否则用户会当成持久化设置，重启后发现值变了会认为开关失效。
          -->
          <div class="cj-auto-analyze">
            <span class="cj-auto-analyze__label">{{ autoAnalyzeSummary(autoAnalyze) }}</span>
            <el-switch
              :model-value="autoAnalyzeEnabled"
              :loading="autoAnalyzeLoading || autoAnalyzeSaving"
              :disabled="autoAnalyzeLoading || autoAnalyzeSaving"
              inline-prompt
              active-text="自动"
              inactive-text="手动"
              @change="handleAutoAnalyzeChange"
            />
            <el-tooltip :content="autoAnalyzeHint" placement="top" :show-after="200">
              <span class="cj-auto-analyze__note">
                {{ autoAnalyzeNote || '默认手动解析' }}
              </span>
            </el-tooltip>
          </div>
          <el-select
            v-model="batchChannel"
            class="cj-filter-select"
            placeholder="来源通道"
            @change="handleChannelChange"
          >
            <el-option
              v-for="option in BATCH_CHANNEL_OPTIONS"
              :key="option.value || 'all'"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button size="small" text type="primary" :icon="RefreshRight" @click="refreshChannels">
            刷新
          </el-button>
        </div>
      </div>
      <el-alert
        v-if="autoAnalyzeError"
        type="warning"
        :closable="false"
        show-icon
        :title="autoAnalyzeError"
      />
      <div class="cj-card__body">
        <el-table
          :data="batches"
          size="small"
          border
          empty-text="还没有批次记录，先下发采集命令或上传 JD"
        >
          <el-table-column prop="batchNo" label="批次号" min-width="180" show-overflow-tooltip />
          <el-table-column label="来源通道" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="channelTagType(row.ingestChannel)" effect="light">
                {{ row.ingestChannelText || channelLabel(row.ingestChannel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="130">
            <template #default="{ row }">{{ sourceLabel(row.sourcePlatform) }}</template>
          </el-table-column>
          <el-table-column prop="itemCount" label="条数" width="70" />
          <el-table-column prop="imported" label="入库" width="70" />
          <el-table-column prop="updated" label="更新" width="70" />
          <el-table-column prop="duplicate" label="重复" width="70" />
          <el-table-column prop="failed" label="失败" width="70" />
          <el-table-column label="接收结论" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="batchResultTagType(row.resultStatus)" effect="light">
                {{ row.resultStatusText || row.resultStatus || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="解析状态" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" :type="analysisTagType(row.analysisState)" effect="light">
                {{ row.analysisStateText || row.analysisState || '—' }}
              </el-tag>
              <span v-if="row.analysisNote" class="cj-note-inline">{{ row.analysisNote }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="接收时间" width="170" />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <!--
                叫「查看 JD」而不是「查看」：这个按钮真正的行为是「按该批次筛选下方的市场 JD 池」。
                用户的目的通常是「这一批推过来的 JD 逐条看一眼、单条重解析」，
                原名会让他在批次台账这一行里找「解析」按钮，从而以为只能整批重解析。
              -->
              <el-tooltip
                content="按该批次筛选下方「市场 JD 池」，在那里可逐条解析并查看解析结果"
                placement="top"
              >
                <el-button size="small" text type="primary" @click="focusBatch(row.batchNo)">
                  查看 JD
                </el-button>
              </el-tooltip>
              <!--
                「原文」= 爬虫推过来的原始字段，未经清洗与改写。
                与市场 JD 池里的「结果」是两回事：这里回答「推了什么」，那里回答「AI 读出了什么」。
              -->
              <el-button
                size="small"
                text
                type="primary"
                @click="openRaw(row)"
              >
                原文
              </el-button>
              <el-button
                size="small"
                text
                type="primary"
                :loading="reanalyzingBatch === row.batchNo"
                @click="handleReanalyzeBatch(row)"
              >
                重新解析
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                :loading="deletingBatch === row.batchNo"
                @click="handleDeleteBatch(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section ref="poolSectionRef" class="glass-card cj-card">
      <div class="cj-card__head">
        <div>
          <div class="section-title">市场 JD 池</div>
          <div class="section-desc">
            按岗位名称与批次号筛选；批次号可留空查看全部。每行都有「解析」（单条跑清洗 → 能力提取 → 准入）
            与「结果」（看这条解析出了什么，含 AI 原始提取结果）。
          </div>
        </div>
        <div class="toolbar-group">
          <el-button :loading="deduplicating" @click="handleDeduplicate">
            <el-icon><Delete /></el-icon> 批次去重
          </el-button>
          <!--
            「重算去重」与「批次去重」不是同一件事：
            批次去重是常规幂等去重（只处理尚未标记的行）；重算去重会**重新判定全部行**，
            用于修复「被误标为重复」的历史数据。两者都作用于当前批次号。
          -->
          <el-tooltip
            content="按当前口径重新判定该批次的重复情况：修复被误标为「重复跳过」的历史数据，真正重复的仍会被标出"
            placement="top"
          >
            <el-button :loading="resettingDedupe" @click="handleResetDedupe">
              <el-icon><RefreshRight /></el-icon> 重算去重
            </el-button>
          </el-tooltip>
          <el-button type="primary" :loading="analyzing" @click="handleAnalyzeBatch">
            <el-icon><MagicStick /></el-icon> 解析该批次
          </el-button>
        </div>
      </div>

      <div class="cj-card__body">
        <div class="cj-filters">
          <el-input
            v-model="poolQuery.postName"
            placeholder="岗位名称"
            clearable
            class="cj-filter-input"
            @keyup.enter="refreshPool"
          />
          <el-input
            v-model="poolQuery.batchNo"
            placeholder="批次号，例如 crawler-20260904-001"
            clearable
            class="cj-filter-input"
            @keyup.enter="refreshPool"
          />
          <el-button type="primary" :loading="poolLoading" @click="refreshPool">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
          <!--
            JD 级治理：批次删除适合「整批推错了」，但池子里常常只混进几条脏数据，
            整批删会误伤同批次的正常数据 —— 所以必须提供单条与勾选批量删除。
          -->
          <el-button
            type="danger"
            plain
            :disabled="!poolSelection.length"
            :loading="poolDeleting"
            @click="handleBatchDeleteJds"
          >
            删除选中（{{ poolSelection.length }}）
          </el-button>
        </div>

        <div v-if="statistics || statsLoading" class="cj-stats">
          <div class="cj-stat"><span>批次</span><strong>{{ statistics?.batchNo ?? poolQuery.batchNo }}</strong></div>
          <div class="cj-stat"><span>总数</span><strong>{{ statistics?.totalCount ?? 0 }}</strong></div>
          <div class="cj-stat"><span>重复</span><strong>{{ statistics?.duplicateCount ?? 0 }}</strong></div>
          <div class="cj-stat"><span>已分析</span><strong>{{ statistics?.analyzedCount ?? 0 }}</strong></div>
          <div class="cj-stat"><span>已匹配岗位</span><strong>{{ statistics?.matchedCount ?? 0 }}</strong></div>
        </div>

        <el-alert
          v-if="poolError"
          type="error"
          :closable="false"
          show-icon
          :title="poolError"
          class="cj-pool-error"
        />

        <el-table
          v-loading="poolLoading"
          :data="poolRecords"
          size="small"
          border
          row-key="id"
          empty-text="暂无市场 JD，先下发采集命令或更换筛选条件"
          @selection-change="handlePoolSelectionChange"
        >
          <el-table-column type="selection" width="46" reserve-selection />
          <el-table-column prop="postName" label="岗位名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="companyName" label="公司" min-width="150" show-overflow-tooltip />
          <el-table-column prop="city" label="城市" width="90" />
          <el-table-column prop="salaryRange" label="薪资" width="110" />
          <el-table-column label="来源通道" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="channelTagType(row.ingestChannel)" effect="light">
                {{ row.ingestChannelText || channelLabel(row.ingestChannel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourcePlatform" label="来源" width="100" />
          <el-table-column prop="batchNo" label="批次号" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="jdAnalysisMeta(row).tone" effect="light">
                {{ jdAnalysisMeta(row).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重复" width="70">
            <template #default="{ row }">
              <span v-if="row.isDuplicate === 1" class="cj-dup">重复</span>
              <span v-else class="cj-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <!--
                单条解析：批次解析只捞「待分析」，重跑整批会连已成功的行一起再烧一遍 AI。
                重复项不允许点击（解析会跳过它），用 tooltip 说明原因而不是静默禁用。
              -->
              <el-tooltip
                :content="singleAnalyzeBlockReason(row) || '单独解析这一条（清洗 → 能力提取 → 准入）'"
                placement="top"
              >
                <el-button
                  size="small"
                  text
                  type="primary"
                  :loading="analyzingJdId === row.id"
                  @click="handleAnalyzeJd(row)"
                >
                  解析
                </el-button>
              </el-tooltip>
              <el-button size="small" text type="primary" @click="openJdResult(row)">
                结果
              </el-button>
              <el-button size="small" text type="primary" @click="focusBatch(row.batchNo || '')">
                同批次
              </el-button>
              <el-button size="small" text type="danger" @click="handleDeleteJd(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="cj-pagination">
          <el-pagination
            :current-page="poolQuery.current"
            :page-size="poolQuery.size"
            :total="poolTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </section>

    <!--
      单条解析结果抽屉。
      解析结果落在 market_jd_data.skill_tags（已准入标签ID）与 recommended_skill_tags（候选）里，
      池表格此前只渲染一个状态标签 —— 用户「点了解析但看不到结果」正是因此。
      这里把 ID 反解成可读标签（后端已补名称），并按分类分组展示。
    -->
    <el-drawer
      v-model="detailVisible"
      :title="detail?.postName ? `解析结果 · ${detail.postName}` : '解析结果'"
      size="640px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="jd-detail">
        <el-alert
          v-if="detailError"
          type="error"
          :closable="false"
          show-icon
          :title="detailError"
          class="jd-detail__alert"
        />

        <template v-if="detail">
          <div class="jd-detail__line">
            <el-tag size="small" :type="jdAnalysisMeta(detail).tone" effect="light">
              {{ jdAnalysisMeta(detail).text }}
            </el-tag>
            <span class="jd-detail__muted">{{ detail.ingestChannelText || channelLabel(detail.ingestChannel) }}</span>
            <span class="jd-detail__muted">批次 {{ detail.batchNo || '—' }}</span>
          </div>

          <div class="jd-detail__meta">
            <div class="jd-detail__meta-item"><span>公司</span><strong>{{ detail.companyName || '—' }}</strong></div>
            <div class="jd-detail__meta-item"><span>城市</span><strong>{{ detail.city || '—' }}</strong></div>
            <div class="jd-detail__meta-item"><span>薪资</span><strong>{{ detail.salaryRange || '—' }}</strong></div>
            <div class="jd-detail__meta-item"><span>来源</span><strong>{{ detail.sourcePlatform || '—' }}</strong></div>
            <div class="jd-detail__meta-item">
              <span>质量分</span>
              <strong>{{ detail.qualityScore ?? '—' }}</strong>
            </div>
            <div class="jd-detail__meta-item">
              <span>匹配岗位</span>
              <strong>{{ detail.matchedPostName || (detail.matchedPostId ? `#${detail.matchedPostId}` : '未匹配') }}</strong>
            </div>
          </div>

          <!-- 空结果必须给原因：未解析 / 跳过 / 重复项 / 已解析但未准入，四种文案各不相同 -->
          <el-alert
            v-if="detailEmptyHint"
            type="info"
            :closable="false"
            show-icon
            :title="detailEmptyHint"
            class="jd-detail__alert"
          />

          <template v-for="group in detailTagGroups" :key="group.category || 'uncategorized'">
            <div class="jd-detail__group-title">
              已准入能力 · {{ group.label }}
              <span class="jd-detail__count">{{ group.tags.length }}</span>
            </div>
            <div class="jd-detail__tags">
              <span
                v-for="(tag, index) in group.tags"
                :key="`accepted-${tag.tagId ?? 'name'}-${index}`"
                class="jd-detail__tag"
              >
                {{ tagRefLabel(tag) }}
                <em v-if="tagLevelLabel(tag.tagLevel)" class="jd-detail__level">{{ tagLevelLabel(tag.tagLevel) }}</em>
              </span>
            </div>
          </template>

          <template v-if="detailCandidateTags.length">
            <div class="jd-detail__group-title">
              推荐候选（待审核，不计入正式能力）
              <span class="jd-detail__count">{{ detailCandidateTags.length }}</span>
            </div>
            <div class="jd-detail__tags">
              <span
                v-for="(tag, index) in detailCandidateTags"
                :key="`candidate-${tag.tagId ?? 'name'}-${index}`"
                class="jd-detail__tag jd-detail__tag--candidate"
              >
                {{ tagRefLabel(tag) }}
                <em v-if="tagLevelLabel(tag.tagLevel)" class="jd-detail__level">{{ tagLevelLabel(tag.tagLevel) }}</em>
              </span>
            </div>
          </template>

          <!--
            AI 原始提取：回答「解析跑过了、AI 到底读出了什么」。
            与上面两组的关键区别是**没有经过标签匹配与准入门禁** ——
            「已准入」为空而这一块是满的，是市场 JD 的常态而不是故障，
            所以它不能因为准入为空就一起收起。
          -->
          <template v-if="detailAiTags.length">
            <div class="jd-detail__group-title">
              AI 原始提取 · 未经标签库匹配与准入
              <span class="jd-detail__count">{{ detailAiTags.length }}</span>
            </div>
            <div class="jd-detail__ai-summary">{{ detailAiSummary }}</div>
            <div class="jd-detail__ai-list">
              <div
                v-for="(tag, index) in detailAiTags"
                :key="`ai-${index}`"
                class="jd-detail__ai-item"
              >
                <div class="jd-detail__ai-head">
                  <span class="jd-detail__ai-name">{{ aiTagDisplayName(tag) }}</span>
                  <el-tag size="small" :type="aiTagTone(tag.matchStatus)" effect="plain" round>
                    {{ aiTagMatchLabel(tag.matchStatus) }}
                  </el-tag>
                  <span v-if="tag.matchedTagName" class="jd-detail__ai-matched">
                    → {{ tag.matchedTagName }}
                  </span>
                </div>
                <div class="jd-detail__ai-meta">
                  <span v-if="tag.techStack">{{ tag.techStack }}</span>
                  <span v-if="tag.category">{{ tagCategoryLabel(tag.category) }}</span>
                  <span v-if="tagLevelLabel(tag.level)">{{ tagLevelLabel(tag.level) }}</span>
                  <span v-if="tag.weight != null">权重 {{ tag.weight }}</span>
                  <span v-if="tag.confidence != null">置信度 {{ tag.confidence }}</span>
                  <span v-if="tag.isCore === 1">核心项</span>
                  <span v-if="tag.isRequired === 1">必填</span>
                </div>
                <div v-if="tag.evidence" class="jd-detail__ai-evidence">{{ tag.evidence }}</div>
              </div>
            </div>
          </template>

          <el-collapse class="jd-detail__raw">
            <el-collapse-item title="原始字段与 JD 正文（核对用）" name="raw">
              <div class="jd-detail__raw-row">
                <span>skill_tags</span>
                <code>{{ detail.rawSkillTags || '—' }}</code>
              </div>
              <div class="jd-detail__raw-row">
                <span>recommended_skill_tags</span>
                <code>{{ detail.rawRecommendedSkillTags || '—' }}</code>
              </div>
              <div class="jd-detail__raw-row">
                <span>ai_skill_tags</span>
                <code>{{ detail.rawAiSkillTags || '—' }}</code>
              </div>
              <div class="jd-detail__raw-row">
                <span>岗位描述</span>
                <pre>{{ detail.jobDescription || '—' }}</pre>
              </div>
              <div class="jd-detail__raw-row">
                <span>任职要求</span>
                <pre>{{ detail.requirements || '—' }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>

      <template #footer>
        <div class="jd-detail__footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="detail"
            type="primary"
            :loading="analyzingJdId === detail.id"
            @click="handleAnalyzeJd(detail)"
          >
            重新解析这一条
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!--
      推送原文抽屉：按批次逐条展示**爬虫推过来的原始字段**，不做任何清洗与改写。
      与「解析结果」抽屉分工明确 —— 这里回答「推了什么」，那里回答「AI 读出了什么」。
      核对字段是否被改写过、推送是否完整（某条描述为空/外部ID 对不上）都只能在这里看。
    -->
    <el-drawer
      v-model="rawVisible"
      :title="rawTitle"
      size="760px"
      destroy-on-close
      @closed="resetRaw"
    >
      <div v-loading="rawLoading" class="raw-jd">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="以下为爬虫推送过来的原始字段，未经清洗与改写"
          class="raw-jd__alert"
        />
        <el-alert
          v-if="rawError"
          type="error"
          :closable="false"
          show-icon
          :title="rawError"
          class="raw-jd__alert"
        />

        <div class="raw-jd__summary">
          <span>共 {{ rawTotal }} 条</span>
          <span class="raw-jd__muted">批次 {{ rawBatchNo || '—' }}</span>
        </div>

        <article v-for="row in rawRecords" :key="row.id" class="raw-jd__item">
          <header class="raw-jd__item-head">
            <div>
              <div class="raw-jd__item-title">{{ row.postName || '（未提供岗位名称）' }}</div>
              <div class="raw-jd__item-sub">{{ row.companyName || '（未提供公司名称）' }}</div>
            </div>
            <el-button size="small" text type="primary" @click="copyRawJd(row)">复制原文</el-button>
          </header>

          <div class="raw-jd__meta">
            <span>城市：{{ row.city || '—' }}</span>
            <span>薪资：{{ row.salaryRange || '—' }}</span>
            <span>来源：{{ sourceLabel(row.sourcePlatform) }}</span>
            <span>外部ID：{{ row.externalId || '—' }}</span>
            <span>发布时间：{{ row.publishedTime || '—' }}</span>
            <span>通道：{{ row.ingestChannelText || channelLabel(row.ingestChannel) }}</span>
          </div>

          <div class="raw-jd__field">
            <div class="raw-jd__field-label">岗位描述</div>
            <pre class="raw-jd__text">{{ row.jobDescription || '（未提供）' }}</pre>
          </div>
          <div class="raw-jd__field">
            <div class="raw-jd__field-label">任职要求</div>
            <pre class="raw-jd__text">{{ row.requirements || '（未提供）' }}</pre>
          </div>
          <div v-if="row.sourceUrl" class="raw-jd__field">
            <div class="raw-jd__field-label">原始链接</div>
            <a :href="row.sourceUrl" target="_blank" rel="noopener noreferrer">{{ row.sourceUrl }}</a>
          </div>
        </article>

        <el-empty
          v-if="!rawLoading && !rawRecords.length"
          :description="rawEmptyText"
          :image-size="72"
        />
      </div>

      <template #footer>
        <div class="raw-jd__footer">
          <el-pagination
            background
            small
            layout="total, sizes, prev, pager, next"
            :total="rawTotal"
            :current-page="rawQuery.current"
            :page-size="rawQuery.size"
            :page-sizes="[10, 20, 50]"
            @current-change="handleRawPageChange"
            @size-change="handleRawSizeChange"
          />
          <el-button @click="rawVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.cj-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 20px;
  align-items: start;
}

.cj-legacy-alert {
  margin-top: 4px;
}

.cj-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 22px 0;
}

.cj-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 22px 22px;
}

.cj-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cj-field > label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-muted);
}

.cj-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.cj-hint {
  color: var(--app-text-muted);
  font-size: 12px;
}

.cj-agent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cj-agent {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: var(--app-surface);
}

.cj-agent__main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.cj-agent__side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cj-agent__id {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
  word-break: break-all;
}

.cj-agent__meta {
  font-size: 12px;
  color: var(--app-text-muted);
}

.cj-error-text {
  color: #dc2626;
  font-size: 12px;
}

.cj-note-inline {
  margin-left: 6px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.cj-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.cj-filter-input {
  width: 240px;
}

.cj-filter-select {
  width: 140px;
}

.cj-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: rgba(47, 107, 255, 0.04);
}

.cj-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.cj-stat strong {
  color: var(--app-text-strong);
  font-size: 14px;
  font-weight: 700;
}

.cj-pool-error {
  margin: 0;
}

.cj-dup {
  color: #d97706;
  font-size: 12px;
}

.cj-muted {
  color: var(--app-text-muted);
}

.cj-pagination {
  display: flex;
  justify-content: flex-end;
}

/* ---------- 单条解析结果抽屉 ---------- */

.jd-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.jd-detail__alert {
  margin: 0;
}

.jd-detail__line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.jd-detail__muted {
  font-size: 12px;
  color: var(--app-text-muted);
}

.jd-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  padding: 12px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: rgba(47, 107, 255, 0.04);
}

.jd-detail__meta-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
}

.jd-detail__meta-item > span {
  flex: none;
  color: var(--app-text-muted);
}

.jd-detail__meta-item > strong {
  overflow: hidden;
  font-weight: 600;
  color: var(--app-text-strong);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jd-detail__group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.jd-detail__count {
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(47, 107, 255, 0.12);
  color: #2f6bff;
  font-size: 12px;
  font-weight: 600;
}

.jd-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.jd-detail__tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface);
  font-size: 12px;
  color: var(--app-text-strong);
}

/* 候选集用虚线区分：它不计入正式能力，实线会让人以为已经准入 */
.jd-detail__tag--candidate {
  border-style: dashed;
  color: var(--app-text-muted);
}

.jd-detail__level {
  font-style: normal;
  font-size: 11px;
  color: var(--app-text-muted);
}

/* AI 原始提取用卡片列表而不是标签流：每一项都带证据原文，
   挤在一行里读不出「这句话支持了什么能力」。 */
.jd-detail__ai-summary {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.jd-detail__ai-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.jd-detail__ai-item {
  padding: 8px 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 8px);
  background: var(--app-bg-secondary, #f8fafc);
}

.jd-detail__ai-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.jd-detail__ai-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
}

.jd-detail__ai-matched {
  font-size: 12px;
  color: var(--app-text-muted);
}

.jd-detail__ai-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.jd-detail__ai-evidence {
  margin-top: 6px;
  padding-left: 8px;
  border-left: 2px solid var(--app-border);
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
  white-space: pre-wrap;
}

.jd-detail__raw {
  margin-top: 4px;
}

.jd-detail__raw-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.jd-detail__raw-row > span {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-muted);
}

.jd-detail__raw-row > code,
.jd-detail__raw-row > pre {
  max-height: 220px;
  margin: 0;
  padding: 8px 10px;
  overflow: auto;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.04);
  font-size: 12px;
  color: var(--app-text-strong);
  white-space: pre-wrap;
  word-break: break-word;
}

.jd-detail__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* ---------- 自动解析开关（批次台账头部） ---------- */
/* 它是「行为开关」而不是筛选器，用底色与筛选/刷新区分开，避免被当成过滤条件 */
.cj-auto-analyze {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: rgba(47, 107, 255, 0.04);
}

.cj-auto-analyze__label {
  font-size: 12px;
  color: var(--app-text-strong);
  white-space: nowrap;
}

/* 覆盖态说明可能较长，截断显示，完整内容在 tooltip 里 */
.cj-auto-analyze__note {
  max-width: 200px;
  overflow: hidden;
  font-size: 12px;
  color: var(--app-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
}

/* ---------- 推送原文抽屉 ---------- */
.raw-jd {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.raw-jd__alert {
  margin: 0;
}

.raw-jd__summary {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 12px;
  color: var(--app-text-strong);
}

.raw-jd__muted {
  color: var(--app-text-muted);
}

.raw-jd__item {
  padding: 12px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: var(--app-surface);
}

.raw-jd__item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.raw-jd__item-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-strong);
  word-break: break-word;
}

.raw-jd__item-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.raw-jd__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin: 8px 0 10px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.raw-jd__field + .raw-jd__field {
  margin-top: 8px;
}

.raw-jd__field-label {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-strong);
}

/* 原文必须原样换行显示（pre-wrap），并限制高度避免一条就把抽屉撑爆 */
.raw-jd__text {
  margin: 0;
  padding: 8px 10px;
  max-height: 220px;
  overflow: auto;
  border-radius: var(--app-radius-sm, 8px);
  background: var(--app-bg-secondary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-strong);
  white-space: pre-wrap;
  word-break: break-word;
}

.raw-jd__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

@media (max-width: 768px) {
  .cj-filter-input {
    width: 100%;
  }

  .cj-filter-select {
    width: 100%;
  }

  .jd-detail__meta {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
