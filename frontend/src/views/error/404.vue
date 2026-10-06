<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <h1>404</h1>
    <p>抱歉，您访问的页面不存在</p>
    <el-button type="primary" @click="router.push('/')">返回首页</el-button>
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
  margin: 20px 0 32px;
}
</style>
