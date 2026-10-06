<script setup lang="ts">
/**
 * 员工「全面能力分析报告」面板（HR 匹配闭环 P2）。
 *
 * 报告由后端在该员工全部 harness 待审能力项清空后自动聚合生成（版本化快照）。
 * 本面板只做展示：版本切换 + 摘要 + 三区明细（自动通过/人工确认/人工拒绝）+ 等级汇总。
 * JSON 区块由后端以字符串返回，这里解析渲染；解析失败按空区块降级，不阻塞整页。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  generateCapabilityReport,
  listCapabilityReports,
  type CapabilityAnalysisItem,
  type CapabilityAnalysisReport,
} from '@/api/employee'

const props = defineProps<{ empId: number }>()

const loading = ref(false)
const generating = ref(false)
const reports = ref<CapabilityAnalysisReport[]>([])
const currentVersion = ref<number | null>(null)

const current = computed(() =>
  reports.value.find(report => report.versionNo === currentVersion.value) ?? null)

const LEVEL_MAP: Record<number, string> = { 1: 'L1 初级', 2: 'L2 中级', 3: 'L3 高级', 4: 'L4 专家', 5: 'L5 权威' }

function levelText(level: number | null): string {
  return level == null ? '--' : (LEVEL_MAP[level] ?? `L${level}`)
}

function parseItems(json: string | null): CapabilityAnalysisItem[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? (parsed as CapabilityAnalysisItem[]) : []
  } catch {
    return []
  }
}

const autoPassed = computed(() => parseItems(current.value?.autoPassedJson ?? null))
const manualConfirmed = computed(() => parseItems(current.value?.manualConfirmedJson ?? null))
const manualRejected = computed(() => parseItems(current.value?.manualRejectedJson ?? null))
const finalLevels = computed(() => parseItems(current.value?.finalLevelsJson ?? null))

function formatTime(raw: string | null): string {
  return raw ? raw.replace('T', ' ').slice(0, 16) : '--'
}

async function load() {
  loading.value = true
  try {
    const res = await listCapabilityReports(props.empId)
    reports.value = res.data ?? []
    currentVersion.value = reports.value[0]?.versionNo ?? null
  } catch {
    reports.value = []
    currentVersion.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 手动补生成报告（兜底）。
 * 自动生成依赖审核链路的 AFTER_COMMIT 事件，事件漏触发时报告不会落库 ——
 * 表现为「能力都审核通过了却仍显示暂无报告」。仍有待审内容时后端会返回带具体数量的原因。
 */
