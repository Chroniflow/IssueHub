package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)") public String tokenHash;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
