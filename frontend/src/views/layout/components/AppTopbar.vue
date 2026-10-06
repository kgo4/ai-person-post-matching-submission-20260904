<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { filterSidebarModules, getSidebarModules, type SidebarModule } from '@/config/sidebar-menu'
import { useFeatureSearch } from '../useFeatureSearch'
import { buildBreadcrumb } from '../breadcrumb'
import NotificationBell from './NotificationBell.vue'

// activeModule 由 layout 解析（含跨前缀模块的特殊映射），
// 面包屑直接复用它，不再自行推断模块归属。
const props = defineProps<{ activeModule: SidebarModule }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
/** 当前主角色（剥掉 ROLE_ 前缀）：被功能搜索、面包屑、账号区三处共用，只解析一次 */
const currentRole = computed(() => (userStore.roles[0] || '').replace(/^ROLE_/, '').toUpperCase())
const searchPermissions = computed(() => userStore.permissions || [])

/**
 * 顶栏搜索 = 功能搜索（页面导航），不是业务数据搜索。
 * 索引来自侧边栏菜单并按角色 + 权限过滤，所以「搜得到 = 点进去进得去」。
 */
const {
  keyword: searchKeyword,
  results: searchResults,
  showPanel: showSearchPanel,
  onSearchInput,
  clearResults: clearSearchResults,
  closeSearchPanel,
} = useFeatureSearch(currentRole, searchPermissions)

const userName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.username || '系统用户')
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase())
/**
 * 头像地址。
 *
 * 直接使用后端下发的值：它要么是站点根相对路径（/uploads/avatars/xxx.png），
 * 要么是完整 URL —— 两种都能直接给 img 用。
 * 关键是**不要**再拼 VITE_APP_BASE_API（/api），/uploads/** 由后端静态资源映射
 * 暴露在站点根下（vite 也单独代理了 /uploads），拼成 /api/uploads/... 会 404。
 */
const avatarUrl = computed(() => userStore.avatar || '')

/**
 * 面包屑子项用「按角色 + 权限过滤后的子项」，
 * 避免出现侧栏看不到、面包屑却显示的越权提示。
 */
const visibleChildren = computed(() => {
  const modules = filterSidebarModules(getSidebarModules(), currentRole.value, userStore.permissions)
  return modules.find(module => module.key === props.activeModule.key)?.children || []
})

const breadcrumbs = computed(() =>
  buildBreadcrumb(
    route.path,
    { label: props.activeModule.label, path: props.activeModule.path, children: visibleChildren.value },
    route.meta?.title as string | undefined,
  ),
)

function goToResult(path: string) {
  router.push(path)
  clearSearchResults()
}

/**
 * 顶栏头像 = 个人中心的唯一入口，点击直接跳转，不再有下拉。
 *
 * 分工（2026-09-04 调整）：顶栏只管「进个人中心」，退出登录移到侧边栏底部 ——
 * 两个账号入口各管一件事、职责不重叠。此前两者都塞在顶栏下拉里，
 * 结果是侧边栏底部的身份信息（姓名/角色）无处安放。
 */
function goProfile() {
  router.push('/profile')
}
</script>

<template>
  <header class="layout-topbar">
    <div class="layout-topbar__left">
      <div class="layout-page-copy">
        <h1>{{ activeModule.label }}</h1>
        <nav class="layout-breadcrumb" aria-label="面包屑">
          <template v-for="(crumb, index) in breadcrumbs" :key="`${crumb.label}-${index}`">
            <span v-if="index > 0" class="layout-breadcrumb__sep">/</span>
            <button
              v-if="crumb.path && index < breadcrumbs.length - 1"
              class="layout-breadcrumb__item"
              type="button"
              @click="router.push(crumb.path!)"
            >
              {{ crumb.label }}
            </button>
            <span v-else class="layout-breadcrumb__item is-current">{{ crumb.label }}</span>
          </template>
        </nav>
      </div>
    </div>
    <div class="layout-topbar__right">
      <div class="layout-search" @focusout="closeSearchPanel">
        <el-icon class="layout-search-icon"><Search /></el-icon>
        <input v-model="searchKeyword" class="layout-search-input" type="text" placeholder="搜索功能，如「匹配结果」" @input="onSearchInput" @keyup.enter="onSearchInput" />
        <div v-if="showSearchPanel" class="search-dropdown">
          <div v-if="searchResults.length === 0" class="search-dropdown__empty">未找到相关功能</div>
          <template v-else>
            <div v-for="item in searchResults" :key="`${item.moduleKey}-${item.path}-${item.label}`" class="search-dropdown__item" @click="goToResult(item.path)">
              <span class="search-item-tag is-feature">{{ item.moduleLabel }}</span>
              <div class="search-item-info"><div class="search-item-name">{{ item.label }}</div><div class="search-item-code">{{ item.path }}</div></div>
            </div>
          </template>
        </div>
      </div>
      <!-- 统一铃铛：任务与通知合并为一个入口，面板内分「通知 / 任务」两区 -->
      <NotificationBell />
      <!--
        顶栏右上角只保留头像：姓名与角色已下移到侧边栏底部（那里空间更宽裕，
        也不会把顶栏右侧挤成三行）。点击即进个人中心，没有下拉。
        无头像时回落成姓名首字，避免出现一个空圆圈。
      -->
      <button
        class="layout-avatar"
        type="button"
        :title="`${userName} · 进入个人中心`"
        :aria-label="`${userName}，进入个人中心`"
        @click="goProfile"
      >
        <img v-if="avatarUrl" :src="avatarUrl" :alt="userName" />
        <template v-else>{{ userInitial }}</template>
      </button>
    </div>
  </header>
