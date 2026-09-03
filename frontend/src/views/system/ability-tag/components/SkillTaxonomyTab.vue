<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSkillTaxonomyRule,
  deleteSkillTaxonomyRule,
  getTagTree,
  pageSkillTaxonomyRules,
  updateSkillTaxonomyRule,
  updateSkillTaxonomyRuleStatus,
} from '@/api'
import type { AbilityTagTreeVO, SkillTaxonomyMap } from '@/api'

const loading = ref(false)
const keyword = ref('')
const rows = ref<SkillTaxonomyMap[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

// 能力层标签（tagLevel=1）作为归属选项，全量标签做 id→名称映射
const abilityOptions = ref<AbilityTagTreeVO[]>([])
const tagNameMap = ref<Record<number, string>>({})

const categoryLabel: Record<string, string> = {
  TECHNICAL: '技术',
  SOFT: '软技能',
  BUSINESS: '业务',
}

function flattenTags(nodes: AbilityTagTreeVO[]) {
  const map: Record<number, string> = {}
  const abilities: AbilityTagTreeVO[] = []
  const walk = (list: AbilityTagTreeVO[]) => {
    for (const n of list) {
      map[n.id] = n.tagName
      if (n.tagLevel === 1) abilities.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
  tagNameMap.value = map
  abilityOptions.value = abilities
}

async function loadTags() {
  const res = await getTagTree()
  flattenTags(res.data ?? [])
}

async function loadRules() {
  loading.value = true
  try {
    const res = await pageSkillTaxonomyRules({
      current: current.value,
      size: size.value,
      keyword: keyword.value || undefined,
    })
    rows.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<SkillTaxonomyMap>({
  skillName: '',
  abilityTagId: 0,
  category: 'TECHNICAL',
  confidence: 1,
  source: 'MANUAL',
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    id: undefined,
    skillName: '',
    abilityTagId: 0,
    category: 'TECHNICAL',
    confidence: 1,
    source: 'MANUAL',
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: SkillTaxonomyMap) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.skillName?.trim()) {
    ElMessage.warning('请输入技能词')
    return
  }
  if (!form.abilityTagId) {
    ElMessage.warning('请选择归属能力标签')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateSkillTaxonomyRule(editingId.value, { ...form })
    } else {
      await createSkillTaxonomyRule({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRules()
  } finally {
    saving.value = false
  }
}

async function handleToggleStatus(row: SkillTaxonomyMap) {
  const target = row.status === 1 ? 0 : 1
  await updateSkillTaxonomyRuleStatus(row.id!, target)
  ElMessage.success('操作成功')
  await loadRules()
}

async function handleDelete(row: SkillTaxonomyMap) {
  await ElMessageBox.confirm(`确认删除规则「${row.skillName}」？`, '提示', { type: 'warning' })
  await deleteSkillTaxonomyRule(row.id!)
  ElMessage.success('已删除')
  await loadRules()
}

onMounted(() => {
  loadTags()
  loadRules()
})
</script>

<template>
  <section class="taxonomy-tab">
    <div class="taxonomy-tab__toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索技能词..."
        clearable
        style="width: 240px"
        @keyup.enter="loadRules"
        @clear="loadRules"
      />
      <el-button type="primary" @click="loadRules">查询</el-button>
      <div class="taxonomy-tab__spacer" />
      <el-button type="primary" @click="openCreate">新增规则</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" style="width: 100%">
      <el-table-column prop="skillName" label="技能词" min-width="160" />
      <el-table-column label="归属能力" min-width="160">
        <template #default="{ row }">
          <span>{{ tagNameMap[row.abilityTagId] || `#${row.abilityTagId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100" align="center">
        <template #default="{ row }">{{ categoryLabel[row.category] || row.category || '—' }}</template>
      </el-table-column>
      <el-table-column prop="confidence" label="置信度" width="90" align="center" />
      <el-table-column prop="source" label="来源" width="110" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="taxonomy-tab__pager">
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadRules"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑规则' : '新增规则'" width="480px">
      <el-form label-width="96px">
        <el-form-item label="技能词" required>
          <el-input v-model="form.skillName" placeholder="如 Vue3、SpringBoot" />
        </el-form-item>
        <el-form-item label="归属能力" required>
          <el-select v-model="form.abilityTagId" placeholder="选择能力层标签" filterable style="width: 100%">
            <el-option
              v-for="tag in abilityOptions"
              :key="tag.id"
              :label="tag.tagName"
              :value="tag.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="技术" value="TECHNICAL" />
            <el-option label="软技能" value="SOFT" />
            <el-option label="业务" value="BUSINESS" />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="form.confidence" :min="0" :max="1" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option label="人工维护" value="MANUAL" />
            <el-option label="AI 建议" value="AI_SUGGEST" />
            <el-option label="向量自动" value="VECTOR_AUTO" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.taxonomy-tab {
  padding: 16px;
}

.taxonomy-tab__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.taxonomy-tab__spacer {
  flex: 1;
}

.taxonomy-tab__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
