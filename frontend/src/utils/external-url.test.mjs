import assert from 'node:assert/strict'
import { toSafeExternalUrl } from './external-url.ts'

// ===== 合法地址 =====

assert.equal(toSafeExternalUrl('https://github.com/foo/bar'), 'https://github.com/foo/bar')
assert.equal(toSafeExternalUrl('http://example.com'), 'http://example.com')
// 首尾空格要归一化，否则 href 里会带上空格
assert.equal(toSafeExternalUrl('  https://example.com/a  '), 'https://example.com/a')
// 带查询串与锚点要原样保留
assert.equal(
  toSafeExternalUrl('https://example.com/a?b=1#c'),
  'https://example.com/a?b=1#c',
)

// ===== 伪协议必须被拦下（本模块存在的理由）=====

assert.equal(toSafeExternalUrl('javascript:alert(1)'), null)
assert.equal(toSafeExternalUrl('JavaScript:alert(1)'), null)
assert.equal(toSafeExternalUrl('data:text/html,<script>alert(1)</script>'), null)
assert.equal(toSafeExternalUrl('vbscript:msgbox(1)'), null)
assert.equal(toSafeExternalUrl('file:///etc/passwd'), null)
assert.equal(toSafeExternalUrl('ftp://example.com'), null)

// ===== 畸形与缺失 =====

assert.equal(toSafeExternalUrl(''), null)
assert.equal(toSafeExternalUrl('   '), null)
assert.equal(toSafeExternalUrl(null), null)
assert.equal(toSafeExternalUrl(undefined), null)
// 无协议：解析失败，必须判为不可点（前端会回退成纯文本展示）
assert.equal(toSafeExternalUrl('github.com/foo/bar'), null)
assert.equal(toSafeExternalUrl('www.example.com'), null)
// startsWith('http') 会放过这种畸形值，new URL 不会
assert.equal(toSafeExternalUrl('httpfoo://example.com'), null)
assert.equal(toSafeExternalUrl('http://'), null)
assert.equal(toSafeExternalUrl('https://'), null)

console.log('external url tests passed')
