package com.example.matching.application.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.evolution.AgentProgressVO;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionScheduleConfigDTO;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.PostEvolutionTaskCreateDTO;
import com.example.matching.dto.evolution.api.EvolutionTaskRequest;
import com.example.matching.dto.evolution.api.KnowledgeSourceDocumentResponse;
import com.example.matching.dto.evolution.api.PostEvolutionChangeItemResponse;
import com.example.matching.dto.evolution.api.PostEvolutionEvidenceResponse;
import com.example.matching.dto.evolution.api.PostEvolutionEvidenceSummaryResponse;
import com.example.matching.dto.evolution.api.PostEvolutionScheduleConfigResponse;
import com.example.matching.dto.evolution.api.PostEvolutionTaskResponse;
import com.example.matching.dto.evolution.api.ScheduleConfigRequest;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionScheduleConfig;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.service.evolution.EvolutionScheduleService;
import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import com.example.matching.service.evolution.PostEvolutionAgentService;
import com.example.matching.service.evolution.PostEvolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostEvolutionApiFacade {

    private final PostEvolutionService postEvolutionService;
    private final PostEvolutionAgentService postEvolutionAgentService;
    private final EvolutionSourceIngestionService evolutionSourceIngestionService;
    private final EvolutionScheduleService evolutionScheduleService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> uploadIndustryWhitepaper(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId) {
        KnowledgeSourceDocument document = evolutionSourceIngestionService.uploadIndustryWhitepaper(fileName, content, dto, operatorId);
        int chunkCount = evolutionSourceIngestionService.indexKnowledgeSource(document.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("title", document.getTitle());
        result.put("sourceType", document.getSourceType());
        result.put("sourceCategory", document.getSourceCategory());
        result.put("chunkCount", chunkCount);
        result.put("status", "ACTIVE");
        return result;
    }

    public Map<String, Object> uploadInternalDocument(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId) {
        KnowledgeSourceDocument document = evolutionSourceIngestionService.uploadInternalDocument(fileName, content, dto, operatorId);
        int chunkCount = evolutionSourceIngestionService.indexKnowledgeSource(document.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("title", document.getTitle());
        result.put("sourceType", document.getSourceType());
        result.put("sourceCategory", document.getSourceCategory());
        result.put("chunkCount", chunkCount);
        result.put("status", "ACTIVE");
        return result;
    }

    public Map<String, Object> syncCloudKnowledge(CloudSyncRequest request) {
        int syncedCount = evolutionSourceIngestionService.syncCloudKnowledge(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("syncedCount", syncedCount);
        result.put("knowledgeBaseCode", request.getKnowledgeBaseCode());
        return result;
    }

    public Map<String, Object> indexKnowledgeSource(Long documentId) {
        int chunkCount = evolutionSourceIngestionService.indexKnowledgeSource(documentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("chunkCount", chunkCount);
        return result;
    }

    public Map<String, Object> runAgent(PostEvolutionAgentRequest request) {
        if (request.getOperatorId() == null) {
            request.setOperatorId(0L);
        }
        // API 调用方可能省略可选开关，统一采用与页面一致的安全默认值。
        if (request.getIncludeWhitepaper() == null) request.setIncludeWhitepaper(Boolean.TRUE);
        if (request.getIncludeCloudKnowledge() == null) request.setIncludeCloudKnowledge(Boolean.TRUE);
        if (request.getIncludeMarketJd() == null) request.setIncludeMarketJd(Boolean.FALSE);
        if (request.getIncludeZhihu() == null) request.setIncludeZhihu(Boolean.TRUE);
        PostEvolutionTask task = postEvolutionAgentService.runEvolutionAndCreateTask(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("taskStatus", task.getTaskStatus());
        result.put("taskCode", task.getTaskCode());
        if (task.getSummaryJson() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = objectMapper.readValue(task.getSummaryJson(), Map.class);
                result.put("summary", summary);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public AgentProgressVO getAgentProgress(Long id) {
        return postEvolutionAgentService.getAgentProgress(id);
    }

    public PostEvolutionScheduleConfigResponse createSchedule(ScheduleConfigRequest req, Long operatorId) {
        EvolutionScheduleConfigDTO dto = toScheduleConfigDTO(req);
        PostEvolutionScheduleConfig entity = evolutionScheduleService.createConfig(dto, operatorId);
        return toScheduleConfigResponse(entity);
    }

    public PostEvolutionScheduleConfigResponse updateSchedule(Long id, ScheduleConfigRequest req) {
        EvolutionScheduleConfigDTO dto = toScheduleConfigDTO(req);
        PostEvolutionScheduleConfig entity = evolutionScheduleService.updateConfig(id, dto);
        return toScheduleConfigResponse(entity);
    }

    public PageResponse<PostEvolutionScheduleConfigResponse> pageSchedules(long current, long size, Long postId) {
        IPage<PostEvolutionScheduleConfig> page = evolutionScheduleService.pageConfigs(new Page<>(current, size), postId);
        return PageResponse.from(page, PostEvolutionApiFacade::toScheduleConfigResponse);
    }

    public PostEvolutionScheduleConfigResponse getSchedule(Long id) {
        PostEvolutionScheduleConfig entity = evolutionScheduleService.getConfigById(id);
        return toScheduleConfigResponse(entity);
    }

    public void deleteSchedule(Long id) {
        evolutionScheduleService.deleteConfig(id);
    }

    public Map<String, Object> runScheduleNow(Long id) {
        Long taskId = evolutionScheduleService.runNow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheduleId", id);
        result.put("taskId", taskId);
        return result;
    }

    public PostEvolutionTaskResponse createTask(EvolutionTaskRequest req, Long userId) {
        PostEvolutionTaskCreateDTO dto = new PostEvolutionTaskCreateDTO();
        dto.setPostId(req.postId());
        dto.setTaskName(req.taskName());
        dto.setNewJdText(req.newJdText());
        PostEvolutionTask entity = postEvolutionService.createTask(dto, userId);
        return toTaskResponse(entity);
    }

    public PostEvolutionTaskResponse analyzeTask(Long id) {
        PostEvolutionTask entity = postEvolutionService.analyzeTask(id);
        return toTaskResponse(entity);
    }

    public PageResponse<PostEvolutionTaskResponse> pageTasks(long current, long size, Long postId, String taskStatus) {
        IPage<PostEvolutionTask> page = postEvolutionService.pageTasks(new Page<>(current, size), postId, taskStatus);
        return PageResponse.from(page, PostEvolutionApiFacade::toTaskResponse);
    }

    public PostEvolutionTaskResponse getTask(Long id) {
        PostEvolutionTask entity = postEvolutionService.getTaskById(id);
        return toTaskResponse(entity);
    }

    public void deleteTask(Long id) {
        postEvolutionService.deleteTask(id);
    }

    public PageResponse<PostEvolutionChangeItemResponse> pageChangeItems(Long taskId, long current, long size) {
        IPage<PostEvolutionChangeItem> page = postEvolutionService.pageChangeItems(taskId, new Page<>(current, size));
        Map<Long, List<PostEvolutionEvidence>> evidenceByItemId = postEvolutionService.getTaskEvidence(taskId).stream()
                .filter(evidence -> evidence.getChangeItemId() != null)
                .collect(Collectors.groupingBy(PostEvolutionEvidence::getChangeItemId));
        return PageResponse.from(page, item -> toChangeItemResponse(
                item, evidenceByItemId.getOrDefault(item.getId(), List.of())));
    }

    public void reviewChangeItem(Long taskId, Long itemId, PostEvolutionReviewDTO dto) {
        postEvolutionService.reviewChangeItem(taskId, itemId, dto);
    }

    public Map<String, Object> applyApprovedChanges(Long id) {
        int applied = postEvolutionService.applyApprovedChanges(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", id);
        result.put("applied", applied);
        return result;
    }

    public List<PostEvolutionEvidenceResponse> getTaskEvidence(Long id) {
        List<PostEvolutionEvidence> list = postEvolutionService.getTaskEvidence(id);
        return list.stream().map(PostEvolutionApiFacade::toEvidenceResponse).toList();
    }

    public List<PostEvolutionEvidenceResponse> getItemEvidence(Long itemId) {
        List<PostEvolutionEvidence> list = postEvolutionService.getItemEvidence(itemId);
        return list.stream().map(PostEvolutionApiFacade::toEvidenceResponse).toList();
    }

    public List<Map<String, Object>> getTimeline(Long postId, String range, int limit) {
        return postEvolutionService.getTimelineEvents(postId, range, limit);
    }

    public Map<String, Object> getDashboardStats(String range) {
        return postEvolutionService.getDashboardStats(range);
    }

    public Map<String, Object> getDashboardTrends(String range) {
        return postEvolutionService.getEvolutionTrends(range);
    }

    public Map<String, Object> getEvolutionGraph(Long postId, String timePoint) {
        return postEvolutionService.getEvolutionGraph(postId, timePoint);
    }

    private EvolutionScheduleConfigDTO toScheduleConfigDTO(ScheduleConfigRequest req) {
        EvolutionScheduleConfigDTO dto = new EvolutionScheduleConfigDTO();
        dto.setPostId(req.postId());
        dto.setEnabled(req.enabled());
        dto.setCronExpression(req.cronExpression());
        dto.setIndustry(req.industry());
        dto.setBusinessDomain(req.businessDomain());
        dto.setIncludeWhitepaper(req.includeWhitepaper());
        dto.setIncludeCloudKnowledge(req.includeCloudKnowledge());
        dto.setIncludeMarketJd(req.includeMarketJd());
        return dto;
    }

    static PostEvolutionTaskResponse toTaskResponse(PostEvolutionTask e) {
        if (e == null) return null;
        return new PostEvolutionTaskResponse(
                e.getId(), e.getTaskCode(), e.getPostId(), e.getTaskName(),
                e.getBaselineVersion(), e.getNewJdText(), e.getRagQueryLogId(),
                e.getTaskStatus(), e.getSummaryJson(), e.getErrorMessage(),
                e.getSourceType(), e.getSourceDocumentId(), e.getBusinessDomain(),
                e.getIndustry(), e.getTriggerType(), e.getContextHash(),
                e.getContextSnapshotId(), e.getEvidenceSummary(), e.getAgentTrace(),
                e.getHarnessSummary(), e.getProgressStatus(), e.getProgressPercent(),
                e.getSourceDocumentIds(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedTime()
        );
    }

    static PostEvolutionChangeItemResponse toChangeItemResponse(PostEvolutionChangeItem e) {
        return toChangeItemResponse(e, List.of());
    }

    static PostEvolutionChangeItemResponse toChangeItemResponse(PostEvolutionChangeItem e,
                                                                 List<PostEvolutionEvidence> linkedEvidence) {
        if (e == null) return null;
        List<PostEvolutionEvidence> verifiedEvidence = linkedEvidence == null ? List.of() : linkedEvidence.stream()
                .filter(PostEvolutionApiFacade::hasCompleteReviewEvidence)
                .toList();
        List<PostEvolutionEvidenceResponse> evidenceItems = verifiedEvidence.stream()
                .map(PostEvolutionApiFacade::toEvidenceResponse)
                .toList();
        return new PostEvolutionChangeItemResponse(
                e.getId(), e.getTaskId(), e.getChangeType(), e.getTagId(),
                e.getAbilityName(), e.getOldLevel(), e.getNewLevel(),
                e.getOldWeight(), e.getNewWeight(), e.getOldIsCore(), e.getNewIsCore(),
                e.getSupportScore(), e.getEvidenceChunkIds(), e.getSourceType(),
                e.getSourceRef(), e.getSourceDetail(), e.getChangeTypeExtended(),
                e.getEvidenceText(), e.getSourceRefsJson(), e.getConfidenceScore(),
                e.getFreshnessScore(), e.getAuthorityScore(), e.getCrossSourceScore(),
                e.getHarnessDecision(), e.getRiskLevel(), e.getConfirmStatus(),
                e.getReviewComment(), e.getCreatedTime(), evidenceItems,
                toEvidenceSummary(verifiedEvidence)
        );
    }

    private static boolean hasCompleteReviewEvidence(PostEvolutionEvidence evidence) {
        return evidence.getSourceType() != null && !evidence.getSourceType().isBlank()
                && evidence.getEvidenceText() != null && !evidence.getEvidenceText().isBlank()
                && evidence.getCollectedTime() != null
                && evidence.getSimilarityScore() != null
                && evidence.getTrustScore() != null
                && evidence.getSourceRef() != null && !evidence.getSourceRef().isBlank();
    }

    private static PostEvolutionEvidenceSummaryResponse toEvidenceSummary(List<PostEvolutionEvidence> evidenceItems) {
        if (evidenceItems.isEmpty()) {
            return null;
        }
        List<BigDecimal> trustScores = evidenceItems.stream()
                .map(PostEvolutionEvidence::getTrustScore)
                .toList();
        BigDecimal maxTrustScore = trustScores.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal averageTrustScore = trustScores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(trustScores.size()), 4, RoundingMode.HALF_UP);
        long sourceCount = evidenceItems.stream()
                .map(PostEvolutionEvidence::getSourceRef)
                .distinct()
                .count();
        return new PostEvolutionEvidenceSummaryResponse(
                Math.toIntExact(sourceCount), maxTrustScore, averageTrustScore, sourceCount >= 2);
    }

    static PostEvolutionEvidenceResponse toEvidenceResponse(PostEvolutionEvidence e) {
        if (e == null) return null;
        return new PostEvolutionEvidenceResponse(
                e.getId(), e.getTaskId(), e.getChangeItemId(), e.getSourceType(),
                e.getSourceId(), e.getSourceTitle(), e.getSourceUrl(),
                e.getEvidenceText(), e.getPublishedTime(), e.getCollectedTime(),
                e.getSourceWeight(), e.getSimilarityScore(), e.getTrustScore(),
                e.getSourceRef(), e.getCreatedTime()
        );
    }

    static PostEvolutionScheduleConfigResponse toScheduleConfigResponse(PostEvolutionScheduleConfig e) {
        if (e == null) return null;
        return new PostEvolutionScheduleConfigResponse(
                e.getId(), e.getPostId(), e.getEnabled(), e.getCronExpression(),
                e.getIndustry(), e.getBusinessDomain(), e.getSourceScope(),
                e.getIncludeWhitepaper(), e.getIncludeCloudKnowledge(), e.getIncludeMarketJd(),
                e.getLastRunTime(),
                e.getNextRunTime(), e.getLastTaskId(), e.getRunCount(),
                e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
