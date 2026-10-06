import { defineConfig, presetIcons, presetUno, transformerDirectives } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  // 组件 <style> 里的 @apply 需要由 UnoCSS 在构建期展开；
  // 缺少该 transformer 时 @apply 会原样进入产物 CSS，浏览器不认识 → 样式静默失效。
  transformers: [transformerDirectives()],
  theme: {
    colors: {
      primary: '#2f6bff',
      'primary-hover': '#1f57e0',
      accent: '#3b82f6',
      'accent-alt': '#6d7cf0',
      surface: '#ffffff',
      'surface-solid': '#ffffff',
      glass: 'rgba(255, 255, 255, 0.86)',
    },
  },
  shortcuts: {
    'flex-center': 'flex justify-center items-center',
    // 卡片风格与 global.css 的 .glass-card 保持同一套观感：纯白 + 冷蓝细边 + 大圆角 + 极轻阴影。
    // 注意 global.css 在 main.ts 中于 uno.css 之后导入，两者冲突时以 global.css 为准，
    // 这里只作为 UnoCSS 工具类场景（未引入 global.css 的组件）的兜底。
    'glass-card': 'rounded-[14px] border border-[#e3eaf6] bg-white shadow-[0_1px_3px_rgba(22,34,63,0.05)]',
    'glass-panel': 'rounded-[14px] border border-[#e3eaf6] bg-white shadow-[0_1px_3px_rgba(22,34,63,0.05)]',
    'glass-toolbar': 'rounded-[12px] border border-[#e3eaf6] bg-white shadow-[0_1px_3px_rgba(22,34,63,0.05)]',
    'glass-btn': 'inline-flex items-center gap-2 rounded-[10px] border border-[#cfd9ee] bg-white px-4 py-2 text-sm font-semibold text-[#58668a] shadow-[0_1px_3px_rgba(22,34,63,0.05)] transition-all duration-250 hover:-translate-y-[1px] hover:border-blue-300/60 hover:text-blue-700 hover:shadow-[0_6px_18px_rgba(47,107,255,0.12)]',
    'glow-text': 'bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-blue-800',
    'gradient-primary': 'bg-[linear-gradient(135deg,#2f6bff_0%,#6d7cf0_100%)]',
    'section-title': 'text-[15px] font-semibold text-[#16223f] tracking-tight',
    'page-desc': 'mt-1 text-[13px] text-[#58668a]',
    'page-shell': 'relative space-y-6',
    'metric-chip': 'inline-flex items-center rounded-full border border-[#e3eaf6] bg-white px-3 py-1 text-xs font-semibold text-[#58668a]',
  },
})
