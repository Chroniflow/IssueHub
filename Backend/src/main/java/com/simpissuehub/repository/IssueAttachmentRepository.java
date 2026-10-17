package com.simpissuehub.repository;

import com.simpissuehub.domain.IssueAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {
    @EntityGraph(attributePaths = {"issue", "issue.author", "uploader"})
    List<IssueAttachment> findByIssue_IdOrderByCreatedAtAscIdAsc(Long issueId);

    @Override
    @EntityGraph(attributePaths = {"issue", "issue.author", "uploader"})
    Optional<IssueAttachment> findById(Long id);
}
