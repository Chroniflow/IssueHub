package com.simpissuehub.repository;

import com.simpissuehub.domain.IssuePoll;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface IssuePollRepository extends JpaRepository<IssuePoll, Long> {
    @EntityGraph(attributePaths = "options")
    Optional<IssuePoll> findByIssue_Id(Long issueId);
    @EntityGraph(attributePaths = {"options", "creator"})
    List<IssuePoll> findByIssueIsNullOrderByCreatedAtDescIdDesc();
    @EntityGraph(attributePaths = {"options", "creator"})
    Optional<IssuePoll> findWithOptionsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "options")
    Optional<IssuePoll> findLockedByIssue_Id(Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "options")
    Optional<IssuePoll> findLockedWithOptionsById(Long id);
}
