type TreeNode = {
  id: number
  tagName: string
  children?: TreeNode[]
}

export function countDirectChildren(node: TreeNode): number {
  return node.children?.length ?? 0
}

export function buildTagPathLabel(tree: TreeNode[], targetId: number): string {
  const path: string[] = []

  function walk(nodes: TreeNode[], trail: string[]): boolean {
    for (const node of nodes) {
      const nextTrail = [...trail, node.tagName]
      if (node.id === targetId) {
        path.push(...nextTrail)
        return true
      }
      if (node.children?.length && walk(node.children, nextTrail)) {
        return true
      }
    }
    return false
  }

  walk(tree, [])
  return path.join(' / ')
}

export function shouldExpandNodeByDefault(input: {
  level: number
  isInSelectedPath: boolean
}): boolean {
  if (input.isInSelectedPath) return true
  return input.level <= 1
}
