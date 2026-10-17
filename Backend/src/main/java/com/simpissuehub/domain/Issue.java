package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "issues")
public class Issue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 120) public String title;
    @Column(nullable = false, columnDefinition = "TEXT") public String body;
    @Column(nullable = false, length = 32) public String category = "其他";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) public State state = State.OPEN;
    @Column(nullable = false) public boolean pinned = false;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") public User author;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_issue_id") public Issue parent;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "issue_tags", joinColumns = @JoinColumn(name = "issue_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    public Set<Tag> tags = new LinkedHashSet<>();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "closed_by") public User closedBy;
    @Column(name = "closed_at") public Instant closedAt;
    @Enumerated(EnumType.STRING) @Column(name = "close_reason", length = 24) public CloseReason closeReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "duplicate_of_issue_id") public Issue duplicateOf;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Version public Long version;
    @Column(nullable = false) public boolean deleted = false;
    @Column(name = "idempotency_key", length = 128) public String idempotencyKey;
    @Column(name = "idempotency_hash", length = 64, columnDefinition = "CHAR(64)") public String idempotencyHash;
    @PrePersist void onCreate() { validateParent(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { validateParent(); updatedAt = Instant.now(); }
    private void validateParent() {
        if (parent != null && id != null && id.equals(parent.id)) throw new IllegalStateException("工单不能作为自身的父议题");
    }
    public enum State { OPEN, CLOSED }
    public enum CloseReason { COMPLETED, NOT_PLANNED, MEANINGLESS, DUPLICATE }
}
