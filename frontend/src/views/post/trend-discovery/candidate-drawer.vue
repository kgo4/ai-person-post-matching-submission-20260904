<script setup lang="ts">
/**
 * 趋势候选详情抽屉（「全量查看 + 调整」的唯一入口）。
 *
 * 设计取向：卡片只放「一眼判断要不要点进去」的字段，其余全部收在这里。
 * 所以本抽屉承担三件事，且都要**按需加载**：
 *   1. 全量查看：完整能力清单、职责、业务场景、证据原文、来源材料、治理理由；
 *   2. 展开调整：岗位名/描述/能力清单可改（默认路径不需要改，零手工填写是常态）；
 *   3. 落地：确认创建（新岗位）/ 确认变更（既有岗位）。
 *
 * 「新能力标签」区只在**存在未归位能力**时出现；没有就整块不渲染，
 * 否则每张候选都挂一个空的标签区，又会回到信息倾倒。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Refresh } from '@element-plus/icons-vue'
import {
  adoptNewTagCandidate,
  confirmTrendCandidate,
  getTrendCandidateDetail,
  ignoreNewTagCandidate,
  listNewTagCandidates,
  updateTrendCandidatePayload,
} from '@/api/post-trend'
import type {
  NewTagCandidate,
  TrendAbilityItem,
  TrendAbilityUpdateItem,
  TrendCandidateDetail,
} from '@/api/post-trend'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import {
  emphasisText,
  harnessBadge,
  similarityText,
  changeTypeLabel,
  changeTypeTagType,
  coverageBadge,
  weightTotalHint,
} from './trend-candidate-meta'

const props = defineProps<{
  modelValue: boolean
  candidateId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'landed', postName: string, createdNewPost: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const loading = ref(false)
/** 常驻错误提示：不用一闪而过的 toast，失败后管理员要能对着原因排查 */
const loadError = ref('')
const actionError = ref('')
const saving = ref(false)
const landing = ref(false)

const detail = ref<TrendCandidateDetail | null>(null)
const tagCandidates = ref<NewTagCandidate[]>([])

/** 可编辑副本 */
const formName = ref('')
const formDescription = ref('')
const formAbilities = ref<TrendAbilityUpdateItem[]>([])
const dirty = ref(false)

const isNewPost = computed(() => detail.value?.summary.candidateType === 'NEW_POST')
const isPending = computed(() => detail.value?.summary.confirmStatus === 'PENDING')
const blockedReason = computed(() => {
  const decision = detail.value?.summary.harnessDecision
  if (decision === 'PASS') return ''
  if (decision === 'REVIEW') return '治理判定为「待复核」：请核对下面的证据与来源，确认无误后再落地'
  if (decision === 'BLOCK') return '治理判定为「已拦截」：证据不足或来源不可采信，不允许落地'
  return '缺少治理判定结果，请人工确认后再落地'
})
const weightHint = computed(() => weightTotalHint(formAbilities.value))
const unresolvedAbilities = computed(
  () => detail.value?.payload.abilities.filter(item => !item.resolved) ?? []
)

watch(
  () => [props.modelValue, props.candidateId] as const,
  ([open, id]) => {
    if (open && id) {
      void load(id)
    }
  },
  { immediate: true }
)

