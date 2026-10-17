ALTER TABLE issues
    ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER state,
    ADD KEY idx_issues_pinned_updated (pinned, updated_at, id);
