package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.evolution.EvolutionScheduleConfigDTO;
import com.example.matching.entity.evolution.PostEvolutionScheduleConfig;

/**
 * 演化定时服务接口
 *
 * @author system
 */
public interface EvolutionScheduleService {

    /**
     * 创建定时配置
     *
     * @param dto      配置DTO
     * @param operatorId 操作人ID
     * @return 配置实体
     */
    PostEvolutionScheduleConfig createConfig(EvolutionScheduleConfigDTO dto, Long operatorId);

    /**
     * 更新定时配置
     *
     * @param id  配置ID
     * @param dto 配置DTO
     * @return 配置实体
     */
    PostEvolutionScheduleConfig updateConfig(Long id, EvolutionScheduleConfigDTO dto);

    /**
     * 分页查询定时配置
     *
     * @param page   分页参数
     * @param postId 岗位ID（可选）
     * @return 分页结果
     */
    IPage<PostEvolutionScheduleConfig> pageConfigs(Page<PostEvolutionScheduleConfig> page, Long postId);

    /**
     * 获取配置详情
     *
     * @param id 配置ID
     * @return 配置实体
     */
    PostEvolutionScheduleConfig getConfigById(Long id);

    /**
     * 删除定时配置
     *
     * @param id 配置ID
     */
    void deleteConfig(Long id);

    /**
     * 立即执行定时任务
     *
     * @param id 配置ID
     * @return 创建的任务ID
     */
    Long runNow(Long id);

    /**
     * 执行定时扫描（由调度器调用）
     */
    void executeScheduledEvolution();
}
