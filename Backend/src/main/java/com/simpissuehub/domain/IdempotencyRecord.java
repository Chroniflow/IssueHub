package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_scope", columnNames = {"user_id", "operation", "idempotency_key"}))
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Column(nullable = false, length = 64) public String operation;
    @Column(name = "idempotency_key", nullable = false, length = 128) public String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)") public String requestHash;
    @Column(name = "resource_type", nullable = false, length = 32) public String resourceType;
    @Column(name = "resource_id", nullable = false, length = 128) public String resourceId;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
