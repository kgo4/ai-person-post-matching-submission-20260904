<script setup lang="ts">
/**
 * 顶栏统一铃铛：**一个入口，两个分区**。
 *
 * 2026-09-04 合并：此前顶栏并排两个铃铛 ——
 *   ① 任务状态铃铛（后台任务进度：匹配/面试分析/PMS 分析）
 *   ② 站内通知铃铛（sys_notification）
 * 两个圆形图标并排，使用者分不清该点哪个。现在合并为一个铃铛，面板内用分段切换
 * 「通知 / 任务」，角标优先显示未读通知数；无未读但有进行中任务时显示任务数（蓝色）。
 *
 * 通知跳转规则：
 * - EMPLOYEE（提醒评估）→ 本人能力评估流程页；
 * - MATCHING_RECORD → 我的匹配结果；
 * - INTERVIEW（终面邀请 / 员工响应 / 结论更新）→ 管理端去 HR 终面追踪页，
 *   员工侧去本人能力画像（「我的视频沟通」区块在页面里）；
 * 其余类型只标已读不跳转，后续按 bizType 扩展。
 *
 * 任务分区：匹配任务只对 HR 可见（其他角色不会入队，见 matching-tasks store 的角色收口）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  pageMyNotifications,
  type NotificationItem,
} from '@/api/notification'
import { useTaskStore } from '@/store/modules/task'
import { hasPermission } from '@/utils/permission'

const router = useRouter()
const taskStore = useTaskStore()

const POLL_INTERVAL_MS = 30_000
const PAGE_SIZE = 10

const unreadCount = ref(0)
const panelVisible = ref(false)
const loading = ref(false)
const items = ref<NotificationItem[]>([])
const total = ref(0)
const current = ref(1)
const showUnreadOnly = ref(false)
/** 面板当前分区：通知（默认）/ 任务 */
const activeTab = ref<'notice' | 'task'>('notice')

const runningTaskCount = computed(() => taskStore.runningTasks.length)

/**
 * 角标：优先未读通知（红）；无未读但有任务时显示任务数
 * （进行中=蓝、仅已完成=绿），让「有东西可看」这件事一眼可见。
 */
const badgeText = computed(() => {
  if (unreadCount.value > 0) return unreadCount.value > 99 ? '99+' : String(unreadCount.value)
  const taskCount = runningTaskCount.value || taskStore.completedTasks.length
  return taskCount > 0 ? String(taskCount) : ''
})

const badgeTone = computed(() => {
  if (unreadCount.value > 0) return 'unread'
  return runningTaskCount.value > 0 ? 'running' : 'done'
})

const bellTitle = computed(() => {
  const parts: string[] = []
  if (unreadCount.value > 0) parts.push(`${unreadCount.value} 条未读通知`)
  if (runningTaskCount.value > 0) parts.push(`${runningTaskCount.value} 个任务进行中`)
  return parts.length ? parts.join('，') : '通知与任务'
})

const hasFinishedTasks = computed(() => taskStore.tasks.some(t => t.status !== 'running'))

/** 任务 → 菜单落点（与任务类型一一对应） */
const taskRouteMap: Record<string, string> = {
  matching: '/matching/tasks',
  'video-analysis': '/employee/ability-profile/live-interview',
  'pms-analysis': '/employee/pms-analysis',
}

function taskTypeLabel(type: string): string {
  if (type === 'matching') return '匹配'
  if (type === 'video-analysis') return '面试分析'
  return 'PMS 分析'
}

let pollTimer: number | null = null

async function refreshUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data ?? 0
  } catch {
    // 静默：未读数拉取失败不打扰用户，下个周期重试
  }
}

async function load(page = 1) {
  loading.value = true
  try {
    const res = await pageMyNotifications(page, PAGE_SIZE)
    const all = res.data?.records ?? []
    items.value = showUnreadOnly.value ? all.filter(item => item.readStatus === 0) : all
    total.value = res.data?.total ?? 0
    current.value = page
  } catch (e: any) {
    ElMessage.error(e?.message || '通知加载失败')
  } finally {
    loading.value = false
  }
}

