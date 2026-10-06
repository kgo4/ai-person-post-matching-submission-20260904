import type { AbilityTagTreeVO } from '@/api'

export interface AbilityTagNameRow {
  id?: number | null
  tagId?: number | null
  tagName?: string | null
  abilityName?: string | null
}

export function buildAbilityTagNameMap(tree: AbilityTagTreeVO[] = []): Map<number, string> {
  const map = new Map<number, string>()
  const visit = (nodes: AbilityTagTreeVO[]) => {
    for (const node of nodes) {
      if (node.id != null && node.tagName) {
        map.set(node.id, node.tagName)
      }
      if (node.children?.length) {
        visit(node.children)
      }
    }
  }
  visit(tree)
  return map
}

export function resolveAbilityTagName(row: AbilityTagNameRow, tagNameMap: Map<number, string>): string {
  if (row.tagName?.trim()) {
    return row.tagName
  }
  if (row.abilityName?.trim()) {
    return row.abilityName
  }
  if (row.tagId != null) {
    return tagNameMap.get(row.tagId) || ''
  }
  return ''
}
