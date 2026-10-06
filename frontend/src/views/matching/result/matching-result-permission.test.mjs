import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * 匹配结果操作按钮的权限收口契约。
 *
 * 线上问题：`PUT /api/matching/record/{id}` 返回 403 —— 前端无条件渲染了
 * 「修改结果 / 推送 / 锁定」，而实际登录角色没有对应权限。后端判定为
 * MATCHING:APPROVE 或 MATCHING:EXECUTE 任一，前端必须用同一口径收口。
 */
const here = dirname(fileURLToPath(import.meta.url))
const table = readFileSync(join(here, 'components/MatchingResultTable.vue'), 'utf8')

assert.match(
  table,
  /hasAnyPermission\(\['MATCHING:APPROVE', 'MATCHING:EXECUTE'\]\)/,
  '应按 MATCHING:APPROVE 或 MATCHING:EXECUTE 判定（与后端 hasAnyAuthority 一致）',
)
assert.match(table, /v-if="canOperateResult"/, '修改/推送/锁定按钮必须用 canOperateResult 收口，不得无条件渲染')

// 「发起审批」入口会先请求 GET /api/system/user/page（要求 USER:MANAGE），
// HR 必然 403；且匹配结果已不再以审批为前置条件，入口应保持移除状态。
assert.equal(table.includes("emit('approval'"), false, '不应再渲染「发起审批」入口')

// 结果页也不应再引用已删除的审批 composable / 弹窗
const resultPage = readFileSync(join(here, 'index.vue'), 'utf8')
assert.equal(resultPage.includes('useResultApproval'), false, '结果页不应再引用 useResultApproval')

// 匹配分析（原驾驶舱，2026-09-04 并入工作台）不得请求连通性自检接口
// （/api/test/** 要求 AI:CONFIG，业务角色必然 403）
const cockpit = readFileSync(join(here, '../../workbench/components/WorkbenchCockpit.vue'), 'utf8')
assert.equal(cockpit.includes('testAll'), false, '匹配分析不应再请求 /api/test/all')

// 匹配详情（从匹配结果页点入）不得再有「生成学习路径」入口：
// 学习路径属员工侧能力成长链路，HR 越权为员工造计划会绕过学习成果复核。
// 断言处理函数与 API 调用而非按钮文案 —— 文案在注释里出现（说明为何下线）不应被判为回归。
const detailPage = readFileSync(join(here, '../detail/index.vue'), 'utf8')
assert.equal(detailPage.includes('handleGenerateLearningPath'), false, '匹配详情不应再保留生成学习路径的处理函数')
assert.equal(detailPage.includes('generateLearningPath('), false, '匹配详情不应再调用 generateLearningPath')

console.log('matching result permission tests passed')
