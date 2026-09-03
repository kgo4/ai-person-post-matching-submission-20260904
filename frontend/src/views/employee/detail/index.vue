<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getEmployee, getTagTree, listAbilities, listFieldsByModule, updateEmployee } from '@/api'
import type { EmpAbility, EmpEmployee, ExtendFieldVO } from '@/api'
import { buildAbilityTagNameMap, resolveAbilityTagName } from '@/utils/abilityTagName'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const abilityLoading = ref(false)
const extendLoading = ref(false)
const extendSaving = ref(false)
const activeTab = ref('basic')
const tagNameMap = ref(new Map<number, string>())

const employee = ref<EmpEmployee>({
  id: Number(route.params.id),
  empCode: '--',
  realName: '--',
  gender: 1,
  phone: '--',
  email: '--',
  extendFields: '',
  isLocked: 0,
  status: 0,
  createdTime: '',
})

const abilities = ref<EmpAbility[]>([])
const extendConfigs = ref<ExtendFieldVO[]>([])
const extendForm = reactive<Record<string, any>>({})
const extendLoaded = ref(false)

onMounted(() => {
  loadEmployee()
})

async function loadEmployee() {
  loading.value = true
  try {
    const res = await getEmployee(Number(route.params.id))
    employee.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadAbilities() {
  abilityLoading.value = true
  try {
    await ensureTagNameMap()
    const res = await listAbilities(Number(route.params.id))
    abilities.value = (res.data || []).map((item) => ({
      ...item,
      tagName: resolveAbilityTagName(item, tagNameMap.value),
    }))
  } finally {
    abilityLoading.value = false
  }
}

async function ensureTagNameMap() {
  if (tagNameMap.value.size > 0) return
  const res = await getTagTree()
  tagNameMap.value = buildAbilityTagNameMap(res.data || [])
}

async function loadExtendFields() {
  extendLoading.value = true
  try {
    const res = await listFieldsByModule('EMPLOYEE')
    extendConfigs.value = (res.data || [])
      .filter((item) => item.status === 1)
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

    const raw = employee.value.extendFields
    let values: Record<string, any> = {}
    if (raw && raw !== '""' && raw !== '"{}"' && raw !== 'null') {
      try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          values = parsed
        }
      } catch {
        values = {}
      }
    }

    for (const config of extendConfigs.value) {
      extendForm[config.fieldName] = values[config.fieldName] ?? ''
    }
    extendLoaded.value = true
  } finally {
    extendLoading.value = false
  }
}

async function handleExtendSave() {
  extendSaving.value = true
  try {
    const data: Record<string, any> = {}
    for (const config of extendConfigs.value) {
      const value = extendForm[config.fieldName]
      if (value != null && value !== '') {
        data[config.fieldName] = value
      }
    }
    await updateEmployee(employee.value.id, {
      ...employee.value,
      extendFields: JSON.stringify(data),
    })
    employee.value.extendFields = JSON.stringify(data)
    ElMessage.success('扩展信息保存成功')
  } finally {
    extendSaving.value = false
  }
}

function parseSelectOptions(optionsStr?: string): { label: string; value: string }[] {
  if (!optionsStr) return []
  return optionsStr.split('\n').filter(Boolean).map((line) => {
    const index = line.indexOf('=')
    if (index === -1) return { label: line.trim(), value: line.trim() }
    return {
      label: line.substring(0, index).trim(),
      value: line.substring(index + 1).trim(),
    }
  })
}

function handleTabChange(tabName: string) {
  if (tabName === 'ability' && abilities.value.length === 0) loadAbilities()
  if (tabName === 'extend' && !extendLoaded.value) loadExtendFields()
}

const levelMap: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
  4: '专家',
}

