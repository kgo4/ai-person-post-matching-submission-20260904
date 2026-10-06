<script setup lang="ts">
import { computed } from 'vue'
import { Search, MoreFilled } from '@element-plus/icons-vue'
import {
  getMatchStatusText,
  getScoreColor,
  getScreeningLevelText,
  interviewResultText,
  interviewTagType,
  parseHardConditions,
} from '../composables/useMatchingResult'
import { hasAnyPermission } from '@/utils/permission'
import type { MatchingRecord } from '@/api'

defineProps<{
  data: MatchingRecord[]
  loading: boolean
  total: number
  currentPage: number
  pageSize: number
  filters: { postId: string; empId: string; matchStatus: string }
  interviewPassedCount: number
  pendingCount: number
  strongMatchCount: number
}>()

/**
 * 匹配结果的「修改 / 推送 / 撤回」属 HR 的匹配运营动作，
 * 需与后端 PUT /api/matching/record/** 的判定一致：
 * MATCHING:APPROVE 或 MATCHING:EXECUTE 任一即可。
 *
 * 此前这些操作无条件渲染，导致线上无 MATCHING:APPROVE 的角色看得到按钮、
 * 点下去却 403（`PUT /api/matching/record/{id}`）。
 */
const canOperateResult = computed(() => hasAnyPermission(['MATCHING:APPROVE', 'MATCHING:EXECUTE']))

const emit = defineEmits<{
  search: []
  'size-change': [size: number]
  'current-change': [page: number]
  reset: []
  lock: [row: MatchingRecord]
  unlock: [row: MatchingRecord]
  delete: [row: MatchingRecord]
  modify: [row: MatchingRecord]
  detail: [id: number]
  publish: [row: MatchingRecord]
  unpublish: [row: MatchingRecord]
  gap: [row: MatchingRecord]
  compare: [row: MatchingRecord]
}>()
</script>

