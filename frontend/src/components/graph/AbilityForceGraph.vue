<script setup lang="ts">
/**
 * 能力力导向图谱组件 v2
 * 改进：更清晰的视觉层次、焦点模式、类型聚类、平滑过渡、性能优化
 */
import { ref, onMounted, watch, nextTick, onUnmounted, computed } from 'vue'
import type { ForceNode, ForceEdge } from './force-graph/config'
import {
  LARGE_GRAPH_THRESHOLD,
  buildNodeConfig,
  buildEdgeConfig,
  buildEdgeHighlightColors,
} from './force-graph/config'
import { createForceGraphRenderer } from './force-graph/renderer'
import type { ForceGraphRenderer, RenderContext } from './force-graph/renderer'
import { createKeyboardManager } from './force-graph/interactions'

export type { ForceNode, ForceEdge }

interface Props {
  nodes: ForceNode[]
  edges: ForceEdge[]
  width?: number
  height?: number
  showLegend?: boolean
  performanceMode?: boolean
  selectedNodeId?: string | null
  theme?: 'dark' | 'tech-light'
}

const props = withDefaults(defineProps<Props>(), {
  width: 900, height: 600, showLegend: true, performanceMode: false,
  selectedNodeId: null, theme: 'dark',
})

const emit = defineEmits<{
  (e: 'node-click', node: ForceNode): void
  (e: 'node-dblclick', node: ForceNode): void
}>()

defineExpose({ resetView, centerNode })

const chartRef = ref<HTMLDivElement>()
const tooltip = ref({ show: false, x: 0, y: 0, content: '' })
const isFullscreen = ref(false)
const focusNodeId = ref<string | null>(null)
const isLargeGraph = computed(() => props.performanceMode || props.nodes.length > LARGE_GRAPH_THRESHOLD)

let renderer: ForceGraphRenderer
let keyboard: ReturnType<typeof createKeyboardManager>

function getConfigs() {
  return {
    nodeConfig: buildNodeConfig(props.theme),
    edgeConfig: buildEdgeConfig(props.theme),
    edgeHighlightColors: buildEdgeHighlightColors(props.theme),
  }
}

function buildRenderContext(): RenderContext {
  const configs = getConfigs()
  return {
    container: chartRef.value!,
    nodes: props.nodes, edges: props.edges,
    width: props.width, height: props.height,
    theme: props.theme,
    performanceMode: isLargeGraph.value,
    isFullscreen: isFullscreen.value,
    selectedNodeId: props.selectedNodeId,
    ...configs,
    onNodeClick: (node) => emit('node-click', node),
    onNodeDblClick: (node) => emit('node-dblclick', node),
    onTooltipShow: (html, x, y) => { tooltip.value = { show: true, x, y, content: html } },
    onTooltipHide: () => { tooltip.value.show = false },
    onFocusChange: (id) => { focusNodeId.value = id },
  }
}

function draw() {
  if (!chartRef.value || props.nodes.length === 0) return
  renderer.render(buildRenderContext())
}

function resetView() { renderer.resetView(); focusNodeId.value = null }
function centerNode(nodeId: string) { renderer.centerNode(nodeId) }
function toggleFullscreen() { isFullscreen.value = !isFullscreen.value; nextTick(draw) }

onMounted(() => {
  renderer = createForceGraphRenderer()
  keyboard = createKeyboardManager()
  keyboard.onEscape(() => {
    if (isFullscreen.value) { isFullscreen.value = false; nextTick(draw) }
    else if (focusNodeId.value) { focusNodeId.value = null }
  })
  nextTick(draw)
})

let lastNodeCount = 0
let lastNodeIds = ''
let lastEdgeCount = 0

watch(() => props.nodes, (newNodes) => {
  const count = newNodes.length
  const ids = count > 0 ? `${newNodes[0]?.id}-${newNodes[count - 1]?.id}` : ''
  if (count !== lastNodeCount || ids !== lastNodeIds) {
    lastNodeCount = count; lastNodeIds = ids
    focusNodeId.value = null; nextTick(draw)
  }
})
watch(() => props.edges, (newEdges) => {
  const count = newEdges.length
  if (count !== lastEdgeCount) { lastEdgeCount = count; nextTick(draw) }
})
watch(() => props.selectedNodeId, (newId) => { renderer?.setSelectedNode(newId) })
watch(() => [props.width, props.height], () => nextTick(draw))

onUnmounted(() => { renderer?.destroy(); keyboard?.destroy() })
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
    <div class="ability-force-graph-wrapper" :class="{ 'is-fullscreen': isFullscreen, 'is-tech-light': theme === 'tech-light' }">
      <div class="graph-toolbar">
        <div class="toolbar-left">
          <span class="stat-chip"><span class="stat-dot"></span>{{ nodes.length }} 节点</span>
          <span class="stat-chip"><span class="stat-dot stat-dot--edge"></span>{{ edges.length }} 关系</span>
          <span v-if="isLargeGraph" class="perf-badge">性能模式</span>
          <span v-if="focusNodeId" class="focus-badge" @click="focusNodeId = null">焦点模式 ×</span>
        </div>
        <div class="toolbar-right">
          <button class="tool-btn" @click="resetView" title="重置视图">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
          </button>
          <button class="tool-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
            <svg v-if="!isFullscreen" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/></svg>
          </button>
        </div>
      </div>
      <div class="ability-force-graph-shell" :class="{ 'is-fullscreen': isFullscreen }">
        <div ref="chartRef" class="ability-force-graph" />
      </div>
      <div v-if="isFullscreen" class="esc-hint">按 ESC 退出全屏</div>
      <Teleport to="body">
        <div v-if="tooltip.show" class="force-graph-tooltip" :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }" v-html="tooltip.content" />
      </Teleport>
    </div>
  </Teleport>
