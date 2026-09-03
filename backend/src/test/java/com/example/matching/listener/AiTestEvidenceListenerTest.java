package com.example.matching.listener;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.impl.AiTestAbilityLevelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTestEvidenceListenerTest {

    private AiTestEvidenceListener listener;
    private PersonAbilityClaimGroupMapper claimGroupMapper;

    @BeforeEach
    void setUp() {
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        listener = new AiTestEvidenceListener(
                mock(EmpAiTestMapper.class),
                mock(AbilityEvidenceCollectionService.class),
                mock(CapabilityAssessmentWorkflowService.class),
                mock(CapabilityStageLifecycleEventPublisher.class),
                new AiTestAbilityLevelResolver(new ObjectMapper()),
                claimGroupMapper,
                new ObjectMapper());
    }

    private PersonAbilityClaimGroup group(long id, Long tagId, String name) {
        PersonAbilityClaimGroup g = new PersonAbilityClaimGroup();
        g.setId(id);
        g.setCanonicalTagId(tagId);
        g.setNormalizedAbilityName(name);
        return g;
    }

    @SuppressWarnings("unchecked")
    private List<PersonAbilityClaim> invokeBuildTestClaims(EmpAiTest test) {
        return (List<PersonAbilityClaim>) ReflectionTestUtils.invokeMethod(listener, "buildTestClaims", test);
    }

    @Test
    void buildTestClaims_resolvesPerAbilityWithQuestionLevelSourceRef() {
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group(100L, 7L, "Java")));

        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setWorkflowId(1L);
        test.setAbilityTagName("Java 综合能力测试");
        test.setMasteryLevel(3);
        test.setScore(new BigDecimal("80"));
        test.setQuestions("[{\"id\":1,\"tagId\":7,\"score\":10},{\"id\":2,\"tagId\":7,\"score\":10}]");
        test.setAiEvaluation("{\"score\":80,\"masteryLevel\":3,"
                + "\"questionResults\":[{\"questionIndex\":0,\"isCorrect\":true,\"score\":10},"
                + "{\"questionIndex\":1,\"isCorrect\":true,\"score\":8}]}");

        List<PersonAbilityClaim> claims = invokeBuildTestClaims(test);

        assertThat(claims).hasSize(1);
        PersonAbilityClaim claim = claims.get(0);
        assertThat(claim.getAbilityName()).isEqualTo("Java");
        assertThat(claim.getClaimedLevel()).isEqualTo(3);
        assertThat(claim.getTagId()).isEqualTo(7L);
        assertThat(claim.getSourceRefsJson())
                .contains("source:AI_TEST:9:Q1")
                .contains("source:AI_TEST:9:Q2");
    }

    @Test
    void buildTestClaims_fallsBackToOverallWhenNoCoverage() {
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setWorkflowId(1L);
        test.setAbilityTagName("Java 综合能力测试");
        test.setMasteryLevel(2);
        test.setScore(new BigDecimal("60"));
        test.setQuestions("[]");
        test.setAiEvaluation("{}");

        List<PersonAbilityClaim> claims = invokeBuildTestClaims(test);

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getAbilityName()).isEqualTo("Java 综合能力测试");
        assertThat(claims.get(0).getClaimedLevel()).isEqualTo(2);
        assertThat(claims.get(0).getSourceRefsJson()).contains("source:AI_TEST:9");
    }

    @Test
    void buildTestClaims_unknownTag_savedAsUnclassifiedObservation() {
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group(100L, 7L, "Java")));

        EmpAiTest test = new EmpAiTest();
        test.setId(9L);
        test.setWorkflowId(1L);
        test.setAbilityTagName("Java 综合能力测试");
        test.setMasteryLevel(3);
        test.setScore(new BigDecimal("80"));
        test.setQuestions("[{\"id\":1,\"tagId\":7,\"score\":10},{\"id\":2,\"tagId\":99,\"score\":10}]");
        test.setAiEvaluation("{\"questionResults\":[{\"questionIndex\":0,\"score\":10},{\"questionIndex\":1,\"score\":10}]}");

        List<PersonAbilityClaim> claims = invokeBuildTestClaims(test);

        // Java 正常归并 + 未知 tagId=99 保存为未归类观察
        assertThat(claims).hasSize(2);
        PersonAbilityClaim unclassified = claims.stream()
                .filter(c -> "UNCLASSIFIED_OBSERVATION".equals(c.getEvidenceStatus()))
                .findFirst().orElseThrow();
        assertThat(unclassified.getTagId()).isEqualTo(99L);
        assertThat(unclassified.getNormalizedAbilityName()).contains("未归类能力");
        // 正常归并的 Java claim 不受影响
        assertThat(claims).anyMatch(c -> "Java".equals(c.getAbilityName()));
    }
}
