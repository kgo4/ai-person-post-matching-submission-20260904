package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.dto.learning.LearningPathPlanVO;
import com.example.matching.entity.learning.LearningPathPlan;

/**
 * 学习路径计划服务接口
 *
 * @author system
 */
public interface LearningPathPlanService {

    /**
     * 从匹配记录生成学习路径计划
     *
     * @param request 生成请求
     * @return 学习路径计划VO
     */
    LearningPathPlanVO generateFromMatchingRecord(LearningPathGenerateRequest request);

    /**
     * 获取学习路径计划详情
     *
     * @param id 计划ID
     * @return 学习路径计划VO
     */
    LearningPathPlanVO getPlan(Long id);

    /**
     * 根据匹配记录ID获取学习路径计划
     *
     * @param matchingRecordId 匹配记录ID
     * @return 学习路径计划VO，不存在返回null
     */
    LearningPathPlanVO getByMatchingRecord(Long matchingRecordId);

    /**
     * 分页查询学习路径计划
     *
     * @param page 分页参数
     * @param empId 员工ID（可选）
     * @param postId 岗位ID（可选）
     * @param status 状态（可选）
     * @return 分页结果
     */
    IPage<LearningPathPlanVO> pagePlans(Page<LearningPathPlan> page, Long empId, Long postId, String status);

    /**
     * 更新步骤状态
     *
     * @param stepId 步骤ID
     * @param status 新状态
     */
    void updateStepStatus(Long stepId, String status);

    /**
     * 轻量回填：重新按 abilityName/tagId 为指定计划的每个步骤绑定真实资源，
     * 保留原步骤记录、完成状态与排序。
     *
     * @param planId 学习路径计划ID
     * @return 已回填的步骤数
     */
    int refreshResourceBindings(Long planId);

    /**
     * 对所有学习路径计划执行一次轻量资源回填。
     *
     * @return 已回填的步骤总数
     */
    int refreshAllResourceBindings();
}
