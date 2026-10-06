<script setup lang="ts">
/**
 * /employee/ability-profile 的角色分流入口。
 *
 * 同一个 URL 需要承载两种完全不同的业务：
 * - 持有 EMPLOYEE:READ 的管理端角色 → 员工档案列表（管理视角，可查看他人）；
 * - 仅有 ASSESSMENT:SELF 的员工 → 本人能力画像（仅本人）。
 * 把分流放在独立入口组件里，避免在 1300 行的管理页内部继续堆角色判断。
 */
import { computed, defineAsyncComponent } from 'vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

const AdminProfiles = defineAsyncComponent(() => import('./index.vue'))
const MyProfile = defineAsyncComponent(() => import('./my-profile.vue'))

/** 能读全员档案的才走管理视图；其余（员工本人）走本人画像 */
const isManagementView = computed(() => userStore.permissions.includes('EMPLOYEE:READ'))
</script>

<template>
  <AdminProfiles v-if="isManagementView" />
  <MyProfile v-else />
</template>
