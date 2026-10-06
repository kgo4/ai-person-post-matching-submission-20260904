package com.example.matching.service.post;

import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;

import java.util.List;

/**
 * JD能力提取服务接口
 * <p>
 * 从岗位JD中通过AI分析提取所需能力项，并支持将确认结果写入岗位能力模型。
 */
public interface JdAbilityExtractService {

    /**
     * 从JD文本中AI分析提取能力项
     *
     * @param postId 岗位ID
     * @param jdText JD文本内容
     * @return AI分析结果（含能力项列表和匹配状态）
     */
    JdAnalyzeResponseDTO analyzeJd(Long postId, String jdText);

    /**
     * 确认并应用AI分析结果到岗位能力模型
     * <p>
     * 将用户确认后的能力项列表：
     * 1. 匹配到已有标签的直接使用tagId
     * 2. 标记为新标签的自动创建
     * 3. 批量写入post_ability_model表
     *
     * @param postId 岗位ID
     * @param items  用户确认的能力项列表
     */
    void applyAnalysisResult(Long postId, List<JdAbilityItemDTO> items);
}
