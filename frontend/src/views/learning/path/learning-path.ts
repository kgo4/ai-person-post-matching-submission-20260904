export interface LearningPathGapInput {
  tagId?: number
  abilityName?: string
  currentLevel?: number | string
  requiredLevel?: number | string
  weakEvidence?: boolean
  reason?: string
}

export interface LearningPathResourceInput {
  abilityName?: string
  tagId?: number
  resourceId?: number
  title?: string
  resourceType?: string
  difficultyLevel?: number
  url?: string
  description?: string
  platform?: string
  platformIcon?: string
  coverImageUrl?: string
  duration?: string
}

export interface LearningPathDiagnosisInput {
  gaps?: LearningPathGapInput[]
  learningPath?: LearningPathResourceInput[]
}

export interface NormalizedLearningGap {
  tagId?: number
  abilityName: string
  currentLevel: number
  requiredLevel: number
  weakEvidence: boolean
  reason: string
  gapLevel: number
  severity: 'danger' | 'warning' | 'info'
}

export interface LearningPathPlanItem extends LearningPathResourceInput {
  abilityName: string
  title: string
  learningMethod: string
  accessPath: string
  hasResource: boolean
  platform?: string
  platformIcon?: string
  coverImageUrl?: string
  duration?: string
}

export interface NormalizedLearningPathDiagnosis {
  gaps: NormalizedLearningGap[]
  learningByAbility: Record<string, LearningPathPlanItem[]>
}

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  COURSE: '在线课程',
  DOC: '文档资料',
  PRACTICE: '练习任务',
  PROJECT: '项目实战',
  BOOK: '书籍阅读',
  VIDEO: '视频课程',
}

const PLATFORM_LABELS: Record<string, string> = {
  MOOC: '慕课网',
  BILIBILI: 'B站',
  YOUTUBE: 'YouTube',
  GITHUB: 'GitHub',
  CSDN: 'CSDN',
  OTHER: '其他',
}

export function platformLabel(platform?: string): string {
  if (!platform) return ''
  return PLATFORM_LABELS[platform] || platform
}

export function platformTagType(platform?: string): string {
  const map: Record<string, string> = { MOOC: 'success', BILIBILI: '', YOUTUBE: 'danger', GITHUB: 'info', CSDN: 'warning' }
  return platform ? (map[platform] || '') : ''
}

export function normalizeLearningPathDiagnosis(diagnosis?: LearningPathDiagnosisInput | null): NormalizedLearningPathDiagnosis {
  const gaps = (diagnosis?.gaps || [])
    .map(normalizeGap)
    .filter((gap) => gap.abilityName)
    .sort((a, b) => {
      if (b.gapLevel !== a.gapLevel) return b.gapLevel - a.gapLevel
      if (Number(b.weakEvidence) !== Number(a.weakEvidence)) return Number(b.weakEvidence) - Number(a.weakEvidence)
      return a.abilityName.localeCompare(b.abilityName)
    })

  const learningByAbility: Record<string, LearningPathPlanItem[]> = {}
  for (const item of diagnosis?.learningPath || []) {
    const abilityName = normalizeAbilityName(item.abilityName)
    if (!abilityName) continue
    if (!learningByAbility[abilityName]) learningByAbility[abilityName] = []
    learningByAbility[abilityName].push(normalizeResource(item, abilityName))
  }

  for (const key of Object.keys(learningByAbility)) {
    learningByAbility[key].sort((a, b) => Number(a.difficultyLevel || 0) - Number(b.difficultyLevel || 0))
  }

  for (const gap of gaps) {
    if (!learningByAbility[gap.abilityName] || learningByAbility[gap.abilityName].length === 0) {
      learningByAbility[gap.abilityName] = [buildMissingResourceItem(gap.abilityName)]
    }
  }

  return { gaps, learningByAbility }
}

export function resourceTypeLabel(type?: string): string {
  if (!type) return '学习资源'
  return RESOURCE_TYPE_LABELS[type] || '学习资源'
}

export function buildLearningOutcomePayload(empId: number, gap: NormalizedLearningGap, resource?: LearningPathPlanItem) {
  const beforeLevel = gap.currentLevel > 0 ? gap.currentLevel : undefined
  const confirmedLevel = clampLevel(Math.max(gap.requiredLevel, gap.currentLevel, 1))
  return {
    empId,
    tagId: gap.tagId,
    abilityName: gap.abilityName,
    completedResourceId: resource?.resourceId,
    beforeLevel,
    confirmedLevel,
    confirmationSource: 'LEARNING_PATH',
    note: resource?.title ? `通过学习路径完成：${resource.title}` : `通过学习路径补齐：${gap.abilityName}`,
    // AI 追溯字段（可选）
    aiSuggestionId: undefined as number | undefined,
    ragChunkIds: undefined as string | undefined,
    aiSuggestionVersion: undefined as string | undefined,
  }
}

function normalizeGap(gap: LearningPathGapInput): NormalizedLearningGap {
  const abilityName = normalizeAbilityName(gap.abilityName || (gap.tagId != null ? `Ability#${gap.tagId}` : ''))
  const currentLevel = toLevel(gap.currentLevel)
  const requiredLevel = toLevel(gap.requiredLevel)
  const gapLevel = Math.max(requiredLevel - currentLevel, 0)
  const weakEvidence = gap.weakEvidence === true
  return {
    tagId: gap.tagId,
    abilityName,
    currentLevel,
    requiredLevel,
    weakEvidence,
    reason: gap.reason || (weakEvidence ? '能力证据不足，需要补充学习成果' : '低于岗位能力要求'),
    gapLevel,
    severity: gapLevel >= 2 ? 'danger' : weakEvidence || gapLevel > 0 ? 'warning' : 'info',
  }
}

function normalizeResource(item: LearningPathResourceInput, abilityName: string): LearningPathPlanItem {
  return {
    ...item,
    abilityName,
    title: item.title || `${abilityName} 学习资源`,
    learningMethod: resourceTypeLabel(item.resourceType),
    accessPath: item.url || '学习资源库',
    hasResource: Boolean(item.resourceId),
    platform: item.platform,
    platformIcon: item.platformIcon,
    coverImageUrl: item.coverImageUrl,
    duration: item.duration,
  }
}

function buildMissingResourceItem(abilityName: string): LearningPathPlanItem {
  return {
    abilityName,
    title: `补充 ${abilityName} 学习资源`,
    description: `当前资源库尚未维护 ${abilityName} 的学习内容，请先在学习资源管理中补录课程、项目或练习任务。`,
    learningMethod: '资源库补录',
    accessPath: '学习资源库待维护',
    hasResource: false,
  }
}

function normalizeAbilityName(name?: string): string {
  return (name || '').trim()
}

function toLevel(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) return clampLevel(value)
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? clampLevel(parsed) : 0
  }
  return 0
}

function clampLevel(level: number): number {
  if (level < 0) return 0
  if (level > 5) return 5
  return Math.round(level)
}
