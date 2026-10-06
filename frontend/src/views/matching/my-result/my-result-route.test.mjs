import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 员工「我的匹配结果」业务约束回归测试。
 *
 * 管理端 /matching/result 控制台含审批、锁定、改分、导出等动作，
 * 员工（数据范围 = 仅本人）调用这些接口一律 403，因此员工入口必须指向只读页，
 * 且只读页不得引用任何管理端动作接口。
 */
const currentDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(currentDir, '../../..')
const read = (...segments) => readFileSync(join(frontendRoot, ...segments), 'utf8')

const matchingRoute = read('router/modules/matching.ts')
const sidebarConfig = read('config/sidebar-menu.ts')
const page = read('views/matching/my-result/index.vue')

// 1. 路由注册
assert.equal(matchingRoute.includes("path: 'my-result'"), true, '缺少 /matching/my-result 路由')
assert.equal(
  matchingRoute.includes('@/views/matching/my-result/index.vue'),
  true,
  '路由未指向员工只读页组件',
)
assert.equal(existsSync(join(currentDir, 'index.vue')), true, '员工只读页文件缺失')

// 2. 管理端控制台只对管理端角色开放
assert.equal(
  /path: 'result',[\s\S]{0,400}?requiredRoles: MANAGEMENT_ROLES/.test(matchingRoute),
  true,
  '管理端匹配结果控制台缺少 requiredRoles 限制',
)

// 3. 员工侧边栏入口指向只读页，不指向管理端控制台
assert.equal(sidebarConfig.includes("path: '/matching/my-result'"), true, '侧边栏缺少员工只读入口')
assert.equal(
  /roles: \['EMPLOYEE'\]/.test(sidebarConfig) &&
    !/我的匹配结果', path: '\/matching\/result'/.test(sidebarConfig),
  true,
  '员工“我的匹配结果”仍指向管理端控制台',
)

// 4. 只读页不得引用管理端动作接口
for (const forbidden of ['approve', 'lockRecord', 'unlockRecord', 'modifyRecord', 'deleteRecord', 'exportMatchResults']) {
  assert.equal(page.includes(forbidden), false, `员工只读页不应引用管理端动作：${forbidden}`)
}
// 必须按“仅本人”口径取数：页码与筛选走 pageRecords，人员范围由服务端固定
assert.equal(page.includes('pageRecords'), true, '员工只读页应通过 pageRecords 取数')
assert.equal(page.includes('empId:'), false, '员工只读页不得向服务端传 empId')

// 5. 【2026-09-04】「发起匹配」已从本页移除，独立为 /matching/self-execute。
// 回归原因：员工在「我的匹配结果」里点发起匹配会撞上 HR 的 MATCHING:EXECUTE 而被 403，
// 且入口藏在弹窗里根本找不到。本页从此只负责呈现已推送结果，不得再出现任何产出动作。
// 注意：只查「产出动作的代码」，不查「发起匹配」这四个字 ——
// hero 里必须留一句指引（去「人岗匹配 → 发起匹配」提交），否则移走入口后员工更找不到路。
for (const forbidden of [
  'executeSelfMatching',
  'getLatestCapabilityReport',
  'getAssessmentProfile',
  'openApplyDialog',
  'submitApply',
  'applyVisible',
  'selectedPostId',
  'el-dialog',
]) {
  assert.equal(page.includes(forbidden), false, `员工只读页不应再出现发起匹配相关代码：${forbidden}`)
}
// 页面必须给出明确指引，告诉员工去哪发起（入口已迁到侧边栏的独立页面）
assert.equal(page.includes('/matching/self-execute'), true, '只读页应指引员工到独立的自助发起匹配页')
// 只读页不得出现多选岗位控件（原本属于发起匹配弹窗）
assert.equal(page.includes('multiple'), false, '员工只读页不应出现多选岗位')

console.log('my-result employee view tests passed')
