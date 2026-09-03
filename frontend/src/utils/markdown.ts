/**
 * Markdown 安全渲染工具
 *
 * 使用 markdown-it 解析 + DOMPurify 清洗，防止 XSS 攻击。
 * 所有 Markdown 内容必须经过此工具渲染后再插入 DOM。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({
  html: false,        // 禁用原始 HTML，由 DOMPurify 统一处理
  breaks: true,       // \n 转 <br>
  linkify: true,      // 自动识别链接
  typographer: false
})

/**
 * 将 Markdown 文本渲染为安全的 HTML 字符串
 *
 * @param markdown 原始 Markdown 文本
 * @returns 经过 DOMPurify 清洗的 HTML 字符串
 */
export function renderMarkdownSafe(markdown: string): string {
  if (!markdown) return ''
  const rawHtml = md.render(markdown)
  return DOMPurify.sanitize(rawHtml, {
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'hr',
      'ul', 'ol', 'li',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'strong', 'em', 'del', 'code', 'pre',
      'blockquote', 'a', 'span'
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class', 'title']
  })
}
