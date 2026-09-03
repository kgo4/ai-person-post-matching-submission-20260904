import assert from 'node:assert/strict'
import { graphDataFromMatchContext, getRelatedNodeLabel } from './kgWorkbench.ts'

const graph = graphDataFromMatchContext({
  status: 'AVAILABLE', employeeId: 7, employeeName: 'Lin', postId: 9, postName: 'Java',
  graphVersion: 'KGV_9', refreshedAt: null, abilities: [{
    abilityId: 31, abilityName: 'Kafka', state: 'MISSING', required: true, core: true,
    weight: 20, requiredLevel: 3, employeeMasteryLevel: null, evidence: []
  }, {
    abilityId: 32, abilityName: 'Java', state: 'SATISFIED', required: true, core: false,
    weight: 80, requiredLevel: 3, employeeMasteryLevel: 4, evidence: [{
      evidenceId: 1, label: '证书', relationType: 'SUPPORTED_BY', confidence: 0.9,
      reviewStatus: 'APPROVED', sourceRefs: ['ref-1'], graphVersion: 'KGV_9', createdTime: null
    }]
  }]
})

// 能力节点包含 matchState
assert.equal(graph.nodes.find(node => node.id === 'ABILITY:31').metadata.matchState, 'MISSING')
assert.equal(graph.nodes.find(node => node.id === 'ABILITY:31').metadata.graphVersion, 'KGV_9')
assert.equal(graph.nodes.find(node => node.id === 'ABILITY:32').metadata.matchState, 'SATISFIED')

// 缺失能力不生成员工能力边
assert.equal(graph.edges.filter(e => e.type === 'HAS_ABILITY').length, 1)

// available 状态正确
assert.equal(graph.available, true)

// 节点和边数量正确：2 个根节点 + 2 个能力 = 4 节点；2 个 REQUIRES + 1 个 HAS_ABILITY = 3 边
assert.equal(graph.nodes.length, 4)
assert.equal(graph.edges.length, 3)

// getRelatedNodeLabel
assert.equal(getRelatedNodeLabel({ sourceLabel: 'Lin', targetLabel: 'Kafka',
  otherNodeId: 'ABILITY:31', direction: 'downstream' }), 'Kafka')
assert.equal(getRelatedNodeLabel({ sourceLabel: 'Lin', targetLabel: 'Kafka',
  otherNodeId: 'EMPLOYEE:7', direction: 'upstream' }), 'Lin')

// 空上下文
const emptyGraph = graphDataFromMatchContext({
  status: 'EMPLOYEE_NOT_FOUND', employeeId: 7, employeeName: null, postId: 9, postName: null,
  graphVersion: null, refreshedAt: null, abilities: []
})
assert.equal(emptyGraph.available, false)
assert.equal(emptyGraph.nodes.length, 2)

console.log('All kgWorkbench tests passed.')
