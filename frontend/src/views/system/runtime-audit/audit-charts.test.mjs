import assert from 'node:assert/strict'

import {
  AUDIT_CHART_HEIGHT,
  callOutcomeOption,
  chartHeightForRows,
  latencyTrendOption,
  promptBreakdownOption,
  ragLatencyTrendOption,
  ragScenarioOption,
  tokenDonutOption,
} from './audit-charts.ts'

/**
 * 运行审计图表配置的断言。
 *
 * 这些断言只做「能证明配置真的对」的事，不写 `assert.ok(option)` 这种空断言：
 *  - 空数据必须落到**空状态提示**，而不是画一张什么都没有的空图；
 *  - 量纲不同的序列必须分左右轴（调用量是"次"、耗时是"ms"），否则图会被压平；
 *  - 排序方向要对（最慢的 Prompt 在最上面、检索量最少的场景在最下面）；
 *  - 容器高度要夹在合理区间，否则条目一多柱子会挤成一团。
 */

/* --------------------------------- 空数据兜底 --------------------------------- */

const EMPTY_CASES = [
  ['token', tokenDonutOption(null)],
  ['调用量', callOutcomeOption(null)],
  ['LLM 趋势', latencyTrendOption([])],
  ['Prompt 耗时', promptBreakdownOption(undefined)],
  ['RAG 趋势', ragLatencyTrendOption([])],
  ['RAG 场景', ragScenarioOption([])],
]

for (const [name, option] of EMPTY_CASES) {
  const text = option?.title?.text ?? ''
  assert.equal(typeof text, 'string', `${name}：空数据必须给出 title 文案`)
  assert.ok(text.length > 0, `${name}：空数据的 title 不能是空字符串`)
  assert.ok(
    /暂无|没有|未采集|没有采集/.test(text),
    `${name}：空状态文案要说清"没有数据"，实际是「${text}」`,
  )
  assert.ok(
    !option.series || option.series.length === 0,
    `${name}：空数据不应再带 series（否则会画出误导性的空坐标系）`,
  )
}

/* ---------------------------------- Token 环形 ---------------------------------- */

{
  const option = tokenDonutOption({ inputTokens: 300, outputTokens: 100, totalTokens: 400 })
  const pie = option.series[0]
  assert.equal(pie.type, 'pie', 'token 结构图应当是环形（pie + inner radius）')
  assert.ok(Array.isArray(pie.radius) && pie.radius.length === 2, '环形要给出内外半径')
  assert.equal(pie.data.length, 2, '只画输入/输出两段；total 是二者之和，画进去会让比例失真')
  assert.equal(pie.data[0].value, 300)
  assert.equal(pie.data[1].value, 100)
  assert.ok(
    String(pie.label.formatter()).includes('400'),
    '圆心应当展示合计 token（400）',
  )
}

/* --------------------------------- 调用量 vs 失败 --------------------------------- */

{
  const option = callOutcomeOption({
    llmCallCount: 20,
    llmErrorCount: 2,
    toolCallCount: 8,
    toolErrorCount: 1,
    toolCacheHitCount: 5,
  })
  const names = option.series.map(s => s.name)
  assert.deepEqual(names, ['调用次数', '失败次数', '缓存命中'], '三组序列的名称与顺序要稳定')
  assert.deepEqual(option.series[0].data, [20, 8], '调用次数按 LLM / 工具两组')
  assert.deepEqual(option.series[1].data, [2, 1], '失败次数按 LLM / 工具两组')
  assert.deepEqual(option.series[2].data, [0, 5], '只有工具有缓存命中，LLM 侧应为 0')
}

/* --------------------------------- LLM 趋势双轴 --------------------------------- */

