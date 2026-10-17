CREATE TABLE incoming_mail_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id VARCHAR(255) NOT NULL,
    sender VARCHAR(254) NOT NULL,
    issue_id BIGINT NULL,
    status VARCHAR(24) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_incoming_mail_message_id (message_id),
    KEY idx_incoming_mail_received (received_at),
    CONSTRAINT fk_incoming_mail_issue FOREIGN KEY (issue_id) REFERENCES issues (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
