<script setup lang="ts">
/**
 * 简历解析页面
 * 上传简历文件，AI解析提取能力信息
 */
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  uploadAndParseResume,
  listResumeParseRecords,
  getResumeParseDetail,
  getResumeFile,
  reparseResume,
} from '@/api'
import type { ResumeParseRecord, AbilityImportResult } from '@/api'

const router = useRouter()
const route = useRoute()
const empId = ref(Number(route.query.empId) || 0)
const workflowId = ref(Number(route.query.workflowId) || 0)
const isAssessmentFlow = computed(() => route.query.fromAssessment === '1' && workflowId.value > 0)
const loading = ref(false)
const uploading = ref(false)
const records = ref<ResumeParseRecord[]>([])

const currentResult = ref<ResumeParseRecord | null>(null)
const resultDialogVisible = ref(false)
const reparseLoading = ref(false)
const showRawContent = ref(false)

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待解析', type: 'info' },
  1: { text: '解析中', type: 'warning' },
  2: { text: '已完成', type: 'success' },
  3: { text: '解析失败', type: 'danger' },
  4: { text: '等待重试', type: 'warning' },
}

onMounted(() => {
  if (empId.value) {
    loadRecords()
  }
})

async function loadRecords() {
  loading.value = true
  try {
    const res = await listResumeParseRecords(empId.value)
    records.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleUpload(file: File) {
  if (!empId.value) {
    ElMessage.warning('请先选择人员')
    return false
  }

  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  ]
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持PDF、DOC、DOCX格式的文件')
    return false
  }

  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }

  uploading.value = true
  try {
    const res = await uploadAndParseResume(empId.value, file)
    ElMessage.success('简历上传成功，正在解析')
    loadRecords()
    if (res.data.status === 2) {
      showResult(res.data)
    }
  } catch {
    // handled by interceptor
  } finally {
    uploading.value = false
  }
  return false
}

async function showResult(record: ResumeParseRecord) {
  currentResult.value = record
  showRawContent.value = false
  resultDialogVisible.value = true
  try {
    const res = await getResumeParseDetail(record.id)
    currentResult.value = res.data
  } catch {
    resultDialogVisible.value = false
    ElMessage.error('获取解析结果失败')
  }
}

function getParsedResult() {
  if (!currentResult.value?.aiAnalysisResult) return null
  try {
    const parsed = JSON.parse(currentResult.value.aiAnalysisResult)
    return normalizeResumeParseResult(parsed)
  } catch {
    return null
  }
}

function normalizeResumeParseResult(parsed: any) {
  if (!parsed || typeof parsed !== 'object') return null
  const basicInfo = normalizeBasicInfo(parsed)
  if (Array.isArray(parsed.abilities)) {
    return { ...parsed, basicInfo }
  }
  if (!Array.isArray(parsed.claims)) {
    return { ...parsed, basicInfo }
  }

  return {
    ...parsed,
    basicInfo,
    abilities: parsed.claims
      .map((claim: any) => ({
        tagName: claim.normalizedAbilityName || claim.abilityName || '',
        level: Number(claim.masteryLevel ?? claim.claimedLevel ?? 0),
        evidence: claim.evidenceText || claim.extractReason || '',
        confidence: claim.confidenceScore,
        tagId: claim.abilityTagId || claim.similarTagId,
        sourceRefs: claim.sourceRefs || [],
      }))
      .filter((ability: any) => ability.tagName && ability.level > 0),
  }
}

function parseJsonObject(raw: any) {
  if (!raw) return null
  if (typeof raw === 'object') return raw
  if (typeof raw !== 'string') return null
  try {
    return JSON.parse(raw)
  } catch {
    const start = raw.indexOf('{')
    const end = raw.lastIndexOf('}')
    if (start >= 0 && end > start) {
      try {
        return JSON.parse(raw.slice(start, end + 1))
      } catch {
        return null
      }
    }
    return null
  }
}

