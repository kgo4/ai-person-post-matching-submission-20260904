package com.example.matching.service.contest.report.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptMetadataResolver;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.contest.ContestReportEvidenceRef;
import com.example.matching.entity.contest.ContestReportTask;
import com.example.matching.entity.contest.ContestReportTypeEnum;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.contest.ContestReportEvidenceRefMapper;
import com.example.matching.mapper.contest.ContestReportTaskMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.contest.report.ContestReportService;
import com.example.matching.service.contest.report.ReportEvidenceRetriever;
import com.example.matching.service.rag.RagScenarioEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 竞赛报告服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestReportServiceImpl implements ContestReportService {

    private final ContestReportTaskMapper reportTaskMapper;
    private final ContestReportEvidenceRefMapper evidenceRefMapper;
    private final ReportEvidenceRetriever evidenceRetriever;
    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final LangChain4jChatService langChain4jChatService;
    private final ContestReportGenerationEngine engine;
    private final ObjectMapper objectMapper;
    private final PromptMetadataResolver metadataResolver;

    private static final DateTimeFormatter TASK_CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContestReportTask generateReport(String reportType, String title, Long createdBy) {
        ContestReportTypeEnum typeEnum = ContestReportTypeEnum.findByType(reportType);
        if (typeEnum == null) {
            throw new IllegalArgumentException("不支持的报告类型: " + reportType);
        }

        ContestReportTask task = new ContestReportTask();
        task.setTaskCode(generateTaskCode());
        task.setReportType(reportType);
        task.setReportTitle(title != null && !title.isEmpty() ? title : typeEnum.getTitle());
        task.setTaskStatus("RUNNING");
        task.setCreatedBy(createdBy);
        task.setCreatedTime(LocalDateTime.now());
        task.setPromptVersion(resolveReportPromptVersion());
        reportTaskMapper.insert(task);

        long startTime = System.currentTimeMillis();
        try {
            // 1. 检索证据（如果需要 RAG）
            ReportEvidenceRetriever.ReportEvidenceSnapshot evidenceSnapshot = null;
            if (typeEnum.isNeedsRag()) {
                evidenceSnapshot = evidenceRetriever.retrieve(reportType, title);
                task.setEvidenceSnapshotJson(evidenceSnapshot.getSnapshotJson());
                task.setRagScenario(RagScenarioEnum.REPORT_GENERATION.name());
                task.setRagHitCount(evidenceSnapshot.getRagHitCount());
            }

            // 2. 生成报告内容
            String markdown;
            String json;
            Map<String, Object> dataModel = null;
            if (typeEnum.isNeedsAi()) {
                dataModel = engine.aggregateMatchingStats();
                String ragContext = evidenceSnapshot != null ? evidenceSnapshot.getRagContextText() : "";
                dataModel.put("ragContext", ragContext);
                Map<String, Object> aiDataModel = dataModel;

                String prompt = engine.renderPromptTemplate("matching-overview-report", aiDataModel);
                String aiResponse = langChain4jChatService.chat("matching-overview-report", prompt,
                        () -> engine.buildFallbackMarkdown(aiDataModel));
                markdown = aiResponse != null ? aiResponse : engine.buildFallbackMarkdown(aiDataModel);
                task.setGenerationMode(evidenceSnapshot != null && evidenceSnapshot.getRagHitCount() > 0 ? "AI_RAG" : "AI");
                task.setModelName("deepseek");
            } else {
                markdown = engine.generateStatReport(reportType);
                task.setGenerationMode("STAT_ONLY");
            }

            // 3. 构建结构化 JSON
            json = engine.buildStructuredJson(reportType, markdown);

            // 4. 校验
            String validationStatus = engine.validateReport(markdown, json);
            task.setValidationStatus(validationStatus);

            // 5. 保存结果
            task.setReportMarkdown(markdown);
            task.setReportJson(json);
            task.setWordCount(markdown.length());
            task.setDurationMs(System.currentTimeMillis() - startTime);
            task.setTaskStatus("SUCCEEDED");
            task.setFinishedTime(LocalDateTime.now());
            reportTaskMapper.updateById(task);

            // 6. 保存证据引用
            if (evidenceSnapshot != null && !evidenceSnapshot.getVerifiedEvidence().isEmpty()) {
                saveEvidenceRefs(task.getId(), evidenceSnapshot.getVerifiedEvidence());
            }

            log.info("报告生成成功：code={}, type={}, mode={}, duration={}ms",
                    task.getTaskCode(), reportType, task.getGenerationMode(), task.getDurationMs());
        } catch (Exception e) {
            log.error("报告生成失败：code={}, type={}", task.getTaskCode(), reportType, e);
            task.setTaskStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setDurationMs(System.currentTimeMillis() - startTime);
            task.setFinishedTime(LocalDateTime.now());
            reportTaskMapper.updateById(task);
        }

        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContestReportTask retryReport(Long id, Long createdBy) {
        ContestReportTask original = reportTaskMapper.selectById(id);
        if (original == null) {
            throw new IllegalArgumentException("报告任务不存在: " + id);
        }
        if (!"FAILED".equals(original.getTaskStatus())) {
            throw new IllegalStateException("只能重试失败的报告任务");
        }
        return generateReport(original.getReportType(), original.getReportTitle(), createdBy);
    }

    @Override
    public Map<String, Object> getReportTaskPage(String reportType, Integer page, Integer size) {
        int effectivePage = page != null ? page : 1;
        int effectiveSize = size != null ? size : 10;

        Page<ContestReportTask> pageResult = reportTaskMapper.selectPage(
                new Page<>(effectivePage, effectiveSize),
                Wrappers.<ContestReportTask>lambdaQuery()
                        .eq(reportType != null && !reportType.isEmpty(), ContestReportTask::getReportType, reportType)
                        .orderByDesc(ContestReportTask::getCreatedTime));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return result;
    }

    @Override
    public ContestReportTask getReportTaskById(Long id) {
        return reportTaskMapper.selectById(id);
    }

    @Override
    public List<Map<String, Object>> getReportEvidenceRefs(Long taskId) {
        List<ContestReportEvidenceRef> refs = evidenceRefMapper.selectList(
                new LambdaQueryWrapper<ContestReportEvidenceRef>()
                        .eq(ContestReportEvidenceRef::getReportTaskId, taskId)
                        .orderByDesc(ContestReportEvidenceRef::getCredibilityScore));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ContestReportEvidenceRef ref : refs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ref.getEvidenceId());
            item.put("evidenceCode", ref.getEvidenceCode());
            item.put("sourceType", ref.getSourceType());
            item.put("abilityName", ref.getAbilityName());
            item.put("confidenceScore", ref.getConfidenceScore());
            item.put("credibilityScore", ref.getCredibilityScore());
            // 补充完整证据信息
            ContestEvidenceItem evidence = evidenceItemMapper.selectById(ref.getEvidenceId());
            if (evidence != null) {
                item.put("sourceTitle", evidence.getSourceTitle());
                item.put("evidenceStatus", evidence.getEvidenceStatus());
                item.put("createdTime", evidence.getCreatedTime());
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public String exportReport(Long id, String format) {
        ContestReportTask task = reportTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("报告任务不存在: " + id);
        }
        if ("json".equalsIgnoreCase(format)) {
            return task.getReportJson() != null ? task.getReportJson() : "{}";
        }
        return task.getReportMarkdown() != null ? task.getReportMarkdown() : "";
    }

    @Override
    public Map<String, Object> getSubmissionChecklist() {
        Map<String, Object> checklist = new LinkedHashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createChecklistItem("source_code", "源代码", "项目源代码及构建脚本", true));
        items.add(createChecklistItem("deployment_guide", "部署指南", "Docker部署文档", true));
        items.add(createChecklistItem("docker_files", "Docker文件", "Dockerfile和docker-compose.yml", true));
        items.add(createChecklistItem("p0_metrics", "P0指标报告", "评测准确率报告", true));
        items.add(createChecklistItem("p1_rag_evolution", "P1 RAG/演化证据", "RAG知识库和岗位演化证据", true));
        items.add(createChecklistItem("graph_snapshot", "图谱快照", "知识图谱快照文件", true));
        items.add(createChecklistItem("demo_video", "演示视频", "10分钟功能演示视频", true));
        items.add(createChecklistItem("ppt", "PPT", "项目介绍PPT", true));
        items.add(createChecklistItem("jd_test_set", "JD测试集", "100条JD测试数据", true));
        items.add(createChecklistItem("new_post_graph", "新岗位图谱", "1个新兴岗位图谱示例", true));
        items.add(createChecklistItem("evolution_graph", "演化图谱", "1个岗位演化图谱示例", true));

        checklist.put("items", items);
        checklist.put("totalCount", items.size());

        return checklist;
    }

    // ===================== 内部方法 =====================

    /**
     * 生成纯统计报告（非 AI）
     */


    /**
     * 保存证据引用
     */
    private void saveEvidenceRefs(Long taskId, List<ContestEvidenceItem> evidences) {
        for (ContestEvidenceItem evidence : evidences) {
            ContestReportEvidenceRef ref = new ContestReportEvidenceRef();
            ref.setReportTaskId(taskId);
            ref.setEvidenceId(evidence.getId());
            ref.setEvidenceCode(evidence.getEvidenceCode());
            ref.setSourceType(evidence.getSourceType());
            ref.setAbilityName(evidence.getAbilityName());
            ref.setConfidenceScore(evidence.getConfidenceScore());
            ref.setCredibilityScore(evidence.getCredibilityScore());
            evidenceRefMapper.insert(ref);
        }
    }

    private String generateTaskCode() {
        return "RPT_" + LocalDateTime.now().format(TASK_CODE_FORMAT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Map<String, Object> createChecklistItem(String code, String name, String description, boolean required) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("description", description);
        item.put("required", required);
        return item;
    }

    private String resolveReportPromptVersion() {
        try {
            return metadataResolver.resolve("matching-overview-report.ftl").version();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve report prompt version", e);
        }
    }
}
