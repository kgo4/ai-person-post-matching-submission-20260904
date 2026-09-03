<script setup lang="ts">
import { getScoreColor } from '../composables/useMatchingResult'
import type { DimensionScore, GapAbility, ImprovementPhase } from '../gap-diagnosis'
import type { GraphData, KnowledgeChunkResult, LearningPathItem, MatchingRecord } from '@/api'

defineProps<{
  visible: boolean
  loading: boolean
  record: MatchingRecord | null
  gaps: GapAbility[]
  learningPath: LearningPathItem[]
  graphData: GraphData | null
  evidenceResults: KnowledgeChunkResult[]
  warnings: string[]
  dimensionScores: DimensionScore[]
  improvementPlan: ImprovementPhase[]
  exportLoading: boolean
  graphSummary: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'print-report': []
  'export-gap': []
  'go-resume': []
}>()
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="人岗差距诊断"
    size="75%"
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="gap-drawer">
      <div v-if="record" class="gap-record-card">
        <div>
          <div class="gap-record-card__label">诊断对象</div>
          <div class="gap-record-card__title">
            {{ record.empName || '员工#' + record.empId }}
            <span>→</span>
            {{ record.postName || '岗位#' + record.postId }}
          </div>
        </div>
        <div class="gap-record-card__score" :style="{ color: getScoreColor(record.finalMatchScore ?? record.aiMatchScore) }">
          {{ record.finalMatchScore ?? record.aiMatchScore }}
        </div>
      </div>

      <div class="gap-actions-bar">
        <el-button size="small" @click="emit('go-resume')" type="primary" plain>
          导入简历解析
        </el-button>
        <el-button size="small" @click="emit('export-gap')" :loading="exportLoading">
          导出报告 (JSON)
        </el-button>
        <el-button size="small" @click="emit('print-report')" plain>
          打印报告
        </el-button>
      </div>

      <el-alert
        v-for="warning in warnings"
        :key="warning"
        :title="warning"
        type="warning"
        show-icon
        :closable="false"
        class="gap-alert"
      />

      <section v-if="dimensionScores.length" class="gap-section">
        <div class="gap-section__head">
          <div>
            <div class="gap-section__title">多维度匹配分析</div>
            <div class="gap-section__desc">从五个维度评估候选人与岗位的匹配程度。</div>
          </div>
        </div>
        <div class="dimension-grid">
          <div v-for="dim in dimensionScores" :key="dim.dimension" class="dimension-item">
            <div class="dimension-item__header">
              <span class="dimension-item__label">{{ dim.label }}</span>
              <span class="dimension-item__score" :style="{ color: getScoreColor(dim.score) }">
                {{ dim.score }}/{{ dim.maxScore }}
              </span>
            </div>
            <el-progress
              :percentage="Math.round((dim.score / dim.maxScore) * 100)"
              :color="getScoreColor(dim.score)"
              :stroke-width="6"
              :show-text="false"
            />
            <div v-if="dim.details?.length" class="dimension-item__details">
              <el-tag v-for="d in dim.details" :key="d" size="small" type="info" style="margin:2px">{{ d }}</el-tag>
            </div>
          </div>
        </div>
      </section>

      <section class="gap-section">
        <div class="gap-section__head">
          <div>
            <div class="gap-section__title">能力缺口</div>
            <div class="gap-section__desc">低于岗位要求或仅有弱证据支撑的能力项会进入后续诊断。</div>
          </div>
          <el-tag type="primary">{{ gaps.length }} 项</el-tag>
        </div>
        <div v-if="gaps.length" class="gap-ability-list">
          <div v-for="item in gaps" :key="item.name" class="gap-ability-item">
            <div class="gap-ability-item__name">{{ item.name }}</div>
            <div class="gap-ability-item__meta">
              当前 {{ item.actualLevel ?? '-' }} / 要求 {{ item.requiredLevel ?? '-' }}
              <span v-if="item.requiredLevel != null && item.actualLevel != null" style="margin-left:4px">
                (差距 {{ (item.requiredLevel - item.actualLevel) }} 级)
              </span>
              <el-tag v-if="item.weakEvidence" size="small" type="warning">弱证据</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无能力缺口" />
      </section>

      <section v-if="improvementPlan.length" class="gap-section">
        <div class="gap-section__head">
          <div>
            <div class="gap-section__title">改进计划</div>
            <div class="gap-section__desc">按能力差距大小分阶段规划学习路径，含预计周期和推荐资源。</div>
          </div>
          <el-tag type="success">{{ improvementPlan.length }} 阶段</el-tag>
        </div>
        <div class="improvement-timeline">
          <div v-for="phase in improvementPlan" :key="phase.phase" class="improvement-phase">
            <div class="improvement-phase__marker">
              <span class="improvement-phase__num">{{ phase.phase }}</span>
            </div>
            <div class="improvement-phase__body">
              <div class="improvement-phase__title">
                {{ phase.title }}
                <el-tag size="small" type="warning">{{ phase.timeframe }}</el-tag>
              </div>
              <p class="improvement-phase__desc">{{ phase.description }}</p>
              <div class="improvement-phase__abilities">
                <span class="improvement-phase__abilities-label">目标能力：</span>
                <el-tag v-for="ab in phase.targetAbilities" :key="ab" size="small" type="primary" style="margin:2px">{{ ab }}</el-tag>
              </div>
              <div v-if="phase.resources.length" class="improvement-phase__resources">
                <span class="improvement-phase__resources-label">推荐资源：</span>
                <div v-for="res in phase.resources" :key="res.title" class="improvement-resource">
                  <span>{{ res.title }}</span>
                  <el-tag size="small" type="info">{{ res.type }}</el-tag>
                  <a v-if="res.url" :href="res.url" target="_blank" rel="noreferrer" class="gap-link">查看</a>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="gap-grid">
        <section class="gap-section">
          <div class="gap-section__head">
            <div>
              <div class="gap-section__title">学习资源</div>
              <div class="gap-section__desc">基于缺口能力自动匹配资源。</div>
            </div>
            <el-tag>{{ learningPath.length }} 条</el-tag>
          </div>
          <div v-if="learningPath.length" class="gap-card-list">
            <article v-for="item in learningPath" :key="`${item.abilityName}-${item.title}`" class="gap-mini-card">
              <div class="gap-mini-card__title">{{ item.title }}</div>
              <div class="gap-mini-card__meta">
                {{ item.abilityName }}
                <span v-if="item.difficultyLevel">L{{ item.difficultyLevel }}</span>
                <span v-if="item.resourceType">{{ item.resourceType }}</span>
              </div>
              <p v-if="item.description" class="gap-mini-card__desc">{{ item.description }}</p>
              <a v-if="item.url" :href="item.url" target="_blank" rel="noreferrer" class="gap-link">打开资源</a>
            </article>
          </div>
          <el-empty v-else-if="!loading" description="暂无学习路径" />
        </section>

        <section class="gap-section">
          <div class="gap-section__head">
            <div>
              <div class="gap-section__title">RAG 证据</div>
              <div class="gap-section__desc">来自本地或云知识库的补充依据。</div>
            </div>
            <el-tag>{{ evidenceResults.length }} 条</el-tag>
          </div>
          <div v-if="evidenceResults.length" class="gap-card-list">
            <article v-for="item in evidenceResults" :key="item.chunkId" class="gap-mini-card">
              <div class="gap-mini-card__title">{{ item.documentTitle }}</div>
              <div class="gap-mini-card__meta">
                {{ item.sourceType }}
                <span>score {{ Number(item.score || 0).toFixed(2) }}</span>
              </div>
              <p class="gap-mini-card__desc">{{ item.chunkText }}</p>
            </article>
          </div>
          <el-empty v-else-if="!loading" description="暂无检索证据" />
        </section>
      </div>

      <section class="gap-section">
        <div class="gap-section__head">
          <div>
            <div class="gap-section__title">图谱路径</div>
            <div class="gap-section__desc">展示员工、岗位、能力与证据之间的关联路径。</div>
          </div>
          <el-tag type="success">{{ graphSummary }}</el-tag>
        </div>
        <div v-if="graphData?.nodes?.length" class="gap-graph-preview">
          <div>
            <div class="gap-subtitle">节点</div>
            <div v-for="node in graphData.nodes.slice(0, 8)" :key="node.id" class="gap-path-line">
              <span>{{ node.label }}</span>
              <el-tag size="small">{{ node.type }}</el-tag>
            </div>
          </div>
          <div>
            <div class="gap-subtitle">关系</div>
            <div v-for="edge in graphData.edges.slice(0, 8)" :key="edge.id" class="gap-path-line">
              <span>{{ edge.source }} → {{ edge.target }}</span>
              <el-tag size="small" type="info">{{ edge.type }}</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无图谱路径" />
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.gap-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 4px;
}

