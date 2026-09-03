import { get, post, put, del } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

export interface GovernanceFilterRule {
  id?: number
  scope: 'POST_JD' | 'PERSON_ABILITY'
  ruleType: 'KEYWORD' | 'REGEX' | 'LENGTH' | 'SECTION_MISSING' | 'EXACT'
  ruleName: string
  patternValue: string
  weight: number
  blockEnabled: number
  enabled: number
  source?: string
  reviewStatus?: string
  sampleCount?: number
  aiRationale?: string
  description?: string
}

export function listGovernanceFilterRules(scope?: string, reviewStatus?: string) {
  return get<GovernanceFilterRule[]>('/governance/filter-rules', { scope, reviewStatus })
}

export function saveGovernanceFilterRule(rule: GovernanceFilterRule) {
  return rule.id ? put<GovernanceFilterRule>(`/governance/filter-rules/${rule.id}`, rule)
    : post<GovernanceFilterRule>('/governance/filter-rules', rule)
}

export function deleteGovernanceFilterRule(id: number): Promise<ApiResponse<void>> {
  return del<void>(`/governance/filter-rules/${id}`)
}

export function generateGovernanceFilterSuggestions(scope: string, samples: string[]) {
  return post<{ accepted: boolean; message: string }>('/governance/filter-rules/suggestions/generate', { scope, samples })
}

export function getGovernanceFilterSamples(scope: string, limit = 30) {
  return get<{ scope: string; count: number; samples: string[] }>('/governance/filter-rules/samples', { scope, limit })
}

export function reviewGovernanceFilterSuggestion(id: number, approve: boolean) {
  return post<void>(`/governance/filter-rules/${id}/review`, null, { params: { approve } })
}