</template>

<style scoped>
.ability-force-graph-wrapper { position: relative; display: inline-block; border-radius: 8px; overflow: hidden; }
.ability-force-graph-wrapper.is-fullscreen { position: fixed; inset: 0; width: 100vw; height: 100vh; z-index: 9000; display: flex; flex-direction: column; padding: 16px; box-sizing: border-box; background: #0f172a; border-radius: 0; animation: graphFadeIn .3s ease; }
@keyframes graphFadeIn { from { opacity: 0 } to { opacity: 1 } }
.graph-toolbar { position: absolute; top: 8px; left: 8px; right: 8px; z-index: 50; display: flex; align-items: center; justify-content: space-between; pointer-events: none; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 6px; pointer-events: auto; }
.stat-chip { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; font-weight: 500; color: rgba(203,213,225,.8); background: rgba(15,23,42,.7); backdrop-filter: blur(8px); border: 1px solid rgba(148,163,184,.08); border-radius: 6px; padding: 3px 8px; }
.stat-dot { width: 5px; height: 5px; border-radius: 50%; background: #60a5fa; }
.stat-dot--edge { background: #4ade80; }
.perf-badge { font-size: 10px; font-weight: 600; color: #fbbf24; background: rgba(15,23,42,.7); backdrop-filter: blur(8px); border: 1px solid rgba(251,191,36,.15); border-radius: 6px; padding: 3px 8px; }
.focus-badge { font-size: 10px; font-weight: 600; color: #60a5fa; background: rgba(37,99,235,.15); border: 1px solid rgba(96,165,250,.2); border-radius: 6px; padding: 3px 8px; cursor: pointer; transition: all .2s; }
.focus-badge:hover { background: rgba(37,99,235,.25); color: #93bbfd; }
.tool-btn { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border: 1px solid rgba(148,163,184,.08); border-radius: 6px; background: rgba(15,23,42,.7); backdrop-filter: blur(8px); color: rgba(203,213,225,.7); cursor: pointer; transition: all .2s; }
.tool-btn:hover { color: #e2e8f0; border-color: rgba(148,163,184,.15); background: rgba(30,41,59,.8); }
.ability-force-graph-shell { position: relative; }
.ability-force-graph-shell.is-fullscreen { flex: 1; max-width: 100%; max-height: 100%; }
.ability-force-graph { display: flex; justify-content: center; align-items: center; border-radius: 8px; overflow: hidden; max-width: 100%; max-height: 100%; }
.esc-hint { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); z-index: 10000; padding: 6px 16px; border-radius: 6px; background: rgba(15,23,42,.8); backdrop-filter: blur(8px); border: 1px solid rgba(148,163,184,.08); color: rgba(203,213,225,.8); font-size: 12px; }
.ability-force-graph-wrapper.is-tech-light .stat-chip,
.ability-force-graph-wrapper.is-tech-light .tool-btn,
.ability-force-graph-wrapper.is-tech-light .perf-badge { color: #47617f; background: rgba(255,255,255,.78); border-color: rgba(37,99,235,.12); box-shadow: 0 6px 18px rgba(37,99,235,.08); }
.ability-force-graph-wrapper.is-tech-light .tool-btn:hover { color: #2563eb; background: #fff; border-color: rgba(59,130,246,.28); }
.ability-force-graph-wrapper.is-tech-light .focus-badge { color: #2563eb; background: rgba(37,99,235,.09); border-color: rgba(37,99,235,.2); }
.ability-force-graph-wrapper.is-tech-light.is-fullscreen { background: linear-gradient(135deg, #eef2f9, #e3eaf5); }
</style>

<style>
@keyframes selectedRingPulse { 0%,100%{stroke-opacity:.6;stroke-dashoffset:0} 50%{stroke-opacity:.3;stroke-dashoffset:6} }
@keyframes focusRingPulse { 0%,100%{stroke-opacity:.5;stroke-dashoffset:0} 50%{stroke-opacity:.2;stroke-dashoffset:8} }
.force-graph-tooltip { position: fixed; z-index: 10001; pointer-events: none; background: rgba(15,23,42,.95); backdrop-filter: blur(12px); border: 1px solid rgba(148,163,184,.1); border-radius: 8px; padding: 10px 14px; min-width: 120px; max-width: 240px; box-shadow: 0 8px 32px rgba(0,0,0,.4); font-family: Inter,PingFang SC,Microsoft YaHei,sans-serif; font-size: 12px; color: rgba(203,213,225,.9); line-height: 1.6; }
</style>
