package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.ai.validation.EmployeeAbilityExtractionValidator;
import com.example.matching.common.exception.AiServiceException;
import com.google.gson.JsonSyntaxException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.matching.application.agent.EmployeeAbilitySnapshot;

import java.math.BigDecimal;
import java.io.EOFException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class EmployeeAbilityAgentServiceImplTest {

    private static AgentMemoryContextService stubMemoryContextService() {
        AgentMemoryContextService svc = mock(AgentMemoryContextService.class);
        when(svc.resolveRules(any(), any()))
                .thenReturn(new AgentMemoryContextService.ContextRules(
                        Collections.emptyList(), Collections.emptyList(), "", ""));
        return svc;
    }

    @Test
    void extractAbilitiesCanonicalizesLegacyProjectSourceBeforeBuildingTheResult() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(false);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties,
                mock(AgentContextPackageService.class),
                mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("PMS_ANALYSIS");
        request.setSourceRefId(8L);
        request.setSourceText("项目复盘素材");

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getSourceType()).isEqualTo("AI_PROJECT");
        assertThat(request.getSourceType()).isEqualTo("AI_PROJECT");
    }

    @Test
    void fallbackUsesExistingAbilitiesFromRequestWhenAgentDisabled() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(false);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java experience");
        request.setSourceRefs(List.of("source:RESUME_PARSE:8"));
        PersonAbilityExtractRequest.ExistingAbility existing = new PersonAbilityExtractRequest.ExistingAbility();
        existing.setAbilityName("Java");
        existing.setCurrentLevel(3);
        request.setExistingAbilities(List.of(existing));

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getClaims()).singleElement()
                .extracting(claim -> claim.getAbilityName())
                .isEqualTo("Java");
    }

    @Test
    void extractContextJsonHasRootSourceTextField() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenAnswer(invocation -> {
            String contextJson = invocation.getArgument(0);
            Map<String, Object> context = mapper.readValue(contextJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            assertThat(context).containsKey("sourceText");
            assertThat(context).doesNotContainKey("request");
            return new PersonAbilityExtractionResult();
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java backend resume text");

        service.extractAbilities(request);
    }

    @Test
    void resumeParsePropagatesTransientAiTransportFailureForRetry() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), new ObjectMapper(),
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenThrow(new RuntimeException(new InterruptedIOException("timeout")));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java backend resume text");

        assertThatThrownBy(() -> service.extractAbilities(request))
                .isInstanceOf(AiServiceException.class)
                .extracting(error -> ((AiServiceException) error).isRetryable())
                .isEqualTo(true);
    }

    @Test
    void resumeParsePropagatesIncompleteStructuredOutputForRetry() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), new ObjectMapper(),
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenThrow(
                new JsonSyntaxException("incomplete structured output", new EOFException("End of input")));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java backend resume text");

        assertThatThrownBy(() -> service.extractAbilities(request))
                .isInstanceOf(AiServiceException.class)
                .extracting(error -> ((AiServiceException) error).isRetryable())
                .isEqualTo(true);
    }

    @Test
    void persistentMalformedJsonOnLongResumePropagatesRetryableWithoutInfiniteSplitLoop() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), new ObjectMapper(),
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        AtomicInteger calls = new AtomicInteger();
        // 模拟模型持续返回非 JSON 文本（如纯字符串），与本次报错的 JsonSyntaxException 一致
        when(aiService.extractAbilities(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            throw new JsonSyntaxException("Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $");
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        // 超过 MIN_RECOVERY_CHUNK_CHARS(512) 的长文本，确保整文首次失败会进入分块恢复路径
        request.setSourceText("Java backend resume text. ".repeat(40));

        assertThatThrownBy(() -> service.extractAbilities(request))
                .isInstanceOf(AiServiceException.class)
                .extracting(error -> ((AiServiceException) error).isRetryable())
                .isEqualTo(true);
        // 修复前：分块递归到底上抛的 AiServiceException 会被主方法再次分块，形成无限循环；
        // 修复后：异常被排除在分块恢复之外，直接走任务重试，调用次数有限（整文 1 次 + 首轮分块若干次）。
        assertThat(calls.get()).isLessThan(50);
    }

    @Test
    void incompleteWholeResumeIsSplitAndAllSuccessfulSegmentsAreMerged() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());

        AtomicInteger calls = new AtomicInteger();
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new JsonSyntaxException("incomplete structured output", new EOFException("End of input"));
            }
            Map<String, Object> context = mapper.readValue((String) invocation.getArgument(0),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            String sourceText = (String) context.get("sourceText");
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setAbilityName("Ability-" + calls.get());
            claim.setMasteryLevel(3);
            claim.setEvidenceText(sourceText);
            PersonAbilityExtractionResult response = new PersonAbilityExtractionResult();
            response.setClaims(List.of(claim));
            return response;
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        String sourceText = "A".repeat(900) + "\n\n" + "B".repeat(900);
        request.setSourceText(sourceText);

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).hasSize(2);
        String recoveredText = String.join("", result.getClaims().stream()
                .map(PersonAbilityClaim::getEvidenceText)
                .toList());
        assertThat(recoveredText.replaceAll("\\s+", ""))
                .isEqualTo(sourceText.replaceAll("\\s+", ""));
    }

    @Test
    void chunkedExtractionKeepsGroundedClaimsWhenOneClaimIsUngrounded() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());

        AtomicInteger calls = new AtomicInteger();
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new JsonSyntaxException("incomplete structured output", new EOFException("End of input"));
            }
            Map<String, Object> context = mapper.readValue((String) invocation.getArgument(0),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            String sourceText = (String) context.get("sourceText");

            PersonAbilityClaim grounded = new PersonAbilityClaim();
            grounded.setAbilityName("Grounded-" + calls.get());
            grounded.setMasteryLevel(3);
            grounded.setEvidenceText(sourceText);

            PersonAbilityClaim ungrounded = new PersonAbilityClaim();
            ungrounded.setAbilityName("Ungrounded-" + calls.get());
            ungrounded.setMasteryLevel(3);
            ungrounded.setEvidenceText("not present in the resume");

            PersonAbilityExtractionResult response = new PersonAbilityExtractionResult();
            response.setClaims(List.of(grounded, ungrounded));
            return response;
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("A".repeat(900) + "\n\n" + "B".repeat(900));

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getClaims()).hasSize(2)
                .allSatisfy(claim -> assertThat(claim.getAbilityName()).startsWith("Grounded-"));
        assertThat(result.getFailedChunkCount()).isEqualTo(2);
    }

    @Test
    void modelJsonBasicInfoDeserializesAndIsPreserved() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String modelJson = """
                {
                  "claims": [{
                    "abilityName": "Java",
                    "masteryLevel": 4,
                    "confidenceScore": 85,
                    "evidenceText": "5 years Java",
                    "sourceRefs": ["source:RESUME_PARSE:8"]
                  }],
                  "basicInfo": {
                    "degree": "本科",
                    "yearsOfWork": 5,
                    "currentTitle": "Java工程师"
                  },
                  "sourceRefs": ["source:RESUME_PARSE:8"]
                }
                """;

        PersonAbilityExtractionResult result = mapper.readValue(modelJson, PersonAbilityExtractionResult.class);

        assertThat(result.getBasicInfo()).isNotNull();
        assertThat(result.getBasicInfo().getDegree()).isEqualTo("本科");
        assertThat(result.getBasicInfo().getYearsOfWork()).isEqualTo(5);
        assertThat(result.getBasicInfo().getCurrentTitle()).isEqualTo("Java工程师");
        assertThat(result.getClaims()).hasSize(1);
    }

    @Test
    void extractAbilitiesPropagatesBasicInfoFromAiResultToServiceResult() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());

        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        aiResult.setSummary("ok");
        PersonAbilityExtractionResult.BasicInfo basicInfo = new PersonAbilityExtractionResult.BasicInfo();
        basicInfo.setDegree("硕士");
        basicInfo.setYearsOfWork(8);
        basicInfo.setCurrentTitle("高级工程师");
        aiResult.setBasicInfo(basicInfo);
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceText("Java backend resume text");
        aiResult.setClaims(List.of(claim));

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        when(aiService.extractAbilities(any())).thenReturn(aiResult);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java backend resume text");

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getBasicInfo()).isNotNull();
        assertThat(result.getBasicInfo().getDegree()).isEqualTo("硕士");
        assertThat(result.getBasicInfo().getYearsOfWork()).isEqualTo(8);
        assertThat(result.getBasicInfo().getCurrentTitle()).isEqualTo("高级工程师");
        assertThat(result.isFallbackUsed()).isFalse();
    }


    @Test
    void modelReportedRefsReplacedByServerStandardRef() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceText("负责Java后端开发");
        // 模型自报引用在受控集合内（校验通过），但最终由服务端标准引用统一覆盖
        claim.setSourceRefs(List.of("source:RESUME_PARSE:8"));
        aiResult.setClaims(List.of(claim));
        when(aiService.extractAbilities(any())).thenReturn(aiResult);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("负责Java后端开发");

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getSourceRefs()).containsExactly("source:RESUME_PARSE:8");
    }

    @Test
    void missingSourceRefEntersReviewRetry() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), new ObjectMapper(),
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceText("简历文本");
        // sourceRefId 与 sourceRefs 均缺失

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getSummary()).contains("REVIEW");
    }

    @Test
    void invalidModelSourceRefsReplacedByServerStandardRefAndEvidencePreserved() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceText("负责Java后端开发");
        // 模拟模型返回占位/无效引用
        claim.setSourceRefs(List.of("ai:ABILITY_TAG:42"));
        aiResult.setClaims(List.of(claim));
        when(aiService.extractAbilities(any())).thenReturn(aiResult);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("负责Java后端开发");

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        // 无效引用被服务端标准引用替换
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getSourceRefs()).containsExactly("source:RESUME_PARSE:8");
        // 原始证据保留
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("负责Java后端开发");
    }

    @Test
    void evidenceSlicedFromSourceWhenOffsetsProvided() throws Exception {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceStart(3);
        claim.setEvidenceEnd(9); // 原文[3,9) = "负责Java"
        aiResult.setClaims(List.of(claim));
        when(aiService.extractAbilities(any())).thenReturn(aiResult);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("abc负责Java后端开发");

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("负责Java");
    }

    @Test
    void ocrResumeKeepsGroundedClaimsWhenAnotherClaimCannotBeGrounded() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), new ObjectMapper(),
                mock(AgentRunConfidencePolicy.class), stubMemoryContextService(),
                mock(AgentMemoryRuleEnforcer.class), new EmployeeAbilityExtractionValidator());
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);

        PersonAbilityClaim grounded = new PersonAbilityClaim();
        grounded.setAbilityName("Java");
        grounded.setMasteryLevel(4);
        grounded.setEvidenceText("负责Java后端开发");
        PersonAbilityClaim ungrounded = new PersonAbilityClaim();
        ungrounded.setAbilityName("Kubernetes");
        ungrounded.setMasteryLevel(3);
        ungrounded.setEvidenceText("管理生产环境Kubernetes集群");
        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        aiResult.setClaims(List.of(grounded, ungrounded));
        when(aiService.extractAbilities(any())).thenReturn(aiResult);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("负责Java后端开发");
        request.setOcrDerived(true);

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).singleElement()
                .extracting(PersonAbilityClaim::getAbilityName)
                .isEqualTo("Java");
    }

    @Test
    void longResumeSkillBeyondChunkBoundaryStillExtracted() throws Exception {
        // 验证分块提取时，唯一有效技能在 chunk 边界之后仍能被提取
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        ObjectMapper mapper = new ObjectMapper();
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(AgentContextPackageService.class), mapper,
                mock(AgentRunConfidencePolicy.class),
                stubMemoryContextService(), mock(AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());

        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        // 模拟两个分块调用：第一个分块返回空，第二个分块返回有效技能
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(aiService.extractAbilities(any())).thenAnswer(invocation -> {
            int callNum = calls.getAndIncrement();
            String contextJson = invocation.getArgument(0);
            Map<String, Object> context = mapper.readValue(contextJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String sourceText = (String) context.get("sourceText");
            if (callNum == 0) {
                // 第一个分块（前12000字符）：无技能
                return new PersonAbilityExtractionResult();
            }
            // 第二个分块：包含有效技能
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setAbilityName("Kubernetes");
            claim.setMasteryLevel(4);
            claim.setEvidenceText(sourceText);
            PersonAbilityExtractionResult response = new PersonAbilityExtractionResult();
            response.setClaims(List.of(claim));
            return response;
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);

        // 构造长文本：前12000字符为填充内容，之后包含唯一技能
        String padding = "A".repeat(12000);
        String skillText = "Expert in Kubernetes cluster management and orchestration.";
        String sourceText = padding + "\n\n" + skillText;

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText(sourceText);

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Kubernetes");
    }
}
