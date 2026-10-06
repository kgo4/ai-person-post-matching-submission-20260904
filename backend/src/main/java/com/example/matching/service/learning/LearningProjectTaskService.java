package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.learning.LearningProjectSubmitDTO;
import com.example.matching.dto.learning.LearningProjectTaskVO;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;

/**
 * 学习项目任务服务接口
 *
 * <p>⚠️ 本接口<b>不含复核方法</b>：项目材料提交即归档并生成证据，
 * HR 不再单独审核项目材料，而是在复核「能力提升申请」时一并查看。
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
     * 提交项目材料。
     *
     * <p>提交即视为该步骤学习完成：生成能力证据、任务与步骤标记完成。
     * <b>不更新能力等级</b> —— 等级只在「能力提升申请」复核通过时更新。
     *
     * @param taskId 任务ID
     * @param dto 提交内容
     * @param empId 提交人ID
     * @return 提交记录
     */
    LearningProjectSubmission submit(Long taskId, LearningProjectSubmitDTO dto, Long empId);
}
