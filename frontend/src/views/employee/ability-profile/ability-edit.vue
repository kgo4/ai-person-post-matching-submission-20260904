<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  getEmployee,
  getAbilityProfile,
  listAbilities,
  batchSaveAbilities,
  deleteAbility,
} from '@/api'
import type { EmpAbility, EmpAbilitySaveDTO, EmpEmployee } from '@/api'
import type { GovernanceTemplate } from '@/api/ability-governance'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const tagTreeLoading = ref(false)

const empId = ref(Number(route.query.empId) || 0)

// 当前编辑的员工信息
const employeeInfo = ref<EmpEmployee | null>(null)

// 原始能力数据（用于变更检测）
const originalAbilities = ref<{
  id?: number
  abilityName: string
  masteryLevel: number
  evaluationSource: string
  sourceWeight: number
  evaluationDate: string
  remark: string
}[]>([])

// 治理模板对话框
const governanceDialogVisible = ref(false)
const governanceForm = ref<GovernanceTemplate & {
  abilityIndex: number
  abilityName: string
  changeType: string
  oldAbilityName?: string
  newAbilityName?: string
}>({
  modifyType: '',
  reason: '',
  abilityIndex: -1,
  abilityName: '',
  changeType: '',
})

// 删除原因选项
const deleteReasonOptions = [
  { label: '证据不足', value: '证据不足' },
  { label: '标签重复', value: '标签重复' },
  { label: '能力不相关', value: '能力不相关' },
  { label: '泛化描述', value: '泛化描述' },
  { label: '来源误判', value: '来源误判' },
]

// 来源选项
const sourceOptions = [
  { label: '人员评估流程', value: 'ASSESSMENT_WORKFLOW' },
  { label: 'PMS项目', value: 'AI_PROJECT' },
  { label: '手动', value: 'MANUAL' },
]

type AbilityItem = {
  id?: number
  abilityName?: string
  masteryLevel: number
  evaluationSource: string
  sourceWeight: number
  evaluationDate: string
  remark: string
  _pendingDelete?: boolean
  _governanceTemplate?: GovernanceTemplate
}

function canRememberResumeNameCorrection(): boolean {
  const ability = form.abilities[governanceForm.value.abilityIndex]
  return governanceForm.value.changeType === 'TAG_REPLACE'
    && ability?.evaluationSource === 'RESUME_PARSE'
}

const form = reactive({
  employeeId: empId.value,
  abilities: [] as AbilityItem[],
})

const levelOptions = [
  { label: '初级', value: 1 },
  { label: '中级', value: 2 },
  { label: '高级', value: 3 },
  { label: '专家', value: 4 },
]

// 等级值 → 中文名称
function levelLabel(value?: number): string {
  return levelOptions.find((o) => o.value === value)?.label || '—'
}

function openAssessmentHistory() {
  router.push({
    path: '/employee/ability-profile/assessment',
    query: { empId: empId.value, history: '1' },
  })
}

onMounted(async () => {
  if (empId.value) {
    await Promise.all([loadEmployeeInfo(), loadExistingAbilities()])
  }
})

async function loadEmployeeInfo() {
  try {
    const res = await getEmployee(empId.value)
    employeeInfo.value = res.data
  } catch {
    // handled by interceptor
  }
}

