<script setup lang="ts">
/**
 * 全局权重配置（匹配正式评分，`MATCHING:CONFIG`）。
 *
 * 页面信息层级（对齐系统其它配置页，不再是一张长卡片堆所有东西）：
 *   1. page-hero：版本 / 权重合计 / 策略模式 + 恢复默认、保存；
 *   2. 权重分布：把四个维度画成一条占比条，让「合计必须 100%」一眼可见；
 *   3. 维度权重：滑块粗调 + 数字框精调，两行对齐；
 *   4. L2 能力匹配策略：9 个阈值由平铺改为按语义 / 覆盖 / 判定三组归类；
 *   5. 策略开关：白名单与硬条件的关系；
 *   6. 底部校验条：合计与校验结论，收尾时不必回到顶部确认。
 *
 * 配色取自 `workbench-chart-theme` 的蓝色梯度（有序维度用同色系深浅），
 * 权重分布条、图例与行内圆点共用同一色值，避免同屏出现多种蓝。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, RefreshLeft } from '@element-plus/icons-vue'
import { getMatchingScoringConfig, saveMatchingScoringConfig } from '@/api/matching'
import { WORKBENCH_CHART } from '@/views/workbench/workbench-chart-theme'
import {
  DEFAULT_DIMENSION_WEIGHTS,
  L2_FIELD_GROUPS,
  L2_FIELDS,
  L2_MODE_DEFAULTS,
  L2_MODE_LABELS,
  buildScoringWeightUpdate,
  getWeightTotal,
  l2FieldSuffix,
  normalizeDimensionWeights,
  resolveL2Params,
  validateDimensionWeights,
  type DimensionWeightValues,
  type L2Field,
  type L2Mode,
  type L2Params,
} from './logic'

const loading = ref(false)
const saving = ref(false)
const version = ref('默认配置')
const form = reactive<DimensionWeightValues & { whitelistBypassHardRules: boolean }>({
  ...DEFAULT_DIMENSION_WEIGHTS,
  whitelistBypassHardRules: true,
})

const l2 = reactive<L2Params & { mode: L2Mode }>({
  mode: 'BALANCED',
  ...L2_MODE_DEFAULTS.BALANCED,
})

/** 维度定义：颜色按「越主导越深」取同一蓝色梯度 */
const items: Array<{ key: keyof DimensionWeightValues; title: string; description: string; max: number; color: string }> = [
  { key: 'abilityWeight', title: '能力等级匹配', description: '人员正式能力等级与岗位要求的逐项匹配，是主要评分依据。', max: 100, color: WORKBENCH_CHART.strong },
  { key: 'semanticWeight', title: '语义匹配', description: '人员与岗位文本的受控语义相近程度。', max: 100, color: WORKBENCH_CHART.primary },
  { key: 'evidenceWeight', title: '证据可信度', description: 'Harness、测试、面试与人工确认等证据的质量和时效。', max: 100, color: WORKBENCH_CHART.light },
  { key: 'aiWeight', title: 'AI 综合评分', description: 'AI 仅解释服务端证据包；模型不可用时由服务端事实分替代，上限 20%。', max: 20, color: WORKBENCH_CHART.lighter },
]

const total = computed(() => getWeightTotal(form))
const validationMessage = computed(() => validateDimensionWeights(form))
const modeLabel = computed(() => L2_MODE_LABELS[l2.mode])

const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getMatchingScoringConfig()
    const data = response.data
    Object.assign(form, normalizeDimensionWeights(data || {}), {
      whitelistBypassHardRules: data?.whitelistBypassHardRules ?? true,
    })
    version.value = data?.version || '默认配置'
    const mode: L2Mode = data?.l2MatchingMode || 'BALANCED'
    Object.assign(l2, { mode }, resolveL2Params(mode, data))
  } catch {
    // 表单/落地类页面不用 toast 兜底：常驻提示说明原因，并保留默认值可继续操作
    loadError.value = '配置加载失败，当前展示的是系统默认值；保存前请先刷新确认线上配置。'
  } finally {
    loading.value = false
  }
}

