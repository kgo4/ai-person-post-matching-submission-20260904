<template>
  <el-drawer
    v-model="visible"
    title="来源详情"
    size="480px"
    :before-close="handleClose"
  >
    <div v-loading="loading" class="source-ref-detail">
      <template v-if="sourceRef">
        <!-- 基本信息 -->
        <section class="detail-section">
          <h4 class="section-title">基本信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="来源引用">
              <el-tag size="small" type="info">{{ sourceRef.ref }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="来源类型">
              <el-tag size="small">{{ sourceRef.sourceType || sourceRef.refType }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="标题">
              {{ sourceRef.title || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 评分信息 -->
        <section class="detail-section">
          <h4 class="section-title">评分信息</h4>
          <div class="score-grid">
            <div class="score-item">
              <span class="score-label">置信度</span>
              <el-progress
                :percentage="sourceRef.confidenceScore || 0"
                :color="getScoreColor(sourceRef.confidenceScore)"
                :stroke-width="8"
              />
            </div>
            <div class="score-item">
              <span class="score-label">可信度</span>
              <el-progress
                :percentage="sourceRef.credibilityScore || 0"
                :color="getScoreColor(sourceRef.credibilityScore)"
                :stroke-width="8"
              />
            </div>
          </div>
        </section>

        <!-- 审核状态 -->
        <section v-if="sourceRef.reviewStatus" class="detail-section">
          <h4 class="section-title">审核状态</h4>
          <el-tag :type="getReviewStatusType(sourceRef.reviewStatus)" effect="plain">
            {{ getReviewStatusLabel(sourceRef.reviewStatus) }}
          </el-tag>
        </section>

        <!-- 内容摘要 -->
        <section v-if="sourceRef.snippet" class="detail-section">
          <h4 class="section-title">内容摘要</h4>
          <div class="snippet-content">
            {{ sourceRef.snippet }}
          </div>
        </section>

        <!-- 操作按钮 -->
        <section class="detail-actions">
          <el-button type="primary" @click="navigateToSource">
            查看原始详情
          </el-button>
          <el-button @click="handleClose">关闭</el-button>
        </section>
      </template>

      <el-empty v-else-if="!loading" description="暂无来源数据" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSourceRefDetail } from '@/api/ai-context'
import type { AiContextSourceRef } from '@/api/ai-context'

const props = defineProps<{
  refValue?: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const router = useRouter()
const loading = ref(false)
const sourceRef = ref<AiContextSourceRef | null>(null)

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.refValue) {
    loadSourceRef()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

watch(() => props.refValue, (val) => {
  if (val && visible.value) {
    loadSourceRef()
  }
})

async function loadSourceRef() {
  if (!props.refValue) return

  loading.value = true
  try {
    const res = await getSourceRefDetail(props.refValue)
    sourceRef.value = res.data
  } catch (error) {
    ElMessage.error('加载来源详情失败')
    sourceRef.value = null
  } finally {
    loading.value = false
  }
}

function handleClose() {
  visible.value = false
  sourceRef.value = null
}

function getScoreColor(score?: number): string {
  if (!score) return '#909399'
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

function getReviewStatusType(status?: string): string {
  switch (status) {
    case 'ACCEPTED':
    case 'AUTO_PASSED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    case 'PENDING':
      return 'warning'
    default:
      return 'info'
  }
}

function getReviewStatusLabel(status?: string): string {
  switch (status) {
    case 'ACCEPTED':
      return '已采纳'
    case 'REJECTED':
      return '已驳回'
    case 'RESOLVED':
      return '已处理'
    case 'AUTO_PASSED':
      return '自动通过'
    case 'PENDING':
      return '待处理'
    default:
      return status || '-'
  }
}

function navigateToSource() {
  if (!sourceRef.value) return

  const { refType, refId } = sourceRef.value
  switch (refType) {
    case 'EMP_ABILITY':
      router.push(`/employee/detail/${refId}`)
      break
    case 'POST_ABILITY_MODEL':
      router.push(`/post/detail/${refId}`)
      break
    case 'MATCHING_RECORD':
      router.push(`/matching/detail/${refId}`)
      break
    case 'CONTEST_EVIDENCE':
      router.push('/capability-brain/evidence')
      break
    case 'LEARNING_SUBMISSION':
      router.push(`/learning/path/${refId}`)
      break
    default:
      ElMessage.info('暂不支持跳转到该来源类型')
  }
  handleClose()
}
</script>

<style scoped>
.source-ref-detail {
  padding: 16px;
}

.detail-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.score-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.score-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.score-label {
  font-size: 12px;
  color: #909399;
}

.snippet-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  max-height: 200px;
  overflow-y: auto;
}

.detail-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
