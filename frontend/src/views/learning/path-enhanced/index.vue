<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, MagicStick, TrendCharts, Check, VideoPlay, Lock } from '@element-plus/icons-vue'
import {
  getAllDomains,
  getNodesByDomainId,
  getDomainMasteryScores,
  getNodeMasteryScores,
  getWeakPoints,
  getLearningPathRecommendations,
  getLearningPathByMastery,
  generateLearningPathByKnowledgeGraph
} from '@/api/learning-path-enhanced'
import type {
  KnowledgeDomain,
  KnowledgeNode,
  LearningPathItemDTO,
  WeakPoint,
  LearningPathRecommendation
} from '@/api/learning-path-enhanced'

const loading = ref(false)
const domains = ref<KnowledgeDomain[]>([])
const selectedDomain = ref<KnowledgeDomain | null>(null)
const domainNodes = ref<KnowledgeNode[]>([])
const domainMasteryScores = ref<Record<number, number>>({})
const nodeMasteryScores = ref<Record<number, number>>({})
const weakPoints = ref<WeakPoint[]>([])
const recommendations = ref<LearningPathRecommendation[]>([])
const learningPath = ref<LearningPathItemDTO[]>([])
const activeStep = ref<number | null>(null)

const empId = ref(1)
const postId = ref(1)

const overallMastery = computed(() => {
  const scores = Object.values(domainMasteryScores.value)
  if (scores.length === 0) return 0
  return Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length)
})

const masteredDomains = computed(() => {
  return Object.values(domainMasteryScores.value).filter(score => score >= 80).length
})

const totalDomains = computed(() => {
  return Object.keys(domainMasteryScores.value).length
})

const masteryRate = computed(() => {
  if (totalDomains.value === 0) return 0
  return Math.round((masteredDomains.value / totalDomains.value) * 100)
})

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadDomains(), loadDomainMasteryScores(), loadWeakPoints(), loadRecommendations()])
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

async function loadDomains() {
  try {
    const res = await getAllDomains()
    domains.value = res.data || []
  } catch (error: any) {
    console.error('加载知识领域失败:', error)
  }
}

async function loadDomainMasteryScores() {
  try {
    const res = await getDomainMasteryScores(empId.value)
    domainMasteryScores.value = res.data || {}
  } catch (error: any) {
    console.error('加载领域掌握度失败:', error)
  }
}

async function loadWeakPoints() {
  try {
    const res = await getWeakPoints(empId.value, 10)
    weakPoints.value = res.data || []
  } catch (error: any) {
    console.error('加载薄弱环节失败:', error)
  }
}

async function loadRecommendations() {
  try {
    const res = await getLearningPathRecommendations(empId.value, postId.value)
    recommendations.value = res.data || []
  } catch (error: any) {
    console.error('加载学习路径推荐失败:', error)
  }
}

async function handleDomainSelect(domain: KnowledgeDomain) {
  selectedDomain.value = domain
  loading.value = true
  try {
    await Promise.all([loadDomainNodes(domain.id), loadNodeMasteryScores(domain.id)])
  } catch (error: any) {
    ElMessage.error(error.message || '加载领域详情失败')
  } finally {
    loading.value = false
  }
}

async function loadDomainNodes(domainId: number) {
  try {
    const res = await getNodesByDomainId(domainId)
    domainNodes.value = res.data || []
  } catch (error: any) {
    console.error('加载知识点失败:', error)
  }
}

async function loadNodeMasteryScores(domainId: number) {
  try {
    const res = await getNodeMasteryScores(empId.value, domainId)
    nodeMasteryScores.value = res.data || {}
  } catch (error: any) {
    console.error('加载知识点掌握度失败:', error)
  }
}

