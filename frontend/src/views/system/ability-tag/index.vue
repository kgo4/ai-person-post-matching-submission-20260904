<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import TagDirectoryPanel from './components/TagDirectoryPanel.vue'
import TagEditorPanel from './components/TagEditorPanel.vue'
import TagTreeGraph from './components/TagTreeGraph.vue'
import TagRelationGovernanceTab from './components/TagRelationGovernanceTab.vue'
import TagAnalyticsTab from './components/TagAnalyticsTab.vue'
import SkillTaxonomyTab from './components/SkillTaxonomyTab.vue'
import { backfillPostAbilities } from '@/api/tag-governance'
import { useTagDirectory } from '@/composables/useTagDirectory'
import { useTagRelations } from '@/composables/useTagRelations'
import { useTagMergeTasks } from '@/composables/useTagMergeTasks'
import {
  buildCategoryPieOption,
  buildWordCloudOption,
  buildBubbleOption,
  buildGraphOption,
} from '@/utils/tagCharts'

const activeMainTab = ref('health')

const dir = useTagDirectory()
const relations = useTagRelations()
const mergeTasks = useTagMergeTasks()
const backfillLoading = ref(false)

const editorRef = ref<InstanceType<typeof TagEditorPanel>>()

const categoryPieOption = computed(() => buildCategoryPieOption(dir.categoryStats.value))
const wordCloudOption = computed(() => buildWordCloudOption(relations.usageStats.value))
const bubbleOption = computed(() => buildBubbleOption(relations.usageStats.value))
const graphOption = computed(() => buildGraphOption(relations.relations.value))

function onSave() {
  dir.handleSave(editorRef.value?.formRef)
}

function onEdit() {
  dir.handleEdit(editorRef.value?.formRef)
}

function onAdd() {
  dir.handleAdd(editorRef.value?.formRef)
}

async function onExecuteMerge() {
  await mergeTasks.handleExecuteMerge()
  dir.loadTree()
  relations.loadStats()
}

function onCancelEdit() {
  dir.handleCancelEdit()
}

async function runPostAbilityBackfill() {
  backfillLoading.value = true
  try {
    const res = await backfillPostAbilities()
    ElMessage.success(`已提交 ${res.data || 0} 条岗位能力治理任务，后台将自动入库`)
  } finally {
    backfillLoading.value = false
  }
}

let mergeNotificationTimer: number | undefined

onMounted(() => {
  dir.loadTree()
  relations.loadStats()
  relations.loadRelations()
  mergeTasks.loadPendingMerges()
  mergeTasks.loadMergeNotifications(false)
  mergeNotificationTimer = window.setInterval(() => mergeTasks.loadMergeNotifications(), 30_000)
})

