<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { MatchingTask } from '@/api/matching/types'
import type { EmployeeRecommendation, PostRecommendation } from '@/api/recommend'
import type { MatchMode } from '../logic'

type Candidate = PostRecommendation | EmployeeRecommendation

const props = defineProps<{
  items: Candidate[]
  previewGenerated: boolean
  mode: MatchMode
  enableAi: boolean
  /** 静态阶段（无任务时由父级 computed 提供：0 待选 / 1 硬条件 / 2 L2 / 3 L3） */
  activeStage: number
  taskStatus: MatchingTask | null
  /** 任务是否已完成 */
  done: boolean
  taskResultCount: number
}>()

const el = ref<HTMLDivElement | null>(null)
let instance: echarts.ECharts | null = null

const PAL = {
  dim: '#334155', dimBg: '#1e293b', active: '#3b82f6', pass: '#10b981',
  warn: '#f59e0b', fail: '#ef4444', ai: '#8b5cf6', edge: '#06b6d4',
  cyan: '#22d3ee', white: '#f8fafc',
}

/** 任务进行中时按 progress 映射阶段：<33 L1，<66 L2，<100 L3 */
function effectiveStage(): number {
  const t = props.taskStatus
  if (t && t.status === 1) {
    if (t.progress < 33) return 1
    if (t.progress < 66) return 2
    return 3
  }
  return props.activeStage
}

function render() {
  if (!el.value) return
  if (!instance) instance = echarts.init(el.value)

  const items = props.items
  const hasData = props.previewGenerated && items.length > 0
  const hasAI = props.enableAi
  const stg = effectiveStage()
  const done = props.done
  const l1Passed = items.filter(i => i.hardConditionStatus === 'PASS').length
  const l1Failed = items.filter(i => i.hardConditionStatus === 'FAIL').length
  const l2Avg = items.length > 0
    ? Math.round(items.reduce((s, i) => s + (i.l2PreviewScore || i.recommendScore || 0), 0) / items.length)
    : 0

  const l1Color = hasData ? (l1Failed === 0 ? PAL.pass : l1Failed < items.length ? PAL.warn : PAL.fail) : PAL.dim
  const l2Color = hasData ? (l2Avg >= 80 ? PAL.pass : l2Avg >= 60 ? PAL.active : PAL.warn) : PAL.dim
  const l3Color = hasAI ? (done ? PAL.pass : props.taskStatus ? PAL.ai : PAL.dim) : '#0f172a'
  const srcColor = hasData ? PAL.active : PAL.dim
  const outColor = done ? PAL.pass : PAL.dim
  const isActive = (idx: number) => stg === idx && idx > 0

  const node = (id: string, big: string, title: string, sub: string, color: string, act: boolean, symSize: number) => ({
    id, big, sub,
    symbol: 'roundRect', symbolSize: [symSize || 120, 72],
    itemStyle: {
      color: act ? color + '20' : 'transparent',
      borderColor: color, borderWidth: act ? 2.5 : 1.5, borderType: act ? 'solid' : 'dashed',
      shadowBlur: act ? 16 : 0, shadowColor: act ? color + '60' : 'transparent', shadowOffsetY: 2,
    },
    label: {
      show: true, position: 'inside',
      formatter: act ? `{a|${big}}\n{b|${title}}` : `{c|${big}}\n{d|${title}}`,
      rich: {
        a: { fontSize: 24, fontFamily: 'JetBrains Mono', fontWeight: 'bold', color: PAL.white, padding: [8, 0, 0, 0], align: 'center' },
        b: { fontSize: 13, fontFamily: 'Noto Sans SC', fontWeight: 'bold', color, padding: [4, 0, 0, 0], align: 'center' },
        c: { fontSize: 22, fontFamily: 'JetBrains Mono', color: PAL.dim, padding: [8, 0, 0, 0], align: 'center' },
        d: { fontSize: 12, fontFamily: 'Noto Sans SC', color: PAL.dim, padding: [2, 0, 0, 0], align: 'center' },
      },
    },
    tooltip: { show: false },
  })

  instance.setOption({
    backgroundColor: 'transparent',
    animationDuration: 800, animationEasing: 'cubicOut',
    series: [{
      type: 'graph',
      layout: 'force',
      force: { repulsion: 280, edgeLength: [240, 300], gravity: 0.08, layoutAnimation: true, friction: 0.6 },
      roam: true, draggable: true,
      center: ['50%', '50%'],
      symbolKeepAspect: true,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 12],
      lineStyle: { curveness: 0.12, opacity: 0.7 },
      cursor: 'grab',
      data: [
        node('src', hasData ? `${items.length}` : '—', props.mode === 'PERSON_TO_POSTS' ? '候选人' : '岗 位', hasData ? `${items.length} 项` : '待选择', srcColor, isActive(0), 120),
        node('l1', hasData ? `${l1Passed}` : '—', 'L1 硬条件', hasData ? (l1Failed > 0 ? `${l1Failed} 未通过` : '全部通过') : '待加载', l1Color, isActive(1), 130),
        node('l2', hasData ? `${l2Avg}` : '—', 'L2 能力评分', hasData ? `均分 ${l2Avg} 分` : '待评估', l2Color, isActive(2), 130),
        node('l3', hasAI ? (done ? '✓' : '…') : '⊗', 'L3 AI 分析', hasAI ? (done ? '分析完成' : props.taskStatus ? '进行中...' : '等待中') : '已关闭', l3Color, isActive(3), 120),
        node('out', done ? `${props.taskResultCount}` : '—', '匹配结果', done ? `${props.taskResultCount} 条记录` : '等待中', outColor, isActive(4), 120),
      ],
      links: [
        { source: 'src', target: 'l1', lineStyle: { color: hasData ? PAL.cyan : PAL.dim, width: hasData ? 3 : 1, opacity: hasData ? 0.9 : 0.3 } },
        { source: 'l1', target: 'l2', lineStyle: { color: hasData && l1Passed > 0 ? PAL.cyan : PAL.dim, width: hasData && l1Passed > 0 ? 3 : 1, opacity: hasData && l1Passed > 0 ? 0.9 : 0.3 } },
        ...(hasAI
          ? [{ source: 'l2', target: 'l3', lineStyle: { color: hasData && hasAI ? PAL.ai : PAL.dim, width: hasData && hasAI ? 2.5 : 1, opacity: hasData && hasAI ? 0.8 : 0.3 } },
             { source: 'l3', target: 'out', lineStyle: { color: done ? PAL.pass : PAL.dim, width: done ? 3 : 1, opacity: done ? 0.9 : 0.3 } }]
          : [{ source: 'l2', target: 'out', lineStyle: { color: hasData ? PAL.cyan : PAL.dim, width: hasData ? 3 : 1, opacity: hasData ? 0.9 : 0.3 } }]),
      ],
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 4 },
        itemStyle: { shadowBlur: 20, shadowColor: '#06b6d4' },
      },
    }],
  }, true)
}

watch(
  () => [props.previewGenerated, props.items.length, props.taskResultCount, props.enableAi, props.activeStage, props.mode, props.taskStatus, props.done],
  () => { nextTick(render) },
)
onMounted(() => { nextTick(render) })
onUnmounted(() => { instance?.dispose() })
</script>

<template>
  <div ref="el" class="pipeline-chart"></div>
</template>

<style scoped>
.pipeline-chart { width: 100%; height: 280px; min-height: 240px; }
</style>
