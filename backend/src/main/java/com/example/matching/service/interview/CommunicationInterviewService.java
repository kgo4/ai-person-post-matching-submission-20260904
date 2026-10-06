package com.example.matching.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.enums.MatchStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.interview.CommunicationInterviewResponse;
import com.example.matching.dto.interview.CreateInterviewRequest;
import com.example.matching.dto.interview.InterviewBriefingResponse;
import com.example.matching.dto.interview.InterviewCandidateResponse;
import com.example.matching.entity.assessment.report.EmpCapabilityAnalysisReport;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.interview.EmpCommunicationInterview;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.event.CommunicationInterviewCreatedEvent;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.interview.EmpCommunicationInterviewMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import com.example.matching.service.closure.ComprehensiveDiagnosisService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.notification.SysNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HR-员工视频终面服务（HR 匹配闭环设计 P5）。
 *
 * <p>路线（需求方确认）：**跳转讯飞会议**，平台不做媒体，只管
 * 「发起邀请 → 通知 → 留痕 → 定论」。会议链接由 HR 手动创建后粘贴，平台零集成零凭据。</p>
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li><b>HR 手选，匹配结论只作推荐</b>：平台不自动发起沟通；候选池是**全部匹配结果**，
 *       匹配通过（强适配/适配）的排在前并打「推荐」标记，但**不是发起的必要前提**；</li>
 *   <li><b>沟通要点实时聚合、不落库</b>：发起时以快照形式随记录留存；</li>
 *   <li><b>员工侧只读</b>：不提供任何员工侧写接口，员工只能消费通知链接；</li>
 *   <li><b>终面完成以 HR 手动录入为准</b> —— 平台无法感知员工是否真的入会。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationInterviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_GAP_ABILITIES = 10;
    /** 员工放弃原因列宽（VARCHAR(500)），与 V164 迁移保持一致 */
    private static final int MAX_RESPONSE_COMMENT_LENGTH = 500;
    /** sys_notification.content 列宽（VARCHAR(512)），留一点余量 */
    private static final int MAX_NOTIFICATION_CONTENT_LENGTH = 500;
    private static final String SOURCE_MANUAL = "MANUAL";

    /**
     * 从粘贴内容中抽取 http(s) 链接。
     *
     * <p>讯飞会议「复制邀请信息」给出的不是裸链接，而是一段多行文本，例如：</p>
     * <pre>
     * 王永峰 邀请您加入【20260904-2104远程会议】
     * 点击链接直接加入会议: https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e
     * 会议号: 11626686 会议密码: 123456
     * 最新版本下载地址: https://meeting.iflyrec.com/download.html
     * </pre>
     * <p>HR 会直接把整段粘贴进来，所以这里按「抽取」而不是「整体校验」处理：
     * 找出所有链接，只保留域名命中白名单的，再优先取「入会链接」。</p>
     */
    // 限定为可打印 ASCII：URL 按 RFC 3986 只用 ASCII 字符集，
    // 这样链接后紧跟的中文说明/中文标点（如「，准时参加」）会自然截断，
    // 不会像 \S+ 那样把整句中文一起吞进链接里。
    private static final Pattern HTTP_URL_PATTERN =
            Pattern.compile("https?://[\\x21-\\x7e]+", Pattern.CASE_INSENSITIVE);

    /** 链接尾部常见的标点：整段粘贴时链接后面常紧跟句读或右括号，抽取后必须剥掉 */
    private static final String TRAILING_PUNCTUATION = ",;:.!?)]}>\"'";

    /** 入会链接的路径特征，用于在同一白名单域名下区分「入会」与「客户端下载」 */
    private static final String JOIN_PATH_HINT = "join";

    private final EmpCommunicationInterviewMapper interviewMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final MatchingRecordService matchingRecordService;
    private final MatchingDataQueryService dataQuery;
    private final CapabilityAnalysisReportService reportService;
    private final ComprehensiveDiagnosisService comprehensiveDiagnosisService;
    private final EmpLearningOutcomeSubmissionMapper outcomeMapper;
    private final SysNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 允许的会议域名白名单，逗号分隔。
     * <p>换会议产品只改配置不改代码。默认放行讯飞听见会议
     * （链接形如 {@code https://meeting.iflyrec.com/meeting/join?<会议码>}）。</p>
     */
    @Value("${communication-interview.allowed-meeting-hosts:meeting.iflyrec.com}")
    private String allowedMeetingHosts;

    /* ===================== 推荐池 ===================== */

    /**
     * 「可发起沟通」候选池：全部匹配记录（**不限匹配状态与推送状态**）。
     *
     * <p>2026-09-04 口径变更：原实现只返回「已推送 + 强适配/适配」的记录，
     * 使 HR 在员工尚未匹配通过时**根本无法找到该员工**去发起沟通。
     * 现在要求是「HR 可以直接选择匹配结果发起邀约，匹配通过只是会向 HR 推荐」，
     * 因此候选池放开为全部记录，通过 {@code recommended} 标记哪些值得优先考虑。</p>
     *
     * <p>推荐 = 匹配状态为强适配/适配（即「匹配通过」），排序时推荐项排在前；
     * 推荐与否**不影响能否发起**，只影响提示。</p>
     *
     * @param limit 最大条数（服务层做上下界保护）
     */
    public List<InterviewCandidateResponse> listCandidates(int limit) {
        List<MatchingRecord> records = matchingRecordService.listRecentRecords(limit);
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> empIds = records.stream()
                .map(MatchingRecord::getEmpId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<Long> pendingEmpIds = new HashSet<>();
        Map<Long, Integer> latestResultByEmp = new HashMap<>();
        if (!empIds.isEmpty()) {
            interviewMapper.selectList(Wrappers.<EmpCommunicationInterview>lambdaQuery()
                    .in(EmpCommunicationInterview::getEmpId, empIds)
                    .eq(EmpCommunicationInterview::getStatus, EmpCommunicationInterview.STATUS_PENDING))
                .forEach(item -> pendingEmpIds.add(item.getEmpId()));
            interviewMapper.selectList(Wrappers.<EmpCommunicationInterview>lambdaQuery()
                    .in(EmpCommunicationInterview::getEmpId, empIds)
                    .eq(EmpCommunicationInterview::getStatus, EmpCommunicationInterview.STATUS_FINISHED)
                    .orderByDesc(EmpCommunicationInterview::getFinishedTime))
                .forEach(item -> latestResultByEmp.putIfAbsent(item.getEmpId(), item.getResult()));
        }

        // 推荐项排前，其余保持「最近更新在前」。用稳定排序（stream.sorted 是稳定的），
        // 所以组内顺序仍是 updatedTime 倒序，HR 看到的优先级不会被搅乱。
        return records.stream()
                .sorted(Comparator.comparing((MatchingRecord r) -> !isRecommended(r)))
                .map(record -> new InterviewCandidateResponse(
                        record.getEmpId(),
                        record.getEmpName(),
                        record.getPostId(),
                        record.getPostName(),
                        record.getId(),
                        record.getMatchStatus(),
                        record.getMatchStatus() == null
                                ? null : MatchStatusEnum.getNameByCode(record.getMatchStatus()),
                        isRecommended(record),
                        record.getFinalMatchScore() != null
                                ? record.getFinalMatchScore() : record.getAiMatchScore(),
                        pendingEmpIds.contains(record.getEmpId()),
                        latestResultByEmp.get(record.getEmpId())))
                .toList();
    }

    /** 「推荐」= 匹配结论为强适配/适配（即需求里的「匹配通过」），仅作提示，不作前置条件 */
    private boolean isRecommended(MatchingRecord record) {
        Integer status = record.getMatchStatus();
        return status != null && (status == MatchStatusEnum.STRONG_MATCH.getCode()
                || status == MatchStatusEnum.MATCH.getCode());
    }


    /* ===================== 沟通要点文档 ===================== */

    /**
     * 真实沟通要点（四区）。实时聚合、不落库；发起沟通时以快照形式随记录留存。
     *
     * @param matchingRecordId 可空；为空时取该员工最近一条「已通过」记录作为匹配概况来源
     */
    public InterviewBriefingResponse buildBriefing(Long empId, Long matchingRecordId) {
        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        String empName = employee == null ? null : employee.getRealName();

        MatchingRecord record = resolveMatchingRecord(empId, matchingRecordId);
        InterviewBriefingResponse.AbilityOverview ability = buildAbilityOverview(empId);
        InterviewBriefingResponse.MatchOverview match = buildMatchOverview(record);
        InterviewBriefingResponse.GapAndLearning gap = buildGapAndLearning(empId, matchingRecordId);
        List<String> talkingPoints = buildTalkingPoints(ability, match, gap);
        String plainText = renderPlainText(empName, record == null ? null : record.getPostName(),
                ability, match, gap, talkingPoints);

        return new InterviewBriefingResponse(
                empId, empName,
                record == null ? null : record.getPostId(),
                record == null ? null : record.getPostName(),
                ability, match, gap, talkingPoints, plainText);
    }

    private MatchingRecord resolveMatchingRecord(Long empId, Long matchingRecordId) {
        if (matchingRecordId != null) {
            MatchingRecord record = matchingRecordService.getDetailById(matchingRecordId);
            if (record != null) {
                return record;
            }
        }
        List<MatchingRecord> passed = matchingRecordService.listPassedPosts(empId);
        return passed.isEmpty() ? null : passed.get(0);
    }

    /** 一、能力概况：直读 P2 报告快照；报告缺失时降级为空统计并说明原因 */
    private InterviewBriefingResponse.AbilityOverview buildAbilityOverview(Long empId) {
        EmpCapabilityAnalysisReport report = reportService.latestByEmp(empId);
        if (report == null) {
            return new InterviewBriefingResponse.AbilityOverview(
                    "尚未生成全面能力分析报告（能力项审核可能未全部完成），建议先完成评估与审核再沟通。",
                    null, 0, 0, 0, List.of());
        }
        return new InterviewBriefingResponse.AbilityOverview(
                report.getSummary(),
                report.getVersionNo(),
                parseArray(report.getAutoPassedJson()).size(),
                parseArray(report.getManualConfirmedJson()).size(),
                parseArray(report.getManualRejectedJson()).size(),
                extractAbilityNames(report.getManualRejectedJson()));
    }

    /** 二、匹配概况 */
    private InterviewBriefingResponse.MatchOverview buildMatchOverview(MatchingRecord record) {
        if (record == null) {
            return new InterviewBriefingResponse.MatchOverview(null, null, null, null);
        }
        return new InterviewBriefingResponse.MatchOverview(
                record.getFinalMatchScore(),
                record.getAiMatchScore(),
                record.getMatchStatus(),
                record.getMatchStatus() == null
                        ? null : MatchStatusEnum.getNameByCode(record.getMatchStatus()));
    }

    /** 三、差距与学习：差距项（复用既有综合诊断）+ 学习成果复核进度（P4 数据） */
    private InterviewBriefingResponse.GapAndLearning buildGapAndLearning(Long empId, Long matchingRecordId) {
        List<String> gapAbilities = new ArrayList<>();
        if (matchingRecordId != null) {
            try {
                ComprehensiveDiagnosisResultDTO diagnosis =
                        comprehensiveDiagnosisService.diagnose(matchingRecordId);
                ComprehensiveDiagnosisFactDTO factPackage =
                        diagnosis == null ? null : diagnosis.getFactPackage();
                if (factPackage != null && factPackage.getAbilityGaps() != null) {
                    gapAbilities = factPackage.getAbilityGaps().stream()
                            .map(ComprehensiveDiagnosisFactDTO.AbilityGapFact::getAbilityName)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .limit(MAX_GAP_ABILITIES)
                            .toList();
                }
            } catch (Exception e) {
                // 辅助材料：诊断读取失败不阻断沟通要点生成
                log.warn("沟通要点：差距诊断读取失败（降级为空）: recordId={}", matchingRecordId, e);
            }
        }
        return new InterviewBriefingResponse.GapAndLearning(
                gapAbilities.size(),
                gapAbilities,
                countOutcome(empId, EmpLearningOutcomeSubmission.STATUS_APPROVED),
                countOutcome(empId, EmpLearningOutcomeSubmission.STATUS_PENDING),
                countOutcome(empId, EmpLearningOutcomeSubmission.STATUS_REJECTED));
    }

    /** 四、建议沟通要点：由前三区数据推导，纯规则、不调 LLM（保证可预期与低延迟） */
    private List<String> buildTalkingPoints(InterviewBriefingResponse.AbilityOverview ability,
                                            InterviewBriefingResponse.MatchOverview match,
                                            InterviewBriefingResponse.GapAndLearning gap) {
        List<String> points = new ArrayList<>();
        if (ability.manualRejectedCount() > 0 && !ability.rejectedAbilities().isEmpty()) {
            points.add("待澄清能力项：" + String.join("、", ability.rejectedAbilities())
                    + "（审核未通过，沟通中确认实际掌握程度与补充证据的意愿）");
        }
        if (gap.gapCount() > 0) {
            points.add("需重点确认的差距项：" + String.join("、", gap.gapAbilities())
                    + "（共 " + gap.gapCount() + " 项）");
        }
        if (gap.pendingOutcomeCount() > 0) {
            points.add("有 " + gap.pendingOutcomeCount() + " 项学习成果待复核，沟通中确认学习投入与完成质量");
        }
        if (gap.rejectedOutcomeCount() > 0) {
            points.add("有 " + gap.rejectedOutcomeCount() + " 项学习成果被驳回，确认员工的学习方式与改进计划");
        }
        if (match.matchStatus() != null && match.matchStatus() == MatchStatusEnum.MATCH.getCode()) {
            points.add("匹配状态为「适配」而非「强适配」，沟通中确认岗位期望与个人发展诉求是否一致");
        }
        if (points.isEmpty()) {
            points.add("该员工能力与匹配数据均无明显风险点，按常规入职前沟通进行");
        }
        return points;
    }

    private String renderPlainText(String empName, String postName,
                                   InterviewBriefingResponse.AbilityOverview ability,
                                   InterviewBriefingResponse.MatchOverview match,
                                   InterviewBriefingResponse.GapAndLearning gap,
                                   List<String> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("真实沟通要点 | ").append(text(empName)).append(" → ").append(text(postName)).append('\n');
        sb.append("\n【一、能力概况】\n").append(text(ability.summary())).append('\n');
        sb.append("harness 自动通过 ").append(ability.autoPassedCount()).append(" 项，")
                .append("人工确认通过 ").append(ability.manualConfirmedCount()).append(" 项，")
                .append("人工拒绝 ").append(ability.manualRejectedCount()).append(" 项\n");
        sb.append("\n【二、匹配概况】\n");
        sb.append("最终匹配分：").append(match.finalMatchScore() == null ? "--" : match.finalMatchScore()).append('\n');
        sb.append("匹配状态：").append(text(match.matchStatusName())).append('\n');
        sb.append("\n【三、差距与学习】\n");
        sb.append("主要差距项：")
                .append(gap.gapAbilities().isEmpty() ? "无" : String.join("、", gap.gapAbilities())).append('\n');
        sb.append("学习成果：已通过 ").append(gap.approvedOutcomeCount())
                .append(" 项，待复核 ").append(gap.pendingOutcomeCount())
                .append(" 项，被驳回 ").append(gap.rejectedOutcomeCount()).append(" 项\n");
        sb.append("\n【四、建议沟通要点】\n");
        for (int i = 0; i < points.size(); i++) {
            sb.append(i + 1).append(". ").append(points.get(i)).append('\n');
        }
        return sb.toString();
    }

    /* ===================== 发起 / 取消 / 录入结论 ===================== */

    /** HR 发起视频终面：校验员工存在 + 会议域名白名单（匹配记录仅做存在性校验，不作结论前置） */
    @Transactional
    public Long create(CreateInterviewRequest req, Long operatorUserId) {
        EmpEmployee employee = empEmployeeMapper.selectById(req.getEmpId());
        if (employee == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }
        String meetingUrl = normalizeAndValidateMeetingUrl(req.getMeetingUrl());

        MatchingRecord record = null;
        if (req.getMatchingRecordId() != null) {
            record = matchingRecordService.getById(req.getMatchingRecordId());
            if (record == null) {
                throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
            }
            // 【2026-09-04 口径变更】不再要求匹配状态为强适配/适配。
            // 原实现把「匹配通过」当成发起邀约的前置条件，导致 HR 想先聊一聊再说时被拦；
            // 现在匹配结论只用于推荐排序，不构成准入。仅保留记录存在性校验，
            // 避免把不存在的 recordId 写进终面记录（脏引用）。
        }

        EmpCommunicationInterview entity = new EmpCommunicationInterview();
        entity.setEmpId(req.getEmpId());
        entity.setPostId(req.getPostId() != null
                ? req.getPostId() : (record == null ? null : record.getPostId()));
        entity.setMatchingRecordId(req.getMatchingRecordId());
        entity.setMeetingUrl(meetingUrl);
        entity.setMeetingSource(SOURCE_MANUAL);
        entity.setScheduledTime(req.getScheduledTime());
        entity.setStatus(EmpCommunicationInterview.STATUS_PENDING);
        entity.setCreatedBy(operatorUserId);
        // 沟通要点快照**不在这里同步生成**：它要跨模块读能力报告、跑差距诊断，属于辅助材料，
        // 却会让「发起邀约」这个用户可感知的主流程跟着一起等，且它的异常会污染发起结果
        // （HR 看到「发起失败」，真正原因却与会议链接毫无关系）。
        // 改为先把记录落库，事务提交后由 CommunicationInterviewBriefingListener 异步回写。
        interviewMapper.insert(entity);

        notifyInviteQuietly(entity, employee);
        eventPublisher.publishEvent(new CommunicationInterviewCreatedEvent(
                entity.getId(), req.getEmpId(), req.getMatchingRecordId()));
        log.info("视频终面已发起: id={}, empId={}, operator={}", entity.getId(), req.getEmpId(), operatorUserId);
        return entity.getId();
    }

    /** HR 取消（仅待沟通状态） */
    @Transactional
    public void cancel(Long id) {
        EmpCommunicationInterview entity = requireExisting(id);
        if (entity.getStatus() == null || entity.getStatus() != EmpCommunicationInterview.STATUS_PENDING) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT.getCode(), "只有待沟通的终面可以取消");
        }
        EmpCommunicationInterview update = new EmpCommunicationInterview();
        update.setId(id);
        update.setStatus(EmpCommunicationInterview.STATUS_CANCELLED);
        interviewMapper.updateById(update);
    }

    /**
     * 员工响应终面邀请：接受 / 放弃。
     *
     * <p>这是**员工侧唯一的写接口**。口径：</p>
     * <ul>
     *   <li><b>只能响应对应本人的终面</b>：empId 归属在服务层强校验，不依赖前端传参；</li>
     *   <li><b>响应一次性</b>：已响应过就不再接受第二次（重复响应会让给 HR 的站内通知
     *       撞上 (receiver, type, bizType, bizId) 防重唯一键而静默丢失，且状态反复横跳对 HR 无意义）。
     *       员工若反悔，由 HR 取消后重新发起一场，与既有「链接失效即重新发起」的口径一致；</li>
     *   <li><b>放弃 = 取消该场终面</b>：直接置为已取消并保留放弃原因，语义上区别于 HR 主动取消；</li>
     *   <li><b>接受只记录意愿</b>：状态仍为待沟通，等 HR 面试后录入结论。</li>
     * </ul>
     *
     * @param interviewId 终面记录ID
     * @param operatorEmpId 当前登录账号关联的员工档案ID
     * @param response 1接受 / 2放弃
     * @param comment 放弃原因（可选，仅放弃时有意义）
     */
    @Transactional
    public void respond(Long interviewId, Long operatorEmpId, Integer response, String comment) {
        EmpCommunicationInterview entity = requireExisting(interviewId);
        if (operatorEmpId == null || !operatorEmpId.equals(entity.getEmpId())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "无权响应该终面邀请");
        }
        if (response == null || (response != EmpCommunicationInterview.RESPONSE_ACCEPTED
                && response != EmpCommunicationInterview.RESPONSE_DECLINED)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "响应只能是 1（接受）或 2（放弃）");
        }
        if (entity.getEmployeeResponse() != null) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT.getCode(),
                    "你已响应过本次终面；如需变更，请联系 HR 重新发起邀请");
        }
        if (entity.getStatus() == null || entity.getStatus() != EmpCommunicationInterview.STATUS_PENDING) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT.getCode(),
                    "该终面已结束或已取消，无法再响应");
        }
        String normalizedComment = normalizeResponseComment(comment);

        EmpCommunicationInterview update = new EmpCommunicationInterview();
        update.setId(interviewId);
        update.setEmployeeResponse(response);
        update.setEmployeeResponseComment(normalizedComment);
        update.setEmployeeRespondedTime(LocalDateTime.now());
        if (response == EmpCommunicationInterview.RESPONSE_DECLINED) {
            update.setStatus(EmpCommunicationInterview.STATUS_CANCELLED);
        }
        interviewMapper.updateById(update);

        notifyResponseQuietly(entity, response, normalizedComment);
        log.info("视频终面员工已响应: id={}, empId={}, response={}", interviewId, entity.getEmpId(), response);
    }

    /** 放弃原因：去空白；超长按列宽截断，避免写库直接报「Data too long」把员工操作判成失败 */
    private String normalizeResponseComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() <= MAX_RESPONSE_COMMENT_LENGTH
                ? trimmed : trimmed.substring(0, MAX_RESPONSE_COMMENT_LENGTH);
    }

    /**
     * HR 录入结论。
     * <p>结论为「待定」时状态保持待沟通（可改期再谈）；其余结论置为已完成。</p>
     */
    @Transactional
    public void recordResult(Long id, Integer result, String comment) {
        EmpCommunicationInterview entity = requireExisting(id);
        if (entity.getStatus() == null || entity.getStatus() != EmpCommunicationInterview.STATUS_PENDING) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT.getCode(), "该终面已处理，无法重复录入结论");
        }
        if (result == null || (result != EmpCommunicationInterview.RESULT_PASS
                && result != EmpCommunicationInterview.RESULT_FAIL
                && result != EmpCommunicationInterview.RESULT_UNDECIDED)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "结论只能是 1（通过）/ 2（不通过）/ 3（待定）");
        }

        EmpCommunicationInterview update = new EmpCommunicationInterview();
        update.setId(id);
        update.setResult(result);
        update.setComment(comment);
        update.setFinishedTime(LocalDateTime.now());
        update.setStatus(result == EmpCommunicationInterview.RESULT_UNDECIDED
                ? EmpCommunicationInterview.STATUS_PENDING
                : EmpCommunicationInterview.STATUS_FINISHED);
        interviewMapper.updateById(update);

        notifyResultQuietly(entity, result, comment);
        log.info("视频终面结论已录入: id={}, empId={}, result={}", id, entity.getEmpId(), result);
    }

    /* ===================== 查询 ===================== */

    /** HR 追踪视图数据源 */
    public PageResponse<CommunicationInterviewResponse> page(long current, long size, Long empId, Integer status) {
        LambdaQueryWrapper<EmpCommunicationInterview> wrapper =
                Wrappers.<EmpCommunicationInterview>lambdaQuery();
        if (empId != null) {
            wrapper.eq(EmpCommunicationInterview::getEmpId, empId);
        }
        if (status != null) {
            wrapper.eq(EmpCommunicationInterview::getStatus, status);
        }
        wrapper.orderByDesc(EmpCommunicationInterview::getCreatedTime);
        return toPageResponse(interviewMapper.selectPage(
                new Page<>(safeCurrent(current), safeSize(size)), wrapper));
    }

    /** 员工查本人终面记录（含结论与 HR 评价原文） */
    public PageResponse<CommunicationInterviewResponse> pageMy(Long empId, long current, long size) {
        if (empId == null) {
            return new PageResponse<>(List.of(), 0, safeCurrent(current), safeSize(size), 0);
        }
        return page(current, size, empId, null);
    }

    /** 待沟通数量（HR 侧待办角标） */
    public long countPending() {
        return interviewMapper.selectCount(Wrappers.<EmpCommunicationInterview>lambdaQuery()
                .eq(EmpCommunicationInterview::getStatus, EmpCommunicationInterview.STATUS_PENDING));
    }

    /* ===================== 内部 ===================== */

    /**
     * 会议链接校验：只做格式 + 域名白名单，**不主动探活**。
     * <p>链接失效由 HR 重新发起一次终面（旧记录取消），保持记录链完整。</p>
     *
     * <p>提示语面向 HR 直接展示，所以必须「说清哪里错了 + 应该长什么样」，
     * 不要只回一个「格式不合法」让 HR 自己猜。</p>
     *
     * @return 规范化后的链接（去空白），便于一并落库
     */
    private String normalizeAndValidateMeetingUrl(String rawInput) {
        if (!StringUtils.hasText(rawInput)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "请填写会议链接");
        }
        List<String> candidates = extractHttpUrls(rawInput);
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "没有识别到会议链接。可以直接把讯飞会议的邀请信息整段粘贴进来"
                            + "（其中需要有一条以 http:// 或 https:// 开头的链接），"
                            + "示例：" + meetingUrlExample());
        }
        Set<String> allowed = allowedHosts();
        List<String> allowedUrls = candidates.stream()
                .filter(candidate -> allowed.contains(hostOf(candidate)))
                .toList();
        if (allowedUrls.isEmpty()) {
            String host = hostOf(candidates.get(0));
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "会议链接域名不在允许列表中"
                            + "（当前识别到：" + (StringUtils.hasText(host) ? host : "无法识别")
                            + "；当前允许：" + String.join("、", allowed) + "）"
                            + "。请从讯飞会议创建会议后复制邀请链接，或联系管理员调整白名单。");
        }
        return preferJoinLink(allowedUrls);
    }

    /**
     * 抽取文本中所有 http(s) 链接（按出现顺序去重，并剥掉尾部标点）。
     * <p>同一条邀请信息里通常有两条同域名链接（入会链接 + 客户端下载地址），
     * 所以这里不能只取「第一个」，要全部交给后续按域名与路径筛选。</p>
     */
    private List<String> extractHttpUrls(String rawInput) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = HTTP_URL_PATTERN.matcher(rawInput);
        while (matcher.find()) {
            String cleaned = trimTrailingPunctuation(matcher.group());
            if (StringUtils.hasText(cleaned)) {
                urls.add(cleaned);
            }
        }
        return new ArrayList<>(urls);
    }

    private String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    /** 解析链接域名，失败返回 null（后续按「不在白名单」统一报错，不单独抛解析异常） */
    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return StringUtils.hasText(host) ? host.toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 同域名多条链接时优先取入会链接。
     * <p>讯飞会议的邀请信息里同时含 {@code /meeting/join?xxx}（入会）与
     * {@code /download.html}（客户端下载），两者域名相同，只能靠路径区分；
     * 若都不含 join 特征，则退回「最先出现的那条」。</p>
     */
    private String preferJoinLink(List<String> urls) {
        return urls.stream()
                .filter(url -> url.toLowerCase().contains(JOIN_PATH_HINT))
                .findFirst()
                .orElse(urls.get(0));
    }

    /** 配置中第一个允许域名拼出的示例链接，用于把报错提示写得可照抄 */
    private String meetingUrlExample() {
        Set<String> allowed = allowedHosts();
        String exampleHost = allowed.isEmpty() ? "meeting.iflyrec.com" : allowed.iterator().next();
        return "https://" + exampleHost + "/meeting/join?xxx";
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(allowedMeetingHosts.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private EmpCommunicationInterview requireExisting(Long id) {
        EmpCommunicationInterview entity = interviewMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND.getCode(), "终面记录不存在");
        }
        return entity;
    }

    private int countOutcome(Long empId, int reviewStatus) {
        Long count = outcomeMapper.selectCount(Wrappers.<EmpLearningOutcomeSubmission>lambdaQuery()
                .eq(EmpLearningOutcomeSubmission::getEmpId, empId)
                .eq(EmpLearningOutcomeSubmission::getReviewStatus, reviewStatus));
        return count == null ? 0 : count.intValue();
    }

    private PageResponse<CommunicationInterviewResponse> toPageResponse(Page<EmpCommunicationInterview> page) {
        List<EmpCommunicationInterview> records = page.getRecords();
        Map<Long, String> empNames = new HashMap<>();
        Map<Long, String> empEmails = new HashMap<>();
        Map<Long, String> postNames = new HashMap<>();

        List<Long> empIds = records.stream()
                .map(EmpCommunicationInterview::getEmpId).filter(Objects::nonNull).distinct().toList();
        List<Long> postIds = records.stream()
                .map(EmpCommunicationInterview::getPostId).filter(Objects::nonNull).distinct().toList();
        if (!empIds.isEmpty()) {
            dataQuery.findEmployeesForMatching(empIds)
                    .forEach(profile -> empNames.put(profile.empId(), profile.realName()));
            // 邮箱单独批量取：MatchingEmployeeProfile 是匹配域的快照视图，刻意不含邮箱，
            // 而「邀请邮件到底发给了谁」是终面专属信息，在这里查可以不动那个共享 DTO。
            empEmployeeMapper.selectBatchIds(empIds)
                    .forEach(employee -> empEmails.put(employee.getId(), employee.getEmail()));
        }
        if (!postIds.isEmpty()) {
            dataQuery.findPostsForMatching(postIds)
                    .forEach(profile -> postNames.put(profile.postId(), profile.postName()));
        }

        List<CommunicationInterviewResponse> views = records.stream()
                .map(item -> new CommunicationInterviewResponse(
                        item.getId(), item.getEmpId(), empNames.get(item.getEmpId()),
                        item.getPostId(), postNames.get(item.getPostId()), item.getMatchingRecordId(),
                        item.getMeetingUrl(), item.getMeetingSource(), item.getScheduledTime(),
                        item.getStatus(), CommunicationInterviewResponse.statusName(item.getStatus()),
                        item.getResult(), CommunicationInterviewResponse.resultName(item.getResult()),
                        item.getComment(),
                        item.getEmployeeResponse(),
                        CommunicationInterviewResponse.employeeResponseName(item.getEmployeeResponse()),
                        item.getEmployeeResponseComment(),
                        item.getEmployeeRespondedTime(),
                        item.getInviteMailStatus(),
                        CommunicationInterviewResponse.mailStatusName(item.getInviteMailStatus()),
                        empEmails.get(item.getEmpId()),
                        item.getInviteMailError(),
                        item.getCreatedBy(), item.getCreatedTime(), item.getFinishedTime()))
                .toList();
        return new PageResponse<>(views, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    private ArrayNode parseArray(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
        } catch (Exception e) {
            log.warn("沟通要点：报告 JSON 解析失败，按空处理");
            return objectMapper.createArrayNode();
        }
    }

    private List<String> extractAbilityNames(String json) {
        List<String> names = new ArrayList<>();
        for (JsonNode node : parseArray(json)) {
            JsonNode name = node.get("abilityName");
            if (name != null && !name.isNull()) {
                names.add(name.asText());
            }
        }
        return names;
    }

    /**
     * 异步回写沟通要点快照（由 {@code CommunicationInterviewBriefingListener} 在事务提交后调用）。
     *
     * <p>用 CATCH 包裹 {@link #buildBriefing} 的**整体**而不只是序列化：报告读取、差距诊断
     * 都涉及跨模块查询，任何一处抛异常都不应该让这条终面记录变脏，更不该影响已完成的发起。</p>
     */
    public void writeBriefingSnapshotQuietly(Long interviewId, Long empId, Long matchingRecordId) {
        String snapshot;
        try {
            snapshot = toJsonQuietly(buildBriefing(empId, matchingRecordId));
        } catch (Exception e) {
            log.warn("沟通要点快照生成失败（不影响已发起的终面）: interviewId={}, empId={}",
                    interviewId, empId, e);
            return;
        }
        if (snapshot == null) {
            return;
        }
        try {
            EmpCommunicationInterview update = new EmpCommunicationInterview();
            update.setId(interviewId);
            update.setBriefingSnapshot(snapshot);
            interviewMapper.updateById(update);
        } catch (Exception e) {
            log.warn("沟通要点快照回写失败: interviewId={}", interviewId, e);
        }
    }

    private String toJsonQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("沟通要点快照序列化失败（不影响发起）", e);
            return null;
        }
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value : "--";
    }

    private long safeCurrent(long current) {
        return Math.max(current, 1);
    }

    private long safeSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /* ===================== 通知（失败绝不阻断主流程） ===================== */

    private void notifyInviteQuietly(EmpCommunicationInterview entity, EmpEmployee employee) {
        try {
            if (employee.getUserId() == null) {
                return;
            }
            SysNotification notification = new SysNotification();
            notification.setReceiverUserId(employee.getUserId());
            notification.setType(SysNotification.TYPE_INVITE_MEETING);
            notification.setTitle("收到视频沟通邀请");
            notification.setContent("HR 邀请你进行一次视频沟通"
                    + (entity.getScheduledTime() == null ? "（时间待定）" : "，预约时间：" + entity.getScheduledTime())
                    + "。请到「能力画像 → 我的视频沟通」接受或放弃，接受后按约定时间进入会议。");
            notification.setBizType(SysNotification.BIZ_INTERVIEW);
            notification.setBizId(entity.getId());
            notification.setCreatedBy(entity.getCreatedBy());
            notificationService.send(notification);
        } catch (Exception e) {
            log.warn("视频终面邀请通知发送失败（不阻断发起）: id={}", entity.getId(), e);
        }
    }

    /**
     * 员工响应后通知发起该场终面的 HR。
     * <p>与其它通知同取向：失败只记日志，绝不把员工的「接受/放弃」判成失败。
     * created_by 留空 —— 该通知由员工动作触发，不是某个管理端用户发出的。</p>
     */
    private void notifyResponseQuietly(EmpCommunicationInterview entity, Integer response, String comment) {
        try {
            if (entity.getCreatedBy() == null) {
                return;
            }
            EmpEmployee employee = empEmployeeMapper.selectById(entity.getEmpId());
            String empName = employee != null && StringUtils.hasText(employee.getRealName())
                    ? employee.getRealName() : "该员工";
            boolean declined = response == EmpCommunicationInterview.RESPONSE_DECLINED;

            StringBuilder content = new StringBuilder();
            content.append(empName)
                    .append(declined ? " 已放弃本次视频终面" : " 已接受本次视频终面邀请");
            if (entity.getScheduledTime() != null) {
                content.append("（预约时间：").append(entity.getScheduledTime()).append("）");
            }
            content.append(declined ? "。" : "，请按计划进行。");
            if (StringUtils.hasText(comment)) {
                content.append("员工说明：").append(comment);
            }

            SysNotification notification = new SysNotification();
            notification.setReceiverUserId(entity.getCreatedBy());
            notification.setType(SysNotification.TYPE_INTERVIEW_RESPONSE);
            notification.setTitle(declined ? "员工已放弃视频终面" : "员工已接受视频终面");
            notification.setContent(abbreviate(content.toString(), MAX_NOTIFICATION_CONTENT_LENGTH));
            notification.setBizType(SysNotification.BIZ_INTERVIEW);
            notification.setBizId(entity.getId());
            notificationService.send(notification);
        } catch (Exception e) {
            log.warn("视频终面员工响应通知发送失败（不阻断员工操作）: id={}", entity.getId(), e);
        }
    }

    /** sys_notification.content 列为 VARCHAR(512)，超长直接截断，避免写库报「Data too long」 */
    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void notifyResultQuietly(EmpCommunicationInterview entity, Integer result, String comment) {
        try {
            EmpEmployee employee = empEmployeeMapper.selectById(entity.getEmpId());
            if (employee == null || employee.getUserId() == null) {
                return;
            }
            SysNotification notification = new SysNotification();
            notification.setReceiverUserId(employee.getUserId());
            notification.setType(SysNotification.TYPE_INTERVIEW_RESULT);
            notification.setTitle("视频沟通结论已更新");
            notification.setContent("本次视频沟通结论：" + CommunicationInterviewResponse.resultName(result)
                    + (StringUtils.hasText(comment) ? "；HR 评价：" + comment : ""));
            notification.setBizType(SysNotification.BIZ_INTERVIEW);
            notification.setBizId(entity.getId());
            notification.setCreatedBy(entity.getCreatedBy());
            notificationService.send(notification);
        } catch (Exception e) {
            log.warn("视频终面结果通知发送失败（不阻断录入）: id={}", entity.getId(), e);
        }
    }
}


