<script setup lang="ts">
/**
 * 员工自助发起匹配（独立入口，替代原先塞在「我的匹配结果」里的弹窗）。
 *
 * 为什么独立成页：「我的匹配结果」的职责是**呈现已推送结果**，
 * 而发起匹配是**产出动作**，两者混在一起会让入口既不显眼、语义也不清楚。
 * 拆开后侧边栏有明确入口，且人员范围/审批流程可以用整页篇幅讲清楚。
 *
 * 三条硬约束（与后端 SecurityConfig + MatchingRecordApiFacade 同口径）：
 *   1. 人员固定为本人 —— 请求体不带 empId，服务端按登录身份固定；
 *   2. 岗位只能选一个 —— 单人单岗，提交的是单元素数组；
 *   3. 必须先完成全部能力项审核（能力分析报告已生成）—— 见 gate.ts。
 *
 * 准入闸门不达标时**不隐藏入口**，而是用常驻 el-alert 说明还差什么并给出跳转，
 * 否则员工只会看到「没有权限」，无法自救。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Promotion, Refresh, UserFilled } from '@element-plus/icons-vue'
import { executeSelfMatching, getLatestCapabilityReport, listEnabledPosts } from '@/api'
import { getAssessmentProfile } from '@/api/assessment'
import type { PostPost } from '@/api'
import { useUserStore } from '@/store/modules/user'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { SELF_MATCH_TARGET_TEXT, resolveApplyGate } from './gate'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const submitError = ref('')

/**
 * 本次提交的落地结果（提交成功后**常驻展示**）。
 *
 * 为什么不用一次性弹窗：匹配提交是「落地类操作」，弹窗关掉后页面上不留任何痕迹，
 * 员工既看不到自己提交了什么，也不知道现在处于哪个环节（此前只转个圈就结束，
 * 表现就是「提交完没有任何状态提示」）。这里把结果落在页面上，
 * 并明确写出「当前等待 HR 审核」，员工不用靠猜。
 */
const submitted = ref<{ count: number; postName: string; at: string } | null>(null)

const posts = ref<PostPost[]>([])
const selectedPostId = ref<number | ''>('')
const empId = ref<number | null>(null)
const empName = ref('')

/** 评估流程未走完；默认 true（保守：读取失败时不放行，避免发起后立即失败） */
const assessmentPending = ref(true)
/** 能力分析报告已生成（全部能力项审核完成的产物） */
const reportReady = ref(false)
/** 闸门判定所需的两个状态是否已读取完成（未完成时先不渲染结论） */
const gateLoaded = ref(false)

const gate = computed(() =>
  resolveApplyGate({
    empId: empId.value,
    assessmentPending: assessmentPending.value,
    reportReady: reportReady.value,
  }),
)

const canApply = computed(() => gate.value.canApply)

/** 选中的岗位名，用于提交前的二次确认文案 */
const selectedPostName = computed(
  () => posts.value.find(post => post.id === selectedPostId.value)?.postName ?? '',
)

