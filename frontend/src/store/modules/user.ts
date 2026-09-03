import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getCurrentUser } from '@/api'
import type { LoginDTO, UserVO } from '@/api'

type UserIdentity = Pick<UserVO, 'id' | 'username' | 'realName' | 'roles' | 'permissions'>

export const useUserStore = defineStore(
  'user',
  () => {
    // 状态
    const token = ref<string>('')
    const userInfo = ref<UserIdentity | null>(null)
    const roles = ref<string[]>([])
    const permissions = ref<string[]>([])

    function applyIdentity(identity: UserIdentity) {
      userInfo.value = identity
      roles.value = (identity.roles ?? []).map(r => r.replace(/^ROLE_/, '').toUpperCase())
      permissions.value = identity.permissions ?? []
    }

    // 登录
    async function login(loginForm: LoginDTO) {
      const res = await loginApi(loginForm)
      token.value = res.data.token
      applyIdentity({
        id: res.data.userId,
        username: res.data.username,
        realName: res.data.realName,
        roles: res.data.roles,
        permissions: res.data.permissions,
      })
    }

    // 获取用户信息
    async function getUserInfo() {
      const res = await getCurrentUser()
      applyIdentity(res.data)
    }

    // 退出登录
    function logout() {
      token.value = ''
      userInfo.value = null
      roles.value = []
      permissions.value = []
    }

    return {
      token,
      userInfo,
      roles,
      permissions,
      login,
      getUserInfo,
      logout,
    }
  },
  {
    persist: {
      key: 'user',
      storage: localStorage,
    },
  }
)
