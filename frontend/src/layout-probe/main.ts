/**
 * 布局版式校验探针（开发工具，不参与打包）。
 *
 * 为什么存在：`vue-tsc` 通过、`vite build` 成功、契约测试全绿，
 * **都证明不了版式是对的**（没有任何组件挂载测试环境）。侧边栏是 200px 定宽，
 * 长名字会不会把栏撑开、账号区有没有真的贴底、窄屏会不会塌 —— 只能实测。
 *
 * 做法：直接挂载 `views/layout/index.vue`，量**真实组件**的几何数字，
 * 而不是照着写一份会漂移的副本。登录态用 localStorage 预置
 * （user store 走 pinia-plugin-persistedstate，key = 'user'）。
 *
 * 用法：
 *   npx vite --port 5111
 *   chrome --headless=new --dump-dom --virtual-time-budget=8000 \
 *          --window-size=1440,900 "http://localhost:5111/layout-probe.html?case=short"
 *
 * 也可直接在浏览器打开该地址肉眼复核（点账号区应只弹「退出登录」，
 * 点顶栏头像应跳个人中心）。`--window-size` 与真实视口差 ~18px，
 * 测 768px 断点请用 768 与 786 两个值夹一下。
 *
 * case：short = 有头像 + 短名字；long = 无头像（首字回落）+ 12 汉字长名字。
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { createRouter, createWebHashHistory } from 'vue-router'
import pinia from '@/store'
import Layout from '@/views/layout/index.vue'
import { primaryRoleLabel } from '@/utils/role-label'
import { useTaskStore } from '@/store/modules/task'
// 必须导入：布局与两个组件全靠 --app-* 设计 token，缺了它量到的几何数字没有意义
import '@/assets/styles/global.css'
import './probe.css'

/** 1x1 透明 PNG：让 <img> 真的加载成功，避免 alt 文本干扰几何测量 */
const TINY_PNG =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=='

/** 12 个汉字：专门用来验证「长名字会不会把 200px 栏宽撑开」 */
const LONG_NAME = '欧阳娜娜娜娜娜娜娜娜'

const CASES: Record<string, Record<string, unknown>> = {
  /** 有头像 + 短名字（HR：真实权限含 MATCHING:READ，用作「有权限应能看到」的对照组） */
  short: {
    token: 'probe-token',
    userInfo: { id: 1, username: 'hr.demo', realName: '陈默', avatar: TINY_PNG, empId: null },
    roles: ['HR_SPECIALIST'],
    permissions: ['EMPLOYEE:READ', 'MATCHING:READ', 'MATCHING:EXECUTE', 'MATCHING:APPROVE'],
    empId: null,
    avatar: TINY_PNG,
  },
  /** 无头像（首字回落）+ 超长名字 */
  long: {
    token: 'probe-token',
    userInfo: { id: 2, username: 'emp.demo', realName: LONG_NAME, avatar: null, empId: null },
    roles: ['EMPLOYEE'],
    permissions: ['EMPLOYEE:READ'],
    empId: null,
    avatar: null,
  },
  /** 岗位体系管理员：只有 POST:READ / POST:MANAGE / POST:EVOLUTION，**没有 MATCHING:READ** */
  architect: {
    token: 'probe-token',
    userInfo: { id: 3, username: 'arch.demo', realName: '周衡', avatar: null, empId: null },
    roles: ['JOB_ARCHITECT'],
    permissions: ['POST:READ', 'POST:MANAGE', 'POST:EVOLUTION'],
    empId: null,
    avatar: null,
  },
}

/**
 * 上一个身份遗留的持久化任务快照。
 *
 * `task` store 的 persist key 就是 `tasks`，登出时**不会**被清理 ——
 * 于是同一浏览器换账号/换角色登录时，pinia 持久化插件会把它恢复回来。
 * 对岗位管理员而言，这会变成「看到 HR 的匹配完成信息、顶栏亮红点、点进去 403」。
 */