async function loadPosts() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listEnabledPosts()
    posts.value = res.data || []
  } catch (e) {
    posts.value = []
    loadError.value = resolveApiErrorMessage(e, '岗位列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 读取评估流程状态；失败时保守视为「未完成」 */
async function loadAssessmentState() {
  if (empId.value == null) {
    assessmentPending.value = true
    gateLoaded.value = true
    return
  }
  try {
    const res = await getAssessmentProfile(empId.value)
    assessmentPending.value = res.data?.assessmentPending === true
  } catch {
    assessmentPending.value = true
  }

  try {
    const res = await getLatestCapabilityReport(empId.value)
    reportReady.value = !!res.data
  } catch {
    reportReady.value = false
  }
  gateLoaded.value = true
}

function goAssessment() {
  const path = gate.value.actionPath
  if (path) router.push(path)
}

async function submit() {
  submitError.value = ''
  submitted.value = null
  if (selectedPostId.value === '') {
    submitError.value = '请先选择要试配的岗位'
    return
  }
  const postName = selectedPostName.value
  submitting.value = true
  try {
    // 员工侧固定为单人（本人）× 单岗：后端契约仍是 postIds 数组，这里只传一个元素
    const res = await executeSelfMatching([selectedPostId.value])
    const count = res.data?.records?.length ?? 1
    // 后端的 self/execute 是**同步**执行（要跑完向量召回与打分才返回），
    // 记录此刻已经写库，所以「等 HR 审核」是真实状态，不是乐观提示。
    submitted.value = {
      count,
      postName: postName || '所选岗位',
      at: new Date().toLocaleString('zh-CN'),
    }
    selectedPostId.value = ''
  } catch (e) {
    // 表单类操作不用 toast 兜底：保留已填内容，用常驻提示展原因
    submitError.value = resolveApiErrorMessage(e, '发起匹配失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function reset() {
  selectedPostId.value = ''
  submitError.value = ''
  loadPosts()
}

onMounted(async () => {
  empId.value = await userStore.ensureEmpId()
  empName.value = userStore.userInfo?.realName ?? ''
  await Promise.all([loadPosts(), loadAssessmentState()])
})
</script>

<template>
  <div class="page-shell se">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">Self Matching</span>
        <h1 class="page-hero__title">发起匹配</h1>
        <p class="page-hero__desc">
          {{ SELF_MATCH_TARGET_TEXT }}选择岗位提交后进入 HR 审核队列，
          HR 审核并推送后可在「我的匹配结果」中查看结果。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">
            <el-icon><UserFilled /></el-icon>
            匹配人员：{{ empName || '本人' }}
          </span>
          <span class="hero-chip">可选岗位：{{ posts.length }} 个</span>
        </div>
      </div>
      <div class="se__actions">
        <el-button :icon="Refresh" :loading="loading" @click="reset">刷新岗位</el-button>
        <el-button @click="router.push('/matching/my-result')">查看我的匹配结果</el-button>
      </div>
    </section>

    <!-- 岗位列表读取失败：常驻告警，不静默 -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="se__notice"
    />

    <!-- 准入闸门不达标：入口常驻可见，但明确写出还差什么 + 跳转补齐 -->
    <el-alert
      v-if="gateLoaded && !canApply"
      title="暂不能发起匹配"
      type="warning"
      show-icon
      :closable="false"
      class="se__notice"
    >
      <template #default>
        <p class="se__notice-text">{{ gate.reason }}</p>
        <el-button v-if="gate.actionPath" size="small" type="primary" plain @click="goAssessment">
          {{ gate.actionLabel }}
        </el-button>
      </template>
    </el-alert>

    <el-alert
      v-else-if="gateLoaded"
      title="可以发起匹配"
      type="success"
      show-icon
      :closable="false"
      class="se__notice"
      description="你的能力项已全部审核完成。选择岗位提交后，HR 审核并推送结果。"
    />

    <section class="glass-card se__card">
      <header class="se__head">
        <div>
          <h2>选择试配岗位</h2>
          <p>一次只能选择一个岗位；如需试配其他岗位，可提交后再次发起</p>
        </div>
      </header>

      <el-form label-position="top" class="se__form" @submit.prevent>
        <el-form-item label="匹配人员">
          <el-input :model-value="empName || '本人'" disabled />
        </el-form-item>

        <el-form-item label="试配岗位">
          <el-select
            v-model="selectedPostId"
            filterable
            clearable
            :disabled="!canApply || loading"
            placeholder="请选择岗位"
            class="se__select"
          >
            <el-option v-for="post in posts" :key="post.id" :label="post.postName" :value="post.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="submitError"
        :title="submitError"
        type="error"
        show-icon
        :closable="false"
        class="se__notice"
      />

      <!-- 提交进行中：说明「为什么慢、要等多久」，不让员工对着一个转圈按钮干等 -->
      <el-alert
        v-if="submitting"
        type="info"
        show-icon
        :closable="false"
        class="se__notice"
        title="正在为你试配"
        description="系统需要逐个岗位计算能力匹配并写入匹配记录，可能需要十几秒，请勿关闭页面。"
      />

      <!-- 提交成功：常驻结果卡，明确写出「提交了什么」与「现在等到哪一步」 -->
      <el-alert
        v-if="submitted"
        type="success"
        show-icon
        :closable="false"
        class="se__notice"
      >
        <template #default>
          <p class="se__notice-text">
            已提交 {{ submitted.count }} 条匹配申请（岗位：{{ submitted.postName }}），提交时间 {{ submitted.at }}。<br />
            当前状态：<strong>等待 HR 审核</strong> —— HR 审核并推送后，结果才会出现在「我的匹配结果」中。
          </p>
          <el-button size="small" type="primary" plain @click="router.push('/matching/my-result')">
            查看我的匹配结果
          </el-button>
        </template>
      </el-alert>

      <div class="se__footer">
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="submitting"
          :disabled="!canApply"
          @click="submit"
        >
          提交匹配{{ selectedPostName ? `（${selectedPostName}）` : '' }}
        </el-button>
        <span v-if="!canApply" class="se__blocked">当前不可发起：{{ gate.reason }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.se__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.se__notice {
  margin-bottom: 12px;
}
.se__notice-text {
  margin: 0 0 8px;
  line-height: 1.7;
}
.se__card {
  padding: 18px 20px;
}
.se__head {
  margin-bottom: 12px;
}
.se__head h2 {
  margin: 0;
  color: #1a2440;
  font-size: 15px;
  font-weight: 700;
}
.se__head p {
  margin: 6px 0 0;
  color: #8b95ab;
  font-size: 12px;
}
.se__form {
  max-width: 460px;
}
.se__select {
  width: 100%;
}
.se__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.se__blocked {
  color: #8b95ab;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .se__form {
    max-width: 100%;
  }
}
</style>
