<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getLearningPathPlan,
  submitProjectTask,
  generateAssessments,
  getAssessmentsByPlan,
  answerAssessment,
  confirmLearningAbilityImprovement,
  updateStepStatus
} from '@/api'
import type {
  LearningPathPlan,
  LearningPathStep,
  LearningProjectTask,
  LearningProjectSubmitDTO,
  LearningAssessmentItem
} from '@/api'

import LearningPathHeader from '@/components/learning/LearningPathHeader.vue'
import LearningGapPanel from '@/components/learning/LearningGapPanel.vue'
import LearningTimeline from '@/components/learning/LearningTimeline.vue'
import LearningProgressPanel from '@/components/learning/LearningProgressPanel.vue'
import LearningResourceList from '@/components/learning/LearningResourceList.vue'
import LearningVerificationPanel from '@/components/learning/LearningVerificationPanel.vue'
import { getStatusMeta, getDifficultyMeta } from '@/components/learning/types'

const route = useRoute()
const router = useRouter()
const planId = Number(route.params.id)

const loading = ref(false)
const plan = ref<LearningPathPlan | null>(null)
const assessments = ref<LearningAssessmentItem[]>([])
const assessmentsLoading = ref(false)
const activeStepId = ref<number | null>(null)
const updatingStepIds = ref(new Set<number>())
const assessmentAnswers = ref<Record<number, string>>({})
const answeringAssessmentIds = ref(new Set<number>())
const confirmingImprovement = ref(false)

const verifyRef = ref<InstanceType<typeof LearningVerificationPanel> | null>(null)

onMounted(async () => {
  await loadPlan()
  await loadAssessments()
})

