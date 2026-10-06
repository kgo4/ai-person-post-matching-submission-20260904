/**
 * Synchronous AI endpoints can legitimately take longer than ordinary CRUD
 * requests. Keep this list narrow so a stalled non-AI request still fails fast.
 */
const AI_ENDPOINTS = [
  /^\/employee\/ability\/(?:resume-parse\/(?:upload|\d+\/(?:reparse|import))|ai-test|video-interview\/\d+\/(?:generate-questions|analyze)|pms\/analyze)(?:\/|$)/,
  /^\/learning\/(?:ai-suggestions|path\/generate|path-enhanced\/generate-by-(?:knowledge-graph|mastery)|assessment\/generate)(?:\/|$)/,
  /^\/matching\/record\/\d+\/ai-report$/,
  /^\/post\/(?:jd-import\/analyze|model-generation(?:\/|$)|model-import\/import\/template-a|excel-import\/analyze|emerging\/(?:re)?analyze|evolution\/tasks\/\d+\/analyze)(?:\/|$)/,
]

export const AI_REQUEST_TIMEOUT_MS = 180_000

export function resolveRequestTimeout(url: string, explicitTimeout?: number): number | undefined {
  if (explicitTimeout !== undefined) return explicitTimeout

  const path = url.split('?')[0]
  return AI_ENDPOINTS.some((endpoint) => endpoint.test(path))
    ? AI_REQUEST_TIMEOUT_MS
    : undefined
}
