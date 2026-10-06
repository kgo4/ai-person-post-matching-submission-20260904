<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, SwitchButton } from '@element-plus/icons-vue'
import { filterSidebarModules, getSidebarModules, type SidebarModule } from '@/config/sidebar-menu'
import { useUserStore } from '@/store/modules/user'
import { primaryRoleLabel } from '@/utils/role-label'

const props = defineProps<{ activeModule: SidebarModule }>()
/**
 * 退出登录的唯一入口。放在侧边栏底部而不是顶栏：
 * 顶栏只有头像（点击进个人中心），两处账号入口职责不重叠。
 */
const emit = defineEmits<{ logout: [] }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const primaryRole = computed(() => (userStore.roles[0] || '').replace(/^ROLE_/, '').toUpperCase())
/**
 * 菜单过滤传**全部角色**而不是 `roles[0]`：
 * 多角色账号（例如同时挂 EMPLOYEE + PLATFORM_ADMIN）若只取首位，
 * 首位不是某菜单声明的角色时该菜单会整体消失，且没有任何报错。
 * filterSidebarModules 内部按「任一命中」放行，并对超级管理员整体放行。
 */
const modules = computed(() => filterSidebarModules(getSidebarModules(), userStore.roles, userStore.permissions))

/* ---------- 底部账号区 ---------- */
const userName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.username || '系统用户')
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase())
/** 头像直接取后端下发值（站点根相对路径或完整 URL 都能直接给 img；不要再拼 /api 前缀） */
const avatarUrl = computed(() => userStore.avatar || '')
const userRoleLabel = computed(() => primaryRoleLabel(userStore.roles))

function handleAccountCommand(command: string) {
  if (command === 'logout') emit('logout')
}

// 排序已在 filterSidebarModules 内按 MODULE_ORDER 完成
const visibleModules = computed(() => modules.value)

/**
 * 被手动收起子项的模块 key。
 *
 * 子项默认跟随「当前模块」自动展开（这是进入某模块后最想要的状态）；
 * 但用户点同一个模块想收起来时，得有个地方记住「这次是收起的」，
 * 否则 activeModule 没变、子项立刻又展开 —— 点击看起来毫无反应。
 * 换模块时清掉旧记录，让新模块恢复自动展开。
 */
const manuallyCollapsed = ref<string[]>([])

watch(
  () => props.activeModule.key,
  (key) => {
    manuallyCollapsed.value = manuallyCollapsed.value.filter(item => item !== key)
  },
)

// activeModule comes from the layout route resolver and is intentionally not
// permission-aware. Always resolve the active module from the filtered list so
// an unauthorized child cannot reappear when its parent is expanded.
const activeChildren = computed(() =>
  modules.value.find(module => module.key === props.activeModule.key)?.children || [])

/** 某模块的子项是否展开：是当前模块 + 没被手动收起 + 确实有子项 */
function isSubnavOpen(moduleKey: string) {
  return props.activeModule.key === moduleKey
    && !manuallyCollapsed.value.includes(moduleKey)
    && activeChildren.value.length > 0
}

