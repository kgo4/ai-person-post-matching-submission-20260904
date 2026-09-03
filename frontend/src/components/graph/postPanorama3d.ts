import type { PanoramaGraphData } from '@/api/post-panorama'

export type Panorama3DLayoutMode = 'stack' | 'post' | 'level'
export type Panorama3DRing = 'center' | 'stack' | 'post' | 'ability' | 'skill' | 'other'

export interface Panorama3DPosition {
  x: number
  y: number
  z: number
}

export interface Panorama3DNode {
  id: string
  label: string
  type: string
  category?: string
  level?: number
  weight?: number
  ring: Panorama3DRing
  radius: number
  color: string
  position: Panorama3DPosition
  meta?: Record<string, any>
}

export interface Panorama3DEdge {
  id: string
  source: string
  target: string
  type: string
  weight: number
  color: string
}

export interface Panorama3DGraph {
  nodes: Panorama3DNode[]
  edges: Panorama3DEdge[]
  centerNode: Panorama3DNode | null
}

const CATEGORY_COLORS = [
  '#2563eb',
  '#0891b2',
  '#059669',
  '#d97706',
  '#dc2626',
  '#7c3aed',
  '#db2777',
  '#0d9488',
  '#ea580c',
  '#4f46e5',
  '#65a30d',
  '#be123c',
  '#0284c7',
  '#16a34a',
  '#9333ea',
  '#c026d3',
  '#e11d48',
  '#0d9488',
  '#ca8a04',
  '#6366f1',
]

const SKILL_POINT_COLORS = [
  '#38bdf8',
  '#22d3ee',
  '#2dd4bf',
  '#60a5fa',
  '#818cf8',
  '#a78bfa',
]

const EDGE_COLORS: Record<string, string> = {
  CORE_REQUIRES: '#ea580c',
  REQUIRES: '#2563eb',
  HAS_SKILL: '#059669',
  SIMILAR_TO: '#7c3aed',
  GAP: '#dc2626',
  LEARN_BY: '#0d9488',
  EVOLVED_FROM: '#db2777',
  PREREQUISITE_OF: '#9333ea',
}

export function shouldShowPanoramaNodeLabel(node: Panorama3DNode, layoutMode: Panorama3DLayoutMode): boolean {
  if (layoutMode === 'stack') {
    return node.ring === 'stack' || node.ring === 'post' || node.ring === 'ability'
  }
  return node.ring === 'center' || node.ring === 'post' || node.ring === 'stack'
    || (node.ring === 'ability' && (node.weight || 0) >= 0.7)
}

const SPHERE_RADIUS: Record<Panorama3DRing, number> = {
  center: 0,
  stack: 80,
  post: 100,
  ability: 200,
  skill: 310,
  other: 400,
}

const IDEAL_EDGE_LENGTH: Record<Panorama3DRing, number> = {
  center: 140,
  stack: 135,
  post: 170,
  ability: 150,
  skill: 120,
  other: 190,
}

