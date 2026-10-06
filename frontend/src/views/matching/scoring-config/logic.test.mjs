import assert from 'node:assert/strict'
import {
  DEFAULT_DIMENSION_WEIGHTS,
  L2_FIELDS,
  L2_FIELD_GROUPS,
  L2_MODE_DEFAULTS,
  buildScoringWeightUpdate,
  getWeightTotal,
  l2FieldSuffix,
  resolveL2Params,
  validateDimensionWeights,
} from './logic.ts'

assert.equal(getWeightTotal(DEFAULT_DIMENSION_WEIGHTS), 100)
assert.equal(validateDimensionWeights(DEFAULT_DIMENSION_WEIGHTS), null)
assert.equal(
  validateDimensionWeights({ ...DEFAULT_DIMENSION_WEIGHTS, aiWeight: 25, abilityWeight: 60 }),
  'AI 权重不能超过 20%',
)
assert.match(
  validateDimensionWeights({ ...DEFAULT_DIMENSION_WEIGHTS, abilityWeight: 60 }),
  /必须等于 100%/,
)

const percentagePayload = buildScoringWeightUpdate(
  { abilityWeight: 65, semanticWeight: 15, evidenceWeight: 10, aiWeight: 10 },
  true,
)
assert.deepEqual(
  percentagePayload,
  { abilityWeight: 65, semanticWeight: 15, evidenceWeight: 10, aiWeight: 10, whitelistBypassHardRules: true },
)

/* ---------------- L2 阈值控件的量纲（历史 bug 回归） ----------------
 * 后端 requiredSemanticThreshold 受 @DecimalMin("0.0") @DecimalMax("1.0") 约束、默认 0.85，
 * 页面一度写成 max=100 / step=1 —— 点一次「+」变成 1.85，保存必然被后端 400 拒绝。
 * 这里断言「控件范围必须与量纲一致」，并且模式默认值都必须落在范围内。 */
assert.equal(L2_FIELDS.length, 9, 'L2 阈值应为 9 项（与后端 ScoringWeightUpdateRequest 对齐）')
assert.equal(L2_FIELD_GROUPS.length, 3, '阈值应按用途分为三组')
assert.equal(
  new Set(L2_FIELDS.map(field => field.key)).size,
  L2_FIELDS.length,
  '字段 key 不得重复（重复会让 v-model 写错目标）',
)

for (const field of L2_FIELDS) {
  assert.ok(field.min <= field.max, `${field.key} 的 min 不得大于 max`)
  if (field.scale === 'fraction') {
    assert.ok(field.max <= 1, `${field.key} 是 0–1 的小数阈值，控件上限不能超过 1`)
    assert.ok(field.step < 1, `${field.key} 的步长必须小于 1，否则一次点击就会越过合法范围`)
    assert.ok(field.precision >= 2, `${field.key} 需要保留 2 位小数`)
  }
  if (field.scale === 'level') {
    assert.equal(field.max, 3, '等级差距上限与后端 @DecimalMax("3") 一致')
    assert.equal(field.precision, 0, '等级差距是整数级')
  }
  if (field.scale === 'score') {
    assert.equal(field.max, 100, '分值是 0–100')
  }
  // 三套预设的默认值都必须落在各自控件允许的范围内
  for (const [mode, defaults] of Object.entries(L2_MODE_DEFAULTS)) {
    const value = defaults[field.key]
    assert.ok(
      value >= field.min && value <= field.max,
      `${mode} 模式下 ${field.key}=${value} 超出控件范围 [${field.min}, ${field.max}]`,
    )
  }
}

assert.equal(l2FieldSuffix(L2_FIELDS.find(f => f.key === 'coreSemanticThreshold'), 0.82), '≈ 82%')
assert.equal(l2FieldSuffix(L2_FIELDS.find(f => f.key === 'allowedLevelGap'), 1), '级')
assert.equal(l2FieldSuffix(L2_FIELDS.find(f => f.key === 'l2PassThreshold'), 60), '分')

/* ---------------- 载入配置：缺项保留模式默认值 ---------------- */

// 后端只返回部分字段时，缺的字段必须用该模式的默认值补上，而不是变成 undefined
const partial = resolveL2Params('STRICT', { l2PassThreshold: 80 })
assert.equal(partial.l2PassThreshold, 80, '后端返回值应覆盖默认值')
assert.equal(partial.coreSemanticThreshold, L2_MODE_DEFAULTS.STRICT.coreSemanticThreshold, '缺项应保留模式默认值')
assert.equal(partial.allowedLevelGap, L2_MODE_DEFAULTS.STRICT.allowedLevelGap)

// 全量返回时逐项覆盖
const full = resolveL2Params('LENIENT', { ...L2_MODE_DEFAULTS.LENIENT, l2PassThreshold: 42 })
assert.equal(full.l2PassThreshold, 42)
assert.equal(full.requiredSemanticThreshold, L2_MODE_DEFAULTS.LENIENT.requiredSemanticThreshold)

// 非法 mode 兜底到 fallback，不让页面拿到 undefined 集合
const fallback = resolveL2Params('UNKNOWN_MODE', null)
assert.deepEqual(fallback, { ...L2_MODE_DEFAULTS.BALANCED })

// null / 非法数值不得写进表单（NaN 会让 el-input-number 显示为空）
const dirty = resolveL2Params('BALANCED', { l2PassThreshold: Number.NaN, aiTriggerThreshold: undefined })
assert.equal(dirty.l2PassThreshold, L2_MODE_DEFAULTS.BALANCED.l2PassThreshold)
assert.equal(dirty.aiTriggerThreshold, L2_MODE_DEFAULTS.BALANCED.aiTriggerThreshold)

console.log('matching scoring config logic tests passed')
