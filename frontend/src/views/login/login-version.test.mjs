import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve('src/views/login/index.vue'), 'utf8')

assert.match(source, /KGO Graph Pro/)
assert.match(source, /KGO Graph Max/)
assert.match(source, /敬请期待/)
assert.match(source, /class="login-version"/)
assert.match(source, /\.login-version\s*\{[\s\S]*?position:\s*fixed/)
assert.match(source, /@media\s*\(max-width:\s*768px\)[\s\S]*?\.login-version\s*\{[\s\S]*?left:\s*50%/)

console.log('login version marker checks passed')