function normalizeBasicInfo(parsed: any) {
  const rawOutput = parseJsonObject(parsed?.rawModelOutput)
  const basicInfo = parsed?.basicInfo || rawOutput?.basicInfo || rawOutput?.resumeBasicInfo || {}
  const text = currentResult.value?.parsedContent || ''

  return {
    education: basicInfo.education || basicInfo.highestEducation || inferEducation(text),
    school: basicInfo.school || basicInfo.university || basicInfo.graduateSchool || inferSchool(text),
    major: basicInfo.major || inferMajor(text),
    workYears: basicInfo.workYears ?? basicInfo.yearsOfExperience ?? inferWorkYears(text),
    age: basicInfo.age ?? inferAge(text),
    lastPosition: basicInfo.lastPosition || basicInfo.currentPosition || basicInfo.position || inferLastPosition(text),
  }
}

function inferEducation(text: string) {
  if (!text) return undefined
  if (/博士|PhD/i.test(text)) return '博士'
  if (/硕士|研究生|Master/i.test(text)) return '硕士'
  if (/本科|学士|Bachelor/i.test(text)) return '本科'
  if (/大专|专科/i.test(text)) return '大专'
  return undefined
}

function inferSchool(text: string) {
  return text.match(/([\u4e00-\u9fa5A-Za-z0-9·\-]{2,30}(大学|学院|学校))/)?.[1]
}

