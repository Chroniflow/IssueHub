CREATE TABLE issue_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(64) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_attachments_storage_key (storage_key),
    KEY idx_issue_attachments_issue_created (issue_id, created_at, id),
    CONSTRAINT fk_issue_attachments_issue FOREIGN KEY (issue_id) REFERENCES issues (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_issue_attachments_size CHECK (size_bytes > 0)
);
