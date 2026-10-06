/**
 * 超级管理员：**可访问所有角色的所有功能**。
 *
 * 为什么单独抽一个模块而不是各页各写一份 `roles[0] === 'SUPER_ADMIN'`：
 * 角色判定散落是这套权限体系历史上反复出问题的根因（菜单显隐、路由守卫、
 * 页面锁域各有一套）。超管又是"必须处处成立"的角色，散着写一定会漏，
 * 而漏掉的表现是**静默失效**（菜单少一项、点进去 403），没有报错。
 *
 * ⚠️ 后端同名常量：`SystemAuthenticationPortAdapter.SUPER_ADMIN_ROLE_CODE`（值必须一致）。
 * 后端按该角色码**动态装载 sys_permission 全表权限**，所以前端只需在
 * 「按角色过滤」的地方放行，权限码判定本身会自然通过。
 *
 * 纯函数、不依赖 Vue，可直接在单测里断言。
 */

/** 超级管理员角色码 */
export const SUPER_ADMIN_ROLE = 'SUPER_ADMIN'

/**
 * 单个角色码是否超级管理员。
 *
 * 比 `normalizeRoleCode` 更宽松：**先大写再去 `ROLE_` 前缀**。
 * 共享的 `normalizeRoleCode` 是"先剥前缀再大写"，因此 `'role_super_admin'` 这种
 * 小写前缀写法它剥不掉（会得到 `ROLE_SUPER_ADMIN`）。超管判定"必须处处成立"，
 * 这里宁可多容忍一种写法，也不让它因为大小写而静默失效。
 */
export function isSuperAdminRole(role?: string | null): boolean {
  return (role || '').trim().toUpperCase().replace(/^ROLE_/, '') === SUPER_ADMIN_ROLE
}

/**
 * 角色集合里是否包含超级管理员。
 *
 * 同时接受字符串与数组：调用方有的只有主角色（`roles[0]`），有的拿得到全量 `roles`。
 * 传 `roles` 全量更准 —— 多角色账号的主角色未必是超管。
 */
export function isSuperAdmin(roles?: Array<string | null | undefined> | string | null): boolean {
  if (!roles) return false
  if (typeof roles === 'string') return isSuperAdminRole(roles)
  return roles.some((role) => isSuperAdminRole(role))
}

/**
 * 超级管理员**也看不到**的菜单路径。
 *
 * 超管的设计是"可访问所有角色的所有功能"，但「所有功能」指的是**已交付的功能**。
 * 知识图谱学习路径（`/learning/path-enhanced`）是一份**未完成**的页面：
 *   1. 它的 `empId` / `postId` 写死为 `1`（库里员工 id 是 45~54，`id=1` 根本不存在）；
 *   2. 它依赖的 `knowledge_domain` 表**没有任何种子数据** → 领域、知识点、掌握度全是空。
 * 所以点「生成路径」只会静默返回空数组（请求成功、提示成功、列表空）。
 *
 * 在功能补完之前，把它从超管的菜单里一并排除 —— 否则超管会以为"超管都点不动，
 * 那肯定是系统坏了"，而实际是这份功能还没做完。
 *
 * ⚠️ 只影响**菜单可见性**，路由与页面代码保持可用：数据就绪后把这个数组清空即可恢复。
 */
export const MENU_EXCLUDED_PATHS: readonly string[] = ['/learning/path-enhanced']

/** 给定路径是否被「菜单排除名单」屏蔽（超管同样不显示）。 */
export function isMenuExcluded(path?: string | null): boolean {
  if (!path) return false
  const normalized = path.trim()
  return MENU_EXCLUDED_PATHS.some((excluded) => normalized === excluded)
}
