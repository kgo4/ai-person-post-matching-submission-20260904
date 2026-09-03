package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.DimensionCorrectionDTO;
import com.example.matching.dto.matching.StructuredReviewDTO;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingFeedbackDimension;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDimensionMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.evaluation.FeedbackReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化复核服务
 * <p>
 * 处理人工对匹配结果的结构化复核，支持维度级别的修正和原因代码。
 * 替代原有的 MatchingRecord 瞬态字段传递方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredReviewService {

    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    private final MatchingFeedbackDimensionMapper feedbackDimensionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 提交结构化复核
     * <p>
     * 流程：
     * 1. 验证匹配记录存在且未锁定
     * 2. 验证维度修正的原因代码
     * 3. 更新匹配记录的最终分和状态
     * 4. 创建反馈数据集记录
     * 5. 创建维度级别反馈明细
     *
     * @param request 结构化复核请求
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, key = "#request.matchingRecordId"),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_AI_REPORT, key = "#request.matchingRecordId"),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void submitStructuredReview(StructuredReviewDTO request) {
        // 1. 验证匹配记录
        MatchingRecord record = matchingRecordMapper.selectById(request.getMatchingRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        }
        if (record.getIsLocked() != null && record.getIsLocked() == 1) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_ALREADY_LOCKED);
        }

        // 2. 验证维度修正的原因代码
        validateDimensionCorrections(request.getDimensionCorrections());

        // 3. 保存原始分数用于反馈
        BigDecimal originalAiScore = record.getAiMatchScore();
        Integer originalStatus = record.getMatchStatus();

        // 4. 更新匹配记录
        if (request.getFinalMatchScore() != null) {
            record.setFinalMatchScore(request.getFinalMatchScore());
        }
        if (request.getMatchStatus() != null) {
            record.setMatchStatus(request.getMatchStatus());
        }
        if (request.getManualRemark() != null) {
            record.setManualRemark(request.getManualRemark());
        }

        // 构建并保存 manualBreakdownJson
        if (request.getDimensionCorrections() != null && !request.getDimensionCorrections().isEmpty()) {
            try {
                Map<String, Object> manualBreakdown = new LinkedHashMap<>();
                for (DimensionCorrectionDTO correction : request.getDimensionCorrections()) {
                    Map<String, Object> dimData = new LinkedHashMap<>();
                    dimData.put("manualScore", correction.getManualScore());
                    dimData.put("reasonCode", correction.getReasonCode());
                    dimData.put("reasonText", correction.getReasonText());
                    manualBreakdown.put(correction.getDimensionKey(), dimData);
                }
                record.setManualBreakdownJson(objectMapper.writeValueAsString(manualBreakdown));
            } catch (Exception e) {
                log.warn("序列化manualBreakdown失败: {}", e.getMessage());
            }
        }

        matchingRecordMapper.updateById(record);

        // 5. 创建反馈数据集记录
        MatchingFeedbackDataset feedback = createFeedbackRecord(record, originalAiScore, originalStatus, request);
        feedbackDatasetMapper.insert(feedback);

        // 6. 创建维度级别反馈明细
        if (request.getDimensionCorrections() != null && !request.getDimensionCorrections().isEmpty()) {
            createDimensionFeedback(feedback.getId(), record.getId(), request.getDimensionCorrections());
        }
    }

    /**
     * 验证维度修正的原因代码
     * <p>
     * 训练eligible的修正必须包含原因代码。
     */
    private void validateDimensionCorrections(List<DimensionCorrectionDTO> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return;
        }

        for (DimensionCorrectionDTO correction : corrections) {
            if (correction.getReasonCode() == null || correction.getReasonCode().isBlank()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "维度 " + correction.getDimensionKey() + " 的修正必须包含原因代码");
            }

            // 验证原因代码是否有效
            try {
                FeedbackReasonCode.valueOf(correction.getReasonCode());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "无效的原因代码: " + correction.getReasonCode());
            }

            // 验证原因代码是否匹配维度
            FeedbackReasonCode code = FeedbackReasonCode.valueOf(correction.getReasonCode());
            if (!"any".equals(code.getDimensionKey())
                    && !code.getDimensionKey().equals(correction.getDimensionKey())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "原因代码 " + correction.getReasonCode() + " 不适用于维度 " + correction.getDimensionKey());
            }
        }
    }

    /**
     * 创建反馈数据集记录
     */
    private MatchingFeedbackDataset createFeedbackRecord(MatchingRecord record,
                                                           BigDecimal originalAiScore,
                                                           Integer originalStatus,
                                                           StructuredReviewDTO request) {
        MatchingFeedbackDataset feedback = new MatchingFeedbackDataset();
        feedback.setMatchingRecordId(record.getId());
        feedback.setEmpId(record.getEmpId());
        feedback.setPostId(record.getPostId());
        feedback.setAiMatchScore(originalAiScore);
        feedback.setFinalMatchScore(record.getFinalMatchScore());
        feedback.setFinalMatchStatus(record.getMatchStatus());
        feedback.setFeedbackTime(LocalDateTime.now());
        feedback.setFeedbackComment(request.getFeedbackComment());
        feedback.setCalibrationSource("STRUCTURED_REVIEW");
        feedback.setCalibrationTemplateVersion("v1");
        feedback.setExportEnabled(Boolean.TRUE.equals(request.getExportEnabled()) ? 1 : 0);

        // 计算采纳状态
        if (record.getFinalMatchScore() != null && originalAiScore != null) {
            double diff = Math.abs(record.getFinalMatchScore().doubleValue() - originalAiScore.doubleValue());
            if (diff < 5 && record.getMatchStatus().equals(originalStatus)) {
                feedback.setAdoptionStatus(1); // 完全采纳
            } else if (diff < 15) {
                feedback.setAdoptionStatus(2); // 部分采纳
            } else {
                feedback.setAdoptionStatus(3); // 未采纳
            }
        } else {
            feedback.setAdoptionStatus(2);
        }

        return feedback;
    }

    /**
     * 创建维度级别反馈明细
     */
    private void createDimensionFeedback(Long feedbackId, Long matchingRecordId,
                                           List<DimensionCorrectionDTO> corrections) {
        // 从 scoreBreakdownJson 中解析系统维度分数
        Map<String, Map<String, Object>> systemBreakdown = loadSystemBreakdown(matchingRecordId);

        List<MatchingFeedbackDimension> dimensions = new ArrayList<>();
        for (DimensionCorrectionDTO correction : corrections) {
            MatchingFeedbackDimension dim = new MatchingFeedbackDimension();
            dim.setFeedbackId(feedbackId);
            dim.setMatchingRecordId(matchingRecordId);
            dim.setDimensionKey(correction.getDimensionKey());
            dim.setManualRawScore(correction.getManualScore());
            dim.setReasonCode(correction.getReasonCode());
            dim.setReasonText(correction.getReasonText());

            // 从系统分解中读取原始分数和权重
            Map<String, Object> sysDim = systemBreakdown.get(correction.getDimensionKey());
            if (sysDim != null) {
                dim.setSystemRawScore(toBigDecimal(sysDim.get("rawScore")));
                dim.setSystemWeight(toBigDecimal(sysDim.get("weight")));
                dim.setSystemWeightedScore(toBigDecimal(sysDim.get("weightedScore")));
            }

            // 计算人工修正加权分
            if (dim.getSystemWeight() != null && correction.getManualScore() != null) {
                dim.setManualWeightedScore(
                        correction.getManualScore().multiply(dim.getSystemWeight())
                                .setScale(2, RoundingMode.HALF_UP));
            }

            dimensions.add(dim);
        }

        // 批量插入
        for (MatchingFeedbackDimension dim : dimensions) {
            feedbackDimensionMapper.insert(dim);
        }
    }

    /**
     * 从匹配记录中加载系统评分分解
     */
    private Map<String, Map<String, Object>> loadSystemBreakdown(Long matchingRecordId) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        try {
            MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
            if (record != null && record.getScoreBreakdownJson() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> breakdown = objectMapper.readValue(
                        record.getScoreBreakdownJson(), Map.class);
                for (Map.Entry<String, Object> entry : breakdown.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dimData = (Map<String, Object>) entry.getValue();
                        result.put(entry.getKey(), dimData);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载系统评分分解失败: matchingRecordId={}, error={}", matchingRecordId, e.getMessage());
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
