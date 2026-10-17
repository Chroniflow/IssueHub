package com.simpissuehub.repository;

import com.simpissuehub.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash and token.usedAt is null and token.expiresAt > :now")
    Optional<PasswordResetToken> findUsableByHashForUpdate(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken token set token.usedAt = :usedAt where token.user.id = :userId and token.usedAt is null")
    int consumeAllUnusedForUser(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);
}
