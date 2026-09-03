import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'index.vue'), 'utf8')
const constants = readFileSync(join(currentDir, 'constants.ts'), 'utf8')

assert.equal(source.includes('采纳并应用到业务数据'), false)
assert.equal(source.includes('businessApplyStatusLabel'), true)
assert.equal(source.includes('businessApplyStatusTagType'), true)
assert.equal(source.includes('currentLog.businessApplyStatus'), true)
assert.equal(source.includes('logs.value.find((log) => log.id === reviewTarget.value?.id)'), true)
assert.equal(source.includes('currentLog.value = refreshedLog'), true)
assert.equal(constants.includes('待领域流程应用'), true)

console.log('governance apply status tests passed')
