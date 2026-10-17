package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registration_verification_codes")
public class RegistrationVerificationCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 254) public String email;
    @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)") public String codeHash;
    @Column(nullable = false, length = 32, columnDefinition = "CHAR(32)") public String salt;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "consumed_at") public Instant consumedAt;
    @Column(nullable = false) public int attempts;
    @Column(name = "created_at", nullable = false) public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
