<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { saveField, updateField, getFieldById } from '@/api/system'
import type { ExtendFieldConfigDTO } from '@/api/types'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const loading = ref(false)
const loadError = ref('')
const saveError = ref('')

const form = reactive({
  id: undefined as number | undefined,
  businessModule: '',
  fieldName: '',
  fieldLabel: '',
  fieldType: 'text',
  selectOptions: [] as { label: string; value: string }[],
  isRequired: 0,
  sortOrder: 0,
  status: 1,
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

const businessModuleOptions = [
  { label: '员工', value: 'EMPLOYEE' },
  { label: '岗位', value: 'POST' },
]

const showSelectOptions = ref(false)

watch(() => form.fieldType, (val) => {
  showSelectOptions.value = val === 'select' || val === 'multi-select'
})

const rules: FormRules = {
  fieldName: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldLabel: [{ required: true, message: '请输入显示标签', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }],
  businessModule: [{ required: true, message: '请选择业务模块', trigger: 'change' }],
}

function serializeOptions(): string {
  const valid = form.selectOptions.filter(o => o.label || o.value)
  return valid.length > 0 ? JSON.stringify(valid) : ''
}

function deserializeOptions(json: string | null | undefined) {
  if (!json) {
    form.selectOptions = []
    return
  }
  try {
    form.selectOptions = JSON.parse(json)
  } catch {
    form.selectOptions = []
  }
}

onMounted(async () => {
  const id = route.query.id as string
  if (id) {
    isEdit.value = true
    loadError.value = ''
    try {
      const res = await getFieldById(Number(id))
      const data = res.data
      if (data) {
        form.id = data.id
        form.businessModule = data.businessModule
        form.fieldName = data.fieldName
        form.fieldLabel = data.fieldLabel
        form.fieldType = data.fieldType
        form.isRequired = data.isRequired
        form.sortOrder = data.sortOrder || 0
        form.status = data.status
        deserializeOptions(data.selectOptions)
      }
    } catch {
      loadError.value = '加载字段数据失败'
    }
  }
})

function addOption() {
  form.selectOptions.push({ label: '', value: '' })
}

function removeOption(index: number) {
  form.selectOptions.splice(index, 1)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  saveError.value = ''
  try {
    const dto: ExtendFieldConfigDTO = {
      id: form.id,
      businessModule: form.businessModule,
      fieldName: form.fieldName,
      fieldLabel: form.fieldLabel,
      fieldType: form.fieldType,
      selectOptions: serializeOptions() || undefined,
      isRequired: form.isRequired,
      sortOrder: form.sortOrder,
      status: form.status,
    }
    if (isEdit.value) {
      await updateField(form.id!, dto)
    } else {
      await saveField(dto)
    }
    ElMessage.success('保存成功')
    router.back()
  } catch {
    saveError.value = '保存失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>字段配置</template>

      <el-alert v-if="loadError" type="error" :title="loadError" show-icon closable style="margin-bottom: 16px;" @close="loadError = ''" />
      <el-alert v-if="saveError" type="error" :title="saveError" show-icon closable style="margin-bottom: 16px;" @close="saveError = ''" />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 700px;"
      >
        <el-form-item label="业务模块" prop="businessModule">
          <el-select v-model="form.businessModule" placeholder="请选择" style="width: 100%;">
            <el-option v-for="item in businessModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段名称" prop="fieldName">
          <el-input v-model="form.fieldName" placeholder="例如：marital_status" />
        </el-form-item>
        <el-form-item label="显示标签" prop="fieldLabel">
          <el-input v-model="form.fieldLabel" placeholder="例如：婚姻状况" />
        </el-form-item>
        <el-form-item label="字段类型" prop="fieldType">
          <el-select v-model="form.fieldType" placeholder="请选择" style="width: 100%;">
            <el-option v-for="item in fieldTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch :model-value="form.isRequired === 1" @change="(val: boolean) => form.isRequired = val ? 1 : 0" />
        </el-form-item>

        <!-- 选项配置（select/multi-select类型） -->
        <el-form-item label="选项配置" v-if="showSelectOptions">
          <div style="width: 100%;">
            <div v-for="(opt, index) in form.selectOptions" :key="index" style="display: flex; gap: 12px; margin-bottom: 8px;">
              <el-input v-model="opt.label" placeholder="显示文本" style="flex: 1;" />
              <el-input v-model="opt.value" placeholder="值" style="flex: 1;" />
              <el-button type="danger" @click="removeOption(index)">删除</el-button>
            </div>
            <el-button type="primary" link @click="addOption">
              <el-icon><Plus /></el-icon> 添加选项
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
