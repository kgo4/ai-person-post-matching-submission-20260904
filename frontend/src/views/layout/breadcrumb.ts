/**
 * 顶栏面包屑解析。
 *
 * 独立成纯逻辑模块的原因：面包屑只依赖「当前路径 + 当前模块 + 该模块的子项」，
 * 不依赖 Vue 运行时，可以在 node 直跑的单测里断言各种路径的解析结果。
 *
 * 职责边界：**不推断模块归属**。当前模块由 layout/index.vue 的 activeModule 统一
 * 解析（其中包含 contest / knowledge-assets / ai-governance 等跨前缀的特殊映射），
 * 这里只做「模块 → 子项」的二级解析，避免同一套归属规则在两处各写一遍。
 */

export interface BreadcrumbChild {
  label: string
  path: string
}

export interface BreadcrumbModule {
  label: string
  path: string
  children: BreadcrumbChild[]
}

export interface BreadcrumbItem {
  label: string
  /** 末级不带路径（当前页不可点），其余带路径 */
  path?: string
}

/** 路径匹配：完全相等或作为父路径前缀 */
function matches(current: string, target: string): boolean {
  return current === target || current.startsWith(`${target}/`)
}

/**
 * 在子项里找最贴合当前路径的一项。
 *
 * 取「路径最长」的那项：同一模块下存在形如 /post/list 与 /post/list/detail 的
 * 父子路径时，应显示更具体的那个，而不是定义顺序里更靠前的那个。
 */
function findBestChild(module: BreadcrumbModule, current: string): BreadcrumbChild | null {
  let best: BreadcrumbChild | null = null
  for (const child of module.children) {
    if (!matches(current, child.path)) continue
    if (!best || child.path.length > best.path.length) best = child
  }
  return best
}

/**
 * 构建面包屑。
 *
 * @param currentPath 当前路由 path
 * @param module      当前所属模块（由 layout 统一解析）
 * @param routeTitle  当前路由 meta.title，作为末级兜底文案
 * @returns 面包屑项，至少一级
 */
export function buildBreadcrumb(
  currentPath: string,
  module: BreadcrumbModule | null | undefined,
  routeTitle?: string,
): BreadcrumbItem[] {
  const path = currentPath || '/'
  if (!module) {
    // 没有归属模块时（如独立全屏页）只展示路由标题，标题也没有则回落工作台
    return routeTitle ? [{ label: routeTitle }] : [{ label: '工作台', path: '/workbench' }]
  }

  const child = findBestChild(module, path)
  if (child) {
    // 子项与模块同名同路径（如 首页 / 首页）时不重复展示，只保留一级
    if (child.path === module.path && child.label === module.label) {
      return [{ label: module.label, path: module.path }]
    }
    return [{ label: module.label, path: module.path }, { label: child.label }]
  }

  if (routeTitle && routeTitle !== module.label) {
    return [{ label: module.label, path: module.path }, { label: routeTitle }]
  }
  return [{ label: module.label, path: module.path }]
}
