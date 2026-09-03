<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, MagicStick, Setting, Briefcase, Star, List, PieChart } from '@element-plus/icons-vue'
import {
  listEnabledPosts,
  getPostModel,
  batchModelConfig,
  getTagTree,
  pageTemplates,
  applyTemplateToPost,
} from '@/api'
import type {
  PostPost,
  PostAbilityModelVO,
  AbilityTagTreeVO,
  PostAbilityModelConfigDTO,
  PostModelTemplate,
} from '@/api'
import AbilityForceGraph from '@/components/graph/AbilityForceGraph.vue'
import type { ForceNode, ForceEdge } from '@/components/graph/AbilityForceGraph.vue'
import JdImportDialog from './jd-import-dialog.vue'
import { isLegacyRelativeWeights, normalizeLegacyRelativeWeights } from './weight-normalization'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const showJdDialog = ref(false)
const activeTab = ref<'config' | 'graph'>('config')
// 岗位选择
const postList = ref<PostPost[]>([])
const initialPostId = route.query.postId ? Number(route.query.postId) : undefined
const postId = ref<number | undefined>(initialPostId)
// 模板选择
const templateList = ref<PostModelTemplate[]>([])
const selectedTemplateId = ref<number | undefined>(undefined)
const flatTags = ref<Map<number, string>>(new Map())
const allTagOptions = ref<{ id: number; name: string }[]>([])
// 模型数据
const model = ref<PostAbilityModelVO | null>(null)
// 编辑中的配置项
interface RowItem {
  modelId?: number
  tagId: number | null
  tagName: string
  abilityName: string
  techStack: string
  minRequiredLevel: number
  weight: number
  isRequired: number
  isCore: number
}
const rows = ref<RowItem[]>([])
const editing = ref(false)
const legacyRelativeWeightsDetected = ref(false)

onMounted(async () => {
  try { const r = await listEnabledPosts(); postList.value = r.data } catch { }
  try { await reloadTagOptions() } catch { }
  try { const r = await pageTemplates({ current: 1, size: 100 }); templateList.value = r.data.records } catch { }
  // 从岗位列表跳转时自动加载能力模型
  if (postId.value) { await onPostChange() }
})

async function reloadTagOptions() {
  const r = await getTagTree()
  flatTags.value = flatten(r.data)
  allTagOptions.value = collectAll(r.data)
}

function flatten(tree: AbilityTagTreeVO[], map = new Map<number, string>()): Map<number, string> {
  for (const t of tree) { map.set(t.id, t.tagName); if (t.children) flatten(t.children, map) }
  return map
}
function collectAll(tree: AbilityTagTreeVO[], list: { id: number; name: string }[] = []): { id: number; name: string }[] {
  for (const t of tree) { list.push({ id: t.id, name: t.tagName }); if (t.children) collectAll(t.children, list) }
  return list
}

async function onPostChange() {
  if (!postId.value) { model.value = null; rows.value = []; return }
  loading.value = true
  try {
    const r = await getPostModel(postId.value)
    model.value = r.data
    const requirements = r.data?.abilityRequirements || []
    const originalWeights = requirements.map(a => Number(a.weight))
    legacyRelativeWeightsDetected.value = isLegacyRelativeWeights(originalWeights)
    const weights = normalizeLegacyRelativeWeights(originalWeights)
    rows.value = requirements.map((a, index) => ({
      modelId: a.modelId, tagId: a.tagId, tagName: a.tagName, abilityName: a.abilityName || '', techStack: a.techStack || '', minRequiredLevel: a.minRequiredLevel,
      weight: weights[index], isRequired: a.isRequired, isCore: a.isCore,
    }))
    if (legacyRelativeWeightsDetected.value) {
      ElMessage.warning('已按原有比例将历史相对权重换算为百分比；保存后将规范化岗位模型。')
    }
  } catch { model.value = null; rows.value = []; legacyRelativeWeightsDetected.value = false }
  finally { loading.value = false }
}

async function handleJdImportSuccess() {
  try { await reloadTagOptions() } catch { }
  await onPostChange()
}

// 力导向图数据
const selectedPostName = computed(() => {
  return postList.value.find(p => p.id === postId.value)?.postName || ''
})

