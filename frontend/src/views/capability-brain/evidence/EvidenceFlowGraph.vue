<script setup lang="ts">
/**
 * 证据流可视化：主体 → 能力（按证据强度着色）→ 支撑证据（按来源类型）
 * 直观呈现「每一项能力信息都有证据支撑」的完整链路。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { canInitializeChart } from '@/components/chart/chartVisibility'
import type { EvidenceChain } from '@/api/contest'
import { formatDate, getEvidenceStrength, getSourceTypeText, getStatusText, getStatusType, scoreText } from './utils'

const props = withDefaults(defineProps<{
  chainData: EvidenceChain | null
  selectedAbilityId?: number | null
}>(), {
  selectedAbilityId: null,
})

const emit = defineEmits<{ (e: 'select-ability', abilityId: number): void }>()

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const MAX_EVIDENCE_PER_ABILITY = 5

const strengthColor: Record<string, string> = { strong: '#67c23a', weak: '#e6a23c', none: '#f56c6c' }
const strengthLabel: Record<string, string> = { strong: '强证据', weak: '弱证据', none: '无证据' }

interface FlowNode {
  id: string
  name: string
  category: number
  abilityId?: number
  evidence?: Record<string, unknown>
}

interface FlowGraph {
  nodes: FlowNode[]
  edges: { source: string; target: string; type: string }[]
  categories: { name: string }[]
}

function buildFlowGraph(data: EvidenceChain): FlowGraph {
  const categories = [
    { name: '主体' },
    { name: '强证据能力' },
    { name: '弱证据能力' },
    { name: '无证据能力' },
    { name: '证据' },
  ]
  const nodes: FlowNode[] = []
  const edges: { source: string; target: string; type: string }[] = []

  const subjectId = 'subject'
  nodes.push({
    id: subjectId,
    name: data.subjectName,
    category: 0,
  })

  for (const ability of data.abilities || []) {
    const abilityNodeId = `ability-${ability.abilityId}`
    const strength = getEvidenceStrength(ability)
    const categoryIndex = strength === 'strong' ? 1 : strength === 'weak' ? 2 : 3
    nodes.push({
      id: abilityNodeId,
      name: ability.abilityName,
      category: categoryIndex,
      abilityId: ability.abilityId,
    })
    edges.push({ source: subjectId, target: abilityNodeId, type: 'HAS' })

    const evidences = ability.evidences || []
    const shown = evidences.slice(0, MAX_EVIDENCE_PER_ABILITY)
    for (const evidence of shown) {
      const evId = `evidence-${evidence.id}`
      nodes.push({
        id: evId,
        name: getSourceTypeText(evidence.sourceType),
        category: 4,
        evidence: { ...evidence } as Record<string, unknown>,
      })
      edges.push({ source: abilityNodeId, target: evId, type: 'SUPPORTED_BY' })
    }
    if (evidences.length > MAX_EVIDENCE_PER_ABILITY) {
      const moreId = `evidence-more-${ability.abilityId}`
      nodes.push({
        id: moreId,
        name: `+${evidences.length - MAX_EVIDENCE_PER_ABILITY} 条`,
        category: 4,
      })
      edges.push({ source: abilityNodeId, target: moreId, type: 'SUPPORTED_BY' })
    }
  }

  return { nodes, edges, categories }
}

const flowGraph = computed<FlowGraph>(() => {
  if (!props.chainData) return { nodes: [], edges: [], categories: [] }
  return buildFlowGraph(props.chainData)
})

function nodeSize(node: FlowNode): number {
  if (node.category === 0) return 56
  if (node.category === 4) return node.evidence ? 15 : 12
  return 26
}

function nodeColor(node: FlowNode): string {
  if (node.category === 0) return '#3b82f6'
  if (node.category === 4) return node.evidence ? '#8b5cf6' : '#a5b4fc'
  return strengthColor[node.category === 1 ? 'strong' : node.category === 2 ? 'weak' : 'none']
}

function buildOption(): EChartsOption {
  const nodes = flowGraph.value.nodes.map(node => ({
    id: node.id,
    name: node.name,
    category: node.category,
    symbolSize: nodeSize(node),
    itemStyle: { color: nodeColor(node) },
    label: {
      show: node.category !== 4 || !!node.evidence,
      formatter: node.category === 4 && node.evidence ? undefined : node.name,
      fontSize: node.category === 0 ? 14 : node.category === 4 ? 10 : 11,
      fontWeight: node.category === 0 ? 700 : 400,
      color: '#334155',
    },
    data: node,
  }))
  const edges = flowGraph.value.edges.map(edge => ({
    source: edge.source,
    target: edge.target,
    value: edge.type.length,
  }))

  return {
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const p = params as { dataType?: string; data?: FlowNode }
        if (p.dataType !== 'node' || !p.data) return ''
        const node = p.data
        if (node.category === 0) return `<b>${props.chainData?.subjectName}</b><br/>${props.chainData?.subjectType === 'EMPLOYEE' ? '人员' : '岗位'}：${props.chainData?.subjectCode || '-'}`
        if (node.category === 4) {
          const ev = node.evidence as Record<string, unknown> | undefined
          if (!ev) return `<b>${node.name}</b>`
          const type = ev.sourceType as string
          return [
            `<b>${getSourceTypeText(type)}</b>`,
            `来源：${(ev.sourceTitle as string) || ev.evidenceCode || '-'}`,
            `状态：<span style="color:${getStatusType(ev.evidenceStatus as string) === 'success' ? '#67c23a' : getStatusType(ev.evidenceStatus as string) === 'danger' ? '#f56c6c' : '#e6a23c'}">${getStatusText(ev.evidenceStatus as string)}</span>`,
            `置信度：${scoreText(ev.confidenceScore as number | undefined)} / 可信度：${scoreText(ev.credibilityScore as number | undefined)}`,
            `时间：${formatDate(ev.createdTime as string | undefined)}`,
          ].join('<br/>')
        }
        const strength = node.category === 1 ? 'strong' : node.category === 2 ? 'weak' : 'none'
        return `<b>${node.name}</b><br/>${strengthLabel[strength]}<br/><span style="color:#94a3b8">点击查看详情</span>`
      },
    },
    legend: {
      data: flowGraph.value.categories.map(c => c.name),
      bottom: 0,
      textStyle: { fontSize: 11, color: '#64748b' },
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      focusNodeAdjacency: true,
      data: nodes,
      links: edges,
      categories: flowGraph.value.categories,
      force: {
        repulsion: 260,
        edgeLength: [70, 180],
        gravity: 0.08,
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      lineStyle: { color: 'source', opacity: 0.55, width: 1.4, curveness: 0.08 },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 2.4, opacity: 0.9 },
      },
    }],
  }
}

function draw() {
  if (!chartRef.value || !canInitializeChart(chartRef.value)) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', (params: unknown) => {
      const p = params as { dataType?: string; data?: FlowNode }
      if (p.dataType !== 'node' || !p.data) return
      if (p.data.category === 1 || p.data.category === 2 || p.data.category === 3) {
        if (p.data.abilityId != null) emit('select-ability', p.data.abilityId)
      }
    })
  }
  chartInstance.setOption(buildOption(), true)
  chartInstance.resize()
}

function handleResize() {
  if (!chartInstance) return
  if (canInitializeChart(chartRef.value)) chartInstance.resize()
}

watch(flowGraph, () => nextTick(draw), { deep: true })
watch(() => props.chainData, () => nextTick(draw))

onMounted(() => {
  nextTick(() => {
    draw()
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(handleResize)
      if (chartRef.value) resizeObserver.observe(chartRef.value)
    }
    window.addEventListener('resize', handleResize)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div class="evidence-flow-graph">
    <div ref="chartRef" class="flow-chart" />
    <p v-if="chainData && chainData.abilities.length" class="flow-hint">
      图中展示 {{ chainData.subjectName }} 的全部能力及每条能力的支撑证据，节点大小代表证据数量，点击能力节点可联动查看详情。
    </p>
  </div>
</template>

<style scoped>
.evidence-flow-graph {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 16px 16px 8px;
}

.flow-chart {
  width: 100%;
  height: 420px;
}

.flow-hint {
  margin: 4px 0 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}
</style>
