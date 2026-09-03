import type { HardCondition, MatchingExecuteDTO, MatchingPair } from '@/api'

export type MatchMode = 'SINGLE' | 'PERSON_TO_POSTS' | 'POST_TO_PEOPLE'

export type MatchingMode = 'SINGLE_EVAL' | 'EMP_TO_POST' | 'POST_TO_EMP'

export interface ExecuteFormState {
  mode: MatchingMode
  selectedEmployeeId?: number
  selectedPostId?: number
  selectedCandidateIds: number[]
  enableAiMatching: boolean
  forceAiMatching: boolean
  aiTopN: number
  aiThreshold: number
  hardConditions: HardCondition[]
}

export interface PostScopeState {
  mode: MatchingMode
  selectedPostId?: number
  selectedCandidateIds: number[]
}

function dedupePairs(pairs: MatchingPair[]): MatchingPair[] {
  const seen = new Set<string>()
  return pairs.filter((pair) => {
    const key = `${pair.empId}:${pair.postId}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function buildPairs(state: ExecuteFormState): MatchingPair[] {
  if (state.mode === 'SINGLE_EVAL') {
    if (!state.selectedEmployeeId || !state.selectedPostId) {
      return []
    }
    return [{ empId: state.selectedEmployeeId, postId: state.selectedPostId }]
  }

  if (state.mode === 'EMP_TO_POST') {
    if (!state.selectedEmployeeId) {
      return []
    }
    return dedupePairs(
      state.selectedCandidateIds.map((postId) => ({
        empId: state.selectedEmployeeId!,
        postId,
      })),
    )
  }

  if (!state.selectedPostId) {
    return []
  }

  return dedupePairs(
    state.selectedCandidateIds.map((empId) => ({
      empId,
      postId: state.selectedPostId!,
    })),
  )
}

export function canCustomizeHardConditions(state: PostScopeState): boolean {
  return getPrimaryPostId(state) != null
}

export function getPrimaryPostId(state: PostScopeState): number | null {
  if (state.mode === 'SINGLE_EVAL' || state.mode === 'POST_TO_EMP') {
    return state.selectedPostId ?? null
  }
  return null
}

export function buildExecutePayload(state: ExecuteFormState): MatchingExecuteDTO {
  const pairs = buildPairs(state)
  const payload: MatchingExecuteDTO = {
    mode: state.mode,
    pairs,
    matchStrategy: 'threeLevel',
    enableAiMatching: state.enableAiMatching,
    forceAiMatching: state.forceAiMatching,
    aiTopN: state.aiTopN,
    aiThreshold: state.aiThreshold,
  }

  if (canCustomizeHardConditions(state)) {
    payload.hardConditions = state.hardConditions
  }

  return payload
}
