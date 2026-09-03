<script setup lang="ts">
/**
 * AI实时视频面试页面
 * 支持：浏览器TTS语音播题、实时视频作答、实时语音转录、自动/手动切题、倒计时
 * 两路分析：视觉（视频帧）+ 语音内容（转录文本）
 */
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera, Timer, ArrowRight, Close } from '@element-plus/icons-vue'
import { useInterviewWebSocket } from '@/composables/useInterviewWebSocket'
import { useMediaCapture } from '@/composables/useMediaCapture'
import { useVideoFrameCapture } from '@/composables/useVideoFrameCapture'
import {
  applyResumeState,
  findLatestViewableInterviewSession,
  prepareInterviewPcm16,
  shouldAcknowledgeAnswerWindow,
  shouldFinishThroughAssessment,
  shouldKeepAsrAlive,
} from './live-interview-logic'
import { finishInterview as finishAssessmentInterview } from '@/api/assessment'
import {
  createVideoInterviewSession,
  generateVideoInterviewQuestions,
  getVideoInterviewDetail,
  issueVideoInterviewWsTicket,
  startInterviewApi,
  finishInterviewApi,
  analyzeVideoInterview,
  uploadVideoInterviewFrame,
  listEnabledPosts,
  listVideoInterviewSessions,
} from '@/api'
import type { VideoInterviewSession, VideoInterviewDetailVO, PostPost } from '@/api/types'

const router = useRouter()
const route = useRoute()
const empId = ref(Number(route.query.empId) || 0)
const postId = ref(Number(route.query.postId) || 0)
const workflowId = ref(Number(route.query.workflowId) || 0)
const isAssessmentFlow = computed(() => route.query.fromAssessment === '1' && workflowId.value > 0)
const loading = ref(false)

// 岗位列表
const postList = ref<PostPost[]>([])
const selectedPostId = ref<number | undefined>(postId.value > 0 ? postId.value : undefined)
// 评估流程锁定的岗位名（岗位与测试一致，不可再选）
const lockedPostName = computed(() => {
  const p = postList.value.find(post => post.id === selectedPostId.value)
  return p?.postName || ''
})
const isPreparing = ref(false)

// 视频元素引用
const videoPreview = ref<HTMLVideoElement | null>(null)

// 会话状态
const session = ref<VideoInterviewSession | null>(null)
const detail = ref<VideoInterviewDetailVO | null>(null)
const sessionId = ref<number | null>(null)
const latestViewableSession = ref<VideoInterviewSession | null>(null)

// 面试状态
const interviewPhase = ref<'setup' | 'interviewing' | 'finished' | 'analyzing' | 'completed'>('setup')

// 当前题目
const currentQuestionOrder = ref(0)
const currentFollowUpId = ref<number | null>(null)
const currentQuestionText = ref('')
const currentDurationSeconds = ref(60)
const remainingSeconds = ref(0)
const isAiSpeaking = ref(false)
const isAnswerAnalyzing = ref(false)
const isExistingSession = ref(false)
const interviewStartTime = ref<number | null>(null)
let analysisPollTimer: number | null = null
let speechPlaybackGeneration = 0
let isInterviewEnding = false

// 倒计时显示
const countdownDisplay = computed(() => {
  const mins = Math.floor(remainingSeconds.value / 60)
  const secs = remainingSeconds.value % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
})

// 进度百分比
const progressPercent = computed(() => {
  if (currentDurationSeconds.value === 0) return 0
  return ((currentDurationSeconds.value - remainingSeconds.value) / currentDurationSeconds.value) * 100
})

// WebSocket
const { isConnected, isReconnecting, isInterviewStarted, connect, disconnect, startInterview, resumeInterview, waitForInterviewStarted, nextQuestion, answerWindowReady, finishInterview, sendAudio } = useInterviewWebSocket()

// 媒体采集
const { isCapturing, hasPermission, startCapture, stopCapture, startAudioCapture, attachVideoElement } = useMediaCapture({
  video: true,
  audio: true,
})
const frameCapture = useVideoFrameCapture({ intervalMs: 15000, width: 480, quality: 0.72 })

// ==================== 浏览器TTS语音播报 ====================

