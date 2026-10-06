/**
 * 前端「动态导入失败」兜底 —— 纯逻辑部分。
 *
 * 背景（2026-09-04 线上报错）：
 *   Failed to fetch dynamically imported module:
 *   https://.../assets/resume-parse-CFw42R8S.js
 *
 * 根因不是后端接口 404，而是**构建产物与浏览器缓存不同步**：
 *   Vite 给每个路由 chunk 打内容哈希（`resume-parse-<hash>.js`）。
 *   用户停留期间已加载旧版主 bundle，其内部记录的 chunk 名是旧哈希 `CFw42R8S`；
 *   此后运维重新构建并部署，服务器上只剩新哈希 `a1tlkSqn`，旧文件被覆盖。
 *   用户此时点「上传简历」→ 浏览器去下载那个**已不存在的旧 chunk** → 404。
 *
 *   注意：这不是 bug 的偶发，而是**每次部署后必然出现**的窗口期问题 ——
 *   只要用户不刷新页面，旧标签页就会一直引用旧 chunk 名。
 *
 * 处理策略：
 *   1) 自动刷新一次，拿回新 bundle（对用户几乎无感）；
 *   2) 若冷却期内再次失败（新部署本身有问题 / 网络问题），**不再自动刷**，
 *      改为中文提示引导手动刷新 —— 避免「刷新 → 404 → 刷新」死循环把用户卡住。
 *
 * 本文件只放纯逻辑，便于用 `.test.mjs` 直接断言；浏览器副作用在
 * `chunk-reload-guard.ts`。
 */

/** 各浏览器对「动态导入失败」的措辞不同，需分别覆盖。 */
const CHUNK_ERROR_MARKERS: RegExp[] = [
  /failed to fetch dynamically imported module/i, // Chrome / Edge
  /error loading dynamically imported module/i, // Firefox
  /importing a module script failed/i, // Safari
  /failed to load module script/i, // Safari / 旧版
  /unable to preload css/i, // Vite 动态样式 chunk
  /dynamically imported module/i, // 兜底
]

/** 自动刷新的冷却窗口：窗口内二次失败就不再自动刷，防止死循环。 */
export const CHUNK_RELOAD_COOLDOWN_MS = 15_000

/** 记录「上次自动刷新时刻」的 sessionStorage key（会话级即可，换标签页应重新允许）。 */
export const CHUNK_RELOAD_FLAG_KEY = 'app:chunk-reload-at'

/** 自动刷新时的过渡提示。 */
export const CHUNK_RELOADING_MESSAGE = '页面资源已更新，正在重新加载…'

/** 自动刷新已失效、需要用户动手时的提示。 */
export const CHUNK_RELOAD_FAILED_MESSAGE =
  '页面资源加载失败，可能是页面版本已更新。请按 Ctrl+F5（Mac 为 ⌘+Shift+R）强制刷新后重试。'

/**
 * 从任意抛出的值里取出可用于匹配的文本。
 *
 * 动态导入失败在不同场景下抛出的东西不一样：Vite 可能抛 `Error`，
 * `vite:preloadError` 事件给的是 Error，跨 iframe 场景可能是普通对象（`instanceof` 失效），
 * 因此不能只判 `instanceof Error`。
 */
function extractErrorText(error: unknown): string {
  if (typeof error === 'string') return error
  if (!error || typeof error !== 'object') return ''

  const { name, message } = error as { name?: unknown; message?: unknown }
  const parts: string[] = []
  if (typeof name === 'string') parts.push(name)
  if (typeof message === 'string') parts.push(message)
  return parts.join(': ')
}

/**
 * 判断异常是否为「动态导入资源加载失败」。
 *
 * 只认特征串，不做模糊猜测 —— 业务错误（接口 500、校验失败）不能被误判成
 * chunk 问题，否则会触发一次莫名其妙的整页刷新，掩盖真正的报错。
 */
export function isChunkLoadError(error: unknown): boolean {
  const text = extractErrorText(error)
  if (!text) return false
  return CHUNK_ERROR_MARKERS.some(pattern => pattern.test(text))
}

/**
 * 是否还允许自动刷新。
 *
 * @param lastReloadAtRaw sessionStorage 里记录的原始字符串（可能是 null / 脏数据）
 * @param now             当前时间戳（显式传入，便于测试）
 * @param cooldownMs      冷却窗口
 */
export function shouldAutoReload(
  lastReloadAtRaw: string | null,
  now: number,
  cooldownMs: number = CHUNK_RELOAD_COOLDOWN_MS
): boolean {
  // 从未刷过 → 允许
  if (lastReloadAtRaw === null || lastReloadAtRaw.trim() === '') return true

  const last = Number(lastReloadAtRaw)
  // 脏数据（被手工改过 / 旧版本格式）→ 当作没刷过，允许一次
  if (!Number.isFinite(last)) return true

  const elapsed = now - last
  // 时钟回拨（用户改系统时间、NTP 校正）→ 差值可能为负。
  // 此时**不允许**自动刷新：宁可让用户手动刷，也不要冒死循环的风险。
  if (elapsed < 0) return false

  return elapsed > cooldownMs
}
