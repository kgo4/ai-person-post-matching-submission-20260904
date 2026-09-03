<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { CircleCheck, Download, Lock, Plus, Refresh, Search, Upload, User } from '@element-plus/icons-vue'
import { batchImport, deleteEmployee, getEmployeeStats, lockEmployee, pageEmployees, saveEmployee, unlockEmployee, updateEmployee, importEmployeesExcel, exportEmployeesExcel, downloadEmployeeTemplate } from '@/api'
import { precheckCapabilityEligibility, type EligibilityPrecheckResult } from '@/api/assessment'
import { syncPmsUsers } from '@/api/ability-source'
import type { EmpEmployee } from '@/api'

const router = useRouter()
const loading = ref(false)
const syncLoading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const tableData = ref<EmpEmployee[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

/** 当前页员工能力状态（正式/待审核），来自匹配资格预检（口径与匹配引擎一致） */
const abilityStatusMap = ref<Record<number, EligibilityPrecheckResult>>({})

const dialogVisible = ref(false)
const dialogTitle = ref('新增人员')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<EmpEmployee>({
  id: 0,
  empCode: '',
  realName: '',
  gender: 1,
  phone: '',
  email: '',
  extendFields: '',
  isLocked: 0,
  status: 1,
  createdTime: '',
})

const formRules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

const activeCount = ref(0)
const lockedCount = ref(0)

async function loadStats() {
  try {
    const res = await getEmployeeStats()
    activeCount.value = res.data.enabled
    lockedCount.value = res.data.locked
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadList()
  loadStats()
})

async function loadList() {
  loading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== undefined) params.status = statusFilter.value
    const res = await pageEmployees(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  loadAbilityStatus()
}

/** 批量加载当前页员工能力状态（precheck 不依赖岗位列表，可直接复用为能力状态查询） */
async function loadAbilityStatus() {
  const ids = tableData.value.map((e) => e.id).filter((id) => id != null)
  if (!ids.length) {
    abilityStatusMap.value = {}
    return
  }
  try {
    const res = await precheckCapabilityEligibility(ids, [])
    const map: Record<number, EligibilityPrecheckResult> = {}
    for (const item of res.data ?? []) map[item.empId] = item
    abilityStatusMap.value = map
  } catch {
    // 能力状态查询失败不阻塞列表展示
    abilityStatusMap.value = {}
  }
}

function abilityStatusOf(rowId: number): EligibilityPrecheckResult | undefined {
  return abilityStatusMap.value[rowId]
}

/** 有待审核能力 → 跳转评估页待确立标签 */
function goProvisionalAssessment(empId: number) {
  router.push({ path: '/employee/ability-profile/assessment', query: { empId, tab: 'provisional' } })
}

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

function handleView(id: number) {
  router.push(`/employee/detail/${id}`)
}

function handleAbilityProfile(empId: number) {
  router.push(`/employee/ability-profile?empId=${empId}`)
}

function resetForm() {
  Object.assign(form, {
    id: 0,
    empCode: '',
    realName: '',
    gender: 1,
    phone: '',
    email: '',
    extendFields: '',
    isLocked: 0,
    status: 1,
    createdTime: '',
  })
}

function openDialog(row?: EmpEmployee) {
  dialogVisible.value = true
  if (row) {
    isEdit.value = true
    dialogTitle.value = '编辑人员'
    Object.assign(form, row)
  } else {
    isEdit.value = false
    dialogTitle.value = '新增人员'
    resetForm()
  }
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value) {
      await updateEmployee(form.id, form)
      ElMessage.success('编辑成功')
    } else {
      const { empCode: _empCode, ...createPayload } = form
      await saveEmployee(createPayload)
      ElMessage.success('新增成功')
    }
    closeDialog()
    loadList()
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该人员吗？', '提示', { type: 'warning' })
    await deleteEmployee(id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // ignore
  }
}

async function handleLock(id: number) {
  await lockEmployee(id)
  ElMessage.success('已锁定')
  loadList()
}

