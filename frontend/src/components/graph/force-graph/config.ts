// ============ 类型定义 ============
export interface ForceNode {
  id: string
  label: string
  type: 'employee' | 'abilityCategory' | 'ability' | 'abilityFact' | 'ABILITY' | 'ABILITY_FACT' | 'post' | 'postAbility'
    | 'AGENT_MEMORY' | 'GOVERNANCE_EVENT' | 'SOURCE_SYSTEM' | 'EVIDENCE'
    | 'RAG_DOCUMENT' | 'EVOLUTION_EVENT' | 'LEARNING_RESOURCE'
    | 'KNOWLEDGE_DOMAIN' | 'KNOWLEDGE_NODE'
  level?: number
  weight?: number
  category?: string
  matchStatus?: 'match' | 'mismatch'
  status?: string
  meta?: Record<string, any>
  x?: number
  y?: number
  fx?: number | null
  fy?: number | null
}

export interface ForceEdge {
  source: string
  target: string
  type: 'employee-category' | 'category-ability' | 'employee-post' | 'post-postAbility' | 'ability-postAbility'
    | 'HAS_ABILITY' | 'HAS_ABILITY_FACT' | 'REQUIRES' | 'SUPPORTED_BY' | 'NORMALIZED_TO'
    | 'GENERATED_MEMORY' | 'USED_BY_AGENT' | 'MATCHES'
    | 'CORE_REQUIRES' | 'GAP' | 'LEARN_BY' | 'SIMILAR_TO' | 'EVOLVED_FROM' | 'MATCHED_BY'
    | 'BELONGS_TO_DOMAIN' | 'HAS_KNOWLEDGE_NODE' | 'PARENT_OF' | 'PREREQUISITE_OF'
  label?: string
  weight?: number
  style: 'solid' | 'dashed'
  color?: string
}

// ============ 类型分组 ============
export const PRIMARY_TYPES = new Set<string>(['employee', 'post', 'SOURCE_SYSTEM'])
export const SECONDARY_TYPES = new Set<string>(['abilityCategory', 'AGENT_MEMORY', 'RAG_DOCUMENT'])
export const TERTIARY_TYPES = new Set<string>(['ability', 'postAbility', 'EVIDENCE', 'GOVERNANCE_EVENT', 'EVOLUTION_EVENT', 'LEARNING_RESOURCE'])
TERTIARY_TYPES.add('ABILITY')
TERTIARY_TYPES.add('ABILITY_FACT')

// ============ 性能常量 ============
export const LARGE_GRAPH_THRESHOLD = 80
export const STABLE_ALPHA = 0.008
export const MAX_TICKS = 400

// ============ 配色方案 ============
export interface NodeVisualConfig {
  color: string
  gradient: [string, string]
  size: number
  glow: string
}

export interface EdgeVisualConfig {
  color: string
  width: number
  dasharray?: string
}

// Dark theme (default)
export const DEFAULT_NODE_CONFIG: Record<string, NodeVisualConfig> = {
  employee:          { color: '#9333ea', gradient: ['#c084fc', '#7c3aed'], size: 40, glow: 'rgba(147, 51, 234, 0.5)' },
  abilityCategory:   { color: '#2563eb', gradient: ['#60a5fa', '#1d4ed8'], size: 26, glow: 'rgba(37, 99, 235, 0.4)' },
  ability:           { color: '#2563eb', gradient: ['#3b82f6', '#1e40af'], size: 16, glow: 'rgba(37, 99, 235, 0.4)' },
  ABILITY:           { color: '#2563eb', gradient: ['#3b82f6', '#1e40af'], size: 16, glow: 'rgba(37, 99, 235, 0.4)' },
  ABILITY_FACT:      { color: '#0f766e', gradient: ['#2dd4bf', '#0f766e'], size: 16, glow: 'rgba(15, 118, 110, 0.4)' },
  post:              { color: '#16a34a', gradient: ['#4ade80', '#15803d'], size: 34, glow: 'rgba(22, 163, 74, 0.5)' },
  postAbility:       { color: '#0891b2', gradient: ['#22d3ee', '#0e7490'], size: 16, glow: 'rgba(8, 145, 178, 0.4)' },
  AGENT_MEMORY:      { color: '#f59e0b', gradient: ['#fbbf24', '#d97706'], size: 22, glow: 'rgba(245, 158, 11, 0.4)' },
  GOVERNANCE_EVENT:  { color: '#ef4444', gradient: ['#f87171', '#dc2626'], size: 20, glow: 'rgba(239, 68, 68, 0.4)' },
  SOURCE_SYSTEM:     { color: '#6366f1', gradient: ['#818cf8', '#4f46e5'], size: 24, glow: 'rgba(99, 102, 241, 0.4)' },
  EVIDENCE:          { color: '#ef4444', gradient: ['#fca5a5', '#dc2626'], size: 14, glow: 'rgba(239, 68, 68, 0.4)' },
  RAG_DOCUMENT:      { color: '#0891b2', gradient: ['#22d3ee', '#0891b2'], size: 18, glow: 'rgba(8, 145, 178, 0.4)' },
  EVOLUTION_EVENT:   { color: '#ec4899', gradient: ['#f472b6', '#db2777'], size: 18, glow: 'rgba(236, 72, 153, 0.4)' },
  LEARNING_RESOURCE: { color: '#14b8a6', gradient: ['#2dd4bf', '#0d9488'], size: 18, glow: 'rgba(20, 184, 166, 0.4)' },
  KNOWLEDGE_DOMAIN:  { color: '#0f766e', gradient: ['#2dd4bf', '#0f766e'], size: 24, glow: 'rgba(15, 118, 110, 0.4)' },
  KNOWLEDGE_NODE:    { color: '#9333ea', gradient: ['#c084fc', '#7e22ce'], size: 15, glow: 'rgba(147, 51, 234, 0.35)' },
}

