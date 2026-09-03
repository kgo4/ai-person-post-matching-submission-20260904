import assert from 'node:assert/strict'
import { shouldPollByRunStatus, isWaitingUser, shouldPollWorkflowStatus } from './assessment-logic.ts'

for (const status of ['RESUME_PARSING', 'TEST_GENERATING', 'TEST_EVALUATING', 'INTERVIEW_PREPARING', 'INTERVIEW_ANALYZING', 'AGGREGATE_HARNESS_RUNNING', 'LEVEL_CONFIRMING']) {
  assert.equal(shouldPollWorkflowStatus(status), true, `${status} must refresh its visible progress`)
}

for (const status of ['RESUME_REQUIRED', 'RESUME_EVIDENCE_READY', 'TEST_IN_PROGRESS', 'INTERVIEW_IN_PROGRESS', 'COMPLETED', 'FAILED', 'RECOVERY_REQUIRED', 'CANCELLED']) {
  assert.equal(shouldPollWorkflowStatus(status), false, `${status} is terminal or action-driven`)
}

// WAITING_USER 不轮询高频，只在返回页面/手动刷新/提交动作后刷新
assert.equal(shouldPollByRunStatus('PENDING'), true)
assert.equal(shouldPollByRunStatus('RUNNING'), true)
assert.equal(shouldPollByRunStatus('WAITING_USER'), false, 'WAITING_USER must not poll at high frequency')
assert.equal(shouldPollByRunStatus('SUCCEEDED'), false)
assert.equal(isWaitingUser('WAITING_USER'), true)
assert.equal(isWaitingUser('RUNNING'), false)

console.log('assessment workflow polling logic tests passed')
