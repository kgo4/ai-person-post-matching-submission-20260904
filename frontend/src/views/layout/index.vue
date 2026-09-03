<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { useTaskStore } from '@/store/modules/task'
import { useMatchingTaskStore } from '@/store/modules/matching-tasks'
import { getSidebarModules } from '@/config/sidebar-menu'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const taskStore = useTaskStore()
const modules = computed(() => getSidebarModules())

onMounted(() => {
  taskStore.cleanup()
  // 启动全局匹配任务轮询 + 刷新后从后端恢复进行中任务
  const matchingTaskStore = useMatchingTaskStore()
  matchingTaskStore.startWatcher()
  matchingTaskStore.refresh()
})

onUnmounted(() => {
  // 布局卸载（登出/路由重建）时停止全局轮询，避免 interval 泄漏与 401 空转
  useMatchingTaskStore().stopWatcher()
})

const activeModule = computed(() => {
  const currentPath = route.path
  return modules.value.find((item) => {
    if (item.key === 'contest' && [
      '/contest',
      '/capability-brain',
      '/system/ability-tag',
    ].some(prefix => currentPath === prefix || currentPath.startsWith(`${prefix}/`))) return true
    if (item.key === 'knowledge-assets' && [
      '/rag',
      '/kg',
    ].some(prefix => currentPath === prefix || currentPath.startsWith(`${prefix}/`))) return true
    if (item.key === 'ai-governance' && (
      currentPath.startsWith('/ai-governance') ||
      currentPath === '/system/governance-filter-rules' ||
      currentPath.startsWith('/system/governance-filter-rules/')
    )) return true
    if (item.key === 'system' && [
      '/system',
    ].some(prefix => currentPath === prefix || currentPath.startsWith(`${prefix}/`))) return true
    return currentPath === item.path || item.children?.some(child => currentPath === child.path || currentPath.startsWith(`${child.path}/`))
  }) || modules.value[0]
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout-root">
    <AppSidebar :active-module="activeModule" @logout="handleLogout" />
    <div class="layout-main-area">
      <AppTopbar :active-module="activeModule" />
      <main class="layout-content">
        <div class="layout-page-wrapper">
          <router-view v-slot="{ Component, route }">
            <keep-alive :max="20">
              <component
                v-if="route.meta.keepAlive === true"
                :is="Component"
                :key="route.name ?? route.path"
              />
            </keep-alive>
            <transition v-if="route.meta.keepAlive !== true" name="page-switch" mode="out-in">
              <component :is="Component" :key="route.name ?? route.path" />
            </transition>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout-root { display: flex; min-height: 100vh; background: var(--app-bg-secondary); }
.layout-main-area { display: flex; flex: 1; flex-direction: column; min-width: 0; padding: 18px; }
.layout-content { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 20px 4px 0; }
.layout-page-wrapper { position: relative; flex: 1; min-height: 0; }
.page-switch-enter-active, .page-switch-leave-active { transition: opacity 0.14s ease; }
.page-switch-enter-from, .page-switch-leave-to { opacity: 0; }
@media (max-width: 768px) { .layout-root { flex-direction: column; } .layout-main-area { padding: 0 16px 16px; } }
</style>
