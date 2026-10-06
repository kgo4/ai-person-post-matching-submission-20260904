<script setup lang="ts">
/**
 * 个人中心。
 *
 * 顶栏右上角账号入口的落地页：维护**自己的**资料（头像 / 手机号 / 邮箱）、改密码、退出登录。
 * 所有账号共用一个页面，不做角色分支 —— 需要改用户名/姓名/角色这类管理端字段的，
 * 请走系统管理里的用户管理（需要 USER:MANAGE）。
 *
 * 数据走 /system/user/profile（手机号不脱敏），而不是全局身份载荷 /current
 * （那里的手机号是打码的，用于个人中心会把「138****0000」写回数据库）。
 *
 * 提交策略：**头像单独提交（上传即落库）**，手机号/邮箱由「保存」按钮提交。
 * 头像是一个意图明确的独立动作，混进表单只会多出"上传了但忘了保存"的失败态。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, Key, SwitchButton, User } from '@element-plus/icons-vue'
import { changePassword, getMyProfile, updateMyProfile, uploadMyAvatar } from '@/api/system'
import type { MyProfileVO } from '@/api/system/types'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const profile = ref<MyProfileVO | null>(null)
/** 头像访问路径：单独一份本地状态，与顶栏共享同一个来源 */
const avatarPreview = ref<string | null>(null)

