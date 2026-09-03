<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  pageUsers,
  saveUser,
  updateUser,
  deleteUser,
  resetPassword,
  updateUserStatus,
} from '@/api'
import type { UserVO, UserSaveDTO } from '@/api'

// ==================== 列表相关 ====================
const loading = ref(false)
const tableData = ref<UserVO[]>([])
const total = ref(0)

const searchForm = reactive({
  keyword: '',
  status: undefined as number | undefined,
  current: 1,
  size: 10,
})

const columns = [
  { prop: 'id', label: 'ID', width: '80px' },
  { prop: 'username', label: '用户名' },
  { prop: 'realName', label: '姓名' },
  { prop: 'phone', label: '手机号' },
  { prop: 'email', label: '邮箱' },
  { prop: 'status', label: '状态' },
  { prop: 'lastLoginTime', label: '最后登录' },
  { prop: 'createdTime', label: '创建时间' },
]

async function fetchList() {
  loading.value = true
  try {
    const res = await pageUsers({
      current: searchForm.current,
      size: searchForm.size,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status,
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch {
    // 错误已由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchForm.current = 1
  fetchList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = undefined
  handleSearch()
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
const dialogTitle = ref('新增用户')
const dialogLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<number>()

const form = reactive<UserSaveDTO>({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  departmentId: undefined,
  status: 1,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
      validator: (_rule, _value, callback) => {
        if (isEdit.value) return callback()
        if (!form.password) return callback(new Error('请输入密码'))
        callback()
      },
    },
  ],
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  dialogTitle.value = '新增用户'
  Object.assign(form, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    departmentId: undefined,
    status: 1,
  })
  formRef.value?.resetFields()
  dialogVisible.value = true
}

function handleEdit(row: UserVO) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑用户'
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone,
    email: row.email,
    departmentId: row.departmentId,
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
      await updateUser(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await saveUser(form)
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
async function handleDelete(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.username}」吗？`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 取消或错误
  }
}

// ==================== 重置密码 ====================
async function handleResetPassword(row: UserVO) {
  try {
    await ElMessageBox.confirm(
      `确定要将用户「${row.username}」的密码重置为默认密码吗？`,
      '确认重置密码',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await resetPassword(row.id)
    ElMessage.success('密码重置成功')
  } catch {
    // 取消或错误
  }
}

// ==================== 状态切换 ====================
async function handleToggleStatus(row: UserVO) {
  const newStatus = row.status === 1 ? 0 : 1
  const label = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${label}用户「${row.username}」吗？`,
      '确认操作',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(`${label}成功`)
    fetchList()
  } catch {
    // 取消或错误
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
          <span>用户管理</span>
          <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon> 新增用户</el-button>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索用户名/姓名"
          clearable
          style="width: 280px;"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="searchForm.status"
          placeholder="状态"
          clearable
          style="width: 140px;"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width">
          <template v-if="col.prop === 'status'" #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link @click="handleResetPassword(row)">重置密码</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'"
            show-password
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
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
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleSave">保存</el-button>
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
