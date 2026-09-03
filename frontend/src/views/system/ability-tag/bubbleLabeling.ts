import type { AbilityTagUsageStat } from '@/api/tag-governance'

export function shouldShowBubbleLabel(stat: AbilityTagUsageStat, index: number): boolean {
  const postCount = Number(stat.usedByPostCount) || 0
  const empCount = Number(stat.usedByEmpCount) || 0
  const heat = Number(stat.heatScore) || 0

  if (index < 3) return true
  if (heat >= 80) return true
  if (postCount >= 15) return true
  if (empCount >= 10) return true
  return false
}