<template>
  <section class="toolbar-and-table">
    <div class="glass-card motion-rise">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">匹配记录</div>
          <div class="section-desc">按岗位、人员和状态过滤，直接在表格中完成复核动作。</div>
        </div>
        <div class="toolbar-group">
          <el-input v-model="filters.postId" placeholder="岗位 ID" clearable class="!w-32" size="default" />
          <el-input v-model="filters.empId" placeholder="人员 ID" clearable class="!w-32" size="default" />
          <el-select v-model="filters.matchStatus" placeholder="匹配状态" clearable class="!w-32" size="default">
            <el-option label="待审核" :value="0" />
            <el-option label="强匹配" :value="1" />
            <el-option label="匹配" :value="2" />
            <el-option label="待观察" :value="3" />
            <el-option label="不匹配" :value="4" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="emit('search')">搜索</el-button>
          <button class="glass-btn" @click="emit('reset')">重置</button>
        </div>
      </div>

      <div class="panel-body">
        <el-table :data="data" v-loading="loading" style="width: 100%" size="default">
          <el-table-column prop="batchNo" label="批次号" width="140" fixed="left" />
          <el-table-column label="人员" min-width="110" fixed="left">
            <template #default="{ row }">{{ row.empName || `员工#${row.empId}` }}</template>
          </el-table-column>
          <el-table-column label="岗位" min-width="110">
            <template #default="{ row }">{{ row.postName || `岗位#${row.postId}` }}</template>
          </el-table-column>
          <el-table-column label="L2" width="70" align="center">
            <template #default="{ row }">
              <span v-if="row.l2Score != null" class="font-semibold">{{ row.l2Score }}</span>
              <span v-else class="text-gray-300">-</span>
            </template>
          </el-table-column>
          <el-table-column label="AI分" width="70" align="center">
            <template #default="{ row }">
              <!-- AI 建议分写入 llm_score（ai_score 恒空），与详情页一致做 llmScore 回退 -->
              <span v-if="row.aiScore != null || row.llmScore != null" class="font-semibold text-sky-600">{{ row.aiScore ?? row.llmScore }}</span>
              <span v-else class="text-gray-300">-</span>
            </template>
          </el-table-column>
          <el-table-column label="最终分" width="80" align="center">
            <template #default="{ row }">
              <span class="result-score" :style="{ color: getScoreColor(row.finalMatchScore ?? row.aiMatchScore) }">
                {{ row.finalMatchScore ?? row.aiMatchScore }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="级别" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.screeningLevel === 1 ? 'info' : row.screeningLevel === 2 ? 'warning' : 'primary'">
                {{ getScreeningLevelText(row.screeningLevel).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.matchStatus === 1 ? 'success' : row.matchStatus === 2 ? 'primary' : row.matchStatus === 3 ? 'warning' : row.matchStatus === 4 ? 'danger' : 'info'">
                {{ getMatchStatusText(row.matchStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="淘汰原因" min-width="200">
            <template #default="{ row }">
              <template v-if="row.screeningLevel === 1 && row.hardConditionResult">
                <div v-for="(detail, index) in parseHardConditions(row.hardConditionResult)" :key="index" class="reason-line">
                  <span :class="detail.passed ? 'text-emerald-500' : 'text-red-500'">{{ detail.passed ? '✓' : '✕' }}</span>
                  <span>{{ detail.label }}：期望 {{ detail.expectedValue }}，实际 {{ detail.actualValue ?? '未填写' }}</span>
                </div>
              </template>
              <span v-else class="text-gray-300">-</span>
            </template>
          </el-table-column>
          <!-- 「审批」列已随审批概念整体移除（匹配结果不需要审批）。
               取而代之看「推送」列：HR 人工确认后可直接推送，推送才是员工可见开关。 -->
          <el-table-column label="终面" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="interviewTagType(row.interviewResult)">
                {{ interviewResultText(row.interviewResult) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="锁定" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isLocked ? 'warning' : 'info'">{{ row.isLocked ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="推送" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.publishStatus === 1 ? 'success' : 'info'">
                {{ row.publishStatus === 1 ? '已推送' : '未推送' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="160" />
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="{ row }">
              <div class="table-link-cluster">
                <el-button type="primary" link size="small" @click="emit('detail', row.id)">详情</el-button>
                <el-button type="warning" link size="small" @click="emit('gap', row)">差距</el-button>
                <!-- 「修改结果」「推送/撤回」「锁定/解锁」都走 PUT /api/matching/record/**，
                     需与后端一致按 MATCHING:APPROVE 或 MATCHING:EXECUTE 显示。
                     此前无条件渲染，线上无该权限的角色看得到按钮、点下去却 403。 -->
                <template v-if="canOperateResult">
                  <el-button type="primary" link size="small" @click="emit('modify', row)">修改结果</el-button>
                  <!-- 匹配结果不再要求先走审批：HR 人工确认/修改结果即为审核动作，可直接推送 -->
                  <el-button
                    v-if="row.publishStatus !== 1"
                    type="success" link size="small"
                    @click="emit('publish', row)"
                  >推送给员工</el-button>
                  <el-button
                    v-else
                    type="danger" link size="small"
                    @click="emit('unpublish', row)"
                  >撤回推送</el-button>
                  <el-button v-if="!row.isLocked" type="success" link size="small" @click="emit('lock', row)">锁定</el-button>
                  <el-button v-else type="info" link size="small" @click="emit('unlock', row)">解锁</el-button>
                </template>
                <el-dropdown trigger="click">
                  <el-button link size="small" class="more-btn">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <!-- 「发起审批」入口已移除：它需要先调 GET /api/system/user/page 选审批人，
                           而该接口要求 USER:MANAGE，HR 必然 403；且匹配结果已不再以审批为前置条件。 -->
                      <el-dropdown-item @click="emit('compare', row)">对比图谱</el-dropdown-item>
                      <el-dropdown-item divided @click="emit('delete', row)">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          @size-change="emit('size-change', $event)"
          @current-change="emit('current-change', $event)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.result-score {
  font-size: 16px;
  font-weight: 800;
}

.reason-line {
  display: flex;
  gap: 6px;
  line-height: 1.5;
  font-size: 12px;
}

.more-btn {
  padding: 0 4px;
  color: var(--app-text-muted);
}
</style>
