ALTER TABLE issues
    ADD CONSTRAINT fk_issues_duplicate_of
        FOREIGN KEY (duplicate_of_issue_id) REFERENCES issues (id);
