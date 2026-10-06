package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.evolution.MarketJdData;

import java.util.List;

/**
 * 市场JD导入服务接口
 * <p>
 * 爬虫推送入口不在这里：它已独立为
 * {@code com.example.matching.service.evolution.crawler.CrawlerMarketJdIngestService}
 * （逐条独立事务 + 批次日志 + 自动触发解析）。本接口只保留文本/Excel/已确认岗位导入，
 * 以及公共的分析与查询能力。
 *
 * @author system
 */
public interface MarketJdImportService {

    /**
     * 批量导入市场JD数据（从文本列表）
     *
     * @param jdTexts JD文本列表
     * @param sourcePlatform 来源平台
     * @return 导入数量
     */
    int importFromTextList(List<String> jdTexts, String sourcePlatform);

    /**
     * 批量导入市场 JD，并返回后续分析所需的市场样本批次号。
     */
    ImportBatchResult importFromTextListWithBatch(List<String> jdTexts, String sourcePlatform);

    /**
     * 从Excel数据导入
     *
     * @param dataList JD数据列表
     * @return 导入数量
     */
    int importFromExcelData(List<MarketJdData> dataList);

    /**
     * 将已经由人工确认的岗位导入批次纳入市场样本。
     * 只接收本批次已精确匹配的正式标签，不重新调用能力提取 Agent 或 Harness。
     */
    int importVerifiedPostBatch(Long postImportBatchId, List<VerifiedPostImportJd> jds);

    /**
     * 删除一个批次的全部市场 JD 数据及其批次登记。
     * <p>
     * 只按 {@code batchNo} 一条路径删除，不区分批次来源通道：爬虫推送的批次与人工上传的
     * 批次在登记表里是同一张表，因此「删错了数据留在池子里」这类问题不需要两套逻辑。
     * <p>
     * 一并清理：该批次 JD 的历史版本快照、以及别的批次里指向本批次规范文档的
     * 去重引用（否则会留下悬空的分组）。
     *
     * @param batchNo 批次号
     * @return 各表实际删除行数
     * @throws com.example.matching.common.exception.BusinessException 批次号为空或批次不存在
     */
    BatchDeleteResult deleteBatch(String batchNo);

    /**
     * 按 JD 主键删除单条市场 JD。
     * <p>
     * 与 {@link #deleteBatch(String)} 的差别只在选择范围：批次删除适合「整批推错了」，
     * 单条删除适合「池子里混了几条脏数据」—— 后者用批次删会误伤同批次的正常数据。
     * <p>
     * 级联口径与批次删除完全一致（历史版本快照 + 别处指向本条的悬空去重引用），
     * 且使用同一条清理路径，避免两套逻辑各自漏表。
     *
     * @param id 市场 JD 主键
     * @return 实际删除行数
     * @throws com.example.matching.common.exception.BusinessException id 为空或记录不存在
     */
    SingleDeleteResult deleteJd(Long id);

    /**
     * 批量删除市场 JD（按主键列表）。
     * <p>
     * 去重后逐条走 {@link #deleteJd(Long)} 的同一套级联清理；重复 id 只删一次。
     * 因此「全选当前页删除」不会因为页面里混入重复行而报错或重复计数。
     *
     * @param ids 市场 JD 主键列表
     * @return 实际删除行数与未找到的 id
     * @throws com.example.matching.common.exception.BusinessException ids 为空
     */
    BatchDeleteByIdsResult deleteJds(List<Long> ids);

    /**
     * 分页查询市场JD数据
     *
     * @param page 分页参数
     * @param postName 岗位名称过滤
     * @param batchNo 批次号过滤
     * @return 分页结果
     */
    IPage<MarketJdData> pageMarketJds(Page<MarketJdData> page, String postName, String batchNo);

    /**
     * 获取指定岗位相关的市场JD数据
     *
     * @param postId 岗位ID
     * @param limit 限制数量
     * @return JD列表
     */
    List<MarketJdData> getMarketJdsByPostId(Long postId, int limit);

    /**
     * 去重处理
     *
     * @param batchNo 批次号
     * @return 去重数量
     */
    int deduplicateByBatch(String batchNo);

