import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCloudKnowledgeStatus } from '@/api/rag'

/**
 * 「云知识库是否已配置」的页面级共享状态。
 *
 * 为什么必须收在一处：演化页面上有 **3 个**云知识库入口 ——
 *   ① 运行演化：`includeCloudKnowledge` 开关
 *   ② 定时演化：`includeCloudKnowledge` 开关
 *   ③ 证据准备：云知识库同步表单
 * 如果各自写一份判断，一定会漏掉其中一个；而漏掉的表现是
 * **「能勾上、能提交，但后端什么也不做」**，全程没有任何报错 —— 典型的静默失效。
 *
 * 状态来源是 `/api/rag/cloud/status` 的 `usable`（由服务端 `VolcengineKnowledgeBaseProperties`
 * 计算：enabled + 有凭证 + 有集合目标）。它与「知识资产」页云知识库卡片用的是**同一个值**，
 * 不会出现「那边显示未配置、这边能勾」的分叉。
 *
 * ⚠️ 该接口收口在 `POST:MANAGE`。演化页只有 `JOB_ARCHITECT` 可达，而它同时持有
 * `POST:EVOLUTION` 与 `POST:MANAGE`，所以不会有 403；拉取失败时**一律按未配置处理**
 * （宁可不给勾，也不要让用户以为勾了会生效）。
 */

/** 未配置时统一的提示标题（三处入口共用一份口径） */
export const CLOUD_KB_NOT_CONFIGURED = '云知识库未配置'

/** 未配置时的补充说明：把「为什么不能在这里开」讲清楚，而不是只说"未配置" */
export const CLOUD_KB_STARTUP_ONLY =
  '云知识库连接信息只在系统启动时由服务端配置提供，运行期不支持修改。'

const loaded = ref(false)
const usable = ref(false)
/** 复用同一个请求：三个组件同一时刻各自 onMounted 也只发一次 */
let inflight: Promise<void> | null = null

export function useCloudKnowledge() {
  async function refresh() {
    try {
      const res = await getCloudKnowledgeStatus()
      usable.value = Boolean(res.data?.usable)
    } catch {
      usable.value = false
    } finally {
      loaded.value = true
    }
  }

  /** 首次调用真正拉取，之后直接返回 */
  function ensureLoaded(): Promise<void> {
    if (loaded.value) return Promise.resolve()
    if (!inflight) {
      inflight = refresh().finally(() => {
        inflight = null
      })
    }
    return inflight
  }

  /** 鼠标悬浮时的提示：已配置时不打扰（返回空串，由调用方禁用 tooltip） */
  function notConfiguredHint(): string {
    return usable.value ? '' : `${CLOUD_KB_NOT_CONFIGURED}：${CLOUD_KB_STARTUP_ONLY}`
  }

  /**
   * 点击已置灰的控件时的提示。
   *
   * 为什么还要单独给点击提示：`el-switch` 一旦 `disabled` 就吞掉点击，
   * 用户只会觉得「点不动」，不知道原因；这里在包裹层上补一句说明。
   */
  function warnNotConfigured() {
    if (usable.value) return
    ElMessage.warning(`${CLOUD_KB_NOT_CONFIGURED}：${CLOUD_KB_STARTUP_ONLY}`)
  }

  return {
    /** 是否已配置且可用（唯一判据） */
    usable,
    loaded,
    ensureLoaded,
    refresh,
    notConfiguredHint,
    warnNotConfigured,
  }
}
