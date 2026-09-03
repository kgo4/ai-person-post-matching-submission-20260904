import assert from 'node:assert/strict'
import {
  applyResumeState,
  findLatestViewableInterviewSession,
  prepareInterviewPcm16,
  resampleToPcm16,
  shouldAcknowledgeAnswerWindow,
  shouldFinishThroughAssessment,
  shouldKeepAsrAlive,
  shouldSendInterviewAudio,
} from './live-interview-logic.ts'

assert.equal(
  shouldSendInterviewAudio({
    isAiSpeaking: true,
    isInterviewActive: true,
    isConnected: true,
  }),
  false,
  'does not send microphone audio while AI is reading a question',
)

assert.equal(
  shouldSendInterviewAudio({
    isAiSpeaking: false,
    isInterviewActive: true,
    isConnected: true,
  }),
  true,
  'sends microphone audio while candidate is answering',
)

assert.equal(
  shouldSendInterviewAudio({
    isAiSpeaking: false,
    isInterviewActive: false,
    isConnected: true,
  }),
  false,
  'does not send audio outside the active interview phase',
)

assert.equal(
  shouldKeepAsrAlive({
    isAiSpeaking: true,
    isInterviewActive: true,
    isConnected: true,
  }),
  false,
  'does not send audio while the question is being read',
)

assert.equal(
  shouldAcknowledgeAnswerWindow({
    playbackGeneration: 4,
    activePlaybackGeneration: 4,
    expectedSessionId: 96,
    currentSessionId: 96,
    isInterviewStarted: true,
    isInterviewEnding: false,
    isAnswerAnalyzing: false,
  }),
  true,
  'acknowledges the current question after its TTS playback completes',
)

assert.equal(
  shouldAcknowledgeAnswerWindow({
    playbackGeneration: 4,
    activePlaybackGeneration: 5,
    expectedSessionId: 96,
    currentSessionId: 96,
    isInterviewStarted: true,
    isInterviewEnding: true,
    isAnswerAnalyzing: false,
  }),
  false,
  'does not acknowledge a question after its TTS playback was cancelled by interview finish',
)

assert.equal(
  shouldFinishThroughAssessment({ isAssessmentFlow: true, workflowId: 9 }),
  true,
  'a workflow-bound interview must finish through the assessment API',
)

assert.equal(
  shouldFinishThroughAssessment({ isAssessmentFlow: true, workflowId: 0 }),
  false,
  'a missing workflow id must not create an invalid assessment finish request',
)

assert.deepEqual(
  Array.from(prepareInterviewPcm16(new Float32Array([0.5, -0.5]), 16_000, true)),
  [0, 0],
  'sends silence rather than the AI question audio while it is being read',
)

assert.deepEqual(
  applyResumeState({
    conversationState: 'ANSWERING_PRESET',
    questionOrder: 2,
    questionText: 'Describe a production incident.',
    followUpId: null,
    followUpOrder: null,
    followUpQuestionText: null,
    questionDeadlineEpochMillis: 1_784_002_837_000,
    durationSeconds: 60,
    remainingSeconds: 37,
    sessionVersion: 4,
  }),
  {
    order: 2,
    text: 'Describe a production incident.',
    durationSeconds: 60,
    remainingSeconds: 37,
    isFollowUp: false,
  },
  'restores a preset question without replaying or resetting its original duration',
)

assert.deepEqual(
  Array.from(resampleToPcm16(new Float32Array([0, 0.25, 0.5, 0.75, 1, 1]), 48_000)),
  [0, 24575],
  'downsamples browser audio to the 16 kHz PCM format required by the ASR service',
)

assert.deepEqual(
  findLatestViewableInterviewSession([
    { id: 4, status: 5, updatedTime: '2026-07-24 09:00:00' },
    { id: 9, status: 3, updatedTime: '2026-07-25 10:00:00' },
    { id: 7, status: 6, updatedTime: '2026-07-25 14:00:00' },
  ]),
  { id: 7, status: 6, updatedTime: '2026-07-25 14:00:00' },
  'selects the latest completed or analyzed interview for result viewing',
)

console.log('live interview logic tests passed')
