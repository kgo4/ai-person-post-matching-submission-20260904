package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.learning.LearningOutcomeSubmissionResponse;
import com.example.matching.dto.learning.LearningOutcomeSubmitDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.notification.SysNotificationService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 员工学习成果 HR 复核闭环（HR 匹配闭环设计 P4）。
 *
 * <p>核心设计（docs/hr-matching-closed-loop-design.md 4.2 节）：</p>
 * <ul>
 *   <li><b>提交与回写解耦</b>：员工提交只落「提交单」，处于「待复核」态，**不回写能力证据**；</li>
 *   <li><b>复核通过才回写</b>：通过时回调既有
 *       {@link CapabilityClosureService#onLearningOutcomeConfirmed}，回写链路一行不改；</li>
 *   <li><b>驳回可重提</b>：驳回附理由退回，员工修改后重新提交会产生新的提交单，旧单留痕；</li>
 *   <li><b>不做重复提交</b>：同一员工 + 同一能力项存在待复核单时拒绝再次提交（应用层幂等，
 *       因为 tag_id 可为 NULL 而 MySQL 唯一索引对 NULL 不生效）。</li>
 * </ul>
 *
 * <p>通知走 P1 通知中心（{@code TYPE_LEARNING_REVIEW} + {@code BIZ_LEARNING_OUTCOME}），
 * 发送失败只记日志、绝不阻断复核主事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmpLearningOutcomeReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmpLearningOutcomeSubmissionMapper submissionMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final CapabilityClosureService capabilityClosureService;
    private final SysNotificationService notificationService;
    private final MatchingRecordService matchingRecordService;

    /* ===================== 员工侧 ===================== */

    /**
     * 提交学习成果（进入待复核，不回写能力证据）。
     *
     * @param empId 当前登录账号绑定的人员档案ID（由调用方按登录身份固定）
     * @return 提交单ID
     */
    @Transactional
    public Long submit(Long empId, LearningOutcomeSubmitDTO dto) {
        if (empId == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(),
                    "当前账号未绑定人员档案，无法提交学习成果");
        }
        if (dto.getConfirmedLevel() == null
                || dto.getConfirmedLevel() < 1 || dto.getConfirmedLevel() > 5) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "自评达成等级必须在 1-5 之间");
        }
        if (dto.getTagId() == null && !StringUtils.hasText(dto.getAbilityName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "请指定能力项（tagId 与 abilityName 至少提供一个）");
        }
        assertNoPendingDuplicate(empId, dto);

        EmpLearningOutcomeSubmission entity = new EmpLearningOutcomeSubmission();
        entity.setEmpId(empId);
        entity.setMatchingRecordId(dto.getMatchingRecordId());
        entity.setTagId(dto.getTagId());
        entity.setAbilityName(dto.getAbilityName());
        entity.setCompletedResourceId(dto.getCompletedResourceId());
        entity.setBeforeLevel(dto.getBeforeLevel());
        entity.setConfirmedLevel(dto.getConfirmedLevel());
        entity.setNote(dto.getNote());
        entity.setAiSuggestionId(dto.getAiSuggestionId());
        entity.setRagChunkIds(dto.getRagChunkIds());
        entity.setAiSuggestionVersion(dto.getAiSuggestionVersion());
        entity.setReviewStatus(EmpLearningOutcomeSubmission.STATUS_PENDING);
        entity.setCreatedBy(SecurityUtils.getCurrentUserId());
        submissionMapper.insert(entity);

        notifyReviewerQuietly(entity);
        log.info("学习成果已提交待复核: submissionId={}, empId={}, tagId={}, abilityName={}",
                entity.getId(), empId, entity.getTagId(), entity.getAbilityName());
        return entity.getId();
    }

    /** 本人提交记录（含复核状态与理由） */
    public PageResponse<LearningOutcomeSubmissionResponse> pageMy(Long empId, long current, long size) {
        Page<EmpLearningOutcomeSubmission> page = new Page<>(safeCurrent(current), safeSize(size));
        if (empId == null) {
            return toPageResponse(page);
        }
        return toPageResponse(submissionMapper.selectPage(page,
                Wrappers.<EmpLearningOutcomeSubmission>lambdaQuery()
                        .eq(EmpLearningOutcomeSubmission::getEmpId, empId)
                        .orderByDesc(EmpLearningOutcomeSubmission::getCreatedTime)));
    }

    /* ===================== HR 侧 ===================== */

    /** HR 复核列表：待复核优先（review_status 升序，0 待复核排最前），同状态按提交时间倒序 */
    public PageResponse<LearningOutcomeSubmissionResponse> pageForReview(long current, long size, Integer reviewStatus) {
        LambdaQueryWrapper<EmpLearningOutcomeSubmission> wrapper =
                Wrappers.<EmpLearningOutcomeSubmission>lambdaQuery();
        if (reviewStatus != null) {
            wrapper.eq(EmpLearningOutcomeSubmission::getReviewStatus, reviewStatus);
        }
        wrapper.orderByAsc(EmpLearningOutcomeSubmission::getReviewStatus)
                .orderByDesc(EmpLearningOutcomeSubmission::getCreatedTime);
        return toPageResponse(submissionMapper.selectPage(
                new Page<>(safeCurrent(current), safeSize(size)), wrapper));
    }

    /**
     * 复核通过：回调既有回写链路，能力证据升级、能力画像自动更新。
     *
     * <p>回写与状态更新在同一事务内 —— 回写失败则整体回滚，不会出现
     * 「已标记通过但能力没更新」的不一致。</p>
     */
    @Transactional
    public Long approve(Long id, String comment, Long reviewerUserId) {
        EmpLearningOutcomeSubmission submission = requirePending(id);

        // 复用既有回写链路：不改动 confirmLearningOutcome 的任何实现
        CapabilityClosureResult closureResult =
                capabilityClosureService.onLearningOutcomeConfirmed(toConfirmDTO(submission));

        EmpLearningOutcomeSubmission update = new EmpLearningOutcomeSubmission();
        update.setId(id);
        update.setReviewStatus(EmpLearningOutcomeSubmission.STATUS_APPROVED);
        update.setReviewComment(comment);
        update.setReviewedBy(reviewerUserId);
        update.setReviewedTime(LocalDateTime.now());
        update.setClosureBusinessKey(closureResult == null ? null : closureResult.getBusinessKey());
        submissionMapper.updateById(update);

        notifyEmployeeQuietly(submission, true, comment);
        log.info("学习成果复核通过: submissionId={}, empId={}, reviewer={}, businessKey={}",
                id, submission.getEmpId(), reviewerUserId,
                closureResult == null ? null : closureResult.getBusinessKey());
        return id;
    }

    /** 复核驳回：附理由退回员工，不回写能力证据 */
    @Transactional
    public void reject(Long id, String comment, Long reviewerUserId) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "驳回理由不能为空");
        }
        EmpLearningOutcomeSubmission submission = requirePending(id);

        EmpLearningOutcomeSubmission update = new EmpLearningOutcomeSubmission();
        update.setId(id);
        update.setReviewStatus(EmpLearningOutcomeSubmission.STATUS_REJECTED);
        update.setReviewComment(comment);
        update.setReviewedBy(reviewerUserId);
        update.setReviewedTime(LocalDateTime.now());
        submissionMapper.updateById(update);

        notifyEmployeeQuietly(submission, false, comment);
        log.info("学习成果复核驳回: submissionId={}, empId={}, reviewer={}", id, submission.getEmpId(), reviewerUserId);
    }

    /** 各状态的待办计数（HR 工作台 / 复核页角标） */
    public long countByReviewStatus(Integer reviewStatus) {
        LambdaQueryWrapper<EmpLearningOutcomeSubmission> wrapper =
                Wrappers.<EmpLearningOutcomeSubmission>lambdaQuery();
        if (reviewStatus != null) {
            wrapper.eq(EmpLearningOutcomeSubmission::getReviewStatus, reviewStatus);
        }
        return submissionMapper.selectCount(wrapper);
    }

    /* ===================== 内部 ===================== */

    /**
     * 转视图并补全员工姓名。HR 复核列表需要直接展示姓名，
     * 这里一次性批量补全，避免前端逐条查档或后端 N+1。
     */
    private PageResponse<LearningOutcomeSubmissionResponse> toPageResponse(Page<EmpLearningOutcomeSubmission> page) {
        List<EmpLearningOutcomeSubmission> records = page.getRecords();
        Map<Long, String> empNames = loadEmpNames(records.stream()
                .map(EmpLearningOutcomeSubmission::getEmpId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<LearningOutcomeSubmissionResponse> views = records.stream()
                .map(s -> toResponse(s, empNames.get(s.getEmpId())))
                .toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private Map<Long, String> loadEmpNames(List<Long> empIds) {
        if (empIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (EmpEmployee employee : empEmployeeMapper.selectBatchIds(empIds)) {
            names.put(employee.getId(), employee.getRealName());
        }
        return names;
    }

    private LearningOutcomeSubmissionResponse toResponse(EmpLearningOutcomeSubmission s, String empName) {
        return new LearningOutcomeSubmissionResponse(
                s.getId(), s.getEmpId(), empName, s.getMatchingRecordId(), s.getTagId(), s.getAbilityName(),
                s.getCompletedResourceId(), s.getBeforeLevel(), s.getConfirmedLevel(), s.getNote(),
                s.getReviewStatus(), s.getReviewComment(), s.getReviewedBy(), s.getReviewedTime(),
                s.getClosureBusinessKey(), s.getCreatedTime());
    }

    /**
     * 通知失败绝不阻断主流程（设计文档风险条目 3）。
     * {@code SysNotificationService.send} 内部已兜底异常，这里再包一层，
     * 保证即使通知实现被替换或抛错，提交/复核本身仍然成功。
     */
    private void notifyReviewerQuietly(EmpLearningOutcomeSubmission submission) {
        try {
            notifyReviewer(submission);
        } catch (Exception e) {
            log.warn("学习成果待复核通知发送失败（不阻断提交）: submissionId={}", submission.getId(), e);
        }
    }

    private void notifyEmployeeQuietly(EmpLearningOutcomeSubmission submission, boolean approved, String comment) {
        try {
            notifyEmployee(submission, approved, comment);
        } catch (Exception e) {
            log.warn("学习成果复核结果通知发送失败（不阻断复核）: submissionId={}", submission.getId(), e);
        }
    }

    private void assertNoPendingDuplicate(Long empId, LearningOutcomeSubmitDTO dto) {
        LambdaQueryWrapper<EmpLearningOutcomeSubmission> wrapper =
                Wrappers.<EmpLearningOutcomeSubmission>lambdaQuery()
                        .eq(EmpLearningOutcomeSubmission::getEmpId, empId)
                        .eq(EmpLearningOutcomeSubmission::getReviewStatus,
                                EmpLearningOutcomeSubmission.STATUS_PENDING);
        if (dto.getTagId() != null) {
            wrapper.eq(EmpLearningOutcomeSubmission::getTagId, dto.getTagId());
        } else {
            wrapper.eq(EmpLearningOutcomeSubmission::getAbilityName, dto.getAbilityName());
        }
        Long exists = submissionMapper.selectCount(wrapper);
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "该能力项已有一条待复核的提交，请等待 HR 复核结果");
        }
    }

    private EmpLearningOutcomeSubmission requirePending(Long id) {
        EmpLearningOutcomeSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND.getCode(), "学习成果提交单不存在");
        }
        if (submission.getReviewStatus() == null
                || submission.getReviewStatus() != EmpLearningOutcomeSubmission.STATUS_PENDING) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT.getCode(),
                    "该提交单已被复核，请刷新后查看");
        }
        return submission;
    }

    /** 提交单 → 既有回写链路入参（字段一一对应，不改变回写语义） */
    private LearningOutcomeConfirmDTO toConfirmDTO(EmpLearningOutcomeSubmission s) {
        LearningOutcomeConfirmDTO dto = new LearningOutcomeConfirmDTO();
        dto.setEmpId(s.getEmpId());
        dto.setTagId(s.getTagId());
        dto.setAbilityName(s.getAbilityName());
        dto.setCompletedResourceId(s.getCompletedResourceId());
        dto.setBeforeLevel(s.getBeforeLevel());
        dto.setConfirmedLevel(s.getConfirmedLevel());
        dto.setConfirmationSource("HR_REVIEW");
        dto.setNote(s.getNote());
        dto.setAiSuggestionId(s.getAiSuggestionId());
        dto.setRagChunkIds(s.getRagChunkIds());
        dto.setAiSuggestionVersion(s.getAiSuggestionVersion());
        return dto;
    }

    /**
     * 通知 HR 有待复核内容。
     *
     * <p>收件人取该员工关联匹配记录的发起 HR（{@code matching_record.created_by}）——
     * 这是「谁在负责这名员工」最直接的依据。没有关联匹配记录时跳过通知，
     * HR 仍可在复核列表页看到（列表按待复核优先排序）。</p>
     */
    private void notifyReviewer(EmpLearningOutcomeSubmission submission) {
        Long receiverUserId = resolveReviewerUserId(submission);
        if (receiverUserId == null) {
            return;
        }
        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(receiverUserId);
        notification.setType(SysNotification.TYPE_LEARNING_REVIEW);
        notification.setTitle("待复核学习成果");
        notification.setContent("有员工提交了学习成果（能力项：" + abilityLabel(submission)
                + "），请在「学习成果复核」中处理。");
        notification.setBizType(SysNotification.BIZ_LEARNING_OUTCOME);
        notification.setBizId(submission.getId());
        notification.setCreatedBy(submission.getCreatedBy());
        notificationService.send(notification);
    }

    private Long resolveReviewerUserId(EmpLearningOutcomeSubmission submission) {
        if (submission.getMatchingRecordId() == null) {
            return null;
        }
        MatchingRecord record = matchingRecordService.getById(submission.getMatchingRecordId());
        return record == null ? null : record.getCreatedBy();
    }

    private void notifyEmployee(EmpLearningOutcomeSubmission submission, boolean approved, String comment) {
        EmpEmployee employee = empEmployeeMapper.selectById(submission.getEmpId());
        if (employee == null || employee.getUserId() == null) {
            return;
        }
        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(employee.getUserId());
        notification.setType(SysNotification.TYPE_LEARNING_REVIEW);
        notification.setTitle(approved ? "学习成果已通过复核" : "学习成果被驳回");
        notification.setContent(approved
                ? "你的学习成果（能力项：" + abilityLabel(submission) + "）已通过 HR 复核，能力画像已更新。"
                : "你的学习成果（能力项：" + abilityLabel(submission) + "）被驳回：" + comment);
        notification.setBizType(SysNotification.BIZ_LEARNING_OUTCOME);
        notification.setBizId(submission.getId());
        notification.setCreatedBy(submission.getReviewedBy());
        notificationService.send(notification);
    }

    private String abilityLabel(EmpLearningOutcomeSubmission submission) {
        if (StringUtils.hasText(submission.getAbilityName())) {
            return submission.getAbilityName();
        }
        return submission.getTagId() == null ? "未知能力" : ("#" + submission.getTagId());
    }

    private long safeCurrent(long current) {
        return Math.max(current, 1);
    }

    private long safeSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}


