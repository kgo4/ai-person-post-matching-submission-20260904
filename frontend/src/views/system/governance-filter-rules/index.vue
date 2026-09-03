<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGovernanceFilterRules, saveGovernanceFilterRule, deleteGovernanceFilterRule, generateGovernanceFilterSuggestions, reviewGovernanceFilterSuggestion, getGovernanceFilterSamples } from '@/api/governance-filter-rules'
import type { GovernanceFilterRule } from '@/api/governance-filter-rules'

const activeScope = ref<'POST_JD' | 'PERSON_ABILITY'>('POST_JD')
const loading = ref(false)
const rows = ref<GovernanceFilterRule[]>([])
const suggestions = ref<GovernanceFilterRule[]>([])
const sampleCount = ref(0)
const generating = ref(false)
const dialogVisible = ref(false)
const editing = ref<GovernanceFilterRule | null>(null)
const form = ref<GovernanceFilterRule>(newRule())

function newRule(): GovernanceFilterRule {
  return { scope: activeScope.value, ruleType: 'KEYWORD', ruleName: '', patternValue: '', weight: 15, blockEnabled: 1, enabled: 1 }
}

const scopeLabel = computed(() => activeScope.value === 'POST_JD' ? '岗位 JD' : '人员能力证据')

async function load() {
  loading.value = true
  try {
    rows.value = (await listGovernanceFilterRules(activeScope.value, 'APPROVED')).data || []
    suggestions.value = (await listGovernanceFilterRules(activeScope.value, 'PENDING')).data || []
  }
  finally { loading.value = false }
}

async function generateSuggestions() {
  generating.value = true
  try {
    const sampleResponse = await getGovernanceFilterSamples(activeScope.value, 30)
    const samples = sampleResponse.data?.samples || []
    sampleCount.value = samples.length
    if (!samples.length) return ElMessage.warning(activeScope.value === 'POST_JD' ? '暂无被过滤的岗位 JD 样本' : '暂无被过滤的人员能力样本')
    await generateGovernanceFilterSuggestions(activeScope.value, samples)
    ElMessage.success(`已提交 ${samples.length} 条样本，AI 将异步生成待审核建议`)
    await load()
  } finally { generating.value = false }
}

async function reviewSuggestion(row: GovernanceFilterRule, approve: boolean) {
  await reviewGovernanceFilterSuggestion(row.id!, approve)
  ElMessage.success(approve ? '建议已采纳并生效' : '建议已拒绝')
  await load()
}

function openCreate() { editing.value = null; form.value = newRule(); dialogVisible.value = true }
function openEdit(row: GovernanceFilterRule) { editing.value = row; form.value = { ...row }; dialogVisible.value = true }

async function save() {
  if (!form.value.ruleName.trim() || !form.value.patternValue.trim()) return ElMessage.warning('请填写规则名称和匹配内容')
  await saveGovernanceFilterRule({ ...form.value, scope: activeScope.value })
  ElMessage.success('规则已保存并生效')
  dialogVisible.value = false
  await load()
}

async function remove(row: GovernanceFilterRule) {
  if (row.source === 'SYSTEM') return ElMessage.warning('系统内置规则不能删除，请停用')
  await ElMessageBox.confirm('确定删除这条自定义规则吗？', '删除确认', { type: 'warning' })
  await deleteGovernanceFilterRule(row.id!)
  await load()
}

async function toggle(row: GovernanceFilterRule) {
  await saveGovernanceFilterRule({ ...row, enabled: row.enabled === 1 ? 0 : 1 })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page-shell governance-rules-page">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Governance Rules</div>
        <h1 class="page-hero__title">数据治理规则</h1>
        <p class="page-hero__desc">岗位 JD 与人员能力证据使用独立规则。AI 只能提出建议，人工确认后才会生效。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增规则</el-button>
    </section>

    <el-tabs v-model="activeScope" @tab-change="load">
      <el-tab-pane label="岗位 JD 过滤规则" name="POST_JD" />
      <el-tab-pane label="人员能力证据规则" name="PERSON_ABILITY" />
    </el-tabs>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="ruleName" label="规则名称" min-width="160" />
      <el-table-column prop="ruleType" label="类型" width="140" />
      <el-table-column prop="patternValue" label="匹配内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="weight" label="贡献分" width="100" />
      <el-table-column prop="source" label="来源" width="110">
        <template #default="{ row }">{{ row.source === 'SYSTEM' ? '系统内置' : row.source === 'AI_SUGGESTION' ? 'AI建议' : '人工' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-switch :model-value="row.enabled === 1" @change="toggle(row)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.source === 'SYSTEM'" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <section class="suggestion-panel">
      <div class="section-title">AI 规则建议</div>
      <p class="section-desc">推荐依据：近期被过滤样本中的重复关键词、长度异常、缺少职责段落和重复宣传话术。AI 只生成候选规则，不会自动启用；采纳前状态为待审核。</p>
      <div class="sample-status">可用样本：{{ sampleCount }} 条（生成时自动刷新）</div>
      <el-button type="primary" plain style="margin-top: 10px" :loading="generating" @click="generateSuggestions">自动生成 AI 建议</el-button>
      <el-table v-if="suggestions.length" :data="suggestions" border style="margin-top: 16px">
        <el-table-column prop="ruleName" label="建议规则" width="160" />
        <el-table-column prop="ruleType" label="类型" width="120" />
        <el-table-column prop="patternValue" label="匹配内容" min-width="180" />
        <el-table-column prop="weight" label="贡献分" width="90" />
        <el-table-column prop="aiRationale" label="建议理由" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default>待审核</template></el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="reviewSuggestion(row, true)">采纳</el-button>
            <el-button link type="danger" @click="reviewSuggestion(row, false)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑规则' : `新增${scopeLabel}规则`" width="520px">
      <el-form label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="form.ruleType" style="width: 100%">
            <el-option label="关键词包含" value="KEYWORD" />
            <el-option label="正则表达式" value="REGEX" />
            <el-option v-if="activeScope === 'POST_JD'" label="正文长度" value="LENGTH" />
            <el-option v-if="activeScope === 'POST_JD'" label="缺少职责段落" value="SECTION_MISSING" />
            <el-option v-if="activeScope === 'PERSON_ABILITY'" label="整词匹配" value="EXACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配内容"><el-input v-model="form.patternValue" placeholder="关键词、正则或长度数值" /></el-form-item>
        <el-form-item v-if="activeScope === 'POST_JD'" label="贡献分"><el-input-number v-model="form.weight" :min="0" :max="100" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存并生效</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.suggestion-panel { margin-top: 24px; padding: 18px; border: 1px solid var(--el-border-color); background: var(--el-bg-color); }
.section-title { font-weight: 600; color: var(--el-text-color-primary); }
.section-desc { color: var(--el-text-color-secondary); font-size: 13px; margin: 6px 0 12px; }
</style>
