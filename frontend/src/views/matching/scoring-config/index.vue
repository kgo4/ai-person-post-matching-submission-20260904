<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMatchingScoringConfig, saveMatchingScoringConfig } from '@/api/matching'
import { DEFAULT_DIMENSION_WEIGHTS, L2_MODE_DEFAULTS, buildScoringWeightUpdate, getWeightTotal, normalizeDimensionWeights, validateDimensionWeights, type DimensionWeightValues } from './logic'

const loading = ref(false)
const saving = ref(false)
const version = ref('默认配置')
const form = reactive<DimensionWeightValues & { whitelistBypassHardRules: boolean }>({
  ...DEFAULT_DIMENSION_WEIGHTS,
  whitelistBypassHardRules: true,
})
const l2 = reactive({ mode: 'BALANCED' as 'LENIENT' | 'BALANCED' | 'STRICT', ...L2_MODE_DEFAULTS.BALANCED })

const items: Array<{ key: keyof DimensionWeightValues; title: string; description: string; max: number }> = [
  { key: 'abilityWeight', title: '能力等级匹配', description: '人员正式能力等级与岗位要求的逐项匹配，是主要评分依据。', max: 100 },
  { key: 'semanticWeight', title: '语义匹配', description: '人员与岗位文本的受控语义相近程度。', max: 100 },
  { key: 'evidenceWeight', title: '证据可信度', description: 'Harness、测试、面试与人工确认等证据的质量和时效。', max: 100 },
  { key: 'aiWeight', title: 'AI 综合评分', description: 'AI 仅解释服务端证据包；模型不可用时由服务端事实分替代。', max: 20 },
]

const total = computed(() => getWeightTotal(form))
const validationMessage = computed(() => validateDimensionWeights(form))

async function load() {
  loading.value = true
  try {
    const response = await getMatchingScoringConfig()
    Object.assign(form, normalizeDimensionWeights(response.data || {}), {
      whitelistBypassHardRules: response.data?.whitelistBypassHardRules ?? true,
    })
    version.value = response.data?.version || '默认配置'
    Object.assign(l2, {
      mode: response.data?.l2MatchingMode || 'BALANCED',
      ...L2_MODE_DEFAULTS[response.data?.l2MatchingMode || 'BALANCED'],
      ...response.data,
    })
  } finally {
    loading.value = false
  }
}

function restoreDefaults() {
  Object.assign(form, DEFAULT_DIMENSION_WEIGHTS)
  Object.assign(l2, { mode: 'BALANCED', ...L2_MODE_DEFAULTS.BALANCED })
}

function applyMode() {
  Object.assign(l2, L2_MODE_DEFAULTS[l2.mode])
}

async function save() {
  if (validationMessage.value) {
    ElMessage.warning(validationMessage.value)
    return
  }
  saving.value = true
  try {
    await saveMatchingScoringConfig({
      ...buildScoringWeightUpdate(form, form.whitelistBypassHardRules),
      l2MatchingMode: l2.mode,
      requiredSemanticThreshold: l2.requiredSemanticThreshold,
      coreSemanticThreshold: l2.coreSemanticThreshold,
      optionalSemanticThreshold: l2.optionalSemanticThreshold,
      similarTagMinimumConfidence: l2.similarTagMinimumConfidence,
      allowedLevelGap: l2.allowedLevelGap,
      coreCoverageThreshold: l2.coreCoverageThreshold,
      requiredCoverageThreshold: l2.requiredCoverageThreshold,
      l2PassThreshold: l2.l2PassThreshold,
      aiTriggerThreshold: l2.aiTriggerThreshold,
    })
    ElMessage.success('匹配评分配置已保存')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Matching Governance</div>
        <h1 class="page-hero__title">匹配评分配置</h1>
        <p class="page-hero__desc">硬条件是资格门槛；正式分由四个固定维度组成，权重必须合计 100%。</p>
      </div>
      <el-tag type="info">版本 {{ version }}</el-tag>
    </section>

    <section class="config-panel">
      <div class="config-summary">
        <strong>权重合计 {{ total.toFixed(2) }}%</strong>
        <el-tag :type="validationMessage ? 'danger' : 'success'">{{ validationMessage || '配置有效' }}</el-tag>
      </div>
      <div class="weight-list">
        <div v-for="item in items" :key="item.key" class="weight-row">
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
          <el-input-number v-model="form[item.key]" :min="0" :max="item.max" :step="0.05" :precision="2" controls-position="right" />
          <span>{{ form[item.key].toFixed(0) }}%</span>
        </div>
      </div>
      <el-alert type="info" :closable="false" show-icon title="RAG 仅用于受控检索上下文和报告解释，不参与人员岗位正式评分。" />
      <div class="l2-section">
        <div class="l2-heading"><div><strong>L2 能力匹配策略</strong><p>AI 评分继续作为独立维度参与正式分；以下参数控制标签语义匹配和进入 AI 深度分析的门槛。</p></div><el-select v-model="l2.mode" style="width: 140px" @change="applyMode"><el-option label="宽松模式" value="LENIENT" /><el-option label="均衡模式（默认）" value="BALANCED" /><el-option label="严格模式" value="STRICT" /></el-select></div>
        <div class="threshold-grid">
          <label>必填语义阈值<el-input-number v-model="l2.requiredSemanticThreshold" :min="0" :max="100" :step="1" /></label>
          <label>核心语义阈值<el-input-number v-model="l2.coreSemanticThreshold" :min="0" :max="1" :step="0.01" :precision="2" /></label>
          <label>普通语义阈值<el-input-number v-model="l2.optionalSemanticThreshold" :min="0" :max="1" :step="0.01" :precision="2" /></label>
          <label>相似标签最低置信度<el-input-number v-model="l2.similarTagMinimumConfidence" :min="0" :max="1" :step="0.01" :precision="2" /></label>
          <label>允许等级差距<el-input-number v-model="l2.allowedLevelGap" :min="0" :max="3" :step="1" /></label>
          <label>核心覆盖率<el-input-number v-model="l2.coreCoverageThreshold" :min="0" :max="1" :step="0.05" :precision="2" /></label>
          <label>必填覆盖率<el-input-number v-model="l2.requiredCoverageThreshold" :min="0" :max="1" :step="0.05" :precision="2" /></label>
          <label>L2 通过分<el-input-number v-model="l2.l2PassThreshold" :min="0" :max="100" :step="1" /></label>
          <label>AI 触发分<el-input-number v-model="l2.aiTriggerThreshold" :min="0" :max="100" :step="1" /></label>
        </div>
      </div>
      <div class="policy-row">
        <div><strong>白名单绕过硬条件</strong><p>黑名单仍然优先淘汰。</p></div>
        <el-switch v-model="form.whitelistBypassHardRules" />
      </div>
      <div class="actions">
        <el-button @click="restoreDefaults">恢复默认</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-hero, .config-summary, .weight-row, .policy-row, .actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.config-panel { max-width: 960px; margin: 24px auto; padding: 24px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); }
.l2-section { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--el-border-color); }
.l2-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.l2-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.threshold-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
.threshold-grid label { display: flex; flex-direction: column; gap: 6px; color: var(--el-text-color-regular); }
.weight-list { margin: 18px 0; }
.weight-row { padding: 16px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.weight-row > div { flex: 1; min-width: 0; }
.weight-row p, .policy-row p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.weight-row > span { width: 48px; text-align: right; }
.policy-row { margin: 18px 0; }
.actions { justify-content: flex-end; margin-top: 20px; }
</style>
