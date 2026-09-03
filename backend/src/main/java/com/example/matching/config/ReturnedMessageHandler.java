package com.example.matching.config;

/**
 * MQ 不可路由消息处理器接口。
 * <p>
 * 由 RabbitMQConfig 的 ReturnsCallback 调用，解耦配置层与业务 Service。
 */
public interface ReturnedMessageHandler {
    void onMessageReturned(String correlationId);
}
