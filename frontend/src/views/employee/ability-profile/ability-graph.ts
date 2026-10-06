import type { ForceEdge, ForceNode } from '@/components/graph/AbilityForceGraph.vue'

/** 建图所需的最小画像信息（HR 侧档案与员工侧「我的能力画像」同源，均为 EmpAbilityProfileVO） */
export interface AbilityGraphProfile {
  realName: string
  abilityDetails?: Array<{
    tagId: number
    tagName: string
    tagCategory?: string
    masteryLevel?: number
  }>
}

/**
 * 把能力画像转成力导向图数据：`员工 → 能力分类 → 能力项` 三层。
 *
 * 单点实现：HR 侧能力画像（views/employee/ability-profile/index.vue）与员工侧
 * 「我的能力画像」（同目录 my-profile.vue）共用。此前员工侧完全没有接图谱组件，
 * 页面只有表格，使用者会认为「能力画像不是图」；两处各写一份建图逻辑也会逐渐漂移。
 */
export function buildAbilityForceGraph(
  profile: AbilityGraphProfile,
): { nodes: ForceNode[]; edges: ForceEdge[] } {
  const nodes: ForceNode[] = []
  const edges: ForceEdge[] = []
  nodes.push({ id: 'employee', label: profile.realName, type: 'employee' })

  const categoryMap = new Map<string, NonNullable<AbilityGraphProfile['abilityDetails']>>()
  for (const item of profile.abilityDetails || []) {
    const category = item.tagCategory || '其他'
    if (!categoryMap.has(category)) {
      categoryMap.set(category, [])
    }
    categoryMap.get(category)!.push(item)
  }

  categoryMap.forEach((items, category) => {
    const categoryId = `category_${category}`
    nodes.push({ id: categoryId, label: category, type: 'abilityCategory', category })
    edges.push({ source: 'employee', target: categoryId, type: 'employee-category', style: 'solid' })

    for (const item of items) {
      const abilityId = `ability_${item.tagId}`
      nodes.push({
        id: abilityId,
        label: item.tagName,
        type: 'ability',
        level: item.masteryLevel,
        category: item.tagCategory,
      })
      edges.push({ source: categoryId, target: abilityId, type: 'category-ability', style: 'solid' })
    }
  })

  return { nodes, edges }
}
