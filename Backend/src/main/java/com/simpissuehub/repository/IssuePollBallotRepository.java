package com.simpissuehub.repository;

import com.simpissuehub.domain.IssuePollBallot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IssuePollBallotRepository extends JpaRepository<IssuePollBallot, Long> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO issue_poll_ballots (poll_id, user_id) VALUES (:pollId, :userId)", nativeQuery = true)
    int ensureBallot(@Param("pollId") Long pollId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ballot FROM IssuePollBallot ballot WHERE ballot.poll.id = :pollId AND ballot.user.id = :userId")
    Optional<IssuePollBallot> findLocked(@Param("pollId") Long pollId, @Param("userId") Long userId);

    Optional<IssuePollBallot> findByPoll_IdAndUser_Id(Long pollId, Long userId);

    long countByPoll_Id(Long pollId);

    @Query("SELECT ballot.poll.issue.id AS issueId, COUNT(ballot) AS participantCount FROM IssuePollBallot ballot WHERE ballot.poll.issue.id IN :issueIds GROUP BY ballot.poll.issue.id")
    List<IssueParticipantCount> countParticipantsByIssueIds(@Param("issueIds") Collection<Long> issueIds);

    interface IssueParticipantCount {
        Long getIssueId();
        long getParticipantCount();
    }
}
