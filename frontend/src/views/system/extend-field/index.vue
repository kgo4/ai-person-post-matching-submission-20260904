<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  pageFields,
  listFieldsByModule,
  saveField,
  updateField,
  deleteField,
} from '@/api'
import type { ExtendFieldVO, ExtendFieldConfigDTO } from '@/api'

// ==================== 列表相关 ====================
const loading = ref(false)
const tableData = ref<ExtendFieldVO[]>([])
const total = ref(0)
const activeModule = ref('')

const searchForm = reactive({
  current: 1,
  size: 10,
})

const moduleTabs = [
  { label: '全部', value: '' },
  { label: '员工', value: 'EMPLOYEE' },
  { label: '岗位', value: 'POST' },
  { label: '能力', value: 'ABILITY' },
]

const fieldTypeMap: Record<string, string> = {
  text: '文本输入',
  number: '数字输入',
  select: '单选下拉',
  'multi-select': '多选下拉',
  date: '日期',
  datetime: '日期时间',
  textarea: '文本域',
  switch: '开关',
}

const columns = [
  { prop: 'id', label: 'ID', width: '80px' },
  { prop: 'fieldName', label: '字段Key' },
  { prop: 'fieldLabel', label: '字段名称' },
  { prop: 'fieldType', label: '字段类型' },
  { prop: 'businessModule', label: '适用模块' },
  { prop: 'isRequired', label: '是否必填' },
  { prop: 'status', label: '状态' },
  { prop: 'sortOrder', label: '排序' },
]

async function fetchList() {
  loading.value = true
  try {
    if (activeModule.value) {
      const res = await listFieldsByModule(activeModule.value)
      tableData.value = res.data
      total.value = res.data.length
    } else {
      const res = await pageFields({
        current: searchForm.current,
        size: searchForm.size,
      })
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleModuleChange(module: string) {
  activeModule.value = module
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
const dialogTitle = ref('新增扩展字段')
const dialogLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<number>()

const form = reactive<ExtendFieldConfigDTO>({
  businessModule: '',
  fieldName: '',
  fieldLabel: '',
  fieldType: 'text',
  selectOptions: '',
  isRequired: 0,
  sortOrder: 0,
})

const fieldTypeOptions = [
  { label: '文本输入', value: 'text' },
  { label: '数字输入', value: 'number' },
  { label: '单选下拉', value: 'select' },
  { label: '多选下拉', value: 'multi-select' },
  { label: '日期', value: 'date' },
  { label: '日期时间', value: 'datetime' },
  { label: '文本域', value: 'textarea' },
  { label: '开关', value: 'switch' },
]

const rules: FormRules = {
  fieldName: [{ required: true, message: '请输入字段Key', trigger: 'blur' }],
  fieldLabel: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }],
  businessModule: [{ required: true, message: '请选择适用模块', trigger: 'change' }],
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  dialogTitle.value = '新增扩展字段'
  Object.assign(form, {
    businessModule: activeModule.value || '',
    fieldName: '',
    fieldLabel: '',
    fieldType: 'text',
    selectOptions: '',
    isRequired: 0,
    sortOrder: 0,
  })
  formRef.value?.resetFields()
  dialogVisible.value = true
}

function handleEdit(row: ExtendFieldVO) {
  isEdit.value = true
  editId.value = row.id
  dialogTitle.value = '编辑扩展字段'
  Object.assign(form, {
    businessModule: row.businessModule,
    fieldName: row.fieldName,
    fieldLabel: row.fieldLabel,
    fieldType: row.fieldType,
    selectOptions: row.selectOptions,
    isRequired: row.isRequired,
    sortOrder: row.sortOrder,
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
      await updateField(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await saveField(form)
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
async function handleDelete(row: ExtendFieldVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除扩展字段「${row.fieldLabel}」吗？`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteField(row.id)
    ElMessage.success('删除成功')
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
          <span>扩展字段配置</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增字段
          </el-button>
        </div>
      </template>

      <!-- 模块筛选tab -->
      <div class="module-tabs">
        <el-radio-group v-model="activeModule" size="small" @change="handleModuleChange">
          <el-radio-button v-for="tab in moduleTabs" :key="tab.value" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width">
          <template v-if="col.prop === 'fieldType'" #default="{ row }">
            {{ fieldTypeMap[row.fieldType] || row.fieldType }}
          </template>
          <template v-else-if="col.prop === 'isRequired'" #default="{ row }">
            <el-tag :type="row.isRequired === 1 ? 'warning' : 'info'" size="small">
              {{ row.isRequired === 1 ? '必填' : '非必填' }}
            </el-tag>
          </template>
          <template v-else-if="col.prop === 'status'" #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container" v-if="!activeModule">
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
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字段Key" prop="fieldName">
          <el-input v-model="form.fieldName" placeholder="例如：marital_status" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="字段名称" prop="fieldLabel">
          <el-input v-model="form.fieldLabel" placeholder="例如：婚姻状况" />
        </el-form-item>
        <el-form-item label="字段类型" prop="fieldType">
          <el-select v-model="form.fieldType" placeholder="请选择" style="width: 100%;">
            <el-option v-for="item in fieldTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用模块" prop="businessModule">
          <el-select v-model="form.businessModule" placeholder="请选择" style="width: 100%;">
            <el-option label="员工" value="EMPLOYEE" />
            <el-option label="岗位" value="POST" />
            <el-option label="能力" value="ABILITY" />
          </el-select>
        </el-form-item>

        <!-- 选项配置（select/multi-select类型） -->
        <el-form-item label="选项配置" v-if="['select', 'multi-select'].includes(form.fieldType || '')">
          <el-input
            v-model="form.selectOptions"
            type="textarea"
            :rows="3"
            placeholder="一行一个选项，格式：显示文本=值，例如：&#10;已婚=married&#10;未婚=unmarried"
          />
        </el-form-item>

        <el-form-item label="是否必填">
          <el-radio-group v-model="form.isRequired">
            <el-radio :value="1">必填</el-radio>
            <el-radio :value="0">非必填</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
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
.module-tabs {
  margin-bottom: 16px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
