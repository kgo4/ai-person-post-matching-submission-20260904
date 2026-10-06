/**
 * 工作台数据契约单测（node 直跑）。
 *
 * 重点覆盖「无基线数据时不得伪造环比」这一约定：
 * buildDelta / mapServerDelta 都必须返回 undefined，而不是 0 或 100%。
 */
import assert from 'node:assert/strict'
import {
  buildDelta,
  emptySnapshot,
  formatMetric,
  mapServerDelta,
  monthDayLabel,
} from './workbench-types.ts'

/* ------------------------------ buildDelta ------------------------------ */

// 缺任一基线数据 → undefined（视图层隐藏环比行）
assert.equal(buildDelta(null, 10), undefined)
assert.equal(buildDelta(10, null), undefined)
assert.equal(buildDelta(undefined, undefined), undefined)
assert.equal(buildDelta(Number.NaN, 10), undefined)

// 计数类：增长 / 下降 / 持平
assert.deepEqual(buildDelta(12, 0), { direction: 'up', text: '12', basis: '较昨日' })
assert.deepEqual(buildDelta(0, 12), { direction: 'down', text: '12', basis: '较昨日' })
assert.deepEqual(buildDelta(5, 5), { direction: 'flat', text: '0', basis: '较昨日' })

// 百分比类
assert.deepEqual(buildDelta(110, 100, '较上周', true), { direction: 'up', text: '10.0%', basis: '较上周' })
assert.deepEqual(buildDelta(90, 100, '较昨日', true), { direction: 'down', text: '10.0%', basis: '较昨日' })

// 百分比：基数为 0 时除零无意义 → undefined
assert.equal(buildDelta(5, 0, '较昨日', true), undefined)

// 百分比：变化不足 0.1% 时按 0.1% 展示，不出现「↑0%」
assert.equal(buildDelta(100, 100.02, '较昨日', true).text, '0.1%')

// 百分比：完全持平仍是 flat 0%
assert.deepEqual(buildDelta(100, 100, '较昨日', true), { direction: 'flat', text: '0%', basis: '较昨日' })

/* ---------------------------- mapServerDelta ---------------------------- */

assert.equal(mapServerDelta(null), undefined)
assert.equal(mapServerDelta(undefined), undefined)
// 后端 diff 已取绝对值，方向由 direction 表达
assert.deepEqual(mapServerDelta({ direction: 'up', diff: 7, basis: '较昨日' }), {
  direction: 'up',
  text: '7',
  basis: '较昨日',
})
// basis 缺省时回落「较昨日」
assert.equal(mapServerDelta({ direction: 'flat', diff: 0 }).basis, '较昨日')
// diff 非法 → undefined，不渲染出「NaN」
assert.equal(mapServerDelta({ direction: 'up', diff: Number.NaN }), undefined)

/* ------------------------------ formatMetric ------------------------------ */

assert.equal(formatMetric(null), '--')
assert.equal(formatMetric(undefined), '--')
assert.equal(formatMetric(Number.NaN), '--')
assert.equal(formatMetric(0), '0', '0 是有效业务值，不能显示为 --')
assert.equal(formatMetric(42), '42')

/* ------------------------------ monthDayLabel ------------------------------ */

assert.equal(monthDayLabel('2026-09-04 10:20:30'), '09-04')
assert.equal(monthDayLabel('2026-09-04T10:20:30'), '09-04')
assert.equal(monthDayLabel(null), '--')
assert.equal(monthDayLabel(undefined), '--')
assert.equal(monthDayLabel('weird'), 'weird')

/* ------------------------------ 快照不再含团队成员 ------------------------------ */

// 【2026-09-04】「团队成员」卡片已从工作台移除（工作台不展示他人档案），
// members 字段与 withMembers 一并删除，避免再次把他人档案带到工作台。
const base = emptySnapshot('测试')
assert.equal(base.subtitle, '测试')
assert.ok(!('members' in base), '工作台快照不应再含 members（团队成员卡片已移除）')

console.log('workbench-types.test.mjs passed')
