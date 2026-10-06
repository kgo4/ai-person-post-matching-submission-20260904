import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'index.vue'), 'utf8')

// The learning-path page was refactored from `path-gen__*` (grid layout, 600px breakpoint)
// to `pg-*` (flex layout, 768px breakpoint). Keep guarding the same concern:
// the AI suggestion step stays narrow-screen friendly (fixed-width number, fluid body).
assert.match(source, /\.pg-ai-step \{[\s\S]*?display: flex[\s\S]*?align-items: flex-start/)
assert.match(source, /\.pg-ai-step__num \{[\s\S]*?flex-shrink: 0/)
assert.match(source, /\.pg-ai-step__body \{[\s\S]*?flex: 1[\s\S]*?min-width: 0/)
assert.match(source, /@media \(max-width: 768px\) \{/)

console.log('AI suggestion mobile layout tests passed')
