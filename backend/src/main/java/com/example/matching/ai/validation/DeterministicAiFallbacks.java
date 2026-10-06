package com.example.matching.ai.validation;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 确定性 AI 降级兜底
 * <p>
 * 所有 fallback 只能基于当前输入计算，不得再次调用 LLM、MQ、数据库或网络。
 * 同步接口返回明确的 degraded 结构；异步任务由调用方记为 FAILED 并记录 AI_OUTPUT_INVALID。
 */
@Slf4j
public final class DeterministicAiFallbacks {

    private DeterministicAiFallbacks() {
    }

    /** 空 JSON 对象 */
    public static final String EMPTY_JSON_OBJECT = "EMPTY_JSON_OBJECT";
    /** 空 JSON 数组 */
    public static final String EMPTY_JSON_ARRAY = "EMPTY_JSON_ARRAY";
    /** AI 测试题目（确定性模板题） */
    public static final String AI_TEST_QUESTIONS = "AI_TEST_QUESTIONS";
    /** AI 测试批阅 */
    public static final String AI_TEST_EVALUATION = "AI_TEST_EVALUATION";
    /** 匹配分析 */
    public static final String MATCHING_ANALYSIS = "MATCHING_ANALYSIS";

    /**
     * 获取受控降级函数
     *
     * @param fallbackName 受控 fallback 名称，见本类常量
     */
    public static Supplier<String> get(String fallbackName) {
        return switch (fallbackName) {
            case EMPTY_JSON_OBJECT -> DeterministicAiFallbacks::emptyJsonObject;
            case EMPTY_JSON_ARRAY -> DeterministicAiFallbacks::emptyJsonArray;
            case AI_TEST_QUESTIONS -> DeterministicAiFallbacks::aiTestQuestions;
            case AI_TEST_EVALUATION -> DeterministicAiFallbacks::aiTestEvaluation;
            case MATCHING_ANALYSIS -> DeterministicAiFallbacks::matchingAnalysis;
            default -> throw new IllegalArgumentException("未知的受控 fallback 名称: " + fallbackName);
        };
    }

    private static String emptyJsonObject() {
        return "{}";
    }

    private static String emptyJsonArray() {
        return "[]";
    }

    /**
     * AI 测试题目兜底：基于输入计算出的确定性模板题（不入库前由校验器复核）
     */
    private static String aiTestQuestions() {
        return """
            [
                {
                    "id": 1,
                    "type": "choice_single",
                    "question": "请描述您对该技能的掌握程度",
                    "options": ["入门", "熟悉", "掌握", "精通", "专家"],
                    "answer": "",
                    "referenceAnswer": "",
                    "score": 10
                },
                {
                    "id": 2,
                    "type": "text",
                    "question": "请举例说明您在实际工作中应用该技能的经验",
                    "options": [],
                    "answer": "",
                    "referenceAnswer": "",
                    "score": 10
                },
                {
                    "id": 3,
                    "type": "case",
                    "question": "请描述一个您主导完成的与该项能力相关的任务，并说明您的具体贡献",
                    "options": [],
                    "answer": "",
                    "referenceAnswer": "",
                    "score": 10
                }
            ]
            """;
    }

    /**
     * AI 测试批阅兜底：明确 degraded 标识，供人工复核
     */
    private static String aiTestEvaluation() {
        return """
            {
                "score": 60,
                "masteryLevel": 2,
                "analysisReport": "AI批阅服务不可用，已给出默认评分。建议人工复核。",
                "details": [],
                "degraded": true
            }
            """;
    }

    /**
     * 匹配分析兜底：明确的 degraded 结构
     */
    private static String matchingAnalysis() {
        return "{\"aiScore\":null,\"aiReport\":\"AI服务不可用，已跳过AI评分\",\"degraded\":true}";
    }

    /**
     * 员工能力提取兜底：仅基于请求输入（已有能力）计算，不访问数据库
     *
     * @param request 提取请求（含 empId、sourceType、sourceRefId、existingAbilities）
     * @return 确定性降级结果
     */
    public static PersonAbilityExtractionResult employeeAbilityClaims(PersonAbilityExtractRequest request) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setEmpId(request.getEmpId());
        result.setSourceType(request.getSourceType());
        result.setSourceRefId(request.getSourceRefId());
        result.setFallbackUsed(true);
        result.setDurationMs(0L);

        List<PersonAbilityClaim> claims = new ArrayList<>();
        if (request.getExistingAbilities() != null) {
            for (PersonAbilityExtractRequest.ExistingAbility existing : request.getExistingAbilities()) {
                PersonAbilityClaim claim = new PersonAbilityClaim();
                claim.setEmpId(request.getEmpId());
                claim.setSourceType(request.getSourceType());
                claim.setSourceRefId(request.getSourceRefId());
                claim.setAbilityName(existing.getAbilityName());
                claim.setNormalizedAbilityName(existing.getAbilityName());
                claim.setAbilityTagId(existing.getAbilityTagId());
                claim.setMasteryLevel(existing.getCurrentLevel());
                claim.setConfidenceScore(BigDecimal.valueOf(50));
                claim.setEvidenceText(request.getEvidenceText() != null && !request.getEvidenceText().isBlank()
                        ? request.getEvidenceText() : "降级方案：基于现有能力数据");
                claim.setExtractReason("确定性降级：基于输入已有能力");
                claim.setSourceRefs(request.getSourceRefs() != null ? request.getSourceRefs() : List.of());
                claims.add(claim);
            }
        }
        result.setClaims(claims);
        result.setSummary("确定性降级方案：基于现有数据生成能力声明");
        return result;
    }
}
