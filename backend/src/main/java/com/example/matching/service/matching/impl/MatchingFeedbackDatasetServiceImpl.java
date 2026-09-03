package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.enums.FeedbackReasonEnum;
import com.example.matching.dto.matching.MatchingFeedbackExportDTO;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.MatchingFeedbackDatasetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingFeedbackDatasetServiceImpl extends ServiceImpl<MatchingFeedbackDatasetMapper, MatchingFeedbackDataset> implements MatchingFeedbackDatasetService {

    private final MatchingRecordMapper matchingRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void submitFeedback(MatchingFeedbackDataset feedback) {
        if (feedback.getMatchingRecordId() != null
                && (feedback.getAiMatchScore() == null || feedback.getEmpId() == null || feedback.getPostId() == null)) {
            MatchingRecord record = matchingRecordMapper.selectById(feedback.getMatchingRecordId());
            if (record != null) {
                if (feedback.getAiMatchScore() == null) {
                    feedback.setAiMatchScore(record.getAiMatchScore());
                }
                if (feedback.getEmpId() == null) {
                    feedback.setEmpId(record.getEmpId());
                }
                if (feedback.getPostId() == null) {
                    feedback.setPostId(record.getPostId());
                }
            }
        }
        if (feedback.getFeedbackTime() == null) {
            feedback.setFeedbackTime(LocalDateTime.now());
        }
        if (feedback.getCalibrationSource() == null || feedback.getCalibrationSource().isBlank()) {
            feedback.setCalibrationSource("MANUAL_FEEDBACK");
        }
        if (feedback.getCalibrationTemplateVersion() == null || feedback.getCalibrationTemplateVersion().isBlank()) {
            feedback.setCalibrationTemplateVersion("v1");
        }
        if (feedback.getExportEnabled() == null) {
            feedback.setExportEnabled(0);
        }
        save(feedback);
    }

    @Override
    public IPage<MatchingFeedbackDataset> pageFeedback(IPage<MatchingFeedbackDataset> page, Integer exportEnabled) {
        LambdaQueryWrapper<MatchingFeedbackDataset> wrapper = Wrappers.<MatchingFeedbackDataset>lambdaQuery();
        if (exportEnabled != null) {
            wrapper.eq(MatchingFeedbackDataset::getExportEnabled, exportEnabled);
        }
        wrapper.orderByDesc(MatchingFeedbackDataset::getFeedbackTime);
        return page(page, wrapper);
    }

    @Override
    public Map<String, Object> getFeedbackSummary(int limit) {
        List<MatchingFeedbackDataset> recent = list(Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .orderByDesc(MatchingFeedbackDataset::getFeedbackTime)
                .last("LIMIT " + limit));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalFeedback", recent.size());

        // 采纳情况统计
        long fullAdoption = recent.stream().filter(f -> f.getAdoptionStatus() != null && f.getAdoptionStatus() == 1).count();
        long partialAdoption = recent.stream().filter(f -> f.getAdoptionStatus() != null && f.getAdoptionStatus() == 2).count();
        long noAdoption = recent.stream().filter(f -> f.getAdoptionStatus() != null && f.getAdoptionStatus() == 3).count();
        summary.put("fullAdoption", fullAdoption);
        summary.put("partialAdoption", partialAdoption);
        summary.put("noAdoption", noAdoption);

        // 平均偏差
        OptionalDouble avgDiff = recent.stream()
                .filter(f -> f.getAiMatchScore() != null && f.getFinalMatchScore() != null)
                .mapToDouble(f -> Math.abs(f.getFinalMatchScore().doubleValue() - f.getAiMatchScore().doubleValue()))
                .average();
        summary.put("averageDeviation", avgDiff.orElse(0));

        return summary;
    }

    @Override
    public List<String> getRecentFeedbackExamples(int limit) {
        List<MatchingFeedbackDataset> recent = list(Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .orderByDesc(MatchingFeedbackDataset::getFeedbackTime)
                .last("LIMIT " + limit));

        return recent.stream().map(f -> {
            MatchingRecord record = matchingRecordMapper.selectById(f.getMatchingRecordId());
            String report = record != null ? record.getAiAnalysisReport() : "";
            return String.format("AI评分%.1f，人工调整为%.1f（偏差%.1f）。%s",
                    f.getAiMatchScore() != null ? f.getAiMatchScore() : 0,
                    f.getFinalMatchScore() != null ? f.getFinalMatchScore() : 0,
                    f.getAiMatchScore() != null && f.getFinalMatchScore() != null
                            ? Math.abs(f.getFinalMatchScore().doubleValue() - f.getAiMatchScore().doubleValue()) : 0,
                    truncate(report, 200));
        }).collect(Collectors.toList());
    }

    @Override
    public List<MatchingFeedbackExportDTO> exportFeedback(Integer exportEnabled) {
        LambdaQueryWrapper<MatchingFeedbackDataset> wrapper = Wrappers.<MatchingFeedbackDataset>lambdaQuery();
        if (exportEnabled != null) {
            wrapper.eq(MatchingFeedbackDataset::getExportEnabled, exportEnabled);
        }
        wrapper.orderByDesc(MatchingFeedbackDataset::getFeedbackTime);

        List<MatchingFeedbackDataset> feedbackList = list(wrapper);

        return feedbackList.stream().map(this::convertToExportDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void batchUpdateExportStatus(List<Long> ids, Integer exportEnabled) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<MatchingFeedbackDataset> updateWrapper = Wrappers.<MatchingFeedbackDataset>lambdaUpdate()
                .set(MatchingFeedbackDataset::getExportEnabled, exportEnabled)
                .in(MatchingFeedbackDataset::getId, ids);
        update(updateWrapper);
    }

    @Override
    public Map<String, Object> getFeedbackTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<MatchingFeedbackDataset> feedbackList = list(Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .ge(MatchingFeedbackDataset::getFeedbackTime, LocalDateTime.of(startDate, LocalTime.MIN))
                .le(MatchingFeedbackDataset::getFeedbackTime, LocalDateTime.of(endDate, LocalTime.MAX))
                .orderByAsc(MatchingFeedbackDataset::getFeedbackTime));

        // 按日期分组统计
        Map<LocalDate, List<MatchingFeedbackDataset>> groupedByDate = feedbackList.stream()
                .collect(Collectors.groupingBy(f -> f.getFeedbackTime().toLocalDate()));

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<Double> avgDeviations = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date.toString());
            List<MatchingFeedbackDataset> dayFeedback = groupedByDate.getOrDefault(date, Collections.emptyList());
            counts.add((long) dayFeedback.size());

            OptionalDouble avgDev = dayFeedback.stream()
                    .filter(f -> f.getAiMatchScore() != null && f.getFinalMatchScore() != null)
                    .mapToDouble(f -> Math.abs(f.getFinalMatchScore().doubleValue() - f.getAiMatchScore().doubleValue()))
                    .average();
            avgDeviations.add(avgDev.orElse(0));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("counts", counts);
        result.put("avgDeviations", avgDeviations);
        return result;
    }

    @Override
    public Map<String, Object> getDeviationDistribution(int limit) {
        List<MatchingFeedbackDataset> recent = list(Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .isNotNull(MatchingFeedbackDataset::getAiMatchScore)
                .isNotNull(MatchingFeedbackDataset::getFinalMatchScore)
                .orderByDesc(MatchingFeedbackDataset::getFeedbackTime)
                .last("LIMIT " + limit));

        // 偏差分布：0-5, 5-10, 10-15, 15-20, 20+
        int[] ranges = {0, 5, 10, 15, 20, Integer.MAX_VALUE};
        String[] labels = {"0-5分", "5-10分", "10-15分", "15-20分", "20分以上"};
        long[] distribution = new long[labels.length];

        for (MatchingFeedbackDataset fb : recent) {
            double deviation = Math.abs(fb.getFinalMatchScore().doubleValue() - fb.getAiMatchScore().doubleValue());
            for (int i = 0; i < ranges.length - 1; i++) {
                if (deviation >= ranges[i] && deviation < ranges[i + 1]) {
                    distribution[i]++;
                    break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("distribution", distribution);
        result.put("totalSamples", recent.size());
        return result;
    }

    @Override
    public Map<String, Object> getCalibrationReplaySummary(int limit) {
        List<MatchingFeedbackDataset> recent = list(Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .isNotNull(MatchingFeedbackDataset::getAiMatchScore)
                .isNotNull(MatchingFeedbackDataset::getFinalMatchScore)
                .orderByDesc(MatchingFeedbackDataset::getFeedbackTime)
                .last("LIMIT " + limit));

        Map<String, Object> result = new HashMap<>();
        result.put("totalSamples", recent.size());
        if (recent.isEmpty()) {
            result.put("currentAverageDeviation", 0d);
            result.put("decalibratedAverageDeviation", 0d);
            result.put("currentAverageBias", 0d);
            result.put("decalibratedAverageBias", 0d);
            result.put("calibrationTargetCounts", Map.of());
            return result;
        }

        double currentDeviationSum = 0d;
        double currentBiasSum = 0d;
        double decalibratedDeviationSum = 0d;
        double decalibratedBiasSum = 0d;
        int validReplayCount = 0;
        Map<String, Long> calibrationTargetCounts = new LinkedHashMap<>();

        for (MatchingFeedbackDataset feedback : recent) {
            BigDecimal aiScore = feedback.getAiMatchScore();
            BigDecimal finalScore = feedback.getFinalMatchScore();
            if (aiScore == null || finalScore == null) {
                continue;
            }

            double currentBias = aiScore.subtract(finalScore).doubleValue();
            currentBiasSum += currentBias;
            currentDeviationSum += Math.abs(currentBias);

            MatchingRecord record = matchingRecordMapper.selectById(feedback.getMatchingRecordId());
            BigDecimal decalibratedScore = removeCalibration(aiScore, record);
            double decalibratedBias = decalibratedScore.subtract(finalScore).doubleValue();
            decalibratedBiasSum += decalibratedBias;
            decalibratedDeviationSum += Math.abs(decalibratedBias);
            validReplayCount++;

            for (String target : resolveCalibrationTargets(feedback.getFeedbackReasons())) {
                calibrationTargetCounts.merge(target, 1L, Long::sum);
            }
        }

        if (validReplayCount == 0) {
            result.put("currentAverageDeviation", 0d);
            result.put("decalibratedAverageDeviation", 0d);
            result.put("currentAverageBias", 0d);
            result.put("decalibratedAverageBias", 0d);
            result.put("calibrationTargetCounts", calibrationTargetCounts);
            return result;
        }

        result.put("currentAverageDeviation", roundDouble(currentDeviationSum / validReplayCount));
        result.put("decalibratedAverageDeviation", roundDouble(decalibratedDeviationSum / validReplayCount));
        result.put("currentAverageBias", roundDouble(currentBiasSum / validReplayCount));
        result.put("decalibratedAverageBias", roundDouble(decalibratedBiasSum / validReplayCount));
        result.put("calibrationTargetCounts", calibrationTargetCounts);
        return result;
    }

    private MatchingFeedbackExportDTO convertToExportDTO(MatchingFeedbackDataset entity) {
        MatchingFeedbackExportDTO dto = new MatchingFeedbackExportDTO();
        dto.setId(entity.getId());
        dto.setMatchingRecordId(entity.getMatchingRecordId());
        dto.setEmpId(entity.getEmpId());
        dto.setPostId(entity.getPostId());
        dto.setAiMatchScore(entity.getAiMatchScore());
        dto.setFinalMatchScore(entity.getFinalMatchScore());
        dto.setFinalMatchStatus(entity.getFinalMatchStatus());
        dto.setFeedbackReasons(entity.getFeedbackReasons());
        dto.setFeedbackComment(entity.getFeedbackComment());
        dto.setFeedbackTime(entity.getFeedbackTime());

        // 采纳状态文本
        if (entity.getAdoptionStatus() != null) {
            switch (entity.getAdoptionStatus()) {
                case 1:
                    dto.setAdoptionStatusText("完全采纳");
                    break;
                case 2:
                    dto.setAdoptionStatusText("部分采纳");
                    break;
                case 3:
                    dto.setAdoptionStatusText("未采纳");
                    break;
                default:
                    dto.setAdoptionStatusText("未知");
            }
        }

        if (entity.getExportEnabled() != null) {
            dto.setExportStatusText(entity.getExportEnabled() == 1 ? "允许导出" : "不允许导出");
        }

        return dto;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private BigDecimal removeCalibration(BigDecimal aiScore, MatchingRecord record) {
        // 反馈训练数据记录的是同一份正式评分；质量/校准不再作为隐藏加减分项。
        return aiScore == null ? BigDecimal.ZERO : aiScore.setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> resolveCalibrationTargets(String feedbackReasonsJson) {
        if (feedbackReasonsJson == null || feedbackReasonsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> reasonCodes = objectMapper.readValue(feedbackReasonsJson, new TypeReference<List<String>>() {});
            return reasonCodes.stream()
                    .map(FeedbackReasonEnum::getByCode)
                    .filter(Objects::nonNull)
                    .map(FeedbackReasonEnum::getCalibrationTarget)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
