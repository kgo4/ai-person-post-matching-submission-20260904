package com.example.matching.service.system;

import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;

import java.util.List;
import java.util.Map;

/**
 * 能力准入服务接口
 * <p>
 * 提供统一的能力准入处理流程，业务服务只需提出能力主张，
 * 由本服务统一决策是否准入、创建标签、进入候选池或拒绝。
 *
 * @author system
 */
public interface AbilityAdmissionService {

    /**
     * 处理单个能力主张
     *
     * @param context 能力主张上下文
     * @return 准入结果
     */
    TagAdmissionResult processAbilityClaim(TagAdmissionContext context);

    /**
     * 批量处理能力主张
     *
     * @param contexts 能力主张上下文列表
     * @return 导入结果统计
     */
    AbilityImportResultDTO processAbilityClaims(List<TagAdmissionContext> contexts);

    /**
     * 批量处理能力主张（返回详细结果）
     * <p>
     * 返回一个包含导入统计和每个能力主张的详细准入结果的Map：
     * - "importResult": AbilityImportResultDTO
     * - "admissionResults": List&lt;TagAdmissionResult&gt;
     *
     * @param contexts 能力主张上下文列表
     * @return 包含统计和详细结果的Map
     */
    Map<String, Object> processAbilityClaimsWithDetails(List<TagAdmissionContext> contexts);
}