function speakText(text: string): Promise<void> {
  return new Promise((resolve) => {
    if (!('speechSynthesis' in window)) {
      console.warn('浏览器不支持语音合成')
      resolve()
      return
    }

    // 取消之前的语音
    window.speechSynthesis.cancel()

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 0.95
    utterance.pitch = 1.0
    utterance.volume = 1.0

    // 尝试选择中文语音
    const voices = window.speechSynthesis.getVoices()
    const zhVoice = voices.find(v => v.lang.startsWith('zh'))
    if (zhVoice) {
      utterance.voice = zhVoice
    }

    utterance.onstart = () => {
      isAiSpeaking.value = true
    }
    utterance.onend = () => {
      isAiSpeaking.value = false
      resolve()
    }
    utterance.onerror = () => {
      isAiSpeaking.value = false
      resolve()
    }

    isAiSpeaking.value = true
    window.speechSynthesis.speak(utterance)
  })
}

function stopSpeaking() {
  speechPlaybackGeneration += 1
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel()
  }
  isAiSpeaking.value = false
}

async function speakThenOpenAnswerWindow(text: string, onReady: () => void) {
  const playbackGeneration = ++speechPlaybackGeneration
  const expectedSessionId = sessionId.value
  await speakText(text)

  if (!shouldAcknowledgeAnswerWindow({
    playbackGeneration,
    activePlaybackGeneration: speechPlaybackGeneration,
    expectedSessionId,
    currentSessionId: sessionId.value,
    isInterviewStarted: isInterviewStarted.value,
    isInterviewEnding,
    isAnswerAnalyzing: isAnswerAnalyzing.value,
  })) {
    return
  }
  onReady()
}

// ==================== 生命周期 ====================

onMounted(async () => {
  // 预加载语音列表
  if ('speechSynthesis' in window) {
    window.speechSynthesis.getVoices()
  }

  const existingSessionId = Number(route.query.sessionId)
  if (existingSessionId > 0) {
    isExistingSession.value = true
    sessionId.value = existingSessionId
    await loadDetail()
    syncPhaseFromDetail()
    if (detail.value?.status === 1 || detail.value?.status === 2) {
      await handleStartInterview()
    }
    return
  }
  if (empId.value) {
    await Promise.all([loadPostList(), loadLatestViewableSession()])
  }
})

async function loadPostList() {
  try {
    const res = await listEnabledPosts()
    postList.value = res.data || []
  } catch (e) {
    console.error('加载岗位列表失败:', e)
  }
}

async function loadLatestViewableSession() {
  if (!empId.value) return
  try {
    const res = await listVideoInterviewSessions(empId.value)
    latestViewableSession.value = findLatestViewableInterviewSession(res.data || [])
  } catch (error) {
    console.error('加载面试结果入口失败:', error)
  }
}

async function viewInterviewResult(id: number) {
  isExistingSession.value = true
  sessionId.value = id
  await loadDetail()
}

onUnmounted(() => {
  stopAnalysisPolling()
  frameCapture.stop()
  stopSpeaking()
  stopCapture()
  disconnect()
})

// ==================== 会话管理 ====================

