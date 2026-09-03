package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 反馈校准服务 —— 支持总分校准。
 *
 * <p>校准公式: calibration = -avg(aiFinal - humanFinal) * factor，clamp 到 ±10。</p>
 * <p>注：维度级校准（calculateDimensionAdjustments）为死代码且未接入打分引擎，
 * 已随本次审计显式清理；若未来需要维度级回灌，应设计为逐维度调整量而非整条记录覆盖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackCalibrationService {

    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;

    private static final BigDecimal MAX_CALIBRATION = new BigDecimal("10.00");
    private static final BigDecimal MIN_CALIBRATION = new BigDecimal("-10.00");
    private static final BigDecimal CALIBRATION_FACTOR = new BigDecimal("0.30");

    /**
     * 计算指定岗位的反馈校准值（总分基础）。
     *
     * @param postId 岗位ID
     * @return 校准值（-10 到 +10），无反馈数据时返回 0
     */
    public BigDecimal calculateCalibration(Long postId) {
        return calculateCalibration(postId, 20);
    }

    /**
     * 计算指定岗位的反馈校准值
     *
     * @param postId 岗位ID
     * @param limit  参考的最近反馈条数
     * @return 校准值（-10 到 +10），无反馈数据时返回 0
     */
    public BigDecimal calculateCalibration(Long postId, int limit) {
        List<MatchingFeedbackDataset> recentFeedback = feedbackDatasetMapper.selectList(
                Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                        .eq(MatchingFeedbackDataset::getPostId, postId)
                        .isNotNull(MatchingFeedbackDataset::getAiMatchScore)
                        .isNotNull(MatchingFeedbackDataset::getFinalMatchScore)
                        .orderByDesc(MatchingFeedbackDataset::getFeedbackTime)
                        .last("LIMIT " + limit));

        if (recentFeedback.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDeviation = BigDecimal.ZERO;
        int count = 0;
        for (MatchingFeedbackDataset fb : recentFeedback) {
            if (fb.getAiMatchScore() != null && fb.getFinalMatchScore() != null) {
                BigDecimal deviation = fb.getAiMatchScore().subtract(fb.getFinalMatchScore());
                totalDeviation = totalDeviation.add(deviation);
                count++;
            }
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgDeviation = totalDeviation.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        BigDecimal calibration = avgDeviation.negate().multiply(CALIBRATION_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
        calibration = calibration.max(MIN_CALIBRATION).min(MAX_CALIBRATION);

        log.debug("岗位{}反馈校准: avgDeviation={}, calibration={}, sampleSize={}",
                postId, avgDeviation, calibration, count);

        return calibration;
    }
}
