import { computed, ref, type Ref } from 'vue'
import { filterSidebarModules, getSidebarModules, type SidebarModule } from '@/config/sidebar-menu'

/**
 * 顶栏「功能搜索」。
 *
 * 只搜**功能（页面）**，不搜业务数据（员工 / 岗位 / 岗位记录等）。
 * 理由是职责边界：顶栏搜索是「我想去哪个页面」的导航动作，
 * 与「我要找某个人/某个岗位」是两件事 —— 后者应该在各业务列表页里做（那里有筛选条件、
 * 分页、字段语义），塞进顶栏只能给出一条条无语境的记录，点进去还要再找一次。
 *
 * 数据源就是侧边栏菜单本身，经 `filterSidebarModules` 按「当前角色 + 权限码」过滤，
 * 因此**搜得到的必然进得去**：能出现在结果里的条目，就是该角色侧边栏上真实可见的入口，
 * 不会出现"搜到点进去 403"。新增页面只需在 `config/sidebar-menu.ts` 加一条，
 * 搜索索引自动跟上，不存在第二份需要同步维护的清单。
 */

export interface FeatureSearchItem {
  /** 所属模块 key，用于排序与去重 */
  moduleKey: string
  /** 所属模块名（结果里作为分类标签展示） */
  moduleLabel: string
  /** 页面（菜单子项）名 */
  label: string
  /** 该页面的路由地址 */
  path: string
}

/** 匹配得分：数值越小越靠前 */
const SCORE_EXACT = 0
const SCORE_PREFIX = 1
const SCORE_LABEL = 2
const SCORE_MODULE = 3

/** 归一化：忽略大小写与空白，避免用户多打一个空格就搜不到 */
function normalize(value: string | null | undefined): string {
  return String(value ?? '').toLowerCase().replace(/\s+/g, '')
}

/**
 * 构建当前角色可用的功能索引。
 *
 * @param modules     菜单定义（默认取 `getSidebarModules()`）
 * @param permissions 当前用户权限码
 * @param role        当前用户主角色（不含 ROLE_ 前缀亦可）
 */
export function buildFeatureIndex(
  modules: SidebarModule[],
  permissions: string[] = [],
  role = '',
): FeatureSearchItem[] {
  const visible = filterSidebarModules(modules, role, permissions)
  const index: FeatureSearchItem[] = []
  const seen = new Set<string>()

  for (const module of visible) {
    for (const child of module.children) {
      // 同一路径可能被多个子项指向（如 HR 与员工共用 /matching/gap-diagnosis），
      // 但过滤后同一角色下不会重复；`label + path` 去重兜住配置里手写重复的情况。
      const key = `${child.path}::${child.label}`
      if (seen.has(key)) continue
      seen.add(key)
      index.push({
        moduleKey: module.key,
        moduleLabel: module.label,
        label: child.label,
        path: child.path,
      })
    }
  }

  return index
}

/**
 * 在索引中检索功能。
 *
 * 排序：完全相等 → 前缀命中 → 页面名包含 → 模块名包含；同档按名称稳定排序。
 * 这样输入「匹配」时，叫「匹配结果」的页面会排在只是同属「人岗匹配」模块的页面之前。
 */
export function searchFeatures(
  index: FeatureSearchItem[],
  keyword: string,
  limit = 8,
): FeatureSearchItem[] {
  const query = normalize(keyword)
  if (!query) return []

  const scored: Array<{ item: FeatureSearchItem; score: number }> = []
  for (const item of index) {
    const label = normalize(item.label)
    const moduleLabel = normalize(item.moduleLabel)

    let score = -1
    if (label === query) score = SCORE_EXACT
    else if (label.startsWith(query)) score = SCORE_PREFIX
    else if (label.includes(query)) score = SCORE_LABEL
    else if (moduleLabel.includes(query)) score = SCORE_MODULE

    if (score >= 0) scored.push({ item, score })
  }

  scored.sort(
    (a, b) =>
      a.score - b.score ||
      a.item.label.localeCompare(b.item.label, 'zh-Hans-CN') ||
      a.item.path.localeCompare(b.item.path),
  )

  return scored.slice(0, Math.max(0, limit)).map(entry => entry.item)
}

/**
 * 顶栏搜索面板状态。
 *
 * 检索是纯本地的（菜单索引已随登录态就绪），没有任何请求，因此**没有 loading 态** ——
 * 本地过滤还转圈只是一帧的闪烁，反而像卡了一下。
 */
export function useFeatureSearch(
  role: Ref<string>,
  permissions: Ref<string[]>,
  options: { limit?: number; closeDelayMs?: number } = {},
) {
  const limit = options.limit ?? 8
  const closeDelayMs = options.closeDelayMs ?? 180

  const keyword = ref('')
  const results = ref<FeatureSearchItem[]>([])
  const showPanel = ref(false)

  const index = computed(() => buildFeatureIndex(getSidebarModules(), permissions.value, role.value))

  let closeTimer: ReturnType<typeof setTimeout> | null = null

  function cancelClose() {
    if (closeTimer) clearTimeout(closeTimer)
    closeTimer = null
  }

  function onSearchInput() {
    cancelClose()
    const query = keyword.value.trim()
    if (!query) {
      results.value = []
      showPanel.value = false
      return
    }
    results.value = searchFeatures(index.value, query, limit)
    showPanel.value = true
  }

  function clearResults() {
    cancelClose()
    results.value = []
    showPanel.value = false
    keyword.value = ''
  }

  /**
   * 失焦关闭要延迟：点击结果项时 input 先失焦、click 才触发，
   * 立即关闭会把这一下点击吃掉（点结果没反应）。
   */
  function closeSearchPanel() {
    cancelClose()
    closeTimer = setTimeout(() => {
      showPanel.value = false
      closeTimer = null
    }, closeDelayMs)
  }

  return {
    keyword,
    results,
    showPanel,
    onSearchInput,
    clearResults,
    closeSearchPanel,
  }
}
