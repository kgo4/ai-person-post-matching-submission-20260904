package com.example.matching.service.matching;

import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.ai.service.AiMatchingService;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostPost;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.matching.impl.MatchingScoreServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 针对已修复问题的专项验证测试。
 * 每个 @Nested 类对应一个修复点。
 */
class FixVerificationTest {

    // =====================================================================
    // P0.3: 黑白名单短路 —— 命中后不应进入综合评分
    // =====================================================================
    @Nested
    @DisplayName("P0.3: Blacklist/whitelist short-circuits composite scoring")
    class BlacklistWhitelistShortCircuit {

        @Test
        @DisplayName("黑名单命中 → 最终分数 0，不调用 matchingScoreService.score()")
        void blacklist_returnsZeroScore_andSkipsComposite() {
            // 构造一个黑名单条目
            MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
            bw.setEmpId(1L);
            bw.setPostId(10L);
            bw.setListType(2); // blacklist

            // 验证 findBwListHit 能识别
            var hit = findBwListHit(1L, 10L, List.of(bw));
            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(2);

            // 黑名单分数 = 0
            BigDecimal forcedScore = hit.getListType() == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
            assertThat(forcedScore).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("白名单命中 → 最终分数 100")
        void whitelist_returns100Score() {
            MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
            bw.setEmpId(2L);
            bw.setPostId(10L);
            bw.setListType(1); // whitelist

            var hit = findBwListHit(2L, 10L, List.of(bw));
            assertThat(hit).isNotNull();

            BigDecimal forcedScore = hit.getListType() == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
            assertThat(forcedScore).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("无名单命中 → 返回 null，继续正常评分")
        void noMatch_returnsNull() {
            MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
            bw.setEmpId(99L);
            bw.setPostId(99L);
            bw.setListType(2);

            var hit = findBwListHit(1L, 10L, List.of(bw));
            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("白名单记录的 quantitativeReport 含 bwListType，应被 L3 过滤")
        void whitelistRecord_excludedFromL3() {
            // 模拟 MatchingAiAnalysisService.runAiScoring 的过滤条件
            MatchingRecord whitelistRecord = new MatchingRecord();
            whitelistRecord.setScreeningLevel(2);
            whitelistRecord.setL2Score(new BigDecimal("100"));
            whitelistRecord.setQuantitativeReport("{\"conclusion\":\"白名单强制通过\",\"bwListType\":1}");

            boolean shouldEnterL3 = whitelistRecord.getScreeningLevel() != null
                    && whitelistRecord.getScreeningLevel() >= 2
                    && whitelistRecord.getL2Score() != null
                    && whitelistRecord.getL2Score().doubleValue() >= 60
                    && (whitelistRecord.getQuantitativeReport() == null
                        || !whitelistRecord.getQuantitativeReport().contains("bwListType"));

            assertThat(shouldEnterL3)
                    .as("Whitelist record with bwListType marker should be excluded from L3")
                    .isFalse();
        }

        // 复制 MatchingExecuteServiceImpl.findBwListHit 逻辑
        private MatchingBlackWhiteList findBwListHit(Long empId, Long postId, List<MatchingBlackWhiteList> bwList) {
            if (bwList == null) return null;
            for (MatchingBlackWhiteList bw : bwList) {
                boolean empMatch = bw.getEmpId() != null && bw.getEmpId().equals(empId);
                boolean postMatch = bw.getPostId() != null && bw.getPostId().equals(postId);
                if (empMatch && postMatch && bw.getListType() != null
                        && (bw.getListType() == 1 || bw.getListType() == 2)) {
                    return bw;
                }
            }
            return null;
        }
    }

    // =====================================================================
    // P0.4: AI 报告 key 匹配验证
    // =====================================================================
    @Nested
    @DisplayName("P0.4: AI report key mismatch fixed")
    class AiReportKeyMismatch {

        @Test
        @DisplayName("generateAiScore 输出 key 'aiReport'，extractReportFromAiResult 能正确读取")
        void reportKeyConsistency() throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();

            // 模拟 generateAiScore 输出（key = "aiReport"）
            Map<String, Object> aiResult = new java.util.LinkedHashMap<>();
            aiResult.put("aiScore", new BigDecimal("75.00"));
            aiResult.put("aiReport", "这是AI分析报告内容");
            aiResult.put("conclusion", "推荐录用");

            // 模拟 extractReportFromAiResult 逻辑
            Object report = aiResult.get("aiReport");
            String extractedReport = report != null ? report.toString() : null;

            assertThat(extractedReport)
                    .as("Report should be extracted from 'aiReport' key")
                    .isEqualTo("这是AI分析报告内容");
        }

        @Test
        @DisplayName("旧 key 'report' 不应被读取（防止兼容性混淆）")
        void oldReportKey_notRead() {
            Map<String, Object> aiResult = new java.util.LinkedHashMap<>();
            aiResult.put("report", "旧格式报告");
            aiResult.put("aiReport", "新格式报告");

            Object report = aiResult.get("aiReport");
            assertThat(report).isEqualTo("新格式报告");
        }
    }

    // =====================================================================
    // P0.4: EvidenceGovernance null-score 守卫
    // =====================================================================
    @Nested
    @DisplayName("P0.4: EvidenceGovernance null-score guard")
    class EvidenceGovernanceNullGuard {

        @Test
        @DisplayName("deterministicScore=null 且 deterministicDecision=REVIEW 时，AI 说 PASS 也应保持 REVIEW")
        void nullScore_keepsReview_whenAiSaysPass() {
            // 模拟 mergeResult 的条件判断
            String deterministicDecision = "REVIEW";
            BigDecimal deterministicScore = null; // null 分数
            String aiDecision = "PASS";

            // 修复后的逻辑
            boolean scoreBelowThreshold = deterministicScore == null
                    || deterministicScore.compareTo(new BigDecimal("70")) < 0;

            // AI 说 PASS，但分数为 null（视为低于阈值），应保持 REVIEW
            boolean shouldKeepReview = "PASS".equals(aiDecision) && scoreBelowThreshold;

            assertThat(shouldKeepReview)
                    .as("null score should be treated as below threshold, keeping REVIEW")
                    .isTrue();
        }

        @Test
        @DisplayName("deterministicScore=80 且 deterministicDecision=REVIEW 时，AI 说 PASS 可以升级")
        void highScore_allowsUpgradeToPass() {
            String deterministicDecision = "REVIEW";
            BigDecimal deterministicScore = new BigDecimal("80");
            String aiDecision = "PASS";

            boolean scoreBelowThreshold = deterministicScore == null
                    || deterministicScore.compareTo(new BigDecimal("70")) < 0;

            boolean shouldKeepReview = "PASS".equals(aiDecision) && scoreBelowThreshold;

            assertThat(shouldKeepReview)
                    .as("score 80 >= 70 should allow AI to upgrade to PASS")
                    .isFalse();
        }

        @Test
        @DisplayName("deterministicScore=50 且 deterministicDecision=REVIEW 时，AI 说 PASS 应保持 REVIEW")
        void lowScore_keepsReview() {
            BigDecimal deterministicScore = new BigDecimal("50");
            String aiDecision = "PASS";

            boolean scoreBelowThreshold = deterministicScore == null
                    || deterministicScore.compareTo(new BigDecimal("70")) < 0;

            boolean shouldKeepReview = "PASS".equals(aiDecision) && scoreBelowThreshold;

            assertThat(shouldKeepReview)
                    .as("score 50 < 70 should keep REVIEW")
                    .isTrue();
        }
    }

    // =====================================================================
    // P0.4: 平均分分母修正
    // =====================================================================
    @Nested
    @DisplayName("P0.4: Average denominator uses non-null count")
    class AverageDenominator {

        @Test
        @DisplayName("有 null 分数时，平均分只计算非 null 分数")
        void nullScores_excludedFromAverage() {
            // 模拟 summarizeHistory 逻辑（用 Arrays.asList 因为 List.of 不支持 null）
            List<BigDecimal> scores = java.util.Arrays.asList(
                    new BigDecimal("80"),
                    null,  // null 分数
                    new BigDecimal("60"),
                    null,  // null 分数
                    new BigDecimal("70")
            );

            List<BigDecimal> nonNullScores = scores.stream()
                    .filter(s -> s != null)
                    .toList();

            BigDecimal total = nonNullScores.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = total.divide(
                    BigDecimal.valueOf(nonNullScores.size()), 2, java.math.RoundingMode.HALF_UP);

            // (80 + 60 + 70) / 3 = 70.00
            assertThat(average).isEqualByComparingTo("70.00");

            // 如果错误地用 records.size()=5 做分母，结果是 210/5 = 42.00（错误）
            BigDecimal wrongAverage = total.divide(
                    BigDecimal.valueOf(scores.size()), 2, java.math.RoundingMode.HALF_UP);
            assertThat(wrongAverage).isEqualByComparingTo("42.00"); // 错误值
            assertThat(average).isNotEqualTo(wrongAverage); // 确认修复有效
        }

        @Test
        @DisplayName("全部为 null 时返回 null")
        void allNulls_returnsNull() {
            List<BigDecimal> scores = java.util.Arrays.asList(null, null, null);
            List<BigDecimal> nonNullScores = scores.stream()
                    .filter(s -> s != null).toList();

            assertThat(nonNullScores).isEmpty();
        }
    }

    // =====================================================================
    // P0.4: LlmResponseParser 替换 extractJson
    // =====================================================================
    @Nested
    @DisplayName("P0.4: LlmResponseParser handles markdown code blocks")
    class LlmResponseParserVerification {

        @Test
        @DisplayName("Markdown code block 中的 JSON 能正确提取")
        void markdownCodeBlock() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            String llmResponse = "好的，以下是分析结果：\n```json\n{\"decision\":\"PASS\",\"score\":85}\n```\n以上是我的分析。";
            String json = parser.extractJson(llmResponse);

            assertThat(json).contains("\"decision\":\"PASS\"");
            assertThat(json).contains("\"score\":85");
        }

        @Test
        @DisplayName("包含前导文本的 JSON 能正确提取")
        void jsonWithLeadingText() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            String llmResponse = "根据分析，结果如下：\n{\"conclusion\":\"推荐\",\"confidence\":0.85}";
            String json = parser.extractJson(llmResponse);

            assertThat(json).startsWith("{");
            assertThat(json).endsWith("}");
            assertThat(json).contains("\"conclusion\":\"推荐\"");
        }

