<script setup lang="ts">
import { computed } from 'vue'
import type { AbilityTagTreeVO } from '@/api'
import type { TagSearchResult } from '@/utils/tagSearch'
import { categoryTabs } from '@/composables/useTagDirectory'
import { countDirectChildren } from '../tag-directory-panel'

const props = defineProps<{
  treeData: AbilityTagTreeVO[]
  filterText: string
  activeCategory: string
  loading: boolean
  searchResults: TagSearchResult[]
  generatingVectors: boolean
}>()

const emit = defineEmits<{
  (e: 'update:filterText', val: string): void
  (e: 'update:activeCategory', val: string): void
  (e: 'categoryChange', val: string): void
  (e: 'searchResultSelect', tagId: number): void
  (e: 'nodeClick', data: AbilityTagTreeVO): void
  (e: 'add'): void
  (e: 'generateVectors'): void
}>()

function countLevel(nodes: AbilityTagTreeVO[], level: number): number {
  return nodes.reduce((total, node) => total + (node.tagLevel === level ? 1 : 0) + countLevel(node.children || [], level), 0)
}

const totalCount = computed(() => countLevel(props.treeData, 0) + countLevel(props.treeData, 1) + countLevel(props.treeData, 2))
const domainCount = computed(() => countLevel(props.treeData, 1))
const skillCount = computed(() => countLevel(props.treeData, 2))
const expandedKeys = computed(() => props.treeData.flatMap(node => [node.id, ...(node.children || []).map(child => child.id)]))

function getNodeMeta(data: AbilityTagTreeVO): string {
  const childCount = countDirectChildren(data as any)
  if (childCount > 0) return `${childCount} 个子标签`
  return '末级标签'
}

function levelLabel(level: number): string {
  if (level === 0) return '根分类'
  if (level === 1) return '能力'
  if (level === 2) return '技能'
  return ''
}
</script>

<template>
  <section class="dir-panel">
    <div class="dir-panel__head">
      <div class="dir-panel__title-row">
        <span class="dir-panel__title">标签目录</span>
        <span class="dir-panel__sub">按分类浏览标签体系 · 共 {{ totalCount }} 个标签</span>
      </div>
      <div class="dir-panel__actions">
        <el-button size="small" text :loading="generatingVectors" @click="emit('generateVectors')">生成向量</el-button>
        <el-button type="primary" size="small" @click="emit('add')">新增最高节点</el-button>
      </div>
    </div>

    <div class="dir-panel__stats" aria-label="标签层级统计">
      <span><strong>{{ totalCount }}</strong> 标签</span>
      <span><strong>{{ domainCount }}</strong> 能力域</span>
      <span><strong>{{ skillCount }}</strong> 技能标签</span>
    </div>

    <!-- Category pills -->
    <div class="dir-panel__cats">
      <button
        v-for="tab in categoryTabs"
        :key="tab.value"
        class="dir-cat"
        :class="{ 'dir-cat--active': activeCategory === tab.value }"
        @click="emit('update:activeCategory', tab.value); emit('categoryChange', tab.value)"
      >{{ tab.label }}</button>
    </div>

    <!-- Search -->
    <div class="dir-panel__search">
      <el-input
        :model-value="filterText"
        placeholder="搜索标签名称..."
        clearable
        size="small"
        @update:model-value="emit('update:filterText', $event)"
      />
    </div>

    <!-- Search results -->
    <div v-if="filterText.trim()" class="dir-panel__list">
      <button
        v-for="item in searchResults"
        :key="item.id"
        class="dir-result"
        type="button"
        @click="emit('searchResultSelect', item.id)"
      >
        <span class="dir-result__name">{{ item.tagName }}</span>
        <span class="dir-result__path">{{ item.path }}</span>
      </button>
      <div v-if="searchResults.length === 0" class="dir-empty">
        无匹配标签，试试其他关键词
      </div>
    </div>

    <!-- Tree -->
    <div v-else class="dir-panel__tree" v-loading="loading">
      <el-tree
        :data="treeData"
        node-key="id"
        :default-expanded-keys="expandedKeys"
        :props="{ children: 'children', label: 'tagName' }"
        highlight-current
        @node-click="(data: AbilityTagTreeVO) => emit('nodeClick', data)"
      >
        <template #default="{ node, data }">
          <div class="dir-tree-node">
            <div class="dir-tree-node__info">
              <div class="dir-tree-node__name-row">
                <span class="dir-tree-node__name">{{ node.label }}</span>
                <span
                  v-if="levelLabel(data.tagLevel)"
                  class="dir-tree-node__level"
                  :class="`dir-tree-node__level--l${data.tagLevel}`"
                >{{ levelLabel(data.tagLevel) }}</span>
              </div>
              <span class="dir-tree-node__meta">{{ getNodeMeta(data) }}</span>
            </div>
          </div>
        </template>
      </el-tree>
    </div>
  </section>
</template>

<style scoped>
.dir-panel {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dir-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px 10px;
}

.dir-panel__title-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dir-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.dir-panel__sub {
  font-size: 11px;
  color: var(--app-text-muted);
}

.dir-panel__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.dir-panel__stats {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 16px 10px;
  color: var(--app-text-muted);
  font-size: 11px;
}

.dir-panel__stats span {
  padding: 3px 8px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 6px;
  background: rgba(248, 250, 252, 0.72);
}

.dir-panel__stats strong {
  color: var(--app-text-strong);
  font-weight: 700;
}

/* Category pills */
.dir-panel__cats {
  display: flex;
  gap: 4px;
  padding: 0 16px 8px;
}

.dir-cat {
  padding: 4px 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.5);
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.dir-cat:hover {
  border-color: rgba(59, 130, 246, 0.3);
  color: var(--app-text-secondary);
}

.dir-cat--active {
  background: rgba(59, 130, 246, 0.08);
  border-color: rgba(59, 130, 246, 0.25);
  color: var(--app-primary);
}

/* Search */
.dir-panel__search {
  padding: 0 16px 8px;
}

/* List & Tree */
.dir-panel__list,
.dir-panel__tree {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 0 8px 12px;
}

.dir-result {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.dir-result:hover {
  background: rgba(59, 130, 246, 0.05);
}

.dir-result__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
}

.dir-result__path {
  font-size: 11px;
  color: var(--app-text-muted);
}

/* Tree node */
.dir-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 2px 4px 2px 0;
}

.dir-tree-node__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dir-tree-node__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dir-tree-node__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.dir-tree-node__level {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.4;
}

.dir-tree-node__level--l1 {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}

.dir-tree-node__level--l2 {
  color: #059669;
  background: rgba(5, 150, 105, 0.1);
}

.dir-tree-node__meta {
  font-size: 11px;
  color: var(--app-text-muted);
  margin-top: 1px;
}

.dir-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--app-text-muted);
}

/* Tree deep styles */
.dir-panel :deep(.el-tree-node__content) {
  height: auto;
  padding: 4px 8px;
  border-radius: 8px;
}

.dir-panel :deep(.el-tree-node__content:hover) {
  background: rgba(59, 130, 246, 0.04);
}

.dir-panel :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(59, 130, 246, 0.08);
}
</style>
