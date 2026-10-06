export interface DimensionWeightValues {
  abilityWeight: number
  semanticWeight: number
  evidenceWeight: number
  aiWeight: number
}

export interface ScoringWeightUpdatePayload extends Partial<DimensionWeightValues> {
  whitelistBypassHardRules?: boolean
  l2MatchingMode?: 'LENIENT' | 'BALANCED' | 'STRICT'
  requiredSemanticThreshold?: number
  coreSemanticThreshold?: number
  optionalSemanticThreshold?: number
  similarTagMinimumConfidence?: number
  allowedLevelGap?: number
  coreCoverageThreshold?: number
  requiredCoverageThreshold?: number
  l2PassThreshold?: number
  aiTriggerThreshold?: number
}

export const L2_MODE_DEFAULTS = {
  LENIENT: { requiredSemanticThreshold: 0.75, coreSemanticThreshold: 0.72, optionalSemanticThreshold: 0.68, similarTagMinimumConfidence: 0.70, allowedLevelGap: 1, coreCoverageThreshold: 0.60, requiredCoverageThreshold: 0.60, l2PassThreshold: 55, aiTriggerThreshold: 50 },
  BALANCED: { requiredSemanticThreshold: 0.85, coreSemanticThreshold: 0.82, optionalSemanticThreshold: 0.78, similarTagMinimumConfidence: 0.80, allowedLevelGap: 0, coreCoverageThreshold: 0.80, requiredCoverageThreshold: 0.75, l2PassThreshold: 60, aiTriggerThreshold: 60 },
  STRICT: { requiredSemanticThreshold: 0.92, coreSemanticThreshold: 0.88, optionalSemanticThreshold: 0.85, similarTagMinimumConfidence: 0.90, allowedLevelGap: 0, coreCoverageThreshold: 1, requiredCoverageThreshold: 0.95, l2PassThreshold: 75, aiTriggerThreshold: 75 },
} as const

/** L2 策略参数（9 个阈值，与后端 ScoringWeightUpdateRequest 一一对应） */
export interface L2Params {
  requiredSemanticThreshold: number
  coreSemanticThreshold: number
  optionalSemanticThreshold: number
  similarTagMinimumConfidence: number
  allowedLevelGap: number
  coreCoverageThreshold: number
  requiredCoverageThreshold: number
  l2PassThreshold: number
  aiTriggerThreshold: number
}

export type L2Mode = 'LENIENT' | 'BALANCED' | 'STRICT'

export type L2NumericKey = keyof L2Params

/**
 * 阈值的量纲。
 *
 * 历史 bug 就出在这里没有统一：后端 `requiredSemanticThreshold` 受
 * `@DecimalMin("0.0") @DecimalMax("1.0")` 约束、默认 0.85，
 * 而页面上那个输入框写的是 `max=100 / step=1` —— 点一次「+」变成 1.85，
 * 保存必然被后端 400 拒绝。量纲放进纯逻辑并加断言，避免再各写一套。
 */
export type L2Scale = 'fraction' | 'level' | 'score'

export interface L2Field {
  key: L2NumericKey
  label: string
  min: number
  max: number
  step: number
  precision: number
  scale: L2Scale
}

/**
 * 9 个阈值按用途分三组。
 *
 * 平铺展示时使用者无法判断「核心语义阈值」与「核心覆盖率」谁先起作用，
 * 分组后每组回答一个问题：怎么算同一个能力项 / 覆盖率不够怎么办 / 什么情况才叫通过。
 */
export const L2_FIELD_GROUPS: ReadonlyArray<{ title: string; hint: string; fields: L2Field[] }> = [
  {
    title: '语义匹配门槛',
    hint: '决定「语义相近」到什么程度才认定为同一个能力项。',
    fields: [
      { key: 'requiredSemanticThreshold', label: '必填能力语义阈值', min: 0, max: 1, step: 0.01, precision: 2, scale: 'fraction' },
      { key: 'coreSemanticThreshold', label: '核心能力语义阈值', min: 0, max: 1, step: 0.01, precision: 2, scale: 'fraction' },
      { key: 'optionalSemanticThreshold', label: '普通能力语义阈值', min: 0, max: 1, step: 0.01, precision: 2, scale: 'fraction' },
      { key: 'similarTagMinimumConfidence', label: '相似标签最低置信度', min: 0, max: 1, step: 0.01, precision: 2, scale: 'fraction' },
    ],
  },
  {
    title: '覆盖率与等级差',
    hint: '决定岗位要求的能力被覆盖得不够时，是否直接判不通过，以及允许的等级容差。',
    fields: [
      { key: 'coreCoverageThreshold', label: '核心能力覆盖率', min: 0, max: 1, step: 0.05, precision: 2, scale: 'fraction' },
      { key: 'requiredCoverageThreshold', label: '必填能力覆盖率', min: 0, max: 1, step: 0.05, precision: 2, scale: 'fraction' },
      { key: 'allowedLevelGap', label: '允许等级差距', min: 0, max: 3, step: 1, precision: 0, scale: 'level' },
    ],
  },
  {
    title: '判定与 AI 触发',
    hint: 'L2 分层分达到通过线即为通过；低于 AI 触发分时不再引入 AI 深度分析。',
    fields: [
      { key: 'l2PassThreshold', label: 'L2 通过分', min: 0, max: 100, step: 1, precision: 0, scale: 'score' },
      { key: 'aiTriggerThreshold', label: 'AI 触发分', min: 0, max: 100, step: 1, precision: 0, scale: 'score' },
    ],
  },
]

