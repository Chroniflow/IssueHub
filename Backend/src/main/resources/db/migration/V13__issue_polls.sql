CREATE TABLE issue_polls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    question VARCHAR(200) NOT NULL,
    multiple_choice TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_polls_issue (issue_id),
    CONSTRAINT fk_issue_polls_issue FOREIGN KEY (issue_id) REFERENCES issues (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE issue_poll_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poll_id BIGINT NOT NULL,
    option_text VARCHAR(100) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_poll_options_position (poll_id, position),
    CONSTRAINT fk_issue_poll_options_poll FOREIGN KEY (poll_id) REFERENCES issue_polls (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE issue_poll_ballots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    poll_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_poll_ballots_poll_user (poll_id, user_id),
    KEY idx_issue_poll_ballots_user (user_id),
    CONSTRAINT fk_issue_poll_ballots_poll FOREIGN KEY (poll_id) REFERENCES issue_polls (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_poll_ballots_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE issue_poll_ballot_choices (
    ballot_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    PRIMARY KEY (ballot_id, option_id),
    KEY idx_issue_poll_choices_option (option_id),
    CONSTRAINT fk_issue_poll_choices_ballot FOREIGN KEY (ballot_id) REFERENCES issue_poll_ballots (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_poll_choices_option FOREIGN KEY (option_id) REFERENCES issue_poll_options (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