// Tech-Light theme (Variant C — 多色相语义配色, 类型区分更清晰)
export const TECH_LIGHT_NODE_CONFIG: Record<string, NodeVisualConfig> = {
  employee:          { color: '#d97706', gradient: ['#fbbf24', '#b45309'], size: 42, glow: 'rgba(217, 119, 6, 0.3)' },
  abilityCategory:   { color: '#2563eb', gradient: ['#93c5fd', '#1d4ed8'], size: 28, glow: 'rgba(59, 130, 246, 0.26)' },
  ability:           { color: '#2563eb', gradient: ['#93c5fd', '#2563eb'], size: 16, glow: 'rgba(59, 130, 246, 0.22)' },
  ABILITY:           { color: '#2563eb', gradient: ['#93c5fd', '#2563eb'], size: 16, glow: 'rgba(59, 130, 246, 0.22)' },
  ABILITY_FACT:      { color: '#0f766e', gradient: ['#5eead4', '#0f766e'], size: 16, glow: 'rgba(13, 148, 136, 0.22)' },
  post:              { color: '#059669', gradient: ['#34d399', '#047857'], size: 36, glow: 'rgba(5, 150, 105, 0.28)' },
  postAbility:       { color: '#0891b2', gradient: ['#67e8f9', '#0e7490'], size: 16, glow: 'rgba(8, 145, 178, 0.22)' },
  AGENT_MEMORY:      { color: '#7c3aed', gradient: ['#c4b5fd', '#6d28d9'], size: 22, glow: 'rgba(124, 58, 237, 0.26)' },
  GOVERNANCE_EVENT:  { color: '#dc2626', gradient: ['#fca5a5', '#b91c1c'], size: 20, glow: 'rgba(220, 38, 38, 0.24)' },
  SOURCE_SYSTEM:     { color: '#4f46e5', gradient: ['#a5b4fc', '#4338ca'], size: 26, glow: 'rgba(79, 70, 229, 0.26)' },
  EVIDENCE:          { color: '#dc2626', gradient: ['#fca5a5', '#991b1b'], size: 14, glow: 'rgba(220, 38, 38, 0.22)' },
  RAG_DOCUMENT:      { color: '#0e7490', gradient: ['#67e8f9', '#155e75'], size: 18, glow: 'rgba(14, 116, 144, 0.24)' },
  EVOLUTION_EVENT:   { color: '#db2777', gradient: ['#f9a8d4', '#be185d'], size: 18, glow: 'rgba(219, 39, 119, 0.24)' },
  LEARNING_RESOURCE: { color: '#0d9488', gradient: ['#5eead4', '#0f766e'], size: 18, glow: 'rgba(13, 148, 136, 0.24)' },
  KNOWLEDGE_DOMAIN:  { color: '#0f766e', gradient: ['#5eead4', '#0f766e'], size: 24, glow: 'rgba(15, 118, 110, 0.26)' },
  KNOWLEDGE_NODE:    { color: '#7e22ce', gradient: ['#c4b5fd', '#6d28d9'], size: 15, glow: 'rgba(126, 34, 206, 0.2)' },
}

