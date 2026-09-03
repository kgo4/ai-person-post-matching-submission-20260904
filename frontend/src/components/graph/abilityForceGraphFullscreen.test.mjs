import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./AbilityForceGraph.vue', import.meta.url), 'utf8')

assert.match(
  source,
  /<Teleport to="body" :disabled="!isFullscreen">[\s\S]*?<div\s+class="ability-force-graph-wrapper"/,
  'fullscreen wrapper should be teleported to body',
)

assert.doesNotMatch(
  source,
  /<div\s+class="ability-force-graph-wrapper"[\s\S]*?<Teleport to="body" :disabled="!isFullscreen">[\s\S]*?class="graph-fullscreen-btn"/,
  'fullscreen should not depend on teleporting only the button',
)

console.log('abilityForceGraph fullscreen structure tests passed')
