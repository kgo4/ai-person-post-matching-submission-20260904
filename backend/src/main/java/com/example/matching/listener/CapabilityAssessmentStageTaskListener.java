package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.service.assessment.CapabilityAssessmentStageRunner;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.example.matching.dto.assessment.AgentMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 能力评估阶段任务消费者
 * <p>
 * 消费 Outbox 投递的阶段执行任务（capability.assessment.stage.execute），
 * 由 StageRunner 执行单个阶段（聚合审核/等级确认等）。
 * 消费端 CAS 抢占保证同一阶段并发只执行一次。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityAssessmentStageTaskListener {

    private final CapabilityAssessmentStageRunner stageRunner;
    private final AssessmentAgentArtifactService artifactService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CAPABILITY_ASSESSMENT_STAGE_QUEUE)
    public void onStageTask(Object payload) {
        Long stageRunId = resolveStageRunId(payload);
        if (stageRunId == null) {
            log.warn("能力评估阶段任务参数为空，跳过");
            return;
        }
        try {
            stageRunner.runStage(stageRunId);
        } catch (Exception e) {
            log.error("能力评估阶段执行异常（StageRunner内部已处理重试/失败标记）: stageRunId={}, error={}",
                    stageRunId, e.getMessage(), e);
        }
    }

    private Long resolveStageRunId(Object payload) {
        if (payload instanceof Number n) return n.longValue();
        try {
            AgentMessageEnvelope envelope;
            if (payload instanceof Message raw) {
                envelope = objectMapper.readValue(new String(raw.getBody(), StandardCharsets.UTF_8),
                        AgentMessageEnvelope.class);
            } else if (payload instanceof byte[] body) {
                envelope = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), AgentMessageEnvelope.class);
            } else {
                envelope = objectMapper.convertValue(payload, AgentMessageEnvelope.class);
            }
            if (envelope.payloadRef() == null) return null;
            var artifact = artifactService.get(envelope.payloadRef());
            if (artifact == null || artifact.getStageRunId() == null) return null;
            return artifact.getStageRunId();
        } catch (Exception e) {
            log.warn("无法解析评估阶段 A2A Envelope: {}", e.getMessage());
            return null;
        }
    }
}