function togglePanel() {
  panelVisible.value = !panelVisible.value
  if (panelVisible.value) {
    // 有未读就打开通知区，否则有任务就打开任务区 —— 别让用户点开再自己找
    activeTab.value = unreadCount.value > 0 || taskStore.tasks.length === 0 ? 'notice' : 'task'
    void load(1)
  }
}

function targetOf(item: NotificationItem): string | null {
  if (item.bizType === 'EMPLOYEE') return '/employee/ability-profile/assessment'
  /*
   * 匹配结果通知（bizType=MATCHING_RECORD）两端共用同一个 bizType，落点必须按身份分流：
   *   员工   → /matching/my-result（只读页，服务端按登录身份收口为本人数据）
   *   管理端 → /matching/result（匹配结果控制台）
   *
   * 2026-09-04 加固：原实现**无条件**送 /matching/my-result。对员工是对的，
   * 但一旦管理端也收到同类通知（补投递 / 历史数据），就会打开员工版只读页 ——
   * 管理端持有 NOTIFICATION:SELF 能过守卫，而后端按 isSelfOnlyCaller() 判定他不是「仅本人」
   * 调用者，于是走全量 page()，结果是「我的匹配结果」页面上出现了全员数据。
   *
   * 判据刻意用 MATCHING:READ 而**不是** EMPLOYEE:READ：后端 MatchingRecordApiFacade
   * 的 isSelfOnlyCaller() 正是「无 MATCHING:READ/EXECUTE/APPROVE」才把数据范围收成本人。
   * 前端跳转与后端收口用**同一个权限码**，两端对「这条通知该去哪」才不会漂移。
   */
  if (item.bizType === 'MATCHING_RECORD') {
    return hasPermission('MATCHING:READ') ? '/matching/result' : '/matching/my-result'
  }
  // 终面类通知两端共用同一个 bizType，落点按身份分开：
  // 有 EMPLOYEE:READ（管理端）→ HR 终面追踪页；员工 → 本人能力画像（内含「我的视频沟通」）。
  if (item.bizType === 'INTERVIEW') {
    return hasPermission('EMPLOYEE:READ')
      ? '/matching/communication-interview'
      : '/employee/ability-profile'
  }
  return null
}

async function handleClick(item: NotificationItem) {
  if (item.readStatus === 0) {
    try {
      await markNotificationRead(item.id)
      item.readStatus = 1
      void refreshUnread()
    } catch {
      // 已读失败不打扰跳转
    }
  }
  const target = targetOf(item)
  if (target) {
    panelVisible.value = false
    router.push(target)
  }
}

