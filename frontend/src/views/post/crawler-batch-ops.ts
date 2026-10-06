/**
 * 推送批次的运维动作（自动解析开关 / 重算去重）与「推送原文」展示的纯逻辑。
 *
 * 为什么单独抽出来：
 * - 「开关当前是页面临时值还是配置默认值」「重算去重到底改了什么」这类口径最容易在改动里
 *   悄悄失真，又最难靠肉眼发现 —— 少写「重启会回落」几个字，用户就会把它当成一个持久化设置，
 *   重启后发现值变了会认为是 bug；
 * - 这里刻意**不 import 任何 `@/` 别名模块**，只用结构类型描述入参，
 *   保证本文件能在 node（`--experimental-strip-types`）下直跑测试。
 *
 * 关键不变式：**「重算后 0 条重复」必须说成「这批本来就没有重复」**，而不是「已处理 0 条」。
 * 用户抱怨的原始现象正是「看起来全是重复跳过」，这句结论才是他真正需要的结果。
 */

/** 提示色阶（与 ElMessage 的 type 一致；标签那套 `danger` 在这里不适用） */
export type NoticeTone = 'success' | 'warning' | 'info' | 'error'

export interface NoticeLike {
  type: NoticeTone
  text: string
}

// ===================== 自动解析开关 =====================

/** 与后端 `CrawlerAutoAnalyzeView` 结构对齐（用结构类型，避免依赖 API 层） */
export interface AutoAnalyzeLike {
  /** 当前生效值（页面临时覆盖优先，否则配置默认） */
  enabled?: boolean | null
  /** 配置默认值 */
  configuredDefault?: boolean | null
  /** 是否处于「页面临时覆盖」状态 */
  overridden?: boolean | null
  /** 后端给出的中文说明（优先展示，避免前后端两套口径） */
  message?: string | null
}

/** 开关旁的短标签；取不到状态时明说「未知」而不是猜一个 */
export function autoAnalyzeSummary(view?: AutoAnalyzeLike | null): string {
  if (!view) return '自动解析状态未知'
  return view.enabled ? '推送后自动解析' : '推送后手动解析'
}

/**
 * 开关下方的常驻说明。
 *
 * 优先用后端 `message`：默认值、手动解析该去哪里点，这些口径由后端定义，
 * 前端再写一份就会在后端调整文案后分叉。
 */
export function autoAnalyzeDetail(view?: AutoAnalyzeLike | null): string {
  if (!view) return '自动解析状态未知，请刷新后重试。'
  const message = (view.message ?? '').trim()
  if (message) return message
  return view.enabled
    ? '推送的批次入库后会立即进入解析链路（治理 → 去重 → 能力提取 → 准入）。'
    : '推送的批次只入库、不解析；需要解析时请在批次行点「重新解析」，或到市场 JD 池按批次解析。'
}

/** 「当前是页面临时设置」的角标文案；非覆盖状态返回 null（不该显示角标） */
export function autoAnalyzeOverrideNote(view?: AutoAnalyzeLike | null): string | null {
  if (!view?.overridden) return null
  const fallback = view.configuredDefault ? '开启' : '关闭'
  return `页面临时设置，服务重启后回到配置默认值（${fallback}）`
}

/**
 * 切换开关成功后的提示。
 *
 * 三种结局分开说：恢复配置默认 / 临时开启 / 临时关闭。合并成「设置已更新」会让用户
 * 无法判断自己刚才到底把系统改成了什么状态，而这是一个会影响后续所有批次的行为开关。
 */
export function describeAutoAnalyzeChange(view?: AutoAnalyzeLike | null): NoticeLike {
  if (!view) return { type: 'info', text: '自动解析设置已更新' }
  if (!view.overridden) {
    return {
      type: 'info',
      text: `已恢复为配置默认值（${view.enabled ? '开启' : '关闭'}自动解析）`,
    }
  }
  return view.enabled
    ? {
        type: 'success',
        text: '已开启自动解析：之后推送的批次会立即进入解析链路（页面临时设置，服务重启后回到默认值）',
      }
    : {
        type: 'info',
        text: '已关闭自动解析：之后推送的批次只入库，需要时在批次行点「重新解析」（页面临时设置，服务重启后回到默认值）',
      }
}

// ===================== 重算去重 =====================

