package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.service.employee.AiTestService;
import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.example.matching.entity.workflow.AssessmentAgentArtifact;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTestTaskListener {

    private final AiTestService aiTestService;
    private final ObjectMapper objectMapper;
    private AssessmentAgentArtifactService artifactService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setArtifactService(AssessmentAgentArtifactService value) { this.artifactService = value; }

    @RabbitListener(queues = RabbitMQConfig.AI_TEST_QUEUE, containerFactory = "slowRabbitListenerContainerFactory")
    public void handleAiTestTask(Object message) {
        AiTestTaskPayload payload = resolvePayload(message);
        if (payload == null) {
            log.warn("忽略无法解析的 AI 测试消息: {}", message);
            return;
        }
        log.info("收到AI测试任务: type={}, testId={}", payload.getTaskType(), payload.getTestId());
        SecurityUtils.setSystemContext();
        try {
            if ("GENERATE".equals(payload.getTaskType())) {
                aiTestService.processGenerateQuestions(payload.getTestId());
            } else if ("EVALUATE".equals(payload.getTaskType())) {
                aiTestService.processEvaluateAnswers(payload.getTestId());
            } else {
                log.warn("忽略未知 AI 测试任务类型: type={}, testId={}",
                        payload.getTaskType(), payload.getTestId());
            }
        } catch (Exception e) {
            // A single assessment task must never escape into the listener container.
            // The task services persist their own failure/recovery state whenever possible;
            // this guard prevents a poison message from blocking the consumer.
            log.error("AI 测试任务发生未处理异常，已隔离: type={}, testId={}, error={}",
                    payload.getTaskType(), payload.getTestId(), e.getMessage(), e);
        } finally {
            SecurityUtils.clear();
        }
    }

    private AiTestTaskPayload resolvePayload(Object message) {
        if (message instanceof AiTestTaskPayload payload) return payload;
        if (message instanceof Message rawMessage) {
            return resolveJsonPayload(new String(rawMessage.getBody(), StandardCharsets.UTF_8));
        }
        if (message instanceof byte[] body) return resolveJsonPayload(new String(body, StandardCharsets.UTF_8));
        if (message instanceof AgentMessageEnvelope envelope) return resolveArtifactPayload(envelope);
        return null;
    }

    private AiTestTaskPayload resolveJsonPayload(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            AgentMessageEnvelope envelope = objectMapper.readValue(json, AgentMessageEnvelope.class);
            return resolveArtifactPayload(envelope);
        } catch (Exception e) {
            // Only legacy, non-workflow messages may carry a task body directly.
            try {
                return objectMapper.readValue(json, AiTestTaskPayload.class);
            } catch (Exception ignored) {
                log.error("解析 AI 测试消息失败: error={}", e.getMessage(), e);
                return null;
            }
        }
    }

    private AiTestTaskPayload resolveArtifactPayload(AgentMessageEnvelope envelope) {
        if (envelope == null || artifactService == null || envelope.payloadRef() == null) return null;
        try {
            AssessmentAgentArtifact artifact = artifactService.get(envelope.payloadRef());
            String json = artifactService.readPayload(artifact);
            return objectMapper.readValue(json, AiTestTaskPayload.class);
        } catch (Exception e) {
            log.error("恢复 AI 测试 Artifact 失败: payloadRef={}, error={}", envelope.payloadRef(), e.getMessage(), e);
            return null;
        }
    }
}
