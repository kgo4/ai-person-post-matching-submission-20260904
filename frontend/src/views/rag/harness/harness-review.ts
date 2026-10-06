/**
 * 人员评估最终审核（assessment 模式）与岗位能力巡检（records 模式）的纯逻辑。
 *
 * <p>背景：原页面把「人员审核明细」塞进主表的展开行里（内层子表 min-width: 1480px、
 * 操作列 width 300 + min-width 260px 的按钮簇），外层又加了 `overflow-x: auto` +
 * `> .el-table { min-width: 760px }`，于是形成**双层横向滚动容器**：
 * 真正的横向滚动发生在外层容器而不是 el-table 自己的滚动体，固定列的 sticky 参照系错位，
 * 右侧的「详情 / 通过并入库 / 驳回 / 处理」和大量字段被裁掉，用户点不到也看不到。
 *
 * <p>重构后明细改由抽屉里的卡片列表承载（无横向滚动），本模块承载
 * 「谁能批量通过」「要求列怎么显示」这类纯计算，避免安全规则散落在模板里无法验证。
 */

export interface HarnessAiAcceptCandidate {
  id: number
  decision: string
  riskLevel: string
  isSelfEvidence?: number | null
  /** 调用方必须用 constants.resolveReviewStatus 解析后传入，避免同一条规则两处实现。 */
  reviewStatus: string
}

/**
 * 可按 AI 建议直接通过的判定：仍待人工审核 + AI 判定 PASS + 非高风险 + 不是自证据。
 * 少任何一个条件都必须留给人工逐条审核 —— 这是「批量通过」唯一的安全边界。
 */
export const isAiAcceptEligible = (item: HarnessAiAcceptCandidate): boolean =>
  item.reviewStatus === 'PENDING'
  && item.decision === 'PASS'
  && item.riskLevel !== 'HIGH'
  && item.isSelfEvidence !== 1

/**
 * 批量通过白名单。返回 id 数组而不是布尔值，前端据此显示「按 AI 建议通过 (n)」的 n，
 * 保证**按钮上的数字与实际提交的条数永远一致**（历史上这两个数曾各算各的）。
 */
export const selectAiAcceptIds = (items: readonly HarnessAiAcceptCandidate[]): number[] =>
  items.filter(isAiAcceptEligible).map((item) => item.id)

export interface PersonCountsLike {
  totalCount: number
  pendingCount: number
  blockCount: number
  passCount: number
}

/** 审核抽屉头部的统计副标题。 */
export const describePersonCounts = (group: PersonCountsLike): string =>
  `能力 ${group.totalCount} · 待审核 ${group.pendingCount} · 已拦截 ${group.blockCount} · 已通过 ${group.passCount}`

export interface AbilityRequirementLike {
  minRequiredLevel?: number | null
  weight?: number | null
}

const formatWeight = (weight: number): string | null => {
  if (!Number.isFinite(weight)) return null
  return Number.isInteger(weight) ? String(weight) : String(Math.round(weight * 100) / 100)
}

/**
 * 巡检明细「要求」列文案：等级与权重合并成一列（原来两列各占 70px，抽屉里被挤到要点）。
 * 两者都缺时给「—」占位，避免空单元格让整列看起来像渲染失败。
 * 注意 weight = 0 是**有效值**（权重确实为 0），只有 null/undefined/NaN 才当作缺失。
 */
export const abilityRequirementText = (item: AbilityRequirementLike): string => {
  const level = item.minRequiredLevel == null ? null : `L${item.minRequiredLevel}`
  const weight = item.weight == null ? null : formatWeight(item.weight)
  const parts = [level, weight === null ? null : `${weight} 分`].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '—'
}

export interface InspectionPostLike {
  abilityCount?: number | null
  riskyCount?: number | null
  highCount?: number | null
  aiSourceCount?: number | null
}

/** 巡检抽屉工具栏的「风险 / 能力」文案。 */
export const describeInspectionPost = (post: InspectionPostLike): string =>
  `${post.riskyCount || 0} 项风险 / ${post.abilityCount || 0} 项能力`

export interface InspectionAbilityLike {
  abilityName?: string | null
  riskTags?: string[] | null
  aiSource?: boolean | number | null
  evidenceText?: string | null
}

/*
 * 巡检明细原先是一张 7 列表格：能力名称(170) + 要求(126) + 核心(64) + 来源(76)
 * + 风险提示(190) + 证据(200) + 操作(116) = 最小 942px，
 * 而抽屉是 920px、去掉内边距只剩约 880px —— 表格必然横向溢出，
 * 固定右侧的「操作」列直接压在「证据」列上，这就是「信息被遮挡」的根因。
 * 改成卡片列表后不再有列宽竞争，下面三个函数承载卡片上的三个判断。
 */

/** 能力名缺失时显式标注，避免渲染成一张看起来像加载失败的空白卡片。 */
export const inspectionAbilityName = (item: InspectionAbilityLike): string =>
  item.abilityName?.trim() || '(空)'

/**
 * 来源只能二选一：AI 抽取 / 人工配置。
 * 用真值判断而不是 `=== 1`，是因为后端这个字段在不同链路里出现过 boolean 与 tinyint 两种形态，
 * 写死 `=== 1` 会把 true 误判成「人工」。
 */
export const inspectionAbilitySourceLabel = (item: InspectionAbilityLike): string =>
  item.aiSource ? 'AI生成' : '人工'

/** 证据缺失给明确占位：留空会被读成「这项能力没有依据」，与「没填」是两回事。 */
export const inspectionAbilityEvidence = (item: InspectionAbilityLike): string =>
  item.evidenceText?.trim() || '未提供证据'
