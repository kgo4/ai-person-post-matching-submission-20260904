<script setup lang="ts">
/**
 * 能力匹配对比图谱组件
 * 使用D3.js绘制能力图谱与岗位要求的对比可视化
 */
import { ref, onMounted, watch, nextTick } from 'vue'
import * as d3 from 'd3'

interface CompareData {
  dimension: string
  employeeScore: number
  postRequirement: number
  maxValue?: number
}

interface Props {
  data: CompareData[]
  width?: number
  height?: number
}

const props = withDefaults(defineProps<Props>(), {
  width: 700,
  height: 400,
})

const chartRef = ref<HTMLDivElement>()

function draw() {
  if (!chartRef.value || props.data.length === 0) return

  d3.select(chartRef.value).selectAll('*').remove()

  const margin = { top: 40, right: 30, bottom: 60, left: 60 }
  const innerWidth = props.width - margin.left - margin.right
  const innerHeight = props.height - margin.top - margin.bottom

  const svg = d3
    .select(chartRef.value)
    .append('svg')
    .attr('width', props.width)
    .attr('height', props.height)
    .append('g')
    .attr('transform', `translate(${margin.left},${margin.top})`)

  const x0 = d3
    .scaleBand()
    .domain(props.data.map((d) => d.dimension))
    .range([0, innerWidth])
    .padding(0.3)

  const x1 = d3.scaleBand().domain(['employee', 'post']).range([0, x0.bandwidth()]).padding(0.1)

  const y = d3
    .scaleLinear()
    .domain([0, d3.max(props.data, (d) => Math.max(d.employeeScore, d.postRequirement)) || 100])
    .nice()
    .range([innerHeight, 0])

  // X轴
  svg
    .append('g')
    .attr('transform', `translate(0,${innerHeight})`)
    .call(d3.axisBottom(x0))
    .selectAll('text')
    .style('font-size', '12px')

  // Y轴
  svg.append('g').call(d3.axisLeft(y)).selectAll('text').style('font-size', '12px')

  // 柱状图
  const groups = svg
    .selectAll('.group')
    .data(props.data)
    .join('g')
    .attr('class', 'group')
    .attr('transform', (d) => `translate(${x0(d.dimension)},0)`)

  // 员工得分柱
  groups
    .append('rect')
    .attr('x', x1('employee') || 0)
    .attr('y', (d) => y(d.employeeScore))
    .attr('width', x1.bandwidth())
    .attr('height', (d) => innerHeight - y(d.employeeScore))
    .attr('fill', '#409eff')
    .attr('rx', 2)

  // 岗位要求柱
  groups
    .append('rect')
    .attr('x', x1('post') || 0)
    .attr('y', (d) => y(d.postRequirement))
    .attr('width', x1.bandwidth())
    .attr('height', (d) => innerHeight - y(d.postRequirement))
    .attr('fill', '#67c23a')
    .attr('rx', 2)

  // 图例
  const legend = svg.append('g').attr('transform', `translate(${innerWidth - 160}, -20)`)

  legend
    .append('rect')
    .attr('width', 14)
    .attr('height', 14)
    .attr('fill', '#409eff')
    .attr('rx', 2)

  legend.append('text').attr('x', 20).attr('y', 12).text('员工能力').style('font-size', '12px')

  legend
    .append('rect')
    .attr('x', 80)
    .attr('width', 14)
    .attr('height', 14)
    .attr('fill', '#67c23a')
    .attr('rx', 2)

  legend.append('text').attr('x', 100).attr('y', 12).text('岗位要求').style('font-size', '12px')
}

onMounted(() => {
  nextTick(draw)
})

watch(() => props.data, draw, { deep: true })
</script>

<template>
  <div ref="chartRef" class="post-match-compare-graph" />
</template>

<style scoped>
.post-match-compare-graph {
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