const totalWeight = computed(() => rows.value.reduce((sum, item) => sum + item.weight, 0))
const coreAbilityCount = computed(() => rows.value.filter(item => item.isCore).length)
const requiredAbilityCount = computed(() => rows.value.filter(item => item.isRequired).length)

const forceNodes = computed<ForceNode[]>(() => {
  if (!selectedPostName.value || rows.value.length === 0) return []

  const nodes: ForceNode[] = []

  // 添加岗位节点
  nodes.push({
    id: 'post',
    label: selectedPostName.value,
    type: 'post',
  })

  // 按类型分组
  const coreItems = rows.value.filter(r => r.isCore)
  const requiredItems = rows.value.filter(r => !r.isCore && r.isRequired)
  const normalItems = rows.value.filter(r => !r.isCore && !r.isRequired)

  // 添加能力大类节点
  if (coreItems.length > 0) {
    nodes.push({ id: 'cat_core', label: '核心能力', type: 'abilityCategory' })
  }
  if (requiredItems.length > 0) {
    nodes.push({ id: 'cat_required', label: '必填能力', type: 'abilityCategory' })
  }
  if (normalItems.length > 0) {
    nodes.push({ id: 'cat_normal', label: '普通能力', type: 'abilityCategory' })
  }

  // 添加具体能力节点
  rows.value.forEach((r, index) => {
    const catId = r.isCore ? 'cat_core' : r.isRequired ? 'cat_required' : 'cat_normal'
    nodes.push({
      id: abilityNodeId(r, index),
      label: abilityDisplayName(r),
      type: 'postAbility',
      level: r.minRequiredLevel,
      weight: r.weight,
      category: r.isCore ? '核心' : r.isRequired ? '必填' : '普通',
    })
  })

  return nodes
})

const forceEdges = computed<ForceEdge[]>(() => {
  if (!selectedPostName.value || rows.value.length === 0) return []

  const edges: ForceEdge[] = []

  // 岗位 -> 能力大类
  const coreItems = rows.value.filter(r => r.isCore)
  const requiredItems = rows.value.filter(r => !r.isCore && r.isRequired)
  const normalItems = rows.value.filter(r => !r.isCore && !r.isRequired)

  if (coreItems.length > 0) {
    edges.push({ source: 'post', target: 'cat_core', type: 'employee-post', style: 'solid', label: '核心' })
  }
  if (requiredItems.length > 0) {
    edges.push({ source: 'post', target: 'cat_required', type: 'employee-post', style: 'solid', label: '必填' })
  }
  if (normalItems.length > 0) {
    edges.push({ source: 'post', target: 'cat_normal', type: 'employee-post', style: 'solid', label: '普通' })
  }

  // 能力大类 -> 具体能力
  rows.value.forEach((r, index) => {
    const catId = r.isCore ? 'cat_core' : r.isRequired ? 'cat_required' : 'cat_normal'
    edges.push({
      source: catId,
      target: abilityNodeId(r, index),
      type: 'post-postAbility',
      style: 'solid',
      label: `${r.weight}%`,
    })
  })

  return edges
})

function abilityDisplayName(row: RowItem) {
  return row.abilityName?.trim() || row.tagName?.trim() || ''
}
function abilityNodeId(row: RowItem, index: number) {
  return row.modelId != null ? `ability_model_${row.modelId}` : `ability_draft_${index}`
}
function addRow() { rows.value.push({ tagId: null, tagName: '', abilityName: '', techStack: '', minRequiredLevel: 1, weight: 20, isRequired: 0, isCore: 0 }); editing.value = true }
function removeRow(i: number) { rows.value.splice(i, 1) }
function onTagSelect(i: number, tagId: number | null) {
  rows.value[i].tagId = tagId
  rows.value[i].tagName = tagId != null ? flatTags.value.get(tagId) || '' : ''
  if (tagId != null && !rows.value[i].abilityName.trim()) {
    rows.value[i].abilityName = rows.value[i].tagName
  }
}

