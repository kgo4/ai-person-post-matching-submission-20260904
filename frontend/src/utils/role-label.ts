/**
 * 角色码 → 中文名。全系统**唯一**的角色展示文案来源。
 *
 * 为什么单独成一个模块：这段映射此前在 `views/error/403.vue` 与
 * `views/workbench/RoleWorkbench.vue` 里各写了一份，侧边栏底部要显示用户角色时
 * 就会出现第三份。四角色的中文名一旦要改（或角色再合并），三处不同步就会
 * 出现「工作台写 HR、侧边栏写 HR_SPECIALIST」这种同一屏两套叫法。
 *
 * 纯函数、不依赖 Vue，可直接在 node 直跑的单测里断言。
 */

/** 角色码 → 中文名。键为**去掉 ROLE_ 前缀并大写**后的角色码。 */
export const ROLE_LABELS: Record<string, string> = {
  EMPLOYEE: '员工',
  HR_SPECIALIST: 'HR',
  JOB_ARCHITECT: '岗位体系管理员',
  PLATFORM_ADMIN: '平台管理员',
  // 超级管理员：可访问所有角色的所有功能（角色码常量见 utils/super-admin.ts）
  SUPER_ADMIN: '超级管理员',
  // 旧码保留映射：V162 已把 AI_CONFIG_MANAGER / SECURITY_ADMIN 合并为 PLATFORM_ADMIN，
  // 过渡期内历史账号仍要看到可读的角色名，而不是一串英文码。
  AI_CONFIG_MANAGER: '平台管理员（角色已合并）',
  SECURITY_ADMIN: '平台管理员（角色已合并）',
}

/**
 * 归一化角色码：剥掉 `ROLE_` 前缀并转大写。
 *
 * store 里的 roles 其实已经归一过一次，但组件拿到的可能是原始值
 * （如后端直出的 `ROLE_HR_SPECIALIST`），所以统一在这里再收敛一次，
 * 避免调用方各自记得要先处理。
 */
export function normalizeRoleCode(role?: string | null): string {
  // 顺序不能反：必须先 trim 再剥前缀。
  // 若先剥前缀，`'  ROLE_EMPLOYEE'` 会因为开头的空格匹配不上 `^ROLE_`
  // 而把带前缀的原值整个留下（被单测抓到过）。
  return (role || '').trim().replace(/^ROLE_/, '').toUpperCase()
}

/**
 * 取角色中文名。
 *
 * 未登记的码**原样展示**而不是吞成「未知角色」：新角色上线时，
 * 看到 `DATA_STEWARD` 比看到「未知角色」更容易定位问题。
 */
export function roleLabelOf(role?: string | null, fallback = '未知角色'): string {
  const code = normalizeRoleCode(role)
  if (!code) return fallback
  return ROLE_LABELS[code] || code
}

/**
 * 多角色场景：`roles` 是数组且首位是主角色（后端按优先级下发）。
 * 取首位即可；全部为空时给 fallback。
 */
export function primaryRoleLabel(roles?: string[] | null, fallback = '未知角色'): string {
  const primary = roles?.find(role => normalizeRoleCode(role))
  return roleLabelOf(primary, fallback)
}
