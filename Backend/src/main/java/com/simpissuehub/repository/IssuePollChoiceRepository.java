package com.simpissuehub.repository;

import com.simpissuehub.domain.IssuePollChoice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface IssuePollChoiceRepository extends JpaRepository<IssuePollChoice, IssuePollChoice.Id> {
    @Modifying
    @Query("DELETE FROM IssuePollChoice choice WHERE choice.ballot.id = :ballotId")
    int deleteByBallotId(@Param("ballotId") Long ballotId);

    @Modifying
    @Query(value = "INSERT INTO issue_poll_ballot_choices (ballot_id, option_id) VALUES (:ballotId, :optionId)", nativeQuery = true)
    int addChoice(@Param("ballotId") Long ballotId, @Param("optionId") Long optionId);

    @Query("SELECT choice.option.id FROM IssuePollChoice choice WHERE choice.ballot.id = :ballotId ORDER BY choice.option.position")
    List<Long> findOptionIdsByBallotId(@Param("ballotId") Long ballotId);

    @Query("SELECT choice.option.id AS optionId, COUNT(choice) AS voteCount FROM IssuePollChoice choice WHERE choice.option.poll.id = :pollId GROUP BY choice.option.id")
    List<OptionVoteCount> countByPollId(@Param("pollId") Long pollId);

    interface OptionVoteCount {
        Long getOptionId();
        long getVoteCount();
    }
}
