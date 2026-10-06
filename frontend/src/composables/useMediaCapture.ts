import { ref, onUnmounted } from 'vue'

export interface MediaCaptureOptions {
  video?: boolean
  audio?: boolean
  videoElement?: HTMLVideoElement | null
}

export function useMediaCapture(options: MediaCaptureOptions = {}) {
  const isCapturing = ref(false)
  const hasPermission = ref(false)
  const error = ref<string | null>(null)

  let mediaStream: MediaStream | null = null
  let audioContext: AudioContext | null = null
  let mediaRecorder: MediaRecorder | null = null
  let audioWorkletNode: AudioWorkletNode | null = null

  /**
   * 请求媒体权限并开始采集
   */
  async function startCapture(): Promise<boolean> {
    try {
      error.value = null

      // 请求媒体权限（启用回声消除、降噪、自动增益）
      mediaStream = await navigator.mediaDevices.getUserMedia({
        video: options.video ?? true,
        audio: options.audio ? {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 16000,
        } : false,
      })

      hasPermission.value = true

      // 将视频流绑定到video元素
      if (options.videoElement && mediaStream.getVideoTracks().length > 0) {
        options.videoElement.srcObject = mediaStream
      }

      isCapturing.value = true
      return true
    } catch (e: any) {
      error.value = e.message || '无法访问摄像头或麦克风'
      hasPermission.value = false
      return false
    }
  }

  /**
   * 停止采集
   */
  function stopCapture() {
    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop())
      mediaStream = null
    }

    if (options.videoElement) {
      options.videoElement.srcObject = null
    }

    if (audioContext) {
      audioContext.close()
      audioContext = null
    }

    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop()
      mediaRecorder = null
    }

    isCapturing.value = false
  }

  function attachVideoElement(videoElement: HTMLVideoElement | null) {
    if (videoElement && mediaStream && mediaStream.getVideoTracks().length > 0) {
      videoElement.srcObject = mediaStream
    }
  }

  /**
   * 获取音频数据（PCM格式）
   * 通过ScriptProcessorNode获取实时音频数据
   */
  function startAudioCapture(onAudioData: (data: Float32Array, sampleRate: number) => void): boolean {
    if (!mediaStream || mediaStream.getAudioTracks().length === 0) {
      error.value = '没有音频轨道'
      return false
    }

    try {
      audioContext = new AudioContext({ sampleRate: 16000 })
      const source = audioContext.createMediaStreamSource(mediaStream)

      // 使用ScriptProcessorNode获取音频数据
      const bufferSize = 4096
      const processor = audioContext.createScriptProcessor(bufferSize, 1, 1)
      const muteGain = audioContext.createGain()
      muteGain.gain.value = 0

      processor.onaudioprocess = (event) => {
        const inputData = event.inputBuffer.getChannelData(0)
        onAudioData(new Float32Array(inputData), audioContext!.sampleRate)
      }

      source.connect(processor)
      processor.connect(muteGain)
      muteGain.connect(audioContext.destination)

      return true
    } catch (e: any) {
      error.value = e.message || '音频采集初始化失败'
      return false
    }
  }

  /**
   * 录制视频
   */
  function startRecording(onDataAvailable?: (data: Blob) => void): boolean {
    if (!mediaStream) {
      error.value = '媒体流未初始化'
      return false
    }

    try {
      const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
        ? 'video/webm;codecs=vp9'
        : 'video/webm'

      mediaRecorder = new MediaRecorder(mediaStream, { mimeType })

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          onDataAvailable?.(event.data)
        }
      }

      mediaRecorder.start(1000) // 每秒触发一次
      return true
    } catch (e: any) {
      error.value = e.message || '视频录制初始化失败'
      return false
    }
  }

  /**
   * 停止录制
   */
  function stopRecording(): Blob | null {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop()
      // 注意：实际数据会通过ondataavailable回调返回
      return null
    }
    return null
  }

  /**
   * 切换摄像头
   */
  async function switchCamera(deviceId: string): Promise<boolean> {
    if (mediaStream) {
      stopCapture()
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { deviceId: { exact: deviceId } },
        audio: options.audio ?? true,
      })

      if (options.videoElement) {
        options.videoElement.srcObject = mediaStream
      }

      return true
    } catch (e: any) {
      error.value = e.message || '切换摄像头失败'
      return false
    }
  }

  /**
   * 获取可用设备列表
   */
  async function getDevices(): Promise<MediaDeviceInfo[]> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      return devices.filter(d => d.kind === 'videoinput' || d.kind === 'audioinput')
    } catch {
      return []
    }
  }

  onUnmounted(() => {
    stopCapture()
  })

  return {
    isCapturing,
    hasPermission,
    error,
    startCapture,
    stopCapture,
    attachVideoElement,
    startAudioCapture,
    startRecording,
    stopRecording,
    switchCamera,
    getDevices,
  }
}
