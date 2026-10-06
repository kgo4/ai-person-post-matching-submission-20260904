/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  buildAdjacencyMap,
  getRelatedNodeIds,
  isEdgeIdRelated,
  buildTooltipContent,
  createKeyboardManager,
} from '../interactions'
import { DEFAULT_NODE_CONFIG } from '../config'
import type { ForceEdge } from '../config'

describe('interactions', () => {
  describe('adjacency and tooltip utilities', () => {
    const edges: ForceEdge[] = [
      { source: 'a', target: 'b', type: 'HAS_ABILITY', style: 'solid' },
      { source: 'b', target: 'c', type: 'REQUIRES', style: 'dashed' },
    ]

    it('buildAdjacencyMap creates correct bidirectional adjacency', () => {
      const map = buildAdjacencyMap(edges)
      expect(map.get('a')?.has('b')).toBe(true)
      expect(map.get('b')?.has('a')).toBe(true)
      expect(map.get('b')?.has('c')).toBe(true)
      expect(map.get('c')?.has('b')).toBe(true)
    })

    it('getRelatedNodeIds returns the node and its neighbors', () => {
      const map = buildAdjacencyMap(edges)
      const relatedB = getRelatedNodeIds('b', map)
      expect(relatedB.has('a')).toBe(true)
      expect(relatedB.has('b')).toBe(true)
      expect(relatedB.has('c')).toBe(true)
    })

    it('isEdgeIdRelated correctly identifies related edges', () => {
      expect(isEdgeIdRelated(edges[0], 'a')).toBe(true)
      expect(isEdgeIdRelated(edges[0], 'b')).toBe(true)
      expect(isEdgeIdRelated(edges[0], 'c')).toBe(false)
    })

    it('buildTooltipContent returns HTML string with node info', () => {
      const map = new Map<string, Set<string>>()
      map.set('n1', new Set(['n2', 'n3']))
      const html = buildTooltipContent(
        { id: 'n1', label: '前端技能', type: 'ability', level: 3, weight: 0.8 },
        DEFAULT_NODE_CONFIG,
        map,
      )
      expect(html).toContain('前端技能')
      expect(html).toContain('L3')
      expect(html).toContain('80%')
      expect(html).toContain('关联 2 个节点')
    })
  })

  describe('fullscreen behavior', () => {
    it('fullscreen wrapper is teleported to body (Vue template check)', async () => {
      const { readFileSync } = await import('node:fs')
      const path = await import('node:path')
      const source = readFileSync(
        path.resolve(process.cwd(), 'src/components/graph/AbilityForceGraph.vue'),
        'utf8',
      )
      expect(source).toMatch(
        /<Teleport to="body" :disabled="!isFullscreen">[\s\S]*?<div\s+class="ability-force-graph-wrapper"/,
      )
    })
  })

  describe('mount/unmount lifecycle', () => {
    it('keyboardManager onEscape registers and unregisters event listeners', () => {
      const addSpy = vi.spyOn(document, 'addEventListener')
      const removeSpy = vi.spyOn(document, 'removeEventListener')

      const manager = createKeyboardManager()

      const cleanup1 = manager.onEscape(() => {})
      const cleanup2 = manager.onEscape(() => {})

      expect(addSpy).toHaveBeenCalledTimes(2)
      // Both should have been registered for 'keydown'
      const keydownCalls = addSpy.mock.calls.filter(c => c[0] === 'keydown')
      expect(keydownCalls.length).toBe(2)

      // Clean up one handler
      cleanup1()
      const removeKeydownCalls = removeSpy.mock.calls.filter(c => c[0] === 'keydown')
      expect(removeKeydownCalls.length).toBe(1)

      // Full destroy
      manager.destroy()
      const removeAfterDestroy = removeSpy.mock.calls.filter(c => c[0] === 'keydown')
      // Total remove calls should be >= 3 (1 from cleanup1 + at least 1 from destroy)
      expect(removeAfterDestroy.length).toBeGreaterThanOrEqual(2)

      addSpy.mockRestore()
      removeSpy.mockRestore()
    })

    it('keyboardManager destroy removes all listeners', () => {
      const addSpy = vi.spyOn(document, 'addEventListener')
      const removeSpy = vi.spyOn(document, 'removeEventListener')

      const manager = createKeyboardManager()
      manager.onEscape(() => {})
      manager.onEscape(() => {})
      manager.onEscape(() => {})

      expect(addSpy).toHaveBeenCalledTimes(3)

      manager.destroy()

      // All 3 should have been removed
      const removeCalls = removeSpy.mock.calls.filter(c => c[0] === 'keydown')
      expect(removeCalls.length).toBe(3)

      addSpy.mockRestore()
      removeSpy.mockRestore()
    })
  })
})
