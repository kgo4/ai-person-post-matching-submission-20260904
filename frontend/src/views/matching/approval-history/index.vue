<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageRecords, listApprovalFlows } from '@/api'
import type { MatchingApprovalFlow, MatchingRecord, PageResultVO } from '@/api'

const router = useRouter()
const loading = ref(false)
const tableData = ref<MatchingRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const flowsLoading = ref<Record<number, boolean>>({})
const approvalFlows = ref<Record<number, MatchingApprovalFlow[]>>({})

const filters = reactive({
  approvalStatus: '' as string | number,
  postId: '',
  empId: '',
})

const approvalStatusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未发起', type: 'info' },
  1: { label: '审批中', type: 'warning' },
  2: { label: '已通过', type: 'success' },
  3: { label: '已驳回', type: 'danger' },
}

const matchStatusMap: Record<number, string> = {
  0: '待审核',
  1: '强匹配',
  2: '匹配',
  3: '待观察',
  4: '不匹配',
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (filters.postId) params.postId = filters.postId
    if (filters.empId) params.empId = filters.empId
    const res = await pageRecords(params)
    const pageResult: PageResultVO<MatchingRecord> = res.data as any
    let records = pageResult.records || []
    if (filters.approvalStatus !== '') {
      records = records.filter(r => r.approvalStatus === Number(filters.approvalStatus))
    }
    tableData.value = records
    total.value = pageResult.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载审批历史失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() { currentPage.value = 1; loadData() }
function handleSizeChange(size: number) { pageSize.value = size; currentPage.value = 1; loadData() }
function handleCurrentChange(page: number) { currentPage.value = page; loadData() }
function viewDetail(id: number) { router.push(`/matching/detail/${id}`) }
function formatTime(time: string) { return time ? new Date(time).toLocaleString('zh-CN') : '-' }

const approvalNodeStatusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待审批', type: 'info' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
}

async function handleExpandChange(row: MatchingRecord, expandedRows: MatchingRecord[]) {
  if (!expandedRows.includes(row)) return
  if (approvalFlows.value[row.id]) return // 已加载
  flowsLoading.value[row.id] = true
  try {
    const res = await listApprovalFlows(row.id)
    approvalFlows.value[row.id] = res.data || []
  } catch {
    approvalFlows.value[row.id] = []
  } finally {
    flowsLoading.value[row.id] = false
  }
}

onMounted(() => { loadData() })
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Approval Archive</div>
        <h1 class="page-hero__title">审批历史</h1>
        <p class="page-hero__desc">查看所有图谱匹配结果的审批记录。</p>
      </div>
      <div class="toolbar-group">
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </section>

    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">审批记录</div>
          <div class="section-desc">支持按审批状态、岗位、人员筛选。</div>
        </div>
        <div class="toolbar-group">
          <el-select v-model="filters.approvalStatus" placeholder="审批状态" clearable class="!w-28" @change="handleSearch">
            <el-option label="未发起" :value="0" />
            <el-option label="审批中" :value="1" />
            <el-option label="已通过" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
          <el-input v-model="filters.postId" placeholder="岗位ID" clearable class="!w-32" />
          <el-input v-model="filters.empId" placeholder="人员ID" clearable class="!w-32" />
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button @click="filters.approvalStatus = ''; filters.postId = ''; filters.empId = ''; handleSearch()">重置</el-button>
        </div>
      </div>

      <div class="panel-body">
        <el-table :data="tableData" v-loading="loading" style="width: 100%" @expand-change="handleExpandChange">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-loading="flowsLoading[row.id]" style="padding: 8px 24px;">
                <template v-if="approvalFlows[row.id]?.length">
                  <div class="section-desc" style="margin-bottom: 10px;">审批流程节点（{{ approvalFlows[row.id].length }} 个节点）</div>
                  <el-table :data="approvalFlows[row.id]" size="small" border>
                    <el-table-column prop="nodeOrder" label="顺序" width="60" align="center" />
                    <el-table-column prop="nodeName" label="审批节点" min-width="140" />
                    <el-table-column prop="approverId" label="审批人ID" width="100" align="center" />
                    <el-table-column label="审批结果" width="100" align="center">
                      <template #default="{ row: node }">
                        <el-tag :type="(approvalNodeStatusMap[node.approvalStatus]?.type || 'info') as any" size="small">
                          {{ approvalNodeStatusMap[node.approvalStatus]?.label || '未知' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="approvalRemark" label="审批意见" min-width="160" show-overflow-tooltip />
                    <el-table-column label="审批时间" width="170">
                      <template #default="{ row: node }">
                        <span class="text-muted">{{ formatTime(node.approvalTime) }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </template>
                <el-empty v-else description="该记录暂无审批流程" :image-size="40" />
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="记录ID" width="80" />
          <el-table-column prop="batchNo" label="批次号" width="160" show-overflow-tooltip />
          <el-table-column label="人员" min-width="100">
            <template #default="{ row }">
              <el-button type="primary" link @click="viewDetail(row.id)">{{ row.empName || `员工#${row.empId}` }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="岗位" min-width="120">
            <template #default="{ row }">{{ row.postName || `岗位#${row.postId}` }}</template>
          </el-table-column>
          <el-table-column label="AI匹配分" width="100" align="center">
            <template #default="{ row }">
              <strong>{{ row.aiMatchScore }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="匹配状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ matchStatusMap[row.matchStatus] || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="(approvalStatusMap[row.approvalStatus]?.type || 'info') as any" size="small">
                {{ approvalStatusMap[row.approvalStatus]?.label || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170">
            <template #default="{ row }">
              <span class="text-muted">{{ formatTime(row.createdTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="viewDetail(row.id)">详情</el-button>
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
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.text-muted { color: var(--app-text-muted); font-size: 13px; }
</style>