async function initSession() {
  loading.value = true
  try {
    isInterviewEnding = false
    // 先检查是否已有未完成的会话
    const hasPost = selectedPostId.value && selectedPostId.value > 0
    const existingSessions = await listVideoInterviewSessions(empId.value)
    const sessions = existingSessions.data || []

    // 查找状态为 0（已创建）或 1（问题已生成）的会话
    const pendingSession = sessions.find(s =>
      (s.status === 0 || s.status === 1) &&
      (hasPost ? s.postId === selectedPostId.value : !s.postId)
    )

    if (pendingSession) {
      // 使用已有的会话
      ElMessage.info('使用已有的面试会话')
      sessionId.value = pendingSession.id
      await loadDetail()
      await handleStartInterview()
      return
    }

    // 没有找到已有会话，创建新会话
    const sessionName = hasPost
      ? `岗位面试-${new Date().toLocaleString('zh-CN')}`
      : `通用面试-${new Date().toLocaleString('zh-CN')}`

    const res = await createVideoInterviewSession({
      empId: empId.value,
      postId: hasPost ? selectedPostId.value : undefined,
      sessionName,
      interviewMode: hasPost ? 'POST_BASED' : 'GENERAL',
    })
    session.value = res.data
    sessionId.value = res.data.id

    await generateVideoInterviewQuestions(res.data.id, {
      mode: hasPost ? 'POST_BASED' : 'GENERAL',
      includeGeneralQuestions: true,
    })

    await loadDetail()
    await handleStartInterview()
  } catch (e) {
    ElMessage.error('初始化面试失败')
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  if (!sessionId.value) return
  try {
    const res = await getVideoInterviewDetail(sessionId.value)
    detail.value = res.data
    syncPhaseFromDetail()
  } catch (e) {
    console.error('加载详情失败:', e)
  }
}

function syncPhaseFromDetail() {
  if (!detail.value) return
  if (detail.value.status === 1 || detail.value.status === 2) {
    interviewPhase.value = 'interviewing'
  } else if (detail.value.status === 3) {
    interviewPhase.value = 'finished'
    stopAnalysisPolling()
  } else if (detail.value.status === 4) {
    interviewPhase.value = 'analyzing'
  } else if (detail.value.status === 5 || detail.value.status === 6) {
    interviewPhase.value = 'completed'
    stopAnalysisPolling()
  } else if (detail.value.status === 7) {
    interviewPhase.value = 'finished'
    stopAnalysisPolling()
  }
}

function startAnalysisPolling() {
  if (analysisPollTimer != null) return
  const startTime = Date.now()
  analysisPollTimer = window.setInterval(() => {
    loadDetail()
    if (Date.now() - startTime > 120_000) {
      stopAnalysisPolling()
      if (interviewPhase.value === 'analyzing') {
        interviewPhase.value = 'completed'
        ElMessage.warning('分析超时，请稍后在面试记录中查看结果')
      }
    }
  }, 3000)
}

function stopAnalysisPolling() {
  if (analysisPollTimer == null) return
  clearInterval(analysisPollTimer)
  analysisPollTimer = null
}

// ==================== 面试流程 ====================

async function handleStartInterview() {
  if (!sessionId.value) return

  try {
    // 兜底：会话已分析完成（status=5）或已结束（status=3）时不再发起 START，
    // 直接展示结果/完成页，避免后端拒绝"面试当前状态不允许开始"导致误报"开始面试失败"。
    if (detail.value?.status === 5) {
      interviewPhase.value = 'completed'
      return
    }
    if (detail.value?.status === 3) {
      interviewPhase.value = 'finished'
      return
    }
    const shouldResume = isExistingSession.value && detail.value?.status === 2
    const success = await startCapture()
    if (!success) {
      ElMessage.error('无法访问摄像头或麦克风，请检查权限设置')
      return
    }

    const ticketRes = await issueVideoInterviewWsTicket(sessionId.value)
    await connect(sessionId.value.toString(), ticketRes.data.ticket, {
      onConnected: () => {
        // console.log('WebSocket已连接')
      },
      onQuestion: async (order, text, duration) => {
        isAnswerAnalyzing.value = false
        currentQuestionOrder.value = order
        currentFollowUpId.value = null
        currentQuestionText.value = text
        currentDurationSeconds.value = duration
        remainingSeconds.value = duration

        // 语音播报题目
        const totalQuestions = detail.value?.questions?.length || 0
        const speakContent = `第${order}题，共${totalQuestions}题。${text}`
        await speakThenOpenAnswerWindow(speakContent, () => answerWindowReady(order))
      },
      onFollowUpQuestion: async (followUpOrder, text, duration, followUpId) => {
        isAnswerAnalyzing.value = false
        currentFollowUpId.value = followUpId ?? null
        currentQuestionText.value = text
        currentDurationSeconds.value = duration
        remainingSeconds.value = duration
        if (followUpId != null) {
          await speakThenOpenAnswerWindow(`追问${followUpOrder}。${text}`, () => answerWindowReady(undefined, followUpId))
        }
      },
      onCountdown: (seconds) => {
        remainingSeconds.value = seconds
      },
      onResumeState: async (state) => {
        isAnswerAnalyzing.value = state.conversationState === 'EVALUATING_ANSWER'
        const restored = applyResumeState(state)
        currentQuestionOrder.value = restored.order
        currentFollowUpId.value = restored.isFollowUp ? state.followUpId ?? null : null
        currentQuestionText.value = restored.text
        currentDurationSeconds.value = restored.durationSeconds
        remainingSeconds.value = restored.remainingSeconds
        if (isAnswerAnalyzing.value) {
          stopSpeaking()
        } else if (state.questionDeadlineEpochMillis === 0) {
          if (restored.isFollowUp && state.followUpId != null) {
            await speakThenOpenAnswerWindow(
              `追问${state.followUpOrder ?? 1}。${restored.text}`,
              () => answerWindowReady(undefined, state.followUpId!),
            )
          } else {
            await speakThenOpenAnswerWindow(`第${restored.order}题。${restored.text}`, () => answerWindowReady(restored.order))
          }
        } else {
          isAiSpeaking.value = false
        }
      },
      onTranscript: (_text, _isFinal) => {
        // 转录数据由后端保存
      },
      onNextQuestion: () => {
        stopSpeaking()
      },
      onAnswerAnalysisStarted: () => {
        isAnswerAnalyzing.value = true
        stopSpeaking()
      },
      onInterviewFinished: () => {
        isAnswerAnalyzing.value = false
        isInterviewEnding = true
        interviewPhase.value = 'finished'
        frameCapture.stop()
        stopSpeaking()
        ElMessage.success('面试已完成')
        setTimeout(() => {
          stopCapture()
        }, 2000)
      },
      onError: (msg) => {
        isAnswerAnalyzing.value = false
        ElMessage.error(msg)
      },
      onReconnecting: () => {
        ElMessage.warning('连接暂时中断，正在恢复面试会话')
      },
      onReconnectFailed: () => {
        ElMessage.error('面试连接恢复失败，请刷新页面后继续面试')
      },
    }, {
      fetchFreshTicket: async () => {
        if (!sessionId.value) throw new Error('面试会话不存在')
        const refreshed = await issueVideoInterviewWsTicket(sessionId.value)
        return refreshed.data.ticket
      },
    })

    const audioCaptureStarted = startAudioCapture((audioData, sampleRate) => {
      if (!shouldKeepAsrAlive({
        isAiSpeaking: isAiSpeaking.value,
        isInterviewActive: interviewPhase.value === 'interviewing',
        isConnected: isConnected.value,
      })) {
        return
      }
      const pcmData = prepareInterviewPcm16(audioData, sampleRate, false)
      sendAudio(pcmData.buffer as ArrayBuffer)
    })
    if (!audioCaptureStarted) {
      disconnect()
      stopCapture()
      ElMessage.error('麦克风音频采集启动失败，请检查浏览器麦克风权限')
      return
    }

    if (shouldResume) {
      resumeInterview()
    } else {
      startInterview()
      await waitForInterviewStarted()
    }

    interviewPhase.value = 'interviewing'
    await nextTick()
    attachVideoElement(videoPreview.value)

    if (!shouldResume) {
      await startInterviewApi(sessionId.value)
    }

    interviewStartTime.value = Date.now()
    startFrameCapture()

    ElMessage.success('面试已开始，请听题并作答')
  } catch (e) {
    ElMessage.error('开始面试失败')
  }
}

function handleNextQuestion() {
  if (!sessionId.value || isAiSpeaking.value || isAnswerAnalyzing.value) return
  isAnswerAnalyzing.value = true
  stopSpeaking()
  nextQuestion()
}

async function handleFinishInterview() {
  if (!sessionId.value) return

  try {
    isAnswerAnalyzing.value = false
    await ElMessageBox.confirm('确定要结束面试吗？', '确认', {
      type: 'warning',
    })

    isInterviewEnding = true
    stopSpeaking()

    // WebSocket 路径会先关闭 ASR 并冲刷最后一段转写，再将会话置为 FINISHED。
    // 不能先调用 REST finish，否则后台分析可能在语音证据入库前启动。
    if (isConnected.value) {
      finishInterview()
      await waitForInterviewFinished()
    } else {
      await finishInterviewApi(sessionId.value)
      await loadDetail()
    }
    if (shouldFinishThroughAssessment({
      isAssessmentFlow: isAssessmentFlow.value,
      workflowId: workflowId.value,
    })) {
      await finishAssessmentInterview(workflowId.value, sessionId.value)
    }
    frameCapture.stop()
    stopCapture()

    interviewPhase.value = 'finished'
    ElMessage.success('面试已结束，正在准备分析...')

    await new Promise(resolve => setTimeout(resolve, 2000))
    await handleAnalyze()
  } catch (e) {
    // 用户取消
  }
}

async function waitForInterviewFinished() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, 300))
    await loadDetail()
    if (detail.value?.status != null && detail.value.status >= 3) {
      return
    }
  }
  throw new Error('等待语音转写写入超时')
}

