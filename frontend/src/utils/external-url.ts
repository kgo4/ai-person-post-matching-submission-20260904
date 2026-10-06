/**
 * 外部链接安全校验。
 *
 * <p>项目材料（仓库 / 演示 / 报告地址）由**员工自由填写**，若直接绑到 `href`，
 * `javascript:` 这类伪协议会在点击时执行脚本 —— 而点这些链接的往往是 HR，
 * 账号权限更高，受害面更大。因此渲染前必须先过这道校验。</p>
 *
 * <p>用 `new URL` 解析而非 `startsWith('http')`：后者会放过 `httpfoo://`、
 * `http://` 之类的畸形值，也会漏掉首尾空格的情况。</p>
 */

/** 允许的协议白名单 */
const ALLOWED_PROTOCOLS = ['http:', 'https:']

/**
 * 校验并归一化一个外部链接。
 *
 * @param raw 原始字符串（来自用户输入）
 * @returns 可安全用于 `href` 的地址；不合法时返回 `null`
 */
export function toSafeExternalUrl(raw?: string | null): string | null {
  if (typeof raw !== 'string') {
    return null
  }
  const trimmed = raw.trim()
  if (!trimmed) {
    return null
  }

  let parsed: URL
  try {
    parsed = new URL(trimmed)
  } catch {
    // 相对地址、缺少协议、纯文本都会走到这里
    return null
  }

  if (!ALLOWED_PROTOCOLS.includes(parsed.protocol)) {
    return null
  }
  return trimmed
}
