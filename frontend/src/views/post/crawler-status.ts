/**
 * 爬虫采集命令 / 批次状态的纯展示逻辑。
 *
 * 抽成纯函数的目的：状态码 → 文案与颜色的映射是最容易「改一处漏一处」的地方，
 * 放在 .ts 里可以被 .test.mjs 直接覆盖，避免页面里散落 if-else 后无人回归。
 *
 * 口径说明：
 * - 命令状态由后端 `CrawlerCommandView.statusText` 给出中文，前端只负责选一个 tag 颜色；
 * - 批次结论与解析状态同样由后端给出 `resultStatusText` / `analysisStateText`，
 *   前端不自行造词，避免与后端口径漂移。
 */

/** 命令终态：到达这些状态后不会再变化，可停止轮询 */
export const TERMINAL_COMMAND_STATUSES = ['SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED'] as const

export type TagType = 'success' | 'danger' | 'warning' | 'info' | 'primary'

/** 命令是否仍在推进中（需要继续轮询） */
export function isCommandActive(status?: string | null): boolean {
  if (!status) {
    // 状态未知时按「仍在推进」处理，宁可多轮询一次，也不要让用户以为已经结束
    return true
  }
  return !(TERMINAL_COMMAND_STATUSES as readonly string[]).includes(status)
}

/** 命令状态 → tag 颜色 */
export function commandTagType(status?: string | null): TagType {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PENDING':
    case 'DISPATCHED':
    case 'RUNNING':
      return 'warning'
    case 'EXPIRED':
    case 'CANCELLED':
      return 'info'
    default:
      return 'info'
  }
}

/** 批次接收结论 → tag 颜色 */
export function batchResultTagType(resultStatus?: string | null): TagType {
  switch (resultStatus) {
    case 'OK':
      return 'success'
    case 'PARTIAL_FAILED':
      return 'warning'
    case 'REJECTED':
    case 'UNAUTHORIZED':
      return 'danger'
    default:
      return 'info'
  }
}

/**
 * 批次解析是否仍在推进（QUEUED / RUNNING）。
 *
 * 页面的在途轮询不能只看「有没有未结束的采集命令」：采集命令入口当前是关闭的
 * （`CRAWLER_COMMAND_QUEUE_ENABLED = false`），而爬虫是**自己按计划推送** JD 的，
 * 于是「批次解析中」这件事发生时页面根本不轮询 —— 用户点完解析/等爬虫推完，
 * 标签会一直停在「解析中」，必须手动刷新才变。判定条件必须把在解析的批次也算进来。
 */
export function isAnalysisActive(analysisState?: string | null): boolean {
  return analysisState === 'QUEUED' || analysisState === 'RUNNING'
}

/** 批次解析状态 → tag 颜色 */
export function analysisTagType(analysisState?: string | null): TagType {
  switch (analysisState) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'QUEUED':
    case 'RUNNING':
      return 'warning'
    case 'SKIPPED':
    case 'NOT_TRIGGERED':
      return 'info'
    default:
      return 'info'
  }
}

// ===================== 市场 JD 行级分析状态（analysis_status） =====================
//
// ⚠️ 这里踩过一次真实的坑，新增页面不要再套用「Excel 导入」那套编码。
//
// 仓库里同时存在两套 analysisStatus 编码，名字一样但含义不同：
//   ① PostImportItem / JdImportTask（Excel 导入、岗位导入）：0 待分析 / 1 分析中 / 2 成功 / 3 失败
//   ② MarketJdData（市场 JD，含爬虫推送）：0 待分析 / 1 已分析 / 2 跳过
// 本模块负责的是 ②。本页曾按 ① 渲染 → **每一条分析成功的 JD 都被显示成「分析中」**，
// 用户看到的现象就是「爬虫推来数据、点了解析之后一直卡在解析中」。
//
// ② 是**后端 load-bearing 的口径**，不能反过来改后端去迁就前端：
//   - `MarketJdQueryPortAdapter`：analysisStatus == 2 的 JD 不得作为岗位演化证据；
//   - `EmergingPostDiscoveryServiceImpl`：只挑 analysisStatus == 1 且 isDuplicate == 0 参与 PMI 统计；
//   - `MarketJdImportServiceImpl`：清洗阻断 / 业务异常写 2，提取 + 准入成功写 1，
//     Harness 基础设施失败回退 0（可重试）。
// 因此 2 = 跳过 / 阻断（不是「成功」），1 = 已分析可用（不是「进行中」）。
//
// MarketJdData **没有**「正在分析中」的行级状态：一次分析是批次内串行批处理，行级只有
// 待分析 / 已分析 / 跳过 三态。「解析中」由批次级 `analysis_state`（见 `analysisTagType`）表达。