async function handleUnlock(id: number) {
  await unlockEmployee(id)
  ElMessage.success('已解锁')
  loadList()
}

async function handleSyncPmsUsers() {
  syncLoading.value = true
  try {
    const res = await syncPmsUsers()
    const { newMapped, totalPmsUsers, alreadyMapped, newCreated } = res.data
    const parts = [`PMS 用户 ${totalPmsUsers} 人`, `新增映射 ${newMapped} 人`]
    if (alreadyMapped > 0) parts.push(`已有映射 ${alreadyMapped} 人`)
    if (newCreated > 0) parts.push(`自动创建员工 ${newCreated} 人`)
    ElMessage.success(`同步完成：${parts.join('，')}`)
    if (newMapped > 0) loadList()
  } finally {
    syncLoading.value = false
  }
}

const importFileRef = ref<HTMLInputElement>()
const excelFileRef = ref<HTMLInputElement>()

function handleBatchImportClick() {
  importFileRef.value?.click()
}

async function handleBatchImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  loading.value = true
  try {
    const text = await file.text()
    const data = JSON.parse(text) as EmpEmployee[]
    const res = await batchImport(data)
    ElMessage.success(`导入成功，共 ${res.data} 条记录`)
    loadList()
  } catch (error: any) {
    ElMessage.error(error?.message || '导入失败，请检查 JSON 格式')
  } finally {
    loading.value = false
    input.value = ''
  }
}

function handleExcelImportClick() {
  excelFileRef.value?.click()
}

async function handleExcelImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  loading.value = true
  try {
    const res = await importEmployeesExcel(file)
    ElMessage.success(`Excel 导入成功，共 ${res.data ?? 0} 条记录`)
    loadList()
  } catch {
    ElMessage.error('Excel 导入失败')
  } finally {
    loading.value = false
    input.value = ''
  }
}

