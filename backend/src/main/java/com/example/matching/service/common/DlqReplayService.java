package com.example.matching.service.common;

import com.example.matching.common.trace.TraceContext;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.system.SysOperationLogService;
import com.example.matching.utils.SecurityUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ReturnListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DLQ 消息重放服务
 * <p>
 * DLQ 不能靠普通消费者记录日志（否则会破坏重放能力）。
 * 重放流程（RabbitTemplate.execute 内单通道完成，禁止丢消息）：
 * <ol>
 *   <li>basicGet(queue, false) 获取但不 ACK</li>
 *   <li>从 x-first-death-exchange / x-first-death-routing-key / x-death 还原原路由</li>
 *   <li>发布到原交换机并 waitForConfirmsOrDie</li>
 *   <li>发布确认后 basicAck 原消息</li>
 *   <li>任一失败执行 basicNack(requeue=true)，消息回到 DLQ</li>
 * </ol>
 * 每条操作写 SysOperationLog（消息 ID、原路由、操作者、结果、失败原因），
 * 不记录消息正文中的个人信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private static final String HEADER_FIRST_DEATH_EXCHANGE = "x-first-death-exchange";
    private static final String HEADER_FIRST_DEATH_ROUTING_KEY = "x-first-death-routing-key";
    private static final String HEADER_DEATH = "x-death";

    private final RabbitTemplate rabbitTemplate;
    private final SysOperationLogService sysOperationLogService;

    /**
     * DLQ 摘要：队列消息数、最近检查时间、阈值状态
     */
    public DlqSummary summary() {
        return rabbitTemplate.execute(channel -> {
            long depth = channel.messageCount(RabbitMQConfig.DEAD_LETTER_QUEUE);
            return new DlqSummary(depth, LocalDateTime.now());
        });
    }

    /**
     * 顺序重放最早消息，最多 count 条（1..100）
     *
     * @return 实际重放条数
     */
    public int replay(int count) {
        return rabbitTemplate.execute(channel -> {
            channel.confirmSelect();
            AtomicReference<String> returnedRoute = new AtomicReference<>();
            ReturnListener returnListener = new ReturnListener() {
                @Override
                public void handleReturn(int replyCode, String replyText, String exchange, String routingKey,
                                         AMQP.BasicProperties properties, byte[] body) {
                    returnedRoute.set(exchange + "/" + routingKey + " (" + replyCode + " " + replyText + ")");
                }
            };
            channel.addReturnListener(returnListener);
            try {
                int replayed = 0;
                for (int i = 0; i < count; i++) {
                    GetResponse response = channel.basicGet(RabbitMQConfig.DEAD_LETTER_QUEUE, false);
                    if (response == null) {
                        break;
                    }
                    if (replayOne(channel, response, returnedRoute)) {
                        replayed++;
                    }
                }
                return replayed;
            } finally {
                channel.removeReturnListener(returnListener);
            }
        });
    }

    /**
     * 显式丢弃最早消息并审计，最多 count 条（1..100）
     *
     * @return 实际丢弃条数
     */
    public int discard(int count, String reason) {
        return rabbitTemplate.execute(channel -> {
            int discarded = 0;
            for (int i = 0; i < count; i++) {
                GetResponse response = channel.basicGet(RabbitMQConfig.DEAD_LETTER_QUEUE, false);
                if (response == null) {
                    break;
                }
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    channel.basicAck(deliveryTag, false);
                    writeAudit(response, "DISCARD", null, "OK", reason);
                    discarded++;
                } catch (Exception e) {
                    log.error("DLQ 丢弃失败: deliveryTag={}, error={}", deliveryTag, e.getMessage(), e);
                    channel.basicNack(deliveryTag, false, true);
                    writeAudit(response, "DISCARD", null, "FAILED",
                            safeTruncate("丢弃失败: " + e.getMessage(), 200));
                    throw e;
                }
            }
            return discarded;
        });
    }

    private boolean replayOne(Channel channel, GetResponse response, AtomicReference<String> returnedRoute) {
        long deliveryTag = response.getEnvelope().getDeliveryTag();
        String messageId = resolveMessageId(response);
        String[] originalRoute = resolveOriginalRoute(response);

        if (originalRoute == null) {
            log.error("DLQ 消息缺少原路由信息，拒绝猜测目标并保留消息: messageId={}", messageId);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackError) {
                log.error("无法将不可重放的 DLQ 消息退回队列: deliveryTag={}", deliveryTag, nackError);
            }
            writeAudit(response, "REPLAY", null, "UNREPLAYABLE", "缺少原交换机或路由键");
            return false;
        }

        try {
            returnedRoute.set(null);
            AMQP.BasicProperties properties = response.getProps();
            String traceId = TraceContext.getOrNull();
            if (traceId != null && properties.getHeaders() != null) {
                properties.getHeaders().put("traceId", traceId);
            }
            channel.basicPublish(originalRoute[0], originalRoute[1], true, properties, response.getBody());
            channel.waitForConfirmsOrDie(5000);
            if (returnedRoute.get() != null) {
                throw new IllegalStateException("消息未路由到目标队列: " + returnedRoute.get());
            }
            channel.basicAck(deliveryTag, false);
            writeAudit(response, "REPLAY", originalRoute, "OK", null);
            log.info("DLQ 消息重放成功: messageId={}, exchange={}, routingKey={}",
                    messageId, originalRoute[0], originalRoute[1]);
            return true;
        } catch (Exception e) {
            log.error("DLQ 消息重放失败，退回 DLQ: messageId={}, error={}", messageId, e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackError) {
                log.error("DLQ 消息 basicNack 失败（可能已丢失，需人工介入）: deliveryTag={}", deliveryTag, nackError);
            }
            writeAudit(response, "REPLAY", originalRoute, "FAILED",
                    safeTruncate("重放失败: " + e.getMessage(), 200));
            return false;
        }
    }

    /**
     * 从 x-first-death-exchange / x-first-death-routing-key 或 x-death 还原原路由
     */
    private String[] resolveOriginalRoute(GetResponse response) {
        Map<String, Object> headers = response.getProps().getHeaders();
        String exchange = null;
        String routingKey = null;

        if (headers != null) {
            exchange = headerString(headers.get(HEADER_FIRST_DEATH_EXCHANGE));
            routingKey = headerString(headers.get(HEADER_FIRST_DEATH_ROUTING_KEY));

            if (exchange == null || routingKey == null) {
                Object death = headers.get(HEADER_DEATH);
                if (death instanceof List<?> deaths && !deaths.isEmpty()) {
                    Object lastDeath = deaths.get(deaths.size() - 1);
                    if (lastDeath instanceof Map<?, ?> deathMap) {
                        if (exchange == null) {
                            exchange = headerString(deathMap.get("exchange"));
                        }
                        if (routingKey == null) {
                            Object keys = deathMap.get("routing-keys");
                            if (keys instanceof List<?> keyList && !keyList.isEmpty()) {
                                routingKey = headerString(keyList.get(0));
                            }
                        }
                    }
                }
            }
        }

        if (exchange == null || exchange.isBlank() || routingKey == null || routingKey.isBlank()) {
            return null;
        }
        return new String[]{exchange, routingKey};
    }

    private String resolveMessageId(GetResponse response) {
        String messageId = response.getProps().getMessageId();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        String correlationId = response.getProps().getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return String.valueOf(response.getEnvelope().getDeliveryTag());
    }

    private String headerString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private void writeAudit(GetResponse response, String action, String[] originalRoute,
                            String result, String reason) {
        try {
            String messageId = resolveMessageId(response);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("messageId", messageId);
            params.put("action", action);
            if (originalRoute != null) {
                params.put("originalExchange", originalRoute[0]);
                params.put("originalRoutingKey", originalRoute[1]);
            }
            params.put("result", result);
            if (reason != null) {
                params.put("reason", reason);
            }

            SysOperationLog audit = new SysOperationLog();
            audit.setUserId(SecurityUtils.getCurrentUserId());
            audit.setRealName(SecurityUtils.getCurrentUsername());
            audit.setOperationModule("DLQ");
            audit.setOperationType("UPDATE");
            audit.setOperationDesc("DLQ消息" + action + ": messageId=" + messageId
                    + ", result=" + result + (reason != null ? ", reason=" + reason : ""));
            audit.setRequestUrl("/api/system/dlq/" + action.toLowerCase());
            audit.setRequestParams(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params));
            audit.setOperationTime(LocalDateTime.now());
            sysOperationLogService.save(audit);
        } catch (Exception e) {
            log.warn("DLQ 审计日志写入失败: {}", e.getMessage());
        }
    }

    private String safeTruncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * DLQ 摘要
     */
    public record DlqSummary(long messageCount, LocalDateTime checkedAt, long alertThreshold, boolean alerting) {
        public DlqSummary(long messageCount, LocalDateTime checkedAt) {
            this(messageCount, checkedAt, 0, false);
        }

        public DlqSummary withThreshold(long threshold) {
            return new DlqSummary(messageCount, checkedAt, threshold, threshold > 0 && messageCount > threshold);
        }
    }
}
