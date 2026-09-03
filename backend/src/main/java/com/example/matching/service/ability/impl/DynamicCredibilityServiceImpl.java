package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.entity.common.DynamicCredibilityWeight;
import com.example.matching.mapper.common.DynamicCredibilityWeightMapper;
import com.example.matching.service.ability.DynamicCredibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态来源可信度服务实现
 * <p>
 * 基于RAG反馈和交叉验证结果，动态调整不同来源的可信度权重。
 * 权重持久化到数据库，启动时加载，更新使用乐观锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicCredibilityServiceImpl implements DynamicCredibilityService {

    private final DynamicCredibilityWeightMapper weightMapper;
    private final ObjectMapper objectMapper;

    /** 内存缓存：source -> weight (0-1)，启动时从DB加载 */
    private final Map<String, Double> dynamicWeights = new ConcurrentHashMap<>();

    private static final double MIN_WEIGHT = 0.30;
    private static final double MAX_WEIGHT = 1.00;
    private static final double ADJUSTMENT_STEP = 0.02;

    @PostConstruct
    public void init() {
        List<DynamicCredibilityWeight> all = weightMapper.selectList(Wrappers.<DynamicCredibilityWeight>lambdaQuery());
        for (DynamicCredibilityWeight w : all) {
            dynamicWeights.put(AbilitySourceType.canonicalize(w.getSourceType()), w.getWeight());
        }
        log.info("已从数据库加载动态可信度权重: {} 条", all.size());
    }

    @Override
    public double getWeight(String source) {
        String canonicalSource = AbilitySourceType.canonicalize(source);
        Double dynamicWeight = dynamicWeights.get(canonicalSource);
        if (dynamicWeight != null) {
            return dynamicWeight;
        }
        return AbilitySourceCredibility.getWeightBySource(source);
    }

    @Override
    public void recordFeedback(String source, boolean isConfirmed, Integer correctionLevel) {
        if (source == null) {
            return;
        }
        String normalizedSource = AbilitySourceType.canonicalize(source);
        double delta = isConfirmed ? ADJUSTMENT_STEP
                : (correctionLevel != null && correctionLevel > 2 ? -ADJUSTMENT_STEP * 2 : -ADJUSTMENT_STEP);

        adjustWeight(normalizedSource, delta, isConfirmed);
    }

    @Override
    public int evaluateSourceQuality(String source) {
        if (source == null) return 50;
        return evaluateFromFeedbackStats(source);
    }

    @Override
    public void resetToDefault(String source) {
        if (source == null) {
            dynamicWeights.clear();
            weightMapper.delete(Wrappers.<DynamicCredibilityWeight>lambdaQuery());
            log.info("所有来源可信度已重置为默认值");
        } else {
            String normalizedSource = AbilitySourceType.canonicalize(source);
            dynamicWeights.remove(normalizedSource);
            weightMapper.delete(Wrappers.<DynamicCredibilityWeight>lambdaQuery()
                    .eq(DynamicCredibilityWeight::getSourceType, normalizedSource));
            log.info("来源可信度已重置: source={}", source);
        }
    }

    @Override
    public String getCredibilityDetail(String source) {
        if (source == null) {
            return "{}";
        }
        String normalizedSource = AbilitySourceType.canonicalize(source);
        double currentWeight = getWeight(normalizedSource);
        double defaultWeight = AbilitySourceCredibility.getWeightBySource(normalizedSource);
        boolean isDynamic = dynamicWeights.containsKey(normalizedSource);

        DynamicCredibilityWeight dbRecord = weightMapper.selectOne(
                Wrappers.<DynamicCredibilityWeight>lambdaQuery()
                        .eq(DynamicCredibilityWeight::getSourceType, normalizedSource));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("source", normalizedSource);
        detail.put("currentWeight", Math.round(currentWeight * 100.0) / 100.0);
        detail.put("defaultWeight", Math.round(defaultWeight * 100.0) / 100.0);
        detail.put("isDynamic", isDynamic);

        if (dbRecord != null) {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("total", dbRecord.getTotalFeedback());
            feedback.put("confirmed", dbRecord.getConfirmCount());
            feedback.put("corrected", dbRecord.getCorrectionCount());
            int total = dbRecord.getConfirmCount() + dbRecord.getCorrectionCount();
            feedback.put("confirmRate", total > 0 ? Math.round((double) dbRecord.getConfirmCount() / total * 100.0) / 100.0 : 0.5);
            detail.put("feedback", feedback);
        } else {
            detail.put("feedback", null);
        }

        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void adjustWeight(String source, double delta, boolean isConfirmed) {
        // 更新内存缓存
        dynamicWeights.compute(source, (key, currentWeight) -> {
            double baseWeight = currentWeight != null
                    ? currentWeight
                    : AbilitySourceCredibility.getWeightBySource(key);
            return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, baseWeight + delta));
        });

        // 持久化到数据库（upsert with optimistic lock）
        DynamicCredibilityWeight existing = weightMapper.selectOne(
                Wrappers.<DynamicCredibilityWeight>lambdaQuery()
                        .eq(DynamicCredibilityWeight::getSourceType, source));

        if (existing != null) {
            existing.setWeight(dynamicWeights.get(source));
            if (isConfirmed) {
                existing.setConfirmCount(existing.getConfirmCount() + 1);
            } else {
                existing.setCorrectionCount(existing.getCorrectionCount() + 1);
            }
            existing.setTotalFeedback(existing.getTotalFeedback() + 1);
            try {
                weightMapper.updateById(existing);
            } catch (Exception e) {
                log.warn("动态可信度持久化失败(乐观锁冲突): source={}, error={}", source, e.getMessage());
            }
        } else {
            DynamicCredibilityWeight record = new DynamicCredibilityWeight();
            record.setSourceType(source);
            record.setWeight(dynamicWeights.get(source));
            record.setConfirmCount(isConfirmed ? 1 : 0);
            record.setCorrectionCount(isConfirmed ? 0 : 1);
            record.setTotalFeedback(1L);
            try {
                weightMapper.insert(record);
            } catch (Exception e) {
                log.warn("动态可信度插入失败(可能并发创建): source={}, error={}", source, e.getMessage());
            }
        }
    }

    private int evaluateFromFeedbackStats(String source) {
        DynamicCredibilityWeight dbRecord = weightMapper.selectOne(
                Wrappers.<DynamicCredibilityWeight>lambdaQuery()
                        .eq(DynamicCredibilityWeight::getSourceType, AbilitySourceType.canonicalize(source)));

        if (dbRecord == null || dbRecord.getTotalFeedback() == 0) {
            return (int) (AbilitySourceCredibility.getWeightBySource(source) * 100);
        }

        int total = dbRecord.getConfirmCount() + dbRecord.getCorrectionCount();
        double confirmRate = total > 0 ? (double) dbRecord.getConfirmCount() / total : 0.5;
        return (int) (confirmRate * 100);
    }
}