export const DEFAULT_EDGE_CONFIG: Record<string, EdgeVisualConfig> = {
  'employee-category': { color: 'rgba(148, 163, 184, 0.4)', width: 1.2 },
  HAS_ABILITY_FACT: { color: 'rgba(15, 118, 110, 0.55)', width: 1.4, dasharray: '3,2' },
  'category-ability':  { color: 'rgba(148, 163, 184, 0.35)', width: 1 },
  'employee-post':     { color: 'rgba(148, 163, 184, 0.4)', width: 1.5 },
  'post-postAbility':  { color: 'rgba(148, 163, 184, 0.3)', width: 1, dasharray: '4,3' },
  'ability-postAbility': { color: 'rgba(148, 163, 184, 0.3)', width: 1 },
  'HAS_ABILITY':     { color: 'rgba(37, 99, 235, 0.35)', width: 1.5 },
  'REQUIRES':        { color: 'rgba(22, 163, 74, 0.35)', width: 1.5, dasharray: '5,3' },
  'SUPPORTED_BY':    { color: 'rgba(245, 158, 11, 0.3)', width: 1 },
  'NORMALIZED_TO':   { color: 'rgba(99, 102, 241, 0.3)', width: 1, dasharray: '3,2' },
  'GENERATED_MEMORY': { color: 'rgba(239, 68, 68, 0.3)', width: 1 },
  'USED_BY_AGENT':   { color: 'rgba(245, 158, 11, 0.3)', width: 1, dasharray: '5,3' },
  'MATCHES':         { color: 'rgba(22, 163, 74, 0.5)', width: 2 },
  'CORE_REQUIRES':   { color: 'rgba(239, 68, 68, 0.5)', width: 2 },
  'GAP':             { color: 'rgba(239, 68, 68, 0.4)', width: 1.5, dasharray: '5,3' },
  'LEARN_BY':        { color: 'rgba(20, 184, 166, 0.3)', width: 1, dasharray: '3,2' },
  'SIMILAR_TO':      { color: 'rgba(99, 102, 241, 0.3)', width: 1 },
  'EVOLVED_FROM':    { color: 'rgba(236, 72, 153, 0.3)', width: 1, dasharray: '5,3' },
  'MATCHED_BY':      { color: 'rgba(22, 163, 74, 0.4)', width: 1.5 },
  'BELONGS_TO_DOMAIN': { color: 'rgba(13, 148, 136, 0.35)', width: 1.2 },
  'HAS_KNOWLEDGE_NODE': { color: 'rgba(8, 145, 178, 0.3)', width: 1 },
  'PARENT_OF':       { color: 'rgba(124, 58, 237, 0.3)', width: 1 },
  'PREREQUISITE_OF': { color: 'rgba(147, 51, 234, 0.5)', width: 1.8, dasharray: '6,3' },
  'MATCH_SATISFIED': { color: 'rgba(34, 197, 94, 0.6)', width: 2 },
  'MATCH_LEVEL_GAP': { color: 'rgba(245, 158, 11, 0.6)', width: 2, dasharray: '5,3' },
  'MATCH_MISSING': { color: 'rgba(239, 68, 68, 0.6)', width: 2, dasharray: '5,3' },
}

export const TECH_LIGHT_EDGE_CONFIG: Record<string, EdgeVisualConfig> = {
  REQUIRES: { color: 'rgba(37, 99, 235, 0.44)', width: 1.6, dasharray: '5,3' },
  SUPPORTED_BY: { color: 'rgba(59, 130, 246, 0.4)', width: 1.2 },
  GENERATED_MEMORY: { color: 'rgba(37, 99, 235, 0.4)', width: 1.2 },
  USED_BY_AGENT: { color: 'rgba(59, 130, 246, 0.4)', width: 1.2, dasharray: '5,3' },
  MATCHES: { color: 'rgba(5, 150, 105, 0.6)', width: 2 },
  CORE_REQUIRES: { color: 'rgba(37, 99, 235, 0.6)', width: 2 },
  GAP: { color: 'rgba(220, 38, 38, 0.5)', width: 1.6, dasharray: '5,3' },
  LEARN_BY: { color: 'rgba(5, 150, 105, 0.44)', width: 1.2, dasharray: '3,2' },
  EVOLVED_FROM: { color: 'rgba(217, 119, 6, 0.44)', width: 1.2, dasharray: '5,3' },
  HAS_ABILITY: { color: 'rgba(217, 119, 6, 0.42)', width: 1.5 },
  NORMALIZED_TO: { color: 'rgba(79, 70, 229, 0.4)', width: 1.2, dasharray: '3,2' },
  SIMILAR_TO: { color: 'rgba(79, 70, 229, 0.4)', width: 1.2 },
  MATCHED_BY: { color: 'rgba(5, 150, 105, 0.5)', width: 1.6 },
  PREREQUISITE_OF: { color: 'rgba(124, 58, 237, 0.5)', width: 1.8, dasharray: '6,3' },
  'employee-post': { color: 'rgba(148, 163, 184, 0.5)', width: 1.6 },
  'employee-category': { color: 'rgba(148, 163, 184, 0.44)', width: 1.3 },
  'category-ability': { color: 'rgba(148, 163, 184, 0.4)', width: 1.1 },
  'post-postAbility': { color: 'rgba(148, 163, 184, 0.36)', width: 1.1, dasharray: '4,3' },
  'ability-postAbility': { color: 'rgba(148, 163, 184, 0.36)', width: 1.1 },
  BELONGS_TO_DOMAIN: { color: 'rgba(13, 148, 136, 0.44)', width: 1.3 },
  HAS_KNOWLEDGE_NODE: { color: 'rgba(8, 145, 178, 0.4)', width: 1.2 },
  PARENT_OF: { color: 'rgba(124, 58, 237, 0.4)', width: 1.2 },
  MATCH_SATISFIED: { color: 'rgba(5, 150, 105, 0.65)', width: 2 },
  MATCH_LEVEL_GAP: { color: 'rgba(217, 119, 6, 0.58)', width: 2, dasharray: '5,3' },
  MATCH_MISSING: { color: 'rgba(220, 38, 38, 0.58)', width: 2, dasharray: '5,3' },
}

