package com.simpissuehub.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "api_tokens")
public class ApiToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Column(nullable = false, length = 64) public String name;
    @Column(nullable = false, length = 64) public String scopes;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)") public String tokenHash;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "last_used_at") public Instant lastUsedAt;
    @Column(name = "revoked_at") public Instant revokedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
