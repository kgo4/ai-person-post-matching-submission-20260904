package com.example.matching.service.evolution;

import com.example.matching.dto.post.JdAbilityItemDTO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 市场JD能力自动准入服务（确定性门禁 + 批量 Harness 决策 + 持久化计划）
 * <p>
 * 仅用于 MarketJdImportServiceImpl 的市场 JD 导入路径。本服务不直接更新
 * {@code MarketJdData}，不在事务内调用 {@code AiTrustHarnessService}；
 * 所有决策在内存中完成，持久化由调用方按返回计划执行。
 */
public interface MarketJdCapabilityAdmissionService {

    /**
     * 纯确定性准入门禁：对一批 JD 的提取结果做服务端可验证的过滤、直接证据自动准入、
     * 现有标签语义 defer 与新能力阈值分组。不调用 Harness、不做任何持久化。
     *
     * @param request 批次提取结果
     * @return 三类不相交的路由结果
     */
    AdmissionGateResult evaluateGate(AdmissionBatchRequest request);

    /**
     * 批次准入主入口：确定性门禁 + AiTrustHarnessService 批量决策 + 自动持久化计划。
     * <p>
     * 契约：
     * <ul>
     *   <li>必须由调用方在<strong>事务外</strong>调用（admitVerifiedNewTag 内部自带事务）</li>
     *   <li>本服务不直接更新 MarketJdData；持久化由调用方按返回计划执行</li>
     *   <li>Harness 一律通过 {@code verifyBatch} 分批调用，绝不在 per-JD 循环中调 {@code verify}</li>
     * </ul>
     *
     * @param request 批次提取结果
     * @return 持久化计划（每个 JD 的最终标签集合、新标签创建记录、计数）
     */
    AdmissionPlan admitBatch(AdmissionBatchRequest request);

    // ==================== 输入模型 ====================

    /**
     * 批次准入输入
     *
     * @param batchNo 导入批次号
     * @param jds     本批次全部 JD 的提取结果（含已清洗文本与来源元数据）
     */
    record AdmissionBatchRequest(String batchNo, List<JdExtraction> jds) {
    }

    /**
     * 单条 JD 的提取上下文
     *
     * @param jdId                market_jd_data.id
     * @param cleanedJdText       已清洗 JD 文本（证据核验基准）
     * @param companyDiversityKey 公司多样性键（可空白；空白的公司不参与公司数阈值）
     * @param serverGeneratedRefs 服务端为该 JD 生成的可信来源引用列表（含 source:MARKET_JD:&lt;jdId&gt;）
     * @param items               该 JD 提取出的能力项（含证据字段）
     */
    record JdExtraction(Long jdId, String cleanedJdText, String companyDiversityKey,
                        List<String> serverGeneratedRefs, List<JdAbilityItemDTO> items) {
    }

    // ==================== 门禁输出模型 ====================

    /**
     * 门禁路由结果：三个不相交的集合
     *
     * @param autoAcceptedTagIdsByJd   直接证据（规范名/别名命中）自动准入：jdId -> 有序去重的正式标签ID集合
     * @param deferredExistingTagClaims 语义 defer：现有标签主张，需 Harness 批量判定
     * @param deferredNewAbilityGroups 达到双阈值的新能力分组（按归一化能力名分组）
     * @param rejectedClaimCount       被拒绝的主张数（证据/来源/标签/阈值不达标，不产生 review 项）
     */
    record AdmissionGateResult(Map<Long, LinkedHashSet<Long>> autoAcceptedTagIdsByJd,
                               Map<Long, LinkedHashSet<Long>> recommendedTagIdsByJd,
                               List<ExistingTagDeferredClaim> deferredExistingTagClaims,
                               Map<String, NewAbilityGroup> deferredNewAbilityGroups,
                               int rejectedClaimCount) {
    }

