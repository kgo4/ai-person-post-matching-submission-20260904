import type { AiLearningSuggestionResponse } from '@/api/ai-learning'

export function selectCachedAiLearningSuggestion(
  responses: AiLearningSuggestionResponse[],
): AiLearningSuggestionResponse | null {
  return [...responses].reverse().find((response) => response.suggestions.length > 0) || null
}
