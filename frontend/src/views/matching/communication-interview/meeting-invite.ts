/**
 * 会议邀请文本的本地判定（纯函数，独立成模块以便 node 直测）。
 *
 * 讯飞会议「复制邀请信息」给出的是一整段多行文本，同域名下**同时存在入会链接与
 * 客户端下载链接**，因此前端**不能**对整段文本做 `new URL(text)` 校验 ——
 * 那会把合法输入直接判错，HR 根本提不上去。
 *
 * 这里只回答一个问题：「文本里有没有 http(s) 链接」。
 * 挑哪一条（优先含 /join 的）以及域名是否在白名单，全部交给后端
 * （`CommunicationInterviewService.normalizeAndValidateMeetingUrl`），
 * 避免前后端两套口径漂移。
 */

/**
 * 抽取 http(s) 链接的正则，与后端同口径：URL 只用可打印 ASCII（RFC 3986），
 * 所以是 `[\x21-\x7e]+` 而不是 `\S+` —— 后者会把链接后紧跟的中文说明
 * （如「会议号: 11626686」）一并吞进链接里。
 */
export const HTTP_URL_PATTERN = /https?:\/\/[\x21-\x7e]+/i

/** 文本中是否含至少一条 http(s) 链接；空值一律视为没有 */
export function containsHttpUrl(text: string | null | undefined): boolean {
  return HTTP_URL_PATTERN.test(text ?? '')
}
