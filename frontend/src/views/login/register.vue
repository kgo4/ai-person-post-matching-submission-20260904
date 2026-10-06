<script setup lang="ts">
/**
 * 自助注册。
 *
 * 支持注册：员工 / HR / 岗位体系管理员 / 平台管理员（**超级管理员不开放注册**）。
 *
 * 三条硬性规则：
 *   1. **邮箱验证码**：60 秒内只能获取一次（倒计时前端展示、后端强制），
 *      验证码 5 分钟有效、连错 5 次作废。
 *   2. **角色授权码**：除员工外必填。授权码由**服务端环境变量**配置
 *      （`REGISTER_CODE_*`），仓库里没有任何默认值 —— 未配置的角色一律不可注册。
 *      这样即使前端被人反编译，也拿不到能注册管理角色的凭证。
 *   3. **手机号 + 邮箱都必填**：手机号目前只做留存与格式校验，验证码走邮箱
 *      （系统没有接短信通道；接短信服务商是另一件事，不要在这里假装能发短信）。
 */
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import Strands from '@/components/common/Strands.vue'
import { registerV2, sendRegisterEmailCode } from '@/api/system'
// 纯逻辑集中放在 register-logic.ts（可被 .test.mjs 直跑），SFC 只做渲染与调用，
// 避免"授权码何时必填 / 倒计时文案"这类不会报错的判断散落在模板里。
import {
  REGISTERABLE_ROLES,
  REGISTER_ROLE_LABELS,
  countdownLabel,
  isCountdownActive,
  isValidEmail,
  isValidEmailCode,
  isValidPhone,
  needsRoleAuthCode,
  type RegisterableRole,
} from './register-logic'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)

/** 重发倒计时（秒） */
const cooldown = ref(0)
let timer: number | undefined

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: '',
  emailCode: '',
  roleCode: 'EMPLOYEE' as RegisterableRole,
  roleAuthCode: '',
})

interface RoleOption {
  value: RegisterableRole
  label: string
  desc: string
  tag: string
  needAuth: boolean
}

/** 角色卡片：一句职责说明 + 是否需要授权码，避免用户盲选 */
const ROLE_DESCS: Record<RegisterableRole, { desc: string; tag: string; needAuth: boolean }> = {
  EMPLOYEE: { desc: '完成本人评估、查看匹配结果与学习路径', tag: '自助开通', needAuth: false },
  HR_SPECIALIST: { desc: '人员档案、评估运营、发起匹配与视频终面', tag: '需授权码', needAuth: true },
  JOB_ARCHITECT: { desc: '岗位建模、能力配置、全景图谱与岗位演化', tag: '需授权码', needAuth: true },
  PLATFORM_ADMIN: { desc: '用户角色、操作审计、AI 模型与系统配置', tag: '需授权码', needAuth: true },
}

const roleOptions: RoleOption[] = REGISTERABLE_ROLES.map(value => ({
  value,
  label: REGISTER_ROLE_LABELS[value],
  ...ROLE_DESCS[value],
}))

/** 只有员工免授权码；其余角色必填（由 register-logic 统一判定，与后端白名单同口径） */
const needsAuthCode = computed(() => needsRoleAuthCode(form.roleCode))

const sendButtonText = computed(() => countdownLabel(cooldown.value))

const checkConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (!value) callback(new Error('请确认密码'))
  else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,20}$/, message: '用户名需为 3-20 位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: (_r: any, v: string, cb: any) => (isValidPhone(v) ? cb() : cb(new Error('请输入正确的 11 位手机号'))), trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: (_r: any, v: string, cb: any) => (isValidEmail(v) ? cb() : cb(new Error('请输入正确的邮箱格式'))), trigger: 'blur' },
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { validator: (_r: any, v: string, cb: any) => (isValidEmailCode(v) ? cb() : cb(new Error('验证码为 6 位数字'))), trigger: 'blur' },
  ],
  roleCode: [{ required: true, message: '请选择注册角色', trigger: 'change' }],
  roleAuthCode: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        // 员工免授权码：留空合法；其他角色必填
        if (!needsAuthCode.value) return callback()
        if (!value || !value.trim()) return callback(new Error('该角色需要授权码'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 启动倒计时；重复调用先清旧的，避免叠加出两个定时器 */
function startCooldown(seconds: number) {
  cooldown.value = Math.max(1, Math.floor(seconds))
  if (timer) window.clearInterval(timer)
  timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      cooldown.value = 0
      if (timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }
  }, 1000)
}

onUnmounted(() => {
  // 组件卸载必须清定时器：不清就会在离开页面后继续跑，且下次进入会叠加
  if (timer) window.clearInterval(timer)
})

/** 切换角色时清掉上一条授权码，避免误把上一角色的码带过去 */
function handleRoleChange() {
  form.roleAuthCode = ''
  formRef.value?.clearValidate(['roleAuthCode'])
}

