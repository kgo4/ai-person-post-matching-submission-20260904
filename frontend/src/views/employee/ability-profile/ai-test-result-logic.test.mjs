import assert from 'node:assert/strict'
import { aiTestResultSummary, isAiTestEvidenceInsufficient } from './ai-test-logic.ts'

const insufficient = {
  status: 'INSUFFICIENT_EVIDENCE',
  analysisReport: 'All answers are either empty, incomplete, or nonsensical.',
}

assert.equal(isAiTestEvidenceInsufficient(insufficient), true)
assert.match(aiTestResultSummary(insufficient), /未生成分数或能力等级/)
assert.doesNotMatch(aiTestResultSummary(insufficient), /All answers are either/)
assert.equal(aiTestResultSummary({ status: 'UNAVAILABLE' }), 'AI 批阅服务暂时不可用，本次结果未评分，请稍后重试。')
assert.equal(aiTestResultSummary({ status: 'VALID', analysisReport: '回答证据充分' }), '回答证据充分')

console.log('ai test result presentation logic tests passed')
