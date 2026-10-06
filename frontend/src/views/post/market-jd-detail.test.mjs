/**
 * 单条市场 JD 解析结果展示逻辑的回归测试。
 *
 * 重点锁死四件事（每一条都对应一个真实会误导用户的失败方式）：
 * 1. 「没有能力标签」的四种原因不能被合并成一句「暂无数据」；
 * 2. `infraFailed` 不能被提示成成功（用户会以为数据丢了）；
 * 3. 反解不到标签名时不能显示空串（会读成「解析没产出能力」）；
 * 4. 分组顺序必须稳定：同类内保持后端顺序、分组按首次出现顺序。
 */
import assert from 'node:assert/strict'
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
} from './market-jd-detail.ts'

/* ---------------- 分类中文名：未知分类必须回显 ---------------- */

assert.equal(tagCategoryLabel('TECHNICAL'), '技术能力')
assert.equal(tagCategoryLabel('SOFT'), '软技能')
assert.equal(tagCategoryLabel('BUSINESS'), '业务能力')
assert.equal(tagCategoryLabel(null), '未分类')
assert.equal(tagCategoryLabel('  '), '未分类')
// 后端新增分类时不能被静默吞成「未分类」，否则没人发现前端漏配了
assert.equal(tagCategoryLabel('DOMAIN_KNOWLEDGE'), 'DOMAIN_KNOWLEDGE')

/* ---------------- 标签名：反解不到必须说明原因 ---------------- */

assert.equal(tagRefLabel({ tagId: 12, tagName: 'Java 并发' }), 'Java 并发')
assert.equal(tagRefLabel({ tagId: 12, tagName: '   ' }), '标签 #12（已删除或停用）')
assert.equal(tagRefLabel({ tagId: 12, tagName: null }), '标签 #12（已删除或停用）')
assert.equal(tagRefLabel({ tagId: null, tagName: 'Java' }), 'Java')
assert.equal(tagRefLabel(null), '未知标签')

/* ---------------- 层级：拿不到层级不能显示 L0 ---------------- */

assert.equal(tagLevelLabel(2), 'L2')
assert.equal(tagLevelLabel(0), '')
assert.equal(tagLevelLabel(null), '')
assert.equal(tagLevelLabel(undefined), '')
assert.equal(tagLevelLabel(-1), '')

/* ---------------- 分组：同类内保序 + 分组按首次出现顺序 ---------------- */

const grouped = groupTagsByCategory([
  { tagId: 1, tagName: 'Java', tagCategory: 'TECHNICAL', tagLevel: 1 },
  { tagId: 2, tagName: '沟通', tagCategory: 'SOFT', tagLevel: 2 },
  { tagId: 3, tagName: 'Spring', tagCategory: 'TECHNICAL', tagLevel: 2 },
  { tagId: 4, tagName: '领域知识', tagCategory: null, tagLevel: null },
])
assert.deepEqual(grouped.map((group) => group.category), ['TECHNICAL', 'SOFT', ''])
assert.deepEqual(grouped.map((group) => group.label), ['技术能力', '软技能', '未分类'])
assert.deepEqual(grouped[0].tags.map((tag) => tag.tagName), ['Java', 'Spring'])
assert.deepEqual(grouped[1].tags.map((tag) => tag.tagName), ['沟通'])
assert.deepEqual(grouped[2].tags.map((tag) => tag.tagName), ['领域知识'])

// 空输入与脏元素都要能扛住（后端返回 null 列表时页面不能崩）
assert.deepEqual(groupTagsByCategory([]), [])
assert.deepEqual(groupTagsByCategory(null), [])
assert.deepEqual(groupTagsByCategory(undefined), [])
assert.equal(groupTagsByCategory([null, undefined]).length, 0)

/* ---------------- 单条解析提示：失败不能被说成成功 ---------------- */

assert.deepEqual(
  singleAnalysisNotice({ analysisStatus: 1, message: '已准入 2 个既有能力标签' }),
  { type: 'success', text: '已准入 2 个既有能力标签' },
)
assert.deepEqual(
  singleAnalysisNotice({ analysisStatus: 0, infraFailed: true, message: '准入环节服务异常' }),
  { type: 'warning', text: '准入环节服务异常' },
)
// infraFailed 即使状态码是 1 也必须按失败提示（防御后端未来改动）
assert.deepEqual(
  singleAnalysisNotice({ analysisStatus: 1, infraFailed: true, message: 'x' }),
  { type: 'warning', text: 'x' },
)
assert.deepEqual(
  singleAnalysisNotice({ analysisStatus: 2, message: '被清洗判定为噪声' }),
  { type: 'info', text: '被清洗判定为噪声' },
)
// 后端没给 message 时回落，不能弹一个空 toast
assert.deepEqual(singleAnalysisNotice({ analysisStatus: 1 }), { type: 'success', text: '解析已提交' })
assert.deepEqual(singleAnalysisNotice(null, '解析失败'), { type: 'info', text: '解析失败' })

/* ---------------- 可解析性：只拦重复项 ---------------- */

assert.equal(singleAnalyzeBlockReason({ isDuplicate: 0, analysisStatus: 0 }), null)
assert.equal(singleAnalyzeBlockReason({ analysisStatus: 1 }), null)
// 已跳过 / 已分析都允许重跑 —— 这正是单条解析与批次解析的区别
assert.equal(singleAnalyzeBlockReason({ isDuplicate: 0, analysisStatus: 2 }), null)
assert.match(String(singleAnalyzeBlockReason({ isDuplicate: 1 })), /重复项/)

/* ---------------- 空结果归因：四种原因不能合并 ---------------- */

