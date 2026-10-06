/**
 * 随「当前登录身份」走的持久化键（pinia `persist` 的 key）。
 *
 * 这些数据由**上一个身份**产生，登出必须清掉 —— 否则下一个身份登录时会被
 * pinia 持久化插件原样恢复，表现为跨身份泄漏：
 * 岗位体系管理员看到 HR 的匹配任务、顶栏铃铛亮红点、面板里写着
 * 「匹配完成，共处理 N 条记录」，点进去还因缺 `MATCHING:READ` 而 403。
 * （2026-09-04 实际发生并被修复。）
 *
 * **不包含 `user`**：user store 的 `logout()` 自身就把字段清空了，
 * 持久化插件会把空值写回，无需 removeItem。
 *
 * ⚠️ 新增任何声明了 `persist` 的 store，都要把它的 key 登记到这里 ——
 * `identity-scope.test.mjs` 会扫描 `store/modules/**` 自动核对，漏登记即失败。
 */
export const IDENTITY_SCOPED_PERSIST_KEYS = ['tasks', 'matching-tasks'] as const

/** 清掉身份相关的持久化数据（登出时调用） */
export function clearIdentityScopedStorage(storage: Storage = localStorage): void {
  for (const key of IDENTITY_SCOPED_PERSIST_KEYS) {
    storage.removeItem(key)
  }
}
