package com.liushao.user.pojo;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

@Entity
@Table(name = "tb_auth_refresh_token")
public class AuthRefreshToken {
    @Id
    @Column(name = "token_hash", length = 32, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;
    @Column(name = "session_id", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sessionId;
    @Column(name = "created_at", nullable = false)
    @Type(type = "com.liushao.user.pojo.UtcDateTimeType")
    private LocalDateTime createdAt;
    @Column(name = "consumed_at")
    @Type(type = "com.liushao.user.pojo.UtcDateTimeType")
    private LocalDateTime consumedAt;

    protected AuthRefreshToken() {
    }

    public AuthRefreshToken(byte[] tokenHash, String sessionId, LocalDateTime createdAt) {
        this.tokenHash = tokenHash.clone();
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    public boolean isConsumed() { return consumedAt != null; }

    public void consume(LocalDateTime now) {
        consumedAt = now;
    }
}