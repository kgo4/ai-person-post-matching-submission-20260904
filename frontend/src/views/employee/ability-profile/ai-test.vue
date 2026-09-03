<script setup lang="ts">
/**
 * AI能力测试页面
 * AI生成测试题目，人员作答，AI自动批阅和出分出分析报告
 */
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  submitAiTestAnswers,
  getAiTestResult,
  generatePostAiTest,
  listAiTests,
  importAiTestResult,
  listEnabledPosts,
} from '@/api'
import type { AiTestRecord, AiTestQuestion, AiTestEvaluation, PostPost } from '@/api'
import { get } from '@/utils/request'
import { aiTestResultSummary, canImportAiTestResult, isAiTestEvidenceInsufficient, pollingPhaseForExistingTest, shouldSubmitThroughAssessmentWorkflow } from './ai-test-logic'
import { submitTest as submitAssessmentTest } from '@/api/assessment'

const router = useRouter()
const route = useRoute()
const empId = ref(Number(route.query.empId) || 0)
const workflowId = ref(Number(route.query.workflowId) || 0)
const routedTestId = Number(route.query.testId) || 0
const isAssessmentFlow = computed(() => route.query.fromAssessment === '1' && workflowId.value > 0)
const loading = ref(false)
// 岗位选择
const postList = ref<PostPost[]>([])
const selectedPostId = ref<number | undefined>(undefined)

// 测试列表
const testList = ref<AiTestRecord[]>([])

// 当前测试
const currentTest = ref<AiTestRecord | null>(null)
const testDialogVisible = ref(false)
const questions = ref<AiTestQuestion[]>([])
const answers = reactive<Record<string, any>>({})
const submitting = ref(false)

// 测试结果
const resultDialogVisible = ref(false)
const evaluation = ref<AiTestEvaluation | null>(null)
const importLoading = ref(false)

