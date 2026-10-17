package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "issue_subscriptions", uniqueConstraints = @UniqueConstraint(name = "uk_issue_subscriptions_issue_user", columnNames = {"issue_id", "user_id"}))
public class IssueSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issue_id") public Issue issue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") public User user;
    @Column(name = "created_at", nullable = false) public Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
