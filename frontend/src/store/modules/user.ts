import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getCurrentUser, getMyEmployee } from '@/api'
import { clearIdentityScopedStorage } from '../identity-scope'
import type { LoginDTO, UserVO } from '@/api'

type UserIdentity = Pick<UserVO, 'id' | 'username' | 'realName' | 'roles' | 'permissions' | 'empId' | 'avatar'>

export const useUserStore = defineStore(
  'user',
  () => {
    // 状态
    const token = ref<string>('')
    const userInfo = ref<UserIdentity | null>(null)
    const roles = ref<string[]>([])
    const permissions = ref<string[]>([])
    /**
     * 当前账号关联的人员档案 ID。
     * 员工角色的“仅本人”页面（能力评估、简历解析、AI 测试/面试、学习路径）
     * 一律用它作为 empId，而不是依赖 URL 传参——URL 传参只作为管理端代看入口。
     */
    const empId = ref<number | null>(null)

    /**
     * 头像访问路径（顶栏与个人中心共用）。
     *
     * 单开一个 ref 而不是只塞进 userInfo：个人中心改完头像要立即刷新顶栏，
     * 而此时不需要重新拉一次身份（roles/permissions 未变）。
     */
    const avatar = ref<string | null>(null)

    function applyIdentity(identity: UserIdentity) {
      avatar.value = identity.avatar ?? null
      userInfo.value = identity
      roles.value = (identity.roles ?? []).map(r => r.replace(/^ROLE_/, '').toUpperCase())
      permissions.value = identity.permissions ?? []
      empId.value = identity.empId ?? null
    }

    // 登录
    async function login(loginForm: LoginDTO) {
      const res = await loginApi(loginForm)
      token.value = res.data.token
      applyIdentity({
        id: res.data.userId,
        username: res.data.username,
        realName: res.data.realName,
        avatar: res.data.avatar ?? null,
        roles: res.data.roles,
        permissions: res.data.permissions,
        empId: res.data.empId ?? null,
      })
    }

    // 获取用户信息
    async function getUserInfo() {
      const res = await getCurrentUser()
      applyIdentity(res.data)
    }

    /**
     * 兜底补全 empId：旧登录态（登录响应早于 empId 字段）或本地缓存缺失时调用。
     * 失败不抛错，保持 empId 为 null，由页面显示业务空状态。
     */
    async function ensureEmpId(): Promise<number | null> {
      if (empId.value != null) return empId.value
      try {
        const res = await getMyEmployee()
        empId.value = res.data?.id ?? null
      } catch {
        empId.value = null
      }
      return empId.value
    }

    // 退出登录
    function logout() {
      token.value = ''
      userInfo.value = null
      roles.value = []
      permissions.value = []
      empId.value = null
      avatar.value = null
      /*
       * 清掉身份相关的持久化数据（task / matching-tasks 快照）。
       *
       * 光清本 store 的字段不够：那些业务状态是**上一个身份**产生的，
       * 留到下一个身份登录就会被持久化插件恢复 ——
       * 岗位体系管理员因此能看到 HR 的匹配任务与「匹配完成」提示。
       * 只做纯字符串清理，不 import 任何 store，避免引入循环依赖。
       */
      clearIdentityScopedStorage()
    }

    /** 个人中心保存头像后同步到顶栏（身份其余部分不变，无需重新请求） */
    function setAvatar(next: string | null) {
      avatar.value = next
      if (userInfo.value) {
        userInfo.value = { ...userInfo.value, avatar: next }
      }
    }

    return {
      token,
      userInfo,
      roles,
      permissions,
      empId,
      avatar,
      login,
      getUserInfo,
      ensureEmpId,
      setAvatar,
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
