<script setup lang="ts">
/**
 * 员工全方位评估报告面板（HR 侧「评估报告」Tab）。
 *
 * 能力评估域**只有一个报告入口**：四部分（简历证据 / AI 测试 / AI 面试 / 最终等级）
 * 汇聚 + AI 综合洞察。原并列的「能力分析报告」降级为报告底部的折叠区（能力达成度详情），
 * 保留其版本化快照与审核理由追溯能力。
 *
 * 报告主体在面试结束时落库，「AI 综合洞察」在该员工全部能力项人工审核清空后生成。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getComprehensiveReportLatest,
  regenerateComprehensiveReport,
  type ComprehensiveAssessmentReportDetail,
} from '@/api/assessment'
import ReportContent from '@/views/employee/ability-profile/assessment-report-content.vue'
import CapabilityAnalysisReportPanel from './CapabilityAnalysisReportPanel.vue'

const props = defineProps<{ empId: number }>()

const loading = ref(false)
const regenerating = ref(false)
const detail = ref<ComprehensiveAssessmentReportDetail | null>(null)

/** 报告不可读时的原因（流程未完成 / 审核未清空 / 报告主体未生成） */
const blockedReason = computed(() =>
  detail.value && !detail.value.available ? (detail.value.unavailableReason || '报告暂不可查看') : '',
)

async function load() {
  loading.value = true
  try {
    const res = await getComprehensiveReportLatest(props.empId)
    detail.value = res.data ?? null
  } catch (e: any) {
    ElMessage.error(e?.message || '评估报告加载失败，请稍后重试')
    detail.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 重新生成 AI 综合洞察。
 * 只重写洞察文字：数值与结论全部来自既有链路，不受影响；
 * 报告尚不可读时后端返回具体原因，这里原样提示而不是静默成功。
 */
async function regenerate() {
  regenerating.value = true
  try {
    await regenerateComprehensiveReport(props.empId)
    ElMessage.success('洞察已重新生成')
    await load()
  } catch (e: any) {
    ElMessage.warning(e?.message || '洞察重新生成失败，请稍后重试')
  } finally {
    regenerating.value = false
  }
}

onMounted(load)
watch(() => props.empId, load)
</script>

<template>
  <div v-loading="loading" class="crp">
    <template v-if="detail && detail.available">
      <ReportContent :report="detail" />

      <el-collapse class="crp__more">
        <el-collapse-item title="能力达成度详情（审核逐项结论与理由）" name="capability">
          <CapabilityAnalysisReportPanel :emp-id="empId" />
        </el-collapse-item>
      </el-collapse>

      <div class="crp__actions">
        <el-button size="small" :icon="Refresh" :loading="regenerating" @click="regenerate">
          重新生成 AI 洞察
        </el-button>
        <span class="crp__hint">只重写洞察文字；分数、等级与结论均来自既有评估链路，不受影响。</span>
      </div>
    </template>

    <el-empty v-else-if="!loading" :image-size="100">
      <template #description>
        <div class="crp__empty">
          <p class="crp__empty-title">{{ blockedReason || '该员工暂无评估报告' }}</p>
          <p class="crp__empty-desc">
            报告在 AI 面试完成、且全部能力项人工审核通过后自动生成。<br />
            报告包含：简历提取证据、AI 测试结果、AI 面试报告、最终等级结论与 AI 综合洞察。
          </p>
        </div>
      </template>
    </el-empty>
  </div>
</template>

<style scoped>
.crp { display: flex; flex-direction: column; gap: 14px; }
.crp__more { border-top: 1px solid var(--app-divider, #e4e7ed); padding-top: 6px; }
.crp__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.crp__hint { color: var(--app-text-muted, #8b95ab); font-size: 12px; }
.crp__empty { text-align: center; }
.crp__empty-title { margin: 0 0 6px; font-size: 14px; font-weight: 600; color: var(--app-text-strong, #1a2440); }
.crp__empty-desc { margin: 0; font-size: 12px; line-height: 1.8; color: var(--app-text-muted, #8b95ab); }
</style>
