/**
 * 单条市场 JD 解析结果（只读展示）的纯逻辑。
 *
 * 为什么单独抽出来：
 * - 状态码 → 文案的映射、以及「为什么没有能力标签」的归因，是最容易「改一处漏一处」的地方，
 *   放在 .ts 里才能被 .test.mjs 直接覆盖；
 * - 这里刻意**不 import 任何 `@/` 别名模块**，只用结构类型描述入参，
 *   保证本文件可以在 node（`--experimental-strip-types`）下直跑测试。
 *
 * 关键的展示不变式：**「没有标签」有四种完全不同的原因**（未解析 / 跳过 / 重复项 / 已解析但未准入），
 * 合并成一句「暂无数据」会把四种情况都变成「系统坏了」。
 */

/** 标签引用（与后端 `MarketJdTagRef` 结构对齐；这里用结构类型避免依赖 API 层） */
export interface TagRefLike {
  tagId?: number | null
  tagName?: string | null
  tagCategory?: string | null
  tagLevel?: number | null
}

export type TagTone = 'success' | 'danger' | 'warning' | 'info' | 'primary'

/**
 * 提示（ElMessage）用的色阶。
 *
 * 刻意不直接用 {@link TagTone}：标签用 `danger`，而 ElMessage 只认 `error`，
 * 两套取值混用会得到一个「类型看着对、编译不过」的错误。
 */
export type NoticeTone = 'success' | 'warning' | 'info' | 'error'

export interface TagGroup {
  /** 原始分类码；空串表示后端未给分类 */
  category: string
  /** 分类中文名 */
  label: string
  tags: TagRefLike[]
}

/** 标签分类中文名唯一来源（TECHNICAL / SOFT / BUSINESS，与 AbilityTag.tagCategory 一致） */
const TAG_CATEGORY_LABELS: Record<string, string> = {
  TECHNICAL: '技术能力',
  SOFT: '软技能',
  BUSINESS: '业务能力',
}

/** 分类码 → 中文名；未识别的分类**回显原值**而不是吞成「未分类」，否则后端新增分类无人发现 */
export function tagCategoryLabel(category?: string | null): string {
  const code = (category ?? '').trim()
  if (!code) return '未分类'
  return TAG_CATEGORY_LABELS[code] ?? code
}

/**
 * 标签展示名。
 *
 * 反解不到名称时必须回显标签ID并说明原因：标签被删除或停用后 `ability_tag` 查不到，
 * 静默显示空串会让用户以为「这条解析没产出能力」。
 */
export function tagRefLabel(tag?: TagRefLike | null): string {
  if (!tag) return '未知标签'
  const name = (tag.tagName ?? '').trim()
  if (name) return name
  if (tag.tagId !== null && tag.tagId !== undefined) {
    return `标签 #${tag.tagId}（已删除或停用）`
  }
  return '未知标签'
}

/** 标签层级展示：拿不到层级显示空串（**不能显示 L0**，0 级会被读成「最低级」而不是「未知」） */
export function tagLevelLabel(level?: number | null): string {
  if (level === null || level === undefined) return ''
  if (!Number.isFinite(level) || level <= 0) return ''
  return `L${level}`
}

/**
 * 按标签分类分组，保持后端返回的标签顺序（同类内顺序不变），
 * 分组本身按「首次出现的顺序」排列 —— 不用字典序，否则页面上的分组顺序会随数据变化而跳。
 */
export function groupTagsByCategory(tags?: TagRefLike[] | null): TagGroup[] {
  const groups = new Map<string, TagGroup>()
  for (const tag of tags ?? []) {
    if (!tag) continue
    const category = (tag.tagCategory ?? '').trim()
    let group = groups.get(category)
    if (!group) {
      group = { category, label: tagCategoryLabel(category), tags: [] }
      groups.set(category, group)
    }
    group.tags.push(tag)
  }
  return [...groups.values()]
}

/** 单条解析结论（与后端 `MarketJdSingleAnalysisResult` 结构对齐） */
export interface SingleAnalysisLike {
  id?: number
  analysisStatus: number
  acceptedTagCount?: number
  recommendedTagCount?: number
  rejectedClaimCount?: number
  infraFailed?: boolean
  message?: string
}

export interface NoticeLike {
  type: NoticeTone
  text: string
}

/**
 * 单条解析结束后的提示。
 *
 * `infraFailed` 绝不能用 success 色：用户会以为解析成功、看到池里状态还是「待分析」时
 * 就会认为数据丢了。要按「本次没跑出结果，但可重试」来提示。
 */
export function singleAnalysisNotice(
  result?: SingleAnalysisLike | null,
  fallback = '解析已提交',
): NoticeLike {
  if (!result) {
    return { type: 'info', text: fallback }
  }
  const text = (result.message ?? '').trim() || fallback
  if (result.infraFailed) {
    return { type: 'warning', text }
  }
  if (result.analysisStatus === 2) {
    return { type: 'info', text }
  }
  if (result.analysisStatus === 1) {
    return { type: 'success', text }
  }
  // analysisStatus === 0 且非 infraFailed：理论上不出现，按可重试提示而不是成功
  return { type: 'warning', text }
}

export interface AnalyzableInput {
  analysisStatus?: number | null
  isDuplicate?: number | null
}

/**
 * 该行能否发起单条解析；不能时返回中文原因（用于按钮 disabled + tooltip）。
 *
 * 只拦「重复项」：重复项在批次解析里也被刻意跳过，允许点击只会让用户白等一次 AI 调用。
 * **不拦已分析 / 已跳过** —— 单条解析是显式动作，允许重跑正是它的价值。
 */
