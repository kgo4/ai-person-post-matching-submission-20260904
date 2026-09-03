<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold, MoonNight, Sunny } from '@element-plus/icons-vue'
import { getSidebarModules, MODULE_ORDER, type SidebarModule } from '@/config/sidebar-menu'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{ activeModule: SidebarModule }>()
const emit = defineEmits<{ logout: [] }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sidebarCollapsed = ref(false)
const darkMode = ref(false)
const modules = computed(() => getSidebarModules())
const userName = computed(() => userStore.userInfo?.username || '系统用户')
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase())
const userRole = computed(() => userStore.roles[0] || 'WORKSPACE')

type WorkspaceDomain = 'workbench' | 'governance'

const GOVERNANCE_MODULE_KEYS = new Set(['contest', 'ai-governance', 'system'])
const activeDomain = ref<WorkspaceDomain>('workbench')

const orderedModules = computed(() => [...modules.value].sort((a, b) =>
  (MODULE_ORDER as readonly string[]).indexOf(a.key) - (MODULE_ORDER as readonly string[]).indexOf(b.key)))

const visibleModules = computed(() => orderedModules.value.filter((item) =>
  activeDomain.value === 'governance'
    ? GOVERNANCE_MODULE_KEYS.has(item.key)
    : !GOVERNANCE_MODULE_KEYS.has(item.key)))

const activeChildren = computed(() => props.activeModule.children)

function go(path: string) {
  router.push(path)
}

