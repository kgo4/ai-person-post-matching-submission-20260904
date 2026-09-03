<template>
  <div class="page-shell">
    <!-- Header -->
    <section class="harness-hero">
      <div class="harness-hero__text">
        <div class="harness-hero__eyebrow">AI Governance</div>
        <h1 class="harness-hero__title">{{ pageTitle }}</h1>
        <p class="harness-hero__desc">{{ pageDescription }}</p>
      </div>
    </section>

    <!-- Tab Switch -->
    <nav class="harness-nav">
      <button
        class="harness-nav__item"
        :class="{ 'harness-nav__item--active': pageMode === 'records' }"
        @click="router.push('/ai-governance/records')"
      >岗位能力巡检</button>
      <button
        class="harness-nav__item"
        :class="{ 'harness-nav__item--active': pageMode === 'assessment' }"
        @click="router.push('/ai-governance/assessment-harness')"
      >人员评估最终审核</button>
    </nav>

    <!-- Summary -->
    <section v-if="pageMode === 'records'" class="harness-stats">
      <div class="harness-stat">
        <span class="harness-stat__val">{{ inspectionSummary.postCount }}</span>
        <span class="harness-stat__lbl">岗位数</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val">{{ inspectionSummary.abilityCount }}</span>
        <span class="harness-stat__lbl">能力总数</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-warn">{{ inspectionSummary.riskyCount }}</span>
        <span class="harness-stat__lbl">风险能力</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-danger">{{ inspectionSummary.highCount }}</span>
        <span class="harness-stat__lbl">高风险</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val">{{ inspectionSummary.aiSourceCount }}</span>
        <span class="harness-stat__lbl">AI来源能力</span>
      </div>
    </section>
    <section v-else class="harness-stats">
      <div class="harness-stat">
        <span class="harness-stat__val">{{ summary.totalCount }}</span>
        <span class="harness-stat__lbl">总判定</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-ok">{{ summary.passCount }}</span>
        <span class="harness-stat__lbl">通过</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-warn">{{ summary.reviewCount }}</span>
        <span class="harness-stat__lbl">复核</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-danger">{{ summary.blockCount }}</span>
        <span class="harness-stat__lbl">拦截</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-danger">{{ summary.highRiskCount }}</span>
        <span class="harness-stat__lbl">高风险</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-warn">{{ summary.selfEvidenceCount }}</span>
        <span class="harness-stat__lbl">自证据</span>
      </div>
      <div class="harness-stat">
        <span class="harness-stat__val is-warn">{{ summary.pendingCount || 0 }}</span>
        <span class="harness-stat__lbl">待处理</span>
      </div>
    </section>

    <!-- Table Card -->
    <section class="harness-table-card">
      <!-- Quick Filters -->
      <div class="harness-quick">
        <template v-if="pageMode === 'assessment'">
          <button class="hq-btn" :class="{ 'hq-btn--active': assessmentView === 'PENDING' }" @click="switchAssessmentView('PENDING')">待审核</button>
          <button class="hq-btn" :class="{ 'hq-btn--active': assessmentView === 'HISTORY' }" @click="switchAssessmentView('HISTORY')">审核历史</button>
        </template>
        <template v-else>
          <button class="hq-btn hq-btn--danger" :class="{ 'hq-btn--active': inspectionOnlyRisky }" @click="toggleOnlyRisky">只看风险</button>
          <button class="hq-btn" :class="{ 'hq-btn--active': inspectionOnlyAi }" @click="toggleOnlyAi">只看AI来源</button>
          <button class="hq-btn" :class="{ 'hq-btn--active': !inspectionOnlyRisky && !inspectionOnlyAi }" @click="resetInspectionFilter">全部</button>
        </template>
      </div>

      <!-- Filters -->
      <div v-if="pageMode === 'records'" class="harness-filters">
        <el-input
          v-model="inspectionKeyword"
          placeholder="按岗位名称/编码搜索"
          clearable
          style="width: 240px"
          @keyup.enter="reloadInspection"
          @clear="reloadInspection"
        />
        <el-button type="primary" @click="reloadInspection">查询</el-button>
        <span class="harness-muted" style="align-self: center">共 {{ inspectionPagination.total }} 个岗位</span>
      </div>

      <!-- 岗位能力巡检表格（records 模式，替换原标签/数据治理记录列表） -->
      <div v-if="pageMode === 'records'" class="harness-table-body">
        <el-table :data="inspectionPosts" v-loading="loading" size="default" row-key="postId">
          <el-table-column label="岗位" min-width="220">
            <template #default="{ row }">
              <div class="harness-person">
                <span class="harness-person__name">{{ row.postName }}</span>
                <span v-if="row.postCode" class="harness-person__code">{{ row.postCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="能力数" width="90" align="center">
            <template #default="{ row }">{{ row.abilityCount }}</template>
          </el-table-column>
          <el-table-column label="风险能力" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.riskyCount" :type="row.highCount ? 'danger' : 'warning'" effect="plain" size="small">{{ row.riskyCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column label="高风险" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.highCount" type="danger" effect="plain" size="small">{{ row.highCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column label="AI来源" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.aiSourceCount" type="warning" effect="plain" size="small">{{ row.aiSourceCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="openInspectionDrawer(row)">查看能力</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 人员评估最终审核按人员聚合（assessment 模式，人员 harness 逻辑不动） -->
      <div v-else class="harness-table-body">
        <el-table :data="groupedLogs" v-loading="loading" size="default" row-key="key">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="harness-subtable-wrap">
                <el-table :data="row.items" size="small" row-key="id" class="harness-subtable">
                  <el-table-column prop="checkCode" label="检查编码" width="180" />
                  <el-table-column label="场景" width="115">
                    <template #default="{ row: r }">{{ labelScenario(r.scenario) }}</template>
                  </el-table-column>
                  <el-table-column label="类型" width="105">
                    <template #default="{ row: r }">{{ labelClaimType(r.claimType) }}</template>
                  </el-table-column>
                  <el-table-column prop="claimText" label="声明内容" min-width="200" show-overflow-tooltip />
                  <el-table-column label="判定" width="70" align="center">
                    <template #default="{ row: r }">
                      <el-tag :type="decisionTagType(r.decision)" effect="plain" size="small">{{ labelDecision(r.decision) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="风险" width="70" align="center">
                    <template #default="{ row: r }">
                      <el-tag :type="riskTagType(r.riskLevel)" effect="plain" size="small">{{ labelRisk(r.riskLevel) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="80" align="center">
                    <template #default="{ row: r }">
                      <el-tag :type="reviewStatusTagType(resolveReviewStatus(r))" effect="plain" size="small">
                        {{ reviewStatusLabel(resolveReviewStatus(r)) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="业务应用" width="105" align="center">
                    <template #default="{ row: r }">
                      <el-tag :type="businessApplyStatusTagType(r.businessApplyStatus)" effect="plain" size="small">
                        {{ businessApplyStatusLabel(r.businessApplyStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="supportScore" label="支撑分" width="70" align="center" />
                  <el-table-column label="自证据" width="70" align="center">
                    <template #default="{ row: r }">
                      <el-tag v-if="r.isSelfEvidence === 1" type="danger" effect="plain" size="small">是</el-tag>
                      <span v-else>否</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createdTime" label="创建时间" width="150" />
                  <el-table-column label="来源" width="120">
                    <template #default="{ row: r }">
                      <el-button v-if="r.sourceType && r.sourceRefId" type="primary" link size="small" @click="navigateToSource(r.sourceType, r.sourceRefId)">
                        {{ labelSourceType(r.sourceType) }} #{{ r.sourceRefId }}
                      </el-button>
                      <span v-else class="harness-muted">{{ labelSourceType(r.sourceType) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="300">
                    <template #default="{ row: r }">
                      <div class="harness-action-cluster">
                      <el-button type="primary" link size="small" @click="showDetail(r)">详情</el-button>
                      <template v-if="canManualHandle(r)">
                        <template v-if="r.decision === 'BLOCK'">
                          <el-button type="warning" link size="small" @click="openReviewDialog(r, 'ACCEPTED', true)">人工修改</el-button>
                        </template>
                        <template v-else>
                          <el-button type="success" link size="small" @click="openReviewDialog(r, 'ACCEPTED')">{{ acceptActionLabel }}</el-button>
                        </template>
                        <el-button type="danger" link size="small" @click="openReviewDialog(r, 'REJECTED')">驳回</el-button>
                        <el-button type="warning" link size="small" @click="openReviewDialog(r, 'RESOLVED')">处理</el-button>
                      </template>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="groupColumnLabel" min-width="170">
            <template #default="{ row }">
              <div class="harness-person">
                <span class="harness-person__name">{{ row.empName }}</span>
                <span v-if="row.empCode" class="harness-person__code">{{ row.empCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="能力数" width="90" align="center">
            <template #default="{ row }">{{ row.totalCount }}</template>
          </el-table-column>
          <el-table-column label="待审核" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.pendingCount" type="warning" effect="plain" size="small">{{ row.pendingCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column label="已拦截" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.blockCount" type="danger" effect="plain" size="small">{{ row.blockCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column label="已通过" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.passCount" type="success" effect="plain" size="small">{{ row.passCount }}</el-tag>
              <span v-else class="harness-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column v-if="pageMode === 'assessment' && assessmentView === 'PENDING'" label="操作" width="190" align="right">
            <template #default="{ row }">
              <el-button
                type="success"
                size="small"
                :disabled="row.pendingCount === 0"
                :loading="aiApprovingGroupKey === row.key"
                @click="acceptAiRecommendations(row)"
              >按 AI 建议通过{{ row.safeAiAcceptCount ? ` (${row.safeAiAcceptCount})` : '' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="pageMode === 'records'" class="harness-pagination">
        <el-pagination
          v-model:current-page="inspectionPagination.current"
          v-model:page-size="inspectionPagination.size"
          :total="inspectionPagination.total"
          layout="total, sizes, prev, pager, next"
          size="small"
          @current-change="loadInspectionPosts"
          @size-change="reloadInspection"
        />
      </div>
    </section>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailVisible" title="Harness 判定详情" size="600px">
      <template v-if="currentLog">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="检查编码">{{ currentLog.checkCode }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ labelScenario(currentLog.scenario) }}</el-descriptions-item>
          <el-descriptions-item label="声明类型">{{ labelClaimType(currentLog.claimType) }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <el-button v-if="currentLog.sourceType && currentLog.sourceRefId" type="primary" link
              @click="navigateToSource(currentLog.sourceType, currentLog.sourceRefId)">
              {{ labelSourceType(currentLog.sourceType) }} #{{ currentLog.sourceRefId }}
            </el-button>
            <span v-else>{{ labelSourceType(currentLog.sourceType) }} #{{ currentLog.sourceRefId || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="判定">
            <el-tag :type="decisionTagType(currentLog.decision)" effect="plain" size="small">{{ labelDecision(currentLog.decision) }}</el-tag>
            <el-tag :type="riskTagType(currentLog.riskLevel)" effect="plain" size="small" style="margin-left:6px">{{ labelRisk(currentLog.riskLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理状态">{{ reviewStatusLabel(resolveReviewStatus(currentLog)) }}</el-descriptions-item>
          <el-descriptions-item label="业务应用">
            <el-tag :type="businessApplyStatusTagType(currentLog.businessApplyStatus)" effect="plain" size="small">
              {{ businessApplyStatusLabel(currentLog.businessApplyStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理备注">{{ currentLog.reviewComment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支撑分">{{ currentLog.supportScore ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="canManualHandle(currentLog)" class="harness-detail-actions">
          <template v-if="currentLog.decision === 'BLOCK'">
            <el-button type="warning" size="small" @click="openReviewDialog(currentLog, 'ACCEPTED', true)">人工修改</el-button>
          </template>
          <template v-else>
            <el-button type="success" size="small" @click="openReviewDialog(currentLog, 'ACCEPTED')">{{ acceptActionLabel }}</el-button>
            <el-button type="danger" size="small" @click="openReviewDialog(currentLog, 'REJECTED')">驳回</el-button>
            <el-button size="small" @click="openReviewDialog(currentLog, 'RESOLVED')">标记已处理</el-button>
          </template>
        </div>
        <el-collapse class="harness-collapse">
          <el-collapse-item title="声明内容">
            <pre class="harness-pre">{{ currentLog.claimText || '(空)' }}</pre>
          </el-collapse-item>
          <el-collapse-item title="原始证据">
            <pre class="harness-pre">{{ currentLog.evidenceText || '(无)' }}</pre>
          </el-collapse-item>
          <el-collapse-item title="来源引用">
            <div v-if="parseSourceRefs(currentLog.sourceRefs).length > 0" class="harness-sourcerefs">
              <el-button v-for="ref in parseSourceRefs(currentLog.sourceRefs)" :key="ref" type="primary" link size="small" @click="openSourceRefDrawer(ref)">{{ ref }}</el-button>
            </div>
            <pre v-else class="harness-pre">{{ currentLog.sourceRefs || '(无)' }}</pre>
          </el-collapse-item>
          <el-collapse-item title="RAG分块">
            <pre class="harness-pre">{{ currentLog.ragChunkIds || '(无)' }}</pre>
          </el-collapse-item>
          <el-collapse-item title="判定原因">
            <div v-if="formatReasons(currentLog.reasonJson).length > 0" class="harness-reasons">
              <div v-for="(item, index) in formatReasons(currentLog.reasonJson)" :key="`${item.label}-${index}`" class="harness-reason">
                <span class="harness-reason__idx">{{ index + 1 }}</span>
                <div class="harness-reason__body">
                  <div class="harness-reason__label">{{ item.label }}</div>
                  <div v-if="item.detail" class="harness-reason__detail">{{ item.detail }}</div>
                </div>
              </div>
            </div>
            <span v-else class="harness-muted">(无)</span>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>

    <!-- Review Dialog -->
    <el-dialog v-model="reviewDialogVisible" :title="reviewDialogTitle" width="540px">
      <div class="harness-review-ctx">
        <div class="harness-review-ctx__item">
          <span class="harness-review-ctx__lbl">AI 声明</span>
          <div class="harness-review-ctx__val harness-review-ctx__claim">{{ reviewTarget?.claimText || '(空)' }}</div>
        </div>
        <div class="harness-review-ctx__row">
          <div class="harness-review-ctx__item" style="flex:1">
            <span class="harness-review-ctx__lbl">判定</span>
            <span>
              <el-tag :type="decisionTagType(reviewTarget?.decision || '')" effect="plain" size="small">{{ labelDecision(reviewTarget?.decision) }}</el-tag>
              <el-tag :type="riskTagType(reviewTarget?.riskLevel || '')" effect="plain" size="small" style="margin-left:4px">{{ labelRisk(reviewTarget?.riskLevel) }}</el-tag>
            </span>
          </div>
          <div class="harness-review-ctx__item" style="flex:1">
            <span class="harness-review-ctx__lbl">支撑分</span>
            <span>{{ reviewTarget?.supportScore ?? '-' }}</span>
          </div>
          <div class="harness-review-ctx__item" style="flex:1">
            <span class="harness-review-ctx__lbl">自证据</span>
            <el-tag v-if="reviewTarget?.isSelfEvidence === 1" type="danger" effect="plain" size="small">是</el-tag>
            <span v-else>否</span>
          </div>
        </div>
        <div v-if="reviewTarget?.evidenceText" class="harness-review-ctx__item">
          <span class="harness-review-ctx__lbl">支撑证据</span>
          <div class="harness-review-ctx__val harness-review-ctx__pre">{{ truncate(reviewTarget.evidenceText, 200) }}</div>
        </div>
        <div v-if="reviewTarget?.reasonJson" class="harness-review-ctx__item">
          <span class="harness-review-ctx__lbl">判定原因</span>
          <div class="harness-review-ctx__val harness-review-ctx__pre">{{ truncate(formatReasonText(reviewTarget.reasonJson), 200) }}</div>
        </div>
      </div>

      <el-divider />

      <el-form label-width="90px" ref="reviewFormRef" :model="reviewForm">
        <el-alert v-if="reviewAction === 'ACCEPTED' && reviewTarget?.decision === 'BLOCK'" type="warning" :closable="false"
          title="该能力已被 Harness 自动拦截。人工修改会覆盖自动结论并写入正式人员能力，请仅在原始证据足以证明误判时执行。" />
        <el-form-item v-if="reviewAction === 'ACCEPTED' && reviewTarget?.decision === 'BLOCK'" label="强制覆盖">
          <el-checkbox v-model="reviewForm.forceOverride">我已核对原始证据，确认覆盖自动拦截结论</el-checkbox>
        </el-form-item>
        <el-form-item label="审核动作">
          <el-tag :type="reviewStatusTagType(reviewAction)" size="large">{{ reviewStatusLabel(reviewAction) }}</el-tag>
        </el-form-item>
        <template v-if="reviewAction === 'ACCEPTED'">
          <el-form-item label="采纳理由">
            <el-input v-model="reviewForm.comment" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="记录采纳理由（可选）" />
          </el-form-item>
        </template>
        <template v-if="reviewAction === 'REJECTED'">
          <el-form-item label="原因分类" required>
            <el-select v-model="reviewForm.rejectCategory" placeholder="请选择拒绝原因分类" style="width: 100%">
              <el-option v-for="opt in REJECT_REASON_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="拒绝原因" required>
            <el-input v-model="reviewForm.comment" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="请详细说明拒绝原因（必填）" />
          </el-form-item>
        </template>
        <template v-if="reviewAction === 'RESOLVED'">
          <el-form-item label="处理说明" required>
            <el-input v-model="reviewForm.comment" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="请填写处理说明（必填）" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">确认</el-button>
      </template>
    </el-dialog>

    <SourceRefDrawer
      v-model="sourceRefDrawerVisible"
      :ref-value="currentSourceRef"
    />

    <!-- 岗位能力明细抽屉（巡检） -->
    <el-drawer v-model="inspectionDrawerVisible" :title="`岗位能力巡检：${inspectionDrawerTitle}`" size="820px">
      <div class="inspect-drawer-toolbar">
        <el-tag size="small" effect="plain" :type="(currentInspectionPost?.riskyCount || 0) ? 'warning' : 'success'">
          {{ currentInspectionPost?.riskyCount || 0 }} 项风险 / {{ currentInspectionPost?.abilityCount || 0 }} 项能力
        </el-tag>
        <span class="harness-muted">此处修改/删除将直接同步到岗位能力表</span>
      </div>
      <el-table :data="inspectionAbilities" v-loading="inspectionAbilitiesLoading" size="default" row-key="id">
        <el-table-column prop="abilityName" label="能力名称" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'inspect-name--abnormal': (row.riskTags || []).includes('名称异常') }">{{ row.abilityName || '(空)' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="70" align="center">
          <template #default="{ row }">{{ row.minRequiredLevel ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="权重" width="70" align="center">
          <template #default="{ row }">{{ row.weight ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="核心" width="64" align="center">
          <template #default="{ row }"><el-tag v-if="row.isCore === 1" type="danger" size="small" effect="plain">核心</el-tag><span v-else class="harness-muted">—</span></template>
        </el-table-column>
        <el-table-column label="来源" width="80" align="center">
          <template #default="{ row }"><el-tag v-if="row.aiSource" type="warning" size="small" effect="plain">AI生成</el-tag><span v-else class="harness-muted">人工</span></template>
        </el-table-column>
        <el-table-column label="风险提示" width="190">
          <template #default="{ row }">
            <div class="inspect-risk-tags">
              <el-tag v-for="tag in (row.riskTags || [])" :key="tag" size="small" effect="plain" :type="riskTextTagType(tag)">{{ tag }}</el-tag>
              <span v-if="!(row.riskTags || []).length" class="harness-muted">正常</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="evidenceText" label="证据" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="right" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditAbility(row)">修改</el-button>
            <el-button type="danger" link size="small" @click="removeAbility(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!inspectionAbilitiesLoading && inspectionAbilities.length === 0" class="harness-muted" style="padding: 16px">
        该岗位暂无能力配置
      </div>
    </el-drawer>

    <!-- 修改岗位能力弹窗（巡检） -->
    <el-dialog v-model="editAbilityVisible" title="修改岗位能力" width="560px">
      <el-form label-width="90px" :model="editAbilityForm">
        <el-form-item label="能力名称">
          <el-input v-model="editAbilityForm.abilityName" placeholder="岗位能力正式名称" />
        </el-form-item>
        <el-form-item label="标签ID">
          <el-input-number v-model="editAbilityForm.tagId" :min="0" :precision="0" style="width: 100%" />
          <div class="harness-hint">可留空；能力名称是主关联字段，标签仅作辅助</div>
        </el-form-item>
        <el-form-item label="最低等级">
          <el-input-number v-model="editAbilityForm.minRequiredLevel" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="editAbilityForm.weight" :min="0" :max="100" :precision="2" />
        </el-form-item>
        <el-form-item label="是否核心">
          <el-switch v-model="editAbilityForm.isCore" :active-value="1" :inactive-value="0" active-text="核心" />
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="editAbilityForm.isRequired" :active-value="1" :inactive-value="0" active-text="必填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editAbilityForm.remark" type="textarea" :rows="2" placeholder="记录修改原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editAbilityVisible = false">取消</el-button>
        <el-button type="primary" :loading="editAbilitySaving" @click="submitEditAbility">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchReviewGovernanceChecks, getAssessmentHarnessPersonGroups, getGovernanceSummary, pageGovernanceChecks, updateGovernanceReviewStatus, REJECT_REASON_OPTIONS } from '@/api/ai-governance'
import type { AiHarnessCheckLog, AiHarnessSummary, AssessmentHarnessPersonGroup, AssessmentHarnessReviewView } from '@/api/ai-governance'
import { getPostInspectionSummary, listPostInspectionAbilities, pagePostInspection } from '@/api/post-ability-inspection'
import type { PostAbilityInspectionItem, PostAbilityInspectionPost, PostAbilityInspectionSummary } from '@/api/post-ability-inspection'
import { deleteModelConfig, updateModelConfig } from '@/api/post'
import SourceRefDrawer from '@/components/common/SourceRefDrawer.vue'
import {
  SCENARIO_LABELS, CLAIM_TYPE_LABELS, DECISION_LABELS, RISK_LABELS, SOURCE_TYPE_LABELS,
  humanizeCode, labelScenario, labelClaimType, labelDecision, labelRisk, labelSourceType,
  decisionTagType, riskTagType, reviewStatusTagType, reviewStatusLabel,
  businessApplyStatusTagType, businessApplyStatusLabel, resolveReviewStatus, CLAIM_TYPE_OPTIONS,
} from './constants'

const router = useRouter()
const route = useRoute()

// 人员评估最终 Harness 与标签/数据治理记录使用不同的后端筛选，避免混入同一审批语义。
const pageMode = computed<'records' | 'assessment'>(() => {
  if (route.path.includes('assessment-harness')) return 'assessment'
  return 'records'
})

const pageTitle = computed(() => {
  if (pageMode.value === 'assessment') return '人员评估最终审核'
  return '岗位能力巡检'
})

const pageDescription = computed(() => {
  if (pageMode.value === 'assessment') {
    return '仅处理完成简历、测试和面试核验后的最终能力结论；通过后自动完成等级确认与正式人员能力入库。'
  }
  return '以岗位为单位检查入库后的能力质量：AI 提取/生成的能力是否存在幻觉、异常名称、等级/权重问题，发现后可当场修改或删除，结果直接同步岗位能力表。'
})

const groupColumnLabel = computed(() => pageMode.value === 'assessment' ? '评估人员' : '治理归属')
const acceptActionLabel = computed(() => pageMode.value === 'assessment' ? '通过并入库' : '采纳治理结论')

const reviewDialogTitle = computed(() => {
  if (reviewAction.value === 'ACCEPTED') {
    return pageMode.value === 'assessment' ? '通过最终审核并入库' : '采纳治理结论'
  }
  if (reviewAction.value === 'REJECTED') return '驳回 Harness 判定'
  return '标记已处理'
})

const loading = ref(false)
const logs = ref<AiHarnessCheckLog[]>([])

/** 人员评估按员工聚合；普通治理记录以独立治理归属展示。 */
interface HarnessPersonGroup {
  key: string
  empId?: number
  empName: string
  empCode?: string
  items: AiHarnessCheckLog[]
  totalCount: number
  /** 待人工审核（reviewStatus = PENDING） */
  pendingCount: number
  /** AI 判定为 BLOCK */
  blockCount: number
  /** AI 判定为 PASS */
  passCount: number
  /** 可按 AI 建议直接通过的待审能力数。 */
  safeAiAcceptCount: number
}

const groupedLogs = computed<HarnessPersonGroup[]>(() => {
  if (pageMode.value === 'assessment') {
    return assessmentGroups.value.filter((group) => group.empId != null).map((group) => ({
      key: group.empId ? `emp:${group.empId}` : 'person-unassigned',
      empId: group.empId,
      empName: group.empName,
      empCode: group.empCode,
      items: group.items,
      totalCount: group.totalCount,
      pendingCount: group.pendingCount,
      blockCount: group.items.filter((item) => item.decision === 'BLOCK').length,
      passCount: group.items.filter((item) => item.decision === 'PASS').length,
      safeAiAcceptCount: group.safeAiAcceptCount,
    }))
  }

  const map = new Map<string, HarnessPersonGroup>()
  for (const log of logs.value) {
    const personAbilityLog = log.claimType === 'EMP_ABILITY' || log.scenario === 'PERSON_ABILITY'
    // 人员审核记录必须归属真实员工；员工删除后残留的孤儿日志不再伪装成“待关联人员”。
    if (personAbilityLog && !log.empId) continue
    const groupKey = log.empId ? `emp:${log.empId}` : personAbilityLog ? 'person-unassigned' : 'governance'
    let group = map.get(groupKey)
    if (!group) {
      group = {
        key: groupKey,
        empId: log.empId,
        empName: log.empName || (log.empId ? `员工#${log.empId}`
          : personAbilityLog ? '人员能力（未关联员工）' : '标签/数据治理记录'),
        empCode: log.empCode,
        items: [],
        totalCount: 0,
        pendingCount: 0,
        blockCount: 0,
        passCount: 0,
        safeAiAcceptCount: 0,
      }
      map.set(groupKey, group)
    }
    group.items.push(log)
    group.totalCount++
    if (resolveReviewStatus(log) === 'PENDING') group.pendingCount++
    if (log.decision === 'BLOCK') group.blockCount++
    if (log.decision === 'PASS') group.passCount++
  }
  return Array.from(map.values())
})
const detailVisible = ref(false)
const currentLog = ref<AiHarnessCheckLog | null>(null)
const quickFilterActive = ref('')
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const assessmentView = ref<AssessmentHarnessReviewView>('PENDING')
const assessmentGroups = ref<AssessmentHarnessPersonGroup[]>([])
const aiApprovingGroupKey = ref<string | null>(null)
const reviewAction = ref<'ACCEPTED' | 'REJECTED' | 'RESOLVED'>('RESOLVED')
const reviewTarget = ref<AiHarnessCheckLog | null>(null)
const reviewFormRef = ref()

// 审核表单
const reviewForm = reactive({
  comment: '',
  rejectCategory: '',
  forceOverride: false,
})

// SourceRef 抽屉
const sourceRefDrawerVisible = ref(false)
const currentSourceRef = ref('')

const filters = reactive({
  scenario: '',
  decision: '',
  riskLevel: '',
  claimType: '',
  reviewStatus: '',
  isSelfEvidence: undefined as number | undefined,
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0,
})

const summary = reactive<AiHarnessSummary>({
  passCount: 0,
  reviewCount: 0,
  blockCount: 0,
  totalCount: 0,
  highRiskCount: 0,
  mediumRiskCount: 0,
  selfEvidenceCount: 0,
  pendingCount: 0,
})

// ===== 岗位能力巡检（records 模式）=====
const inspectionPosts = ref<PostAbilityInspectionPost[]>([])
const inspectionAbilities = ref<PostAbilityInspectionItem[]>([])
const inspectionAbilitiesLoading = ref(false)
const inspectionDrawerVisible = ref(false)
const currentInspectionPost = ref<PostAbilityInspectionPost | null>(null)
const inspectionKeyword = ref('')
const inspectionOnlyRisky = ref(false)
const inspectionOnlyAi = ref(false)
const inspectionPagination = reactive({ current: 1, size: 10, total: 0 })
const inspectionSummary = reactive<PostAbilityInspectionSummary>({
  postCount: 0,
  abilityCount: 0,
  riskyCount: 0,
  highCount: 0,
  aiSourceCount: 0,
})
const inspectionDrawerTitle = computed(() => currentInspectionPost.value?.postName || '')

const loadInspectionSummary = async () => {
  try {
    const res = await getPostInspectionSummary()
    Object.assign(inspectionSummary, res.data)
  } catch (e) {
    // 巡检统计失败不阻断页面
    console.warn('岗位能力巡检汇总加载失败', e)
  }
}

const loadInspectionPosts = async () => {
  loading.value = true
  try {
    const res = await pagePostInspection({
      current: inspectionPagination.current,
      size: inspectionPagination.size,
      keyword: inspectionKeyword.value || undefined,
      onlyRisky: inspectionOnlyRisky.value || undefined,
      onlyAi: inspectionOnlyAi.value || undefined,
    })
    inspectionPosts.value = res.data.records
    inspectionPagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const reloadInspection = () => {
  inspectionPagination.current = 1
  void Promise.all([loadInspectionSummary(), loadInspectionPosts()])
}

const toggleOnlyRisky = () => {
  inspectionOnlyRisky.value = !inspectionOnlyRisky.value
  if (inspectionOnlyRisky.value) inspectionOnlyAi.value = false
  reloadInspection()
}
const toggleOnlyAi = () => {
  inspectionOnlyAi.value = !inspectionOnlyAi.value
  if (inspectionOnlyAi.value) inspectionOnlyRisky.value = false
  reloadInspection()
}
const resetInspectionFilter = () => {
  inspectionOnlyRisky.value = false
  inspectionOnlyAi.value = false
  reloadInspection()
}

const openInspectionDrawer = async (row: PostAbilityInspectionPost) => {
  currentInspectionPost.value = row
  inspectionDrawerVisible.value = true
  inspectionAbilities.value = []
  inspectionAbilitiesLoading.value = true
  try {
    const res = await listPostInspectionAbilities(row.postId)
    inspectionAbilities.value = res.data
  } finally {
    inspectionAbilitiesLoading.value = false
  }
}

// 修改岗位能力
const editAbilityVisible = ref(false)
const editAbilitySaving = ref(false)
const editAbilityForm = reactive({
  id: 0,
  abilityName: '',
  tagId: undefined as number | undefined,
  minRequiredLevel: 3,
  weight: 0,
  isRequired: 0,
  isCore: 0,
  remark: '',
})

const openEditAbility = (item: PostAbilityInspectionItem) => {
  editAbilityForm.id = item.id
  editAbilityForm.abilityName = item.abilityName || ''
  editAbilityForm.tagId = item.tagId
  editAbilityForm.minRequiredLevel = item.minRequiredLevel ?? 3
  editAbilityForm.weight = Number(item.weight ?? 0)
  editAbilityForm.isRequired = item.isRequired ?? 0
  editAbilityForm.isCore = item.isCore ?? 0
  editAbilityForm.remark = item.remark || ''
  editAbilityVisible.value = true
}

const submitEditAbility = async () => {
  if (!editAbilityForm.abilityName.trim()) {
    ElMessage.warning('能力名称不能为空')
    return
  }
  if (!currentInspectionPost.value) return
  editAbilitySaving.value = true
  try {
    await updateModelConfig(editAbilityForm.id, {
      postId: currentInspectionPost.value.postId,
      abilityName: editAbilityForm.abilityName,
      tagId: editAbilityForm.tagId || undefined,
      minRequiredLevel: editAbilityForm.minRequiredLevel,
      weight: editAbilityForm.weight,
      isRequired: editAbilityForm.isRequired,
      isCore: editAbilityForm.isCore,
      remark: editAbilityForm.remark || undefined,
    })
    ElMessage.success('岗位能力已更新')
    editAbilityVisible.value = false
    await openInspectionDrawer(currentInspectionPost.value)
    await Promise.all([loadInspectionSummary(), loadInspectionPosts()])
  } finally {
    editAbilitySaving.value = false
  }
}

const removeAbility = async (item: PostAbilityInspectionItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除「${item.abilityName || '(空能力)'}」？删除后将同步移除该岗位的能力要求，匹配与学习路径将不再引用它。`,
      '删除岗位能力',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  if (!currentInspectionPost.value) return
  try {
    await deleteModelConfig(item.id)
    ElMessage.success('已删除')
    await openInspectionDrawer(currentInspectionPost.value)
    await Promise.all([loadInspectionSummary(), loadInspectionPosts()])
  } catch (e) {
    ElMessage.error('删除失败：' + ((e as Error)?.message || '未知错误'))
  }
}

const riskTextTagType = (tag: string): 'danger' | 'warning' | 'info' | 'primary' => {
  if (['名称异常', 'Harness拦截', '提取被拒', '高风险', '等级异常', '权重异常'].includes(tag)) return 'danger'
  if (['AI生成', 'Harness复核', '提取未确认', '中风险'].includes(tag)) return 'warning'
  if (tag === '未关联标签') return 'info'
  return 'primary'
}

const loadSummary = async () => {
  const assessmentOnly = pageMode.value === 'assessment'
  const res = await getGovernanceSummary(assessmentOnly)
  Object.assign(summary, res.data)
}

const loadLogs = async () => {
  loading.value = true
  try {
    if (pageMode.value === 'assessment') {
      const res = await getAssessmentHarnessPersonGroups(assessmentView.value)
      assessmentGroups.value = res.data
      logs.value = res.data.flatMap((group) => group.items)
      return
    }

    const params: any = {
      current: pagination.current,
      size: pagination.size,
      claimType: filters.claimType || undefined,
      isSelfEvidence: filters.isSelfEvidence,
      // AI/data governance excludes personnel final-assessment approvals.
      assessmentOnly: false,
    }

    params.scenario = filters.scenario || undefined
    params.decision = filters.decision || undefined
    params.riskLevel = filters.riskLevel || undefined
    params.reviewStatus = filters.reviewStatus || undefined

    const res = await pageGovernanceChecks(params)
    logs.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const acceptAiRecommendations = async (group: HarnessPersonGroup) => {
  const eligible = group.items.filter((item) => resolveReviewStatus(item) === 'PENDING'
    && item.decision === 'PASS'
    && item.riskLevel !== 'HIGH'
    && item.isSelfEvidence !== 1)
  if (eligible.length === 0) {
    ElMessage.warning('该人员没有可按 AI 建议直接通过的能力，需逐条审核')
    return
  }

  try {
    await ElMessageBox.confirm(
      `将通过 ${eligible.length} 项低风险且有有效证据支撑的能力；其余 ${group.pendingCount - eligible.length} 项将保留待审核。`,
      `按 AI 建议通过：${group.empName}`,
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' },
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }

  aiApprovingGroupKey.value = group.key
  try {
    const res = await batchReviewGovernanceChecks({
      ids: eligible.map((item) => item.id),
      reviewStatus: 'ACCEPTED',
      reviewComment: '按 AI 建议通过',
    })
    const result = res.data
    if (result.failedCount) {
      ElMessage.warning(`${group.empName}：已通过 ${result.successCount} 项；${result.failedCount} 项仍需逐条审核`)
    } else {
      ElMessage.success(`${group.empName}：已按 AI 建议通过 ${result.successCount} 项`)
    }
    await reload()
  } finally {
    aiApprovingGroupKey.value = null
  }
}

const switchAssessmentView = (view: AssessmentHarnessReviewView) => {
  if (assessmentView.value === view) return
  assessmentView.value = view
  void reload()
}

const reload = async () => {
  if (pageMode.value === 'records') {
    await Promise.all([loadInspectionSummary(), loadInspectionPosts()])
    return
  }
  await Promise.all([loadSummary(), loadLogs()])
}

const showDetail = (log: AiHarnessCheckLog) => {
  currentLog.value = log
  detailVisible.value = true
}

const canManualHandle = (log: AiHarnessCheckLog | null) => {
  if (!log) return false
  const status = resolveReviewStatus(log)
  // AUTO_PASSED/PASS is audit-only. Even if a historical response carries a
  // stale pending flag, it must never expose a second review action.
  if (status === 'AUTO_PASSED' || log.decision === 'PASS') return false
  return status === 'PENDING'
}

type ReasonItem = { label: string; detail?: string }

const REASON_TEXT_LABELS: Array<[RegExp, string]> = [
  [/^claimText is empty$/i, 'AI 声明内容为空，无法进行可信性校验'],
  [/matched formal tag/i, '已匹配到正式能力标签'],
  [/original evidence text present/i, '存在原始证据文本，可用于支撑该声明'],
  [/all sourceRefs are invalid/i, '来源引用格式无效或无法识别'],
  [/sourceRefs present/i, '已提供来源引用'],
  [/RAG chunks present/i, '已命中 RAG 知识分块作为辅助证据'],
  [/similar formal tag found/i, '找到相似的正式能力标签，需要确认是否复用'],
  [/self evidence or AI-derived source cannot prove the claim/i, '存在自证据风险：AI 生成内容或间接来源不能单独证明该声明'],
  [/diagnosis claim must reference fact:\* or evidence:\* sourceRefs/i, '诊断类声明必须引用事实包或证据中心来源'],
  [/diagnosis claim is grounded by fact package evidence/i, '诊断类声明已有事实包证据支撑'],
  [/new ability has evidence and should enter candidate governance/i, '新能力有证据支撑，应进入候选能力治理流程'],
  [/new ability lacks original evidence/i, '新能力缺少原始证据，不能直接采纳'],
  [/support is partial and requires review/i, '证据只能部分支撑该声明，需要人工复核'],
  [/insufficient support/i, '证据支撑不足，存在幻觉风险'],
  [/emerging post claim must have evidence and sourceRefs/i, '新兴岗位声明必须同时具备证据文本和来源引用'],
  [/emerging post claim requires multiple source support/i, '新兴岗位声明需要多个来源交叉支撑'],
  [/emerging post claim has sufficient evidence/i, '新兴岗位声明已有较充分证据，但仍建议人工确认'],
  [/high impact change must have evidence and sourceRefs/i, '高影响变更必须提供证据文本和来源引用'],
  [/high impact change requires manual review/i, '高影响变更需要人工审核后才能采纳'],
  [/single source support requires review for evolution changes/i, '岗位演化变更只有单一来源支撑，需要人工复核'],
  [/multiple source support with evidence/i, '已有多来源证据支撑'],
  [/evolution claim requires review/i, '岗位演化类声明默认需要人工复核'],
  [/AI interview observation must match a formal ability tag/i, 'AI 面试观察必须匹配正式能力标签'],
  [/AI interview observation lacks answer evidence/i, 'AI 面试观察缺少回答证据，不能证明能力判断'],
  [/AI interview observation lacks valid sourceRefs/i, 'AI 面试观察缺少有效来源引用'],
  [/AI interview observation must reference interview session/i, 'AI 面试观察必须关联面试场次'],
  [/AI interview observation must reference interview question or follow-up to prove ability judgment/i, 'AI 面试观察必须引用面试题或追问记录来证明能力判断'],
  [/AI interview observation is grounded by interview question\/follow-up evidence/i, 'AI 面试观察已有面试题或追问证据支撑'],
  [/sourceRefs.*required|missing.*sourceRefs|sourceRefs.*missing/i, '缺少来源引用'],
  [/claim text is empty|empty claim/i, 'AI 声明内容为空，无法进行可信性校验'],
  [/evidence.*empty|missing evidence|no evidence/i, '缺少支撑证据'],
  [/self[- ]?evidence/i, '存在自证据风险：声明不能只由 AI 自己生成的内容证明'],
  [/support is partial|partial support/i, '证据只能部分支撑该声明，需要人工复核'],
  [/requires review|manual review/i, '需要人工复核'],
  [/low support|support.*low/i, '证据支撑度偏低'],
  [/candidate|similar tag/i, '涉及候选标签或相似标签，需要人工确认'],
  [/blocked|block/i, '已被可信门禁拦截'],
]

const translateReasonText = (value: unknown) => {
  const text = String(value ?? '').trim()
  if (!text) return ''
  const matched = REASON_TEXT_LABELS.find(([pattern]) => pattern.test(text))
  return matched ? matched[1] : '存在其他可信性风险，需要人工核对证据后处理'
}

const formatReasonValue = (label: string, value: unknown): ReasonItem | null => {
  if (value == null || value === '') return null
  if (Array.isArray(value)) {
    const detail = value.map(translateReasonText).filter(Boolean).join('；')
    return detail ? { label, detail } : null
  }
  if (typeof value === 'object') {
    return { label, detail: '包含结构化判定信息，请结合声明内容和证据来源核对' }
  }
  return { label, detail: translateReasonText(value) }
}

const formatReasons = (reasonJson?: string): ReasonItem[] => {
  if (!reasonJson) return []
  try {
    const parsed = JSON.parse(reasonJson)
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => translateReasonText(item))
        .filter(Boolean)
        .map((label) => ({ label }))
    }
    if (typeof parsed === 'string') {
      const label = translateReasonText(parsed)
      return label ? [{ label }] : []
    }
    if (parsed && typeof parsed === 'object') {
      const obj = parsed as Record<string, unknown>
      const items = [
        formatReasonValue('判定原因', obj.reason),
        formatReasonValue('详细说明', obj.detail),
        formatReasonValue('消息', obj.message),
        formatReasonValue('匹配规则', obj.matchedRule),
        formatReasonValue('风险因素', obj.riskFactors),
        formatReasonValue('证据缺口', obj.evidenceGap),
        formatReasonValue('置信度', obj.confidence),
        formatReasonValue('支撑分', obj.score),
        formatReasonValue('原因列表', obj.reasons),
      ].filter((item): item is ReasonItem => !!item)
      return items.length > 0 ? items : [{ label: '结构化判定信息', detail: '系统返回了非标准判定结构，请结合声明内容和证据来源核对' }]
    }
  } catch {
    const label = translateReasonText(reasonJson)
    return label ? [{ label }] : []
  }
  return []
}

const formatReasonText = (reasonJson?: string) => {
  const items = formatReasons(reasonJson)
  if (items.length === 0) return '(无)'
  return items.map((item) => (item.detail ? `${item.label}：${item.detail}` : item.label)).join('\n')
}

const truncate = (str: string, maxLen: number) => {
  if (!str) return ''
  return str.length > maxLen ? str.substring(0, maxLen) + '...' : str
}

const quickFilter = (type: string) => {
  quickFilterActive.value = type
  // 清除原有筛选
  filters.scenario = ''
  filters.decision = ''
  filters.riskLevel = ''
  filters.claimType = ''
  filters.reviewStatus = ''
  filters.isSelfEvidence = undefined

  switch (type) {
    case 'pending':
      filters.reviewStatus = 'PENDING'
      break
    case 'block':
      filters.decision = 'BLOCK'
      break
    case 'highRisk':
      filters.riskLevel = 'HIGH'
      break
    case 'selfEvidence':
      filters.isSelfEvidence = 1
      break
    default:
      break
  }
  void reload()
}

const openReviewDialog = (log: AiHarnessCheckLog, action: 'ACCEPTED' | 'REJECTED' | 'RESOLVED', forceOverride = false) => {
  reviewTarget.value = log
  reviewAction.value = action
  // 重置表单
  reviewForm.comment = ''
  reviewForm.rejectCategory = ''
  reviewForm.forceOverride = forceOverride
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (!reviewTarget.value) return

  // 前端校验
  if (reviewAction.value === 'REJECTED') {
    if (!reviewForm.rejectCategory) {
      ElMessage.warning('请选择拒绝原因分类')
      return
    }
    if (!reviewForm.comment.trim()) {
      ElMessage.warning('请填写拒绝原因')
      return
    }
  }
  if (reviewAction.value === 'RESOLVED' && !reviewForm.comment.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  if (reviewAction.value === 'ACCEPTED' && reviewTarget.value.decision === 'BLOCK'
      && reviewForm.forceOverride && !reviewForm.comment.trim()) {
    ElMessage.warning('强制覆盖时必须填写原因')
    return
  }
  if (reviewAction.value === 'ACCEPTED' && reviewTarget.value.decision === 'BLOCK' && reviewForm.forceOverride) {
    await ElMessageBox.confirm('这会覆盖 Harness 自动拦截结论并写入正式人员能力。请确认原始证据足以支撑该能力。', '确认人工修改', {
      type: 'warning', confirmButtonText: '确认写入', cancelButtonText: '取消',
    })
  }

  reviewSubmitting.value = true
  try {
    await updateGovernanceReviewStatus(reviewTarget.value.id, {
      reviewStatus: reviewAction.value,
      reviewComment: reviewForm.comment || undefined,
      rejectReasonCategory: reviewAction.value === 'REJECTED' ? reviewForm.rejectCategory : undefined,
      forceOverride: reviewAction.value === 'ACCEPTED' ? reviewForm.forceOverride : undefined,
    })
    ElMessage.success('审核状态已更新')
    reviewDialogVisible.value = false
    await reload()
    if (pageMode.value === 'assessment' && assessmentView.value === 'PENDING') {
      detailVisible.value = false
    }
    const refreshedLog = logs.value.find((log) => log.id === reviewTarget.value?.id)
    if (refreshedLog && currentLog.value?.id === refreshedLog.id) {
      currentLog.value = refreshedLog
    }
  } finally {
    reviewSubmitting.value = false
  }
}

/** 打开来源详情抽屉 */
const openSourceRefDrawer = (ref: string) => {
  currentSourceRef.value = ref
  sourceRefDrawerVisible.value = true
}

/** 解析 sourceRefs JSON 数组 */
const parseSourceRefs = (sourceRefsJson?: string): string[] => {
  if (!sourceRefsJson) return []
  try {
    const parsed = JSON.parse(sourceRefsJson)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** 来源跳转：根据 sourceType 跳转到对应的业务页面 */
const navigateToSource = (sourceType: string, sourceRefId: number) => {
  switch (sourceType) {
    case 'MATCH_GAP_DIAGNOSIS':
      router.push('/matching/gap-diagnosis')
      break
    case 'REPORT_GENERATION':
    case 'CONTEST_REPORT_TASK':
      // 报告相关跳转到证据中心或治理记录
      router.push('/capability-brain/evidence')
      break
    case 'JD_IMPORT':
    case 'JD_ABILITY_EXTRACT':
    case 'COMPANY_POST_REQUIREMENT':
      router.push(`/post/detail/${sourceRefId}`)
      break
    case 'RESUME_PARSE':
    case 'AI_RESUME':
    case 'EMP_ABILITY':
      router.push(`/employee/detail/${sourceRefId}`)
      break
    case 'AI_TEST':
      router.push('/employee/ability-profile/ai-test')
      break
    case 'VIDEO_INTERVIEW':
    case 'AI_VIDEO_INTERVIEW':
      router.push('/employee/ability-profile/live-interview')
      break
    case 'POST_EVOLUTION_TASK':
    case 'POST_EVOLUTION':
      // 跳转到岗位演化详情
      router.push(`/capability-brain/evolution/detail/${sourceRefId}`)
      break
    default:
      break
  }
}

watch(pageMode, () => {
  assessmentView.value = 'PENDING'
  assessmentGroups.value = []
  logs.value = []
  inspectionPagination.current = 1
  inspectionKeyword.value = ''
  inspectionOnlyRisky.value = false
  inspectionOnlyAi.value = false
  void reload()
})

onMounted(() => {
  void reload()
})
</script>

<style scoped>
/* ====== AI Governance — Variant C ====== */

/* Header */
.harness-hero {
  padding: 22px 26px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05), transparent 50%), rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
}
.harness-hero__eyebrow { display: inline-flex; padding: 4px 10px; border-radius: 6px; background: rgba(59, 130, 246, 0.08); color: var(--app-primary); font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; margin-bottom: 8px; }
.harness-hero__title { margin: 0; font-size: 26px; font-weight: 800; color: var(--app-text-strong); letter-spacing: -0.04em; }
.harness-hero__desc { margin: 4px 0 0; color: var(--app-text-secondary); font-size: 13px; max-width: 560px; }

/* Tab Nav */
.harness-nav { display: flex; gap: 0; border-bottom: 1px solid rgba(148, 163, 184, 0.14); margin-top: 4px; }
.harness-nav__item { padding: 11px 20px; border: none; border-bottom: 2px solid transparent; background: transparent; color: var(--app-text-muted); font-size: 14px; font-weight: 600; cursor: pointer; transition: color 0.2s, border-color 0.2s; }
.harness-nav__item:hover { color: var(--app-text-secondary); }
.harness-nav__item--active { color: var(--app-primary); border-bottom-color: var(--app-primary); }

/* Stats */
.harness-stats { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; }
.harness-stat { display: flex; flex-direction: column; gap: 3px; padding: 14px 12px; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 10px; background: rgba(255, 255, 255, 0.5); backdrop-filter: blur(6px); }
.harness-stat__val { font-size: 22px; font-weight: 800; color: var(--app-text-strong); line-height: 1; }
.harness-stat__val.is-ok { color: #059669; }
.harness-stat__val.is-warn { color: #d97706; }
.harness-stat__val.is-danger { color: #dc2626; }
.harness-stat__lbl { font-size: 11px; font-weight: 600; color: var(--app-text-muted); }

/* Table Card */
.harness-table-card { display: flex; flex-direction: column; gap: 12px; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 14px; background: rgba(255, 255, 255, 0.58); backdrop-filter: blur(10px); overflow: hidden; }

/* Quick Filters */
.harness-quick { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; padding: 14px 18px 0; }
.hq-btn { padding: 4px 12px; border: 1px solid rgba(148, 163, 184, 0.14); border-radius: 6px; background: rgba(255, 255, 255, 0.5); color: var(--app-text-secondary); font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; }
.hq-btn:hover { border-color: rgba(59, 130, 246, 0.3); color: var(--app-primary); }
.hq-btn--active { background: rgba(59, 130, 246, 0.08); border-color: var(--app-primary); color: var(--app-primary); }
.hq-btn--danger.hq-btn--active { background: rgba(220, 38, 38, 0.08); border-color: #dc2626; color: #dc2626; }

/* Filters */
.harness-filters { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px 18px; }
.harness-table-body { padding: 0; overflow-x: auto; }
.harness-table-body > .el-table { min-width: 760px; }
.harness-subtable-wrap { padding: 4px 16px 12px 44px; overflow-x: auto; }
.harness-subtable { min-width: 1480px; }
.harness-action-cluster { display: flex; flex-wrap: wrap; align-items: center; gap: 2px 8px; min-width: 260px; }
.harness-action-cluster .el-button { margin-left: 0; white-space: nowrap; }
.harness-person { display: flex; align-items: center; gap: 8px; }
.harness-person__name { font-weight: 600; color: var(--app-text-strong); }
.harness-person__code { font-size: 12px; color: var(--app-text-muted); }
.harness-pagination { display: flex; justify-content: flex-end; padding: 10px 18px 14px; }

/* Detail Actions */
.harness-detail-actions { display: flex; gap: 8px; margin-top: 16px; }
.harness-collapse { margin-top: 16px; }
.harness-pre { background: rgba(255, 255, 255, 0.5); padding: 12px; border-radius: 6px; border: 1px solid rgba(148, 163, 184, 0.1); font-size: 12px; max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-all; color: var(--app-text-secondary); }
.harness-sourcerefs { display: flex; flex-direction: column; gap: 4px; padding: 6px 0; }
.harness-muted { color: var(--app-text-muted); font-size: 12px; }

.harness-reasons { display: flex; flex-direction: column; gap: 8px; }
.harness-reason { display: flex; gap: 10px; padding: 10px 12px; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 8px; background: rgba(255, 255, 255, 0.5); }
.harness-reason__idx { width: 22px; height: 22px; flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--app-primary); color: #fff; font-size: 11px; font-weight: 700; }
.harness-reason__body { min-width: 0; }
.harness-reason__label { color: var(--app-text-strong); font-size: 13px; font-weight: 600; line-height: 1.5; }
.harness-reason__detail { margin-top: 2px; color: var(--app-text-secondary); font-size: 12px; line-height: 1.5; word-break: break-word; }

/* Review Dialog */
.harness-review-ctx { background: rgba(255, 255, 255, 0.5); border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 8px; padding: 12px 14px; margin-bottom: 8px; }
.harness-review-ctx__item { margin-bottom: 8px; }
.harness-review-ctx__item:last-child { margin-bottom: 0; }
.harness-review-ctx__lbl { display: block; font-size: 11px; font-weight: 600; color: var(--app-text-muted); margin-bottom: 3px; }
.harness-review-ctx__val { font-size: 13px; color: var(--app-text); line-height: 1.5; }
.harness-review-ctx__row { display: flex; gap: 16px; }
.harness-review-ctx__claim { font-weight: 600; color: var(--app-text-strong); }
.harness-review-ctx__pre { font-size: 12px; color: var(--app-text-secondary); background: rgba(255, 255, 255, 0.5); padding: 6px 8px; border-radius: 4px; border: 1px solid rgba(148, 163, 184, 0.1); max-height: 80px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
.harness-hint { font-size: 11px; color: var(--app-text-muted); margin-top: 4px; }

/* ====== 岗位能力巡检 ====== */
.inspect-drawer-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.inspect-risk-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.inspect-name--abnormal { color: #dc2626; font-weight: 600; }

@media (max-width: 1024px) {
  .harness-stats { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 720px) {
  .harness-stats { grid-template-columns: repeat(2, 1fr); }
  .harness-filters { flex-direction: column; }
}
</style>





