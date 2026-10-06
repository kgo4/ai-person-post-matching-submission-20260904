<script setup lang="ts">
/**
 * 员工本人「我的匹配结果」（只读）。
 *
 * 与 /matching/result 的管理端匹配结果页不同：管理端页含审批、锁定、改分、导出等动作，
 * 员工侧调用这些接口一律 403。这里只读 HR **已推送**（publishStatus=1）的本人记录，
 * 人员范围由服务端按登录身份固定为本人，前端不传 empId。
 *
 * 【2026-09-04 职责拆分】本页原先还内嵌「发起匹配」弹窗，现该动作已独立为
 * `/matching/self-execute`（侧边栏「人岗匹配 → 发起匹配」）。
 * 本页从此只负责**呈现已推送结果**，不再承担产出动作 —— 两者的准入条件、
 * 数据来源、失败语义都不同，混在一起会让入口既不显眼、语义也不清楚。
 *
 * 【2026-09-04 口径】员工侧可见性只看推送状态：匹配结果不再以「审批通过」为前置条件，
 * HR 人工查看/修改结果本身即为审核动作，确认后可直接推送。此前叠了 approvalStatus=2，
 * 导致「HR 点推送后员工看不到」——推送把 publishStatus 置 1，但 approvalStatus 仍是 0。
 *
 * 依赖匹配结果的员工侧功能（差距诊断）同样受推送闸门约束：没有已推送记录时入口不可用。
 *
 * 分数取值口径与管理端一致：优先 finalMatchScore，缺失时回退 aiMatchScore。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, Refresh, TrendCharts } from '@element-plus/icons-vue'
import { listEnabledPosts, pageRecords } from '@/api'
import type { MatchingRecord, PostPost } from '@/api'
import {
  getMatchStatusText,
  getMatchStatusType,
  getScoreColor,
} from '@/views/matching/detail/utils'
import { useUserStore } from '@/store/modules/user'
import { resolveApiErrorMessage } from '@/utils/request-error-message'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const error = ref('')
const records = ref<MatchingRecord[]>([])
const total = ref(0)
const posts = ref<PostPost[]>([])

const query = reactive({
  current: 1,
  size: 10,
  postId: '' as number | '',
  matchStatus: '' as number | '',
})

/** 员工侧不提供全量导出与审批动作，这里只做只读统计，口径与本页数据一致 */
const topScore = computed(() => {
  const scores = records.value
    .map((row) => row.finalMatchScore ?? row.aiMatchScore)
    .filter((score): score is number => typeof score === 'number')
  return scores.length ? Math.max(...scores) : null
})

