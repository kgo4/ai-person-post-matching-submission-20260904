import { computed, ref } from 'vue'

/**
 * 演化流程的「目标岗位上下文」。
 *
 * 为什么需要它：拆组件后，「证据准备」需要目标岗位名来生成外部趋势检索词，
 * 而目标岗位是用户在「运行演化」里选的 —— 两个组件是同级的。
 * 与其让 index.vue 做中转（层层 props/emits，改一个字段要动三个文件），
 * 不如把这些字段放在模块作用域的 ref 上：同一页面内天然共享，
 * 组件卸载后随页面一起释放，也不会泄漏到其它路由。
 *
 * 注意：这里是页面级单例，不要在里面放「每次运行一份」的数据（如进度、结果），
 * 那类状态必须留在组件内部，否则二次进入页面会看到上一次的残留。
 */
const targetPostId = ref<number | undefined>(undefined)
const targetPostName = ref('')
const industry = ref('')
const businessDomain = ref('')

export function useEvolutionTarget() {
  /**
   * 外部趋势检索词：岗位名 + 行业 + 业务领域 + 固定后缀。
   * 三者为空时返回空串，调用方据此禁用「按岗位采集」按钮，
   * 而不是拼出一个只有固定后缀的无意义查询。
   */
  const externalQuery = computed(() => {
    const postName = targetPostName.value.trim()
    if (!postName) return ''
    const context = [industry.value.trim(), businessDomain.value.trim()].filter(Boolean).join(' ')
    return [postName, context, '岗位能力 技术趋势'].filter(Boolean).join(' ')
  })

  function setTarget(postId?: number, postName?: string) {
    targetPostId.value = postId
    targetPostName.value = postName || ''
  }

  function resetTarget() {
    targetPostId.value = undefined
    targetPostName.value = ''
    industry.value = ''
    businessDomain.value = ''
  }

  return {
    targetPostId,
    targetPostName,
    industry,
    businessDomain,
    externalQuery,
    setTarget,
    resetTarget,
  }
}
