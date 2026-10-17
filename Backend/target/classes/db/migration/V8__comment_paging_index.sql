DROP INDEX idx_comments_issue_deleted_created ON issue_comments;
CREATE INDEX idx_comments_issue_deleted_created_id
    ON issue_comments (issue_id, deleted, created_at, id);
