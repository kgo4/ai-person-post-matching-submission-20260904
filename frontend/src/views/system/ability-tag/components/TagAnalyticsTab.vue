<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import type { AbilityTagUsageStat } from '@/api/tag-governance'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import type { TagMergeNotification } from '@/api/tag-governance'

defineProps<{
  mergeForm: { threshold: number; scheduledTime: string | Date }
  mergingTags: boolean
  schedulingMerge: boolean
  pendingMerges: Array<{ taskId: string; scheduledTime: string; threshold: number }>
  recentMergeNotifications: TagMergeNotification[]
  lastMergeResult: {
    foundPairs: number
    mergedCount: number
    totalTags: number
    tagsWithVector: number
    details: Array<{ mergeTag: string; keepTag: string; similarity: number }>
  } | null
  mergeResultDialogVisible: boolean
  categoryPieOption: EChartsOption
  wordCloudOption: EChartsOption
  bubbleOption: EChartsOption
  usageStats: AbilityTagUsageStat[]
  computingStats: boolean
}>()

const emit = defineEmits<{
  (e: 'update:mergeResultDialogVisible', val: boolean): void
  (e: 'executeMerge'): void
  (e: 'scheduleMerge'): void
  (e: 'cancelMerge', taskId: string): void
  (e: 'computeStats'): void
  (e: 'update:mergeThreshold', val: number): void
  (e: 'update:mergeScheduledTime', val: string | Date): void
}>()

const disablePastDate = (date: Date) => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}
</script>

<template>
  <div class="health-charts">
    <!-- 标签自动归并 -->
    <section class="glass-card motion-rise" style="margin-bottom: 18px;">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">标签自动归并</div>
          <div class="section-desc">设置阈值和时间，手动触发或定时执行标签归并</div>
        </div>
        <div class="toolbar-group">
          <span style="color: #7690ad; font-size: 12px;">阈值</span>
          <el-input-number
            :model-value="mergeForm.threshold"
            :min="0.5"
            :max="1"
            :step="0.05"
            :precision="2"
            size="small"
            style="width: 110px;"
            @update:model-value="emit('update:mergeThreshold', $event)"
          />
          <el-button type="primary" size="small" :loading="mergingTags" @click="emit('executeMerge')">
            立即执行
          </el-button>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 10px; padding: 0 18px 14px; flex-wrap: wrap;">
        <span style="color: #7690ad; font-size: 12px; white-space: nowrap;">定时执行</span>
        <el-date-picker
          :model-value="mergeForm.scheduledTime"
          type="datetime"
          placeholder="选择执行时间"
          size="small"
          format="YYYY-MM-DD HH:mm"
          :disabled-date="disablePastDate"
          style="width: 200px;"
          @update:model-value="emit('update:mergeScheduledTime', $event)"
        />
        <el-button size="small" :loading="schedulingMerge" :disabled="!mergeForm.scheduledTime" @click="emit('scheduleMerge')">
          设定定时
        </el-button>
        <div v-if="pendingMerges.length > 0" style="display: flex; align-items: center; gap: 8px; margin-left: 12px; flex-wrap: wrap;">
          <el-tag
            v-for="task in pendingMerges"
            :key="task.taskId"
            closable
            size="small"
            type="warning"
            @close="emit('cancelMerge', task.taskId)"
          >
            阈值{{ Math.round(task.threshold * 100) }}%，{{ task.scheduledTime.replace('T', ' ').substring(0, 16) }}
          </el-tag>
        </div>
      </div>
      <div v-if="recentMergeNotifications.length" style="display: flex; align-items: center; gap: 8px; padding: 0 18px 14px; flex-wrap: wrap;">
        <span style="color: #7690ad; font-size: 12px; white-space: nowrap;">最近结果</span>
        <el-tag v-for="task in recentMergeNotifications" :key="task.taskId" :type="task.status === 'COMPLETED' ? 'success' : 'danger'" size="small">
          {{ task.status === 'COMPLETED' ? '完成' : '失败' }}：{{ task.taskId }}{{ task.errorMessage ? `，${task.errorMessage}` : '' }}
        </el-tag>
      </div>
    </section>

    <!-- 第一行：环形图 + 词云 -->
    <div class="health-row">
      <section class="glass-card health-card motion-rise">
        <div class="toolbar-panel">
          <div class="section-title">标签分类分布</div>
          <el-button size="small" :loading="computingStats" @click="emit('computeStats')">刷新统计</el-button>
        </div>
        <div class="panel-body chart-body">
          <EChartsWrapper :option="categoryPieOption" height="280px" />
        </div>
      </section>

      <section class="glass-card health-card motion-rise">
        <div class="toolbar-panel">
          <div class="section-title">标签热度词云</div>
          <div class="section-desc">字号越大 = 热度越高，颜色 = 分类</div>
        </div>
        <div class="panel-body chart-body">
          <EChartsWrapper v-if="usageStats.length > 0" :option="wordCloudOption" height="280px" />
          <el-empty v-else description="暂无统计数据，请先刷新统计" :image-size="48" />
        </div>
      </section>
    </div>

    <!-- 第二行：气泡图 -->
    <section class="glass-card motion-rise">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">标签引用分布</div>
          <div class="section-desc">X轴 = 岗位引用数 · Y轴 = 员工引用数 · 气泡大小 = 综合热度 · 悬停查看详情</div>
        </div>
        <el-button size="small" :loading="computingStats" @click="emit('computeStats')">刷新统计</el-button>
      </div>
      <div class="panel-body">
        <EChartsWrapper v-if="usageStats.length > 0" :option="bubbleOption" height="300px" />
        <el-empty v-else description="暂无统计数据，请点击「刷新统计」生成" :image-size="48" />
      </div>
    </section>
  </div>

  <!-- 归并结果对话框 -->
  <el-dialog :model-value="mergeResultDialogVisible" @update:model-value="emit('update:mergeResultDialogVisible', $event)" title="归并结果" width="600px" :close-on-click-modal="false">
    <div v-if="lastMergeResult" class="merge-result-body">
      <div class="merge-result-stats">
        <div class="merge-stat-item">
          <span>{{ lastMergeResult.totalTags }}</span>
          <small>标签总数</small>
        </div>
        <div class="merge-stat-item">
          <span>{{ lastMergeResult.tagsWithVector }}</span>
          <small>有向量标签</small>
        </div>
        <div class="merge-stat-item">
          <span>{{ lastMergeResult.foundPairs }}</span>
          <small>发现相似对</small>
        </div>
        <div class="merge-stat-item highlight">
          <span>{{ lastMergeResult.mergedCount }}</span>
          <small>成功归并</small>
        </div>
      </div>
      <div v-if="lastMergeResult.details.length > 0" style="margin-top: 16px;">
        <div class="section-title" style="margin-bottom: 8px;">归并明细</div>
        <el-table :data="lastMergeResult.details" size="small" max-height="300">
          <el-table-column prop="mergeTag" label="被归并标签" min-width="140" />
          <el-table-column prop="keepTag" label="保留标签" min-width="140" />
          <el-table-column prop="similarity" label="相似度" width="100" align="center">
            <template #default="{ row }">
              {{ (row.similarity * 100).toFixed(1) }}%
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="未发现满足阈值的可归并标签对" :image-size="60" />
    </div>
    <template #footer>
      <el-button @click="emit('update:mergeResultDialogVisible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>
