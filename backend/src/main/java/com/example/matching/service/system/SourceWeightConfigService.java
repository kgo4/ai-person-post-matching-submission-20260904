package com.example.matching.service.system;

import com.example.matching.entity.system.SourceWeightConfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * 来源证据权重配置服务
 *
 * @author system
 */
public interface SourceWeightConfigService {

    /**
     * 获取指定来源类型的权重值（带缓存）
     *
     * @param sourceType 来源类型
     * @return 权重值，未配置时返回 0.10
     */
    BigDecimal getWeight(String sourceType);

    /**
     * 获取所有权重配置列表
     *
     * @return 配置列表，按 sortOrder 排序
     */
    List<SourceWeightConfig> listAll();

    /**
     * 批量更新权重配置，清除缓存
     *
     * @param configs 待更新的配置列表
     * @return 更新后的配置列表
     */
    List<SourceWeightConfig> batchUpdate(List<SourceWeightConfig> configs);
}
