import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { findRequiredPathPermission } from '../../../router/path-permissions.ts'

/**
 * 员工「发起匹配」独立入口的契约回归（2026-09-04）。
 *
 * 用户原话：「发起匹配页面需要在侧边栏里面有入口，不要出现在我的匹配结果里面」。
 * 触发这件事的真实故障是：员工在「我的匹配结果」的弹窗里点发起匹配 →
 * 前端把 403 统一渲染成「没有权限访问该资源」，而后端真实原因是
 *   ① 线上库缺 MATCHING:SELF 授权；② 该入口指向的是 HR 的 /matching/execute（要 MATCHING:EXECUTE）。
 *
 * 因此本条测试锁三件事：
 *   1. 入口三件套（路由 / 路径权限规则 / 侧边栏）必须同时存在且**口径一致**；
 *   2. 员工路径不得与 HR 的 /matching/execute 混用；
 *   3. 闸门不达标时**不隐藏入口**，而是给出常驻原因与跳转。
 */
const currentDir = dirname(fileURLToPath(import.meta.url))
/** 与 my-result 测试同口径：这里实际是 frontend/src */
const srcRoot = resolve(currentDir, '../../..')
const read = (...segments) => readFileSync(join(srcRoot, ...segments), 'utf8')

const matchingRoute = read('router/modules/matching.ts')
const sidebarConfig = read('config/sidebar-menu.ts')
const page = read('views/matching/self-execute/index.vue')

/* ---------- 1. 路由注册 ---------- */
assert.equal(matchingRoute.includes("path: 'self-execute'"), true, '缺少 /matching/self-execute 路由')
assert.equal(
  matchingRoute.includes('@/views/matching/self-execute/index.vue'),
  true,
  '路由未指向员工自助发起匹配页',
)
assert.equal(existsSync(join(currentDir, 'index.vue')), true, '自助发起匹配页文件缺失')
assert.equal(existsSync(join(currentDir, 'gate.ts')), true, '准入闸门纯逻辑文件缺失')

/* ---------- 2. 路由 meta 必须由 MATCHING:SELF 收口（不是 HR 的 MATCHING:EXECUTE） ---------- */
assert.equal(
  /path: 'self-execute',[\s\S]{0,300}?requiredPermissions: \['MATCHING:SELF'\]/.test(matchingRoute),
  true,
  '自助发起匹配的路由 meta 必须要求 MATCHING:SELF，否则员工会被守卫拦成 403',
)

/* ---------- 3. 路径规则：直接调用真实实现，不做字符串比对 ---------- */
assert.equal(
  findRequiredPathPermission('/matching/self-execute'),
  'MATCHING:SELF',
  '/matching/self-execute 必须由 MATCHING:SELF 收口',
)
// 顺序敏感：新规则不得影响相邻规则（execute 最具体 → 先匹配；my-result 是另一码；tasks 落兜底）
assert.equal(findRequiredPathPermission('/matching/execute'), 'MATCHING:EXECUTE')
assert.equal(findRequiredPathPermission('/matching/my-result'), 'NOTIFICATION:SELF')
assert.equal(findRequiredPathPermission('/matching/tasks'), 'MATCHING:READ')

/* ---------- 4. 侧边栏独立入口 ---------- */
assert.equal(
  sidebarConfig.includes("path: '/matching/self-execute'"),
  true,
  '侧边栏缺少员工自助发起匹配入口',
)
assert.equal(
  /path: '\/matching\/self-execute', permission: 'MATCHING:SELF', roles: \['EMPLOYEE'\]/.test(sidebarConfig),
  true,
  '侧边栏入口必须限定 EMPLOYEE 且要求 MATCHING:SELF',
)
/** 模块标题落点 = 过滤后可见子项的第一个，因此顺序决定员工点「人岗匹配」进哪一页 */
const matchingModuleSlice = sidebarConfig.slice(sidebarConfig.indexOf("key: 'matching'"))
assert.ok(
  matchingModuleSlice.indexOf("path: '/matching/self-execute'")
    < matchingModuleSlice.indexOf("path: '/matching/my-result'"),
  '自助发起匹配应排在「我的匹配结果」之前（员工进「人岗匹配」先看到发起入口）',
)

/* ---------- 5. 单人单岗 + 人员固定本人 ---------- */
assert.equal(
  page.includes('executeSelfMatching([selectedPostId.value])'),
  true,
  '提交必须以单元素岗位数组调用（后端契约 postIds 不变）',
)
assert.equal(page.includes('multiple'), false, '岗位选择必须单选，不得出现多选控件')
assert.equal(
  /executeSelfMatching\(\[[^\]]*emp/.test(page),
  false,
  '员工自助页不得把 empId 传进请求（人员范围由服务端按登录身份固定）',
)

/* ---------- 6. 闸门不达标不隐藏入口：常驻说明 + 跳转补齐 ---------- */
assert.equal(page.includes('resolveApplyGate'), true, '页面必须走统一的准入闸门判定')
assert.equal(
  /v-if="gateLoaded && !canApply"/.test(page),
  true,
  '闸门不达标时应渲染常驻提示（入口常驻可见），而不是把入口藏掉',
)
assert.equal(page.includes('el-alert'), true, '原因必须用常驻 el-alert 展示，不能只用 toast')
assert.equal(
  page.includes('resolveApiErrorMessage'),
  true,
  '失败文案必须归一化，英文技术文案不得透出',
)

/* ---------- 7. 只读页不得再承担发起动作（用户明确要求） ---------- */
const myResultPage = read('views/matching/my-result/index.vue')
assert.equal(
  myResultPage.includes('executeSelfMatching'),
  false,
  '「我的匹配结果」不得再出现发起匹配动作',
)
assert.equal(
  myResultPage.includes('/matching/self-execute'),
  true,
  '「我的匹配结果」应指引员工到独立的自助发起匹配页',
)

console.log('self execute route tests passed')
