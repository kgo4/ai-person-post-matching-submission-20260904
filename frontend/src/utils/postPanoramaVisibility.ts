import type { PanoramaGraphData } from '@/api/post-panorama'

type VisibilityFilters = {
  level?: string
  abilityCategory?: string
  keyword?: string
  limit?: number
}

export function filterPanoramaGraph(data: PanoramaGraphData, filters: VisibilityFilters): PanoramaGraphData {
  const normalizedKeyword = filters.keyword?.trim().toLowerCase()
  const matches = (node: PanoramaGraphData['nodes'][number]) => {
    const categoryMatch = !filters.abilityCategory || node.category === filters.abilityCategory
    const levelMatch = !filters.level || String(node.level ?? '') === String(filters.level)
    const keywordMatch = !normalizedKeyword
      || [node.label, node.category, node.type].filter(Boolean).some(value => String(value).toLowerCase().includes(normalizedKeyword))
    return categoryMatch && levelMatch && keywordMatch
  }

  const postIds = new Set(data.nodes.filter(node => isPost(node.type) && matches(node)).map(node => node.id))
  const categoryIds = new Set(data.nodes.filter(node => !isPost(node.type) && matches(node)).map(node => node.id))
  const visibleIds = new Set([...postIds, ...categoryIds])

  if (filters.abilityCategory || filters.level || normalizedKeyword) {
    const nodeMap = new Map(data.nodes.map(node => [node.id, node]))
    const frontier = [...visibleIds]
    for (let depth = 0; depth < 2; depth += 1) {
      const next: string[] = []
      for (const edge of data.edges) {
        const neighbor = frontier.includes(edge.source) ? edge.target : frontier.includes(edge.target) ? edge.source : null
        const neighborNode = neighbor ? nodeMap.get(neighbor) : null
        const categoryAllowed = !filters.abilityCategory
          || !neighborNode
          || isPost(neighborNode.type)
          || neighborNode.category === filters.abilityCategory
        if (neighbor && categoryAllowed && !visibleIds.has(neighbor)) {
          visibleIds.add(neighbor)
          next.push(neighbor)
        }
      }
      if (!next.length) break
      frontier.splice(0, frontier.length, ...next)
    }
  }

  const nodes = data.nodes.filter(node => visibleIds.has(node.id)).slice(0, filters.limit || data.nodes.length)
  const nodeIds = new Set(nodes.map(node => node.id))
  const edges = data.edges.filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
  return {
    ...data,
    nodes,
    edges,
    stats: {
      ...data.stats,
      nodeCount: nodes.length,
      edgeCount: edges.length,
      postCount: nodes.filter(node => isPost(node.type)).length,
      abilityCount: nodes.filter(node => isAbility(node.type)).length,
      skillPointCount: nodes.filter(node => isSkill(node.type)).length,
    },
  }
}

function isPost(type: string) {
  return type === 'POST' || type === 'post'
}

function isAbility(type: string) {
  return type === 'ABILITY' || type === 'ability' || type === 'postAbility'
}

function isSkill(type: string) {
  return type === 'SKILL' || type === 'skill' || type === 'abilityCategory'
}
