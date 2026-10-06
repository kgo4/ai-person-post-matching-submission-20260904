package com.example.matching.ai.context.service.impl;

import com.example.matching.ai.context.dto.*;
import com.example.matching.ai.context.service.AiContextCompressorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI上下文压缩服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextCompressorServiceImpl implements AiContextCompressorService {

    private final ObjectMapper objectMapper;

    /** 列表上限 */
    private static final int MAX_EMPLOYEE_ABILITIES = 20;
    private static final int MAX_POST_REQUIREMENTS = 20;
    private static final int MAX_GAPS = 12;
    private static final int MAX_EVIDENCES = 30;
    private static final int MAX_RISK_SIGNALS = 10;
    private static final int MAX_SOURCE_REFS = 60;

    @Override
    public AiContextPackageDTO compress(AiContextPackageDTO context) {
        if (context == null) {
            return null;
        }

        // 1. 压缩员工能力列表（按可信度排序，保留上限）
        if (context.getEmployeeAbilities() != null) {
            context.setEmployeeAbilities(
                    context.getEmployeeAbilities().stream()
                            .sorted(Comparator.comparing(
                                    (AiContextAbilityDTO a) -> a.getCredibility() != null ? a.getCredibility() : java.math.BigDecimal.ZERO)
                                    .reversed())
                            .limit(MAX_EMPLOYEE_ABILITIES)
                            .collect(Collectors.toList())
            );
        }

        // 2. 压缩岗位要求列表（核心能力优先）
        if (context.getPostRequirements() != null) {
            context.setPostRequirements(
                    context.getPostRequirements().stream()
                            .sorted(Comparator.comparing(
                                    (AiContextAbilityDTO a) -> Boolean.TRUE.equals(a.getCore()) ? 0 : 1)
                                    .thenComparing(Comparator.comparing(
                                            (AiContextAbilityDTO a) -> a.getWeight() != null ? a.getWeight() : java.math.BigDecimal.ZERO)
                                            .reversed()))
                            .limit(MAX_POST_REQUIREMENTS)
                            .collect(Collectors.toList())
            );
        }

        // 3. 压缩差距列表（核心能力缺口优先）
        if (context.getGaps() != null) {
            context.setGaps(
                    context.getGaps().stream()
                            .sorted(Comparator.comparing(
                                    (AiContextGapDTO g) -> "HIGH".equals(g.getPriority()) ? 0 :
                                            "MEDIUM".equals(g.getPriority()) ? 1 : 2)
                                    .thenComparing(Comparator.comparing(
                                            (AiContextGapDTO g) -> g.getGap() != null ? g.getGap() : 0)
                                            .reversed()))
                            .limit(MAX_GAPS)
                            .collect(Collectors.toList())
            );
        }

        // 4. 压缩证据列表（已审核优先，高可信度优先）
        if (context.getEvidences() != null) {
            context.setEvidences(
                    context.getEvidences().stream()
                            .sorted(Comparator.comparing(
                                    (AiContextEvidenceDTO e) -> "VERIFIED".equals(e.getEvidenceStatus()) ? 0 : 1)
                                    .thenComparing(Comparator.comparing(
                                            (AiContextEvidenceDTO e) -> e.getCredibilityScore() != null ? e.getCredibilityScore() : java.math.BigDecimal.ZERO)
                                            .reversed()))
                            .limit(MAX_EVIDENCES)
                            .collect(Collectors.toList())
            );
        }

        // 5. 压缩风险信号
        if (context.getRiskSignals() != null) {
            context.setRiskSignals(
                    context.getRiskSignals().stream()
                            .sorted(Comparator.comparing(
                                    (AiContextRiskSignalDTO r) -> "HIGH".equals(r.getRiskLevel()) ? 0 :
                                            "MEDIUM".equals(r.getRiskLevel()) ? 1 : 2))
                            .limit(MAX_RISK_SIGNALS)
                            .collect(Collectors.toList())
            );
        }

        // 6. 压缩来源引用
        if (context.getSourceRefs() != null) {
            context.setSourceRefs(
                    context.getSourceRefs().stream()
                            .limit(MAX_SOURCE_REFS)
                            .collect(Collectors.toList())
            );
        }

        // 7. 计算token估算
        context.setTokenEstimate(estimateTokens(context));

        // 8. 计算上下文hash
        context.setContextHash(calculateHash(context));

        return context;
    }

    /**
     * 粗略估算token数量
     * 中文约2字符/token，英文约4字符/token
     */
    private int estimateTokens(AiContextPackageDTO context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            // 粗略估算：每2个字符约1个token
            return Math.max(1, json.length() / 2);
        } catch (JsonProcessingException e) {
            log.warn("估算token失败", e);
            return 0;
        }
    }

    /**
     * 计算上下文hash
     */
    private String calculateHash(AiContextPackageDTO context) {
        try {
            // 使用关键字段计算hash
            String key = context.getScenario() + ":" +
                    context.getMatchingRecordId() + ":" +
                    context.getEmpId() + ":" +
                    context.getPostId();

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("计算hash失败", e);
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