    /**
     * 重算某批次的去重：先**清掉该批次所有重复判定**（并修正去重键），再重新跑一轮去重。
     *
     * <h2>为什么需要这个入口</h2>
     * {@link #deduplicateByBatch(String)} 只处理 {@code isDuplicate=0} 的行 —— 这是幂等所必需的
     * （否则每次解析都要重算全批），但也意味着**一旦被判重复就再也不会被重新评估**。
     * 而历史上治理阶段曾经用错误的口径覆写过去重键（只含正文、丢掉岗位名与公司名），
     * 造成一批业务上并不重复的数据被标成 {@code isDuplicate=1} 并显示为「重复跳过」。
     * 修正口径后，这些历史判定仍然留在库里，必须有一次「按正确口径重判」的机会。
     *
     * <h2>做什么</h2>
     * <ol>
     *   <li>爬虫来源行（{@code ingestChannel=CRAWLER}）按
     *       {@code MarketJdTextMetrics.dedupeKey} 重算 {@code text_hash}；非爬虫来源行不动去重键
     *       （人工上传的去重键取自导入原文，无法由结构化字段复原）；</li>
     *   <li>清空 {@code isDuplicate} / {@code canonicalDocumentId} / {@code similarityGroupId}；</li>
     *   <li>调用 {@link #deduplicateByBatch(String)} 按统一口径重新判定。</li>
     * </ol>
     * 真重复仍会被重新标出（判据来自数据本身，不是「一次豁免」）。
     *
     * @param batchNo 批次号
     * @return 扫描行数、重算去重键的行数、本轮重新命中的重复数
     * @throws com.example.matching.common.exception.BusinessException 批次号为空（400）或该批次无数据（404）
     */
    DedupeResetResult resetDedupeByBatch(String batchNo);

    /**
     * 统计批次数据
     *
     * @param batchNo 批次号
     * @return 统计信息
     */
    BatchStatistics getBatchStatistics(String batchNo);

    /**
     * 批量分析市场JD：治理 → Agent提取岗位能力 → Harness
     * <p>
     * 串联完整链路：
     * 1. RecruitmentDataGovernanceService.governBatch() — 数据清洗/去重/噪声过滤
     * 2. PostCapabilityGenerationService.analyzePostText() — Agent提取能力 + Harness
     * 3. 更新 MarketJdData.analysisStatus
     *
     * @param batchNo 批次号
     * @return 批量分析结果
     */
    BatchAnalysisResult analyzeBatch(String batchNo);

    /**
     * 单条解析：对一条市场 JD 执行「清洗 → 能力提取 → 准入」并落库。
     * <p>
     * 与 {@link #analyzeBatch(String)} 的关系：
     * <ul>
     *   <li>链路完全一致（同一套清洗、同一套 Agent 提取、同一个准入服务），差别只在选择范围
     *       —— 批次解析扫全批次，单条解析只处理指定的一条；</li>
     *   <li>批次解析只捞 {@code analysisStatus=0}（避免重复劳动），单条解析是用户的显式动作，
     *       允许对任意非重复项重跑（含已分析 / 已跳过），用于「这条结果不对，我想再跑一次」；</li>
     *   <li>新能力准入需要跨 JD / 跨公司互相印证（{@code new-ability-min-jd-count} /
     *       {@code new-ability-min-company-count}），单条解析**不会**新建标签 ——
     *       这是设计使然，结论会体现在返回的中文说明里，不能静默。</li>
     * </ul>
     *
     * @param id 市场 JD 主键
     * @return 本次解析的结论（含未准入原因说明）
     * @throws com.example.matching.common.exception.BusinessException id 为空（400）、
     *         记录不存在（404）、或该条已被判定为重复项（409）
     */
    SingleAnalysisResult analyzeOne(Long id);

    /**
     * 批次统计信息
     */
    class BatchStatistics {
        private String batchNo;
        private int totalCount;
        private int duplicateCount;
        private int analyzedCount;
        private int matchedCount;

        // Getters and Setters
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        public int getAnalyzedCount() { return analyzedCount; }
        public void setAnalyzedCount(int analyzedCount) { this.analyzedCount = analyzedCount; }
        public int getMatchedCount() { return matchedCount; }
        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
    }

    record ImportBatchResult(String batchNo, int imported) {
    }

    /**
     * 批次删除结果。
     *
     * @param batchNo          被删除的批次号
     * @param marketJdRows     {@code market_jd_data} 删除行数
     * @param versionSnapshots {@code market_jd_data_version} 删除行数
     * @param batchLogs        {@code market_jd_crawler_batch_log} 删除行数
     */
    record BatchDeleteResult(String batchNo, int marketJdRows, int versionSnapshots, int batchLogs) {
    }

    /**
     * 单条 JD 删除结果。
     *
     * @param id               被删除的市场 JD 主键
     * @param marketJdRows     {@code market_jd_data} 删除行数（存在则为 1）
     * @param versionSnapshots {@code market_jd_data_version} 删除行数
     */
    record SingleDeleteResult(Long id, int marketJdRows, int versionSnapshots) {
    }

