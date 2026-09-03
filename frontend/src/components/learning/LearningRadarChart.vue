<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import type { NormalizedLearningGap } from '@/views/learning/path/learning-path'

const props = defineProps<{
  gaps: NormalizedLearningGap[]
  empName?: string
  postName?: string
  /** 最大等级，默认 5 */
  maxLevel?: number
}>()

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

const maxLevel = computed(() => props.maxLevel ?? 5)

const option = computed(() => {
  const indicator = props.gaps.map((gap) => ({
    name: gap.abilityName.length > 6
      ? gap.abilityName.slice(0, 6) + '…'
      : gap.abilityName,
    max: maxLevel.value,
  }))

  const currentData = props.gaps.map((g) =>
    Math.min(g.currentLevel, maxLevel.value),
  )
  const requiredData = props.gaps.map((g) =>
    Math.min(g.requiredLevel, maxLevel.value),
  )
  const gapData = props.gaps.map((g) =>
    Math.max(g.requiredLevel - g.currentLevel, 0),
  )

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151', fontSize: 12 },
      formatter: (params: any) => {
        if (!params || params.name == null) return ''
        const idx = indicator.findIndex((i) => i.name === params.name)
        if (idx < 0) return params.name
        const gap = props.gaps[idx]
        if (!gap) return params.name
        return `<b>${gap.abilityName}</b><br/>
          当前: <span style="color:#3b82f6;font-weight:700">L${gap.currentLevel}</span>
          → 目标: <span style="color:#f59e0b;font-weight:700">L${gap.requiredLevel}</span>
          <br/>差距: <span style="color:${gap.severity === 'danger' ? '#dc2626' : '#d97706'}">${gap.gapLevel} 级</span>`
      },
    },
    legend: {
      data: ['当前水平', '岗位要求'],
      bottom: 0,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: '#6b7280' },
    },
    radar: {
      center: ['50%', '48%'],
      radius: '62%',
      indicator,
      axisName: {
        color: '#374151',
        fontSize: 11,
        fontWeight: 500,
      },
      splitArea: {
        areaStyle: { color: ['#f9fafb', '#fff', '#f9fafb', '#fff', '#f9fafb'] },
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
    },
    series: [
      {
        name: '当前水平',
        type: 'radar',
        data: [{ value: currentData, name: '当前水平' }],
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: '#3b82f6', width: 2, type: 'solid' },
        areaStyle: { color: 'rgba(59,130,246,0.15)' },
        itemStyle: { color: '#3b82f6' },
      },
      {
        name: '岗位要求',
        type: 'radar',
        data: [{ value: requiredData, name: '岗位要求' }],
        symbol: 'diamond',
        symbolSize: 5,
        lineStyle: { color: '#f59e0b', width: 2, type: 'dashed' },
        areaStyle: { color: 'rgba(245,158,11,0.06)' },
        itemStyle: { color: '#f59e0b' },
      },
      ...(props.gaps.some((g) => g.gapLevel > 0)
        ? [
            {
              name: '能力差距',
              type: 'radar',
              data: [{ value: gapData, name: '能力差距' }],
              symbol: 'none',
              lineStyle: { color: '#ef4444', width: 1, type: 'dotted' as const },
              areaStyle: { color: 'rgba(239,68,68,0.08)' },
              itemStyle: { color: '#ef4444' },
            },
          ]
        : []),
    ],
  }
})

function initChart() {
  if (!chartRef.value || chartInstance) return
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(option.value, true)
}

function updateChart() {
  if (!chartInstance) {
    initChart()
    return
  }
  chartInstance.setOption(option.value, true)
}

function handleResize() {
  chartInstance?.resize()
}

watch(
  () => [props.gaps, props.maxLevel],
  () => nextTick(updateChart),
  { deep: true },
)

onMounted(() => {
  nextTick(initChart)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div class="radar-chart">
    <div v-if="gaps.length === 0" class="radar-chart__empty">
      暂无能力差距数据
    </div>
    <div
      v-else
      ref="chartRef"
      class="radar-chart__canvas"
    />
  </div>
</template>

<style scoped>
.radar-chart {
  width: 100%;
}

.radar-chart__canvas {
  width: 100%;
  height: 320px;
}

.radar-chart__empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #9ca3af;
}
</style>
