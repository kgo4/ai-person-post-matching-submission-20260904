<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lightning, Monitor, Search } from '@element-plus/icons-vue'
import { useTaskStore } from '@/store/modules/task'
import { useGlobalSearch } from '../useGlobalSearch'
import TaskNotificationPanel from './TaskNotificationPanel.vue'

defineProps<{ activeModule: { label: string; summary: string } }>()
const router = useRouter()
const taskStore = useTaskStore()
const showTaskPanel = ref(false)
const {
  keyword: searchKeyword,
  results: searchResults,
  showPanel: showSearchPanel,
  loading: searchLoading,
  onSearchInput,
  clearResults: clearSearchResults,
  closeSearchPanel,
} = useGlobalSearch()

function goToResult(path: string) {
  router.push(path)
  clearSearchResults()
}
</script>

<template>
  <header class="layout-topbar">
    <div class="layout-topbar__left">
      <div class="layout-module-badge"><el-icon><Monitor /></el-icon>{{ activeModule.label }}</div>
      <div class="layout-page-copy"><h1>{{ activeModule.label }}</h1><p>{{ activeModule.summary }}</p></div>
    </div>
    <div class="layout-topbar__right">
      <div class="layout-search" @focusout="closeSearchPanel">
        <el-icon class="layout-search-icon"><Search /></el-icon>
        <input v-model="searchKeyword" class="layout-search-input" type="text" placeholder="搜索员工 / 岗位 ..." @input="onSearchInput" @keyup.enter="onSearchInput" />
        <div v-if="showSearchPanel" class="search-dropdown">
          <div v-if="searchLoading" class="search-dropdown__loading"><span class="search-spinner"></span>搜索中...</div>
          <div v-else-if="searchResults.length === 0" class="search-dropdown__empty">未找到匹配结果</div>
          <template v-else>
            <div v-for="item in searchResults" :key="`${item.type}-${item.id}`" class="search-dropdown__item" @click="goToResult(item.path)">
              <span class="search-item-tag" :class="`is-${item.type}`">{{ item.type === 'employee' ? '员工' : '岗位' }}</span>
              <div class="search-item-info"><div class="search-item-name">{{ item.name }}</div><div class="search-item-code">{{ item.code }}</div></div>
            </div>
          </template>
        </div>
      </div>
      <TaskNotificationPanel v-model:visible="showTaskPanel" @navigate="router.push" @dismiss="taskStore.removeTask" @clear-finished="taskStore.clearFinished" />
      <div class="layout-chip"><el-icon><Lightning /></el-icon>Realtime</div>
    </div>
  </header>
</template>

<style scoped>
.layout-topbar { position: sticky; top: 0; z-index: 15; display: flex; align-items: center; justify-content: space-between; flex-wrap: nowrap; gap: 18px; min-height: 88px; padding: 18px 24px; border: 1px solid var(--app-border); border-radius: var(--app-radius-lg); background: var(--app-surface); box-shadow: var(--app-shadow-sm); }
.layout-topbar__left, .layout-topbar__right { display: flex; align-items: center; gap: 16px; }
.layout-topbar__left { flex: 1 1 auto; min-width: 0; }
.layout-topbar__right { flex: 0 0 auto; margin-left: auto; justify-content: flex-end; flex-wrap: nowrap; }
.layout-module-badge, .layout-chip { display: inline-flex; align-items: center; gap: 8px; padding: 10px 14px; border: 1px solid var(--app-border); border-radius: 999px; background: var(--app-bg-tertiary); color: var(--app-accent); font-size: 12px; font-weight: 700; }
.layout-page-copy h1 { margin: 0; color: var(--app-text-strong); font-size: 18px; font-weight: 800; letter-spacing: -0.03em; white-space: nowrap; }
.layout-page-copy p { margin: 4px 0 0; color: var(--app-text-muted); font-size: 12px; white-space: nowrap; }
.layout-search { position: relative; display: flex; align-items: center; }
.layout-search-icon { position: absolute; left: 14px; color: var(--app-text-muted); }
.layout-search-input { width: 280px; height: 44px; padding: 0 14px 0 40px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-control-surface); outline: none; color: var(--app-text); }
.layout-search-input:focus { border-color: var(--app-accent); box-shadow: 0 0 0 3px rgba(31, 157, 134, 0.12); }
.search-dropdown { position: absolute; top: 100%; right: 0; left: 0; z-index: 100; min-width: 320px; margin-top: 8px; overflow: hidden; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-overlay-surface); box-shadow: var(--app-shadow-lg); }
.search-dropdown__loading, .search-dropdown__empty { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 24px; color: var(--app-text-muted); font-size: 13px; }
.search-spinner { width: 16px; height: 16px; border: 2px solid rgba(37, 99, 235, 0.2); border-top-color: var(--app-primary); border-radius: 50%; animation: search-spin 0.6s linear infinite; }
@keyframes search-spin { to { transform: rotate(360deg); } }
.search-dropdown__item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background-color 0.15s ease; }
.search-dropdown__item:hover { background: var(--app-sidebar-hover); }
.search-item-tag { display: inline-flex; align-items: center; flex-shrink: 0; padding: 4px 10px; border-radius: 8px; font-size: 11px; font-weight: 700; }
.search-item-tag.is-employee { color: var(--app-accent); background: var(--app-accent-soft); }
.search-item-tag.is-post { color: var(--app-text); background: var(--app-primary-soft); }
.search-item-info { flex: 1; min-width: 0; }
.search-item-name { overflow: hidden; color: var(--app-text-strong); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.search-item-code { margin-top: 2px; color: var(--app-text-muted); font-size: 11px; }
@media (max-width: 1100px) { .layout-topbar { padding: 16px 18px; } .layout-search-input { width: 180px; } .layout-topbar__right { gap: 10px; } }
@media (max-width: 768px) { .layout-topbar { flex-direction: column; align-items: flex-start; } .layout-topbar__right { width: 100%; flex-wrap: wrap; } .layout-search, .layout-search-input { width: 100%; } }
</style>
