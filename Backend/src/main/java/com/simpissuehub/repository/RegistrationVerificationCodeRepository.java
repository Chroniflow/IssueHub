package com.simpissuehub.repository;

import com.simpissuehub.domain.RegistrationVerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RegistrationVerificationCodeRepository extends JpaRepository<RegistrationVerificationCode, Long> {
    Optional<RegistrationVerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegistrationVerificationCode> findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(String email, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update RegistrationVerificationCode code set code.consumedAt = :now where code.email = :email and code.consumedAt is null")
    int consumeUnused(@Param("email") String email, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RegistrationVerificationCode code where code.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
