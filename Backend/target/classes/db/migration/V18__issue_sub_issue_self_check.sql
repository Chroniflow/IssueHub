-- MySQL 8.4 rejects CHECK constraints that reference an AUTO_INCREMENT column
-- (issues.id). The application validates parent_issue_id before persistence.
SELECT 1;
