import assert from 'node:assert/strict'
import { extractGapAbilities, parseMatchingReportPayload } from './gap-diagnosis.ts'

const report = parseMatchingReportPayload({
  abilityDetails: [
    { tagName: 'Spring Boot', requiredLevel: 4, actualLevel: 2.5, weakEvidence: false },
    { abilityName: 'Neo4j', requiredLevel: 3, actualLevel: 3, weakEvidence: true },
    { tagName: 'Spring Boot', requiredLevel: 4, actualLevel: 1, weakEvidence: true },
    { tagId: 99, requiredLevel: 2, actualLevel: 3, weakEvidence: false },
  ],
})

assert.deepEqual(
  extractGapAbilities(report),
  [
    { name: 'Spring Boot', requiredLevel: 4, actualLevel: 2.5, weakEvidence: false },
    { name: 'Neo4j', requiredLevel: 3, actualLevel: 3, weakEvidence: true },
  ],
)

assert.deepEqual(
  parseMatchingReportPayload(JSON.stringify({ abilityDetails: [{ tagName: 'RAG', requiredLevel: 3, actualLevel: 1 }] })).abilityDetails,
  [{ tagName: 'RAG', requiredLevel: 3, actualLevel: 1 }],
)

assert.deepEqual(parseMatchingReportPayload('not json').abilityDetails, [])

console.log('matching result gap diagnosis tests passed')