const form = reactive({
  phone: '',
  email: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const displayName = computed(() => profile.value?.realName || profile.value?.username || '系统用户')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleText = computed(() => (profile.value?.roles ?? []).join(' / ') || '未分配角色')
/** 是否设置了自定义头像 —— 空串/null 都按「未设置」处理，回落到首字头像 */
const hasAvatar = computed(() => Boolean(avatarPreview.value))
/** 相对路径拼到后端静态资源前缀上，绝对路径（http/斜杠开头）原样使用 */
const avatarUrl = computed(() => toAssetUrl(avatarPreview.value))

/**
 * 头像路径 -> 可直接用于 img src 的地址。
 *
 * 直接用后端下发的值：它要么是站点根相对路径（/uploads/avatars/xxx.png），
 * 要么是完整 URL —— 两种都能直接给 img 用。关键是**不要**拼 VITE_APP_BASE_API（/api），
 * /uploads/** 由后端静态资源映射暴露在站点根下，拼成 /api/uploads/... 会 404。
 */
function toAssetUrl(path: string | null): string {
  return path || ''
}

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  try {
    const res = await getMyProfile()
    profile.value = res.data
    avatarPreview.value = res.data?.avatar ?? null
    form.phone = res.data?.phone ?? ''
    form.email = res.data?.email ?? ''
  } catch {
    ElMessage.error('个人资料加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 头像上传前校验：与后端白名单/大小限制保持一致，避免白跑一趟 */
function beforeAvatarUpload(file: File) {
  const allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / GIF / WebP 格式的头像')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 2MB')
    return false
  }
  return true
}

async function handleAvatarChange(uploadFile: { raw?: File }) {
  const file = uploadFile.raw
  if (!file || !beforeAvatarUpload(file)) return
  uploading.value = true
  try {
    const res = await uploadMyAvatar(file)
    // 上传即落库，不等「保存资料」。
    // 原先是两步（先上传拿到路径、预览，再点保存写入），但这会让人以为
    // "上传了却没变" —— 头像是一个独立且意图明确的动作，没必要跟
    // 手机号/邮箱绑在同一次提交里，多一个"忘了点保存"的失败态。
    await persistAvatar(res.data)
    ElMessage.success('头像已更新')
  } catch {
    ElMessage.error('头像上传失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}

/** 恢复默认头像：同样立即落库（把 avatar 清空） */
async function resetAvatar() {
  try {
    await persistAvatar(null)
    ElMessage.success('已恢复默认头像')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/**
 * 头像落库 + 同步顶栏 + 刷新本地。
 *
 * 只提交 avatar 一个字段：DTO 里其余字段为 undefined → JSON 不含该键 →
 * 后端按「本次不改这一项」处理，因此不会顺带把未保存的手机号/邮箱写进去。
 */
async function persistAvatar(path: string | null) {
  await updateMyProfile({ avatar: path ?? '' })
  avatarPreview.value = path
  userStore.setAvatar(path)
  await loadProfile()
}

async function saveProfile() {
  saving.value = true
  try {
    // 只提交联系方式：头像在「上传即落库」时已经写过了，
    // 这里再带上 avatar 只会在用户没换头像时做一次无意义的写入。
    await updateMyProfile({
      phone: form.phone.trim(),
      email: form.email.trim(),
    })
    ElMessage.success('联系方式已保存')
    await loadProfile()
  } catch {
    ElMessage.error('保存失败，请检查手机号与邮箱格式')
  } finally {
    saving.value = false
  }
}

async function submitPasswordChange() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写旧密码与新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改，请重新登录')
    handleLogout()
  } catch {
    ElMessage.error('密码修改失败，请确认旧密码是否正确')
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div v-loading="loading" class="pc">
    <header class="pc__hero">
      <div class="pc__hero-copy">
        <h1>个人中心</h1>
        <p>维护头像与联系方式、修改密码；角色与权限由管理员分配，此处只读。</p>
      </div>
    </header>

    <div class="pc__grid">
      <!-- 左：身份卡 -->
      <section class="pc__card pc__card--identity">
        <div class="pc__avatar-block">
          <el-upload
            class="pc__avatar-upload"
            :show-file-list="false"
            :auto-upload="false"
            accept="image/jpeg,image/png,image/gif,image/webp"
            :disabled="uploading"
            @change="handleAvatarChange"
          >
            <div class="pc__avatar" :class="{ 'is-busy': uploading }">
              <img v-if="hasAvatar" :src="avatarUrl" :alt="displayName" />
              <span v-else>{{ initial }}</span>
              <div class="pc__avatar-mask">
                <el-icon><Camera /></el-icon>
                <small>{{ uploading ? '上传中' : '更换头像' }}</small>
              </div>
            </div>
          </el-upload>
          <button v-if="hasAvatar" class="pc__avatar-reset" type="button" @click="resetAvatar">
            恢复默认头像
          </button>
          <small class="pc__avatar-hint">点击头像即可更换，上传后立即生效</small>
        </div>

        <div class="pc__identity">
          <strong class="pc__identity-name">{{ displayName }}</strong>
          <span class="pc__identity-account">@{{ profile?.username || '-' }}</span>
          <el-tag size="small" effect="plain" class="pc__identity-role">{{ roleText }}</el-tag>
        </div>

        <dl class="pc__meta">
          <div class="pc__meta-item">
            <dt><el-icon><User /></el-icon>人员档案</dt>
            <dd v-if="profile?.empId">已绑定（ID {{ profile.empId }}）</dd>
            <dd v-else class="is-muted">未绑定</dd>
          </div>
          <div class="pc__meta-item">
            <dt>最后登录</dt>
            <dd>{{ (profile?.lastLoginTime || '--').replace('T', ' ').slice(0, 16) }}</dd>
          </div>
        </dl>
      </section>

      <!-- 右：资料表单 + 密码 + 退出 -->
      <section class="pc__card">
        <header class="pc__card-head">
          <h2>联系方式</h2>
          <p>头像上传后立即生效；手机号与邮箱点击下方「保存」后生效。用户名与姓名由账号体系维护。</p>
        </header>

        <el-form label-position="top" class="pc__form">
          <el-form-item label="用户名">
            <el-input :model-value="profile?.username" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input :model-value="profile?.realName" disabled />
          </el-form-item>
          <div class="pc__form-row">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" clearable />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" maxlength="128" placeholder="请输入邮箱" clearable />
            </el-form-item>
          </div>
          <div class="pc__actions">
            <el-button type="primary" :loading="saving" @click="saveProfile">保存联系方式</el-button>
            <el-button @click="loadProfile">重置</el-button>
          </div>
        </el-form>

        <el-divider />

        <header class="pc__card-head">
          <h2><el-icon><Key /></el-icon>修改密码</h2>
          <p>修改成功后需要重新登录。</p>
        </header>
        <el-form label-position="top" class="pc__form">
          <el-form-item label="旧密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
          </el-form-item>
          <div class="pc__form-row">
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
          </div>
          <div class="pc__actions">
            <el-button @click="submitPasswordChange">确认修改密码</el-button>
          </div>
        </el-form>

        <el-divider />

        <div class="pc__logout">
          <div>
            <strong>退出登录</strong>
            <p>退出后需重新输入账号密码。</p>
          </div>
          <el-button type="danger" plain :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/*
 * 与工作台共用一套卡片规范（白底 + 1px 边框 + radius-lg + shadow-sm），
 * 避免同一个系统里出现两种"卡片"。
 */
.pc {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 8px;
  color: var(--app-text);
}

.pc__hero {
  padding: 20px 22px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}

.pc__hero-copy h1 {
  margin: 0;
  color: var(--app-text-strong);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.pc__hero-copy p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.pc__grid {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.pc__card {
  padding: 20px 22px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}

.pc__card--identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.pc__avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.pc__avatar-upload :deep(.el-upload) {
  display: block;
}

.pc__avatar {
  position: relative;
  display: grid;
  width: 96px;
  height: 96px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--app-primary), var(--app-accent-alt));
  font-size: 34px;
  font-weight: 800;
  cursor: pointer;
}

.pc__avatar.is-busy {
  opacity: 0.6;
}

.pc__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pc__avatar-mask {
  position: absolute;
  inset: auto 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 0 8px;
  background: rgba(15, 23, 42, 0.55);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.pc__avatar:hover .pc__avatar-mask {
  opacity: 1;
}

.pc__avatar-mask small {
  font-size: 10px;
  font-weight: 600;
}

.pc__avatar-reset {
  border: 0;
  background: transparent;
  color: var(--app-text-muted);
  font-size: 11px;
  cursor: pointer;
}

.pc__avatar-reset:hover {
  color: var(--app-accent);
}

.pc__avatar-hint {
  color: var(--app-text-muted);
  font-size: 11px;
}

.pc__identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
}

.pc__identity-name {
  color: var(--app-text-strong);
  font-size: 16px;
  font-weight: 700;
}

.pc__identity-account {
  color: var(--app-text-muted);
  font-size: 12px;
}

.pc__identity-role {
  margin-top: 4px;
}

.pc__meta {
  width: 100%;
  margin: 18px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--app-divider);
  text-align: left;
}

.pc__meta-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 5px 0;
}

.pc__meta-item dt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.pc__meta-item dd {
  margin: 0;
  color: var(--app-text);
  font-size: 12px;
  font-weight: 600;
}

.pc__meta-item dd.is-muted {
  color: var(--app-text-muted);
  font-weight: 500;
}

.pc__card-head h2 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--app-text-strong);
  font-size: 15px;
  font-weight: 700;
}

.pc__card-head p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.pc__form {
  margin-top: 14px;
}

.pc__form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0 16px;
}

.pc__actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.pc__logout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pc__logout strong {
  color: var(--app-text-strong);
  font-size: 14px;
  font-weight: 700;
}

.pc__logout p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .pc__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