export const DEFAULT_EDGE_HIGHLIGHT_COLORS: Record<string, string> = {
  'HAS_ABILITY': '#3b82f6',
  'REQUIRES': '#22c55e',
  'MATCHES': '#22c55e',
  'CORE_REQUIRES': '#ef4444',
  'GAP': '#ef4444',
  'GENERATED_MEMORY': '#ef4444',
  'USED_BY_AGENT': '#f59e0b',
  'SUPPORTED_BY': '#f59e0b',
  'NORMALIZED_TO': '#818cf8',
  'EVOLVED_FROM': '#ec4899',
  'MATCHED_BY': '#22c55e',
  'LEARN_BY': '#14b8a6',
  'SIMILAR_TO': '#6366f1',
  'PREREQUISITE_OF': '#9333ea',
}

export const TECH_LIGHT_EDGE_HIGHLIGHT_COLORS: Record<string, string> = {
  REQUIRES: '#2563eb', MATCHES: '#059669', CORE_REQUIRES: '#2563eb',
  GAP: '#dc2626', GENERATED_MEMORY: '#2563eb', USED_BY_AGENT: '#3b82f6',
  SUPPORTED_BY: '#3b82f6', EVOLVED_FROM: '#d97706',
  PREREQUISITE_OF: '#2563eb',
}

export const LEGEND_ITEMS = [
  { type: 'post', label: '岗位' },
  { type: 'ability', label: '能力' },
  { type: 'employee', label: '员工' },
  { type: 'EVIDENCE', label: '证据' },
  { type: 'RAG_DOCUMENT', label: '知识文档' },
  { type: 'AGENT_MEMORY', label: 'Agent记忆' },
  { type: 'EVOLUTION_EVENT', label: '演化事件' },
]

export const TYPE_NAMES: Record<string, string> = {
  employee: '员工', abilityCategory: '能力大类', ability: '能力',
  post: '岗位', postAbility: '岗位要求能力', AGENT_MEMORY: 'Agent记忆',
  GOVERNANCE_EVENT: '治理事件', SOURCE_SYSTEM: '来源系统', EVIDENCE: '证据',
  RAG_DOCUMENT: '知识文档', EVOLUTION_EVENT: '演化事件', LEARNING_RESOURCE: '学习资源',
}

export function truncateLabel(label: string, maxLen: number): string {
  if (!label) return ''
  return label.length > maxLen ? `${label.slice(0, maxLen)}…` : label
}

export function buildNodeConfig(theme: 'dark' | 'tech-light'): Record<string, NodeVisualConfig> {
  if (theme === 'tech-light') return { ...DEFAULT_NODE_CONFIG, ...TECH_LIGHT_NODE_CONFIG }
  return { ...DEFAULT_NODE_CONFIG }
}

export function buildEdgeConfig(theme: 'dark' | 'tech-light'): Record<string, EdgeVisualConfig> {
  const base = { ...DEFAULT_EDGE_CONFIG }
  if (theme === 'tech-light') {
    for (const [key, val] of Object.entries(TECH_LIGHT_EDGE_CONFIG)) {
      base[key] = val
    }
  }
  return base
}

export function buildEdgeHighlightColors(theme: 'dark' | 'tech-light'): Record<string, string> {
  const base = { ...DEFAULT_EDGE_HIGHLIGHT_COLORS }
  if (theme === 'tech-light') {
    for (const [key, val] of Object.entries(TECH_LIGHT_EDGE_HIGHLIGHT_COLORS)) {
      base[key] = val
    }
  }
  return base
}
