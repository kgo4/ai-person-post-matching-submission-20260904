package com.example.matching.service.employee;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.entity.employee.EmpResumeParse;
import java.util.List;

/**
 * Resume Parse Service Interface
 */
public interface ResumeParseService extends IService<EmpResumeParse> {

    /**
     * Upload and parse resume
     *
     * @param empId   Employee ID
     * @param file    Resume file
     * @param userId  Operator ID
     * @return Parse record
     */
    EmpResumeParse uploadAndParse(Long empId, String fileName, byte[] content, Long userId);

    /**
     * Get resume parse records for an employee
     *
     * @param empId Employee ID
     * @return List of parse records
     */
    List<EmpResumeParse> listByEmpId(Long empId);

    /**
     * Execute a queued resume parse task.
     *
     * @param parseId Parse record ID
     */
    void processQueuedParse(Long parseId);

    /**
     * Mark a parse record as failed when its asynchronous task cannot be dispatched.
     *
     * @param parseId Parse record ID
     * @param errorMessage Dispatch failure reason
     */
    void markTaskDispatchFailed(Long parseId, String errorMessage);

    /**
     * Import parsed abilities to employee ability profile
     * <p>
     * Returns structured result showing how many abilities were imported,
     * how many entered candidate pool, and how many were rejected.
     *
     * @param parseId Parse record ID
     * @return Import result with detailed statistics
     * @deprecated 已废弃。简历能力正式入库统一走「能力评估工作流证据路径」
     * （saveResumeEvidenceForWorkflow）或 GovernedAdmissionServiceImpl，勿新增调用。
     */
    @Deprecated
    AbilityImportResultDTO importToAbilityProfile(Long parseId);

    /**
     * 将简历解析结果保存为能力评估工作流的待验证证据（阶段 1）。
     * <p>
     * 仅当员工存在活跃评估工作流时生效；保存为 COLLECTED + DISPLAY_ONLY，
     * 不触发任何正式入库。解析结果中的无原文证据 Claim 会被拒绝。
     *
     * @param parseId 解析记录ID
     * @return 保存的证据数量
     */
    int saveResumeEvidenceForWorkflow(Long parseId);

    /**
     * Re-parse resume with latest prompt
     *
     * @param parseId Parse record ID
     * @return Updated parse record
     */
    EmpResumeParse reparse(Long parseId);

    /**
     * 人工重试：仅允许失败或等待重试状态的记录重新投递到主队列。
     *
     * @param parseId 解析记录ID
     * @return 更新后的记录
     */
    EmpResumeParse retryFailedTask(Long parseId);

    /**
     * 扫描并恢复僵尸任务（处理中超过阈值仍未完成的任务）。
     *
     * @return 恢复的任务数量
     */
    int recoverZombieTasks();

    int recoverUndispatchedTasks();

    /** M27：扫描 status=4（等待重试）且 nextRetryTime 已过期未投递的记录，CAS 补投 */
    int recoverWaitingRetryTasks();
}
