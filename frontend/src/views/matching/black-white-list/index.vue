<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { pageBWList, saveBWEntry, updateBWEntry, deleteBWEntry, pageEmployees, listEnabledPosts } from '@/api'
import type { MatchingBlackWhiteList, PageResultVO, PostPost } from '@/api'

const activeTab = ref('all')
const loading = ref(false)
const tableData = ref<MatchingBlackWhiteList[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const empOptions = ref<{ id: number; realName: string; empCode: string }[]>([])
const postOptions = ref<PostPost[]>([])

const searchEmpId = ref<number>()
const searchPostId = ref<number>()

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const dialogTitle = ref('新增')
const editId = ref<number | null>(null)
const form = reactive({
  listType: 1,
  empId: undefined as number | undefined,
  postId: undefined as number | undefined,
  remark: '',
})

onMounted(async () => {
  try {
    const [empRes, postRes] = await Promise.all([
      pageEmployees({ current: 1, size: 999 }),
      listEnabledPosts(),
    ])
    empOptions.value = ((empRes.data as any)?.records || []).map((e: any) => ({
      id: e.id, realName: e.realName, empCode: e.empCode,
    }))
    postOptions.value = postRes.data || []
  } catch { /* ignore */ }
  loadData()
})

function getListTypeText(type: number) { return type === 1 ? '白名单' : '黑名单' }
function getListTypeTag(type: number) { return type === 1 ? 'success' : 'danger' }
function getEmpName(id: number) { const e = empOptions.value.find(x => x.id === id); return e ? `${e.realName} (${e.empCode})` : `员工#${id}` }
function getPostName(id: number) { const p = postOptions.value.find(x => x.id === id); return p?.postName || `岗位#${id}` }

async function loadData() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (searchEmpId.value) params.empId = searchEmpId.value
    if (searchPostId.value) params.postId = searchPostId.value
    if (activeTab.value === 'whitelist') params.listType = 1
    if (activeTab.value === 'blacklist') params.listType = 2
    const res = await pageBWList(params)
    const pageResult: PageResultVO<MatchingBlackWhiteList> = res.data as any
    tableData.value = pageResult.records || []
    total.value = pageResult.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally { loading.value = false }
}

function handleTabChange() { currentPage.value = 1; loadData() }
function handleSearch() { currentPage.value = 1; loadData() }
function handleSizeChange(size: number) { pageSize.value = size; currentPage.value = 1; loadData() }
function handleCurrentChange(page: number) { currentPage.value = page; loadData() }

function openAddDialog() {
  dialogTitle.value = '新增'; editId.value = null
  form.listType = activeTab.value === 'blacklist' ? 2 : 1
  form.empId = undefined; form.postId = undefined; form.remark = ''
  dialogVisible.value = true
}

function openEditDialog(row: MatchingBlackWhiteList) {
  dialogTitle.value = '编辑'; editId.value = row.id
  form.listType = row.listType; form.empId = row.empId; form.postId = row.postId; form.remark = row.remark
  dialogVisible.value = true
}

async function handleSave() {
  dialogLoading.value = true
  try {
    const data: MatchingBlackWhiteList = {
      id: editId.value || 0, listType: form.listType,
      empId: form.empId!, postId: form.postId!, remark: form.remark, status: 1,
    }
    if (editId.value) { await updateBWEntry(editId.value, data); ElMessage.success('更新成功') }
    else { await saveBWEntry(data); ElMessage.success('新增成功') }
    dialogVisible.value = false; loadData()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { dialogLoading.value = false }
}

async function handleDelete(row: MatchingBlackWhiteList) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
    await deleteBWEntry(row.id)
    ElMessage.success('删除成功'); loadData()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e.message || '删除失败') }
}
</script>

<template>
  <div class="page-shell motion-page">
    <section class="page-hero motion-scan">
      <div>
        <div class="page-hero__eyebrow">Access Control</div>
        <h1 class="page-hero__title">黑白名单</h1>
        <p class="page-hero__desc">白名单强制匹配、黑名单强制排除，维护人岗匹配的准入规则</p>
      </div>
    </section>

    <section class="glass-card motion-rise">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">名单条目</div>
          <div class="section-desc">支持按人员和岗位搜索，Tab 切换全部/白名单/黑名单</div>
        </div>
        <el-button type="primary" @click="openAddDialog">+ 新增</el-button>
      </div>

      <div class="toolbar-panel">
        <div class="toolbar-group">
          <el-select v-model="searchEmpId" placeholder="搜索人员" filterable clearable style="width:220px" @change="handleSearch">
            <el-option v-for="e in empOptions" :key="e.id" :label="`${e.realName} (${e.empCode})`" :value="e.id" />
          </el-select>
          <el-select v-model="searchPostId" placeholder="搜索岗位" filterable clearable style="width:220px" @change="handleSearch">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button @click="searchEmpId = undefined; searchPostId = undefined; handleSearch()">重置</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange" style="padding: 0 20px">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="白名单" name="whitelist" />
        <el-tab-pane label="黑名单" name="blacklist" />
      </el-tabs>

      <div class="panel-body">
        <el-table :data="tableData" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="getListTypeTag(row.listType)" size="small">{{ getListTypeText(row.listType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="人员" min-width="160">
            <template #default="{ row }">{{ getEmpName(row.empId) }}</template>
          </el-table-column>
          <el-table-column label="岗位" min-width="160">
            <template #default="{ row }">{{ getPostName(row.postId) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="200" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-footer">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="currentPage" :page-size="pageSize" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
      <el-form :model="form" label-width="60px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.listType">
            <el-radio :value="1">白名单</el-radio>
            <el-radio :value="2">黑名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="人员">
          <el-select v-model="form.empId" placeholder="搜索选择人员" filterable style="width:100%">
            <el-option v-for="e in empOptions" :key="e.id" :label="`${e.realName} (${e.empCode})`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.postId" placeholder="搜索选择岗位" filterable style="width:100%">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="输入备注原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
