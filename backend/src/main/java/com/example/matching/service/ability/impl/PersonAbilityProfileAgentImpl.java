package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.application.agent.ReviewState;
import com.example.matching.service.ability.PersonAbilityExtractionAgent;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import com.example.matching.service.ability.PersonAbilityProfileBuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员能力画像Agent实现（顶层编排服务）
 * <p>
 * 职责：综合多来源能力证据，生成最终人员能力画像。
 * <p>
 * 能力来源：
 * 1. 简历解析 - 发现候选人声称的经历和技能，可信度中等
 * 2. AI面试 - 验证简历真实性、能力边界、表达逻辑、问题解决能力
 * 3. AI测评 - 验证知识掌握和标准题表现
 * 4. PMS / 项目系统 - 验证真实工作表现，可信度较高
 * 5. 学习成果 - 验证近期提升和成长轨迹
 * 6. 人工录入 - 人工评估和确认
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonAbilityProfileAgentImpl implements PersonAbilityProfileAgent {

    private final PersonAbilityExtractionAgent extractionAgent;
    private final PersonAbilityClaimMapper claimMapper;
    private final PersonAbilityProfileBuildService profileBuildService;
    private final PersonAbilityProfileMapper profileMapper;
    private final InterviewAbilityObservationMapper observationMapper;

    // ==================== 审核状态常量 ====================
    private static final String REVIEW_STATUS_AUTO = "AUTO";
    private static final String REVIEW_STATUS_PENDING = "PENDING_REVIEW";
    private static final String REVIEW_STATUS_REVIEWED = "REVIEWED";

    @Override
    @Transactional
    public List<PersonAbilityProfile> buildProfile(Long empId) {
        log.info("开始构建人员能力画像，empId={}", empId);

        List<PersonAbilityClaim> claims = new ArrayList<>(loadAdmittedClaims(empId));
        log.info("加载已准入能力主张完成，empId={}, claimCount={}", empId, claims.size());

        // 2. 获取最新的面试能力观察（如果有）
        List<InterviewAbilityObservation> latestObservations = getLatestInterviewObservations(empId);

        // 3. 将面试观察转换为能力主张
        if (!latestObservations.isEmpty()) {
            List<PersonAbilityClaim> interviewClaims = profileBuildService.convertObservationsToClaims(latestObservations);
            claims.addAll(interviewClaims);
            log.info("将面试观察转换为能力主张完成，empId={}, interviewClaimCount={}", empId, interviewClaims.size());
        }

        // 4. 通过PersonAbilityProfileBuildService融合所有来源
        List<PersonAbilityProfile> profiles = profileBuildService.buildProfile(empId, claims);
        log.info("人员能力画像构建完成，empId={}, profileCount={}", empId, profiles.size());

        return profiles;
    }

    @Override
    @Transactional
    public List<PersonAbilityProfile> buildProfileWithInterview(Long empId, Long sessionId) {
        log.info("开始构建人员能力画像（包含指定面试会话），empId={}, sessionId={}", empId, sessionId);

        List<PersonAbilityClaim> claims = new ArrayList<>(loadAdmittedClaims(empId));
        log.info("加载已准入能力主张完成，empId={}, claimCount={}", empId, claims.size());

        // 2. 获取指定面试会话的能力观察
        List<InterviewAbilityObservation> observations = observationMapper.selectList(
                Wrappers.<InterviewAbilityObservation>lambdaQuery()
                        .eq(InterviewAbilityObservation::getSessionId, sessionId)
                        .eq(InterviewAbilityObservation::getIsDeleted, 0)
        );

        // 3. 将面试观察转换为能力主张
        if (!observations.isEmpty()) {
            List<PersonAbilityClaim> interviewClaims = profileBuildService.convertObservationsToClaims(observations);
            claims.addAll(interviewClaims);
            log.info("将面试观察转换为能力主张完成，empId={}, interviewClaimCount={}", empId, interviewClaims.size());
        }

        // 4. 通过PersonAbilityProfileBuildService融合所有来源
        List<PersonAbilityProfile> profiles = profileBuildService.buildProfile(empId, claims);
        log.info("人员能力画像构建完成，empId={}, profileCount={}", empId, profiles.size());

        return profiles;
    }

    @Override
    @Transactional
    public List<PersonAbilityProfile> refreshProfile(Long empId) {
        log.info("刷新人员能力画像，empId={}", empId);
        return buildProfile(empId);
    }

    @Override
    public List<PersonAbilityProfile> getProfile(Long empId) {
        return profileBuildService.getLatestProfile(empId);
    }

    @Override
    public PersonAbilityProfile getProfileByTag(Long empId, Long tagId) {
        return profileBuildService.getProfileByTag(empId, tagId);
    }

    @Override
    public boolean hasPendingReview(Long empId) {
        Long pendingCount = profileMapper.selectCount(
                Wrappers.<PersonAbilityProfile>lambdaQuery()
                        .eq(PersonAbilityProfile::getEmpId, empId)
                        .eq(PersonAbilityProfile::getReviewStatus, REVIEW_STATUS_PENDING)
                        .eq(PersonAbilityProfile::getIsDeleted, 0)
        );
        return pendingCount != null && pendingCount > 0;
    }

    @Override
    @Transactional
    public PersonAbilityProfile reviewProfile(Long profileId, Long reviewerId, boolean approved, String comment) {
        log.info("审核人员能力画像，profileId={}, reviewerId={}, approved={}", profileId, reviewerId, approved);

        PersonAbilityProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "画像不存在，profileId=" + profileId).entity("PERSON_ABILITY_PROFILE", profileId).build();
        }

        if (!ReviewState.PENDING.name().equals(profile.getReviewState())
                && !REVIEW_STATUS_PENDING.equals(profile.getReviewStatus())) {
            throw BusinessException.of(ErrorCodeEnum.STATE_CONFLICT, "画像状态不是待审核，profileId=" + profileId + ", status=" + profile.getReviewStatus()).entity("PERSON_ABILITY_PROFILE", profileId).build();
        }

        ReviewState decision = approved ? ReviewState.APPROVED : ReviewState.REJECTED;
        profile.setReviewState(decision.name());
        profile.setReviewStatus(decision.toLegacyStatus());
        profile.setReviewedBy(reviewerId);
        profile.setReviewedTime(LocalDateTime.now());
        profile.setReviewComment(comment);
        profileMapper.updateById(profile);

        log.info("人员能力画像审核完成，profileId={}, approved={}", profileId, approved);
        return profile;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 获取员工最新的面试能力观察
     */
    private List<InterviewAbilityObservation> getLatestInterviewObservations(Long empId) {
        // 查找该员工最近一次面试会话
        List<InterviewAbilityObservation> observations = observationMapper.selectList(
                Wrappers.<InterviewAbilityObservation>lambdaQuery()
                        .eq(InterviewAbilityObservation::getEmpId, empId)
                        .eq(InterviewAbilityObservation::getIsDeleted, 0)
                        .orderByDesc(InterviewAbilityObservation::getCreatedTime)
        );

        if (observations.isEmpty()) {
            return List.of();
        }

        // 获取最新会话ID
        Long latestSessionId = observations.get(0).getSessionId();

        // 返回该会话的所有观察
        return observations.stream()
                .filter(o -> latestSessionId.equals(o.getSessionId()))
                .toList();
    }

    private List<PersonAbilityClaim> loadAdmittedClaims(Long empId) {
        return claimMapper.selectList(Wrappers.<PersonAbilityClaim>lambdaQuery()
                .eq(PersonAbilityClaim::getEmpId, empId)
                .in(PersonAbilityClaim::getStatus, "PENDING_HARNESS_REVIEW", "READY_FOR_FUSION", "FUSED")
                .eq(PersonAbilityClaim::getIsDeleted, 0));
    }
}