    /**
     * 现有标签语义 defer 主张（evidence 有效但无规范名/别名直接命中）
     *
     * @param jdId         市场JD ID
     * @param matchedTagId 匹配到的启用正式标签ID
     * @param suggestedName 能力名
     * @param matchStatus  MATCHED 或 SIMILAR
     * @param evidenceText 证据文本（Harness 载荷）
     * @param sourceRefs   可信来源引用（仅 source:MARKET_JD:&lt;jdId&gt;）
     */
    record ExistingTagDeferredClaim(Long jdId, Long matchedTagId, String suggestedName,
                                    String matchStatus, String evidenceText, List<String> sourceRefs) {
    }

    /**
     * 新能力分组（达到 minJdCount 与 minCompanyCount 双阈值）
     *
     * @param normalizedAbilityName 归一化能力名（仅大小写/空白）
     * @param members               去重后的成员（companyKey+jdId 去重，供 Harness 取代表成员）
     * @param distinctJdCount       distinct jdId 数
     * @param distinctCompanyCount  非空白 companyDiversityKey 去重数
     */
    record NewAbilityGroup(String normalizedAbilityName, List<GroupMember> members,
                           int distinctJdCount, int distinctCompanyCount) {
    }

    /**
     * 新能力分组代表成员
     *
     * @param jdId               市场JD ID
     * @param companyDiversityKey 公司多样性键（可为空白）
     * @param suggestedName       建议能力名
     * @param evidenceText        证据文本
     * @param sourceRefs          可信来源引用（仅 source:MARKET_JD:&lt;jdId&gt;）
     */
    record GroupMember(Long jdId, String companyDiversityKey, String suggestedName,
                       String evidenceText, List<String> sourceRefs) {
    }

    // ==================== 批次准入计划 ====================

    /**
     * 批次准入持久化计划（全部决策完成后由调用方执行持久化）
     *
     * @param acceptedTagIdsByJd       每个 JD 的最终正式标签ID集合（直通 AUTO_ACCEPT ∪ Harness PASS 合并；无准入的 JD 也会出现在 map 中，值为空集合）
     * @param formalTagCreations       新能力 PASS 自动建/复用的正式标签记录（tagId 已写入对应组内每个 JD 的 acceptedTagIdsByJd）
     * @param autoAcceptedCount        直接证据自动准入 claim 数
     * @param harnessPassCount         Harness PASS 数（现有标签 + 新能力组）
     * @param harnessBlockedCount      Harness BLOCK 或基础设施失败数
     * @param harnessRetryDroppedCount RETRY 重试一次后仍未通过（丢弃）数
     * @param existingReviewDroppedCount 现有标签 REVIEW 丢弃数（不产生候选、不写入 skill_tags）
     * @param reviewCandidateGroupCount  新能力 REVIEW 候选组数（受 reviewMaxGroupsPerBatch 上限约束，建候选前检查）
     * @param rejectedClaimCount       被拒绝的主张数（门禁拒绝 + 新能力组未达阈值/超 cap/被 BLOCK 等）
     * @param infraFailedJdIds         受 Harness 基础设施失败（超时/异常/响应基数不符）影响的 JD ID 集合；
     *                                 这些 JD 必须保持 analysisStatus=0 以便重试，且永不因失败而被准入
     */
    record AdmissionPlan(Map<Long, LinkedHashSet<Long>> acceptedTagIdsByJd,
                         Map<Long, LinkedHashSet<Long>> recommendedTagIdsByJd,
                         List<FormalTagPlan> formalTagCreations,
                         int autoAcceptedCount,
                         int harnessPassCount,
                         int harnessBlockedCount,
                         int harnessRetryDroppedCount,
                         int existingReviewDroppedCount,
                         int reviewCandidateGroupCount,
                         int rejectedClaimCount,
                         Set<Long> infraFailedJdIds) {
    }

    /**
     * 新能力 PASS 自动建/复用正式标签的记录
     *
     * @param tagId               正式标签ID（新建或复用）
     * @param normalizedAbilityName 归一化能力名
     * @param sourceType          来源类型（MARKET_JD）
     */
    record FormalTagPlan(Long tagId, String normalizedAbilityName, String sourceType) {
    }
}
