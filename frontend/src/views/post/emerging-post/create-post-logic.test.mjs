/**
 * create-post-logic 纯逻辑测试（`node --experimental-strip-types` 直跑）
 *
 * 覆盖：候选→表单初值、能力项转换、可提交校验、证据摘要、按钮显隐互斥。
 */
import assert from 'node:assert/strict'
import {
  DEFAULT_MIN_LEVEL,
  DEFAULT_TAG_CATEGORY,
  DEFAULT_WEIGHT,
  buildCreateFormFromCandidate,
  buildEvidenceSummary,
  dedupe,
  shouldOfferEvolution,
  shouldOfferManualCreate,
  toAbilityItem,
  validateCreateForm,
} from './create-post-logic.ts'

/* ---------------- 1. 候选 → 表单初值 ---------------- */
{
  const candidate = {
    candidateName: ' AI 应用工程师 ',
    coreAbilities: ['Python', '  LangChain ', '', '向量数据库'],
    evidenceSummary: '来自 12 条市场 JD 的能力共现',
  }
  const form = buildCreateFormFromCandidate(candidate)

  assert.equal(form.postName, 'AI 应用工程师', '候选名应 trim 后作为初值')
  assert.equal(form.description, '来自 12 条市场 JD 的能力共现', '描述取自证据摘要')
  assert.equal(form.abilities.length, 3, '空白能力名应被剔除')
  assert.deepEqual(
    form.abilities.map((a) => a.suggestedName),
    ['Python', 'LangChain', '向量数据库'],
  )
}

{
  const form = buildCreateFormFromCandidate(null)
  assert.equal(form.postName, '', 'null 候选 → 空表单')
  assert.deepEqual(form.abilities, [])
  assert.equal(form.description, '')
}

{
  // 无 evidenceSummary 时回退到 differentiationReason
  const form = buildCreateFormFromCandidate({
    candidateName: 'X',
    coreAbilities: [],
    differentiationReason: '与既有岗位差异显著',
  })
  assert.equal(form.description, '与既有岗位差异显著', '应回退到差异化理由')
}

/* ---------------- 2. 能力项转换 ---------------- */
{
  const item = toAbilityItem('  Kubernetes ')
  assert.equal(item.suggestedName, 'Kubernetes', '应 trim')
  assert.equal(item.tagCategory, DEFAULT_TAG_CATEGORY)
  assert.equal(item.minRequiredLevel, DEFAULT_MIN_LEVEL)
  assert.equal(item.weight, DEFAULT_WEIGHT)
  assert.equal(item.matchStatus, 'NEW', '新建岗位的能力项应标为 NEW')
  assert.equal(item.isRequired, 1)
}

/* ---------------- 3. 可提交校验 ---------------- */
{
  const ok = { postName: 'AI 应用工程师', description: '', abilities: [toAbilityItem('Python')] }
  assert.equal(validateCreateForm(ok), null, '合法表单应通过')

  assert.equal(validateCreateForm(null), '表单未初始化')

  assert.equal(
    validateCreateForm({ ...ok, postName: '' }),
    '请填写正式岗位名称',
    '空岗位名必须拦截',
  )
  assert.equal(
    validateCreateForm({ ...ok, postName: '   ' }),
    '请填写正式岗位名称',
    '纯空白岗位名必须拦截',
  )
  assert.equal(
    validateCreateForm({ ...ok, postName: 'x'.repeat(101) }),
    '岗位名称不能超过 100 个字符',
  )
  assert.equal(
    validateCreateForm({ ...ok, abilities: [] }),
    '至少需要一项能力要求',
    '无能力项必须拦截',
  )
  assert.equal(
    validateCreateForm({ ...ok, abilities: [toAbilityItem(''), toAbilityItem('Java')] }),
    '存在未填写名称的能力项，请补全或删除',
  )
}

/* ---------------- 4. 证据摘要 ---------------- */
{
  const s = buildEvidenceSummary({
    evidencePostNames: ['AI 工程师', 'AI 工程师', ' 算法工程师 '],
    sourceTitles: ['行业白皮书.pdf', '行业白皮书.pdf'],
    sourceRefs: [1, 2, 3],
    riskFlags: ['样本量偏少', '样本量偏少'],
  })

  assert.deepEqual(s.postNames, ['AI 工程师', '算法工程师'], '应去重 + trim')
  assert.deepEqual(s.sourceTitles, ['行业白皮书.pdf'])
  assert.equal(s.evidenceCount, 3, '证据条数取 sourceRefs 长度')
  assert.deepEqual(s.riskFlags, ['样本量偏少'])
}

{
  const s = buildEvidenceSummary(null)
  assert.deepEqual(s.postNames, [])
  assert.deepEqual(s.sourceTitles, [])
  assert.equal(s.evidenceCount, 0)
  assert.deepEqual(s.riskFlags, [])
}

/* ---------------- 5. dedupe ---------------- */
assert.deepEqual(dedupe([]), [])
assert.deepEqual(dedupe(['', '  ', 'a', 'a', ' b ']), ['a', 'b'], '应去空白与重复并保序')
assert.deepEqual(dedupe(['x', 'y', 'z']), ['x', 'y', 'z'], '应保持原顺序')

/* ---------------- 6. 按钮显隐互斥（关键：不再两个按钮同时出现） ---------------- */
{
  assert.equal(shouldOfferManualCreate({ recommendedAction: 'EMERGING_POST_REVIEW' }), true)
  assert.equal(shouldOfferManualCreate({ recommendedAction: 'POST_EVOLUTION' }), false)
  assert.equal(shouldOfferManualCreate(null), false)
  assert.equal(shouldOfferManualCreate({}), false, '未给动作时不应显示创建按钮')

  assert.equal(shouldOfferEvolution({ recommendedAction: 'POST_EVOLUTION' }), true)
  assert.equal(shouldOfferEvolution({ recommendedAction: 'EMERGING_POST_REVIEW' }), false)
  assert.equal(shouldOfferEvolution(null), false)

  // 互斥性：任一动作下两个按钮至多出现一个
  for (const action of ['POST_EVOLUTION', 'EMERGING_POST_REVIEW', undefined]) {
    const c = { recommendedAction: action }
    const both = shouldOfferManualCreate(c) && shouldOfferEvolution(c)
    assert.equal(both, false, `动作=${action} 时两个按钮不应同时出现`)
  }
}

console.log('create-post-logic.test.mjs: 全部断言通过')
