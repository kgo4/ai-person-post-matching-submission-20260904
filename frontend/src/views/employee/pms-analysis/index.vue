<script setup lang="ts">
/**
 * PMS 项目分析（HR 独立功能）。
 *
 * 【这个页面解决什么】
 * PMS 平台上的人与本平台无关：同步过来时他们**还没有本系统账号**，
 * 等员工注册之后 HR 才把两边绑成一个员工。所以列表里的「未绑定」不是漏数据，
 * 而是这条链路最需要被处理的状态 —— 未绑定的排在前面，且随时可以看绑定冲突。
 *
 * 【与旧实现的区别】
 * 旧页面挂在员工档案的逐行按钮上，以 empId 为入口，同步时还会往人员库插影子档案。
 * 现在入口是 PMS 人员，同步只落映射（不写 emp_employee），非 PMS 员工完全不受影响。
 *
 * 【口径】能分析、导入才要求绑定：
 * 未绑定也能跑分析看结果（分析只依赖 PMS 数据），只有「写入人员能力画像」需要 empId。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Search } from '@element-plus/icons-vue'
import {
  analyzePmsUser,
  bindPmsRosterUser,
  getPmsAnalysisDetail,
  getPmsRoster,
  getPmsUserAnalysisHistory,
  importPmsAbilities,
  syncPmsUsers,
  unbindPmsRosterUser,
} from '@/api/ability-source'
import type { PmsAnalysisTask, PmsRosterItem, PmsRosterResponse } from '@/api/ability-source'
import { pageEmployees } from '@/api/employee'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { useTaskStore } from '@/store/modules/task'
import {
  analysisStatusMeta,
  bindBlockReason,
  emptyRosterReason,
  employeeDisplayName,
  importActionState,
  pmsDisplayName,
  rosterBindingMeta,
  rosterMatchesKeyword,
  selectableConflictEmpIds,
  summarizeSyncResult,
} from './pms-roster'

const taskStore = useTaskStore()

const loading = ref(false)
const syncing = ref(false)
const roster = ref<PmsRosterResponse | null>(null)
/** 加载/同步失败的原因。常驻展示，不用 toast —— 失败后用户要能对着原因排查 */
const loadError = ref('')

const keyword = ref('')
const boundFilter = ref<'all' | 'unbound' | 'bound'>('all')
const analysisMonths = ref(6)

const items = computed<PmsRosterItem[]>(() => roster.value?.items ?? [])

const filteredItems = computed(() =>
  items.value.filter(item => {
    if (boundFilter.value === 'unbound' && item.bound) return false
    if (boundFilter.value === 'bound' && !item.bound) return false
    return rosterMatchesKeyword(item, keyword.value)
  }),
)

/**
 * 空列表的诊断文案。
 * 必须排除「还没加载完」与「加载失败」两种情形 —— 否则首屏在请求返回前
 * 就会闪一个「PMS 平台当前没有可显示的人员」，把「还没拿到」说成「没有」。
 */
const rosterEmptyReason = computed(() => {
  if (loading.value || !roster.value) return ''
  const current = roster.value
  return items.value.length === 0 ? emptyRosterReason(current.message) : ''
})

async function loadRoster() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getPmsRoster()
    roster.value = res.data
  } catch (error) {
    roster.value = null
    loadError.value = resolveApiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  syncing.value = true
  loadError.value = ''
  try {
    const res = await syncPmsUsers()
    ElMessage.success(`同步完成：${summarizeSyncResult(res.data)}`)
    await loadRoster()
  } catch (error) {
    loadError.value = resolveApiErrorMessage(error)
  } finally {
    syncing.value = false
  }
}

onMounted(loadRoster)

/* ==================== 绑定 / 解绑 ==================== */

const bindVisible = ref(false)
const bindTarget = ref<PmsRosterItem | null>(null)
const bindEmpId = ref<number | null>(null)
const bindKeyword = ref('')
const bindOptions = ref<Array<{ id: number; realName: string; empCode: string }>>([])
const bindOptionsLoading = ref(false)
const bindSubmitting = ref(false)
const bindError = ref('')

