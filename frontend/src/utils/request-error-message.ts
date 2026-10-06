/**
 * 统一解析请求失败的可读提示语。
 *
 * 背景（这是一个真实踩过的坑）：
 * 后端 `GlobalExceptionHandler` 对 `BusinessException` 做了「HTTP 状态码对齐」——
 * `PARAM_ERROR(400)` / `NOT_FOUND(404)` / `STATE_CONFLICT(409)` 这类标准语义码会被
 * 真的写进 HTTP 状态码。axios 因此走 **error 分支**（而不是拿到 200 + 业务码），
 * reject 出来的是原始 axios 错误，`error.message` 是
 * `"Request failed with status code 400"` 这种英文原文，
 * 后端精心写的中文业务提示（例如「会议链接域名不在允许列表中（当前允许：meeting.iflyrec.com）」）
 * 就整条丢掉了 —— 用户看到的就是「直接报错、没有提示」。
 *
 * 所以这里统一：**优先取后端响应体里的 message**，其次按状态码给中文兜底，
 * 最后才落到网络类提示。纯函数、无副作用，便于单测。
 */

/** 只描述本函数用到的字段，避免把 axios 类型泄漏到调用方 */
export interface RequestErrorLike {
  message?: string
  code?: string
  response?: {
    status?: number
    data?: string | { message?: string; code?: number } | null
  } | null
}

const NETWORK_FALLBACK = '网络异常，请稍后重试'

/**
 * axios / 浏览器抛出的是**英文技术文案**，不是给业务用户看的。
 * 这些一律不许透出，必须换成中文兜底。
 */
const TECHNICAL_MESSAGES = [
  /^request failed with status code\b/i,
  /^network error$/i,
  /^failed to fetch$/i,
  /\btimeout\b/i,
]

/**
 * 机器文本的特征串。
 *
 * 为什么需要它：后端已经有一道出口闸门（`UserFacingMessage`），但那是**最后一道**。
 * 历史上有过两次「JDBC 原文直接出现在界面上」，源头都是某条新加的 catch 忘了包装。
 * 前端再加一道，是为了让「后端漏了」不至于直接砸到用户脸上——这叫纵深防御。
 *
 * 只用**强特征**（数据库关键字、异常类名、堆栈行、事务提示语），不用「英文」这种弱特征：
 * 弱特征会把 `HR`、`PDF` 这类正常英文词误判成技术文案。整条都是英文但不是技术文本的，
 * 交给下面的「无中文且很长」规则兜底。
 */
const TECHNICAL_MARKERS: RegExp[] = [
  // 数据库约束与 SQL
  /duplicate entry/i,
  /\bfor key\b[^\n]*(uk_|idx_|pk_)/i,
  /constraintviolationexception/i,
  /\bsqlstate\b/i,
  /###\s*error (updating|querying|executing) database/i,
  /###\s*the error may (exist|involve)/i,
  /\bsql:\s*(insert|update|delete|select)\b/i,
  // 异常类名与堆栈
  /caused by:/i,
  /nested exception/i,
  /\bat [\w$.]+\([\w$]+\.java:\d+\)/,
  /\bjava\.(lang|sql|util|io)\.[A-Z]/,
  /\borg\.springframework\b/i,
  // 事务 / 连接类
  /transaction\s+(rolled back|silently)/i,
  /rollback-only/i,
  /connection (refused|reset by peer)/i,
]

/** 整条没有中文、且已经是「一句话」而非单个词：判定为机器文本。 */
const ENGLISH_WORD_SEQUENCE = /\s/
const ENGLISH_ONLY_MIN_LENGTH = 40

const CJK = /[\u4e00-\u9fa5]/

/**
 * 这段文本像不像「机器说的话」。
 *
 * 导出是为了让调用方（例如材料上传的失败提示）能对**自己拼的文案**自检，
 * 而不是只在最终展示时才兜底。
 *
 * 判定顺序刻意是「先强特征、后中文检测」：`索引失败: Duplicate entry '...'`
 * 这种「中文前缀 + 英文异常原文」的拼接文案，必须由强特征拦下，不能因为有中文就放行。
 */
