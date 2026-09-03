package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG评分服务
 * <p>
 * 基于知识库检索结果，评估员工与岗位的匹配程度。
 * RAG分数反映的是：员工的历史项目经验、技能描述与岗位要求的语义匹配度。
 * <p>
 * 评分逻辑：
 * 1. 构建查询文本（员工能力 + 岗位要求）
 * 2. 从知识库检索相关文档
 * 3. 计算检索结果的相关性分数
 */
@Slf4j
@Service
public class RagScoreService {

    private final RagRetrievalService ragRetrievalService;

    public RagScoreService(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    /**
     * 计算RAG匹配分数（M-12：只消费匹配专用 DTO）
     *
     * @param employee     员工匹配画像
     * @param post         岗位匹配画像
     * @param empAbilities 员工能力快照列表
     * @param requirements 岗位要求快照列表
     * @return RAG分数（0-100），如果无法计算返回null
     */
    public BigDecimal calculateRagScore(MatchingEmployeeProfile employee,
                                         MatchingPostProfile post,
                                         List<MatchingAbilitySnapshot> empAbilities,
                                         List<MatchingRequirementSnapshot> requirements) {
        // 规范化输入为空集合
        final List<MatchingAbilitySnapshot> safeAbilities = empAbilities != null ? empAbilities : Collections.emptyList();
        final List<MatchingRequirementSnapshot> safeRequirements = requirements != null ? requirements : Collections.emptyList();

        try {
            // 构建查询文本（使用标签名称）
            String query = buildQueryText(employee, post, safeAbilities, safeRequirements);
            if (query == null || query.isBlank()) {
                log.debug("RAG查询文本为空，跳过RAG评分");
                return null;
            }

            // 从知识库检索相关上下文
            RagRetrievalResult retrievalResult = ragRetrievalService.retrieve(RagRetrievalRequest.builder()
                    .queryText(query)
                    .scenario(RagScenarioEnum.MATCHING_ANALYSIS)
                    .topK(5)
                    .build());
            final List<RagRetrievalResult.RagHit> safeHits = retrievalResult != null && retrievalResult.hasHits()
                    ? retrievalResult.getHits()
                    : Collections.emptyList();
            if (safeHits.isEmpty()) {
                log.debug("RAG未检索到相关知识，empId={}, postId={}", employee.empId(), post.postId());
                return null;
            }

            // 基于检索结果计算分数
            BigDecimal score = calculateScoreFromHits(safeHits, safeAbilities, safeRequirements);

            log.debug("RAG评分完成: empId={}, postId={}, score={}", employee.empId(), post.postId(), score);
            return score;

        } catch (Exception e) {
            log.warn("RAG评分失败: empId={}, postId={}, error={}", employee.empId(), post.postId(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建RAG查询文本（使用标签名称而非ID）
     */
    private String buildQueryText(MatchingEmployeeProfile employee,
                                   MatchingPostProfile post,
                                   List<MatchingAbilitySnapshot> empAbilities,
                                   List<MatchingRequirementSnapshot> requirements) {
        StringBuilder query = new StringBuilder();

        // 构建标签名称映射
        Map<Long, String> tagNameMap = buildTagNameMap(requirements, empAbilities);

        // 岗位信息
        if (post.postName() != null) {
            query.append("岗位：").append(post.postName()).append("。");
        }
        if (post.jobDescription() != null && !post.jobDescription().isBlank()) {
            query.append("岗位描述：").append(post.jobDescription(), 0, Math.min(200, post.jobDescription().length())).append("。");
        }

        // 岗位要求
        if (!requirements.isEmpty()) {
            query.append("岗位要求：");
            for (MatchingRequirementSnapshot req : requirements) {
                String tagName = req.abilityName() != null ? req.abilityName()
                        : tagNameMap.getOrDefault(req.tagId(), "未命名能力");
                query.append(tagName).append("(等级").append(req.minRequiredLevel()).append(") ");
            }
            query.append("。");
        }

        // 员工能力
        if (!empAbilities.isEmpty()) {
            query.append("员工能力：");
            for (MatchingAbilitySnapshot ability : empAbilities) {
                String tagName = ability.abilityName() != null ? ability.abilityName()
                        : tagNameMap.getOrDefault(ability.tagId(), "未命名能力");
                Integer level = ability.level();
                query.append(tagName);
                if (level != null) {
                    query.append("(等级").append(level).append(")");
                }
                query.append(" ");
            }
            query.append("。");
        }

        return query.toString();
    }

    /**
     * 构建标签ID到名称的映射
     */
    private Map<Long, String> buildTagNameMap(List<MatchingRequirementSnapshot> requirements,
                                              List<MatchingAbilitySnapshot> empAbilities) {
        Map<Long, String> tagNameMap = new HashMap<>();
        if (requirements != null) {
            for (MatchingRequirementSnapshot req : requirements) {
                if (req.tagId() != null && req.abilityName() != null) {
                    tagNameMap.putIfAbsent(req.tagId(), req.abilityName());
                }
            }
        }
        if (empAbilities != null) {
            for (MatchingAbilitySnapshot ability : empAbilities) {
                if (ability.tagId() != null && ability.abilityName() != null) {
                    tagNameMap.putIfAbsent(ability.tagId(), ability.abilityName());
                }
            }
        }
        return tagNameMap;
    }

    /**
     * 基于检索上下文计算分数
     * <p>
     * 简化实现：根据检索到的文档数量和相关性估算分数
     * 后续可以接入LLM进行更精确的语义评估
     */
    private BigDecimal calculateScoreFromHits(List<RagRetrievalResult.RagHit> hits,
                                               List<MatchingAbilitySnapshot> empAbilities,
                                               List<MatchingRequirementSnapshot> requirements) {
        // 简化评分逻辑：
        // 1. 检索到的文档越多，说明员工经验与岗位越相关
        // 2. 计算能力覆盖率

        if (requirements.isEmpty()) {
            return BigDecimal.valueOf(50); // 默认中等分数
        }

        // 计算能力覆盖率
        long matchedCount = 0;
        for (MatchingRequirementSnapshot req : requirements) {
            for (MatchingAbilitySnapshot emp : empAbilities) {
                boolean sameTag = emp.tagId() != null && emp.tagId().equals(req.tagId());
                boolean sameName = emp.abilityName() != null && req.abilityName() != null
                        && emp.abilityName().trim().equalsIgnoreCase(req.abilityName().trim());
                if (sameTag || sameName) {
                    if (emp.level() != null && req.minRequiredLevel() != null
                            && emp.level() >= req.minRequiredLevel()) {
                        matchedCount++;
                    }
                    break;
                }
            }
        }

        BigDecimal coverageRate = BigDecimal.valueOf(matchedCount)
                .divide(BigDecimal.valueOf(requirements.size()), 4, RoundingMode.HALF_UP);

        // 基础分 = 覆盖率 * 70 + 上下文质量分 * 30
        BigDecimal baseScore = coverageRate.multiply(BigDecimal.valueOf(70));

        // 上下文质量分：根据检索到的内容长度估算
        BigDecimal averageRelevance = hits.stream()
                .map(hit -> BigDecimal.valueOf(hit.getScore()))
                .map(this::normalizeRelevanceFromBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(hits.size()), 4, RoundingMode.HALF_UP);
        BigDecimal contextQuality = averageRelevance.multiply(BigDecimal.valueOf(30));

        BigDecimal totalScore = baseScore.add(contextQuality);

        // 限制在 0-100 范围
        if (totalScore.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (totalScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }

        return totalScore.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRelevanceFromBigDecimal(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double scoreVal = score.doubleValue();
        double normalized = scoreVal <= 1.0 ? scoreVal * 100.0D : Math.min(scoreVal, 100.0D);
        return BigDecimal.valueOf(normalized)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRelevance(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score) || score <= 0) {
            return BigDecimal.ZERO;
        }
        double normalized = score <= 1.0 ? score * 100.0D : Math.min(score, 100.0D);
        return BigDecimal.valueOf(normalized)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}
