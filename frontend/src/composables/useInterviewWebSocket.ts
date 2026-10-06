import { ref, onUnmounted } from 'vue'

export interface InterviewMessage {
  type: string
  content?: string
  parentQuestionId?: number
  followUpId?: number
  followUpOrder?: number
  questionOrder?: number
  questionText?: string
  durationSeconds?: number
  remainingSeconds?: number
  text?: string
  isFinal?: boolean
  resume?: InterviewResumeState
}

export interface InterviewResumeState {
  conversationState: string
  questionOrder: number
  questionText: string
  followUpId?: number | null
  followUpOrder?: number | null
  followUpQuestionText?: string | null
  questionDeadlineEpochMillis: number
  durationSeconds: number
  remainingSeconds: number
  sessionVersion: number
}

export interface InterviewCallbacks {
  onConnected?: () => void
  onQuestion?: (questionOrder: number, questionText: string, durationSeconds: number) => void
  onFollowUpQuestion?: (followUpOrder: number, questionText: string, durationSeconds: number, followUpId?: number) => void
  onCountdown?: (remainingSeconds: number) => void
  onTranscript?: (text: string, isFinal: boolean) => void
  onNextQuestion?: () => void
  onAnswerAnalysisStarted?: () => void
  onInterviewFinished?: () => void
  onResumeState?: (state: InterviewResumeState) => void
  onError?: (message: string) => void
  /** 连接断开后开始尝试重连时触发 */
  onReconnecting?: (attempt: number, max: number) => void
  /** 重连成功（且如曾面试，已自动恢复面试）时触发 */
  onReconnected?: () => void
  /** 达到最大重连次数仍失败时触发 */
  onReconnectFailed?: () => void
}

export interface ConnectOptions {
  /**
   * 当 WebSocket 意外断开后，调用此函数获取新的 WS 鉴权 ticket 进行重连。
   * 若不提供则禁用自动重连。
   */
  fetchFreshTicket?: () => Promise<string>
  /** 最大重连次数，默认 5 */
  maxReconnects?: number
  /** 心跳发送间隔（毫秒），默认 15000；为 0 禁用心跳 */
  heartbeatMs?: number
  /** PONG 等待超时（毫秒），默认 10000；超时强制关闭触发重连 */
  pongTimeoutMs?: number
}