const RESIDUAL_TASKS = {
  tasks: [
    {
      id: 'mt-9f3a2c',
      type: 'matching',
      refId: 0,
      refName: '匹配任务 9f3a2c',
      status: 'completed',
      progress: 100,
      message: '匹配完成，共处理 12 条记录',
      startTime: Date.now() - 120_000,
      endTime: Date.now() - 30_000,
      notified: false,
    },
  ],
}

const params = new URLSearchParams(location.search)
const caseName = params.get('case') || 'short'
const state = CASES[caseName] || CASES.short

// 必须在任何 store 实例化之前落到 localStorage，否则持久化插件读不到
localStorage.setItem('user', JSON.stringify(state))
// `?residue=1` 模拟「上一个身份留下的持久化任务」，与身份解耦以便任意 case 复用
if (params.get('residue') === '1') {
  localStorage.setItem('tasks', JSON.stringify(RESIDUAL_TASKS))
} else {
  localStorage.removeItem('tasks')
}
localStorage.removeItem('matching-tasks')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    { path: '/workbench', name: 'workbench', component: { template: '<div>工作台占位</div>' } },
    { path: '/profile', name: 'profile', component: { template: '<div>个人中心占位</div>' } },
  ],
})

const app = createApp(Layout)
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

/**
 * 等待若干毫秒。
 *
 * 用 `setTimeout` 而**不是** `requestAnimationFrame`：headless 下 rAF 依赖
 * BeginFrame，会间歇性不触发 —— 表现为「这一组有报告、下一组没有」的 flaky，
 * 极难定位（本探针踩过，一度以为是选择器问题）。
 * Vue 的 DOM 更新走 microtask，setTimeout 之后必定已完成。
 */