async function load(candidateId: number) {
  loading.value = true
  loadError.value = ''
  actionError.value = ''
  detail.value = null
  tagCandidates.value = []
  try {
    const res = await getTrendCandidateDetail(candidateId)
    const data = res.data
    if (!data) {
      loadError.value = '未取得候选详情，请稍后重试'
      return
    }
    detail.value = data
    formName.value = data.summary.postName ?? ''
    formDescription.value = data.summary.postDescription ?? ''
    formAbilities.value = data.payload.abilities.map(toEditable)
    dirty.value = false
    if (data.newTagCandidateIds.length > 0) {
      await loadTagCandidates(data.summary.taskId)
    }
  } catch (error) {
    loadError.value = resolveApiErrorMessage(error, '候选详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTagCandidates(taskId: number) {
  try {
    const res = await listNewTagCandidates(taskId)
    const ids = new Set(detail.value?.newTagCandidateIds ?? [])
    tagCandidates.value = (res.data ?? []).filter(item => ids.has(item.id))
  } catch {
    // 标签候选加载失败不阻断详情查看；这里静默降级，界面上就不显示该区块
    tagCandidates.value = []
  }
}

function toEditable(item: TrendAbilityItem): TrendAbilityUpdateItem {
  return {
    abilityName: item.abilityName,
    tagId: item.tagId ?? null,
    suggestedLevel: item.suggestedLevel ?? null,
    suggestedWeight: item.suggestedWeight ?? null,
    isCore: item.isCore ?? 0,
  }
}

function markDirty() {
  dirty.value = true
}

function addAbility() {
  formAbilities.value.push({
    abilityName: '',
    tagId: null,
    suggestedLevel: 3,
    suggestedWeight: 10,
    isCore: 0,
  })
  markDirty()
}

function removeAbility(index: number) {
  formAbilities.value.splice(index, 1)
  markDirty()
}

/** 找出该能力对应的能力标签候选（按名称对齐） */
function tagCandidateOf(abilityName: string): NewTagCandidate | undefined {
  return tagCandidates.value.find(item => item.candidateName === abilityName)
}

async function handleSave() {
  if (!detail.value) return
  const abilities = formAbilities.value.filter(item => item.abilityName.trim().length > 0)
  if (abilities.length === 0) {
    actionError.value = '能力清单不能为空，否则落地后岗位将没有任何能力要求'
    return
  }
  saving.value = true
  actionError.value = ''
  try {
    await updateTrendCandidatePayload(detail.value.summary.id, {
      postName: formName.value.trim() || undefined,
      postDescription: formDescription.value || undefined,
      abilities,
    })
    ElMessage.success('调整已保存')
    dirty.value = false
    await load(detail.value.summary.id)
  } catch (error) {
    actionError.value = resolveApiErrorMessage(error, '保存调整失败')
  } finally {
    saving.value = false
  }
}

async function handleLand() {
  if (!detail.value) return
  landing.value = true
  actionError.value = ''
  try {
    const res = await confirmTrendCandidate(detail.value.summary.id)
    const result = res.data
    if (result) {
      emit('landed', result.postName, result.createdNewPost)
    }
    dirty.value = false
    visible.value = false
  } catch (error) {
    // 失败保持抽屉打开、保留已填内容，管理员可直接看到原因
    actionError.value = resolveApiErrorMessage(error, '落地失败')
  } finally {
    landing.value = false
  }
}

async function handleAdopt(candidate: NewTagCandidate) {
  if (!candidate.similarTagId) return
  actionError.value = ''
  try {
    await adoptNewTagCandidate(candidate.id, candidate.similarTagId, `采用既有标签「${candidate.similarTagName}」`)
    ElMessage.success(`已归位到「${candidate.similarTagName}」`)
    if (detail.value) await load(detail.value.summary.id)
  } catch (error) {
    actionError.value = resolveApiErrorMessage(error, '采用既有标签失败')
  }
}

async function handleIgnore(candidate: NewTagCandidate) {
  actionError.value = ''
  try {
    await ignoreNewTagCandidate(candidate.id, '该能力名不进入标签体系')
    ElMessage.success('已忽略该能力名')
    if (detail.value) await load(detail.value.summary.id)
  } catch (error) {
    actionError.value = resolveApiErrorMessage(error, '忽略失败')
  }
}

function refresh() {
  if (detail.value) void load(detail.value.summary.id)
}
</script>

<template>
  <el-drawer v-model="visible" size="62%" :destroy-on-close="true" class="td-drawer">
    <template #header>
      <div class="td-drawer__header">
        <div>
          <div class="td-drawer__title">
            {{ detail?.summary.postName || '候选详情' }}
          </div>
          <div class="td-drawer__sub">
            <template v-if="detail">
              <el-tag size="small" effect="plain">
                {{ isNewPost ? '新岗位候选' : '能力变更候选' }}
              </el-tag>
              <span v-if="!isNewPost" class="td-drawer__sub-text">
                锚定岗位：{{ detail.summary.matchedPostName || '—' }}
              </span>
              <span class="td-drawer__sub-text">
                相似度 {{ similarityText(detail.summary.similarityScore) }}
              </span>
              <span class="td-drawer__sub-text">
                材料强调度 {{ emphasisText(detail.summary.emphasisScore) }}
              </span>
            </template>
          </div>
        </div>
        <el-button :loading="loading" @click="refresh">
          <el-icon><Refresh /></el-icon> 重新加载
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="td-drawer__body">
      <el-alert
        v-if="loadError"
        class="td-alert"
        type="error"
        :closable="false"
        show-icon
        :title="loadError"
      />

      <template v-if="detail">
        <el-alert
          v-if="blockedReason"
          class="td-alert"
          :type="detail.summary.harnessDecision === 'BLOCK' ? 'error' : 'warning'"
          :closable="false"
          show-icon
          :title="blockedReason"
        >
          <template #default>
            <div v-if="detail.payload.harnessReason" class="td-alert__reason">
              {{ detail.payload.harnessReason }}
            </div>
          </template>
        </el-alert>

        <!-- ============ 岗位信息 ============ -->
        <section class="td-block">
          <div class="td-block__head">
            <span class="td-block__title">岗位信息</span>
            <span class="td-block__desc">默认无需修改；只在解析结果明显偏离材料原意时调整</span>
          </div>
          <div class="td-field">
            <label>岗位名称</label>
            <el-input v-model="formName" :disabled="!isPending" maxlength="100" @input="markDirty" />
          </div>
          <div class="td-field">
            <label>岗位描述</label>
            <el-input
              v-model="formDescription"
              type="textarea"
              :rows="3"
              :disabled="!isPending"
              maxlength="2000"
              show-word-limit
              @input="markDirty"
            />
          </div>
          <div v-if="detail.payload.responsibilities.length" class="td-field">
            <label>职责（AI 归纳，只读）</label>
            <ul class="td-list">
              <li v-for="(item, index) in detail.payload.responsibilities" :key="`r-${index}`">{{ item }}</li>
            </ul>
          </div>
          <div v-if="detail.payload.businessScenarios.length" class="td-field">
            <label>业务场景（AI 归纳，只读）</label>
            <ul class="td-list">
              <li v-for="(item, index) in detail.payload.businessScenarios" :key="`s-${index}`">{{ item }}</li>
            </ul>
          </div>
        </section>

        <!-- ============ 能力清单 ============ -->
        <section class="td-block">
          <div class="td-block__head">
            <span class="td-block__title">能力清单（{{ formAbilities.length }} 项）</span>
            <span class="td-block__desc">
              归位状态来自系统既有能力词表；未归位的能力仍可落库，只是不挂标签
            </span>
          </div>

          <el-alert v-if="weightHint" class="td-alert" type="info" :closable="false" show-icon :title="weightHint" />

          <el-table :data="formAbilities" size="small" border class="td-table">
            <el-table-column label="能力名称" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.abilityName" size="small" :disabled="!isPending" @input="markDirty" />
              </template>
            </el-table-column>
            <el-table-column label="归位" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.tagId" size="small" type="success" effect="plain">已归位</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">未归位</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议等级" width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.suggestedLevel"
                  size="small"
                  :min="1"
                  :max="5"
                  :disabled="!isPending"
                  controls-position="right"
                  @change="markDirty"
                />
              </template>
            </el-table-column>
            <el-table-column label="建议权重" width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.suggestedWeight"
                  size="small"
                  :min="0"
                  :max="100"
                  :step="1"
                  :disabled="!isPending"
                  controls-position="right"
                  @change="markDirty"
                />
              </template>
            </el-table-column>
            <el-table-column label="核心" width="80">
              <template #default="{ row }">
                <el-switch
                  v-model="row.isCore"
                  :active-value="1"
                  :inactive-value="0"
                  :disabled="!isPending"
                  @change="markDirty"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="!isNewPost" label="变更" width="110">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  effect="plain"
                  :type="changeTypeTagType(abilityChangeTypeOf(row.abilityName))"
                >
                  {{ changeTypeLabel(abilityChangeTypeOf(row.abilityName)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="isPending" label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeAbility($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button v-if="isPending" class="td-add" link type="primary" @click="addAbility">
            <el-icon><Plus /></el-icon> 新增能力项
          </el-button>

          <el-alert
            v-if="detail.payload.unmatchedExistingAbilities.length"
            class="td-alert"
            type="warning"
            :closable="false"
            show-icon
            title="既有岗位已有、但本次材料未提及的能力"
          >
            <template #default>
              <div class="td-alert__reason">
                {{ detail.payload.unmatchedExistingAbilities.join('、') }}
              </div>
              <div class="td-alert__hint">
                仅作参考，系统不会因为「材料没提」而删除这些能力：材料是抽样的，没提到不代表岗位不再需要。
              </div>
            </template>
          </el-alert>
        </section>

        <!-- ============ 新能力标签（仅未归位时出现） ============ -->
        <section v-if="unresolvedAbilities.length" class="td-block">
          <div class="td-block__head">
            <span class="td-block__title">新能力标签（{{ unresolvedAbilities.length }} 项待归位）</span>
            <span class="td-block__desc">
              这些能力名在既有词表里没找到对应项；可归位到最相似的既有标签，或忽略该命名
            </span>
          </div>
          <div v-for="ability in unresolvedAbilities" :key="ability.abilityName" class="td-tag-row">
            <div class="td-tag-row__main">
              <span class="td-tag-row__name">{{ ability.abilityName }}</span>
              <template v-if="tagCandidateOf(ability.abilityName)?.similarTagName">
                <span class="td-tag-row__arrow">→</span>
                <span class="td-tag-row__similar">
                  {{ tagCandidateOf(ability.abilityName)?.similarTagName }}
                  <span class="td-tag-row__sim">
                    相似度 {{ similarityText(tagCandidateOf(ability.abilityName)?.similarityScore) }}
                  </span>
                </span>
              </template>
              <span v-else class="td-tag-row__empty">无足够相似的既有标签</span>
            </div>
            <div class="td-tag-row__actions">
              <el-button
                v-if="tagCandidateOf(ability.abilityName)?.similarTagId"
                size="small"
                type="primary"
                plain
                @click="handleAdopt(tagCandidateOf(ability.abilityName)!)"
              >
                采用既有标签
              </el-button>
              <el-button
                v-if="tagCandidateOf(ability.abilityName)"
                size="small"
                @click="handleIgnore(tagCandidateOf(ability.abilityName)!)"
              >
                忽略
              </el-button>
            </div>
          </div>
          <div class="td-hint">
            采用后会把归位结果回填到本任务下所有待确认候选；正式标签的升级仍走标签治理流程，不在本页完成。
          </div>
        </section>

        <!-- ============ 证据 ============ -->
        <section class="td-block">
          <div class="td-block__head">
            <span class="td-block__title">证据原文</span>
            <span class="td-block__desc">
              材料中提到该岗位的片段；提及 {{ detail.payload.mentionCount ?? 0 }} 段
            </span>
          </div>
          <div v-if="coverageBadge(detail.summary.sourceCoverage)" class="td-coverage">
            <el-tag size="small" type="success" effect="plain">
              {{ coverageBadge(detail.summary.sourceCoverage) }}
            </el-tag>
            <span class="td-hint">多个不同类别的材料都提到该岗位，趋势更可信</span>
          </div>
          <pre v-if="detail.evidenceText" class="td-evidence">{{ detail.evidenceText }}</pre>
          <el-empty v-else description="本次未保留证据片段" :image-size="60" />

          <div v-if="detail.sourceTitles.length" class="td-field">
            <label>来源材料</label>
            <div class="td-source-list">
              <span v-for="(title, index) in detail.sourceTitles" :key="`src-${index}`" class="td-source">
                {{ title }}
                <span class="td-source__ref">{{ detail.sourceRefs[index] || '' }}</span>
              </span>
            </div>
          </div>
        </section>

        <!-- ============ 治理判定 ============ -->
        <section class="td-block">
          <div class="td-block__head">
            <span class="td-block__title">治理判定</span>
            <span class="td-block__desc">决定该候选能否批量落地</span>
          </div>
          <div class="td-governance">
            <el-tag :type="harnessBadge(detail.summary.harnessDecision).type" effect="dark">
              {{ harnessBadge(detail.summary.harnessDecision).label }}
            </el-tag>
            <span v-if="detail.summary.riskLevel" class="td-governance__risk">
              风险等级：{{ detail.summary.riskLevel }}
            </span>
          </div>
          <div v-if="detail.payload.harnessReason" class="td-hint">{{ detail.payload.harnessReason }}</div>
        </section>
      </template>
    </div>

    <template #footer>
      <div class="td-drawer__footer">
        <el-alert
          v-if="actionError"
          class="td-alert td-alert--footer"
          type="error"
          :closable="false"
          show-icon
          :title="actionError"
        />
        <div class="td-drawer__footer-actions">
          <el-button @click="visible = false">关闭</el-button>
          <el-button
            v-if="isPending"
            :loading="saving"
            :disabled="!dirty"
            @click="handleSave"
          >
            保存调整
          </el-button>
          <el-button
            v-if="isPending"
            type="primary"
            :loading="landing"
            :disabled="!isNewPost && !detail?.summary.matchedPostId"
            @click="handleLand"
          >
            {{ isNewPost ? '确认创建岗位' : '确认更新能力' }}
          </el-button>
          <el-tag v-else type="success" effect="plain">该候选已处理</el-tag>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script lang="ts">
import type { TrendCandidateDetail as DetailType } from '@/api/post-trend'

/**
 * 「变更」列需要按能力名回查解析阶段的 diff 标注。
 * 调整过能力清单后 diff 以后端重算为准，这里只用于展示**未调整前**的判定，
 * 因此取值为「详情里该能力的 changeType」，找不到就显示「—」。
 */
export default {
  methods: {
    abilityChangeTypeOf(this: { detail: DetailType | null }, abilityName: string): string | null {
      const found = this.detail?.payload.abilities.find(item => item.abilityName === abilityName)
      return found?.changeType ?? null
    },
  },
}
</script>

<style scoped>
.td-drawer__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.td-drawer__title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text-primary, #1f2430);
}

.td-drawer__sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.td-drawer__sub-text {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-drawer__body {
  min-height: 200px;
}

.td-alert {
  margin-bottom: 12px;
}

.td-alert__reason {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
}

.td-alert__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-block {
  padding: 14px 0;
  border-bottom: 1px solid var(--app-border-color, #eef0f4);
}

.td-block:last-child {
  border-bottom: none;
}

.td-block__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.td-block__title {
  font-size: 14px;
  font-weight: 600;
}

.td-block__desc {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-field {
  margin-bottom: 12px;
}

.td-field > label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--app-text-primary, #1f2430);
}

.td-table {
  width: 100%;
}

.td-add {
  margin-top: 8px;
}

.td-tag-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--app-border-color, #eef0f4);
}

.td-tag-row__main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}

.td-tag-row__name {
  font-weight: 600;
}

.td-tag-row__arrow {
  color: var(--app-text-secondary, #6b7280);
}

.td-tag-row__similar {
  color: var(--app-text-primary, #1f2430);
}

.td-tag-row__sim {
  margin-left: 6px;
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-tag-row__empty {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-tag-row__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.td-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary, #6b7280);
}

.td-coverage {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.td-evidence {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: var(--app-radius-md, 10px);
  border: 1px solid var(--app-border-color, #eef0f4);
  background: var(--app-fill-color-light, #f7f8fa);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.td-source-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.td-source {
  font-size: 13px;
}

.td-source__ref {
  margin-left: 8px;
  font-size: 11px;
  color: var(--app-text-secondary, #6b7280);
  word-break: break-all;
}

.td-governance {
  display: flex;
  align-items: center;
  gap: 10px;
}

.td-governance__risk {
  font-size: 12px;
  color: var(--app-text-secondary, #6b7280);
}

.td-drawer__footer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.td-alert--footer {
  margin-bottom: 0;
}

.td-drawer__footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
