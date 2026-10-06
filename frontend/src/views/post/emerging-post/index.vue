<template>
  <div class="emerging-post-page">
    <el-card shadow="never" class="discovery-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>市场 JD 统计</span>
            <span class="header-subtitle">纯统计口径（能力词共现 + 来源分布），不含大模型推断；趋势岗位与能力变更建议请到「岗位趋势发现」</span>
          </div>
        </div>
      </template>

      <div class="discovery-summary">
        <!--
          口径必须写清「这是什么来源」。本页的语料是两类的混合：
          市场 JD（market_jd_data）与用户上传的知识资料（已索引文档）。
          原先只写「有效 JD」，用户会把自己上传的白皮书也算进 JD 里。
        -->
        <div class="summary-item summary-item--corpus">
          <span>市场 JD 条数</span>
          <strong>{{ marketInsight?.analyzedJdCount ?? 0 }}</strong>
          <em class="summary-item__hint">已解析入库的招聘 JD</em>
        </div>
        <div class="summary-item summary-item--corpus">
          <span>上传资料份数</span>
          <strong>{{ marketInsight?.indexedDocumentCount ?? 0 }}</strong>
          <em class="summary-item__hint">行业报告 / 政策文件，非 JD</em>
        </div>
        <div class="summary-item">
          <span>运行模式</span>
          <el-tag :type="modeTagType(discoveryMode)" effect="light">{{ modeLabel(discoveryMode) }}</el-tag>
        </div>
        <div class="summary-item">
          <span>候选方向</span>
          <strong>{{ marketInsight?.candidateCount ?? discoveryCandidates.length }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>来源平台</span>
          <strong>{{ marketInsight?.sourcePlatformCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>独立招聘主体</span>
          <strong>{{ marketInsight?.independentEmployerCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>去重</span>
          <strong>{{ marketInsight?.deduplicatedCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>噪声过滤</span>
          <strong>{{ marketInsight?.noiseFilteredCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>识别能力词</span>
          <strong>{{ marketInsight?.recognizedAbilityCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>解析词表</span>
          <strong>{{ marketInsight?.vocabularySize ?? 0 }}</strong>
        </div>
        <div class="summary-note">
          <span v-if="discoveryMode === 'OBSERVATION'">小样本仅生成观察信号，不能直接创建岗位。</span>
          <span v-else>候选结果需经过现有岗位演化或新兴岗位人工确认流程。</span>
        </div>
      </div>

      <el-alert
        v-if="discoveryLoaded && marketInsight?.diagnosticMessage"
        :title="marketInsight.diagnosticMessage"
        :type="discoveryCandidates.length ? 'success' : 'warning'"
        :closable="false"
        show-icon
        class="discovery-diagnostic"
      >
        <template #default>
          <span class="discovery-diagnostic__meta">能力词表来源：{{ vocabularySourceLabel(marketInsight.vocabularySource) }}</span>
        </template>
      </el-alert>

      <el-empty v-if="!discovering && discoveryLoaded && !discoveryCandidates.length" description="尚未形成可展示的能力社区，请参考上方解析诊断定位原因" :image-size="72" />
      <el-table v-else-if="discoveryCandidates.length" :data="discoveryCandidates" size="small" class="discovery-table">
        <!-- 名称由后端按能力社区生成；表头写清来源，避免用户以为这是抓来的岗位名 -->
        <el-table-column prop="candidateName" label="候选方向（系统按能力社区生成）" min-width="210" show-overflow-tooltip />
        <el-table-column label="核心技能" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="skill in row.coreAbilities" :key="skill" size="small" class="skill-tag">{{ skill }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="emergenceScore" label="新兴度" width="90">
          <template #default="{ row }"><strong>{{ row.emergenceScore ?? 0 }}</strong></template>
        </el-table-column>
        <el-table-column prop="trendGrowthScore" label="趋势" width="80" />
        <el-table-column prop="cohesionScore" label="凝聚度" width="90" />
        <!--
          证据按来源分列：市场 JD 与上传资料是完全不同的证据，合并成一个数字会让
          「N 条 JD」虚高，用户会以为自己的白皮书变成了招聘 JD。
        -->
        <el-table-column label="证据覆盖" min-width="230">
          <template #default="{ row }">
            <span class="evidence-coverage">
              <b>{{ row.marketJdCount ?? 0 }}</b> 条 JD · <b>{{ row.knowledgeDocumentCount ?? 0 }}</b> 份资料
            </span>
            <span class="evidence-coverage evidence-coverage--sub">
              {{ row.sourcePlatformCount ?? 0 }} 个平台 · {{ row.independentEmployerCount ?? 0 }} 个主体
            </span>
          </template>
        </el-table-column>
        <el-table-column label="来源构成" min-width="170">
          <template #default="{ row }">
            <el-tag
              v-for="source in row.evidenceBreakdown || []"
              :key="source"
              size="small"
              effect="plain"
              class="source-type-tag"
            >{{ sourceTypeLabel(source) }}</el-tag>
            <span v-if="!(row.evidenceBreakdown || []).length" class="evidence-coverage">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="candidateStatusType(row)" size="small">{{ candidateStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议流转" min-width="150">
          <template #default="{ row }">
            <span>{{ row.differentiationReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.recommendedAction === 'POST_EVOLUTION'" link type="primary" size="small" @click="goToEvolution(row)">进入岗位演化</el-button>
            <el-button v-else link type="primary" size="small" @click="openEvidence(row)">查看证据</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="16">
      <!-- 左侧：来源与审核说明 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>数据来源</span>
          </template>
          <!--
            把两类语料写清楚：上传的资料**不是 JD**，它与市场 JD 一起参与能力共现，
            但统计与证据展示都分开计数。用户此前正是看到「N 条 JD」里含自己的白皮书才产生误解。
          -->
          <el-alert title="无需填写岗位名称" type="success" :closable="false" show-icon>
            本页统计两类语料：<b>市场 JD</b>（采集/导入的招聘岗位数据）与<b>上传资料</b>（行业报告、政策文件）。
            两者共同参与能力词共现，但分开计数 —— 资料不会被算作 JD。
            候选方向名称由系统按能力社区自动生成，不取自任何文件名。
          </el-alert>
          <div class="source-actions">
            <el-button type="primary" :loading="discovering" @click="loadDiscovery">开始发现</el-button>
            <!--
              就地展开上传，不再跳转到资料库。

              原先这里是 router.push('/rag/knowledge')：用户正处在「发现 → 审核候选 → 创建岗位」
              的流程里，上传只是**前置准备**，却被踢到另一个模块，而且落地页没有回程入口。
              同一个「上传并索引」，岗位演化页一直是就地弹表单，本页跳走属于两套行为。

              批量管理（删除 / 重索引 / 检索验证）仍去资料库，作为下方次级文字链保留。
            -->
            <el-button v-if="canUploadSource" plain @click="toggleUploadPanel">
              {{ uploadPanelOpen ? '收起上传' : '上传并索引资料' }}
            </el-button>
          </div>

          <el-collapse-transition>
            <div v-if="canUploadSource && uploadPanelOpen" class="upload-panel">
              <el-form label-position="top" size="small">
                <el-form-item label="文件">
                  <el-upload
                    :auto-upload="false"
                    :limit="1"
                    :show-file-list="false"
                    accept=".pdf,.doc,.docx,.txt,.md"
                    :on-change="onSourceFileChange"
                  >
                    <el-button size="small" plain>选择文件</el-button>
                  </el-upload>
                  <span v-if="sourceFile" class="upload-panel__file">{{ sourceFile.name }}</span>
                  <span v-else class="upload-panel__hint">支持 pdf / doc / docx / txt / md</span>
                </el-form-item>
                <el-form-item label="资料标题">
                  <el-input v-model="sourceForm.title" maxlength="120" placeholder="如：2026 智能制造人才需求白皮书" />
                  <!--
                    明确要求手填：标题会随资料进入知识库，并出现在候选方向的证据列表里。
                    用文件名当标题会让证据列表出现一堆 .docx / .pdf 字样，无法审核。
                  -->
                  <span class="upload-panel__hint upload-panel__hint--block">
                    请填写资料的真实名称，不要直接用文件名 —— 标题会随资料进入知识库，并出现在候选方向的证据中。
                  </span>
                </el-form-item>
                <div class="upload-panel__row">
                  <el-form-item label="行业">
                    <el-input v-model="sourceForm.industry" placeholder="可留空" />
                  </el-form-item>
                  <el-form-item label="可信等级">
                    <el-select v-model="sourceForm.trustLevel" style="width:140px">
                      <el-option label="高" value="HIGH" />
                      <el-option label="中" value="MEDIUM" />
                      <el-option label="低" value="LOW" />
                    </el-select>
                  </el-form-item>
                </div>
                <div class="upload-panel__actions">
                  <el-button type="primary" :loading="uploadingSource" @click="handleUploadSource">上传并索引</el-button>
                  <el-button @click="toggleUploadPanel">取消</el-button>
                </div>
              </el-form>
            </div>
          </el-collapse-transition>

          <div class="source-note">
            两类语料可以单独使用：只采了市场 JD、或只传了资料，都会出结果。
            发现结果只进入人工审核池，不会自动创建岗位。
            <span v-if="canUploadSource">上传成功后会自动刷新下方发现结果。</span>
            <router-link class="source-note__link" to="/rag/knowledge">去资料库管理</router-link>
          </div>
          <el-divider />
          <div class="review-steps">
            <div><b>1</b> 解析、清洗、去重、索引（市场 JD 与上传资料分别计数）</div>
            <div><b>2</b> 按能力社区生成候选方向，并计算趋势/可信度</div>
            <div><b>3</b> 人工选择演化、创建、驳回或继续观察</div>
          </div>
        </el-card>

        <!-- 数据源概览 -->
        <el-card v-if="result?.dataSources?.length" shadow="never" style="margin-top:12px">
          <template #header>
            <span>数据源概览</span>
          </template>
          <div class="source-tags">
            <el-tag
              v-for="src in result.dataSources"
              :key="src"
              size="small"
              type="info"
              style="margin:2px"
            >{{ src }}</el-tag>
          </div>
          <div v-if="result.crossValidation" style="margin-top:8px;font-size:12px;color:var(--app-text-muted)">
            覆盖 {{ result.crossValidation.sourceDiversity }} 类数据源 ·
            最新数据 {{ freshnessLabel(result.crossValidation.freshnessLevel) }}
          </div>
        </el-card>

      </el-col>

      <!-- 右侧：分析结果 -->
      <el-col :span="14">
        <!-- 结构化岗位定义 -->
        <el-card v-if="hasStructuredDefinition" shadow="never" style="margin-bottom:12px">
          <template #header>
            <span>岗位定义</span>
          </template>
          <div v-if="result?.reasoning" style="margin-bottom:12px;padding:8px 12px;background:var(--el-fill-color-light);border-radius:4px;font-size:13px;color:var(--el-text-color-regular)">
            <strong>岗位摘要：</strong>{{ result.reasoning }}
          </div>
          <el-collapse>
            <el-collapse-item v-if="result?.coreResponsibilities?.length" title="核心职责" name="responsibilities">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.coreResponsibilities" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.requiredSkills?.length" title="必备技能" name="required">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.requiredSkills" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.bonusSkills?.length" title="加分技能" name="bonus">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.bonusSkills" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.industryScenarios?.length" title="典型行业应用场景" name="scenarios">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.industryScenarios" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <!-- 交叉验证摘要 -->
        <el-card v-if="result?.crossValidation" shadow="never" style="margin-bottom:12px">
          <template #header>
            <div class="card-header">
              <span>交叉验证摘要</span>
              <el-tag
                :type="result.crossValidation.consistencyScore >= 80 ? 'success' : result.crossValidation.consistencyScore >= 60 ? 'warning' : 'danger'"
                size="small"
              >一致性 {{ result.crossValidation.consistencyScore }}%</el-tag>
            </div>
          </template>
          <div class="cv-grid">
            <div v-for="item in result.crossValidation.sourceBreakdown" :key="item.sourceType" class="cv-item">
              <span class="cv-item__label">{{ item.label }}</span>
              <span class="cv-item__count">{{ item.abilityCount }} 项能力</span>
            </div>
          </div>
        </el-card>

        <!-- 推荐原型 -->
        <el-card v-if="result?.recommendedPrototypes?.length" shadow="never" style="margin-bottom: 12px">
          <template #header>
            <span>推荐岗位原型</span>
          </template>
          <el-table :data="result.recommendedPrototypes" border size="small">
            <el-table-column prop="prototypeName" label="原型名称" />
            <el-table-column prop="industry" label="行业" width="100" />
            <el-table-column prop="category" label="分类" width="80" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="applyPrototype(row)">应用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 推荐能力 — 含证据来源、置信度、人工优化入口 -->
        <el-card v-if="result?.recommendedAbilities?.length" shadow="never">
          <template #header>
            <div class="card-header">
              <span>推荐能力要求（共 {{ result.recommendedAbilities.length }} 项）</span>
              <div>
                <el-button v-if="editedAbilities.length > 0" text type="warning" size="small" @click="resetEdits" style="margin-right:8px">
                  撤销修改 ({{ editedAbilities.length }})
                </el-button>
                <el-button type="primary" size="small" :loading="creating" :disabled="creating" @click="handleCreatePost">一键创建岗位</el-button>
              </div>
            </div>
          </template>
          <el-table :data="displayAbilities" border size="small">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="suggestedName" label="能力标签" min-width="130">
              <template #default="{ row }">
                <div class="ability-name-cell">
                  <span>{{ row.suggestedName }}</span>
                  <el-tag v-if="row.hallucinationRisk" type="danger" size="small" effect="dark">幻觉风险</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="160">
              <template #default="{ row }">
                <ConfidenceGauge
                  :score="row.confidenceScore ?? 70"
                  :evidence-count="row.evidenceSources?.length ?? 0"
                  :show-evidence-count="true"
                  :show-hallucination-risk="true"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column label="证据来源" min-width="180">
              <template #default="{ row }">
                <div class="source-stack">
                  <el-tag v-for="(ev, idx) in (row.evidenceSources || [])" :key="`${ev.sourceType}-${idx}`" size="small" effect="plain" type="info">
                    {{ evidenceSourceLabel(ev) }}
                  </el-tag>
                  <span v-if="!row.evidenceSources?.length" class="text-muted">暂无来源</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="tagCategory" label="分类" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.tagCategory === 'TECHNICAL'" type="primary" size="small">技术</el-tag>
                <el-tag v-else-if="row.tagCategory === 'BUSINESS'" type="warning" size="small">业务</el-tag>
                <el-tag v-else-if="row.tagCategory === 'SOFT'" type="success" size="small">软技能</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="minRequiredLevel" label="等级" width="55" />
            <el-table-column prop="weight" label="权重" width="55" />
            <el-table-column label="核心" width="55">
              <template #default="{ row }">
                <el-tag v-if="row.isCore" type="danger" size="small">核心</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="匹配" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.matchStatus === 'MATCHED'" type="success" size="small">已有</el-tag>
                <el-tag v-else-if="row.matchStatus === 'SIMILAR'" type="warning" size="small">相似</el-tag>
                <el-tag v-else type="info" size="small">新建</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row, $index }">
                <el-button link type="danger" size="small" @click="removeAbility($index)">
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="result?.reasoning" style="margin-top:10px;font-size:12px;color:var(--app-text-muted);line-height:1.6">
            <strong>分析依据：</strong>{{ result.reasoning }}
          </div>
        </el-card>

        <!-- 空状态 -->
        <el-card v-if="!result && !analyzing" shadow="never">
          <el-empty description="选择候选并查看来源证据，或先上传资料并索引" />
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="selectedCandidate" shadow="never" class="evidence-card">
      <template #header>
        <div class="card-header">
          <span>候选证据与人工审核依据</span>
          <el-button text type="primary" @click="selectedCandidate = null">收起</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="候选方向（系统生成）">{{ selectedCandidate.candidateName }}</el-descriptions-item>
        <el-descriptions-item label="发现模式">{{ modeLabel(selectedCandidate.discoveryMode || 'OBSERVATION') }}</el-descriptions-item>
        <!--
          证据必须分列且标明性质：
          · 真实岗位名只可能来自市场 JD 的岗位字段；
          · 资料标题可能来自上传文件名，**不是岗位名**，只能作溯源。
          合并成一行会让用户以为文件名就是被识别出来的岗位。
        -->
        <el-descriptions-item label="证据中的真实岗位名" :span="2">
          {{ selectedCandidate.evidencePostNames?.length ? selectedCandidate.evidencePostNames.join('、') : '未包含市场 JD 证据' }}
        </el-descriptions-item>
        <el-descriptions-item label="资料标题（仅溯源，非岗位名）" :span="2">
          {{ selectedCandidate.sourceTitles?.length ? selectedCandidate.sourceTitles.join('、') : '本次证据未引用上传资料' }}
        </el-descriptions-item>
        <el-descriptions-item label="证据来源构成" :span="2">
          <el-tag v-for="source in selectedCandidate.evidenceBreakdown || []" :key="`bd-${source}`" size="small" effect="plain" style="margin-right:6px">
            {{ sourceTypeLabel(source) }}
          </el-tag>
          <span v-if="!(selectedCandidate.evidenceBreakdown || []).length">暂无</span>
        </el-descriptions-item>
        <el-descriptions-item label="能力信号" :span="2">{{ selectedCandidate.coreAbilities?.join('、') || '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="证据摘要" :span="2">{{ selectedCandidate.evidenceSummary || '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="风险" :span="2">
          <el-tag v-for="flag in (selectedCandidate.riskFlags || [])" :key="flag" type="warning" size="small" style="margin-right:6px">{{ flag }}</el-tag>
          <span v-if="!selectedCandidate.riskFlags?.length">未发现明显风险</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="evidence-actions">
        <el-button v-if="shouldOfferEvolution(selectedCandidate)" type="primary" @click="goToEvolution(selectedCandidate)">进入岗位演化审核</el-button>
        <el-button v-if="shouldOfferManualCreate(selectedCandidate)" type="success" @click="openCreateDialog(selectedCandidate)">进入人工创建审核</el-button>
        <el-button @click="ElMessage.info('候选保留在趋势观察池，不会创建正式岗位')">继续观察</el-button>
      </div>
    </el-card>

    <!-- 人工创建岗位对话框：预填候选名 + 能力项，并展示证据供人工判断 -->
    <el-dialog v-model="createDialogVisible" title="人工创建正式岗位" width="720px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        候选方向名由系统按能力社区自动生成，<b>不是正式岗位名</b>；请结合下方证据确认真实岗位名称后再创建。
      </el-alert>

      <el-form label-position="top" size="small">
        <el-form-item label="正式岗位名称" required>
          <el-input v-model="createForm.postName" maxlength="100" show-word-limit placeholder="请填写正式岗位名称" />
        </el-form-item>
        <el-form-item label="岗位描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可留空，后续在岗位详情页补充" />
        </el-form-item>
      </el-form>

      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="证据中的真实岗位名">
          {{ createEvidence.postNames.length ? createEvidence.postNames.join('、') : '未包含市场 JD 证据' }}
        </el-descriptions-item>
        <el-descriptions-item label="证据条数">{{ createEvidence.evidenceCount }}</el-descriptions-item>
        <el-descriptions-item label="资料标题（仅溯源，非岗位名）" :span="2">
          {{ createEvidence.sourceTitles.length ? createEvidence.sourceTitles.join('、') : '本次证据未引用上传资料' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="createEvidence.riskFlags.length" label="风险提示" :span="2">
          <el-tag v-for="flag in createEvidence.riskFlags" :key="`rk-${flag}`" type="warning" size="small" effect="plain" style="margin-right:6px">{{ flag }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="create-abilities">
        <div class="create-abilities__header">
          <span>能力要求（共 {{ createForm.abilities.length }} 项，可删除不需要的项）</span>
        </div>
        <el-table :data="createForm.abilities" border size="small" empty-text="没有可创建的能力项">
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column prop="suggestedName" label="能力标签" min-width="140" />
          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <el-input v-model="row.tagCategory" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="最低等级" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.minRequiredLevel" :min="1" :max="5" size="small" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeCreateAbility($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="creating" @click="submitCreatePost">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { discoverEmergingPosts, getMarketInsight, confirmEmergingPost } from '@/api/emerging-post'
import type { EmergingAbilityItem, EmergingPostDiscovery, EmergingPostResponse, MarketInsight } from '@/api/emerging-post'
import {
  buildCreateFormFromCandidate,
  buildEvidenceSummary,
  shouldOfferEvolution,
  shouldOfferManualCreate,
  validateCreateForm,
  type CreatePostForm,
} from './create-post-logic'
import { uploadIndustryWhitepaper } from '@/api/evolution'
import type { PostPrototypeVO } from '@/api/post-prototype'
import { useRouter } from 'vue-router'
import { hasPermission } from '@/utils/permission'
import ConfidenceGauge from '@/components/common/ConfidenceGauge.vue'

const router = useRouter()
const analyzing = ref(false)
const optimizing = ref(false)
const creating = ref(false)
const result = ref<EmergingPostResponse | null>(null)
const removedIndices = ref<Set<number>>(new Set())
const discovering = ref(false)
const discoveryLoaded = ref(false)

/* ------------------------------ 人工创建岗位 ------------------------------ */
/** 创建对话框可见性 */
const createDialogVisible = ref(false)
/** 创建表单（预填候选名 + 能力项，人工可改） */
const createForm = reactive<CreatePostForm>({ postName: '', description: '', abilities: [] })
/** 创建前展示的证据摘要（供人工判断，不做自动化决策） */
const createEvidence = reactive(buildEvidenceSummary(null))

/** 打开创建对话框：用候选预填表单，并展示证据供人工确认 */
function openCreateDialog(candidate: EmergingPostDiscovery) {
  const form = buildCreateFormFromCandidate(candidate)
  createForm.postName = form.postName
  createForm.description = form.description
  createForm.abilities = form.abilities

  const evidence = buildEvidenceSummary(candidate)
  createEvidence.postNames = evidence.postNames
  createEvidence.sourceTitles = evidence.sourceTitles
  createEvidence.evidenceCount = evidence.evidenceCount
  createEvidence.riskFlags = evidence.riskFlags

  createDialogVisible.value = true
}

/** 删除一项能力要求 */
function removeCreateAbility(index: number) {
  createForm.abilities.splice(index, 1)
}

/**
 * 提交创建。
 * <p>
 * 校验不过时**保持对话框打开并保留已填内容**（人工输入成本高，不能清空），
 * 只在对话框内提示；成功后跳转新岗位详情页，便于继续配置能力模型。
 */
async function submitCreatePost() {
  if (creating.value) return
  const error = validateCreateForm(createForm)
  if (error) {
    ElMessage.warning(error)
    return
  }
  creating.value = true
  try {
    const res = await confirmEmergingPost({
      postName: createForm.postName.trim(),
      description: createForm.description?.trim() || undefined,
      abilities: createForm.abilities,
    })
    const newPostId = res.data
    ElMessage.success('岗位创建成功')
    createDialogVisible.value = false
    await loadDiscovery()
    if (newPostId) {
      router.push(`/post/detail/${newPostId}`)
    }
  } catch (e: any) {
    const message = String(e?.message || '')
    if (message.toLowerCase().includes('timeout') || message.includes('超时')) {
      ElMessage.warning('请求超时，岗位可能已创建，请刷新岗位列表确认')
    } else {
      ElMessage.error('创建失败: ' + (message || '未知错误'))
    }
  } finally {
    creating.value = false
  }
}
const discoveryCandidates = ref<EmergingPostDiscovery[]>([])
const marketInsight = ref<MarketInsight | null>(null)
const discoveryMode = computed(() => discoveryCandidates.value[0]?.discoveryMode || inferMode(marketInsight.value?.analyzedJdCount || 0))

const selectedCandidate = ref<EmergingPostDiscovery | null>(null)

/* ===================== 资料上传（就地展开，不跳转） ===================== */

/**
 * 是否可就地上传资料。
 *
 * 复用岗位演化页的上传接口（`/post/evolution/sources/industry-whitepaper`），
 * 该前缀要求 `POST:EVOLUTION`；本页自身的门槛是 `POST:MANAGE`，两者并不等价 ——
 * 所以按权限显隐，避免「看得到按钮、点下去 403」。
 */
const canUploadSource = computed(() => hasPermission('POST:EVOLUTION'))

const uploadPanelOpen = ref(false)
const uploadingSource = ref(false)
const sourceFile = ref<File | null>(null)
const sourceForm = reactive({ title: '', industry: '', trustLevel: 'MEDIUM' })

function toggleUploadPanel() {
  uploadPanelOpen.value = !uploadPanelOpen.value
}

function onSourceFileChange(file: { raw?: File }) {
  sourceFile.value = file.raw ?? null
  /*
   * 【不要再用文件名兜底填标题】
   *
   * 原先这里会把 `2026年AI人才需求白皮书.docx` 去掉扩展名后直接写进标题。
   * 这个标题会随资料写进知识库（`rag_knowledge_document.title`），并出现在
   * 候选方向的证据列表里 —— 结果是页面上冒出一堆以文件名命名的「资料/岗位」，
   * 既不可读也无法审核。标题必须由人明确给出，宁可多一步输入。
   */
}

function resetUploadPanel() {
  sourceFile.value = null
  sourceForm.title = ''
  sourceForm.industry = ''
  sourceForm.trustLevel = 'MEDIUM'
}

async function handleUploadSource() {
  if (!sourceFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!sourceForm.title.trim()) {
    ElMessage.warning('请填写资料标题')
    return
  }
  uploadingSource.value = true
  try {
    const res = await uploadIndustryWhitepaper(sourceFile.value, {
      title: sourceForm.title.trim(),
      industry: sourceForm.industry.trim() || undefined,
      trustLevel: sourceForm.trustLevel,
    })
    ElMessage.success(`资料已上传并索引，共 ${res.data?.chunkCount ?? 0} 个知识片段，正在按新资料重新解析…`)
    // 上传只是前置准备，用户真正的目标是发现结果 —— 所以直接刷新，
    // 不让他上传完还要自己再点一次「开始发现」。
    resetUploadPanel()
    uploadPanelOpen.value = false
    await loadDiscovery()
  } catch (error) {
    // 失败时保留已选文件与已填内容，否则要重填一遍
    console.error('资料上传或索引失败', error)
    ElMessage.error('资料上传或索引失败，请检查文件后重试')
  } finally {
    uploadingSource.value = false
  }
}

const displayAbilities = computed(() => {
  if (!result.value?.recommendedAbilities) return []
  return result.value.recommendedAbilities.filter((_, idx) => !removedIndices.value.has(idx))
})

const editedAbilities = computed(() => {
  if (!result.value?.recommendedAbilities) return []
  return result.value.recommendedAbilities.filter((_, idx) => removedIndices.value.has(idx))
})

const hasStructuredDefinition = computed(() => {
  if (!result.value) return false
  return !!(result.value.coreResponsibilities?.length ||
    result.value.requiredSkills?.length ||
    result.value.bonusSkills?.length ||
    result.value.industryScenarios?.length)
})

function freshnessLabel(level: string): string {
  const map: Record<string, string> = { FRESH: '7天内', RECENT: '30天内', STALE: '超过30天' }
  return map[level] || level
}

function removeAbility(index: number) {
  const next = new Set(removedIndices.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  removedIndices.value = next
}

function resetEdits() {
  removedIndices.value = new Set()
}

function inferMode(count: number): 'OBSERVATION' | 'CANDIDATE' | 'DISCOVERY' {
  if (count >= 500) return 'DISCOVERY'
  if (count >= 50) return 'CANDIDATE'
  return 'OBSERVATION'
}

function modeLabel(mode: string) {
  return ({ OBSERVATION: '观察模式', CANDIDATE: '候选模式', DISCOVERY: '发现模式' } as Record<string, string>)[mode] || mode
}

function modeTagType(mode: string) {
  return ({ OBSERVATION: 'info', CANDIDATE: 'warning', DISCOVERY: 'success' } as Record<string, 'info' | 'warning' | 'success'>)[mode] || 'info'
}

function vocabularySourceLabel(source?: string) {
  const map: Record<string, string> = {
    POST_ABILITY_MODEL: '岗位能力模型',
    ABILITY_TAG: '能力标签库',
    SEED: '内置引导词表（岗位能力模型与标签库均为空）',
  }
  return source ? map[source] || source : '未知'
}

function candidateStatusLabel(candidate: EmergingPostDiscovery) {
  if (candidate.reviewStatus === 'OBSERVATION') return '仅观察'
  return candidate.recommendedAction === 'POST_EVOLUTION' ? '演化候选' : '待人工确认'
}

function candidateStatusType(candidate: EmergingPostDiscovery) {
  if (candidate.reviewStatus === 'OBSERVATION') return 'info'
  return candidate.recommendedAction === 'POST_EVOLUTION' ? 'warning' : 'success'
}

async function loadDiscovery() {
  discovering.value = true
  try {
    // 列表与顶部洞察必须使用同一候选集，否则会出现统计 50 条、列表仅 10 条的错觉。
    const [candidatesResult, insightResult] = await Promise.all([discoverEmergingPosts(50), getMarketInsight()])
    discoveryCandidates.value = candidatesResult.data || []
    marketInsight.value = insightResult.data || null
    discoveryLoaded.value = true
  } catch (error: any) {
    ElMessage.error('市场岗位发现失败: ' + (error.message || '未知错误'))
  } finally {
    discovering.value = false
  }
}

/**
 * 证据来源类型 → 中文名。
 * 表格的「来源构成」列与证据区的来源标签共用这一份映射，避免两处各写一套后对不上。
 */
const SOURCE_TYPE_LABELS: Record<string, string> = {
  MARKET_JD: '市场招聘 JD',
  OFFICIAL_POLICY: '官方政策',
  OFFICIAL_DOCUMENT: '官方文件',
  INDUSTRY_REPORT: '行业报告',
  INDUSTRY_WHITEPAPER: '行业白皮书',
  ZHIHU_TREND: '知乎趋势',
  CLOUD_KNOWLEDGE_INTERNAL: '云端知识库',
  RESUME: '简历资料',
}

function sourceTypeLabel(type: string): string {
  return SOURCE_TYPE_LABELS[type] || type
}

function evidenceSourceLabel(source: { sourceType?: string; sourceName?: string }): string {
  return source.sourceName?.trim() || sourceTypeLabel(source.sourceType || '') || '未知来源'
}

function openEvidence(candidate: EmergingPostDiscovery) {
  selectedCandidate.value = candidate
  ElMessage.info('已选中候选，请在证据区查看来源并进入人工审核')
}

function goToEvolution(candidate: EmergingPostDiscovery) {
  const postId = candidate.relatedExistingPostIds?.[0]
  if (!postId) {
    ElMessage.warning('未找到可演化的既有岗位')
    return
  }
  router.push({
    path: '/post/evolution',
    query: {
      postId: String(postId), trigger: 'MARKET_DISCOVERY', candidateName: candidate.candidateName,
      abilities: (candidate.coreAbilities || []).join('、'), reason: candidate.differentiationReason || '',
      evidenceCount: String((candidate.sourceRefs || []).length),
    },
  })
}

const handleAnalyze = loadDiscovery

const handleReanalyze = async () => {
  if (!result.value) return
  optimizing.value = true
  try {
    await loadDiscovery()
    ElMessage.success('趋势候选已刷新')
  } catch (e: any) {
    ElMessage.error('重分析失败: ' + (e.message || '未知错误'))
  } finally {
    optimizing.value = false
  }
}

const applyPrototype = async (_prototype: PostPrototypeVO) => {
  ElMessage.info('请先创建岗位，然后在岗位详情页应用原型')
}

/**
 * 「一键创建岗位」：不再走死路，改为打开人工创建对话框。
 * <p>
 * 说明：趋势候选的方向名是系统自动生成的，**不等于正式岗位名**，因此必须由人工确认后创建，
 * 不能真的"一键"落库。这里复用证据卡片上的同一个对话框，保证两处行为一致。
 */
const handleCreatePost = () => {
  if (creating.value) return
  const abilities = displayAbilities.value
  if (!abilities.length) {
    ElMessage.warning('没有可创建的能力项')
    return
  }
  // 用当前分析结果构造一个"候选"形态，复用 openCreateDialog 的预填逻辑
  // （岗位名留空由人工填写：分析结果里没有正式岗位名，只有岗位描述与能力项）
  openCreateDialog({
    candidateName: '',
    coreAbilities: abilities.map((a) => a.suggestedName).filter(Boolean),
    evidenceSummary: result.value?.suggestedDescription || result.value?.reasoning || '',
  } as EmergingPostDiscovery)
}

onMounted(() => {
  loadDiscovery()
})

</script>

<style scoped>
.emerging-post-page {
  padding: 16px;
}
.discovery-card { margin-bottom: 16px; }
.header-subtitle { margin-left: 10px; color: var(--app-text-muted); font-size: 12px; font-weight: normal; }
.discovery-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 16px 28px; margin-bottom: 12px; }
.summary-item { display: flex; align-items: center; gap: 8px; color: var(--app-text-secondary); font-size: 13px; }
.summary-item strong { color: var(--app-text-primary); font-size: 18px; }
.summary-item--quality strong { font-size: 15px; }
/* 语料口径块：市场 JD 与上传资料分列展示，用竖线在视觉上把两类来源与其它指标隔开 */
.summary-item--corpus {
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  padding-left: 12px;
  border-left: 3px solid var(--app-primary);
}
.summary-item--corpus strong { line-height: 1.2; }
.summary-item__hint {
  color: var(--app-text-muted);
  font-size: 11px;
  font-style: normal;
}
.summary-note { margin-left: auto; color: var(--app-text-muted); font-size: 12px; }
.discovery-diagnostic { margin-bottom: 12px; }
.discovery-diagnostic__meta { color: var(--app-text-muted); font-size: 12px; }
.evidence-coverage { color: var(--app-text-secondary); font-size: 12px; white-space: nowrap; }
.evidence-coverage b { color: var(--app-text-primary); }
.evidence-coverage--sub { display: block; color: var(--app-text-muted); font-size: 11px; }
.source-type-tag { margin: 2px 4px 2px 0; }
.skill-tag { margin: 2px 4px 2px 0; }
.discovery-table :deep(.cell) { line-height: 1.45; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.market-jd-input-row { display: flex; width: 100%; gap: 8px; align-items: flex-start; }
.source-actions { display: flex; gap: 8px; margin-top: 16px; }
.source-note { margin-top: 12px; color: var(--app-text-muted); font-size: 12px; line-height: 1.6; }
.source-note__link { margin-left: 8px; color: var(--app-primary); text-decoration: none; }
.source-note__link:hover { text-decoration: underline; }
/* 就地上传面板：用内嵌面板而非弹窗，保持「上传是发现的前置准备」这一层关系可见 */
.upload-panel {
  margin-top: 12px;
  padding: 12px 14px 2px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}
.upload-panel :deep(.el-form-item) { margin-bottom: 10px; }
.upload-panel__row { display: flex; gap: 16px; flex-wrap: wrap; }
.upload-panel__row :deep(.el-form-item) { flex: 1 1 160px; }
.upload-panel__file { margin-left: 10px; color: var(--app-text); font-size: 12px; }
.upload-panel__hint { margin-left: 10px; color: var(--app-text-muted); font-size: 12px; }
/* 标题栏下方的强提示：说明为什么不能用文件名当标题 */
.upload-panel__hint--block { display: block; margin: 6px 0 0; line-height: 1.5; }
.upload-panel__actions { display: flex; gap: 8px; margin-bottom: 10px; }
.evidence-card { margin-top: 16px; }
.evidence-actions { display: flex; gap: 8px; margin-top: 16px; flex-wrap: wrap; }
/* 人工创建对话框：能力项区域的头部与表格间距（与页面其它卡片保持一致的视觉节奏） */
.create-abilities__header {
  margin-bottom: 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.review-steps { display: grid; gap: 10px; color: var(--app-text-secondary); font-size: 13px; }
.review-steps b { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border-radius: 50%; background: var(--app-primary); color: white; margin-right: 6px; }
.cv-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.cv-item {
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.06);
}
.cv-item__label {
  font-size: 12px;
  color: var(--app-text-secondary);
}
.cv-item__count {
  font-size: 16px;
  font-weight: 800;
  color: var(--app-primary);
}
.evidence-stack,
.source-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.ability-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.text-muted {
  color: var(--app-text-muted);
  font-size: 12px;
}
.source-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.definition-list {
  margin: 0;
  padding-left: 20px;
  list-style: disc;
}
.definition-list li {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
