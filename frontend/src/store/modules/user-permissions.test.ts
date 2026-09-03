/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const api = vi.hoisted(() => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
}))

vi.mock('@/api', () => ({
  login: api.login,
  getCurrentUser: api.getCurrentUser,
}))

import { useUserStore } from './user'

describe('user-permissions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('has empty permissions on init', () => {
    const store = useUserStore()
    expect(store.permissions).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.token).toBe('')
  })

  it('logout clears all identity', () => {
    const store = useUserStore()
    store.permissions = ['admin']
    store.roles = ['admin']
    store.token = 'fake-token'
    store.userInfo = { id: 1, username: 'test' } as any

    store.logout()

    expect(store.permissions).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
  })

  it('hardcoded *:*:* is removed from permissions', () => {
    const store = useUserStore()
    expect(store.permissions).not.toContain('*:*:*')
  })

  it('hydrates backend roles and permissions during login', async () => {
    api.login.mockResolvedValue({
      data: {
        token: 'token-1',
        userId: 1,
        username: 'admin',
        realName: 'Admin User',
        roles: ['ADMIN'],
        permissions: ['system:user:read'],
      },
    })
    const store = useUserStore()

    await store.login({ username: 'admin', password: 'secret' })

    expect(store.token).toBe('token-1')
    expect(store.userInfo?.id).toBe(1)
    expect(store.roles).toEqual(['ADMIN'])
    expect(store.permissions).toEqual(['system:user:read'])
  })

  it('hydrates an empty permission list when current user response omits it', async () => {
    api.getCurrentUser.mockResolvedValue({
      data: { id: 2, username: 'ordinary-user', roles: ['USER'] },
    })
    const store = useUserStore()

    await store.getUserInfo()

    expect(store.roles).toEqual(['USER'])
    expect(store.permissions).toEqual([])
  })
})