export const L2_FIELDS: L2Field[] = L2_FIELD_GROUPS.flatMap((group) => group.fields)

/** 数字框后的量纲后缀：0–1 的小数要标出等价百分比，否则 0.85 与 85 容易看错 */
export function l2FieldSuffix(field: L2Field, value: number): string {
  if (field.scale === 'fraction') return `≈ ${Math.round(value * 100)}%`
  if (field.scale === 'level') return '级'
  return '分'
}

export const L2_MODE_LABELS: Record<L2Mode, string> = {
  LENIENT: '宽松模式',
  BALANCED: '均衡模式（默认）',
  STRICT: '严格模式',
}

/** 先铺该模式的默认值，再用已有配置逐项覆盖；缺项保留默认值，避免被 undefined 覆盖成空 */
export function resolveL2Params(mode: L2Mode, existing?: Partial<L2Params> | null, fallback: L2Mode = 'BALANCED'): L2Params {
  const safeMode: L2Mode = mode in L2_MODE_DEFAULTS ? mode : fallback
  const params: L2Params = { ...L2_MODE_DEFAULTS[safeMode] }
  for (const field of L2_FIELDS) {
    const value = existing?.[field.key]
    if (value != null && Number.isFinite(Number(value))) {
      params[field.key] = Number(value)
    }
  }
  return params
}

export const DEFAULT_DIMENSION_WEIGHTS: Readonly<DimensionWeightValues> = Object.freeze({
  abilityWeight: 65,
  semanticWeight: 15,
  evidenceWeight: 10,
  aiWeight: 10,
})

export function normalizeDimensionWeights(config: Partial<DimensionWeightValues>): DimensionWeightValues {
  // 兼容旧后端/历史配置返回的 0~1 小数；页面和保存接口统一使用百分比。
  const rawValues = [config.abilityWeight, config.semanticWeight, config.evidenceWeight, config.aiWeight]
    .filter((value): value is number => value != null && Number.isFinite(Number(value)))
    .map(Number)
  const legacyFractionScale = rawValues.length > 0
    && rawValues.every(value => value >= 0 && value <= 1)
    && rawValues.reduce((sum, value) => sum + value, 0) <= 1.0001
  const normalize = (value: number | undefined, fallback: number) => {
    if (value == null) return fallback
    const numeric = Number(value)
    return legacyFractionScale ? numeric * 100 : numeric
  }
  return {
    abilityWeight: normalize(config.abilityWeight, DEFAULT_DIMENSION_WEIGHTS.abilityWeight),
    semanticWeight: normalize(config.semanticWeight, DEFAULT_DIMENSION_WEIGHTS.semanticWeight),
    evidenceWeight: normalize(config.evidenceWeight, DEFAULT_DIMENSION_WEIGHTS.evidenceWeight),
    aiWeight: normalize(config.aiWeight, DEFAULT_DIMENSION_WEIGHTS.aiWeight),
  }
}

export function getWeightTotal(weights: DimensionWeightValues): number {
  return Math.round((weights.abilityWeight + weights.semanticWeight + weights.evidenceWeight + weights.aiWeight) * 100) / 100
}

export function validateDimensionWeights(weights: DimensionWeightValues): string | null {
  const values = Object.values(weights)
  if (values.some(value => !Number.isFinite(value) || value < 0 || value > 100)) return '权重必须在 0 到 100 之间'
  if (weights.aiWeight > 20) return 'AI 权重不能超过 20%'
  if (Math.abs(getWeightTotal(weights) - 100) > 0.0001) return `四项权重之和必须等于 100%，当前 ${getWeightTotal(weights).toFixed(2)}%`
  return null
}

export function buildScoringWeightUpdate(
  weights: DimensionWeightValues,
  whitelistBypassHardRules: boolean,
): ScoringWeightUpdatePayload {
  return {
    abilityWeight: normalizeWeightValue(weights.abilityWeight),
    semanticWeight: normalizeWeightValue(weights.semanticWeight),
    evidenceWeight: normalizeWeightValue(weights.evidenceWeight),
    aiWeight: normalizeWeightValue(weights.aiWeight),
    whitelistBypassHardRules,
  }
}

function normalizeWeightValue(value: number): number {
  return Number(Number(value).toFixed(4))
}