async function handleSendCode() {
  if (isCountdownActive(cooldown.value)) return
  if (!form.email.trim()) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  const emailValid = await formRef.value?.validateField('email').catch(() => false)
  if (emailValid === false) return

  sending.value = true
  try {
    const res = await sendRegisterEmailCode({ email: form.email.trim() })
    startCooldown(res.data?.cooldownSeconds ?? 60)
    if (res.data?.code) {
      // 联调模式：后端把验证码一并回传（生产关闭），直接填进去省去查邮件
      form.emailCode = res.data.code
      ElMessage.success(`验证码已生成（联调模式）：${res.data.code}`)
    } else {
      const minutes = Math.round((res.data?.expireSeconds ?? 300) / 60)
      ElMessage.success(`验证码已发送至 ${form.email.trim()}，${minutes} 分钟内有效`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '验证码发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await registerV2({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      phone: form.phone.trim(),
      email: form.email.trim(),
      emailCode: form.emailCode.trim(),
      roleCode: form.roleCode,
      roleAuthCode: needsAuthCode.value ? form.roleAuthCode.trim() : undefined,
    })
    /*
     * 注册成功后**不自动登录**，回登录页让用户自己登一次。
     *
     * 原因：角色授权（写 sys_user_role）与权限缓存失效发生在同一个注册请求内，
     * 紧接着的自动登录可能读到刚写入的旧权限快照并缓存 30 分钟 ——
     * 表现为「注册成功但所有接口 403，重新登录又好了」。
     * 让用户手动登录，权限一定是按最新角色加载的，绕开这个时序边界。
     */
    ElMessage.success('注册成功，请使用刚设置的账号登录')
    // 带上用户名，登录页自动回填，省得再敲一遍
    router.replace({ path: '/login', query: { username: form.username.trim(), registered: '1' } })
  } catch (e: any) {
    // 失败保持表单内容：验证码是一次性的，重新填一遍代价很高
    ElMessage.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-root">
    <div class="register-strands-bg">
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

    <div class="register-shell">
      <div class="register-brand">
        <span class="register-badge">Create Account</span>
        <h1 class="register-title">创建您的账号</h1>
        <p class="register-subtitle">
          多源异构岗位与能力图谱平台<br />
          角色化权限 · 数据范围隔离 · 邮箱验证
        </p>
      </div>

      <section class="register-form-panel">
        <div class="register-form-head">
          <h2>注册信息</h2>
          <span class="register-form-head__sub">按角色开通对应功能，带 <em>*</em> 为必填项</span>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          @keyup.enter="handleRegister"
        >
          <!-- 角色选择：卡片式，先明确身份再填信息 -->
          <el-form-item prop="roleCode" label="注册角色" class="role-item">
            <div class="role-grid">
              <button
                v-for="opt in roleOptions"
                :key="opt.value"
                type="button"
                class="role-card"
                :class="{ 'is-active': form.roleCode === opt.value }"
                @click="form.roleCode = opt.value; handleRoleChange()"
              >
                <span class="role-card__top">
                  <span class="role-card__label">{{ opt.label }}</span>
                  <span class="role-card__tag" :class="{ 'is-auth': opt.needAuth }">{{ opt.tag }}</span>
                </span>
                <span class="role-card__desc">{{ opt.desc }}</span>
              </button>
            </div>
          </el-form-item>

          <transition name="auth-slide">
            <el-form-item v-if="needsAuthCode" prop="roleAuthCode" class="auth-item">
              <template #label>
                角色授权码
                <span class="field-hint">由平台管理员提供（服务端配置，非登录密码）</span>
              </template>
              <el-input
                v-model="form.roleAuthCode"
                placeholder="请输入该角色的授权码"
                show-password
                clearable
                :prefix-icon="'Key'"
              />
            </el-form-item>
          </transition>

          <div class="section-divider"><span>账号信息</span></div>

          <el-form-item prop="username">
            <template #label>用户名 <em>*</em></template>
            <el-input v-model="form.username" placeholder="3-20 位字母、数字或下划线" clearable :prefix-icon="'User'" />
          </el-form-item>

          <div class="field-row">
            <el-form-item prop="realName">
              <template #label>真实姓名 <em>*</em></template>
              <el-input v-model="form.realName" placeholder="请输入真实姓名" clearable :prefix-icon="'Postcard'" />
            </el-form-item>
            <el-form-item prop="phone">
              <template #label>手机号码 <em>*</em></template>
              <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" clearable :prefix-icon="'Iphone'" />
            </el-form-item>
          </div>

          <el-form-item prop="email">
            <template #label>邮箱 <em>*</em></template>
            <el-input v-model="form.email" placeholder="用于接收注册验证码" clearable :prefix-icon="'Message'" />
          </el-form-item>

          <el-form-item prop="emailCode">
            <template #label>邮箱验证码 <em>*</em></template>
            <div class="code-row">
              <el-input v-model="form.emailCode" placeholder="6 位数字" maxlength="6" clearable :prefix-icon="'CircleCheck'" />
              <el-button class="code-btn" :disabled="isCountdownActive(cooldown)" :loading="sending" @click="handleSendCode">
                {{ sendButtonText }}
              </el-button>
            </div>
          </el-form-item>

          <div class="section-divider"><span>设置密码</span></div>

          <div class="field-row">
            <el-form-item prop="password">
              <template #label>密码 <em>*</em></template>
              <el-input v-model="form.password" type="password" placeholder="6-32 位" show-password clearable :prefix-icon="'Lock'" />
            </el-form-item>
            <el-form-item
              label="确认密码 *"
              prop="confirmPassword"
              :rules="[{ validator: checkConfirmPassword, trigger: 'blur' }]"
            >
              <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password clearable :prefix-icon="'Lock'" />
            </el-form-item>
          </div>

          <el-form-item class="submit-item">
            <el-button type="primary" :loading="loading" class="register-btn" @click="handleRegister">
              {{ loading ? '注册中…' : '注 册' }}
            </el-button>
          </el-form-item>

          <div class="register-footer">
            已有账号？<router-link to="/login">返回登录</router-link>
          </div>
        </el-form>
      </section>
    </div>

    <p class="register-version" aria-label="版本信息">
      KGO Graph Max <span aria-hidden="true">·</span> KGO Graph Max <span aria-hidden="true">·</span> 敬请期待
    </p>
  </div>
</template>

<style scoped>
.register-root {
  position: relative;
  min-height: 100vh;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: #ffffff;
}

.register-strands-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.register-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.register-brand {
  text-align: center;
}

.register-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.register-title {
  margin: 18px 0 0;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.3;
  font-weight: 900;
  letter-spacing: -0.02em;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.5);
}

.register-subtitle {
  margin: 12px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
  letter-spacing: 0.02em;
}

.register-form-panel {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(18px);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  padding: 30px 32px 26px;
}

.register-form-head {
  margin-bottom: 20px;
  text-align: center;
}

.register-form-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.register-form-head__sub {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.register-form-head__sub em,
.el-form-item :deep(label) em,
.el-form-item em {
  color: #ef4444;
  font-style: normal;
}

/* ---------- 角色卡片 ---------- */
.role-item :deep(.el-form-item__content) {
  display: block;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.5);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.role-card:hover {
  border-color: rgba(59, 130, 246, 0.45);
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.12);
}

.role-card.is-active {
  border-color: #2563eb;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.16), rgba(37, 99, 235, 0.08));
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14), 0 6px 18px rgba(37, 99, 235, 0.14);
}

