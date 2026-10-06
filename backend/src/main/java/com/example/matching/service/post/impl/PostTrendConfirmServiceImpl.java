package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.application.post.PostApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.dto.post.api.PostTrendCandidateUpdateRequest;
import com.example.matching.dto.post.api.PostTrendLandResult;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostTrendConfirmService;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.service.post.support.TrendAbilityDiffService;
import com.example.matching.service.post.support.TrendCandidatePayloadCodec;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagHierarchy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 趋势候选落地实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTrendConfirmServiceImpl implements PostTrendConfirmService {

    private static final String DEFAULT_REVIEW_COMMENT = "岗位管理员确认趋势候选";
    private static final String DEFAULT_TAG_COMMENT = "采用既有能力标签";
    private static final String DEFAULT_IGNORE_COMMENT = "该能力名不进入标签体系";
    private static final int COMMENT_MAX_LENGTH = 500;

    /** 与 {@code post_trend_candidate.post_description VARCHAR(2048)} 对齐，超长会直接写入失败。 */
    private static final int DESCRIPTION_MAX_LENGTH = 2048;

    /** 与 {@code post_trend_candidate.post_name VARCHAR(255)} 对齐。 */
    private static final int POST_NAME_MAX_LENGTH = 255;

    /** 新岗位默认以启用状态落库：管理员已确认能力画像，停用会让人误以为创建失败。 */
    private static final int NEW_POST_ENABLED = 1;

    private final PostTrendCandidateMapper candidateMapper;
    private final PostApiFacade postApiFacade;
    private final PostAbilityModelService postAbilityModelService;
    private final PostTrendTaskService postTrendTaskService;
    private final TrendAbilityDiffService abilityDiffService;
    private final TrendCandidatePayloadCodec payloadCodec;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final AbilityTagMapper abilityTagMapper;

    @Override
    @Transactional
    public PostTrendLandResult land(Long candidateId, String reviewComment, Long operatorId) {
        PostTrendCandidate candidate = requireCandidate(candidateId);
        if (!candidate.isPending()) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "该候选已由他人处理（当前状态：" + describeStatus(candidate.getConfirmStatus()) + "），未重复落地");
        }
        TrendCandidatePayload payload = payloadCodec.read(candidate);

        Long postId;
        boolean createdNewPost;
        if (candidate.isNewPost()) {
            createdNewPost = true;
            postId = createPostFromCandidate(candidateId, candidate, reviewComment, operatorId);
            // 新岗位的一切能力都是新增，避免沿用解析阶段的 diff 标注造成误读
            abilityDiffService.markAllAsAdded(payload);
        } else {
            createdNewPost = false;
            postId = resolveExistingPostId(candidate);
            claim(candidateId, postId, reviewComment, operatorId);
        }

        // 能力变更候选必须与既有能力做全量合并：batchConfig 是先物理删除再写入，
        // 只传变更项会把材料没提到的能力一并删掉。
        List<PostAbilityModelConfigDTO> configs =
                abilityDiffService.mergeForPersist(createdNewPost ? null : postId, payload);
        if (configs.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "该候选没有任何能力项，已放弃落地；请先在详情里补充能力清单再确认");
        }
        configs.forEach(config -> config.setPostId(postId));

        postAbilityModelService.batchConfig(configs);
        postTrendTaskService.refreshConfirmStatus(candidate.getTaskId());

        log.info("趋势候选已落地: candidateId={}, type={}, postId={}, abilityCount={}, created={}",
                candidateId, candidate.getCandidateType(), postId, configs.size(), createdNewPost);
        return new PostTrendLandResult(candidateId, candidate.getCandidateType(), postId,
                candidate.getPostName(), configs.size(), createdNewPost);
    }

    /**
     * 建岗位 + 抢占候选。
     * <p>
     * 顺序上必须「先建岗拿到 ID，再用 CAS 抢占」：{@code markLanded} 需要写入 created_post_id。
     * 抢占失败时抛异常，建岗操作随事务回滚，不会留下没人认领的岗位。
     */
    private Long createPostFromCandidate(Long candidateId, PostTrendCandidate candidate,
                                         String reviewComment, Long operatorId) {
        if (!StringUtils.hasText(candidate.getPostName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "候选缺少岗位名称，无法创建岗位");
        }
        Long postId = postApiFacade.createAndReturnId(new PostCreateRequest(
                null,
                candidate.getPostName().trim(),
                candidate.getPostDescription(),
                NEW_POST_ENABLED,
                null));
        if (postId == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "岗位创建失败，未取得岗位ID");
        }
        claim(candidateId, postId, reviewComment, operatorId);
        return postId;
    }

    private Long resolveExistingPostId(PostTrendCandidate candidate) {
        Long postId = candidate.getMatchedPostId();
        if (postId == null) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "能力变更候选缺少目标岗位，无法落地；请改为新岗位候选或重新解析");
        }
        if (postApiFacade.get(postId) == null) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "目标岗位已不存在（postId=" + postId + "），能力变更无法落地");
        }
        return postId;
    }

    private void claim(Long candidateId, Long postId, String reviewComment, Long operatorId) {
        int updated = candidateMapper.markLanded(candidateId, postId,
                resolveComment(reviewComment, DEFAULT_REVIEW_COMMENT), operatorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "该候选已被其他操作处理，本次未重复落地；请刷新列表后重试");
        }
    }

    @Override
    @Transactional
    public boolean review(Long candidateId, String confirmStatus, String reviewComment, Long operatorId) {
        PostTrendCandidate candidate = requireCandidate(candidateId);
        if (!PostTrendCandidate.CONFIRM_APPROVED.equals(confirmStatus)
                && !PostTrendCandidate.CONFIRM_REJECTED.equals(confirmStatus)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核结论只能是 APPROVED 或 REJECTED");
        }
        int updated = candidateMapper.reviewPending(candidateId, confirmStatus,
                resolveComment(reviewComment, PostTrendCandidate.CONFIRM_REJECTED.equals(confirmStatus)
                        ? "岗位管理员驳回了该候选"
                        : "岗位管理员标记通过（未落地）"),
                operatorId);
        if (updated == 0) {
            log.info("趋势候选已不在待确认状态，审核未生效: candidateId={}", candidateId);
            return false;
        }
        postTrendTaskService.refreshConfirmStatus(candidate.getTaskId());
        return true;
    }

    @Override
    @Transactional
    public void updatePayload(Long candidateId, PostTrendCandidateUpdateRequest request) {
        PostTrendCandidate candidate = requireCandidate(candidateId);
        if (!candidate.isPending()) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "候选已处理，不能再修改；如需调整请重新发起解析");
        }
        if (request == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "调整内容不能为空");
        }

        TrendCandidatePayload payload = payloadCodec.read(candidate);
        if (request.abilities() != null) {
            payload.setAbilities(toAbilityItems(request.abilities()));
        }
        // 能力清单变了，变更类型必须重算：抽屉里显示的 diff 与落地写入的必须同源
        if (candidate.isNewPost()) {
            abilityDiffService.markAllAsAdded(payload);
        } else {
            abilityDiffService.applyDiff(candidate.getMatchedPostId(), payload);
        }

        String postName = StringUtils.hasText(request.postName())
                ? truncate(request.postName().trim(), POST_NAME_MAX_LENGTH) : candidate.getPostName();
        String postDescription = request.postDescription() != null
                ? request.postDescription() : candidate.getPostDescription();

        int updated = candidateMapper.updatePendingPayload(candidateId,
                payloadCodec.write(payload), postName, truncate(postDescription, DESCRIPTION_MAX_LENGTH));
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "候选已被其他操作处理，本次调整未生效");
        }
    }

    private List<TrendCandidatePayload.TrendAbilityItem> toAbilityItems(
            List<PostTrendCandidateUpdateRequest.AbilityItem> items) {
        List<TrendCandidatePayload.TrendAbilityItem> result = new ArrayList<>();
        for (PostTrendCandidateUpdateRequest.AbilityItem item : items) {
            if (item == null || !StringUtils.hasText(item.abilityName())) {
                continue;
            }
            TrendCandidatePayload.TrendAbilityItem target = new TrendCandidatePayload.TrendAbilityItem();
            target.setAbilityName(item.abilityName().trim());
            target.setTagId(item.tagId());
            target.setResolved(item.tagId() != null);
            target.setSuggestedLevel(item.suggestedLevel());
            target.setSuggestedWeight(item.suggestedWeight());
            target.setIsCore(Integer.valueOf(1).equals(item.isCore()) ? 1 : 0);
            result.add(target);
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "能力清单不能为空，否则落地后岗位将没有任何能力要求");
        }
        return result;
    }

    @Override
    @Transactional
    public void adoptNewTag(Long candidateId, Long tagId, String comment, Long operatorId) {
        AbilityTagCandidate tagCandidate = requireTagCandidate(candidateId);
        if (tagId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择一个既有能力标签");
        }
        AbilityTag tag = abilityTagMapper.selectById(tagId);
        if (!AbilityTagHierarchy.isAssessable(tag)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "只能采用启用的 L2 可评估能力标签，tagId=" + tagId);
        }
        if (!AbilityTagCandidate.STATUS_PENDING.equals(tagCandidate.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT, "该能力候选已被处理，无需重复操作");
        }
        Long canonicalTagId = tag.getCanonicalTagId() != null ? tag.getCanonicalTagId() : tag.getId();        abilityTagCandidateService.merge(candidateId, canonicalTagId, operatorId,
                resolveComment(comment, DEFAULT_TAG_COMMENT));
        backfillAdoptedTag(tagCandidate, canonicalTagId, tag.getTagName());
    }

    @Override
    @Transactional
    public void ignoreNewTag(Long candidateId, String comment, Long operatorId) {
        AbilityTagCandidate tagCandidate = requireTagCandidate(candidateId);
        if (!AbilityTagCandidate.STATUS_PENDING.equals(tagCandidate.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT, "该能力候选已被处理，无需重复操作");
        }
        abilityTagCandidateService.reject(candidateId, operatorId,
                resolveComment(comment, DEFAULT_IGNORE_COMMENT));
    }

    /**
     * 把「采用既有标签」的结果回填到同一任务下仍待确认的候选能力项上。
     * <p>
     * 不回填的话，管理员点完「采用」再落地，写进岗位能力画像的 tagId 依然是空的，
     * 这次「采用」只改了候选池、对业务数据毫无影响。
     */
    private void backfillAdoptedTag(AbilityTagCandidate tagCandidate, Long tagId, String tagName) {
        Long taskId = tagCandidate.getSourceRefId();
        if (taskId == null) {
            return;
        }
        String targetKey = AbilityNameNormalizer.normalize(tagCandidate.getCandidateName());
        List<PostTrendCandidate> pendings = candidateMapper.selectList(
                new LambdaQueryWrapper<PostTrendCandidate>()
                        .eq(PostTrendCandidate::getTaskId, taskId)
                        .eq(PostTrendCandidate::getConfirmStatus, PostTrendCandidate.CONFIRM_PENDING));
        int patched = 0;
        for (PostTrendCandidate pending : pendings) {
            TrendCandidatePayload payload = payloadCodec.read(pending);
            boolean changed = false;
            for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
                if (item == null || item.getTagId() != null || !StringUtils.hasText(item.getAbilityName())) {
                    continue;
                }
                if (!targetKey.equals(AbilityNameNormalizer.normalize(item.getAbilityName()))) {
                    continue;
                }
                item.setTagId(tagId);
                item.setMatchedTagName(tagName);
                item.setResolved(true);
                changed = true;
            }
            if (!changed) {
                continue;
            }
            candidateMapper.updatePendingPayload(pending.getId(), payloadCodec.write(payload),
                    pending.getPostName(), pending.getPostDescription());
            patched++;
        }
        if (patched > 0) {
            log.info("已把能力标签归位结果回填到待确认候选: taskId={}, tagId={}, candidateCount={}",
                    taskId, tagId, patched);
        }
    }

    private AbilityTagCandidate requireTagCandidate(Long candidateId) {
        AbilityTagCandidate tagCandidate = candidateId == null ? null : abilityTagCandidateService.getById(candidateId);
        if (tagCandidate == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "能力标签候选不存在: " + candidateId);
        }
        return tagCandidate;
    }

    private PostTrendCandidate requireCandidate(Long candidateId) {
        PostTrendCandidate candidate = candidateId == null ? null : candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "趋势候选不存在: " + candidateId);
        }
        return candidate;
    }

    private String resolveComment(String comment, String fallback) {
        return truncate(StringUtils.hasText(comment) ? comment.trim() : fallback, COMMENT_MAX_LENGTH);
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String describeStatus(String status) {
        if (PostTrendCandidate.CONFIRM_APPROVED.equals(status)) {
            return "已通过";
        }
        if (PostTrendCandidate.CONFIRM_REJECTED.equals(status)) {
            return "已驳回";
        }
        return Objects.toString(status, "未知");
    }

    /** 供批量落地复用：判断候选是否满足「可被批量确认」的条件。 */
    public static boolean batchConfirmable(PostTrendCandidate candidate) {
        return candidate != null
                && candidate.isPending()
                && PostTrendCandidate.HARNESS_PASS.equals(candidate.getHarnessDecision());
    }

    /** 供批量落地复用：给出被跳过的中文原因。 */
    public static String batchSkipReason(PostTrendCandidate candidate) {
        if (candidate == null) {
            return "候选不存在";
        }
        if (!candidate.isPending()) {
            return "候选已被处理";
        }
        if (!PostTrendCandidate.HARNESS_PASS.equals(candidate.getHarnessDecision())) {
            return "治理判定为「" + describeHarness(candidate.getHarnessDecision())
                    + "」，需逐条人工确认后才能落地";
        }
        return null;
    }

    private static String describeHarness(String decision) {
        if (PostTrendCandidate.HARNESS_REVIEW.equals(decision)) {
            return "待复核";
        }
        if (PostTrendCandidate.HARNESS_BLOCK.equals(decision)) {
            return "已拦截";
        }
        return Objects.toString(decision, "未知");
    }
}
