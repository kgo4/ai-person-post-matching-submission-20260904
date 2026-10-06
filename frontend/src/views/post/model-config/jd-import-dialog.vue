<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, WarningFilled, CircleCheckFilled, Plus } from '@element-plus/icons-vue'
import { analyzeJd, confirmJdResult } from '@/api'
import type { JdAbilityItem, JdAnalyzeResponse } from '@/api'

const props = defineProps<{
  postId: number
  postName: string
}>()

const emit = defineEmits<{
  (e: 'success'): void
}>()

const visible = defineModel<boolean>('visible', { default: false })

// 步骤控制：1=输入JD, 2=预览结果
const step = ref(1)
const jdText = ref('')
const analyzing = ref(false)
const confirming = ref(false)
const analysisResult = ref<JdAnalyzeResponse | null>(null)

// 可编辑的能力项列表
interface EditableAbilityItem extends JdAbilityItem {
  _editing?: boolean
}
const editableItems = ref<EditableAbilityItem[]>([])

const categoryLabel: Record<string, string> = {
  TECHNICAL: '技术能力',
  SOFT: '软技能',
  BUSINESS: '业务能力',
}

const levelLabel: Record<number, string> = {
  1: '1-入门',
  2: '2-熟悉',
  3: '3-掌握',
  4: '4-精通',
  5: '5-专家',
}

const totalWeight = computed(() => {
  return editableItems.value.reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
})

function handleOpen() {
  step.value = 1
  jdText.value = ''
  analysisResult.value = null
  editableItems.value = []
}

async function handleAnalyze() {
  if (!jdText.value.trim()) {
    ElMessage.warning('请输入JD内容')
    return
  }
  analyzing.value = true
  try {
    const res = await analyzeJd(props.postId, jdText.value.trim())
    analysisResult.value = res.data
    if (res.data.analysisStatus === 2) {
      editableItems.value = res.data.abilities.map(a => ({ ...a }))
      step.value = 2
      ElMessage.success('AI分析完成，请检查结果')
    } else {
      ElMessage.error(res.data.errorMessage || 'AI分析失败')
    }
  } catch {
    // handled by interceptor
  } finally {
    analyzing.value = false
  }
}

function removeItem(index: number) {
  editableItems.value.splice(index, 1)
}

function addItem() {
  editableItems.value.push({
    suggestedName: '',
    tagCategory: 'TECHNICAL',
    minRequiredLevel: 2,
    weight: 10,
    isCore: 0,
    isRequired: 0,
    reasoning: '',
    matchStatus: 'NEW',
  })
}

function getMatchStatusTag(status: string) {
  switch (status) {
    case 'MATCHED': return { type: 'success', text: '已有标签' }
    case 'SIMILAR': return { type: 'warning', text: '疑似相似' }
    case 'NEW': return { type: 'info', text: '新标签' }
    default: return { type: 'info', text: status }
  }
}

