<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Edit, View, Clock, User, Medal, DataBoard, TrendCharts, Grid, Tickets, ArrowLeft } from '@element-plus/icons-vue'
import { getAbilityProfile, getEmployee, listPendingAbilityClaims, pageEmployees, getTagTree } from '@/api'
import { precheckCapabilityEligibility, type EligibilityPrecheckResult } from '@/api/assessment'
import { updateGovernanceReviewStatus, batchReviewGovernanceChecks } from '@/api/ai-governance'
import type { EmpAbilityProfileVO, EmpEmployee, PendingAbilityClaim, AbilityTagTreeVO } from '@/api'
import type { PersonAbilityProfile } from '@/api/ability-governance'
import AbilityForceGraph from '@/components/graph/AbilityForceGraph.vue'
import type { ForceEdge, ForceNode } from '@/components/graph/AbilityForceGraph.vue'
import AbilityRadarChart from '@/components/graph/AbilityRadarChart.vue'
import GovernanceDialog from './governance-dialog.vue'
import EvidenceDrawer from './evidence-drawer.vue'
import GovernanceHistory from './governance-history.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const tableData = ref<EmpEmployee[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

/** 当前页员工能力状态（正式/待审核），口径与匹配引擎一致 */
const abilityStatusMap = ref<Record<number, EligibilityPrecheckResult>>({})

const profileVisible = ref(false)
const profileLoading = ref(false)
const selectedEmployee = ref<EmpEmployee | null>(null)
const profile = ref<EmpAbilityProfileVO | null>(null)
const pendingClaims = ref<PendingAbilityClaim[]>([])
// 标签层级映射：tagId -> { tagLevel, parentId, parentName }
const tagMetaMap = ref<Record<number, { tagLevel: number; parentId: number; parentName: string }>>({})
const radarChartData = ref<{ axis: string; value: number; maxValue: number }[]>([])
const radarData = ref<{ name: string; value: number }[]>([])
const forceNodes = ref<ForceNode[]>([])
const forceEdges = ref<ForceEdge[]>([])

// 治理相关状态
const governanceDialogVisible = ref(false)
const evidenceDrawerVisible = ref(false)
const governanceHistoryVisible = ref(false)
const selectedAbility = ref<PersonAbilityProfile | null>(null)

// 图谱自适应宽度
const graphWidth = ref(1100)
function updateGraphWidth() {
  const w = window.innerWidth
  if (w < 800) graphWidth.value = w - 60
  else if (w < 1400) graphWidth.value = w - 100
  else graphWidth.value = Math.min(1300, w - 160)
}
if (typeof window !== 'undefined') {
  window.addEventListener('resize', updateGraphWidth)
  updateGraphWidth()
}

async function loadList() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    const res = await pageEmployees(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  loadAbilityStatus()
}

/** 批量加载当前页员工能力状态（precheck 不依赖岗位列表） */
async function loadAbilityStatus() {
  const ids = tableData.value.map((e) => e.id).filter((id) => id != null)
  if (!ids.length) {
    abilityStatusMap.value = {}
    return
  }
  try {
    const res = await precheckCapabilityEligibility(ids, [])
    const map: Record<number, EligibilityPrecheckResult> = {}
    for (const item of res.data ?? []) map[item.empId] = item
    abilityStatusMap.value = map
  } catch {
    abilityStatusMap.value = {}
  }
}

function abilityStatusOf(rowId: number): EligibilityPrecheckResult | undefined {
  return abilityStatusMap.value[rowId]
}

/** 有待审核能力 → 跳转评估页待确立标签 */
function goProvisionalAssessment(empId: number) {
  router.push({ path: '/employee/ability-profile/assessment', query: { empId, tab: 'provisional' } })
}

onMounted(async () => {
  await loadList()
  loadTagLevels()

  // 如果URL中有empId参数，自动加载该员工的能力画像
  const empId = route.query.empId
  if (empId) {
    const id = Number(empId)
    // 先从列表中查找
    let emp = tableData.value.find((e) => e.id === id)
    // 如果列表中没有，单独请求
    if (!emp) {
      try {
        const res = await getEmployee(id)
        emp = res.data
      } catch {
        // ignore
      }
    }
    if (emp) {
      handleViewProfile(emp)
    }
  }
})

