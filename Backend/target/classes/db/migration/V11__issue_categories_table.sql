CREATE TABLE issue_categories (id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(32) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_issue_categories_name(name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO issue_categories(name) SELECT DISTINCT category FROM issues WHERE category IS NOT NULL;
