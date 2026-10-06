import assert from 'node:assert/strict'
import { canInitializeChart } from './chartVisibility.ts'

assert.equal(canInitializeChart({ clientWidth: 0, clientHeight: 320 }), false)
assert.equal(canInitializeChart({ clientWidth: 480, clientHeight: 0 }), false)
assert.equal(canInitializeChart({ clientWidth: 480, clientHeight: 320 }), true)

console.log('chartVisibility tests passed')
