import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { stripSfcComments } from '../utils/sfc-source.ts'

/**
 * 公开页面（免登录可达）的契约测试。
 *
 * 回归背景：注册页**未登录进不去** —— 守卫原先只把 `/login` 当公开页，
 * `/register` 会落到 `if (!token) next('/login?redirect=...')`，
 * 于是「点注册 → 被弹回登录页」，注册页永远打不开（自相矛盾）。
 *
 * 这里不去跑真实路由（本项目没有组件挂载环境），而是断言守卫源码的结构：
 *   · 存在一个集中声明的公开路径清单；
 *   · 注册页在清单里；
 *   · 守卫把「公开路径」的判断放在「未登录重定向」**之前**（顺序错了照样进不去）。
 */

const ROOT = resolve(import.meta.dirname, '..')
const raw = readFileSync(resolve(ROOT, 'router/index.ts'), 'utf8')
const src = stripSfcComments(raw)

/* ============ 1. 公开路径清单存在且含注册页 ============ */

const listMatch = src.match(/const\s+PUBLIC_PATHS\s*(?::[^=]+)?=\s*\[([^\]]*)\]/)
assert.ok(listMatch, '必须有一个集中声明的 PUBLIC_PATHS 清单（散在守卫里写容易漏）')

const paths = listMatch[1]
  .split(',')
  .map(s => s.trim().replace(/^['"]|['"]$/g, ''))
  .filter(Boolean)

assert.ok(paths.includes('/login'), '登录页必须是公开路径')
assert.ok(
  paths.includes('/register'),
  '注册页必须在公开路径清单里 —— 否则未登录用户会被守卫弹回登录页，注册页永远进不去',
)
// 不允许把业务页放进公开清单（放进去 = 免登录可见，属越权）
for (const p of paths) {
  assert.ok(
    ['/login', '/register'].includes(p),
    `公开路径清单里出现非预期路径 ${p}：新增公开页需要显式评审（这是免登录可达的白名单）`,
  )
}

/* ============ 2. 顺序：公开路径判断必须在「未登录重定向」之前 ============ */

const idxPublic = src.indexOf('PUBLIC_PATHS.includes(to.path)')
const idxTokenGate = src.indexOf('if (!userStore.token)')
assert.ok(idxPublic >= 0, '守卫里必须使用 PUBLIC_PATHS 判断')
assert.ok(idxTokenGate >= 0, '守卫里必须有未登录重定向')
assert.ok(
  idxPublic < idxTokenGate,
  '公开路径判断必须排在「未登录重定向」之前 —— 顺序反了的话注册页照样进不去',
)

/* ============ 3. 已登录访问公开页要回首页，未登录放行 ============ */

const publicBlock = src.slice(idxPublic, idxTokenGate)
assert.ok(publicBlock.includes("next('/')"), '已登录访问登录/注册页应被送回首页')
assert.ok(/next\(\)/.test(publicBlock), '未登录访问公开页应放行（next()）')

/* ============ 4. 注册路由本身存在 ============ */

// ⚠️ 这里用**原始源码**而不是剥离过注释的：`stripSfcComments` 处理行注释时
// 会把注释所在行一起吞掉，粘在注释后的路由字面量可能被吃掉（已实测踩到）。
// 路由是否存在属"结构事实"，与注释无关，用原文判断更准。
assert.ok(/path:\s*['"]\/register['"]/.test(raw), '注册路由必须存在')
assert.ok(raw.includes('@/views/login/register.vue'), '注册路由必须指向注册页组件')

/* ============ 5. 注册成功后**不自动登录**（防回归） ============ */

// 为什么要有这条：注册流程内"写角色授权 → 失效权限缓存 → 自动登录"发生在同一请求里，
// 自动登录可能读到刚写入的旧权限快照（缓存 30min），表现为
// 「注册成功但所有接口 403，重新登录又好了」。
// 改为跳回登录页让用户自己登，权限一定按最新角色加载。
const registerSrc = readFileSync(new URL('../views/login/register.vue', import.meta.url), 'utf8')
const registerCode = stripSfcComments(registerSrc)

assert.ok(
  !/userStore\.login\s*\(/.test(registerCode),
  '注册页不得自动登录（userStore.login）—— 会踩权限缓存时序，导致注册后全部 403',
)
assert.ok(
  /router\.(replace|push)\s*\([^)]*\/login/s.test(registerCode),
  '注册成功应跳回登录页让用户手动登录',
)
assert.ok(
  /query:\s*\{[^}]*username/s.test(registerCode),
  '跳登录页应带上 username，便于登录页回填',
)

// 登录页要能接住这两个 query 参数
const loginSrc = readFileSync(new URL('../views/login/index.vue', import.meta.url), 'utf8')
assert.ok(
  /route\.query\.username/.test(stripSfcComments(loginSrc)),
  '登录页应回填注册时带过来的 username',
)

console.log('public-paths.test.mjs: 全部断言通过')
