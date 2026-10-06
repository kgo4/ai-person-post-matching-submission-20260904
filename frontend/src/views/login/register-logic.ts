/**
 * 注册页的纯逻辑（与 Vue 无关，可被 `.test.mjs` 直接断言）。
 *
 * 为什么要把这几段抽出来：注册流程里有几处**很容易写错、又不会报错**的判断 ——
 * 授权码何时必填、按钮何时可点、倒计时怎么显示。它们散在 SFC 里时没有任何测试能覆盖
 * （本项目没有组件挂载环境），一旦改错就是"点了没反应"或"填了没校验"。
 */

/** 可自助注册的角色码（与后端 RegisterRoleCodeProperties 保持一致；超管不开放） */
export const REGISTERABLE_ROLES = ['EMPLOYEE', 'HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN'] as const

export type RegisterableRole = (typeof REGISTERABLE_ROLES)[number]

/** 角色码 → 展示名（注册页专用，避免与 utils/role-label 的全局文案互相牵制） */
export const REGISTER_ROLE_LABELS: Record<RegisterableRole, string> = {
  EMPLOYEE: '员工',
  HR_SPECIALIST: 'HR',
  JOB_ARCHITECT: '岗位体系管理员',
  PLATFORM_ADMIN: '平台管理员',
}

/**
 * 该角色是否需要授权码。
 *
 * **只有员工不需要**。后端的白名单也是这个口径（EMPLOYEE 恒通过），
 * 两处必须一致，否则会出现"前端要填、后端不校验"或反过来的错位。
 */
export function needsRoleAuthCode(role: string | undefined | null): boolean {
  return normalizeRole(role) !== 'EMPLOYEE'
}

/** 归一化角色码：去空白并大写；空值按员工处理（与后端 resolveRegistrationRole 一致）。 */
export function normalizeRole(role: string | undefined | null): string {
  const trimmed = (role ?? '').trim().toUpperCase()
  return trimmed || 'EMPLOYEE'
}

/** 邮箱格式（与后端 @Email 的宽松口径对齐：有 @、前后非空、域名含点） */
export function isValidEmail(value: string | undefined | null): boolean {
  const v = (value ?? '').trim()
  if (!v || v.includes(' ')) return false
  const at = v.indexOf('@')
  if (at <= 0 || at !== v.lastIndexOf('@')) return false
  const domain = v.slice(at + 1)
  return domain.includes('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

/** 手机号：11 位、1 开头、第二位 3-9（与后端正则一致） */
export function isValidPhone(value: string | undefined | null): boolean {
  return /^1[3-9]\d{9}$/.test((value ?? '').trim())
}

/** 6 位数字验证码 */
export function isValidEmailCode(value: string | undefined | null): boolean {
  return /^\d{6}$/.test((value ?? '').trim())
}

/**
 * 倒计时按钮文案。
 *
 * `remain` 为剩余秒数：> 0 显示「Xs 后重发」并禁用，<= 0 显示「获取验证码」并可点。
 * 单独抽出来是因为这里最容易写出 `remain > 0 ? '获取验证码' : ...` 这种反转错误，
 * 而反转后只是"按钮文案和行为相反"，不会报任何错。
 */
export function countdownLabel(remain: number): string {
  const seconds = Math.max(0, Math.ceil(remain))
  return seconds > 0 ? `${seconds}s 后重发` : '获取验证码'
}

/** 倒计时是否处于禁止重发状态 */
export function isCountdownActive(remain: number): boolean {
  return Math.max(0, Math.ceil(remain)) > 0
}

/** 注册按钮是否可提交：所有必填项都合法才允许（避免用户点了才逐条报错） */
export function canSubmitRegister(form: {
  username?: string
  password?: string
  confirmPassword?: string
  realName?: string
  phone?: string
  email?: string
  emailCode?: string
  roleCode?: string
  roleAuthCode?: string
  sending?: boolean
}): boolean {
  const username = (form.username ?? '').trim()
  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return false
  const password = form.password ?? ''
  if (password.length < 6 || password.length > 32 || /\s/.test(password)) return false
  if (password !== form.confirmPassword) return false
  if (!(form.realName ?? '').trim()) return false
  if (!isValidPhone(form.phone)) return false
  if (!isValidEmail(form.email)) return false
  if (!isValidEmailCode(form.emailCode)) return false
  if (needsRoleAuthCode(form.roleCode) && !(form.roleAuthCode ?? '').trim()) return false
  return true
}
