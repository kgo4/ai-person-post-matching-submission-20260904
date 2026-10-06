import type { ForceNode, ForceEdge, NodeVisualConfig } from './config'
import { TYPE_NAMES } from './config'

export function buildAdjacencyMap(edges: ForceEdge[]): Map<string, Set<string>> {
  const map = new Map<string, Set<string>>()
  for (const e of edges) {
    const s = typeof e.source === 'object' ? (e.source as any).id : e.source
    const t = typeof e.target === 'object' ? (e.target as any).id : e.target
    if (!map.has(s)) map.set(s, new Set())
    if (!map.has(t)) map.set(t, new Set())
    map.get(s)!.add(t)
    map.get(t)!.add(s)
  }
  return map
}

export function getRelatedNodeIds(nodeId: string, adjacencyMap: Map<string, Set<string>>): Set<string> {
  const result = new Set<string>([nodeId])
  const neighbors = adjacencyMap.get(nodeId)
  if (neighbors) {
    for (const id of neighbors) result.add(id)
  }
  return result
}

export function isEdgeIdRelated(edge: any, nodeId: string): boolean {
  const s = typeof edge.source === 'object' ? edge.source.id : edge.source
  const t = typeof edge.target === 'object' ? edge.target.id : edge.target
  return s === nodeId || t === nodeId
}

export function buildTooltipContent(
  d: ForceNode,
  nodeConfig: Record<string, NodeVisualConfig>,
  adjacencyMap: Map<string, Set<string>>,
): string {
  // 转义 HTML 实体防止 XSS
  const esc = (s: unknown): string => {
    if (s == null) return ''
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
  }

  let html = `<div style="font-weight:700;font-size:14px;margin-bottom:6px;color:#f1f5f9">${esc(d.label)}</div>`
  html += `<div style="display:flex;align-items:center;gap:6px;margin-bottom:4px">`
  html += `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${esc(nodeConfig[d.type]?.color || '#475569')}"></span>`
  html += `<span>${esc(TYPE_NAMES[d.type] || d.type)}</span>`
  html += `</div>`

  if (d.category) html += `<div style="color:#94a3b8">分类：${esc(d.category)}</div>`
  if (d.level != null) html += `<div style="color:#94a3b8">等级：L${esc(d.level)}</div>`
  if (d.weight != null) html += `<div style="color:#94a3b8">权重：${esc((d.weight * 100).toFixed(0))}%</div>`
  if (d.status) html += `<div style="color:#94a3b8">状态：${d.status === 'ACTIVE' ? '活跃' : esc(d.status)}</div>`
  if (d.matchStatus) html += `<div style="color:${d.matchStatus === 'match' ? '#22c55e' : '#ef4444'}">${d.matchStatus === 'match' ? '匹配' : '缺失'}</div>`

  const neighbors = adjacencyMap.get(d.id)
  if (neighbors) html += `<div style="color:#64748b;margin-top:4px;font-size:11px">关联 ${neighbors.size} 个节点</div>`

  return html
}

export interface KeyboardManager {
  /** Register an ESC handler. Returns an cleanup function. */
  onEscape(handler: () => void): () => void
  /** Remove all keyboard listeners. */
  destroy(): void
}

export function createKeyboardManager(): KeyboardManager {
  const cleanups: Array<() => void> = []

  function onEscape(handler: () => void): () => void {
    const listener = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handler()
    }
    document.addEventListener('keydown', listener)
    const cleanup = () => document.removeEventListener('keydown', listener)
    cleanups.push(cleanup)
    return cleanup
  }

  function destroy() {
    for (const fn of cleanups) fn()
    cleanups.length = 0
  }

  return { onEscape, destroy }
}
