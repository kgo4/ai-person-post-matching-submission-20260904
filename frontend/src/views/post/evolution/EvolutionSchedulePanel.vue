<script setup lang="ts">
/**
 * 定时演化：按 Cron 周期性运行演化分析，只生成待确认建议。
 *
 * 从原 867 行页面里拆出，弹窗一并带走 —— 弹窗的生命周期完全由本面板的
 * 「新建/编辑」按钮驱动，留在 index.vue 里只会让外壳多背一份无关状态。
 *
 * 定时任务**不会**自动修改岗位能力模型，这一点必须在列表和弹窗里都说清楚：
 * 用户看到「定时执行」很容易以为系统会自动改能力项。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createSchedule, deleteSchedule, pageSchedules, runScheduleNow, updateSchedule } from '@/api/evolution'
import type { PostEvolutionScheduleConfig } from '@/api/evolution'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { useCloudKnowledge } from './use-cloud-knowledge'

/**
 * 云知识库未配置时，开关置灰。
 *
 * 这里**不改写已保存的值**（编辑旧配置时可能本来就是 1）：静默把用户的配置改成 0
 * 属于数据篡改；置灰表达的是「当前不可修改」，不是「帮你关掉」。
 * 只有**新建**时才按可用性取默认值。
 */
const {
  usable: cloudKbUsable,
  ensureLoaded: ensureCloudKbLoaded,
  notConfiguredHint: cloudKbHint,
  warnNotConfigured: warnCloudKbNotConfigured,
} = useCloudKnowledge()

const scheduleLoading = ref(false)
const schedules = ref<PostEvolutionScheduleConfig[]>([])
const showScheduleDialog = ref(false)
const editingSchedule = ref<PostEvolutionScheduleConfig | null>(null)
const saving = ref(false)

const DEFAULT_CRON = '0 0 2 * * ?'

const scheduleForm = reactive({
  postId: 0,
  industry: '',
  businessDomain: '',
  cronExpression: DEFAULT_CRON,
  includeWhitepaper: 1,
  includeCloudKnowledge: 1,
  includeMarketJd: 0,
  enabled: 1,
})

async function loadSchedules() {
  scheduleLoading.value = true
  try {
    const res = await pageSchedules({ current: 1, size: 50 })
    schedules.value = res.data.records
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '加载定时配置失败'))
  } finally {
    scheduleLoading.value = false
  }
}

function openScheduleDialog(schedule?: PostEvolutionScheduleConfig) {
  if (schedule) {
    editingSchedule.value = schedule
    Object.assign(scheduleForm, {
      postId: schedule.postId,
      industry: schedule.industry || '',
      businessDomain: schedule.businessDomain || '',
      cronExpression: schedule.cronExpression || DEFAULT_CRON,
      includeWhitepaper: schedule.includeWhitepaper,
      includeCloudKnowledge: schedule.includeCloudKnowledge,
      includeMarketJd: schedule.includeMarketJd,
      enabled: schedule.enabled,
    })
  } else {
    editingSchedule.value = null
    Object.assign(scheduleForm, {
      postId: 0,
      industry: '',
      businessDomain: '',
      cronExpression: DEFAULT_CRON,
      includeWhitepaper: 1,
      // 新建时按可用性取默认值（未配置 → 0）；编辑时不改写已保存的值
      includeCloudKnowledge: cloudKbUsable.value ? 1 : 0,
      includeMarketJd: 0,
      enabled: 1,
    })
  }
  showScheduleDialog.value = true
}

async function handleSaveSchedule() {
  if (!scheduleForm.postId) {
    ElMessage.warning('请填写岗位ID')
    return
  }
  saving.value = true
  try {
    if (editingSchedule.value) {
      await updateSchedule(editingSchedule.value.id, scheduleForm)
      ElMessage.success('更新成功')
    } else {
      await createSchedule(scheduleForm)
      ElMessage.success('创建成功')
    }
    showScheduleDialog.value = false
    await loadSchedules()
  } catch (error) {
    // 保存失败保持弹窗打开并保留已填内容，否则要重填一遍
    ElMessage.error(resolveApiErrorMessage(error, '保存定时配置失败'))
  } finally {
    saving.value = false
  }
}

