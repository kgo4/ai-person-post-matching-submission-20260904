import { describe, expect, it } from 'vitest'
import { buildPostPanorama3DGraph, shouldShowPanoramaNodeLabel } from './postPanorama3d'

const data = {
  available: true,
  nodes: [
    { id: 'post:1', type: 'POST', label: '云原生工程师' },
    { id: 'ability:1', type: 'ABILITY', label: '容器编排', category: '云原生' },
    { id: 'skill:1', type: 'SKILL', label: 'Kubernetes', category: '云原生' },
  ],
  edges: [
    { id: 'e1', source: 'post:1', target: 'ability:1', type: 'REQUIRES' },
    { id: 'e2', source: 'ability:1', target: 'skill:1', type: 'HAS_SKILL' },
  ],
  stats: { nodeCount: 3, edgeCount: 2, postCount: 1, abilityCount: 1, skillPointCount: 1 },
}

describe('buildPostPanorama3DGraph', () => {
  it('uses technical stacks as the first visible layer in stack mode', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack' })

    expect(graph.nodes.find(node => node.id === 'stack:云原生')).toMatchObject({
      label: '云原生',
      ring: 'stack',
    })
    expect(graph.edges.find(edge => edge.id === 'stack:云原生->post:1')).toBeDefined()
  })

  it('does not add technical stack nodes in level mode', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'level' })

    expect(graph.nodes.find(node => node.id === 'stack:云原生')).toBeUndefined()
  })

  it('uses ability labels instead of post labels in stack mode', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack' })
    const post = graph.nodes.find(node => node.id === 'post:1')!
    const ability = graph.nodes.find(node => node.id === 'ability:1')!

    expect(shouldShowPanoramaNodeLabel(post, 'stack')).toBe(false)
    expect(shouldShowPanoramaNodeLabel(ability, 'stack')).toBe(true)
    expect(shouldShowPanoramaNodeLabel(post, 'level')).toBe(true)
  })

  it('centers the selected technology stack without removing other graph nodes', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack', focusNodeId: 'stack:云原生' })

    expect(graph.centerNode?.id).toBe('stack:云原生')
    expect(graph.nodes.find(node => node.id === 'post:1')).toBeDefined()
  })

  it('centers the selected post in a full graph', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack', focusNodeId: 'post:1' })

    expect(graph.centerNode?.id).toBe('post:1')
    expect(graph.nodes.find(node => node.id === 'ability:1')).toBeDefined()
  })
})
