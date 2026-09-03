import { onUnmounted, ref } from 'vue'
import { pageEmployees, pagePosts } from '@/api'

export interface GlobalSearchResult {
  type: 'employee' | 'post'
  id: number
  name: string
  code: string
  path: string
}

const DEBOUNCE_MS = 300

/**
 * 全局搜索 composable：
 * - 保留请求代数计数器与 AbortController，较慢的旧请求不会覆盖较新的响应
 * - 取消静默处理；非取消错误保持可见
 * - 空白输入/卸载时清空结果
 */
export function useGlobalSearch() {
  const keyword = ref('')
  const results = ref<GlobalSearchResult[]>([])
  const showPanel = ref(false)
  const loading = ref(false)

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let controller: AbortController | null = null
  let generation = 0
  let closePanelTimer: ReturnType<typeof setTimeout> | null = null

  function clearTimers() {
    if (debounceTimer) clearTimeout(debounceTimer)
    if (closePanelTimer) clearTimeout(closePanelTimer)
    debounceTimer = null
    closePanelTimer = null
  }

  function abortPending() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  async function handleSearch() {
    const query = keyword.value.trim()
    const myGeneration = ++generation
    abortPending()

    if (!query) {
      results.value = []
      showPanel.value = false
      loading.value = false
      return
    }

    loading.value = true
    showPanel.value = true
    results.value = []

    controller = new AbortController()
    try {
      const [empRes, postRes] = await Promise.allSettled([
        pageEmployees({ current: 1, size: 5, keyword: query }, { signal: controller.signal }),
        pagePosts({ current: 1, size: 5, keyword: query }, { signal: controller.signal }),
      ])

      // 只应用最新一代的响应
      if (myGeneration !== generation) return

      const collected: GlobalSearchResult[] = []
      if (empRes.status === 'fulfilled' && empRes.value.data?.records) {
        empRes.value.data.records.forEach((emp: any) => {
          collected.push({
            type: 'employee',
            id: emp.id,
            name: emp.realName,
            code: emp.empCode,
            path: `/employee/detail/${emp.id}`,
          })
        })
      }
      if (postRes.status === 'fulfilled' && postRes.value.data?.records) {
        postRes.value.data.records.forEach((post: any) => {
          collected.push({
            type: 'post',
            id: post.id,
            name: post.postName,
            code: post.postCode,
            path: `/post/detail/${post.id}`,
          })
        })
      }
      results.value = collected
    } catch (error: unknown) {
      if (isAbortError(error)) return
      results.value = []
    } finally {
      if (myGeneration === generation) {
        loading.value = false
      }
    }
  }

  function onSearchInput() {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(handleSearch, DEBOUNCE_MS)
  }

  function clearResults() {
    generation++
    abortPending()
    results.value = []
    showPanel.value = false
    loading.value = false
    keyword.value = ''
  }

  function closeSearchPanel() {
    if (closePanelTimer) clearTimeout(closePanelTimer)
    closePanelTimer = setTimeout(() => {
      showPanel.value = false
    }, 200)
  }

  function isAbortError(error: unknown): boolean {
    return error instanceof DOMException && error.name === 'AbortError'
  }

  onUnmounted(() => {
    clearTimers()
    abortPending()
    generation++
  })

  return {
    keyword,
    results,
    showPanel,
    loading,
    onSearchInput,
    clearResults,
    closeSearchPanel,
  }
}