export function singleAnalyzeBlockReason(row: AnalyzableInput): string | null {
  if ((row?.isDuplicate ?? 0) === 1) {
    return '重复项：同批次内已有等价正文，解析会跳过它（如需解析请先删除该条重复数据）'
  }
  return null
}

/** AI 原始提取的能力项（与后端 `MarketJdDetailResponse.AiTag` 结构对齐） */
export interface AiTagLike {
  name?: string | null
  techStack?: string | null
  category?: string | null
  abilityType?: string | null
  level?: number | null
  weight?: number | null
  isCore?: number | null
  isRequired?: number | null
  confidence?: number | null
  matchStatus?: string | null
  matchedTagId?: number | null
  matchedTagName?: string | null
  similarityScore?: number | null
  evidence?: string | null
  reasoning?: string | null
}

/**
 * 该能力项与系统标签库的关系，中文唯一来源。
 *
 * `matchStatus` 缺失必须是「未做匹配」而不是「未收录」：前者是数据没带上匹配结果
 * （历史行、或匹配环节未执行），后者是明确判断过「系统里没有这个标签」。
 * 把两者合并会让用户以为系统已经判定过，从而对「为什么没有标签」得出错误结论。
 */
export function aiTagMatchLabel(matchStatus?: string | null): string {
  const status = (matchStatus ?? '').trim().toUpperCase()
  if (status === 'MATCHED') return '已命中系统标签'
  if (status === 'SIMILAR') return '疑似相似'
  if (status === 'NEW') return '系统未收录'
  return '未做匹配'
}

/** 能力项名称；名称为空时不能渲染成空白（会让整行看起来是坏数据） */
export function aiTagDisplayName(tag?: AiTagLike | null): string {
  const name = (tag?.name ?? '').trim()
  return name || '（AI 未给出名称）'
}

/** 能力项标签的色阶：命中=成功色，疑似=警示色，未收录/未匹配=中性色 */
export function aiTagTone(matchStatus?: string | null): TagTone {
  const status = (matchStatus ?? '').trim().toUpperCase()
  if (status === 'MATCHED') return 'success'
  if (status === 'SIMILAR') return 'warning'
  return 'info'
}

export interface AiTagSummary {
  total: number
  matched: number
  similar: number
  unlisted: number
}

/** 统计 AI 提取结果与系统标签库的关系，供摘要与空态归因使用 */
export function summarizeAiTags(tags?: AiTagLike[] | null): AiTagSummary {
  let matched = 0
  let similar = 0
  let unlisted = 0
  for (const tag of tags ?? []) {
    if (!tag) continue
    const status = (tag.matchStatus ?? '').trim().toUpperCase()
    if (status === 'MATCHED') matched++
    else if (status === 'SIMILAR') similar++
    else unlisted++
  }
  return { total: matched + similar + unlisted, matched, similar, unlisted }
}

/** AI 提取结果的一句话摘要（抽屉顶部） */
export function aiTagSummaryText(summary: AiTagSummary): string {
  if (!summary || summary.total === 0) {
    return 'AI 没有从这条 JD 里提取到能力项'
  }
  const parts = [`共 ${summary.total} 项`]
  if (summary.matched > 0) parts.push(`命中系统标签 ${summary.matched} 项`)
  if (summary.similar > 0) parts.push(`疑似相似 ${summary.similar} 项`)
  if (summary.unlisted > 0) parts.push(`系统未收录 ${summary.unlisted} 项`)
  return parts.join('，')
}

/**
 * 「没有能力标签」时的常驻归因说明；有标签时返回 null。
 *
 * 五种原因分开写：未解析 / 被跳过 / 重复项 / 已解析但未准入 / **已解析且 AI 有提取、
 * 但没有一项进入正式标签库**。最后一种最容易被读成故障，必须点出「这是设计使然」，
 * 并指向 AI 原始提取结果 —— 否则用户看到的就是「解析完了什么都没有」。
 */
export function emptyTagHint(params: {
  analysisStatus?: number | null
  isDuplicate?: number | null
  acceptedCount?: number
  candidateCount?: number
  aiTagCount?: number
}): string | null {
  const acceptedCount = params?.acceptedCount ?? 0
  if (acceptedCount > 0) {
    return null
  }
  if ((params?.isDuplicate ?? 0) === 1) {
    return '该 JD 已被判定为重复项，解析会刻意跳过它，因此没有能力标签。'
  }
  if (params?.analysisStatus === 2) {
    return '该 JD 被判为噪声/无效正文，或该 JD 不适用于能力提取，已标记为「跳过」，因此没有能力标签。'
  }
  if (params?.analysisStatus === 1) {
    const aiTagCount = params?.aiTagCount ?? 0
    if (aiTagCount > 0) {
      return `解析已完成：AI 从这条 JD 里提取出 ${aiTagCount} 项能力，但没有一项进入系统正式标签库。`
        + '这通常不是故障 —— 市场 JD 的表述往往对不上既有正式标签，而新能力需不少于 3 条 JD '
        + '且不少于 2 家公司互相印证才会自动建标签。AI 提取的完整结果见下方「AI 原始提取」。'
    }
    if ((params?.candidateCount ?? 0) > 0) {
      return '解析已完成：本次没有标签正式准入，但有候选能力进入推荐集（需人工审核后才计入正式能力）。'
    }
    return '解析已完成，但本次没有任何能力标签准入。常见原因：本 JD 的能力主张未命中既有标签，'
      + '而新能力需不少于 3 条 JD 且不少于 2 家公司互相印证才会自动建标签（单条解析不会新建标签）。'
  }
  return '尚未解析。可点击本行「解析」单独跑这一条。'
}
