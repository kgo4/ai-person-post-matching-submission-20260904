import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import systemRoutes from './modules/system'
import employeeRoutes from './modules/employee'
import postRoutes from './modules/post'
import matchingRoutes from './modules/matching'
import knowledgeRoutes from './modules/knowledge'
import { findRequiredPathPermission } from './path-permissions'
import { filterSidebarModules, getSidebarModules } from '@/config/sidebar-menu'

/**
 * 「三层同源」契约测试。
 *
 * 同一份权限口径在前端有**三层**载体，历史上已经漂移过两次：
 *   ① 侧边栏 `config/sidebar-menu.ts`（菜单显隐）
 *   ② 路由守卫 `router/path-permissions.ts`（按路径拦截）
 *   ③ **路由 meta.requiredPermissions**（守卫里更早执行的一道检查）
 *
 * 第二次漂移就发生在这里：2026-09-04 把知识资产/图谱从 `AI:CONFIG` 改挂 `POST:MANAGE` 时，
 * 只改了 ①②，漏了 ③ —— 结果是**岗位体系管理员点「上传并索引资料」被守卫拦到 403**，
 * 而他其实持有正确权限码。这类 bug 不报错、不留日志，只有真人点进去才会发现。
 *
 * 本测试遍历所有路由，把 ②③ 做交叉校验：**两处声明的权限码必须有交集**。
 * 完全相等不强求（② 是"路径级"、③ 是"路由级"，粒度可以更细），但**不允许不相交** ——
 * 不相交就意味着"守卫按 A 放行、守卫又按 B 拒绝"，其中一条必然挡住正确的人。
 */

/** 把路由树拍平成「绝对路径 → meta.requiredPermissions」 */
function flatten(routes: RouteRecordRaw[], parentPath = ''): Array<{ path: string; required: string[] }> {
  const out: Array<{ path: string; required: string[] }> = []
  for (const route of routes) {
    const raw = route.path ?? ''
    const path = raw.startsWith('/')
      ? raw
      : `${parentPath.replace(/\/$/, '')}/${raw}`.replace(/\/{2,}/g, '/')
    const required = (route.meta?.requiredPermissions as string[] | undefined) ?? []
    if (required.length > 0 && path) out.push({ path, required })
    if (route.children?.length) out.push(...flatten(route.children, path))
  }
  return out
}

/**
 * 各模块的默认导出**既有数组也有单个路由**（system.ts 就是一个 RouteRecordRaw），
 * 这里统一归一为数组再遍历。
 */
function toArray(module: RouteRecordRaw | RouteRecordRaw[]): RouteRecordRaw[] {
  return Array.isArray(module) ? module : [module]
}

const ALL_ROUTES = [
  ...toArray(systemRoutes),
  ...toArray(employeeRoutes),
  ...toArray(postRoutes),
  ...toArray(matchingRoutes),
  ...toArray(knowledgeRoutes),
]

