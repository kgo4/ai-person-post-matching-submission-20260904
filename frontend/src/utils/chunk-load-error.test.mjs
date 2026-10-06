import assert from 'node:assert/strict'
import {
  CHUNK_RELOAD_COOLDOWN_MS,
  isChunkLoadError,
  shouldAutoReload,
} from './chunk-load-error.ts'

/* ============ isChunkLoadError：各浏览器措辞都要认 ============ */

// 用户实际上报的原文（Chrome，URL 已部署变更）
assert.equal(
  isChunkLoadError(
    new TypeError(
      'Failed to fetch dynamically imported module: https://129.211.182.128/assets/resume-parse-CFw42R8S.js'
    )
  ),
  true
)

// 同一条也可能是纯字符串（未包装成 Error 的场景）
assert.equal(
  isChunkLoadError('Failed to fetch dynamically imported module: /assets/foo.js'),
  true
)

// Firefox
assert.equal(isChunkLoadError(new Error('error loading dynamically imported module')), true)
// Safari
assert.equal(isChunkLoadError(new Error('Importing a module script failed.')), true)
assert.equal(isChunkLoadError(new Error('Failed to load module script: 404')), true)
// Vite 动态 CSS 预加载失败
assert.equal(isChunkLoadError(new Error('Unable to preload CSS for /assets/x.css')), true)

/* ============ 跨 realm / 非 Error 对象 ============ */

// `instanceof Error` 在跨 iframe 场景会失效 → 不能只认 Error
assert.equal(
  isChunkLoadError({ name: 'TypeError', message: 'Failed to fetch dynamically imported module: /a.js' }),
  true
)
// 缺少 name 也要能判
assert.equal(isChunkLoadError({ message: 'dynamically imported module' }), true)

/* ============ 绝不能误判（这是本模块最重要的约束） ============ */

// 业务错误不能被当 chunk 问题，否则会触发一次莫名其妙的整页刷新、掩盖真实报错
assert.equal(isChunkLoadError(new Error('匹配任务执行失败：岗位数据为空')), false)
assert.equal(isChunkLoadError(new Error('Request failed with status code 500')), false)
assert.equal(isChunkLoadError(new Error('登录已过期，请重新登录')), false)
assert.equal(isChunkLoadError(new Error('Network Error')), false)

// 空值族
assert.equal(isChunkLoadError(null), false)
assert.equal(isChunkLoadError(undefined), false)
assert.equal(isChunkLoadError(''), false)
assert.equal(isChunkLoadError({}), false)
assert.equal(isChunkLoadError(404), false)
assert.equal(isChunkLoadError({ message: '' }), false)
assert.equal(isChunkLoadError({ message: 123 }), false)

/* ============ shouldAutoReload：防死循环的核心 ============ */

const NOW = 1_700_000_000_000

// 从未刷过 → 允许自动刷
assert.equal(shouldAutoReload(null, NOW), true)
assert.equal(shouldAutoReload('', NOW), true)
assert.equal(shouldAutoReload('   ', NOW), true)

// 刚刷过（冷却窗口内）→ 禁止，改为提示用户手动刷
assert.equal(shouldAutoReload(String(NOW - 1_000), NOW), false)
assert.equal(shouldAutoReload(String(NOW), NOW), false)
// 边界：正好等于冷却时长 → 不算超过，仍需等待（避免边界抖动）
assert.equal(shouldAutoReload(String(NOW - CHUNK_RELOAD_COOLDOWN_MS), NOW), false)
// 超过冷却窗口 → 允许再刷一次（用户可能隔了很久才又点进来）
assert.equal(shouldAutoReload(String(NOW - CHUNK_RELOAD_COOLDOWN_MS - 1), NOW), true)

// 脏数据（被人手工改过 / 旧格式）→ 当作没刷过，允许一次
assert.equal(shouldAutoReload('not-a-number', NOW), true)
assert.equal(shouldAutoReload('NaN', NOW), true)

// 时钟回拨 → 差值可能为负。必须保守禁止自动刷，否则会变成无限刷新
assert.equal(shouldAutoReload(String(NOW + 60_000), NOW), false)

// 自定义冷却窗口要生效
assert.equal(shouldAutoReload(String(NOW - 5_000), NOW, 1_000), true)
assert.equal(shouldAutoReload(String(NOW - 500), NOW, 1_000), false)

console.log('chunk-load-error: all assertions passed')