function isActiveChild(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function resolveDomain(moduleKey: string): WorkspaceDomain {
  return GOVERNANCE_MODULE_KEYS.has(moduleKey) ? 'governance' : 'workbench'
}

function switchDomain(domain: WorkspaceDomain) {
  if (activeDomain.value === domain) return
  activeDomain.value = domain
  const defaultModule = visibleModules.value.find((item) =>
    domain === 'governance' ? item.key === 'ai-governance' : item.key === 'dashboard') || visibleModules.value[0]
  if (defaultModule) go(defaultModule.path)
}

function handleUserCommand(command: string) {
  if (command === 'logout') emit('logout')
}

function toggleTheme() {
  darkMode.value = !darkMode.value
  document.documentElement.dataset.theme = darkMode.value ? 'dark' : 'light'
  localStorage.setItem('app-theme', darkMode.value ? 'dark' : 'light')
}

onMounted(() => {
  const savedTheme = localStorage.getItem('app-theme')
  darkMode.value = savedTheme === 'dark'
  document.documentElement.dataset.theme = darkMode.value ? 'dark' : 'light'
})

watch(() => props.activeModule.key, (moduleKey) => {
  activeDomain.value = resolveDomain(moduleKey)
}, { immediate: true })
</script>

<template>
  <aside class="layout-sidebar" :class="{ 'layout-sidebar--collapsed': sidebarCollapsed }">
    <div class="sidebar-logo" @click="go('/dashboard')">
      <div class="sidebar-logo-icon"><span></span><i></i></div>
      <div v-if="!sidebarCollapsed" class="sidebar-logo-copy">
        <strong>GraphMatch</strong>
        <span>智能胜任力平台</span>
      </div>
    </div>

    <div v-if="!sidebarCollapsed" class="sidebar-intro">
      <button class="sidebar-intro__tab" :class="{ 'sidebar-intro__tab--active': activeDomain === 'workbench' }" @click="switchDomain('workbench')">业务工作台</button>
      <button class="sidebar-intro__tab" :class="{ 'sidebar-intro__tab--active': activeDomain === 'governance' }" @click="switchDomain('governance')">能力治理</button>
    </div>

    <nav class="sidebar-nav">
      <div v-for="item in visibleModules" :key="item.key" class="sidebar-nav-group">
        <button
          class="sidebar-nav-item"
          :class="{ 'sidebar-nav-item--active': props.activeModule.key === item.key }"
          :title="item.label"
          @click="go(item.path)"
        >
          <span class="sidebar-nav-icon">
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
          </span>
          <div v-if="!sidebarCollapsed" class="sidebar-nav-copy">
            <span class="sidebar-nav-label">{{ item.label }}</span>
            <span class="sidebar-nav-summary">{{ item.summary }}</span>
          </div>
        </button>

        <div v-if="!sidebarCollapsed && props.activeModule.key === item.key && activeChildren.length > 0" class="sidebar-subnav">
          <button
            v-for="child in activeChildren"
            :key="child.path"
            class="sidebar-subnav-item"
            :class="{ 'sidebar-subnav-item--active': isActiveChild(child.path) }"
            @click="go(child.path)"
          >
            <span class="sidebar-subnav-dot"></span>
            {{ child.label }}
            <span v-if="child.beta" class="sidebar-beta-badge">Beta</span>
          </button>
        </div>
      </div>
    </nav>

    <div class="sidebar-bottom">
      <button class="sidebar-theme-toggle" :title="darkMode ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme">
        <el-icon><Sunny v-if="!darkMode" /><MoonNight v-else /></el-icon>
        <span v-if="!sidebarCollapsed">{{ darkMode ? '深色模式' : '浅色模式' }}</span>
        <i class="sidebar-theme-toggle__track" :class="{ 'is-dark': darkMode }"><b></b></i>
      </button>
      <button class="sidebar-collapse-btn" :title="sidebarCollapsed ? '展开' : '收起'" @click="sidebarCollapsed = !sidebarCollapsed">
        <el-icon :size="16"><Expand v-if="sidebarCollapsed" /><Fold v-else /></el-icon>
        <span v-if="!sidebarCollapsed">收起导航</span>
      </button>
      <el-dropdown trigger="click" @command="handleUserCommand">
        <button class="sidebar-user" :title="userName">
          <span class="sidebar-user__avatar">{{ userInitial }}</span>
          <span v-if="!sidebarCollapsed" class="sidebar-user__copy"><b>{{ userName }}</b><small>{{ userRole }}</small></span>
          <span v-if="!sidebarCollapsed" class="sidebar-user__more">...</span>
        </button>
        <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </div>
  </aside>
</template>

<style scoped>
.layout-sidebar { position: sticky; top: 16px; z-index: 20; display: flex; flex-direction: column; width: 300px; height: calc(100vh - 32px); margin: 0 0 16px 16px; padding: 22px 16px 16px; border: 1px solid rgba(226, 232, 240, 0.9); border-radius: 24px; background: var(--app-sidebar-surface); backdrop-filter: blur(18px); box-shadow: 0 12px 32px rgba(71, 85, 105, 0.1); transition: width 0.3s cubic-bezier(0.16, 1, 0.3, 1), background-color 0.2s ease, box-shadow 0.2s ease; }
.layout-sidebar--collapsed { width: 88px; padding-right: 14px; padding-left: 14px; }
.sidebar-logo { display: flex; align-items: center; gap: 12px; padding: 4px 8px 18px; cursor: pointer; border-bottom: 1px solid rgba(226, 232, 240, 0.72); }
.sidebar-logo-icon { position: relative; display: block; width: 42px; height: 42px; flex: 0 0 42px; background: transparent; transition: transform 0.2s ease, box-shadow 0.2s ease; }
.sidebar-logo-icon span, .sidebar-logo-icon i { position: absolute; top: 11px; width: 19px; height: 19px; border-radius: 7px; transform: rotate(45deg); }
.sidebar-logo-icon span { left: 5px; background: #f6b54a; }
.sidebar-logo-icon i { right: 4px; background: #16a695; }
.sidebar-logo:hover .sidebar-logo-icon { transform: scale(1.03); box-shadow: var(--app-shadow-sm); }
.sidebar-logo-copy strong { display: block; color: var(--app-text-strong); font-size: 17px; font-weight: 700; letter-spacing: -0.02em; }
.sidebar-logo-copy span { display: block; margin-top: 2px; color: var(--app-text-muted); font-size: 11px; font-weight: 500; letter-spacing: 0.04em; }
.sidebar-intro { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; margin: 16px 0 14px; padding: 3px; border: 1px solid var(--app-border); border-radius: 12px; background: var(--app-bg-tertiary); }
.sidebar-intro__tab { display: grid; width: 100%; min-height: 34px; place-items: center; border: 0; border-radius: 9px; color: var(--app-text-muted); background: transparent; font-size: 11px; font-weight: 700; cursor: pointer; }
.sidebar-intro__tab--active { color: #fff; background: #5b5ce2; box-shadow: 0 4px 9px rgba(91, 92, 226, 0.24); }
.sidebar-nav { flex: 1; overflow-y: auto; padding: 0 2px; }
.sidebar-nav-group + .sidebar-nav-group { margin-top: 5px; }
.sidebar-nav-item { width: 100%; display: flex; align-items: center; gap: 11px; min-height: 48px; padding: 8px 12px; border: 1px solid transparent; border-radius: 10px; background: transparent; cursor: pointer; transition: background-color 0.22s ease, border-color 0.22s ease, transform 0.22s ease; }
.sidebar-nav-item:hover { background: var(--app-sidebar-hover); border-color: var(--app-border); }
.sidebar-nav-item--active { background: var(--app-sidebar-active); border-color: transparent; box-shadow: 0 5px 12px rgba(91, 92, 226, 0.08); }
.sidebar-nav-icon { display: grid; place-items: center; width: 30px; height: 30px; flex-shrink: 0; border-radius: 8px; background: transparent; color: var(--app-text-secondary); transition: background-color 0.22s ease, color 0.22s ease; }
.sidebar-nav-item--active .sidebar-nav-icon { color: var(--app-accent); background: var(--app-accent-soft); }
.layout-sidebar--collapsed .sidebar-nav-item { justify-content: center; padding: 10px 6px; }
.layout-sidebar--collapsed .sidebar-nav-icon { width: 38px; height: 38px; }
.sidebar-nav-copy { display: flex; flex-direction: column; min-width: 0; text-align: left; line-height: 1.3; }
.sidebar-nav-label { color: var(--app-text-strong); font-size: 13px; font-weight: 650; }
.sidebar-nav-summary { display: none; }
.sidebar-subnav { display: flex; flex-direction: column; gap: 4px; margin: 6px 0 0 50px; padding: 6px 0 6px 12px; border-left: 1.5px solid var(--app-border); animation: subnav-expand 0.25s ease; }
@keyframes subnav-expand { from { opacity: 0; max-height: 0; } to { opacity: 1; max-height: 600px; } }
.sidebar-subnav-item { display: inline-flex; align-items: center; gap: 8px; padding: 7px 10px; border: none; border-radius: 8px; background: transparent; color: var(--app-text-secondary); font-size: 13px; font-weight: 500; cursor: pointer; text-align: left; transition: color 0.18s ease, background-color 0.18s ease; }
.sidebar-subnav-item:hover { color: var(--app-accent); background: var(--app-accent-soft); }
.sidebar-subnav-item--active { color: var(--app-accent); background: var(--app-accent-soft); font-weight: 600; }
.sidebar-subnav-dot { width: 5px; height: 5px; flex-shrink: 0; border-radius: 50%; background: currentColor; opacity: 0.5; transition: opacity 0.18s ease; }
.sidebar-subnav-item--active .sidebar-subnav-dot { opacity: 1; }
.sidebar-beta-badge { margin-left: auto; padding: 2px 7px; border-radius: 100px; color: var(--app-accent); background: var(--app-accent-soft); font-size: 10px; font-weight: 700; letter-spacing: 0.04em; animation: beta-pulse 3s ease-in-out infinite; }
@keyframes beta-pulse { 0%, 100% { opacity: 0.8; } 50% { opacity: 1; } }
.sidebar-bottom { padding-top: 14px; border-top: 1px solid rgba(226, 232, 240, 0.72); }
.sidebar-theme-toggle { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 36px; padding: 5px 8px; border: 0; background: transparent; color: var(--app-text-muted); font-size: 11px; cursor: pointer; text-align: left; }
.sidebar-theme-toggle__track { position: relative; width: 30px; height: 18px; margin-left: auto; border-radius: 99px; background: #e7eaf0; box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.1); transition: background-color 0.2s ease; }
.sidebar-theme-toggle__track b { position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.2); transition: transform 0.2s ease; }
.sidebar-theme-toggle__track.is-dark { background: #5b5ce2; }
.sidebar-theme-toggle__track.is-dark b { transform: translateX(12px); }
.sidebar-collapse-btn { width: 100%; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 8px 12px; border: 0; border-radius: 8px; background: transparent; color: var(--app-text-muted); font-size: 11px; font-weight: 600; cursor: pointer; transition: background-color 0.22s ease, border-color 0.22s ease, color 0.22s ease; }
.sidebar-collapse-btn:hover { border-color: var(--app-border-strong); background: var(--app-bg-secondary); color: var(--app-text); }
.sidebar-user { display: flex; width: 100%; align-items: center; gap: 9px; min-height: 48px; margin-top: 10px; padding: 8px; border: 0; border-radius: 10px; color: inherit; background: transparent; cursor: pointer; text-align: left; }
.sidebar-user:hover { background: var(--app-sidebar-hover); }
.sidebar-user__avatar { display: grid; width: 32px; height: 32px; flex: 0 0 32px; place-items: center; border-radius: 50%; color: #fff; background: #e7a778; font-size: 12px; font-weight: 800; }
.sidebar-user__copy { display: flex; min-width: 0; flex-direction: column; }
.sidebar-user__copy b { overflow: hidden; color: var(--app-text-strong); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-user__copy small { overflow: hidden; margin-top: 2px; color: var(--app-text-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-user__more { margin-left: auto; color: var(--app-text-muted); font-size: 16px; letter-spacing: 1px; }
@media (max-width: 1100px) { .layout-sidebar { width: 88px; } .sidebar-logo-copy, .sidebar-intro, .sidebar-nav-copy, .sidebar-subnav, .sidebar-collapse-btn span, .sidebar-theme-toggle > span { display: none; } .sidebar-theme-toggle { justify-content: center; } .sidebar-theme-toggle__track { display: none; } }
@media (max-width: 768px) { .layout-sidebar { position: relative; top: auto; width: 100%; height: auto; min-height: 0; margin: 0; padding: 16px 16px 0; border-radius: 0; } }
</style>