    /**
     * 批量 JD 删除结果。
     *
     * @param requested      请求删除的 id 数（去重后）
     * @param marketJdRows   {@code market_jd_data} 实际删除行数
     * @param versionSnapshots {@code market_jd_data_version} 实际删除行数
     * @param missingIds     库中不存在的 id（前端据此提示「有 N 条已被他人删除」）
     */
    record BatchDeleteByIdsResult(int requested, int marketJdRows, int versionSnapshots, List<Long> missingIds) {
    }

    record VerifiedPostImportJd(String postName, String jobDescription, Long matchedPostId,
                                List<Long> verifiedTagIds) {
    }

    /**
     * 批次去重重算结果。
     *
     * @param batchNo        批次号
     * @param scannedRows    该批次扫描到的行数
     * @param hashRecomputed 去重键被重算的行数（爬虫来源行）
     * @param duplicateHits  重判后仍被判为重复的行数 —— 与 {@code scannedRows} 一起看才有意义：
     *                       「扫描 50 行、只有 1 条重复」才是口径修好的证据，
     *                       只回一个「已重置」会让用户误以为重复被无条件豁免了
     */
    record DedupeResetResult(String batchNo, int scannedRows, int hashRecomputed, int duplicateHits) {
    }

    /**
     * 单条解析结论。
     * <p>
     * <b>不返回「已分析」这类中文文案</b>：行级状态码到文案的映射在前端
     * {@code views/post/crawler-status.ts} 里是唯一来源（历史上前后端各存一份同名编码，
     * 导致分析成功的 JD 被显示成「分析中」），后端只回数字，避免再造第二份口径。
     *
     * @param id                    市场 JD 主键
     * @param analysisStatus        落库后的状态：0 待分析（基础设施失败，可重试）/ 1 已分析 / 2 跳过
     * @param acceptedTagCount      本次准入的正式能力标签数
     * @param recommendedTagCount   本次进入推荐集（候选，未正式准入）的标签数
     * @param rejectedClaimCount    被门禁拒绝的能力主张数
     * @param infraFailed           是否为基础设施失败（true 时状态保持 0，且不改动既有标签）
     * @param message               中文结论，含「为什么没有结果」，前端可直接展示
     */
    record SingleAnalysisResult(Long id, int analysisStatus, int acceptedTagCount,
                                int recommendedTagCount, int rejectedClaimCount,
                                boolean infraFailed, String message) {
    }

    /**
     * 批量分析结果
     */
    class BatchAnalysisResult {
        private String batchNo;
        private int totalCount;
        private int skippedDuplicate;
        private int skippedNoise;
        private int governedCount;
        private int extractedSuccess;
        private int extractedFailed;
        private List<String> errors;
        // 市场JD能力自动准入计数（Task 6 新增；现有字段含义不变）
        private int autoAdmittedCount;
        private int harnessPassCount;
        private int harnessBlockedCount;
        private int reviewCandidateGroupCount;
        private int rejectedClaimCount;

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getSkippedDuplicate() { return skippedDuplicate; }
        public void setSkippedDuplicate(int skippedDuplicate) { this.skippedDuplicate = skippedDuplicate; }
        public int getSkippedNoise() { return skippedNoise; }
        public void setSkippedNoise(int skippedNoise) { this.skippedNoise = skippedNoise; }
        public int getGovernedCount() { return governedCount; }
        public void setGovernedCount(int governedCount) { this.governedCount = governedCount; }
        public int getExtractedSuccess() { return extractedSuccess; }
        public void setExtractedSuccess(int extractedSuccess) { this.extractedSuccess = extractedSuccess; }
        public int getExtractedFailed() { return extractedFailed; }
        public void setExtractedFailed(int extractedFailed) { this.extractedFailed = extractedFailed; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public int getAutoAdmittedCount() { return autoAdmittedCount; }
        public void setAutoAdmittedCount(int autoAdmittedCount) { this.autoAdmittedCount = autoAdmittedCount; }
        public int getHarnessPassCount() { return harnessPassCount; }
        public void setHarnessPassCount(int harnessPassCount) { this.harnessPassCount = harnessPassCount; }
        public int getHarnessBlockedCount() { return harnessBlockedCount; }
        public void setHarnessBlockedCount(int harnessBlockedCount) { this.harnessBlockedCount = harnessBlockedCount; }
        public int getReviewCandidateGroupCount() { return reviewCandidateGroupCount; }
        public void setReviewCandidateGroupCount(int reviewCandidateGroupCount) { this.reviewCandidateGroupCount = reviewCandidateGroupCount; }
        public int getRejectedClaimCount() { return rejectedClaimCount; }
        public void setRejectedClaimCount(int rejectedClaimCount) { this.rejectedClaimCount = rejectedClaimCount; }
    }
}
