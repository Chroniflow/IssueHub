package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 32) public String username;
    @Column(nullable = false, unique = true, length = 254) public String email;
    @Column(name = "avatar_url", length = 512) public String avatarUrl;
    @Column(name = "password_hash", nullable = false, length = 100) public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) public Role role = Role.USER;
    @Column(nullable = false) public boolean enabled = true;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public enum Role { USER, STAFF, ADMIN }
}