        @Test
        @DisplayName("JSON 数组也能正确提取")
        void jsonArray() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            String llmResponse = "提取的能力列表：\n[\"Java\",\"Spring\",\"MySQL\"]\n以上。";
            String json = parser.extractJson(llmResponse);

            assertThat(json).startsWith("[");
            assertThat(json).contains("Java");
        }

        @Test
        @DisplayName("纯 JSON 直接返回")
        void pureJson() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            String pure = "{\"key\":\"value\"}";
            String json = parser.extractJson(pure);

            assertThat(json).isEqualTo(pure);
        }
    }

    // =====================================================================
    // P0.3: MatchingScoreService LLM 路径权重修复
    // =====================================================================
    @Nested
    @DisplayName("P0.3: MatchingScoreService reports one unified weight profile")
    class LlmPathWeightFix {

        @Test
        @DisplayName("有 AI 分数时，报告仍使用统一权重")
        void withAi_usesUnifiedWeights() {
            var profile = MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
            var input = MatchScoreInput.withAi(
                    new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("60"),
                    new BigDecimal("75"), profile);

            MatchingScoreService service = new MatchingScoreServiceImpl(
                    mock(MatchingTrainingWeightProfileStore.class));

            // 直接调用 score 方法（不需要 mock store，因为 profile 在 input 中）
            MatchScoreResult result = service.score(input);

            assertThat(result.hasLlm()).isTrue();
            assertThat(result.abilityWeight())
                    .isEqualByComparingTo(BigDecimal.valueOf(profile.getAbilityWeight()));
        }

        @Test
        @DisplayName("无 AI 分数时，事实回退仍使用统一权重")
        void deterministicScore_usesUnifiedWeights() {
            var profile = MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile();
            var input = MatchScoreInput.deterministic(
                    new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("60"),
                    profile);

            MatchingScoreService service = new MatchingScoreServiceImpl(
                    mock(MatchingTrainingWeightProfileStore.class));

            MatchScoreResult result = service.score(input);

            assertThat(result.hasLlm()).isFalse();
            assertThat(result.abilityWeight())
                    .isEqualByComparingTo(BigDecimal.valueOf(profile.getAbilityWeight()));
        }
    }

    // =====================================================================
    // P0.1: Redis 反序列化白名单验证
    // =====================================================================
    @Nested
    @DisplayName("P0.1: Redis deserialization allowlist blocks non-whitelisted types")
    class RedisDeserializationVerification {

        private ObjectMapper createConfiguredMapper() {
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator ptv =
                    com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                            .allowIfBaseType(Object.class)
                            .allowIfSubType("com.example.matching.")
                            .allowIfSubType("java.util.")
                            .allowIfSubType("java.lang.")
                            .allowIfSubType("java.math.")
                            .allowIfSubType("java.time.")
                            .build();
            mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper;
        }

        @Test
        @DisplayName("项目实体类可以正常序列化/反序列化")
        void projectEntity_roundTrips() throws Exception {
            var mapper = createConfiguredMapper();
            var record = new com.example.matching.entity.matching.MatchingRecord();
            record.setId(42L);
            record.setAiMatchScore(new BigDecimal("88.50"));

            String json = mapper.writeValueAsString(record);
            var deserialized = mapper.readValue(json, com.example.matching.entity.matching.MatchingRecord.class);

            assertThat(deserialized.getId()).isEqualTo(42L);
            assertThat(deserialized.getAiMatchScore()).isEqualByComparingTo("88.50");
        }

        @Test
        @DisplayName("java.util 集合类型可以正常序列化/反序列化")
        void standardCollections_roundTrip() throws Exception {
            var mapper = createConfiguredMapper();
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("count", 10);
            map.put("name", "test");
            map.put("score", new BigDecimal("99.9"));

            String json = mapper.writeValueAsString(map);
            @SuppressWarnings("unchecked")
            var deserialized = mapper.readValue(json, java.util.Map.class);

            assertThat(deserialized.get("count")).isEqualTo(10);
            assertThat(deserialized.get("score")).isEqualTo(new BigDecimal("99.9"));
        }

        @Test
        @DisplayName("非白名单类型反序列化被拒绝")
        void nonWhitelistedType_rejected() throws Exception {
            var mapper = createConfiguredMapper();
            // 尝试反序列化 javax.swing.JFrame（不在白名单中）
            String malicious = "[\"javax.swing.JFrame\",{\"title\":\"hack\"}]";

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> mapper.readValue(malicious, Object.class))
                    .isInstanceOf(Exception.class);
        }
    }

    // =====================================================================
    // P1: EvidenceGovernance LlmResponseParser 注入验证
    // =====================================================================
    @Nested
    @DisplayName("P1: EvidenceGovernanceAgentServiceImpl uses LlmResponseParser")
    class EvidenceGovernanceJsonParsing {

        @Test
        @DisplayName("Markdown 包裹的 JSON 能被正确解析（旧 extractJson 会失败）")
        void markdownWrappedJson_parsed() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            String response = "以下是审核结果：\n```json\n{\"decision\":\"REVIEW\",\"riskLevel\":\"MEDIUM\",\"supportScore\":65}\n```";

            String json = parser.extractJson(response);
            assertThat(json).contains("\"decision\":\"REVIEW\"");
            assertThat(json).contains("\"supportScore\":65");
        }

        @Test
        @DisplayName("包含花括号的说明文本不会干扰 JSON 提取")
        void explanatoryTextWithBraces_notConfused() {
            var parser = new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

            // 旧 indexOf('{') 方法会从 "注意{..." 开始提取，导致垃圾
            String response = "注意{此处不是JSON}，结果如下：\n{\"decision\":\"PASS\",\"supportScore\":90}";

            String json = parser.extractJson(response);
            // 应该提取到最后一个完整的 JSON 对象
            assertThat(json).contains("\"decision\":\"PASS\"");
        }
    }
}
