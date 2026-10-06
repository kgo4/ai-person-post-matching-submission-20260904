package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.example.matching.entity.workflow.AssessmentAgentArtifact;

public interface AssessmentAgentArtifactService {
    AgentMessageEnvelope storeStageTask(Long workflowId, Long stageRunId, String stageType,
                                        String scopeHash, String taxonomyVersion);
    AssessmentAgentArtifact get(Long artifactId);
    AgentMessageEnvelope storePayload(Long workflowId, Long stageRunId, String artifactType,
                                      Object payload, String scopeHash, String taxonomyVersion);
    String readPayload(AssessmentAgentArtifact artifact);
}
