package com.simpissuehub.repository;
import com.simpissuehub.domain.Issue;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.util.List;
public interface IssueRepository extends JpaRepository<Issue, Long> {
    @Modifying
    @Query("update Issue i set i.category = :newName where i.category = :oldName")
    int renameCategory(@Param("oldName") String oldName, @Param("newName") String newName);
    @EntityGraph(attributePaths = {"author", "tags"})
    @Query("SELECT DISTINCT i FROM Issue i LEFT JOIN i.tags t WHERE i.deleted = false AND (:state IS NULL OR i.state = :state) AND (:author IS NULL OR i.author.username = :author) AND (:category IS NULL OR i.category = :category) AND (:tag IS NULL OR t.name = :tag) AND (:query IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.body) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Issue> searchFiltered(@Param("query") String query, @Param("tag") String tag, @Param("category") String category, @Param("state") Issue.State state, @Param("author") String author, Pageable pageable);
    @Override
    @EntityGraph(attributePaths = {"author", "tags", "parent", "duplicateOf"})
    Optional<Issue> findById(Long id);
    @EntityGraph(attributePaths = {"author", "tags"})
    List<Issue> findByParent_IdAndDeletedFalseOrderByCreatedAtAscIdAsc(Long parentId);
    @EntityGraph(attributePaths = {"author", "tags"})
    Page<Issue> findByDeletedFalse(Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    List<Issue> findTop50ByDeletedFalseOrderByUpdatedAtDescIdDesc();
    long countByDeletedFalse();
    long countByStateAndDeletedFalse(Issue.State state);
    @Query("SELECT COUNT(DISTINCT i) FROM Issue i LEFT JOIN i.tags t WHERE i.deleted = false AND (:state IS NULL OR i.state = :state) AND (:author IS NULL OR i.author.username = :author) AND (:category IS NULL OR i.category = :category) AND (:tag IS NULL OR t.name = :tag) AND (:query IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.body) LIKE LOWER(CONCAT('%', :query, '%')))")
    long countFiltered(@Param("query") String query, @Param("tag") String tag, @Param("category") String category, @Param("state") Issue.State state, @Param("author") String author);
    Optional<Issue> findByAuthor_IdAndIdempotencyKey(Long authorId, String idempotencyKey);
    @EntityGraph(attributePaths = {"author", "tags"})
    Page<Issue> findByStateAndDeletedFalse(Issue.State state, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    Page<Issue> findDistinctByTags_NameAndDeletedFalse(String tag, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    Page<Issue> findDistinctByTags_NameAndStateAndDeletedFalse(String tag, Issue.State state, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"}) Page<Issue> findByAuthor_UsernameAndDeletedFalse(String author, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"}) Page<Issue> findByAuthor_UsernameAndStateAndDeletedFalse(String author, Issue.State state, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"}) Page<Issue> findDistinctByTags_NameAndAuthor_UsernameAndDeletedFalse(String tag, String author, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"}) Page<Issue> findDistinctByTags_NameAndStateAndAuthor_UsernameAndDeletedFalse(String tag, Issue.State state, String author, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT i.* FROM issues i WHERE i.deleted = false AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(*) FROM issues i WHERE i.deleted = false AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> search(@Param("query") String query, @Param("sort") String sort, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT i.* FROM issues i WHERE i.deleted = false AND i.state = :state AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(*) FROM issues i WHERE i.deleted = false AND i.state = :state AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByState(@Param("query") String query, @Param("state") String state, @Param("sort") String sort, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT DISTINCT i.* FROM issues i JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(DISTINCT i.id) FROM issues i JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByTag(@Param("query") String query, @Param("tag") String tag, @Param("sort") String sort, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT DISTINCT i.* FROM issues i JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND i.state = :state AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(DISTINCT i.id) FROM issues i JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND i.state = :state AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByTagAndState(@Param("query") String query, @Param("tag") String tag, @Param("state") String state, @Param("sort") String sort, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT i.* FROM issues i JOIN users u ON u.id = i.author_id WHERE i.deleted = false AND u.username = :author AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(*) FROM issues i JOIN users u ON u.id = i.author_id WHERE i.deleted = false AND u.username = :author AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByAuthor(@Param("query") String query, @Param("author") String author, @Param("sort") String sort, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT i.* FROM issues i JOIN users u ON u.id = i.author_id WHERE i.deleted = false AND i.state = :state AND u.username = :author AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(*) FROM issues i JOIN users u ON u.id = i.author_id WHERE i.deleted = false AND i.state = :state AND u.username = :author AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByAuthorAndState(@Param("query") String query, @Param("author") String author, @Param("state") String state, @Param("sort") String sort, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT DISTINCT i.* FROM issues i JOIN users u ON u.id = i.author_id JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND u.username = :author AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(DISTINCT i.id) FROM issues i JOIN users u ON u.id = i.author_id JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND u.username = :author AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByAuthorAndTag(@Param("query") String query, @Param("author") String author, @Param("tag") String tag, @Param("sort") String sort, Pageable pageable);
    @EntityGraph(attributePaths = {"author", "tags"})
    @Query(value = "SELECT DISTINCT i.* FROM issues i JOIN users u ON u.id = i.author_id JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND i.state = :state AND u.username = :author AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE) ORDER BY CASE WHEN :sort = 'updatedAsc' THEN i.updated_at END ASC, CASE WHEN :sort = 'updatedDesc' THEN i.updated_at END DESC, CASE WHEN :sort = 'createdAsc' THEN i.created_at END ASC, CASE WHEN :sort = 'createdDesc' THEN i.created_at END DESC, i.id DESC", countQuery = "SELECT COUNT(DISTINCT i.id) FROM issues i JOIN users u ON u.id = i.author_id JOIN issue_tags it ON it.issue_id = i.id JOIN tags t ON t.id = it.tag_id WHERE i.deleted = false AND i.state = :state AND u.username = :author AND t.name = :tag AND MATCH(i.title, i.body) AGAINST (:query IN BOOLEAN MODE)", nativeQuery = true)
    Page<Issue> searchByAuthorTagAndState(@Param("query") String query, @Param("author") String author, @Param("tag") String tag, @Param("state") String state, @Param("sort") String sort, Pageable pageable);
}
