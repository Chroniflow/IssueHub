package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "issue_links")
public class IssueLink {
    @EmbeddedId public Id id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("sourceIssueId") @JoinColumn(name = "source_issue_id") public Issue source;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("targetIssueId") @JoinColumn(name = "target_issue_id") public Issue target;
    @Column(name = "created_at", nullable = false) public Instant createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "source_issue_id") public Long sourceIssueId;
        @Column(name = "target_issue_id") public Long targetIssueId;
        @Column(name = "relation_type", length = 24) public String relationType;
        public Id() {}
        public Id(Long sourceIssueId, Long targetIssueId, String relationType) { this.sourceIssueId = sourceIssueId; this.targetIssueId = targetIssueId; this.relationType = relationType; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof Id other)) return false; return Objects.equals(sourceIssueId, other.sourceIssueId) && Objects.equals(targetIssueId, other.targetIssueId) && Objects.equals(relationType, other.relationType); }
        @Override public int hashCode() { return Objects.hash(sourceIssueId, targetIssueId, relationType); }
    }
}
