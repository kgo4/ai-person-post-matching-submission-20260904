package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.example.matching.entity.employee.EmpVideoInterviewEvidence;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.integration.volcengine.DoubaoChatClient;
import com.example.matching.mapper.employee.EmpVideoInterviewEvidenceMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 视频面试视觉证据分析：关键帧选取、图像转 DataURL、视觉 AI 分析、证据标记。
 * <p>
 * 从 VideoInterviewServiceImpl（690 行）中拆分的视觉分析组件。
 * <p>
 * <b>唯一厂商依赖例外（第一期）</b>：视频/图片视觉分析使用火山豆包视觉模型（DoubaoChatClient），
 * 不纳入企业全局文本模型（EnterpriseChatLanguageModel）——企业全局模型只承载文本类 AI 业务。
 * 若企业模型不支持视觉能力（或本功能未启用），视觉分析跳过并标记为"不支持"，
 * 不影响文本链路。后续如需统一，可改为 OpenAI-compatible 多模态适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoInterviewVisualAnalyzer {

    private final EmpVideoInterviewEvidenceMapper evidenceMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;
    @Qualifier("aiTaskExecutor")
    private final Executor aiTaskExecutor;
    private final com.example.matching.integration.volcengine.VideoInterviewPromptBuilder promptBuilder;
    private final DoubaoChatClient doubaoChatClient;
    private final ObjectMapper objectMapper;

    /** 视觉分析开关：关闭或企业模型不支持视觉时跳过视觉分析。 */
    @org.springframework.beans.factory.annotation.Value("${video-interview.visual-analysis-enabled:true}")
    private boolean visualAnalysisEnabled = true;

    /** Visual evidence is auxiliary and must never keep the interview in ANALYZING indefinitely. */
    @org.springframework.beans.factory.annotation.Value("${video-interview.visual-analysis-timeout-seconds:60}")
    private long visualAnalysisTimeoutSeconds = 60;

    @org.springframework.beans.factory.annotation.Value("${video-interview.visual-analysis-max-questions:6}")
    private int visualAnalysisMaxQuestions = 6;

    private static final String EVIDENCE_TYPE_VISUAL = "VISUAL";
    /** 每个预设题或追问只发送一张中段稳定帧，控制视觉请求体积与模型耗时。 */
    public void analyzeVisualEvidence(Long sessionId) {
        if (!visualAnalysisEnabled) {
            log.info("Visual analysis is disabled (video-interview.visual-analysis-enabled=false), "
                    + "skipping visual evidence for sessionId={}", sessionId);
            return;
        }
        List<EmpVideoInterviewEvidence> visualEvidences = evidenceMapper.selectList(
                Wrappers.<EmpVideoInterviewEvidence>lambdaQuery()
                        .eq(EmpVideoInterviewEvidence::getSessionId, sessionId)
                        .eq(EmpVideoInterviewEvidence::getEvidenceType, EVIDENCE_TYPE_VISUAL)
        );
        if (visualEvidences.isEmpty()) {
            log.info("No visual frames collected for interview, sessionId={}", sessionId);
            return;
        }

        Map<VisualUnit, List<EmpVideoInterviewEvidence>> byUnit = visualEvidences.stream()
                .filter(e -> e.getQuestionId() != null)
                .collect(Collectors.groupingBy(this::visualUnitOf));

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        byUnit.entrySet().stream().limit(Math.max(1, visualAnalysisMaxQuestions)).forEach(entry -> {
            futures.add(CompletableFuture.runAsync(() -> {
                analyzeVisualUnit(entry.getKey(), entry.getValue());
            }, aiTaskExecutor));
        });

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(visualAnalysisTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            futures.forEach(future -> future.cancel(true));
            log.warn("视觉分析超时，降级为文本面试分析: sessionId={}, timeoutSeconds={}",
                    sessionId, visualAnalysisTimeoutSeconds);
        } catch (Exception e) {
            log.error("并发视觉分析中断: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    public void analyzeQuestionVisualEvidence(Long questionId, List<EmpVideoInterviewEvidence> evidences) {
        analyzeVisualUnit(new VisualUnit(questionId, null), evidences);
    }

    private void analyzeVisualUnit(VisualUnit unit, List<EmpVideoInterviewEvidence> evidences) {
        Long questionId = unit.questionId();
        EmpVideoInterviewQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            return;
        }

        // 非视觉题或音频题跳过视觉分析
        if ("AUDIO".equals(question.getQuestionType())) {
            return;
        }

        // 关键帧采样：每个预设题或追问只取一张中段稳定帧。
        List<EmpVideoInterviewEvidence> keyFrames = selectKeyFrames(evidences);
        if (keyFrames.isEmpty()) {
            markVisualEvidence(evidences, "未采集到可分析视觉证据。", BigDecimal.valueOf(0.3), null);
            return;
        }

        List<Map<String, Object>> frameRefs = new ArrayList<>();
        List<String> imageDataUrls = new ArrayList<>();
        for (EmpVideoInterviewEvidence evidence : keyFrames) {
            Map<String, Object> ref = parseFrameRef(evidence.getFrameRefsJson());
            if (!ref.isEmpty()) {
                frameRefs.add(ref);
            }
            String imagePath = ref.get("imagePath") != null ? ref.get("imagePath").toString() : null;
            String dataUrl = imagePathToDataUrl(imagePath);
            if (dataUrl != null) {
                imageDataUrls.add(dataUrl);
            }
        }

        if (imageDataUrls.isEmpty()) {
            markVisualEvidence(evidences, "视觉帧已采集，但图片文件不可读，未形成视觉分析结论。", null, null);
            return;
        }

        String questionText = resolveVisualUnitText(question, unit.followUpId());
        String prompt = promptBuilder.buildVisionAnalysisPrompt(null, questionText, frameRefs);
        String response = doubaoChatClient.analyzeVision(null, prompt, imageDataUrls);
        if (response == null || response.isBlank()) {
            markVisualEvidence(evidences, "视觉帧已采集，但视觉模型暂未返回结果，本条不参与评分。", null, null);
        } else {
            markVisualEvidence(evidences, response, BigDecimal.valueOf(0.8), extractVisualScore(response));
        }
    }

    public List<EmpVideoInterviewEvidence> selectKeyFrames(List<EmpVideoInterviewEvidence> evidences) {
        if (evidences.isEmpty()) {
            return List.of();
        }
        // 只保留中段稳定帧：每个预设题或追问最多一张，避免多图请求造成视觉模型超时。
        List<EmpVideoInterviewEvidence> sorted = new ArrayList<>(evidences);
        sorted.sort(Comparator.comparing(e -> {
            Map<String, Object> ref = parseFrameRef(e.getFrameRefsJson());
            return ref.get("captureSecond") instanceof Number ? ((Number) ref.get("captureSecond")).intValue() : 0;
        }));
        return List.of(sorted.get(sorted.size() / 2));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseFrameRef(String frameRefsJson) {
        if (frameRefsJson == null || frameRefsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(frameRefsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Only an explicit model score can enter the presentation score; text alone is not a score. */
    BigDecimal extractVisualScore(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            JsonNode score = objectMapper.readTree(response).path("visualScore");
            if (!score.isNumber()) {
                return null;
            }
            return BigDecimal.valueOf(Math.max(0D, Math.min(100D, score.asDouble())));
        } catch (Exception ignored) {
            return null;
        }
    }

    public String imagePathToDataUrl(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(imagePath));
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Failed to read visual frame image, path={}, error={}", imagePath, e.getMessage());
            return null;
        }
    }

    public void markVisualEvidence(List<EmpVideoInterviewEvidence> evidences, String text,
                                    BigDecimal confidenceScore, BigDecimal rawScore) {
        for (EmpVideoInterviewEvidence evidence : evidences) {
            evidence.setEvidenceText(text);
            evidence.setConfidenceScore(confidenceScore);
            evidence.setRawScore(rawScore);
            evidenceMapper.updateById(evidence);
        }
    }

    public String buildFrameRefsJson(Long sessionId, VideoInterviewFrameDTO dto) {
        try {
            String imagePath = saveFrameImage(sessionId, dto);
            Map<String, Object> frameRef = new LinkedHashMap<>();
            frameRef.put("questionOrder", dto.getQuestionOrder());
            if (dto.getFollowUpId() != null) {
                frameRef.put("followUpId", dto.getFollowUpId());
            }
            frameRef.put("captureSecond", dto.getCaptureSecond());
            frameRef.put("mimeType", "image/jpeg");
            frameRef.put("imagePath", imagePath);
            return objectMapper.writeValueAsString(frameRef);
        } catch (Exception e) {
            log.warn("构建视频抽帧引用失败: {}", e.getMessage());
            return "{}";
        }
    }

    public String saveFrameImage(Long sessionId, VideoInterviewFrameDTO dto) throws Exception {
        String base64 = dto.getImageDataUrl().substring("data:image/jpeg;base64,".length());
        byte[] bytes = Base64.getDecoder().decode(base64);
        Path uploadDir = Paths.get("uploads/video-interview", sessionId.toString()).toAbsolutePath();
        Files.createDirectories(uploadDir);
        String unit = dto.getFollowUpId() == null ? "q" + dto.getQuestionOrder()
                : "followup" + dto.getFollowUpId();
        String filename = String.format("%s_%s.jpg", unit, dto.getCaptureSecond());
        Path filePath = uploadDir.resolve(filename).toAbsolutePath();
        Files.write(filePath, bytes);
        return filePath.toString();
    }

    private VisualUnit visualUnitOf(EmpVideoInterviewEvidence evidence) {
        Map<String, Object> frameRef = parseFrameRef(evidence.getFrameRefsJson());
        Object followUpId = frameRef.get("followUpId");
        return new VisualUnit(evidence.getQuestionId(), followUpId instanceof Number number ? number.longValue() : null);
    }

    private String resolveVisualUnitText(EmpVideoInterviewQuestion question, Long followUpId) {
        if (followUpId == null) {
            return question.getQuestionText();
        }
        InterviewFollowUpQuestion followUp = followUpQuestionMapper.selectById(followUpId);
        if (followUp != null && followUp.getQuestionText() != null && !followUp.getQuestionText().isBlank()) {
            return followUp.getQuestionText();
        }
        return question.getQuestionText();
    }

    private record VisualUnit(Long questionId, Long followUpId) {
    }
}
