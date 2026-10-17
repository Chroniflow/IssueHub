ALTER TABLE issues
    ADD COLUMN idempotency_key VARCHAR(128) NULL,
    ADD COLUMN idempotency_hash CHAR(64) NULL,
    ADD UNIQUE KEY uk_issues_author_idempotency (author_id, idempotency_key);
