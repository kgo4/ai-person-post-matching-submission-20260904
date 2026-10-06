import assert from 'node:assert/strict'
import {
  autoAnalyzeDetail,
  autoAnalyzeOverrideNote,
  autoAnalyzeSummary,
  dedupeResetConfirmText,
  describeAutoAnalyzeChange,
  describeDedupeReset,
  emptyRawHint,
  rawDrawerTitle,
  renderRawJdText,
} from './crawler-batch-ops.ts'

/* ============ 自动解析开关：短标签 ============ */
assert.equal(autoAnalyzeSummary(null), '自动解析状态未知', '取不到状态时必须说未知，不能猜')
assert.equal(autoAnalyzeSummary({ enabled: true }), '推送后自动解析')
assert.equal(autoAnalyzeSummary({ enabled: false }), '推送后手动解析')

/* ============ 自动解析开关：说明文案优先用后端 message ============ */
// 后端给了 message 就必须原样用，避免前端再维护一份会分叉的口径
assert.equal(
  autoAnalyzeDetail({ enabled: false, message: '后端口径：只入库不解析' }),
  '后端口径：只入库不解析',
)
// 后端 message 为空时才用兜底，且必须告诉用户「手动解析该去哪点」
const fallbackOff = autoAnalyzeDetail({ enabled: false, message: '  ' })
assert.ok(fallbackOff.includes('只入库'), `未开启应说明不解析，实际：${fallbackOff}`)
assert.ok(fallbackOff.includes('重新解析'), `未开启必须给出可操作的下一步，实际：${fallbackOff}`)
assert.ok(autoAnalyzeDetail(null).includes('刷新'), '状态未知时应提示刷新')
assert.ok(autoAnalyzeDetail({ enabled: true }).includes('解析链路'))

/* ============ 自动解析开关：覆盖角标 ============ */
assert.equal(autoAnalyzeOverrideNote(null), null)
assert.equal(autoAnalyzeOverrideNote({ overridden: false }), null, '非覆盖状态不该出现角标')
const noteOn = autoAnalyzeOverrideNote({ overridden: true, configuredDefault: true })
assert.ok(noteOn && noteOn.includes('重启'), `覆盖态必须说明会回落，实际：${noteOn}`)
assert.ok(noteOn && noteOn.includes('开启'), `角标要给出回落后的值，实际：${noteOn}`)
const noteOff = autoAnalyzeOverrideNote({ overridden: true, configuredDefault: false })
assert.ok(noteOff && noteOff.includes('关闭'), `实际：${noteOff}`)

/* ============ 自动解析开关：切换后的提示 ============ */
// 覆盖态：必须提醒「重启会回落」，否则用户会以为是持久化设置
const changeOn = describeAutoAnalyzeChange({ enabled: true, overridden: true })
assert.equal(changeOn.type, 'success')
assert.ok(changeOn.text.includes('重启'), `实际：${changeOn.text}`)
const changeOff = describeAutoAnalyzeChange({ enabled: false, overridden: true })
assert.equal(changeOff.type, 'info', '关闭不是「成功」，不该用 success 色')
assert.ok(changeOff.text.includes('重新解析'), `实际：${changeOff.text}`)
// 与配置默认一致（后端会清掉覆盖）→ 提示「已恢复配置默认」
const changeReset = describeAutoAnalyzeChange({ enabled: true, overridden: false })
assert.ok(changeReset.text.includes('配置默认'), `实际：${changeReset.text}`)
assert.equal(describeAutoAnalyzeChange(null).type, 'info')

/* ============ 重算去重 ============ */
assert.equal(describeDedupeReset(null).type, 'info')

const noRows = describeDedupeReset({ scannedRows: 0, duplicateHits: 0 })
assert.ok(noRows.text.includes('没有已入库的数据'), `实际：${noRows.text}`)