export function buildPostPanorama3DGraph(
  data: PanoramaGraphData,
  options: { layoutMode?: Panorama3DLayoutMode; focusNodeId?: string } = {},
): Panorama3DGraph {
  const layoutMode = options.layoutMode || 'stack'
  let sourceNodes = [...data.nodes]
  let sourceEdges = [...data.edges]
  const effectiveMode = layoutMode === 'level' ? 'post' : layoutMode
  // 两种视图只保留岗位与技术栈的关系，避免把岗位能力等级/技能点混入视图语义。
  const relationTypes = effectiveMode === 'stack'
    ? new Set(['TECH_STACK_POST'])
    : new Set(['POST_TECH_STACK'])
  sourceEdges = sourceEdges.filter(edge => relationTypes.has(edge.type))
  const relationNodeIds = new Set<string>()
  sourceEdges.forEach(edge => {
    relationNodeIds.add(edge.source)
    relationNodeIds.add(edge.target)
  })
  sourceNodes = sourceNodes.filter(node => relationNodeIds.has(node.id)
    && (isPostNode(node.type) || isTechStackNode(node.type)))

  const defaultCenterId = resolveCenterIdForMode(sourceNodes, effectiveMode)
  // 只有用户选中具体技术栈/岗位时才切换为单中心关系投影；未选择时保持全景展示。
  if (options.focusNodeId) {
    const focusedEdges = sourceEdges.filter(edge => edge.source === options.focusNodeId || edge.target === options.focusNodeId)
    const focusedIds = new Set<string>([options.focusNodeId])
    focusedEdges.forEach(edge => { focusedIds.add(edge.source); focusedIds.add(edge.target) })
    sourceEdges = focusedEdges
    sourceNodes = sourceNodes.filter(node => focusedIds.has(node.id))
  }
  const categories = collectCategories(sourceNodes)

  const nodes = sourceNodes.map((node, index) => {
    const ring = getNodeRing(node, defaultCenterId, effectiveMode)
    const base = mapNode(node, ring, categories)
    base.position = getSpherePosition(base, index, sourceNodes.length, categories, effectiveMode)
    return base
  })
  const nodeIds = new Set(nodes.map(node => node.id))
  const edges: Panorama3DEdge[] = sourceEdges
    .filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
    .map(edge => ({
      id: edge.id || `${edge.source}-${edge.target}`,
      source: edge.source,
      target: edge.target,
      type: edge.type,
      weight: normalizeWeight(edge.weight),
      color: EDGE_COLORS[edge.type] || (edge.type?.includes('CORE') ? '#ea580c' : '#2563eb'),
    }))

  const centerId = nodes.some(node => node.id === options.focusNodeId) ? options.focusNodeId! : defaultCenterId
  for (const node of nodes) {
    if (node.id === centerId) {
      node.ring = 'center'
      node.radius = 14
      node.color = '#1d4ed8'
      node.position = { x: 0, y: 0, z: 0 }
    } else if (node.ring === 'center') {
      node.ring = isPostNode(node.type) ? 'post' : node.ring
      node.radius = getVisualRadius(node.ring, node.level || 3, node.weight || 0.5)
      node.color = getNodeColor(node)
    }
  }

  applyForceLayout(nodes, edges, categories, effectiveMode)

  return {
    nodes,
    edges,
    centerNode: nodes.find(node => node.id === centerId) || nodes.find(node => node.ring === 'center') || null,
  }
}

export function getNodeColor(node: Pick<Panorama3DNode, 'id' | 'category' | 'ring'>): string {
  if (node.ring === 'center') return '#1d4ed8'
  if (node.ring === 'stack') return '#7c3aed'
  if (node.ring === 'skill') {
    return SKILL_POINT_COLORS[hashText(`${node.category || 'skill'}:${node.id || ''}`) % SKILL_POINT_COLORS.length]
  }
  if (!node.category) {
    const idHash = hashText(node.id || String(Math.random()))
    return CATEGORY_COLORS[idHash % CATEGORY_COLORS.length]
  }
  return CATEGORY_COLORS[hashText(node.category) % CATEGORY_COLORS.length]
}

function resolveCenterId(nodes: PanoramaGraphData['nodes']): string | null {
  const domainNode = nodes.find(node => node.type === 'domain')
  if (domainNode) return domainNode.id
  const postNodes = nodes.filter(node => isPostNode(node.type))
  const explicitMain = postNodes.find(node => /新一代信息技术|全景|中心/.test(node.label))
  if (explicitMain) return explicitMain.id
  const weighted = [...postNodes].sort((a, b) => normalizeWeight(b.weight) - normalizeWeight(a.weight))[0]
  if (weighted) return weighted.id
  return nodes[0]?.id || null
}

function resolveCenterIdForMode(nodes: PanoramaGraphData['nodes'], mode: 'stack' | 'post'): string | null {
  const preferred = mode === 'stack'
    ? nodes.find(node => isTechStackNode(node.type))
    : nodes.find(node => isPostNode(node.type))
  return preferred?.id || resolveCenterId(nodes)
}