function isActiveChild(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function handleModuleClick(item: SidebarModule) {
  // 点当前模块 = 收起/展开它的子项，而不是"再跳一次"（已经在里面了）
  if (props.activeModule.key === item.key) {
    manuallyCollapsed.value = manuallyCollapsed.value.includes(item.key)
      ? manuallyCollapsed.value.filter(key => key !== item.key)
      : [...manuallyCollapsed.value, item.key]
    return
  }
  router.push(item.path)
}
</script>

<template>
  <aside class="layout-sidebar">
    <div class="sidebar-logo" @click="router.push('/workbench')">
      <!--
        品牌块两行，自上而下：先图标，再团队名（小字）。
        不再显示系统名「多源异构岗位与能力图谱」—— 200px 栏宽里 12 个汉字必然折行，
        而登录页已经承担了系统名的展示，侧边栏留一个标识即可。

        图标为正方形，按 40px 显示尺寸分别导出 1x/2x/3x 走 srcset ——
        交一张大图让浏览器缩的滤波质量不如预先按显示尺寸导出，这是更早一版图标发糊的原因。
        alt 留空（纯装饰）：团队名就在下一行，读屏再播报一遍图标名是重复信息。
      -->
      <img
        class="sidebar-logo-icon"
        src="/logo-mark.png"
        :srcset="`/logo-mark.png 1x, /logo-mark@2x.png 2x, /logo-mark@3x.png 3x`"
        alt=""
        width="40"
        height="40"
      />
      <span class="sidebar-logo-team">KGOAIspace</span>
    </div>

    <nav class="sidebar-nav">
      <div v-for="item in visibleModules" :key="item.key" class="sidebar-nav-group">
        <button
          class="sidebar-nav-item"
          :class="{ 'sidebar-nav-item--active': props.activeModule.key === item.key }"
          :title="item.label"
          :aria-expanded="isSubnavOpen(item.key)"
          @click="handleModuleClick(item)"
        >
          <span class="sidebar-nav-icon">
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
          </span>
          <div class="sidebar-nav-copy">
            <span class="sidebar-nav-label">{{ item.label }}</span>
          </div>
          <el-icon v-if="item.children.length" class="sidebar-nav-caret" :class="{ 'is-open': isSubnavOpen(item.key) }">
            <ArrowRight />
          </el-icon>
        </button>

        <div v-if="isSubnavOpen(item.key)" class="sidebar-subnav">
          <button
            v-for="child in activeChildren"
            :key="child.path"
            class="sidebar-subnav-item"
            :class="{ 'sidebar-subnav-item--active': isActiveChild(child.path) }"
            @click="router.push(child.path)"
          >
            <span class="sidebar-subnav-dot"></span>
            {{ child.label }}
            <span v-if="child.beta" class="sidebar-beta-badge">Beta</span>
          </button>
        </div>
      </div>
    </nav>

    <!--
      底部账号区：头像 + 用户名，用户名下方是角色。
      点击整块只弹「退出登录」—— 个人中心由顶栏头像进入，两个入口各管一件事，
      不在这里再放一个「个人中心」造成重复。
      placement=top-start 让菜单朝上弹出，否则会被视口底部裁掉。
    -->
    <el-dropdown class="sidebar-account" trigger="click" placement="top-start" @command="handleAccountCommand">
      <button class="sidebar-account__trigger" type="button" :title="`${userName}（${userRoleLabel}）`">
        <span class="sidebar-account__avatar">
          <img v-if="avatarUrl" :src="avatarUrl" :alt="userName" />
          <template v-else>{{ userInitial }}</template>
        </span>
        <span class="sidebar-account__copy">
          <b>{{ userName }}</b>
          <small>{{ userRoleLabel }}</small>
        </span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="logout">
            <el-icon><SwitchButton /></el-icon>退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </aside>
</template>

<style scoped>
/*
 * 侧边栏承担导航 + 底部账号区：
 *   · 顶部 = 品牌标识（只有图标 + 团队名，不再有系统名）；
 *   · 底部 = 头像 + 姓名 + 角色，点击只弹「退出登录」；
 *   · 个人中心仍由顶栏右上角头像进入（职责不重叠）。
 * 主题切换、导航折叠仍保持移除状态。
 */
.layout-sidebar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  width: 200px;
  height: 100vh;
  margin: 0;
  padding: 22px 12px 14px;
  border: 0;
  border-right: 1px solid var(--app-divider);
  border-radius: 0;
  background: var(--app-surface);
  box-shadow: 2px 0 18px rgba(53, 68, 112, 0.04);
}

/*
 * 品牌块：图标在上、团队名（小字）在下，整体居中。
 * 居中是因为这里只有两个居中的元素，左对齐会让 40px 图标与下方导航项的图标
 * 看起来"该对齐又差一点"；居中则明确是"标识区"而非"列表第一项"。
 */
.sidebar-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 2px 4px 18px;
  cursor: pointer;
}

/*
 * 品牌图标：正方形 40px。图片已按 1x/2x/3x 预渲染（40/80/120），
 * 这里保持 40px 显示尺寸即整数倍缩放，不依赖 object-fit 二次缩放。
 */
.sidebar-logo-icon {
  display: block;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  object-fit: contain;
  transition: transform 0.2s ease;
}

.sidebar-logo:hover .sidebar-logo-icon {
  transform: scale(1.04);
}

/* 团队名：弱信息，小字，落在图标正下方 */
.sidebar-logo-team {
  color: var(--app-text-muted);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.sidebar-nav-group + .sidebar-nav-group {
  margin-top: 3px;
}

.sidebar-nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  padding: 7px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.22s ease, border-color 0.22s ease;
}

.sidebar-nav-item:hover {
  background: var(--app-sidebar-hover);
  border-color: var(--app-border);
}

