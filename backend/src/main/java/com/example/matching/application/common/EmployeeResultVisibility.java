package com.example.matching.application.common;

import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 员工侧「评估结果可见性」的统一判定。
 *
 * <p><b>口径（2026-09-04 收敛）</b>：员工本人的能力项与评估结果，
 * 必须同时满足两个条件才对本人可见：
 * <ol>
 *   <li><b>评估流程已完成</b>（{@code WorkflowStatusEnum.COMPLETED}，即报告主体已生成）；</li>
 *   <li><b>HR 已把该员工全部能力项人工审核完毕</b> —— 这一条以「全面能力分析报告已生成」
 *       为代表（报告的生成条件就是审核队列清空，见 {@code CapabilityAnalysisReportService}）。</li>
 * </ol>
 * 换言之：**审核中间态一律不进入员工侧视图** —— 不展示「待确立 / 待审核能力」，
 * 也不展示未定稿的「已确立能力」。
 *
 * <p>管理端（HR）不受此限制：HR 需要随时查看人员能力以便审核与修改。
 *
 * <p><b>为什么单独抽一个组件</b>：这个判据原本散在
 * {@code CapabilityAssessmentFacadeImpl} 的私有方法里，只有评估画像视图用了它；
 * 而员工侧的能力画像接口（{@code EmpAbilityApiFacade}）没有这道闸门，
 * 于是「前端隐藏了、接口仍可直连拿到未定稿能力」。抽成一处后两条链路共用同一判据，
 * 避免再次出现「同一口径两处实现、其中一处漏掉」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeResultVisibility {

    private final SelfScopeSupport selfScopeSupport;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final CapabilityAnalysisReportService capabilityAnalysisReportService;

    /**
     * 该员工的能力/评估结果是否应对外（员工本人）隐藏。
     *
     * @return true 表示应隐藏（员工侧）；管理端调用方恒为 false
     */
    public boolean shouldHideFromEmployee(Long empId) {
        if (empId == null || !selfScopeSupport.isSelfOnlyCaller()) {
            return false;
        }
        return !isResultReady(empId);
    }

    /**
     * 结果是否已定稿（与调用方身份无关，供需要显式判断的场景复用）。
     *
     * <p>判定失败（例如报告服务异常）时按「未定稿」处理 —— 宁可晚一点给员工看，
     * 也不放未审核的内容出去。
     */
    public boolean isResultReady(Long empId) {
        if (empId == null) {
            return false;
        }
        try {
            if (workflowService.getLatestCompletedWorkflowId(empId) == null) {
                return false;
            }
            return capabilityAnalysisReportService.existsLatest(empId);
        } catch (Exception e) {
            log.warn("评估结果定稿判定失败，按未定稿处理: empId={}", empId, e);
            return false;
        }
    }

    /**
     * 员工侧评估结果的状态，供员工界面选择正确文案。
     *
     * <p><b>为什么需要多态</b>：{@link #shouldHideFromEmployee} 只回答「能不能看」，
     * 但「不能看」里混着三种完全不同的处境 —— 从未评估过、评估还在进行中、
     * 已走完流程在等 HR 审核。若统一渲染成「你的评估已提交，HR 正在审核」，
     * 对从未评估的人是凭空捏造了一个不存在的审核进度。
     * 界面必须分开说，否则就会骗人。</p>
     */
    public enum ResultState {
        /** 从未发起过任何能力评估（没有可等待的结果，应引导去发起） */
        NOT_STARTED,
        /** 已发起评估，流程仍在推进中（简历/测试/面试尚未走完） */
        IN_PROGRESS,
        /** 流程已走完，等待 HR 审核清空能力项并生成报告 */
        PENDING_REVIEW,
        /** 结果已定稿，员工可见能力画像与报告 */
        READY
    }

    /**
     * 解析员工侧评估结果状态。判据全部来自真实数据，不做任何推断。
     *
     * <p>判定顺序：已定稿 → 无流程 → 流程终态但报告未生成 → 流程进行中。
     * 「已定稿」优先，因为它蕴含其余一切；「有无流程」次之，用于把
     * 「从未评估」从「进行中/待审核」里摘出来。</p>
     *
     * <p>管理端（HR / 岗位体系 / 管理员）恒为 {@link ResultState#READY}：
     * 他们不受员工侧闸门限制，需要随时查看人员能力以便审核与修改；
     * 若在此处漏判，HR 打开员工画像会被误标成「待审核」而看不到数据。</p>
     *
     * <p>查询异常时保守地返回 {@link ResultState#PENDING_REVIEW}：
     * 既不放未定稿结果出去，也不会对着真正在审核的员工说「你从没评估过」。</p>
     */
    public ResultState resolveState(Long empId) {
        if (empId == null || !selfScopeSupport.isSelfOnlyCaller()) {
            return ResultState.READY;
        }
        if (isResultReady(empId)) {
            return ResultState.READY;
        }
        try {
            PersonCapabilityWorkflow latest = workflowService.getLatestWorkflow(empId);
            if (latest == null) {
                return ResultState.NOT_STARTED;
            }
            // 流程已进入终态（COMPLETED 但报告尚未生成）或专门的待复核态 → 确实在等审核
            String status = latest.getStatus();
            if (WorkflowStatusEnum.REVIEW_REQUIRED.getCode().equals(status)
                || WorkflowStatusEnum.COMPLETED.getCode().equals(status)) {
                return ResultState.PENDING_REVIEW;
            }
            // 其余（简历/测试/面试各阶段、恢复中等）都属于流程还在推进
            return ResultState.IN_PROGRESS;
        } catch (Exception e) {
            log.warn("评估流程状态判定失败，按待审核处理: empId={}", empId, e);
            return ResultState.PENDING_REVIEW;
        }
    }
}