async function generate() {
  generating.value = true
  try {
    await generateCapabilityReport(props.empId)
    ElMessage.success('报告已生成')
    await load()
  } catch (e: any) {
    ElMessage.warning(e?.message || '报告生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="car">
    <template v-if="reports.length > 0">
      <div class="car__toolbar">
        <el-select v-model="currentVersion" placeholder="报告版本" class="car__version">
          <el-option
            v-for="report in reports"
            :key="report.id"
            :label="`v${report.versionNo}${report.createdTime ? ' · ' + report.createdTime.replace('T', ' ').slice(0, 10) : ''}`"
            :value="report.versionNo"
          />
        </el-select>
        <span v-if="current?.summary" class="car__summary">{{ current.summary }}</span>
      </div>

      <section class="car__section">
        <h3 class="car__section-title is-auto">✅ harness 自动通过（{{ autoPassed.length }}）</h3>
        <p v-if="autoPassed.length === 0" class="car__empty">无自动通过项</p>
        <ul v-else class="car__list">
          <li v-for="(item, index) in autoPassed" :key="`auto-${index}`" class="car__row">
            <b>{{ item.abilityName || '未命名能力项' }}</b>
            <el-tag size="small" type="success">{{ levelText(item.finalLevel) }}</el-tag>
            <small>由 harness 自动判定通过，证据链已归档至能力画像</small>
          </li>
        </ul>
      </section>

      <section class="car__section">
        <h3 class="car__section-title is-confirmed">👤 人工确认通过（{{ manualConfirmed.length }}）</h3>
        <p v-if="manualConfirmed.length === 0" class="car__empty">无人工确认项</p>
        <ul v-else class="car__list">
          <li v-for="(item, index) in manualConfirmed" :key="`confirmed-${index}`" class="car__row">
            <div class="car__row-main">
              <b>{{ item.abilityName || '未命名能力项' }}</b>
              <el-tag size="small" type="primary">{{ levelText(item.finalLevel) }}</el-tag>
            </div>
            <small class="car__reason">
              审核理由：{{ item.comment || '（未填写理由）' }}
              <span v-if="item.reviewedTime"> · {{ formatTime(item.reviewedTime) }}</span>
            </small>
          </li>
        </ul>
      </section>

      <section class="car__section">
        <h3 class="car__section-title is-rejected">🚫 人工拒绝（{{ manualRejected.length }}）</h3>
        <p v-if="manualRejected.length === 0" class="car__empty">无人工拒绝项</p>
        <ul v-else class="car__list">
          <li v-for="(item, index) in manualRejected" :key="`rejected-${index}`" class="car__row">
            <div class="car__row-main">
              <b>{{ item.abilityName || '未命名能力项' }}</b>
              <el-tag size="small" type="danger">未通过</el-tag>
            </div>
            <small class="car__reason">
              拒绝理由：{{ item.comment || '（未填写理由）' }}
              <span v-if="item.reviewedTime"> · {{ formatTime(item.reviewedTime) }}</span>
            </small>
          </li>
        </ul>
      </section>

      <section class="car__section">
        <h3 class="car__section-title">📊 最终等级汇总（{{ finalLevels.length }}）</h3>
        <el-table :data="finalLevels" size="small" border>
          <el-table-column label="能力项" min-width="160">
            <template #default="{ row }">{{ row.abilityName || '未命名能力项' }}</template>
          </el-table-column>
          <el-table-column label="最终等级" width="120">
            <template #default="{ row }">{{ levelText(row.finalLevel) }}</template>
          </el-table-column>
          <el-table-column label="确认方式" width="140">
            <template #default="{ row }">
              {{
                row.decisionStatus === 'AUTO_CONFIRMED'
                  ? 'harness 自动'
                  : row.decisionStatus === 'HUMAN_CONFIRMED'
                    ? '人工确认'
                    : row.decisionStatus === 'REJECTED'
                      ? '人工拒绝'
                      : row.decisionStatus || '--'
              }}
            </template>
          </el-table-column>
          <el-table-column label="审核时间" width="160">
            <template #default="{ row }">{{ formatTime(row.reviewedTime) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <el-empty v-else-if="!loading" description="该员工暂无能力分析报告">
      <template #description>
        <div>
          <p style="margin: 0 0 6px;">该员工暂无能力分析报告</p>
          <p style="margin: 0; font-size: 12px; opacity: 0.72; line-height: 1.7;">
            报告在「全部能力项人工审核完成」后自动生成。<br />
            若确认已审核完仍为空，可手动补生成（系统会告诉你还差什么）。
          </p>
        </div>
      </template>
      <el-button type="primary" :loading="generating" @click="generate">生成报告</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.car { display: flex; flex-direction: column; gap: 16px; }
.car__toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.car__version { width: 220px; }
.car__summary { color: var(--app-text-secondary, #606266); font-size: 13px; line-height: 1.7; }
.car__section { border: 1px solid var(--app-divider, #e4e7ed); border-radius: 10px; padding: 12px 16px; }
.car__section-title { margin: 0 0 8px; font-size: 13px; font-weight: 700; color: var(--app-text-strong, #1a2440); }
.car__section-title.is-auto { color: var(--app-success, #12b76a); }
.car__section-title.is-confirmed { color: var(--app-primary, #2f6bff); }
.car__section-title.is-rejected { color: var(--app-danger, #f04438); }
.car__list { margin: 0; padding: 0; list-style: none; }
.car__row { display: flex; flex-direction: column; gap: 4px; padding: 8px 0; border-bottom: 1px solid var(--app-divider, #ebeef5); }
.car__row:last-child { border-bottom: 0; }
.car__row > b { color: var(--app-text-strong, #1a2440); font-size: 13px; }
.car__row-main { display: flex; align-items: center; gap: 10px; }
.car__row-main b { color: var(--app-text-strong, #1a2440); font-size: 13px; }
.car__reason { color: var(--app-text-muted, #8b95ab); font-size: 12px; line-height: 1.6; }
.car__empty { margin: 4px 0; color: var(--app-text-muted, #909399); font-size: 12px; }
</style>
