package com.liushao.user.dao;

import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.liushao.user.pojo.AuthRefreshToken;

public interface AuthRefreshTokenDao extends JpaRepository<AuthRefreshToken, byte[]> {
    @Query("select token from AuthRefreshToken token where token.sessionId = :sessionId order by token.tokenHash")
    List<AuthRefreshToken> findBatchBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    boolean existsBySessionId(String sessionId);

    @Query("select token.sessionId from AuthRefreshToken token where token.tokenHash = :hash")
    Optional<String> findSessionId(@Param("hash") byte[] hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "5000"))
    @Query("select token from AuthRefreshToken token where token.tokenHash = :hash")
    Optional<AuthRefreshToken> lockByHash(@Param("hash") byte[] hash);
}