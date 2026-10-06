<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import type { EChartsOption } from 'echarts'
import { canInitializeChart } from './chartVisibility'

const props = withDefaults(defineProps<{
  option: EChartsOption
  width?: string
  height?: string
  autoResize?: boolean
  onChartClick?: (params: unknown) => void
}>(), {
  width: '100%',
  height: '300px',
  autoResize: true,
})

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

function initChart() {
  if (!canInitializeChart(chartRef.value) || chartInstance) return
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(props.option)
  chartInstance.on('click', (params: unknown) => props.onChartClick?.(params))
}

function updateChart() {
  if (!chartInstance) {
    initChart()
  }
  if (!chartInstance) return
  chartInstance.setOption(props.option, true)
}

function handleResize() {
  if (!chartInstance) {
    initChart()
    return
  }
  if (canInitializeChart(chartRef.value)) {
    chartInstance.resize()
  }
}

function observeContainer() {
  if (!chartRef.value || !props.autoResize || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => {
    handleResize()
  })
  resizeObserver.observe(chartRef.value)
}

watch(() => props.option, () => {
  nextTick(updateChart)
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    initChart()
    observeContainer()
  })
  if (props.autoResize) {
    window.addEventListener('resize', handleResize)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div ref="chartRef" :style="{ width, height }" />
</template>