const statusMap: Record<number, { text: string; type: string }> = {
  [-1]: { text: '生成中', type: 'warning' },
  0: { text: '待作答', type: 'info' },
  1: { text: '评估中', type: 'warning' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已导入', type: 'success' },
}

let pollTimer: number | null = null

onMounted(async () => {
  loadPosts()
  if (empId.value) {
    await loadTestList()
  }
  if (routedTestId > 0) {
    await resumeRoutedTest(routedTestId)
  }
})

onUnmounted(() => {
  stopPolling()
})

async function loadPosts() {
  try { const res = await listEnabledPosts(); postList.value = res.data || [] } catch {}
}

async function handleGenerateTest() {
  if (!empId.value) { ElMessage.warning('缺少员工ID'); return }
  if (!selectedPostId.value) { ElMessage.warning('请选择岗位'); return }
  loading.value = true
  try {
    const res = await generatePostAiTest(empId.value, selectedPostId.value)
    ElMessage.success('测试已创建，正在生成题目...')
    loadTestList()
    startPolling(res.data.id, 'GENERATING')
  } catch { ElMessage.error('生成失败') }
  finally { loading.value = false }
}

async function loadTestList() {
  loading.value = true
  try {
    const res = await listAiTests(empId.value)
    testList.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function resumeRoutedTest(testId: number) {
  try {
    const res = await getAiTestResult(testId)
    const record = res.data
    if (!record) {
      ElMessage.error('未找到评估流程创建的测试')
      return
    }
    const index = testList.value.findIndex(test => test.id === testId)
    if (index >= 0) testList.value[index] = record
    else testList.value.unshift(record)

    const phase = pollingPhaseForExistingTest(record.status)
    if (phase) startPolling(testId, phase)
    else if (record.status === 0) openTest(record)
  } catch {
    ElMessage.error('加载评估流程测试失败')
  }
}

function startPolling(testId: number, phase: 'GENERATING' | 'EVALUATING') {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getAiTestResult(testId)
      const record = res.data
      if (!record) {
        stopPolling()
        return
      }

      // Refresh the list entry
      const idx = testList.value.findIndex(t => t.id === testId)
      if (idx !== -1) testList.value[idx] = record

      if (phase === 'GENERATING') {
        if (record.status === 0) {
          stopPolling()
          ElMessage.success('题目已生成，可以开始作答')
          openTest(record)
        } else if (record.status === -1 && record.errorMessage) {
          stopPolling()
          ElMessage.error('题目暂时无法生成，请稍后再试')
        }
      } else if (phase === 'EVALUATING') {
        if (record.status === 2) {
          stopPolling()
          ElMessage.success('AI批阅完成')
          showResult(record)
        } else if (record.status === 1 && record.errorMessage) {
          stopPolling()
          ElMessage.error('批阅暂时无法完成，请稍后再试')
        }
      }
    } catch {
      // continue polling
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function openTest(test: AiTestRecord) {
  currentTest.value = test

  // 解析题目
  try {
    const parsed = JSON.parse(test.questions)
    if (Array.isArray(parsed)) {
      questions.value = parsed
    } else if (parsed && Array.isArray(parsed.questions)) {
      questions.value = parsed.questions
    } else {
      questions.value = []
      ElMessage.error('题目格式异常')
      return
    }
  } catch {
    questions.value = []
    ElMessage.error('题目格式异常')
    return
  }

  // 初始化答案
  questions.value.forEach(q => {
    const t = q.type || ''
    if (t === 'choice_multiple') {
      answers[q.id] = []
    } else if (t === 'choice' || t === 'choice_single') {
      // 兼容旧数据：归一化 type=choice 视为单选
      answers[q.id] = ''
    } else {
      answers[q.id] = ''
    }
  })

  // 如果已有答案，回显（兼容旧数据：多选题目答案可能是字符串）
  if (test.answers) {
    try {
      const savedAnswers = JSON.parse(test.answers)
      for (const [qId, val] of Object.entries(savedAnswers)) {
        const q = questions.value.find(q => String(q.id) === String(qId))
        if (q && (q.type === 'choice_multiple') && typeof val === 'string') {
          // 旧数据兼容：单选字符串 → 转为单元素数组
          answers[Number(qId) || (qId as any)] = val ? [val] : []
        } else {
          answers[Number(qId) || (qId as any)] = val
        }
      }
    } catch {
      // ignore
    }
  }

  testDialogVisible.value = true
}

async function handleSubmitAnswers() {
  if (!currentTest.value) return

  // 检查是否所有题目都已作答
  const unanswered = questions.value.filter(q => {
    const ans = answers[q.id]
    if (q.type === 'choice_multiple') {
      return !Array.isArray(ans) || ans.length === 0
    }
    return !ans
  })
  if (unanswered.length > 0) {
    ElMessage.warning(`还有 ${unanswered.length} 道题目未作答`)
    return
  }

  submitting.value = true
  try {
    const res = shouldSubmitThroughAssessmentWorkflow({
      isAssessmentFlow: isAssessmentFlow.value,
      workflowId: workflowId.value,
    })
      ? await submitAssessmentTest(workflowId.value, currentTest.value.id, answers)
      : await submitAiTestAnswers(currentTest.value.id, answers)
    ElMessage.success('答案已提交，AI正在批阅...')
    testDialogVisible.value = false
    loadTestList()
    startPolling(currentTest.value.id, 'EVALUATING')
  } catch {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function showResult(test: AiTestRecord) {
  // console.log('[showResult] test data:', test)
  // console.log('[showResult] masteryLevel:', test.masteryLevel, 'score:', test.score)
  currentTest.value = { ...test }

  // 解析批阅结果
  if (test.aiEvaluation) {
    try {
      evaluation.value = JSON.parse(test.aiEvaluation)
      // console.log('[showResult] evaluation:', evaluation.value)
    } catch (e) {
      console.error('[showResult] aiEvaluation parse error:', e)
      evaluation.value = null
    }
  } else {
    evaluation.value = null
  }

  resultDialogVisible.value = true
}

async function handleImport() {
  if (!currentTest.value) return
  if (!canImportAiTestResult({ isAssessmentFlow: isAssessmentFlow.value, status: currentTest.value.status })) {
    ElMessage.info('评估流程中的测试结果已作为证据同步，等待聚合审核和等级确认')
    return
  }

  importLoading.value = true
  try {
    await importAiTestResult(currentTest.value.id)
    ElMessage.success('测试结果已导入能力档案')
    resultDialogVisible.value = false
    loadTestList()
  } catch {
    // handled by interceptor
  } finally {
    importLoading.value = false
  }
}

function getLevelText(level: number) {
  const map: Record<number, string> = {
    1: '入门',
    2: '熟悉',
    3: '掌握',
    4: '精通',
    5: '专家',
  }
  return map[level] || '未知'
}

// 整体证据不足时仍保留评分器返回的逐题真实得分；不据此伪造最终能力等级。
const partialScore = computed(() => {
  const result = evaluation.value as (AiTestEvaluation & { questionResults?: any[] }) | null
  if (!result) return null
  if (result.score != null) return Number(result.score)
  const details = result.details?.length ? result.details : result.questionResults
  if (!Array.isArray(details) || !details.length) return null
  const scored = details.filter(item => Number.isFinite(Number(item?.score)))
  if (!scored.length) return null
  const total = scored.reduce((sum, item) => sum + Number(item.score), 0)
  const max = scored.reduce((sum, item) => sum + Number(item.maxScore ?? item.score ?? 0), 0)
  return max > 0 ? Math.round(total * 100 / max) : null
})

const displayDetails = computed(() => {
  const result = evaluation.value as (AiTestEvaluation & { questionResults?: any[] }) | null
  if (!result) return []
  if (result.details?.length) return result.details
  return (result.questionResults || []).map((item: any, index: number) => ({
    questionId: item.questionId ?? item.questionIndex ?? index + 1,
    score: Number(item.score ?? 0),
    maxScore: Number(item.maxScore ?? item.score ?? 0),
    comment: item.comment || '',
  }))
})

const hasPartialScore = computed(() => partialScore.value != null)
const resultSummary = computed(() => hasPartialScore.value && isAiTestEvidenceInsufficient(evaluation.value)
  ? `已完成部分评分，当前阶段得分 ${partialScore.value} 分。部分题目证据不足，最终能力等级待补充证据后确认。`
  : aiTestResultSummary(evaluation.value))
const isResultUnscored = computed(() => !hasPartialScore.value && (
  isAiTestEvidenceInsufficient(evaluation.value)
    || evaluation.value?.status === 'UNAVAILABLE'
    || evaluation.value?.status === 'INVALID_OUTPUT'
))

function getDifficultyText(difficulty: string) {
  const map: Record<string, string> = {
    easy: '基础',
    medium: '中等',
    hard: '进阶',
  }
  return map[difficulty] || difficulty
}

function getQuestionCount() {
  if (!currentTest.value?.questions) return 0
  try {
    const parsed = JSON.parse(currentTest.value.questions)
    return Array.isArray(parsed) ? parsed.length : 0
  } catch {
    return 0
  }
}

function getQuestionTypeText(type: string) {
  const map: Record<string, string> = {
    choice_single: '单选题',
    choice_multiple: '多选题',
    choice: '选择题',
    text: '简答题',
    case: '案例分析',
  }
  return map[type] || type
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>AI能力测试</span>
          <el-button @click="isAssessmentFlow ? router.push({ path: '/employee/ability-profile/assessment', query: { empId, workflowId, fromAssessment: '1', refresh: String(Date.now()) } }) : router.back()">{{ isAssessmentFlow ? '返回评估流程' : '返回' }}</el-button>
        </div>
      </template>

      <div v-if="!empId" style="text-align: center; padding: 40px;">
        <el-empty description="请从人员能力画像页面选择人员后再操作" />
      </div>

      <template v-else>
        <!-- 生成测试 -->
        <el-form v-if="!isAssessmentFlow" inline style="margin-bottom: 20px;">
          <el-form-item label="选择岗位">
            <el-select v-model="selectedPostId" placeholder="选择目标岗位" filterable style="width:280px">
              <el-option v-for="p in postList" :key="p.id" :label="p.postName" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleGenerateTest" :loading="loading">
              生成测试题目
            </el-button>
          </el-form-item>
        </el-form>

        <el-divider />

        <!-- 测试列表 -->
        <h4>测试记录</h4>
        <el-table :data="testList" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="80px" />
          <el-table-column prop="testTitle" label="测试标题" min-width="200px" />
          <el-table-column prop="abilityTagName" label="测试能力" width="120px" />
          <el-table-column label="得分" width="100px">
            <template #default="{ row }">
              <span v-if="row.score != null" :style="{ fontWeight: 'bold', color: row.score >= 60 ? '#67c23a' : '#f56c6c' }">
                {{ row.score }}
              </span>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="掌握等级" width="100px">
            <template #default="{ row }">
              <span v-if="row.masteryLevel">{{ getLevelText(row.masteryLevel) }}</span>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100px">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.status]?.type as any" size="small">
                {{ statusMap[row.status]?.text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="180px">
            <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200px" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 0" type="primary" link @click="openTest(row)">
                开始作答
              </el-button>
              <el-button v-if="row.status >= 2" type="success" link @click="showResult(row)">
                查看结果
              </el-button>
              <el-button v-if="canImportAiTestResult({ isAssessmentFlow: isAssessmentFlow, status: row.status })" type="warning" link @click="currentTest = row; handleImport()">
                导入档案
              </el-button>
              <span v-else-if="isAssessmentFlow && row.status === 2" style="color: #909399; font-size: 12px;">证据已同步</span>
              <span v-if="row.status === -1 && row.errorMessage" style="color:#f56c6c;font-size:12px;">暂未生成</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 答题弹窗 -->
    <el-dialog
      v-model="testDialogVisible"
      :title="currentTest?.testTitle || 'AI能力测试'"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="questions.length > 0">
        <el-alert
          :title="`共 ${questions.length} 道题目，请认真作答`"
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        />

        <div v-for="(question, index) in questions" :key="question.id" class="question-item">
          <div class="question-header">
            <span class="question-index">{{ index + 1 }}</span>
            <el-tag size="small" type="info">{{ getQuestionTypeText(question.type) }}</el-tag>
            <el-tag size="small" :type="question.difficulty === 'easy' ? 'success' : question.difficulty === 'medium' ? 'warning' : 'danger'">
              {{ getDifficultyText(question.difficulty) }}
            </el-tag>
            <span class="question-score">{{ question.score }}分</span>
          </div>

          <div class="question-content">{{ question.question }}</div>

          <!-- 单选题 -->
          <div v-if="question.type === 'choice_single' && question.options" class="question-options">
            <el-radio-group v-model="answers[question.id]">
              <el-radio
                v-for="(option, optIndex) in question.options"
                :key="optIndex"
                :value="option"
                style="display: block; margin-bottom: 8px;"
              >
                {{ option }}
              </el-radio>
            </el-radio-group>
          </div>

          <!-- 多选题 -->
          <div v-if="question.type === 'choice_multiple' && question.options" class="question-options">
            <el-checkbox-group v-model="answers[question.id]">
              <el-checkbox
                v-for="(option, optIndex) in question.options"
                :key="optIndex"
                :label="option"
                style="display: block; margin-bottom: 8px;"
              >
                {{ option }}
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <!-- 兼容旧 choice 类型（未区分单选/多选）视为单选 -->
          <div v-if="question.type === 'choice' && question.options" class="question-options">
            <el-radio-group v-model="answers[question.id]">
              <el-radio
                v-for="(option, optIndex) in question.options"
                :key="optIndex"
                :value="option"
                style="display: block; margin-bottom: 8px;"
              >
                {{ option }}
              </el-radio>
            </el-radio-group>
          </div>

          <!-- 简答题/案例分析 -->
          <div v-else class="question-answer">
            <el-input
              v-model="answers[question.id]"
              type="textarea"
              :rows="4"
              placeholder="请输入您的答案"
            />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitAnswers">
          提交答案
        </el-button>
      </template>
    </el-dialog>

    <!-- 结果弹窗 -->
    <el-dialog
      v-model="resultDialogVisible"
      title="AI测试结果"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="currentTest && evaluation">
        <!-- 成绩概览 -->
        <el-row :gutter="20" style="margin-bottom: 20px;">
          <el-col :span="8">
            <el-card shadow="hover">
              <div style="text-align: center;">
                <div :style="{ fontSize: isResultUnscored ? '24px' : '36px', fontWeight: 'bold', color: isResultUnscored ? '#909399' : (currentTest.score != null && currentTest.score >= 60 ? '#67c23a' : '#f56c6c') }">
                  {{ isResultUnscored ? '未评分' : (currentTest.score ?? partialScore ?? '—') }}
                </div>
                <div style="color: #909399;">{{ isResultUnscored ? '评分状态' : (currentTest.score == null ? '阶段得分' : '得分') }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div style="text-align: center;">
                <div :style="{ fontSize: isResultUnscored ? '24px' : '36px', fontWeight: 'bold', color: isResultUnscored ? '#909399' : '#409eff' }">
                  {{ isResultUnscored ? '待补充证据' : getLevelText(currentTest.masteryLevel) }}
                </div>
                <div style="color: #909399;">掌握等级</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div style="text-align: center;">
                <div :style="{ fontSize: '36px', fontWeight: 'bold' }">
                  {{ getQuestionCount() }}
                </div>
                <div style="color: #909399;">题目数量</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 分析报告 -->
        <div v-if="resultSummary" class="result-section">
          <h4>分析报告</h4>
          <el-alert v-if="isResultUnscored" type="warning" :closable="false" :title="resultSummary" />
          <p v-else>{{ resultSummary }}</p>
        </div>

        <!-- 详细批阅 -->
        <div v-if="displayDetails.length > 0" class="result-section">
          <h4>详细批阅</h4>
          <el-table :data="displayDetails" border size="small">
            <el-table-column prop="questionId" label="题号" width="80px" />
            <el-table-column label="得分" width="100px">
              <template #default="{ row }">
                <span :style="{ color: row.score >= row.maxScore * 0.6 ? '#67c23a' : '#f56c6c' }">
                  {{ row.score }} / {{ row.maxScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="comment" label="评语" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <el-empty v-else description="暂无批阅结果" />

      <template #footer>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
        <el-button
          v-if="currentTest && canImportAiTestResult({ isAssessmentFlow: isAssessmentFlow, status: currentTest.status })"
          type="primary"
          :loading="importLoading"
          @click="handleImport"
        >
          导入到能力档案
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.question-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.question-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.question-score {
  margin-left: auto;
  color: #909399;
  font-size: 14px;
}

.question-content {
  font-size: 15px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 12px;
}

.question-options {
  padding-left: 36px;
}

.question-answer {
  padding-left: 36px;
}

.result-section {
  margin-bottom: 20px;
}

.result-section h4 {
  margin-bottom: 10px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 8px;
}

.result-section p {
  color: #606266;
  line-height: 1.8;
}
</style>