.role-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.role-card__label {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.role-card__tag {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
  font-size: 10px;
  font-weight: 600;
}

.role-card__tag.is-auth {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.role-card__desc {
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

/* ---------- 分区标题 ---------- */
.section-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 0 14px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.section-divider::before,
.section-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(15, 23, 42, 0.12), transparent);
}

/* ---------- 表单控件 ---------- */
.register-form-panel :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px !important;
  border: 1px solid rgba(0, 0, 0, 0.1) !important;
  background: rgba(255, 255, 255, 0.5) !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06) !important;
  transition: all 0.25s ease !important;
}

.register-form-panel :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(59, 130, 246, 0.6) !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15), 0 4px 12px rgba(0, 0, 0, 0.08) !important;
}

.register-form-panel :deep(.el-input__inner) {
  color: #0f172a !important;
  font-size: 14px;
  font-weight: 500;
}

.register-form-panel :deep(.el-input__inner::placeholder) {
  color: #94a3b8 !important;
}

.register-form-panel :deep(.el-form-item__label) {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  padding-bottom: 4px;
}

.register-form-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}

.field-hint {
  margin-left: 6px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 400;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-btn {
  flex: 0 0 auto;
  min-width: 116px;
  border-radius: 14px !important;
  border-color: rgba(37, 99, 235, 0.4) !important;
  color: #1d4ed8 !important;
  font-weight: 600;
}

.code-btn:not(.is-disabled):hover {
  border-color: #2563eb !important;
  background: rgba(219, 234, 254, 0.6) !important;
}

.auth-slide-enter-active,
.auth-slide-leave-active {
  transition: all 0.25s ease;
}

.auth-slide-enter-from,
.auth-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.submit-item {
  margin-top: 18px;
  margin-bottom: 0 !important;
}

.register-btn {
  width: 100%;
  min-height: 48px !important;
  border-radius: 14px !important;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, #3b82f6, #2563eb) !important;
  border: none !important;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3) !important;
  transition: all 0.25s ease !important;
}

.register-btn:hover {
  box-shadow: 0 6px 24px rgba(37, 99, 235, 0.4) !important;
  transform: translateY(-1px);
}

.register-footer {
  margin-top: 16px;
  text-align: center;
  color: #475569;
  font-size: 13px;
}

.register-footer a {
  color: #2563eb;
  font-weight: 600;
}

.register-version {
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

@media (max-width: 640px) {
  .register-shell {
    max-width: 100%;
  }

  .register-title {
    font-size: 24px;
  }

  .register-form-panel {
    padding: 24px 20px;
  }

  .role-grid,
  .field-row {
    grid-template-columns: 1fr;
  }

  .register-version {
    right: auto;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    text-align: center;
  }
}
</style>