async function handleExportExcel() {
  try {
    const res = await exportEmployeesExcel()
    const payload: any = (res as any).data ?? res
    const blob: Blob = payload instanceof Blob ? payload : new Blob([payload])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'employees.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleDownloadTemplate() {
  try {
    const res = await downloadEmployeeTemplate()
    const payload: any = (res as any).data ?? res
    const blob: Blob = payload instanceof Blob ? payload : new Blob([payload])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'employee-template.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Talent Pool</div>
        <h1 class="page-hero__title">人员工作台</h1>
        <p class="page-hero__desc">管理基础员工档案、同步外部映射关系，并把人才快速送入能力画像与匹配链路。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">当前页 {{ total }} 人</span>
          <span class="hero-chip">启用 {{ activeCount }} 人</span>
          <span class="hero-chip">锁定 {{ lockedCount }} 人</span>
        </div>
      </div>

      <div class="toolbar-group hero-actions">
        <button class="glass-btn" @click="handleBatchImportClick">
          <el-icon><Upload /></el-icon>
          批量导入
        </button>
        <button class="glass-btn" @click="handleExcelImportClick">
          <el-icon><Upload /></el-icon>
          Excel 导入
        </button>
        <button class="glass-btn" @click="handleExportExcel">
          <el-icon><Download /></el-icon>
          Excel 导出
        </button>
        <button class="glass-btn" @click="handleDownloadTemplate">模板下载</button>
        <el-button type="success" :loading="syncLoading" @click="handleSyncPmsUsers">同步 PMS 人员</el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          新增人员
        </el-button>
        <input ref="importFileRef" type="file" accept=".json" style="display: none" @change="handleBatchImportFile" />
        <input ref="excelFileRef" type="file" accept=".xlsx,.xls" style="display: none" @change="handleExcelImportFile" />
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card" style="grid-column: span 4;">
        <div class="metric-card__icon" style="background: rgba(37,99,235,0.12); color: #2563eb;">
          <el-icon :size="22"><User /></el-icon>
        </div>
        <div>
          <div class="metric-card__label">员工池规模</div>
          <div class="metric-card__value">{{ total }}</div>
          <div class="metric-card__hint">全部员工数量</div>
        </div>
      </article>
      <article class="metric-card" style="grid-column: span 4;">
        <div class="metric-card__icon" style="background: rgba(5,150,105,0.12); color: #059669;">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div>
          <div class="metric-card__label">启用人员</div>
          <div class="metric-card__value">{{ activeCount }}</div>
          <div class="metric-card__hint">可参与流程的员工</div>
        </div>
      </article>
      <article class="metric-card" style="grid-column: span 4;">
        <div class="metric-card__icon" style="background: rgba(217,119,6,0.12); color: #d97706;">
          <el-icon :size="22"><Lock /></el-icon>
        </div>
        <div>
          <div class="metric-card__label">锁定人员</div>
          <div class="metric-card__value">{{ lockedCount }}</div>
          <div class="metric-card__hint">暂不可继续流程</div>
        </div>
      </article>
    </section>

    <section class="glass-card">
      <div class="toolbar-panel">
        <div>
          <div class="section-title">人才列表</div>
          <div class="section-desc">支持关键词搜索、状态筛选与后续画像操作。</div>
        </div>
        <div class="toolbar-group">
          <el-input v-model="keyword" placeholder="搜索姓名 / 人员编号" clearable class="!w-64" />
          <el-select v-model="statusFilter" placeholder="状态" clearable class="!w-36">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleSearch">刷新</el-button>
        </div>
      </div>

      <div class="panel-body">
        <el-table :data="tableData" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="empCode" label="人员编号" width="140" />
          <el-table-column prop="realName" label="姓名" width="120" />
          <el-table-column prop="phone" label="手机号" min-width="150" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="锁定" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isLocked === 1 ? 'warning' : 'info'">{{ row.isLocked === 1 ? '已锁定' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="能力状态" min-width="190">
            <template #default="{ row }">
              <div class="ability-status-cell">
                <template v-if="abilityStatusOf(row.id)?.hasConfirmedAbilities">
                  <el-tag type="success" size="small" effect="plain" round>已具备正式能力</el-tag>
                  <el-tag
                    v-if="abilityStatusOf(row.id)?.hasProvisionalAbilities"
                    type="warning" size="small" round class="provisional-link"
                    @click="goProvisionalAssessment(row.id)"
                  >待审核 {{ abilityStatusOf(row.id)?.provisionalAbilityCount ?? 0 }} 项</el-tag>
                </template>
                <template v-else>
                  <el-tag type="danger" size="small" effect="plain" round>暂无正式能力</el-tag>
                  <el-tag
                    v-if="abilityStatusOf(row.id)?.hasProvisionalAbilities"
                    type="warning" size="small" round class="provisional-link"
                    @click="goProvisionalAssessment(row.id)"
                  >待审核 {{ abilityStatusOf(row.id)?.provisionalAbilityCount ?? 0 }} 项</el-tag>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="380" fixed="right">
            <template #default="{ row }">
              <div class="table-link-cluster">
                <el-button type="primary" link @click="handleView(row.id)">详情</el-button>
                <el-button type="success" link @click="handleAbilityProfile(row.id)">能力画像</el-button>
                <el-button type="warning" link @click="openDialog(row)">编辑</el-button>
                <el-button v-if="row.isLocked !== 1" type="info" link @click="handleLock(row.id)">锁定</el-button>
                <el-button v-else type="info" link @click="handleUnlock(row.id)">解锁</el-button>
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
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="人员编号">
          <el-input v-model="form.empCode" readonly />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="0">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ability-status-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.provisional-link {
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.provisional-link:hover {
  opacity: 0.75;
}

.hero-actions {
  justify-content: flex-end;
}

@media (max-width: 1024px) {
  .metric-grid > article {
    grid-column: span 6 !important;
  }
}

@media (max-width: 720px) {
  .metric-grid > article {
    grid-column: span 12 !important;
  }
}
</style>
