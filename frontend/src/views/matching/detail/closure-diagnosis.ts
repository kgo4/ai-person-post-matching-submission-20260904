import type { ClosureGapItem, LearningOutcomeConfirmDTO, MatchDiagnosisResult } from '@/api/capability-closure'
import type { LearningPathItem } from '@/api/learning'

export interface NormalizedClosureGap extends ClosureGapItem {
  currentLevel: number
  requiredLevel: number
  severity: 'danger' | 'warning'
}

export interface NormalizedDiagnosis {
  gaps: NormalizedClosureGap[]
  learningByAbility: Record<string, LearningPathItem[]>
}

export function normalizeDiagnosis(diagnosis?: Partial<MatchDiagnosisResult> | null): NormalizedDiagnosis {
  const gaps = (diagnosis?.gaps || []).map((gap) => {
    const currentLevel = Number(gap.currentLevel || 0)
    const requiredLevel = Number(gap.requiredLevel || 0)
    return {
      ...gap,
      abilityName: gap.abilityName || `Ability#${gap.tagId || '-'}`,
      currentLevel,
      requiredLevel,
      severity: gap.weakEvidence || currentLevel < requiredLevel ? 'danger' : 'warning',
    } as NormalizedClosureGap
  })

  const learningByAbility: Record<string, LearningPathItem[]> = {}
  for (const item of diagnosis?.learningPath || []) {
    const key = item.abilityName || 'Unknown'
    if (!learningByAbility[key]) learningByAbility[key] = []
    learningByAbility[key].push(item)
  }

  return { gaps, learningByAbility }
}

export function buildLearningOutcomePayload(
  matchRecord: { empId?: number | null },
  gap: NormalizedClosureGap,
  resource?: LearningPathItem,
): LearningOutcomeConfirmDTO {
  const beforeLevel = Number(gap.currentLevel || 0)
  const targetLevel = Number(gap.requiredLevel || beforeLevel || 1)
  return {
    empId: Number(matchRecord.empId),
    tagId: gap.tagId,
    abilityName: gap.abilityName,
    completedResourceId: resource?.resourceId,
    beforeLevel: beforeLevel > 0 ? beforeLevel : undefined,
    confirmedLevel: clampLevel(Math.max(targetLevel, beforeLevel)),
    confirmationSource: 'MANUAL_CONFIRM',
    note: resource?.title ? `Completed learning resource: ${resource.title}` : `Confirmed improvement for ${gap.abilityName}`,
  }
}

function clampLevel(level: number) {
  if (level < 1) return 1
  if (level > 5) return 5
  return level
}
