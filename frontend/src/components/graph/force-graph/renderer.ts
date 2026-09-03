import * as d3 from 'd3'
import type { ForceNode, ForceEdge, NodeVisualConfig, EdgeVisualConfig } from './config'
import {
  PRIMARY_TYPES, SECONDARY_TYPES, TERTIARY_TYPES,
  STABLE_ALPHA, MAX_TICKS, LEGEND_ITEMS,
  truncateLabel,
} from './config'
import { buildAdjacencyMap, getRelatedNodeIds, isEdgeIdRelated, buildTooltipContent } from './interactions'

export interface RenderContext {
  container: HTMLElement
  nodes: ForceNode[]
  edges: ForceEdge[]
  width: number
  height: number
  performanceMode: boolean
  isFullscreen: boolean
  selectedNodeId: string | null
  theme: 'dark' | 'tech-light'
  nodeConfig: Record<string, NodeVisualConfig>
  edgeConfig: Record<string, EdgeVisualConfig>
  edgeHighlightColors: Record<string, string>
  onNodeClick: (node: ForceNode) => void
  onNodeDblClick: (node: ForceNode) => void
  onTooltipShow: (html: string, x: number, y: number) => void
  onTooltipHide: () => void
  onFocusChange: (nodeId: string | null) => void
}

export interface ForceGraphRenderer {
  render(ctx: RenderContext): void
  setSelectedNode(nodeId: string | null): void
  resetView(): void
  centerNode(nodeId: string): void
  destroy(): void
  getFocusNodeId(): string | null
}

interface InternalState {
  svg: d3.Selection<SVGSVGElement, unknown, null, undefined> | null
  simulation: d3.Simulation<ForceNode, ForceEdge> | null
  zoom: d3.ZoomBehavior<SVGSVGElement, unknown> | null
  animationId: number | null
  nodeSelection: d3.Selection<SVGGElement, ForceNode, SVGGElement, unknown> | null
  linkSelection: d3.Selection<SVGLineElement, ForceEdge, SVGGElement, unknown> | null
  linkGlowSelection: d3.Selection<SVGLineElement, ForceEdge, SVGGElement, unknown> | null
  linkLabelSelection: d3.Selection<SVGTextElement, ForceEdge, SVGGElement, unknown> | null
  currentCtx: RenderContext | null
  adjacencyMap: Map<string, Set<string>>
}

