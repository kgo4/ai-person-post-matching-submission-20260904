<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import Strands from '@/components/common/Strands.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const loginError = ref('')

const loginForm = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loginError.value = ''
  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (error: any) {
    loginError.value = error.message || '登录失败，请检查用户名和密码'
    ElMessage.error(loginError.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-root">
    <div class="login-strands-bg">
      <Strands
        :colors="['#FF4242', '#7C3AED', '#06B6D4', '#EAB308']"
        :count="3"
        :speed="0.5"
        :amplitude="1"
        :waviness="1"
        :thickness="0.7"
        :glow="2.6"
        :taper="3"
        :spread="1"
        :intensity="0.6"
        :saturation="1.5"
        :opacity="1"
        :scale="1.5"
      />
      </div>

    <div class="login-shell">
      <div class="login-brand">
        <span class="login-badge">AI-Powered Matching</span>
        <h1 class="login-title">多源异构岗位与能力图谱平台</h1>
        <p class="login-subtitle">简历解析 · AI面试 · 项目分析<br/>多源数据统一构建能力图谱，驱动人岗精准匹配</p>
      </div>

      <section class="login-form-panel">
        <div class="login-form-head">
          <h2>Welcome Back</h2>
          <span class="login-form-head__sub">登录图谱平台，开启智能匹配</span>
        </div>

        <el-form ref="loginFormRef" :model="loginForm" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-alert
            v-if="loginError"
            :title="loginError"
            type="error"
            show-icon
            :closable="false"
            class="login-alert"
          />

          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="'User'" />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="'Lock'"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">登录</el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          还没有账号？<router-link to="/register">注册账号</router-link>
        </div>
      </section>
    </div>

    <p class="login-version" aria-label="版本信息">
      KGO Graph Pro <span aria-hidden="true">·</span> KGO Graph Max <span aria-hidden="true">·</span> 敬请期待
    </p>
  </div>
</template>

<style scoped>
.login-root {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.login-strands-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.login-brand {
  text-align: center;
}

.login-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #3B82F6;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.login-title {
  margin: 20px 0 0;
  color: #0f172a;
  font-size: 32px;
  line-height: 1.3;
  font-weight: 900;
  letter-spacing: -0.02em;
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.5);
}

.login-subtitle {
  margin: 16px 0 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.8;
  letter-spacing: 0.02em;
  text-shadow: 0 1px 1px rgba(255, 255, 255, 0.4);
}

.login-form-panel {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(16px);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  padding: 32px;
}

.login-form-head {
  margin-bottom: 24px;
  text-align: center;
}

.login-form-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  text-shadow: 0 1px 1px rgba(255, 255, 255, 0.3);
}

.login-form-head__sub {
  display: block;
  margin-top: 6px;
  color: #475569;
  font-size: 13px;
}

.login-alert {
  margin-bottom: 16px;
}

.login-form-panel :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 14px !important;
  border: 1px solid rgba(0, 0, 0, 0.1) !important;
  background: rgba(255, 255, 255, 0.5) !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06) !important;
  transition: all 0.25s ease !important;
}

.login-form-panel :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(59, 130, 246, 0.6) !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15), 0 4px 12px rgba(0, 0, 0, 0.08) !important;
}

.login-form-panel :deep(.el-input__inner) {
  color: #0f172a !important;
  font-size: 14px;
  font-weight: 500;
}

.login-form-panel :deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}

.login-btn {
  width: 100%;
  min-height: 48px !important;
  border-radius: 14px !important;
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.02em;
  background: linear-gradient(135deg, #3B82F6, #2563EB) !important;
  border: none !important;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3) !important;
  transition: all 0.25s ease !important;
}

.login-btn:hover {
  box-shadow: 0 6px 24px rgba(37, 99, 235, 0.4) !important;
  transform: translateY(-1px);
}

.login-footer {
  margin-top: 16px;
  text-align: center;
  color: #475569;
  font-size: 13px;
}

.login-footer a {
  color: #2563EB;
  font-weight: 600;
}

.login-version {
  position: fixed;
  right: 24px;
  bottom: 18px;
  z-index: 1;
  margin: 0;
  color: rgba(71, 85, 105, 0.72);
  font-size: 12px;
  line-height: 1.5;
  pointer-events: none;
}

@media (max-width: 768px) {
  .login-root {
    padding: 20px;
  }

  .login-shell {
    max-width: 100%;
    gap: 20px;
  }

  .login-title {
    font-size: 24px;
    white-space: normal;
  }

  .login-subtitle {
    font-size: 13px;
  }

  .login-form-panel {
    padding: 24px 20px;
  }

  .login-form-head h2 {
    font-size: 22px;
  }

  .login-version {
    right: auto;
    bottom: 12px;
    left: 50%;
    max-width: calc(100% - 32px);
    text-align: center;
    transform: translateX(-50%);
  }
}
</style>
