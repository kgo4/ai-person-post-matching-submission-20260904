/**
 * 运行审计的图表配置（纯函数）。
 *
 * 为什么单独成一个文件：图表配置是"数据 → 视觉"的映射，属于可断言的逻辑
 * （空数据要不要画、双轴有没有配、单位是不是 ms）。抽成不依赖 Vue 的纯函数后，
 * 可以用 node 直跑的 `.test.mjs` 断言，不必起浏览器。
 *
 * ⚠️ 本文件**刻意不引入任何相对路径的运行时依赖**：node 的 `--experimental-strip-types`
 * 解析不了无扩展名的相对导入（`./x` / `@/x`），只有 `import type` 会被整个擦除。
 * 所以这里只允许 `import type`，配色也内联在本文件（与 `workbench-chart-theme.ts`
 * 的白蓝基调一致，但为了上面的约束不直接引用它）。
 */
import type { EChartsOption } from 'echarts'
import type {
  LatencyPoint,
  PromptPoint,
  RagScenarioPoint,
  RagTrendPoint,
  RuntimeMetrics,
} from '@/api'

/** 白蓝基调，与工作台图表同源（有序用蓝色梯度、无序用中性灰）。 */
const COLOR = {
  primary: '#2563eb',
  strong: '#1d4ed8',
  light: '#60a5fa',
  lighter: '#a8c6f5',
  neutral: '#cbd5e1',
  danger: '#e11d48',
  axis: '#8b95ab',
  split: '#eef2fb',
} as const

/** 图表容器高度：同屏图表统一高度，避免 210/260/280 混排。 */
export const AUDIT_CHART_HEIGHT = '240px'

/**
 * 横向柱状图的容器高度随条目数变化。
 *
 * 固定高度在条目变多时会把柱子挤成一团（ECharts 不会自动撑开容器），
 * 所以按 32px/条估算并夹在 240–420px 之间。
 */
export function chartHeightForRows(count?: number): string {
  const rows = count ?? 0
  return `${Math.min(420, Math.max(240, rows * 32 + 60))}px`
}

const AXIS = {
  axisLine: { lineStyle: { color: COLOR.split } },
  axisLabel: { color: COLOR.axis, fontSize: 11 },
  axisTick: { show: false },
} as const

const GRID = { left: 8, right: 12, top: 28, bottom: 4, containLabel: true } as const

/** 统一的空数据兜底：不让图表画出一片空白还不给解释。 */
function emptyOption(message: string): EChartsOption {
  return {
    title: {
      text: message,
      left: 'center',
      top: 'center',
      textStyle: { color: COLOR.axis, fontSize: 12, fontWeight: 'normal' },
    },
  }
}

/**
 * Token 消耗结构（环形）。
 *
 * 只放输入/输出两段 —— `total` 是二者之和，画进环里会让比例失真。
 */
export function tokenDonutOption(metrics: RuntimeMetrics | null): EChartsOption {
  const input = Math.max(0, metrics?.inputTokens ?? 0)
  const output = Math.max(0, metrics?.outputTokens ?? 0)
  if (input + output === 0) {
    return emptyOption('暂未采集到 token 消耗')
  }
  return {
    tooltip: { trigger: 'item', valueFormatter: (v: unknown) => `${Number(v).toLocaleString('zh-CN')} tokens` },
    legend: { bottom: 0, textStyle: { color: COLOR.axis, fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          position: 'center',
          formatter: () => `${(input + output).toLocaleString('zh-CN')}\n合计`,
          color: COLOR.strong,
          fontSize: 13,
          lineHeight: 18,
        },
        emphasis: { label: { show: true } },
        data: [
          { name: '输入 token', value: input, itemStyle: { color: COLOR.primary } },
          { name: '输出 token', value: output, itemStyle: { color: COLOR.lighter } },
        ],
      },
    ],
  }
}

/** LLM / 工具的调用量与失败数（分组柱状）。 */
export function callOutcomeOption(metrics: RuntimeMetrics | null): EChartsOption {
  const groups = [
    { name: 'LLM 调用', calls: metrics?.llmCallCount ?? 0, failed: metrics?.llmErrorCount ?? 0, cache: 0 },
    { name: '工具调用', calls: metrics?.toolCallCount ?? 0, failed: metrics?.toolErrorCount ?? 0, cache: metrics?.toolCacheHitCount ?? 0 },
  ]
  if (groups.every(g => g.calls === 0)) {
    return emptyOption('暂未采集到调用记录')
  }
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, textStyle: { color: COLOR.axis, fontSize: 11 } },
    grid: GRID,
    xAxis: { type: 'category', data: groups.map(g => g.name), ...AXIS },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: COLOR.split } }, ...AXIS },
    series: [
      {
        name: '调用次数',
        type: 'bar',
        barMaxWidth: 36,
        itemStyle: { color: COLOR.primary, borderRadius: [3, 3, 0, 0] },
        data: groups.map(g => g.calls),
      },
      {
        name: '失败次数',
        type: 'bar',
        barMaxWidth: 36,
        itemStyle: { color: COLOR.danger, borderRadius: [3, 3, 0, 0] },
        data: groups.map(g => g.failed),
      },
      {
        name: '缓存命中',
        type: 'bar',
        barMaxWidth: 36,
        itemStyle: { color: COLOR.neutral, borderRadius: [3, 3, 0, 0] },
        data: groups.map(g => g.cache),
      },
    ],
  }
}

/**
 * LLM 调用量与响应时间趋势（柱 + 双折线，双 Y 轴）。
 *
 * 调用量是指标量、耗时是毫秒 —— 量纲不同，必须分开左右轴，否则调用量会把耗时压成一条直线。
 */
