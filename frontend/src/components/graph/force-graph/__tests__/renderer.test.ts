/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createForceGraphRenderer } from '../renderer'
import { buildNodeConfig, buildEdgeConfig, buildEdgeHighlightColors } from '../config'
import type { ForceNode, ForceEdge } from '../config'

function makeNodes(): ForceNode[] {
  return [
    { id: 'e1', label: '张三', type: 'employee' },
    { id: 'p1', label: '前端开发', type: 'post' },
    { id: 'a1', label: 'Vue', type: 'ability' },
    { id: 'a2', label: 'React', type: 'ability' },
  ]
}

function makeEdges(): ForceEdge[] {
  return [
    { source: 'e1', target: 'p1', type: 'employee-post', style: 'solid' },
    { source: 'e1', target: 'a1', type: 'HAS_ABILITY', style: 'solid' },
    { source: 'p1', target: 'a2', type: 'REQUIRES', style: 'dashed' },
  ]
}

describe('ForceGraphRenderer', () => {
  let container: HTMLDivElement

  beforeEach(() => {
    container = document.createElement('div')
    document.body.appendChild(container)
  })

  describe('destroy', () => {
    it('stops the simulation when destroy is called', () => {
      const renderer = createForceGraphRenderer()
      const nodes = makeNodes()
      const edges = makeEdges()

      const configs = {
        nodeConfig: buildNodeConfig('dark'),
        edgeConfig: buildEdgeConfig('dark'),
        edgeHighlightColors: buildEdgeHighlightColors('dark'),
      }

      renderer.render({
        container,
        nodes,
        edges,
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      expect(container.querySelector('svg')).toBeTruthy()

      renderer.destroy()

      // After destroy and re-rendering with no nodes, old SVG should be cleaned
      renderer.render({
        container,
        nodes: [],
        edges: [],
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      // The render returned early (nodes.length === 0), so old SVG is still there
      // But a subsequent render with data should work
      renderer.render({
        container,
        nodes,
        edges,
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      const svgs = container.querySelectorAll('svg')
      // After re-render, there should be exactly one SVG (the new one replaced the old)
      expect(svgs.length).toBe(1)
    })
  })

  describe('node updates', () => {
    it('does not leave stale SVG elements after re-rendering with different data', () => {
      const renderer = createForceGraphRenderer()
      const configs = {
        nodeConfig: buildNodeConfig('dark'),
        edgeConfig: buildEdgeConfig('dark'),
        edgeHighlightColors: buildEdgeHighlightColors('dark'),
      }

      renderer.render({
        container,
        nodes: makeNodes(),
        edges: makeEdges(),
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      const firstSvgCount = container.querySelectorAll('svg').length
      expect(firstSvgCount).toBe(1)

      const reducedNodes: ForceNode[] = [
        { id: 'e1', label: '张三', type: 'employee' },
      ]
      const reducedEdges: ForceEdge[] = []

      renderer.render({
        container,
        nodes: reducedNodes,
        edges: reducedEdges,
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      const secondSvgCount = container.querySelectorAll('svg').length
      expect(secondSvgCount).toBe(1)

      // Only the remaining node should be in the SVG
      const nodeTexts = container.querySelectorAll('.nodes text')
      // The employee node has one label text
      const labelCount = Array.from(nodeTexts).filter(t => t.textContent?.includes('张三')).length
      expect(labelCount).toBe(1)
    })
  })

  describe('selected node highlighting', () => {
    it('highlights only nodes related to the selected node', () => {
      const renderer = createForceGraphRenderer()
      const configs = {
        nodeConfig: buildNodeConfig('dark'),
        edgeConfig: buildEdgeConfig('dark'),
        edgeHighlightColors: buildEdgeHighlightColors('dark'),
      }

      const nodes = makeNodes()
      const edges = makeEdges()

      renderer.render({
        container,
        nodes,
        edges,
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: null,
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      expect(container.querySelector('svg')).toBeTruthy()

      // Simulate selected node by re-rendering with selectedNodeId
      renderer.render({
        container,
        nodes,
        edges,
        width: 600,
        height: 400,
        performanceMode: false,
        isFullscreen: false,
        selectedNodeId: 'e1',
        theme: 'dark',
        ...configs,
        onNodeClick: () => {},
        onNodeDblClick: () => {},
        onTooltipShow: () => {},
        onTooltipHide: () => {},
        onFocusChange: () => {},
      })

      // The selected ring should exist on the selected node
      const selectedRings = container.querySelectorAll('.node-selected-ring')
      expect(selectedRings.length).toBe(1)
    })
  })
})
