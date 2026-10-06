/**
 * 项目材料的链接预处理（HR 复核侧展示用）。
 *
 * <p>把「员工填了什么」与「能不能点」分开表达：</p>
 * <ul>
 *   <li>没填 → 不产生条目；</li>
 *   <li>填了且合法 → 给出 {@code href}，渲染成可点链接；</li>
 *   <li>填了但不合法（缺协议、`javascript:` 等）→ {@code href} 为 null，
 *       但仍保留 {@code raw} 原文，由调用方按纯文本展示 ——
 *       直接隐藏会让 HR 以为员工没交材料，而事实上他交了，只是写了个不规范的地址。</li>
 * </ul>
 */

import { toSafeExternalUrl } from '../../../utils/external-url.ts'

export interface MaterialLinkInput {
  repoUrl?: string | null
  demoUrl?: string | null
  reportUrl?: string | null
}

export interface MaterialLink {
  /** 便于 v-for 的稳定 key */
  key: 'repo' | 'demo' | 'report'
  /** 中文标签：仓库 / 演示 / 报告 */
  label: string
  /** 可安全点击的地址；不合法时为 null */
  href: string | null
  /** 员工填写的原文（用于 href 为 null 时按纯文本兜底展示） */
  raw: string
}

const LINK_FIELDS: Array<{ key: MaterialLink['key']; label: string; pick: (m: MaterialLinkInput) => string | null | undefined }> = [
  { key: 'repo', label: '仓库', pick: m => m?.repoUrl },
  { key: 'demo', label: '演示', pick: m => m?.demoUrl },
  { key: 'report', label: '报告', pick: m => m?.reportUrl },
]

/**
 * 构建一条材料的链接条目列表，只含员工实际填写的项。
 */
export function buildMaterialLinks(material?: MaterialLinkInput | null): MaterialLink[] {
  const links: MaterialLink[] = []
  for (const field of LINK_FIELDS) {
    const raw = field.pick(material ?? {})
    const text = typeof raw === 'string' ? raw.trim() : ''
    if (!text) {
      continue
    }
    links.push({
      key: field.key,
      label: field.label,
      href: toSafeExternalUrl(text),
      raw: text,
    })
  }
  return links
}

/**
 * 不合法地址的提示语；全部合法（或没有地址）时返回 null。
 *
 * <p>提示里必须写明**怎么改**（补 `http(s)://`），否则 HR 只看到「打不开」，
 * 既无法判断该找员工确认、也不知道要确认什么。</p>
 */
export function materialLinkHint(links?: MaterialLink[] | null): string | null {
  const broken = (links ?? []).filter(link => link.href === null)
  if (broken.length === 0) {
    return null
  }
  const labels = broken.map(link => link.label).join('、')
  return `${labels}地址格式不规范（需以 http:// 或 https:// 开头），已按原文展示、无法直接打开。`
}

/**
 * 提交时间的展示格式。
 *
 * <p>后端回 ISO（`2026-09-04T15:30:00`），直接显示会带一个 `T`。
 * 这里只做字符串裁剪而不用 `Date` 解析：解析会引入时区换算，
 * 让「提交时间」与实际记录的时刻对不上，而复核场景不需要精确到分钟以外的换算。</p>
 *
 * @returns 形如 `2026-09-04 15:30`；空值或非法值返回空串（不显示好过显示 Invalid Date）
 */
export function formatSubmittedAt(raw?: string | null): string {
  if (typeof raw !== 'string') {
    return ''
  }
  const trimmed = raw.trim()
  if (!trimmed) {
    return ''
  }
  return trimmed.replace('T', ' ').slice(0, 16)
}
