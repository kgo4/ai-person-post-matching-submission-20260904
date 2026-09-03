import type { AbilityTagTreeVO } from '@/api'
import { buildTagPathLabel } from '@/views/system/ability-tag/tag-directory-panel'

export interface TagSearchResult {
  id: number
  tagName: string
  path: string
  tagCategory?: string
}

export function searchTags(treeData: AbilityTagTreeVO[], keyword: string): TagSearchResult[] {
  if (!keyword) return []

  const results: TagSearchResult[] = []

  function walk(nodes: AbilityTagTreeVO[]) {
    for (const node of nodes) {
      if (node.tagName.includes(keyword)) {
        results.push({
          id: node.id,
          tagName: node.tagName,
          path: buildTagPathLabel(treeData as any, node.id),
          tagCategory: node.tagCategory,
        })
      }
      if (node.children?.length) walk(node.children)
    }
  }

  walk(treeData)
  return results
}