function inferMajor(text: string) {
  return text.match(/专业[:：\s]*([\u4e00-\u9fa5A-Za-z0-9+\-#/ ]{2,30})/)?.[1]?.trim()
}

function inferWorkYears(text: string) {
  const match = text.match(/(\d+(?:\.\d+)?)\s*(年|年以上)\s*(工作经验|开发经验|经验)/)
  return match ? Number(match[1]) : undefined
}

function inferAge(text: string) {
  const match = text.match(/(?:年龄|Age)[:：\s]*(\d{2})/)
  return match ? Number(match[1]) : undefined
}

function inferLastPosition(text: string) {
  return text.match(/(?:职位|岗位|职务|应聘岗位)[:：\s]*([\u4e00-\u9fa5A-Za-z0-9+\-#/ ]{2,30})/)?.[1]?.trim()
}

function getBasicInfo() {
  const basicInfo = getParsedResult()?.basicInfo
  if (!basicInfo) return null
  return Object.values(basicInfo).some(value => value !== undefined && value !== null && value !== '')
    ? basicInfo
    : null
}

async function viewResume() {
  if (!currentResult.value) return
  try {
    const res = await getResumeFile(currentResult.value.id)
    // 拦截器对 blob 直接返回 AxiosResponse，取其 data
    const payload = (res as any).data ?? res
    const blob: Blob = payload instanceof Blob ? payload : new Blob([payload])
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch {
    ElMessage.error('查看简历失败')
  }
}

async function handleReparse() {
  if (!currentResult.value) return
  reparseLoading.value = true
  try {
    const res = await reparseResume(currentResult.value.id)
    ElMessage.success('重新解析完成')
    currentResult.value = res.data
    loadRecords()
  } catch {
    ElMessage.error('重新解析失败')
  } finally {
    reparseLoading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>简历解析</span>
          <el-button @click="isAssessmentFlow ? router.push({ path: '/employee/ability-profile/assessment', query: { empId, workflowId, fromAssessment: '1', refresh: String(Date.now()) } }) : router.back()">{{ isAssessmentFlow ? '返回评估流程' : '返回' }}</el-button>
        </div>
      </template>

      <div v-if="!empId" style="text-align: center; padding: 40px;">
        <el-empty description="请从人员能力画像页面选择人员后再操作" />
      </div>

      <template v-else>
        <el-upload
          class="upload-area"
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".pdf,.doc,.docx"
          :on-change="(file: any) => handleUpload(file.raw)"
          v-loading="uploading"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将简历文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 PDF、DOC、DOCX 格式，文件大小不超过 10MB</div>
          </template>
        </el-upload>

        <el-divider />

        <h4>解析记录</h4>
        <el-table :data="records" v-loading="loading" border stripe>
          <el-table-column prop="id" label="ID" width="80px" />
          <el-table-column prop="fileName" label="文件名" min-width="200px" show-overflow-tooltip />
          <el-table-column prop="fileType" label="文件类型" width="100px" />
          <el-table-column label="状态" width="100px">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.status]?.type as any" size="small">
                {{ statusMap[row.status]?.text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="上传时间" width="180px">
            <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250px" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 2" type="primary" link @click="showResult(row)">查看结果</el-button>
              <el-button type="info" link @click="currentResult = row; viewResume()">查看简历</el-button>
              <el-button v-if="row.status === 2 || row.status === 3" type="warning" link @click="currentResult = row; handleReparse()">重新解析</el-button>
              <span v-if="row.status === 3" style="color: #f56c6c; font-size: 12px;">暂未完成，可重新解析</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 解析结果弹窗 -->
    <el-dialog
      v-model="resultDialogVisible"
      title="简历解析结果"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="currentResult">
        <el-alert
          :title="isAssessmentFlow
            ? 'AI已从简历中提取以下能力主张；解析完成后系统会自动保存并进入 Harness 校验，不需要手动保存'
            : 'AI已从简历中提取以下能力主张；当前为结果查看模式，不提供手动保存入口'"
          type="success"
          :closable="false"
          style="margin-bottom: 16px;"
        />

        <template v-if="getParsedResult()">
          <!-- 基本信息 -->
          <div class="result-section">
            <h4>基本信息（用于硬性条件筛选）</h4>
            <template v-if="getBasicInfo()">
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="最高学历">{{ getBasicInfo()?.education ?? '未识别' }}</el-descriptions-item>
                <el-descriptions-item label="毕业院校">{{ getBasicInfo()?.school ?? '未识别' }}</el-descriptions-item>
                <el-descriptions-item label="专业">{{ getBasicInfo()?.major ?? '未识别' }}</el-descriptions-item>
                <el-descriptions-item label="工作年限">{{ getBasicInfo()?.workYears ?? '未识别' }} 年</el-descriptions-item>
                <el-descriptions-item label="年龄">{{ getBasicInfo()?.age ?? '未识别' }}</el-descriptions-item>
                <el-descriptions-item label="最近职位">{{ getBasicInfo()?.lastPosition ?? '未识别' }}</el-descriptions-item>
              </el-descriptions>
              <el-alert
                title="以上信息由AI从简历中提取，可能不准确。请点击「查看简历」核对原始内容。"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 8px;"
              />
            </template>
            <el-empty v-else description="AI未提取到基本信息（可能是旧版解析结果，建议点击「重新解析」）" :image-size="60" />
          </div>

          <!-- 简历摘要 -->
          <div v-if="getParsedResult()?.summary" class="result-section">
            <h4>简历摘要</h4>
            <p>{{ getParsedResult()?.summary }}</p>
          </div>

          <!-- 提取的能力 -->
          <div v-if="getParsedResult()?.abilities?.length > 0" class="result-section">
            <h4>提取的能力信息</h4>
            <el-table :data="getParsedResult()?.abilities" border size="small">
              <el-table-column prop="tagName" label="能力标签" width="150px" />
              <el-table-column label="掌握等级" width="120px">
                <template #default="{ row }">
                  <el-rate v-model="row.level" disabled :max="5" />
                </template>
              </el-table-column>
              <el-table-column prop="evidence" label="简历依据" show-overflow-tooltip />
            </el-table>
          </div>

          <!-- 工作经历 -->
          <div v-if="getParsedResult()?.workExperience" class="result-section">
            <h4>工作经历摘要</h4>
            <p>{{ getParsedResult()?.workExperience }}</p>
          </div>

          <!-- 教育背景 -->
          <div v-if="getParsedResult()?.education" class="result-section">
            <h4>教育背景</h4>
            <p>{{ getParsedResult()?.education }}</p>
          </div>

          <!-- 查看解析原文 -->
          <div class="result-section">
            <el-collapse v-model="showRawContent">
              <el-collapse-item title="查看AI解析的原始文本" name="raw">
                <pre style="background: #f5f7fa; padding: 12px; border-radius: 4px; max-height: 300px; overflow: auto; font-size: 12px; white-space: pre-wrap;">{{ currentResult.parsedContent }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </template>

        <el-empty v-else description="解析结果格式异常" />
      </div>

      <template #footer>
        <el-button @click="viewResume" type="info">查看原始简历</el-button>
        <el-button @click="handleReparse" :loading="reparseLoading" type="warning">重新解析</el-button>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.result-section {
  margin-bottom: 20px;
}

.result-section h4 {
  margin-bottom: 10px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 8px;
}

.result-section p {
  color: #606266;
  line-height: 1.8;
}
</style>
