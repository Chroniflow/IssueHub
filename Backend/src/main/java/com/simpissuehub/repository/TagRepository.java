package com.simpissuehub.repository;

import com.simpissuehub.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tag from Tag tag where tag.id = :id")
    Optional<Tag> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @Query(value = "SELECT COUNT(*) FROM issue_tags WHERE tag_id = :tagId", nativeQuery = true)
    long countIssuesByTagId(@org.springframework.data.repository.query.Param("tagId") Long tagId);
    @Query(value = "SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(i.id) AS issueCount "
        + "FROM tags t LEFT JOIN issue_tags it ON it.tag_id = t.id "
            + "LEFT JOIN issues i ON i.id = it.issue_id AND i.deleted = false "
            + "GROUP BY t.id, t.name, t.color ORDER BY t.name", nativeQuery = true)
    List<TagSummary> findSummaries();

    interface TagSummary {
        Long getId();
        String getName();
        String getColor();
        Long getIssueCount();
    }
}
