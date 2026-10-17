package com.simpissuehub.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "issue_attachments")
public class IssueAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issue_id") public Issue issue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploader_id") public User uploader;
    @Column(name = "storage_key", nullable = false, unique = true, length = 64) public String storageKey;
    @Column(name = "original_name", nullable = false, length = 255) public String originalName;
    @Column(name = "content_type", nullable = false, length = 100) public String contentType;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(name = "created_at", nullable = false) public Instant createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
