package com.simpissuehub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "issue_poll_options", uniqueConstraints = @UniqueConstraint(name = "uk_issue_poll_options_position", columnNames = {"poll_id", "position"}))
public class IssuePollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "poll_id", nullable = false) public IssuePoll poll;
    @Column(name = "option_text", nullable = false, length = 100) public String text;
    @Column(nullable = false) public short position;
}