function restoreDefaults() {
  Object.assign(form, DEFAULT_DIMENSION_WEIGHTS)
  Object.assign(l2, { mode: 'BALANCED' }, L2_MODE_DEFAULTS.BALANCED)
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
        <h1 class="page-hero__title">全局权重配置</h1>
        <p class="page-hero__desc">
          硬条件只作资格门槛；正式分由四个固定维度加权得出，权重合计必须为 100%。修改后对所有新发起的匹配生效。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">当前版本 {{ version }}</span>
          <span class="hero-chip">权重合计 {{ total.toFixed(2) }}%</span>
          <span class="hero-chip">L2 策略 {{ modeLabel }}</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-button :icon="RefreshLeft" @click="restoreDefaults">恢复默认</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
      </div>
    </section>

    <el-alert
      v-if="loadError"
      type="warning"
      show-icon
      :closable="false"
      title="配置加载失败"
      :description="loadError"
    />

    <!-- 权重分布：把「合计 100%」变成看得见的占比条 -->
    <section class="glass-card">
      <div class="panel-body">
        <div class="section-header">
          <div>
            <div class="section-title">权重分布</div>
            <div class="section-desc">四个维度在正式分中的占比，合计必须为 100%，否则无法保存。</div>
          </div>
          <el-tag :type="validationMessage ? 'danger' : 'success'" effect="plain" round>
            {{ validationMessage || '配置有效' }}
          </el-tag>
        </div>

        <div class="weight-bar" role="img" :aria-label="`权重合计 ${total.toFixed(2)}%`">
          <div
            v-for="item in items"
            :key="item.key"
            class="weight-bar__seg"
            :style="{ flexGrow: Math.max(form[item.key], 0), background: item.color }"
          >
            <span v-if="form[item.key] >= 10">{{ form[item.key].toFixed(0) }}%</span>
          </div>
        </div>

        <div class="weight-legend">
          <span v-for="item in items" :key="item.key" class="weight-legend__item">
            <i :style="{ background: item.color }" />
            {{ item.title }}
            <em>{{ form[item.key].toFixed(2) }}%</em>
          </span>
        </div>
      </div>
    </section>

    <!-- 维度权重：滑块粗调 + 数字框精调 -->
    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">维度权重</div>
          <div class="section-desc">拖动滑块粗调，右侧输入框精确到 0.01；AI 综合评分权重上限 20%。</div>
        </div>
        <el-tag type="info" effect="plain" round>合计 {{ total.toFixed(2) }}%</el-tag>
      </div>
      <div class="panel-body weight-list">
        <div v-for="item in items" :key="item.key" class="weight-row">
          <div class="weight-row__meta">
            <span class="weight-row__dot" :style="{ background: item.color }" />
            <div>
              <div class="weight-row__title">{{ item.title }}</div>
              <p class="weight-row__desc">{{ item.description }}</p>
            </div>
          </div>
          <el-slider
            v-model="form[item.key]"
            :min="0"
            :max="item.max"
            :step="1"
            :show-tooltip="false"
            class="weight-row__slider"
          />
          <el-input-number
            v-model="form[item.key]"
            :min="0"
            :max="item.max"
            :step="0.05"
            :precision="2"
            controls-position="right"
            class="weight-row__input"
          />
        </div>
      </div>
    </section>

    <!-- L2 能力匹配策略：9 个阈值按用途分三组 -->
    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">L2 能力匹配策略</div>
          <div class="section-desc">切换到预设模式会覆盖下方全部阈值，可再逐项微调。</div>
        </div>
        <el-select v-model="l2.mode" style="width: 180px" @change="applyMode">
          <el-option label="宽松模式" value="LENIENT" />
          <el-option label="均衡模式（默认）" value="BALANCED" />
          <el-option label="严格模式" value="STRICT" />
        </el-select>
      </div>
      <div class="panel-body l2-groups">
        <article v-for="group in L2_FIELD_GROUPS" :key="group.title" class="l2-group">
          <header class="l2-group__head">
            <div class="l2-group__title">{{ group.title }}</div>
            <p class="l2-group__hint">{{ group.hint }}</p>
          </header>
          <div class="l2-group__fields">
            <div v-for="field in group.fields" :key="field.key" class="l2-field">
              <label class="l2-field__label" :for="`l2-${field.key}`">{{ field.label }}</label>
              <el-input-number
                :id="`l2-${field.key}`"
                v-model="l2[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step"
                :precision="field.precision"
                controls-position="right"
                class="l2-field__input"
              />
              <span class="l2-field__suffix">{{ l2FieldSuffix(field, l2[field.key]) }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- 策略开关 -->
    <section class="glass-card">
      <div class="panel-body">
        <div class="policy-row">
          <div>
            <div class="section-title">白名单绕过硬条件</div>
            <div class="section-desc">开启后，白名单人员的匹配不会被硬条件淘汰；黑名单仍然优先淘汰，不受此开关影响。</div>
          </div>
          <el-switch v-model="form.whitelistBypassHardRules" />
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="RAG 仅用于受控检索上下文与报告解释，不参与人员岗位正式评分。"
        />
      </div>
    </section>

    <!-- 底部校验条：收尾时不必回到顶部确认 -->
    <section class="glass-card">
      <div class="panel-body check-bar">
        <div class="check-bar__left">
          <span class="check-bar__total">权重合计 <strong>{{ total.toFixed(2) }}%</strong></span>
          <el-tag :type="validationMessage ? 'danger' : 'success'" effect="plain" round>
            {{ validationMessage || '配置有效' }}
          </el-tag>
        </div>
        <div class="toolbar-group">
          <el-button :icon="RefreshLeft" @click="restoreDefaults">恢复默认</el-button>
          <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------- 权重分布条 ---------- */
.weight-bar {
  display: flex;
  gap: 3px;
  height: 34px;
  padding: 4px;
  border-radius: 10px;
  background: var(--app-surface-soft, #f4f7fd);
  border: 1px solid var(--app-border, #e8ecf5);
}

.weight-bar__seg {
  display: grid;
  place-items: center;
  /* flex-basis 归零，占比完全由 flex-grow（= 权重值）决定，
     否则段内文字宽度会参与基准尺寸，把比例带偏 */
  flex-basis: 0;
  min-width: 0;
  border-radius: 7px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  transition: flex-grow 0.25s ease;
}

.weight-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 14px;
}

.weight-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--app-text-secondary, #414a63);
  font-size: 13px;
}

.weight-legend__item i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.weight-legend__item em {
  font-style: normal;
  font-weight: 700;
  color: var(--app-text-strong, #1a2440);
}

/* ---------- 维度权重行 ---------- */
.weight-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.weight-row {
  display: grid;
  grid-template-columns: minmax(240px, 1.4fr) minmax(160px, 1fr) 150px;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--app-divider, #eef2fb);
}

.weight-row:last-child {
  border-bottom: none;
}

.weight-row__meta {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.weight-row__dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.weight-row__title {
  color: var(--app-text-strong, #1a2440);
  font-size: 14px;
  font-weight: 700;
}

.weight-row__desc {
  margin: 4px 0 0;
  color: var(--app-text-muted, #8b95ab);
  font-size: 12px;
  line-height: 1.6;
}

.weight-row__slider {
  min-width: 0;
}

.weight-row__input {
  width: 100%;
}

/* ---------- L2 阈值分组 ---------- */
.l2-groups {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.l2-group {
  padding: 16px 18px;
  border: 1px solid var(--app-border, #e8ecf5);
  border-radius: var(--app-radius-md, 12px);
  background: var(--app-surface-soft, #f9fbff);
}

.l2-group__head {
  margin-bottom: 14px;
}

.l2-group__title {
  color: var(--app-text-strong, #1a2440);
  font-size: 14px;
  font-weight: 700;
}

.l2-group__hint {
  margin: 4px 0 0;
  color: var(--app-text-muted, #8b95ab);
  font-size: 12px;
  line-height: 1.6;
}

.l2-group__fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 14px 18px;
}

.l2-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.l2-field__label {
  color: var(--app-text-secondary, #414a63);
  font-size: 12px;
  font-weight: 600;
}

.l2-field__input {
  width: 100%;
}

.l2-field__suffix {
  color: var(--app-text-muted, #8b95ab);
  font-size: 11px;
}

/* ---------- 策略开关 / 底部校验条 ---------- */
.policy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}

.check-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px 20px;
}

.check-bar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.check-bar__total {
  color: var(--app-text-secondary, #414a63);
  font-size: 13px;
}

.check-bar__total strong {
  margin-left: 4px;
  color: var(--app-text-strong, #1a2440);
  font-size: 18px;
  font-weight: 800;
}

@media (max-width: 1024px) {
  .weight-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
  .weight-row__input {
    width: 160px;
  }
}
</style>
