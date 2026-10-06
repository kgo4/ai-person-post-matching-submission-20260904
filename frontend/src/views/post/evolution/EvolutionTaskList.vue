<script setup lang="ts">
/**
 * 变更审核：演化任务列表与后续动作（详情 / 分析 / 应用 / 删除）。
 *
 * 从原 867 行页面里拆出。对外暴露 `reload()`，因为「运行演化」提交任务后
 * 列表要立刻出现新任务 —— 两个组件是兄弟，不能靠 props 传递刷新信号。
 *
 * 岗位名走 map 回填：任务表只存 postId，直接显示 id 用户看不懂是哪个岗位；
 * 单个岗位查询失败时回落到任务名，而不是留一个空白单元格。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { analyzeEvolutionTask, applyEvolutionChanges, deleteEvolutionTask, pageEvolutionTasks } from '@/api/evolution'
import type { PostEvolutionTask } from '@/api/evolution'
import { getPost } from '@/api/post'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { canDeleteTask, statusTagType, statusText, triggerTypeText } from './evolution-status'

const router = useRouter()
const loading = ref(false)
const tasks = ref<PostEvolutionTask[]>([])
const postNameMap = reactive<Record<number, string>>({})
const searchStatus = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })

function displayPostName(task: PostEvolutionTask) {
  return postNameMap[task.postId]
    || (task.taskName?.replace(/\s*演化任务\s*$/, '') || '岗位名称待补充')
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await pageEvolutionTasks({
      current: pagination.current,
      size: pagination.size,
      taskStatus: searchStatus.value || undefined,
    })
    tasks.value = res.data.records
    pagination.total = res.data.total
    // 只回填缺失的岗位名，避免每次翻页都重复查同一批岗位
    const missing = [...new Set(tasks.value.map(task => task.postId))]
      .filter(postId => postId != null && !postNameMap[postId])
    await Promise.all(missing.map(async postId => {
      try {
        const postRes = await getPost(postId)
        if (postRes.data?.postName) postNameMap[postId] = postRes.data.postName
      } catch {
        // 岗位查不到不影响列表展示，displayPostName 有回落
      }
    }))
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '加载演化任务失败'))
  } finally {
    loading.value = false
  }
}

async function reload() {
  await loadTasks()
}

async function handleDeleteTask(task: PostEvolutionTask) {
  if (!canDeleteTask(task)) {
    ElMessage.warning('运行中的演化任务不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除「${displayPostName(task)}」这条演化记录？关联证据和变更项也会删除，但不会影响岗位能力模型。`,
      '删除演化记录',
      { type: 'warning' },
    )
  } catch {
    // 用户取消：正常路径
    return
  }
  try {
    await deleteEvolutionTask(task.id)
    ElMessage.success('演化记录已删除')
    // 删掉当前页最后一条时回退一页，否则会停在一个空页上
    if (tasks.value.length === 1 && pagination.current > 1) {
      pagination.current -= 1
    }
    await loadTasks()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '删除演化记录失败'))
  }
}

async function handleAnalyze(task: PostEvolutionTask) {
  try {
    await ElMessageBox.confirm('确认执行演化分析？', '提示')
  } catch {
    return
  }
  loading.value = true
  try {
    await analyzeEvolutionTask(task.id)
    ElMessage.success('分析完成')
    await loadTasks()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '演化分析失败'))
  } finally {
    loading.value = false
  }
}

async function handleApply(task: PostEvolutionTask) {
  try {
    await ElMessageBox.confirm('确认应用已审核通过的变更？', '提示')
  } catch {
    return
  }
  loading.value = true
  try {
    const res = await applyEvolutionChanges(task.id)
    ElMessage.success(`已应用 ${res.data.applied} 项变更`)
    await loadTasks()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '应用变更失败'))
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)

defineExpose({ reload })
</script>

<template>
  <section class="evo-card">
    <div class="evo-card__head">
      <span class="evo-card__title">演化任务列表</span>
      <span class="evo-hint">变更建议必须在此逐条审核后才会应用，不存在自动落地的路径。</span>
    </div>
    <div class="evo-card__toolbar">
      <el-select v-model="searchStatus" clearable placeholder="全部状态" size="default" style="width:140px">
        <el-option label="待处理" value="PENDING" />
        <el-option label="运行中" value="RUNNING" />
        <el-option label="待确认" value="WAIT_CONFIRM" />
        <el-option label="已应用" value="APPLIED" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <el-button type="primary" plain @click="loadTasks">查询</el-button>
    </div>
    <div class="evo-card__body--flush">
      <el-table v-loading="loading" :data="tasks" empty-text="暂无演化任务，先到「运行演化」提交一次">
        <el-table-column prop="taskCode" label="任务编码" width="200" />
        <el-table-column label="岗位" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ displayPostName(row) }}</template>
        </el-table-column>
        <el-table-column label="触发方式" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ triggerTypeText(row.triggerType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.taskStatus)" size="small">{{ statusText(row.taskStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="router.push(`/post/evolution/detail/${row.id}`)">详情</el-button>
            <el-button
              v-if="row.taskStatus === 'PENDING' || row.taskStatus === 'FAILED'"
              type="success"
              link
              size="small"
              @click="handleAnalyze(row)"
            >分析</el-button>
            <el-button
              v-if="row.taskStatus === 'WAIT_CONFIRM'"
              type="warning"
              link
              size="small"
              @click="handleApply(row)"
            >应用</el-button>
            <el-button
              v-if="canDeleteTask(row)"
              type="danger"
              link
              size="small"
              @click="handleDeleteTask(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="evo-pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next"
          size="small"
          @current-change="loadTasks"
          @size-change="loadTasks"
        />
      </div>
    </div>
  </section>
</template>
