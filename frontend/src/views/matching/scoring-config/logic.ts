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
