package com.simpissuehub.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") public User actor;
    @Column(nullable = false, length = 64) public String action;
    @Column(name = "target_type", nullable = false, length = 32) public String targetType;
    @Column(name = "target_id", nullable = false, length = 64) public String targetId;
    @Column(length = 500) public String summary;
    @Column(name = "request_id", length = 64) public String requestId;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