export function createForceGraphRenderer(): ForceGraphRenderer {
  const state: InternalState = {
    svg: null,
    simulation: null,
    zoom: null,
    animationId: null,
    nodeSelection: null,
    linkSelection: null,
    linkGlowSelection: null,
    linkLabelSelection: null,
    currentCtx: null,
    adjacencyMap: new Map(),
  }

  function getContainerSize(ctx: RenderContext): { w: number; h: number } {
    if (ctx.isFullscreen) {
      return { w: window.innerWidth, h: window.innerHeight }
    }
    return { w: ctx.width, h: ctx.height }
  }

  function isEdgeRelated(edge: any, nodeId: string): boolean {
    return isEdgeIdRelated(edge, nodeId)
  }

  function destroy() {
    if (state.simulation) {
      state.simulation.stop()
      state.simulation = null
    }
    if (state.animationId != null) {
      cancelAnimationFrame(state.animationId)
      state.animationId = null
    }
    state.nodeSelection = null
    state.linkSelection = null
    state.linkGlowSelection = null
    state.linkLabelSelection = null
    state.svg = null
    state.zoom = null
    state.currentCtx = null
  }

  function setSelectedNode(nodeId: string | null) {
    if (!state.nodeSelection || !state.currentCtx) return
    updateSelectedState(nodeId, state.currentCtx)
  }

  function resetView() {
    if (!state.svg || !state.zoom || !state.currentCtx) return
    const { w, h } = getContainerSize(state.currentCtx)
    state.svg.transition().duration(500).call(
      state.zoom.transform as any,
      d3.zoomIdentity.translate(w / 2, h / 2).scale(1).translate(-w / 2, -h / 2)
    )
  }

  function centerNode(nodeId: string) {
    zoomToNode(nodeId, 1.5)
  }

  function getFocusNodeId(): string | null {
    return null
  }

  function zoomToNode(nodeId: string, scale: number = 1.5) {
    if (!state.svg || !state.zoom || !state.simulation || !state.currentCtx) return
    const { w, h } = getContainerSize(state.currentCtx)
    const simNode = state.simulation.nodes().find((n: any) => n.id === nodeId) as any
    if (!simNode || simNode.x == null) return
    state.svg.transition().duration(600).ease(d3.easeCubicInOut).call(
      state.zoom.transform as any,
      d3.zoomIdentity.translate(w / 2, h / 2).scale(scale).translate(-simNode.x, -simNode.y)
    )
  }

  function updateSelectedState(selectedId: string | null, ctx: RenderContext) {
    const { nodeSelection, linkSelection, linkGlowSelection, linkLabelSelection } = state
    if (!nodeSelection) return
    const ringColor = ctx.theme === 'tech-light' ? '#2563eb' : '#60a5fa'

    nodeSelection.selectAll('.node-selected-ring').remove()
    if (!selectedId) {
      resetHighlight(ctx)
      return
    }

    nodeSelection.filter((d: any) => d.id === selectedId)
      .append('circle')
      .attr('class', 'node-selected-ring')
      .attr('r', (d: any) => (ctx.nodeConfig[d.type]?.size || 16) + 10)
      .attr('fill', 'none')
      .attr('stroke', ringColor)
      .attr('stroke-width', 1.5)
      .attr('stroke-opacity', 0.6)
      .attr('stroke-dasharray', '3,2')
      .style('animation', 'selectedRingPulse 2s ease-in-out infinite')
  }

  function applyFocusMode(nodeId: string, ctx: RenderContext) {
    const { nodeSelection, linkSelection, linkGlowSelection, linkLabelSelection } = state
    if (!nodeSelection || !linkSelection) return
    const related = getRelatedNodeIds(nodeId, state.adjacencyMap)
    const perf = ctx.performanceMode
    const ringColor = ctx.theme === 'tech-light' ? '#2563eb' : '#60a5fa'

    nodeSelection.selectAll('.node-focus-ring').remove()
    nodeSelection.filter((d: any) => d.id === nodeId)
      .append('circle')
      .attr('class', 'node-focus-ring')
      .attr('r', (d: any) => (ctx.nodeConfig[d.type]?.size || 16) + 12)
      .attr('fill', 'none')
      .attr('stroke', ringColor)
      .attr('stroke-width', 2)
      .attr('stroke-opacity', 0.5)
      .attr('stroke-dasharray', '4,3')
      .style('animation', 'focusRingPulse 2.5s ease-in-out infinite')

    nodeSelection.select('.node-main')
      .transition().duration(300)
      .attr('fill-opacity', (n: any) => related.has(n.id) ? 1 : (ctx.theme === 'tech-light' ? 0.14 : 0.05))
      .attr('stroke-opacity', (n: any) => related.has(n.id) ? 1 : 0.03)

    nodeSelection.select('.node-outer-ring')
      .transition().duration(300)
      .attr('stroke-opacity', (n: any) => related.has(n.id) ? 0.55 : 0)

    if (!perf) {
      nodeSelection.select('.node-glow')
        .transition().duration(300)
        .attr('stroke-opacity', (n: any) => related.has(n.id) ? 0.5 : 0)
    }

    if (linkSelection) {
      linkSelection.transition().duration(300)
        .attr('stroke-opacity', (e: any) => isEdgeRelated(e, nodeId) ? 0.8 : 0.02)
        .attr('stroke', (e: any) => {
          if (isEdgeRelated(e, nodeId)) {
            return ctx.edgeHighlightColors[e.type] || ringColor
          }
          return ctx.edgeConfig[e.type]?.color || 'rgba(148, 163, 184, 0.3)'
        })
        .attr('stroke-width', (e: any) => {
          const base = ctx.edgeConfig[e.type]?.width || 1
          return isEdgeRelated(e, nodeId) ? base + 1.5 : base
        })
        .attr('marker-end', (e: any) => {
          if (isEdgeRelated(e, nodeId) && ['employee-post', 'REQUIRES', 'MATCHES', 'CORE_REQUIRES'].includes(e.type)) {
            return 'url(#arrowhead-highlight)'
          }
          return ['employee-post', 'REQUIRES', 'MATCHES', 'CORE_REQUIRES', 'PREREQUISITE_OF'].includes(e.type) ? 'url(#arrowhead)' : ''
        })
    }

    if (linkGlowSelection) {
      linkGlowSelection.transition().duration(300)
        .attr('stroke-opacity', (e: any) => isEdgeRelated(e, nodeId) ? 0.4 : 0)
    }

    if (linkLabelSelection) {
      linkLabelSelection.transition().duration(300)
        .attr('fill', (e: any) => isEdgeRelated(e, nodeId) ? 'rgba(203, 213, 225, 0.8)' : 'rgba(148, 163, 184, 0)')
    }
  }

  function resetHighlight(ctx: RenderContext) {
    const { nodeSelection, linkSelection, linkGlowSelection, linkLabelSelection } = state
    const perf = ctx.performanceMode

    if (nodeSelection) {
      nodeSelection.select('.node-main')
        .transition().duration(300)
        .attr('fill-opacity', (d: any) => {
          const light = ctx.theme === 'tech-light'
          if (d.type === 'employee' || d.type === 'post') return light ? 1 : 0.95
          if (TERTIARY_TYPES.has(d.type)) return (light ? 0.92 : 0.75) + (d.level || 3) * 0.04
          return light ? 0.95 : 0.85
        })
        .attr('stroke-opacity', 1)

      if (!perf) {
        nodeSelection.select('.node-glow')
          .transition().duration(300)
          .attr('stroke-opacity', 0)
      }

      nodeSelection.select('.node-outer-ring')
        .transition().duration(300)
        .attr('stroke-opacity', (d: any) => ctx.theme === 'tech-light' ? 0.4 : 0.25)
    }

    if (linkSelection) {
      linkSelection.transition().duration(300)
        .attr('stroke-opacity', (d: any) => {
          const light = ctx.theme === 'tech-light'
          if (['MATCHES', 'CORE_REQUIRES', 'HAS_ABILITY', 'REQUIRES'].includes(d.type)) return light ? 0.6 : 0.5
          return light ? 0.42 : 0.25
        })
        .attr('stroke', (d: any) => ctx.edgeConfig[d.type]?.color || 'rgba(148, 163, 184, 0.3)')
        .attr('stroke-width', (d: any) => ctx.edgeConfig[d.type]?.width || 1)
        .attr('marker-end', (d: any) => ['employee-post', 'REQUIRES', 'MATCHES', 'CORE_REQUIRES', 'PREREQUISITE_OF'].includes(d.type) ? 'url(#arrowhead)' : '')
    }

    if (linkGlowSelection) {
      linkGlowSelection.transition().duration(300)
        .attr('stroke-opacity', 0)
    }

    if (linkLabelSelection) {
      linkLabelSelection.transition().duration(300)
        .attr('fill', 'rgba(148, 163, 184, 0)')
    }
  }

  function render(ctx: RenderContext) {
    if (!ctx.container || ctx.nodes.length === 0) return

    destroy()

    const container = ctx.container
    d3.select(container).selectAll('*').remove()

    const { w, h } = getContainerSize(ctx)
    const perf = ctx.performanceMode
    const isLight = ctx.theme === 'tech-light'
    const labelColor = isLight ? '#1e293b' : '#e2e8f0'
    const labelShadow = isLight ? 'none' : '0 1px 2px rgba(0, 0, 0, 0.6)'
    const ringColor = isLight ? '#2563eb' : '#60a5fa'
    const legendBg = isLight ? 'rgba(255, 255, 255, 0.9)' : 'rgba(15, 23, 42, 0.8)'
    const legendBorder = isLight ? 'rgba(148, 163, 184, 0.16)' : 'rgba(148, 163, 184, 0.08)'
    const legendText = isLight ? '#475569' : 'rgba(203, 213, 225, 0.8)'
    const legendTitleText = isLight ? '#64748b' : 'rgba(148, 163, 184, 0.7)'
    const particleColor = isLight ? '#3b82f6' : '#60a5fa'
    const arrowColor = isLight ? 'rgba(148, 163, 184, 0.6)' : 'rgba(148, 163, 184, 0.5)'
    const arrowHighlightColor = isLight ? '#2563eb' : '#60a5fa'
    const nodeStroke = isLight ? 'rgba(255, 255, 255, 0.6)' : 'rgba(255, 255, 255, 0.08)'

    const containerDiv = d3.select(container)
      .append('div')
      .style('position', 'relative')
      .style('width', w + 'px')
      .style('height', h + 'px')
      .style('overflow', 'hidden')

    state.svg = containerDiv.append('svg')
      .attr('width', w)
      .attr('height', h)
      .style('position', 'relative')
      .style('z-index', '1')

    const svg = state.svg
    const g = svg.append('g')

    state.zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.2, 4])
      .on('zoom', (event) => {
        g.attr('transform', event.transform)
      })
    svg.call(state.zoom)

    const defs = svg.append('defs')

    if (!perf) {
      const bgPatternId = 'bg-grid-' + Math.random().toString(36).slice(2, 8)
      const bgPattern = defs.append('pattern')
        .attr('id', bgPatternId)
        .attr('width', 28)
        .attr('height', 28)
        .attr('patternUnits', 'userSpaceOnUse')
      if (isLight) {
        // 浅色主题：细腻网格线，营造技术画布感
        bgPattern.append('line')
          .attr('x1', 0).attr('y1', 0).attr('x2', 28).attr('y2', 0)
          .attr('stroke', 'rgba(37, 99, 235, 0.055)')
          .attr('stroke-width', 1)
        bgPattern.append('line')
          .attr('x1', 0).attr('y1', 0).attr('x2', 0).attr('y2', 28)
          .attr('stroke', 'rgba(37, 99, 235, 0.055)')
          .attr('stroke-width', 1)
      } else {
        bgPattern.append('circle')
          .attr('cx', 12)
          .attr('cy', 12)
          .attr('r', 0.4)
          .attr('fill', 'rgba(148, 163, 184, 0.08)')
      }

      g.append('rect')
        .attr('width', w * 4)
        .attr('height', h * 4)
        .attr('x', -w * 1.5)
        .attr('y', -h * 1.5)
        .attr('fill', `url(#${bgPatternId})`)
    }

    Object.entries(ctx.nodeConfig).forEach(([type, config]) => {
      const gradId = `grad-${type}`
      const grad = defs.append('radialGradient')
        .attr('id', gradId)
        .attr('cx', '35%')
        .attr('cy', '35%')
        .attr('r', '65%')
      grad.append('stop').attr('offset', '0%').attr('stop-color', config.gradient[0]).attr('stop-opacity', 1)
      grad.append('stop').attr('offset', '100%').attr('stop-color', config.gradient[1]).attr('stop-opacity', 1)
    })

    if (!perf) {
      const glowFilter = defs.append('filter')
        .attr('id', 'node-glow')
        .attr('x', '-80%').attr('y', '-80%')
        .attr('width', '260%').attr('height', '260%')
      glowFilter.append('feGaussianBlur')
        .attr('stdDeviation', '4')
        .attr('result', 'coloredBlur')
      const feMerge = glowFilter.append('feMerge')
      feMerge.append('feMergeNode').attr('in', 'coloredBlur')
      feMerge.append('feMergeNode').attr('in', 'SourceGraphic')

      const shadowFilter = defs.append('filter')
        .attr('id', 'node-shadow')
        .attr('x', '-40%').attr('y', '-40%')
        .attr('width', '180%').attr('height', '180%')
      shadowFilter.append('feDropShadow')
        .attr('dx', '0').attr('dy', '2')
        .attr('stdDeviation', '3')
        .attr('flood-color', 'rgba(0, 0, 0, 0.3)')

      const linkGlow = defs.append('filter')
        .attr('id', 'link-glow')
        .attr('x', '-40%').attr('y', '-40%')
        .attr('width', '180%').attr('height', '180%')
      linkGlow.append('feGaussianBlur')
        .attr('stdDeviation', '2')
        .attr('result', 'coloredBlur')
      const linkMerge = linkGlow.append('feMerge')
      linkMerge.append('feMergeNode').attr('in', 'coloredBlur')
      linkMerge.append('feMergeNode').attr('in', 'SourceGraphic')
    }

    defs.append('marker')
      .attr('id', 'arrowhead')
      .attr('viewBox', '0 -4 8 8')
      .attr('refX', 28)
      .attr('refY', 0)
      .attr('markerWidth', 6)
      .attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-4L8,0L0,4')
      .attr('fill', arrowColor)

    defs.append('marker')
      .attr('id', 'arrowhead-highlight')
      .attr('viewBox', '0 -4 8 8')
      .attr('refX', 28)
      .attr('refY', 0)
      .attr('markerWidth', 6)
      .attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-4L8,0L0,4')
      .attr('fill', arrowHighlightColor)

    const nodesCopy: ForceNode[] = ctx.nodes.map(n => ({ ...n }))
    const edgesCopy: ForceEdge[] = ctx.edges.map(e => ({ ...e }))

    state.adjacencyMap = buildAdjacencyMap(edgesCopy)

    state.simulation = d3.forceSimulation<ForceNode>(nodesCopy)
      .force('link', d3.forceLink<ForceNode, ForceEdge>(edgesCopy)
        .id(d => d.id)
        .distance(d => {
          const s = d.source as unknown as ForceNode
          const t = d.target as unknown as ForceNode
          // Keep connected labels apart; the rendered text is wider than the node circle.
          if (s.type === t.type) return 120
          if ((s.type === 'employee' && t.type === 'post') || (s.type === 'post' && t.type === 'employee')) return 280
          if (s.type === 'ability' || t.type === 'ability') return 150
          return 180
        })
        .strength(d => {
          const s = d.source as unknown as ForceNode
          const t = d.target as unknown as ForceNode
          if (s.type === t.type) return 0.3
          return 0.15
        })
      )
      .force('charge', d3.forceManyBody()
        .strength(d => {
          const node = d as ForceNode
          if (PRIMARY_TYPES.has(node.type)) return perf ? -450 : -900
          if (SECONDARY_TYPES.has(node.type)) return perf ? -250 : -500
          return perf ? -140 : -280
        })
      )
      .force('center', d3.forceCenter(w / 2, h / 2).strength(0.05))
      .force('collision', d3.forceCollide().strength(0.95).radius(d => {
        const node = d as ForceNode
        return (ctx.nodeConfig[node.type]?.size || 16) + 28
      }))
      .force('x', d3.forceX(w / 2).strength(0.015))
      .force('y', d3.forceY(h / 2).strength(0.015))
      .alphaDecay(0.02)
      .velocityDecay(0.4)

    // ---------- 连线层 ----------
    const linkGroup = g.append('g').attr('class', 'links')

    state.linkGlowSelection = linkGroup
      .selectAll<SVGLineElement, ForceEdge>('line')
      .data(edgesCopy)
      .join('line')
      .attr('stroke', d => ctx.edgeHighlightColors[d.type] || 'rgba(148, 163, 184, 0.15)')
      .attr('stroke-width', d => (ctx.edgeConfig[d.type]?.width || 1) + 3)
      .attr('stroke-opacity', 0)
      .attr('filter', perf ? 'none' : 'url(#link-glow)')

    state.linkSelection = linkGroup
      .selectAll<SVGLineElement, ForceEdge>('.link-main')
      .data(edgesCopy)
      .join('line')
      .attr('class', 'link-main')
      .attr('stroke', d => ctx.edgeConfig[d.type]?.color || 'rgba(148, 163, 184, 0.3)')
      .attr('stroke-width', d => ctx.edgeConfig[d.type]?.width || 1)
      .attr('stroke-dasharray', d => ctx.edgeConfig[d.type]?.dasharray || 'none')
      .attr('stroke-opacity', d => {
        if (['MATCHES', 'CORE_REQUIRES', 'HAS_ABILITY', 'REQUIRES'].includes(d.type)) return isLight ? 0.6 : 0.5
        return isLight ? 0.42 : 0.25
      })
      .attr('marker-end', d => ['employee-post', 'REQUIRES', 'MATCHES', 'CORE_REQUIRES', 'PREREQUISITE_OF'].includes(d.type) ? 'url(#arrowhead)' : '')

    state.linkLabelSelection = null
    if (!perf && edgesCopy.length < 100) {
      state.linkLabelSelection = linkGroup
        .selectAll<SVGTextElement, ForceEdge>('.link-label')
        .data(edgesCopy.filter(d => d.label))
        .join('text')
        .attr('class', 'link-label')
        .text(d => d.label || '')
        .attr('font-size', '8px')
        .attr('fill', 'rgba(148, 163, 184, 0)')
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('pointer-events', 'none') as unknown as d3.Selection<SVGTextElement, ForceEdge, SVGGElement, unknown>
    }

    // ---------- 节点层 ----------
    const nodeGroup = g.append('g').attr('class', 'nodes')

    state.nodeSelection = nodeGroup
      .selectAll<SVGGElement, ForceNode>('g')
      .data(nodesCopy)
      .join('g')
      .style('cursor', 'pointer')
      .call(d3.drag<SVGGElement, ForceNode>()
        .on('start', (event, d) => {
          if (!event.active) state.simulation!.alphaTarget(0.2).restart()
          d.fx = d.x
          d.fy = d.y
        })
        .on('drag', (event, d) => {
          d.fx = event.x
          d.fy = event.y
        })
        .on('end', (event, d) => {
          if (!event.active) state.simulation!.alphaTarget(0)
          d.fx = null
          d.fy = null
        })
      )

    const nodeSel = state.nodeSelection

    if (!perf) {
      nodeSel.append('circle')
        .attr('r', d => (ctx.nodeConfig[d.type]?.size || 16) + 5)
        .attr('fill', 'none')
        .attr('stroke', d => ctx.nodeConfig[d.type]?.color || '#475569')
        .attr('stroke-width', 1.5)
        .attr('stroke-opacity', 0)
        .attr('filter', 'url(#node-glow)')
        .attr('class', 'node-glow')
    }

    nodeSel.append('circle')
      .attr('r', d => {
        const base = ctx.nodeConfig[d.type]?.size || 16
        if (d.type === 'ability' || d.type === 'postAbility') {
          return base * (0.8 + (d.level || 3) * 0.08)
        }
        return base
      })
      .attr('fill', d => `url(#grad-${d.type})`)
      .attr('fill-opacity', d => {
        if (d.type === 'employee' || d.type === 'post') return isLight ? 1 : 0.95
        if (TERTIARY_TYPES.has(d.type)) return (isLight ? 0.92 : 0.75) + (d.level || 3) * 0.04
        return isLight ? 0.95 : 0.85
      })
      .attr('stroke', d => {
        if (d.matchStatus === 'mismatch') return '#ef4444'
        if (d.matchStatus === 'match') return '#22c55e'
        if (d.type === 'employee' || d.type === 'post') return nodeStroke
        return nodeStroke
      })
      .attr('stroke-width', d => d.matchStatus ? 2.5 : (PRIMARY_TYPES.has(d.type) ? 1.2 : 0.8))
      .attr('filter', perf ? 'none' : 'url(#node-shadow)')
      .attr('class', 'node-main')

    // 主节点（员工/岗位）外环：增强层级与辨识度
    if (!perf) {
      nodeSel.filter(d => d.type === 'employee' || d.type === 'post')
        .append('circle')
        .attr('r', d => (ctx.nodeConfig[d.type]?.size || 16) + 4)
        .attr('fill', 'none')
        .attr('stroke', d => ctx.nodeConfig[d.type]?.color || '#475569')
        .attr('stroke-opacity', isLight ? 0.4 : 0.25)
        .attr('stroke-width', 1)
        .attr('class', 'node-outer-ring')
        .attr('pointer-events', 'none')
    }

    if (!perf) {
      nodeSel.filter(d => PRIMARY_TYPES.has(d.type) || SECONDARY_TYPES.has(d.type))
        .append('circle')
        .attr('r', d => (ctx.nodeConfig[d.type]?.size || 16) * 0.35)
        .attr('fill', 'rgba(255, 255, 255, 0.12)')
        .attr('transform', d => {
          const r = (ctx.nodeConfig[d.type]?.size || 16) * 0.2
          return `translate(${-r}, ${-r})`
        })
        .attr('pointer-events', 'none')
    }

    nodeSel.append('text')
      .text(d => {
        if (perf) {
          return PRIMARY_TYPES.has(d.type) ? truncateLabel(d.label, 8) : ''
        }
        if (PRIMARY_TYPES.has(d.type)) return truncateLabel(d.label, 10)
        if (SECONDARY_TYPES.has(d.type)) return truncateLabel(d.label, 6)
        if (TERTIARY_TYPES.has(d.type)) return truncateLabel(d.label, 5)
        return ''
      })
      .attr('font-size', d => {
        const base = isLight ? 1 : 0
        if (d.type === 'employee') return `${12 + base}px`
        if (d.type === 'post') return `${12 + base}px`
        if (PRIMARY_TYPES.has(d.type)) return `${11 + base}px`
        if (SECONDARY_TYPES.has(d.type)) return `${10 + base}px`
        return `${9 + base}px`
      })
      .attr('font-weight', d => PRIMARY_TYPES.has(d.type) ? '600' : '400')
      .attr('fill', labelColor)
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'middle')
      .attr('dy', d => TERTIARY_TYPES.has(d.type) && !perf ? '-0.4em' : '0')
      .attr('pointer-events', 'none')
      .style('text-shadow', labelShadow)
      // 浅色主题：白色描边光晕, 保证标签在网格/边线之上清晰可读
      .style('paint-order', isLight ? 'stroke' : '')
      .style('stroke', isLight ? 'rgba(255, 255, 255, 0.9)' : 'none')
      .style('stroke-width', isLight ? '3px' : '0')
      .style('stroke-linejoin', isLight ? 'round' : '')

    if (!perf) {
      nodeSel.append('text')
        .text(d => {
          if ((d.type === 'ability' || d.type === 'postAbility') && d.level) return `L${d.level}`
          return ''
        })
        .attr('font-size', `${isLight ? 9 : 8}px`)
        .attr('fill', isLight ? 'rgba(71, 85, 105, 0.8)' : 'rgba(148, 163, 184, 0.7)')
        .attr('font-weight', 600)
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('dy', d => (d.type === 'ability' || d.type === 'postAbility') ? '0.7em' : '0')
        .attr('pointer-events', 'none')
        .style('paint-order', 'stroke')
        .style('stroke', isLight ? 'rgba(255, 255, 255, 0.9)' : 'none')
        .style('stroke-width', isLight ? '2.5px' : '0')
    }

    // ---------- 粒子动画 ----------
    if (!perf && edgesCopy.length > 0 && edgesCopy.length < 80) {
      const particleGroup = g.append('g').attr('class', 'particles')
      const particleCount = Math.min(Math.floor(edgesCopy.length / 3), 20)

      for (let i = 0; i < particleCount; i++) {
        const edge = edgesCopy[i % edgesCopy.length]
        const particle = particleGroup.append('circle')
          .attr('r', 1.5)
          .attr('fill', particleColor)
          .attr('opacity', 0)

        animateParticle(particle, edge, edgesCopy)
      }
    }

    // ---------- 交互 ----------
    nodeSel.on('mouseover', function (event, d) {
      if (!state.nodeSelection || !state.linkSelection) return
      const related = getRelatedNodeIds(d.id, state.adjacencyMap)
      const dimOpacity = isLight ? 0.16 : 0.08

      state.nodeSelection.select('.node-main')
        .transition().duration(200)
        .attr('fill-opacity', (n: any) => related.has(n.id) ? 1 : dimOpacity)
        .attr('stroke-opacity', (n: any) => related.has(n.id) ? 1 : 0.05)

      state.nodeSelection.select('.node-outer-ring')
        .transition().duration(200)
        .attr('stroke-opacity', (n: any) => related.has(n.id) ? 0.55 : 0)

      if (!perf) {
        state.nodeSelection.select('.node-glow')
          .transition().duration(200)
          .attr('stroke-opacity', (n: any) => related.has(n.id) ? 0.4 : 0)
      }

      state.linkSelection.transition().duration(200)
        .attr('stroke-opacity', (e: any) => isEdgeRelated(e, d.id) ? 0.7 : 0.03)
        .attr('stroke', (e: any) => {
          if (isEdgeRelated(e, d.id)) {
            return ctx.edgeHighlightColors[e.type] || ringColor
          }
          return ctx.edgeConfig[e.type]?.color || 'rgba(148, 163, 184, 0.3)'
        })
        .attr('stroke-width', (e: any) => {
          const base = ctx.edgeConfig[e.type]?.width || 1
          return isEdgeRelated(e, d.id) ? base + 1 : base
        })

      if (state.linkGlowSelection) {
        state.linkGlowSelection.transition().duration(200)
          .attr('stroke-opacity', (e: any) => isEdgeRelated(e, d.id) ? 0.3 : 0)
      }

      if (state.linkLabelSelection) {
        state.linkLabelSelection.transition().duration(200)
          .attr('fill', (e: any) => isEdgeRelated(e, d.id) ? 'rgba(203, 213, 225, 0.8)' : 'rgba(148, 163, 184, 0)')
      }

      const html = buildTooltipContent(d, ctx.nodeConfig, state.adjacencyMap)
      ctx.onTooltipShow(html, event.pageX + 15, event.pageY - 10)
    })

    nodeSel.on('mouseout', function () {
      resetHighlight(ctx)
      ctx.onTooltipHide()
    })

    nodeSel.on('click', function (_event, d) {
      ctx.onNodeClick(d)
      ctx.onFocusChange(d.id)
      applyFocusMode(d.id, ctx)
    })

    nodeSel.on('dblclick', function (event, d) {
      event.preventDefault()
      event.stopPropagation()
      ctx.onNodeDblClick(d)
      ctx.onFocusChange(d.id)
      applyFocusMode(d.id, ctx)
      zoomToNode(d.id, 2)
    })

    svg.on('click', function (event) {
      if (event.target === svg.node()) {
        ctx.onFocusChange(null)
        resetHighlight(ctx)
        state.nodeSelection?.selectAll('.node-focus-ring').remove()
      }
    })

    // ---------- 选中态 ----------
    if (ctx.selectedNodeId) {
      updateSelectedState(ctx.selectedNodeId, ctx)
    }

    // ---------- 力导向更新 ----------
    let tickCount = 0
    let didAutoFit = false

    // 布局收敛后自动适配视口：根据全部节点的包围盒计算缩放与平移，保证整张图可见
    function autoFitView() {
      const sim = state.simulation
      const nodes = sim?.nodes() ?? []
      if (!nodes.length || !state.svg || !state.zoom) return
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      for (const n of nodes) {
        if (n.x == null || n.y == null) return
        if (n.x < minX) minX = n.x
        if (n.x > maxX) maxX = n.x
        if (n.y < minY) minY = n.y
        if (n.y > maxY) maxY = n.y
      }
      const bw = Math.max(maxX - minX, 1)
      const bh = Math.max(maxY - minY, 1)
      const padding = 70
      let scale = Math.min((w - padding * 2) / bw, (h - padding * 2) / bh)
      scale = Math.min(Math.max(scale, 0.2), 1.2)
      const cx = (minX + maxX) / 2
      const cy = (minY + maxY) / 2
      state.svg.transition().duration(400).ease(d3.easeCubicInOut).call(
        state.zoom.transform as any,
        d3.zoomIdentity.translate(w / 2, h / 2).scale(scale).translate(-cx, -cy)
      )
    }

    state.simulation.on('tick', () => {
      tickCount++

      if (state.linkSelection) {
        state.linkSelection
          .attr('x1', (d: any) => d.source.x || 0)
          .attr('y1', (d: any) => d.source.y || 0)
          .attr('x2', (d: any) => d.target.x || 0)
          .attr('y2', (d: any) => d.target.y || 0)
      }

      if (state.linkGlowSelection) {
        state.linkGlowSelection
          .attr('x1', (d: any) => d.source.x || 0)
          .attr('y1', (d: any) => d.source.y || 0)
          .attr('x2', (d: any) => d.target.x || 0)
          .attr('y2', (d: any) => d.target.y || 0)
      }

      if (state.linkLabelSelection) {
        state.linkLabelSelection
          .attr('x', (d: any) => (d.source.x + d.target.x) / 2)
          .attr('y', (d: any) => (d.source.y + d.target.y) / 2)
      }

      if (state.nodeSelection) {
        state.nodeSelection.attr('transform', d => `translate(${d.x},${d.y})`)
      }

      if (state.simulation && (state.simulation.alpha() < STABLE_ALPHA || tickCount > MAX_TICKS)) {
        state.simulation.stop()
        if (!didAutoFit) {
          didAutoFit = true
          autoFitView()
        }
      }
    })

    // ---------- 图例 ----------
    if (LEGEND_ITEMS.length > 0) {
      const legendH = 28 + LEGEND_ITEMS.length * 22
      const legend = svg.append('g')
        .attr('transform', `translate(12, ${h - legendH - 12})`)

      legend.append('rect')
        .attr('width', 120)
        .attr('height', legendH)
        .attr('rx', 6)
        .attr('fill', legendBg)
        .attr('stroke', legendBorder)
        .attr('stroke-width', 1)

      legend.append('text')
        .attr('x', 12)
        .attr('y', 18)
        .text('图例')
        .attr('font-size', '10px')
        .attr('font-weight', '600')
        .attr('fill', legendTitleText)

      LEGEND_ITEMS.forEach((item, i) => {
        const y = 36 + i * 22
        const config = ctx.nodeConfig[item.type]

        legend.append('circle')
          .attr('cx', 20)
          .attr('cy', y)
          .attr('r', 5)
          .attr('fill', config?.color || '#475569')
          .attr('fill-opacity', 0.9)

        legend.append('text')
          .attr('x', 32)
          .attr('y', y)
          .text(item.label)
          .attr('font-size', '10px')
          .attr('fill', legendText)
          .attr('dominant-baseline', 'middle')
      })
    }

    state.currentCtx = ctx
  }

  function animateParticle(
    particle: d3.Selection<SVGCircleElement, unknown, null, undefined>,
    edge: ForceEdge,
    allEdges: ForceEdge[]
  ) {
    const duration = 4000 + Math.random() * 3000

    function animate() {
      const sourceX = (edge.source as any).x || 0
      const sourceY = (edge.source as any).y || 0
      const targetX = (edge.target as any).x || 0
      const targetY = (edge.target as any).y || 0

      particle
        .attr('cx', sourceX)
        .attr('cy', sourceY)
        .attr('opacity', 0)
        .transition()
        .duration(duration)
        .ease(d3.easeLinear)
        .attr('cx', targetX)
        .attr('cy', targetY)
        .attr('opacity', 0.6)
        .transition()
        .duration(800)
        .attr('opacity', 0)
        .on('end', () => {
          if (!state.simulation) return
          const nextEdge = allEdges[Math.floor(Math.random() * allEdges.length)]
          animateParticle(particle, nextEdge, allEdges)
        })
    }

    animate()
  }

  return { render, setSelectedNode, resetView, centerNode, destroy, getFocusNodeId }
}
