import assert from 'node:assert/strict'
import {
  buildExecutePayload,
  canCustomizeHardConditions,
  getPrimaryPostId,
} from './logic.ts'

assert.deepEqual(
  buildExecutePayload({
    mode: 'SINGLE_EVAL',
    selectedEmployeeId: 11,
    selectedPostId: 21,
    selectedCandidateIds: [],
    enableAiMatching: false,
    forceAiMatching: false,
    aiTopN: 5,
    aiThreshold: 60,
    hardConditions: [],
  }),
  {
    mode: 'SINGLE_EVAL',
    pairs: [{ empId: 11, postId: 21 }],
    matchStrategy: 'threeLevel',
    hardConditions: [],
    enableAiMatching: false,
    forceAiMatching: false,
    aiTopN: 5,
    aiThreshold: 60,
  },
)

assert.deepEqual(
  buildExecutePayload({
    mode: 'EMP_TO_POST',
    selectedEmployeeId: 11,
    selectedPostId: undefined,
    selectedCandidateIds: [21, 22],
    enableAiMatching: true,
    forceAiMatching: false,
    aiTopN: 8,
    aiThreshold: 72,
    hardConditions: [{ field: 'education', operator: 'eq', value: 'Bachelor' }],
  }),
  {
    mode: 'EMP_TO_POST',
    pairs: [
      { empId: 11, postId: 21 },
      { empId: 11, postId: 22 },
    ],
    matchStrategy: 'threeLevel',
    enableAiMatching: true,
    forceAiMatching: false,
    aiTopN: 8,
    aiThreshold: 72,
  },
)

assert.deepEqual(
  buildExecutePayload({
    mode: 'POST_TO_EMP',
    selectedEmployeeId: undefined,
    selectedPostId: 21,
    selectedCandidateIds: [101, 102],
    enableAiMatching: true,
    forceAiMatching: false,
    aiTopN: 6,
    aiThreshold: 70,
    hardConditions: [{ field: 'education', operator: 'eq', value: 'Bachelor' }],
  }),
  {
    mode: 'POST_TO_EMP',
    pairs: [
      { empId: 101, postId: 21 },
      { empId: 102, postId: 21 },
    ],
    matchStrategy: 'threeLevel',
    hardConditions: [{ field: 'education', operator: 'eq', value: 'Bachelor' }],
    enableAiMatching: true,
    forceAiMatching: false,
    aiTopN: 6,
    aiThreshold: 70,
  },
)

assert.equal(
  canCustomizeHardConditions({
    mode: 'SINGLE_EVAL',
    selectedPostId: 21,
    selectedCandidateIds: [],
  }),
  true,
)

assert.equal(
  canCustomizeHardConditions({
    mode: 'POST_TO_EMP',
    selectedPostId: 21,
    selectedCandidateIds: [101, 102],
  }),
  true,
)

assert.equal(
  canCustomizeHardConditions({
    mode: 'EMP_TO_POST',
    selectedPostId: undefined,
    selectedCandidateIds: [21, 22],
  }),
  false,
)

assert.equal(
  getPrimaryPostId({
    mode: 'SINGLE_EVAL',
    selectedPostId: 21,
    selectedCandidateIds: [],
  }),
  21,
)

assert.equal(
  getPrimaryPostId({
    mode: 'POST_TO_EMP',
    selectedPostId: 21,
    selectedCandidateIds: [101, 102],
  }),
  21,
)

assert.equal(
  getPrimaryPostId({
    mode: 'EMP_TO_POST',
    selectedPostId: undefined,
    selectedCandidateIds: [21, 22],
  }),
  null,
)

console.log('matching execute logic tests passed')
