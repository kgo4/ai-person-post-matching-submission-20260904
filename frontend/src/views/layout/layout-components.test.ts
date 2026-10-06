import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stripSfcComments } from '@/utils/sfc-source'

const currentDir = dirname(fileURLToPath(import.meta.url))

/**
 * 契约断言一律看**剥掉注释后**的源码。
 *
 * 本组件的注释按约定要写清「为什么删掉某个东西」，于是注释里会自然出现
 * 「不再显示系统名『多源异构岗位与能力图谱』」这类句子 —— 而断言恰恰要求
 * 这串字不得出现。扫原文 = 注释越详尽测试越红，代码其实是对的（本文件踩过）。
 */
const layoutSource = stripSfcComments(readFileSync(join(currentDir, 'index.vue'), 'utf8'))

function componentSource(name: string) {
  const path = join(currentDir, 'components', name)
  expect(existsSync(path)).toBe(true)
  return stripSfcComments(readFileSync(path, 'utf8'))
}

describe('layout component boundaries', () => {
  it('composes navigation and topbar through dedicated components', () => {
    expect(layoutSource).toContain("import AppSidebar from './components/AppSidebar.vue'")
    expect(layoutSource).toContain("import AppTopbar from './components/AppTopbar.vue'")
    expect(layoutSource).toContain('<AppSidebar')
    expect(layoutSource).toContain('<AppTopbar')
    expect(layoutSource).not.toContain('class="sidebar-nav"')
    expect(layoutSource).not.toContain('class="layout-topbar"')
  })

  it('keeps component-local styles with the component that renders them', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    const topbarSource = componentSource('AppTopbar.vue')
    // 2026-09-04：任务面板与通知铃铛已合并为一个铃铛（面板内分「通知 / 任务」两区），
    // 独立的 TaskNotificationPanel.vue 已删除 —— 顶栏不再并排两个铃铛。
    const bellSource = componentSource('NotificationBell.vue')

    expect(sidebarSource).toContain('<style scoped>')
    expect(sidebarSource).toContain('.layout-sidebar')
    expect(topbarSource).toContain('<style scoped>')
    expect(topbarSource).toContain('.layout-topbar')
    expect(bellSource).toContain('<style scoped>')
    expect(bellSource).toContain('.notice-panel')
    // 合并后仍必须能渲染任务区，否则任务进度就没有入口了
    expect(bellSource).toContain('taskStore.tasks')
    expect(bellSource).not.toContain('TaskNotificationPanel')
  })

  it('顶栏只挂一个铃铛', () => {
    const topbarSource = componentSource('AppTopbar.vue')
    const bellUsages = topbarSource.match(/<NotificationBell/g) ?? []
    expect(bellUsages).toHaveLength(1)
    expect(topbarSource).not.toContain('TaskNotificationPanel')
  })

  /* ============ 顶栏/侧边栏精简（2026-09-04） ============
   * 一次性删掉三个"看着有用、实际没人用"的元素：
   * Realtime 徽标（纯装饰）、深浅模式切换（全局只有它设置 data-theme，
   * 且浅色是唯一实际在用的主题）、收起导航（宽屏下 200px 本就不占地方）。
   *
   * 注意：同一轮里被删掉的「左下角登录信息」已在第二轮**以不同形态回来**
   * （侧边栏底部身份区 + 退出登录），原因见下方「账号入口重新分工」一节。 */

  it('顶栏不再有 Realtime 徽标', () => {
    const topbarSource = componentSource('AppTopbar.vue')
    expect(topbarSource).not.toContain('Realtime')
    expect(topbarSource).not.toContain('layout-chip')
    // 只删模板不删 import 会留下未使用的 Lightning 图标
    expect(topbarSource).not.toMatch(/\bLightning\b/)
  })

  it('侧边栏不再有主题切换与收起导航', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    for (const dead of [
      'sidebar-theme-toggle',
      'sidebar-collapse-btn',
      'darkMode',
      'app-theme',
      '收起导航',
    ]) {
      expect(sidebarSource).not.toContain(dead)
    }
  })

  /* ============ 账号入口重新分工（2026-09-04 第二轮） ============
   * 上一轮把账号入口全部收进顶栏下拉，理由是「两处都能退出登录时，
   * 改密码/换头像就没人知道该去哪」——那条不变式**依然成立**，只是换了切法：
   *
   *   顶栏右上角 = 只有头像 → 点击进个人中心（没有下拉、没有登出）
   *   侧边栏底部 = 身份展示（头像/姓名/角色）+ 唯一退出登录入口
   *
   * 两个入口各管一件事、职责不重叠，仍是「同一件事只有一个入口」。
   * 因此断言的是**职责不重叠**，而不是"侧边栏不许有账号区"。 */
  it('账号入口职责不重叠：顶栏只进个人中心、侧边栏底部只退出登录', () => {
    const topbarSource = componentSource('AppTopbar.vue')
    const sidebarSource = componentSource('AppSidebar.vue')

    // 顶栏：头像 → 个人中心；且不得再出现登出（登出只由侧边栏负责）
    expect(topbarSource).toContain("router.push('/profile')")
    expect(topbarSource).not.toContain('logout')
    expect(topbarSource).not.toMatch(/\bSwitchButton\b/)

    // 侧边栏：有退出登录，且**不得**再放个人中心（否则又变成两处都能进）
    expect(sidebarSource).toContain('退出登录')
    expect(sidebarSource).toContain("defineEmits")
    expect(sidebarSource).toContain("command=\"logout\"")
    expect(sidebarSource).not.toContain("'/profile'")
  })

  it('退出登录事件由侧边栏向上抛给布局页', () => {
    // 事件换个组件抛就必须同步改绑定处，否则点了没反应（静默失效）
    expect(layoutSource).toContain('<AppSidebar')
    expect(layoutSource).toMatch(/<AppSidebar[^>]*@logout="handleLogout"/)
    expect(layoutSource).not.toMatch(/<AppTopbar[^>]*@logout/)
  })

  /* ============ 跨身份任务泄漏（2026-09-04 二次收口） ============
   * `task` store 持久化在 `localStorage['tasks']`，**登出并不会清理它**。
   * 于是上一个账号（HR）的匹配任务会在下一个身份（岗位体系管理员）登录时
   * 被 pinia 持久化插件原样恢复 —— 实测表现为：
   *   顶栏铃铛亮红点「1」、面板多出「任务」tab、里面写着
   *   「匹配完成，共处理 12 条记录」，点进去还因缺 MATCHING:READ 而 403。
   * 接口侧与写入侧早已收口，唯独**装载侧**漏了，所以这里钉住这次调用。
   * 若被删除，岗位管理员会再次「收到」HR 的匹配完成信息。 */
  it('进入系统时按当前身份裁剪任务列表（防跨身份任务泄漏）', () => {
    expect(layoutSource).toMatch(/taskStore\.retainVisible\(/)
    // 必须用当前账号的权限列表，而不是某个写死的角色判断
    expect(layoutSource).toMatch(/taskStore\.retainVisible\([^)]*permissions/)
  })

  it('侧边栏底部展示头像、姓名与角色', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    expect(sidebarSource).toContain('sidebar-account')
    expect(sidebarSource).toContain('sidebar-account__avatar')
    // 用户名与角色上下两行
    expect(sidebarSource).toContain('{{ userName }}')
    expect(sidebarSource).toContain('{{ userRoleLabel }}')
    // 角色文案必须走统一映射，不能自己写死一份
    expect(sidebarSource).toContain("from '@/utils/role-label'")
  })

  it('顶栏右上角只剩头像，不再显示姓名与角色', () => {
    const topbarSource = componentSource('AppTopbar.vue')
    expect(topbarSource).toContain('layout-avatar')
    expect(topbarSource).not.toContain('layout-user__copy')
    expect(topbarSource).not.toContain('userRole')
    // 除掉的元素不能留下未使用的 import
    expect(topbarSource).not.toMatch(/\bArrowDown\b/)
  })

  it('侧边栏顶部只显示图标与团队名，不显示系统名', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    expect(sidebarSource).toContain('sidebar-logo-icon')
    expect(sidebarSource).toContain('sidebar-logo-team')
    // 系统名已从侧边栏移除（200px 栏宽放不下 12 个汉字；登录页仍保留系统名）
    expect(sidebarSource).not.toContain('多源异构岗位与能力图谱')
    expect(sidebarSource).not.toContain('sidebar-logo-system')
    expect(sidebarSource).not.toContain('sidebar-logo-brand')
  })

  it('侧边栏支持再次点击当前模块收起子项', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    // 收起状态必须被记住：否则 activeModule 没变，子项会立刻又被算成展开
    expect(sidebarSource).toContain('manuallyCollapsed')
    expect(sidebarSource).toContain('isSubnavOpen')
    // 换模块时清掉收起记录，让新模块恢复默认展开
    expect(sidebarSource).toMatch(/watch\(/)
  })

  /* 终面通知（bizType=INTERVIEW）两端共用同一个 bizType，落点必须按身份分流：
   * 员工被送到 /matching/communication-interview 会因缺 MATCHING:READ 而**静默 403**，
   * 表面上就是"点了通知没反应"。这里把分流规则钉住。 */
  it('终面通知按身份分流落点：管理端去 HR 终面页，员工去本人能力画像', () => {
    const bellSource = componentSource('NotificationBell.vue')
    expect(bellSource).toContain("item.bizType === 'INTERVIEW'")
    expect(bellSource).toContain("hasPermission('EMPLOYEE:READ')")
    expect(bellSource).toContain("'/matching/communication-interview'")
    expect(bellSource).toContain("'/employee/ability-profile'")
  })

  /* 匹配结果通知（bizType=MATCHING_RECORD）同样两端共用一个 bizType，落点必须按身份分流。
   * 回归风险：原实现无条件送 /matching/my-result —— 员工侧正确，但管理端一旦收到同类通知
   * 就会打开员工版只读页；他持有 NOTIFICATION:SELF 能过守卫，而后端 isSelfOnlyCaller()
   * 判定他不是「仅本人」调用者 → 走全量 page()，于是「我的匹配结果」里出现全员数据。 */
  it('匹配结果通知按身份分流落点，绝不让管理端落进员工只读页', () => {
    const bellSource = componentSource('NotificationBell.vue')
    expect(bellSource).toContain("item.bizType === 'MATCHING_RECORD'")
    // 判据必须与后端 isSelfOnlyCaller() 同源（同一个权限码），否则两端对去向的理解会漂移
    expect(bellSource).toContain("hasPermission('MATCHING:READ')")
    expect(bellSource).toContain("'/matching/result'")
    expect(bellSource).toContain("'/matching/my-result'")
    // 钉住「不再无条件指向员工只读页」这件事本身
    expect(bellSource).not.toContain(
      "if (item.bizType === 'MATCHING_RECORD') return '/matching/my-result'",
    )
  })
})