export function looksTechnicalText(message: string | null | undefined): boolean {
  const text = (message ?? '').trim()
  if (!text) return true
  if (TECHNICAL_MARKERS.some(pattern => pattern.test(text))) return true
  // 全中文之外的情况：没有中文、且是多个词组成的一句话（`ACTIVE`、`PDF` 这类单个词放行，
  // 它们更可能是枚举值而不是异常原文；多词英文几乎不可能是给中文用户看的提示）
  return !CJK.test(text) && (ENGLISH_WORD_SEQUENCE.test(text) || text.length >= ENGLISH_ONLY_MIN_LENGTH)
}

function isTechnicalMessage(message: string): boolean {
  const text = message.trim()
  if (!text) return true
  return TECHNICAL_MESSAGES.some(pattern => pattern.test(text)) || looksTechnicalText(text)
}

/**
 * 提取后端响应体中的业务提示语。
 * 兼容 `{ message }`（R.fail 结构）以及 axios 直接返回的字符串 body。
 *
 * 若 body 里拿到的是机器文本（后端闸门漏了），这里**当作没有提示**处理，
 * 让调用方落到状态码中文兜底——宁可提示笼统，也不把 SQL 砸给用户。
 */
function backendMessage(error: RequestErrorLike): string | null {
  const data = error.response?.data
  if (!data) return null
  if (typeof data === 'string') {
    const text = data.trim()
    return text.length > 0 && !looksTechnicalText(text) ? text : null
  }
  const message = typeof data.message === 'string' ? data.message.trim() : ''
  if (message.length === 0) return null
  return looksTechnicalText(message) ? null : message
}


function statusFallback(status: number | undefined): string | null {
  switch (status) {
    case 400:
      return '请求参数有误，请检查后重试'
    case 401:
      return '登录已过期，请重新登录'
    case 403:
      return '没有权限访问该资源'
    case 404:
      return '请求的资源不存在'
    case 409:
      return '数据状态已变化，请刷新后重试'
    case 413:
      return '内容过大，请精简后重试'
    case 429:
      return '操作过于频繁，请稍后再试'
    case 500:
      return '服务器内部错误，请稍后重试'
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后重试'
    default:
      return null
  }
}

/**
 * 解析请求失败提示语。
 *
 * @param error    axios 抛出的错误（或任意含 message/response 的对象）
 * @param fallback 完全无法判断时的兜底文案
 */
export function resolveApiErrorMessage(
  error: unknown,
  fallback: string = NETWORK_FALLBACK,
): string {
  const err = (error ?? {}) as RequestErrorLike

  // 1) 后端有明确业务提示 → 直接用。这是最有信息量的一档。
  const backend = backendMessage(err)

  // 2) 没有任何响应体 → 网络层问题（超时/断网/被取消）
  if (!err.response) {
    const raw = typeof err.message === 'string' ? err.message : ''
    if (/timeout/i.test(raw)) return '请求超时，请稍后重试'
    if (/cancel/i.test(raw) || err.code === 'ERR_CANCELED') return '请求已取消'
    if (backend) return backend
    if (raw && !isTechnicalMessage(raw)) return raw
    return fallback
  }

  const status = err.response.status

  // 3) 401/403 属于「客户端无法自救」的场景，用固定中文提示更稳妥：
  //    后端 Spring Security 拒绝时响应体常常是空的，拿不到任何 message。
  const authMessage = statusFallback(status)
  if (status === 401 || status === 403) {
    return authMessage ?? backend ?? fallback
  }

  // 4) 其余情况：后端 message 优先，其次状态码兜底，最后通用兜底。
  return backend ?? authMessage ?? fallback
}

/**
 * 是否为「请求已取消」——调用方通常应当静默忽略，而不是弹错误提示。
 */
export function isCanceledRequest(error: unknown): boolean {
  const err = (error ?? {}) as RequestErrorLike
  if (err.code === 'ERR_CANCELED') return true
  return typeof err.message === 'string' && /canceled|cancelled/i.test(err.message)
}
