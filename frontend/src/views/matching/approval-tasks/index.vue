<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pendingTasks, approve, getCurrentUser } from '@/api'
import type { UserVO } from '@/api'

const router = useRouter()
const loading = ref(false)
const taskList = ref<any[]>([])
const currentUser = ref<UserVO | null>(null)

const approvalDialogVisible = ref(false)
const approvalSubmitting = ref(false)
const currentTask = ref<any>(null)
const approvalForm = ref({ approved: true, remark: '' })

async function loadCurrentUser() {
  try {
    const res = await getCurrentUser()
    currentUser.value = res.data
  } catch { /* handled by interceptor */ }
}

async function loadTasks() {
  if (!currentUser.value) return
  loading.value = true
  try {
    const res = await pendingTasks(currentUser.value.id)
    taskList.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载待办任务失败')
    taskList.value = []
  } finally {
    loading.value = false
  }
}

function viewDetail(matchingRecordId: number) {
  router.push(`/matching/detail/${matchingRecordId}`)
}

function openApprovalDialog(task: any, approved: boolean) {
  currentTask.value = task
  approvalForm.value.approved = approved
  approvalForm.value.remark = ''
  approvalDialogVisible.value = true
}

async function handleSubmitApproval() {
  if (!currentTask.value) return
  if (!approvalForm.value.approved && !approvalForm.value.remark.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  approvalSubmitting.value = true
  try {
    const task = currentTask.value
    const dto = {
      matchingRecordId: task.matchingRecordId,
      approvalStatus: approvalForm.value.approved ? 2 : 3,
      approvalRemark: approvalForm.value.remark,
    }
    await approve(dto)
    ElMessage.success(approvalForm.value.approved ? '审批通过' : '已驳回')
    approvalDialogVisible.value = false
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '审批操作失败')
  } finally {
    approvalSubmitting.value = false
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(async () => {
  await loadCurrentUser()
  await loadTasks()
})
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Review Queue</div>
        <h1 class="page-hero__title">待办审批</h1>
        <p class="page-hero__desc">
          <template v-if="currentUser">当前用户：{{ currentUser.realName }} ({{ currentUser.username }})</template>
          <template v-else>加载中...</template>
        </p>
      </div>
      <div class="toolbar-group">
        <el-button type="primary" :loading="loading" @click="loadTasks">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </section>

    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">待办任务列表</div>
          <div class="section-desc">需要您审批的图谱匹配结果。</div>
        </div>
      </div>
      <div class="panel-body">
        <el-table :data="taskList" v-loading="loading" style="width: 100%">
          <el-table-column prop="taskName" label="任务名称" min-width="150" />
          <el-table-column prop="matchingRecordId" label="匹配记录" width="130">
            <template #default="{ row }">
              <el-button type="primary" link @click="viewDetail(row.matchingRecordId)">
                #{{ row.matchingRecordId }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              <span class="text-muted">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="table-link-cluster">
                <el-button type="primary" link @click="viewDetail(row.matchingRecordId)">详情</el-button>
                <el-button type="success" link @click="openApprovalDialog(row, true)">通过</el-button>
                <el-button type="danger" link @click="openApprovalDialog(row, false)">驳回</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && taskList.length === 0" description="暂无待办任务" />
      </div>
    </section>

    <el-dialog v-model="approvalDialogVisible" :title="approvalForm.approved ? '审批通过' : '审批驳回'" width="500px" :close-on-click-modal="false">
      <div v-if="currentTask">
        <div style="margin-bottom: 16px;">
          <span class="text-muted">任务：</span><strong>{{ currentTask.taskName }}</strong>
          <span class="text-muted" style="margin-left: 16px;">匹配记录：</span><strong>#{{ currentTask.matchingRecordId }}</strong>
        </div>
        <el-form :model="approvalForm" label-width="100px">
          <el-form-item label="审批结果">
            <el-tag :type="approvalForm.approved ? 'success' : 'danger'" size="large">
              {{ approvalForm.approved ? '通过' : '驳回' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="审批意见" :required="!approvalForm.approved">
            <el-input v-model="approvalForm.remark" type="textarea" :rows="4" :placeholder="approvalForm.approved ? '请输入审批意见（可选）' : '请输入驳回原因（必填）'" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="approvalDialogVisible = false">取消</el-button>
        <el-button :type="approvalForm.approved ? 'success' : 'danger'" :loading="approvalSubmitting" @click="handleSubmitApproval">
          {{ approvalForm.approved ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.text-muted { color: var(--app-text-muted); font-size: 13px; }
</style>
