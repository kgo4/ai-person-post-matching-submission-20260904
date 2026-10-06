import assert from 'node:assert/strict'
import {
  countdownLabel,
  isCountdownActive,
  isValidEmail,
  isValidEmailCode,
  isValidPhone,
  needsRoleAuthCode,
  normalizeRole,
  canSubmitRegister,
  REGISTERABLE_ROLES,
} from './register-logic.ts'

/* ============ 角色与授权码 ============ */

// 只有员工免授权码 —— 与后端 RegisterRoleCodeProperties 白名单同口径
assert.equal(needsRoleAuthCode('EMPLOYEE'), false, '员工不需要授权码')
assert.equal(needsRoleAuthCode('HR_SPECIALIST'), true)
assert.equal(needsRoleAuthCode('JOB_ARCHITECT'), true)
assert.equal(needsRoleAuthCode('PLATFORM_ADMIN'), true)
// 留空按员工（与后端 resolveRegistrationRole 一致），否则老页面会被判成"要授权码"
assert.equal(needsRoleAuthCode(undefined), false, '角色留空时按员工处理')
assert.equal(needsRoleAuthCode(''), false)
assert.equal(needsRoleAuthCode(' employee '), false, '大小写与空白要容错')

// 超级管理员不开放注册：连选项都不该出现
assert.ok(!(REGISTERABLE_ROLES).includes('SUPER_ADMIN'), '超管不得出现在可注册角色里')
assert.equal(REGISTERABLE_ROLES.length, 4, '可注册角色恰好 4 个')

assert.equal(normalizeRole(undefined), 'EMPLOYEE')
assert.equal(normalizeRole('hr_specialist'), 'HR_SPECIALIST')

/* ============ 邮箱 / 手机号 / 验证码 ============ */

assert.equal(isValidEmail('a@b.com'), true)
assert.equal(isValidEmail('  a@b.com  '), true, '前后空格应被容忍')
assert.equal(isValidEmail('a@b'), false, '域名无点不算邮箱')
assert.equal(isValidEmail('a b@c.com'), false, '含空格不算邮箱')
assert.equal(isValidEmail('a@@b.com'), false, '多个 @ 不算邮箱')
assert.equal(isValidEmail('@b.com'), false)
assert.equal(isValidEmail('a@.com'), false)
assert.equal(isValidEmail('a@b.'), false)
assert.equal(isValidEmail(''), false)
assert.equal(isValidEmail(null), false)

assert.equal(isValidPhone('13800138000'), true)
assert.equal(isValidPhone('12800138000'), false, '第二位不能是 2')
assert.equal(isValidPhone('1380013800'), false, '差一位不算')
assert.equal(isValidPhone('138001380000'), false, '多一位不算')
assert.equal(isValidPhone(''), false)

assert.equal(isValidEmailCode('123456'), true)
assert.equal(isValidEmailCode('12345'), false)
assert.equal(isValidEmailCode('1234567'), false)
assert.equal(isValidEmailCode('12345a'), false)
assert.equal(isValidEmailCode(''), false)

/* ============ 倒计时 ============ */

// 60s 内禁止重发（这是需求里明确的一条）
assert.equal(isCountdownActive(60), true)
assert.equal(isCountdownActive(1), true)
assert.equal(isCountdownActive(0), false)
assert.equal(isCountdownActive(-3), false, '负数不能算成"还在倒计时"')

assert.equal(countdownLabel(60), '60s 后重发')
assert.equal(countdownLabel(1), '1s 后重发')
assert.equal(countdownLabel(0), '获取验证码')
assert.equal(countdownLabel(-5), '获取验证码')
// 小数：向上取整，避免 0.4 秒时显示"0s 后重发"却仍然不可点
assert.equal(countdownLabel(0.4), '1s 后重发')
assert.equal(isCountdownActive(0.4), true)

/* ============ 提交按钮可用性 ============ */

const base = {
  username: 'zhangsan',
  password: 'abc123',
  confirmPassword: 'abc123',
  realName: '张三',
  phone: '13800138000',
  email: 'zhangsan@company.com',
  emailCode: '123456',
  roleCode: 'EMPLOYEE',
  roleAuthCode: '',
}

assert.equal(canSubmitRegister(base), true, '员工填全即可提交')

// 员工不需要授权码：即使留空也能提交
assert.equal(canSubmitRegister({ ...base, roleAuthCode: '' }), true)

// 其它角色缺授权码 → 不可提交
for (const roleCode of ['HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN']) {
  assert.equal(canSubmitRegister({ ...base, roleCode }), false, `${roleCode} 缺授权码不该可提交`)
  assert.equal(
    canSubmitRegister({ ...base, roleCode, roleAuthCode: 'X-2026-KB' }), true,
    `${roleCode} 填了授权码应可提交`,
  )
}

assert.equal(canSubmitRegister({ ...base, username: 'ab' }), false, '用户名太短')
assert.equal(canSubmitRegister({ ...base, username: 'a'.repeat(21) }), false, '用户名太长')
assert.equal(canSubmitRegister({ ...base, username: '张三' }), false, '用户名不支持中文')
assert.equal(canSubmitRegister({ ...base, password: '12345' }), false, '密码太短')
assert.equal(canSubmitRegister({ ...base, password: 'a'.repeat(33) }), false, '密码太长')
assert.equal(canSubmitRegister({ ...base, password: 'abc 123', confirmPassword: 'abc 123' }), false, '密码不能含空格')
assert.equal(canSubmitRegister({ ...base, confirmPassword: 'abc124' }), false, '两次密码不一致')
assert.equal(canSubmitRegister({ ...base, realName: '   ' }), false, '姓名不能只有空格')
assert.equal(canSubmitRegister({ ...base, phone: '123' }), false, '手机号格式错')
assert.equal(canSubmitRegister({ ...base, email: 'bad' }), false, '邮箱格式错')
assert.equal(canSubmitRegister({ ...base, emailCode: 'abc' }), false, '验证码格式错')

// 用户名前后空格应被容忍（提交前会 trim）
assert.equal(canSubmitRegister({ ...base, username: '  zhangsan  ' }), true)

console.log('register-logic.test.mjs: 全部断言通过')