async function handleConfirm() {
  if (editableItems.value.length === 0) {
    ElMessage.warning('至少需要一项能力要求')
    return
  }
  // 校验
  for (let i = 0; i < editableItems.value.length; i++) {
    const item = editableItems.value[i]
    if (!item.suggestedName?.trim()) {
      ElMessage.warning(`第${i + 1}项能力名称不能为空`)
      return
    }
    if (!item.weight || item.weight <= 0) {
      ElMessage.warning(`第${i + 1}项权重必须大于0`)
      return
    }
  }
  if (totalWeight.value < 95 || totalWeight.value > 105) {
    try {
      await ElMessageBox.confirm(
        `当前权重总和为 ${totalWeight.value.toFixed(1)}%，建议在95-105%之间。是否继续？`,
        '权重提示',
        { type: 'warning', confirmButtonText: '继续提交', cancelButtonText: '返回修改' }
      )
    } catch {
      return
    }
  }

  confirming.value = true
  try {
    await confirmJdResult(props.postId, editableItems.value)
    ElMessage.success('能力模型已更新')
    visible.value = false
    emit('success')
  } catch {
    // handled by interceptor
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="从JD智能分析能力项"
    width="900px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <!-- 步骤1：输入JD -->
    <div v-if="step === 1">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        <template #title>
          <span>将为岗位 <b>{{ postName }}</b> 分析JD，AI将自动提取所需能力项并与已有标签匹配</span>
        </template>
      </el-alert>

      <el-input
        v-model="jdText"
        type="textarea"
        :rows="12"
        placeholder="请粘贴岗位JD（Job Description）内容..."
        resize="vertical"
      />

      <div style="text-align: right; margin-top: 16px;">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="MagicStick"
          :loading="analyzing"
          :disabled="!jdText.trim()"
          @click="handleAnalyze"
        >
          {{ analyzing ? 'AI分析中...' : '开始分析' }}
        </el-button>
      </div>
    </div>

    <!-- 步骤2：预览分析结果 -->
    <div v-if="step === 2">
      <!-- 岗位摘要 -->
      <el-alert
        v-if="analysisResult?.jobSummary"
        type="success"
        :closable="false"
        style="margin-bottom: 16px;"
      >
        <template #title>
          <span>岗位摘要：{{ analysisResult.jobSummary }}</span>
        </template>
      </el-alert>

      <!-- 操作栏 -->
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <div>
          <el-button type="primary" :icon="Plus" size="small" @click="addItem">添加能力项</el-button>
          <span style="margin-left: 16px; color: #909399; font-size: 13px;">
            共 {{ editableItems.length }} 项 | 权重总和：
            <span :style="{ color: totalWeight >= 95 && totalWeight <= 105 ? '#67c23a' : '#f56c6c', fontWeight: 'bold' }">
              {{ totalWeight.toFixed(1) }}%
            </span>
          </span>
        </div>
        <el-button size="small" @click="step = 1">返回修改JD</el-button>
      </div>

      <!-- 结果表格 -->
      <el-table :data="editableItems" border stripe size="small" max-height="400">
        <el-table-column label="能力名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.suggestedName" size="small" placeholder="能力名称" />
          </template>
        </el-table-column>

        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-select v-model="row.tagCategory" size="small">
              <el-option label="技术能力" value="TECHNICAL" />
              <el-option label="软技能" value="SOFT" />
              <el-option label="业务能力" value="BUSINESS" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="等级" width="100" align="center">
          <template #default="{ row }">
            <el-select v-model="row.minRequiredLevel" size="small">
              <el-option v-for="l in 5" :key="l" :label="levelLabel[l]" :value="l" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="权重%" width="90" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="100" :step="5" size="small" controls-position="right" style="width: 70px;" />
          </template>
        </el-table-column>

        <el-table-column label="核心" width="65" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isCore" :active-value="1" :inactive-value="0" size="small" />
          </template>
        </el-table-column>

        <el-table-column label="必填" width="65" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.isRequired" :active-value="1" :inactive-value="0" size="small" />
          </template>
        </el-table-column>

        <el-table-column label="匹配状态" width="120" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.matchStatus === 'SIMILAR' && row.matchedTagName"
              :content="`相似标签：${row.matchedTagName}（相似度：${((row.similarityScore || 0) * 100).toFixed(0)}%）`"
              placement="top"
            >
              <el-tag :type="getMatchStatusTag(row.matchStatus).type" size="small" style="cursor: pointer;">
                <el-icon v-if="row.matchStatus === 'SIMILAR'" style="margin-right: 2px;"><WarningFilled /></el-icon>
                <el-icon v-else-if="row.matchStatus === 'MATCHED'" style="margin-right: 2px;"><CircleCheckFilled /></el-icon>
                {{ getMatchStatusTag(row.matchStatus).text }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="getMatchStatusTag(row.matchStatus).type" size="small">
              <el-icon v-if="row.matchStatus === 'MATCHED'" style="margin-right: 2px;"><CircleCheckFilled /></el-icon>
              {{ getMatchStatusTag(row.matchStatus).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="AI依据" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-size: 12px; color: #909399;">{{ row.reasoning }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="60" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" text size="small" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部按钮 -->
      <div style="text-align: right; margin-top: 16px;">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="confirming"
          :disabled="editableItems.length === 0"
          @click="handleConfirm"
        >
          确认应用（{{ editableItems.length }}项）
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>