function handleSearch() {
  currentPage.value = 1
  loadList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadList()
}

async function handleViewProfile(row: EmpEmployee) {
  selectedEmployee.value = row
  profileVisible.value = true
  profileLoading.value = true
  try {
    await Promise.all([
      (async () => {
        const profileRes = await getAbilityProfile(row.id)
        profile.value = profileRes.data
        radarData.value = (profileRes.data.abilityDetails || []).map((item) => ({
          name: item.tagName,
          value: item.masteryLevel * 25,
        }))
        radarChartData.value = (profileRes.data.abilityDetails || []).map((item) => ({
          axis: item.tagName,
          value: item.masteryLevel * 25,
          maxValue: 100,
        }))
        buildForceGraphData(profileRes.data)
      })(),
      (async () => {
        const pendingClaimsRes = await listPendingAbilityClaims(row.id)
        pendingClaims.value = pendingClaimsRes.data || []
      })(),
    ])
  } finally {
    profileLoading.value = false
  }
  // 滚动到顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ==================== 待融合能力审核（画像页直接审，无需跳治理台） ====================

const reviewingIds = ref(new Set<number>())
const batchReviewing = ref(false)

/** 采纳待审能力：直接写入正式人员能力 */
async function acceptPendingClaim(claim: PendingAbilityClaim) {
  if (!claim.harnessLogId) {
    ElMessage.warning('该待审能力缺少 Harness 关联，请到 AI 治理页处理')
    return
  }
  if (reviewingIds.value.has(claim.id)) return
  reviewingIds.value.add(claim.id)
  try {
    await updateGovernanceReviewStatus(claim.harnessLogId, { reviewStatus: 'ACCEPTED', reviewComment: '画像页人工采纳' })
    ElMessage.success(`已采纳「${claim.abilityName}」`)
    await refreshPendingClaims()
  } catch (e: any) {
    ElMessage.error(e?.message || '采纳失败')
  } finally {
    reviewingIds.value.delete(claim.id)
  }
}

/** 驳回待审能力 */
async function rejectPendingClaim(claim: PendingAbilityClaim) {
  if (!claim.harnessLogId) {
    ElMessage.warning('该待审能力缺少 Harness 关联，请到 AI 治理页处理')
    return
  }
  if (reviewingIds.value.has(claim.id)) return
  try {
    const { value } = await ElMessageBox.prompt(`请输入驳回「${claim.abilityName}」的原因`, '驳回待审能力', {
      inputType: 'textarea',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
    })
    if (!value || !value.trim()) {
      ElMessage.warning('驳回原因不能为空')
      return
    }
    reviewingIds.value.add(claim.id)
    await updateGovernanceReviewStatus(claim.harnessLogId, {
      reviewStatus: 'REJECTED',
      reviewComment: value.trim(),
      rejectReasonCategory: 'OTHER',
    })
    ElMessage.success('已驳回')
    await refreshPendingClaims()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '驳回失败')
  } finally {
    reviewingIds.value.delete(claim.id)
  }
}

/** 按 AI 建议批量采纳：仅 Harness 判定 PASS 的待审能力（后端会二次过滤非安全项） */
async function batchAcceptPending() {
  const ids = pendingClaims.value
    .filter((c) => c.harnessLogId && c.harnessDecision === 'PASS')
    .map((c) => c.harnessLogId) as number[]
  if (!ids.length) {
    ElMessage.warning('没有可批量采纳的待审能力（需 Harness 判定为 PASS）')
    return
  }
  batchReviewing.value = true
  try {
    const res = await batchReviewGovernanceChecks({ ids, reviewStatus: 'ACCEPTED', reviewComment: '按 AI 建议批量采纳' })
    const r = res.data
    ElMessage.success(`已采纳 ${r.successCount} 项${r.failedCount ? `，${r.failedCount} 项需人工处理` : ''}`)
    await refreshPendingClaims()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量采纳失败')
  } finally {
    batchReviewing.value = false
  }
}

/** 刷新当前员工的待融合能力 */
async function refreshPendingClaims() {
  if (!selectedEmployee.value) return
  try {
    const res = await listPendingAbilityClaims(selectedEmployee.value.id)
    pendingClaims.value = res.data || []
  } catch {
    // 保留原数据
  }
}

