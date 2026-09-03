import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const routerIndex = readFileSync(join(currentDir, 'index.ts'), 'utf8')
const knowledgeRoute = readFileSync(join(currentDir, 'modules/knowledge.ts'), 'utf8')

assert.equal(routerIndex.includes('legacyCapabilityRouteMap'), false, 'router should not redirect the entire capability center')
assert.equal(routerIndex.includes('const legacyCapabilityPath = legacyCapabilityRouteMap[to.path]'), false, 'guard should not intercept all capability routes')

assert.equal(routerIndex.includes("redirect: '/capability-brain/evidence'"), true, 'capability center should default to evidence instead of workbench')
assert.equal(routerIndex.includes("component: () => import('@/views/contest/cockpit/index.vue')"), false, 'workbench cockpit page should not be routed')
assert.equal(routerIndex.includes("name: 'CapabilityBrainOverview'"), false, 'workbench overview route should not remain as a page')
assert.equal(routerIndex.includes('工作台'), false, 'workbench label should not remain in routes')

for (const routeName of [
  "name: 'CapabilityBrainEvidence'",
]) {
  assert.equal(routerIndex.includes(routeName), true, `capability route should remain: ${routeName}`)
}

for (const removedRoute of [
  "name: 'CapabilityBrainEvaluation'",
  "name: 'CapabilityBrainEvaluationDetail'",
  "path: 'evaluation'",
  "path: 'evaluation/detail/:id'",
]) {
  assert.equal(routerIndex.includes(removedRoute), false, `deprecated evaluation route should be removed: ${removedRoute}`)
}

for (const routeName of [
  "name: 'RagKnowledge'",
  "name: 'KgWorkbench'",
]) {
  assert.equal(knowledgeRoute.includes(routeName), true, `capability route should exist in knowledge module: ${routeName}`)
}

console.log('capability route tests passed')
