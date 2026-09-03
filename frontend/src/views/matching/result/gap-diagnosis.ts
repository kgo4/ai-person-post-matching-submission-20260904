export interface MatchingReportAbilityDetail {
  tagId?: number
  tagName?: string
  abilityName?: string
  requiredLevel?: number
  actualLevel?: number | string
  weakEvidence?: boolean
}

export interface MatchingReportPayload {
  abilityDetails: MatchingReportAbilityDetail[]
  /** 多维度评分（后端可能返回） */
  dimensionScores?: DimensionScore[]
  /** 综合建议 */
  overallSuggestions?: string[]
}

/** 多维度匹配评分 */
export interface DimensionScore {
  dimension: string
  label: string
  score: number
  maxScore: number
  details?: string[]
}

export interface GapAbility {
  name: string
  requiredLevel?: number
  actualLevel?: number
  weakEvidence: boolean
}

/** 改进计划阶段 */
export interface ImprovementPhase {
  phase: number
  title: string
  timeframe: string
  description: string
  targetAbilities: string[]
  resources: ImprovementResource[]
}

/** 改进资源 */
export interface ImprovementResource {
  title: string
  type: string
  difficultyLevel?: number
  url?: string
}

export function parseMatchingReportPayload(payload: unknown): MatchingReportPayload {
  if (typeof payload === 'string') {
    try {
      return parseMatchingReportPayload(JSON.parse(payload))
    } catch {
      return { abilityDetails: [] }
    }
  }

  if (!payload || typeof payload !== 'object') {
    return { abilityDetails: [] }
  }

  const abilityDetails = (payload as { abilityDetails?: unknown }).abilityDetails
  return {
    abilityDetails: Array.isArray(abilityDetails) ? abilityDetails as MatchingReportAbilityDetail[] : [],
  }
}

export function extractGapAbilities(report: MatchingReportPayload): GapAbility[] {
  const seen = new Set<string>()
  const gaps: GapAbility[] = []

  for (const item of report.abilityDetails || []) {
    const name = normalizeAbilityName(item)
    if (!name || seen.has(name)) continue

    const requiredLevel = toNumber(item.requiredLevel)
    const actualLevel = toNumber(item.actualLevel)
    const weakEvidence = item.weakEvidence === true
    const belowRequirement = requiredLevel != null && actualLevel != null && actualLevel < requiredLevel

    if (!belowRequirement && !weakEvidence) continue

    seen.add(name)
    gaps.push({
      name,
      requiredLevel,
      actualLevel,
      weakEvidence,
    })
  }

  return gaps
}

export function buildGapKnowledgeQuery(gaps: GapAbility[]): string {
  return gaps.map((item) => item.name).filter(Boolean).join(' ')
}

function normalizeAbilityName(item: MatchingReportAbilityDetail): string {
  const explicitName = item.tagName || item.abilityName
  if (explicitName && explicitName.trim()) return explicitName.trim()
  return item.tagId != null ? `Ability#${item.tagId}` : ''
}

function toNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

/**
 * 默认多维度评分（当后端仍未下发 dimensionScores 时的确定性占位值）。
 * 该函数仅用于向后兼容的展示兜底——业务规则应以后端 AI 分析结果为准。
 * 所有分值为 matchScore 的确定性单调映射，不含随机抖动，确保每次渲染结果一致。
 */
export function getDefaultDimensionScores(matchScore: number): DimensionScore[] {
  const normalize = (v: number) => Math.min(100, Math.max(0, Math.round(v)))
  return [
    {
      dimension: 'hard_condition',
      label: '硬性条件',
      score: normalize(matchScore >= 60 ? 85 : matchScore * 1.2),
      maxScore: 100,
      details: ['学历', '工作年限', '专业背景'],
    },
    {
      dimension: 'skill_match',
      label: '技能匹配',
      score: normalize(matchScore * 0.95),
      maxScore: 100,
      details: ['核心技术栈', '工具链', '框架掌握'],
    },
    {
      dimension: 'project_experience',
      label: '项目经验',
      score: normalize(matchScore * 0.85),
      maxScore: 100,
      details: ['项目规模', '角色定位', '成果产出'],
    },
    {
      dimension: 'education',
      label: '学历背景',
      score: normalize(matchScore >= 50 ? 75 : 60),
      maxScore: 100,
      details: ['学历层次', '专业相关度', '院校水平'],
    },
    {
      dimension: 'comprehensive',
      label: '综合素质',
      score: normalize(matchScore * 0.80),
      maxScore: 100,
      details: ['沟通能力', '团队协作', '学习能力'],
    },
  ]
}

/** 基于能力缺口生成改进计划 */
export function buildImprovementPlan(gaps: GapAbility[], learningPath: { abilityName: string; title: string; resourceType?: string; difficultyLevel?: number; url?: string; description?: string }[]): ImprovementPhase[] {
  if (gaps.length === 0) return []

  const phases: ImprovementPhase[] = []
  const sorted = [...gaps].sort((a, b) => {
    const aGap = (a.requiredLevel || 0) - (a.actualLevel || 0)
    const bGap = (b.requiredLevel || 0) - (b.actualLevel || 0)
    return aGap - bGap
  })

  // Phase 1: 基础补齐 (small gaps)
  const smallGaps = sorted.filter(g => ((g.requiredLevel || 0) - (g.actualLevel || 0)) <= 1)
  if (smallGaps.length > 0) {
    phases.push({
      phase: 1,
      title: '基础能力补齐',
      timeframe: '1-2周',
      description: '针对与岗位要求差距较小的能力项，通过快速学习和练习达到基本要求。',
      targetAbilities: smallGaps.map(g => g.name),
      resources: matchResources(smallGaps, learningPath),
    })
  }

  // Phase 2: 核心能力提升 (medium gaps)
  const mediumGaps = sorted.filter(g => {
    const gap = (g.requiredLevel || 0) - (g.actualLevel || 0)
    return gap >= 2 && gap <= 3
  })
  if (mediumGaps.length > 0) {
    phases.push({
      phase: 2,
      title: '核心能力提升',
      timeframe: '2-4周',
      description: '针对差距在2-3级的关键能力，进行系统性学习和项目实践。',
      targetAbilities: mediumGaps.map(g => g.name),
      resources: matchResources(mediumGaps, learningPath),
    })
  }

  // Phase 3: 深度学习 (large gaps)
  const largeGaps = sorted.filter(g => ((g.requiredLevel || 0) - (g.actualLevel || 0)) >= 4)
  if (largeGaps.length > 0) {
    phases.push({
      phase: 3,
      title: '深度学习与实践',
      timeframe: '4-8周',
      description: '针对差距较大的能力项，制定长期学习计划，结合项目实战和导师指导。',
      targetAbilities: largeGaps.map(g => g.name),
      resources: matchResources(largeGaps, learningPath),
    })
  }

  return phases
}

function matchResources(gaps: GapAbility[], learningPath: { abilityName: string; title: string; resourceType?: string; difficultyLevel?: number; url?: string; description?: string }[]): ImprovementResource[] {
  const resources: ImprovementResource[] = []
  for (const gap of gaps) {
    const match = learningPath.find(lp => lp.abilityName === gap.name || lp.title.includes(gap.name))
    if (match) {
      resources.push({
        title: match.title,
        type: match.resourceType || '文档',
        difficultyLevel: match.difficultyLevel,
        url: match.url,
      })
    }
  }
  return resources
}
