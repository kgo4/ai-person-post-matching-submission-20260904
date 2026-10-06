import assert from 'node:assert/strict'
import {
  buildPostPanorama3DGraph,
  getNodeColor,
} from './postPanorama3d.ts'

const sampleGraph = {
  available: true,
  nodes: [
    { id: 'POST:1', type: 'POST', label: 'Java后端开发工程师', level: 3, weight: 1, category: '岗位' },
    { id: 'POST:2', type: 'POST', label: '人工智能工程师', level: 4, weight: 0.9, category: '岗位' },
    { id: 'TECH_STACK:Java', type: 'TECH_STACK', label: 'Java', level: 4, weight: 0.9, category: 'Java' },
    { id: 'TECH_STACK:Python', type: 'TECH_STACK', label: 'Python', level: 4, weight: 0.8, category: 'Python' },
  ],
  edges: [
    { id: 'e1', source: 'POST:1', target: 'TECH_STACK:Java', type: 'POST_TECH_STACK', weight: 0.9 },
    { id: 'e2', source: 'POST:2', target: 'TECH_STACK:Python', type: 'POST_TECH_STACK', weight: 0.8 },
    { id: 'e3', source: 'TECH_STACK:Java', target: 'POST:1', type: 'TECH_STACK_POST', weight: 0.9 },
    { id: 'e4', source: 'TECH_STACK:Python', target: 'POST:2', type: 'TECH_STACK_POST', weight: 0.8 },
  ],
  stats: { nodeCount: 4, edgeCount: 4, postCount: 2, abilityCount: 0, skillPointCount: 0 },
}

function findNode(graph, id) {
  return graph.nodes.find(node => node.id === id)
}

{
  const graph = buildPostPanorama3DGraph(sampleGraph, { layoutMode: 'stack' })

  assert.equal(graph.nodes.length, 4)
  assert.equal(graph.edges.length, 2)
  assert.equal(findNode(graph, 'TECH_STACK:Java').ring, 'center')
  assert.equal(findNode(graph, 'POST:1').ring, 'post')
  assert.ok(graph.edges.every(edge => edge.type === 'TECH_STACK_POST'))
}

{
  const graph = buildPostPanorama3DGraph(sampleGraph, { layoutMode: 'post' })
  assert.equal(graph.centerNode?.id, 'POST:1')
  assert.equal(findNode(graph, 'TECH_STACK:Java').ring, 'stack')
  assert.ok(graph.edges.every(edge => edge.type === 'POST_TECH_STACK'))
}

{
  const graph = buildPostPanorama3DGraph(sampleGraph, { layoutMode: 'stack', focusNodeId: 'TECH_STACK:Java' })
  assert.deepEqual(graph.nodes.map(node => node.id).sort(), ['POST:1', 'TECH_STACK:Java'])
}

{
  const graph = buildPostPanorama3DGraph(sampleGraph, { layoutMode: 'post', focusNodeId: 'POST:2' })
  assert.deepEqual(graph.nodes.map(node => node.id).sort(), ['POST:2', 'TECH_STACK:Python'])
}

{
  const firstSkill = getNodeColor({ id: 'SKILL:100', category: 'AI', ring: 'skill' })
  const secondSkill = getNodeColor({ id: 'SKILL:101', category: 'AI', ring: 'skill' })
  const ability = getNodeColor({ id: 'ABILITY:10', category: 'AI', ring: 'ability' })

  assert.notEqual(firstSkill, secondSkill)
  assert.notEqual(firstSkill, ability)
}

function distance(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
}

console.log('postPanorama3d tests passed')
