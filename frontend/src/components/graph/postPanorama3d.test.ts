import { describe, expect, it } from 'vitest'
import { buildPostPanorama3DGraph, shouldShowPanoramaNodeLabel } from './postPanorama3d'
import type { PanoramaGraphData } from '@/api/post-panorama'

/**
 * 3D 岗位全景图谱已收窄为「岗位 ↔ 技术栈」二层模型。
 *
 * 旧模型会把岗位能力等级、能力、技能点一起铺进 3D 空间，并按 category 合成
 * `stack:<分类>` 虚拟节点；后来为了实现层明确为
 * 「只保留岗位与技术栈的关系，避免把岗位能力等级/技能点混入视图语义」
 * （见 `postPanorama3d.ts` 建图入口注释），本测试随之改写：
 *
 *   · 技术栈节点不再合成，直接来自数据的 `TECH_STACK` 节点；
 *   · 两种视图各认一种边类型：stack 视图读 `TECH_STACK_POST`，post 视图读 `POST_TECH_STACK`；
 *   · `level` 已从视图切换中移除，作为 `post` 的别名保留（`effectiveMode` 归一）。
 *
 * 下面每条断言都用新模型的实际输出校准过，不是照搬旧期望。
 */

const data: PanoramaGraphData = {
  available: true,
  nodes: [
    { id: 'stack:cloud', type: 'TECH_STACK', label: '云原生', category: '云原生' },
    { id: 'post:1', type: 'POST', label: '云原生工程师' },
    { id: 'post:2', type: 'POST', label: '后端工程师' },
  ],
  edges: [
    // stack 视图消费
    { id: 'e1', source: 'stack:cloud', target: 'post:1', type: 'TECH_STACK_POST' },
    { id: 'e2', source: 'stack:cloud', target: 'post:2', type: 'TECH_STACK_POST' },
    // post / level 视图消费
    { id: 'p1', source: 'post:1', target: 'stack:cloud', type: 'POST_TECH_STACK' },
    { id: 'p2', source: 'post:2', target: 'stack:cloud', type: 'POST_TECH_STACK' },
  ],
  stats: { nodeCount: 3, edgeCount: 4, postCount: 2, abilityCount: 0, skillPointCount: 0 },
}

