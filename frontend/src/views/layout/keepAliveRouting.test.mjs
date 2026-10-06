import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'index.vue'), 'utf8')

assert.equal(source.includes('v-if="route.meta.keepAlive === true"'), true)
assert.equal(source.includes('v-if="route.meta.keepAlive !== true"'), true)

console.log('keep-alive routing tests passed')
