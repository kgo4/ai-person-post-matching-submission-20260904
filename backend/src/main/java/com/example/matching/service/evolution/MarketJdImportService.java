package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.evolution.MarketJdData;

import java.util.List;

/**
 * 市场JD导入服务接口
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

    record VerifiedPostImportJd(String postName, String jobDescription, Long matchedPostId,
                                List<Long> verifiedTagIds) {
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
