<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()

const form = reactive({
  softRequirements: '',
  cultureFit: '',
  growthPotential: '',
  teamFit: '',
  specialRequirements: '',
})

async function handleSubmit() {
  // TODO: 调用API保存
  ElMessage.success('保存成功')
  router.back()
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>岗位隐性要求录入</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-width="140px" style="max-width: 700px;">
        <el-form-item label="软性技能要求">
          <el-input v-model="form.softRequirements" type="textarea" :rows="3" placeholder="如：沟通能力、抗压能力、学习能力等" />
        </el-form-item>
        <el-form-item label="文化适配要求">
          <el-input v-model="form.cultureFit" type="textarea" :rows="3" placeholder="如：团队协作氛围、加班接受度等" />
        </el-form-item>
        <el-form-item label="成长潜力要求">
          <el-input v-model="form.growthPotential" type="textarea" :rows="3" placeholder="如：学习意愿、职业规划匹配度等" />
        </el-form-item>
        <el-form-item label="团队适配要求">
          <el-input v-model="form.teamFit" type="textarea" :rows="3" placeholder="如：现有团队构成、性格互补等" />
        </el-form-item>
        <el-form-item label="特殊要求">
          <el-input v-model="form.specialRequirements" type="textarea" :rows="3" placeholder="其他特殊要求" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
