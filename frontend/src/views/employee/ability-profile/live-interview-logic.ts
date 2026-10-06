export interface AudioSendState {
  isAiSpeaking: boolean
  isInterviewActive: boolean
  isConnected: boolean
}

export interface AnswerWindowAcknowledgementState {
  playbackGeneration: number
  activePlaybackGeneration: number
  expectedSessionId: number | null
  currentSessionId: number | null
  isInterviewStarted: boolean
  isInterviewEnding: boolean
  isAnswerAnalyzing: boolean
}

export interface ResumeStateInput {
  questionOrder: number
  questionText: string
  followUpId?: number | null
  followUpQuestionText?: string | null
  durationSeconds: number
  remainingSeconds: number
}

export interface InterviewSessionSummary {
  id: number
  status: number
  updatedTime?: string
}

export function applyResumeState(state: ResumeStateInput) {
  const isFollowUp = state.followUpId != null && !!state.followUpQuestionText
  return {
    order: state.questionOrder,
    text: isFollowUp ? state.followUpQuestionText! : state.questionText,
    durationSeconds: state.durationSeconds,
    remainingSeconds: state.remainingSeconds,
    isFollowUp,
  }
}

export function shouldSendInterviewAudio(state: AudioSendState): boolean {
  return state.isInterviewActive && state.isConnected && !state.isAiSpeaking
}

export function shouldKeepAsrAlive(state: AudioSendState): boolean {
  return shouldSendInterviewAudio(state)
}

/**
 * Browser TTS cancellation still resolves its pending promise on some engines.
 * Only the currently active playback may acknowledge that an answer window is ready.
 */
export function shouldAcknowledgeAnswerWindow(state: AnswerWindowAcknowledgementState): boolean {
  return state.playbackGeneration === state.activePlaybackGeneration
    && state.expectedSessionId != null
    && state.expectedSessionId === state.currentSessionId
    && state.isInterviewStarted
    && !state.isInterviewEnding
    && !state.isAnswerAnalyzing
}

export function shouldFinishThroughAssessment(context: {
  isAssessmentFlow: boolean
  workflowId: number
}): boolean {
  return context.isAssessmentFlow && context.workflowId > 0
}

export function resampleToPcm16(
  input: Float32Array,
  inputSampleRate: number,
  targetSampleRate = 16_000,
): Int16Array {
  if (input.length === 0 || inputSampleRate <= 0 || targetSampleRate <= 0) {
    return new Int16Array()
  }

  const sampleRatio = inputSampleRate / targetSampleRate
  const output = new Int16Array(Math.max(1, Math.floor(input.length / sampleRatio)))

  for (let index = 0; index < output.length; index++) {
    const sourceIndex = index * sampleRatio
    const leftIndex = Math.floor(sourceIndex)
    const rightIndex = Math.min(leftIndex + 1, input.length - 1)
    const fraction = sourceIndex - leftIndex
    const sample = input[leftIndex] + (input[rightIndex] - input[leftIndex]) * fraction
    const normalized = Math.max(-1, Math.min(1, sample))
    output[index] = normalized < 0 ? normalized * 0x8000 : normalized * 0x7FFF
  }

  return output
}

export function prepareInterviewPcm16(
  input: Float32Array,
  inputSampleRate: number,
  mute: boolean,
): Int16Array {
  const pcm = resampleToPcm16(input, inputSampleRate)
  if (mute) {
    pcm.fill(0)
  }
  return pcm
}

export function findLatestViewableInterviewSession<T extends InterviewSessionSummary>(sessions: T[]): T | null {
  return sessions
    .filter((session) => session.status === 5 || session.status === 6)
    .sort((left, right) => (right.updatedTime || '').localeCompare(left.updatedTime || ''))[0] || null
}

/** 面试结束后的落点：给 HR 看明细，给员工交回评估流程。 */
export type InterviewResultEntry = 'DETAIL' | 'HANDOFF'

/**
 * 面试结果明细的可见性（口径唯一来源）。
 *
 * <p>员工侧**一律不看面试结果明细** —— 面试综合分、逐题得分、能力核验、总结报告
 * 都不给。原因是这些是 AI 面试一结束就产出的**中间态结论**，而员工可见的评估结论
 * 必须等 HR 把全部能力项人工审核完毕（后端 `EmployeeResultVisibility` 的同一道闸门）。
 *
 * <p>原先本页在「面试结束 → 自动分析完成」后**自动**渲染整份面试结果，
 * 等于绕过了那道闸门：员工在 HR 还没审核时就看到了一份带分数的评估报告，
 * 且那个分数是**面试环节**的综合分（`EmpVideoInterviewSession.overallScore`），
 * 并非人员评估的综合分（各能力最终等级均值 × 20）。
 *
 * <p>员工要看结果请走「人员能力评估流程」页：那里的报告正文与综合评分都由后端闸门保护。
 *
 * @param hasManagementScope 是否持管理范围权限（`EMPLOYEE:READ`，即 HR）
 */
export function resolveInterviewResultEntry(hasManagementScope: boolean): InterviewResultEntry {
  return hasManagementScope ? 'DETAIL' : 'HANDOFF'
}
