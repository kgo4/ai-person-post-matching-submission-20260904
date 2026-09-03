import type { AbilityTagTreeVO } from '@/api'

export function countTreeNodes(tree: AbilityTagTreeVO[]): Record<string, number> {
  const counts: Record<string, number> = { TECHNICAL: 0, SOFT: 0, BUSINESS: 0 }
  function walk(nodes: AbilityTagTreeVO[]) {
    for (const node of nodes) {
      // 健康页展示的是标签库总量，L0 最高节点同样属于正式标签，不能被排除。
      if (node.tagCategory && counts[node.tagCategory] !== undefined) {
        counts[node.tagCategory]++
      }
      if (node.children?.length) walk(node.children)
    }
  }
  walk(tree)
  return counts
}

export function countAllNodes(nodes: AbilityTagTreeVO[]): number {
  let count = 0
  for (const node of nodes) {
    count++
    if (node.children?.length) count += countAllNodes(node.children)
  }
  return count
}