async function handleSave() {
  if (!postId.value) { ElMessage.error('请先选择岗位'); return }
  if (rows.value.some(row => !row.abilityName.trim())) {
    ElMessage.error('每个能力项必须填写能力名称')
    return
  }
  if (totalWeight.value < 95 || totalWeight.value > 105) {
    ElMessage.error(`权重总和为 ${totalWeight.value.toFixed(2)}%，请调整至 95% - 105% 后保存`)
    return
  }
  const dto: PostAbilityModelConfigDTO[] = rows.value.map(r => ({
    postId: postId.value!, tagId: flatTags.value.has(r.tagId as number) ? r.tagId : null, abilityName: r.abilityName.trim(), techStack: r.techStack?.trim() || '', minRequiredLevel: r.minRequiredLevel,
    weight: r.weight, isRequired: r.isRequired, isCore: r.isCore,
  }))
  loading.value = true
  try {
    await batchModelConfig(dto)
    ElMessage.success('保存成功')
    editing.value = false
    await onPostChange()
  }
  catch { }
  finally { loading.value = false }
}

// 应用模板到岗位
async function handleApplyTemplate() {
  if (!postId.value) {
    ElMessage.error('请先选择岗位')
    return
  }
  if (!selectedTemplateId.value) {
    ElMessage.error('请先选择模板')
    return
  }

  try {
    await ElMessageBox.confirm(
      '应用模板将覆盖当前岗位的能力模型配置，是否继续？',
      '确认应用模板',
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  loading.value = true
  try {
    await applyTemplateToPost(selectedTemplateId.value, postId.value)
    ElMessage.success('模板应用成功')
    // 重新加载岗位能力模型
    await onPostChange()
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <!-- Header -->
    <section class="page-hero motion-scan">
      <div class="page-hero__left">
        <div class="page-hero__eyebrow">Post Capability Model</div>
        <h1 class="page-hero__title">岗位能力模型配置</h1>
        <p class="page-hero__desc">配置岗位能力标签、等级要求和权重分布；支持模板套用与 JD AI 智能分析。</p>
        <div class="page-hero__meta">
          <span class="hero-chip"><Briefcase /></span>
          <span class="hero-chip">AI 分析</span>
          <span class="hero-chip">模板引擎</span>
        </div>
      </div>
      <div class="page-hero__select">
        <span class="page-hero__select-label">目标岗位</span>
        <el-select
          v-model="postId"
          placeholder="选择岗位开始配置"
          filterable
          clearable
          size="large"
          class="!w-[280px]"
          @change="onPostChange"
        >
          <el-option v-for="p in postList" :key="p.id" :label="p.postName" :value="p.id" />
        </el-select>
      </div>
    </section>

    <!-- Stats Bar -->
    <section v-if="postId" class="model-stat-grid">
      <div class="model-stat-card">
        <div class="model-stat-card__icon model-stat-card__icon--blue">
          <el-icon :size="20"><List /></el-icon>
        </div>
        <div class="model-stat-card__body">
          <div class="model-stat-card__label">能力项</div>
          <div class="model-stat-card__value">{{ rows.length }}</div>
        </div>
      </div>
      <div class="model-stat-card">
        <div class="model-stat-card__icon model-stat-card__icon--red">
          <el-icon :size="20"><Star /></el-icon>
        </div>
        <div class="model-stat-card__body">
          <div class="model-stat-card__label">核心能力</div>
          <div class="model-stat-card__value">{{ coreAbilityCount }}</div>
        </div>
      </div>
      <div class="model-stat-card">
        <div class="model-stat-card__icon model-stat-card__icon--green">
          <el-icon :size="20"><Setting /></el-icon>
        </div>
        <div class="model-stat-card__body">
          <div class="model-stat-card__label">必填能力</div>
          <div class="model-stat-card__value">{{ requiredAbilityCount }}</div>
        </div>
      </div>
      <div class="model-stat-card">
        <div class="model-stat-card__icon" :class="totalWeight === 100 ? 'model-stat-card__icon--teal' : 'model-stat-card__icon--amber'">
          <el-icon :size="20"><PieChart /></el-icon>
        </div>
        <div class="model-stat-card__body">
          <div class="model-stat-card__label">总权重</div>
          <div class="model-stat-card__value" :class="{ 'model-stat-card__value--warn': totalWeight !== 100 }">{{ totalWeight }}%</div>
        </div>
      </div>
    </section>

    <!-- Toolbar -->
    <section v-if="postId" class="model-toolbar">
      <div class="model-toolbar__template">
        <span class="model-toolbar__label">模板导入</span>
        <div class="model-toolbar__inline">
          <el-select v-model="selectedTemplateId" placeholder="选择模板" filterable clearable size="default" class="!w-[200px]">
            <el-option v-for="t in templateList" :key="t.id" :label="t.templateName" :value="t.id" />
          </el-select>
          <el-button type="primary" :disabled="!selectedTemplateId" @click="handleApplyTemplate" plain>
            应用模板
          </el-button>
        </div>
      </div>
      <div class="model-toolbar__actions">
        <el-button :disabled="!postId" @click="showJdDialog = true">
          <el-icon><MagicStick /></el-icon> JD 智能分析
        </el-button>
        <el-button type="primary" :icon="Plus" @click="addRow" :disabled="!postId">
          添加能力
        </el-button>
        <el-button v-if="rows.length > 0" type="success" :loading="loading" @click="handleSave">
          保存配置
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="legacyRelativeWeightsDetected"
      class="model-legacy-weight-alert"
      type="warning"
      :closable="false"
      title="已识别历史相对权重"
      description="当前数值已按原有比例换算为百分比展示。点击保存配置后，将以规范化的百分比权重写入岗位模型。"
      show-icon
    />

    <!-- Empty State -->
    <section v-if="!postId" class="cfg-empty">
      <div class="cfg-empty__icon">
        <el-icon :size="48"><Setting /></el-icon>
      </div>
      <h3>选择岗位开始配置</h3>
      <p>在上方下拉框中选取目标岗位，即可配置其能力模型、等级要求和权重分布。</p>
      <el-button class="cfg-empty__tmpl" @click="router.push('/post/template')">
        <el-icon><Setting /></el-icon> 管理能力模板
      </el-button>
    </section>

    <!-- Tabs Content -->
    <section v-if="postId" class="model-content">
      <div class="model-tabs">
        <button
          class="model-tab"
          :class="{ 'model-tab--active': activeTab === 'config' }"
          @click="activeTab = 'config'"
        >
          <el-icon :size="15"><Setting /></el-icon>
          能力配置
        </button>
        <button
          class="model-tab"
          :class="{ 'model-tab--active': activeTab === 'graph' }"
          @click="activeTab = 'graph'"
          :disabled="rows.length === 0"
        >
          <el-icon :size="15"><PieChart /></el-icon>
          能力图谱
          <span v-if="forceNodes.length > 0" class="model-tab__badge">{{ forceNodes.length }}</span>
        </button>
      </div>

      <!-- Config Table -->
      <div v-show="activeTab === 'config'" class="model-panel">
        <el-table :data="rows" size="default" style="width: 100%" :header-cell-style="{ fontWeight: 600, color: '#475569', fontSize: '12px' }">
          <el-table-column label="岗位能力" min-width="260">
            <template #default="{ row, $index }">
              <div class="model-tag-select-cell">
                <el-input v-model="row.abilityName" :placeholder="row.tagName || '岗位能力名称'" maxlength="255" />
                <div v-if="row.tagId && row.tagName" class="model-tag-select-cell__badge">
                  <span class="model-tag-select-cell__name">已关联标签：{{ row.tagName }}</span>
                </div>
                <el-select
                  v-model="row.tagId"
                  placeholder="可选：关联系统能力标签"
                  filterable
                  clearable
                  @change="(v:any) => onTagSelect($index, v)"
                  style="width:100%"
                  size="default"
                >
                  <el-option v-for="tag in allTagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
                </el-select>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="技术栈" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.techStack" placeholder="如 Spring / MySQL / Redis" maxlength="64" />
            </template>
          </el-table-column>
          <el-table-column label="要求等级" width="130" align="center">
            <template #default="{ row }">
              <el-select v-model="row.minRequiredLevel" size="default">
                <el-option :value="1" label="L1 入门" />
                <el-option :value="2" label="L2 熟悉" />
                <el-option :value="3" label="L3 掌握" />
                <el-option :value="4" label="L4 精通" />
                <el-option :value="5" label="L5 专家" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="权重" width="140" align="center">
            <template #default="{ row }">
              <div class="model-weight-cell">
                <el-input-number v-model="row.weight" :min="0" :max="100" :step="5" controls-position="right" size="default" />
                <div class="model-weight-cell__bar">
                  <div
                    class="model-weight-cell__fill"
                    :style="{ width: row.weight + '%' }"
                  ></div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="必填" width="80" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.isRequired" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="核心" width="80" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.isCore" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="" width="60" align="center">
            <template #default="{ $index }">
              <el-button type="danger" :icon="Delete" text @click="removeRow($index)" />
            </template>
          </el-table-column>
        </el-table>

        <div v-if="rows.length === 0" class="cfg-table-empty">
          尚未添加能力要求，点击上方「添加能力」或「JD 智能分析」开始配置
        </div>
      </div>

      <!-- Graph Panel -->
      <div v-show="activeTab === 'graph'" class="model-panel model-graph">
        <AbilityForceGraph :nodes="forceNodes" :edges="forceEdges" :width="960" :height="580" />
      </div>
    </section>

    <!-- JD Dialog -->
    <JdImportDialog
      v-if="postId"
      v-model:visible="showJdDialog"
      :post-id="postId"
      :post-name="selectedPostName"
      @success="handleJdImportSuccess"
    />
  </div>
</template>

<style scoped>
/* ====== 岗位能力模型配置 ====== */

/* ---- Header (复用 page-hero 体系) ---- */

.page-hero__left {
  flex: 1;
  min-width: 0;
}

.page-hero__select {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.page-hero__select-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

/* ---- 统计卡片网格 ---- */

.model-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.model-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(8px);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.model-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}

.model-stat-card__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  color: #fff;
}

.model-stat-card__icon--blue   { background: linear-gradient(135deg, #2563eb, #3b82f6); }
.model-stat-card__icon--red    { background: linear-gradient(135deg, #dc2626, #f87171); }
.model-stat-card__icon--green  { background: linear-gradient(135deg, #059669, #10b981); }
.model-stat-card__icon--teal   { background: linear-gradient(135deg, #0d9488, #14b8a6); }
.model-stat-card__icon--amber  { background: linear-gradient(135deg, #d97706, #f59e0b); }

.model-stat-card__body { min-width: 0; }

.model-stat-card__label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.03em;
  margin-bottom: 3px;
}

.model-stat-card__value {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.03em;
  line-height: 1;
}

.model-stat-card__value--warn {
  color: #d97706;
}

/* ---- Toolbar ---- */

.model-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
}

.model-toolbar__template {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.model-toolbar__label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

.model-toolbar__inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ---- Empty State ---- */

.cfg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  border: 1px solid rgba(148, 163, 184, 0.13);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.5);
}

.cfg-empty__icon { color: #94a3b8; margin-bottom: 16px; opacity: 0.5; }

.cfg-empty h3 { margin: 0 0 8px; font-size: 17px; font-weight: 700; color: #475569; }
.cfg-empty p { margin: 0; font-size: 13px; color: #94a3b8; text-align: center; max-width: 400px; line-height: 1.6; }
.cfg-empty__tmpl { margin-top: 20px; }

/* ---- Tabs ---- */

.model-content {
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 12px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.model-tabs {
  display: flex;
  gap: 4px;
  padding: 6px;
  margin: 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
  background: rgba(248, 250, 252, 0.4);
}

.model-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.model-tab:hover {
  color: #475569;
  background: rgba(37, 99, 235, 0.04);
}

.model-tab--active {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}

.model-tab:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.model-tab__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(37, 99, 235, 0.15);
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

/* ---- Content Panel ---- */

.model-panel {
  padding: 18px 20px;
  min-height: 300px;
}

.model-graph {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 520px;
  padding: 12px;
}

.cfg-table-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #94a3b8;
  font-size: 13px;
}

/* ---- 能力标签选择单元格 ---- */

.model-tag-select-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.model-tag-select-cell__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.06);
  border: 1px solid rgba(37, 99, 235, 0.12);
  width: fit-content;
}

.model-tag-select-cell__name {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
}

/* ---- 权重单元格 ---- */

.model-weight-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-weight-cell__bar {
  flex: 1;
  min-width: 40px;
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  overflow: hidden;
}

.model-weight-cell__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #2563eb, #06b6d4);
  transition: width 0.3s ease;
}

/* ---- Responsive ---- */

@media (max-width: 1024px) {
  .model-stat-grid { grid-template-columns: repeat(2, 1fr); }
  .page-hero { flex-direction: column; }
  .model-toolbar { flex-direction: column; align-items: stretch; }
  .model-toolbar__actions { flex-wrap: wrap; }
}

@media (max-width: 720px) {
  .model-stat-grid { grid-template-columns: 1fr 1fr; }
}
</style>