describe('权限三层同源（path-permissions ↔ 路由 meta）', () => {
  it('路由 meta 声明的权限码与 path-permissions 的规则必须有交集', () => {
    for (const { path, required } of flatten(ALL_ROUTES)) {
      const pathRule = findRequiredPathPermission(path)
      if (!pathRule) continue // 该路径不在路径规则表里，由 meta 单独收口
      const pathList = Array.isArray(pathRule) ? pathRule : [pathRule]
      const overlap = required.some(code => pathList.includes(code))
      expect(
        overlap,
        `路径 ${path} 的权限口径不一致：路由 meta 要求 [${required.join(', ')}]，`
        + `而 path-permissions 要求 [${pathList.join(', ')}] —— 二者不相交时，`
        + `守卫会先用 meta 拦下本该放行的角色（2026-09-04 的知识资产 403 就是这样来的）`,
      ).toBe(true)
    }
  })

  it('知识资产与图谱：三层都指向 POST:MANAGE（岗位体系管理员），且人员域额外放开 HR', () => {
    const expectations: Record<string, string[]> = {
      '/rag/knowledge': ['POST:MANAGE'],
      '/rag/logs': ['POST:MANAGE'],
      // 图谱同一页面两个域：岗位管理员与 HR 都能进，进页面后按角色锁域
      // （/kg/** 的路径级规则是粗粒度的两码并集；更细的差异由路由 meta 表达，
      //   例如 /kg/snapshot 只给 POST:MANAGE —— 见下一条用例）
      '/kg/workbench': ['POST:MANAGE', 'ASSESSMENT:MANAGE'],
    }
    for (const [path, codes] of Object.entries(expectations)) {
      const rule = findRequiredPathPermission(path)
      const ruleList = Array.isArray(rule) ? rule : rule ? [rule] : []
      expect(ruleList, `${path} 的路径规则应为 [${codes.join(', ')}]`).toEqual(codes)
    }
  })

  it('图谱运维与操作日志：路由 meta 表达比路径规则更细的收口', () => {
    const metaByPath = new Map(flatten(ALL_ROUTES).map(item => [item.path, item.required]))
    // 图谱快照属运维：只给岗位体系管理员，HR 不可见（路径级规则较粗，由 meta 收紧）
    expect(metaByPath.get('/kg/snapshot'), '图谱快照应只对 POST:MANAGE 开放').toEqual(['POST:MANAGE'])
    // 操作日志：曾因 meta(AUDIT:READ) 与路径兜底(ASSESSMENT:MANAGE) 不相交而**谁都进不去**
    expect(metaByPath.get('/system/operation-log')).toEqual(['AUDIT:READ'])
    expect(findRequiredPathPermission('/system/operation-log')).toBe('AUDIT:READ')
  })

  it('运行审计（2026-09-04 新增）：三层必须都是 AUDIT:READ', () => {
    // 这个页面同样踩过 /system/* 兜底的坑：路径规则若漏写，会被
    // /^\/system\// 的 ASSESSMENT:MANAGE 兜住，与 meta 的 AUDIT:READ 不相交
    // → 持有 AUDIT:READ 的平台管理员反而进不去（与操作日志当年一模一样）。
    const metaByPath = new Map(flatten(ALL_ROUTES).map(item => [item.path, item.required]))
    expect(metaByPath.get('/system/runtime-audit'), '路由 meta 必须是 AUDIT:READ').toEqual(['AUDIT:READ'])
    expect(findRequiredPathPermission('/system/runtime-audit'), '路径规则必须是 AUDIT:READ').toBe('AUDIT:READ')

    // 第三层载体（侧边栏）：只持有 AUDIT:READ 的平台管理员必须能在菜单里看到它，
    // 否则「页可达但没有入口」，等于用户看不到这个功能。
    const platformAdminMenu = filterSidebarModules(
      getSidebarModules(),
      ['PLATFORM_ADMIN'],
      ['USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG'],
    ).flatMap(module => module.children.map(child => child.path))
    expect(platformAdminMenu).toContain('/system/runtime-audit')
    expect(platformAdminMenu).toContain('/system/operation-log')

    // 反例：没有 AUDIT:READ 的角色不该看到审计入口（避免把审计页暴露给业务角色）
    const hrMenu = filterSidebarModules(
      getSidebarModules(),
      ['HR_SPECIALIST'],
      ['ASSESSMENT:MANAGE', 'MATCHING:READ'],
    ).flatMap(module => module.children.map(child => child.path))
    expect(hrMenu).not.toContain('/system/runtime-audit')
    expect(hrMenu).not.toContain('/system/operation-log')
  })

  it('平台侧入口仍由 AI:CONFIG 收口，不得被业务权限码顶替', () => {
    // Agent 记忆与模型配置属平台 AI 基础设施，只有 PLATFORM_ADMIN 持有 AI:CONFIG
    for (const path of ['/system/agent-memory', '/system/ai-model-config']) {
      const rule = findRequiredPathPermission(path)
      expect(rule, `${path} 必须由 AI:CONFIG 收口`).toBe('AI:CONFIG')
    }
  })
})
