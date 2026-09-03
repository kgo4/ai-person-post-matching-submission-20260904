<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import type { AbilityTagRelation } from '@/api/tag-governance'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'

const props = defineProps<{
  relations: AbilityTagRelation[]
  relationsLoading: boolean
  relationStatus: string
  relationType: string
  discoveringRelations: boolean
  createRelationDialogVisible: boolean
  createRelationForm: { sourceTagId: string; targetTagId: string; relationType: string; remark: string }
  graphOption: EChartsOption
}>()

const emit = defineEmits<{
  (e: 'update:relationStatus', val: string): void
  (e: 'update:relationType', val: string): void
  (e: 'update:createRelationDialogVisible', val: boolean): void
  (e: 'update:createRelationForm', val: { sourceTagId: string; targetTagId: string; relationType: string; remark: string }): void
  (e: 'refreshRelations'): void
  (e: 'discoverRelations', threshold: number): void
  (e: 'createRelation'): void
  (e: 'approveRelation', id: number): void
  (e: 'rejectRelation', id: number): void
  (e: 'batchApproveAll'): void
  (e: 'batchRejectAll'): void
}>()

const pendingRelations = computed(() => props.relations.filter(r => r.status === 'PENDING'))
</script>

<template>
  <section class="glass-card motion-rise">
    <div class="toolbar-panel">
      <div>
        <div class="section-title">标签关系网络</div>
        <div class="section-desc">蓝色=语义等价 · 绿色=语义相近 · 虚线=待审核</div>
      </div>
      <div class="toolbar-group">
        <el-select :model-value="relationType" placeholder="关系类型" clearable class="!w-28" @update:model-value="emit('update:relationType', $event); emit('refreshRelations')">
          <el-option label="语义等价" value="SAME_AS" />
          <el-option label="语义相近" value="SIMILAR" />
        </el-select>
        <el-button @click="emit('refreshRelations')">刷新</el-button>
        <el-dropdown @command="(v: any) => emit('discoverRelations', v)" :disabled="discoveringRelations">
          <el-button type="primary" :loading="discoveringRelations">
            自动发现 <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="0.5">宽松 (≥50%)</el-dropdown-item>
              <el-dropdown-item :command="0.6">标准 (≥60%)</el-dropdown-item>
              <el-dropdown-item :command="0.7">严格 (≥70%)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="emit('update:createRelationDialogVisible', true)">手动创建</el-button>
      </div>
    </div>

    <div class="relation-layout">
      <!-- 左：关系图 -->
      <div class="relation-graph">
        <EChartsWrapper v-if="relations.length > 0" :option="graphOption" height="520px" />
        <el-empty v-else description="暂无标签关系数据，点击「自动发现」扫描" :image-size="56" />
      </div>

      <!-- 右：待审核面板 -->
      <div class="review-panel" v-if="relations.length > 0">
        <div class="review-panel__head">
          <span class="review-panel__title">待审核</span>
          <el-tag v-if="pendingRelations.length" type="warning" size="small" effect="dark">{{ pendingRelations.length }} 条</el-tag>
          <span v-else class="review-panel__empty">暂无</span>
          <div class="review-panel__actions" v-if="pendingRelations.length > 0">
            <el-button type="success" size="small" @click="emit('batchApproveAll')">全部通过</el-button>
            <el-button type="danger" size="small" @click="emit('batchRejectAll')">全部拒绝</el-button>
          </div>
        </div>
        <div class="review-panel__body">
          <div v-for="row in pendingRelations" :key="row.id" class="review-item">
            <div class="review-item__tags">
              <span class="review-item__source">{{ row.sourceTagName }}</span>
              <span class="review-item__arrow">→</span>
              <span class="review-item__target">{{ row.targetTagName }}</span>
            </div>
            <div class="review-item__meta">
              <el-tag :type="row.relationType === 'SAME_AS' ? '' : 'success'" size="small" effect="plain">
                {{ row.relationType === 'SAME_AS' ? '等价' : '相近' }}
              </el-tag>
              <span class="review-item__score">{{ row.similarityScore ? (row.similarityScore * 100).toFixed(0) + '%' : '-' }}</span>
            </div>
            <div class="review-item__ops">
              <el-button type="success" link size="small" @click="emit('approveRelation', row.id)">通过</el-button>
              <el-button type="danger" link size="small" @click="emit('rejectRelation', row.id)">拒绝</el-button>
            </div>
          </div>
          <el-empty v-if="pendingRelations.length === 0" description="全部处理完毕 ✓" :image-size="40" />
        </div>
      </div>
    </div>
  </section>

  <!-- 创建关系对话框 -->
  <el-dialog :model-value="createRelationDialogVisible" @update:model-value="emit('update:createRelationDialogVisible', $event)" title="手动创建标签关系" width="480px" :close-on-click-modal="false">
    <el-form label-width="80px">
      <el-form-item label="源标签ID">
        <el-input :model-value="createRelationForm.sourceTagId" placeholder="输入源标签ID" type="number" @update:model-value="emit('update:createRelationForm', { ...createRelationForm, sourceTagId: $event })" />
      </el-form-item>
      <el-form-item label="目标标签ID">
        <el-input :model-value="createRelationForm.targetTagId" placeholder="输入目标标签ID" type="number" @update:model-value="emit('update:createRelationForm', { ...createRelationForm, targetTagId: $event })" />
      </el-form-item>
      <el-form-item label="关系类型">
        <el-select :model-value="createRelationForm.relationType" class="!w-full" @update:model-value="emit('update:createRelationForm', { ...createRelationForm, relationType: $event })">
          <el-option label="语义相近" value="SIMILAR" />
          <el-option label="语义等价" value="SAME_AS" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input :model-value="createRelationForm.remark" placeholder="可选备注" @update:model-value="emit('update:createRelationForm', { ...createRelationForm, remark: $event })" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:createRelationDialogVisible', false)">取消</el-button>
      <el-button type="primary" @click="emit('createRelation')">创建</el-button>
    </template>
  </el-dialog>
</template>
