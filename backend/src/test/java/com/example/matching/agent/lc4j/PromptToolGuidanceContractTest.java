package com.example.matching.agent.lc4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提示词工具指导契约：
 * 暴露工具的模板必须包含 "## Tool use"/"## TOOL USAGE" 章节，
 * 说明每个可用工具的用途、证据权威，以及"已验证证据优先于 RAG 引用"的规则。
 */
@DisplayName("提示词工具指导契约")
class PromptToolGuidanceContractTest {

    private static final List<String> ALL_PROMPTS = List.of(
            "ai-test-generate-system.txt",
            "ai-test-evaluate-system.txt",
            "employee-ability-system.txt",
            "employee-ability-extract-system.txt",
            "evidence-governance-system.txt",
            "post-ability-system.txt",
            "post-ability-extract-system.txt",
            "matching-analysis-system.txt",
            "learning-path-system.txt",
            "interview-plan-system.txt",
            "interview-followup-system.txt",
            "interview-observation-system.txt",
            "interview-report-system.txt",
            "interview-answer-quality-system.txt",
            "pms-ability-analysis-system.txt");

    @Test
    @DisplayName("暴露工具的提示词必须包含工具使用指导章节")
    void toolExposingPromptsMustIncludeToolUsageSection() throws IOException {
        for (String promptName : ALL_PROMPTS) {
            String prompt = readPrompt(promptName);
            if (!exposesTools(prompt)) {
                continue;
            }
            boolean hasToolSection = prompt.contains("## Tool use")
                    || prompt.contains("## TOOL USAGE")
                    || prompt.contains("## TOOLUSE");
            assertThat(hasToolSection)
                    .as("%s 暴露工具但缺少工具使用章节", promptName)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("引用证据工具必须区分已验证证据与 RAG 参考")
    void evidenceToolsMustSeparateVerifiedFromRag() throws IOException {
        for (String promptName : ALL_PROMPTS) {
            String prompt = readPrompt(promptName);
            if (!prompt.contains("EvidenceContextTool")) {
                continue;
            }
            assertThat(prompt)
                    .as("%s 必须说明 ragReferences 不可独立支持结论", promptName)
                    .contains("ragReferences");
        }
    }

    @Test
    @DisplayName("AI测试评分提示词的等级字段必须与结构化 DTO 一致")
    void aiTestEvaluationPromptUsesMasteryLevelForTheFinalLevel() throws IOException {
        String prompt = readPrompt("ai-test-evaluate-system.txt");

        assertThat(prompt).contains("\"masteryLevel\": \"integer 1-5 or null\"");
        assertThat(prompt).doesNotContain("\"suggestedLevel\"");
    }

    @Test
    @DisplayName("AI测试模型只能返回业务评分状态")
    void aiTestEvaluationPromptDoesNotExposeInfrastructureFailureStatuses() throws IOException {
        String prompt = readPrompt("ai-test-evaluate-system.txt");

        assertThat(prompt).contains("## Status And Levels");
        assertThat(prompt).contains("Return `VALID` only");
        assertThat(prompt).contains("Return `INSUFFICIENT_EVIDENCE`");
        assertThat(prompt).contains("Do not output `UNAVAILABLE` or `INVALID_OUTPUT`");
    }

    private boolean exposesTools(String prompt) {
        return prompt.contains("Tool") || prompt.contains("工具");
    }

    private String readPrompt(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("ai/prompt/" + name)) {
            if (is == null) {
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