{
  const option = latencyTrendOption([
    { hour: '09:00', callCount: 3, avgLatencyMs: 1200, maxLatencyMs: 3000, failedCount: 0, cacheHitCount: 1 },
    { hour: '10:00', callCount: 5, avgLatencyMs: 900, maxLatencyMs: 2500, failedCount: 1, cacheHitCount: 2 },
  ])
  assert.deepEqual(option.xAxis.data, ['09:00', '10:00'], 'X 轴是时段标签（不含日期）')
  assert.ok(Array.isArray(option.yAxis), '调用量（次）与耗时（ms）量纲不同，必须双 Y 轴')
  assert.equal(option.yAxis.length, 2)
  assert.equal(option.yAxis[0].name, '次')
  assert.equal(option.yAxis[1].name, 'ms')

  const byName = Object.fromEntries(option.series.map(s => [s.name, s]))
  assert.deepEqual(byName['调用量'].data, [3, 5])
  assert.equal(byName['调用量'].yAxisIndex ?? 0, 0, '调用量走左轴')
  assert.deepEqual(byName['平均耗时'].data, [1200, 900])
  assert.equal(byName['平均耗时'].yAxisIndex, 1, '耗时走右轴，否则会被调用量的量纲压平')
  assert.deepEqual(byName['最大耗时'].data, [3000, 2500])
}

/* ------------------------------- Prompt 耗时排序 ------------------------------- */

{
  const option = promptBreakdownOption([
    { promptName: 'fast', callCount: 10, avgLatencyMs: 100, maxLatencyMs: 200, failedCount: 0 },
    { promptName: 'slow', callCount: 2, avgLatencyMs: 5000, maxLatencyMs: 9000, failedCount: 1 },
    { promptName: 'mid', callCount: 4, avgLatencyMs: 800, maxLatencyMs: 1500, failedCount: 0 },
  ])
  assert.deepEqual(
    option.yAxis.data,
    ['slow', 'mid', 'fast'],
    '按平均耗时降序；配合 inverse 让最慢的排在最上面',
  )
  assert.equal(option.yAxis.inverse, true, '横向柱要 inverse，否则最慢的会掉到最下面')
  assert.deepEqual(option.series[0].data, [5000, 800, 100], '柱子数据顺序必须与 Y 轴类目一致')
}

/* ---------------------------------- RAG 图表 ---------------------------------- */

{
  const option = ragLatencyTrendOption([
    { hour: '09:00', queryCount: 2, avgLatencyMs: 300, maxLatencyMs: 500, avgHitCount: 3.5, degradedCount: 0 },
  ])
  assert.deepEqual(option.xAxis.data, ['09:00'])
  assert.equal(option.yAxis.length, 2, 'RAG 趋势同样需要双轴（次 / ms 与命中数）')
  const names = option.series.map(s => s.name)
  assert.deepEqual(names, ['检索量', '平均延迟', '平均命中数'])
}

{
  const option = ragScenarioOption([
    { scenario: 'b', queryCount: 9, avgLatencyMs: 100, avgHitCount: 2 },
    { scenario: 'a', queryCount: 3, avgLatencyMs: 200, avgHitCount: 1 },
  ])
  assert.deepEqual(option.yAxis.data, ['a', 'b'], '横向柱按检索量升序，让最多的排在最上面')
  assert.deepEqual(option.series[0].data, [3, 9], '柱子数据顺序必须与 Y 轴类目一致')
}

/* ------------------------------------ 高度 ------------------------------------ */

assert.equal(chartHeightForRows(0), '240px', '没有条目时用最小高度')
assert.equal(chartHeightForRows(3), '240px', '少量条目仍用最小高度（3*32+60=156 → 夹到 240）')
assert.equal(chartHeightForRows(6), '252px', '6*32+60=252，落在区间内按实际值')
assert.equal(chartHeightForRows(100), '420px', '条目很多时封顶，不让图表无限撑高')
assert.equal(AUDIT_CHART_HEIGHT, '240px', '同屏图表统一高度是一个约定值')

console.log('audit-charts.test.mjs passed')
