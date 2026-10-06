<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 动态扩展字段表单数据
const form = reactive<Record<string, any>>({})

// 扩展字段配置（由后端返回）
const extendFields = ref<any[]>([])

async function handleSubmit() {
  // TODO: 调用API保存
  ElMessage.success('保存成功')
  router.back()
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>自定义扩展字段录入</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-width="120px" style="max-width: 700px;">
        <!-- 动态渲染扩展字段 -->
        <template v-if="extendFields.length > 0">
          <el-form-item
            v-for="field in extendFields"
            :key="field.fieldKey"
            :label="field.fieldName"
            :prop="field.fieldKey"
          >
            <!-- 文本 -->
            <el-input
              v-if="field.fieldType === 'text'"
              v-model="form[field.fieldKey]"
              :placeholder="field.placeholder"
            />
            <!-- 数字 -->
            <el-input-number
              v-else-if="field.fieldType === 'number'"
              v-model="form[field.fieldKey]"
            />
            <!-- 单选 -->
            <el-select
              v-else-if="field.fieldType === 'select'"
              v-model="form[field.fieldKey]"
              :placeholder="field.placeholder"
              style="width: 100%;"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- 多选 -->
            <el-select
              v-else-if="field.fieldType === 'multi-select'"
              v-model="form[field.fieldKey]"
              multiple
              :placeholder="field.placeholder"
              style="width: 100%;"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- 默认文本 -->
            <el-input v-else v-model="form[field.fieldKey]" :placeholder="field.placeholder" />
          </el-form-item>
        </template>
        <el-empty v-else description="暂无扩展字段配置" />

        <el-form-item v-if="extendFields.length > 0">
          <el-button type="primary" @click="handleSubmit" :loading="loading">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
