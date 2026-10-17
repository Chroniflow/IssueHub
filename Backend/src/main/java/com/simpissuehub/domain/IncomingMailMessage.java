package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incoming_mail_messages")
public class IncomingMailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "message_id", nullable = false, unique = true, length = 255) public String messageId;
    @Column(nullable = false, length = 254) public String sender;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "issue_id") public Issue issue;
    @Column(nullable = false, length = 24) public String status;
    @Column(name = "received_at", nullable = false) public Instant receivedAt;
    @Column(name = "processed_at") public Instant processedAt;
    @PrePersist void onCreate() { if (receivedAt == null) receivedAt = Instant.now(); }
}
