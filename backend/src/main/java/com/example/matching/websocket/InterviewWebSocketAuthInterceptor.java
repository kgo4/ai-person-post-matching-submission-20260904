package com.example.matching.websocket;

import com.example.matching.service.interview.InterviewWebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Validates one-time WebSocket tickets before the HTTP upgrade reaches the handler.
 */
@Component
@RequiredArgsConstructor
public class InterviewWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final InterviewWebSocketTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long sessionId = extractSessionId(request);
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");

        Long userId = ticketService.consume(ticket, sessionId);
        if (userId == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put("sessionId", sessionId.toString());
        // 把鉴权阶段拿到的 userId 透传给后续 WebSocket 处理线程，供 SecurityUtils 注入审计身份
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private Long extractSessionId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String[] parts = path.split("/");
        if (parts.length == 0) {
            return null;
        }
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
