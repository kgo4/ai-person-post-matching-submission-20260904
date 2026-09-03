import { defineConfig, presetIcons, presetUno } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  theme: {
    colors: {
      primary: '#2563eb',
      'primary-hover': '#1d4ed8',
      accent: '#3b82f6',
      surface: 'rgba(255, 255, 255, 0.64)',
      'surface-solid': '#ffffff',
      glass: 'rgba(255, 255, 255, 0.58)',
    },
  },
  shortcuts: {
    'flex-center': 'flex justify-center items-center',
    'glass-card': 'rounded-[14px] border border-slate-200/40 bg-white/60 backdrop-blur-xl shadow-[0_4px_20px_rgba(15,23,42,0.05)]',
    'glass-panel': 'rounded-[14px] border border-slate-200/40 bg-white/54 backdrop-blur-lg shadow-[0_2px_14px_rgba(15,23,42,0.04)]',
    'glass-toolbar': 'rounded-[12px] border border-slate-200/40 bg-white/50 backdrop-blur-lg shadow-[0_2px_12px_rgba(15,23,42,0.04)]',
    'glass-btn': 'inline-flex items-center gap-2 rounded-[10px] border border-slate-200/50 bg-white/64 px-4 py-2 text-sm font-semibold text-slate-600 shadow-[0_2px_8px_rgba(15,23,42,0.04)] transition-all duration-250 hover:-translate-y-[1px] hover:border-blue-300/60 hover:text-blue-700 hover:shadow-[0_6px_18px_rgba(37,99,235,0.12)]',
    'glow-text': 'bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-blue-800',
    'gradient-primary': 'bg-[linear-gradient(135deg,#2563eb_0%,#3b82f6_100%)]',
    'section-title': 'text-[15px] font-semibold text-slate-800 tracking-tight',
    'page-desc': 'mt-1 text-[13px] text-slate-500',
    'page-shell': 'relative space-y-6',
    'metric-chip': 'inline-flex items-center rounded-full border border-slate-200/40 bg-white/58 px-3 py-1 text-xs font-semibold text-slate-600 backdrop-blur-md',
  },
})
