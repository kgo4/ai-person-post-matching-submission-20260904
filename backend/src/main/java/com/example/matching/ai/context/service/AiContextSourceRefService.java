package com.example.matching.ai.context.service;

import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;

/**
 * AI上下文来源引用服务
 *
 * @author system
 */
public interface AiContextSourceRefService {

    /**
     * 从员工能力生成来源引用
     */
    AiContextSourceRefDTO fromEmpAbility(EmpAbility ability, AbilityTag tag);

    /**
     * 从岗位能力要求生成来源引用
     */
    AiContextSourceRefDTO fromPostAbilityModel(PostAbilityModel model, AbilityTag tag);

    /**
     * 从证据生成来源引用
     */
    AiContextSourceRefDTO fromEvidence(ContestEvidenceItem evidence);

    /**
     * 从匹配记录生成来源引用
     */
    AiContextSourceRefDTO fromMatchingRecord(MatchingRecord record);

    /**
     * 校验来源引用是否在上下文包中
     */
    boolean isAllowedSourceRef(String ref, AiContextPackageDTO context);

    /**
     * 解析来源引用详情（仅返回解析成功的引用）
     */
    AiContextSourceRefDTO resolve(String ref);

    /**
     * 解析来源引用并返回结构化状态。
     * <p>
     * 状态语义：
     * - VALID：解析成功且记录存在
     * - NOT_FOUND：格式正确但记录不存在或已删除
     * - UNAUTHORIZED：记录存在但不属于当前业务对象/租户
     * - UNSUPPORTED：实体类型不受支持或格式非法
     * - DEPENDENCY_ERROR：依赖查询异常，需要重试
     */
    ResolveOutcome resolveWithStatus(String ref);

    /**
     * 来源引用解析结果
     */
    record ResolveOutcome(SourceRefValidationResult status, AiContextSourceRefDTO resolved) {
    }
}
