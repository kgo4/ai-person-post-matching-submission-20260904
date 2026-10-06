<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  pageRoles,
  saveRole,
  updateRole,
  deleteRole,
  listEnabledRoles,
  assignRoles,
  getUserRoleIds,
  pageUsers,
} from '@/api'
import type { RoleVO, RoleSaveDTO, UserVO } from '@/api'

// ==================== 列表相关 ====================
const loading = ref(false)
const tableData = ref<RoleVO[]>([])
const total = ref(0)

const searchForm = reactive({
  keyword: '',
  current: 1,
  size: 10,
})

const columns = [
  { prop: 'id', label: 'ID', width: '80px' },
  { prop: 'roleName', label: '角色名称' },
  { prop: 'roleCode', label: '角色标识' },
  { prop: 'description', label: '描述' },
  { prop: 'status', label: '状态' },
  { prop: 'createdTime', label: '创建时间' },
]

async function fetchList() {
  loading.value = true
  try {
    const res = await pageRoles({
      current: searchForm.current,
      size: searchForm.size,
      keyword: searchForm.keyword || undefined,
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchForm.current = 1
  fetchList()
}

function handleSizeChange(size: number) {
  searchForm.size = size
  fetchList()
}

function handleCurrentChange(current: number) {
  searchForm.current = current
  fetchList()
}

// ==================== 新增/编辑弹窗 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const dialogLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<number>()

const form = reactive<RoleSaveDTO>({
  roleCode: '',
  roleName: '',
  description: '',
  dataScope: undefined,
  status: 1,
})

const rules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  dialogTitle.value = '新增角色'
  Object.assign(form, {
    roleCode: '',
    roleName: '',
    description: '',
    dataScope: undefined,
    status: 1,
  })
  formRef.value?.resetFields()
  dialogVisible.value = true
}

function handleEdit(row: RoleVO) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑角色'
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description,
    dataScope: row.dataScope,
    status: row.status,
  })
  formRef.value?.resetFields()
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  dialogLoading.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateRole(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await saveRole(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // 错误已由拦截器处理
  } finally {
    dialogLoading.value = false
  }
}

// ==================== 删除 ====================
async function handleDelete(row: RoleVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色「${row.roleName}」吗？`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 取消或错误
  }
}

// ==================== 分配角色弹窗 ====================
const assignDialogVisible = ref(false)
const assignLoading = ref(false)
const assignUserId = ref<number>()
const assignedRoleIds = ref<number[]>([])
const enabledRoles = ref<RoleVO[]>([])

// 用户搜索相关
const userSearchKeyword = ref('')
const userOptions = ref<UserVO[]>([])
const userLoading = ref(false)

async function handleSearchUsers(keyword: string) {
  if (!keyword) {
    userOptions.value = []
    return
  }
  userLoading.value = true
  try {
    const res = await pageUsers({ current: 1, size: 20, keyword })
    userOptions.value = res.data.records
  } catch {
    // 错误已由拦截器处理
  } finally {
    userLoading.value = false
  }
}

function onUserSelectChange(val: number | undefined) {
  if (!val) {
    assignedRoleIds.value = []
    return
  }
  loadUserRoles(val)
}

async function loadUserRoles(userId: number) {
  try {
    const res = await getUserRoleIds(userId)
    assignedRoleIds.value = res.data || []
  } catch {
    assignedRoleIds.value = []
  }
}

function handleAssignRole() {
  assignUserId.value = undefined
  userSearchKeyword.value = ''
  userOptions.value = []
  assignedRoleIds.value = []
  assignDialogVisible.value = true
  // 加载所有启用的角色
  listEnabledRoles().then((res) => {
    enabledRoles.value = res.data
  }).catch(() => {
    enabledRoles.value = []
  })
}

async function handleAssignSave() {
  if (!assignUserId.value) {
    ElMessage.warning('请选择用户')
    return
  }
  assignLoading.value = true
  try {
    await assignRoles(assignUserId.value, assignedRoleIds.value)
    ElMessage.success('分配角色成功')
    assignDialogVisible.value = false
  } catch {
    // 错误已由拦截器处理
  } finally {
    assignLoading.value = false
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>角色权限管理</span>
          <div style="display: flex; gap: 12px;">
            <el-button type="success" @click="handleAssignRole"><el-icon><User /></el-icon> 分配角色</el-button>
            <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon> 新增角色</el-button>
          </div>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索角色名称/标识"
          clearable
          style="width: 280px;"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width">
          <template v-if="col.prop === 'status'" #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.current"
          v-model:page-size="searchForm.size"
          :page-sizes="[10, 15, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色标识" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="请输入角色标识" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog
      v-model="assignDialogVisible"
      title="分配角色"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="选择用户">
          <el-select
            v-model="assignUserId"
            filterable
            remote
            reserve-keyword
            placeholder="请输入用户名/姓名搜索"
            :remote-method="handleSearchUsers"
            :loading="userLoading"
            clearable
            style="width: 100%;"
            @change="onUserSelectChange"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.realName} (${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色列表">
          <el-select
            v-model="assignedRoleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%;"
          >
            <el-option
              v-for="role in enabledRoles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignLoading" @click="handleAssignSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
