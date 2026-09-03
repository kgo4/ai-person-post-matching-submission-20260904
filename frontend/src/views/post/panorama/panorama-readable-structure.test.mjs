import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')
const graph = readFileSync(new URL('../../../components/graph/PostPanorama3DGraph.vue', import.meta.url), 'utf8')

assert.match(page, /新一代信息技术岗位全景图谱/, 'the domain must be explicit in the page title')
assert.match(page, /按技术栈/, 'the page must expose a technology-stack view')
assert.match(page, /按岗位级别/, 'the page must expose a level view')
assert.match(page, /reading-path__item--post">岗位/, 'the reading hierarchy must start with posts')
assert.match(page, /reading-path__item--ability">能力标签/, 'the reading hierarchy must include abilities')
assert.match(page, /reading-path__item--skill">技能点/, 'the reading hierarchy must include skill points')
assert.match(page, /聚焦关系/, 'the legend must explain the focused relationship state')
assert.match(graph, /node-label/, 'key graph nodes must have persistent labels')

console.log('panorama readable structure tests passed')
