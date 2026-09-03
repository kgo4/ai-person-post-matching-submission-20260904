<template>
  <div class="learning-resources-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>学习资源管理</span>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>新建资源
          </el-button>
        </div>
      </template>

      <!-- 搜索区 -->
      <el-form inline class="search-bar" @submit.prevent>
        <el-form-item label="能力名称">
          <el-input v-model="searchForm.abilityName" placeholder="模糊搜索" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="标题 / 描述 / 能力名" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="searchForm.resourceType" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="opt in resourceTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="searchForm.platform" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="opt in platformOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="view-switch">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="list"><el-icon><List /></el-icon></el-radio-button>
            <el-radio-button value="card"><el-icon><Grid /></el-icon></el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <!-- 批量操作工具栏 -->
      <div class="batch-bar">
        <span class="batch-tip">已选 {{ selectedIds.length }} 项</span>
        <el-button size="small" :disabled="!selectedIds.length" @click="batchUpdateStatus(1)">批量启用</el-button>
        <el-button size="small" :disabled="!selectedIds.length" @click="batchUpdateStatus(0)">批量禁用</el-button>
        <el-popconfirm title="确定删除选中的资源？" width="220" @confirm="batchDelete">
          <template #reference>
            <el-button size="small" type="danger" :disabled="!selectedIds.length">批量删除</el-button>
          </template>
        </el-popconfirm>
      </div>

      <!-- 列表视图 -->
      <el-table v-if="viewMode === 'list'" :data="resources" v-loading="loading" stripe
        empty-text="暂无学习资源，点击右上角「新建资源」添加"
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="abilityName" label="关联能力" width="130" show-overflow-tooltip />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.platform" size="small" :type="platformTagType(row.platform)">
              {{ platformLabel(row.platform) }}
            </el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resourceType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ resourceTypeLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficultyLevel" label="难度" width="120" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.difficultyLevel" disabled :max="5" />
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="90" show-overflow-tooltip />
        <el-table-column prop="url" label="链接" width="70">
          <template #default="{ row }">
            <el-link v-if="row.url" :href="row.url" target="_blank" type="primary">打开</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该资源？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 卡片视图 -->
      <div v-else-if="viewMode === 'card'" v-loading="loading" class="resource-grid">
        <el-empty v-if="!loading && !resources.length" description="暂无学习资源，点击右上角「新建资源」添加" />
        <div v-for="item in resources" :key="item.id" class="resource-card">
          <div class="card-cover" :class="{ disabled: item.status !== 1 }">
            <img v-if="item.coverImageUrl" :src="item.coverImageUrl" :alt="item.title" loading="lazy" />
            <div v-else class="cover-placeholder">{{ resourceTypeLabel(item.resourceType) }}</div>
            <div v-if="item.status !== 1" class="card-status-badge">已禁用</div>
          </div>
          <div class="card-body">
            <div class="card-title" :title="item.title">{{ item.title }}</div>
            <div class="card-meta">
              <el-tag v-if="item.abilityName" size="small" type="primary">{{ item.abilityName }}</el-tag>
              <el-tag v-if="item.platform" size="small" :type="platformTagType(item.platform)">{{ platformLabel(item.platform) }}</el-tag>
              <el-tag size="small" type="info">{{ resourceTypeLabel(item.resourceType) }}</el-tag>
            </div>
            <div class="card-sub">
              <el-rate :model-value="item.difficultyLevel" disabled :max="5" />
              <span v-if="item.duration" class="card-duration">{{ item.duration }}</span>
            </div>
            <div v-if="item.description" class="card-desc" :title="item.description">{{ item.description }}</div>
            <div class="card-actions">
              <el-link v-if="item.url" :href="item.url" target="_blank" type="primary">打开资源</el-link>
              <el-button link type="primary" size="small" @click="openEditDialog(item)">编辑</el-button>
              <el-popconfirm title="确定删除该资源？" @confirm="handleDelete(item.id)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
              <el-switch :model-value="item.status === 1" @change="toggleStatus(item, $event)" />
            </div>
          </div>
        </div>
      </div>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[16, 32, 48, 80]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadResources"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 新建/编辑资源对话框 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑学习资源' : '新建学习资源'" width="650px">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="能力名称" prop="abilityName">
              <el-input v-model="formData.abilityName" placeholder="如：Java并发编程" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能力标签">
              <el-tree-select
                v-model="formData.tagId"
                :data="tagTree"
                :props="{ label: 'tagName', children: 'children', value: 'id' }"
                placeholder="选择能力标签（可选）" clearable filterable check-strictly style="width: 100%"
                @change="onTagChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="isEdit && editingCode" label="资源编码">
          <el-input :model-value="editingCode" disabled />
        </el-form-item>
        <el-form-item label="资源标题" prop="title">
          <el-input v-model="formData.title" placeholder="如：Java并发编程实战（慕课网）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资源类型">
              <el-select v-model="formData.resourceType" style="width: 100%">
                <el-option v-for="opt in resourceTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度等级">
              <el-rate v-model="formData.difficultyLevel" :max="5" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="平台">
              <el-select v-model="formData.platform" clearable placeholder="选择平台" style="width: 100%">
                <el-option v-for="opt in platformOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学习时长">
              <el-input v-model="formData.duration" placeholder="如：约8小时" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="资源链接" prop="url">
          <el-input v-model="formData.url" placeholder="https://www.imooc.com/..." />
        </el-form-item>
        <el-form-item label="封面图">
          <div class="cover-field">
            <el-upload
              class="cover-uploader"
              :show-file-list="false"
              :auto-upload="false"
              :disabled="uploadingCover"
              accept="image/jpeg,image/png,image/gif,image/webp"
              :on-change="handleCoverChange"
            >
              <div v-if="formData.coverImageUrl" class="cover-preview">
                <img :src="formData.coverImageUrl" alt="封面预览" />
                <div class="cover-preview-mask">
                  <el-icon><Refresh /></el-icon>
                  <span>点击更换</span>
                </div>
              </div>
              <div v-else class="cover-upload-placeholder">
                <el-icon class="is-loading" v-if="uploadingCover"><Loading /></el-icon>
                <template v-else>
                  <el-icon><Plus /></el-icon>
                  <span>本地上传</span>
                </template>
              </div>
            </el-upload>
            <div class="cover-url-row">
              <el-input v-model="formData.coverImageUrl" placeholder="或粘贴图片链接（http/https），二选一" clearable />
              <el-button v-if="formData.coverImageUrl" link type="danger" @click="formData.coverImageUrl = ''">清除</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" placeholder="越小越靠前" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="资源简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { List, Grid, Plus, Refresh, Loading } from '@element-plus/icons-vue'
