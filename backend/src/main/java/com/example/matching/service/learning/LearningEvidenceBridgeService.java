package com.example.matching.service.learning;

import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;

/**
 * 学习证据桥接服务接口
 * <p>
 * 负责将审核通过的项目提交转化为能力证据。
 *
 * @author system
 */
public interface LearningEvidenceBridgeService {

    /**
     * 为审核通过的提交创建证据
     *
     * @param submission    提交记录
     * @param task          项目任务
     * @param step          学习步骤
     * @param confidence    证据置信度 (0-100)
     * @param credibility   证据可信度 (0-100)
     * @return 证据ID
     */
    Long createEvidenceForApprovedSubmission(LearningProjectSubmission submission,
                                              LearningProjectTask task,
                                              LearningPathStep step,
                                              Integer confidence,
                                              Integer credibility);
}
