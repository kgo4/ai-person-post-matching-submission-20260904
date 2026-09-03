package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.learning.LearningProjectReviewDTO;
import com.example.matching.dto.learning.LearningProjectSubmitDTO;
import com.example.matching.dto.learning.LearningProjectTaskVO;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;

/**
 * 学习项目任务服务接口
 *
 * @author system
 */
public interface LearningProjectTaskService {

    /**
     * 分页查询项目任务
     *
     * @param page 分页参数
     * @param planId 计划ID（可选）
     * @param empId 员工ID（可选）
     * @param status 状态（可选）
     * @return 分页结果
     */
    IPage<LearningProjectTaskVO> pageTasks(Page<LearningProjectTask> page, Long planId, Long empId, String status);

    /**
     * 获取项目任务详情
     *
     * @param id 任务ID
     * @return 任务VO
     */
    LearningProjectTaskVO getTask(Long id);

    /**
     * 提交项目任务
     *
     * @param taskId 任务ID
     * @param dto 提交内容
     * @param empId 提交人ID
     * @return 提交记录
     */
    LearningProjectSubmission submit(Long taskId, LearningProjectSubmitDTO dto, Long empId);

    /**
     * 审核项目提交
     *
     * @param submissionId 提交ID
     * @param dto 审核内容
     * @param reviewerId 审核人ID
     * @return 审核后的提交记录
     */
    LearningProjectSubmission review(Long submissionId, LearningProjectReviewDTO dto, Long reviewerId);
}