async function handleRunSchedule(schedule: PostEvolutionScheduleConfig) {
  try {
    await ElMessageBox.confirm('确认立即执行此定时任务？', '提示')
  } catch {
    return
  }
  try {
    await runScheduleNow(schedule.id)
    ElMessage.success('任务已启动')
    await loadSchedules()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '启动定时任务失败'))
  }
}

async function handleDeleteSchedule(schedule: PostEvolutionScheduleConfig) {
  try {
    await ElMessageBox.confirm('确认删除此定时配置？', '提示')
  } catch {
    return
  }
  try {
    await deleteSchedule(schedule.id)
    ElMessage.success('删除成功')
    await loadSchedules()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '删除定时配置失败'))
  }
}

onMounted(() => {
  void loadSchedules()
  // 与「运行演化」「证据准备」共用同一次状态请求（composable 内部去重）
  void ensureCloudKbLoaded()
})
</script>

<template>
  <section class="evo-card">
    <div class="evo-card__head">
      <span class="evo-card__title">定时演化配置</span>
      <el-tag type="info" size="small">只生成待确认建议</el-tag>
      <el-button type="primary" size="small" style="margin-left:auto" @click="openScheduleDialog()">新建配置</el-button>
    </div>
    <div class="evo-card__body--flush">
      <el-table
        v-loading="scheduleLoading"
        :data="schedules"
        empty-text="暂无定时配置，点右上角「新建配置」按 Cron 周期运行演化分析"
      >
        <el-table-column prop="postId" label="岗位ID" width="100" />
        <el-table-column prop="industry" label="行业" width="150" />
        <el-table-column prop="businessDomain" label="业务领域" width="150" />
        <el-table-column prop="cronExpression" label="执行频率" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="runCount" label="执行次数" width="100" />
        <el-table-column prop="lastRunTime" label="最近执行" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openScheduleDialog(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleRunSchedule(row)">立即执行</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteSchedule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <el-dialog v-model="showScheduleDialog" :title="editingSchedule ? '编辑定时配置' : '新建定时配置'" width="560px">
    <div class="evo-dialog-form">
      <div class="evo-form-row">
        <label class="evo-label">岗位ID</label>
        <el-input-number v-model="scheduleForm.postId" :min="1" style="width:100%" />
      </div>
      <div class="evo-inline-fields">
        <div class="evo-form-row" style="flex:1"><label class="evo-label">行业</label><el-input v-model="scheduleForm.industry" /></div>
        <div class="evo-form-row" style="flex:1"><label class="evo-label">业务领域</label><el-input v-model="scheduleForm.businessDomain" /></div>
      </div>
      <div class="evo-form-row">
        <label class="evo-label">执行频率</label>
        <el-input v-model="scheduleForm.cronExpression" placeholder="0 0 2 * * ?" />
        <span class="evo-hint">Cron 表达式，默认每天凌晨 2 点执行</span>
      </div>
      <div class="evo-switch-row">
        <el-switch v-model="scheduleForm.includeWhitepaper" :active-value="1" :inactive-value="0" active-text="权威材料" />
        <!-- 未配置时置灰；disabled 的 el-switch 吞点击，故在包裹层补提示 -->
        <el-tooltip :content="cloudKbHint" :disabled="cloudKbUsable" placement="top">
          <span class="evo-switch-guard" @click="warnCloudKbNotConfigured">
            <el-switch
              v-model="scheduleForm.includeCloudKnowledge"
              :active-value="1"
              :inactive-value="0"
              :disabled="!cloudKbUsable"
              active-text="云知识库"
            />
            <el-tag v-if="!cloudKbUsable" size="small" type="info" effect="plain">未配置</el-tag>
          </span>
        </el-tooltip>
        <el-switch v-model="scheduleForm.includeMarketJd" :active-value="1" :inactive-value="0" active-text="市场 JD" />
      </div>
      <div class="evo-hint">定时任务只生成待确认的变更建议，不会自动修改岗位能力模型。</div>
      <div class="evo-switch-row">
        <el-switch v-model="scheduleForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" />
      </div>
    </div>
    <template #footer>
      <el-button @click="showScheduleDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSaveSchedule">保存</el-button>
    </template>
  </el-dialog>
</template>
