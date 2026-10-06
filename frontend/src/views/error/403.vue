<script setup lang="ts">
/**
 * 403 无权限页。
 *
 * 与 404 分开是必要的：路由守卫在角色 / 权限不足时跳 /403，
 * 如果复用 404 组件，用户会把「权限不足」误读为「页面不存在」，
 * 进而反复点「返回首页」而不知道为什么进不去。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { primaryRoleLabel } from '@/utils/role-label'

const router = useRouter()
const userStore = useUserStore()

/** 展示当前角色，便于使用者判断该找谁开权限。文案映射统一来自 utils/role-label */
const roleLabel = computed(() => primaryRoleLabel(userStore.roles))

function goBack() {
  // 优先回退到上一页；没有历史记录时回工作台
  if (window.history.length > 1) router.back()
  else router.push('/workbench')
}
</script>

<template>
  <div class="error-page">
    <h1>403</h1>
    <p>抱歉，当前角色（{{ roleLabel }}）没有访问该页面的权限</p>
    <p class="error-page__hint">如需访问，请联系管理员为你的账号开通对应权限。</p>
    <div class="error-page__actions">
      <el-button @click="goBack">返回上一页</el-button>
      <el-button type="primary" @click="router.push('/workbench')">返回工作台</el-button>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: #f0f2f5;
}

.error-page h1 {
  font-size: 120px;
  color: #409eff;
  margin: 0;
}

.error-page p {
  font-size: 18px;
  color: #606266;
  margin: 20px 0 0;
}

.error-page__hint {
  margin-top: 8px !important;
  font-size: 13px !important;
  color: #909399 !important;
}

.error-page__actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}
</style>