.gap-record-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.78);
}

.gap-record-card__label {
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.gap-record-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 800;
}

.gap-record-card__score {
  min-width: 72px;
  text-align: right;
  font-size: 28px;
  font-weight: 900;
}

.gap-actions-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.dimension-item {
  padding: 12px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.1);
}

.dimension-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.dimension-item__label {
  font-size: 12px;
  font-weight: 700;
  color: var(--app-text-secondary);
}

.dimension-item__score {
  font-size: 14px;
  font-weight: 800;
}

.dimension-item__details {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.improvement-timeline {
  display: flex;
  flex-direction: column;
}

.improvement-phase {
  display: flex;
  gap: 16px;
  padding: 14px 0;
}

.improvement-phase + .improvement-phase {
  border-top: 1px solid var(--app-divider);
}

.improvement-phase__marker {
  display: flex;
  align-items: flex-start;
  flex-shrink: 0;
  padding-top: 2px;
}

.improvement-phase__num {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--app-primary), var(--app-accent));
  color: #fff;
  font-size: 14px;
  font-weight: 800;
}

.improvement-phase__body {
  flex: 1;
  min-width: 0;
}

.improvement-phase__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text-strong);
  margin-bottom: 4px;
}

.improvement-phase__desc {
  font-size: 13px;
  color: var(--app-text-secondary);
  margin: 6px 0;
  line-height: 1.5;
}

.improvement-phase__abilities {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.improvement-phase__abilities-label,
.improvement-phase__resources-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--app-text-muted);
}

.improvement-phase__resources {
  margin-top: 10px;
}

.improvement-resource {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  color: var(--app-text);
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);
}

.improvement-resource:last-child {
  border-bottom: none;
}

@media (max-width: 1200px) {
  .dimension-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .dimension-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.gap-alert {
  margin: 0;
}

.gap-section {
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: #fff;
}

.gap-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.gap-section__title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 850;
}

.gap-section__desc {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.gap-ability-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.gap-ability-item {
  padding: 12px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.04);
}

.gap-ability-item__name {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 800;
}

.gap-ability-item__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.gap-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.gap-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.gap-mini-card {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.72);
}

.gap-mini-card__title {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 800;
}

.gap-mini-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.gap-mini-card__desc {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.gap-link {
  display: inline-flex;
  margin-top: 10px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.gap-graph-preview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.gap-subtitle {
  margin-bottom: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.gap-path-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  color: var(--app-text);
  font-size: 12px;
}

@media (max-width: 1024px) {
  .gap-grid,
  .gap-graph-preview,
  .gap-ability-list {
    grid-template-columns: 1fr;
  }
}
</style>
