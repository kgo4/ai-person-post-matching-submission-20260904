/**
 * 顶栏面包屑解析单测（node 直跑）。
 *
 * 覆盖：子项命中、最长路径优先、模块同名折叠、路由标题兜底、无模块兜底。
 * 模块归属不在本模块职责内（由 layout 的 activeModule 统一解析），因此不在此断言。
 */
import assert from 'node:assert/strict'
import { buildBreadcrumb } from './breadcrumb.ts'

const postModule = {
  label: '岗位模型',
  path: '/post/model-config',
  children: [
    { label: '能力配置', path: '/post/model-config' },
    { label: '岗位档案', path: '/post/list' },
    { label: '岗位详情', path: '/post/list/detail' },
  ],
}

/* ------------------------------ 子项命中 ------------------------------ */

assert.deepEqual(buildBreadcrumb('/post/list', postModule), [
  { label: '岗位模型', path: '/post/model-config' },
  { label: '岗位档案' },
])

/* --------------------------- 最长路径优先 --------------------------- */

// /post/list/detail 同时命中 /post/list 与 /post/list/detail，应取更具体的那个
assert.deepEqual(buildBreadcrumb('/post/list/detail', postModule), [
  { label: '岗位模型', path: '/post/model-config' },
  { label: '岗位详情' },
])

/* ---------------------------- 子项前缀匹配 ---------------------------- */

assert.deepEqual(buildBreadcrumb('/post/model-config/step-2', postModule), [
  { label: '岗位模型', path: '/post/model-config' },
  { label: '能力配置' },
])

/* --------------------------- 模块同名折叠 --------------------------- */

// 子项与模块同名同路径时不重复展示
const homeModule = {
  label: '首页',
  path: '/workbench',
  children: [{ label: '首页', path: '/workbench' }],
}
assert.deepEqual(buildBreadcrumb('/workbench', homeModule), [{ label: '首页', path: '/workbench' }])

// 模块名与子项名不同时展示两级，与侧栏层级一致
const namedModule = {
  label: '首页',
  path: '/workbench',
  children: [{ label: '工作台', path: '/workbench' }],
}
assert.deepEqual(buildBreadcrumb('/workbench', namedModule), [
  { label: '首页', path: '/workbench' },
  { label: '工作台' },
])

/* ---------------------------- 路由标题兜底 ---------------------------- */

// 路径不命中任何子项，但有路由 title → 「模块 / 路由标题」
assert.deepEqual(buildBreadcrumb('/post/unknown-page', postModule, '未知页'), [
  { label: '岗位模型', path: '/post/model-config' },
  { label: '未知页' },
])

// 不命中子项也没有 title → 只展示模块
assert.deepEqual(buildBreadcrumb('/post/unknown-page', postModule), [
  { label: '岗位模型', path: '/post/model-config' },
])

// title 与模块名相同时不重复展示两级
assert.deepEqual(buildBreadcrumb('/post/unknown-page', postModule, '岗位模型'), [
  { label: '岗位模型', path: '/post/model-config' },
])

/* ------------------------------ 无模块兜底 ------------------------------ */

assert.deepEqual(buildBreadcrumb('/somewhere', null, '神秘页'), [{ label: '神秘页' }])
assert.deepEqual(buildBreadcrumb('/somewhere', undefined), [{ label: '工作台', path: '/workbench' }])

// 空路径不能抛异常
assert.ok(buildBreadcrumb('', postModule).length >= 1)

console.log('breadcrumb.test.mjs passed')