const hintUnanalyzed = emptyTagHint({ analysisStatus: 0, acceptedCount: 0 })
const hintSkipped = emptyTagHint({ analysisStatus: 2, acceptedCount: 0 })
const hintDuplicate = emptyTagHint({ analysisStatus: 0, isDuplicate: 1, acceptedCount: 0 })
const hintNoAdmit = emptyTagHint({ analysisStatus: 1, acceptedCount: 0 })
const hintCandidateOnly = emptyTagHint({ analysisStatus: 1, acceptedCount: 0, candidateCount: 3 })

assert.ok(hintUnanalyzed && hintUnanalyzed.includes('尚未解析'))
assert.ok(hintSkipped && hintSkipped.includes('跳过'))
assert.ok(hintDuplicate && hintDuplicate.includes('重复项'))
assert.ok(hintNoAdmit && hintNoAdmit.includes('3 条 JD'))
assert.ok(hintCandidateOnly && hintCandidateOnly.includes('推荐集'))

// 四者互不相同：一旦有人把它们「简化」成同一句话，这里立刻红
const hints = [hintUnanalyzed, hintSkipped, hintDuplicate, hintNoAdmit]
assert.equal(new Set(hints).size, 4, '四种「没有能力标签」的原因必须各自有独立文案')

// 重复项的判定优先于状态码：重复项状态停在 0，不能提示成「尚未解析，点解析就行」
assert.ok(hintDuplicate && !hintDuplicate.includes('尚未解析'))
// 有标签时不再显示空态说明
assert.equal(emptyTagHint({ analysisStatus: 1, acceptedCount: 2 }), null)

/* ---------------- AI 原始提取：没进正式标签库也必须看得见 ---------------- */

// 匹配状态：四种取值必须分开说。缺省值不能被说成「系统未收录」——
// 那是「明确判断过系统里没有」，而 null 是「根本没做匹配」，混了就变成假结论。
assert.equal(aiTagMatchLabel('MATCHED'), '已命中系统标签')
assert.equal(aiTagMatchLabel('SIMILAR'), '疑似相似')
assert.equal(aiTagMatchLabel('NEW'), '系统未收录')
assert.equal(aiTagMatchLabel(null), '未做匹配')
assert.equal(aiTagMatchLabel(undefined), '未做匹配')
assert.equal(aiTagMatchLabel('  matched  '), '已命中系统标签')
// 未知取值回显成中性说明而不是猜一个
assert.equal(aiTagMatchLabel('SOMETHING_ELSE'), '未做匹配')

assert.equal(aiTagDisplayName({ name: 'Java 并发' }), 'Java 并发')
assert.equal(aiTagDisplayName({ name: '   ' }), '（AI 未给出名称）')
assert.equal(aiTagDisplayName({}), '（AI 未给出名称）')
assert.equal(aiTagDisplayName(null), '（AI 未给出名称）')

assert.equal(aiTagTone('MATCHED'), 'success')
assert.equal(aiTagTone('SIMILAR'), 'warning')
assert.equal(aiTagTone('NEW'), 'info')
assert.equal(aiTagTone(null), 'info')

const aiSummary = summarizeAiTags([
  { name: 'A', matchStatus: 'MATCHED' },
  { name: 'B', matchStatus: 'MATCHED' },
  { name: 'C', matchStatus: 'SIMILAR' },
  { name: 'D', matchStatus: 'NEW' },
  { name: 'E' },
  null,
])
assert.deepEqual(aiSummary, { total: 5, matched: 2, similar: 1, unlisted: 2 })
assert.deepEqual(summarizeAiTags([]), { total: 0, matched: 0, similar: 0, unlisted: 0 })
assert.deepEqual(summarizeAiTags(null), { total: 0, matched: 0, similar: 0, unlisted: 0 })

const aiText = aiTagSummaryText(aiSummary)
assert.ok(aiText.includes('共 5 项'))
assert.ok(aiText.includes('命中系统标签 2 项'))
assert.ok(aiText.includes('系统未收录 2 项'))
// 某项为 0 时不该出现在摘要里（「疑似相似 0 项」是噪声）
assert.ok(!aiText.includes('疑似相似 0 项'))
assert.equal(aiTagSummaryText({ total: 0, matched: 0, similar: 0, unlisted: 0 }),
  'AI 没有从这条 JD 里提取到能力项')

// 第五种空态：AI 明明提取到了，只是没有一项进入正式标签库。
// 这是市场 JD 最常见的情形，必须与「AI 什么也没提取到」分开 ——
// 否则用户看到「没有任何能力标签准入」会认定解析坏了。
const hintAiExtracted = emptyTagHint({ analysisStatus: 1, acceptedCount: 0, aiTagCount: 12 })
assert.ok(hintAiExtracted && hintAiExtracted.includes('12 项'))
assert.ok(hintAiExtracted.includes('AI 原始提取'), '必须指向 AI 原始提取结果区块，否则用户不知道去哪儿看')
assert.notEqual(hintAiExtracted, hintNoAdmit)

// aiTagCount 为 0 时不能走 AI 分支（AI 什么都没提取到是另一回事）
assert.equal(emptyTagHint({ analysisStatus: 1, acceptedCount: 0, aiTagCount: 0 }), hintNoAdmit)

// 五种「没有能力标签」的原因必须两两不同
const allHints = [hintUnanalyzed, hintSkipped, hintDuplicate, hintNoAdmit, hintAiExtracted]
assert.equal(new Set(allHints).size, 5, '五种原因必须各自有独立文案')

// 有正式标签时不显示空态；AI 结果再多也不影响这条（有准入就展示准入结果）
assert.equal(emptyTagHint({ analysisStatus: 1, acceptedCount: 2, aiTagCount: 12 }), null)

console.log('market jd detail logic tests passed')
