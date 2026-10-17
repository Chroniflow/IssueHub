package com.simpissuehub.repository;

import com.simpissuehub.domain.IssueLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface IssueLinkRepository extends JpaRepository<IssueLink, IssueLink.Id> {
    @EntityGraph(attributePaths = {"source", "target"})
    @Query("select l from IssueLink l where l.source.id = :issueId or l.target.id = :issueId")
    List<IssueLink> findForIssue(@Param("issueId") Long issueId);
    @Query("select count(l) > 0 from IssueLink l where ((l.source.id = :a and l.target.id = :b) or (l.source.id = :b and l.target.id = :a))")
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);
}
