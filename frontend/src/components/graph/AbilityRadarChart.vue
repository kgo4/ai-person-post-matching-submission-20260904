<script setup lang="ts">
/**
 * 能力雷达图组件 v2
 * 现代化设计：渐变填充、发光数据点、动画入场、多色轴线
 */
import { ref, onMounted, watch, nextTick } from 'vue'
import * as d3 from 'd3'

interface AbilityData {
  axis: string
  value: number
  maxValue?: number
}

interface Props {
  data: AbilityData[]
  width?: number
  height?: number
  maxValue?: number
  levels?: number
}

const props = withDefaults(defineProps<Props>(), {
  width: 440,
  height: 440,
  maxValue: 100,
  levels: 5,
})

const chartRef = ref<HTMLDivElement>()

// 每轴颜色
const axisColors = [
  '#2563eb', '#059669', '#d97706', '#dc2626', '#7c3aed',
  '#0891b2', '#ea580c', '#4f46e5', '#0d9488', '#be185d',
]

function draw() {
  if (!chartRef.value || props.data.length === 0) return

  d3.select(chartRef.value).selectAll('*').remove()

  const total = props.data.length
  if (total < 3) return

  const margin = 90
  const radius = Math.min(props.width, props.height) / 2 - margin
  const angleSlice = (Math.PI * 2) / total

  const svg = d3
    .select(chartRef.value)
    .append('svg')
    .attr('width', props.width)
    .attr('height', props.height)
    .attr('viewBox', `0 0 ${props.width} ${props.height}`)
    .style('overflow', 'visible')

  // defs - 渐变和发光滤镜
  const defs = svg.append('defs')

  // 发光滤镜
  const glowFilter = defs.append('filter').attr('id', 'radarGlow')
  glowFilter.append('feGaussianBlur').attr('stdDeviation', '3').attr('result', 'blur')
  glowFilter.append('feMerge')
    .selectAll('feMergeNode')
    .data(['blur', 'SourceGraphic'])
    .enter().append('feMergeNode').attr('in', d => d)

  // 面积渐变
  const areaGrad = defs.append('linearGradient')
    .attr('id', 'radarAreaGrad')
    .attr('x1', '0%').attr('y1', '0%')
    .attr('x2', '100%').attr('y2', '100%')
  areaGrad.append('stop').attr('offset', '0%').attr('stop-color', '#2563eb').attr('stop-opacity', 0.35)
  areaGrad.append('stop').attr('offset', '50%').attr('stop-color', '#06b6d4').attr('stop-opacity', 0.2)
  areaGrad.append('stop').attr('offset', '100%').attr('stop-color', '#7c3aed').attr('stop-opacity', 0.15)

  // 描边渐变
  const strokeGrad = defs.append('linearGradient')
    .attr('id', 'radarStrokeGrad')
    .attr('x1', '0%').attr('y1', '0%')
    .attr('x2', '100%').attr('y2', '100%')
  strokeGrad.append('stop').attr('offset', '0%').attr('stop-color', '#2563eb')
  strokeGrad.append('stop').attr('offset', '100%').attr('stop-color', '#06b6d4')

  const g = svg.append('g')
    .attr('transform', `translate(${props.width / 2},${props.height / 2})`)

  // 背景微光环
  g.append('circle')
    .attr('r', radius + 8)
    .attr('fill', 'none')
    .attr('stroke', 'rgba(37,99,235,0.06)')
    .attr('stroke-width', 16)

  // 网格圆环 - 从外向内
  for (let level = props.levels; level >= 1; level--) {
    const r = (radius / props.levels) * level
    const opacity = 0.04 + (level / props.levels) * 0.06
    g.append('circle')
      .attr('r', r)
      .attr('fill', level % 2 === 0 ? 'rgba(37,99,235,0.03)' : 'none')
      .attr('stroke', `rgba(37,99,235,${opacity})`)
      .attr('stroke-width', level === props.levels ? '1.5' : '0.8')
      .attr('stroke-dasharray', level === props.levels ? '' : '3,3')
  }

  // 轴线
  props.data.forEach((_, i) => {
    const angle = angleSlice * i - Math.PI / 2
    g.append('line')
      .attr('x1', 0).attr('y1', 0)
      .attr('x2', radius * Math.cos(angle))
      .attr('y2', radius * Math.sin(angle))
      .attr('stroke', 'rgba(148,163,184,0.2)')
      .attr('stroke-width', '1')
  })

  // 数据区域
  const areaPath = d3.lineRadial<AbilityData>()
    .radius(d => (d.value / (d.maxValue || props.maxValue)) * radius)
    .angle((_, i) => angleSlice * i)
    .curve(d3.curveCardinalClosed.tension(0.3))

  // 填充区域
  g.append('path')
    .datum(props.data)
    .attr('d', areaPath as any)
    .attr('fill', 'url(#radarAreaGrad)')
    .attr('stroke', 'url(#radarStrokeGrad)')
    .attr('stroke-width', '2.5')
    .attr('stroke-linejoin', 'round')
    .attr('filter', 'url(#radarGlow)')
    .style('opacity', 0)
    .transition()
    .duration(800)
    .ease(d3.easeCubicOut)
    .style('opacity', 1)

  // 数据点
  props.data.forEach((d, i) => {
    const angle = angleSlice * i - Math.PI / 2
    const r = (d.value / (d.maxValue || props.maxValue)) * radius
    const cx = r * Math.cos(angle)
    const cy = r * Math.sin(angle)
    const color = axisColors[i % axisColors.length]

    // 外发光环
    g.append('circle')
      .attr('cx', cx).attr('cy', cy).attr('r', 0)
      .attr('fill', 'none')
      .attr('stroke', color)
      .attr('stroke-width', '7')
      .attr('stroke-opacity', 0.2)
      .transition().duration(600).delay(400 + i * 80)
      .attr('r', 7)

    // 实心点
    g.append('circle')
      .attr('cx', cx).attr('cy', cy).attr('r', 0)
      .attr('fill', color)
      .attr('stroke', '#fff')
      .attr('stroke-width', '3')
      .transition().duration(400).delay(300 + i * 80)
      .ease(d3.easeBackOut)
      .attr('r', 6)
  })

  // 百分比标签
  props.data.forEach((d, i) => {
    const angle = angleSlice * i - Math.PI / 2
    const r = (d.value / (d.maxValue || props.maxValue)) * radius
    const labelR = r > 30 ? r + 18 : r + 26
    g.append('text')
      .attr('x', labelR * Math.cos(angle))
      .attr('y', labelR * Math.sin(angle))
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'middle')
      .attr('font-size', '11px')
      .attr('font-weight', '700')
      .attr('fill', axisColors[i % axisColors.length])
      .attr('opacity', 0)
      .text(`${d.value}`)
      .transition().duration(400).delay(600 + i * 80)
      .attr('opacity', 1)
  })

  // 轴标签（带截断）
  props.data.forEach((d, i) => {
    const angle = angleSlice * i - Math.PI / 2
    const labelR = radius + 34
    const x = labelR * Math.cos(angle)
    const y = labelR * Math.sin(angle)
    const anchor =
      Math.abs(x) < 20 ? 'middle' :
      x > 0 ? 'start' : 'end'

    // 长标签截断，hover显示完整文字
    const maxLen = 8
    const displayLabel = d.axis.length > maxLen ? d.axis.slice(0, maxLen) + '…' : d.axis

    const textEl = g.append('text')
      .attr('x', x)
      .attr('y', y)
      .attr('text-anchor', anchor)
      .attr('dominant-baseline', 'middle')
      .attr('font-size', '12px')
      .attr('font-weight', '600')
      .attr('fill', '#334155')
      .attr('letter-spacing', '0.01em')
      .attr('opacity', 0)
      .text(displayLabel)

    // 完整文字作为 tooltip
    if (d.axis.length > maxLen) {
      textEl.append('title').text(d.axis)
    }

    textEl.transition().duration(400).delay(200 + i * 60)
      .attr('opacity', 1)
  })
}

onMounted(() => nextTick(draw))
watch(() => props.data, () => nextTick(draw), { deep: true })
watch(() => [props.width, props.height], () => nextTick(draw))
</script>

<template>
  <div ref="chartRef" class="ability-radar-chart" />
</template>

<style scoped>
.ability-radar-chart {
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: visible;
}
.ability-radar-chart :deep(svg) {
  overflow: visible;
}
</style>
