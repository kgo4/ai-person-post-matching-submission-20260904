<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Briefcase, CircleCheck, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { deletePost, listEnabledPosts, pagePosts, savePost, updatePost } from '@/api'
import type { PostPost } from '@/api'

const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const tableData = ref<PostPost[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('新增岗位')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PostPost>({
  id: 0,
  postCode: '',
  postName: '',
  jobDescription: '',
  templateId: 0,
  status: 1,
  createdTime: '',
})

const formRules: FormRules = {
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
}

const enabledCount = ref(0)

async function loadEnabledCount() {
  try {
    const res = await listEnabledPosts()
    enabledCount.value = res.data.length
  } catch {
    // ignore
  }
}

async function loadList() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== undefined) params.status = statusFilter.value
    const res = await pagePosts(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadList()
  loadEnabledCount()
})

function handleSearch() {
  currentPage.value = 1
  loadList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadList()
}

function openDialog(row?: PostPost) {
  dialogVisible.value = true
  if (row) {
    isEdit.value = true
    dialogTitle.value = '编辑岗位'
    Object.assign(form, row)
  } else {
    isEdit.value = false
    dialogTitle.value = '新增岗位'
    Object.assign(form, {
      id: 0,
      postCode: '',
      postName: '',
      jobDescription: '',
      templateId: 0,
      status: 1,
      createdTime: '',
    })
  }
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updatePost(form.id, form)
      ElMessage.success('编辑成功')
    } else {
      const { postCode: _postCode, ...createPayload } = form
      await savePost(createPayload)
      ElMessage.success('新增成功')
    }
    closeDialog()
    loadList()
    loadEnabledCount()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该岗位吗？', '提示', { type: 'warning' })
    await deletePost(id)
    ElMessage.success('删除成功')
    loadList()
    loadEnabledCount()
  } catch {
    // ignore
  }
}

function handleDetail(id: number) {
  router.push(`/post/detail/${id}`)
}
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Position Graph</div>
        <h1 class="page-hero__title">岗位标准中心</h1>
        <p class="page-hero__desc">维护岗位条目、编码、模板和能力模型入口，让后续匹配规则与 AI 分析建立在统一岗位标准上。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">岗位总量 {{ total }}</span>
          <span class="hero-chip">启用 {{ enabledCount }}</span>
          <span class="hero-chip">Model Driven</span>
        </div>
      </div>

      <div class="toolbar-group">
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          新增岗位
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card" style="grid-column: span 6;">
        <div class="metric-card__icon" style="background: rgba(37,99,235,0.12); color: #2563eb;">
          <el-icon :size="22"><Briefcase /></el-icon>
        </div>
        <div>
          <div class="metric-card__label">岗位总数</div>
          <div class="metric-card__value">{{ total }}</div>
          <div class="metric-card__hint">当前检索结果中的岗位数量</div>
        </div>
      </article>
      <article class="metric-card" style="grid-column: span 6;">
        <div class="metric-card__icon" style="background: rgba(5,150,105,0.12); color: #059669;">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div>
          <div class="metric-card__label">启用岗位</div>
          <div class="metric-card__value">{{ enabledCount }}</div>
          <div class="metric-card__hint">可参与模型配置与匹配执行</div>
        </div>
      </article>
    </section>

    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">岗位列表</div>
          <div class="section-desc">统一管理岗位条目，并从这里进入能力模型配置。</div>
        </div>
        <div class="toolbar-group">
          <el-input v-model="keyword" placeholder="搜索岗位名称 / 编码" clearable class="!w-64" />
          <el-select v-model="statusFilter" placeholder="状态" clearable class="!w-36">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleSearch">刷新</el-button>
        </div>
      </div>

      <div class="panel-body">
        <el-table :data="tableData" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="postName" label="岗位名称" min-width="180" />
          <el-table-column prop="postCode" label="岗位编码" min-width="140" />
          <el-table-column prop="templateId" label="模板 ID" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="240">
            <template #default="{ row }">
              <span>{{ row.jobDescription || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="300" fixed="right">
            <template #default="{ row }">
              <div class="table-link-cluster">
                <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
                <el-button type="success" link @click="router.push(`/post/model-config?postId=${row.id}`)">模型配置</el-button>
                <el-button type="info" link @click="handleDetail(row.id)">详情</el-button>
                <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
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
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="form.postName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="岗位编码">
          <el-input v-model="form.postCode" readonly />
        </el-form-item>
        <el-form-item label="关联模板">
          <el-input-number v-model="form.templateId" :min="0" placeholder="模板 ID" />
        </el-form-item>
        <el-form-item label="职位描述">
          <el-input v-model="form.jobDescription" type="textarea" :rows="3" placeholder="请输入职位描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@media (max-width: 720px) {
  .metric-grid > article {
    grid-column: span 12 !important;
  }
}
</style>
