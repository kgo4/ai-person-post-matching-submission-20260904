import { describe, expect, it } from 'vitest'
import { SUPER_ADMIN_ROLE, isSuperAdmin, isSuperAdminRole } from './super-admin'
import { ROLE_LABELS } from './role-label'

/**
 * 超级管理员识别（2026-09-04）。
 *
 * 这一层必须"处处成立"：角色判定散落各处，超管一旦在某处没被识别，
 * 表现就是静默失效（菜单少一项 / 点进去 403），不会有报错。
 * 所以这里把边界（前缀、大小写、空白、null、多角色、不在首位）全部钉死。
 */
describe('isSuperAdminRole', () => {
  it('识别超管角色码，容忍 ROLE_ 前缀 / 大小写 / 空白', () => {
    expect(SUPER_ADMIN_ROLE).toBe('SUPER_ADMIN')
    expect(isSuperAdminRole('SUPER_ADMIN')).toBe(true)
    expect(isSuperAdminRole('ROLE_SUPER_ADMIN')).toBe(true)
    expect(isSuperAdminRole('role_super_admin')).toBe(true)
    expect(isSuperAdminRole('  ROLE_SUPER_ADMIN  ')).toBe(true)
  })

  it('不误判其它角色码', () => {
    // 不能是前缀匹配
    expect(isSuperAdminRole('SUPER_ADMINX')).toBe(false)
    expect(isSuperAdminRole('PLATFORM_ADMIN')).toBe(false)
    expect(isSuperAdminRole('HR_SPECIALIST')).toBe(false)
    expect(isSuperAdminRole('')).toBe(false)
    expect(isSuperAdminRole(null)).toBe(false)
    expect(isSuperAdminRole(undefined)).toBe(false)
  })
})

describe('isSuperAdmin', () => {
  it('接受单个角色码', () => {
    expect(isSuperAdmin('SUPER_ADMIN')).toBe(true)
    expect(isSuperAdmin('ROLE_SUPER_ADMIN')).toBe(true)
    expect(isSuperAdmin('HR_SPECIALIST')).toBe(false)
    expect(isSuperAdmin(null)).toBe(false)
  })

  it('多角色时超管不在首位也要识别出来', () => {
    expect(isSuperAdmin(['EMPLOYEE', 'SUPER_ADMIN'])).toBe(true)
    expect(isSuperAdmin(['SUPER_ADMIN', 'HR_SPECIALIST'])).toBe(true)
    expect(isSuperAdmin(['ROLE_SUPER_ADMIN'])).toBe(true)
  })

  it('空数组与含空值的数组不误判、不抛错', () => {
    expect(isSuperAdmin([])).toBe(false)
    expect(isSuperAdmin([null, undefined, '', 'PLATFORM_ADMIN'])).toBe(false)
    expect(isSuperAdmin([null, 'SUPER_ADMIN'])).toBe(true)
  })
})

describe('角色文案表', () => {
  it('超管必须有中文名，否则界面会直接显示英文码', () => {
    expect(ROLE_LABELS.SUPER_ADMIN).toBe('超级管理员')
  })
})