/** 市场 JD 行级分析状态取值（与后端 `MarketJdData.analysisStatus` 一致） */
export const MARKET_JD_ANALYSIS_STATUS = {
  PENDING: 0,
  ANALYZED: 1,
  SKIPPED: 2,
} as const

export interface JdAnalysisStatusMeta {
  text: string
  tone: TagType
}

/**
 * 市场 JD 行级分析状态 → 文案与颜色。
 *
 * `isDuplicate` 必须一起传：去重命中的 JD 会被刻意跳过分析，状态**永远停在 0**。
 * 只看状态码会显示成「待分析」，让人以为系统还欠它一次解析（反复点「重新解析」也不会变），
 * 所以重复项要明确说成「重复跳过」。
 */
export function marketJdAnalysisStatusMeta(
  status?: number | null,
  isDuplicate?: number | null,
): JdAnalysisStatusMeta {
  // 状态缺失与 0 同义（都是「还没分析」），但 switch 不会把 null 匹配到 case 0，所以先归一化
  const normalized = status === null || status === undefined
    ? MARKET_JD_ANALYSIS_STATUS.PENDING
    : status
  if (normalized === MARKET_JD_ANALYSIS_STATUS.PENDING) {
    return isDuplicate === 1
      ? { text: '重复跳过', tone: 'info' }
      : { text: '待分析', tone: 'info' }
  }
  switch (normalized) {
    case MARKET_JD_ANALYSIS_STATUS.ANALYZED:
      return { text: '已分析', tone: 'success' }
    case MARKET_JD_ANALYSIS_STATUS.SKIPPED:
      // 跳过包含「去重命中」与「清洗/治理阻断」：都不是失败，但数据不可用，用 warning 而非 danger
      return { text: '跳过', tone: 'warning' }
    default:
      // 未知取值回显原码：静默归一化会让后端新增状态无人发现
      return { text: `未知(${normalized})`, tone: 'info' }
  }
}

// ===================== 批次来源通道（爬虫 vs 人工上传） =====================
//
// 通道是运维判断「这批数据该不该信、该不该留」的第一依据，所以：
// - 取值集合与后端 MarketJdData.CHANNEL_* 一一对应，新增通道时这里必须先红；
// - 通道 tag 用主色区分爬虫与人工，避免运维把抓来的和手工传的混为一谈。

/** 全部批次通道（与后端取值一致） */
export const BATCH_CHANNELS = ['CRAWLER', 'MANUAL_UPLOAD', 'POST_IMPORT'] as const

export type BatchChannel = (typeof BATCH_CHANNELS)[number]

/** 批次列表的通道筛选项（value 为空串表示不筛选） */
export const BATCH_CHANNEL_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: '全部来源' },
  { value: 'CRAWLER', label: '爬虫推送' },
  { value: 'MANUAL_UPLOAD', label: '人工上传' },
  { value: 'POST_IMPORT', label: '岗位导入' },
]

/** 通道代码 → 中文名（后端已透传 ingestChannelText，这里是第二取值的兜底映射） */
export function channelLabel(code?: string | null): string {
  switch (code) {
    case 'CRAWLER':
      return '爬虫推送'
    case 'MANUAL_UPLOAD':
      return '人工上传'
    case 'POST_IMPORT':
      return '岗位导入'
    case null:
    case undefined:
    case '':
      return '—'
    default:
      // 未知通道回显原值：静默显示成「—」会让运维误以为这条没有来源
      return code
  }
}

