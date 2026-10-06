<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DocumentCopy,
  View,
  Delete,
  Check,
  RefreshLeft,
  Plus,
} from '@element-plus/icons-vue'
import {
  listEnabledPosts,
  listVersions,
  getVersionItems,
  publishVersion,
  rollbackVersion,
  deleteDraftVersion,
  normalizeWeights,
  getUnmatchedAbilities,
  bindUnmatchedAbility,
  ignoreUnmatchedAbility,
} from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

// 岗位选择
const postList = ref<any[]>([])
const selectedPostId = ref<number | undefined>(
  route.query.postId ? Number(route.query.postId) : undefined
)

// 版本列表
const versions = ref<any[]>([])
const selectedVersion = ref<any>(null)
const versionItems = ref<any[]>([])

// 未匹配能力（M-07）
const unmatchedAbilities = ref<any[]>([])
const unmatchedLoading = ref(false)

// 来源类型映射
const sourceTypeMap: Record<string, string> = {
  TEMPLATE: '原型生成',
  JD_AI: 'JD智能生成',
  EXCEL: 'Excel导入',
  COPY: '复制',
  MANUAL: '手动配置',
  FEEDBACK: '反馈优化',
}

// 状态映射
const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  ACTIVE: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'warning' },
  REVIEW_REQUIRED: { label: '待审核', type: 'warning' },
}

// 未匹配原因映射
const unmatchedReasonMap: Record<string, string> = {
  MATCHED_TAG_ID_NOT_FOUND: '匹配标签ID无效',
  TAG_NAME_NOT_FOUND: '标签名称不存在',
  TAG_DISABLED: '标签已禁用',
  TAG_NAME_AMBIGUOUS: '标签名称存在歧义',
}

// 未匹配记录状态映射
const unmatchedStatusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '待处理', type: 'warning' },
  TAG_BOUND: { label: '已绑定', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info' },
}

onMounted(async () => {
  await loadPostList()
  if (selectedPostId.value) {
    await loadVersions()
  }
})

async function loadPostList() {
  try {
    const r = await listEnabledPosts()
    postList.value = r.data || []
  } catch {
    postList.value = []
  }
}

async function loadVersions() {
  if (!selectedPostId.value) {
    versions.value = []
    return
  }

  loading.value = true
  try {
    const r = await listVersions(selectedPostId.value)
    versions.value = r.data || []
    // 自动选中已发布的版本
    const activeVersion = versions.value.find((v: any) => v.status === 'ACTIVE')
    if (activeVersion) {
      await selectVersion(activeVersion)
    }
  } catch {
    versions.value = []
  } finally {
    loading.value = false
  }
}

async function selectVersion(version: any) {
  selectedVersion.value = version
  try {
    const r = await getVersionItems(version.id)
    versionItems.value = r.data || []
  } catch {
    versionItems.value = []
  }
  await loadUnmatchedAbilities(version.id)
}

async function loadUnmatchedAbilities(versionId: number) {
  unmatchedLoading.value = true
  try {
    const r = await getUnmatchedAbilities(versionId)
    unmatchedAbilities.value = r.data || []
  } catch {
    unmatchedAbilities.value = []
  } finally {
    unmatchedLoading.value = false
  }
}

async function handleBindUnmatched(record: any) {
  try {
    const { value } = await ElMessageBox.prompt(
      `绑定能力「${record.abilityName}」到已有标签，请输入正式标签ID`,
      '绑定已有标签',
      {
        confirmButtonText: '绑定',
        cancelButtonText: '取消',
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入有效的标签ID',
        inputPlaceholder: '例如：123',
      }
    )
    await bindUnmatchedAbility(selectedVersion.value.id, record.id, Number(value))
    ElMessage.success('绑定成功，已生成版本明细')
    await Promise.all([loadUnmatchedAbilities(selectedVersion.value.id), loadVersions(), selectVersion(selectedVersion.value)])
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '绑定失败')
    }
  }
}

async function handleIgnoreUnmatched(record: any) {
  try {
    await ElMessageBox.confirm(
      `确认忽略能力「${record.abilityName}」？忽略后不可恢复绑定操作。`,
      '确认忽略',
      { type: 'warning' }
    )
    await ignoreUnmatchedAbility(selectedVersion.value.id, record.id)
    ElMessage.success('已忽略')
    await loadUnmatchedAbilities(selectedVersion.value.id)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

async function handlePublish(versionId: number) {
  try {
    await ElMessageBox.confirm(
      '发布后将替换当前生效的能力模型，确认发布？',
      '确认发布',
      { type: 'warning' }
    )

    loading.value = true
    await publishVersion(versionId)
    ElMessage.success('版本已发布')
    await loadVersions()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '发布失败')
    }
  } finally {
    loading.value = false
  }
}

