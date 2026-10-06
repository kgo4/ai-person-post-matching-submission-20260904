import { describe, expect, it } from 'vitest'
import { filterPanoramaGraph } from './postPanoramaVisibility'

const graph = {
  available: true,
  nodes: [
    { id: 'post:1', type: 'POST', label: '云原生工程师', level: 3 },
    { id: 'ability:1', type: 'ABILITY', label: '容器编排', category: '云原生' },
    { id: 'skill:1', type: 'SKILL', label: 'Kubernetes', category: '云原生' },
    { id: 'post:2', type: 'POST', label: '数据工程师', level: 2 },
    { id: 'ability:2', type: 'ABILITY', label: '数据建模', category: '大数据' },
    { id: 'skill:2', type: 'SKILL', label: 'Spark', category: '大数据' },
  ],
  edges: [
    { id: 'e1', source: 'post:1', target: 'ability:1', type: 'REQUIRES' },
    { id: 'e2', source: 'ability:1', target: 'skill:1', type: 'HAS_SKILL' },
    { id: 'e3', source: 'post:2', target: 'ability:2', type: 'REQUIRES' },
    { id: 'e4', source: 'ability:2', target: 'skill:2', type: 'HAS_SKILL' },
  ],
  stats: { nodeCount: 6, edgeCount: 4, postCount: 2, abilityCount: 2, skillPointCount: 2 },
}

describe('filterPanoramaGraph', () => {
  it('keeps only a technology stack and its direct context', () => {
    const result = filterPanoramaGraph(graph, { abilityCategory: '云原生' })

    expect(result.nodes.map(node => node.id)).toEqual(['post:1', 'ability:1', 'skill:1'])
    expect(result.edges.map(edge => edge.id)).toEqual(['e1', 'e2'])
  })

  it('does not expand beyond the selected stack two-hop context', () => {
    const connectedGraph = {
      ...graph,
      edges: [...graph.edges, { id: 'e5', source: 'skill:1', target: 'ability:2', type: 'RELATED' }],
    }

    const result = filterPanoramaGraph(connectedGraph, { abilityCategory: '云原生' })

    expect(result.nodes.map(node => node.id)).not.toContain('post:2')
  })

  it('keeps a selected level with its ability and skill descendants', () => {
    const result = filterPanoramaGraph(graph, { level: '3' })

    expect(result.nodes.map(node => node.id)).toEqual(['post:1', 'ability:1', 'skill:1'])
    expect(result.stats.nodeCount).toBe(3)
  })
})
