package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "issue_polls", uniqueConstraints = @UniqueConstraint(name = "uk_issue_polls_issue", columnNames = "issue_id"))
public class IssuePoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "issue_id") public Issue issue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "creator_id", nullable = false) public User creator;
    @Column(nullable = false, length = 200) public String question;
    @Column(name = "multiple_choice", nullable = false) public boolean multipleChoice;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<IssuePollOption> options = new ArrayList<>();

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
