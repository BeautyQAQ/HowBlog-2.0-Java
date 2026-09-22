package com.liushao.user.im;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.auth.AuthenticatedUser;
import com.liushao.auth.SessionVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatWebSocketHandlerTest {
    private final SessionVerifier verifier = mock(SessionVerifier.class);
    private final ChatWebSocketHandler handler = new ChatWebSocketHandler(new ObjectMapper(), verifier);

    @Test
    void revokedSenderCannotBroadcast() throws Exception {
        WebSocketSession sender = session("sender", "a".repeat(64));
        WebSocketSession recipient = session("recipient", "b".repeat(64));
        handler.afterConnectionEstablished(sender);
        handler.afterConnectionEstablished(recipient);
        clearInvocations(sender, recipient);
        when(verifier.isActive("a".repeat(64), "sender")).thenReturn(false);
        handler.handleTextMessage(sender, new TextMessage("{\"content\":\"blocked\"}"));
        verify(sender).close(CloseStatus.POLICY_VIOLATION);
        verify(recipient, never()).sendMessage(any());
    }

    @Test
    void revokedRecipientCannotReceiveMessages() throws Exception {
        WebSocketSession sender = session("sender", "a".repeat(64));
        WebSocketSession recipient = session("recipient", "b".repeat(64));
        handler.afterConnectionEstablished(sender);
        handler.afterConnectionEstablished(recipient);
        clearInvocations(sender, recipient);
        when(verifier.isActive("b".repeat(64), "recipient")).thenReturn(false);
        handler.handleTextMessage(sender, new TextMessage("{\"to\":\"recipient\",\"content\":\"message\"}"));
        verify(recipient).close(CloseStatus.POLICY_VIOLATION);
        verify(recipient, never()).sendMessage(any());
        verify(sender).sendMessage(any(TextMessage.class));
    }

    @Test
    void idleCleanupClosesRevokedAndExpiredConnections() throws Exception {
        WebSocketSession revoked = session("revoked", "a".repeat(64));
        WebSocketSession expired = session("expired", "b".repeat(64));
        handler.afterConnectionEstablished(revoked);
        handler.afterConnectionEstablished(expired);
        when(verifier.isActive("a".repeat(64), "revoked")).thenReturn(false);
        expired.getAttributes().put("identity", new AuthenticatedUser("expired", "test", "b".repeat(64), Instant.EPOCH));
        handler.closeInactiveSessions();
        verify(revoked).close(CloseStatus.POLICY_VIOLATION);
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void storageFailureClosesConnectionWithoutReply() throws Exception {
        WebSocketSession session = session("sender", "a".repeat(64));
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(verifier.isActive("a".repeat(64), "sender"))
                .thenThrow(new DataAccessResourceFailureException("internal connection"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        verify(session).close(CloseStatus.SERVER_ERROR);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void replacementClosesPreviousConnection() throws Exception {
        WebSocketSession previous = session("sender", "a".repeat(64));
        WebSocketSession replacement = session("sender", "b".repeat(64));
        handler.afterConnectionEstablished(previous);
        handler.afterConnectionEstablished(replacement);
        verify(previous).close(CloseStatus.NORMAL);
    }

    private WebSocketSession session(String user, String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("user", user);
        attributes.put("identity", new AuthenticatedUser(user, "test", id, Instant.now().plusSeconds(120)));
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        when(verifier.isActive(id, user)).thenReturn(true);
        return session;
    }
}