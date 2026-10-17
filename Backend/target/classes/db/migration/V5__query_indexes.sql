-- Cover the frequent filtered list and reverse-association queries.
CREATE INDEX idx_issues_deleted_updated ON issues (deleted, updated_at, id);
CREATE INDEX idx_issues_author_deleted_updated ON issues (author_id, deleted, updated_at, id);
CREATE INDEX idx_issues_state_deleted_updated ON issues (state, deleted, updated_at, id);
CREATE INDEX idx_issue_tags_tag_issue ON issue_tags (tag_id, issue_id);
CREATE INDEX idx_comments_issue_deleted_created ON issue_comments (issue_id, deleted, created_at);
CREATE INDEX idx_issue_links_target ON issue_links (target_issue_id, source_issue_id);
CREATE INDEX idx_api_tokens_user_created ON api_tokens (user_id, created_at);
