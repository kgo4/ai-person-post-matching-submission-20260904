package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.example.matching.entity.workflow.AssessmentAgentArtifact;
import com.example.matching.mapper.workflow.AssessmentAgentArtifactMapper;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessmentAgentArtifactServiceImpl implements AssessmentAgentArtifactService {
    private static final String CONTRACT_VERSION = "assessment-a2a-v1";
    private final AssessmentAgentArtifactMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentMessageEnvelope storeStageTask(Long workflowId, Long stageRunId, String stageType,
                                                String scopeHash, String taxonomyVersion) {
        String content = write(Map.of("stageRunId", stageRunId, "stageType", stageType));
        String hash = sha256(content);
        AssessmentAgentArtifact existing = mapper.selectOne(new LambdaQueryWrapper<AssessmentAgentArtifact>()
                .eq(AssessmentAgentArtifact::getWorkflowId, workflowId)
                .eq(AssessmentAgentArtifact::getArtifactType, "STAGE_TASK")
                .eq(AssessmentAgentArtifact::getContentHash, hash)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new AssessmentAgentArtifact();
            existing.setWorkflowId(workflowId);
            existing.setStageRunId(stageRunId);
            existing.setArtifactType("STAGE_TASK");
            existing.setContentJson(content);
            existing.setContentHash(hash);
            existing.setCreatedTime(LocalDateTime.now());
            mapper.insert(existing);
        }
        return new AgentMessageEnvelope(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                workflowId, stageRunId, "CapabilityAssessmentOrchestrator", "CapabilityAssessmentStageRunner",
                CONTRACT_VERSION, existing.getId(), scopeHash, taxonomyVersion, 1,
                Instant.now().plusSeconds(300), 1);
    }

    @Override
    public AssessmentAgentArtifact get(Long artifactId) {
        return mapper.selectById(artifactId);
    }

    @Override
    @Transactional
    public AgentMessageEnvelope storePayload(Long workflowId, Long stageRunId, String artifactType,
                                              Object payload, String scopeHash, String taxonomyVersion) {
        String content = write(payload);
        String hash = sha256(content);
        AssessmentAgentArtifact existing = mapper.selectOne(new LambdaQueryWrapper<AssessmentAgentArtifact>()
                .eq(AssessmentAgentArtifact::getWorkflowId, workflowId)
                .eq(AssessmentAgentArtifact::getArtifactType, artifactType)
                .eq(AssessmentAgentArtifact::getContentHash, hash)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new AssessmentAgentArtifact();
            existing.setWorkflowId(workflowId);
            existing.setStageRunId(stageRunId);
            existing.setArtifactType(artifactType);
            existing.setContentJson(content);
            existing.setContentHash(hash);
            existing.setCreatedTime(LocalDateTime.now());
            mapper.insert(existing);
        }
        return new AgentMessageEnvelope(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                workflowId, stageRunId, "CapabilityAssessmentOrchestrator", artifactType,
                CONTRACT_VERSION, existing.getId(), scopeHash, taxonomyVersion, 1,
                Instant.now().plusSeconds(300), 1);
    }

    @Override
    public String readPayload(AssessmentAgentArtifact artifact) {
        return artifact == null ? null : artifact.getContentJson();
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("无法序列化 Agent Artifact", e); }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("无法计算 Artifact 哈希", e); }
    }
}
