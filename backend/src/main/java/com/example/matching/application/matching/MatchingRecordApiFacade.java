package com.example.matching.application.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.enums.PublishStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.api.MatchingExecuteResultResponse;
import com.example.matching.dto.matching.StructuredReviewDTO;
import com.example.matching.dto.matching.api.MatchingRecordResponse;
import com.example.matching.dto.matching.api.MatchingTaskResponse;
import com.example.matching.dto.matching.api.ModifyResultRequest;
import com.example.matching.entity.interview.EmpCommunicationInterview;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.mapper.interview.EmpCommunicationInterviewMapper;
import com.example.matching.service.matching.MatchingExecuteService;
import com.example.matching.service.matching.MatchingExecuteResult;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.matching.StructuredReviewService;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import com.example.matching.service.common.ExcelService;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MatchingRecordApiFacade {

    private final MatchingRecordService matchingRecordService;
    private final MatchingExecuteService matchingExecuteService;
    private final MatchingTaskService matchingTaskService;
    private final StructuredReviewService structuredReviewService;
    private final ExcelService excelService;
    private final com.example.matching.converter.matching.MatchingRecordConverter matchingRecordConverter;
    private final EmpEmployeeService empEmployeeService;
    private final CapabilityAnalysisReportService capabilityAnalysisReportService;
    private final EmpCommunicationInterviewMapper communicationInterviewMapper;

    @Autowired
    public MatchingRecordApiFacade(MatchingRecordService matchingRecordService,
                                   MatchingExecuteService matchingExecuteService,
                                   MatchingTaskService matchingTaskService,
                                   StructuredReviewService structuredReviewService,
                                   ExcelService excelService,
                                   com.example.matching.converter.matching.MatchingRecordConverter matchingRecordConverter,
                                   EmpEmployeeService empEmployeeService,
                                   CapabilityAnalysisReportService capabilityAnalysisReportService,
                                   EmpCommunicationInterviewMapper communicationInterviewMapper) {
        this.matchingRecordService = matchingRecordService;
        this.matchingExecuteService = matchingExecuteService;
        this.matchingTaskService = matchingTaskService;
        this.structuredReviewService = structuredReviewService;
        this.excelService = excelService;
        this.matchingRecordConverter = matchingRecordConverter;
        this.empEmployeeService = empEmployeeService;
        this.capabilityAnalysisReportService = capabilityAnalysisReportService;
        this.communicationInterviewMapper = communicationInterviewMapper;
    }

    /** 兼容现有单元测试与旧调用方；生产 Bean 使用包含员工数据范围服务的构造器。 */
    public MatchingRecordApiFacade(MatchingRecordService matchingRecordService,
                                   MatchingExecuteService matchingExecuteService,
                                   MatchingTaskService matchingTaskService,
                                   StructuredReviewService structuredReviewService,
                                   ExcelService excelService,
                                   com.example.matching.converter.matching.MatchingRecordConverter matchingRecordConverter) {
        this(matchingRecordService, matchingExecuteService, matchingTaskService, structuredReviewService,
                excelService, matchingRecordConverter, null, null, null);
    }

    public MatchingExecuteResultResponse execute(MatchingExecuteDTO dto) {
        MatchingExecuteResult result = matchingExecuteService.execute(dto);
        List<MatchingRecordResponse> records = result.records().stream().map(this::toResponse).toList();
        return new MatchingExecuteResultResponse(
                records,
                result.candidateScope().name(),
                result.candidateCount(),
                result.totalActiveCount(),
                result.truncated(),
                result.taskId(),
                result.isAsync(),
                result.excludedCount()
        );
    }

    public Map<String, String> executeAsync(MatchingExecuteDTO dto) {
        String taskId = matchingTaskService.submitTask(dto);
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        return result;
    }

    public MatchingTaskResponse getTaskStatus(String taskId) {
        MatchingTask task = matchingTaskService.getTaskStatus(taskId);
        if (task == null) {
            return null;
        }
        return toTaskResponse(task);
    }

    public boolean cancelMatchingTask(String taskId) {
        return matchingTaskService.cancelTask(taskId);
    }

    public boolean deleteMatchingTask(String taskId) {
        return matchingTaskService.deleteTask(taskId);
    }

    public PageResponse<MatchingTaskResponse> pageMatchingTasks(long current, long size, Integer status) {
        IPage<MatchingTask> page = matchingTaskService.pageTasks(current, size, status);
        return PageResponse.from(page, this::toTaskResponse);
    }

    public PageResponse<MatchingRecordResponse> page(long current, long size, Long postId, Long empId, Integer matchStatus) {
        IPage<MatchingRecord> page;
        if (isSelfOnlyCaller()) {
            Long selfEmpId = currentEmpId();
            // 人员范围固定为本人；岗位 / 匹配状态仅在本人数据内收窄，empId 入参一律忽略
            page = selfEmpId == null
                    ? new Page<>(current, size)
                    : matchingRecordService.pagePublishedRecords(new Page<>(current, size), selfEmpId, postId, matchStatus);
        } else {
            page = matchingRecordService.pageRecords(new Page<>(current, size), postId, empId, matchStatus);
        }
        fillInterviewResults(page.getRecords());
        return PageResponse.from(page, this::toResponse);
    }

    /**
     * 回填「最近一次已完成视频终面」的结果。
     *
     * <p>为什么放在门面而不是 Service：`EmpCommunicationInterview` 属于面试域，
     * 让匹配 Service 直接依赖它会造成跨域耦合；而这里本来就是"给前端拼展示字段"的位置
     * （`empName` / `postName` 也是同样的处理）。
     *
     * <p>口径：按 {@code matching_record_id} 关联，只取 **STATUS_FINISHED** 的记录；
     * 同一匹配有多场时取 {@code id} 最大的那场（最新一场）。未完成的（待面 / 已取消）
     * 一律视为「未完成终面」→ 字段留 null，前端不会把它算进"已通过"。
     *
     * <p>一次批量查询完成，不做 N+1。
     */
    private void fillInterviewResults(List<MatchingRecord> records) {
        if (records == null || records.isEmpty() || communicationInterviewMapper == null) {
            return;
        }
        List<Long> recordIds = records.stream()
                .map(MatchingRecord::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (recordIds.isEmpty()) {
            return;
        }
        List<EmpCommunicationInterview> finished =
                communicationInterviewMapper.selectList(
                        Wrappers.<EmpCommunicationInterview>lambdaQuery()
                                .in(EmpCommunicationInterview::getMatchingRecordId, recordIds)
                                .eq(EmpCommunicationInterview::getStatus, EmpCommunicationInterview.STATUS_FINISHED)
                                .orderByAsc(EmpCommunicationInterview::getId));

        Map<Long, Integer> latestResultByRecord = new HashMap<>();
        for (EmpCommunicationInterview item : finished) {
            // orderByAsc + 覆盖写：同一匹配多场终面时，结果是最后一场的
            if (item.getMatchingRecordId() != null) {
                latestResultByRecord.put(item.getMatchingRecordId(), item.getResult());
            }
        }
        for (MatchingRecord record : records) {
            record.setInterviewResult(latestResultByRecord.get(record.getId()));
        }
    }

    /**
     * 工作台匹配统计。
     *
     * <p><b>【2026-09-04 修复】员工侧曾返回全库数字。</b>
     * {@code matchingRecordService.dashboardSummary()} 是一条**无人员维度**的全表统计
     * （{@code SELECT ... FROM matching_record WHERE is_deleted = 0}），
     * 而 {@code recent} 却走了会按调用方收口的 {@link #page}。两者口径不一致导致：
     * 一个**没有任何匹配结果**的员工，卡片显示「匹配结果 500 条」，
     * 工作台据此推出「查看匹配结果 / 生成学习路径」等**并不存在**的待办。</p>
     *
     * <p>现在员工侧（self-only 调用方）的 total 与 recent 统一取自同一个收口过的分页查询，
     * 保证「列表里几条，卡片就显示几条」。管理端维持全库统计。</p>
     */
    public Map<String, Object> dashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        if (isSelfOnlyCaller()) {
            // 员工侧：只给「本人可见」的数字，且与 recent 同源，避免两个口径打架。
            // 不塞 score/status 分布为零值 —— 员工侧不使用，写 0 只会制造"看起来有数据"的假象。
            PageResponse<MatchingRecordResponse> selfPage = page(1, 10, null, null, null);
            summary.put("total", selfPage.total());
            summary.put("recent", selfPage.records());
            return summary;
        }

        Map<String, Long> matchingSummary = matchingRecordService.dashboardSummary();
        summary.put("total", matchingSummary.getOrDefault("totalCount", 0L));
        summary.put("score90", matchingSummary.getOrDefault("score90", 0L));
        summary.put("score75", matchingSummary.getOrDefault("score75", 0L));
        summary.put("score60", matchingSummary.getOrDefault("score60", 0L));
        summary.put("scoreBelow60", matchingSummary.getOrDefault("scoreBelow60", 0L));
        for (int status = 0; status <= 4; status++) {
            summary.put("status" + status, matchingSummary.getOrDefault("status" + status, 0L));
        }
        summary.put("pendingPublish", matchingSummary.getOrDefault("pendingPublish", 0L));
        summary.put("recent", page(1, 10, null, null, null).records());
        return summary;
    }

    public MatchingRecordResponse getById(Long id) {
        MatchingRecord record;
        if (isSelfOnlyCaller()) {
            Long selfEmpId = currentEmpId();
            record = selfEmpId == null ? null : matchingRecordService.getPublishedDetailById(id, selfEmpId);
        } else {
            record = matchingRecordService.getDetailById(id);
        }
        if (record == null) {
            return null;
        }
        return toResponse(record);
    }

    /**
     * 是否“仅本人”调用方：只持有 NOTIFICATION:SELF 而没有任何匹配管理端权限。
     */
    private boolean isSelfOnlyCaller() {
        return SecurityUtils.hasAuthority("NOTIFICATION:SELF")
                && !SecurityUtils.hasAnyAuthority("MATCHING:READ", "MATCHING:EXECUTE", "MATCHING:APPROVE");
    }

    /** 当前登录账号关联的人员档案 ID；未绑定或未登录时返回 null。 */
    private Long currentEmpId() {
        if (empEmployeeService == null || SecurityUtils.getCurrentUserId() == null) {
            return null;
        }
        var employee = empEmployeeService.getByUserId(SecurityUtils.getCurrentUserId());
        return employee == null ? null : employee.getId();
    }

    /**
     * 员工自助发起匹配的准入闸门（闭环设计 S1/G5）。
     *
     * <p>必须有已生成的能力分析报告 —— 报告是所有能力项完成 HR 审核后的产物，
     * 只有拿到它才代表能力画像已定稿，此时自助匹配的结果才有意义。</p>
     *
     * <p>只作用于员工自助入口，HR 的 {@code /execute} 与 {@code /execute-async}
     * 不受此约束（HR 场景允许对尚无报告的人员试配）。</p>
     */
    private void assertCapabilityReportReady(Long empId) {
        if (capabilityAnalysisReportService == null) {
            // 兼容只注入 6 个依赖的旧构造器（单元测试场景）
            return;
        }
        if (!capabilityAnalysisReportService.existsLatest(empId)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
                    "尚未生成全面能力分析报告，请先完成人员能力评估并等待审核通过后再发起匹配");
        }
    }

    /**
     * 以记录 ID 为入参的接口统一收口：仅本人调用方只能访问归属自己的“已发布”匹配记录，
     * 防止枚举 recordId 读取他人的匹配详情与报告。管理端不受限。
     */
    private void assertRecordAccessible(Long id) {
        if (!isSelfOnlyCaller()) {
            return;
        }
        Long selfEmpId = currentEmpId();
        if (selfEmpId == null || matchingRecordService.getPublishedDetailById(id, selfEmpId) == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "无权访问该匹配记录");
        }
    }

    public void modifyResult(Long id, ModifyResultRequest req) {
        MatchingRecord record = new MatchingRecord();
        record.setFinalMatchScore(req.matchScore());
        record.setMatchStatus(req.matchStatus());
        record.setManualRemark(req.remark());
        record.setFeedbackComment(req.remark());
        matchingRecordService.modifyResult(id, record);
    }

    public void lockResult(Long id) {
        matchingRecordService.lockResult(id);
    }

    public void unlockResult(Long id) {
        matchingRecordService.unlockResult(id);
    }

    public String generateReport(Long id) {
        assertRecordAccessible(id);
        return matchingRecordService.generateReport(id);
    }

    public String generateAiReport(Long id) {
        assertRecordAccessible(id);
        return matchingRecordService.generateAiReport(id);
    }

    public void deleteRecord(Long id) {
        matchingRecordService.deleteRecord(id);
    }

    /**
     * 推送 / 撤回匹配结果给员工本人。
     * <p>HR 审核通过后可以选择是否让员工看到该匹配结果，也可以先推送再撤回。
     * 未审核通过的记录不允许推送（由 Service 层统一校验）。</p>
     *
     * @param id        匹配记录ID
     * @param published true=推送，false=撤回
     * @return true=状态已变更
     */
    public boolean publishRecord(Long id, boolean published) {
        return matchingRecordService.updatePublishStatus(
                id, published ? PublishStatusEnum.PUBLISHED : PublishStatusEnum.UNPUBLISHED);
    }

    /**
     * 员工自助发起匹配：为自己选定一个或多个岗位试配。
     * <p>与管理端 {@link #execute} 的区别在于授权边界：管理端可以指定任意人员与岗位组合
     * （三种模式），员工侧只能指定岗位，人员范围由服务端按登录身份固定为本人，
     * 请求体里的 empId 一律忽略，避免员工借该入口对他人发起匹配。</p>
     * <p>产生的记录同样进入 HR 审核队列，不会直接对员工可见。</p>
     *
     * @param postIds 员工选择的岗位ID列表
     */
    public MatchingExecuteResultResponse executeSelfMatching(List<Long> postIds) {
        Long selfEmpId = currentEmpId();
        if (selfEmpId == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "当前账号未绑定人员档案，无法发起匹配");
        }
        if (postIds == null || postIds.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "请至少选择一个岗位");
        }
        assertCapabilityReportReady(selfEmpId);
        // 同一员工对多个岗位试配，与 EMP_TO_POST 模式语义一致（pairs 内 empId 必须相同）
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setMode("EMP_TO_POST");
        dto.setPairs(postIds.stream().distinct().map(postId -> {
            MatchingExecuteDTO.MatchingPair pair = new MatchingExecuteDTO.MatchingPair();
            pair.setEmpId(selfEmpId);
            pair.setPostId(postId);
            return pair;
        }).toList());
        return execute(dto);
    }

    public void submitStructuredReview(StructuredReviewDTO request) {
        structuredReviewService.submitStructuredReview(request);
    }

    public boolean retryAiScoring(Long id) {
        return matchingRecordService.retryAiScoring(id);
    }

    public byte[] exportExcel(Long postId) {
        // 全量匹配结果导出属于管理端能力：员工（仅本人数据范围）导出会泄露他人匹配分数，
        // 员工侧不提供导出入口，此处显式拒绝。
        if (isSelfOnlyCaller()) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "无权导出全量匹配结果");
        }
        return excelService.buildMatchResultsExcel(postId);
    }

    private MatchingRecordResponse toResponse(MatchingRecord e) {
        // M17：DTO 收口——字段映射由 MapStruct 生成的 MatchingRecordConverter 承担
        return matchingRecordConverter.toResponse(e);
    }

    private MatchingTaskResponse toTaskResponse(MatchingTask e) {
        return new MatchingTaskResponse(
            e.getId(), e.getTaskId(), e.getPostId(), e.getEmpIds(),
            e.getStatus(), e.getProgress(),
            e.getTotalCount(), e.getProcessedCount(),
            e.getResultMessage(), e.getErrorMessage(),
            e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