/** 加载标签树，构建 tagId -> 层级元数据 映射（能力层/技能层） */
async function loadTagLevels() {
  try {
    const res = await getTagTree()
    const map: Record<number, { tagLevel: number; parentId: number; parentName: string }> = {}
    const walk = (nodes: AbilityTagTreeVO[], parentId = 0, parentName = '') => {
      for (const n of nodes) {
        map[n.id] = { tagLevel: n.tagLevel, parentId, parentName }
        if (n.children?.length) walk(n.children, n.id, n.tagName)
      }
    }
    walk(res.data ?? [])
    tagMetaMap.value = map
  } catch {
    tagMetaMap.value = {}
  }
}

interface GroupedAbility {
  tagId: number
  tagName: string
  tagCategory: string
  masteryLevel: number
  masteryLevelName: string
  tagLevel: number
  parentName: string
  children: GroupedAbility[]
}

/** 将已融合能力按「能力层→技能层」组织成树 */
const abilityTree = computed<GroupedAbility[]>(() => {
  const details = profile.value?.abilityDetails || []
  const meta = tagMetaMap.value
  const roots: GroupedAbility[] = []
  const abilityById = new Map<number, GroupedAbility>()

  const toNode = (item: EmpAbilityProfileVO['abilityDetails'][number]): GroupedAbility => ({
    tagId: item.tagId,
    tagName: item.tagName,
    tagCategory: item.tagCategory,
    masteryLevel: item.masteryLevel,
    masteryLevelName: item.masteryLevelName,
    tagLevel: meta[item.tagId]?.tagLevel ?? 1,
    parentName: meta[item.tagId]?.parentName ?? '',
    children: [],
  })

  // 第一遍：能力层节点先行
  for (const item of details) {
    const node = toNode(item)
    abilityById.set(item.tagId, node)
    if (node.tagLevel !== 2) {
      roots.push(node)
    }
  }
  // 第二遍：技能层节点挂到对应能力下
  for (const item of details) {
    const m = meta[item.tagId]
    if (m?.tagLevel === 2 && m.parentId) {
      const parent = abilityById.get(m.parentId)
      const child = abilityById.get(item.tagId)!
      if (parent) {
        parent.children.push(child)
      } else {
        roots.push(child)
      }
    }
  }
  return roots
})

function buildForceGraphData(currentProfile: EmpAbilityProfileVO) {
  const nodes: ForceNode[] = []
  const edges: ForceEdge[] = []
  nodes.push({ id: 'employee', label: currentProfile.realName, type: 'employee' })

  const categoryMap = new Map<string, typeof currentProfile.abilityDetails>()
  ;(currentProfile.abilityDetails || []).forEach((item) => {
    const category = item.tagCategory || '其他'
    if (!categoryMap.has(category)) categoryMap.set(category, [])
    categoryMap.get(category)!.push(item)
  })

  categoryMap.forEach((items, category) => {
    const categoryId = `category_${category}`
    nodes.push({ id: categoryId, label: category, type: 'abilityCategory', category })
    edges.push({ source: 'employee', target: categoryId, type: 'employee-category', style: 'solid' })

    items.forEach((item) => {
      const abilityId = `ability_${item.tagId}`
      nodes.push({
        id: abilityId,
        label: item.tagName,
        type: 'ability',
        level: item.masteryLevel,
        category: item.tagCategory,
      })
      edges.push({ source: categoryId, target: abilityId, type: 'category-ability', style: 'solid' })
    })
  })

  forceNodes.value = nodes
  forceEdges.value = edges
}

function closeProfile() {
  profileVisible.value = false
  profile.value = null
  pendingClaims.value = []
  selectedEmployee.value = null
  radarData.value = []
  radarChartData.value = []
  forceNodes.value = []
  forceEdges.value = []
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const levelMap: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
  4: '专家',
}

function getRadarColor(index: number): string {
  const colors = ['#2563EB', '#059669', '#D97706', '#DC2626', '#64748B']
  return colors[index % colors.length]
}

const pendingSourceLabels: Record<string, string> = {
  RESUME_PARSE: '简历解析',
  AI_TEST: 'AI 测试',
  AI_INTERVIEW: 'AI 面试',
  AI_PROJECT: '项目分析',
}

function pendingSourceLabel(sourceType: string) {
  return pendingSourceLabels[sourceType] || sourceType
}

