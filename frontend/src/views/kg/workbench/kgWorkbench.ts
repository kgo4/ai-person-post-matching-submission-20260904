/**
 * AI 常读图谱 - 纯函数模块
 * 职责：viewMode 到 API 参数的映射、节点类型 label/color、统计派生、关系分组
 */
import type { GraphNode, GraphEdge, GraphData, GraphMatchContext, GraphMatchAbilityContext, GraphStats } from '@/api/kg'
import type { ForceNode, ForceEdge } from '@/components/graph/AbilityForceGraph.vue'

// ===================== 类型定义 =====================

export type ViewMode = 'all' | 'employee' | 'post' | 'ability' | 'memory' | 'match'

export interface ViewModeConfig {
  value: ViewMode
  label: string
}

export interface NodeTypeOption {
  value: string
  label: string
  color: string
}

export interface TimelineEventConfig {
  label: string
  color: string
  icon: string
}

export interface RelatedRelation {
  edgeType: string
  sourceLabel: string
  targetLabel: string
  otherNodeId: string
  direction: 'upstream' | 'downstream' | 'other'
}

// ===================== 视角配置 =====================

export const VIEW_MODES: ViewModeConfig[] = [
  { value: 'all', label: '全景' },
  { value: 'employee', label: '人员' },
  { value: 'post', label: '岗位' },
  { value: 'ability', label: '能力' },
  { value: 'memory', label: '记忆' },
  { value: 'match', label: '人岗匹配' },
]

// ===================== 节点类型配置 =====================

export const NODE_TYPE_OPTIONS: NodeTypeOption[] = [
  { value: 'POST', label: '岗位', color: '#059669' },
  { value: 'ABILITY', label: '能力', color: '#2563eb' },
  { value: 'EMPLOYEE', label: '员工', color: '#d97706' },
  { value: 'EVIDENCE', label: '证据', color: '#dc2626' },
  { value: 'AGENT_MEMORY', label: 'Agent记忆', color: '#7c3aed' },
  { value: 'GOVERNANCE_EVENT', label: '治理事件', color: '#dc2626' },
  { value: 'SOURCE_SYSTEM', label: '来源系统', color: '#4f46e5' },
  { value: 'RAG_DOCUMENT', label: '知识文档', color: '#0e7490' },
  { value: 'EVOLUTION_EVENT', label: '演化事件', color: '#db2777' },
  { value: 'KNOWLEDGE_DOMAIN', label: '知识领域', color: '#0f766e' },
  { value: 'KNOWLEDGE_NODE', label: '知识点', color: '#7e22ce' },
  { value: 'LEARNING_RESOURCE', label: '学习资源', color: '#0d9488' },
]

export const NODE_TYPE_LABEL_MAP: Record<string, string> = {
  POST: '岗位',
  ABILITY: '能力',
  EMPLOYEE: '员工',
  EVIDENCE: '证据',
  AGENT_MEMORY: 'Agent记忆',
  GOVERNANCE_EVENT: '治理事件',
  SOURCE_SYSTEM: '来源系统',
  RAG_DOCUMENT: '知识文档',
  EVOLUTION_EVENT: '演化事件',
  LEARNING_RESOURCE: '学习资源',
  KNOWLEDGE_DOMAIN: '知识领域',
  KNOWLEDGE_NODE: '知识点',
  // ForceNode 兼容
  employee: '人员',
  abilityCategory: '能力大类',
  ability: '能力',
  post: '岗位',
  postAbility: '岗位要求能力',
}

export const NODE_TYPE_COLOR_MAP: Record<string, string> = {
  POST: 'success',
  ABILITY: 'primary',
  EMPLOYEE: 'warning',
  EVIDENCE: '',
  AGENT_MEMORY: 'warning',
  GOVERNANCE_EVENT: 'danger',
  SOURCE_SYSTEM: '',
  RAG_DOCUMENT: 'info',
  KNOWLEDGE_DOMAIN: 'success',
  KNOWLEDGE_NODE: 'primary',
  LEARNING_RESOURCE: 'success',
}

// ===================== 时间线事件配置 =====================

