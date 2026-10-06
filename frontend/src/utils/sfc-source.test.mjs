import assert from 'node:assert/strict'
import { extractTemplate, stripSfcComments } from './sfc-source.ts'

/* ============ stripSfcComments ============ */

// HTML 注释要真正被去掉 —— 这正是骗到本目录契约测试的那一种
assert.equal(
  stripSfcComments('<template><!-- 不再显示系统名『多源异构岗位与能力图谱』 --><b>x</b></template>'),
  '<template><b>x</b></template>',
)

// 块注释（脚本 / 样式）
assert.equal(stripSfcComments('a/* 说明 */b'), 'ab')

// 行注释
assert.equal(stripSfcComments('const a = 1 // 说明\nconst b = 2'), 'const a = 1 \nconst b = 2')

// 顺序陷阱：块注释内部的 `//` 不能把块注释后面的代码吃掉。
// 若先处理行注释，`/* x // y */ const z = 1` 会变成 `/* x ` + ` const z = 1` 丢失。
assert.equal(stripSfcComments('/* x // y */ const z = 1').trim(), 'const z = 1')

// URL 不能被行注释规则误伤（负向后行断言的真正用途）
assert.equal(
  stripSfcComments("// 头像 https://cdn.example.com/a.png\nconst u = 'https://cdn.example.com/b.png'"),
  "\nconst u = 'https://cdn.example.com/b.png'",
)

// 路径写法（单斜杠、`/**`）不受影响
assert.equal(stripSfcComments("const p = '/uploads/**'\nconst q = \"/logo-mark.png\""), "const p = '/uploads/**'\nconst q = \"/logo-mark.png\"")

// 缩进的整行注释
assert.equal(stripSfcComments('  // 说明\ncode'), '  \ncode')

/* ============ extractTemplate ============ */

const sfc = `<script>const a = 1</script>
<template>
  <div>
    <template v-else>回落</template>
    <el-dropdown>账号区</el-dropdown>
  </div>
</template>
<style>.x{}</style>`

const tpl = extractTemplate(sfc)
// 关键：必须跨过内层嵌套的 `<template v-else>` 一直取到根部闭合标签，
// 否则切片在第一个 `</template>` 处就截断，后面的断言会假通过。
assert.equal(tpl.includes('账号区'), true, 'extractTemplate 应在嵌套 template 之后仍继续')
assert.equal(tpl.includes('const a = 1'), false, '不应包含 script 段')
assert.equal(tpl.includes('.x{}'), false, '不应包含 style 段')

// 没有 template 段时返回空串，而不是抛错或返回整份源码
assert.equal(extractTemplate('<script>const a = 1</script>'), '')

console.log('sfc source helper tests passed')