async function loadPlan() {
  loading.value = true
  try {
    const res = await getLearningPathPlan(planId)
    plan.value = res.data
    // Auto-select first non-completed step
    if (res.data?.steps?.length) {
      const firstActive = res.data.steps.find(s => s.status !== 'COMPLETED')
      activeStepId.value = firstActive?.id || res.data.steps[0].id
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载学习路径失败')
  } finally {
    loading.value = false
  }
}

async function loadAssessments() {
  assessmentsLoading.value = true
  try {
    const res = await getAssessmentsByPlan(planId)
    assessments.value = res.data || []
  } catch {
    assessments.value = []
  } finally {
    assessmentsLoading.value = false
  }
}

async function handleGenerateAssessments() {
  assessmentsLoading.value = true
  try {
    const res = await generateAssessments({ planId, includeProjectReview: true })
    assessments.value = res.data || []
    ElMessage.success('评估题目已生成')
  } catch (error: any) {
    ElMessage.error(error.message || '生成评估题目失败')
  } finally {
    assessmentsLoading.value = false
  }
}

async function handleStepStatusChange(step: LearningPathStep, status: string) {
  // A status change is idempotent from the user's perspective. Guard the
  // button while the request is in flight so a double click cannot create
  // duplicate progress log entries.
  if (updatingStepIds.value.has(step.id)) return
  updatingStepIds.value.add(step.id)
  try {
    await updateStepStatus(step.id, status)
    ElMessage.success('状态已更新')
    await loadPlan()
  } catch (error: any) {
    // The write may have reached the server even when the browser loses the
    // response (common with a transient TLS/proxy interruption). Confirm the
    // persisted state before reporting a failure to the user.
    try {
      const latest = await getLearningPathPlan(planId)
      const persisted = latest.data?.steps?.find(item => item.id === step.id)
      if (persisted?.status === status) {
        plan.value = latest.data
        ElMessage.success('状态已更新')
        return
      }
    } catch {
      // Preserve the original error below when the confirmation request also
      // cannot reach the server.
    }

    const networkFailure = !error?.response && (error?.message === 'Network Error' || error?.code === 'ERR_NETWORK')
    ElMessage.error(networkFailure
      ? '网络连接中断，状态尚未确认，请刷新后重试'
      : (error?.message || '更新状态失败'))
  } finally {
    updatingStepIds.value.delete(step.id)
  }
}

async function handleAnswerAssessment(item: LearningAssessmentItem) {
  const answerText = assessmentAnswers.value[item.id] || item.answerText || ''
  if (!answerText.trim()) {
    ElMessage.warning('请先填写答案')
    return
  }
  answeringAssessmentIds.value.add(item.id)
  try {
    const res = await answerAssessment(item.id, { answerText })
    assessments.value = assessments.value.map(current => current.id === item.id ? res.data : current)
    assessmentAnswers.value[item.id] = res.data.answerText || answerText
    ElMessage.success(res.data.assessmentStatus === 'PASSED' ? '测评已通过' : '答案已评分，请根据反馈补充后重试')
  } catch (error: any) {
    ElMessage.error(error.message || '测评评分失败')
  } finally {
    answeringAssessmentIds.value.delete(item.id)
  }
}

async function handleConfirmImprovement() {
  if (!activeStep.value) return
  confirmingImprovement.value = true
  try {
    await confirmLearningAbilityImprovement(planId, activeStep.value.id)
    ElMessage.success('能力提升已确认并写入人员正式能力')
    await Promise.all([loadPlan(), loadAssessments()])
  } catch (error: any) {
    ElMessage.error(error.message || '能力提升确认失败')
  } finally {
    confirmingImprovement.value = false
  }
}

async function handleSubmitTask(taskId: number, data: LearningProjectSubmitDTO) {
  try {
    await submitProjectTask(taskId, data)
    ElMessage.success('提交成功')
    await loadPlan()
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  }
}

function handleSubmitProjectTask(task: LearningProjectTask) {
  verifyRef.value?.openSubmitDialog(task)
}

const activeStep = computed(() => {
  if (!plan.value?.steps || !activeStepId.value) return null
  return plan.value.steps.find(s => s.id === activeStepId.value) || null
})

const activeStepHasPassedAssessment = computed(() => Boolean(activeStep.value?.id
  && assessments.value.some(item => item.stepId === activeStep.value?.id && item.assessmentStatus === 'PASSED')))
</script>

<template>
  <div class="lp-workbench">
    <!-- Header -->
    <LearningPathHeader
      :plan-title="plan?.planTitle"
      :emp-id="plan?.empId"
      :emp-name="plan?.empName"
      :post-id="plan?.postId"
      :post-name="plan?.postName"
      :matching-record-id="plan?.matchingRecordId"
      :plan-status="plan?.planStatus"
      :current-score="plan?.currentScore"
      :target-score="plan?.targetScore"
      :completed-step-count="plan?.completedStepCount"
      :total-step-count="plan?.totalStepCount"
      :pending-submission-count="plan?.pendingSubmissionCount"
      :loading="loading"
      @back="router.back()"
      @refresh="loadPlan()"
      @generate-assessment="handleGenerateAssessments()"
    />

    <!-- AI Summary (if any) -->
    <div v-if="plan?.aiSummary" class="lp-workbench__summary">
      <strong>AI 学习建议：</strong>{{ plan.aiSummary }}
    </div>

    <!-- Three-column layout -->
    <div class="lp-workbench__body" v-loading="loading">
      <!-- Left: Gap Panel -->
      <LearningGapPanel
        v-if="plan"
        :steps="plan.steps"
        :current-score="plan.currentScore"
        :target-score="plan.targetScore"
        :emp-name="plan.empName"
        :post-name="plan.postName"
        @select-step="activeStepId = $event"
      />

      <!-- Center: Timeline -->
      <LearningTimeline
        v-if="plan"
        :steps="plan.steps"
        :active-step-id="activeStepId"
        @select-step="activeStepId = $event"
        @start-learning="(step) => handleStepStatusChange(step, 'IN_PROGRESS')"
        @mark-completed="(step) => handleStepStatusChange(step, 'COMPLETED')"
        @submit-task="handleSubmitProjectTask"
      />

      <!-- Right: Progress + Resources + Verification -->
      <LearningProgressPanel v-if="plan" :steps="plan.steps">
        <template #active-step>
          <div v-if="activeStep" class="lp-workbench__active-detail">
            <div class="lp-workbench__active-title">{{ activeStep.stepTitle }}</div>
            <div class="lp-workbench__active-meta">
              <span :style="{ color: getStatusMeta(activeStep.status).color }">{{ getStatusMeta(activeStep.status).label }}</span>
              <span v-if="activeStep.estimatedHours"> · {{ activeStep.estimatedHours }}h</span>
            </div>

            <LearningResourceList :step="activeStep" />

            <div class="lp-workbench__verify">
              <LearningVerificationPanel
                ref="verifyRef"
                :pending-count="plan?.pendingSubmissionCount"
                :can-confirm="activeStepHasPassedAssessment && !confirmingImprovement"
                @submit-task="handleSubmitTask"
                @generate-assessment="handleGenerateAssessments()"
                @confirm-improvement="handleConfirmImprovement()"
              />
            </div>
          </div>
          <div v-else class="lp-workbench__no-active">选择一个步骤查看详情</div>
        </template>
      </LearningProgressPanel>
    </div>

    <!-- Assessments (shown below if any) -->
    <section v-if="assessments.length" class="lp-workbench__assessments">
      <div class="lp-workbench__assessments-header">
        <h3>评估题目</h3>
        <span class="lp-workbench__assessments-count">{{ assessments.length }} 题</span>
      </div>
      <div class="lp-workbench__assessments-list">
        <div v-for="item in assessments" :key="item.id" class="assessment-item">
          <div class="assessment-item__header">
            <span
              class="assessment-item__type"
              :style="{ color: item.questionType === 'INTERVIEW' ? '#2563eb' : '#d97706', background: item.questionType === 'INTERVIEW' ? '#dbeafe' : '#fef3c7' }"
            >
              {{ item.questionType === 'INTERVIEW' ? '面试题' : '项目评审' }}
            </span>
            <span
              class="assessment-item__difficulty"
              :style="{ color: getDifficultyMeta(item.difficultyLevel).color }"
            >
              {{ getDifficultyMeta(item.difficultyLevel).label }}
            </span>
            <span v-if="item.source === 'AI_LEARNING'" class="assessment-item__source">AI 生成</span>
          </div>
          <div class="assessment-item__question">{{ item.questionText }}</div>
          <el-input
            v-model="assessmentAnswers[item.id]"
            class="assessment-item__input"
            type="textarea"
            :rows="4"
            :disabled="answeringAssessmentIds.has(item.id)"
            placeholder="结合实际项目或学习成果作答，写清场景、实现方案和验证结果"
          />
          <div class="assessment-item__actions">
            <el-button type="primary" size="small" :loading="answeringAssessmentIds.has(item.id)" @click="handleAnswerAssessment(item)">
              提交并评分
            </el-button>
            <span v-if="item.assessmentStatus" :class="['assessment-item__status', item.assessmentStatus]">
              {{ item.assessmentStatus === 'PASSED' ? `已通过 ${item.score ?? 0} 分` : item.assessmentStatus === 'NOT_PASSED' ? `待完善 ${item.score ?? 0} 分` : '待作答' }}
            </span>
          </div>
          <div v-if="item.scoringFeedback" class="assessment-item__feedback">{{ item.scoringFeedback }}</div>
          <div v-if="item.referenceAnswer && item.assessmentStatus" class="assessment-item__answer">
            <div class="assessment-item__answer-label">参考答案</div>
            <div class="assessment-item__answer-text">{{ item.referenceAnswer }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.lp-workbench {
  padding: 16px;
  background: #f6f8fb;
  min-height: 100%;
}

.lp-workbench__summary {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.lp-workbench__summary strong {
  color: #111827;
}

.lp-workbench__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* Active step detail */
.lp-workbench__active-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.lp-workbench__active-title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.lp-workbench__active-meta {
  font-size: 12px;
  color: #9ca3af;
}

.lp-workbench__verify {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.lp-workbench__no-active {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

/* Assessments */
.lp-workbench__assessments {
  margin-top: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 20px 24px;
}

.lp-workbench__assessments-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.lp-workbench__assessments-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.lp-workbench__assessments-count {
  font-size: 13px;
  color: #9ca3af;
}

.lp-workbench__assessments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.assessment-item {
  padding: 14px 16px;
  border: 1px solid #f3f4f6;
  border-radius: 8px;
}

.assessment-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.assessment-item__type {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.assessment-item__difficulty {
  font-size: 12px;
  font-weight: 600;
}

.assessment-item__source {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
  font-weight: 600;
}

.assessment-item__question {
  font-size: 14px;
  color: #111827;
  line-height: 1.6;
}

.assessment-item__answer {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f3f4f6;
}

.assessment-item__input { margin-top: 12px; }
.assessment-item__actions { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.assessment-item__status { font-size: 12px; color: #6b7280; }
.assessment-item__status.PASSED { color: #15803d; }
.assessment-item__status.NOT_PASSED { color: #b45309; }
.assessment-item__feedback { margin-top: 8px; font-size: 12px; line-height: 1.5; color: #6b7280; }

.assessment-item__answer-label {
  font-size: 12px;
  font-weight: 700;
  color: #9ca3af;
  margin-bottom: 4px;
}

.assessment-item__answer-text {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

/* Responsive */
@media (max-width: 1200px) {
  .lp-workbench__body {
    flex-wrap: wrap;
  }
}

@media (max-width: 960px) {
  .lp-workbench__body {
    flex-direction: column;
  }
}
</style>
