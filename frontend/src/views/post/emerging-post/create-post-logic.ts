/**
 * 新兴岗位「人工创建」的纯逻辑。
 *
 * 背景：市场 JD 统计页发现的新兴岗位，按 `recommendedAction` 分两条路：
 *  - `POST_EVOLUTION`       → 与既有岗位相似，走「岗位演化审核」（已有实现）
 *  - `EMERGING_POST_REVIEW` → 与既有岗位差距大，需**人工查看证据后决定是否创建新岗位**
 *
 * 后一条原先只有一个弹提示的假按钮（不跳转、不调接口）。本模块提供该流程所需的纯函数：
 * 候选 → 创建表单初值、能力项转换、可提交校验、证据摘要文案。
 *
 * 纯函数、无 Vue / 请求依赖，可被 `node --experimental-strip-types` 直跑测试。
 */
import type { EmergingPostDiscovery } from '@/api/emerging-post'
import type { JdAbilityItem } from '@/api/post/types'

/** 创建岗位表单 */
export interface CreatePostForm {
  /** 正式岗位名称（人工填写/确认） */
  postName: string
  /** 岗位描述 */
  description: string
  /** 能力要求项 */
  abilities: JdAbilityItem[]
}

/** 单条能力项在创建表单里的可编辑形态（比 JdAbilityItem 宽松，便于人工微调） */
export interface EditableAbility {
  suggestedName: string
  tagCategory: string
  minRequiredLevel: number
  weight: number
  isCore: number
  isRequired: number
  reasoning: string
}

/** 默认分类（后端枚举口径） */
export const DEFAULT_TAG_CATEGORY = 'TECH'

/** 默认能力等级（1~5） */
export const DEFAULT_MIN_LEVEL = 2

/** 默认权重（百分比口径，与岗位模型一致） */
export const DEFAULT_WEIGHT = 20

/**
 * 由候选生成创建表单初值。
 * <p>
 * 关键：**候选名只是初值**，正式岗位名必须由人工确认 —— 候选名是系统按能力社区
 * 自动生成的方向名，不等于正式岗位名称（与页面顶部「无需填写岗位名称」的说明一致）。
 */
export function buildCreateFormFromCandidate(
  candidate: EmergingPostDiscovery | null | undefined,
): CreatePostForm {
  const names = (candidate?.coreAbilities || [])
    .map((n) => (n || '').trim())
    .filter((n) => n.length > 0)

  return {
    postName: (candidate?.candidateName || '').trim(),
    description: (candidate?.evidenceSummary || candidate?.differentiationReason || '').trim(),
    abilities: names.map((name) => toAbilityItem(name)),
  }
}

/** 能力名 → 能力项（给出保守默认值，人工可在对话框里调整） */
export function toAbilityItem(name: string): JdAbilityItem {
  return {
    suggestedName: name.trim(),
    tagCategory: DEFAULT_TAG_CATEGORY,
    minRequiredLevel: DEFAULT_MIN_LEVEL,
    weight: DEFAULT_WEIGHT,
    isCore: 0,
    isRequired: 1,
    reasoning: '来自市场 JD 新兴岗位候选的证据聚合',
    matchStatus: 'NEW',
  }
}

/**
 * 表单可提交校验。
 * @returns 错误提示文案；null 表示校验通过
 */
export function validateCreateForm(form: CreatePostForm | null | undefined): string | null {
  if (!form) return '表单未初始化'
  const name = (form.postName || '').trim()
  if (!name) return '请填写正式岗位名称'
  if (name.length > 100) return '岗位名称不能超过 100 个字符'
  if (!form.abilities || form.abilities.length === 0) return '至少需要一项能力要求'
  const hasBlank = form.abilities.some((a) => !a.suggestedName || !a.suggestedName.trim())
  if (hasBlank) return '存在未填写名称的能力项，请补全或删除'
  return null
}

/**
 * 证据摘要 —— 供人工在创建前判断。
 * <p>
 * 刻意区分「真实岗位名」与「资料标题」：前者来自市场 JD 的岗位字段，
 * 后者来自上传文件名，**不是岗位名**，只能作溯源（沿用该页既有口径）。
 */
export interface EvidenceSummary {
  postNames: string[]
  sourceTitles: string[]
  evidenceCount: number
  riskFlags: string[]
}

export function buildEvidenceSummary(
  candidate: EmergingPostDiscovery | null | undefined,
): EvidenceSummary {
  return {
    postNames: dedupe(candidate?.evidencePostNames || []),
    sourceTitles: dedupe(candidate?.sourceTitles || []),
    evidenceCount: (candidate?.sourceRefs || []).length,
    riskFlags: dedupe(candidate?.riskFlags || []),
  }
}

/** 去重 + 去空白（保序） */
export function dedupe(values: readonly string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const v of values) {
    const t = (v || '').trim()
    if (!t || seen.has(t)) continue
    seen.add(t)
    out.push(t)
  }
  return out
}

/**
 * 该候选是否应展示「进入人工创建审核」按钮。
 * <p>
 * 只有 `EMERGING_POST_REVIEW`（与既有岗位差距大、需人工决策）才展示。
 * `POST_EVOLUTION` 走「进入岗位演化审核」，两者**互斥**，避免同一弹窗出现两个指向同一动作的按钮。
 */
export function shouldOfferManualCreate(
  candidate: { recommendedAction?: string } | null | undefined,
): boolean {
  return candidate?.recommendedAction === 'EMERGING_POST_REVIEW'
}

/** 该候选是否应展示「进入岗位演化审核」按钮 */
export function shouldOfferEvolution(
  candidate: { recommendedAction?: string } | null | undefined,
): boolean {
  return candidate?.recommendedAction === 'POST_EVOLUTION'
}
