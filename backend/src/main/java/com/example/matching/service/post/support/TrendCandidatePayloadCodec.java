package com.example.matching.service.post.support;

import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.entity.post.PostTrendCandidate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选载荷的 JSON 编解码。
 * <p>
 * 抽出来的唯一理由：审核侧（读取 → 人工调整 → 写回）与落地侧（读取 → 写能力模型）
 * 必须用同一套解析口径。两处各写一份 {@code readValue}，一旦字段增删就会一边成功一边静默丢数据。
 * <p>
 * 解析失败一律降级为「空载荷」并留日志，而不是抛异常：
 * 一条候选的载荷损坏不该让整个候选列表打不开，但落地时会因「没有任何能力项」明确失败，
 * 不会出现「岗位建了、能力没了」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendCandidatePayloadCodec {

    private final ObjectMapper objectMapper;

    public TrendCandidatePayload read(String json) {
        if (!StringUtils.hasText(json)) {
            return new TrendCandidatePayload();
        }
        try {
            TrendCandidatePayload payload = objectMapper.readValue(json, TrendCandidatePayload.class);
            if (payload == null) {
                return new TrendCandidatePayload();
            }
            if (payload.getAbilities() == null) {
                payload.setAbilities(new ArrayList<>());
            }
            if (payload.getResponsibilities() == null) {
                payload.setResponsibilities(new ArrayList<>());
            }
            if (payload.getBusinessScenarios() == null) {
                payload.setBusinessScenarios(new ArrayList<>());
            }
            if (payload.getUnmatchedExistingAbilities() == null) {
                payload.setUnmatchedExistingAbilities(new ArrayList<>());
            }
            return payload;
        } catch (Exception e) {
            log.warn("趋势候选载荷解析失败，按空载荷处理: err={}", e.getMessage());
            return new TrendCandidatePayload();
        }
    }

    public TrendCandidatePayload read(PostTrendCandidate candidate) {
        return candidate == null ? new TrendCandidatePayload() : read(candidate.getCandidatePayload());
    }

    public String write(TrendCandidatePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? new TrendCandidatePayload() : payload);
        } catch (Exception e) {
            log.warn("趋势候选载荷序列化失败: err={}", e.getMessage());
            return "{}";
        }
    }

    /** 读取列里存字符串数组的 JSON（source_refs / source_document_ids 等）。 */
    public List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return values == null ? new ArrayList<>() : values;
        } catch (Exception e) {
            log.warn("字符串数组JSON解析失败，按空列表处理: err={}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
