package com.example.matching.vo.post;

import lombok.Data;

/**
 * 岗位趋势「待人工审核」汇总。
 * <p>
 * 存在的理由：工作台的待办必须是**真实待处理项**，不能是静态入口清单。
 * 「岗位趋势发现」的待办在跨任务维度上才有意义（管理员可能同时挂着几个解析任务），
 * 而候选分页接口要求 taskId，无法回答「全站还有多少候选没审」，
 * 因此单出一个只读汇总端点，避免工作台为了拿一个数字去拉全部任务再逐个查候选。
 *
 * @author system
 */
@Data
public class TrendPendingSummaryVO {

    /** 待确认候选总数（confirm_status = PENDING） */
    private long pendingCandidateCount;

    /** 其中新岗位候选数 */
    private long pendingNewPostCount;

    /** 其中能力变更候选数 */
    private long pendingChangeCount;

    /** 处于 WAIT_CONFIRM 的任务数（解析已完成、等人审） */
    private long awaitingTaskCount;
}
