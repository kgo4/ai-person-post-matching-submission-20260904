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
