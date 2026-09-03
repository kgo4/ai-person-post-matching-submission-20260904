import assert from 'node:assert/strict'
import {
  buildLearningOutcomePayload,
  normalizeLearningPathDiagnosis,
  resourceTypeLabel,
} from './learning-path.ts'

const normalized = normalizeLearningPathDiagnosis({
  matchingRecordId: 12,
  empId: 9,
  postId: 3,
  gaps: [
    {
      tagId: 1,
      abilityName: 'Java',
      currentLevel: 2,
      requiredLevel: 4,
      weakEvidence: false,
      reason: '低于岗位要求',
    },
    {
      tagId: 2,
      abilityName: 'Docker',
      currentLevel: 3,
      requiredLevel: 3,
      weakEvidence: true,
      reason: '证据不足',
    },
  ],
  learningPath: [
    {
      abilityName: 'Java',
      resourceId: 88,
      title: 'Java 项目实战',
      resourceType: 'PROJECT',
      difficultyLevel: 4,
      url: 'https://example.com/java',
      description: '补齐工程能力',
    },
  ],
})

assert.deepEqual(
  normalized.gaps.map((gap) => ({
    abilityName: gap.abilityName,
    gapLevel: gap.gapLevel,
    currentLevel: gap.currentLevel,
    requiredLevel: gap.requiredLevel,
    reason: gap.reason,
  })),
  [
    {
      abilityName: 'Java',
      gapLevel: 2,
      currentLevel: 2,
      requiredLevel: 4,
      reason: '低于岗位要求',
    },
    {
      abilityName: 'Docker',
      gapLevel: 0,
      currentLevel: 3,
      requiredLevel: 3,
      reason: '证据不足',
    },
  ],
)

assert.equal(normalized.learningByAbility.Java[0].learningMethod, '项目实战')
assert.equal(normalized.learningByAbility.Java[0].accessPath, 'https://example.com/java')
assert.equal(normalized.learningByAbility.Docker[0].title, '补充 Docker 学习资源')
assert.equal(normalized.learningByAbility.Docker[0].accessPath, '学习资源库待维护')

assert.equal(resourceTypeLabel('VIDEO'), '视频课程')
assert.equal(resourceTypeLabel('UNKNOWN'), '学习资源')

assert.deepEqual(
  buildLearningOutcomePayload(9, normalized.gaps[0], normalized.learningByAbility.Java[0]),
  {
    empId: 9,
    tagId: 1,
    abilityName: 'Java',
    completedResourceId: 88,
    beforeLevel: 2,
    confirmedLevel: 4,
    confirmationSource: 'LEARNING_PATH',
    note: '通过学习路径完成：Java 项目实战',
    aiSuggestionId: undefined,
    ragChunkIds: undefined,
    aiSuggestionVersion: undefined,
  },
)

console.log('learning path normalization tests passed')