async function handleGenerateLearningPath() {
  loading.value = true
  try {
    const res = await generateLearningPathByKnowledgeGraph({
      abilityNames: recommendations.value.map(r => r.abilityName),
      currentLevel: 1,
      targetLevel: 3
    })
    learningPath.value = res.data || []
    ElMessage.success('学习路径生成成功')
  } catch (error: any) {
    ElMessage.error(error.message || '生成学习路径失败')
  } finally {
    loading.value = false
  }
}

async function handleGenerateByMastery() {
  loading.value = true
  try {
    const res = await getLearningPathByMastery(empId.value, postId.value)
    learningPath.value = res.data || []
    ElMessage.success('基于掌握度的学习路径生成成功')
  } catch (error: any) {
    ElMessage.error(error.message || '生成学习路径失败')
  } finally {
    loading.value = false
  }
}

function getMasteryColor(score: number): string {
  if (score >= 80) return '#059669'
  if (score >= 60) return '#2563eb'
  if (score >= 40) return '#d97706'
  return '#dc2626'
}

function getMasteryText(score: number): string {
  if (score >= 80) return '优秀'
  if (score >= 60) return '良好'
  if (score >= 40) return '一般'
  return '薄弱'
}

function getPriorityText(priority: number): string {
  if (priority >= 50) return '高'
  if (priority >= 30) return '中'
  return '低'
}

function getPriorityColor(priority: number): string {
  if (priority >= 50) return '#dc2626'
  if (priority >= 30) return '#d97706'
  return '#6b7280'
}
</script>

