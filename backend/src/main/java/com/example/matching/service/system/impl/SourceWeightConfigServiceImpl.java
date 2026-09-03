package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.system.SourceWeightConfig;
import com.example.matching.mapper.system.SourceWeightConfigMapper;
import com.example.matching.service.system.SourceWeightConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;

/**
 * 来源证据权重配置服务实现
 * <p>
 * 使用 ConcurrentHashMap + 时间戳实现本地缓存：
 * - 7 条配置，无需外部缓存依赖
 * - batchUpdate 时主动清除缓存
 * - TTL=60s，超时自动从 DB 刷新
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceWeightConfigServiceImpl implements SourceWeightConfigService {

    private final SourceWeightConfigMapper mapper;

    /** 默认权重，当 DB 无记录时使用 */
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.valueOf(10);

    /** 缓存 TTL 毫秒 */
    private static final long CACHE_TTL_MS = 60_000;

    /** 权重缓存：sourceType → weight */
    private volatile Map<String, BigDecimal> weightCache;

    /** 缓存加载时间戳 */
    private volatile long cacheLoadedAt = 0;

    @Override
    public BigDecimal getWeight(String sourceType) {
        Map<String, BigDecimal> cache = getCache();
        return cache.getOrDefault(AbilitySourceType.canonicalize(sourceType), DEFAULT_WEIGHT);
    }

    @Override
    public List<SourceWeightConfig> listAll() {
        List<SourceWeightConfig> configs = mapper.selectList(
                new LambdaQueryWrapper<SourceWeightConfig>()
                        .eq(SourceWeightConfig::getIsActive, 1)
                        .orderByAsc(SourceWeightConfig::getSortOrder));
        Map<String, SourceWeightConfig> canonicalConfigs = new LinkedHashMap<>();
        for (SourceWeightConfig config : configs) {
            String originalSourceType = config.getSourceType();
            String canonicalSourceType = AbilitySourceType.canonicalize(originalSourceType);
            SourceWeightConfig existing = canonicalConfigs.get(canonicalSourceType);
            if (existing == null || canonicalSourceType.equals(originalSourceType)) {
                config.setSourceType(canonicalSourceType);
                canonicalConfigs.put(canonicalSourceType, config);
            }
        }
        return canonicalConfigs.values().stream()
                .filter(config -> AbilitySourceType.isConfigurableAssessmentSource(config.getSourceType()))
                .toList();
    }

    @Override
    @Transactional
    public List<SourceWeightConfig> batchUpdate(List<SourceWeightConfig> configs) {
        for (SourceWeightConfig config : configs) {
            if (config.getId() == null || config.getWeight() == null) {
                continue;
            }
            SourceWeightConfig existing = mapper.selectById(config.getId());
            if (existing == null) {
                throw new BusinessException(404, "来源权重配置不存在");
            }
            if (!AbilitySourceType.isConfigurableAssessmentSource(existing.getSourceType())) {
                throw new BusinessException(400, "该来源已废弃，不能继续修改来源权重");
            }
            // 边界校验
            BigDecimal w = config.getWeight();
            if (w.compareTo(BigDecimal.ZERO) < 0 || w.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException(400, "来源权重必须在 0 到 100 之间");
            }
            mapper.updateById(config);
        }
        List<SourceWeightConfig> active = mapper.selectList(new LambdaQueryWrapper<SourceWeightConfig>()
                .eq(SourceWeightConfig::getIsActive, 1));
        List<SourceWeightConfig> configurable = active.stream()
                .filter(c -> AbilitySourceType.isConfigurableAssessmentSource(c.getSourceType())).toList();
        BigDecimal total = configurable.stream().map(SourceWeightConfig::getWeight)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(BigDecimal.valueOf(100)) != 0) {
            BigDecimal normalizedTotal = BigDecimal.ZERO;
            for (int i = 0; i < configurable.size(); i++) {
                SourceWeightConfig item = configurable.get(i);
                BigDecimal normalized;
                if (i == configurable.size() - 1) {
                    normalized = BigDecimal.valueOf(100).subtract(normalizedTotal).setScale(6, java.math.RoundingMode.HALF_UP);
                } else {
                    normalized = item.getWeight().multiply(BigDecimal.valueOf(100))
                            .divide(total, 6, java.math.RoundingMode.HALF_UP);
                    normalizedTotal = normalizedTotal.add(normalized);
                }
                item.setWeight(normalized);
                mapper.updateById(item);
            }
        }
        // 清除缓存，下次访问自动从 DB 刷新
        invalidateCache();

        log.info("来源权重批量更新完成，count={}", configs.size());
        return listAll();
    }

    // ==================== 缓存管理 ====================

    private Map<String, BigDecimal> getCache() {
        Map<String, BigDecimal> cache = weightCache;
        long now = System.currentTimeMillis();

        // 缓存有效，直接返回
        if (cache != null && (now - cacheLoadedAt) < CACHE_TTL_MS) {
            return cache;
        }

        // 缓存过期或未初始化，从 DB 加载
        synchronized (this) {
            // 双重检查
            if (weightCache != null && (System.currentTimeMillis() - cacheLoadedAt) < CACHE_TTL_MS) {
                return weightCache;
            }

            List<SourceWeightConfig> configs = mapper.selectList(
                    new LambdaQueryWrapper<SourceWeightConfig>()
                            .eq(SourceWeightConfig::getIsActive, 1));

            Map<String, BigDecimal> newCache = new ConcurrentHashMap<>();
            for (SourceWeightConfig config : configs) {
                String originalSourceType = config.getSourceType();
                String canonicalSourceType = AbilitySourceType.canonicalize(originalSourceType);
                if (canonicalSourceType.equals(originalSourceType) || !newCache.containsKey(canonicalSourceType)) {
                    newCache.put(canonicalSourceType, config.getWeight());
                }
            }

            weightCache = newCache;
            cacheLoadedAt = System.currentTimeMillis();
            log.debug("来源权重缓存已刷新，size={}", newCache.size());
            return newCache;
        }
    }

    private void invalidateCache() {
        weightCache = null;
        cacheLoadedAt = 0;
    }
}
