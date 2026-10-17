package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "issue_poll_ballots", uniqueConstraints = @UniqueConstraint(name = "uk_issue_poll_ballots_poll_user", columnNames = {"poll_id", "user_id"}))
public class IssuePollBallot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "poll_id", nullable = false) public IssuePoll poll;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) public User user;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