function confidenceText(value?: number) {
  return value == null ? '-' : `${Number(value).toFixed(0)}%`
}

const harnessLabels: Record<string, string> = {
  PASS: '已通过',
  REVIEW: '待审核',
  BLOCK: '已拦截',
}

function harnessTagType(decision?: string) {
  if (!decision) return 'info'
  if (decision === 'PASS') return 'success'
  if (decision === 'REVIEW') return 'warning'
  return 'danger'
}

function harnessLabel(decision?: string) {
  return harnessLabels[decision || ''] || '未经过Harness'
}

// 治理相关方法
function handleGovernance(ability: any) {
  selectedAbility.value = {
    profileId: ability.id || 0,
    empId: selectedEmployee.value?.id || 0,
    tagId: ability.tagId,
    tagName: ability.tagName,
    tagCategory: ability.tagCategory || 'TECHNICAL',
    level: ability.masteryLevel,
    confidence: Number(ability.confidence ?? ability.confidenceScore ?? 0),
    sourceBreakdown: [],
    evidenceCount: 0,
    lastUpdatedTime: '',
    humanReviewed: false,
    reviewStatus: 'AI_GENERATED',
  }
  governanceDialogVisible.value = true
}

function handleViewEvidence(ability: any) {
  selectedAbility.value = {
    profileId: ability.id || 0,
    empId: selectedEmployee.value?.id || 0,
    tagId: ability.tagId,
    tagName: ability.tagName,
    tagCategory: ability.tagCategory || 'TECHNICAL',
    level: ability.masteryLevel,
    confidence: Number(ability.confidence ?? ability.confidenceScore ?? 0),
    sourceBreakdown: [],
    evidenceCount: 0,
    lastUpdatedTime: '',
    humanReviewed: false,
    reviewStatus: 'AI_GENERATED',
  }
  evidenceDrawerVisible.value = true
}

function handleGovernanceSuccess() {
  // 刷新能力画像
  if (selectedEmployee.value) {
    handleViewProfile(selectedEmployee.value)
  }
}
</script>