function evaluationSourceLabel(source?: string): string {
  if (source === 'AI_PROJECT') return 'PMS项目'
  if (source === 'MANUAL') return '手动'
  return '人员评估流程'
}
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Employee Detail</div>
        <h1 class="page-hero__title">{{ employee.realName || '人员详情' }}</h1>
        <p class="page-hero__desc">查看基础档案、能力证据与扩展字段，作为后续画像和匹配决策的来源底座。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">编号 {{ employee.empCode }}</span>
          <span class="hero-chip">{{ employee.status === 1 ? '启用中' : '已停用' }}</span>
          <span class="hero-chip">{{ employee.isLocked === 1 ? '已锁定' : '正常' }}</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-button @click="router.back()">返回</el-button>
      </div>
    </section>

    <section class="info-grid">
      <article class="info-card">
        <div class="info-card__label">姓名</div>
        <div class="info-card__value">{{ employee.realName }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">人员编号</div>
        <div class="info-card__value">{{ employee.empCode }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">手机号</div>
        <div class="info-card__value">{{ employee.phone || '--' }}</div>
      </article>
      <article class="info-card">
        <div class="info-card__label">邮箱</div>
        <div class="info-card__value">{{ employee.email || '--' }}</div>
      </article>
    </section>

    <section class="glass-card" v-loading="loading">
      <div class="panel-body">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="基础信息" name="basic">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="姓名">{{ employee.realName }}</el-descriptions-item>
              <el-descriptions-item label="人员编号">{{ employee.empCode }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ employee.gender === 1 ? '男' : '女' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ employee.phone || '--' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ employee.email || '--' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ employee.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
              <el-descriptions-item label="锁定状态">{{ employee.isLocked === 1 ? '已锁定' : '正常' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ employee.createdTime || '--' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="能力画像" name="ability">
            <el-table :data="abilities" v-loading="abilityLoading" style="width: 100%">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="tagName" label="能力标签" min-width="160" />
              <el-table-column prop="masteryLevel" label="掌握等级">
                <template #default="{ row }">{{ levelMap[row.masteryLevel] || row.masteryLevel }}</template>
              </el-table-column>
              <el-table-column label="评估来源">
                <template #default="{ row }">{{ evaluationSourceLabel(row.evaluationSource) }}</template>
              </el-table-column>
              <el-table-column prop="sourceWeight" label="来源权重" />
              <el-table-column prop="evaluationDate" label="评估日期" />
              <el-table-column prop="remark" label="备注" />
            </el-table>
            <el-empty v-if="!abilityLoading && abilities.length === 0" description="暂无能力画像数据" />
          </el-tab-pane>

          <el-tab-pane label="扩展信息" name="extend">
            <div v-loading="extendLoading">
              <template v-if="extendConfigs.length > 0">
                <el-form label-width="120px" style="max-width: 760px;">
                  <el-form-item
                    v-for="config in extendConfigs"
                    :key="config.fieldName"
                    :label="config.fieldLabel"
                    :required="config.isRequired === 1"
                  >
                    <el-input
                      v-if="config.fieldType === 'text' || config.fieldType === 'textarea'"
                      v-model="extendForm[config.fieldName]"
                      :type="config.fieldType === 'textarea' ? 'textarea' : 'text'"
                      :rows="config.fieldType === 'textarea' ? 3 : undefined"
                      placeholder="请输入"
                    />
                    <el-input-number
                      v-else-if="config.fieldType === 'number'"
                      v-model="extendForm[config.fieldName]"
                      controls-position="right"
                    />
                    <el-select
                      v-else-if="config.fieldType === 'select'"
                      v-model="extendForm[config.fieldName]"
                      placeholder="请选择"
                      style="width: 100%;"
                    >
                      <el-option
                        v-for="option in parseSelectOptions(config.selectOptions)"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                    <el-select
                      v-else-if="config.fieldType === 'multi-select'"
                      v-model="extendForm[config.fieldName]"
                      multiple
                      placeholder="请选择"
                      style="width: 100%;"
                    >
                      <el-option
                        v-for="option in parseSelectOptions(config.selectOptions)"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                    <el-date-picker
                      v-else-if="config.fieldType === 'date'"
                      v-model="extendForm[config.fieldName]"
                      type="date"
                      placeholder="请选择日期"
                      value-format="YYYY-MM-DD"
                      style="width: 100%;"
                    />
                    <el-date-picker
                      v-else-if="config.fieldType === 'datetime'"
                      v-model="extendForm[config.fieldName]"
                      type="datetime"
                      placeholder="请选择日期时间"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      style="width: 100%;"
                    />
                    <el-switch
                      v-else-if="config.fieldType === 'switch'"
                      v-model="extendForm[config.fieldName]"
                      :active-value="1"
                      :inactive-value="0"
                    />
                    <el-input
                      v-else
                      v-model="extendForm[config.fieldName]"
                      placeholder="请输入"
                    />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="extendSaving" @click="handleExtendSave">保存</el-button>
                  </el-form-item>
                </el-form>
              </template>
              <el-empty v-else-if="extendLoaded" description="暂无扩展字段配置，请先在系统中维护字段。" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </div>
</template>