function collectCategories(nodes: PanoramaGraphData['nodes']): string[] {
  const set = new Set<string>()
  for (const node of nodes) {
    if (node.category) set.add(node.category)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-CN'))
}

function mapNode(
  node: PanoramaGraphData['nodes'][number],
  ring: Panorama3DRing,
  categories: string[],
): Panorama3DNode {
  const level = normalizeLevel(node.level)
  const weight = normalizeWeight(node.weight)
  const mapped: Panorama3DNode = {
    id: node.id,
    label: node.label,
    type: node.type,
    category: node.category,
    level,
    weight,
    ring,
    radius: getVisualRadius(ring, level, weight),
    color: '#94a3b8',
    position: { x: 0, y: 0, z: 0 },
    meta: node.meta,
  }
  mapped.color = getNodeColor(mapped)
  if (ring === 'center') mapped.radius = 14
  if (ring === 'center') mapped.color = '#1d4ed8'
  return mapped
}

function getNodeRing(node: PanoramaGraphData['nodes'][number], centerId: string | null | undefined, layoutMode: Panorama3DLayoutMode): Panorama3DRing {
  if (node.id === centerId) return 'center'
  if (layoutMode === 'post') {
    if (isPostNode(node.type)) return 'post'
    if (isTechStackNode(node.type)) return 'stack'
  } else {
    if (isTechStackNode(node.type)) return 'stack'
    if (isPostNode(node.type)) return 'post'
  }
  if (isAbilityNode(node.type)) return 'ability'
  if (isSkillNode(node.type)) return 'skill'
  return 'other'
}

function addStackLayer(nodes: Panorama3DNode[], edges: Panorama3DEdge[], categories: string[]) {
  if (nodes.some(node => isTechStackNode(node.type))) return
  const nodeMap = new Map(nodes.map(node => [node.id, node]))
  const postIdsByCategory = new Map<string, Set<string>>()
  for (const edge of edges) {
    const source = nodeMap.get(edge.source)
    const target = nodeMap.get(edge.target)
    const ability = source?.ring === 'ability' ? source : target?.ring === 'ability' ? target : null
    const post = isPostRing(source?.ring) ? source : isPostRing(target?.ring) ? target : null
    if (ability?.category && post) {
      const postIds = postIdsByCategory.get(ability.category) || new Set<string>()
      postIds.add(post.id)
      postIdsByCategory.set(ability.category, postIds)
    }
  }

  categories.forEach((category, index) => {
    const stackId = `stack:${category}`
    const angle = (Math.PI * 2 * index) / Math.max(categories.length, 1)
    const stackNode: Panorama3DNode = {
      id: stackId,
      label: category,
      type: 'TECH_STACK',
      category,
      ring: 'stack',
      radius: 10,
      color: '#7c3aed',
      position: { x: Math.cos(angle) * 80, y: 0, z: Math.sin(angle) * 80 },
    }
    nodes.push(stackNode)
    for (const postId of postIdsByCategory.get(category) || []) {
      edges.push({
        id: `${stackId}->${postId}`,
        source: stackId,
        target: postId,
        type: 'TECH_STACK_POST',
        weight: 0.9,
        color: '#7c3aed',
      })
    }
  })
}

function isPostRing(ring?: Panorama3DRing) {
  return ring === 'center' || ring === 'post'
}

function getSpherePosition(
  node: Panorama3DNode,
  index: number,
  total: number,
  categories: string[],
  layoutMode: Panorama3DLayoutMode,
): Panorama3DPosition {
  if (node.ring === 'center') return { x: 0, y: 0, z: 0 }
  const radius = SPHERE_RADIUS[node.ring] + (node.level || 3) * 16
  const categoryIndex = Math.max(0, categories.indexOf(node.category || ''))
  const categoryOffset = categories.length > 0 ? (Math.PI * 2 * categoryIndex) / categories.length : 0
  const goldenAngle = Math.PI * (3 - Math.sqrt(5))
  const y = 1 - (2 * (index + 0.5)) / Math.max(total, 1)
  const ringRadius = Math.sqrt(Math.max(0, 1 - y * y))
  const theta = index * goldenAngle + categoryOffset
  const levelY = layoutMode === 'post' ? y * radius * 0.48 : ((node.level || 3) - 3) * 70

  return {
    x: Math.cos(theta) * ringRadius * radius,
    y: levelY,
    z: Math.sin(theta) * ringRadius * radius,
  }
}

function applyForceLayout(
  nodes: Panorama3DNode[],
  edges: Panorama3DEdge[],
  categories: string[],
  layoutMode: Panorama3DLayoutMode,
) {
  const nodeMap = new Map(nodes.map(node => [node.id, node]))
  const velocities = new Map<string, Panorama3DPosition>()
  for (const node of nodes) velocities.set(node.id, { x: 0, y: 0, z: 0 })

  for (let tick = 0; tick < 90; tick += 1) {
    const cooling = 1 - tick / 110

    for (let i = 0; i < nodes.length; i += 1) {
      const a = nodes[i]
      if (a.ring === 'center') continue
      const va = velocities.get(a.id)!

      for (let j = i + 1; j < nodes.length; j += 1) {
        const b = nodes[j]
        if (b.ring === 'center') continue
        const vb = velocities.get(b.id)!
        const delta = subtract(a.position, b.position)
        const dist = Math.max(1, magnitude(delta))
        const strength = ((a.radius + b.radius + 34) / (dist * dist)) * 22
        const force = scale(normalize(delta), strength)
        addTo(va, force)
        addTo(vb, scale(force, -1))
      }

      const target = getModeTarget(a, categories, layoutMode)
      addTo(va, scale(subtract(target, a.position), layoutMode === 'level' ? 0.004 : 0.006))
      addTo(va, scale(a.position, -0.0012))
    }

    for (const edge of edges) {
      const source = nodeMap.get(edge.source)
      const target = nodeMap.get(edge.target)
      if (!source || !target) continue
      const delta = subtract(target.position, source.position)
      const dist = Math.max(1, magnitude(delta))
      const ideal = getIdealLength(source, target)
      const pull = (dist - ideal) * 0.006 * edge.weight
      const force = scale(normalize(delta), pull)
      if (source.ring !== 'center') addTo(velocities.get(source.id)!, force)
      if (target.ring !== 'center') addTo(velocities.get(target.id)!, scale(force, -1))
    }

    for (const node of nodes) {
      if (node.ring === 'center') {
        node.position = { x: 0, y: 0, z: 0 }
        continue
      }
      const velocity = velocities.get(node.id)!
      velocity.x *= 0.78
      velocity.y *= 0.78
      velocity.z *= 0.78
      addTo(node.position, scale(velocity, cooling))
      clampToSphere(node.position, SPHERE_RADIUS.other + 60)
    }
  }
}

function getModeTarget(
  node: Panorama3DNode,
  categories: string[],
  layoutMode: Panorama3DLayoutMode,
): Panorama3DPosition {
  const effectiveCategory = layoutMode === 'post'
    ? (node.category || `__${node.ring}__`)
    : (node.category || `__${node.ring}__`)
  const categoryIndex = Math.max(0, categories.indexOf(effectiveCategory))
  const angle = categories.length > 1
    ? (Math.PI * 2 * categoryIndex) / categories.length
    : (Math.PI * 2 * hashText(effectiveCategory)) / 128
  const radius = SPHERE_RADIUS[node.ring]
  if (layoutMode === 'post') return { x: Math.cos(angle) * radius, y: 0, z: Math.sin(angle) * radius }
  return {
    x: Math.cos(angle) * radius,
    y: 0,
    z: Math.sin(angle) * radius,
  }
}

function getIdealLength(source: Panorama3DNode, target: Panorama3DNode): number {
  if (source.ring === 'center' || target.ring === 'center') return 150
  const outer = source.ring === 'skill' || target.ring === 'skill' ? 'skill' : target.ring
  return IDEAL_EDGE_LENGTH[outer]
}

function getVisualRadius(ring: Panorama3DRing, level: number, weight: number): number {
  const base = ring === 'post' ? 4.6 : ring === 'stack' ? 7.2 : ring === 'ability' ? 3.4 : ring === 'skill' ? 2.2 : 3
  return base + level * 0.34 + weight * 1.35
}

function isPostNode(type: string): boolean {
  return type === 'post' || type === 'POST'
}

function isAbilityNode(type: string): boolean {
  return type === 'ability' || type === 'ABILITY' || type === 'ABILITY_FACT' || type === 'abilityFact' || type === 'postAbility' || type === 'skillPoint'
}

function isSkillNode(type: string): boolean {
  return type === 'skill' || type === 'SKILL' || type === 'abilityCategory'
}

function isTechStackNode(type: string): boolean {
  return type === 'techStack' || type === 'TECH_STACK'
}

function normalizeLevel(level?: number): number {
  if (!Number.isFinite(level)) return 3
  return Math.max(1, Math.min(5, Math.round(level || 3)))
}

function normalizeWeight(weight?: number): number {
  if (!Number.isFinite(weight)) return 0.5
  const value = Number(weight)
  if (value > 1) return Math.max(0, Math.min(1, value / 100))
  return Math.max(0, Math.min(1, value))
}

function hashText(text: string): number {
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

function subtract(a: Panorama3DPosition, b: Panorama3DPosition): Panorama3DPosition {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }
}

function addTo(a: Panorama3DPosition, b: Panorama3DPosition): void {
  a.x += b.x
  a.y += b.y
  a.z += b.z
}

function scale(a: Panorama3DPosition, value: number): Panorama3DPosition {
  return { x: a.x * value, y: a.y * value, z: a.z * value }
}

function magnitude(a: Panorama3DPosition): number {
  return Math.hypot(a.x, a.y, a.z)
}

function normalize(a: Panorama3DPosition): Panorama3DPosition {
  const length = magnitude(a) || 1
  return { x: a.x / length, y: a.y / length, z: a.z / length }
}

function clampToSphere(position: Panorama3DPosition, maxRadius: number): void {
  const length = magnitude(position)
  if (length <= maxRadius) return
  const ratio = maxRadius / length
  position.x *= ratio
  position.y *= ratio
  position.z *= ratio
}