async function handleAnalyze() {
  if (!sessionId.value) return

  interviewPhase.value = 'analyzing'
  try {
    await analyzeVideoInterview(sessionId.value)
    await loadDetail()
    startAnalysisPolling()
    ElMessage.success('已开始分析，可离开页面，稍后回来查看结果')
  } catch (e) {
    await loadDetail()
    if (detail.value?.status === 5 || detail.value?.questions?.some(q => q.answerScore != null)) {
      interviewPhase.value = 'completed'
      ElMessage.warning('分析请求超时，但已获取到分析结果')
    } else {
      ElMessage.error('分析失败')
      interviewPhase.value = 'finished'
    }
  }
}

function startFrameCapture() {
  if (!sessionId.value || !videoPreview.value) return

  frameCapture.start(videoPreview.value, (imageDataUrl) => {
    if (!sessionId.value || currentQuestionOrder.value <= 0 || !interviewStartTime.value) return

    uploadVideoInterviewFrame(sessionId.value, {
      questionOrder: currentQuestionOrder.value,
      followUpId: currentFollowUpId.value ?? undefined,
      captureSecond: Math.floor((Date.now() - interviewStartTime.value) / 1000),
      imageDataUrl,
    }).catch((e) => {
      console.warn('上传视频抽帧失败:', e)
    })
  })
}

