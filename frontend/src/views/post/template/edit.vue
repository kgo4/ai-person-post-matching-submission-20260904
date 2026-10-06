<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  saveTemplate,
  updateTemplate,
  getTemplate,
} from '@/api'
import type { PostTemplateSaveDTO } from '@/api'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = ref(false)

const form = reactive<PostTemplateSaveDTO>({
  id: undefined,
  templateCode: '',
  templateName: '',
  postSequence: '',
  description: '',
})

const rules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  postSequence: [{ required: true, message: '请输入岗位序列', trigger: 'blur' }],
}

onMounted(() => {
  const id = route.query.id as string
  if (id) {
    isEdit.value = true
    loadTemplate(Number(id))
  }
})

async function loadTemplate(id: number) {
  loading.value = true
  try {
    const res = await getTemplate(id)
    Object.assign(form, res.data)
    form.id = res.data.id
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    if (isEdit.value && form.id) {
      await updateTemplate(form.id, form)
    } else {
      const { templateCode: _templateCode, ...createPayload } = form
      await saveTemplate(createPayload)
    }
    ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
    router.back()
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover" v-loading="loading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>{{ isEdit ? '编辑模板' : '新增模板' }}</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width: 800px;">
        <el-form-item v-if="isEdit" label="模板编码">
          <el-input v-model="form.templateCode" readonly />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="岗位序列" prop="postSequence">
          <el-input v-model="form.postSequence" placeholder="请输入岗位序列" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