async function handleRollback(versionId: number) {
  try {
    await ElMessageBox.confirm(
      '回滚将重新发布该版本的能力模型，确认回滚？',
      '确认回滚',
      { type: 'warning' }
    )

    loading.value = true
    await rollbackVersion(versionId)
    ElMessage.success('已回滚到该版本')
    await loadVersions()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '回滚失败')
    }
  } finally {
    loading.value = false
  }
}

async function handleDelete(versionId: number) {
  try {
    await ElMessageBox.confirm(
      '删除后不可恢复，确认删除？',
      '确认删除',
      { type: 'warning' }
    )

    loading.value = true
    await deleteDraftVersion(versionId)
    ElMessage.success('草稿已删除')
    if (selectedVersion.value?.id === versionId) {
      selectedVersion.value = null
      versionItems.value = []
    }
    await loadVersions()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  } finally {
    loading.value = false
  }
}

async function handleNormalize() {
  if (!selectedPostId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }

  try {
    await ElMessageBox.confirm(
      '将当前生效的能力模型权重按比例归一化到100%，确认？',
      '一键归一化',
      { type: 'info' }
    )

    loading.value = true
    await normalizeWeights(selectedPostId.value)
    ElMessage.success('权重已归一化到100%')
    await loadVersions()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '归一化失败')
    }
  } finally {
    loading.value = false
  }
}

function goToModelConfig() {
  router.push('/post/model-config')
}
</script>

