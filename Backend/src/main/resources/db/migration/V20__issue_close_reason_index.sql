CREATE INDEX idx_issues_duplicate_of
    ON issues (duplicate_of_issue_id);