export const TIMELINE_EVENT_CONFIG: Record<string, TimelineEventConfig> = {
  NODE_ADDED: { label: '新增节点', color: '#2563eb', icon: '+' },
  EDGE_ADDED: { label: '新增关系', color: '#059669', icon: '→' },
  GOVERNANCE: { label: '治理事件', color: '#dc2626', icon: '!' },
  EVOLUTION: { label: '演化事件', color: '#ec4899', icon: '↻' },
}

// ===================== 数据转换 =====================

/**
 * 将 GraphNode 转换为 ForceNode
 */
export function toForceNode(n: GraphNode): ForceNode {
  return {
    id: n.id,
    label: n.label,
    type: n.type as ForceNode['type'],
    level: n.level,
    weight: n.weight,
    category: n.category,
    status: n.status,
    meta: n.metadata,
  }
}

/**
 * 将 GraphEdge 转换为 ForceEdge
 */
export function toForceEdge(e: GraphEdge): ForceEdge {
  return {
    source: e.source,
    target: e.target,
    type: e.type as ForceEdge['type'],
    weight: e.weight,
    label: e.metadata?.label,
    style: e.metadata?.dash ? 'dashed' : 'solid',
  }
}

// ===================== 关系分析 =====================

/**
 * 获取选中节点的关联节点列表
 */
export function getRelatedNodes(
  selectedId: string,
  edges: GraphEdge[],
  nodes: GraphNode[]
): RelatedRelation[] {
  const nodeMap = new Map(nodes.map(n => [n.id, n]))

  return edges
    .filter(e => e.source === selectedId || e.target === selectedId)
    .map(e => ({
      edgeType: e.type,
      sourceLabel: nodeMap.get(e.source)?.label || e.source,
      targetLabel: nodeMap.get(e.target)?.label || e.target,
      otherNodeId: e.source === selectedId ? e.target : e.source,
      direction: (e.source === selectedId ? 'downstream' : 'upstream') as 'upstream' | 'downstream',
    }))
}

/**
 * 将关联关系按方向分组
 */
export function groupRelationsByDirection(
  relations: RelatedRelation[]
): Record<string, RelatedRelation[]> {
  const groups: Record<string, RelatedRelation[]> = {
    upstream: [],
    downstream: [],
    other: [],
  }
  for (const rel of relations) {
    if (rel.direction === 'upstream') groups.upstream.push(rel)
    else if (rel.direction === 'downstream') groups.downstream.push(rel)
    else groups.other.push(rel)
  }
  return groups
}

// ===================== 工具函数 =====================

/**
 * 获取视图模式的显示名称
 */
export function getViewModeLabel(mode: ViewMode): string {
  return VIEW_MODES.find(m => m.value === mode)?.label || '全景'
}

/**
 * 格式化时间戳
 */
export function formatTime(ts: string | undefined): string {
  if (!ts) return '-'
  try {
    return new Date(ts).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return ts
  }
}

/**
 * 获取时间线事件配置，带默认值
 */
export function getTimelineEventConfig(eventType: string): TimelineEventConfig {
  return TIMELINE_EVENT_CONFIG[eventType] || TIMELINE_EVENT_CONFIG.NODE_ADDED
}

// ===================== 匹配上下文映射 =====================

/**
 * 获取关联节点的标签（用于直接关系行显示）
 */
export function getRelatedNodeLabel(relation: RelatedRelation): string {
  return relation.direction === 'downstream' ? relation.targetLabel : relation.sourceLabel
}

function employeeNode(ctx: GraphMatchContext): GraphNode {
  return {
    id: `EMPLOYEE:${ctx.employeeId}`,
    label: ctx.employeeName || `员工${ctx.employeeId}`,
    type: 'EMPLOYEE',
    metadata: { graphVersion: ctx.graphVersion },
  }
}

function postNode(ctx: GraphMatchContext): GraphNode {
  return {
    id: `POST:${ctx.postId}`,
    label: ctx.postName || `岗位${ctx.postId}`,
    type: 'POST',
    metadata: { graphVersion: ctx.graphVersion },
  }
}