function getLevelText(level: number) {
  const map: Record<number, string> = { 1: '入门', 2: '熟悉', 3: '掌握', 4: '精通', 5: '专家' }
  return map[level] || '未知'
}
</script>

<template>
  <div class="live-interview-page">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>{{ isExistingSession ? '继续 AI 实时面试' : '发起 AI 实时面试' }}</span>
          <el-button @click="isAssessmentFlow ? router.push({ path: '/employee/ability-profile/assessment', query: { empId, workflowId, fromAssessment: '1', refresh: String(Date.now()) } }) : router.back()">{{ isAssessmentFlow ? '返回评估流程' : '返回' }}</el-button>
        </div>
      </template>

      <!-- 准备阶段：选择岗位 -->
      <div v-if="interviewPhase === 'setup'" class="setup-phase">
        <div v-if="!isPreparing" class="post-selector">
          <h3>{{ isExistingSession ? '正在恢复面试' : '选择面试岗位' }}</h3>
          <el-alert title="请佩戴耳机" type="warning" show-icon :closable="false" style="margin-bottom: 16px;">
            面试过程中AI会语音播报题目，请务必佩戴耳机以避免回声干扰语音识别。
          </el-alert>
          <p style="color: #909399; margin-bottom: 20px;">{{ isExistingSession ? '系统正在载入已生成的题目和会话状态。' : '选择目标岗位后，系统将根据岗位能力模型自动生成面试题目并语音播报' }}</p>

          <el-form label-width="100px">
            <el-form-item label="目标岗位">
              <template v-if="isAssessmentFlow">
                <span style="line-height: 32px; color: #303133;">{{ lockedPostName || '已锁定岗位' }}</span>
                <div style="color: #909399; font-size: 12px;">评估流程岗位与 AI 测试一致，不可更改</div>
              </template>
              <span v-else style="line-height: 32px; color: #909399;">面试只能从评估流程发起</span>
            </el-form-item>
          </el-form>

          <div style="text-align: center; margin-top: 30px;">
            <el-button v-if="isAssessmentFlow" type="primary" size="large" :loading="loading" @click="initSession">
              {{ selectedPostId ? '开始岗位面试' : '开始通用面试' }}
            </el-button>
            <el-button v-if="latestViewableSession" size="large" @click="viewInterviewResult(latestViewableSession.id)">
              查看最近面试结果
            </el-button>
          </div>
        </div>
        <div v-else v-loading="loading" style="min-height: 300px;">
          <el-empty description="正在准备面试..." />
        </div>
      </div>

      <!-- 面试中 -->
      <div v-else-if="interviewPhase === 'interviewing'" class="interviewing-phase">
        <div class="interview-studio">
          <!-- 顶栏 -->
          <div class="studio-topbar">
            <div class="studio-brand">
              <span class="studio-brand__dot" :class="{ 'is-live': isCapturing }"></span>
              <span class="studio-brand__text">{{ isCapturing ? '面试进行中' : '设备待机' }}</span>
            </div>
            <div class="studio-topbar__right">
              <el-tag v-if="isAiSpeaking" size="small" class="ai-speaking-tag">
                <el-icon class="is-loading"><Microphone /></el-icon> AI 读题中
              </el-tag>
              <el-tag :type="isConnected ? 'success' : (isReconnecting ? 'warning' : 'danger')" size="small" effect="light">
                {{ isConnected ? '连接正常' : (isReconnecting ? '正在重连' : '连接断开') }}
              </el-tag>
            </div>
          </div>

          <div class="studio-body">
            <!-- 左侧：视频面板 -->
            <div class="video-panel">
              <video ref="videoPreview" autoplay muted playsinline class="video-preview" />
              <div class="video-panel__frame"></div>
              <div v-if="!isCapturing" class="video-panel__hint">
                <el-icon :size="26"><VideoCamera /></el-icon>
                <span>摄像头未开启，请检查权限</span>
              </div>
              <div class="video-panel__corner">
                <span class="rec-dot" :class="{ 'is-rec': isCapturing }"></span>
                <span class="video-panel__corner-text">{{ isCapturing ? 'REC' : 'STANDBY' }}</span>
              </div>
              <div class="video-panel__shade"></div>
            </div>

            <!-- 右侧：题目与控制 -->
            <div class="question-panel">
              <div class="question-panel__head">
                <div class="question-no">
                  <span class="question-no__label">QUESTION</span>
                  <span class="question-no__value">{{ currentQuestionOrder }}</span>
                </div>
                <div class="question-timer" :class="{ 'is-danger': remainingSeconds <= 10 }">
                  <el-progress
                    type="circle"
                    :percentage="progressPercent"
                    :width="68"
                    :stroke-width="6"
                    :color="remainingSeconds <= 10 ? '#f56c6c' : '#818cf8'"
                    :show-text="false"
                  />
                  <div class="question-timer__text">
                    <el-icon :size="15"><Timer /></el-icon>
                    <span
                      class="question-timer__num"
                      :class="{ 'countdown-pulse': remainingSeconds <= 10 && remainingSeconds > 0 }"
                    >{{ countdownDisplay }}</span>
                  </div>
                </div>
              </div>

              <div class="question-body" :class="{ 'is-reading': isAiSpeaking }">
                <div class="question-body__label">
                  <span v-if="isAiSpeaking" class="reading-bar"></span>
                  <el-icon v-if="isAiSpeaking" class="is-loading"><Microphone /></el-icon>
                  {{ isAiSpeaking ? 'AI 正在读题，请聆听' : '请针对题目作答' }}
                </div>
                <div class="question-text">{{ currentQuestionText || '等待题目...' }}</div>
              </div>

              <div class="control-dock">
                <el-button class="btn-next" type="primary" size="large" :disabled="isAiSpeaking || isAnswerAnalyzing" @click="handleNextQuestion">
                  <el-icon><ArrowRight /></el-icon>
                  {{ isAiSpeaking ? '请稍候，读题中...' : isAnswerAnalyzing ? '正在核验回答' : '下一题' }}
                </el-button>
                <el-button class="btn-end" size="large" @click="handleFinishInterview">
                  <el-icon><Close /></el-icon>
                  结束面试
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 面试结束 -->
      <div v-else-if="interviewPhase === 'finished'" class="finished-phase">
        <el-result icon="success" title="面试已完成" sub-title="评估报告正在生成，可返回评估流程查看">
          <template #extra>
            <el-button type="primary" @click="handleAnalyze">重新分析</el-button>
            <el-button @click="router.push(`/employee/ability-profile?empId=${empId}`)">返回面试记录</el-button>
          </template>
        </el-result>
      </div>

      <!-- 分析中 -->
      <div v-else-if="interviewPhase === 'analyzing'" class="analyzing-phase">
        <el-result icon="info" title="正在分析" sub-title="AI正在后台分析面试结果（视觉+语音内容），您可以安全离开，稍后通过面试记录查看结果。">
          <template #extra>
            <el-button type="primary" loading>分析中...</el-button>
            <el-button @click="router.push(`/employee/ability-profile?empId=${empId}`)">返回面试记录</el-button>
          </template>
        </el-result>
      </div>

      <!-- 分析完成 -->
      <div v-else-if="interviewPhase === 'completed'" class="completed-phase">
        <el-descriptions title="面试结果" :column="2" border style="margin-bottom: 20px;">
          <el-descriptions-item label="综合得分">
            <span :style="{ fontSize: '24px', fontWeight: 'bold', color: detail?.overallScore != null && detail.overallScore >= 60 ? '#67c23a' : '#909399' }">
              {{ detail?.overallScore ?? '证据不足' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="问题数量">{{ detail?.questionCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detail?.questions?.length" style="margin-bottom: 20px;">
          <h4>题目结果</h4>
          <el-table :data="detail.questions" border size="small">
            <el-table-column prop="questionOrder" label="序号" width="60px" />
            <el-table-column prop="questionText" label="题目" min-width="200px" show-overflow-tooltip />
            <el-table-column label="回答证据" min-width="180px" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.answerTranscript || '未采集到语音回答转写' }}
              </template>
            </el-table-column>
            <el-table-column label="得分" width="80px">
              <template #default="{ row }">
                <span v-if="row.answerScore != null" :style="{ color: row.answerScore >= 60 ? '#67c23a' : '#f56c6c' }">{{ row.answerScore }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="analysisComment" label="评语" min-width="200px" show-overflow-tooltip />
          </el-table>
        </div>

        <div v-if="detail?.abilities?.length" style="margin-bottom: 20px;">
          <h4>能力核验结果</h4>
          <el-table :data="detail.abilities" border size="small">
            <el-table-column prop="tagName" label="能力标签" width="120px" />
            <el-table-column label="掌握等级" width="100px">
              <template #default="{ row }">
                <el-tag :type="row.masteryLevel >= 4 ? 'success' : row.masteryLevel >= 3 ? 'primary' : 'warning'" size="small">
                  {{ getLevelText(row.masteryLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="80px">
              <template #default="{ row }">{{ (row.confidenceScore * 100).toFixed(0) }}%</template>
            </el-table-column>
            <el-table-column prop="evidenceSummary" label="证据摘要" min-width="200px" show-overflow-tooltip />
            <el-table-column label="状态" width="80px">
              <template #default="{ row }">
                <el-tag :type="row.importedFlag ? 'success' : 'info'" size="small">{{ row.importedFlag ? '已导入' : '待导入' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="detail?.evidences?.length" style="margin-bottom: 20px;">
          <h4>分析证据</h4>
          <el-table :data="detail.evidences" border size="small">
            <el-table-column prop="evidenceType" label="类型" width="100px">
              <template #default="{ row }">
                <el-tag :type="row.evidenceType === 'VISUAL' ? 'success' : row.evidenceType === 'TEXT' ? 'primary' : 'warning'" size="small">{{ row.evidenceType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="evidenceText" label="证据内容" min-width="320px" show-overflow-tooltip />
            <el-table-column label="置信度" width="90px">
              <template #default="{ row }">{{ row.confidenceScore != null ? `${(row.confidenceScore * 100).toFixed(0)}%` : '-' }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="detail?.summaryReport" style="margin-bottom: 20px;">
          <h4>总结报告</h4>
          <el-input type="textarea" :model-value="detail.summaryReport" :rows="6" readonly />
        </div>

        <div style="text-align: center;">
          <el-button size="large" @click="router.push(`/employee/ability-profile?empId=${empId}`)">返回面试记录</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.live-interview-page {
  padding: 20px;
}

.setup-phase {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.post-selector {
  width: 100%;
  max-width: 600px;
  padding: 40px;
}

.post-selector h3 {
  text-align: center;
  margin-bottom: 8px;
  color: #303133;
  font-size: 24px;
}

.post-selector p {
  text-align: center;
}

.interviewing-phase {
  min-height: 500px;
}

/* ============ 面试工作台（浅色玻璃，与系统一致） ============ */
.interview-studio {
  position: relative;
  border-radius: 18px;
  padding: 20px;
  background:
    linear-gradient(135deg, rgba(59, 130, 246, 0.05), transparent 60%),
    rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 2px 24px rgba(15, 23, 42, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.studio-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 16px;
}
.studio-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.studio-brand__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.18);
  transition: all 0.3s;
}
.studio-brand__dot.is-live {
  background: #f43f5e;
  box-shadow: 0 0 0 3px rgba(244, 63, 94, 0.16), 0 0 12px rgba(244, 63, 94, 0.55);
  animation: rec-breathe 1.6s ease-in-out infinite;
}
@keyframes rec-breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
.studio-brand__text {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.4px;
}
.studio-topbar__right {
  display: flex;
  gap: 10px;
  align-items: center;
}
.ai-speaking-tag {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(124, 58, 237, 0.1));
  border: 1px solid rgba(124, 58, 237, 0.28);
  color: #6d28d9;
}

.studio-body {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 20px;
}

/* 视频面板 */
.video-panel {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: #0f172a;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 12px 32px -14px rgba(15, 23, 42, 0.28);
}
.video-preview {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  transform: scaleX(-1);
  display: block;
  background: #0f172a;
}
.video-panel__frame {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(transparent 80%, rgba(15, 23, 42, 0.42));
  z-index: 2;
}
.video-panel__hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 14px;
  background: rgba(15, 23, 42, 0.55);
  z-index: 2;
}
.video-panel__corner {
  position: absolute;
  top: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.14);
  z-index: 4;
}
.rec-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}
.rec-dot.is-rec {
  background: #f43f5e;
  box-shadow: 0 0 10px rgba(244, 63, 94, 0.8);
  animation: rec-breathe 1.2s ease-in-out infinite;
}
.video-panel__corner-text {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #e2e8f0;
}
.video-panel__shade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 70px;
  background: linear-gradient(transparent, rgba(15, 23, 42, 0.6));
  z-index: 2;
}

/* 题目面板 */
.question-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.question-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.question-no {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.question-no__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: #2563eb;
}
.question-no__value {
  font-size: 44px;
  font-weight: 800;
  line-height: 1;
  color: #1e293b;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(135deg, #2563eb 0%, #06b6d4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.question-timer {
  position: relative;
  width: 68px;
  height: 68px;
  flex-shrink: 0;
}
.question-timer__text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #64748b;
}
.question-timer__num {
  font-size: 17px;
  font-weight: 700;
  color: #334155;
  font-variant-numeric: tabular-nums;
}
.question-timer.is-danger .question-timer__num {
  color: #dc2626;
}

.question-body {
  flex: 1;
  border-radius: 14px;
  padding: 18px 20px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(148, 163, 184, 0.18);
  transition: border-color 0.3s, background 0.3s;
}
.question-body.is-reading {
  border-color: rgba(124, 58, 237, 0.4);
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.06), rgba(124, 58, 237, 0.05));
}
.question-body__label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #2563eb;
  margin-bottom: 12px;
  text-transform: uppercase;
}
.reading-bar {
  width: 5px;
  height: 14px;
  border-radius: 3px;
  background: linear-gradient(180deg, #3b82f6, #8b5cf6);
  animation: reading-bounce 0.9s ease-in-out infinite;
}
@keyframes reading-bounce {
  0%, 100% { transform: scaleY(0.6); opacity: 0.6; }
  50% { transform: scaleY(1); opacity: 1; }
}
.question-text {
  font-size: 18px;
  line-height: 1.75;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
}

.control-dock {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
}
.control-dock .el-button {
  height: 50px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}
.btn-next.el-button--primary {
  background: linear-gradient(135deg, #2563eb 0%, #06b6d4 100%);
  border: none;
  box-shadow: 0 10px 24px -8px rgba(37, 99, 235, 0.45);
}
.btn-next.el-button--primary:hover {
  background: linear-gradient(135deg, #1d4ed8 0%, #0891b2 100%);
  transform: translateY(-1px);
}
.btn-next.el-button.is-disabled {
  background: #e2e8f0;
  color: #94a3b8;
}
.btn-end {
  background: rgba(244, 63, 94, 0.08);
  border: 1px solid rgba(244, 63, 94, 0.3);
  color: #e11d48;
}
.btn-end:hover {
  background: rgba(244, 63, 94, 0.14);
  border-color: rgba(244, 63, 94, 0.5);
  color: #be123c;
}

.finished-phase,
.analyzing-phase,
.completed-phase {
  min-height: 400px;
}

.countdown-pulse {
  animation: pulse-red 1s ease-in-out infinite;
}

@keyframes pulse-red {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.08); }
}

h4 {
  margin-bottom: 12px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 8px;
}
</style>
