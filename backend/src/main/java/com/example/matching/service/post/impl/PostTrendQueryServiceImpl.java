package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.dto.post.api.PostTrendCandidateDetailResponse;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.dto.post.api.TrendNewTagCandidateResponse;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.post.PostTrendTask;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.post.PostTrendTaskMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.post.PostTrendQueryService;
import com.example.matching.service.post.support.TrendCandidatePayloadCodec;
import com.example.matching.service.post.support.TrendAbilityResolver;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.vo.post.TrendPendingSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 趋势候选读取实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTrendQueryServiceImpl implements PostTrendQueryService {

    /** 卡片上最多显示几个能力名：够看出岗位方向即可，再多就是信息倾倒。 */
    private static final int PREVIEW_ABILITY_LIMIT = 4;

    private final PostTrendCandidateMapper candidateMapper;
    private final PostTrendTaskMapper taskMapper;
    private final RagKnowledgeDocumentMapper ragKnowledgeDocumentMapper;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final TrendCandidatePayloadCodec payloadCodec;

    @Override
    public PageResponse<PostTrendCandidateResponse> pageCandidates(long current, long size, Long taskId,
                                                                   String candidateType, String confirmStatus,
                                                                   String harnessDecision) {
        LambdaQueryWrapper<PostTrendCandidate> wrapper = new LambdaQueryWrapper<PostTrendCandidate>()
                .eq(taskId != null, PostTrendCandidate::getTaskId, taskId)
                .eq(StringUtils.hasText(candidateType), PostTrendCandidate::getCandidateType, candidateType)
                .eq(StringUtils.hasText(confirmStatus), PostTrendCandidate::getConfirmStatus, confirmStatus)
                .eq(StringUtils.hasText(harnessDecision), PostTrendCandidate::getHarnessDecision, harnessDecision)
                // 待确认优先、强调度高优先：管理员先看该看的
                .orderByAsc(PostTrendCandidate::getConfirmStatus)
                .orderByDesc(PostTrendCandidate::getEmphasisScore)
                .orderByDesc(PostTrendCandidate::getId);
        Page<PostTrendCandidate> page = candidateMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResponse.from(page, this::toSummary);
    }

    @Override
    public PostTrendCandidateResponse toSummary(PostTrendCandidate candidate) {
        PostTrendCandidateResponse response = new PostTrendCandidateResponse();
        if (candidate == null) {
            return response;
        }
        response.setId(candidate.getId());
        response.setTaskId(candidate.getTaskId());
        response.setCandidateType(candidate.getCandidateType());
        response.setPostName(candidate.getPostName());
        response.setPostDescription(candidate.getPostDescription());
        response.setMatchedPostId(candidate.getMatchedPostId());
        response.setMatchedPostName(candidate.getMatchedPostName());
        response.setSimilarityScore(candidate.getSimilarityScore());
        response.setEmphasisScore(candidate.getEmphasisScore());
        response.setSourceCoverage(candidate.getSourceCoverage());
        response.setHarnessDecision(candidate.getHarnessDecision());
        response.setRiskLevel(candidate.getRiskLevel());
        response.setConfirmStatus(candidate.getConfirmStatus());
        response.setCreatedPostId(candidate.getCreatedPostId());
        response.setCreatedTime(candidate.getCreatedTime());

        TrendCandidatePayload payload = payloadCodec.read(candidate);
        List<TrendCandidatePayload.TrendAbilityItem> abilities = payload.getAbilities();
        response.setAbilityCount(abilities.size());
        int unresolved = 0;
        int changes = 0;
        List<String> preview = new ArrayList<>();
        for (TrendCandidatePayload.TrendAbilityItem item : abilities) {
            if (item == null) {
                continue;
            }
            if (!item.isResolved()) {
                unresolved++;
            }
            if (item.getChangeType() != null && !"UNCHANGED".equals(item.getChangeType())) {
                changes++;
            }
            if (preview.size() < PREVIEW_ABILITY_LIMIT && StringUtils.hasText(item.getAbilityName())) {
                preview.add(item.getAbilityName());
            }
        }
        response.setUnresolvedAbilityCount(unresolved);
        response.setChangeCount(changes);
        response.setPreviewAbilities(preview);
        return response;
    }

    @Override
    public PostTrendCandidateDetailResponse getCandidateDetail(Long candidateId) {
        PostTrendCandidate candidate = candidateId == null ? null : candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "趋势候选不存在: " + candidateId);
        }
        PostTrendCandidateDetailResponse detail = new PostTrendCandidateDetailResponse();
        detail.setSummary(toSummary(candidate));
        detail.setPayload(payloadCodec.read(candidate));
        detail.setEvidenceText(candidate.getEvidenceText());

        List<String> sourceRefs = payloadCodec.readStringList(candidate.getSourceRefs());
        detail.setSourceRefs(sourceRefs);
        detail.setSourceTitles(resolveSourceTitles(sourceRefs));
        detail.setNewTagCandidateIds(resolvePendingTagCandidates(candidate));
        return detail;
    }

    /**
     * 由 sourceRef 反解出来源材料标题。
     * <p>
     * sourceRef 形如 {@code source:INDUSTRY_WHITEPAPER:11:0}，第三段是 RAG 文档ID，
     * 第四段只是分块定位。只按文档ID回查标题即可，分块不参与展示。
     */
    private List<String> resolveSourceTitles(List<String> sourceRefs) {
        List<String> titles = new ArrayList<>();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return titles;
        }
        LinkedHashSet<Long> documentIds = new LinkedHashSet<>();
        for (String ref : sourceRefs) {
            Long documentId = parseDocumentId(ref);
            if (documentId != null) {
                documentIds.add(documentId);
            }
        }
        if (documentIds.isEmpty()) {
            return titles;
        }
        Map<Long, String> titleById = new LinkedHashMap<>();
        try {
            List<RagKnowledgeDocument> documents =
                    ragKnowledgeDocumentMapper.selectBatchIds(new ArrayList<>(documentIds));
            if (documents != null) {
                for (RagKnowledgeDocument document : documents) {
                    if (document != null) {
                        titleById.put(document.getId(), document.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("回查来源材料标题失败，仅返回来源引用: err={}", e.getMessage());
        }
        for (Long documentId : documentIds) {
            String title = titleById.get(documentId);
            titles.add(StringUtils.hasText(title) ? title : "材料 #" + documentId);
        }
        return titles;
    }

    private Long parseDocumentId(String sourceRef) {
        if (!StringUtils.hasText(sourceRef)) {
            return null;
        }
        String[] parts = sourceRef.split(":");
        if (parts.length < 3) {
            return null;
        }
        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 找出本候选里仍未归位的能力所对应的标签候选ID，供界面显示「采用现有标签 / 忽略」。 */
    private List<Long> resolvePendingTagCandidates(PostTrendCandidate candidate) {
        List<Long> ids = new ArrayList<>();
        TrendCandidatePayload payload = payloadCodec.read(candidate);
        boolean hasUnresolved = payload.getAbilities().stream()
                .anyMatch(item -> item != null && !item.isResolved());
        if (!hasUnresolved || candidate.getTaskId() == null) {
            return ids;
        }
        Map<String, Long> idByName = new LinkedHashMap<>();
        // 先看载荷里直接记录的候选ID（解析时就已写入），这是最精确的来源
        for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
            if (item != null && !item.isResolved() && item.getTagCandidateId() != null) {
                ids.add(item.getTagCandidateId());
                if (StringUtils.hasText(item.getAbilityName())) {
                    idByName.put(AbilityNameNormalizer.normalize(item.getAbilityName()), item.getTagCandidateId());
                }
            }
        }
        for (AbilityTagCandidate tagCandidate : listTaskTagCandidates(candidate.getTaskId())) {
            Long existing = idByName.get(AbilityNameNormalizer.normalize(tagCandidate.getCandidateName()));
            if (existing != null) {
                continue;
            }
            for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
                if (item == null || item.isResolved() || !StringUtils.hasText(item.getAbilityName())) {
                    continue;
                }
                if (AbilityNameNormalizer.normalize(item.getAbilityName())
                        .equals(AbilityNameNormalizer.normalize(tagCandidate.getCandidateName()))) {
                    ids.add(tagCandidate.getId());
                    break;
                }
            }
        }
        return ids.stream().distinct().toList();
    }

    @Override
    public List<TrendNewTagCandidateResponse> listNewTagCandidates(Long taskId) {
        List<AbilityTagCandidate> candidates = listTaskTagCandidates(taskId);
        List<TrendNewTagCandidateResponse> responses = new ArrayList<>(candidates.size());
        for (AbilityTagCandidate candidate : candidates) {
            TrendNewTagCandidateResponse response = new TrendNewTagCandidateResponse();
            response.setId(candidate.getId());
            response.setCandidateName(candidate.getCandidateName());
            response.setSimilarTagId(candidate.getSimilarTagId());
            response.setSimilarTagName(candidate.getSimilarTagName());
            response.setSimilarityScore(candidate.getSimilarityScore());
            response.setEvidenceText(candidate.getEvidenceText());
            response.setStatus(candidate.getStatus());
            response.setOccurrenceCount(candidate.getOccurrenceCount());
            response.setCreatedTime(candidate.getCreatedTime());
            responses.add(response);
        }
        return responses;
    }

    /**
     * 本次解析写入的能力标签候选。
     * <p>
     * 按 {@code source_ref_id = taskId} 收口：不带任务过滤会把别的链路（JD 导入、简历解析）
     * 沉淀的候选混进趋势页，管理员会看到一堆与本次材料无关的能力。
     */
    private List<AbilityTagCandidate> listTaskTagCandidates(Long taskId) {
        LambdaQueryWrapper<AbilityTagCandidate> wrapper = new LambdaQueryWrapper<AbilityTagCandidate>()
                .eq(AbilityTagCandidate::getSourceType, TrendAbilityResolver.SOURCE_TYPE_POST_TREND)
                .eq(AbilityTagCandidate::getStatus, AbilityTagCandidate.STATUS_PENDING)
                .orderByDesc(AbilityTagCandidate::getId);
        if (taskId != null) {
            wrapper.eq(AbilityTagCandidate::getSourceRefId, taskId);
        }
        List<AbilityTagCandidate> candidates = abilityTagCandidateService.list(wrapper);
        return candidates == null ? List.of() : candidates;
    }

    @Override
    public TrendPendingSummaryVO pendingSummary() {
        TrendPendingSummaryVO summary = new TrendPendingSummaryVO();
        // 全部按 PENDING 收口：已通过/已驳回不该继续占工作台待办位（否则待办永远清不掉）
        long pendingNewPost = countPendingByType(PostTrendCandidate.TYPE_NEW_POST);
        long pendingChange = countPendingByType(PostTrendCandidate.TYPE_ABILITY_CHANGE);
        summary.setPendingNewPostCount(pendingNewPost);
        summary.setPendingChangeCount(pendingChange);
        summary.setPendingCandidateCount(pendingNewPost + pendingChange);
        summary.setAwaitingTaskCount(countAwaitingTasks());
        return summary;
    }

    private long countPendingByType(String candidateType) {
        try {
            Long count = candidateMapper.selectCount(new LambdaQueryWrapper<PostTrendCandidate>()
                    .eq(PostTrendCandidate::getConfirmStatus, PostTrendCandidate.CONFIRM_PENDING)
                    .eq(PostTrendCandidate::getCandidateType, candidateType));
            return count == null ? 0L : count;
        } catch (Exception e) {
            // 工作台待办属于「锦上添花」：查不到就当 0，不要因此让整个工作台报错
            log.warn("统计待确认趋势候选失败: type={}, err={}", candidateType, e.getMessage());
            return 0L;
        }
    }

    private long countAwaitingTasks() {
        try {
            Long count = taskMapper.selectCount(new LambdaQueryWrapper<PostTrendTask>()
                    .eq(PostTrendTask::getTaskStatus, TaskStatusEnum.WAIT_CONFIRM.getCode()));
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("统计待确认趋势任务失败: err={}", e.getMessage());
            return 0L;
        }
    }
}
