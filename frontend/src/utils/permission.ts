import { useUserStore } from '@/store/modules/user'

/**
 * 权限检查工具
 * 用于按钮级别权限控制
 */

/**
 * 检查是否有指定权限
 * @param permission 权限标识
 * @returns boolean
 */
export function hasPermission(permission: string): boolean {
  const userStore = useUserStore()
  const permissions = userStore.permissions || []
  return permissions.includes(permission)
}

/**
 * 检查是否有指定角色
 * @param role 角色标识（应与后端 roleCode 一致，如 ADMIN/HR/EMPLOYEE）
 * @returns boolean
 */
export function hasRole(role: string): boolean {
  const userStore = useUserStore()
  const roles = userStore.roles || []
  const normalized = role.toUpperCase()
  if (roles.includes('ADMIN')) {
    return true
  }
  return roles.includes(normalized)
}

/**
 * 检查是否有任意一个权限
 * @param permissionList 权限标识列表
 * @returns boolean
 */
export function hasAnyPermission(permissionList: string[]): boolean {
  return permissionList.some((permission) => hasPermission(permission))
}

/**
 * 检查是否有所有权限
 * @param permissionList 权限标识列表
 * @returns boolean
 */
export function hasAllPermissions(permissionList: string[]): boolean {
  return permissionList.every((permission) => hasPermission(permission))
}

/**
 * 检查是否有任意一个角色
 * @param roleList 角色标识列表
 * @returns boolean
 */
export function hasAnyRole(roleList: string[]): boolean {
  return roleList.some((role) => hasRole(role))
}

/**
 * 权限指令（自定义指令）
 * 用法: v-permission="'system:user:add'"
 */
export const permissionDirective = {
  mounted(el: HTMLElement, binding: { value: string | string[] }) {
    const { value } = binding

    if (!value) return

    const permissions = Array.isArray(value) ? value : [value]
    const hasAuth = hasAnyPermission(permissions)

    if (!hasAuth) {
      el.parentNode?.removeChild(el)
    }
  },
}
