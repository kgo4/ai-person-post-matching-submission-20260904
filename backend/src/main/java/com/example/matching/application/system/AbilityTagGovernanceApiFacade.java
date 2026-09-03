package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.schedule.MergeScheduleManager;
import com.example.matching.schedule.TagMergeScheduler;
import com.example.matching.service.system.AbilityTagGovernanceService;
import com.example.matching.service.system.AbilityTagRelationService;
import com.example.matching.vo.system.AbilityTagRelationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbilityTagGovernanceApiFacade {

    private final AbilityTagGovernanceService governanceService;
    private final AbilityTagRelationService relationService;
    private final TagQueryPort tagQueryPort;
    private final TagMergeScheduler tagMergeScheduler;
    private final MergeScheduleManager mergeScheduleManager;

    public IPage<?> pageCandidates(Integer pageNum, Integer pageSize, String status, String sourceType) {
        return governanceService.pageCandidates(new Page<>(pageNum, pageSize), status, sourceType);
    }

    public Long approveCandidate(Long id, String tagCategory, Long parentDomainId, Long reviewedBy) {
        return governanceService.approveCandidate(id, tagCategory, parentDomainId, reviewedBy);
    }

    public Long approveCandidate(Long id, String tagCategory, Long parentDomainId, Long reviewedBy,
                                 String editedCandidateName, String reviewComment) {
        return governanceService.approveCandidate(id, tagCategory, parentDomainId, reviewedBy,
                editedCandidateName, reviewComment);
    }

    /** Compatibility overload for older callers that did not provide a category. */
    public Long approveCandidate(Long id, String tagCategory, Long reviewedBy) {
        return governanceService.approveCandidate(id, tagCategory, 0L, reviewedBy);
    }

    public void rejectCandidate(Long id, Long reviewedBy, String reason) {
        governanceService.rejectCandidate(id, reviewedBy, reason);
    }

    public void mergeCandidate(Long id, Long targetTagId, Long reviewedBy) {
        governanceService.mergeCandidateToExisting(id, targetTagId, reviewedBy);
    }

    public void computeStats() {
        governanceService.computeUsageStats();
    }

    public List<?> getUsageStats(int topN) {
        return governanceService.getUsageStats(topN);
    }

    public IPage<AbilityTagRelationVO> pageRelations(Integer pageNum, Integer pageSize,
                                                      Long sourceTagId, Long targetTagId,
                                                      String relationType, String status) {
        IPage<AbilityTagRelation> page = relationService.pageRelations(
                new Page<>(pageNum, pageSize), sourceTagId, targetTagId, relationType, status);

        Set<Long> tagIds = page.getRecords().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getSourceTagId(), r.getTargetTagId()))
                .collect(Collectors.toSet());

        Map<Long, String> tagNameMap = tagIds.isEmpty() ? Map.of() :
                tagQueryPort.batchGetTags(new ArrayList<>(tagIds)).stream()
                        .collect(Collectors.toMap(TagQueryPort.TagDTO::id, TagQueryPort.TagDTO::tagName, (a, b) -> a));

        return page.convert(r -> {
            AbilityTagRelationVO vo = new AbilityTagRelationVO();
            vo.setId(r.getId());
            vo.setSourceTagId(r.getSourceTagId());
            vo.setTargetTagId(r.getTargetTagId());
            vo.setSourceTagName(tagNameMap.getOrDefault(r.getSourceTagId(), "标签" + r.getSourceTagId()));
            vo.setTargetTagName(tagNameMap.getOrDefault(r.getTargetTagId(), "标签" + r.getTargetTagId()));
            vo.setRelationType(r.getRelationType());
            vo.setSimilarityScore(r.getSimilarityScore());
            vo.setStatus(r.getStatus());
            vo.setEvidenceSource(r.getEvidenceSource());
            vo.setRemark(r.getRemark());
            vo.setCreatedTime(r.getCreatedTime());
            return vo;
        });
    }

    public Object createRelation(Long sourceTagId, Long targetTagId, String relationType,
                                Double similarityScore, String remark) {
        return relationService.createRelation(
                sourceTagId, targetTagId, relationType, similarityScore, "MANUAL", remark, null);
    }

    public void approveRelation(Long id, Long updatedBy) {
        relationService.approveRelation(id, updatedBy);
    }

    public void rejectRelation(Long id, Long updatedBy) {
        relationService.rejectRelation(id, updatedBy);
    }

    public int discoverRelations(double threshold) {
        return relationService.discoverRelations(threshold);
    }

    public Map<String, Object> executeMerge(double threshold) {
        return tagMergeScheduler.executeMerge(threshold);
    }

    public Map<String, Object> scheduleMerge(double threshold, LocalDateTime scheduledTime, Long operatorId) {
        return mergeScheduleManager.schedule(scheduledTime, threshold, operatorId);
    }

    /** Compatibility entry point for callers that have not propagated an operator yet. */
    public Map<String, Object> scheduleMerge(double threshold, LocalDateTime scheduledTime) {
        return scheduleMerge(threshold, scheduledTime, 0L);
    }

    public boolean cancelMerge(String taskId) {
        return mergeScheduleManager.cancel(taskId);
    }

    public List<Map<String, Object>> listPendingMerges() {
        return mergeScheduleManager.listPending();
    }

    public List<Map<String, Object>> listRecentMergeNotifications(Long operatorId) {
        return mergeScheduleManager.listRecentTerminalTasks(operatorId);
    }
}
