import assert from 'node:assert/strict'
import { buildMaterialLinks, formatSubmittedAt, materialLinkHint } from './material-links.ts'

// ===== 只产出员工实际填写的项 =====

assert.deepEqual(buildMaterialLinks({}), [])
assert.deepEqual(buildMaterialLinks(null), [])
assert.deepEqual(buildMaterialLinks(undefined), [])
// 全为空白等于没填
assert.deepEqual(buildMaterialLinks({ repoUrl: '   ', demoUrl: '', reportUrl: null }), [])

const repoOnly = buildMaterialLinks({ repoUrl: 'https://github.com/a/b' })
assert.equal(repoOnly.length, 1)
assert.equal(repoOnly[0].key, 'repo')
assert.equal(repoOnly[0].label, '仓库')
assert.equal(repoOnly[0].href, 'https://github.com/a/b')

// ===== 顺序固定：仓库 / 演示 / 报告（不随填写顺序变化）=====

const all = buildMaterialLinks({
  reportUrl: 'https://r.example.com',
  repoUrl: 'https://github.com/a/b',
  demoUrl: 'https://d.example.com',
})
assert.deepEqual(all.map(l => l.key), ['repo', 'demo', 'report'])
assert.deepEqual(all.map(l => l.label), ['仓库', '演示', '报告'])

// ===== 不合法地址：href 为 null，但 raw 必须保留原文 =====
// 直接隐藏会让 HR 以为员工没交材料，而事实上他交了，只是写得不规范。

const broken = buildMaterialLinks({ repoUrl: 'javascript:alert(1)' })
assert.equal(broken.length, 1)
assert.equal(broken[0].href, null)
assert.equal(broken[0].raw, 'javascript:alert(1)')

const missingScheme = buildMaterialLinks({ reportUrl: 'github.com/a' })
assert.equal(missingScheme[0].href, null)
assert.equal(missingScheme[0].raw, 'github.com/a')

// raw 要 trim 后再暴露，避免模板里出现带前导空格的文本
const padded = buildMaterialLinks({ repoUrl: '   https://ok.com   ' })
assert.equal(padded[0].raw, 'https://ok.com')
assert.equal(padded[0].href, 'https://ok.com')

// ===== 提示语 =====

// 全部合法 → 不提示
assert.equal(materialLinkHint(all), null)
assert.equal(materialLinkHint([]), null)
assert.equal(materialLinkHint(null), null)

// 单项不合法：点名是哪一项，并写明怎么改
const hintOne = materialLinkHint(broken)
assert.ok(hintOne)
assert.ok(hintOne.includes('仓库'))
assert.ok(hintOne.includes('http://') && hintOne.includes('https://'))

// 多项不合法：用顿号连接，且每项都要出现（不能只报第一项）
const hintMany = materialLinkHint(buildMaterialLinks({
  repoUrl: 'github.com/a',
  reportUrl: 'javascript:alert(1)',
}))
assert.ok(hintMany)
assert.ok(hintMany.includes('仓库'))
assert.ok(hintMany.includes('报告'))
assert.ok(hintMany.includes('、'))
// 合法的演示项不该被点名
assert.equal(hintMany.includes('演示'), false)

// ===== 提交时间格式 =====

assert.equal(formatSubmittedAt('2026-09-04T15:30:00'), '2026-09-04 15:30')
assert.equal(formatSubmittedAt('2026-09-04T15:30:00.123'), '2026-09-04 15:30')
assert.equal(formatSubmittedAt('  2026-09-04T15:30:00  '), '2026-09-04 15:30')
// 空/缺失一律空串：模板里配合 v-if 不显示，好过显示 Invalid Date
assert.equal(formatSubmittedAt(''), '')
assert.equal(formatSubmittedAt('   '), '')
assert.equal(formatSubmittedAt(null), '')
assert.equal(formatSubmittedAt(undefined), '')

console.log('material links tests passed')
