package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.PostEvolutionTaskCreateDTO;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;

import java.util.List;
import java.util.Map;

/**
 * 岗位演化服务接口
 *
 * @author system
 */
public interface PostEvolutionService {

    /**
     * 创建演化任务
     *
     * @param dto 创建DTO
     * @param userId 创建人ID
     * @return 任务实体
     */
    PostEvolutionTask createTask(PostEvolutionTaskCreateDTO dto, Long userId);

    /**
     * 执行演化分析
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    PostEvolutionTask analyzeTask(Long taskId);

    /**
     * 分页查询任务
     *
     * @param page 分页参数
     * @param postId 岗位ID过滤
     * @param taskStatus 状态过滤
     * @return 分页结果
     */
    IPage<PostEvolutionTask> pageTasks(Page<PostEvolutionTask> page, Long postId, String taskStatus);

    /**
     * 获取任务详情（含变更项）
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    PostEvolutionTask getTaskById(Long taskId);

    /** 删除演化任务及其关联证据、变更项，不影响岗位能力模型。 */
    void deleteTask(Long taskId);

    /**
     * 获取任务的变更项列表
     *
     * @param taskId 任务ID
     * @return 变更项列表
     */
    IPage<PostEvolutionChangeItem> pageChangeItems(Long taskId, Page<PostEvolutionChangeItem> page);

    /**
     * 审核变更项
     *
     * @param taskId 任务ID
     * @param itemId 变更项ID
     * @param dto 审核DTO
     */
    void reviewChangeItem(Long taskId, Long itemId, PostEvolutionReviewDTO dto);

    /**
     * 应用已审核通过的变更
     *
     * @param taskId 任务ID
     * @return 应用的变更数量
     */
    int applyApprovedChanges(Long taskId);

    /**
     * 获取任务的证据列表
     *
     * @param taskId 任务ID
     * @return 证据列表
     */
    List<PostEvolutionEvidence> getTaskEvidence(Long taskId);

    /**
     * 获取变更项的证据列表
     *
     * @param itemId 变更项ID
     * @return 证据列表
     */
    List<PostEvolutionEvidence> getItemEvidence(Long itemId);

    /**
     * 获取演化时间线事件
     *
     * @param postId 岗位ID（可选）
     * @param range 时间范围：7d/30d/90d
     * @param limit 最大条数
     * @return 时间线事件列表
     */
    List<Map<String, Object>> getTimelineEvents(Long postId, String range, int limit);

    /**
     * 获取仪表盘统计数据
     *
     * @param range 时间范围：7d/30d/90d
     * @return 统计数据
     */
    Map<String, Object> getDashboardStats(String range);

    /**
     * 获取演化趋势数据
     *
     * @param range 时间范围：7d/30d/90d
     * @return 趋势数据
     */
    Map<String, Object> getEvolutionTrends(String range);

    /**
     * 获取岗位能力演化图谱
     *
     * @param postId 岗位ID
     * @param timePoint 时间点（可选）
     * @return 图谱数据
     */
    Map<String, Object> getEvolutionGraph(Long postId, String timePoint);
}
