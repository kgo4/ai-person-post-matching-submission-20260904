package com.example.matching.service.common;

import java.util.Map;

/**
 * 通用事件 Outbox 调度器接口（M-09）。
 * <p>
 * payload 存储 canonical JSON，dispatch 时用原始字节构造 Message（不经过 Jackson2JsonMessageConverter 二次序列化）。
 * PUBLISHED 仅表示 broker 已接收且未被 returned；returned/nack/超时回退到 PENDING 并按退避重试。
 */
public interface EventOutboxDispatcher {

    /**
     * 将事件写入 outbox（在业务事务内调用）。payload 存储 canonical JSON 字符串。
     */
    void enqueue(String eventType, String exchange, String routingKey, Object payload);

    /**
     * 标记 correlationId 对应消息被 broker 退回，回退为 PENDING 并重试。
     */
    void markReturned(String correlationId);

    /**
     * 定时扫描并派发 PENDING 消息（调度器入口）。
     */
    void dispatchPendingEvents();

    /**
     * 各状态消息数量统计。
     */
    Map<String, Long> statusSummary();

    /**
     * 手动重放指定 outbox 消息。
     *
     * @return true 表示已派发（重放成功或进入重试）
     */
    boolean replay(Long outboxId);
}
