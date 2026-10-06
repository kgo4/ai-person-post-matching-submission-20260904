<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">企业 AI 模型配置</h1>
      <p class="page-desc">系统只配置一个企业自部署的全局模型，所有文本类 AI 业务统一使用它。密钥加密保存，接口永不返回明文。</p>
    </div>

    <el-card v-loading="loading">
      <el-form :model="form" label-width="140px" style="max-width: 640px">
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
          <span style="margin-left: 10px; color: #8a93a6; font-size: 12px">关闭后 AI 文本业务返回明确失败，不会回退到任何内置厂商模型</span>
        </el-form-item>
        <el-form-item label="网关地址">
          <el-input v-model="form.baseUrl" placeholder="https://your-gateway.example.com/v1" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" placeholder="model-name" />
        </el-form-item>
        <el-form-item label="API 密钥">
          <el-input v-model="form.apiKey" type="password" show-password
                    :placeholder="form.apiKeyConfigured ? '已配置（留空则保留旧密钥）' : '请输入 API 密钥'" />
        </el-form-item>
        <el-form-item label="超时（秒）">
          <el-input-number v-model="form.timeoutSeconds" :min="300" :max="300" />
        </el-form-item>
        <el-divider content-position="left">AI 运行参数</el-divider>
        <p class="runtime-param-hint">作用于当前启用的语言模型，与企业模型连接配置独立。</p>
        <el-form-item label="温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="AI 测试题数">
          <el-input-number v-model="form.testQuestionCount" :min="3" :max="10" :step="1" />
        </el-form-item>
        <el-form-item label="AI 面试题数">
          <el-input-number v-model="form.interviewQuestionCount" :min="3" :max="10" :step="1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button :loading="checking" @click="healthCheck">连通性检查</el-button>
          <span v-if="checkResult" style="margin-left: 12px">
            <el-tag :type="checkResult.ok ? 'success' : 'danger'">
              {{ checkResult.ok ? '连接正常' : checkResult.reason || '连接失败' }}
            </el-tag>
          </span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiModelConfig, saveAiModelConfig, healthCheckAiModel, type SystemAiModelConfig } from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const checking = ref(false)
const checkResult = ref<Record<string, any> | null>(null)
const form = reactive<SystemAiModelConfig>({
  id: 1,
  enabled: false,
  baseUrl: '',
  modelName: '',
  apiKey: '',
  apiKeyConfigured: false,
  timeoutSeconds: 300,
  temperature: 0.2,
  testQuestionCount: 5,
  interviewQuestionCount: 6,
})

async function load() {
  loading.value = true
  try {
    const res = await getAiModelConfig()
    const data = res.data
    if (data) {
      form.id = data.id ?? 1
      form.enabled = data.enabled ?? false
      form.baseUrl = data.baseUrl ?? ''
      form.modelName = data.modelName ?? ''
      form.apiKeyConfigured = data.apiKeyConfigured ?? false
      form.timeoutSeconds = 300
      form.temperature = data.temperature ?? 0.2
      form.testQuestionCount = data.testQuestionCount ?? 5
      form.interviewQuestionCount = data.interviewQuestionCount ?? 6
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveAiModelConfig({ ...form })
    ElMessage.success('保存成功，已原子替换全局模型实例')
    await load()
  } finally {
    saving.value = false
  }
}

async function healthCheck() {
  checking.value = true
  try {
    const res = await healthCheckAiModel()
    checkResult.value = res.data
  } finally {
    checking.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 20px; margin: 0 0 6px; }
.page-desc { color: #8a93a6; font-size: 13px; margin: 0; }
.runtime-param-hint { margin: -4px 0 16px 140px; color: #8a93a6; font-size: 12px; }
</style>