/** 有已推送记录才解锁差距诊断等依赖匹配结果的员工侧功能 */
const hasPublishedRecord = computed(() => records.value.length > 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await pageRecords({
      current: query.current,
      size: query.size,
      postId: query.postId === '' ? undefined : query.postId,
      matchStatus: query.matchStatus === '' ? undefined : query.matchStatus,
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    records.value = []
    total.value = 0
    // 统一归一化：英文技术文案不透出（见 utils/request-error-message.ts）
    error.value = resolveApiErrorMessage(e, '匹配结果加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  try {
    const res = await listEnabledPosts()
    posts.value = res.data || []
  } catch {
    posts.value = []
  }
}

function handleSearch() {
  query.current = 1
  load()
}

function handleReset() {
  query.postId = ''
  query.matchStatus = ''
  handleSearch()
}

function handleSizeChange(size: number) {
  query.size = size
  query.current = 1
  load()
}

function handleCurrentChange(page: number) {
  query.current = page
  load()
}

function scoreOf(row: MatchingRecord) {
  return row.finalMatchScore ?? row.aiMatchScore ?? null
}

function openGapDiagnosis(row: MatchingRecord) {
  router.push({ path: '/matching/gap-diagnosis', query: { recordId: String(row.id) } })
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '--'
}

onMounted(async () => {
  const empId = await userStore.ensureEmpId()
  await Promise.all([loadPosts(), load()])
  if (empId == null) {
    // 用常驻 el-alert 说明，不用 toast 一闪而过 —— 员工看不到原因就只会认为“没有入口”。
    // 放在 load() 之后覆盖，因为「未绑定档案」比接口报错更接近根因。
    error.value = '当前账号尚未绑定人员档案，请联系 HR 或权限管理员完成绑定'
  }
})
</script>

<template>
  <div class="page-shell mr">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">My Matching</span>
        <h1 class="page-hero__title">我的匹配结果</h1>
        <p class="page-hero__desc">
          HR 审核通过并推送后的本人匹配结果会显示在这里，你可以在“我的差距诊断”中查看能力差距与提升建议。
          需要试配新岗位时，请到「人岗匹配 → 发起匹配」提交。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">已推送记录：{{ total }} 条</span>
          <span class="hero-chip">本页最高分：{{ topScore == null ? '--' : topScore }}</span>
        </div>
      </div>
      <div class="mr__actions">
        <el-tooltip
          :disabled="hasPublishedRecord"
          content="需要先有 HR 已推送的匹配结果"
          placement="top"
        >
          <span>
            <el-button
              :icon="TrendCharts"
              :disabled="!hasPublishedRecord"
              @click="router.push('/matching/gap-diagnosis')"
            >
              我的差距诊断
            </el-button>
          </span>
        </el-tooltip>
        <el-button :icon="Reading" @click="router.push('/learning/path')">我的学习路径</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <section class="glass-card mr__card">
      <header class="mr__head">
        <div>
          <h2>已推送匹配结果</h2>
          <p>仅本人数据；岗位与匹配状态可在本人范围内进一步筛选</p>
        </div>
        <div class="mr__filters">
          <el-select v-model="query.postId" placeholder="全部岗位" clearable class="mr__filter" @change="handleSearch">
            <el-option v-for="post in posts" :key="post.id" :label="post.postName" :value="post.id" />
          </el-select>
          <el-select v-model="query.matchStatus" placeholder="全部状态" clearable class="mr__filter" @change="handleSearch">
            <el-option label="匹配通过" :value="1" />
            <el-option label="待定" :value="0" />
            <el-option label="不匹配" :value="2" />
          </el-select>
          <el-button text @click="handleReset">重置</el-button>
        </div>
      </header>

      <el-table v-loading="loading" :data="records" stripe size="default">
        <!--
          空态必须把「为什么是空的」说清楚：本页只显示 HR **已推送**的记录，
          所以员工刚提交申请时这里必然是空的 —— 若只写「暂无数据」，
          员工会以为提交失败。这是「发起匹配后看不到任何反馈」的一半原因。
        -->
        <template #empty>
          <div class="mr__empty">
            <p class="mr__empty-title">暂无已推送的匹配结果</p>
            <p class="mr__empty-hint">
              如果你刚在「发起匹配」提交了申请，它需要 HR 审核并推送后才会出现在这里，
              审核期间本页为空属于正常状态。
            </p>
            <el-button size="small" type="primary" plain @click="router.push('/matching/self-execute')">
              去发起匹配
            </el-button>
          </div>
        </template>
        <el-table-column label="岗位" min-width="180">
          <template #default="{ row }">{{ row.postName || `岗位 #${row.postId}` }}</template>
        </el-table-column>
        <el-table-column label="匹配得分" width="120" align="center">
          <template #default="{ row }">
            <span class="mr__score" :style="{ color: getScoreColor(scoreOf(row)) }">
              {{ scoreOf(row) ?? '--' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="匹配状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getMatchStatusType(row.matchStatus)">
              {{ getMatchStatusText(row.matchStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推送状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.publishStatus === 1 ? 'success' : 'info'">
              {{ row.publishStatus === 1 ? '已推送' : '未推送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedTime || row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openGapDiagnosis(row)">差距诊断</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mr__pager">
        <el-pagination
          :current-page="query.current"
          :page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.mr__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.mr__card {
  padding: 18px 20px;
}
.mr__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.mr__head h2 {
  margin: 0;
  color: #1a2440;
  font-size: 15px;
  font-weight: 700;
}
.mr__head p {
  margin: 6px 0 0;
  color: #8b95ab;
  font-size: 12px;
}
.mr__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.mr__filter {
  width: 160px;
}
.mr__score {
  font-size: 14px;
  font-weight: 700;
}
.mr__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.mr__empty {
  padding: 18px 12px;
  text-align: center;
}
.mr__empty-title {
  margin: 0;
  color: #1a2440;
  font-size: 14px;
  font-weight: 600;
}
.mr__empty-hint {
  margin: 8px auto 12px;
  max-width: 520px;
  color: #8b95ab;
  font-size: 12px;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .mr__filter {
    width: 100%;
  }
}
</style>
