package com.liushao.user.im;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.liushao.auth.JwtTokenService;
import com.liushao.auth.SessionVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:ws-origin;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "how.auth.jwt.secret=test-only-signing-key-not-for-production",
        "how.auth.rate-limit.enabled=false", "how.auth.cleanup.enabled=false",
        "how.cors.allowed-origins=https://allowed.test",
        "logging.level.root=WARN"
})
class WebSocketOriginTest {
    @LocalServerPort private int port;
    @Autowired private JwtTokenService tokens;
    @MockBean private SessionVerifier sessions;

    @Test
    void acceptsConfiguredOriginAndRejectsOthers() throws Exception {
        when(sessions.isActive(anyString(), anyString())).thenReturn(true);
        String token = tokens.issueToken("10001", "13800000000", "a".repeat(64), Instant.now().plusSeconds(600));

        assertTrue(handshakeSucceeds("https://allowed.test", token));
        assertFalse(handshakeSucceeds("https://denied.test", token));
    }

    private boolean handshakeSucceeds(String origin, String token) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        StandardWebSocketClient client = new StandardWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/im?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
        try (WebSocketSession session = client.doHandshake(new TextWebSocketHandler(), headers, uri)
                .get(10, TimeUnit.SECONDS)) {
            return session.isOpen();
        } catch (ExecutionException failure) {
            return false;
        }
    }
}
