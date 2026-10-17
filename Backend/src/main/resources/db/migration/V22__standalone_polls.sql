ALTER TABLE issue_polls
    ADD COLUMN creator_id BIGINT NULL AFTER issue_id,
    MODIFY COLUMN issue_id BIGINT NULL;

UPDATE issue_polls p
JOIN issues i ON i.id = p.issue_id
SET p.creator_id = i.author_id
WHERE p.creator_id IS NULL;

ALTER TABLE issue_polls
    MODIFY COLUMN creator_id BIGINT NOT NULL,
    ADD KEY idx_issue_polls_creator_created (creator_id, created_at),
    ADD CONSTRAINT fk_issue_polls_creator FOREIGN KEY (creator_id) REFERENCES users (id) ON DELETE CASCADE;