function wait(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// 兜底：任何未捕获异常都写进报告，否则外部只看到「没报告」
for (const type of ['error', 'unhandledrejection'] as const) {
  window.addEventListener(type, event => {
    if (document.getElementById('probe-report')) return
    const detail =
      type === 'error'
        ? (event as ErrorEvent).message + '\n' + ((event as ErrorEvent).error?.stack || '')
        : String((event as PromiseRejectionEvent).reason?.stack || (event as PromiseRejectionEvent).reason)
    writeReport({ case: caseName, fatal: type, detail })
  })
}

/**
 * router.isReady() 之后再挂载，保证 useRoute() 拿到的不是初始的 / 空路由
 * （否则 activeModule 会回落到第一个模块，顶栏标题测不到真实值）。
 * 之后点开顶栏铃铛面板 —— 任务列表是 `v-if="panelVisible"` 才渲染的，
 * 不点开就量不到（「上一个身份的匹配任务是否泄漏给当前角色」正在那里）。
 */
router.isReady().then(async () => {
  app.mount('#probe-app')
  await wait(80)
  const bellBtn = document.querySelector('.notice-bell__btn') as HTMLElement | null
  bellBtn?.click()
  await wait(150)
  safeReport()
})

/**
 * 探针绝不能静默失败：一旦抛错而没写出报告，外部只看到「没报告」，
 * 会误以为是加载慢或选择器问题，白白浪费一轮排查（已踩过）。
 * 所以这里把异常本身也当成一种报告写出去。
 */
function safeReport() {
  try {
    reportGeometry()
  } catch (error) {
    const detail = error instanceof Error ? (error.stack || error.message) : String(error)
    writeReport({
      case: caseName,
      residue: params.get('residue') === '1',
      fatal: 'reportGeometry 抛出异常',
      detail,
    })
  }
}

function writeReport(payload: unknown) {
  const pre = document.createElement('pre')
  pre.id = 'probe-report'
  pre.textContent = `PROBE_REPORT_START\n${JSON.stringify(payload, null, 2)}\nPROBE_REPORT_END`
  document.body.appendChild(pre)
  const summary = (payload as { summary?: string }).summary
  document.title = summary ? `probe ${caseName} ${summary}` : `probe ${caseName} FATAL`
}

/* ---------------------------------------------------------------- 测量与判定 */

function reportGeometry() {
  const round = (n: number) => Math.round(n * 100) / 100
  const rect = (el: Element | null) => {
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: round(r.x), y: round(r.y), w: round(r.width), h: round(r.height), bottom: round(r.bottom), right: round(r.right) }
  }
  const q = (sel: string) => document.querySelector(sel)

  const sidebar = q('.layout-sidebar') as HTMLElement | null
  const sidebarStyle = sidebar ? getComputedStyle(sidebar) : null
  const account = q('.sidebar-account') as HTMLElement | null
  const accountTrigger = q('.sidebar-account__trigger') as HTMLElement | null
  const accountAvatar = q('.sidebar-account__avatar') as HTMLElement | null
  const nameEl = q('.sidebar-account__copy b') as HTMLElement | null
  const roleEl = q('.sidebar-account__copy small') as HTMLElement | null
  const logoIcon = q('.sidebar-logo-icon') as HTMLElement | null
  const logoTeam = q('.sidebar-logo-team') as HTMLElement | null
  const nav = q('.sidebar-nav') as HTMLElement | null
  const topbarAvatar = q('.layout-avatar') as HTMLElement | null
  const topbarRight = q('.layout-topbar__right') as HTMLElement | null

  const sidebarRect = rect(sidebar)
  const accountRect = rect(account)
  const triggerRect = rect(accountTrigger)
  const nameRect = rect(nameEl)
  const roleRect = rect(roleEl)
  const iconRect = rect(logoIcon)
  const teamRect = rect(logoTeam)

  // 侧边栏内横向溢出：任何后代越出内容区右边界都算「被撑开」
  const sidebarPadRight = sidebarStyle ? parseFloat(sidebarStyle.paddingRight) : 0
  const sidebarPadBottom = sidebarStyle ? parseFloat(sidebarStyle.paddingBottom) : 0
  const contentRight = sidebarRect ? sidebarRect.right - sidebarPadRight : 0
  const overflowingDescendants: string[] = []
  if (sidebar) {
    for (const el of Array.from(sidebar.querySelectorAll('*'))) {
      const r = el.getBoundingClientRect()
      if (r.width > 0 && r.right > contentRight + 0.5) {
        overflowingDescendants.push(`${el.className || el.tagName} +${round(r.right - contentRight)}px`)
      }
    }
  }

  const accountText = account?.textContent || ''
  const roleText = roleEl?.textContent?.trim() || ''
  const topbarRightText = topbarRight?.textContent || ''
  const sidebarText = sidebar?.textContent || ''
  const realName = String((state.userInfo as { realName: string }).realName)
  const roles = (state.roles as string[]) || []
  const isNarrow = window.innerWidth <= 768

  const checks: { name: string; pass: boolean; detail: string }[] = []
  const check = (name: string, pass: boolean, detail: string) => checks.push({ name, pass, detail })

  check(
    '侧边栏宽度未被内容撑开',
    !!sidebarRect && (isNarrow ? Math.abs(sidebarRect.w - window.innerWidth) <= 1 : Math.abs(sidebarRect.w - 200) <= 0.5),
    `实际 ${sidebarRect ? sidebarRect.w : 'n/a'}px（期望 ${isNarrow ? `${window.innerWidth}（窄屏满宽）` : '200'}）`,
  )
  check(
    '侧边栏无横向溢出（无后代越出内容区）',
    overflowingDescendants.length === 0,
    overflowingDescendants.length ? overflowingDescendants.join(' | ') : '无',
  )
  check(
    '账号区贴底（与侧边栏底内边距对齐）',
    !!sidebarRect && !!accountRect && Math.abs(sidebarRect.bottom - sidebarPadBottom - accountRect.bottom) <= 1,
    `账号区 bottom=${accountRect?.bottom}，期望 ${round((sidebarRect?.bottom || 0) - sidebarPadBottom)}（侧边栏 bottom ${sidebarRect?.bottom} − padding ${sidebarPadBottom}）`,
  )
  check(
    '账号区宽度 = 侧边栏内容区宽（不超出）',
    !!sidebarRect && !!accountRect && accountRect.w <= sidebarRect.w - sidebarPadRight + 0.5,
    `账号区 ${accountRect?.w}px / 内容区 ${round((sidebarRect?.w || 0) - sidebarPadRight)}px`,
  )
  check(
    '整行可点：触发区撑满账号区',
    !!accountRect && !!triggerRect && Math.abs(triggerRect.w - accountRect.w) <= 1,
    `触发区 ${triggerRect?.w}px / 账号区 ${accountRect?.w}px`,
  )
  check('账号区含用户名', !!nameEl && !!nameEl.textContent?.trim(), `「${nameEl?.textContent}」`)
  check('用户名下方显示角色', !!nameRect && !!roleRect && roleRect.y >= nameRect.bottom - 0.5 && !!roleText, `角色行 y=${roleRect?.y} ≥ 姓名行 bottom=${nameRect?.bottom}，文案「${roleText}」`)
  /*
   * 判据必须是「渲染文案 === 统一映射的结果」，不能写成「必须含汉字」——
   * HR_SPECIALIST 的中文界面正式叫法就是「HR」，用是否含汉字去判会误报。
   * 真正要守的是「不要把角色**码**直接抛到界面上」。
   */
  const expectedRoleLabel = primaryRoleLabel(roles)
  check(
    '角色文案来自统一映射（不是把角色码直接渲染）',
    roleText === expectedRoleLabel && roleText !== roles[0],
    `渲染「${roleText}」，映射期望「${expectedRoleLabel}」，角色码「${roles[0]}」`,
  )
  check('账号区含头像', !!accountAvatar, `${accountAvatar?.getBoundingClientRect().width}×${accountAvatar?.getBoundingClientRect().height}`)
  check('顶部只显示图标与团队名', !!logoIcon && !!logoTeam, `图标 ${iconRect?.w}×${iconRect?.h}，团队名「${logoTeam?.textContent}」`)
  check('团队名在图标下方', !!iconRect && !!teamRect && teamRect.y >= iconRect.bottom - 0.5, `团队名 y=${teamRect?.y} ≥ 图标 bottom=${iconRect?.bottom}`)
  check('渲染结果不含系统名', !sidebarText.includes('多源异构岗位与能力图谱'), sidebarText.includes('多源异构岗位与能力图谱') ? '出现了' : '未出现')
  check('顶栏右上角不再显示姓名与角色', !topbarRightText.includes(realName) && !topbarRightText.includes(roleText), `顶栏右侧文本「${topbarRightText.trim()}」`)
  check(
    '顶栏头像是 34×34 圆形',
    !!topbarAvatar && Math.abs(topbarAvatar.getBoundingClientRect().width - 34) <= 0.5
      && getComputedStyle(topbarAvatar).borderRadius === '50%',
    `${round(topbarAvatar?.getBoundingClientRect().width || 0)}×${round(topbarAvatar?.getBoundingClientRect().height || 0)}，radius=${topbarAvatar ? getComputedStyle(topbarAvatar).borderRadius : 'n/a'}`,
  )
  check(
    '顶栏头像可点（有 title/aria-label，点击进个人中心）',
    !!topbarAvatar && !!topbarAvatar.getAttribute('title')?.includes('进入个人中心'),
    `title="${topbarAvatar?.getAttribute('title')}"`,
  )
  if (caseName === 'long') {
    /*
     * 两个分支的期望**相反**：
     *   · 200px 栏宽 → 名字必须被截断（scrollWidth > clientWidth），否则就是把栏撑开了；
     *   · 窄屏横条 → 有空间就不该截断，完整显示才对。
     * 判据写成"必须截断"只在宽屏成立，窄屏会误报（本探针踩过）。
     */
    const truncated = !!nameEl && nameEl.scrollWidth > nameEl.clientWidth
    check(
      isNarrow ? '长名字在窄屏下完整显示（有空间不截断）' : '长名字在 200px 栏宽下走省略号而不是撑开',
      !!nameEl && getComputedStyle(nameEl).textOverflow === 'ellipsis'
        && (isNarrow ? !truncated : truncated),
      `scrollWidth ${nameEl?.scrollWidth} / clientWidth ${nameEl?.clientWidth}，text-overflow=${nameEl ? getComputedStyle(nameEl).textOverflow : 'n/a'}，截断=${truncated}`,
    )
  }

  /* ---------- 顶栏铃铛：任务可见性（跨身份泄漏的观测点） ---------- */
  const taskStore = useTaskStore()
  const storeTaskTypes = taskStore.tasks.map(t => t.type)
  const badgeEl = q('.notice-bell__badge') as HTMLElement | null
  const badgeText = (badgeEl?.textContent || '').trim()
  const badgeTone = badgeEl ? Array.from(badgeEl.classList).find(c => c.startsWith('is-')) || '' : ''
  const taskTabLabel = Array.from(document.querySelectorAll('.notice-panel__tab'))
    .map(el => (el.textContent || '').replace(/\s+/g, ' ').trim())
  const panelTaskItems = Array.from(document.querySelectorAll('.notice-panel__tasks .task-item'))
    .map(el => (el.textContent || '').replace(/\s+/g, ' ').trim())
  const matchingLeaked = storeTaskTypes.includes('matching')

  const permissions = (state.permissions as string[]) || []
  const roleCanSeeMatching = permissions.includes('MATCHING:READ')

  check(
    roleCanSeeMatching
      ? '有 MATCHING:READ 的角色能看到匹配任务（对照组）'
      : '无 MATCHING:READ 的角色不得看到匹配任务（跨身份泄漏）',
    roleCanSeeMatching ? matchingLeaked : !matchingLeaked,
    `taskStore 任务类型=[${storeTaskTypes.join(',')}]，铃铛角标="${badgeText}"(${badgeTone})，` +
      `面板 tab=${JSON.stringify(taskTabLabel)}，任务项=${JSON.stringify(panelTaskItems)}`,
  )
  check(
    roleCanSeeMatching ? '对照组：铃铛角标反映匹配任务' : '无权限时铃铛不因他人的匹配任务亮角标',
    roleCanSeeMatching ? badgeText !== '' : !panelTaskItems.some(t => t.includes('匹配')),
    `角标="${badgeText}"，面板任务项=${JSON.stringify(panelTaskItems)}`,
  )

  const passed = checks.filter(c => c.pass).length
  const payload = {
    case: caseName,
    viewport: { w: window.innerWidth, h: window.innerHeight },
    narrowBranch: isNarrow,
    geometry: {
      sidebar: sidebarRect,
      sidebarPadding: { right: sidebarPadRight, bottom: sidebarPadBottom },
      contentRight,
      nav: rect(nav),
      account: accountRect,
      accountTrigger: triggerRect,
      accountAvatar: rect(accountAvatar),
      name: nameRect,
      role: roleRect,
      logoIcon: iconRect,
      logoTeam: teamRect,
      topbarAvatar: rect(topbarAvatar),
    },
    text: {
      account: accountText.replace(/\s+/g, ' ').trim(),
      role: roleText,
      topbarRight: topbarRightText.replace(/\s+/g, ' ').trim(),
    },
    bell: {
      storeTaskTypes,
      badgeText,
      badgeTone,
      taskTabLabel,
      panelTaskItems,
      roleCanSeeMatching,
    },
    checks,
    summary: `${passed}/${checks.length} passed`,
  }

  writeReport(payload)
}