/** 「全部已读」按当前分区语义执行：通知=标记已读，任务=清掉已完成项 */
async function handleReadAll() {
  if (activeTab.value === 'task') {
    taskStore.markAllNotified()
    taskStore.clearFinished()
    return
  }
  try {
    await markAllNotificationsRead()
    ElMessage.success('已全部标记为已读')
    void refreshUnread()
    void load(current.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

function handlePageChange(page: number) {
  void load(page)
}

function formatTime(raw?: string | null) {
  if (!raw) return ''
  return raw.replace('T', ' ').slice(0, 16)
}

function goToTask(taskId: string, type: string) {
  taskStore.markNotified(taskId)
  panelVisible.value = false
  router.push(taskRouteMap[type] || '/workbench')
}

onMounted(() => {
  void refreshUnread()
  pollTimer = window.setInterval(refreshUnread, POLL_INTERVAL_MS)
})

onUnmounted(() => {
  if (pollTimer != null) window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="notice-bell">
    <button class="notice-bell__btn" type="button" :title="bellTitle" @click="togglePanel">
      <el-icon :size="18"><Bell /></el-icon>
      <span v-if="badgeText" class="notice-bell__badge" :class="`is-${badgeTone}`">{{ badgeText }}</span>
    </button>

    <!-- 点击面板外任意处收起（比 focusout 可靠：面板内点击不会误关） -->
    <div v-if="panelVisible" class="notice-bell__overlay" @click="panelVisible = false"></div>

    <div v-if="panelVisible" class="notice-panel">
      <header class="notice-panel__head">
        <div class="notice-panel__tabs">
          <button
            type="button"
            class="notice-panel__tab"
            :class="{ 'is-active': activeTab === 'notice' }"
            @click="activeTab = 'notice'"
          >
            通知
            <em v-if="unreadCount > 0">{{ unreadCount > 99 ? '99+' : unreadCount }}</em>
          </button>
          <button
            v-if="taskStore.tasks.length > 0"
            type="button"
            class="notice-panel__tab"
            :class="{ 'is-active': activeTab === 'task' }"
            @click="activeTab = 'task'"
          >
            任务
            <em v-if="runningTaskCount > 0">{{ runningTaskCount }}</em>
          </button>
        </div>
        <button
          v-if="activeTab === 'notice' ? unreadCount > 0 : hasFinishedTasks"
          class="notice-panel__read-all"
          type="button"
          @click="handleReadAll"
        >
          全部已读
        </button>
      </header>

      <!-- 通知区 -->
      <template v-if="activeTab === 'notice'">
        <div v-if="loading" class="notice-panel__empty">加载中...</div>
        <div v-else-if="items.length === 0" class="notice-panel__empty">暂无通知</div>
        <ul v-else class="notice-panel__list">
          <li
            v-for="item in items"
            :key="item.id"
            class="notice-panel__item"
            :class="{ 'is-unread': item.readStatus === 0 }"
            @click="handleClick(item)"
          >
            <span class="notice-panel__dot" :class="{ 'is-unread': item.readStatus === 0 }"></span>
            <div class="notice-panel__copy">
              <b>{{ item.title }}</b>
              <p>{{ item.content }}</p>
            </div>
            <time>{{ formatTime(item.createdTime) }}</time>
          </li>
        </ul>

        <footer v-if="total > PAGE_SIZE" class="notice-panel__pager">
          <el-pagination
            :current-page="current"
            :page-size="PAGE_SIZE"
            :total="total"
            layout="prev, pager, next"
            small
            background
            @current-change="handlePageChange"
          />
        </footer>
      </template>

      <!-- 任务区 -->
      <template v-else>
        <div v-if="taskStore.tasks.length === 0" class="notice-panel__empty">暂无进行中的任务</div>
        <ul v-else class="notice-panel__list notice-panel__tasks">
          <li
            v-for="task in taskStore.tasks"
            :key="task.id"
            class="task-item"
            :class="task.status"
            @click="goToTask(task.id, task.type)"
          >
            <span class="task-item__icon">
              <i v-if="task.status === 'running'" class="task-spinner"></i>
              <i v-else-if="task.status === 'completed'" class="task-icon is-done">✓</i>
              <i v-else class="task-icon is-failed">✕</i>
            </span>
            <span class="task-item__info">
              <b>{{ task.refName || `任务 #${task.refId}` }}</b>
              <small>
                <em class="task-type-tag">{{ taskTypeLabel(task.type) }}</em>
                {{ task.message || '处理中...' }}
              </small>
            </span>
            <button
              v-if="task.status !== 'running'"
              class="task-item__dismiss"
              type="button"
              title="移除"
              @click.stop="taskStore.removeTask(task.id)"
            >
              <el-icon><Close /></el-icon>
            </button>
          </li>
        </ul>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* 白蓝色调：底色沿用 --app-surface 白，强调色统一走 --app-primary 蓝 */
.notice-bell { position: relative; }
.notice-bell__btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--app-border);
  border-radius: 50%;
  background: var(--app-surface);
  color: var(--app-text-secondary);
  cursor: pointer;
  box-shadow: var(--app-shadow-sm);
  transition: border-color 0.15s ease, color 0.15s ease;
}
.notice-bell__btn:hover { border-color: var(--app-primary); color: var(--app-primary); }
.notice-bell__badge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  line-height: 18px;
  text-align: center;
}
/* 未读通知=红；任务进行中=蓝（脉动）；任务已完成=绿 */
.notice-bell__badge.is-unread { background: var(--app-danger, #f04438); }
.notice-bell__badge.is-running { background: var(--app-primary); animation: notice-pulse 2s ease-in-out infinite; }
.notice-bell__badge.is-done { background: var(--app-success, #12b76a); }
@keyframes notice-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.14); }
}

.notice-bell__overlay { position: fixed; inset: 0; z-index: 119; }

.notice-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 120;
  width: 360px;
  max-height: 460px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-overlay-surface, var(--app-surface));
  box-shadow: var(--app-shadow-lg);
}
.notice-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--app-divider);
}
.notice-panel__tabs { display: flex; align-items: center; gap: 4px; }
.notice-panel__tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.notice-panel__tab:hover { color: var(--app-primary); background: var(--app-surface-soft); }
.notice-panel__tab.is-active { color: var(--app-primary); background: var(--app-primary-soft, rgba(47, 107, 255, 0.1)); }
.notice-panel__tab em {
  min-width: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--app-danger, #f04438);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 800;
  line-height: 16px;
  text-align: center;
}
.notice-panel__read-all {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-primary);
  font-size: 12px;
  cursor: pointer;
}
.notice-panel__empty { padding: 32px; color: var(--app-text-muted); font-size: 13px; text-align: center; }
.notice-panel__list { overflow-y: auto; margin: 0; padding: 0; list-style: none; }
.notice-panel__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-divider);
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.notice-panel__item:last-child { border-bottom: 0; }
.notice-panel__item:hover { background: var(--app-surface-soft, var(--app-sidebar-hover)); }
.notice-panel__dot {
  width: 7px;
  height: 7px;
  margin-top: 6px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: transparent;
}
.notice-panel__dot.is-unread { background: var(--app-primary); }
.notice-panel__copy { min-width: 0; flex: 1; }
.notice-panel__copy b { display: block; color: var(--app-text-strong); font-size: 13px; font-weight: 650; }
.notice-panel__item.is-unread .notice-panel__copy b { font-weight: 750; }
.notice-panel__copy p {
  margin: 3px 0 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.notice-panel__item time { flex: 0 0 auto; color: var(--app-text-muted); font-size: 11px; }
.notice-panel__pager { display: flex; justify-content: center; padding: 10px; border-top: 1px solid var(--app-divider); }

/* 任务区（沿用原任务面板的视觉，间距对齐通知区） */
.notice-panel__tasks { padding: 6px; }
.task-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.task-item:hover { background: var(--app-surface-soft); }
.task-item__icon { display: flex; align-items: center; justify-content: center; flex: 0 0 28px; width: 28px; height: 28px; }
.task-spinner {
  width: 20px;
  height: 20px;
  border: 2.5px solid var(--app-primary-soft, rgba(47, 107, 255, 0.2));
  border-top-color: var(--app-primary);
  border-radius: 50%;
  animation: notice-spin 0.8s linear infinite;
}
@keyframes notice-spin { to { transform: rotate(360deg); } }
.task-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}
.task-icon.is-done { color: var(--app-success, #12b76a); background: var(--app-success-soft, rgba(18, 183, 106, 0.12)); }
.task-icon.is-failed { color: var(--app-danger, #f04438); background: var(--app-danger-soft, rgba(240, 68, 56, 0.12)); }
.task-item__info { min-width: 0; flex: 1; }
.task-item__info b {
  display: block;
  overflow: hidden;
  color: var(--app-text-strong);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-item__info small {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-type-tag {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--app-primary-soft, rgba(47, 107, 255, 0.1));
  color: var(--app-primary);
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}
.task-item__dismiss {
  display: grid;
  place-items: center;
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.task-item__dismiss:hover { color: var(--app-danger); background: var(--app-danger-soft, rgba(240, 68, 56, 0.12)); }
</style>