/** 当前这一行自己已绑的员工必须可选，否则 HR 打不开自己的绑定项、也改不了绑 */
const bindConflictEmpIds = computed(() =>
  selectableConflictEmpIds(roster.value?.boundEmpIds ?? [], bindTarget.value?.empId ?? null),
)

function openBind(item: PmsRosterItem) {
  bindTarget.value = item
  bindEmpId.value = item.empId
  bindKeyword.value = ''
  bindError.value = ''
  bindVisible.value = true
  void loadBindOptions()
}

async function loadBindOptions() {
  bindOptionsLoading.value = true
  try {
    const params: Record<string, unknown> = { current: 1, size: 50 }
    if (bindKeyword.value.trim()) params.keyword = bindKeyword.value.trim()
    const res = await pageEmployees(params)
    bindOptions.value = res.data.records.map(row => ({
      id: row.id,
      realName: row.realName,
      empCode: row.empCode,
    }))
  } catch (error) {
    bindError.value = resolveApiErrorMessage(error)
    bindOptions.value = []
  } finally {
    bindOptionsLoading.value = false
  }
}

const bindBlockedReason = computed(() =>
  bindBlockReason({
    pmsUserId: bindTarget.value?.pmsUserId ?? null,
    empId: bindEmpId.value,
    conflictEmpIds: bindConflictEmpIds.value,
  }),
)

async function submitBind() {
  const target = bindTarget.value
  const empId = bindEmpId.value
  const blocked = bindBlockedReason.value
  if (blocked || !target || empId === null || empId === undefined) {
    // 失败原因常驻在弹窗里，不只弹 toast：弹窗保持打开、已选内容不丢
    bindError.value = blocked || '请选择要绑定的员工'
    return
  }
  bindSubmitting.value = true
  bindError.value = ''
  try {
    await bindPmsRosterUser(target.pmsUserId, empId)
    ElMessage.success('绑定成功')
    bindVisible.value = false
    await loadRoster()
  } catch (error) {
    bindError.value = resolveApiErrorMessage(error)
  } finally {
    bindSubmitting.value = false
  }
}