/** 与后端 `MarketJdImportService.DedupeResetResult` 结构对齐 */
export interface DedupeResetLike {
  batchNo?: string | null
  /** 扫描到的行数 */
  scannedRows?: number | null
  /** 重算了去重键的行数（只有爬虫来源需要重算） */
  hashRecomputed?: number | null
  /** 按正确口径判出的重复行数 */
  duplicateHits?: number | null
}

/**
 * 重算去重完成后的提示。
 *
 * `duplicateHits === 0` 是一个**成功结论**而不是「什么都没做」：
 * 它正好回答了用户「这批看起来全是重复、其实没有重复」的疑问，必须说清楚。
 */
export function describeDedupeReset(result?: DedupeResetLike | null): NoticeLike {
  if (!result) {
    return { type: 'info', text: '重算去重已完成' }
  }
  const scanned = result.scannedRows ?? 0
  if (scanned === 0) {
    return { type: 'info', text: '该批次没有已入库的数据，无需重算去重' }
  }
  const hits = result.duplicateHits ?? 0
  const recomputed = result.hashRecomputed ?? 0
  if (hits === 0) {
    return {
      type: 'success',
      text: `已按当前口径重判 ${scanned} 条：这批数据本来就没有重复`
        + '（此前显示的「重复跳过」属误判，已恢复为待分析）',
    }
  }
  const suffix = recomputed > 0 ? `；同时重算了 ${recomputed} 条的去重键` : ''
  return {
    type: 'success',
    text: `已按当前口径重判 ${scanned} 条，其中 ${hits} 条确认为重复（会被跳过解析）${suffix}`,
  }
}

/** 重算去重前的确认文案（这是写操作，必须先把「会改什么」讲清楚） */
export function dedupeResetConfirmText(batchNo: string): string {
  return `将按当前去重口径重新判定批次 ${batchNo} 的全部数据：`
    + '此前被误判为「重复」的记录会恢复为待分析，真正重复的记录仍会被标出。是否继续？'
}

// ===================== 推送原文 =====================

/** 与后端 `MarketJdResponse` 的原文相关字段对齐 */
export interface RawJdLike {
  postName?: string | null
  companyName?: string | null
  city?: string | null
  salaryRange?: string | null
  sourcePlatform?: string | null
  externalId?: string | null
  sourceUrl?: string | null
  publishedTime?: string | null
  batchNo?: string | null
  jobDescription?: string | null
  requirements?: string | null
}

const MISSING = '（未提供）'

/**
 * 空值归一化。
 *
 * 刻意不把空串写成 `-`：岗位描述为空与「字段缺失」在核对爬虫推送时含义不同，
 * 统一显示成 `-` 会让用户无法区分「爬虫没推」和「推了空串」。
 */
function orMissing(value?: string | null): string {
  const text = (value ?? '').trim()
  return text || MISSING
}

/** 单条推送原文的纯文本（供「复制原文」使用，也是页面上展示的同一份内容顺序） */
export function renderRawJdText(row: RawJdLike): string {
  const lines = [
    `岗位：${orMissing(row.postName)}`,
    `公司：${orMissing(row.companyName)}`,
    `城市：${orMissing(row.city)}`,
    `薪资：${orMissing(row.salaryRange)}`,
    `来源：${orMissing(row.sourcePlatform)}`,
    `外部ID：${orMissing(row.externalId)}`,
    `发布时间：${orMissing(row.publishedTime)}`,
    `批次：${orMissing(row.batchNo)}`,
    '',
    '【岗位描述】',
    orMissing(row.jobDescription),
    '',
    '【任职要求】',
    orMissing(row.requirements),
  ]
  const url = (row.sourceUrl ?? '').trim()
  if (url) {
    lines.push('', `【原始链接】${url}`)
  }
  return lines.join('\n')
}

/** 抽屉标题：带上批次号，避免同时打开多个批次时看错 */
export function rawDrawerTitle(batchNo: string, channelText?: string | null): string {
  const channel = (channelText ?? '').trim()
  return channel ? `推送原文 · ${channel} · ${batchNo}` : `推送原文 · ${batchNo}`
}

/** 列表为空时的说明：区分「批次没数据」与「已加载但这一页为空」 */
export function emptyRawHint(loading: boolean, total: number): string {
  if (loading) return '加载中…'
  if (total > 0) return '当前页没有数据'
  return '该批次没有已入库的 JD'
}
