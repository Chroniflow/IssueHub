ALTER TABLE issues
    ADD COLUMN close_reason VARCHAR(24) NULL,
    ADD COLUMN duplicate_of_issue_id BIGINT NULL;