<template>
  <div class="kg-path" v-loading="loading">
    <!-- Compact header -->
    <header class="kg-path__header">
      <div class="kg-path__header-left">
        <h1 class="kg-path__title">知识图谱学习路径</h1>
        <span class="kg-path__subtitle">基于掌握度评估生成个性化学习路径</span>
        <span class="kg-path__mastery" :style="{ color: getMasteryColor(overallMastery) }">
          综合 {{ overallMastery }}%
        </span>
      </div>
      <div class="kg-path__header-right">
        <el-button size="small" :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button size="small" type="primary" :loading="loading" @click="handleGenerateLearningPath">
          <el-icon><Search /></el-icon> 生成路径
        </el-button>
        <el-button size="small" type="success" plain :loading="loading" @click="handleGenerateByMastery">
          <el-icon><MagicStick /></el-icon> 掌握度生成
        </el-button>
      </div>
    </header>

    <!-- Three-column layout -->
    <div class="kg-path__body">
      <!-- Left: Domain mastery -->
      <aside class="kg-path__left">
        <section class="kg-path__panel">
          <div class="kg-path__panel-title">知识领域</div>
          <div class="kg-path__domain-list">
            <div
              v-for="domain in domains"
              :key="domain.id"
              class="kg-path__domain-item"
              :class="{ 'is-active': selectedDomain?.id === domain.id }"
              @click="handleDomainSelect(domain)"
            >
              <div class="kg-path__domain-name">{{ domain.domainName }}</div>
              <div class="kg-path__domain-bar">
                <div
                  class="kg-path__domain-fill"
                  :style="{ width: (domainMasteryScores[domain.id] || 0) + '%', background: getMasteryColor(domainMasteryScores[domain.id] || 0) }"
                ></div>
              </div>
              <span class="kg-path__domain-score" :style="{ color: getMasteryColor(domainMasteryScores[domain.id] || 0) }">
                {{ domainMasteryScores[domain.id] || 0 }}%
              </span>
            </div>
          </div>
        </section>

        <!-- Node details (shown when domain selected) -->
        <section v-if="selectedDomain" class="kg-path__panel">
          <div class="kg-path__panel-title">{{ selectedDomain.domainName }} 知识点</div>
          <div class="kg-path__node-list">
            <div v-for="node in domainNodes" :key="node.id" class="kg-path__node-item">
              <span class="kg-path__node-level">L{{ node.nodeLevel }}</span>
              <span class="kg-path__node-name">{{ node.nodeName }}</span>
              <span class="kg-path__node-score" :style="{ color: getMasteryColor(nodeMasteryScores[node.id] || 0) }">
                {{ nodeMasteryScores[node.id] || 0 }}%
              </span>
            </div>
          </div>
        </section>
      </aside>

      <!-- Center: Learning path timeline -->
      <main class="kg-path__center">
        <div v-if="learningPath.length" class="kg-path__timeline">
          <div class="kg-path__timeline-line"></div>
          <div
            v-for="(item, index) in learningPath"
            :key="index"
            class="kg-path__step"
            :class="{ 'is-active': activeStep === index }"
            @click="activeStep = index"
          >
            <div class="kg-path__step-dot" :style="{ background: index <= Math.floor(learningPath.length * overallMastery / 100) ? '#059669' : '#e5e7eb' }">
              <Check v-if="index <= Math.floor(learningPath.length * overallMastery / 100)" style="width:10px;height:10px;color:#fff" />
            </div>
            <div class="kg-path__step-body">
              <div class="kg-path__step-header">
                <span class="kg-path__step-ability">{{ item.abilityName }}</span>
                <span class="kg-path__step-difficulty">难度 {{ item.difficultyLevel }}</span>
              </div>
              <div class="kg-path__step-title">{{ item.title }}</div>
              <div v-if="item.description" class="kg-path__step-desc">{{ item.description }}</div>
              <div v-if="item.url" class="kg-path__step-action">
                <el-link :href="item.url" target="_blank" type="primary">查看资源</el-link>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="kg-path__empty">
          点击顶部「生成路径」按钮生成学习路径
        </div>
      </main>

      <!-- Right: Weak points & Recommendations -->
      <aside class="kg-path__right">
        <!-- Weak points -->
        <section class="kg-path__panel">
          <div class="kg-path__panel-title">薄弱环节</div>
          <div class="kg-path__weak-list">
            <div v-for="(wp, idx) in weakPoints" :key="idx" class="kg-path__weak-item">
              <span class="kg-path__weak-rank">{{ idx + 1 }}</span>
              <span class="kg-path__weak-name">{{ wp.domainName }}</span>
              <span class="kg-path__weak-score" :style="{ color: getMasteryColor(wp.masteryScore) }">
                {{ wp.masteryScore }}%
              </span>
              <span class="kg-path__weak-tag" :style="{ color: getMasteryColor(wp.masteryScore), background: wp.masteryScore < 30 ? '#fee2e2' : wp.masteryScore < 60 ? '#fef3c7' : '#dbeafe' }">
                {{ getMasteryText(wp.masteryScore) }}
              </span>
            </div>
          </div>
        </section>

        <!-- Recommendations -->
        <section class="kg-path__panel">
          <div class="kg-path__panel-title">学习推荐</div>
          <div class="kg-path__rec-list">
            <div v-for="(rec, idx) in recommendations" :key="idx" class="kg-path__rec-item">
              <div class="kg-path__rec-header">
                <span class="kg-path__rec-name">{{ rec.abilityName }}</span>
                <span class="kg-path__rec-priority" :style="{ color: getPriorityColor(rec.priority) }">
                  {{ getPriorityText(rec.priority) }}优先级
                </span>
              </div>
              <div class="kg-path__rec-levels">
                L{{ rec.currentLevel }} → L{{ rec.requiredLevel }}
              </div>
              <div class="kg-path__rec-bar">
                <div
                  class="kg-path__rec-fill"
                  :style="{ width: rec.masteryScore + '%', background: getMasteryColor(rec.masteryScore) }"
                ></div>
              </div>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.kg-path {
  padding: 16px;
  min-height: 100%;
}

/* Header */
.kg-path__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 56px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  margin-bottom: 16px;
}

.kg-path__header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kg-path__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.kg-path__subtitle {
  font-size: 13px;
  color: var(--app-text-muted);
}

