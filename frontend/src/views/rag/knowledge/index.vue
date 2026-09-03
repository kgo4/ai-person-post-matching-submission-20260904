<template>
  <div class="rag-workbench">
    <!-- 顶部：标题 + 操作按钮 -->
    <header class="workbench-header">
      <div class="header-left">
        <div class="title-badge">
          <el-icon :size="18"><Collection /></el-icon>
        </div>
        <div class="title-group">
          <h1 class="page-title">AI 知识资产</h1>
          <span class="page-subtitle">AI 回答问题的资料底库 —— 岗位建模、匹配分析、学习推荐等 AI 功能都从这里取资料</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain @click="goGraph">
          查看知识图谱
          <el-icon class="el-icon--right"><Right /></el-icon>
        </el-button>
      </div>
    </header>

    <!-- 业务定位引导卡 -->
    <div class="guide-card">
      <div class="guide-card__icon">
        <el-icon :size="20"><Reading /></el-icon>
      </div>
      <div class="guide-card__main">
        <div class="guide-card__title">这些资料是 AI 回答问题的依据</div>
        <div class="guide-card__desc">岗位建模、匹配分析、学习推荐、岗位演化、面试追问、证据追溯、竞赛报告、幻觉审核等 AI 功能回答问题时，都会从这里检索资料作为依据。</div>
      </div>
      <div class="guide-card__meta">
        <span class="guide-pill guide-pill--blue">
          <el-icon :size="12"><Files /></el-icon>
          资料来源：JD、岗位原型、能力标签、学习资源、人员能力、岗位能力模型、来源证据、手动补充
        </span>
        <span class="guide-pill guide-pill--muted">
          <el-icon :size="12"><Share /></el-icon>
          与图谱的区别：图谱看关系，这里管资料
        </span>
      </div>
    </div>

    <!-- 知识资产流转图：帮助用户理解资料如何被 AI 使用 -->
    <section class="knowledge-flow" aria-label="知识资产流转流程">
      <div class="knowledge-flow__heading">
        <div>
          <div class="knowledge-flow__eyebrow">资料如何产生价值</div>
          <h2 class="knowledge-flow__title">从业务资料到 AI 场景引用</h2>
        </div>
        <span class="knowledge-flow__note">每一步都可追溯</span>
      </div>
      <div class="knowledge-flow__steps">
        <div class="flow-step flow-step--blue">
          <span class="flow-step__icon"><el-icon :size="18"><FolderOpened /></el-icon></span>
          <div><strong>业务资料</strong><small>JD、岗位、人员、学习资源</small></div>
        </div>
        <el-icon class="flow-arrow"><Right /></el-icon>
        <div class="flow-step flow-step--cyan">
          <span class="flow-step__icon"><el-icon :size="18"><Document /></el-icon></span>
          <div><strong>知识文档</strong><small>统一标题、来源和正文</small></div>
        </div>
        <el-icon class="flow-arrow"><Right /></el-icon>
        <div class="flow-step flow-step--green">
          <span class="flow-step__icon"><el-icon :size="18"><Grid /></el-icon></span>
          <div><strong>分片/索引</strong><small>切成可检索的资料片段</small></div>
        </div>
        <el-icon class="flow-arrow"><Right /></el-icon>
        <div class="flow-step flow-step--purple">
          <span class="flow-step__icon"><el-icon :size="18"><MagicStick /></el-icon></span>
          <div><strong>AI 场景引用</strong><small>建模、匹配、推荐、审核</small></div>
        </div>
      </div>
    </section>

    <!-- 指标卡 -->
    <div class="stats-grid">
      <div class="stat-card stat-clickable" @click="filterByStatus('all')">
        <span class="stat-card__icon stat-icon--primary"><el-icon :size="16"><Document /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value">{{ stats.totalDocs }}</strong>
          <span class="stat-card__label">资料总数</span>
        </div>
        <div class="stat-card__bar"><i class="bar-fill bar-fill--primary" :style="{ width: readyRatio + '%' }"></i></div>
      </div>
      <div class="stat-card stat-clickable" @click="filterByStatus('indexed')">
        <span class="stat-card__icon stat-icon--success"><el-icon :size="16"><CircleCheck /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value">{{ stats.indexedDocs }}</strong>
          <span class="stat-card__label">已就绪</span>
        </div>
        <div class="stat-card__bar"><i class="bar-fill bar-fill--success" :style="{ width: readyRatio + '%' }"></i></div>
      </div>
      <div class="stat-card stat-clickable" @click="filterByStatus('unindexed')">
        <span class="stat-card__icon stat-icon--warning"><el-icon :size="16"><Clock /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value" :class="{ 'is-warn': stats.unindexedDocs > 0 }">{{ stats.unindexedDocs }}</strong>
          <span class="stat-card__label">待就绪</span>
        </div>
        <div class="stat-card__bar"><i class="bar-fill bar-fill--warning" :style="{ width: pendingRatio + '%' }"></i></div>
      </div>
      <div class="stat-card">
        <span class="stat-card__icon stat-icon--cyan"><el-icon :size="16"><Files /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value">{{ stats.totalChunks }}</strong>
          <span class="stat-card__label">资料片段</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="stat-card__icon stat-icon--purple"><el-icon :size="16"><MagicStick /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value">{{ AI_SCENARIO_COUNT }}</strong>
          <span class="stat-card__label">AI 使用场景</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="stat-card__icon stat-icon--slate"><el-icon :size="16"><Timer /></el-icon></span>
        <div class="stat-card__body">
          <strong class="stat-card__value stat-card__value--sm">{{ stats.lastIndexed || '-' }}</strong>
          <span class="stat-card__label">最近就绪</span>
        </div>
      </div>
    </div>

    <!-- 三栏工作区 -->
    <div class="workbench-body">
      <!-- 左侧筛选 -->
      <aside class="sidebar-left">
        <div class="sidebar-section">
          <label class="filter-label">来源类型</label>
          <el-select v-model="searchForm.sourceType" clearable placeholder="全部" style="width: 100%">
            <el-option label="JD 导入" value="JD_IMPORT" />
            <el-option label="岗位原型" value="POST_PROTOTYPE" />
            <el-option label="能力标签" value="ABILITY_TAG" />
            <el-option label="学习资源" value="LEARNING_RESOURCE" />
            <el-option label="人员能力" value="EMP_ABILITY" />
            <el-option label="岗位能力模型" value="POST_ABILITY_MODEL" />
            <el-option label="来源证据" value="CONTEST_EVIDENCE" />
            <el-option label="手动补充" value="MANUAL_TEXT" />
          </el-select>
        </div>
        <div class="sidebar-section">
          <label class="filter-label">标题搜索</label>
          <el-input v-model="searchForm.title" placeholder="模糊搜索" clearable />
        </div>
        <div class="sidebar-section">
          <label class="filter-label">就绪状态</label>
          <el-segmented v-model="statusFilter" :options="statusOptions" style="width: 100%" />
        </div>
        <div class="sidebar-actions">
          <el-button type="primary" style="width: 100%" @click="loadDocuments">查询</el-button>
          <el-button style="width: 100%; margin-left: 0; margin-top: 8px" @click="resetFilters">重置</el-button>
        </div>
      </aside>

      <!-- 中间：文档池 -->
      <main class="main-content">
        <div class="table-head">
          <div class="table-head__title">
            <span class="table-head__icon"><el-icon :size="14"><Files /></el-icon></span>
            资料清单
            <span class="table-head__count">{{ pagination.total }} 份</span>
          </div>
          <div class="table-head__hint">点击「就绪」可对单份资料构建索引</div>
        </div>
        <el-table :data="documents" v-loading="loading" stripe size="small" height="100%" class="doc-table">
          <el-table-column label="资料名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="doc-name">
                <el-icon :size="13" class="doc-name__icon"><Document /></el-icon>
                {{ displayText(row.title, '未命名文档') }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="sourceType" label="来源" width="118">
            <template #default="{ row }">
              <el-tag size="small" effect="light" round class="src-tag">
                <i class="src-tag__dot" :style="{ background: sourceTypeColor(row.sourceType) }"></i>
                {{ sourceTypeLabel(row.sourceType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="docStatus" label="就绪状态" width="96" align="center">
            <template #default="{ row }">
              <span class="doc-status" :class="row.docStatus === 'ACTIVE' ? 'is-ready' : 'is-pending'">
                <i class="status-dot"></i>{{ row.docStatus === 'ACTIVE' ? '已就绪' : '待就绪' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="片段数" width="72" align="center">
            <template #default="{ row }">
              <span class="chunk-badge">{{ row.chunkCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastIndexedTime" label="最近就绪" width="150">
            <template #default="{ row }">
              <span class="time-cell">
                <el-icon :size="12"><Clock /></el-icon>
                {{ formatTime(row.lastIndexedTime) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="updatedTime" label="更新时间" width="150">
            <template #default="{ row }">
              <span class="time-cell">
                <el-icon :size="12"><RefreshRight /></el-icon>
                {{ formatTime(row.updatedTime) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                class="index-btn"
                @click="handleIndex(row)"
              >{{ row.docStatus === 'ACTIVE' ? '重新就绪' : '就绪' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          size="small"
          class="table-pager"
          @current-change="loadDocuments"
          @size-change="loadDocuments"
        />
      </main>

      <!-- 右侧面板 -->
      <aside class="sidebar-right">
        <!-- 检索演示 -->
        <div class="right-section">
          <div class="section-title-row">
            <h3 class="section-title">
              <span class="section-title__icon"><el-icon :size="14"><Search /></el-icon></span>
              试一试:AI 会引用哪些资料
            </h3>
            <span class="section-title__tag">演示</span>
          </div>
          <div class="search-form-compact">
            <el-select v-model="chunkSearch.scenario" clearable placeholder="全部场景" style="width: 100%">
              <el-option label="岗位建模时" value="POST_ABILITY_EXTRACT" />
              <el-option label="员工画像时" value="EMP_ABILITY_ANALYSIS" />
              <el-option label="匹配分析时" value="MATCH_GAP_DIAGNOSIS" />
              <el-option label="学习推荐时" value="LEARNING_PATH_SUGGESTION" />
              <el-option label="岗位演化时" value="POST_EVOLUTION" />
              <el-option label="幻觉审核时" value="EVIDENCE_GOVERNANCE" />
            </el-select>
            <el-input
              v-model="chunkSearch.queryText"
              type="textarea"
              :rows="2"
              placeholder="输入一个岗位要求或员工描述试试"
              class="query-input"
            />
            <el-button type="primary" class="search-btn" :loading="chunkLoading" @click="handleChunkSearch">
              <el-icon v-if="!chunkLoading" class="el-icon--left"><Search /></el-icon>检索
            </el-button>
          </div>

          <!-- 检索结果 -->
          <div class="search-results" v-loading="chunkLoading">
            <template v-if="chunkResults.length > 0">
              <div v-for="hit in chunkResults" :key="hit.chunkId" class="result-card">
                <div class="result-title">
                  <el-icon :size="12" class="result-title__icon"><Document /></el-icon>
                  {{ hit.documentTitle }}
                </div>
                <div class="result-meta">
                  <el-tag size="small" effect="light" round class="src-tag src-tag--sm">
                    <i class="src-tag__dot" :style="{ background: sourceTypeColor(hit.sourceType) }"></i>
                    {{ sourceTypeLabel(hit.sourceType) }}
                  </el-tag>
                </div>
                <div class="result-score">
                  <span>相关度</span>
                  <div class="score-bar"><i :style="{ width: scorePct(hit.score) + '%' }"></i></div>
                  <b>{{ scorePct(hit.score) }}%</b>
                </div>
                <div class="result-text">{{ hit.chunkText }}</div>
              </div>
            </template>
            <el-empty v-else-if="!chunkLoading && chunkSearched" description="暂无命中" :image-size="48" />
            <div v-else-if="!chunkLoading && !chunkSearched" class="search-idle">
              <el-icon :size="20"><MagicStick /></el-icon>
              <span>选择一个场景并输入问题，查看 AI 将引用哪些资料片段</span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 高级运维(面向管理员) -->
    <div class="ops-collapse">
      <el-collapse>
        <el-collapse-item title="高级运维(面向管理员)" name="ops">
          <div class="ops-row">
            <el-button @click="showCreateDialog = true">新建文档</el-button>
            <el-button @click="showBackfillDialog = true">从业务数据生成</el-button>
            <el-button type="primary" :loading="batchIndexLoading" @click="handleBatchIndex">构建索引</el-button>
            <el-tooltip :content="cloudStatus.usable ? '同步本地文档到云端知识库' : '云端知识库未配置'" placement="bottom">
              <el-button
                :type="cloudStatus.usable ? 'primary' : 'default'"
                :disabled="!cloudStatus.usable"
                @click="showCloudSyncDialog = true"
              >同步云端</el-button>
            </el-tooltip>
          </div>

          <div class="ops-cloud">
            <div class="section-header-row">
              <h3 class="section-title">云端知识库</h3>
              <div class="section-header-actions">
                <el-tag :type="cloudStatus.usable ? 'success' : 'info'" size="small" effect="plain">
                  {{ cloudStatus.usable ? '已连接' : '未配置' }}
                </el-tag>
                <el-button link type="primary" size="small" @click="openCloudConfig">配置</el-button>
              </div>
            </div>

            <template v-if="cloudStatus.usable">
              <div class="cloud-info">
                <div class="cloud-info-row">
                  <span class="cloud-label">模式</span>
                  <span>{{ cloudStatus.providerMode || 'mysql' }}</span>
                </div>
                <div class="cloud-info-row">
                  <span class="cloud-label">资源</span>
                  <span class="text-muted">{{ cloudStatus.resourceId || '-' }}</span>
                </div>
              </div>
              <el-input
                v-model="cloudSearch.queryText"
                type="textarea"
                :rows="2"
                placeholder="云端检索"
                style="margin-top: 8px"
              />
              <el-button
                style="width: 100%; margin-top: 8px"
                :loading="cloudSearchLoading"
                @click="handleCloudSearch"
              >云端检索</el-button>

              <div v-if="cloudSearchResult" class="cloud-result-brief">
                <span>命中 {{ cloudSearchResult.hitCount }}</span>
                <span>{{ cloudSearchResult.latencyMs }}ms</span>
                <el-tag v-if="cloudSearchResult.fallbackUsed" size="small" type="warning">已降级</el-tag>
              </div>
            </template>

            <template v-else>
              <p class="text-muted" style="font-size: 12px; margin: 8px 0 0">未配置云端连接</p>
            </template>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 新建文档对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建检索文档" width="520px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="来源类型">
          <el-select v-model="createForm.sourceType" style="width: 100%">
            <el-option label="手动补充" value="MANUAL_TEXT" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建并索引</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCloudConfigDialog" title="配置云端知识库" width="520px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px">
        配置仅作用于当前服务实例；生产环境建议同时写入服务端环境变量，重启后配置不会丢失。
      </el-alert>
      <el-form :model="cloudConfigForm" label-width="100px">
        <el-form-item label="启用云端"><el-switch v-model="cloudConfigForm.enabled" /></el-form-item>
        <el-form-item label="服务地址"><el-input v-model="cloudConfigForm.endpoint" placeholder="https://..." /></el-form-item>
        <el-form-item label="区域"><el-input v-model="cloudConfigForm.region" /></el-form-item>
        <el-form-item label="Access Key"><el-input v-model="cloudConfigForm.accessKey" /></el-form-item>
        <el-form-item label="Secret Key"><el-input v-model="cloudConfigForm.secretKey" type="password" show-password placeholder="留空表示保持原值" /></el-form-item>
        <el-form-item label="资源 ID"><el-input v-model="cloudConfigForm.resourceId" /></el-form-item>
        <el-form-item label="集合名称"><el-input v-model="cloudConfigForm.collectionName" /></el-form-item>
        <el-form-item label="检索模式"><el-select v-model="cloudConfigForm.providerMode" style="width:100%"><el-option label="混合检索" value="hybrid" /><el-option label="仅本地" value="mysql" /><el-option label="仅云端" value="cloud" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCloudConfigDialog = false">取消</el-button>
        <el-button type="primary" :loading="cloudConfigLoading" @click="saveCloudConfig">保存配置</el-button>
      </template>
    </el-dialog>

    <!-- 从业务数据生成文档 -->
    <el-dialog v-model="showBackfillDialog" title="从业务数据生成文档" width="400px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="数据来源">
          <el-select v-model="backfillSourceType" style="width: 100%">
            <el-option label="岗位原型" value="POST_PROTOTYPE" />
            <el-option label="能力标签" value="ABILITY_TAG" />
            <el-option label="学习资源" value="LEARNING_RESOURCE" />
            <el-option label="JD 导入" value="JD_IMPORT" />
            <el-option label="人员能力" value="EMP_ABILITY" />
            <el-option label="岗位能力模型" value="POST_ABILITY_MODEL" />
            <el-option label="来源证据" value="CONTEST_EVIDENCE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBackfillDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBackfill">生成并索引</el-button>
      </template>
    </el-dialog>

    <!-- 同步云端 -->
    <el-dialog v-model="showCloudSyncDialog" title="同步本地知识到云端" width="440px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="来源类型">
          <el-select v-model="cloudSyncForm.sourceType" clearable placeholder="全部来源" style="width: 100%">
            <el-option label="岗位原型" value="POST_PROTOTYPE" />
            <el-option label="能力标签" value="ABILITY_TAG" />
            <el-option label="学习资源" value="LEARNING_RESOURCE" />
            <el-option label="JD 导入" value="JD_IMPORT" />
            <el-option label="人员能力" value="EMP_ABILITY" />
            <el-option label="岗位能力模型" value="POST_ABILITY_MODEL" />
            <el-option label="来源证据" value="CONTEST_EVIDENCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步数量">
          <el-input-number v-model="cloudSyncForm.limit" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item>
          <template #label>
            <el-tooltip content="只预览同步结果，不写入云端" placement="top">
              <span>试运行 <el-icon><QuestionFilled /></el-icon></span>
            </el-tooltip>
          </template>
          <el-switch v-model="cloudSyncForm.dryRun" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCloudSyncDialog = false">取消</el-button>
        <el-button type="primary" :loading="cloudSyncLoading" @click="handleCloudSync">开始同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled, Collection, Right, Reading, Files, Share, Document, CircleCheck, Clock, MagicStick, Timer, Search, RefreshRight, FolderOpened, Grid } from '@element-plus/icons-vue'
import {
  pageKnowledgeDocuments,
  createKnowledgeDocument,
  indexKnowledgeDocument,
  batchIndexKnowledgeDocuments,
  backfillKnowledgeDocuments,
  searchKnowledgeChunks,
  syncCloudKnowledge,
  getCloudKnowledgeStatus,
  searchCloudKnowledge,
  updateCloudKnowledgeConfig,
} from '@/api/rag'
import type { RagKnowledgeDocument, KnowledgeChunkResult } from '@/api/rag'

// ---- State ----
const router = useRouter()
const AI_SCENARIO_COUNT = 8
const loading = ref(false)
const chunkLoading = ref(false)
const batchIndexLoading = ref(false)
const cloudSyncLoading = ref(false)
const documents = ref<RagKnowledgeDocument[]>([])
const chunkResults = ref<KnowledgeChunkResult[]>([])
const chunkSearched = ref(false)
const showCreateDialog = ref(false)
const showBackfillDialog = ref(false)
const showCloudSyncDialog = ref(false)
const showCloudConfigDialog = ref(false)
const backfillSourceType = ref('POST_PROTOTYPE')

const stats = reactive({
  totalDocs: 0,
  indexedDocs: 0,
  unindexedDocs: 0,
  totalChunks: 0,
  lastIndexed: '',
})

const searchForm = reactive({ sourceType: '', title: '' })
const statusFilter = ref('all')
const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '已就绪', value: 'indexed' },
  { label: '待就绪', value: 'unindexed' },
]

const pagination = reactive({ current: 1, size: 20, total: 0 })
const createForm = reactive({ sourceType: 'MANUAL_TEXT', title: '', content: '' })
const cloudSyncForm = reactive({ sourceType: '', limit: 100, dryRun: true })
const chunkSearch = reactive({ queryText: '', scenario: '' })

const cloudStatus = reactive({
  enabled: false,
  usable: false,
  providerMode: 'mysql',
  resourceId: '',
  collectionName: '',
  endpoint: '',
  hasCredentials: false,
  hasCollectionTarget: false,
  scenarios: [] as { key: string; name: string; allowCloud: boolean }[],
})
const cloudSearch = reactive({ queryText: '', scenario: '' })
const cloudSearchLoading = ref(false)
const cloudSearchResult = ref<any>(null)
const cloudConfigLoading = ref(false)
const cloudConfigForm = reactive({ enabled: false, endpoint: '', region: '', accessKey: '', secretKey: '', resourceId: '', collectionName: '', providerMode: 'hybrid' })

const readyRatio = computed(() => {
  if (!stats.totalDocs) return 0
  return Math.round((stats.indexedDocs / stats.totalDocs) * 100)
})

const pendingRatio = computed(() => {
  if (!stats.totalDocs) return 0
  return Math.round((stats.unindexedDocs / stats.totalDocs) * 100)
})

// ---- Helpers ----
const displayText = (value: unknown, fallback: string) => {
  if (value === null || value === undefined || String(value).trim() === '') return fallback
  return String(value)
}

const formatTime = (value: string | null | undefined) => value ? value.slice(0, 16) : '暂无'

const sourceTypeLabel = (type: string | null | undefined) => {
  const map: Record<string, string> = {
    JD_IMPORT: 'JD 导入',
    POST_PROTOTYPE: '岗位原型',
    ABILITY_TAG: '能力标签',
    LEARNING_RESOURCE: '学习资源',
    EMP_ABILITY: '人员能力',
    POST_ABILITY_MODEL: '岗位能力模型',
    CONTEST_EVIDENCE: '来源证据',
    MANUAL_TEXT: '手动补充',
    CLOUD_KNOWLEDGE_INTERNAL: '企业云知识库',
    INDUSTRY_WHITEPAPER: '行业白皮书',
    INTERNAL_POST_INFO: '内部岗位资料',
    INTERNAL_BUSINESS_UPDATE: '内部业务资料',
    INTERNAL_POLICY: '内部制度资料',
  }
  return map[type || ''] || '未知来源'
}

const sourceTypeColor = (type: string | null | undefined) => {
  const map: Record<string, string> = {
    JD_IMPORT: '#64748b',
    POST_PROTOTYPE: '#2563eb',
    ABILITY_TAG: '#059669',
    LEARNING_RESOURCE: '#d97706',
    EMP_ABILITY: '#dc2626',
    POST_ABILITY_MODEL: '#0e7490',
    CONTEST_EVIDENCE: '#7c3aed',
    MANUAL_TEXT: '#0891b2',
  }
  return map[type || ''] || '#64748b'
}

const scorePct = (score: number) => ((score || 0) * 100).toFixed(1)

// ---- Actions ----
const goGraph = () => {
  router.push('/kg/workbench')
}

const filterByStatus = (status: string) => {
  statusFilter.value = status
  pagination.current = 1
  loadDocuments()
}

const resetFilters = () => {
  searchForm.sourceType = ''
  searchForm.title = ''
  statusFilter.value = 'all'
  pagination.current = 1
  loadDocuments()
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await pageKnowledgeDocuments({
      current: pagination.current,
      size: pagination.size,
      sourceType: searchForm.sourceType || undefined,
      docStatus: statusFilter.value === 'all'
        ? undefined
        : statusFilter.value === 'indexed' ? 'ACTIVE' : 'INACTIVE',
      title: searchForm.title || undefined,
    })
    documents.value = res.data.records
    pagination.total = res.data.total

    const all = res.data.records
    stats.totalDocs = res.data.total
    stats.indexedDocs = all.filter((d: RagKnowledgeDocument) => d.docStatus === 'ACTIVE').length
    stats.unindexedDocs = all.filter((d: RagKnowledgeDocument) => d.docStatus !== 'ACTIVE').length
    stats.totalChunks = all.reduce((sum: number, d: RagKnowledgeDocument) => sum + (d.chunkCount || 0), 0)
    const indexed = all.filter((d: RagKnowledgeDocument) => d.lastIndexedTime).map((d: RagKnowledgeDocument) => d.lastIndexedTime)
    stats.lastIndexed = indexed.length > 0 ? indexed.sort().reverse()[0]?.slice(0, 16) || '' : ''
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!createForm.title || !createForm.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  const res = await createKnowledgeDocument(createForm)
  const indexRes = await indexKnowledgeDocument(res.data.id)
  ElMessage.success(`创建并索引完成，共 ${indexRes.data.chunkCount} 个分块`)
  showCreateDialog.value = false
  createForm.title = ''
  createForm.content = ''
  loadDocuments()
}

const handleIndex = async (doc: RagKnowledgeDocument) => {
  const res = await indexKnowledgeDocument(doc.id)
  ElMessage.success(`索引完成，共 ${res.data.chunkCount} 个分块`)
  loadDocuments()
}

const handleBackfill = async () => {
  const res = await backfillKnowledgeDocuments(backfillSourceType.value)
  const indexRes = await batchIndexKnowledgeDocuments({
    sourceType: backfillSourceType.value,
    onlyUnindexed: true,
    limit: Math.max(res.data.created, 100),
  })
  ElMessage.success(`生成 ${res.data.created} 个文档，索引 ${indexRes.data.documentCount} 个文档 / ${indexRes.data.chunkCount} 个分块`)
  showBackfillDialog.value = false
  loadDocuments()
}

const handleBatchIndex = async () => {
  batchIndexLoading.value = true
  try {
    const res = await batchIndexKnowledgeDocuments({
      sourceType: searchForm.sourceType || undefined,
      onlyUnindexed: true,
      limit: 500,
    })
    ElMessage.success(`索引完成：${res.data.documentCount} 个文档 / ${res.data.chunkCount} 个分块`)
    loadDocuments()
  } finally {
    batchIndexLoading.value = false
  }
}

const handleCloudSync = async () => {
  cloudSyncLoading.value = true
  try {
    const res = await syncCloudKnowledge({
      sourceType: cloudSyncForm.sourceType || undefined,
      limit: cloudSyncForm.limit,
      dryRun: cloudSyncForm.dryRun,
    })
    ElMessage.success(`同步完成：扫描 ${res.data.scanned}，写入 ${res.data.created}，跳过 ${res.data.skipped}，失败 ${res.data.failed}`)
    showCloudSyncDialog.value = false
  } finally {
    cloudSyncLoading.value = false
  }
}

const handleChunkSearch = async () => {
  if (!chunkSearch.queryText) return
  chunkLoading.value = true
  chunkSearched.value = true
  try {
    const res = await searchKnowledgeChunks({
      queryText: chunkSearch.queryText,
      scenario: chunkSearch.scenario || undefined,
      topK: 10,
    })
    chunkResults.value = res.data
  } finally {
    chunkLoading.value = false
  }
}

const loadCloudStatus = async () => {
  try {
    const res = await getCloudKnowledgeStatus()
    Object.assign(cloudStatus, res.data)
  } catch {
    // ignore
  }
}

const openCloudConfig = () => {
  Object.assign(cloudConfigForm, {
    enabled: cloudStatus.enabled,
    endpoint: cloudStatus.endpoint,
    region: '',
    accessKey: '',
    secretKey: '',
    resourceId: cloudStatus.resourceId || '',
    collectionName: cloudStatus.collectionName || '',
    providerMode: cloudStatus.providerMode || 'hybrid',
  })
  showCloudConfigDialog.value = true
}

const saveCloudConfig = async () => {
  cloudConfigLoading.value = true
  try {
    const res = await updateCloudKnowledgeConfig(cloudConfigForm)
    Object.assign(cloudStatus, res.data)
    showCloudConfigDialog.value = false
    ElMessage.success('云端知识库配置已更新')
  } finally {
    cloudConfigLoading.value = false
  }
}

const handleCloudSearch = async () => {
  if (!cloudSearch.queryText) return
  cloudSearchLoading.value = true
  cloudSearchResult.value = null
  try {
    const res = await searchCloudKnowledge({
      queryText: cloudSearch.queryText,
      scenario: cloudSearch.scenario || undefined,
    })
    cloudSearchResult.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '云端检索失败')
  } finally {
    cloudSearchLoading.value = false
  }
}

onMounted(() => {
  loadDocuments()
  loadCloudStatus()
})
</script>


<style scoped>
/* ====== AI 知识资产 — 精致浅色工作台 ====== */
.rag-workbench {
  padding: 18px 20px 20px;
  background:
    radial-gradient(900px 340px at 16% -8%, rgba(37, 99, 235, 0.07), transparent 62%),
    radial-gradient(760px 320px at 96% -6%, rgba(5, 150, 105, 0.05), transparent 55%),
    #f3f6fc;
  min-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-sizing: border-box;
}

/* ---- Header ---- */
.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.title-badge {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
}
.title-group { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  letter-spacing: -0.01em;
  line-height: 1.25;
}
.page-subtitle {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---- 业务定位引导卡 ---- */
.guide-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  background: linear-gradient(120deg, rgba(37, 99, 235, 0.07), rgba(96, 165, 250, 0.04) 55%, rgba(255, 255, 255, 0));
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
}
.guide-card__icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.28);
}
.guide-card__main { flex: 1; min-width: 0; }
.guide-card__title { font-size: 14px; font-weight: 700; color: #0f172a; }
.guide-card__desc { margin-top: 3px; font-size: 12px; line-height: 1.6; color: #64748b; }
.guide-card__meta { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }
.guide-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 100px;
  font-size: 12px;
  line-height: 1.4;
}
.guide-pill--blue { color: #1d4ed8; background: rgba(37, 99, 235, 0.1); border: 1px solid rgba(37, 99, 235, 0.14); }
.guide-pill--muted { color: #64748b; background: rgba(148, 163, 184, 0.1); border: 1px solid rgba(148, 163, 184, 0.14); }

/* ---- 资料流转图 ---- */
.knowledge-flow {
  padding: 16px 18px 18px;
  border: 1px solid #dbe5f1;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}
.knowledge-flow__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.knowledge-flow__eyebrow {
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.knowledge-flow__title {
  margin: 3px 0 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
}
.knowledge-flow__note {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}
.knowledge-flow__steps {
  display: flex;
  align-items: center;
  gap: 10px;
}
.flow-step {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  min-height: 66px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  box-sizing: border-box;
}
.flow-step > div { min-width: 0; }
.flow-step strong,
.flow-step small { display: block; }
.flow-step strong { color: #1e293b; font-size: 13px; line-height: 1.4; }
.flow-step small { margin-top: 3px; color: #64748b; font-size: 11px; line-height: 1.4; white-space: normal; }
.flow-step__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 8px;
}
.flow-step--blue { background: #eff6ff; border-color: #bfdbfe; }
.flow-step--blue .flow-step__icon { color: #2563eb; background: #dbeafe; }
.flow-step--cyan { background: #ecfeff; border-color: #a5f3fc; }
.flow-step--cyan .flow-step__icon { color: #0891b2; background: #cffafe; }
.flow-step--green { background: #ecfdf5; border-color: #a7f3d0; }
.flow-step--green .flow-step__icon { color: #059669; background: #d1fae5; }
.flow-step--purple { background: #f5f3ff; border-color: #ddd6fe; }
.flow-step--purple .flow-step__icon { color: #7c3aed; background: #ede9fe; }
.flow-arrow { flex: 0 0 auto; color: #94a3b8; }

/* ---- 指标卡 ---- */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px 16px;
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 6px 18px rgba(15, 23, 42, 0.05);
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.stat-clickable { cursor: pointer; }
.stat-clickable:hover {
  transform: translateY(-2px);
  border-color: rgba(37, 99, 235, 0.3);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.1), 0 10px 26px rgba(15, 23, 42, 0.07);
}
.stat-card__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon--primary { background: rgba(37, 99, 235, 0.1); color: #2563eb; }
.stat-icon--success { background: rgba(5, 150, 105, 0.1); color: #059669; }
.stat-icon--warning { background: rgba(217, 119, 6, 0.1); color: #d97706; }
.stat-icon--cyan { background: rgba(8, 145, 178, 0.1); color: #0891b2; }
.stat-icon--purple { background: rgba(124, 58, 237, 0.1); color: #7c3aed; }
.stat-icon--slate { background: rgba(100, 116, 139, 0.1); color: #64748b; }
.stat-card__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.stat-card__value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  letter-spacing: -0.02em;
}
.stat-card__value--sm { font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.stat-card__value.is-warn { color: #d97706; }
.stat-card__label { font-size: 12px; color: #94a3b8; }
.stat-card__bar {
  position: absolute;
  left: 0;
  bottom: 0;
  right: 0;
  height: 3px;
  background: rgba(148, 163, 184, 0.12);
}
.bar-fill { display: block; height: 100%; border-radius: 0 2px 2px 0; transition: width 0.4s ease; }
.bar-fill--primary { background: linear-gradient(90deg, #60a5fa, #2563eb); }
.bar-fill--success { background: linear-gradient(90deg, #34d399, #059669); }
.bar-fill--warning { background: linear-gradient(90deg, #fcd34d, #d97706); }

/* ---- 三栏工作区 ---- */
.workbench-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.sidebar-left,
.main-content,
.right-section,
.ops-collapse {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 6px 18px rgba(15, 23, 42, 0.04);
}

/* Left sidebar */
.sidebar-left {
  width: 232px;
  flex-shrink: 0;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.filter-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
}
.sidebar-actions { margin-top: auto; }
.sidebar-actions .el-button { font-weight: 600; }

/* Main content */
.main-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}
.table-head__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.table-head__icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.table-head__count {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 1px 8px;
  border-radius: 100px;
}
.table-head__hint { font-size: 11px; color: #b6c2d2; white-space: nowrap; }

.doc-table { flex: 1; }
.doc-table :deep(.el-table__inner-wrapper::before) { display: none; }
.doc-table :deep(.el-table__header th.el-table__cell) {
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
  font-size: 12px;
  border-bottom: 1px solid #eef2f7;
}
.doc-table :deep(.el-table__row) { transition: background 0.15s; }
.doc-table :deep(.el-table__row:hover > td.el-table__cell) { background: #f8fbff; }
.doc-table :deep(.el-table__row--striped.el-table__row--striped td.el-table__cell) { background: #fafcff; }
.doc-table :deep(.el-table__row--striped.el-table__row--striped:hover > td.el-table__cell) { background: #f3f8ff; }

.doc-name { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; color: #1e293b; }
.doc-name__icon { color: #2563eb; opacity: 0.65; }

.src-tag {
  border: none;
  background: #f1f5f9;
  color: #475569;
  font-weight: 500;
}
.src-tag__dot { width: 6px; height: 6px; border-radius: 50%; margin-right: 2px; flex-shrink: 0; }
.src-tag--sm { font-size: 11px; padding: 2px 8px; }

.doc-status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 500; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.doc-status.is-ready { color: #059669; }
.doc-status.is-ready .status-dot { background: #059669; box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.12); }
.doc-status.is-pending { color: #d97706; }
.doc-status.is-pending .status-dot { background: #d97706; box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.12); }

.chunk-badge {
  display: inline-block;
  min-width: 26px;
  padding: 1px 8px;
  border-radius: 100px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}
.time-cell { display: inline-flex; align-items: center; gap: 4px; color: #64748b; font-size: 12px; }
.index-btn { font-weight: 600; }
.table-pager {
  margin-top: 10px;
  padding: 0 4px 2px;
  justify-content: flex-end;
}

/* Right sidebar */
.sidebar-right {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.right-section { padding: 16px; }
.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
}
.section-title__icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.section-title__tag {
  font-size: 10px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  padding: 1px 8px;
  border-radius: 100px;
  font-weight: 600;
}
.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-header-row .section-title { margin: 0; }

.query-input { margin-top: 8px; }
.search-btn { width: 100%; margin-top: 8px; font-weight: 600; }

/* 检索结果 */
.search-results { margin-top: 12px; max-height: 430px; overflow-y: auto; }
.search-results::-webkit-scrollbar { width: 6px; }
.search-results::-webkit-scrollbar-thumb { background: rgba(148, 163, 184, 0.3); border-radius: 6px; }
.result-card { padding: 12px 0; border-bottom: 1px solid #eef2f7; }
.result-card:last-child { border-bottom: none; }
.result-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.result-title__icon { color: #2563eb; flex-shrink: 0; }
.result-meta { display: flex; align-items: center; gap: 8px; margin: 6px 0; }
.result-score {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0 8px;
  font-size: 11px;
  color: #94a3b8;
}
.score-bar {
  flex: 1;
  height: 4px;
  border-radius: 100px;
  background: #eef2f7;
  overflow: hidden;
}
.score-bar i {
  display: block;
  height: 100%;
  border-radius: 100px;
  background: linear-gradient(90deg, #60a5fa, #2563eb);
  transition: width 0.3s ease;
}
.result-score b { color: #2563eb; font-weight: 600; font-size: 12px; min-width: 40px; text-align: right; }
.result-text {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.search-idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px 14px;
  color: #b6c2d2;
  font-size: 12px;
  text-align: center;
  line-height: 1.6;
}

/* Cloud section */
.cloud-section { flex-shrink: 0; }
.cloud-info { display: flex; flex-direction: column; gap: 4px; }
.cloud-info-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #475569;
}
.cloud-label { color: #94a3b8; }
.cloud-result-brief {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: #475569;
}

.text-muted { color: #94a3b8; font-size: 12px; }

/* ---- 高级运维折叠区 ---- */
.ops-collapse { padding: 0 16px; }
.ops-collapse :deep(.el-collapse) { border: none; }
.ops-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  height: 44px;
}
.ops-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.ops-cloud {
  margin-top: 4px;
  padding: 12px;
  border: 1px dashed #dbe2ec;
  border-radius: 10px;
  max-width: 480px;
}
.ops-cloud .section-title { margin-bottom: 8px; }

/* ---- Responsive ---- */
@media (max-width: 1280px) {
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 1200px) {
  .workbench-body { flex-direction: column; }
  .knowledge-flow__steps { flex-wrap: wrap; }
  .flow-step { flex: 1 1 calc(50% - 30px); }
  .flow-arrow { transform: rotate(90deg); }
  .sidebar-left,
  .sidebar-right { width: 100%; }
  .sidebar-left {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 12px;
  }
  .sidebar-left > .sidebar-section { flex: 1; min-width: 160px; }
  .sidebar-actions {
    margin-top: 0;
    display: flex;
    gap: 8px;
    width: 100%;
  }
  .sidebar-actions .el-button { flex: 1; }
  .main-content { min-height: 400px; }
  .search-results { max-height: 260px; }
  .guide-card__meta { align-items: flex-start; }
}
@media (max-width: 720px) {
  .knowledge-flow { padding: 14px; }
  .knowledge-flow__heading { align-items: flex-start; flex-direction: column; }
  .knowledge-flow__steps { display: grid; grid-template-columns: 1fr; gap: 8px; }
  .flow-step { min-height: 58px; }
  .flow-arrow { justify-self: center; transform: rotate(90deg); }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
