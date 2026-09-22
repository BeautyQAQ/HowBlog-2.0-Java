package com.liushao.user.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String username = currentUser(session);
        if (username == null || username.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessions.put(username, session);
        send(session, Map.of("type", "ready", "user", username));
        for (String onlineUser : sessions.keySet()) {
            if (!onlineUser.equals(username)) {
                send(session, Map.of("type", "presence", "event", "online", "user", onlineUser));
            }
        }
        broadcast(Map.of("type", "presence", "event", "online", "user", username), username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode request = objectMapper.readTree(message.getPayload());
        String type = request.path("type").asText("message");
        String username = currentUser(session);

        if ("join".equals(type)) {
            String room = request.path("room").asText("").trim();
            session.getAttributes().put("room", room);
            send(session, Map.of("type", "joined", "room", room));
            return;
        }
        if ("ping".equals(type)) {
            send(session, Map.of("type", "pong"));
            return;
        }
        if (!"message".equals(type)) {
            send(session, Map.of("type", "error", "message", "不支持的消息类型"));
            return;
        }

        String content = request.path("content").asText("").trim();
        if (content.isEmpty()) {
            send(session, Map.of("type", "error", "message", "消息内容不能为空"));
            return;
        }

        String room = request.path("room").asText("").trim();
        String target = request.path("to").asText("").trim();
        Map<String, Object> response = Map.of(
                "type", "message",
                "from", username,
                "to", target,
                "room", room,
                "content", content,
                "timestamp", Instant.now().toString()
        );
        if (!target.isEmpty()) {
            sendToUser(response, target);
            if (!target.equals(username)) {
                send(session, response);
            }
        } else if (!room.isEmpty()) {
            broadcastToRoom(response, room);
        } else {
            broadcast(response, null);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = currentUser(session);
        if (username != null && sessions.remove(username, session)) {
            try {
                broadcast(Map.of("type", "presence", "event", "offline", "user", username), username);
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastToRoom(Map<String, Object> message, String room) throws IOException {
        for (WebSocketSession session : sessions.values()) {
            if (room.equals(session.getAttributes().get("room"))) {
                send(session, message);
            }
        }
    }

    private void sendToUser(Map<String, Object> message, String username) throws IOException {
        WebSocketSession session = sessions.get(username);
        if (session == null || !session.isOpen()) {
            return;
        }
        send(session, message);
    }

    private void broadcast(Map<String, Object> message, String excludedUser) throws IOException {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(excludedUser)) {
                send(entry.getValue(), message);
            }
        }
    }

    private void send(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    private String currentUser(WebSocketSession session) {
        Object user = session.getAttributes().get("user");
        return user == null ? null : user.toString();
    }

}