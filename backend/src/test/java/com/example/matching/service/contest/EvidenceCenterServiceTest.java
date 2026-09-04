package com.example.matching.service.contest;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.service.contest.impl.EvidenceBackfillService;
import com.example.matching.service.contest.impl.EvidenceCenterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

/**
 * 证据中心服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class EvidenceCenterServiceTest {

    @Mock
    private ContestEvidenceItemMapper evidenceItemMapper;
    @Mock
    private PostQueryPort postQueryPort;
    @Mock
    private TalentQueryPort talentQueryPort;
    @Mock
    private com.example.matching.port.matching.MatchingQueryPort matchingQueryPort;
    @Mock
    private TagQueryPort tagQueryPort;
    @Mock
    private com.example.matching.service.harness.AiTrustHarnessService aiTrustHarnessService;

    @InjectMocks
    private EvidenceCenterServiceImpl evidenceCenterService;

    private EvidenceCreateDTO createDTO;

    @BeforeEach
    void setUp() {
        EvidenceBackfillService backfillService = new EvidenceBackfillService(
                evidenceItemMapper, postQueryPort, talentQueryPort, matchingQueryPort, tagQueryPort,
                aiTrustHarnessService);
        org.springframework.test.util.ReflectionTestUtils.setField(evidenceCenterService, "backfillService", backfillService);
        createDTO = new EvidenceCreateDTO();
        createDTO.setSourceType("JD_IMPORT");
        createDTO.setSourceRefId(1L);
        createDTO.setSourceTitle("测试JD");
        createDTO.setSourceText("Java开发工程师...");
        createDTO.setTargetType("POST_ABILITY_MODEL");
        createDTO.setTargetRefId(100L);
        createDTO.setAbilityName("Java");
        createDTO.setConfidenceScore(new BigDecimal("85.5"));
        createDTO.setCredibilityScore(new BigDecimal("90.0"));
    }

    @Test
    @DisplayName("创建证据：成功")
    void createEvidence_success() {
        doReturn(1).when(evidenceItemMapper).insert(any(ContestEvidenceItem.class));

        ContestEvidenceItem result = evidenceCenterService.createEvidence(createDTO);

        assertNotNull(result);
        assertEquals("JD_IMPORT", result.getSourceType());
        assertEquals("PENDING", result.getEvidenceStatus());
        assertNotNull(result.getEvidenceCode());
        assertTrue(result.getEvidenceCode().startsWith("EVD_"));
        assertEquals(0, new BigDecimal("85.50").compareTo(result.getConfidenceScore()));
        verify(evidenceItemMapper, times(1)).insert(any(ContestEvidenceItem.class));
    }

    @Test
    @DisplayName("创建证据：分数超过100被限制为100")
    void createEvidence_scoreClamped() {
        createDTO.setConfidenceScore(new BigDecimal("150"));
        createDTO.setCredibilityScore(new BigDecimal("-10"));
        doReturn(1).when(evidenceItemMapper).insert(any(ContestEvidenceItem.class));

        ContestEvidenceItem result = evidenceCenterService.createEvidence(createDTO);

        assertEquals(0, new BigDecimal("100.00").compareTo(result.getConfidenceScore()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getCredibilityScore()));
    }

    @Test
    @DisplayName("审核证据：VERIFIED成功")
    void reviewEvidence_verified() {
        ContestEvidenceItem existing = new ContestEvidenceItem();
        existing.setId(1L);
        existing.setEvidenceStatus("PENDING");
        doReturn(existing).when(evidenceItemMapper).selectById(1L);
        doReturn(1).when(evidenceItemMapper).updateById(any(ContestEvidenceItem.class));

        EvidenceReviewDTO reviewDTO = new EvidenceReviewDTO();
        reviewDTO.setEvidenceStatus("VERIFIED");
        reviewDTO.setReviewComment("审核通过");

        assertDoesNotThrow(() -> evidenceCenterService.reviewEvidence(1L, reviewDTO, 100L));
        assertEquals("VERIFIED", existing.getEvidenceStatus());
        assertEquals("审核通过", existing.getReviewComment());
        assertEquals(100L, existing.getReviewedBy());
        assertNotNull(existing.getReviewedTime());
    }

    @Test
    @DisplayName("审核证据：REJECTED成功")
    void reviewEvidence_rejected() {
        ContestEvidenceItem existing = new ContestEvidenceItem();
        existing.setId(2L);
        existing.setEvidenceStatus("PENDING");
        doReturn(existing).when(evidenceItemMapper).selectById(2L);
        doReturn(1).when(evidenceItemMapper).updateById(any(ContestEvidenceItem.class));

        EvidenceReviewDTO reviewDTO = new EvidenceReviewDTO();
        reviewDTO.setEvidenceStatus("REJECTED");
        reviewDTO.setReviewComment("信息不准确");

        assertDoesNotThrow(() -> evidenceCenterService.reviewEvidence(2L, reviewDTO, 100L));
        assertEquals("REJECTED", existing.getEvidenceStatus());
    }

    @Test
    @DisplayName("审核证据：无效状态抛异常")
    void reviewEvidence_invalidStatus() {
        ContestEvidenceItem existing = new ContestEvidenceItem();
        existing.setId(1L);
        existing.setEvidenceStatus("PENDING");
        doReturn(existing).when(evidenceItemMapper).selectById(1L);

        EvidenceReviewDTO reviewDTO = new EvidenceReviewDTO();
        reviewDTO.setEvidenceStatus("INVALID");

        assertThrows(BusinessException.class,
                () -> evidenceCenterService.reviewEvidence(1L, reviewDTO, 100L));
    }

    @Test
    @DisplayName("审核证据：不存在的ID抛异常")
    void reviewEvidence_notFound() {
        doReturn(null).when(evidenceItemMapper).selectById(999L);

        EvidenceReviewDTO reviewDTO = new EvidenceReviewDTO();
        reviewDTO.setEvidenceStatus("VERIFIED");

        assertThrows(BusinessException.class,
                () -> evidenceCenterService.reviewEvidence(999L, reviewDTO, 100L));
    }

    @Test
    @DisplayName("回填员工能力：原系统能力来源进入证据中心")
    void backfillEvidence_empAbilityPromotesOriginalSource() {
        EmployeeAbilityDTO ability = new EmployeeAbilityDTO(
                11L, 1L, 2L, 4, "MANUAL", new BigDecimal("0.80"), LocalDate.of(2026, 6, 1), "项目交付记录");

        EmployeeDTO employee = new EmployeeDTO(1L, "张三", "E001", null, null, null, null, null);

        TagDTO tag = new TagDTO(2L, "Java", "JAVA", null, null, null, null, null, null, null, null, null);

        doReturn(List.of(ability)).when(talentQueryPort).listActiveAbilities(any(Integer.class));
        doReturn(employee).when(talentQueryPort).getEmployeeById(1L);
        doReturn(tag).when(tagQueryPort).getTagById(2L);
        doReturn(0L).when(evidenceItemMapper).selectCount(any());
        doReturn(List.of(passDecision())).when(aiTrustHarnessService).verifyBatch(any());
        ArgumentCaptor<ContestEvidenceItem> captor = ArgumentCaptor.forClass(ContestEvidenceItem.class);
        doReturn(1).when(evidenceItemMapper).insert(captor.capture());

        int created = evidenceCenterService.backfillEvidence("EMP_ABILITY", 10);

        assertEquals(1, created);
        ContestEvidenceItem item = captor.getValue();
        assertEquals("EMP_ABILITY", item.getSourceType());
        assertEquals(11L, item.getSourceRefId());
        assertEquals("EMP_ABILITY", item.getTargetType());
        assertEquals(11L, item.getTargetRefId());
        assertEquals(2L, item.getTagId());
        assertEquals("Java", item.getAbilityName());
        assertEquals("VERIFIED", item.getEvidenceStatus());
        assertTrue(item.getReviewComment().contains("[harness]"));
        assertTrue(item.getSourceText().contains("张三"));
        assertTrue(item.getSourceText().contains("MANUAL"));
        assertTrue(item.getSourceText().contains("项目交付记录"));
    }

    @Test
    @DisplayName("回填岗位能力模型：原系统岗位能力来源进入证据中心")
    void backfillEvidence_postAbilityModelPromotesOriginalSource() {
        PostAbilityDTO model = new PostAbilityDTO(
                21L, 3L, 2L, 4, new BigDecimal("75"), 1, 1, "v20260601120000", "核心后端能力", null);

        PostDTO post = new PostDTO(3L, "Java开发工程师", "P003", null, null, null, null);

        TagDTO tag = new TagDTO(2L, "Java", "JAVA", null, null, null, null, null, null, null, null, null);

        doReturn(List.of(model)).when(postQueryPort).listActivePostAbilityModels(any(Integer.class));
        doReturn(post).when(postQueryPort).getPostById(3L);
        doReturn(tag).when(tagQueryPort).getTagById(2L);
        doReturn(0L).when(evidenceItemMapper).selectCount(any());
        doReturn(List.of(passDecision())).when(aiTrustHarnessService).verifyBatch(any());
        ArgumentCaptor<ContestEvidenceItem> captor = ArgumentCaptor.forClass(ContestEvidenceItem.class);
        doReturn(1).when(evidenceItemMapper).insert(captor.capture());

        int created = evidenceCenterService.backfillEvidence("POST_ABILITY_MODEL", 10);

        assertEquals(1, created);
        ContestEvidenceItem item = captor.getValue();
        assertEquals("POST_ABILITY_MODEL", item.getSourceType());
        assertEquals(21L, item.getSourceRefId());
        assertEquals("POST_ABILITY_MODEL", item.getTargetType());
        assertEquals(21L, item.getTargetRefId());
        assertEquals(2L, item.getTagId());
        assertEquals("Java", item.getAbilityName());
        assertEquals("VERIFIED", item.getEvidenceStatus());
        assertTrue(item.getReviewComment().contains("[harness]"));
        assertTrue(item.getSourceText().contains("Java开发工程师"));
        assertTrue(item.getSourceText().contains("核心后端能力"));
    }

    @Test
    @DisplayName("回填岗位能力模型：缺失能力名称时跳过，避免生成能力#null")
    void backfillEvidence_postAbilityModel_withoutTagIsSkipped() {
        PostAbilityDTO model = new PostAbilityDTO(
                22L, 3L, null, 4, new BigDecimal("75"), 1, 1,
                "v20260601120000", null, null);

        PostDTO post = new PostDTO(3L, "Java开发工程师", "P003", null, null, null, null);

        doReturn(List.of(model)).when(postQueryPort).listActivePostAbilityModels(any(Integer.class));
        doReturn(post).when(postQueryPort).getPostById(3L);
        doReturn(0L).when(evidenceItemMapper).selectCount(any());
        int created = evidenceCenterService.backfillEvidence("POST_ABILITY_MODEL", 10);

        assertEquals(0, created);
        verifyNoInteractions(tagQueryPort);
        verify(evidenceItemMapper, org.mockito.Mockito.never())
                .insert(org.mockito.ArgumentMatchers.<ContestEvidenceItem>any());
    }

    @Test
    @DisplayName("employee evidence chain groups abilities and source evidence")
    @SuppressWarnings("unchecked")
    void getEmployeeEvidenceChain_groupsAbilitiesAndEvidence() {
        EmployeeDTO employee = new EmployeeDTO(1L, "Alice", "E001", null, null, null, null, null);

        EmployeeAbilityDTO ability = new EmployeeAbilityDTO(
                11L, 1L, 2L, 4, "RESUME_PARSE", new BigDecimal("0.80"), null, null);

        TagDTO tag = new TagDTO(2L, "Java", "JAVA", null, null, null, null, null, null, null, null, null);

        ContestEvidenceItem evidence = new ContestEvidenceItem();
        evidence.setId(101L);
        evidence.setSourceType("RESUME_PARSE");
        evidence.setSourceTitle("resume.pdf");
        evidence.setTargetType("EMP_ABILITY");
        evidence.setTargetRefId(11L);
        evidence.setAbilityName("Java");
        evidence.setConfidenceScore(new BigDecimal("88"));
        evidence.setCredibilityScore(new BigDecimal("92"));
        evidence.setEvidenceStatus("VERIFIED");

        doReturn(employee).when(talentQueryPort).getEmployeeById(1L);
        doReturn(List.of(ability)).when(talentQueryPort).listAbilitiesByEmpId(1L);
        doReturn(tag).when(tagQueryPort).getTagById(2L);
        doReturn(List.of(evidence)).when(evidenceItemMapper).selectList(any());

        Map<String, Object> chain = evidenceCenterService.getEmployeeEvidenceChain(1L);

        assertEquals("EMPLOYEE", chain.get("subjectType"));
        assertEquals("Alice", chain.get("subjectName"));
        assertEquals(1, chain.get("abilityCount"));
        assertEquals(1, chain.get("evidenceCount"));
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) chain.get("abilities");
        assertEquals("Java", abilities.get(0).get("abilityName"));
        assertEquals(4, abilities.get(0).get("level"));
        assertEquals(1, abilities.get(0).get("evidenceCount"));
        assertEquals(new BigDecimal("88.00"), abilities.get(0).get("averageConfidence"));
        assertEquals(new BigDecimal("92.00"), abilities.get(0).get("averageCredibility"));
    }

    @Test
    @DisplayName("post evidence chain groups requirements and source evidence")
    @SuppressWarnings("unchecked")
    void getPostEvidenceChain_groupsRequirementsAndEvidence() {
        PostDTO post = new PostDTO(3L, "Backend Engineer", "P003", null, null, null, null);

        PostAbilityDTO model = new PostAbilityDTO(
                21L, 3L, 2L, 4, new BigDecimal("75"), 1, 1, null, null, null);

        TagDTO tag = new TagDTO(2L, "Java", "JAVA", null, null, null, null, null, null, null, null, null);

        ContestEvidenceItem evidence = new ContestEvidenceItem();
        evidence.setId(201L);
        evidence.setSourceType("POST_ABILITY_MODEL");
        evidence.setSourceTitle("JD backend source");
        evidence.setTargetType("POST_ABILITY_MODEL");
        evidence.setTargetRefId(21L);
        evidence.setAbilityName("Java");
        evidence.setConfidenceScore(new BigDecimal("80"));
        evidence.setCredibilityScore(new BigDecimal("85"));
        evidence.setEvidenceStatus("VERIFIED");

        doReturn(post).when(postQueryPort).getPostById(3L);
        doReturn(List.of(model)).when(postQueryPort).listRequirementsByPostId(3L);
        doReturn(tag).when(tagQueryPort).getTagById(2L);
        doReturn(List.of(evidence)).when(evidenceItemMapper).selectList(any());

        Map<String, Object> chain = evidenceCenterService.getPostEvidenceChain(3L);

        assertEquals("POST", chain.get("subjectType"));
        assertEquals("Backend Engineer", chain.get("subjectName"));
        assertEquals(1, chain.get("abilityCount"));
        assertEquals(1, chain.get("evidenceCount"));
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) chain.get("abilities");
        assertEquals("Java", abilities.get(0).get("abilityName"));
        assertEquals(4, abilities.get(0).get("level"));
        assertEquals(new BigDecimal("75"), abilities.get(0).get("weight"));
        assertEquals(true, abilities.get(0).get("required"));
        assertEquals(true, abilities.get(0).get("core"));
        assertEquals(1, abilities.get(0).get("evidenceCount"));
    }

    /** 构造一个 harness PASS 决策，用于回填自动审核测试 */
    private AiHarnessDecisionDTO passDecision() {
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision("PASS");
        decision.setRiskLevel("LOW");
        decision.setSupportScore(new BigDecimal("95"));
        decision.setReasons(List.of("matched formal tag", "original evidence text present"));
        return decision;
    }

}
