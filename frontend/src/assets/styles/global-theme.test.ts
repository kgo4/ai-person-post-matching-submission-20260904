import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const rawCss = readFileSync(join(currentDir, 'global.css'), 'utf8')

/**
 * 去掉注释后的 CSS。
 *
 * 断言必须看**规则**而不是注释：文件头正好用 `[data-theme='dark']` 这个写法
 * 记录了"它已被删除、不要再补半套"，若直接扫原文会被自己的说明文字误伤。
 */
const css = rawCss.replace(/\/\*[\s\S]*?\*\//g, '')

/**
 * 全局样式的主题契约。
 *
 * 本系统是**纯浅色主题**：原深色主题（变量块 + Element Plus 深色覆盖）
 * 已随侧边栏的深浅模式切换一并删除（2026-09-04），现在没有任何地方会设置 `data-theme`。
 *
 * 之所以要一条测试守着，是因为「只补半套」的暗色规则比没有更糟：
 * 只写 `[data-theme='dark']` 的变量块而不写组件覆盖，会得到背景变深、
 * 文字仍是深色的不可读页面 —— 而这种问题只有真的切到深色时才会暴露。
 */
describe('global.css 主题契约', () => {
  it('不再包含任何 data-theme 主题分支（纯浅色）', () => {
    expect(css).not.toContain('[data-theme')
    // 连前缀式的写法也一并守住，避免换个写法又溜进来
    expect(css).not.toMatch(/data-theme/)
  })

  it('核心设计 token 仍在 :root 中声明', () => {
    for (const token of [
      '--app-bg',
      '--app-surface',
      '--app-border',
      '--app-text',
      '--app-text-strong',
      '--app-primary',
      '--app-accent',
      '--app-radius-lg',
      '--app-shadow-sm',
    ]) {
      expect(css, `缺少设计 token ${token}`).toContain(token)
    }
    // token 必须落在 :root 里，否则组件取不到值
    const rootBlock = css.slice(css.indexOf(':root'), css.indexOf('}', css.indexOf(':root')))
    expect(rootBlock).toContain('--app-primary')
  })

  it('Element Plus 变量仍被映射到设计 token', () => {
    // 组件库默认色必须走 --app-*，否则换主色时 Element Plus 不会跟着变
    expect(css).toContain('--el-text-color-primary: var(--app-text-strong)')
    expect(css).toContain('--el-mask-color')
  })
})
