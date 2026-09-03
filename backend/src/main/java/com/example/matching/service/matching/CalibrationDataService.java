package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.enums.FeedbackReasonEnum;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingFeedbackDimension;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDimensionMapper;
import com.example.matching.port.post.PostQueryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工校准数据中心：查询、筛选、流式导出（JSONL/CSV）。
 * <p>
 * 导出原则：
 * - 默认不导出姓名、手机号、简历正文、原始面试文本；
 * - exportEnabled=true 的数据必须有原始 AI 分、人工最终分；
 * - 每个被修正维度必须有合法原因码（FeedbackReasonEnum）；
 * - 流式响应，避免一次性加载大数据集。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationDataService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MatchingFeedbackDatasetMapper feedbackDatasetMapper;
    private final MatchingFeedbackDimensionMapper feedbackDimensionMapper;
    private final PostQueryPort postQueryPort;

    public IPage<com.example.matching.dto.matching.CalibrationRecordVO> pageCalibration(long current, long size,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           Long postId, Boolean exportEnabled) {
        var wrapper = Wrappers.<MatchingFeedbackDataset>lambdaQuery();
        if (startTime != null) {
            wrapper.ge(MatchingFeedbackDataset::getFeedbackTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MatchingFeedbackDataset::getFeedbackTime, endTime);
        }
        if (postId != null) {
            wrapper.eq(MatchingFeedbackDataset::getPostId, postId);
        }
        if (exportEnabled != null) {
            wrapper.eq(MatchingFeedbackDataset::getExportEnabled, exportEnabled ? 1 : 0);
        }
        wrapper.orderByDesc(MatchingFeedbackDataset::getFeedbackTime);
        IPage<MatchingFeedbackDataset> page = feedbackDatasetMapper.selectPage(new Page<>(current, size), wrapper);
        return page.convert(CalibrationDataService::toVo);
    }

    /**
     * 流式导出校准数据。仅导出满足约束的样本：
     * exportEnabled=true 且 aiMatchScore/finalMatchScore 非空；
     * 若 includeDimensions=true，被修正维度必须有合法原因码，否则该样本剔除。
     * <p>
     * 导出文件头部包含清单元数据（数据集版本、导出时间、筛选条件、字段说明、样本数量、脱敏策略）。
     * 按 ID 分页游标逐批加载（避免全量内存），维度按批次批量查询（避免 N+1）。
     */
    public void exportCalibration(String format, LocalDateTime startTime, LocalDateTime endTime,
                                  Long postId, boolean includeDimensions,
                                  boolean maskPersonalData, OutputStream out) throws IOException {
        final int pageSize = 200;

        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeManifest(writer, format, startTime, endTime, postId, includeDimensions, maskPersonalData);
            if ("csv".equalsIgnoreCase(format)) {
                writeCsvHeader(writer, includeDimensions);
            }
            Long cursorId = null;
            while (true) {
                List<MatchingFeedbackDataset> batch = loadExportPage(cursorId, pageSize, startTime, endTime, postId);
                if (batch.isEmpty()) {
                    break;
                }
                Map<Long, List<MatchingFeedbackDimension>> dimensionsByFeedback = includeDimensions
                        ? loadDimensionsBatch(batch)
                        : Map.of();

                for (MatchingFeedbackDataset sample : batch) {
                    if (!Integer.valueOf(1).equals(sample.getExportEnabled())
                            || sample.getAiMatchScore() == null
                            || sample.getFinalMatchScore() == null) {
                        continue;
                    }
                    if ("csv".equalsIgnoreCase(format)) {
                        writeCsvRow(writer, sample, includeDimensions, maskPersonalData,
                                dimensionsByFeedback.get(sample.getId()));
                    } else {
                        String line = buildJsonlLine(sample, includeDimensions, maskPersonalData,
                                dimensionsByFeedback.get(sample.getId()));
                        if (line != null) {
                            writer.write(line);
                            writer.write("\n");
                        }
                    }
                }
                writer.flush();
                cursorId = batch.get(batch.size() - 1).getId();
            }
            writer.flush();
        }
    }

    /**
     * 导出清单元数据：数据集版本、导出时间、筛选条件、字段说明、样本数量、脱敏策略。
     * JSONL 以 manifest 行开头；CSV 以 # 注释行开头。
     */
    private void writeManifest(Writer writer, String format, LocalDateTime startTime, LocalDateTime endTime,
                               Long postId, boolean includeDimensions, boolean maskPersonalData) throws IOException {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("startTime", startTime != null ? startTime.toString() : null);
        filters.put("endTime", endTime != null ? endTime.toString() : null);
        filters.put("postId", postId);
        filters.put("exportEnabled", 1);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("type", "manifest");
        manifest.put("schemaVersion", "calibration-v1");
        manifest.put("exportedAt", LocalDateTime.now().toString());
        manifest.put("filters", filters);
        manifest.put("fieldDescriptions", Map.of(
                "matchingRecordId", "匹配记录ID（可溯源）",
                "postId", "岗位ID",
                "empId", "员工ID（默认脱敏为****后两位）",
                "aiMatchScore", "系统/AI匹配分（原始）",
                "finalMatchScore", "人工最终分",
                "matchStatus", "最终匹配状态",
                "feedbackComment", "人工调整说明",
                "templateVersion", "校准模板版本",
                "corrections", "维度级人工修正（dimensionKey/systemRawScore/manualRawScore/reasonCode/reasonText）"));
        manifest.put("maskingPolicy", maskPersonalData ? "EMPLOYEE_ID_MASKED" : "NONE");
        manifest.put("sampleCount", countExportSamples(startTime, endTime, postId));

        if ("csv".equalsIgnoreCase(format)) {
            writer.write("# manifest: " + toJson(manifest).replace("\n", " "));
            writer.write("\n");
        } else {
            writer.write(toJson(manifest));
            writer.write("\n");
        }
    }

    private long countExportSamples(LocalDateTime startTime, LocalDateTime endTime, Long postId) {
        var wrapper = Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .eq(MatchingFeedbackDataset::getExportEnabled, 1)
                .isNotNull(MatchingFeedbackDataset::getAiMatchScore)
                .isNotNull(MatchingFeedbackDataset::getFinalMatchScore);
        if (startTime != null) {
            wrapper.ge(MatchingFeedbackDataset::getFeedbackTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MatchingFeedbackDataset::getFeedbackTime, endTime);
        }
        if (postId != null) {
            wrapper.eq(MatchingFeedbackDataset::getPostId, postId);
        }
        return feedbackDatasetMapper.selectCount(wrapper);
    }

    private List<MatchingFeedbackDataset> loadExportPage(Long cursorId, int pageSize,
                                                          LocalDateTime startTime, LocalDateTime endTime, Long postId) {
        var wrapper = Wrappers.<MatchingFeedbackDataset>lambdaQuery()
                .eq(MatchingFeedbackDataset::getExportEnabled, 1)
                .isNotNull(MatchingFeedbackDataset::getAiMatchScore)
                .isNotNull(MatchingFeedbackDataset::getFinalMatchScore);
        if (cursorId != null) {
            wrapper.gt(MatchingFeedbackDataset::getId, cursorId);
        }
        if (startTime != null) {
            wrapper.ge(MatchingFeedbackDataset::getFeedbackTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MatchingFeedbackDataset::getFeedbackTime, endTime);
        }
        if (postId != null) {
            wrapper.eq(MatchingFeedbackDataset::getPostId, postId);
        }
        wrapper.orderByAsc(MatchingFeedbackDataset::getId)
                .last("LIMIT " + pageSize);
        return feedbackDatasetMapper.selectList(wrapper);
    }

    private Map<Long, List<MatchingFeedbackDimension>> loadDimensionsBatch(List<MatchingFeedbackDataset> batch) {
        List<Long> feedbackIds = batch.stream().map(MatchingFeedbackDataset::getId).toList();
        if (feedbackIds.isEmpty()) {
            return Map.of();
        }
        List<MatchingFeedbackDimension> dimensions = feedbackDimensionMapper.selectList(
                Wrappers.<MatchingFeedbackDimension>lambdaQuery()
                        .in(MatchingFeedbackDimension::getFeedbackId, feedbackIds));
        Map<Long, List<MatchingFeedbackDimension>> byFeedback = new LinkedHashMap<>();
        for (MatchingFeedbackDimension dim : dimensions) {
            byFeedback.computeIfAbsent(dim.getFeedbackId(), k -> new ArrayList<>()).add(dim);
        }
        return byFeedback;
    }

    private String buildJsonlLine(MatchingFeedbackDataset sample, boolean includeDimensions, boolean maskPersonalData,
                                  List<MatchingFeedbackDimension> preloadedDimensions) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "calibration-v1");
        root.put("matchingRecordId", sample.getMatchingRecordId());

        Map<String, Object> post = new LinkedHashMap<>();
        post.put("id", sample.getPostId());
        if (!maskPersonalData) {
            com.example.matching.port.post.PostQueryPort.PostDTO postDto = postQueryPort.getPostById(sample.getPostId());
            post.put("name", postDto != null ? postDto.postName() : null);
        }
        root.put("post", post);

        Map<String, Object> employee = new LinkedHashMap<>();
        employee.put("id", maskPersonalData ? maskId(sample.getEmpId()) : sample.getEmpId());
        root.put("employee", employee);

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("aiMatchScore", sample.getAiMatchScore());
        scores.put("finalMatchScore", sample.getFinalMatchScore());
        scores.put("matchStatus", sample.getFinalMatchStatus());
        root.put("scores", scores);

        if (includeDimensions) {
            List<MatchingFeedbackDimension> dimensions = preloadedDimensions != null ? preloadedDimensions : loadDimensions(sample.getId());
            List<Map<String, Object>> corrections = new ArrayList<>();
            for (MatchingFeedbackDimension dim : dimensions) {
                if (!isValidReasonCode(dim.getReasonCode())) {
                    return null; // 非法原因码：该样本不进入导出文件
                }
                Map<String, Object> correction = new LinkedHashMap<>();
                correction.put("dimensionKey", dim.getDimensionKey());
                correction.put("systemRawScore", dim.getSystemRawScore());
                correction.put("manualRawScore", dim.getManualRawScore());
                correction.put("reasonCode", dim.getReasonCode());
                correction.put("reasonText", dim.getReasonText());
                corrections.add(correction);
            }
            root.put("corrections", corrections);
        }

        root.put("feedbackComment", sample.getFeedbackComment());
        root.put("templateVersion", sample.getCalibrationTemplateVersion() != null ? sample.getCalibrationTemplateVersion() : "v1");
        root.put("createdAt", sample.getFeedbackTime() != null ? sample.getFeedbackTime().toString() : null);
        return toJson(root);
    }

    private void writeCsvHeader(Writer writer, boolean includeDimensions) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("matchingRecordId,postId,empId,aiMatchScore,finalMatchScore,matchStatus,feedbackComment,templateVersion,createdAt");
        if (includeDimensions) {
            header.append(",dimensionKey,manualRawScore,reasonCode,reasonText");
        }
        writer.write(header.toString());
        writer.write("\n");
    }

    private void writeCsvRow(Writer writer, MatchingFeedbackDataset sample,
                             boolean includeDimensions, boolean maskPersonalData,
                             List<MatchingFeedbackDimension> preloadedDimensions) throws IOException {
        if (!includeDimensions) {
            StringBuilder row = new StringBuilder();
            row.append(sample.getMatchingRecordId()).append(',')
                    .append(sample.getPostId()).append(',')
                    .append(maskPersonalData ? maskId(sample.getEmpId()) : sample.getEmpId()).append(',')
                    .append(sample.getAiMatchScore()).append(',')
                    .append(sample.getFinalMatchScore()).append(',')
                    .append(sample.getFinalMatchStatus()).append(',')
                    .append(csvEscape(sample.getFeedbackComment())).append(',')
                    .append(sample.getCalibrationTemplateVersion() != null ? sample.getCalibrationTemplateVersion() : "v1").append(',')
                    .append(sample.getFeedbackTime());
            writer.write(row.toString());
            writer.write("\n");
            return;
        }
        List<MatchingFeedbackDimension> dimensions = preloadedDimensions != null ? preloadedDimensions : loadDimensions(sample.getId());
        if (dimensions.isEmpty()) {
            return; // 要求维度但无修正维度：剔除
        }
        for (MatchingFeedbackDimension dim : dimensions) {
            if (!isValidReasonCode(dim.getReasonCode())) {
                return; // 非法原因码：该样本剔除
            }
            StringBuilder row = new StringBuilder();
            row.append(sample.getMatchingRecordId()).append(',')
                    .append(sample.getPostId()).append(',')
                    .append(maskPersonalData ? maskId(sample.getEmpId()) : sample.getEmpId()).append(',')
                    .append(sample.getAiMatchScore()).append(',')
                    .append(sample.getFinalMatchScore()).append(',')
                    .append(sample.getFinalMatchStatus()).append(',')
                    .append(csvEscape(sample.getFeedbackComment())).append(',')
                    .append(sample.getCalibrationTemplateVersion() != null ? sample.getCalibrationTemplateVersion() : "v1").append(',')
                    .append(sample.getFeedbackTime()).append(',')
                    .append(dim.getDimensionKey()).append(',')
                    .append(dim.getManualRawScore()).append(',')
                    .append(dim.getReasonCode()).append(',')
                    .append(csvEscape(dim.getReasonText()));
            writer.write(row.toString());
            writer.write("\n");
        }
    }

    private List<MatchingFeedbackDimension> loadDimensions(Long feedbackId) {
        return feedbackDimensionMapper.selectList(Wrappers.<MatchingFeedbackDimension>lambdaQuery()
                .eq(MatchingFeedbackDimension::getFeedbackId, feedbackId));
    }

    private boolean isValidReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return false;
        }
        try {
            FeedbackReasonEnum.valueOf(reasonCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String maskId(Long id) {
        if (id == null) {
            return null;
        }
        String value = String.valueOf(id);
        String suffix = value.length() <= 2 ? value : value.substring(value.length() - 2);
        return "****" + suffix;
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize calibration sample", e);
            return null;
        }
    }

    private static com.example.matching.dto.matching.CalibrationRecordVO toVo(MatchingFeedbackDataset f) {
        return new com.example.matching.dto.matching.CalibrationRecordVO(
                f.getId(), f.getMatchingRecordId(), f.getEmpId(), f.getPostId(),
                f.getAiMatchScore(), f.getFinalMatchScore(), f.getFinalMatchStatus(),
                f.getAdoptionStatus(), f.getFeedbackReasons(), f.getFeedbackComment(),
                f.getCalibrationSource(), f.getCalibrationTemplateVersion(), f.getExportEnabled(), f.getFeedbackTime());
    }
}