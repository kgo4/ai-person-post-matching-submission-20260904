package com.example.matching.websocket;

import com.example.matching.config.SecurityProperties;
import com.example.matching.config.WebSocketOriginPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置
 * <p>
 * 注册面试控制通道的WebSocket处理器
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler interviewWebSocketHandler;
    private final InterviewWebSocketAuthInterceptor interviewWebSocketAuthInterceptor;
    private final SecurityProperties securityProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 面试控制通道：前端连接此端点进行实时面试
        registry.addHandler(interviewWebSocketHandler, "/ws/interview/{sessionId}")
                .addInterceptors(interviewWebSocketAuthInterceptor)
                .setAllowedOriginPatterns(
                        WebSocketOriginPatterns.fromCommaSeparated(securityProperties.getAllowedOrigins()));
    }
}