// 关键不变式：0 条重复是**成功结论**，必须说成「本来就没有重复」，不能说「已处理 0 条」
const clean = describeDedupeReset({ scannedRows: 12, hashRecomputed: 12, duplicateHits: 0 })
assert.equal(clean.type, 'success')
assert.ok(clean.text.includes('本来就没有重复'), `实际：${clean.text}`)
assert.ok(clean.text.includes('12'), `应报出扫描条数，实际：${clean.text}`)
assert.equal(/已处理\s*0\s*条/.test(clean.text), false, '不得出现「已处理 0 条」这类让人以为白跑的说法')

// 真有重复：要报出条数，并说明它们会被跳过解析
const dirty = describeDedupeReset({ scannedRows: 30, hashRecomputed: 30, duplicateHits: 7 })
assert.equal(dirty.type, 'success')
assert.ok(dirty.text.includes('7'), `实际：${dirty.text}`)
assert.ok(dirty.text.includes('跳过解析'), `实际：${dirty.text}`)
assert.ok(dirty.text.includes('30'), `实际：${dirty.text}`)

// 非爬虫来源不重算去重键（hashRecomputed=0）时，不要画蛇添足地提「重算了 0 条」
const manual = describeDedupeReset({ scannedRows: 5, hashRecomputed: 0, duplicateHits: 0 })
assert.ok(manual.text.includes('重判'), `应说清做了什么（按当前口径重判），实际：${manual.text}`)
assert.equal(manual.text.includes('重算了 0'), false, `不该出现「重算了 0 条」，实际：${manual.text}`)

// 确认文案：必须讲清「会改什么」并且带上批次号
const confirm = dedupeResetConfirmText('B-20260904')
assert.ok(confirm.includes('B-20260904'))
assert.ok(confirm.includes('恢复'), `实际：${confirm}`)
assert.ok(confirm.includes('重复'), `实际：${confirm}`)

/* ============ 推送原文 ============ */
const full = renderRawJdText({
  postName: '大模型算法工程师',
  companyName: '示例科技',
  city: '深圳',
  salaryRange: '30-60K',
  sourcePlatform: 'jd',
  externalId: 'JD-998877',
  publishedTime: '2026-09-04T09:00:00',
  batchNo: 'B-1',
  jobDescription: '负责大模型训练与推理优化',
  requirements: '熟悉 PyTorch',
  sourceUrl: 'https://example.com/job/1',
})
assert.ok(full.includes('大模型算法工程师'))
assert.ok(full.includes('JD-998877'), '外部ID 必须出现（核对「是不是我推的那条」靠它）')
assert.ok(full.includes('【岗位描述】') && full.includes('【任职要求】'))
assert.ok(full.includes('https://example.com/job/1'), '有链接时必须带上原始链接')

// 缺失字段用统一占位，且**不能**把空串显示成 `-`
const partial = renderRawJdText({ postName: '岗位A' })
assert.ok(partial.includes('（未提供）'))
assert.equal(partial.includes('【原始链接】'), false, '没有链接时不该出现空的链接段')
assert.equal(partial.startsWith('岗位：岗位A'), true, '首行应为岗位名')

// 空串与纯空白同样按缺失处理
assert.equal(renderRawJdText({ city: '   ' }).includes('城市：（未提供）'), true)

/* ============ 抽屉标题与空态 ============ */
assert.equal(rawDrawerTitle('B-1', '爬虫推送'), '推送原文 · 爬虫推送 · B-1')
assert.equal(rawDrawerTitle('B-1', null), '推送原文 · B-1', '没有通道名时不要留下多余的分隔符')
assert.equal(rawDrawerTitle('B-1', '  '), '推送原文 · B-1')

assert.equal(emptyRawHint(true, 0), '加载中…')
assert.equal(emptyRawHint(false, 0), '该批次没有已入库的 JD')
assert.equal(emptyRawHint(false, 40), '当前页没有数据', '总数大于 0 却空页，是页码问题而不是批次空')

console.log('crawler batch ops tests passed')
