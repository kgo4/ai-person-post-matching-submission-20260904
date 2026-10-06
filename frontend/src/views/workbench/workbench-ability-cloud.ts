/**
 * 工作台「能力热度词云」图表配置。
 *
 * 与趋势折线共用 workbench-chart-theme，保证同一屏只有一套蓝：
 * 热度是有序数据（引用次数越多越热），所以按排名走同色系梯度（越热越深），
 * 而不是给每个能力随机上色 —— 后者会把"热度"这个可排序的量降级成无意义的花色。
 */
import type { EChartsOption } from 'echarts'
import { WORKBENCH_CHART as CHART } from './workbench-chart-theme'
import type { WorkbenchAbilityHeat } from './workbench-types'

const RAMP = [CHART.strong, CHART.primary, CHART.light, CHART.lighter]

/** 按名次取梯度色：index 0 为最热（最深），末尾最浅 */
export function abilityCloudColor(index: number, total: number): string {
  if (total <= 1) return RAMP[0]
  const slot = Math.min(RAMP.length - 1, Math.floor((index / total) * RAMP.length))
  return RAMP[slot]
}

export function buildAbilityCloudOption(heat: WorkbenchAbilityHeat[]): EChartsOption {
  return {
    tooltip: {
      show: true,
      borderWidth: 0,
      backgroundColor: 'rgba(22, 34, 63, 0.92)',
      textStyle: { color: '#ffffff', fontSize: 12 },
      formatter: (params: { name?: string; value?: number }) =>
        `${params.name}<br/>被 ${params.value ?? 0} 个岗位能力项引用`,
    },
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        keepAspect: false,
        left: 'center',
        top: 'center',
        width: '96%',
        height: '92%',
        sizeRange: [13, 40],
        rotationRange: [-30, 30],
        rotationStep: 15,
        gridSize: 8,
        drawOutOfBound: false,
        shrinkToFit: true,
        layoutAnimation: true,
        textStyle: {
          fontFamily: 'Inter, PingFang SC, Microsoft YaHei, sans-serif',
          fontWeight: 700,
        },
        emphasis: {
          focus: 'self',
          textStyle: { textShadowBlur: 4, textShadowColor: 'rgba(37, 99, 235, 0.35)' },
        },
        data: heat.map((item, index) => ({
          name: item.name,
          value: item.value,
          textStyle: { color: abilityCloudColor(index, heat.length) },
        })),
      },
    ] as never,
  } as EChartsOption
}
