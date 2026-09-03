<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { replaceTag, changeLevel, removeTag } from '@/api/ability-governance'
import type { PersonAbilityProfile } from '@/api/ability-governance'

const props = defineProps<{
  visible: boolean
  ability: PersonAbilityProfile | null
  empId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// 当前步骤
const currentStep = ref(0)
const modifyType = ref('')

// 标签替换表单
const replaceForm = ref({
  newTagId: null as number | null,
  newTagName: '',
  keepOldAsAlias: true,
  reason: '',
  applicableSources: [] as string[],
  positiveExamples: '',
  negativeExamples: '',
  sourceWeightAdvice: '',
})

// 等级修正表单
const levelForm = ref({
  newLevel: 3,
  reason: '',
  supportEvidence: [] as string[],
  counterEvidence: [] as string[],
  sourceWeightAdvice: '',
})

// 删除标签表单
const removeForm = ref({
  reason: '',
  misjudgedSource: '',
  addToRejectRule: false,
  replacementSuggestion: '',
})

// 来源选项
const sourceOptions = [
  { label: '简历解析', value: 'RESUME_PARSE' },
  { label: 'AI测试', value: 'AI_TEST' },
  { label: 'AI面试', value: 'AI_INTERVIEW' },
  { label: 'PMS分析', value: 'AI_PROJECT' },
]

// 删除原因选项
const removeReasonOptions = [
  { label: '泛化描述', value: '泛化描述' },
  { label: '证据不足', value: '证据不足' },
  { label: '标签重复', value: '标签重复' },
  { label: '能力不相关', value: '能力不相关' },
  { label: '来源误判', value: '来源误判' },
]

// 来源权重建议选项
const weightAdviceOptions = [
  { label: 'PMS证据权重提高', value: 'PMS证据权重提高' },
  { label: 'AI测试权重提高', value: 'AI测试权重提高' },
  { label: '简历自述权重降低', value: '简历自述权重降低' },
  { label: '面试表现权重提高', value: '面试表现权重提高' },
]

const loading = ref(false)

watch(() => props.visible, (val) => {
  if (val) {
    currentStep.value = 0
    modifyType.value = ''
    resetForms()
  }
})

function resetForms() {
  replaceForm.value = {
    newTagId: null,
    newTagName: '',
    keepOldAsAlias: true,
    reason: '',
    applicableSources: [],
    positiveExamples: '',
    negativeExamples: '',
    sourceWeightAdvice: '',
  }
  levelForm.value = {
    newLevel: props.ability?.level || 3,
    reason: '',
    supportEvidence: [],
    counterEvidence: [],
    sourceWeightAdvice: '',
  }
  removeForm.value = {
    reason: '',
    misjudgedSource: '',
    addToRejectRule: false,
    replacementSuggestion: '',
  }
}

function selectModifyType(type: string) {
  modifyType.value = type
  currentStep.value = 1
}

function goBack() {
  currentStep.value = 0
  modifyType.value = ''
}

async function handleSubmit() {
  if (!props.ability) return

  loading.value = true
  try {
    if (modifyType.value === 'TAG_REPLACE') {
      if (!replaceForm.value.newTagId) {
        ElMessage.warning('请选择新标签')
        return
      }
      if (!replaceForm.value.reason) {
        ElMessage.warning('请填写修改原因')
        return
      }
      await replaceTag({
        empId: props.empId,
        oldTagId: props.ability.tagId,
        newTagId: replaceForm.value.newTagId,
        reason: replaceForm.value.reason,
        keepOldAsAlias: replaceForm.value.keepOldAsAlias,
        applicableSources: replaceForm.value.applicableSources,
        positiveExamples: replaceForm.value.positiveExamples.split(',').filter(Boolean),
        negativeExamples: replaceForm.value.negativeExamples.split(',').filter(Boolean),
        sourceWeightAdvice: replaceForm.value.sourceWeightAdvice,
      })
      ElMessage.success('标签替换成功')
    } else if (modifyType.value === 'LEVEL_CHANGE') {
      if (!levelForm.value.reason) {
        ElMessage.warning('请填写修正原因')
        return
      }
      await changeLevel({
        empId: props.empId,
        tagId: props.ability.tagId,
        newLevel: levelForm.value.newLevel,
        reason: levelForm.value.reason,
        supportEvidence: levelForm.value.supportEvidence,
        counterEvidence: levelForm.value.counterEvidence,
        sourceWeightAdvice: levelForm.value.sourceWeightAdvice,
      })
      ElMessage.success('等级修正成功')
    } else if (modifyType.value === 'REMOVE_TAG') {
      if (!removeForm.value.reason) {
        ElMessage.warning('请选择删除原因')
        return
      }
      await removeTag({
        empId: props.empId,
        tagId: props.ability.tagId,
        reason: removeForm.value.reason,
        misjudgedSource: removeForm.value.misjudgedSource,
        addToRejectRule: removeForm.value.addToRejectRule,
        replacementSuggestion: removeForm.value.replacementSuggestion,
      })
      ElMessage.success('标签删除成功')
    }
    emit('success')
    dialogVisible.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

const levelMap: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
  4: '专家',
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="人工修正能力标签"
    width="680px"
    :close-on-click-modal="false"
  >
    <div v-if="ability" class="governance-dialog">
      <!-- 当前能力信息 -->
      <div class="current-ability">
        <div class="current-ability__header">
          <span class="current-ability__label">当前能力</span>
          <el-tag v-if="ability.humanReviewed" type="success" size="small">已人工确认</el-tag>
          <el-tag v-else type="info" size="small">AI生成</el-tag>
        </div>
        <div class="current-ability__info">
          <div class="info-item">
            <span class="info-item__label">标签：</span>
            <span class="info-item__value">{{ ability.tagName }}</span>
          </div>
          <div class="info-item">
            <span class="info-item__label">等级：</span>
            <span class="info-item__value">{{ levelMap[ability.level] || ability.level }}</span>
          </div>
          <div class="info-item">
            <span class="info-item__label">置信度：</span>
            <span class="info-item__value">{{ Math.round(ability.confidence * 100) }}%</span>
          </div>
          <div class="info-item">
            <span class="info-item__label">来源：</span>
            <span class="info-item__value">
              <el-tag v-for="s in ability.sourceBreakdown" :key="s.sourceType" size="small" class="mr-1">
                {{ s.sourceType === 'RESUME_PARSE' ? '简历' : s.sourceType === 'AI_TEST' ? 'AI测试' : s.sourceType === 'AI_INTERVIEW' ? 'AI面试' : s.sourceType === 'AI_PROJECT' ? 'PMS' : s.sourceType }}
              </el-tag>
            </span>
          </div>
        </div>
      </div>

      <!-- 步骤1：选择修正类型 -->
      <div v-if="currentStep === 0" class="modify-types">
        <div class="modify-types__title">选择修正类型</div>
        <div class="modify-types__grid">
          <div class="modify-type-card" @click="selectModifyType('TAG_REPLACE')">
            <div class="modify-type-card__icon">🔄</div>
            <div class="modify-type-card__title">标签替换</div>
            <div class="modify-type-card__desc">将能力标签替换为另一个标签</div>
          </div>
          <div class="modify-type-card" @click="selectModifyType('LEVEL_CHANGE')">
            <div class="modify-type-card__icon">📊</div>
            <div class="modify-type-card__title">等级修正</div>
            <div class="modify-type-card__desc">调整能力掌握等级</div>
          </div>
          <div class="modify-type-card" @click="selectModifyType('REMOVE_TAG')">
            <div class="modify-type-card__icon">🗑️</div>
            <div class="modify-type-card__title">删除标签</div>
            <div class="modify-type-card__desc">移除不相关的能力标签</div>
          </div>
        </div>
      </div>

      <!-- 步骤2：填写表单 -->
      <div v-if="currentStep === 1" class="modify-form">
        <el-button link @click="goBack" class="mb-4">← 返回选择</el-button>

        <!-- 标签替换表单 -->
        <template v-if="modifyType === 'TAG_REPLACE'">
          <el-form label-width="100px" label-position="top">
            <el-form-item label="新标签" required>
              <el-input
                v-model="replaceForm.newTagName"
                placeholder="搜索标签库..."
                :prefix-icon="Search"
              />
              <!-- TODO: 标签搜索选择器 -->
            </el-form-item>
            <el-form-item label="保留原标签为别名">
              <el-switch v-model="replaceForm.keepOldAsAlias" />
            </el-form-item>
            <el-form-item label="修改原因" required>
              <el-input
                v-model="replaceForm.reason"
                type="textarea"
                :rows="3"
                placeholder="请说明替换原因..."
              />
            </el-form-item>
            <el-form-item label="适用来源">
              <el-checkbox-group v-model="replaceForm.applicableSources">
                <el-checkbox v-for="opt in sourceOptions" :key="opt.value" :label="opt.value">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="典型触发表达（逗号分隔）">
              <el-input v-model="replaceForm.positiveExamples" placeholder="例如：Spring Boot, SpringBoot" />
            </el-form-item>
            <el-form-item label="不应再生成的表达（逗号分隔）">
              <el-input v-model="replaceForm.negativeExamples" placeholder="例如：SpringBoot开发能力" />
            </el-form-item>
            <el-form-item label="来源权重建议">
              <el-select v-model="replaceForm.sourceWeightAdvice" placeholder="选择建议" clearable>
                <el-option v-for="opt in weightAdviceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-form>
        </template>

        <!-- 等级修正表单 -->
        <template v-if="modifyType === 'LEVEL_CHANGE'">
          <el-form label-width="100px" label-position="top">
            <el-form-item label="新等级" required>
              <el-slider
                v-model="levelForm.newLevel"
                :min="1"
                :max="5"
                :step="1"
                show-stops
                :marks="{ 1: '初级', 2: '中级', 3: '高级', 4: '专家', 5: '大师' }"
              />
            </el-form-item>
            <el-form-item label="修正原因" required>
              <el-input
                v-model="levelForm.reason"
                type="textarea"
                :rows="3"
                placeholder="请说明等级修正原因..."
              />
            </el-form-item>
            <el-form-item label="来源权重建议">
              <el-select v-model="levelForm.sourceWeightAdvice" placeholder="选择建议" clearable>
                <el-option v-for="opt in weightAdviceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-form>
        </template>

        <!-- 删除标签表单 -->
        <template v-if="modifyType === 'REMOVE_TAG'">
          <el-form label-width="100px" label-position="top">
            <el-form-item label="删除原因" required>
              <el-select v-model="removeForm.reason" placeholder="选择删除原因">
                <el-option v-for="opt in removeReasonOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="误判来源">
              <el-select v-model="removeForm.misjudgedSource" placeholder="选择误判来源" clearable>
                <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="加入拒绝规则">
              <el-switch v-model="removeForm.addToRejectRule" />
              <span class="ml-2 text-sm text-gray-500">开启后，Agent 将不再输出类似标签</span>
            </el-form-item>
            <el-form-item label="替代建议">
              <el-input
                v-model="removeForm.replacementSuggestion"
                placeholder="建议替代的能力标签..."
              />
            </el-form-item>
          </el-form>
        </template>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button v-if="currentStep === 1" type="primary" :loading="loading" @click="handleSubmit">
        确认提交
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.governance-dialog {
  @apply space-y-6;
}

.current-ability {
  @apply bg-gray-50 rounded-lg p-4;
}

.current-ability__header {
  @apply flex items-center gap-2 mb-3;
}

.current-ability__label {
  @apply text-sm font-medium text-gray-700;
}

.current-ability__info {
  @apply grid grid-cols-2 gap-3;
}

.info-item {
  @apply text-sm;
}

.info-item__label {
  @apply text-gray-500;
}

.info-item__value {
  @apply font-medium text-gray-900;
}

.modify-types__title {
  @apply text-sm font-medium text-gray-700 mb-3;
}

.modify-types__grid {
  @apply grid grid-cols-3 gap-4;
}

.modify-type-card {
  @apply border rounded-lg p-4 cursor-pointer transition-all hover:border-blue-400 hover:bg-blue-50;
}

.modify-type-card__icon {
  @apply text-2xl mb-2;
}

.modify-type-card__title {
  @apply font-medium text-gray-900 mb-1;
}

.modify-type-card__desc {
  @apply text-xs text-gray-500;
}

.modify-form {
  @apply mt-4;
}
</style>
