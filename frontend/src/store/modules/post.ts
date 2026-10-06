import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPost } from '@/api'
import type { PostPost } from '@/api'

export const usePostStore = defineStore('post', () => {
  const postCache = ref<Map<number, PostPost>>(new Map())

  async function fetchPost(id: number) {
    const cached = postCache.value.get(id)
    if (cached) return cached
    const res = await getPost(id)
    const post = res.data
    postCache.value.set(id, post)
    return post
  }

  function clearCache() {
    postCache.value.clear()
  }

  return { postCache, fetchPost, clearCache }
})