export function latencyTrendOption(points: LatencyPoint[] | undefined): EChartsOption {
  const rows = points ?? []
  if (rows.length === 0) {
    return emptyOption('所选窗口内没有 LLM 调用记录')
  }
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: COLOR.axis, fontSize: 11 } },
    grid: GRID,
    xAxis: { type: 'category', boundaryGap: true, data: rows.map(p => p.hour), ...AXIS },
    yAxis: [
      { type: 'value', name: '次', nameTextStyle: { color: COLOR.axis, fontSize: 11 }, minInterval: 1, splitLine: { lineStyle: { color: COLOR.split } }, ...AXIS },
      { type: 'value', name: 'ms', nameTextStyle: { color: COLOR.axis, fontSize: 11 }, splitLine: { show: false }, ...AXIS },
    ],
    series: [
      {
        name: '调用量',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: COLOR.lighter, borderRadius: [3, 3, 0, 0] },
        data: rows.map(p => p.callCount),
      },
      {
        name: '平均耗时',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbolSize: 6,
        lineStyle: { width: 2, color: COLOR.primary },
        itemStyle: { color: COLOR.primary },
        data: rows.map(p => p.avgLatencyMs),
      },
      {
        name: '最大耗时',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbolSize: 4,
        lineStyle: { width: 1.5, color: COLOR.neutral, type: 'dashed' },
        itemStyle: { color: COLOR.neutral },
        data: rows.map(p => p.maxLatencyMs),
      },
    ],
  }
}

/** 各 Prompt 的平均耗时（横向柱状，bar 越长越慢；tooltip 带调用量与失败数）。 */
export function promptBreakdownOption(points: PromptPoint[] | undefined): EChartsOption {
  const rows = [...(points ?? [])].sort((a, b) => b.avgLatencyMs - a.avgLatencyMs)
  if (rows.length === 0) {
    return emptyOption('所选窗口内没有 Prompt 调用记录')
  }
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const list = Array.isArray(params) ? params : [params]
        const first = list[0] as { dataIndex: number } | undefined
        const row = first ? rows[first.dataIndex] : undefined
        if (!row) return ''
        return `${row.promptName}<br/>平均耗时 ${row.avgLatencyMs} ms（最大 ${row.maxLatencyMs} ms）`
          + `<br/>调用 ${row.callCount} 次，失败 ${row.failedCount} 次`
      },
    },
    grid: { left: 8, right: 24, top: 12, bottom: 4, containLabel: true },
    xAxis: { type: 'value', name: 'ms', nameTextStyle: { color: COLOR.axis, fontSize: 11 }, splitLine: { lineStyle: { color: COLOR.split } }, ...AXIS },
    yAxis: { type: 'category', inverse: true, data: rows.map(p => p.promptName), ...AXIS },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: COLOR.primary, borderRadius: [0, 3, 3, 0] },
        data: rows.map(p => p.avgLatencyMs),
      },
    ],
  }
}

/** RAG 检索量与延迟趋势（柱 + 双折线）。 */
export function ragLatencyTrendOption(points: RagTrendPoint[] | undefined): EChartsOption {
  const rows = points ?? []
  if (rows.length === 0) {
    return emptyOption('所选窗口内没有 RAG 检索记录')
  }
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: COLOR.axis, fontSize: 11 } },
    grid: GRID,
    xAxis: { type: 'category', boundaryGap: true, data: rows.map(p => p.hour), ...AXIS },
    yAxis: [
      { type: 'value', name: '次', nameTextStyle: { color: COLOR.axis, fontSize: 11 }, minInterval: 1, splitLine: { lineStyle: { color: COLOR.split } }, ...AXIS },
      { type: 'value', name: 'ms / 命中', nameTextStyle: { color: COLOR.axis, fontSize: 11 }, splitLine: { show: false }, ...AXIS },
    ],
    series: [
      {
        name: '检索量',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: COLOR.lighter, borderRadius: [3, 3, 0, 0] },
        data: rows.map(p => p.queryCount),
      },
      {
        name: '平均延迟',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbolSize: 6,
        lineStyle: { width: 2, color: COLOR.primary },
        itemStyle: { color: COLOR.primary },
        data: rows.map(p => p.avgLatencyMs),
      },
      {
        name: '平均命中数',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbolSize: 4,
        lineStyle: { width: 1.5, color: COLOR.neutral, type: 'dashed' },
        itemStyle: { color: COLOR.neutral },
        data: rows.map(p => p.avgHitCount),
      },
    ],
  }
}

/** 各 RAG 场景的检索量（柱状）；场景多时横向柱更易读。 */
export function ragScenarioOption(points: RagScenarioPoint[] | undefined): EChartsOption {
  const rows = [...(points ?? [])].sort((a, b) => a.queryCount - b.queryCount)
  if (rows.length === 0) {
    return emptyOption('所选窗口内没有 RAG 场景数据')
  }
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const list = Array.isArray(params) ? params : [params]
        const first = list[0] as { dataIndex: number } | undefined
        const row = first ? rows[first.dataIndex] : undefined
        if (!row) return ''
        return `${row.scenario}<br/>检索 ${row.queryCount} 次`
          + `<br/>平均延迟 ${row.avgLatencyMs} ms，平均命中 ${row.avgHitCount} 条`
      },
    },
    grid: { left: 8, right: 24, top: 12, bottom: 4, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: COLOR.split } }, ...AXIS },
    yAxis: { type: 'category', data: rows.map(p => p.scenario), ...AXIS },
    series: [
      {
        name: '检索量',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: COLOR.primary, borderRadius: [0, 3, 3, 0] },
        data: rows.map(p => p.queryCount),
      },
    ],
  }
}
