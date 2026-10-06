/**
 * 前端「动态导入失败」兜底 —— 浏览器副作用部分。
 *
 * 纯逻辑见 `chunk-load-error.ts`（那里解释了根因与策略），本文件只负责：
 *   ① 挂 `router.onError`（路由懒加载 chunk 拉取失败会走这里）
 *   ② 挂 `vite:preloadError`（Vite 预加载 CSS/JS 失败）
 * 两者最终都交给同一个 `handleChunkLoadError`，避免出现两套行为。
 */

import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CHUNK_RELOAD_FAILED_MESSAGE,
  CHUNK_RELOAD_FLAG_KEY,
  CHUNK_RELOADING_MESSAGE,
  isChunkLoadError,
  shouldAutoReload,
} from './chunk-load-error'

/** 提示展示时长：reload 前给它留一点时间，否则用户只看到白屏，以为卡死。 */
const RELOAD_DELAY_MS = 500

/**
 * sessionStorage 是否可用。
 *
 * 隐私模式 / 禁用 Cookie 场景下 `setItem` 会抛异常。**这直接影响能否自动刷新**：
 * 我们靠 sessionStorage 记录「上次自动刷新的时刻」来防死循环，
 * 存不了状态就没有防循环能力 → 此时必须**放弃自动刷新**，改为让用户手动刷。
 * （若这里天真地按「没记录 = 没刷过」放行，storage 不可用时会变成无限刷新。）
 */
function isSessionStorageAvailable(): boolean {
  try {
    const probeKey = '__chunk_reload_probe__'
    window.sessionStorage.setItem(probeKey, '1')
    window.sessionStorage.removeItem(probeKey)
    return true
  } catch {
    return false
  }
}

function readLastReloadAt(): string | null {
  try {
    return window.sessionStorage.getItem(CHUNK_RELOAD_FLAG_KEY)
  } catch {
    return null
  }
}

function markReloaded(now: number): void {
  try {
    window.sessionStorage.setItem(CHUNK_RELOAD_FLAG_KEY, String(now))
  } catch {
    // 已由 isSessionStorageAvailable 前置拦截，这里仅兜底
  }
}

/**
 * 统一的处理入口。
 *
 * @returns 是否已识别并处理（false 表示这不是 chunk 问题，调用方应继续按原逻辑处理）
 */
export function handleChunkLoadError(error: unknown): boolean {
  if (!isChunkLoadError(error)) return false

  if (!isSessionStorageAvailable()) {
    ElMessage.error(CHUNK_RELOAD_FAILED_MESSAGE)
    return true
  }

  const now = Date.now()
  if (!shouldAutoReload(readLastReloadAt(), now)) {
    // 冷却期内二次失败：多半不是「浏览器持有旧 bundle」，而是新部署本身有问题。
    // 再自动刷下去就是死循环 —— 交给用户手动决定。
    ElMessage.error(CHUNK_RELOAD_FAILED_MESSAGE)
    return true
  }

  markReloaded(now)
  ElMessage.warning(CHUNK_RELOADING_MESSAGE)
  window.setTimeout(() => window.location.reload(), RELOAD_DELAY_MS)
  return true
}

/**
 * 安装兜底。应在 `createRouter()` 之后、导出 router 之前调用。
 */
export function installChunkLoadErrorHandler(router: Router): void {
  router.onError(error => {
    if (handleChunkLoadError(error)) return
    // 非 chunk 错误（业务异常等）保持原有行为：交给控制台，不吞掉。
    console.error('[router] 路由异常：', error)
  })
}

/**
 * 安装 Vite 预加载失败兜底。
 *
 * Vite 在 `<link rel="modulepreload">` / 动态 CSS 拉取失败时派发 `vite:preloadError`，
 * 默认行为是把错误抛给 `window.onerror`（控制台一片红）。这里接管它：
 * 能自动恢复就恢复，恢复不了给中文提示。
 *
 * 注意事件类型：Vite 声明的 `vite:preloadError` 载荷是 `{ payload: Error }`，
 * **不是** `CustomEvent.detail`（`detail` 是 DOM 的，`payload` 是 Vite 的）。
 */
export function installPreloadErrorHandler(): void {
  window.addEventListener('vite:preloadError', event => {
    // 已处理则阻止默认抛错，避免控制台噪音掩盖真实问题
    if (handleChunkLoadError(event.payload)) {
      event.preventDefault()
    }
  })
}

/** 一次性安装全部兜底（router 侧 + preload 侧）。 */
export function installChunkLoadGuards(router: Router): void {
  installChunkLoadErrorHandler(router)
  installPreloadErrorHandler()
}