import {
  pageLearningResources,
  saveLearningResource,
  deleteLearningResource,
  updateLearningResourceStatus,
  batchUpdateLearningResourceStatus,
  batchDeleteLearningResources,
  uploadResourceCover,
} from '@/api/learning'
import { getTagTree } from '@/api/system'
import type { AbilityTagTreeVO } from '@/api/system/types'
import type { LearningResource, LearningResourceSaveDTO } from '@/api/learning'

const loading = ref(false)
const saving = ref(false)
const resources = ref<LearningResource[]>([])
const showDialog = ref(false)
const isEdit = ref(false)
const editingCode = ref('')
const viewMode = ref<'list' | 'card'>(localStorage.getItem('learning-resource-view-mode') === 'list' ? 'list' : 'card')
// 默认图封面模式（card），用户切换后持久化到 localStorage
watch(viewMode, (v) => {
  localStorage.setItem('learning-resource-view-mode', v)
})
const uploadingCover = ref(false)
const selectedIds = ref<number[]>([])
const tagTree = ref<AbilityTagTreeVO[]>([])
const formRef = ref<FormInstance>()

const resourceTypeOptions = [
  { label: '课程', value: 'COURSE' },
  { label: '视频', value: 'VIDEO' },
  { label: '文档', value: 'DOC' },
  { label: '练习', value: 'PRACTICE' },
  { label: '项目', value: 'PROJECT' },
  { label: '书籍', value: 'BOOK' },
]

