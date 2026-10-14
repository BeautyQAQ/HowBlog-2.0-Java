package com.liushao.user.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.liushao.user.pojo.AuthSession;

public interface AuthSessionDao extends JpaRepository<AuthSession, String> {
    @Query("select session.id from AuthSession session where session.expiresAt <= :cutoff order by session.expiresAt, session.id")
    List<String> findExpiredIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "5000"))
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> lockById(@Param("id") String id);

    @Modifying(flushAutomatically = true)
    @Query("update AuthSession session set session.revokedAt = :now "
            + "where session.userId = :userId and session.revokedAt is null and session.id <> :keepId")
    int revokeActiveExcept(@Param("userId") String userId, @Param("keepId") String keepId,
            @Param("now") LocalDateTime now);
}