.kg-path__mastery {
  font-size: 14px;
  font-weight: 700;
  padding: 2px 10px;
  background: #f9fafb;
  border-radius: 6px;
}

.kg-path__header-right {
  display: flex;
  gap: 8px;
}

/* Three-column body */
.kg-path__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* Left */
.kg-path__left {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Center */
.kg-path__center {
  flex: 1;
  min-width: 0;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  padding: 20px 24px;
}

/* Right */
.kg-path__right {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Panel */
.kg-path__panel {
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  padding: 16px;
}

.kg-path__panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong);
  margin-bottom: 12px;
}

/* Domain list */
.kg-path__domain-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kg-path__domain-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.kg-path__domain-item:hover {
  background: #f9fafb;
}

.kg-path__domain-item.is-active {
  background: rgba(59, 130, 246, 0.06);
}

.kg-path__domain-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kg-path__domain-bar {
  width: 60px;
  height: 4px;
  background: rgba(148, 163, 184, 0.1);
  border-radius: 2px;
  overflow: hidden;
  flex-shrink: 0;
}

.kg-path__domain-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

.kg-path__domain-score {
  font-size: 12px;
  font-weight: 700;
  width: 36px;
  text-align: right;
  flex-shrink: 0;
}

/* Node list */
.kg-path__node-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 300px;
  overflow-y: auto;
}

.kg-path__node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
}

.kg-path__node-level {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: var(--app-text-secondary);
  flex-shrink: 0;
}

.kg-path__node-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--app-text-secondary);
}

.kg-path__node-score {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

/* Timeline */
.kg-path__timeline {
  position: relative;
  padding-left: 32px;
}

.kg-path__timeline-line {
  position: absolute;
  left: 10px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: rgba(148, 163, 184, 0.16);
  border-radius: 1px;
}

.kg-path__step {
  position: relative;
  margin-bottom: 16px;
  cursor: pointer;
}

.kg-path__step-dot {
  position: absolute;
  left: -32px;
  top: 14px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.kg-path__step-body {
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.5);
  transition: border-color 0.15s;
}

.kg-path__step:hover .kg-path__step-body {
  border-color: var(--app-primary);
}

.kg-path__step.is-active .kg-path__step-body {
  border-color: var(--app-primary);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.06);
}

.kg-path__step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.kg-path__step-ability {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 4px;
}

.kg-path__step-difficulty {
  font-size: 11px;
  color: var(--app-text-muted);
}

.kg-path__step-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-strong);
  margin-bottom: 4px;
}

.kg-path__step-desc {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kg-path__step-action {
  margin-top: 8px;
}

/* Weak points */
.kg-path__weak-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kg-path__weak-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
}

.kg-path__weak-rank {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: var(--app-text-secondary);
  flex-shrink: 0;
}

.kg-path__weak-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--app-text-secondary);
  font-weight: 500;
}

.kg-path__weak-score {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.kg-path__weak-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

/* Recommendations */
.kg-path__rec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kg-path__rec-item {
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 8px;
}

.kg-path__rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.kg-path__rec-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
}

.kg-path__rec-priority {
  font-size: 11px;
  font-weight: 600;
}

.kg-path__rec-levels {
  font-size: 11px;
  color: var(--app-text-muted);
  margin-bottom: 6px;
}

.kg-path__rec-bar {
  height: 4px;
  background: rgba(148, 163, 184, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.kg-path__rec-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

/* Empty */
.kg-path__empty {
  padding: 48px 24px;
  text-align: center;
  font-size: 14px;
  color: var(--app-text-muted);
}

/* Responsive */
@media (max-width: 1200px) {
  .kg-path__body {
    flex-wrap: wrap;
  }
  .kg-path__right {
    width: 100%;
  }
}

@media (max-width: 960px) {
  .kg-path__body {
    flex-direction: column;
  }
  .kg-path__left,
  .kg-path__right {
    width: 100%;
  }
}
</style>
