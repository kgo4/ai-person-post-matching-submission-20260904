import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')
const graph = readFileSync(new URL('../../../components/graph/PostPanorama3DGraph.vue', import.meta.url), 'utf8')

/**
 * 岗位全景页的可读性契约。
 *
 * 视图已从「按技术栈 / 按岗位级别」+ 岗位→能力→技能点 三层阅读链，收窄为
 * 「按技术栈 / 按岗位」两层（岗位 ↔ 技术栈）。收窄原因见 postPanorama3d.ts 建图入口注释：
 * 「两种视图只保留岗位与技术栈的关系，避免把岗位能力等级/技能点混入视图语义」。
 *
 * 因此这里锁定的是**收窄后**的结构：不再断言 level 视图与 能力/技能点 层级。
 */

assert.match(page, /新一代信息技术岗位全景图谱/, 'the domain must be explicit in the page title')
assert.match(page, /按技术栈/, 'the page must expose a technology-stack view')
assert.match(page, /按岗位/, 'the page must expose a post view')

// 阅读层级随视图切换：技术栈视图是「技术栈 → 岗位」，岗位视图是「岗位（中心）→ 技术栈」。
// 两种视图都必须把 岗位 与 技术栈 这两个层级标出来，否则用户不知道自己在看哪一层。
assert.match(page, /reading-path__item--stack">技术栈/, 'the reading hierarchy must include the technology-stack layer')
assert.match(page, /reading-path__item--post">岗位/, 'the reading hierarchy must include the post layer')

// 聚焦关系是「单中心投影」的视觉说明，聚焦后其余无关节点会被剔除，必须有图例解释
assert.match(page, /聚焦关系/, 'the legend must explain the focused relationship state')

// 中心节点不带常驻标签，其余关键节点必须常驻标签，否则 3D 场景里读不出节点身份
assert.match(graph, /node-label/, 'key graph nodes must have persistent labels')

console.log('panorama readable structure tests passed')