<template>
  <div class="page-shell motion-page">
    <!-- ====== 列表视图 ====== -->
    <template v-if="!profileVisible">
      <section class="page-hero motion-scan">
        <div>
          <div class="page-hero__eyebrow">Capability Graph</div>
          <h1 class="page-hero__title">能力档案中心</h1>
          <p class="page-hero__desc">统一查看员工能力画像、能力图谱和画像入口，支撑简历解析、AI 面试和项目分析等上游能力来源。</p>
          <div class="page-hero__meta">
            <span class="hero-chip">Resume Parse</span>
            <span class="hero-chip">AI Interview</span>
            <span class="hero-chip">Project Evidence</span>
            <span class="hero-chip" @click="router.push('/agent/memory')">Agent 记忆</span>
          </div>
        </div>
      </section>

      <section class="glass-card motion-rise">
        <div class="toolbar-panel">
          <div>
            <div class="section-title">员工画像列表</div>
            <div class="section-desc">从这里进入能力编辑、AI 面试、项目分析与图谱查看。</div>
          </div>
          <div class="toolbar-group">
            <el-input v-model="keyword" placeholder="搜索姓名 / 人员编号" clearable class="!w-64" />
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleSearch">刷新</el-button>
          </div>
        </div>

        <div class="panel-body">
          <el-table :data="tableData" v-loading="loading" class="motion-scan" style="width: 100%">
            <el-table-column prop="realName" label="姓名" min-width="140" />
            <el-table-column prop="empCode" label="人员编号" min-width="140" />
            <el-table-column label="能力状态" min-width="200">
              <template #default="{ row }">
                <div class="ability-status-cell">
                  <template v-if="abilityStatusOf(row.id)?.hasConfirmedAbilities">
                    <el-tag type="success" size="small" effect="plain" round>已具备正式能力</el-tag>
                    <el-tag
                      v-if="abilityStatusOf(row.id)?.hasProvisionalAbilities"
                      type="warning" size="small" round class="provisional-link"
                      @click="goProvisionalAssessment(row.id)"
                    >待审核 {{ abilityStatusOf(row.id)?.provisionalAbilityCount ?? 0 }} 项</el-tag>
                  </template>
                  <template v-else>
                    <el-tag type="danger" size="small" effect="plain" round>暂无正式能力</el-tag>
                    <el-tag
                      v-if="abilityStatusOf(row.id)?.hasProvisionalAbilities"
                      type="warning" size="small" round class="provisional-link"
                      @click="goProvisionalAssessment(row.id)"
                    >待审核 {{ abilityStatusOf(row.id)?.provisionalAbilityCount ?? 0 }} 项</el-tag>
                  </template>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="620" fixed="right">
              <template #default="{ row }">
                <div class="table-link-cluster">
                  <el-button type="primary" link @click="router.push({ path: '/employee/ability-profile/edit', query: { empId: row.id } })">能力编辑</el-button>
                  <el-button type="primary" link @click="router.push({ path: '/employee/ability-profile/assessment', query: { empId: row.id } })">评估流程</el-button>
                  <el-button type="warning" link @click="router.push({ path: '/employee/ability-profile/live-interview', query: { empId: row.id } })">面试记录</el-button>
                  <el-button type="info" link @click="router.push({ path: '/employee/ability-profile/pms-analysis', query: { empId: row.id } })">项目分析</el-button>
                  <el-button link @click="handleViewProfile(row)">能力图谱</el-button>
                  <el-button link :icon="Clock" @click="selectedEmployee = row; governanceHistoryVisible = true">治理历史</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel-footer">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </template>

    <!-- ====== 画像详情视图 ====== -->
    <template v-else>
      <div v-loading="profileLoading" class="profile-page">
        <!-- 返回栏 -->
        <div class="profile-page__back">
          <el-button :icon="ArrowLeft" @click="closeProfile" round>返回列表</el-button>
        </div>

        <template v-if="profile">
          <!-- 画像头部 -->
          <section class="profile-hero">
            <div class="profile-hero__avatar">
              {{ selectedEmployee?.realName?.charAt(0) || '?' }}
            </div>
            <div class="profile-hero__info">
              <h2 class="profile-hero__name">{{ profile.realName }} 的能力画像</h2>
              <div class="profile-hero__meta">
                <span class="profile-hero__chip">{{ profile.empCode }}</span>
                <span class="profile-hero__chip profile-hero__chip--score">综合评分 {{ profile.overallScore }}</span>
              </div>
            </div>
            <div class="profile-hero__actions">
              <el-button :icon="Edit" @click="router.push({ path: '/employee/ability-profile/edit', query: { empId: selectedEmployee?.id } })" round>能力编辑</el-button>
              <el-button :icon="Clock" @click="governanceHistoryVisible = true" round>治理历史</el-button>
            </div>
          </section>

          <!-- 指标卡片区 -->
          <div class="profile-stat-grid">
            <div class="profile-stat-card">
              <div class="profile-stat-card__icon profile-stat-card__icon--blue">
                <el-icon :size="20"><User /></el-icon>
              </div>
              <div class="profile-stat-card__body">
                <div class="profile-stat-card__label">人员编号</div>
                <div class="profile-stat-card__value">{{ profile.empCode }}</div>
              </div>
            </div>
            <div class="profile-stat-card">
              <div class="profile-stat-card__icon profile-stat-card__icon--green">
                <el-icon :size="20"><Medal /></el-icon>
              </div>
              <div class="profile-stat-card__body">
                <div class="profile-stat-card__label">综合评分</div>
                <div class="profile-stat-card__value profile-stat-card__value--score">{{ profile.overallScore }}</div>
              </div>
            </div>
            <div class="profile-stat-card">
              <div class="profile-stat-card__icon profile-stat-card__icon--amber">
                <el-icon :size="20"><Grid /></el-icon>
              </div>
              <div class="profile-stat-card__body">
                <div class="profile-stat-card__label">已融合能力</div>
                <div class="profile-stat-card__value">{{ profile.abilityDetails?.length || 0 }}<span class="profile-stat-card__unit"> 项</span></div>
              </div>
            </div>
            <div v-if="pendingClaims.length" class="profile-stat-card">
              <div class="profile-stat-card__icon profile-stat-card__icon--purple">
                <el-icon :size="20"><Tickets /></el-icon>
              </div>
              <div class="profile-stat-card__body">
                <div class="profile-stat-card__label">待融合能力</div>
                <div class="profile-stat-card__value">{{ pendingClaims.length }}<span class="profile-stat-card__unit"> 项</span></div>
              </div>
            </div>
          </div>

          <!-- 双列布局：能力分布 + 雷达图 -->
          <div class="profile-chart-row">
            <div class="profile-chart-panel">
              <div class="profile-chart-panel__header">
                <el-icon :size="16"><DataBoard /></el-icon>
                <span>能力掌握分布</span>
              </div>
              <div v-if="radarData.length > 0" class="profile-distribution">
                <div
                  v-for="(item, index) in radarData"
                  :key="item.name"
                  class="profile-distribution__item"
                >
                  <div class="profile-distribution__head">
                    <span
                      class="profile-distribution__dot"
                      :style="{ background: getRadarColor(index) }"
                    ></span>
                    <span class="profile-distribution__name">{{ item.name }}</span>
                    <span class="profile-distribution__score">{{ item.value }}<span class="profile-distribution__max"> / 100</span></span>
                  </div>
                  <el-progress
                    :percentage="item.value"
                    :color="getRadarColor(index)"
                    :stroke-width="8"
                    :show-text="false"
                  />
                </div>
              </div>
              <div v-else class="profile-empty-hint">暂无分布数据</div>
            </div>

            <div v-if="radarChartData.length > 0" class="profile-chart-panel">
              <div class="profile-chart-panel__header">
                <el-icon :size="16"><TrendCharts /></el-icon>
                <span>能力雷达图</span>
              </div>
              <div class="profile-radar-wrap">
                <AbilityRadarChart :data="radarChartData" :width="440" :height="440" />
              </div>
            </div>
          </div>

          <!-- 能力图谱 -->
          <div v-if="forceNodes.length > 0" class="profile-graph-panel motion-rise">
            <div class="profile-graph-panel__top">
              <div class="profile-chart-panel__header">
                <el-icon :size="16"><Grid /></el-icon>
                <span>能力关系图谱</span>
                <span class="profile-graph-panel__live">
                  <span class="profile-graph-panel__live-dot"></span>
                  力导向仿真
                </span>
              </div>
              <span class="profile-graph-panel__hint">🖱 拖拽 · 滚轮缩放 · 双击聚焦 · 悬停查看详情</span>
            </div>
            <div class="profile-graph-canvas">
              <AbilityForceGraph
                :nodes="forceNodes"
                :edges="forceEdges"
                :width="graphWidth"
                :height="560"
                theme="tech-light"
              />
            </div>
          </div>

          <!-- 已融合能力表格 -->
          <div class="profile-table-panel">
            <div class="profile-table-panel__header">
              <div>
                <div class="profile-table-panel__title">已融合能力</div>
                <div class="profile-table-panel__desc">已通过 Harness 审核并融合到画像中的能力标签</div>
              </div>
              <div class="profile-table-panel__actions">
                <el-tag v-if="profile.abilityDetails?.length" size="small" effect="plain" round>
                  {{ profile.abilityDetails.length }} 项
                </el-tag>
              </div>
            </div>
            <el-table
              v-if="profile.abilityDetails?.length"
              :data="abilityTree"
              row-key="tagId"
              :tree-props="{ children: 'children' }"
              default-expand-all
              style="width: 100%"
              :header-cell-style="{ fontWeight: 600, color: '#475569', fontSize: '12px' }"
            >
              <el-table-column prop="tagName" label="能力标签" min-width="150">
                <template #default="{ row }">
                  <div class="profile-tag-cell">
                    <div class="profile-tag-cell__name-row">
                      <span class="profile-tag-cell__name">{{ row.tagName }}</span>
                      <span class="profile-tag-cell__level" :class="`profile-tag-cell__level--l${row.tagLevel}`">
                        {{ row.tagLevel === 2 ? '技能' : '能力' }}
                      </span>
                    </div>
                    <span class="profile-tag-cell__cat">{{ row.tagCategory }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="masteryLevel" label="等级" width="160" align="center">
                <template #default="{ row }">
                  <div class="profile-level-badge" :class="`profile-level-badge--l${row.masteryLevel}`">
                    {{ levelMap[row.masteryLevel] || row.masteryLevel }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="masteryLevelName" label="等级描述" width="140">
                <template #default="{ row }">
                  <span class="profile-level-desc">{{ row.masteryLevelName || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <div class="table-link-cluster">
                    <el-button type="primary" link :icon="View" size="small" @click="handleViewEvidence(row)">证据</el-button>
                    <el-button type="warning" link :icon="Edit" size="small" @click="handleGovernance(row)">人工修正</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="profile-empty-hint">该员工暂无已融合能力</div>
          </div>

          <!-- 待融合能力表格 -->
          <div v-if="pendingClaims.length" class="profile-table-panel">
            <div class="profile-table-panel__header">
              <div>
                <div class="profile-table-panel__title">待融合能力</div>
                <div class="profile-table-panel__desc">AI 自动发现的能力声明，可在本页直接采纳/驳回，无需跳转治理台</div>
              </div>
              <div style="display: flex; gap: 8px; align-items: center;">
                <el-button type="success" size="small" :loading="batchReviewing" :disabled="!pendingClaims.some((c) => c.harnessDecision === 'PASS')" @click="batchAcceptPending">
                  按 AI 建议批量采纳
                </el-button>
                <el-tag type="warning" size="small" effect="dark" round>{{ pendingClaims.length }} 项待处理</el-tag>
              </div>
            </div>
            <el-table
              :data="pendingClaims"
              style="width: 100%"
              :header-cell-style="{ fontWeight: 600, color: '#475569', fontSize: '12px' }"
            >
              <el-table-column prop="abilityName" label="能力名称" min-width="140" />
              <el-table-column label="来源" width="110" align="center">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" round>{{ pendingSourceLabel(row.sourceType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="等级" width="80" align="center">
                <template #default="{ row }">
                  <span class="profile-level-desc">{{ levelMap[row.claimedLevel] || row.claimedLevel || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="置信度" width="90" align="center">
                <template #default="{ row }">
                  <el-progress
                    :percentage="Math.round(Number(row.confidenceScore) || 0)"
                    :stroke-width="6"
                    :show-text="false"
                    :color="(Number(row.confidenceScore) || 0) >= 70 ? '#059669' : (Number(row.confidenceScore) || 0) >= 40 ? '#d97706' : '#94a3b8'"
                  />
                  <span class="profile-confidence-text">{{ confidenceText(row.confidenceScore) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Harness" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="harnessTagType(row.harnessDecision)" size="small" effect="dark" round>
                    {{ harnessLabel(row.harnessDecision) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="evidenceText" label="证据" min-width="180" show-overflow-tooltip />
              <el-table-column prop="createdTime" label="进入时间" width="150" />
              <el-table-column label="操作" width="130" align="center">
                <template #default="{ row }">
                  <template v-if="row.harnessLogId">
                    <el-button type="success" link size="small" :loading="reviewingIds.has(row.id)" @click="acceptPendingClaim(row)">采纳</el-button>
                    <el-button type="danger" link size="small" :loading="reviewingIds.has(row.id)" @click="rejectPendingClaim(row)">驳回</el-button>
                  </template>
                  <span v-else class="profile-muted">—</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </template>

    <!-- 人工修正弹窗 -->
    <GovernanceDialog
      v-model:visible="governanceDialogVisible"
      :ability="selectedAbility"
      :emp-id="selectedEmployee?.id || 0"
      @success="handleGovernanceSuccess"
    />

    <!-- 证据抽屉 -->
    <EvidenceDrawer
      v-model:visible="evidenceDrawerVisible"
      :ability="selectedAbility"
      :emp-id="selectedEmployee?.id || 0"
    />

    <!-- 治理历史抽屉 -->
    <GovernanceHistory
      v-model:visible="governanceHistoryVisible"
      :emp-id="selectedEmployee?.id || 0"
    />
  </div>
</template>

<style scoped>
/* ====== 画像页面 ====== */

.ability-status-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.provisional-link {
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.provisional-link:hover {
  opacity: 0.75;
}

/* 画像页面容器 */
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 返回栏 */
.profile-page__back {
  margin-bottom: -8px;
}

/* 画像头部 */
.profile-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 26px 30px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05), transparent 60%),
              rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(14px);
  box-shadow: 0 2px 20px rgba(15, 23, 42, 0.05);
}

.profile-hero__avatar {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: linear-gradient(135deg, #2563eb 0%, #06b6d4 100%);
  color: #fff;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.32);
  flex-shrink: 0;
}

.profile-hero__info {
  flex: 1;
  min-width: 0;
}

.profile-hero__name {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.profile-hero__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.profile-hero__chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 14px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.profile-hero__chip--score {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  font-weight: 700;
}

.profile-hero__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .profile-hero {
    flex-wrap: wrap;
  }
  .profile-hero__actions {
    width: 100%;
  }
}

/* ====== 指标卡片 ====== */

.profile-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 14px;
}

.profile-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(10px);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.profile-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  border-color: rgba(59, 130, 246, 0.2);
}

.profile-stat-card__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  flex-shrink: 0;
  color: #fff;
}

.profile-stat-card__icon--blue {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.profile-stat-card__icon--green {
  background: linear-gradient(135deg, #059669, #10b981);
}

.profile-stat-card__icon--amber {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.profile-stat-card__icon--purple {
  background: linear-gradient(135deg, #7c3aed, #a78bfa);
}

.profile-stat-card__body {
  min-width: 0;
}

.profile-stat-card__label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.03em;
  margin-bottom: 3px;
}

.profile-stat-card__value {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
  line-height: 1;
}

.profile-stat-card__value--score {
  color: #2563eb;
}

.profile-stat-card__unit {
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

/* ====== 图表行 ====== */

.profile-chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

@media (max-width: 860px) {
  .profile-chart-row {
    grid-template-columns: 1fr;
  }
}

.profile-chart-panel {
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
  padding: 20px 22px;
  overflow: visible;
}

.profile-chart-panel__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.profile-chart-panel__header .el-icon {
  color: #2563eb;
}

/* ====== 能力分布 ====== */

.profile-distribution {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-distribution__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-distribution__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-distribution__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.profile-distribution__name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  flex: 1;
}

.profile-distribution__score {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.profile-distribution__max {
  font-weight: 500;
  color: #94a3b8;
  font-size: 11px;
}

/* ====== 雷达图容器 ====== */

.profile-radar-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 340px;
  padding: 8px;
  overflow: visible;
}

/* ====== 图谱面板 ====== */

.profile-graph-panel {
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(12px);
  padding: 20px 22px;
}

.profile-graph-panel__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.profile-graph-panel__live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #059669;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(5, 150, 105, 0.08);
}

.profile-graph-panel__live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
  animation: livePulse 1.6s ease-in-out infinite;
}

@keyframes livePulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.7); }
}

.profile-graph-panel__hint {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  white-space: nowrap;
}

.profile-graph-canvas {
  display: flex;
  justify-content: center;
  border-radius: 14px;
  background:
    radial-gradient(ellipse at 50% 0%, rgba(37, 99, 235, 0.05), transparent 60%),
    radial-gradient(ellipse at 80% 100%, rgba(6, 182, 212, 0.04), transparent 60%),
    rgba(248, 250, 252, 0.5);
  overflow: hidden;
}

/* ====== 表格面板 ====== */

.profile-table-panel {
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.profile-table-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0.08));
}

.profile-table-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.profile-table-panel__desc {
  margin-top: 3px;
  font-size: 12px;
  color: #94a3b8;
}

.profile-table-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ====== 能力标签单元格 ====== */

.profile-tag-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-tag-cell__name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.profile-tag-cell__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.profile-tag-cell__level {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.4;
}

.profile-tag-cell__level--l1 {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}

.profile-tag-cell__level--l2 {
  color: #059669;
  background: rgba(5, 150, 105, 0.1);
}

.profile-tag-cell__cat {
  font-size: 11px;
  color: #94a3b8;
}

/* ====== 等级徽章 ====== */

.profile-level-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.profile-level-badge--l1 {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}

.profile-level-badge--l2 {
  color: #059669;
  background: rgba(5, 150, 105, 0.1);
}

.profile-level-badge--l3 {
  color: #d97706;
  background: rgba(217, 119, 6, 0.1);
}

.profile-level-badge--l4 {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.profile-level-desc {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

/* ====== 置信度文字 ====== */

.profile-confidence-text {
  display: block;
  font-size: 11px;
  color: #64748b;
  text-align: center;
  margin-top: 2px;
  font-weight: 600;
}

/* ====== 空状态提示 ====== */

.profile-muted {
  color: #94a3b8;
  font-size: 12px;
}

.profile-empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80px;
  color: #94a3b8;
  font-size: 13px;
}
</style>