function abilityNode(ability: GraphMatchAbilityContext, graphVersion: string | null): GraphNode {
  return {
    id: `ABILITY:${ability.abilityId}`,
    label: ability.abilityName,
    type: 'ABILITY',
    weight: ability.weight,
    level: ability.requiredLevel ?? undefined,
    metadata: {
      matchState: ability.state,
      requiredLevel: ability.requiredLevel,
      employeeMasteryLevel: ability.employeeMasteryLevel,
      required: ability.required,
      core: ability.core,
      evidenceCount: ability.evidence.length,
      sourceRefs: ability.evidence.flatMap(e => e.sourceRefs),
      reviewStatus: ability.evidence[0]?.reviewStatus ?? null,
      graphVersion: ability.evidence[0]?.graphVersion ?? graphVersion,
    },
  }
}

function requirementEdge(postId: number, ability: GraphMatchAbilityContext): GraphEdge {
  return {
    id: `POST:${postId}-REQUIRES-ABILITY:${ability.abilityId}`,
    source: `POST:${postId}`,
    target: `ABILITY:${ability.abilityId}`,
    type: 'REQUIRES',
    weight: ability.weight,
    metadata: { label: '要求' },
  }
}

function employeeAbilityEdge(employeeId: number, ability: GraphMatchAbilityContext): GraphEdge {
  return {
    id: `EMPLOYEE:${employeeId}-HAS_ABILITY-ABILITY:${ability.abilityId}`,
    source: `EMPLOYEE:${employeeId}`,
    target: `ABILITY:${ability.abilityId}`,
    type: 'HAS_ABILITY',
    weight: ability.weight,
    metadata: { masteryLevel: ability.employeeMasteryLevel },
  }
}

function graphStats(nodes: GraphNode[], edges: GraphEdge[]): GraphStats {
  return {
    nodeCount: nodes.length,
    edgeCount: edges.length,
    postCount: nodes.filter(n => n.type === 'POST').length,
    abilityCount: nodes.filter(n => n.type === 'ABILITY').length,
    evidenceCount: nodes.filter(n => ['EVIDENCE', 'DOCUMENT', 'KNOWLEDGE_DOCUMENT'].includes(String(n.type).toUpperCase())).length,
    evolutionCount: 0,
    knowledgeDomainCount: 0,
    knowledgeNodeCount: 0,
    prerequisiteCount: edges.filter(e => e.type === 'PREREQUISITE_OF').length,
  }
}

/**
 * 将 GraphMatchContext 转换为前端 GraphData（纯函数）
 */
export function graphDataFromMatchContext(context: GraphMatchContext): GraphData {
  const nodes: GraphNode[] = [employeeNode(context), postNode(context)]
  const edges: GraphEdge[] = []
  const evidenceKeys = new Set<string>()
  for (const ability of context.abilities) {
    nodes.push(abilityNode(ability, context.graphVersion))
    edges.push(requirementEdge(context.postId, ability))
    if (ability.employeeMasteryLevel != null) {
      edges.push(employeeAbilityEdge(context.employeeId, ability))
    }
    for (const evidence of ability.evidence || []) {
      const evidenceId = evidence.evidenceId ?? `${ability.abilityId}-${evidence.label}`
      const key = `EVIDENCE:${evidenceId}`
      if (!evidenceKeys.has(key)) {
        evidenceKeys.add(key)
        nodes.push({ id: key, label: evidence.label || '有效证据', type: 'EVIDENCE', metadata: {
          confidence: evidence.confidence, reviewStatus: evidence.reviewStatus,
          sourceRefs: evidence.sourceRefs, graphVersion: evidence.graphVersion,
        } })
      }
      edges.push({ id: `ABILITY:${ability.abilityId}-SUPPORTED_BY-${key}`, source: `ABILITY:${ability.abilityId}`, target: key, type: 'SUPPORTED_BY', weight: evidence.confidence ?? undefined })
    }
  }
  return { available: context.status === 'AVAILABLE', nodes, edges, stats: graphStats(nodes, edges) }
}
