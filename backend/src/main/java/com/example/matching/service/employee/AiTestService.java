package com.example.matching.service.employee;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.employee.EmpAiTest;

import java.util.List;
import java.util.Map;

/**
 * AI测试服务接口
 */
public interface AiTestService extends IService<EmpAiTest> {

    /**
     * 基于岗位能力模型生成综合测试
     */
    EmpAiTest generatePostTest(Long empId, Long postId, Long userId);

    /**
     * 基于简历能力主张与目标岗位生成验证测试（能力评估工作流主流程）。
     * <p>
     * 简历 Claim 是待验证假设，不是题目答案；测试可支持、削弱或否定简历 Claim。
     * 测试记录关联 workflowId，评分后由工作流保存为测试证据，不直接正式入库。
     *
     * @param empId      员工ID
     * @param workflowId 能力评估工作流ID
     * @param postId     目标岗位ID
     * @param userId     操作人
     * @return 生成的测试记录
     */
    EmpAiTest generateWorkflowTest(Long empId, Long workflowId, Long postId, Long userId);

    /**
     * 校验岗位可作为能力评估的测试上下文。
     * 岗位能力模型不决定评估范围，但没有模型的岗位不能作为目标岗位。
     */
    void assertWorkflowTestPostConfigured(Long postId);

    /**
     * 生成AI测试题目
     */
    EmpAiTest generateTest(Long empId, Long abilityTagId, Long userId);

    /**
     * MQ消费者：后台生成题目
     */
    void processGenerateQuestions(Long testId);

    /**
     * 提交答案
     */
    EmpAiTest submitAnswers(Long testId, Map<String, Object> answers);

    /**
     * MQ消费者：后台评估答案
     */
    void processEvaluateAnswers(Long testId);

    /**
     * 获取测试结果
     */
    EmpAiTest getTestResult(Long testId);

    /**
     * 获取员工的测试列表
     */
    List<EmpAiTest> listByEmpId(Long empId);

    /**
     * 按工作流获取最新测试记录（评估流程用），无则返回 null。
     */
    EmpAiTest getLatestByWorkflowId(Long workflowId);

    /**
     * 将测试结果导入到员工能力档案
     */
    boolean importToAbilityProfile(Long testId);

    /**
     * 管理员重放失败任务：仅允许 FAILED -> PENDING，并写系统操作日志
     *
     * @return true 表示至少一个任务已重放
     */
    boolean redeliverTask(Long testId);
}