const platformOptions = [
  { label: '慕课网', value: 'MOOC' },
  { label: 'B站', value: 'BILIBILI' },
  { label: 'YouTube', value: 'YOUTUBE' },
  { label: 'GitHub', value: 'GITHUB' },
  { label: 'CSDN', value: 'CSDN' },
  { label: '其他', value: 'OTHER' },
]

const searchForm = reactive({
  abilityName: '',
  keyword: '',
  resourceType: '',
  platform: '',
  status: undefined as number | undefined,
})

const pagination = reactive({
  current: 1,
  size: 16,
  total: 0,
})

const rules: FormRules = {
  abilityName: [{ required: true, message: '请输入能力名称', trigger: 'blur' }],
  title: [{ required: true, message: '请输入资源标题', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入资源链接', trigger: 'blur' },
    { type: 'url', message: '请输入合法的链接地址（含 http/https）', trigger: 'blur' },
  ],
}

const defaultForm = (): LearningResourceSaveDTO => ({
  abilityName: '',
  tagId: undefined,
  title: '',
  resourceType: 'COURSE',
  difficultyLevel: 1,
  url: '',
  description: '',
  platform: '',
  platformIcon: '',
  coverImageUrl: '',
  duration: '',
  sortOrder: 0,
})

const formData = reactive<LearningResourceSaveDTO>(defaultForm())

function platformLabel(p: string) {
  const map: Record<string, string> = { MOOC: '慕课', BILIBILI: 'B站', YOUTUBE: 'YouTube', GITHUB: 'GitHub', CSDN: 'CSDN', OTHER: '其他' }
  return map[p] || p
}

function platformTagType(p: string) {
  const map: Record<string, string> = { MOOC: 'success', BILIBILI: 'primary', YOUTUBE: 'danger', GITHUB: 'info', CSDN: 'warning' }
  return map[p] || ''
}

function resourceTypeLabel(t: string) {
  const map: Record<string, string> = { COURSE: '课程', VIDEO: '视频', DOC: '文档', PRACTICE: '练习', PROJECT: '项目', BOOK: '书籍' }
  return map[t] || t
}

const loadResources = async () => {
  loading.value = true
  try {
    const res = await pageLearningResources({
      current: pagination.current,
      size: pagination.size,
      abilityName: searchForm.abilityName || undefined,
      keyword: searchForm.keyword || undefined,
      resourceType: searchForm.resourceType || undefined,
      platform: searchForm.platform || undefined,
      status: searchForm.status,
    })
    resources.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const loadTagTree = async () => {
  try {
    const res = await getTagTree()
    tagTree.value = res.data || []
  } catch {
    tagTree.value = []
  }
}

function findTagName(tree: AbilityTagTreeVO[], id: number | undefined): string | undefined {
  if (id == null) return undefined
  for (const node of tree) {
    if (node.id === id) return node.tagName
    if (node.children?.length) {
      const hit = findTagName(node.children, id)
      if (hit) return hit
    }
  }
  return undefined
}

function onTagChange(tagId: number | undefined) {
  const name = findTagName(tagTree.value, tagId)
  if (name) formData.abilityName = name
}

/** 本地上传封面：校验图片类型后上传，成功回填 coverImageUrl */
const handleCoverChange = async (uploadFile: any) => {
  const raw: File | undefined = uploadFile?.raw
  if (!raw) return
  if (!/^image\/(jpeg|png|gif|webp)$/.test(raw.type)) {
    ElMessage.error('仅支持 JPG/PNG/GIF/WebP 格式的封面图片')
    return
  }
  uploadingCover.value = true
  try {
    const res = await uploadResourceCover(raw)
    formData.coverImageUrl = res.data
    ElMessage.success('封面上传成功')
  } finally {
    uploadingCover.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadResources()
}

function handleReset() {
  Object.assign(searchForm, { abilityName: '', keyword: '', resourceType: '', platform: '', status: undefined })
  handleSearch()
}

function handleSizeChange() {
  // 修改每页条数后回到第一页，避免页码越界导致空列表
  pagination.current = 1
  loadResources()
}

function openCreateDialog() {
  isEdit.value = false
  editingCode.value = ''
  Object.assign(formData, defaultForm())
  formRef.value?.clearValidate()
  showDialog.value = true
}

function openEditDialog(row: LearningResource) {
  isEdit.value = true
  editingCode.value = row.resourceCode || ''
  Object.assign(formData, {
    id: row.id,
    abilityName: row.abilityName,
    tagId: row.tagId,
    title: row.title,
    resourceType: row.resourceType,
    difficultyLevel: row.difficultyLevel,
    url: row.url,
    description: row.description,
    platform: row.platform,
    platformIcon: row.platformIcon,
    coverImageUrl: row.coverImageUrl,
    duration: row.duration,
    sortOrder: row.sortOrder,
  })
  formRef.value?.clearValidate()
  showDialog.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await saveLearningResource(formData)
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    showDialog.value = false
    loadResources()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  await deleteLearningResource(id)
  ElMessage.success('删除成功')
  // 删除的是当前页最后一条且不在第一页时回退一页
  if (resources.value.length === 1 && pagination.current > 1) {
    pagination.current -= 1
  }
  loadResources()
}

const handleSelectionChange = (rows: any[]) => {
  selectedIds.value = rows.map((r) => r.id)
}

const toggleStatus = async (row: LearningResource, val: boolean | string | number) => {
  const status = val ? 1 : 0
  try {
    await updateLearningResourceStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    // 失败时 switch 保持原值（受控 model-value）
  }
}

const batchUpdateStatus = async (status: number) => {
  if (!selectedIds.value.length) return
  await batchUpdateLearningResourceStatus(selectedIds.value, status)
  ElMessage.success(status === 1 ? '批量启用成功' : '批量禁用成功')
  loadResources()
}

const batchDelete = async () => {
  if (!selectedIds.value.length) return
  await batchDeleteLearningResources(selectedIds.value)
  ElMessage.success('批量删除成功')
  // 全选删除当前页且不在第一页时回退一页
  if (resources.value.length === selectedIds.value.length && pagination.current > 1) {
    pagination.current -= 1
  }
  loadResources()
}

onMounted(() => {
  loadResources()
  loadTagTree()
})
</script>

<style scoped>
.learning-resources-page {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 4px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.search-bar .view-switch {
  margin-left: auto;
  margin-right: 0;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.batch-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-right: 4px;
}

/* 卡片视图 */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.resource-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
  transition: box-shadow 0.2s;
}

.resource-card:hover {
  box-shadow: var(--el-box-shadow-light);
}

.card-cover {
  position: relative;
  height: 120px;
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card-cover.disabled img {
  filter: grayscale(0.8);
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-color-primary-light-3);
}

.card-status-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.card-body {
  padding: 12px;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  line-height: 1.4;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.card-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-duration {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
}

.card-actions .el-switch {
  margin-left: auto;
}

.cover-url-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.cover-field {
  width: 100%;
}

.cover-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s;
}

.cover-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.cover-upload-placeholder {
  width: 220px;
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  background: var(--el-fill-color-light);
}

.cover-preview {
  position: relative;
  width: 220px;
  height: 120px;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-preview-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.cover-preview:hover .cover-preview-mask {
  opacity: 1;
}

.text-gray-400 {
  color: #9ca3af;
}
</style>
