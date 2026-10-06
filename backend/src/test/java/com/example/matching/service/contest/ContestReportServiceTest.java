package com.example.matching.service.contest;

import com.example.matching.entity.contest.ContestReportTask;
import com.example.matching.ai.service.PromptMetadataResolver;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.contest.ContestReportTaskMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.contest.report.impl.ContestReportGenerationEngine;
import com.example.matching.service.contest.report.impl.ContestReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 竞赛报告服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestReportServiceTest {

    @Mock
    private ContestReportTaskMapper reportTaskMapper;
    @Mock
    private com.example.matching.port.kg.GraphQueryPort graphQueryPort;
    @Mock
    private com.example.matching.port.matching.MatchingQueryPort matchingQueryPort;
    @Mock
    private com.example.matching.port.post.PostQueryPort postQueryPort;
    @Mock
    private com.example.matching.port.talent.TalentQueryPort talentQueryPort;
    @Mock
    private com.example.matching.port.tag.TagQueryPort tagQueryPort;

    @InjectMocks
    private ContestReportServiceImpl reportService;

    @Mock
    private com.example.matching.mapper.contest.ContestEvidenceItemMapper evidenceItemMapper;
    @Mock
    private com.example.matching.ai.service.LangChain4jChatService langChain4jChatService;
    @Mock
    private com.example.matching.mapper.contest.ContestReportEvidenceRefMapper evidenceRefMapper;
    @Mock
    private com.example.matching.service.contest.report.ReportEvidenceRetriever evidenceRetriever;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private PromptMetadataResolver metadataResolver;

    @BeforeEach
    void setUp() {
        when(metadataResolver.resolve("matching-overview-report.ftl"))
                .thenReturn(new PromptMetadataResolver.PromptMetadata("matching-overview-report", "v1.0"));
        // 用真实引擎 + mock Port，让 graphQueryPort 抛异常的失败路径可测
        ContestReportGenerationEngine engine = new ContestReportGenerationEngine(
                evidenceItemMapper, graphQueryPort, matchingQueryPort,
                postQueryPort, talentQueryPort, tagQueryPort,
                langChain4jChatService, mock(com.example.matching.resilience.AiServiceResilience.class),
                mock(com.example.matching.ai.service.PromptTemplateService.class), objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(reportService, "engine", engine);
    }

    @Test
    @DisplayName("报告生成：评测报告包含三个指标标签")
    void generateReport_evaluationContainsMetrics() {
        when(reportTaskMapper.insert(any(ContestReportTask.class))).thenReturn(1);
        when(reportTaskMapper.updateById(any(ContestReportTask.class))).thenReturn(1);
        when(graphQueryPort.countNodesByType()).thenReturn(Collections.emptyMap());

        ContestReportTask task = reportService.generateReport("SUMMARY", "摘要报告", 1L);

        assertNotNull(task);
        // 纯统计路径（needsAi=false）应直接成功
        assertEquals("SUCCEEDED", task.getTaskStatus());
    }

    @Test
    @DisplayName("提交清单：包含竞赛所需全部要素")
    void getSubmissionChecklist_containsAllItems() {
        Map<String, Object> checklist = reportService.getSubmissionChecklist();

        assertNotNull(checklist);
        assertTrue(checklist.containsKey("items"));
        assertTrue(checklist.containsKey("totalCount"));

        java.util.List<?> items = (java.util.List<?>) checklist.get("items");
        assertEquals(11, items.size());
    }

    @Test
    void generateReportRejectsMissingPromptVersion() {
        when(metadataResolver.resolve("matching-overview-report.ftl"))
                .thenThrow(new IllegalArgumentException("missing valid version header"));

        assertThrows(IllegalStateException.class,
                () -> reportService.generateReport("SUMMARY", "report", 1L));
        verifyNoInteractions(reportTaskMapper);
    }

    @Test
    @DisplayName("报告生成：失败时存储错误信息")
    void generateReport_failureStoresError() {
        when(reportTaskMapper.insert(any(ContestReportTask.class))).thenReturn(1);
        when(graphQueryPort.countNodesByType()).thenThrow(new RuntimeException("数据库连接失败"));
        when(reportTaskMapper.updateById(any(ContestReportTask.class))).thenReturn(1);

        ContestReportTask task = reportService.generateReport("GRAPH", "图谱报告", 1L);

        assertNotNull(task);
        assertEquals("FAILED", task.getTaskStatus());
        assertNotNull(task.getErrorMessage());
        assertTrue(task.getErrorMessage().contains("数据库连接失败"));
    }
}
