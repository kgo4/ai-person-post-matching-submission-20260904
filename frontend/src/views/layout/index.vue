<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useTaskStore } from '@/store/modules/task'
import { useMatchingTaskStore } from '@/store/modules/matching-tasks'
import { getSidebarModules } from '@/config/sidebar-menu'
import { hasPermission } from '@/utils/permission'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'
import type { SidebarModule } from '@/config/sidebar-menu'

/**
 * 个人中心的合成模块：只为顶栏标题/面包屑提供文案。
 * key 用 'personal-center'（不与任何侧边栏模块 key 重合），
 * 因此侧边栏不会把某个业务模块标成「当前」，符合「账号页不属业务域」的定位。
 */
const PERSONAL_CENTER_MODULE: SidebarModule = {
  key: 'personal-center',
  label: '个人中心',
  summary: '账号与联系方式',
  path: '/profile',
  icon: User,
  children: [],
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const taskStore = useTaskStore()
const modules = computed(() => getSidebarModules())

onMounted(() => {
  taskStore.cleanup()
  /*
   * 【2026-09-04 二次收口】按当前身份裁剪任务列表。
   *
   * `task` store 持久化在 `localStorage['tasks']`，而登出并不会清理它 ——
   * 于是上一个账号（如 HR）的匹配任务会在下一个身份（如岗位体系管理员）
   * 登录时被 pinia 持久化插件**原样恢复**：顶栏铃铛亮红点、面板里写着
   * 「匹配完成，共处理 N 条记录」，点进去还因缺 MATCHING:READ 而 403。
   *
   * 接口侧（SecurityConfig）与写入侧（matching-tasks 的 syncToTaskStore）
   * 都已收口，这里是**读取/装载侧**的同一道口径：三处共用
   * `store/modules/task-visibility.ts` 这一个规则来源。
   * 裁剪后 pinia 会把新值写回 localStorage，残留数据一并清除。
   */
  taskStore.retainVisible(userStore.permissions || [])

  // 启动全局匹配任务轮询 + 刷新后从后端恢复进行中任务。
  // 匹配任务属匹配运营（HR）：其他角色既无 MATCHING:READ，
  // 也不应收到「匹配完成」提示。此前这里对所有角色无条件轮询全量匹配任务，
  // 并经 taskStore 同步到顶栏铃铛 —— 表现为「匹配完成通知所有角色都收到」。
  if (hasPermission('MATCHING:READ')) {
    const matchingTaskStore = useMatchingTaskStore()
    matchingTaskStore.startWatcher()
    matchingTaskStore.refresh()
  }
})

onUnmounted(() => {
  // 布局卸载（登出/路由重建）时停止全局轮询，避免 interval 泄漏与 401 空转
  useMatchingTaskStore().stopWatcher()
})

const activeModule = computed(() => {
  const currentPath = route.path
  // 个人中心不属于任何业务模块（见 router 中的同名路由）：给它一个合成模块，
  // 让顶栏标题显示「个人中心」而不是回落到「工作台」；
  // 它的 key 不与任何侧边栏模块重合，因此侧边栏不会高亮错一项。
  if (currentPath === '/profile' || currentPath.startsWith('/profile/')) {
    return PERSONAL_CENTER_MODULE
  }
  return modules.value.find((item) => {
    if (item.key === 'contest' && [
      '/contest',
      '/capability-brain',
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
    <!--
      账号入口分两处、职责不重叠（2026-09-04 调整）：
      侧边栏底部 = 身份展示（头像/姓名/角色）+ 唯一退出登录入口；
      顶栏右上角 = 只有头像，点击进个人中心。
    -->
    <AppSidebar :active-module="activeModule" @logout="handleLogout" />
    <div class="layout-main-area">
      <!-- 顶栏与页面内容同处一张面板：合并掉原来"浮动顶栏 + 下方卡片"的两段式观感 -->
      <div class="layout-surface">
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
  </div>
</template>

<style scoped>
.layout-root { display: flex; min-height: 100vh; background: #f5f7fc; }
.layout-main-area { display: flex; flex: 1; flex-direction: column; min-width: 0; padding: 16px 20px 20px; }
/*
 * 主内容面板：顶栏（表头行）+ 页面内容同处一张白卡。
 * 不再对子元素 overflow:hidden —— 顶栏里的搜索下拉是绝对定位的，会被裁掉。
 */
.layout-surface { display: flex; flex: 1; flex-direction: column; min-height: 0; border: 1px solid var(--app-border); border-radius: var(--app-radius-lg); background: var(--app-surface); box-shadow: var(--app-shadow-sm); }
/*
 * 内容区压一层极浅冷蓝（--app-content-bg），使纯白卡片与背景拉开层次。
 * 此前内容区继承 .layout-surface 的纯白，卡片也是纯白 —— 两者只能靠 1px 边框区分，
 * 观感上就是"卡片贴在背景上"。
 * 圆角取 calc(radius-lg - 1px)：面板本身有 1px 边框，内层圆角要小 1px 才不露白角
 * （.layout-surface 刻意不加 overflow:hidden —— 顶栏的搜索下拉是绝对定位的，会被裁）。
 */
.layout-content { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 18px 20px 20px; border-radius: 0 0 calc(var(--app-radius-lg) - 1px) calc(var(--app-radius-lg) - 1px); background: var(--app-content-bg); }
.layout-page-wrapper { position: relative; flex: 1; min-height: 0; }
.page-switch-enter-active, .page-switch-leave-active { transition: opacity 0.14s ease; }
.page-switch-enter-from, .page-switch-leave-to { opacity: 0; }
@media (max-width: 768px) { .layout-root { flex-direction: column; } .layout-main-area { padding: 12px; } .layout-content { padding: 14px; } }
</style>
