import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

/**
 * 解析当前页面应采用的人员档案 ID（empId）。
 *
 * 取值优先级：
 * 1. URL 显式传入的 empId —— HR / 管理员代看某个人员时使用；
 * 2. 登录态绑定的本人档案 —— 员工自主访问，URL 不再需要带参数。
 *
 * 这样员工从工作台或侧边栏进入评估、简历解析、AI 测试/面试时不会再落到空状态；
 * 同时管理端原有的“带 empId 打开指定人员页面”行为保持不变。
 */
export function useEmployeeScope() {
  const route = useRoute()
  const userStore = useUserStore()

  /** URL 上显式指定的人员（管理端代看） */
  const queryEmpId = computed(() => {
    const raw = Number(route.query.empId)
    return Number.isFinite(raw) && raw > 0 ? raw : 0
  })

  /** 当前页面应使用的人员档案 ID；未解析到时为 0，由页面展示空状态 */
  const empId = computed(() => queryEmpId.value || userStore.empId || 0)

  /**
   * 解析本人身份并返回最终 empId。
   * 旧登录态（登录响应早于 empId 字段）或本地缓存缺失时，通过 /employee/me 兜底补全。
   */
  async function resolveEmployeeScope(): Promise<number> {
    if (queryEmpId.value) return queryEmpId.value
    const resolved = await userStore.ensureEmpId()
    return resolved ?? 0
  }

  return { empId, queryEmpId, resolveEmployeeScope }
}