<template>
  <div class="model-version-container">
    <el-card class="header-card">
      <template #header>
        <div class="card-header">
          <el-icon><DocumentCopy /></el-icon>
          <span>模型发布记录</span>
        </div>
      </template>

      <el-row :gutter="20" align="middle">
        <el-col :span="12">
          <el-select
            v-model="selectedPostId"
            placeholder="请选择岗位"
            filterable
            style="width: 100%"
            @change="loadVersions"
          >
            <el-option
              v-for="post in postList"
              :key="post.id"
              :label="post.postName"
              :value="post.id"
            />
          </el-select>
        </el-col>
        <el-col :span="12">
          <el-button type="primary" @click="goToModelConfig">
            <el-icon><Plus /></el-icon>
            前往岗位能力配置
          </el-button>
          <el-button @click="handleNormalize" :disabled="!selectedPostId">
            <el-icon><RefreshLeft /></el-icon>
            一键归一化
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="20" class="main-content">
      <!-- 左侧：版本列表 -->
      <el-col :span="8">
        <el-card v-loading="loading">
          <template #header>
            <span>版本列表</span>
          </template>

          <div v-if="versions.length === 0" class="empty-tip">
            暂无版本记录
          </div>

          <div
            v-for="version in versions"
            :key="version.id"
            class="version-item"
            :class="{ active: selectedVersion?.id === version.id }"
            @click="selectVersion(version)"
          >
            <div class="version-header">
              <span class="version-no">{{ version.versionNo }}</span>
              <el-tag :type="(statusMap[version.status]?.type as any) || 'info'" size="small">
                {{ statusMap[version.status]?.label || version.status }}
              </el-tag>
            </div>
            <div class="version-info">
              <span>来源：{{ sourceTypeMap[version.sourceType] || version.sourceType }}</span>
              <span>项数：{{ version.itemCount }}</span>
            </div>
            <div class="version-actions">
              <el-button
                v-if="version.status === 'DRAFT'"
                type="success"
                size="small"
                @click.stop="handlePublish(version.id)"
              >
                发布
              </el-button>
              <el-button
                v-if="version.status === 'ARCHIVED'"
                type="warning"
                size="small"
                @click.stop="handleRollback(version.id)"
              >
                回滚
              </el-button>
              <el-button
                v-if="version.status === 'DRAFT'"
                type="danger"
                size="small"
                @click.stop="handleDelete(version.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：版本明细 -->
      <el-col :span="16">
        <el-card v-loading="loading">
          <template #header>
            <span>
              版本明细
              <span v-if="selectedVersion" class="version-badge">
                {{ selectedVersion.versionNo }}
              </span>
            </span>
          </template>

          <div v-if="!selectedVersion" class="empty-tip">
            请选择一个版本查看明细
          </div>

          <template v-else>
            <el-descriptions :column="3" border class="version-detail">
              <el-descriptions-item label="版本号">
                {{ selectedVersion.versionNo }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="(statusMap[selectedVersion.status]?.type as any) || 'info'">
                  {{ statusMap[selectedVersion.status]?.label || selectedVersion.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="来源">
                {{ sourceTypeMap[selectedVersion.sourceType] || selectedVersion.sourceType }}
              </el-descriptions-item>
              <el-descriptions-item label="能力项数">
                {{ selectedVersion.itemCount }}
              </el-descriptions-item>
              <el-descriptions-item label="权重总和">
                {{ selectedVersion.totalWeight }}
              </el-descriptions-item>
              <el-descriptions-item label="质量评分">
                {{ selectedVersion.qualityScore ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="说明" :span="3">
                {{ selectedVersion.description || '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-table :data="versionItems" border stripe style="margin-top: 20px">
              <el-table-column prop="tagId" label="标签ID" width="80" />
              <el-table-column prop="minRequiredLevel" label="最低等级" width="100">
                <template #default="{ row }">
                  {{ row.minRequiredLevel }}级
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="100">
                <template #default="{ row }">
                  {{ row.weight }}%
                </template>
              </el-table-column>
              <el-table-column prop="isCore" label="核心项" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.isCore ? 'danger' : 'info'" size="small">
                    {{ row.isCore ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="isRequired" label="必填" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.isRequired ? 'warning' : 'info'" size="small">
                    {{ row.isRequired ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="配置理由" min-width="200" show-overflow-tooltip />
            </el-table>

            <!-- 未匹配能力（M-07） -->
            <div class="unmatched-section">
              <div class="unmatched-header">
                <span>未匹配能力（AI 提取但未匹配已有标签）</span>
                <el-tag v-if="unmatchedAbilities.length > 0" size="small" type="warning">
                  {{ unmatchedAbilities.length }} 项待处理
                </el-tag>
              </div>
              <el-table
                v-loading="unmatchedLoading"
                :data="unmatchedAbilities"
                border
                stripe
                empty-text="暂无未匹配能力"
              >
                <el-table-column prop="abilityName" label="能力名称" min-width="140" show-overflow-tooltip />
                <el-table-column prop="reason" label="未匹配原因" width="150">
                  <template #default="{ row }">
                    {{ unmatchedReasonMap[row.reason] || row.reason }}
                  </template>
                </el-table-column>
                <el-table-column prop="minRequiredLevel" label="建议等级" width="90">
                  <template #default="{ row }">
                    {{ row.minRequiredLevel ?? '-' }}级
                  </template>
                </el-table-column>
                <el-table-column prop="weight" label="建议权重" width="90">
                  <template #default="{ row }">
                    {{ row.weight ?? '-' }}%
                  </template>
                </el-table-column>
                <el-table-column label="必需/核心" width="110">
                  <template #default="{ row }">
                    <el-tag v-if="row.isRequired" size="small" type="warning">必需</el-tag>
                    <el-tag v-if="row.isCore" size="small" type="danger" style="margin-left: 4px">核心</el-tag>
                    <span v-if="!row.isRequired && !row.isCore">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="(unmatchedStatusMap[row.status]?.type as any) || 'info'" size="small">
                      {{ unmatchedStatusMap[row.status]?.label || row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="recommendedTagName" label="已绑定标签" width="120">
                  <template #default="{ row }">
                    <span v-if="row.boundTagId">{{ row.recommendedTagName || ('#' + row.boundTagId) }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="reasoning" label="AI 推理说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="170" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.status === 'PENDING'">
                      <el-button type="primary" size="small" link @click="handleBindUnmatched(row)">
                        绑定已有标签
                      </el-button>
                      <el-button type="info" size="small" link @click="handleIgnoreUnmatched(row)">
                        忽略
                      </el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.model-version-container {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
}

.main-content {
  margin-top: 20px;
}

.version-item {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.version-item:hover {
  border-color: #409eff;
  background-color: #f5f7fa;
}

.version-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.version-no {
  font-weight: bold;
  color: #303133;
}

.version-info {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.version-actions {
  display: flex;
  gap: 8px;
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.version-badge {
  margin-left: 8px;
  font-size: 14px;
  color: #409eff;
}

.version-detail {
  margin-bottom: 20px;
}

.unmatched-section {
  margin-top: 24px;
}

.unmatched-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
}
</style>

