export interface VideoFrameCaptureOptions {
  intervalMs?: number
  width?: number
  quality?: number
}

export function useVideoFrameCapture(options: VideoFrameCaptureOptions = {}) {
  let timer: number | null = null
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')

  const intervalMs = options.intervalMs ?? 2000
  const width = options.width ?? 480
  const quality = options.quality ?? 0.72

  function start(videoElement: HTMLVideoElement, onFrame: (imageDataUrl: string) => void) {
    stop()
    if (!ctx) return

    timer = window.setInterval(() => {
      if (videoElement.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || videoElement.videoWidth === 0) {
        return
      }

      const height = Math.round(width * (videoElement.videoHeight / videoElement.videoWidth))
      canvas.width = width
      canvas.height = height
      ctx.drawImage(videoElement, 0, 0, width, height)
      onFrame(canvas.toDataURL('image/jpeg', quality))
    }, intervalMs)
  }

  function stop() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  return {
    start,
    stop,
  }
}
