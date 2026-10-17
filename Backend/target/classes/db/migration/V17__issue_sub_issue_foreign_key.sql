ALTER TABLE issues
    ADD CONSTRAINT fk_issues_parent
        FOREIGN KEY (parent_issue_id) REFERENCES issues (id);