async function handleUnbind(item: PmsRosterItem) {
  try {
    await ElMessageBox.confirm(
      `解除「${pmsDisplayName(item)}」与「${employeeDisplayName(item)}」的绑定？`
        + '已导入的员工能力不会被删除；如需清理请到员工能力档案处理。',
      '解绑确认',
      { type: 'warning', confirmButtonText: '解除绑定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await unbindPmsRosterUser(item.pmsUserId)
    ElMessage.success('已解绑，该 PMS 人员回到「未绑定」')
    await loadRoster()
  } catch (error) {
    loadError.value = resolveApiErrorMessage(error)
  }
}

/* ==================== 分析 / 历史 / 导入 ==================== */

const panelVisible = ref(false)
const panelTarget = ref<PmsRosterItem | null>(null)
const history = ref<PmsAnalysisTask[]>([])
const historyLoading = ref(false)
const activeTaskId = ref<number | null>(null)
const detailLoading = ref(false)
const detailSummary = ref('')
const detailAbilities = ref<Array<Record<string, any>>>([])
const selectedIndexes = ref<number[]>([])
const analyzing = ref(false)
const importing = ref(false)
const panelError = ref('')

const activeTask = computed(() => history.value.find(task => task.id === activeTaskId.value) ?? null)
const importState = computed(() =>
  importActionState({
    item: panelTarget.value,
    analysisStatus: activeTask.value?.analysisStatus ?? null,
    selectedCount: selectedIndexes.value.length,
    abilityCount: detailAbilities.value.length,
  }),
)

function openPanel(item: PmsRosterItem) {
  panelTarget.value = item
  panelVisible.value = true
  panelError.value = ''
  activeTaskId.value = null
  detailSummary.value = ''
  detailAbilities.value = []
  selectedIndexes.value = []
  void loadHistory()
}

async function loadHistory() {
  if (!panelTarget.value) return
  historyLoading.value = true
  try {
    const res = await getPmsUserAnalysisHistory(panelTarget.value.pmsUserId)
    history.value = res.data ?? []
  } catch (error) {
    panelError.value = resolveApiErrorMessage(error)
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

async function runAnalysis() {
  if (!panelTarget.value) return
  analyzing.value = true
  panelError.value = ''
  const taskId = `pms-${panelTarget.value.pmsUserId}-${Date.now()}`
  taskStore.addTask({
    id: taskId,
    type: 'pms-analysis',
    refId: panelTarget.value.pmsUserId,
    refName: pmsDisplayName(panelTarget.value),
  })
  try {
    const res = await analyzePmsUser(panelTarget.value.pmsUserId, analysisMonths.value)
    taskStore.updateTask(taskId, { status: 'completed', message: '分析完成' })
    ElMessage.success(`分析完成，提取 ${res.data.extractedAbilityCount} 项能力`)
    await loadHistory()
    await selectTask(res.data)
    // 同步最新分析状态到花名册行，不整表刷新以免打断当前抽屉
    if (panelTarget.value) panelTarget.value.lastAnalysisStatus = res.data.analysisStatus
  } catch (error) {
    taskStore.updateTask(taskId, { status: 'failed', message: '分析失败' })
    panelError.value = resolveApiErrorMessage(error)
    // 失败任务已经落库（status=3 + errorMessage），刷新历史 HR 才能看到真实原因；
    // 只在 toast 里给一句提示的话，用户点掉就再也找不到线索了。
    await loadHistory()
  } finally {
    analyzing.value = false
  }
}

async function selectTask(task: PmsAnalysisTask) {
  activeTaskId.value = task.id
  detailLoading.value = true
  detailSummary.value = ''
  detailAbilities.value = []
  selectedIndexes.value = []
  panelError.value = ''
  try {
    const res = await getPmsAnalysisDetail(task.id)
    detailSummary.value = res.data.summary || ''
    detailAbilities.value = (res.data.abilities ?? []) as Array<Record<string, any>>
    // 默认全选：HR 通常是「全都要」，逐个勾是更少见的例外
    selectedIndexes.value = detailAbilities.value.map((_row, index) => index)
  } catch (error) {
    panelError.value = resolveApiErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

function toggleAbility(index: number) {
  if (selectedIndexes.value.includes(index)) {
    selectedIndexes.value = selectedIndexes.value.filter(value => value !== index)
  } else {
    selectedIndexes.value = [...selectedIndexes.value, index].sort((a, b) => a - b)
  }
}

async function submitImport() {
  const target = panelTarget.value
  const task = activeTask.value
  if (!target || !task) return
  if (selectedIndexes.value.length === 0) {
    panelError.value = '请至少选择一项能力'
    return
  }
  if (target.empId === null || target.empId === undefined) {
    // 正常情况下按钮已被 importState 禁用；这里兜住「绑定关系在抽屉打开期间被别处解开」
    panelError.value = '该 PMS 人员尚未绑定本系统员工，无法写入人员能力画像'
    return
  }
  importing.value = true
  panelError.value = ''
  try {
    const res = await importPmsAbilities(target.empId, task.id, selectedIndexes.value)
    ElMessage.success(`已写入人员能力画像，共 ${res.data.importedCount} 项`)
    await loadHistory()
  } catch (error) {
    panelError.value = resolveApiErrorMessage(error)
  } finally {
    importing.value = false
  }
}

function formatTime(time: string | null | undefined) {
  if (!time) return '—'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="page-container">
    <div class="page-shell">
      <section class="page-hero">
        <div>
          <span class="page-hero__eyebrow">人员库 · PMS 项目分析</span>
          <h1 class="page-hero__title">PMS 项目分析</h1>
          <p class="page-hero__desc">
            管理在 PMS 平台上已有数据的在岗人员。同步只登记 PMS 人员，不会在他们的账号建立前
            写入人员库——谁绑给谁由你决定，本平台其他员工不受影响。
          </p>
        </div>
      </section>

      <section v-if="roster" class="hero-chip-row pms-stat-row">
        <span class="hero-chip" :class="{ 'hero-chip--plain': !roster.pmsConnected }">
          {{ roster.pmsConnected ? 'PMS 已连接' : 'PMS 未连接' }}
        </span>
        <span class="hero-chip hero-chip--plain">PMS 人员 {{ roster.totalPmsUsers }}</span>
        <span class="hero-chip hero-chip--plain">已绑定 {{ roster.boundCount }}</span>
        <span class="hero-chip hero-chip--plain">未绑定 {{ roster.unboundCount }}</span>
        <span class="hero-chip hero-chip--plain">已分析 {{ roster.analyzedCount }}</span>
      </section>

      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        title="操作失败"
        :description="loadError"
      />

      <section class="glass-card pms-panel">
        <div class="toolbar-group pms-toolbar">
          <el-input
            v-model="keyword"
            :prefix-icon="Search"
            placeholder="搜索 PMS 昵称 / 用户名 / 工号 / 已绑定员工"
            clearable
            class="pms-search"
          />
          <el-radio-group v-model="boundFilter">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="unbound">未绑定</el-radio-button>
            <el-radio-button value="bound">已绑定</el-radio-button>
          </el-radio-group>
          <span class="pms-spacer" />
          <span class="pms-months">
            分析范围
            <el-select v-model="analysisMonths" size="small" class="pms-months__select">
              <el-option :value="3" label="近 3 个月" />
              <el-option :value="6" label="近 6 个月" />
              <el-option :value="12" label="近 12 个月" />
            </el-select>
          </span>
          <el-button :icon="Refresh" :loading="loading" @click="loadRoster">刷新</el-button>
          <el-button type="primary" :loading="syncing" @click="handleSync">同步 PMS 人员</el-button>
        </div>

        <el-alert
          v-if="rosterEmptyReason"
          type="info"
          :closable="false"
          show-icon
          title="暂无 PMS 人员可显示"
          :description="rosterEmptyReason"
        />

        <el-table v-else :data="filteredItems" v-loading="loading" border size="small">
          <el-table-column label="PMS 人员" min-width="180">
            <template #default="{ row }">
              <div class="pms-person">
                <span class="pms-person__name">{{ pmsDisplayName(row) }}</span>
                <span class="pms-person__sub">{{ row.pmsUsername || '—' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="PMS 工号" width="120">
            <template #default="{ row }">{{ row.pmsEmployeeId || '—' }}</template>
          </el-table-column>
          <el-table-column label="PMS 角色" width="100">
            <template #default="{ row }">{{ row.pmsRole || '—' }}</template>
          </el-table-column>
          <el-table-column label="绑定情况" min-width="200">
            <template #default="{ row }">
              <el-tag :type="rosterBindingMeta(row).tone" size="small">
                {{ rosterBindingMeta(row).text }}
              </el-tag>
              <el-button
                v-if="row.bound"
                type="primary"
                link
                size="small"
                class="pms-inline-link"
                @click="openBind(row)"
              >
                {{ employeeDisplayName(row) }}
              </el-button>
              <span v-else class="pms-muted">员工注册后可在此绑定</span>
            </template>
          </el-table-column>
          <el-table-column label="分析情况" min-width="180">
            <template #default="{ row }">
              <el-tag :type="analysisStatusMeta(row.lastAnalysisStatus).tone" size="small">
                {{ analysisStatusMeta(row.lastAnalysisStatus).text }}
              </el-tag>
              <span class="pms-muted pms-muted--inline">
                共 {{ row.analysisCount }} 次 · {{ formatTime(row.lastAnalysisTime) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="table-link-cluster">
                <el-button type="primary" link :icon="MagicStick" @click="openPanel(row)">分析</el-button>
                <el-button link @click="openBind(row)">
                  {{ row.bound ? '改绑' : '绑定员工' }}
                </el-button>
                <el-button v-if="row.bound" type="danger" link @click="handleUnbind(row)">解绑</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <!-- 绑定弹窗 -->
    <el-dialog v-model="bindVisible" title="绑定本系统员工" width="640px" :close-on-click-modal="false">
      <template v-if="bindTarget">
        <el-descriptions :column="2" border size="small" class="pms-bind-target">
          <el-descriptions-item label="PMS 人员">{{ pmsDisplayName(bindTarget) }}</el-descriptions-item>
          <el-descriptions-item label="PMS 工号">{{ bindTarget.pmsEmployeeId || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="bindError"
          type="error"
          :closable="false"
          show-icon
          title="无法完成绑定"
          :description="bindError"
          class="pms-bind-alert"
        />

        <div class="pms-bind-search">
          <el-input
            v-model="bindKeyword"
            :prefix-icon="Search"
            placeholder="按姓名 / 员工编号搜索"
            clearable
            @keyup.enter="loadBindOptions"
            @clear="loadBindOptions"
          />
          <el-button :loading="bindOptionsLoading" @click="loadBindOptions">搜索</el-button>
        </div>

        <el-empty v-if="!bindOptions.length" description="没有匹配的员工" :image-size="60" />
        <el-radio-group v-else v-model="bindEmpId" class="pms-bind-list">
          <label
            v-for="option in bindOptions"
            :key="option.id"
            class="pms-bind-item"
            :class="{ 'is-disabled': bindConflictEmpIds.includes(option.id) }"
          >
            <el-radio :value="option.id" :disabled="bindConflictEmpIds.includes(option.id)">
              <span class="pms-bind-item__name">{{ option.realName || '未命名' }}</span>
              <span class="pms-bind-item__code">{{ option.empCode || '—' }}</span>
              <span v-if="bindConflictEmpIds.includes(option.id)" class="pms-bind-item__hint">
                已绑定其他 PMS 人员
              </span>
            </el-radio>
          </label>
        </el-radio-group>

        <p class="pms-bind-tip">
          绑定只是把两边对应起来，不会新建或修改人员档案；解绑时已导入的能力仍会保留。
        </p>
      </template>

      <template #footer>
        <div class="pms-dialog-footer">
          <!-- 禁用按钮旁边必须写明原因，否则 HR 只看到一个点不动的按钮 -->
          <span v-if="bindBlockedReason" class="pms-muted">{{ bindBlockedReason }}</span>
          <span v-else class="pms-muted">绑定后即可把该项目分析的能力写入人员画像</span>
          <div class="pms-dialog-footer__actions">
            <el-button @click="bindVisible = false">取消</el-button>
            <el-button
              type="primary"
              :loading="bindSubmitting"
              :disabled="!!bindBlockedReason"
              @click="submitBind"
            >
              {{ bindTarget?.bound ? '确认改绑' : '确认绑定' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 分析结果与历史抽屉 -->
    <el-drawer
      v-model="panelVisible"
      :title="panelTarget ? `项目分析 · ${pmsDisplayName(panelTarget)}` : '项目分析'"
      size="min(880px, 96vw)"
    >
      <div v-if="panelTarget" class="pms-panel-body">
        <el-alert
          v-if="!panelTarget.bound"
          type="info"
          :closable="false"
          show-icon
          title="该 PMS 人员尚未绑定员工"
          description="可以先做分析看结果；写入人员能力画像前需要先绑定到本系统员工。"
        />
        <el-alert
          v-if="panelError"
          type="error"
          :closable="false"
          show-icon
          title="操作失败"
          :description="panelError"
        />

        <div class="pms-toolbar">
          <span class="pms-months">
            分析范围
            <el-select v-model="analysisMonths" size="small" class="pms-months__select">
              <el-option :value="3" label="近 3 个月" />
              <el-option :value="6" label="近 6 个月" />
              <el-option :value="12" label="近 12 个月" />
            </el-select>
          </span>
          <el-button type="primary" :icon="MagicStick" :loading="analyzing" @click="runAnalysis">
            {{ analyzing ? 'AI 分析中…' : '开始分析' }}
          </el-button>
          <el-button :icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新历史</el-button>
        </div>

        <h4 class="section-title">分析历史</h4>
        <el-table :data="history" v-loading="historyLoading" border size="small" @row-click="selectTask">
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="analysisStatusMeta(row.analysisStatus).tone" size="small">
                {{ analysisStatusMeta(row.analysisStatus).text }}
              </el-tag>
            </template>
          </el-table-column>
          <!--
            失败原因必须有一列：接口只回一句可读提示，原始技术细节（缺列、超时、AI 报错）
            只落在任务的 errorMessage 上。没有这一列时 HR 只能看到一个「失败」标签，
            完全不知道下一步该做什么。
          -->
          <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" class="pms-error">{{ row.errorMessage }}</span>
              <span v-else class="pms-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="范围" width="80" align="center">
            <template #default="{ row }">{{ row.dateRangeMonths }} 个月</template>
          </el-table-column>
          <el-table-column prop="workOrderCount" label="工单" width="64" align="center" />
          <el-table-column prop="bugCount" label="Bug" width="64" align="center" />
          <el-table-column prop="testCaseCount" label="用例" width="64" align="center" />
          <el-table-column prop="projectCount" label="项目" width="64" align="center" />
          <el-table-column label="提取能力" width="90" align="center">
            <template #default="{ row }">
              <span class="pms-emphasis">{{ row.extractedAbilityCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分析时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click.stop="selectTask(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="activeTask">
          <h4 class="section-title">提取的能力</h4>
          <div v-loading="detailLoading">
            <div v-if="detailSummary" class="pms-summary">
              <span class="pms-summary__label">分析摘要</span>
              <p class="pms-summary__text">{{ detailSummary }}</p>
            </div>

            <ul v-if="detailAbilities.length" class="pms-ability-list">
              <li
                v-for="(ability, index) in detailAbilities"
                :key="index"
                class="pms-ability"
                :class="{ 'is-selected': selectedIndexes.includes(index) }"
                @click="toggleAbility(index)"
              >
                <div class="pms-ability__head">
                  <span class="pms-ability__name">{{ ability.tagName || '(空)' }}</span>
                  <el-tag size="small" :type="(ability.level ?? 0) >= 4 ? 'success' : 'primary'">
                    {{ ability.level ?? '—' }} 级
                  </el-tag>
                  <span class="pms-ability__conf">
                    置信度 {{ ability.confidence ? Math.round(ability.confidence * 100) + '%' : '—' }}
                  </span>
                </div>
                <p class="pms-ability__evidence">{{ ability.evidence || '未提供证据' }}</p>
              </li>
            </ul>
            <el-empty v-else-if="!detailLoading" description="该任务没有提取到能力" :image-size="70" />
          </div>

          <div class="pms-panel-footer">
            <span class="pms-muted">{{ importState.hint }}</span>
            <el-button
              type="primary"
              :loading="importing"
              :disabled="importState.disabled"
              @click="submitImport"
            >
              写入人员能力画像
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.pms-stat-row {
  flex-wrap: wrap;
}

.pms-panel {
  padding: 18px;
}

.pms-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.pms-search {
  width: 300px;
}

.pms-spacer {
  flex: 1;
}

.pms-months {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.pms-months__select {
  width: 130px;
}

.pms-person {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pms-person__name {
  color: var(--app-text-strong);
  font-weight: 600;
}

.pms-person__sub {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.pms-inline-link {
  margin-left: 8px;
}

.pms-muted {
  color: var(--app-text-secondary);
  font-size: 12px;
}

/* 失败原因：用 error 色但保持小字，避免整行看起来像严重告警刷屏 */
.pms-error {
  color: var(--app-danger, #f04438);
  font-size: 12px;
  word-break: break-word;
}

.pms-muted--inline {
  margin-left: 8px;
}

.pms-emphasis {
  color: var(--app-accent);
  font-weight: 700;
}

.pms-bind-target {
  margin-bottom: 14px;
}

.pms-bind-alert {
  margin-bottom: 14px;
}

.pms-bind-search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pms-bind-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
  width: 100%;
}

.pms-bind-item {
  display: block;
  padding: 8px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  cursor: pointer;
}

.pms-bind-item.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pms-bind-item__name {
  font-weight: 600;
  margin-right: 8px;
}

.pms-bind-item__code {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.pms-bind-item__hint {
  margin-left: 8px;
  color: var(--el-color-warning);
  font-size: 12px;
}

.pms-bind-tip {
  margin-top: 14px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.pms-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pms-dialog-footer__actions {
  display: inline-flex;
  gap: 8px;
  flex: 0 0 auto;
}

.pms-panel-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pms-summary {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--app-surface-soft);
}

.pms-summary__label {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.pms-summary__text {
  margin: 6px 0 0;
  color: var(--app-text-strong);
  line-height: 1.6;
}

.pms-ability-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pms-ability {
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.pms-ability.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.pms-ability__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pms-ability__name {
  font-weight: 600;
  color: var(--app-text-strong);
}

.pms-ability__conf {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.pms-ability__evidence {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.pms-panel-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}
</style>
