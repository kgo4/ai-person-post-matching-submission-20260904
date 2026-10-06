import assert from 'node:assert/strict'
import { buildAbilityForceGraph } from './ability-graph.ts'

/**
 * 能力关系图谱建图契约。
 *
 * 员工侧「我的能力画像」此前没有任何图谱组件（只有表格），
 * 与 HR 侧档案共用本模块后，这里锁死三层结构不再退化。
 */
const profile = {
  realName: '张三',
  abilityDetails: [
    { tagId: 1, tagName: 'Java', tagCategory: '编程语言', masteryLevel: 3 },
    { tagId: 2, tagName: 'Spring', tagCategory: '框架', masteryLevel: 2 },
    { tagId: 3, tagName: 'Python', tagCategory: '编程语言', masteryLevel: 4 },
  ],
}

const { nodes, edges } = buildAbilityForceGraph(profile)

// 员工 1 + 能力分类 2（编程语言/框架）+ 能力项 3
assert.equal(nodes.length, 6, '节点数应为 员工 + 分类 + 能力项')
assert.equal(nodes[0].id, 'employee', '首个节点应为员工自身')
assert.equal(nodes[0].label, '张三')
// 员工→分类 2 条 + 分类→能力 3 条
assert.equal(edges.length, 5, '边数应为 员工→分类 + 分类→能力')

const categories = nodes
  .filter((node) => node.type === 'abilityCategory')
  .map((node) => node.label)
  .sort()
assert.deepEqual(categories, ['框架', '编程语言'], '应按 tagCategory 归并分类节点')

const java = nodes.find((node) => node.id === 'ability_1')
assert.ok(java, '能力项节点应存在')
assert.equal(java.label, 'Java')
assert.equal(java.level, 3, '等级需带入节点用于渲染')
assert.equal(java.category, '编程语言')

// 缺 tagCategory 的能力归入「其他」，不丢节点
const withoutCategory = buildAbilityForceGraph({
  realName: '王五',
  abilityDetails: [{ tagId: 9, tagName: '沟通' }],
})
assert.equal(withoutCategory.nodes.length, 3, '员工 + 其他分类 + 能力项')
assert.ok(
  withoutCategory.nodes.some((node) => node.label === '其他'),
  '未填分类的能力应归入「其他」',
)

// 无能力明细：只剩员工节点，页面据此展示空态说明（forceNodes.length > 1 判定）
const empty = buildAbilityForceGraph({ realName: '李四' })
assert.equal(empty.nodes.length, 1, '无能力时只保留员工节点')
assert.equal(empty.edges.length, 0)

console.log('ability graph tests passed')
