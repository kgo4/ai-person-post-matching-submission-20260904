package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.PostEvolutionTaskCreateDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.evolution.impl.PostEvolutionChangeComparator;
import com.example.matching.service.evolution.impl.PostEvolutionDashboardService;
import com.example.matching.service.evolution.impl.PostEvolutionScoringService;
import com.example.matching.service.evolution.impl.PostEvolutionServiceImpl;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 岗位演化服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class PostEvolutionServiceTest {

    @Mock
    private PostEvolutionChangeComparator changeComparator;

    @Mock
    private PostEvolutionDashboardService dashboardService;

    @Mock
    private PostEvolutionScoringService scoringService;

    @Mock
    private PostEvolutionTaskMapper taskMapper;

    @Mock
    private PostEvolutionChangeItemMapper changeItemMapper;

    @Mock
    private PostAbilityModelMapper postAbilityModelMapper;

    @Mock
    private AbilityTagMapper abilityTagMapper;

    @Mock
    private PostCapabilityGenerationService postCapabilityGenerationService;

    @Mock
    private PostDataCleaningService postDataCleaningService;

    @Mock
    private AbilityEvidenceIngestionService abilityEvidenceIngestionService;

    @Mock
    private CapabilityClosureService capabilityClosureService;

    @Mock
    private AiTrustHarnessService aiTrustHarnessService;

    @Mock
    private MarketJdDataMapper marketJdDataMapper;

    @Mock
    private PostEvolutionEvidenceMapper evidenceMapper;

    @Mock
    private MatchingFeedbackDatasetMapper feedbackDatasetMapper;

    @Mock
    private MatchingRecordMapper matchingRecordMapper;

    @Mock
    private EvolutionEvidenceCollector evidenceCollector;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.example.matching.service.governance.GovernedAdmissionService governedAdmissionService;

    @Mock
    private com.example.matching.mapper.governance.GovernanceAdmissionMapper admissionMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @InjectMocks
    private PostEvolutionServiceImpl postEvolutionService;

    private PostEvolutionTask task;
    private PostAbilityModel existingAbility;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.TransactionStatus txStatus =
                mock(org.springframework.transaction.TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(txStatus);

        task = new PostEvolutionTask();
        task.setId(1L);
        task.setPostId(100L);
        task.setTaskStatus("PENDING");
        task.setNewJdText("新的JD文本");

        existingAbility = new PostAbilityModel();
        existingAbility.setId(1L);
        existingAbility.setPostId(100L);
        existingAbility.setTagId(1L);
        existingAbility.setMinRequiredLevel(3);
        existingAbility.setWeight(new BigDecimal("20"));
        existingAbility.setIsCore(1);
        existingAbility.setIsDeleted(0);

        // Mock harness service to return PASS by default
        AiHarnessDecisionDTO passDecision = new AiHarnessDecisionDTO();
        passDecision.setDecision("PASS");
        lenient().when(aiTrustHarnessService.verify(any())).thenReturn(passDecision);

        // Mock governed admission to return PASS admission
        com.example.matching.dto.governance.GovernanceAdmission admission =
                new com.example.matching.dto.governance.GovernanceAdmission();
        admission.setId(1L);
        admission.setFinalDecision(com.example.matching.dto.governance.GovernanceGrant.PASS.name());
        lenient().when(governedAdmissionService.admitPostAbility(any())).thenReturn(admission);
        com.example.matching.entity.governance.GovernanceAdmissionRecord record =
                new com.example.matching.entity.governance.GovernanceAdmissionRecord();
        record.setId(1L);
        record.setFinalDecision("PASS");
        lenient().when(admissionMapper.selectById(1L)).thenReturn(record);

        // Stub evidence collector to return real evidence objects
        PostEvolutionEvidence jdEvidence = new PostEvolutionEvidence();
        jdEvidence.setId(1L);
        jdEvidence.setTaskId(1L);
        jdEvidence.setEvidenceText("JD文本摘要");
        lenient().when(evidenceCollector.createJdEvidence(any(), any())).thenReturn(jdEvidence);
        lenient().when(evidenceCollector.createMarketJdEvidence(any(), any())).thenReturn(new PostEvolutionEvidence());
        lenient().when(evidenceCollector.createFeedbackEvidence(any(), any())).thenReturn(new PostEvolutionEvidence());
        lenient().when(evidenceCollector.createMatchingGapEvidence(any(), any())).thenReturn(new PostEvolutionEvidence());

        PostCleaningResult cleaningResult = new PostCleaningResult();
        cleaningResult.setCleanedPostName("岗位 #100");
        cleaningResult.setCleanedText("新的JD文本");
        cleaningResult.setCleaningRecordId(1L);
        cleaningResult.setBlocked(false);
        lenient().when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleaningResult);
    }

    @Test
    @DisplayName("创建任务成功")
    void createTask_success() {
        PostEvolutionTaskCreateDTO dto = new PostEvolutionTaskCreateDTO();
        dto.setPostId(100L);
        dto.setTaskName("测试任务");
        dto.setNewJdText("新JD");

        doReturn(1).when(taskMapper).insert(any(PostEvolutionTask.class));

        PostEvolutionTask result = postEvolutionService.createTask(dto, 1L);

        assertNotNull(result);
        assertEquals(100L, result.getPostId());
        assertEquals("PENDING", result.getTaskStatus());
        assertNotNull(result.getTaskCode());
    }

    @Test
    @DisplayName("已有活动任务时拒绝创建同岗位演化任务")
    void createTask_rejectsPostWithActiveTask() {
        PostEvolutionTaskCreateDTO dto = new PostEvolutionTaskCreateDTO();
        dto.setPostId(100L);
        dto.setTaskName("重复任务");
        dto.setNewJdText("新JD");
        when(taskMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> postEvolutionService.createTask(dto, 1L));
        verify(taskMapper, never()).insert(any(PostEvolutionTask.class));
    }

    @Test
    @DisplayName("新增能力创建ADDED变更项")
    void analyzeTask_newAbilityCreatesAdded() throws Exception {
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        JdAbilityItemDTO newItem = new JdAbilityItemDTO();
        newItem.setSuggestedName("新能力");
        newItem.setMinRequiredLevel(3);
        newItem.setWeight(new BigDecimal("20"));
        newItem.setIsCore(1);
        when(postCapabilityGenerationService.analyzePostText(any(), any(), any(), any(), any())).thenReturn(Collections.singletonList(newItem));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doReturn(1).when(changeItemMapper).insert(any(PostEvolutionChangeItem.class));

        PostEvolutionChangeItem added = new PostEvolutionChangeItem();
        added.setTaskId(1L);
        added.setChangeType("ADDED");
        added.setAbilityName("新能力");
        when(changeComparator.compareAbilities(any(), any(), any())).thenReturn(List.of(added));
        PostEvolutionScoringService.EvolutionScore score = new PostEvolutionScoringService.EvolutionScore();
        score.setFinalScore(new BigDecimal("50"));
        when(scoringService.calculateEvolutionScore(any(), any(), any())).thenReturn(score);
        when(scoringService.findRelatedEvidence(any(), any())).thenReturn(Collections.emptyList());

        PostEvolutionTask result = postEvolutionService.analyzeTask(1L);

        assertEquals("WAIT_CONFIRM", result.getTaskStatus());
        verify(changeItemMapper, times(1)).insert(any(PostEvolutionChangeItem.class));
    }

    @Test
    @DisplayName("PostEvolutionServiceImpl 仍以 5 参方法 + POST_EVOLUTION_TASK 来源调用能力提取")
    void analyzeTask_stillUsesFiveArgPostEvolutionTaskSource() throws Exception {
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        JdAbilityItemDTO newItem = new JdAbilityItemDTO();
        newItem.setSuggestedName("新能力");
        newItem.setMinRequiredLevel(3);
        newItem.setWeight(new BigDecimal("20"));
        newItem.setIsCore(1);
        when(postCapabilityGenerationService.analyzePostText(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(newItem));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doReturn(1).when(changeItemMapper).insert(any(PostEvolutionChangeItem.class));

        PostEvolutionChangeItem added = new PostEvolutionChangeItem();
        added.setTaskId(1L);
        added.setChangeType("ADDED");
        added.setAbilityName("新能力");
        when(changeComparator.compareAbilities(any(), any(), any())).thenReturn(List.of(added));
        PostEvolutionScoringService.EvolutionScore score = new PostEvolutionScoringService.EvolutionScore();
        score.setFinalScore(new BigDecimal("50"));
        when(scoringService.calculateEvolutionScore(any(), any(), any())).thenReturn(score);
        when(scoringService.findRelatedEvidence(any(), any())).thenReturn(Collections.emptyList());

        postEvolutionService.analyzeTask(1L);

        // 回归：调用方必须继续使用 5 参 analyzePostText（含 Harness 行为），
        // 而不是被替换为市场专用的 analyzeMarketJdText 或 3 参版本
        ArgumentCaptor<String> sourceTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> refIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(postCapabilityGenerationService)
                .analyzePostText(any(), any(), sourceTypeCaptor.capture(), refIdCaptor.capture(), any());
        assertEquals("POST_EVOLUTION_TASK", sourceTypeCaptor.getValue());
        assertEquals(1L, refIdCaptor.getValue());
        verify(postCapabilityGenerationService, never()).analyzeMarketJdText(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI和Harness完成后使用独立短事务写回演化结果")
    void analyzeTask_persistsResultsInSeparateShortTransaction() throws Exception {
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());

        JdAbilityItemDTO newItem = new JdAbilityItemDTO();
        newItem.setSuggestedName("新能力");
        newItem.setMinRequiredLevel(3);
        newItem.setWeight(new BigDecimal("20"));
        newItem.setIsCore(1);
        when(postCapabilityGenerationService.analyzePostText(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(newItem));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PostEvolutionChangeItem added = new PostEvolutionChangeItem();
        added.setTaskId(1L);
        added.setChangeType("ADDED");
        added.setAbilityName("新能力");
        when(changeComparator.compareAbilities(any(), any(), any())).thenReturn(List.of(added));
        PostEvolutionScoringService.EvolutionScore score = new PostEvolutionScoringService.EvolutionScore();
        score.setFinalScore(new BigDecimal("50"));
        when(scoringService.calculateEvolutionScore(any(), any(), any())).thenReturn(score);
        when(scoringService.findRelatedEvidence(any(), any())).thenReturn(Collections.emptyList());

        postEvolutionService.analyzeTask(1L);

        verify(transactionManager, times(2)).getTransaction(any());
        verify(changeItemMapper).insert(added);
        verify(taskMapper, atLeast(2)).updateById(any(PostEvolutionTask.class));
    }

    @Test
    @DisplayName("removed ability uses tag name instead of raw tag id")
    void analyzeTask_removedAbilityUsesTagName() throws Exception {
        existingAbility.setTagId(12L);
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.singletonList(existingAbility));
        when(postCapabilityGenerationService.analyzePostText(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doReturn(1).when(changeItemMapper).insert(any(PostEvolutionChangeItem.class));

        AbilityTag tag = new AbilityTag();
        tag.setId(12L);
        tag.setTagName("Spring Boot");
        lenient().when(abilityTagMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(tag));

        PostEvolutionChangeItem removed = new PostEvolutionChangeItem();
        removed.setTaskId(1L);
        removed.setTagId(12L);
        removed.setAbilityName("Spring Boot");
        removed.setChangeType("REMOVED");
        when(changeComparator.compareAbilities(any(), any(), any())).thenReturn(List.of(removed));
        PostEvolutionScoringService.EvolutionScore score = new PostEvolutionScoringService.EvolutionScore();
        score.setFinalScore(new BigDecimal("50"));
        when(scoringService.calculateEvolutionScore(any(), any(), any())).thenReturn(score);
        when(scoringService.findRelatedEvidence(any(), any())).thenReturn(Collections.emptyList());

        postEvolutionService.analyzeTask(1L);

        ArgumentCaptor<PostEvolutionChangeItem> captor = ArgumentCaptor.forClass(PostEvolutionChangeItem.class);
        verify(changeItemMapper).insert(captor.capture());
        PostEvolutionChangeItem item = captor.getValue();
        assertEquals("REMOVED", item.getChangeType());
        assertEquals(12L, item.getTagId());
        assertEquals("Spring Boot", item.getAbilityName());
    }

    @Test
    @DisplayName("审核变更项成功")
    void reviewChangeItem_success() {
        PostEvolutionChangeItem item = new PostEvolutionChangeItem();
        item.setId(1L);
        item.setTaskId(1L);
        item.setConfirmStatus("PENDING");
        doReturn(item).when(changeItemMapper).selectById(1L);
        doReturn(1).when(changeItemMapper).updateById(any(PostEvolutionChangeItem.class));

        PostEvolutionReviewDTO dto = new PostEvolutionReviewDTO();
        dto.setConfirmStatus("APPROVED");
        dto.setReviewComment("审核通过");

        assertDoesNotThrow(() -> postEvolutionService.reviewChangeItem(1L, 1L, dto));
        assertEquals("APPROVED", item.getConfirmStatus());
    }

    @Test
    @DisplayName("应用仅应用已审核通过的变更")
    void applyApprovedChanges_onlyAppliesApproved() {
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));

        PostEvolutionChangeItem approvedItem = new PostEvolutionChangeItem();
        approvedItem.setId(1L);
        approvedItem.setTaskId(1L);
        approvedItem.setChangeType("ADDED");
        approvedItem.setTagId(2L);
        approvedItem.setNewLevel(3);
        approvedItem.setNewWeight(new BigDecimal("100"));
        approvedItem.setNewIsCore(0);
        approvedItem.setConfirmStatus("APPROVED");

        when(changeItemMapper.selectList(any())).thenReturn(Collections.singletonList(approvedItem));
        doReturn(1).when(postAbilityModelMapper).insert(any(PostAbilityModel.class));

        int applied = postEvolutionService.applyApprovedChanges(1L);

        assertEquals(1, applied);
        assertEquals("APPLIED", task.getTaskStatus());
        verify(capabilityClosureService).onPostEvolutionApplied(1L);
        verify(eventPublisher, times(1)).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("无 PASS 准入的已审批项不能把任务标记为已应用")
    void applyApprovedChanges_marksTaskPartiallyAppliedWhenAdmissionBlocksItem() {
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));

        PostEvolutionChangeItem approvedItem = new PostEvolutionChangeItem();
        approvedItem.setId(1L);
        approvedItem.setTaskId(1L);
        approvedItem.setChangeType("ADDED");
        approvedItem.setTagId(2L);
        approvedItem.setNewWeight(new BigDecimal("100"));
        approvedItem.setConfirmStatus("APPROVED");
        when(changeItemMapper.selectList(any())).thenReturn(Collections.singletonList(approvedItem));

        com.example.matching.entity.governance.GovernanceAdmissionRecord blocked =
                new com.example.matching.entity.governance.GovernanceAdmissionRecord();
        blocked.setId(1L);
        blocked.setFinalDecision("REVIEW");
        when(admissionMapper.selectById(any())).thenReturn(blocked);

        assertEquals(0, postEvolutionService.applyApprovedChanges(1L));

        assertEquals("PARTIALLY_APPLIED", task.getTaskStatus());
        assertTrue(task.getErrorMessage().contains("1"));
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("无标签的新增项不能写入正式岗位能力模型")
    void applyApprovedChanges_skipsAddedItemWithoutTagId() {
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);
        doReturn(1).when(taskMapper).updateById(any(PostEvolutionTask.class));

        PostEvolutionChangeItem approvedItem = new PostEvolutionChangeItem();
        approvedItem.setId(1L);
        approvedItem.setTaskId(1L);
        approvedItem.setChangeType("ADDED");
        approvedItem.setConfirmStatus("APPROVED");
        approvedItem.setGovernanceAdmissionId(1L);
        when(changeItemMapper.selectList(any())).thenReturn(Collections.singletonList(approvedItem));

        assertEquals(0, postEvolutionService.applyApprovedChanges(1L));

        assertEquals("PARTIALLY_APPLIED", task.getTaskStatus());
        assertTrue(task.getErrorMessage().contains("缺少能力标签"));
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("没有已审核通过的变更项时应用失败并抛出业务异常")
    void applyApprovedChanges_allRejectedThrowsBusinessException() {
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);

        when(changeItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class, () -> postEvolutionService.applyApprovedChanges(1L));
        verify(capabilityClosureService, never()).onPostEvolutionApplied(anyLong());
        verify(eventPublisher, never()).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("状态不允许分析时抛异常")
    void analyzeTask_invalidStatusThrows() {
        task.setTaskStatus("APPLIED");
        doReturn(task).when(taskMapper).selectById(1L);

        assertThrows(BusinessException.class, () -> postEvolutionService.analyzeTask(1L));
    }

    @Test
    @DisplayName("新增能力没有显式权重时应用被拒绝")
    void applyApprovedChanges_addedAbilityWithoutWeightIsRejected() {
        // M8：newWeight == null 的新增能力无法应用（不允许默认 weight=0）
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);

        PostEvolutionChangeItem approvedItem = new PostEvolutionChangeItem();
        approvedItem.setId(1L);
        approvedItem.setTaskId(1L);
        approvedItem.setChangeType("ADDED");
        approvedItem.setTagId(2L);
        approvedItem.setAbilityName("Kubernetes");
        approvedItem.setConfirmStatus("APPROVED");
        approvedItem.setNewWeight(null);
        when(changeItemMapper.selectList(any())).thenReturn(Collections.singletonList(approvedItem));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postEvolutionService.applyApprovedChanges(1L));
        assertTrue(ex.getMessage().contains("显式设置权重"));
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("演化等级超出 1-5 范围时应用被拒绝")
    void applyApprovedChanges_levelOutOfRangeIsRejected() {
        // M3：演化等级限制为业务范围 1-5
        task.setTaskStatus("WAIT_CONFIRM");
        doReturn(task).when(taskMapper).selectById(1L);

        PostEvolutionChangeItem approvedItem = new PostEvolutionChangeItem();
        approvedItem.setId(1L);
        approvedItem.setTaskId(1L);
        approvedItem.setChangeType("UPDATED_LEVEL");
        approvedItem.setTagId(2L);
        approvedItem.setNewLevel(6);
        approvedItem.setNewWeight(new BigDecimal("100"));
        approvedItem.setConfirmStatus("APPROVED");
        approvedItem.setGovernanceAdmissionId(1L);
        when(changeItemMapper.selectList(any())).thenReturn(Collections.singletonList(approvedItem));
        PostAbilityModel existingModel = new PostAbilityModel();
        existingModel.setPostId(1L);
        existingModel.setTagId(2L);
        existingModel.setWeight(new BigDecimal("100"));
        existingModel.setMinRequiredLevel(3);
        existingModel.setIsRequired(1);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.singletonList(existingModel));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postEvolutionService.applyApprovedChanges(1L));
        assertTrue(ex.getMessage().contains("1-5"));
        verify(postAbilityModelMapper, never()).insert(any(PostAbilityModel.class));
    }
}
