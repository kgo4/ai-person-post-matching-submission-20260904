<template>
  <div class="prototype-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>岗位能力模板素材</span>
          <el-button type="primary" @click="showCreateDialog">新增素材</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" style="margin-bottom: 16px">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="搜索素材名称" clearable />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="searchForm.industry" placeholder="行业" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="searchForm.category" placeholder="分类" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="prototypeName" label="素材名称" min-width="150" />
        <el-table-column prop="industry" label="行业" width="100" />
        <el-table-column prop="category" label="分类" width="80" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="showEditDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑素材' : '新增素材'" width="700px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="素材名称" required>
          <el-input v-model="formData.prototypeName" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="formData.industry" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="formData.category" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-divider content-position="left">能力标签模板</el-divider>
        <div v-for="(tag, index) in formData.tags" :key="index" style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center">
          <el-input v-model="tag.tagId" placeholder="标签ID" style="width: 100px" />
          <el-input-number v-model="tag.weight" :min="0" :max="100" placeholder="权重" style="width: 120px" />
          <el-input-number v-model="tag.minRequiredLevel" :min="1" :max="5" placeholder="等级" style="width: 120px" />
          <el-checkbox v-model="tag.isCore" :true-value="1" :false-value="0">核心</el-checkbox>
          <el-checkbox v-model="tag.isRequired" :true-value="1" :false-value="0">必备</el-checkbox>
          <el-button link type="danger" @click="formData.tags.splice(index, 1)">删除</el-button>
        </div>
        <el-button @click="formData.tags.push({ tagId: 0, weight: 10, minRequiredLevel: 2, isCore: 0, isRequired: 0, sortOrder: formData.tags.length })">添加标签</el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="素材详情" width="700px">
      <div v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="素材名称">{{ detailData.prototypeName }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ detailData.industry }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detailData.category }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailData.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detailData.description }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px">能力标签模板</h4>
        <el-table :data="detailData.tags" border>
          <el-table-column prop="tagName" label="标签名称" />
          <el-table-column prop="tagCategory" label="分类" width="90" />
          <el-table-column prop="weight" label="权重" width="70" />
          <el-table-column prop="minRequiredLevel" label="等级" width="70" />
          <el-table-column label="核心" width="60">
            <template #default="{ row }">
              <el-tag v-if="row.isCore" type="danger" size="small">核心</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="必备" width="60">
            <template #default="{ row }">
              <el-tag v-if="row.isRequired" type="warning" size="small">必备</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePrototypes, getPrototype, savePrototype, deletePrototype } from '@/api/post-prototype'
import type { PostPrototypeVO, PostPrototypeSaveDTO } from '@/api/post-prototype'

type PrototypeFormData = PostPrototypeSaveDTO & { tags: NonNullable<PostPrototypeSaveDTO['tags']> }

const loading = ref(false)
const saving = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailData = ref<PostPrototypeVO | null>(null)

const searchForm = ref({ keyword: '', industry: '', category: '' })
const pagination = ref({ pageNum: 1, pageSize: 10, total: 0 })

const formData = ref<PrototypeFormData>({
  prototypeName: '',
  industry: '',
  category: '',
  description: '',
  status: 1,
  tags: []
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await pagePrototypes({
      pageNum: pagination.value.pageNum,
      pageSize: pagination.value.pageSize,
      ...searchForm.value
    })
    const data = res.data as any
    tableData.value = data.records
    pagination.value.total = data.total
  } catch (e: any) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  editingId.value = null
  formData.value = { prototypeName: '', industry: '', category: '', description: '', status: 1, tags: [] }
  dialogVisible.value = true
}

const showEditDialog = async (row: any) => {
  editingId.value = row.id
  try {
    const res = await getPrototype(row.id)
    const detail = res.data
    formData.value = {
      id: detail.id,
      prototypeName: detail.prototypeName,
      industry: detail.industry,
      category: detail.category,
      description: detail.description,
      status: detail.status,
      tags: detail.tags?.map(t => ({
        tagId: t.tagId,
        weight: t.weight,
        minRequiredLevel: t.minRequiredLevel,
        isCore: t.isCore,
        isRequired: t.isRequired,
        sortOrder: t.sortOrder
      })) || []
    }
    dialogVisible.value = true
  } catch (e: any) {
    ElMessage.error('加载详情失败')
  }
}

const showDetail = async (row: any) => {
  try {
    const res = await getPrototype(row.id)
    detailData.value = res.data
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error('加载详情失败')
  }
}

const handleSave = async () => {
  if (!formData.value.prototypeName) {
    ElMessage.warning('请输入素材名称')
    return
  }
  saving.value = true
  try {
    await savePrototype(formData.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认删除该模板素材？', '确认')
    await deletePrototype(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.prototype-page {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

