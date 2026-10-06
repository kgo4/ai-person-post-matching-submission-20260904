<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Setting } from '@element-plus/icons-vue'
import {
  pageTemplates,
  deleteTemplate,
  getTemplateAbilityModels,
  saveTemplateAbilityModels,
  getTagTree,
} from '@/api'
import type { PostModelTemplate, TemplateAbilityModel, AbilityTagTreeVO } from '@/api'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const tableData = ref<PostModelTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 能力要求配置弹窗
const abilityDialogVisible = ref(false)
const currentTemplateId = ref<number | null>(null)
const currentTemplateName = ref('')
const abilityModels = ref<TemplateAbilityModel[]>([])
const abilityTags = ref<{ id: number; tagName: string }[]>([])
const abilityLoading = ref(false)

const columns = [
  { prop: 'id', label: 'ID', width: '80px' },
  { prop: 'templateCode', label: '模板编码' },
  { prop: 'templateName', label: '模板名称' },
  { prop: 'postSequence', label: '岗位序列' },
  { prop: 'description', label: '描述' },
  { prop: 'status', label: '状态' },
]

async function loadList() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    const res = await pageTemplates(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

// 展平树形标签
function flattenTree(tree: AbilityTagTreeVO[]): { id: number; tagName: string }[] {
  const result: { id: number; tagName: string }[] = []
  function traverse(nodes: AbilityTagTreeVO[]) {
    for (const node of nodes) {
      result.push({ id: node.id, tagName: node.tagName })
      if (node.children && node.children.length > 0) {
        traverse(node.children)
      }
    }
  }
  traverse(tree)
  return result
}

async function loadAbilityTags() {
  try {
    const res = await getTagTree()
    abilityTags.value = flattenTree(res.data || [])
  } catch {
    abilityTags.value = []
  }
}

onMounted(() => {
  loadList()
  loadAbilityTags()
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

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该模板吗？', '提示', { type: 'warning' })
    await deleteTemplate(id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // cancelled or error
  }
}

// 打开能力要求配置弹窗
async function openAbilityDialog(row: PostModelTemplate) {
  currentTemplateId.value = row.id
  currentTemplateName.value = row.templateName
  abilityDialogVisible.value = true
  await loadAbilityModels(row.id)
}

// 加载模板能力要求
async function loadAbilityModels(templateId: number) {
  abilityLoading.value = true
  try {
    const res = await getTemplateAbilityModels(templateId)
    abilityModels.value = res.data || []
  } catch {
    abilityModels.value = []
  } finally {
    abilityLoading.value = false
  }
}

// 添加能力要求行
function addAbilityModel() {
  abilityModels.value.push({
    templateId: currentTemplateId.value!,
    tagId: 0,
    minRequiredLevel: 1,
    weight: 0,
    isRequired: 0,
    isCore: 0,
    remark: '',
  })
}

// 删除能力要求行
function removeAbilityModel(index: number) {
  abilityModels.value.splice(index, 1)
}

// 保存能力要求
async function handleSaveAbilityModels() {
  if (!currentTemplateId.value) return

  // 校验
  const validModels = abilityModels.value.filter(m => m.tagId > 0)
  if (validModels.length === 0) {
    ElMessage.warning('请至少添加一个能力要求')
    return
  }

  try {
    await saveTemplateAbilityModels(currentTemplateId.value, validModels)
    ElMessage.success('保存成功')
    abilityDialogVisible.value = false
  } catch {
    // handled by interceptor
  }
}

// 获取标签名称
function getTagName(tagId: number): string {
  const tag = abilityTags.value.find(t => t.id === tagId)
  return tag ? tag.tagName : `标签#${tagId}`
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>岗位能力模板</span>
          <el-button type="primary" @click="router.push('/post/template/edit')">
            <el-icon><Plus /></el-icon> 新增能力模板
          </el-button>
        </div>
      </template>

      <div class="search-bar">
        <el-input v-model="keyword" placeholder="搜索能力模板名称" clearable style="width: 280px;" />
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
        <el-button @click="handleSearch"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" />
        <el-table-column label="操作" width="280px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push({ path: '/post/template/edit', query: { id: row.id } })">编辑</el-button>
            <el-button type="warning" link @click="openAbilityDialog(row)">
              <el-icon><Setting /></el-icon> 配置能力要求
            </el-button>
            <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
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
    </el-card>

    <!-- 能力要求配置弹窗 -->
    <el-dialog
      v-model="abilityDialogVisible"
      :title="`配置能力要求 - ${currentTemplateName}`"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-loading="abilityLoading">
        <div style="margin-bottom: 16px;">
          <el-button type="primary" @click="addAbilityModel">
            <el-icon><Plus /></el-icon> 添加能力要求
          </el-button>
        </div>

        <el-table :data="abilityModels" border size="small">
          <el-table-column label="能力标签" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.tagId" placeholder="请选择能力标签" style="width: 100%;">
                <el-option
                  v-for="tag in abilityTags"
                  :key="tag.id"
                  :label="tag.tagName"
                  :value="tag.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="最低要求等级" width="130" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.minRequiredLevel" :min="1" :max="5" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="权重" width="120" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.weight" :min="0" :max="100" :precision="2" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="必填" width="80" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.isRequired" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="核心项" width="80" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.isCore" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeAbilityModel($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="abilityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveAbilityModels">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

