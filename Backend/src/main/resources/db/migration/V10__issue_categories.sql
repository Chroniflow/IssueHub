ALTER TABLE issues ADD COLUMN category VARCHAR(32) NULL;
UPDATE issues SET category = '其他' WHERE category IS NULL;
ALTER TABLE issues MODIFY COLUMN category VARCHAR(32) NOT NULL;
CREATE INDEX idx_issues_category_updated ON issues (category, updated_at);
