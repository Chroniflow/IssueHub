package com.simpissuehub.repository;
import com.simpissuehub.domain.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {
    long countByDeletedFalse();
    @EntityGraph(attributePaths = {"author"})
    Page<IssueComment> findByIssueIdAndDeletedFalse(Long issueId, Pageable pageable);
    @EntityGraph(attributePaths = {"author"})
    List<IssueComment> findByIssueIdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long issueId);
}
