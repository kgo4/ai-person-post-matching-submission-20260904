package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.ai.context.dto.AiContextAbilityDTO;
import com.example.matching.ai.context.dto.AiContextGraphSummaryDTO;
import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.dto.AiContextScoreBreakdownDTO;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.service.AiContextPackageService;
import com.example.matching.application.agent.AgentScoreBreakdown;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.application.agent.PostRequirementSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent上下文包服务实现
 * <p>
 * 适配器模式：调用 AiContextPackageService 构建完整上下文，然后转换为 Agent 使用的强类型格式。
 * 所有上下文数据都来自 AiContextPackageService，确保数据来源统一。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextPackageServiceImpl implements AgentContextPackageService {

    private final AiContextPackageService aiContextPackageService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentContextPackage buildForMatchingRecord(Long matchingRecordId) {
        AiContextPackageDTO aiContext = aiContextPackageService.buildForMatching(matchingRecordId);
        return convertToAgentContext(aiContext);
    }

    @Override
    public AgentContextPackage buildForEmployee(Long empId) {
        AiContextPackageDTO aiContext = aiContextPackageService.buildForEmployee(empId);
        return convertToAgentContext(aiContext);
    }

    @Override
    public AgentContextPackage buildForPost(Long postId) {
        AiContextPackageDTO aiContext = aiContextPackageService.buildForPost(postId);
        return convertToAgentContext(aiContext);
    }

    /**
     * 将 AiContextPackageDTO 转换为 AgentContextPackage（强类型）
     */
    private AgentContextPackage convertToAgentContext(AiContextPackageDTO aiContext) {
        if (aiContext == null) {
            return null;
        }

        AgentContextPackage context = new AgentContextPackage();
        context.setEmpId(aiContext.getEmpId());
        context.setEmpName(aiContext.getEmpName());
        context.setPostId(aiContext.getPostId());
        context.setPostName(aiContext.getPostName());
        context.setMatchingRecordId(aiContext.getMatchingRecordId());
        context.setMatchScore(aiContext.getMatchScore());

        if (aiContext.getEmployeeAbilities() != null) {
            context.setEmployeeAbilities(
                    aiContext.getEmployeeAbilities().stream()
                            .map(AgentContextPackageServiceImpl::toEmployeeAbilitySnapshot)
                            .collect(Collectors.toList()));
        }

        if (aiContext.getPostRequirements() != null) {
            context.setPostRequirements(
                    aiContext.getPostRequirements().stream()
                            .map(AgentContextPackageServiceImpl::toPostRequirementSnapshot)
                            .collect(Collectors.toList()));
        }

        if (aiContext.getScoreBreakdown() != null) {
            context.setScoreBreakdown(
                    aiContext.getScoreBreakdown().stream()
                            .map(AgentContextPackageServiceImpl::toAgentScoreBreakdown)
                            .collect(Collectors.toList()));
        }

        if (aiContext.getSourceRefs() != null) {
            context.setSourceRefs(
                    aiContext.getSourceRefs().stream()
                            .map(AgentContextPackageServiceImpl::toSourceRef)
                            .collect(Collectors.toList()));
        }

        if (aiContext.getGraphSummary() != null) {
            context.setGraphSummary(toMap(aiContext.getGraphSummary()));
        }

        if (aiContext.getFeedbackSignals() != null) {
            context.setFeedbackSignals(aiContext.getFeedbackSignals());
        }

        return context;
    }

    private static EmployeeAbilitySnapshot toEmployeeAbilitySnapshot(AiContextAbilityDTO dto) {
        return new EmployeeAbilitySnapshot(
                dto.getAbilityTagId(),
                dto.getAbilityName(),
                dto.getCurrentLevel(),
                dto.getSource(),
                dto.getCredibility(),
                dto.getEvidenceCount() != null ? dto.getEvidenceCount() : 0);
    }

    private static PostRequirementSnapshot toPostRequirementSnapshot(AiContextAbilityDTO dto) {
        return new PostRequirementSnapshot(
                dto.getAbilityTagId(),
                dto.getAbilityName(),
                dto.getRequiredLevel(),
                dto.getWeight(),
                dto.getRequired() != null && dto.getRequired(),
                dto.getCore() != null && dto.getCore());
    }

    private static AgentScoreBreakdown toAgentScoreBreakdown(AiContextScoreBreakdownDTO dto) {
        return new AgentScoreBreakdown(
                dto.getDimension(),
                dto.getScore(),
                dto.getWeight(),
                dto.getDescription());
    }

    private static AgentSourceRef toSourceRef(AiContextSourceRefDTO dto) {
        AgentSourceRef ref = new AgentSourceRef();
        ref.setRef(dto.getRef());
        ref.setRefType(dto.getRefType());
        ref.setRefId(dto.getRefId());
        ref.setTitle(dto.getTitle());
        ref.setSnippet(dto.getSnippet());
        ref.setSourceType(dto.getSourceType());
        ref.setConfidenceScore(dto.getConfidenceScore());
        ref.setCredibilityScore(dto.getCredibilityScore());
        ref.setReviewStatus(dto.getReviewStatus());
        return ref;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(AiContextGraphSummaryDTO dto) {
        return objectMapper.convertValue(dto, Map.class);
    }
}
