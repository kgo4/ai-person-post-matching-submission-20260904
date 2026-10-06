<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPost, getTagTree, listPostHardConditionRules, listPostModels } from '@/api'
import type { PostAbilityModel, PostHardConditionRule, PostPost } from '@/api'
import { buildAbilityTagNameMap, resolveAbilityTagName } from '@/utils/abilityTagName'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const modelLoading = ref(false)
const ruleLoading = ref(false)
const activeTab = ref('basic')
const tagNameMap = ref(new Map<number, string>())

const post = ref<PostPost>({
  id: Number(route.params.id),
  postCode: '',
  postName: '',
  jobDescription: '',
  templateId: 0,
  status: 1,
  createdTime: '',
})

const models = ref<PostAbilityModel[]>([])
const hardRules = ref<PostHardConditionRule[]>([])

onMounted(() => {
  loadPost()
})

async function loadPost() {
  loading.value = true
  try {
    const res = await getPost(Number(route.params.id))
    post.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  modelLoading.value = true
  try {
    await ensureTagNameMap()
    const res = await listPostModels(Number(route.params.id))
    models.value = (res.data || []).map((item) => ({
      ...item,
      tagName: resolveAbilityTagName(item, tagNameMap.value),
    }))
  } finally {
    modelLoading.value = false
  }
}

async function ensureTagNameMap() {
  if (tagNameMap.value.size > 0) return
  const res = await getTagTree()
  tagNameMap.value = buildAbilityTagNameMap(res.data || [])
}

async function loadHardRules() {
  ruleLoading.value = true
  try {
    const res = await listPostHardConditionRules(Number(route.params.id))
    hardRules.value = res.data
  } finally {
    ruleLoading.value = false
  }
}

function handleTabChange(tabName: string) {
  if (tabName === 'model' && models.value.length === 0) loadModels()
  if (tabName === 'hardRule' && hardRules.value.length === 0) loadHardRules()
}
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Post Detail</div>
        <h1 class="page-hero__title">{{ post.postName || '岗位详情' }}</h1>
        <p class="page-hero__desc">查看岗位基础信息、能力模型和硬性筛选规则，作为后续匹配执行的岗位标准。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">岗位编码 {{ post.postCode }}</span>
          <span class="hero-chip">模板 {{ post.templateId || '--' }}</span>
          <span class="hero-chip">{{ post.status === 1 ? '启用中' : '已禁用' }}</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-button @click="router.back()">返回</el-button>
      </div>
    </section>

    <section class="info-grid">
      <article class="info-card">
        <div class="info-card__label">岗位名称</div>
        <div class="info-card__value">{{ post.postName || '--' }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">岗位编码</div>
        <div class="info-card__value">{{ post.postCode || '--' }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">模板 ID</div>
        <div class="info-card__value">{{ post.templateId || '--' }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">状态</div>
        <div class="info-card__value">{{ post.status === 1 ? '启用' : '禁用' }}</div>
      </article>
    </section>

    <section class="glass-card" v-loading="loading">
      <div class="panel-body">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="基础信息" name="basic">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="岗位名称">{{ post.postName }}</el-descriptions-item>
              <el-descriptions-item label="岗位编码">{{ post.postCode }}</el-descriptions-item>
              <el-descriptions-item label="关联模板 ID">{{ post.templateId || '--' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ post.status === 1 ? '启用' : '禁用' }}</el-descriptions-item>
              <el-descriptions-item label="职位描述" :span="2">{{ post.jobDescription || '--' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="能力模型" name="model">
            <el-table :data="models" v-loading="modelLoading" style="width: 100%">
              <el-table-column prop="tagName" label="能力标签" min-width="160" />
              <el-table-column prop="minRequiredLevel" label="最低要求等级" width="140" />
              <el-table-column prop="weight" label="权重" width="100" />
              <el-table-column prop="isCore" label="是否核心" width="110">
                <template #default="{ row }">{{ row.isCore === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="isRequired" label="是否必填" width="110">
                <template #default="{ row }">{{ row.isRequired === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" />
            </el-table>
            <el-empty v-if="!modelLoading && models.length === 0" description="暂无能力模型数据" />
          </el-tab-pane>

          <el-tab-pane label="硬性条件" name="hardRule">
            <el-table :data="hardRules" v-loading="ruleLoading" style="width: 100%">
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column prop="fieldName" label="字段名" width="140" />
              <el-table-column prop="fieldLabel" label="显示名称" width="140" />
              <el-table-column prop="fieldType" label="字段类型" width="120" />
              <el-table-column prop="operator" label="运算符" width="100" />
              <el-table-column prop="expectedValue" label="期望值" min-width="160" />
              <el-table-column prop="enabled" label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="80" />
              <el-table-column prop="remark" label="备注" />
            </el-table>
            <el-empty v-if="!ruleLoading && hardRules.length === 0" description="暂无硬性条件规则" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </div>
</template>
