<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { register as registerApi } from '@/api/system'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const confirmPwd = ref('')

const form = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
})

const checkPassword = (_rule: any, value: string, callback: any) => {
  if (!value) callback(new Error('请确认密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度3-20位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于6位', trigger: 'blur' },
  ],
  confirmPwd: [{ validator: checkPassword, trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await registerApi(form as any)
    ElMessage.success('注册成功，正在登录...')
    // 注册后自动登录
    await userStore.login({ username: form.username, password: form.password } as any)
    router.push('/dashboard')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-bg-grid"></div>
    <div class="login-card">
      <div class="login-header">
        <span class="eyebrow">Create account</span>
        <h2>创建账号</h2>
        <p>岗位和能力图谱构建与动态演化分析</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" clearable />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password clearable />
        </el-form-item>
        <el-form-item prop="confirmPwd">
          <el-input v-model="confirmPwd" type="password" placeholder="确认密码" show-password clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleRegister">
            注 册
          </el-button>
        </el-form-item>
        <div class="footer-link">
          已有账号？<router-link to="/login">返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 18%, rgba(37, 99, 235, 0.28), transparent 28%),
    radial-gradient(circle at 78% 18%, rgba(20, 184, 166, 0.24), transparent 30%),
    linear-gradient(135deg, #08111f 0%, #111827 48%, #0f172a 100%);
}
.login-container::before,
.login-container::after {
  content: "";
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  filter: blur(16px);
  opacity: 0.34;
}
.login-container::before {
  left: -140px;
  bottom: -120px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.8), transparent 66%);
}
.login-container::after {
  right: -160px;
  top: -130px;
  background: radial-gradient(circle, rgba(45, 212, 191, 0.7), transparent 68%);
}
.login-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at center, black, transparent 76%);
}
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.90);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(24px);
}
.login-header { text-align: left; margin-bottom: 32px; }
.eyebrow {
  display: inline-flex;
  margin-bottom: 10px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.login-header h2 { font-size: 28px; color: #172033; margin-bottom: 8px; }
.login-header p { color: #667085; font-size: 14px; }
.login-btn { width: 100%; height: 44px; }
.footer-link { text-align: center; font-size: 14px; color: #667085; }
.footer-link a { color: #2563eb; font-weight: 700; text-decoration: none; }

@media (max-width: 520px) {
  .login-card {
    width: calc(100vw - 32px);
    padding: 30px 22px;
  }
}
</style>
