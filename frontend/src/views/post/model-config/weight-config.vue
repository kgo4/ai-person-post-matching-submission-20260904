<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getTagTree,
  listPostModels,
  saveModelConfig,
  updateModelConfig,
  batchModelConfig,
} from '@/api'
import type { PostAbilityModel, PostAbilityModelConfigDTO } from '@/api'
import { buildAbilityTagNameMap, resolveAbilityTagName } from '@/utils/abilityTagName'
import { normalizeLegacyRelativeWeights } from './weight-normalization'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const tagNameMap = ref(new Map<number, string>())

const postId = ref(Number(route.query.postId) || 0)

const weightItems = ref<PostAbilityModel[]>([])
const totalWeight = computed(() =>
  weightItems.value.reduce((s, item) => s + item.weight, 0)
)

onMounted(() => {
  if (postId.value) {
    loadConfigs()
  }
})

async function loadConfigs() {
  loading.value = true
  try {
    await ensureTagNameMap()
    const res = await listPostModels(postId.value)
    const rawItems = res.data || []
    const normalizedWeights = normalizeLegacyRelativeWeights(rawItems.map(item => Number(item.weight)))
    weightItems.value = rawItems.map((item, index) => ({
      ...item,
      weight: normalizedWeights[index],
      tagName: resolveAbilityTagName(item, tagNameMap.value),
    }))
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function ensureTagNameMap() {
  if (tagNameMap.value.size > 0) return
  const res = await getTagTree()
  tagNameMap.value = buildAbilityTagNameMap(res.data || [])
}

async function handleSave() {
  if (weightItems.value.length === 0) {
    ElMessage.warning('暂无配置数据')
    return
  }
  if (weightItems.value.some((item) => item.tagId == null && !item.abilityName?.trim())) {
    ElMessage.error('存在未命名且未关联标签的岗位能力，请先在岗位能力模型配置页补充名称')
    return
  }
  const sum = totalWeight.value
  if (sum !== 100) {
    ElMessage.warning('权重总和必须为100，当前为' + sum)
    return
  }
  loading.value = true
  try {
    const dtoList: PostAbilityModelConfigDTO[] = weightItems.value.map((item) => ({
      id: item.id,
      postId: postId.value,
      tagId: item.tagId,
      abilityName: item.abilityName,
      minRequiredLevel: item.minRequiredLevel,
      weight: item.weight,
      isRequired: item.isRequired,
      isCore: item.isCore,
      remark: item.remark,
    }))
    await batchModelConfig(dtoList)
    ElMessage.success('权重配置保存成功')
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
          <span>权重 & 规则配置</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-alert
        :title="'权重总和：' + totalWeight + '%（需为100%）'"
        :type="totalWeight === 100 ? 'success' : 'warning'"
        show-icon
        :closable="false"
        style="margin-bottom: 16px;"
      />

      <el-table :data="weightItems" border>
        <el-table-column prop="tagName" label="能力项" min-width="160" />
        <el-table-column label="权重 (%)">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="100" :step="5" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="最低分数线">
          <template #default="{ row }">
            <el-input-number v-model="row.minRequiredLevel" :min="0" :max="100" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="是否必须达标">
          <template #default="{ row }">
            <el-switch v-model="row.isRequired" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="是否核心">
          <template #default="{ row }">
            <el-switch v-model="row.isCore" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
      </el-table>

      <div v-if="weightItems.length === 0" style="padding: 40px; text-align: center;">
        <el-empty description="暂无权重要配置数据，请先在模型配置中选择岗位和能力项" />
      </div>

      <div style="text-align: center; margin-top: 24px;">
        <el-button type="primary" @click="handleSave">保存</el-button>
      </div>
    </el-card>
  </div>
</template>