export function useInterviewWebSocket() {
  const isConnected = ref(false)
  const isInterviewStarted = ref(false)
  const isReconnecting = ref(false)
  const reconnectAttempt = ref(0)
  const currentMessage = ref<InterviewMessage | null>(null)

  let ws: WebSocket | null = null
  let callbacks: InterviewCallbacks = {}
  let pendingConnectResolve: (() => void) | null = null
  let pendingConnectReject: ((error: Error) => void) | null = null
  let pendingStartResolve: (() => void) | null = null
  let pendingStartReject: ((error: Error) => void) | null = null

  // 重连上下文
  let lastSessionId: string | null = null
  let connectOptions: ConnectOptions = {}
  let manualClose = false
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttemptsUsed = 0
  // 面试启动后状态——重连成功后据此自动恢复
  let interviewEverStarted = false

  // 心跳
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let pongTimer: ReturnType<typeof setTimeout> | null = null
  let lastPongAt = 0

  /**
   * 连接到面试WebSocket
   */
  function connect(
    sessionId: string,
    ticket: string,
    interviewCallbacks: InterviewCallbacks,
    options: ConnectOptions = {},
  ): Promise<void> {
    disconnectInternal(true)
    callbacks = interviewCallbacks
    connectOptions = options
    lastSessionId = sessionId
    manualClose = false
    reconnectAttemptsUsed = 0

    void openSocket(sessionId, ticket)

    const connectPromise = new Promise<void>((resolve, reject) => {
      pendingConnectResolve = resolve
      pendingConnectReject = reject
    })
    return connectPromise
  }

  /**
   * 真正创建 WebSocket 实例并绑定所有事件
   */
  function openSocket(sessionId: string, ticket: string) {
    const wsBaseUrl = import.meta.env.VITE_WS_URL || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`
    const url = `${wsBaseUrl}/ws/interview/${sessionId}?ticket=${encodeURIComponent(ticket)}`

    if (import.meta.env.DEV) { console.log('WebSocket连接地址:', url) }
    const socket = new WebSocket(url)
    socket.binaryType = 'arraybuffer'
    ws = socket

    socket.onopen = () => {
      if (ws !== socket) return
      isConnected.value = true
      // 初始 connect 的 resolve 仍按原逻辑通过 CONNECTED 之外直接触发
      callbacks.onConnected?.()
      pendingConnectResolve?.()
      pendingConnectResolve = null
      pendingConnectReject = null

      // 若是重连成功，且此前面试已启动 → 自动重启面试
      if (reconnectAttemptsUsed > 0 && interviewEverStarted) {
        if (import.meta.env.DEV) { console.log('重连成功，自动重启面试') }
        send({ action: 'RESUME_INTERVIEW' })
        // 不必等待 INTERVIEW_STARTED，让 onmessage 自然更新状态
        callbacks.onReconnected?.()
      }
      reconnectAttemptsUsed = 0
      isReconnecting.value = false
      reconnectAttempt.value = 0

      // 启动心跳
      startHeartbeat()
    }

    socket.onmessage = async (event) => {
      if (ws !== socket) return

      try {
        const message: InterviewMessage = JSON.parse(event.data)
        currentMessage.value = message

        switch (message.type) {
          case 'CONNECTED':
            isConnected.value = true
            break
          case 'INTERVIEW_STARTED':
            isInterviewStarted.value = true
            interviewEverStarted = true
            pendingStartResolve?.()
            pendingStartResolve = null
            pendingStartReject = null
            break
          case 'RESUME_STATE':
            if (message.resume) {
              isInterviewStarted.value = true
              interviewEverStarted = true
              callbacks.onResumeState?.(message.resume)
            }
            break
          case 'QUESTION':
            if (message.questionOrder && message.questionText && message.durationSeconds) {
              callbacks.onQuestion?.(message.questionOrder, message.questionText, message.durationSeconds)
            }
            break
          case 'FOLLOW_UP_QUESTION':
            if (message.questionText && message.durationSeconds) {
              callbacks.onFollowUpQuestion?.(
                message.followUpOrder ?? 1,
                message.questionText,
                message.durationSeconds,
                message.followUpId,
              )
            }
            break
          case 'COUNTDOWN':
            if (message.remainingSeconds !== undefined) {
              callbacks.onCountdown?.(message.remainingSeconds)
            }
            break
          case 'TRANSCRIPT':
            if (message.text !== undefined) {
              callbacks.onTranscript?.(message.text, message.isFinal ?? false)
            }
            break
          case 'NEXT_QUESTION':
            callbacks.onNextQuestion?.()
            break
          case 'ANSWER_ANALYSIS_STARTED':
            callbacks.onAnswerAnalysisStarted?.()
            break
          case 'INTERVIEW_FINISHED':
            isInterviewStarted.value = false
            interviewEverStarted = false
            callbacks.onInterviewFinished?.()
            break
          case 'PONG':
            // 服务端心跳回包
            lastPongAt = Date.now()
            if (pongTimer) {
              clearTimeout(pongTimer)
              pongTimer = null
            }
            break
          case 'ERROR':
            pendingStartReject?.(new Error(message.content ?? 'Interview start failed'))
            pendingStartResolve = null
            pendingStartReject = null
            callbacks.onError?.(message.content ?? '未知错误')
            break
        }
      } catch (e) {
        console.error('解析WebSocket消息失败:', e)
      }
    }

    socket.onclose = () => {
      if (ws !== socket) return
      stopHeartbeat()
      isConnected.value = false
      // 仅当面试尚未结束（interviewEverStarted 仍 true）才算意外断开 → 尝试重连
      const wasInterviewing = interviewEverStarted

      // 第一次连接若失败/onclose reject pending
      pendingConnectReject?.(new Error('WebSocket closed before opening'))
      pendingConnectResolve = null
      pendingConnectReject = null
      // 若是初次 startInterview 还未成功就断开，立即 reject
      if (!interviewEverStarted) {
        pendingStartReject?.(new Error('WebSocket closed before interview started'))
      }
      pendingStartResolve = null
      pendingStartReject = null

      if (manualClose) {
        // 主动断开：不重连，仅重置面试已启动状态
        isInterviewStarted.value = false
        interviewEverStarted = false
        return
      }

      // 若未提供 ticketFetcher，则禁用重连
      if (!connectOptions.fetchFreshTicket) {
        isInterviewStarted.value = false
        return
      }

      // 意外断开 → 触发重连（只在面试进行中或之后才有价值；初始连接失败也尝试）
      if (wasInterviewing || !interviewEverStarted) {
        scheduleReconnect()
      } else {
        isInterviewStarted.value = false
      }
    }

    socket.onerror = (error) => {
      if (ws !== socket) return
      console.error('WebSocket错误:', error)
      pendingConnectReject?.(new Error('WebSocket connection error'))
      if (!interviewEverStarted) {
        pendingStartReject?.(new Error('WebSocket connection error'))
      }
      pendingConnectResolve = null
      pendingConnectReject = null
      pendingStartResolve = null
      pendingStartReject = null
      callbacks.onError?.('WebSocket连接错误')
      // 错误后通常伴随 onclose，由 onclose 触发重连，避免重复
    }
  }

  /**
   * 计算并安排下一次重连（指数退避 + 抖动）
   */
  function scheduleReconnect() {
    const max = connectOptions.maxReconnects ?? 5
    if (reconnectAttemptsUsed >= max) {
      isReconnecting.value = false
      reconnectAttempt.value = 0
      callbacks.onReconnectFailed?.()
      return
    }
    reconnectAttemptsUsed++
    isReconnecting.value = true
    reconnectAttempt.value = reconnectAttemptsUsed
    callbacks.onReconnecting?.(reconnectAttemptsUsed, max)

    const base = Math.min(1000 * 2 ** (reconnectAttemptsUsed - 1), 16000)
    const jitter = Math.floor(base * 0.2 * (Math.random() * 2 - 1))
    const delay = Math.max(500, base + jitter)
    if (import.meta.env.DEV) { console.log(`安排第 ${reconnectAttemptsUsed} 次重连，延迟 ${delay}ms`) }

    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void doReconnect()
    }, delay)
  }

  async function doReconnect() {
    if (!lastSessionId || !connectOptions.fetchFreshTicket) {
      isReconnecting.value = false
      return
    }
    try {
      const freshTicket = await connectOptions.fetchFreshTicket()
      openSocket(lastSessionId, freshTicket)
    } catch (e) {
      console.error('获取新 ticket 失败:', e)
      // 获取 ticket 也失败了，继续重试
      scheduleReconnect()
    }
  }

  // ==================== 心跳 ====================

  function startHeartbeat() {
    stopHeartbeat()
    const interval = connectOptions.heartbeatMs ?? 15000
    if (interval <= 0) return
    lastPongAt = Date.now()
    heartbeatTimer = setInterval(() => {
      if (!ws || ws.readyState !== WebSocket.OPEN) return
      send({ action: 'PING' })
      // 启动 PONG 超时监听
      const pongTimeout = connectOptions.pongTimeoutMs ?? 10000
      if (pongTimer) clearTimeout(pongTimer)
      pongTimer = setTimeout(() => {
        // 超过 pongTimeoutMs 仍未收到 PONG → 判定链路僵死，主动关闭以触发重连
        console.warn(`PONG 超时 ${pongTimeout}ms，主动关闭以触发重连`)
        if (ws) {
          // 触发 onclose →重连
          ws.close()
        }
      }, pongTimeout)
    }, interval)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (pongTimer) {
      clearTimeout(pongTimer)
      pongTimer = null
    }
  }

  /**
   * 发送开始面试命令
   */
  function startInterview() {
    send({ action: 'START_INTERVIEW' })
  }

  function resumeInterview() {
    send({ action: 'RESUME_INTERVIEW' })
  }

  function waitForInterviewStarted(): Promise<void> {
    if (isInterviewStarted.value) {
      return Promise.resolve()
    }

    return new Promise((resolve, reject) => {
      pendingStartResolve = resolve
      pendingStartReject = reject
    })
  }

  /**
   * 发送下一题命令
   */
  function nextQuestion() {
    send({ action: 'NEXT_QUESTION' })
  }

  function answerWindowReady(questionOrder?: number, followUpId?: number) {
    send({ action: 'QUESTION_READ_COMPLETE', questionOrder, followUpId })
  }

  /**
   * 发送结束面试命令
   */
  function finishInterview() {
    send({ action: 'FINISH_INTERVIEW' })
  }

  /**
   * 发送音频数据
   */
  function sendAudio(audioData: ArrayBuffer) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(audioData)
    }
  }

  /**
   * 发送文本消息
   */
  function send(data: Record<string, unknown>) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(data))
    }
  }

  /**
   * 取消尚未触发的重连定时器
   */
  function clearTimeouts() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  /**
   * 主动断开
   */
  function disconnect() {
    disconnectInternal(false)
  }

  function disconnectInternal(isReconnectReset: boolean) {
    manualClose = true
    clearTimeouts()
    stopHeartbeat()
    pendingConnectResolve = null
    pendingConnectReject = null
    pendingStartResolve = null
    pendingStartReject = null
    if (ws) {
      ws.close()
      ws = null
    }
    isConnected.value = false
    isInterviewStarted.value = false
    if (isReconnectReset) {
      isReconnecting.value = false
      reconnectAttempt.value = 0
      interviewEverStarted = false
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    isInterviewStarted,
    isReconnecting,
    reconnectAttempt,
    currentMessage,
    connect,
    disconnect,
    startInterview,
    resumeInterview,
    waitForInterviewStarted,
    nextQuestion,
    answerWindowReady,
    finishInterview,
    sendAudio,
    send,
  }
}
