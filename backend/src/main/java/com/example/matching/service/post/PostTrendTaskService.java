package com.example.matching.service.post;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.entity.post.PostTrendTask;
import com.example.matching.vo.post.PostTrendProgressVO;
import com.example.matching.vo.post.PostTrendTaskVO;

import java.util.List;

/**
 * 岗位趋势解析任务的生命周期服务。
 * <p>
 * 只负责任务的创建、查询、进度、取消与超时回收；真正的解析编排在趋势解析服务中。
 * 状态流转一律走 Mapper 的条件更新（CAS），保证抢占 / 置终态 / 取消三者并发安全。
 */
public interface PostTrendTaskService {

    /**
     * 新建解析任务并在事务提交后投递执行。
     *
     * @param sourceDocumentIds 已上传并索引的知识文档ID
     * @param sourceCategories  材料类别（POLICY/REPORT/STANDARD…），用于跨来源印证
     */
    PostTrendTask createTask(List<Long> sourceDocumentIds, List<String> sourceCategories,
                             String taskName, Long operatorId);

    /** 按ID取任务，不存在则抛 404。 */
    PostTrendTask requireTask(Long taskId);

    /** 按ID取任务视图，不存在则抛 404。 */
    PostTrendTaskVO getTask(Long taskId);

    PageResponse<PostTrendTaskVO> pageTasks(long current, long size);

    PostTrendProgressVO getProgress(Long taskId);

    /**
     * 取消任务。
     * <p>
     * 执行线程可能已在 LLM 调用中，无法真正中断；因此置为终态时统一走 CAS
     * （仅 RUNNING 可写终态），使取消结果不会被随后的收尾写入覆盖。
     *
     * @return 是否实际发生取消（任务已是终态时返回 false）
     */
    boolean cancel(Long taskId);

    /** 更新执行进度（执行线程调用；仅在 RUNNING 期间生效）。 */
    void reportProgress(Long taskId, String progressStatus, int percent);

    /** 解析完成：有候选 → WAIT_CONFIRM，无候选 → COMPLETED。 */
    void markAnalysisFinished(Long taskId, PostTrendAnalysisSummary summary);

    void markFailed(Long taskId, String errorMessage);

    /**
     * 审核结束后收敛任务状态。
     * <p>
     * 候选全部处理完（无 PENDING）之前一直停在 WAIT_CONFIRM；全部落地 → APPLIED，
     * 部分落地 → PARTIALLY_APPLIED，全部驳回 → COMPLETED（没有岗位被改，任务只是走完了流程）。
     */
    void refreshConfirmStatus(Long taskId);

    /** 扫描长时间停留在 RUNNING 的任务并置为 FAILED，返回处理数量。 */
    int scanZombieTasks();
}