describe('buildPostPanorama3DGraph', () => {
  it('stack 视图以技术栈为中心，岗位铺在外圈（技术栈是第一层）', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack' })

    expect(graph.centerNode?.id).toBe('stack:cloud')
    // 技术栈是中心，岗位在外圈 —— 阅读顺序「技术栈 → 岗位」
    expect(graph.nodes.find(node => node.id === 'stack:cloud')?.ring).toBe('center')
    expect(graph.nodes.find(node => node.id === 'post:1')?.ring).toBe('post')
    // 只消费 TECH_STACK_POST，岗位→技术栈的边不混入
    expect(graph.edges.map(edge => edge.id).sort()).toEqual(['e1', 'e2'])
  })

  it('post 视图以岗位为中心，技术栈铺在外圈', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'post' })

    expect(graph.centerNode?.id).toBe('post:1')
    expect(graph.nodes.find(node => node.id === 'post:1')?.ring).toBe('center')
    expect(graph.nodes.find(node => node.id === 'stack:cloud')?.ring).toBe('stack')
    expect(graph.edges.map(edge => edge.id).sort()).toEqual(['p1', 'p2'])
  })

  it('level 视图已下线，作为 post 视图的别名保留', () => {
    // 『按岗位级别』已从视图切换中移除（页面只剩 按技术栈 / 按岗位）。
    // level 仍被类型与 `effectiveMode` 接受，语义等同 post —— 这样旧链接不会白屏。
    const level = buildPostPanorama3DGraph(data, { layoutMode: 'level' })
    const post = buildPostPanorama3DGraph(data, { layoutMode: 'post' })

    expect(level.centerNode?.id).toBe(post.centerNode?.id)
    expect(level.nodes.map(node => `${node.id}:${node.ring}`).sort())
      .toEqual(post.nodes.map(node => `${node.id}:${node.ring}`).sort())
  })

  it('聚焦技术栈时切换为单中心投影，但保留与之相关的岗位节点', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack', focusNodeId: 'stack:cloud' })

    expect(graph.centerNode?.id).toBe('stack:cloud')
    expect(graph.nodes.find(node => node.id === 'post:1')).toBeDefined()
    expect(graph.nodes.find(node => node.id === 'post:2')).toBeDefined()
  })

  it('聚焦岗位时只保留该岗位与其关联技术栈，不保留无关岗位', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack', focusNodeId: 'post:1' })

    expect(graph.centerNode?.id).toBe('post:1')
    expect(graph.nodes.find(node => node.id === 'stack:cloud')).toBeDefined()
    // post:2 与 post:1 没有共同边，聚焦后应被剔除（这正是「聚焦」与「全景」的区别）
    expect(graph.nodes.find(node => node.id === 'post:2')).toBeUndefined()
  })

  it('中心节点不显示常驻标签，岗位/技术栈节点显示', () => {
    const graph = buildPostPanorama3DGraph(data, { layoutMode: 'stack' })
    const center = graph.nodes.find(node => node.id === 'stack:cloud')!
    const post = graph.nodes.find(node => node.id === 'post:1')!

    // 中心是自己所在位置，不需要标签再标一遍
    expect(shouldShowPanoramaNodeLabel(center, 'stack')).toBe(false)
    expect(shouldShowPanoramaNodeLabel(post, 'stack')).toBe(true)
    expect(shouldShowPanoramaNodeLabel(post, 'post')).toBe(true)
  })

  it('无数据时返回空图且不抛错', () => {
    const graph = buildPostPanorama3DGraph({
      available: true,
      nodes: [],
      edges: [],
      stats: { nodeCount: 0, edgeCount: 0, postCount: 0, abilityCount: 0, skillPointCount: 0 },
    }, { layoutMode: 'stack' })

    expect(graph.nodes).toEqual([])
    expect(graph.edges).toEqual([])
    expect(graph.centerNode).toBeNull()
  })

  /**
   * 回归：整张 3D 图曾被压成一个平面圆盘。
   *
   * 根因是高度写成了 `((node.level || 3) - 3) * 70`，而后端建图
   * （GraphNodeProjectionService 的 buildPostNodes / buildPostCapabilityNodes）
   * 从未调用 setLevelValue，岗位与技术栈节点的 level 全是 null，
   * normalizeLevel 一律回落成 3 ⇒ 高度恒为 0；力导向的目标点又是 y=0，二次抹平。
   *
   * 因此下面这条断言刻意用**没有 level 的节点**：一旦有人再把高度挂到 level 上，
   * 或者让力导向把 y 拉回 0，它就会红。
   */
  it('高度必须来自球面分布：level 全缺失时也不能退化成平面', () => {
    const noLevel: PanoramaGraphData = {
      available: true,
      nodes: [
        { id: 'stack:cloud', type: 'TECH_STACK', label: '云原生', category: '云原生' },
        { id: 'post:1', type: 'POST', label: '云原生工程师', category: '云原生' },
        { id: 'post:2', type: 'POST', label: '后端工程师', category: '云原生' },
        { id: 'post:3', type: 'POST', label: 'SRE 工程师', category: '云原生' },
      ],
      edges: [
        { id: 'e1', source: 'stack:cloud', target: 'post:1', type: 'TECH_STACK_POST' },
        { id: 'e2', source: 'stack:cloud', target: 'post:2', type: 'TECH_STACK_POST' },
        { id: 'e3', source: 'stack:cloud', target: 'post:3', type: 'TECH_STACK_POST' },
      ],
      stats: { nodeCount: 4, edgeCount: 3, postCount: 3, abilityCount: 0, skillPointCount: 0 },
    }

    const graph = buildPostPanorama3DGraph(noLevel, { layoutMode: 'stack' })
    const ys = graph.nodes.map(node => node.position.y)
    const thickness = Math.max(...ys) - Math.min(...ys)

    // 平面时该值为 0；修复后由球面分布撑开上百个单位
    expect(thickness).toBeGreaterThan(50)
    expect(ys.some(y => Math.abs(y) > 20)).toBe(true)
  })

  it('level 不再是"有没有高度"的开关：缺失或统一时同样是立体球壳', () => {
    const base = {
      available: true,
      nodes: [
        { id: 'stack:cloud', type: 'TECH_STACK', label: '云原生', category: '云原生' },
        { id: 'post:1', type: 'POST', label: '云原生工程师', category: '云原生' },
        { id: 'post:2', type: 'POST', label: '后端工程师', category: '云原生' },
        { id: 'post:3', type: 'POST', label: 'SRE 工程师', category: '云原生' },
      ],
      edges: [
        { id: 'e1', source: 'stack:cloud', target: 'post:1', type: 'TECH_STACK_POST' },
        { id: 'e2', source: 'stack:cloud', target: 'post:2', type: 'TECH_STACK_POST' },
        { id: 'e3', source: 'stack:cloud', target: 'post:3', type: 'TECH_STACK_POST' },
      ],
      stats: { nodeCount: 4, edgeCount: 3, postCount: 3, abilityCount: 0, skillPointCount: 0 },
    } as PanoramaGraphData

    const uniform = buildPostPanorama3DGraph(base, { layoutMode: 'stack' })
    const varied = buildPostPanorama3DGraph({
      ...base,
      nodes: base.nodes.map(node => (node.type === 'POST' ? { ...node, level: 5 } : node)),
    }, { layoutMode: 'stack' })

    const uniformYs = uniform.nodes.map(node => node.position.y)
    expect(Math.max(...uniformYs) - Math.min(...uniformYs)).toBeGreaterThan(50)

    // level 仍参与节点大小与球壳半径（radius = 基准 + level * 16），所以高度绝对值会变；
    // 这里要锁的是**谁高谁低不受 level 影响** —— 过去 level 一致时高度会集体归零。
    const heightRank = (graph: ReturnType<typeof buildPostPanorama3DGraph>) => graph.nodes
      .map((node, index) => ({ y: node.position.y, index }))
      .sort((a, b) => a.y - b.y)
      .map(item => item.index)

    expect(heightRank(varied)).toEqual(heightRank(uniform))
  })
})