.sidebar-nav-item--active {
  color: #fff;
  background: linear-gradient(100deg, var(--app-primary), var(--app-accent-alt));
  border-color: transparent;
  box-shadow: var(--app-glow);
}

.sidebar-nav-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 8px;
  color: var(--app-text-secondary);
  transition: color 0.22s ease;
}

.sidebar-nav-item--active .sidebar-nav-icon {
  color: #fff;
}

.sidebar-nav-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  text-align: left;
  line-height: 1.3;
}

.sidebar-nav-label {
  color: var(--app-text-strong);
  font-size: 12px;
  font-weight: 650;
}

.sidebar-nav-item--active .sidebar-nav-label {
  color: #fff;
}

/* 子项展开指示：点同一项可收起，得让用户看得出"这里能收" */
.sidebar-nav-caret {
  margin-left: auto;
  color: var(--app-text-muted);
  font-size: 12px;
  transition: transform 0.2s ease, color 0.2s ease;
}

.sidebar-nav-caret.is-open {
  transform: rotate(90deg);
}

.sidebar-nav-item--active .sidebar-nav-caret {
  color: #fff;
}

.sidebar-subnav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 6px 0 0 32px;
  padding: 6px 0 6px 9px;
  border-left: 1.5px solid var(--app-border);
  animation: subnav-expand 0.25s ease;
}

@keyframes subnav-expand {
  from {
    opacity: 0;
    max-height: 0;
  }
  to {
    opacity: 1;
    max-height: 600px;
  }
}

.sidebar-subnav-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.sidebar-subnav-item:hover {
  color: var(--app-accent);
  background: var(--app-accent-soft);
}

.sidebar-subnav-item--active {
  color: var(--app-accent);
  background: var(--app-accent-soft);
  font-weight: 600;
}

.sidebar-subnav-dot {
  width: 5px;
  height: 5px;
  flex-shrink: 0;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
  transition: opacity 0.18s ease;
}

.sidebar-subnav-item--active .sidebar-subnav-dot {
  opacity: 1;
}

.sidebar-beta-badge {
  margin-left: auto;
  padding: 2px 7px;
  border-radius: 100px;
  color: var(--app-accent);
  background: var(--app-accent-soft);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  animation: beta-pulse 3s ease-in-out infinite;
}

@keyframes beta-pulse {
  0%,
  100% {
    opacity: 0.8;
  }
  50% {
    opacity: 1;
  }
}

/* ---------- 底部账号区 ---------- */
/* nav 是 flex:1，所以这里天然贴底；再加一条分隔线与导航列表拉开层次 */
.sidebar-account {
  flex: 0 0 auto;
  width: 100%;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--app-divider);
}

/*
 * el-dropdown 会给根元素套一层，触发器自身要撑满宽度，
 * 否则鼠标只能点中文字那一小块，而不是整行。
 */
.sidebar-account__trigger {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.sidebar-account__trigger:hover {
  border-color: var(--app-border);
  background: var(--app-sidebar-hover);
}

.sidebar-account__avatar {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--app-primary), var(--app-accent-alt));
  font-size: 12px;
  font-weight: 800;
}

.sidebar-account__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 姓名在上、角色在下。两者都要能省略，否则长名字会把 200px 栏宽撑开 */
.sidebar-account__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.3;
}

.sidebar-account__copy b {
  overflow: hidden;
  max-width: 118px;
  color: var(--app-text-strong);
  font-size: 12.5px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-account__copy small {
  overflow: hidden;
  max-width: 118px;
  margin-top: 1px;
  color: var(--app-text-muted);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .layout-sidebar {
    position: relative;
    top: auto;
    width: 100%;
    height: auto;
    min-height: 0;
    padding: 16px 16px 0;
    border-right: 0;
    border-bottom: 1px solid var(--app-divider);
  }

  /* 窄屏侧边栏变成顶部横条，nav 不再撑满高度，账号区跟着落到最后一行 */
  .sidebar-account {
    margin-top: 8px;
  }

  /*
   * 118px 的截断上限是为 200px 栏宽定的；窄屏这里是一条 700px+ 的横条，
   * 再按 118px 截断就成了"明明有空间却显示省略号"（实测 name.w=118 而可用宽度 688）。
   * 放开上限即可 —— 外层 copy 有 min-width:0、b/small 有 overflow:hidden，
   * 名字过长时仍由省略号收口，不会撑破横条。
   */
  .sidebar-account__copy b,
  .sidebar-account__copy small {
    max-width: none;
  }
}
</style>