/** 通道 → tag 颜色；爬虫用主色强调（数据来自外部），人工用中性色 */
export function channelTagType(code?: string | null): TagType {
  switch (code) {
    case 'CRAWLER':
      return 'primary'
    case 'MANUAL_UPLOAD':
      return 'warning'
    case 'POST_IMPORT':
      return 'info'
    default:
      return 'info'
  }
}

/** 批次删除结果 → 中文结论（用于删除成功后的提示语） */
export function describeBatchDelete(result?: {
  batchNo?: string
  marketJdRows?: number
  versionSnapshots?: number
  batchLogs?: number
} | null): string {
  if (!result) {
    return '批次已删除'
  }
  const rows = result.marketJdRows ?? 0
  const logs = result.batchLogs ?? 0
  if (rows === 0 && logs === 0) {
    return '批次已不存在，无数据被删除'
  }
  return `批次已删除：市场 JD ${rows} 条、历史版本 ${result.versionSnapshots ?? 0} 条、批次登记 ${logs} 条`
}

/** 单条 JD 删除结果 → 中文结论 */
export function describeSingleJdDelete(result?: {
  id?: number
  marketJdRows?: number
  versionSnapshots?: number
} | null): string {
  if (!result || (result.marketJdRows ?? 0) === 0) {
    // 后端已做「存在才删」判定，走到这里说明记录被并发删掉了 —— 不能说「删除成功」，
    // 否则用户会以为自己刚删的那条数据还在。
    return '该 JD 已不存在，无需删除'
  }
  return `已删除该 JD，同时清理历史版本 ${result.versionSnapshots ?? 0} 条`
}

/**
 * 批量 JD 删除结果 → 中文结论。
 *
 * `missingIds` 必须体现出来：勾选时记录还在，提交后可能已被他人删掉，
 * 全部报「成功 N 条」会让用户对数据现状产生错误判断。
 */
export function describeBatchJdDelete(result?: {
  requested?: number
  marketJdRows?: number
  versionSnapshots?: number
  missingIds?: number[]
} | null): string {
  if (!result) {
    return '批量删除已完成'
  }
  const rows = result.marketJdRows ?? 0
  const missing = result.missingIds?.length ?? 0
  // 先判「一条都没删掉」：此时再补一句「另有 N 条已不存在」是重复的，
  // 而且「已删除 0 条 JD」这种说法本身就会让人以为操作部分失败了。
  if (rows === 0) {
    return '所选 JD 均已不存在，无需删除'
  }
  const base = `已删除 ${rows} 条 JD，清理历史版本 ${result.versionSnapshots ?? 0} 条`
  if (missing > 0) {
    return `${base}；另有 ${missing} 条已不存在（可能已被他人删除）`
  }
  return base
}

/** 采集来源代码 → 中文名；未知代码回显原值而不是吞成空 */
export function sourceLabel(code?: string | null): string {
  const map: Record<string, string> = {
    jd: '京东 / 招聘 JD',
    remoteok: 'RemoteOK',
    themuse: 'The Muse',
    arbeitnow: 'Arbeitnow',
  }
  if (!code) return '—'
  return map[code] ?? code
}

/** 多来源代码 → 中文名列表 */
export function sourceLabels(codes?: string[] | null): string[] {
  if (!codes?.length) return []
  return codes.map(item => sourceLabel(item))
}

/** 命令列表汇总，用于卡片标题右侧概览 */
export interface CommandSummary {
  total: number
  active: number
  succeeded: number
  failed: number
}

export function summarizeCommands(commands: Array<{ status?: string | null }> | null | undefined): CommandSummary {
  const list = commands ?? []
  let active = 0
  let succeeded = 0
  let failed = 0
  for (const command of list) {
    if (isCommandActive(command.status)) {
      active += 1
    } else if (command.status === 'SUCCEEDED') {
      succeeded += 1
    } else if (command.status === 'FAILED') {
      failed += 1
    }
  }
  return { total: list.length, active, succeeded, failed }
}
