package com.simpissuehub.repository;

import com.simpissuehub.domain.IssueSubscription;
import com.simpissuehub.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IssueSubscriptionRepository extends JpaRepository<IssueSubscription, Long> {
    boolean existsByIssue_IdAndUser_Id(Long issueId, Long userId);
    long countByIssue_Id(Long issueId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO issue_subscriptions (issue_id, user_id) VALUES (:issueId, :userId)", nativeQuery = true)
    int subscribe(@Param("issueId") Long issueId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM IssueSubscription subscription WHERE subscription.issue.id = :issueId AND subscription.user.id = :userId")
    int unsubscribe(@Param("issueId") Long issueId, @Param("userId") Long userId);

    @Query("SELECT subscription.user FROM IssueSubscription subscription WHERE subscription.issue.id = :issueId AND subscription.user.enabled = true")
    List<User> findEnabledSubscribers(@Param("issueId") Long issueId);
}