</template>

<style scoped>
/*
 * 顶栏 = 内容面板（.layout-surface）的**表头行**，不再是一根浮在灰底上的散装横条。
 *
 * 原版式是「灰底上浮动一条顶栏 → 20px 空隙 → 页面自己的 hero 卡片」，
 * 两段式堆叠让顶部显得又空又挤（两行标题 + 两层留白）。
 * 现在顶栏与页面内容同处一张白卡：靠一条分隔线与内容区隔开，视觉上是一块整体。
 * 注意：因此去掉了 sticky —— 面板内做 sticky 会脱离圆角卡片、看起来像断裂。
 */
.layout-topbar { position: relative; z-index: 15; display: flex; align-items: center; justify-content: space-between; flex-wrap: nowrap; gap: 18px; min-height: 64px; padding: 14px 20px; border: 0; border-bottom: 1px solid var(--app-divider); border-radius: 0; background: transparent; box-shadow: none; }
.layout-topbar__left, .layout-topbar__right { display: flex; align-items: center; gap: 16px; }
.layout-topbar__left { flex: 1 1 auto; min-width: 0; }
.layout-topbar__right { flex: 0 0 auto; margin-left: auto; justify-content: flex-end; flex-wrap: nowrap; }
.layout-page-copy h1 { margin: 0; color: var(--app-text-strong); font-size: 18px; font-weight: 800; letter-spacing: -0.03em; white-space: nowrap; }
.layout-breadcrumb { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.layout-breadcrumb__sep { color: var(--app-text-muted); font-size: 11px; }
.layout-breadcrumb__item { padding: 0; border: 0; background: transparent; color: var(--app-text-muted); font-size: 12px; white-space: nowrap; }
button.layout-breadcrumb__item { cursor: pointer; }
button.layout-breadcrumb__item:hover { color: var(--app-accent); }
.layout-breadcrumb__item.is-current { color: var(--app-text-secondary); font-weight: 600; }
.layout-search { position: relative; display: flex; align-items: center; }
.layout-search-icon { position: absolute; left: 14px; color: var(--app-text-muted); }
.layout-search-input { width: 250px; height: 38px; padding: 0 14px 0 38px; border: 1px solid var(--app-border); border-radius: 20px; background: var(--app-surface); outline: none; color: var(--app-text); box-shadow: var(--app-shadow-sm); }
.layout-search-input:focus { border-color: var(--app-accent); box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14); }
.search-dropdown { position: absolute; top: 100%; right: 0; left: 0; z-index: 100; min-width: 320px; margin-top: 8px; overflow: hidden; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-overlay-surface); box-shadow: var(--app-shadow-lg); }
/* 检索是纯本地菜单过滤，没有 loading 态（不转圈，避免一帧闪烁反而像卡顿） */
.search-dropdown__empty { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 24px; color: var(--app-text-muted); font-size: 13px; }
.search-dropdown__item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background-color 0.15s ease; }
.search-dropdown__item:hover { background: var(--app-sidebar-hover); }
/* 标签位展示功能所属模块，同名页面（如「差距诊断」）靠它区分 */
.search-item-tag { display: inline-flex; align-items: center; flex-shrink: 0; padding: 4px 10px; border-radius: 8px; color: var(--app-accent); background: var(--app-accent-soft); font-size: 11px; font-weight: 700; }
.search-item-info { flex: 1; min-width: 0; }
.search-item-name { overflow: hidden; color: var(--app-text-strong); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.search-item-code { margin-top: 2px; color: var(--app-text-muted); font-size: 11px; }
/*
 * 顶栏头像：只有一个圆形头像，没有姓名/角色/下拉箭头。
 * 不用药丸形容器（那是"头像 + 两行文字"的载体），改成一个 34px 圆按钮，
 * hover 时加一圈浅色轮廓表示可点。
 */
.layout-avatar { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; padding: 0; overflow: hidden; border: 1px solid var(--app-border); border-radius: 50%; color: #fff; background: linear-gradient(135deg, var(--app-primary), var(--app-accent-alt)); cursor: pointer; font-size: 13px; font-weight: 800; transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease; }
.layout-avatar img { width: 100%; height: 100%; object-fit: cover; }
.layout-avatar:hover { border-color: var(--app-accent); box-shadow: 0 0 0 3px var(--app-accent-soft); }
.layout-avatar:focus-visible { outline: none; border-color: var(--app-accent); box-shadow: 0 0 0 3px var(--app-accent-soft); }
@media (max-width: 1100px) { .layout-topbar { padding: 16px 18px; } .layout-search-input { width: 180px; } .layout-topbar__right { gap: 10px; } }
@media (max-width: 768px) { .layout-topbar { flex-direction: column; align-items: flex-start; } .layout-topbar__right { width: 100%; flex-wrap: wrap; } .layout-search, .layout-search-input { width: 100%; } }
</style>
