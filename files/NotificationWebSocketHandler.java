package com.notifications.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifications.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler that maintains per-user sessions and delivers
 * real-time notification payloads.
 *
 * Connection URL: ws://host/ws/notifications?userId={userId}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    // userId -> set of open sessions (a user can be on multiple devices)
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket connected: userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        log.info("WebSocket disconnected: sessionId={} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Clients can send a ping; we respond with pong
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException e) {
                log.warn("Failed to send pong: {}", e.getMessage());
            }
        }
    }

    /**
     * Deliver a notification to all active sessions for a user.
     */
    public void sendToUser(String userId, NotificationDto.NotificationResponse notification) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active WebSocket sessions for userId={}", userId);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("event", "NOTIFICATION", "data", notification));
            TextMessage message = new TextMessage(payload);

            sessions.removeIf(session -> {
                if (!session.isOpen()) return true;
                try {
                    session.sendMessage(message);
                    return false;
                } catch (IOException e) {
                    log.warn("Failed to send to sessionId={}: {}", session.getId(), e.getMessage());
                    return true;
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize notification for userId={}", userId, e);
        }
    }

    /**
     * Broadcast unread-count update to a user's sessions.
     */
    public void sendUnreadCount(String userId, long unreadCount) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("event", "UNREAD_COUNT", "data", Map.of("unreadCount", unreadCount)));
            TextMessage message = new TextMessage(payload);
            sessions.stream().filter(WebSocketSession::isOpen)
                    .forEach(s -> {
                        try { s.sendMessage(message); }
                        catch (IOException e) { log.warn("Send failed: {}", e.getMessage()); }
                    });
        } catch (Exception e) {
            log.error("Failed to send unread count for userId={}", userId, e);
        }
    }

    public int getConnectedUserCount() {
        return userSessions.size();
    }

    private String extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "userId".equals(kv[0])) return kv[1];
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try { session.close(CloseStatus.POLICY_VIOLATION); }
        catch (IOException ignored) {}
    }
}
