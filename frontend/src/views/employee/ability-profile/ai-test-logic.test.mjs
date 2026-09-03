import assert from 'node:assert/strict'
import { canImportAiTestResult, pollingPhaseForExistingTest, shouldSubmitThroughAssessmentWorkflow } from './ai-test-logic.ts'

assert.equal(
  canImportAiTestResult({ isAssessmentFlow: true, status: 2 }),
  false,
  'an assessment-flow test result must remain evidence and cannot be imported directly',
)

assert.equal(
  canImportAiTestResult({ isAssessmentFlow: false, status: 2 }),
  true,
  'a completed independent test may retain the legacy import action',
)

assert.equal(
  canImportAiTestResult({ isAssessmentFlow: false, status: 1 }),
  false,
  'an unevaluated test cannot be imported',
)

assert.equal(
  shouldSubmitThroughAssessmentWorkflow({ isAssessmentFlow: true, workflowId: 9 }),
  true,
  'an assessment-flow test submission must advance the assessment workflow',
)

assert.equal(
  shouldSubmitThroughAssessmentWorkflow({ isAssessmentFlow: true, workflowId: 0 }),
  false,
  'a missing workflow id must never produce an invalid assessment request',
)

assert.equal(
  shouldSubmitThroughAssessmentWorkflow({ isAssessmentFlow: false, workflowId: 9 }),
  false,
  'an independent test must retain the legacy submission endpoint',
)

assert.equal(
  pollingPhaseForExistingTest(-1),
  'GENERATING',
  'a routed test with generating status must resume question polling',
)

assert.equal(
  pollingPhaseForExistingTest(1),
  'EVALUATING',
  'a routed test with evaluating status must resume result polling',
)

assert.equal(
  pollingPhaseForExistingTest(0),
  undefined,
  'a ready test must not keep polling',
)

console.log('AI test flow logic tests passed')
