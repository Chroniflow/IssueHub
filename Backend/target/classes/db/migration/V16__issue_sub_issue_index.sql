CREATE INDEX idx_issues_parent_created
    ON issues (parent_issue_id, created_at, id);