async function loadExistingAbilities() {
  loading.value = true
  try {
    if (!Number.isFinite(empId.value) || empId.value <= 0) {
      form.abilities = []
      originalAbilities.value = []
      ElMessage.warning('未获取到有效员工信息')
      return
    }
    const res = await listAbilities(empId.value)
    // 兼容后端直接数组及分页包装，能力数据始终来自正式能力表。
    const payload = (res as any)?.data
    let rows = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.records)
        ? payload.records
        : []
    // 正式能力列表为空时从同一正式能力表的画像投影兜底，避免响应包装或旧接口差异造成空白。
    if (rows.length === 0) {
      const profileRes = await getAbilityProfile(empId.value)
      const details = Array.isArray((profileRes as any)?.data?.abilityDetails)
        ? (profileRes as any).data.abilityDetails
        : []
      rows = details.map((item: any) => ({
        id: item.id,
        abilityName: item.abilityName || item.tagName,
        tagName: item.tagName,
        masteryLevel: item.masteryLevel,
        evaluationSource: 'ASSESSMENT_WORKFLOW',
        sourceWeight: 0,
        evaluationDate: '',
        remark: '',
      }))
    }
    form.abilities = rows.map((item: EmpAbility) => ({
      id: item.id,
      abilityName: item.abilityName || item.tagName || '',
      masteryLevel: item.masteryLevel,
      evaluationSource: item.evaluationSource || 'ASSESSMENT_WORKFLOW',
      sourceWeight: item.sourceWeight,
      evaluationDate: item.evaluationDate,
      remark: item.remark,
    }))
    // 保存原始数据副本
    originalAbilities.value = JSON.parse(JSON.stringify(form.abilities))
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function addAbility() {
  form.abilities.push({
    id: undefined,
    abilityName: '',
    masteryLevel: 1,
    evaluationSource: 'MANUAL',
    sourceWeight: 0,
    evaluationDate: '',
    remark: '',
  })
}

function removeAbility(index: number) {
  const ability = form.abilities[index]
  // 如果是已存在的能力，需要填写删除原因
  if (ability.id) {
    governanceForm.value = {
      modifyType: 'DELETE_ABILITY',
      reason: '',
      abilityIndex: index,
      abilityName: ability.abilityName || '',
      changeType: 'DELETE_ABILITY',
      deleteReason: '',
      misjudgedSource: '',
      addToRejectRule: false,
      replacementSuggestion: '',
    }
    governanceDialogVisible.value = true
  } else {
    // 新增的能力直接删除
    form.abilities.splice(index, 1)
  }
}

// 变更详情接口
interface ChangeDetail {
  index: number
  primaryType: string // 主要变更类型
  oldAbility?: any
  newAbility: any
  hasTagChange: boolean
  hasNameChange: boolean
  hasLevelChange: boolean
  hasRemarkChange: boolean
  levelDirection?: 'UP' | 'DOWN'
}

// 检测变更类型（合并同一能力项的所有变更）
function detectChanges(): ChangeDetail[] {
  const changes: ChangeDetail[] = []

  form.abilities.forEach((ability, index) => {
    if (!ability.id) {
      // 新增能力
      changes.push({
        index,
        primaryType: 'MANUAL_ADD',
        newAbility: ability,
        hasTagChange: false,
        hasNameChange: false,
        hasLevelChange: false,
        hasRemarkChange: false,
      })
    } else {
      // 查找原始能力
      const oldAbility = originalAbilities.value.find((a) => a.id === ability.id)
      if (oldAbility) {
        const hasNameChange = (oldAbility.abilityName || '').trim() !== (ability.abilityName || '').trim()
        const hasTagChange = false
        const hasLevelChange = oldAbility.masteryLevel !== ability.masteryLevel
        const hasRemarkChange = oldAbility.remark !== ability.remark

        // 有变更才处理
        if (hasNameChange || hasTagChange || hasLevelChange || hasRemarkChange) {
          // 确定主要变更类型（能力名称优先，其次标签、等级、备注）
          let primaryType = 'EVIDENCE_UPDATE'
          if (hasNameChange) {
            primaryType = 'ABILITY_RENAME'
          } else if (hasTagChange) {
            primaryType = 'TAG_REPLACE'
          } else if (hasLevelChange) {
            primaryType = ability.masteryLevel > oldAbility.masteryLevel ? 'LEVEL_UP' : 'LEVEL_DOWN'
          }

          changes.push({
            index,
            primaryType,
            oldAbility,
            newAbility: ability,
            hasNameChange,
            hasTagChange,
            hasLevelChange,
            hasRemarkChange,
            levelDirection: hasLevelChange ? (ability.masteryLevel > oldAbility.masteryLevel ? 'UP' : 'DOWN') : undefined,
          })
        }
      }
    }
  })

  return changes
}

// 显示治理模板对话框（支持合并变更）
function showGovernanceDialog(change: ChangeDetail) {
  const ability = change.newAbility
  const oldAbility = change.oldAbility

  // 构建变更摘要
  let changeSummary = ''
  if (change.hasTagChange && oldAbility) {
    changeSummary += `能力名称: ${oldAbility.abilityName || ''} → ${ability.abilityName || ''}\n`
  }
  if (change.hasNameChange && oldAbility) {
    changeSummary += `能力名称: ${oldAbility.abilityName || ''} → ${ability.abilityName || ''}\n`
  }
  if (change.hasLevelChange && oldAbility) {
    const levelLabels: Record<number, string> = { 1: '初级', 2: '中级', 3: '高级', 4: '专家' }
    changeSummary += `等级: ${levelLabels[oldAbility.masteryLevel]} → ${levelLabels[ability.masteryLevel]}\n`
  }
  if (change.hasRemarkChange) {
    changeSummary += `备注已修改\n`
  }

  switch (change.primaryType) {
    case 'MANUAL_ADD':
      governanceForm.value = {
        modifyType: 'MANUAL_ADD',
        reason: '',
        abilityIndex: change.index,
        abilityName: ability.abilityName || '',
        changeType: 'MANUAL_ADD',
        changeSummary,
        supportEvidence: '',
        mainEvidenceSources: [],
      }
      break

    case 'TAG_REPLACE':
      governanceForm.value = {
        modifyType: 'TAG_REPLACE',
        reason: '',
        abilityIndex: change.index,
        abilityName: ability.abilityName || '',
        changeType: 'TAG_REPLACE',
        changeSummary,
        oldTagName: oldAbility?.abilityName,
        newTagName: ability.abilityName,
        hasLevelChange: change.hasLevelChange,
        oldLevel: oldAbility?.masteryLevel,
        newLevel: ability.masteryLevel,
        keepOldAsAlias: true,
        rememberResumeNameCorrection: false,
        triggerExpressions: [],
        negativeExpressions: [],
      }
      break

    case 'ABILITY_RENAME':
      governanceForm.value = {
        modifyType: 'ABILITY_RENAME',
        reason: '',
        abilityIndex: change.index,
        abilityName: ability.abilityName || '',
        changeType: 'ABILITY_RENAME',
        changeSummary,
        oldAbilityName: oldAbility?.abilityName,
        newAbilityName: ability.abilityName,
        supportEvidence: '',
      }
      break

    case 'LEVEL_UP':
    case 'LEVEL_DOWN':
      governanceForm.value = {
        modifyType: change.primaryType,
        reason: '',
        abilityIndex: change.index,
        abilityName: ability.abilityName || '',
        changeType: change.primaryType,
        changeSummary,
        oldLevel: oldAbility?.masteryLevel,
        newLevel: ability.masteryLevel,
        supportEvidence: '',
        counterEvidence: '',
        mainEvidenceSources: [],
      }
      break

    case 'EVIDENCE_UPDATE':
      governanceForm.value = {
        modifyType: 'EVIDENCE_UPDATE',
        reason: '',
        abilityIndex: change.index,
        abilityName: ability.abilityName || '',
        changeType: 'EVIDENCE_UPDATE',
        changeSummary,
        addedEvidence: '',
        removedEvidence: '',
      }
      break
  }

  governanceDialogVisible.value = true
}

// 验证治理模板
function validateGovernanceTemplate(): boolean {
  if (!governanceForm.value.reason) {
    ElMessage.warning('请填写修改原因')
    return false
  }

  switch (governanceForm.value.modifyType) {
    case 'MANUAL_ADD':
      if (!governanceForm.value.supportEvidence) {
        ElMessage.warning('请填写支持证据')
        return false
      }
      break

    case 'TAG_REPLACE':
      // 标签替换已经通过选择新标签完成
      break

    case 'ABILITY_RENAME':
      if (!governanceForm.value.newAbilityName?.trim()) {
        ElMessage.warning('请输入新的能力名称')
        return false
      }
      break

    case 'LEVEL_UP':
    case 'LEVEL_DOWN':
      if (!governanceForm.value.supportEvidence) {
        ElMessage.warning('请填写支持证据')
        return false
      }
      break

    case 'DELETE_ABILITY':
      if (!governanceForm.value.deleteReason) {
        ElMessage.warning('请选择删除原因')
        return false
      }
      break

    case 'EVIDENCE_UPDATE':
      // 证据更新只需要原因
      break
  }

  return true
}

// 提交治理模板
function submitGovernanceTemplate() {
  if (!validateGovernanceTemplate()) {
    return
  }

  // 如果是删除操作，执行删除
  if (governanceForm.value.changeType === 'DELETE_ABILITY') {
    const index = governanceForm.value.abilityIndex
    // 标记为待删除（在提交时处理）
    form.abilities[index]._pendingDelete = true
    form.abilities[index]._governanceTemplate = {
      modifyType: 'DELETE_ABILITY',
      reason: governanceForm.value.reason,
      deleteReason: governanceForm.value.deleteReason,
      misjudgedSource: governanceForm.value.misjudgedSource,
      addToRejectRule: governanceForm.value.addToRejectRule,
      replacementSuggestion: governanceForm.value.replacementSuggestion,
    }
  } else {
    // 其他操作，保存治理模板到能力项
    const index = governanceForm.value.abilityIndex
    form.abilities[index]._governanceTemplate = {
      modifyType: governanceForm.value.modifyType,
      reason: governanceForm.value.reason,
      // 标签替换相关（后端校验要求 newTagId 必填）
      oldTagId: governanceForm.value.oldTagId,
      oldTagName: governanceForm.value.oldTagName,
      newTagId: governanceForm.value.newTagId,
      newTagName: governanceForm.value.newTagName,
      keepOldAsAlias: governanceForm.value.keepOldAsAlias,
      rememberResumeNameCorrection: governanceForm.value.rememberResumeNameCorrection,
      triggerExpressions: governanceForm.value.triggerExpressions,
      negativeExpressions: governanceForm.value.negativeExpressions,
      // 等级修改相关（后端校验要求 newLevel 必填）
      oldLevel: governanceForm.value.oldLevel,
      newLevel: governanceForm.value.newLevel,
      supportEvidence: governanceForm.value.supportEvidence,
      counterEvidence: governanceForm.value.counterEvidence,
      mainEvidenceSources: governanceForm.value.mainEvidenceSources,
      // 能力名称修改独立于标签关联，标签为空时也必须保留名称治理信息
      oldAbilityName: governanceForm.value.oldAbilityName,
      newAbilityName: governanceForm.value.newAbilityName,
      // 证据修改相关
      addedEvidence: governanceForm.value.addedEvidence,
      removedEvidence: governanceForm.value.removedEvidence,
      // 通用
      sourceWeightAdvice: governanceForm.value.sourceWeightAdvice,
    }
  }

  governanceDialogVisible.value = false

  // 继续处理下一个变更或提交
  processNextChangeOrSubmit()
}

// 待处理的变更列表
const pendingChanges = ref<ChangeDetail[]>([])
const currentChangeIndex = ref(0)

// 变更汇总对话框
const changeSummaryVisible = ref(false)
const batchMode = ref(false) // 是否批量模式
const batchReason = ref('') // 批量原因

// 处理下一个变更或提交
function processNextChangeOrSubmit() {
  currentChangeIndex.value++

  if (currentChangeIndex.value < pendingChanges.value.length) {
    // 显示下一个变更的治理模板
    showGovernanceDialog(pendingChanges.value[currentChangeIndex.value])
  } else {
    // 所有变更都已处理，执行提交
    executeSubmit()
  }
}

async function handleSubmit() {
  if (!empId.value) {
    ElMessage.warning('请先选择人员')
    return
  }
  if (form.abilities.length === 0) {
    ElMessage.warning('请至少添加一项能力')
    return
  }

  // 标签关联是可选项；能力名称是正式能力表的权威字段，不得因未关联标签而阻断保存。
  // 检测变更
  const changes = detectChanges()

  if (changes.length === 0) {
    ElMessage.info('没有检测到变更')
    return
  }

  // 开始处理变更
  pendingChanges.value = changes

  if (changes.length === 1) {
    // 只有一个变更，直接弹模板
    currentChangeIndex.value = 0
    showGovernanceDialog(changes[0])
  } else {
    // 多个变更，显示汇总对话框
    changeSummaryVisible.value = true
  }
}

// 批量填写模式
function startBatchMode() {
  batchMode.value = true
  batchReason.value = ''
}

// 确认批量填写
function confirmBatchMode() {
  if (!batchReason.value) {
    ElMessage.warning('请填写修改原因')
    return
  }

  // 为所有变更设置相同的治理模板
  pendingChanges.value.forEach((change, index) => {
    const ability = change.newAbility
    form.abilities[change.index]._governanceTemplate = {
      modifyType: change.primaryType,
      reason: batchReason.value,
      // 根据变更类型设置其他字段
      ...(change.primaryType === 'TAG_REPLACE' && {
        oldTagId: change.oldAbility?.tagId,
        newTagId: ability.tagId,
      }),
      ...(change.primaryType === 'ABILITY_RENAME' && {
        oldAbilityName: change.oldAbility?.abilityName || change.oldAbility?.tagName,
        newAbilityName: ability.abilityName,
      }),
      ...(change.primaryType === 'LEVEL_UP' || change.primaryType === 'LEVEL_DOWN' ? {
        newLevel: ability.masteryLevel,
        supportEvidence: batchReason.value,
      } : {}),
    }
  })

  changeSummaryVisible.value = false
  batchMode.value = false
  executeSubmit()
}

// 逐个填写模式
function startOneByOneMode() {
  changeSummaryVisible.value = false
  currentChangeIndex.value = 0
  showGovernanceDialog(pendingChanges.value[0])
}

// 取消变更汇总
function cancelChangeSummary() {
  changeSummaryVisible.value = false
  pendingChanges.value = []
  batchMode.value = false
}

async function executeSubmit() {
  loading.value = true
  try {
    // 只提交新增或实际发生变化的能力，避免未修改项重复触发后端治理、证据入库和事件链路。
    const changedIndexes = new Set(pendingChanges.value.map((change) => change.index))
    const dtoList: EmpAbilitySaveDTO[] = form.abilities
      .filter((a, index) => !a._pendingDelete && a.abilityName?.trim() && changedIndexes.has(index))
      .map((a) => ({
        id: a.id,
        empId: empId.value,
        abilityName: a.abilityName?.trim(),
        masteryLevel: a.masteryLevel,
        evaluationSource: a.evaluationSource || 'MANUAL',
        sourceWeight: a.sourceWeight,
        evaluationDate: a.evaluationDate,
        remark: a.remark,
        governanceTemplate: a._governanceTemplate || undefined,
      }))

    // 删除走正式能力记录删除接口，不再通过保存接口，也不依赖 tagId。
    const deleteList = form.abilities
      .filter((a) => a._pendingDelete && a._governanceTemplate)
      .map((a) => a.id)
      .filter((id): id is number => Number.isFinite(id))

    // 提交保存（包含治理模板）
    if (dtoList.length > 0) {
      await batchSaveAbilities(dtoList)
    }
    if (deleteList.length > 0) {
      await Promise.all(deleteList.map((id) => deleteAbility(id)))
    }

    ElMessage.success('保存成功')
    router.back()
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

// 获取治理模板标题
function getGovernanceDialogTitle(): string {
  switch (governanceForm.value.changeType) {
    case 'MANUAL_ADD':
      return '人工新增能力 - 治理模板'
    case 'TAG_REPLACE':
      return '标签替换 - 治理模板'
    case 'LEVEL_UP':
      return '等级上调 - 治理模板'
    case 'LEVEL_DOWN':
      return '等级下调 - 治理模板'
    case 'DELETE_ABILITY':
      return '删除能力 - 治理模板'
    case 'EVIDENCE_UPDATE':
      return '证据修改 - 治理模板'
    default:
      return '治理模板'
  }
}

// 获取变更类型标签
function getChangeTypeLabel(change: ChangeDetail): string {
  const labels: Record<string, string> = {
    MANUAL_ADD: '人工新增',
    ABILITY_RENAME: '能力名称修改',
    TAG_REPLACE: '标签替换',
    LEVEL_UP: '等级上调',
    LEVEL_DOWN: '等级下调',
    EVIDENCE_UPDATE: '证据修改',
  }
  let label = labels[change.primaryType] || change.primaryType
  // 如果有多个变更，追加说明
  const extras = []
  if (change.hasTagChange && change.primaryType !== 'TAG_REPLACE') extras.push('标签')
  if (change.hasLevelChange && !change.primaryType.includes('LEVEL')) extras.push('等级')
  if (extras.length > 0) {
    label += `（含${extras.join('、')}变更）`
  }
  return label
}
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <section class="page-hero">
      <div class="page-hero__main">
        <div class="page-hero__eyebrow">能力治理</div>
        <h1 class="page-hero__title">{{ employeeInfo ? `${employeeInfo.realName} 的能力编辑` : '能力编辑' }}</h1>
        <p class="page-hero__desc">标准化维护人员的能力标签、掌握等级与来源依据；保存时将引导填写治理原因，确保每一次改动都有据可查。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">人员编号：{{ employeeInfo?.empCode || '—' }}</span>
          <span class="hero-chip">能力项：{{ form.abilities.length }} 项</span>
        </div>
      </div>
      <div class="page-hero__actions">
        <el-button type="primary" plain @click="openAssessmentHistory">评估流程历史</el-button>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </section>

    <!-- 未选择人员 -->
    <el-card v-if="!empId" shadow="hover">
      <el-empty description="请从人员能力画像页面选择人员后再编辑" />
    </el-card>

    <!-- 能力编辑主体 -->
    <el-card v-else shadow="hover" v-loading="loading || tagTreeLoading">
      <template #header>
        <div class="section-header">
          <div>
            <div class="section-title">能力清单</div>
            <div class="section-desc">维护每位人员的正式能力名称、掌握等级与来源依据</div>
          </div>
          <el-button type="primary" @click="addAbility">
            <el-icon><Plus /></el-icon>
            <span>添加能力项</span>
          </el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-position="top">
        <div v-for="(ability, index) in form.abilities" :key="index" class="ability-card">
          <div class="ability-card__header">
            <div class="ability-card__title">
              <span class="ability-card__index">{{ index + 1 }}</span>
              <span class="ability-card__name">{{ ability.abilityName || '新能力项' }}</span>
            </div>
            <el-button text type="danger" :icon="Delete" @click="removeAbility(index)">删除</el-button>
          </div>

          <div class="ability-card__body">
            <el-row :gutter="16">
              <el-col :xs="24" :md="10">
                <el-form-item label="能力名称" required>
                  <el-input v-model="ability.abilityName" placeholder="输入正式能力名称" />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :md="7">
                <el-form-item label="掌握等级">
                  <el-select v-model="ability.masteryLevel" placeholder="选择等级" style="width: 100%">
                    <el-option v-for="opt in levelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="12" :md="7">
                <el-form-item label="来源">
                  <el-select v-model="ability.evaluationSource" placeholder="选择来源" style="width: 100%">
                    <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :xs="12" :md="12">
                <el-form-item label="来源权重">
                  <el-input-number v-model="ability.sourceWeight" :min="0" :max="100" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :md="12">
                <el-form-item label="评估日期">
                  <el-date-picker v-model="ability.evaluationDate" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="ability.remark" placeholder="补充说明（可选）" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 已填写的治理模板状态 -->
            <div v-if="ability._governanceTemplate" class="governance-status">
              <el-tag type="success" size="small">已填写治理模板</el-tag>
              <span class="governance-reason">{{ ability._governanceTemplate.reason }}</span>
            </div>
          </div>
        </div>
      </el-form>

      <div class="panel-footer">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">保存</el-button>
      </div>
    </el-card>

    <!-- 变更汇总对话框（多变更时显示） -->
    <el-dialog
      v-model="changeSummaryVisible"
      title="检测到多个变更"
      width="700px"
      :close-on-click-modal="false"
    >
      <div class="change-summary-dialog">
        <el-alert
          title="您有多个能力项被修改，请选择处理方式"
          type="info"
          :closable="false"
          show-icon
          class="mb-4"
        />

        <!-- 变更列表 -->
        <el-table :data="pendingChanges" border size="small" class="mb-4">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="newAbility.abilityName" label="能力名称" min-width="150" />
          <el-table-column label="变更类型" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="row.primaryType === 'TAG_REPLACE' ? 'warning' : row.primaryType.includes('LEVEL') ? 'success' : 'info'">
                {{ getChangeTypeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更详情" min-width="250">
            <template #default="{ row }">
              <div class="text-sm">
                <div v-if="row.hasNameChange">{{ row.oldAbility?.abilityName }} → {{ row.newAbility.abilityName }}</div>
                <div v-if="row.hasLevelChange">等级: {{ levelLabel(row.oldAbility?.masteryLevel) }} → {{ levelLabel(row.newAbility.masteryLevel) }}</div>
                <div v-if="row.hasRemarkChange && !row.hasTagChange && !row.hasLevelChange">备注已修改</div>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <!-- 批量填写模式 -->
        <div v-if="batchMode" class="batch-mode">
          <el-divider>批量填写原因</el-divider>
          <el-form label-width="100px">
            <el-form-item label="统一原因" required>
              <el-input
                v-model="batchReason"
                type="textarea"
                :rows="4"
                placeholder="请填写适用于所有变更的统一原因..."
              />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <div v-if="!batchMode">
          <el-button @click="cancelChangeSummary">取消</el-button>
          <el-button type="warning" @click="startBatchMode">批量填写（一个原因）</el-button>
          <el-button type="primary" @click="startOneByOneMode">逐个填写（详细原因）</el-button>
        </div>
        <div v-else>
          <el-button @click="batchMode = false">返回</el-button>
          <el-button type="primary" @click="confirmBatchMode">确认提交</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 治理模板对话框 -->
    <el-dialog
      v-model="governanceDialogVisible"
      :title="getGovernanceDialogTitle()"
      width="680px"
      :close-on-click-modal="false"
    >
      <div class="governance-dialog">
        <!-- 变更摘要 -->
        <el-alert
          v-if="governanceForm.changeSummary"
          :title="'检测到以下变更'"
          type="info"
          :closable="false"
          show-icon
        >
          <template #default>
            <pre class="whitespace-pre-wrap text-sm mt-2">{{ governanceForm.changeSummary }}</pre>
          </template>
        </el-alert>

        <!-- 当前能力信息 -->
        <div class="current-ability">
          <div class="current-ability__header">
            <span class="current-ability__label">当前能力</span>
            <el-tag type="info" size="small">{{ governanceForm.abilityName }}</el-tag>
          </div>
          <div v-if="governanceForm.oldLevel" class="current-ability__info">
            <div class="info-item">
              <span class="info-item__label">原等级：</span>
              <span class="info-item__value">{{ levelLabel(governanceForm.oldLevel) }}</span>
            </div>
            <div class="info-item">
              <span class="info-item__label">新等级：</span>
              <span class="info-item__value">{{ levelLabel(governanceForm.newLevel) }}</span>
            </div>
          </div>
          <div v-if="governanceForm.oldTagName" class="current-ability__info">
            <div class="info-item">
              <span class="info-item__label">原标签：</span>
              <span class="info-item__value">{{ governanceForm.oldTagName }}</span>
            </div>
            <div class="info-item">
              <span class="info-item__label">新标签：</span>
              <span class="info-item__value">{{ governanceForm.newTagName }}</span>
            </div>
          </div>
        </div>

        <!-- 治理模板表单 -->
        <el-form label-width="120px" label-position="top">
          <!-- 修改原因（所有类型都需要） -->
          <el-form-item label="修改原因" required>
            <el-input
              v-model="governanceForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请详细说明修改原因..."
            />
          </el-form-item>

          <!-- 人工新增能力模板 -->
          <template v-if="governanceForm.changeType === 'ABILITY_RENAME'">
            <el-form-item label="原能力名称">
              <el-input :model-value="governanceForm.oldAbilityName" disabled />
            </el-form-item>
            <el-form-item label="新能力名称">
              <el-input :model-value="governanceForm.newAbilityName" disabled />
            </el-form-item>
            <el-form-item label="名称修改依据">
              <el-input v-model="governanceForm.supportEvidence" type="textarea" :rows="2" placeholder="可补充名称修改依据" />
            </el-form-item>
          </template>

          <!-- 人工新增能力模板 -->
          <template v-if="governanceForm.changeType === 'MANUAL_ADD'">
            <el-form-item label="支持证据" required>
              <el-input
                v-model="governanceForm.supportEvidence"
                type="textarea"
                :rows="2"
                placeholder="请提供支持该能力的证据..."
              />
            </el-form-item>
            <el-form-item label="为什么系统没有自动识别">
              <el-input
                v-model="governanceForm.additionalNotes"
                type="textarea"
                :rows="2"
                placeholder="请说明系统未能识别的原因..."
              />
            </el-form-item>
          </template>

          <!-- 标签替换模板 -->
          <template v-if="governanceForm.changeType === 'TAG_REPLACE'">
            <el-form-item v-if="canRememberResumeNameCorrection()" label="简历提取纠偏">
              <el-switch v-model="governanceForm.rememberResumeNameCorrection" />
              <span class="ml-2 text-sm text-gray-500">记住本次名称修正，用于后续简历能力提取</span>
            </el-form-item>
            <el-form-item label="是否保留原标签为别名">
              <el-switch v-model="governanceForm.keepOldAsAlias" />
            </el-form-item>
            <el-form-item label="典型触发表达">
              <el-input
                v-model="governanceForm.triggerExpressions"
                placeholder="输入触发原标签的典型表达，逗号分隔"
              />
            </el-form-item>
            <el-form-item label="不应再生成的表达">
              <el-input
                v-model="governanceForm.negativeExpressions"
                placeholder="输入不应再生成的表达，逗号分隔"
              />
            </el-form-item>
          </template>

          <!-- 等级上调模板 -->
          <template v-if="governanceForm.changeType === 'LEVEL_UP'">
            <el-form-item label="支持证据" required>
              <el-input
                v-model="governanceForm.supportEvidence"
                type="textarea"
                :rows="2"
                placeholder="请提供支持等级上调的证据..."
              />
            </el-form-item>
            <el-form-item label="主要依据来源">
              <el-checkbox-group v-model="governanceForm.mainEvidenceSources">
                <el-checkbox v-for="opt in sourceOptions" :key="opt.value" :label="opt.value">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="来源权重建议">
              <el-input
                v-model="governanceForm.sourceWeightAdvice"
                placeholder="例如：PMS证据权重应高于简历自述"
              />
            </el-form-item>
          </template>

          <!-- 等级下调模板 -->
          <template v-if="governanceForm.changeType === 'LEVEL_DOWN'">
            <el-form-item label="反证证据" required>
              <el-input
                v-model="governanceForm.supportEvidence"
                type="textarea"
                :rows="2"
                placeholder="请提供支持等级下调的证据..."
              />
            </el-form-item>
            <el-form-item label="误判来源">
              <el-checkbox-group v-model="governanceForm.mainEvidenceSources">
                <el-checkbox v-for="opt in sourceOptions" :key="opt.value" :label="opt.value">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </template>

          <!-- 删除能力模板 -->
          <template v-if="governanceForm.changeType === 'DELETE_ABILITY'">
            <el-form-item label="删除原因" required>
              <el-select v-model="governanceForm.deleteReason" placeholder="选择删除原因">
                <el-option v-for="opt in deleteReasonOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="误判来源">
              <el-select v-model="governanceForm.misjudgedSource" placeholder="选择误判来源" clearable>
                <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="加入拒绝规则">
              <el-switch v-model="governanceForm.addToRejectRule" />
              <span class="ml-2 text-sm text-gray-500">开启后，Agent 将不再输出类似标签</span>
            </el-form-item>
            <el-form-item label="替代建议">
              <el-input
                v-model="governanceForm.replacementSuggestion"
                placeholder="建议替代的能力标签..."
              />
            </el-form-item>
          </template>

          <!-- 证据修改模板 -->
          <template v-if="governanceForm.changeType === 'EVIDENCE_UPDATE'">
            <el-form-item label="新增的证据">
              <el-input
                v-model="governanceForm.addedEvidence"
                type="textarea"
                :rows="2"
                placeholder="描述新增的证据..."
              />
            </el-form-item>
            <el-form-item label="删除的证据">
              <el-input
                v-model="governanceForm.removedEvidence"
                type="textarea"
                :rows="2"
                placeholder="描述删除的证据..."
              />
            </el-form-item>
          </template>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="governanceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGovernanceTemplate">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ===================== 页头 ===================== */
.page-hero__main {
  flex: 1;
  min-width: 0;
}

.page-hero__actions {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

/* ===================== 能力项卡片 ===================== */
.ability-card {
  position: relative;
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  box-shadow: var(--app-shadow-sm);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ability-card:hover {
  border-color: rgba(59, 130, 246, 0.28);
  box-shadow: var(--app-shadow-md);
}

.ability-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--app-divider);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42), rgba(255, 255, 255, 0.1));
}

.ability-card__title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ability-card__index {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  flex-shrink: 0;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  background: linear-gradient(135deg, #2563eb 0%, #0891b2 100%);
}

.ability-card__name {
  overflow: hidden;
  color: var(--app-text-strong);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ability-card__body {
  padding: 18px 18px 0;
}

/* ===================== 治理模板状态 ===================== */
.governance-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--app-success-soft);
}

.governance-reason {
  flex: 1;
  overflow: hidden;
  color: var(--app-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===================== 治理模板对话框 ===================== */
.governance-dialog {
  @apply space-y-4;
}

.current-ability {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  background: rgba(248, 250, 252, 0.7);
}

.current-ability__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.current-ability__label {
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.current-ability__info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.info-item {
  font-size: 13px;
}

.info-item__label {
  color: var(--app-text-muted);
}

.info-item__value {
  color: var(--app-text-strong);
  font-weight: 600;
}

/* ===================== 变更汇总对话框 ===================== */
.change-summary-dialog {
  @apply space-y-4;
}

.batch-mode {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  background: rgba(248, 250, 252, 0.7);
}
</style>
