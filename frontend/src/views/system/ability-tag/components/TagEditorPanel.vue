<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FormInstance } from 'element-plus'
import type { EChartsOption } from 'echarts'
import type { AbilityTagSaveDTO, AbilityTagTreeVO } from '@/api'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'

const props = defineProps<{
  selectedNode: AbilityTagTreeVO | null
  detailLoading: boolean
  isEditMode: boolean
  form: AbilityTagSaveDTO & { id?: number }
  rules: Record<string, any>
  saveLoading: boolean
  totalTagCount: number
  treeData: AbilityTagTreeVO[]
  activeCategoryLabel: string
  categoryPieOption: EChartsOption
  generatingVectors: boolean
}>()

const leafCount = computed(() => countLeaves(props.treeData))
const maxDepth = computed(() => calcMaxDepth(props.treeData, 0))

function countLeaves(nodes: AbilityTagTreeVO[]): number {
  return nodes.reduce((count, node) => count + (node.children?.length ? countLeaves(node.children) : 1), 0)
}

function calcMaxDepth(nodes: AbilityTagTreeVO[], depth: number): number {
  return nodes.reduce((max, node) => Math.max(max, node.children?.length ? calcMaxDepth(node.children, depth + 1) : depth + 1), depth)
}

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'cancelEdit'): void
  (e: 'save'): void
  (e: 'delete'): void
  (e: 'add', parentId?: number): void
  (e: 'generateVectors'): void
}>()

const formRef = ref<FormInstance>()
defineExpose({ formRef })
</script>

<template>
  <!-- Detail / Edit View -->
  <section v-if="selectedNode || isEditMode" class="edt-panel" v-loading="detailLoading">
    <div class="edt-panel__head">
      <div>
        <span class="edt-panel__title">{{ isEditMode ? (form.id ? '编辑标签' : '新增标签') : '标签详情' }}</span>
        <span class="edt-panel__sub">{{ isEditMode ? '填写标签信息后保存' : '查看标签基本信息与结构关系' }}</span>
      </div>
      <div class="edt-panel__actions">
        <template v-if="!isEditMode">
          <el-button type="primary" size="small" @click="emit('edit')">编辑</el-button>
          <el-button type="danger" size="small" @click="emit('delete')">删除</el-button>
        </template>
        <template v-else>
          <el-button type="primary" size="small" :loading="saveLoading" @click="emit('save')">保存</el-button>
          <el-button size="small" @click="emit('cancelEdit')">取消</el-button>
        </template>
      </div>
    </div>

    <div class="edt-panel__body">
      <div class="edt-section">
        <div class="edt-section__title">基本信息</div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="!isEditMode" class="edt-form">
          <el-form-item v-if="form.id" label="标签编码">
            <el-input v-model="form.tagCode" readonly />
          </el-form-item>
          <el-form-item label="标签名称" prop="tagName">
            <el-input v-model="form.tagName" placeholder="请输入标签名称" />
          </el-form-item>
          <el-form-item label="标签分类" prop="tagCategory">
            <el-select v-model="form.tagCategory" placeholder="请选择分类" style="width: 100%">
              <el-option label="技术" value="TECHNICAL" />
              <el-option label="软技能" value="SOFT" />
              <el-option label="业务" value="BUSINESS" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          </el-form-item>
        </el-form>
      </div>

      <div v-if="selectedNode && !isEditMode" class="edt-section">
        <div class="edt-section__title">结构关系</div>
        <div class="edt-relation-grid">
          <div class="edt-relation-item">
            <span class="edt-relation-item__val">{{ selectedNode ? (selectedNode.children?.length ?? 0) : 0 }}</span>
            <span class="edt-relation-item__lbl">子标签数</span>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Overview View -->
  <section v-else-if="false" class="edt-panel edt-overview">
    <div class="edt-panel__head">
      <div>
        <span class="edt-panel__title">{{ activeCategoryLabel }}概览</span>
        <span class="edt-panel__sub">当前分类的标签统计与分布</span>
      </div>
      <div class="edt-panel__actions">
        <el-button size="small" @click="emit('add')">新增根标签</el-button>
      </div>
    </div>

    <div class="edt-panel__body edt-overview__body">
      <!-- Stats -->
      <div class="edt-stats">
        <div class="edt-stat">
          <span class="edt-stat__val">{{ totalTagCount }}</span>
          <span class="edt-stat__lbl">标签总数</span>
        </div>
        <div class="edt-stat">
          <span class="edt-stat__val">{{ treeData.length }}</span>
          <span class="edt-stat__lbl">一级节点</span>
        </div>
        <div class="edt-stat">
          <span class="edt-stat__val">{{ leafCount }}</span>
          <span class="edt-stat__lbl">末级标签</span>
        </div>
        <div class="edt-stat">
          <span class="edt-stat__val">{{ maxDepth }}</span>
          <span class="edt-stat__lbl">最大层级</span>
        </div>
      </div>

      <!-- Pie Chart -->
      <div class="edt-chart">
        <div class="edt-chart__title">分类分布</div>
        <EChartsWrapper v-if="totalTagCount > 0" :option="categoryPieOption" height="340px" />
        <div v-else class="edt-empty">暂无标签数据</div>
      </div>

      <!-- Top-Level Nodes Preview -->
      <div v-if="treeData.length > 0" class="edt-top-nodes">
        <div class="edt-top-nodes__title">一级分类节点</div>
        <div class="edt-top-nodes__list">
          <div v-for="node in treeData" :key="node.id" class="edt-top-node">
            <span class="edt-top-node__name">{{ node.tagName }}</span>
            <span class="edt-top-node__count">{{ node.children?.length || 0 }} 子标签</span>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section v-else class="edt-panel edt-panel--empty">
    <el-empty description="从目录或树图选择标签" :image-size="72" />
  </section>
</template>

<style scoped>
.edt-panel {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.edt-overview {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
}

.edt-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}

.edt-panel__title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.edt-panel__sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--app-text-muted);
}

.edt-panel__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.edt-panel__body {
  padding: 16px 18px;
}

/* Sections */
.edt-section {
  margin-bottom: 20px;
}

.edt-section:last-child {
  margin-bottom: 0;
}

.edt-section__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-strong);
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}

.edt-form {
  max-width: 560px;
}

/* Relation grid */
.edt-relation-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.edt-relation-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.45);
}

.edt-relation-item__val {
  font-size: 20px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.edt-relation-item__lbl {
  font-size: 11px;
  color: var(--app-text-muted);
}

/* Stats */
.edt-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.edt-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 12px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.45);
}

.edt-stat__val {
  font-size: 26px;
  font-weight: 800;
  color: var(--app-text-strong);
  letter-spacing: -0.03em;
  line-height: 1;
}

.edt-stat__lbl {
  font-size: 11px;
  font-weight: 600;
  color: var(--app-text-muted);
}

/* Chart */
.edt-chart {
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  padding: 16px;
}

.edt-chart__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-strong);
  margin-bottom: 8px;
}

.edt-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  font-size: 13px;
  color: var(--app-text-muted);
}

/* Overview body scroll */
.edt-overview__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* Top-level nodes */
.edt-top-nodes {
  margin-top: 14px;
}

.edt-top-nodes__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-strong);
  margin-bottom: 8px;
}

.edt-top-nodes__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.edt-top-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.1);
  transition: background 0.15s;
  cursor: pointer;
}

.edt-top-node:hover {
  background: rgba(59, 130, 246, 0.05);
}

.edt-top-node__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
}

.edt-top-node__count {
  font-size: 11px;
  color: var(--app-text-muted);
}
</style>