onUnmounted(() => {
  if (mergeNotificationTimer !== undefined) window.clearInterval(mergeNotificationTimer)
})
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Capability Taxonomy</div>
        <h1 class="page-hero__title">能力标签治理</h1>
        <p class="page-hero__desc">
          汇总全部岗位能力表中的能力名称，提供标签目录、引用统计、词云与热力分析。人员正式能力表与岗位能力表始终是业务主数据。
        </p>
        <el-button type="primary" :loading="backfillLoading" @click="runPostAbilityBackfill">同步岗位能力</el-button>
      </div>
    </section>

    <nav class="tag-nav">
      <button
        v-for="tab in [{ key: 'health', label: '标签统计' }, { key: 'tags', label: '标签目录' }]"
        :key="tab.key"
        :class="{ 'tag-nav__item--active': activeMainTab === tab.key }"
        class="tag-nav__item"
        @click="activeMainTab = tab.key"
      >{{ tab.label }}</button>
    </nav>

    <div class="tag-content">
      <div v-show="activeMainTab === 'tags'" class="tag-layout">
        <TagDirectoryPanel
          :tree-data="dir.treeData.value"
          :filter-text="dir.filterText.value"
          :active-category="dir.activeCategory.value"
          :loading="dir.loading.value"
          :search-results="dir.searchResults.value"
          :generating-vectors="dir.generatingVectors.value"
          @update:filter-text="dir.filterText.value = $event"
          @update:active-category="dir.activeCategory.value = $event"
          @category-change="dir.handleCategoryChange"
          @search-result-select="dir.handleSearchResultSelect"
          @node-click="dir.handleNodeClick"
          @add="onAdd"
          @generate-vectors="dir.handleGenerateVectors"
        />
        <TagTreeGraph
          :tree-data="dir.treeData.value"
          :loading="dir.loading.value"
          @select="dir.handleSearchResultSelect"
        />
        <TagEditorPanel
          ref="editorRef"
          :selected-node="dir.selectedNode.value"
          :detail-loading="dir.detailLoading.value"
          :is-edit-mode="dir.isEditMode.value"
          :form="dir.form"
          :rules="dir.rules"
          :save-loading="dir.saveLoading.value"
          :total-tag-count="dir.totalTagCount.value"
          :tree-data="dir.treeData.value"
          :active-category-label="dir.activeCategoryLabel.value"
          :category-pie-option="categoryPieOption"
          :generating-vectors="dir.generatingVectors.value"
          @edit="onEdit"
          @cancel-edit="onCancelEdit"
          @save="onSave"
          @delete="dir.handleDelete"
          @add="onAdd"
        />
      </div>

      <div v-show="activeMainTab === 'taxonomy'">
        <SkillTaxonomyTab />
      </div>

      <div v-show="activeMainTab === 'health'">
        <TagAnalyticsTab
          :merge-form="mergeTasks.mergeForm"
          :merging-tags="mergeTasks.mergingTags.value"
          :scheduling-merge="mergeTasks.schedulingMerge.value"
          :pending-merges="mergeTasks.pendingMerges.value"
          :recent-merge-notifications="mergeTasks.recentMergeNotifications.value"
          :last-merge-result="mergeTasks.lastMergeResult.value"
          :merge-result-dialog-visible="mergeTasks.mergeResultDialogVisible.value"
          :category-pie-option="categoryPieOption"
          :word-cloud-option="wordCloudOption"
          :bubble-option="bubbleOption"
          :usage-stats="relations.usageStats.value"
          :computing-stats="relations.computingStats.value"
          @update:merge-result-dialog-visible="mergeTasks.mergeResultDialogVisible.value = $event"
          @update:merge-threshold="mergeTasks.mergeForm.threshold = $event"
          @update:merge-scheduled-time="mergeTasks.mergeForm.scheduledTime = $event"
          @execute-merge="onExecuteMerge"
          @schedule-merge="mergeTasks.handleScheduleMerge"
          @cancel-merge="mergeTasks.handleCancelMerge"
          @compute-stats="relations.handleComputeStats"
        />
        <TagRelationGovernanceTab
          :relations="relations.relations.value"
          :relations-loading="relations.relationsLoading.value"
          :relation-status="relations.relationSearch.value.status"
          :relation-type="relations.relationSearch.value.relationType"
          :discovering-relations="relations.discoveringRelations.value"
          :create-relation-dialog-visible="relations.createRelationDialogVisible.value"
          :create-relation-form="relations.createRelationForm.value"
          :graph-option="graphOption"
          @update:relation-status="relations.relationSearch.value.status = $event"
          @update:relation-type="relations.relationSearch.value.relationType = $event"
          @update:create-relation-dialog-visible="relations.createRelationDialogVisible.value = $event"
          @update:create-relation-form="relations.createRelationForm.value = $event"
          @refresh-relations="relations.loadRelations"
          @discover-relations="relations.handleDiscoverRelations"
          @create-relation="relations.handleCreateRelation"
          @approve-relation="relations.handleApproveRelation"
          @reject-relation="relations.handleRejectRelation"
          @batch-approve-all="relations.handleBatchApproveAll"
          @batch-reject-all="relations.handleBatchRejectAll"
        />
      </div>
    </div>
  </div>
</template>

<style>
@import './styles.css';
</style>
