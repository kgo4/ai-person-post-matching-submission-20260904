import assert from 'node:assert/strict'
import {
  buildLearningOutcomePayload,
  normalizeDiagnosis,
} from './closure-diagnosis.ts'

const diagnosis = normalizeDiagnosis({
  gaps: [
    {
      tagId: 7,
      abilityName: 'Java',
      currentLevel: 2,
      requiredLevel: 4,
      weakEvidence: true,
      reason: 'Below required level',
    },
  ],
  learningPath: [
    {
      abilityName: 'Java',
      resourceId: 88,
      title: 'Java project practice',
    },
  ],
})

assert.equal(diagnosis.gaps.length, 1)
assert.equal(diagnosis.gaps[0].severity, 'danger')
assert.equal(diagnosis.learningByAbility.Java.length, 1)

const payload = buildLearningOutcomePayload(
  { empId: 9 },
  diagnosis.gaps[0],
  diagnosis.learningByAbility.Java[0],
)

assert.deepEqual(payload, {
  empId: 9,
  tagId: 7,
  abilityName: 'Java',
  completedResourceId: 88,
  beforeLevel: 2,
  confirmedLevel: 4,
  confirmationSource: 'MANUAL_CONFIRM',
  note: 'Completed learning resource: Java project practice',
})
