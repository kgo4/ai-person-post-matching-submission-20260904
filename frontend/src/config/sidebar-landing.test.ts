import { describe, expect, it } from 'vitest'
import { filterSidebarModules, getSidebarModules } from './sidebar-menu'
import { isAssessmentProfilePath, satisfiesPathPermission } from '@/router/path-permissions'

/** 各角色实际持有的权限码（V147 + V162 合并 AI 管理员/权限管理员为 PLATFORM_ADMIN） */
const ROLE_PERMISSIONS: Record<string, string[]> = {
  // MATCHING:SELF 为员工自助发起匹配（2026-09-04 补授，见 sql/hotfix_20260904_grant_matching_self.sql）
  EMPLOYEE: ['ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'LEARNING:SELF', 'MATCHING:SELF'],
  HR_SPECIALIST: [
    'ASSESSMENT:SELF',
    'NOTIFICATION:SELF',
    'EMPLOYEE:READ',
    'ASSESSMENT:MANAGE',
    'MATCHING:READ',
    'MATCHING:EXECUTE',
    'MATCHING:APPROVE',
    'MATCHING:CONFIG',
    'POST:READ',
  ],
  // 岗位体系管理员：岗位体系 + 知识资产/图谱（2026-09-04 起 /rag、/kg 改挂 POST:MANAGE）
  JOB_ARCHITECT: ['POST:READ', 'POST:MANAGE', 'POST:EVOLUTION'],
  // 原 AI_CONFIG_MANAGER + SECURITY_ADMIN 合并而来
  PLATFORM_ADMIN: ['USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG'],
}

const ALL_ROLES = Object.keys(ROLE_PERMISSIONS)
const MANAGEMENT_ROLES = ['HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN']

function menuFor(role: string) {
  return filterSidebarModules(getSidebarModules(), role, ROLE_PERMISSIONS[role] ?? [])
}

describe('侧边栏模块标题落点必须可达', () => {
  /**
   * 回归：AppSidebar 点击模块标题时跳转 module.path。
   * 若 module.path 指向一个「按 roles/permission 过滤后不属于本角色」的子项，
   * 路由守卫会判无权限并跳 /403，而 /403 复用 404 组件 —— 用户看到的是 404 页。
   * 因此模块 path 必须落在该角色可见的子项集合内。
   */
  it.each(ALL_ROLES)('%s 的每个模块标题都落到自己可见的子项', role => {
    const modules = menuFor(role)
    expect(modules.length).toBeGreaterThan(0)

    for (const module of modules) {
      const visiblePaths = module.children.map(child => child.path)
      expect(
        visiblePaths,
        `模块【${module.label}】的标题路径 ${module.path} 不在该角色可见子项内，点击会跳 403/404`,
      ).toContain(module.path)
    }
  })

  it('员工点「人岗匹配」模块标题应进入自助发起匹配，且绝不会落到 HR 的 /matching/execute', () => {
    const matching = menuFor('EMPLOYEE').find(module => module.key === 'matching')
    expect(matching).toBeDefined()
    // 2026-09-04：「发起匹配」独立成员工自助页并置于模块首位，模块标题落点随之改变
    expect(matching!.path).toBe('/matching/self-execute')
    expect(matching!.path).not.toBe('/matching/execute')
    // 兜底：员工侧任何可见入口都不得指向 HR 的管理端发起匹配页
    expect(matching!.children.map(child => child.path)).not.toContain('/matching/execute')
  })

  it('员工点「能力画像」「学习成长」模块标题应进入本人视图', () => {
    const employeeModule = menuFor('EMPLOYEE').find(module => module.key === 'employee')
    const learningModule = menuFor('EMPLOYEE').find(module => module.key === 'learning')
    expect(employeeModule!.path).toBe('/employee/ability-profile')
    // 2026-09-04：员工侧与 HR 侧拆分页面，员工落点为本人视图 /learning/my-path；
    // 不得回落到 HR 的学习路径工作台 /learning/path（那页第一步是「选人 + 搜人」）。
    expect(learningModule!.path).toBe('/learning/my-path')
    expect(learningModule!.path).not.toBe('/learning/path')
  })

  it('HR 点「人岗匹配」模块标题仍进入发起匹配（权限最高的主推入口）', () => {
    const matching = menuFor('HR_SPECIALIST').find(module => module.key === 'matching')
    expect(matching!.path).toBe('/matching/execute')
  })

  /**
   * 模块 path 需要与该角色可见子项集合保持一致，
   * 但不应改写为「第一个可点子项」以外的语义 —— 配置里声明的顺序即优先级。
   */
  it('模块标题取可见子项中的第一个（配置顺序即优先级）', () => {
    for (const role of ALL_ROLES) {
      for (const module of menuFor(role)) {
        expect(module.path).toBe(module.children[0].path)
      }
    }
  })

  /**
   * 回归：菜单里「可见」的每一个入口（含模块标题落点），点进去必须能通过路由守卫。
   * 之前两侧规则各自维护：侧边栏按子项 permission 显隐、路由守卫按 pathPermission 拦截，
   * 一旦不一致就会出现「入口显示了却进不去」（用户看到的是 403/404 页）。
   * 现在守卫规则收敛在 router/path-permissions.ts，这里用同一份规则做交叉校验。
   */
  it.each(ALL_ROLES)('%s 的所有可见菜单入口都能通过路由守卫', role => {
    const perms = ROLE_PERMISSIONS[role]
    for (const module of menuFor(role)) {
      for (const path of [module.path, ...module.children.map(child => child.path)]) {
        const assessmentBlocked =
          isAssessmentProfilePath(path) &&
          !(perms.includes('ASSESSMENT:SELF') || perms.includes('ASSESSMENT:MANAGE'))
        const guardOk = satisfiesPathPermission(path, perms)

        expect(
          !guardOk ? '路径权限不足' : assessmentBlocked ? '缺 ASSESSMENT:SELF/MANAGE' : null,
          `角色 ${role} 的菜单入口 ${path}（${module.label}）可见但会被路由守卫拦截`,
        ).toBeNull()
      }
    }
  })
})
