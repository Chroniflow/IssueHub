package com.simpissuehub.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "issue_poll_ballot_choices")
public class IssuePollChoice {
    @EmbeddedId public Id id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("ballotId") @JoinColumn(name = "ballot_id") public IssuePollBallot ballot;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("optionId") @JoinColumn(name = "option_id") public IssuePollOption option;

    @Embeddable
    public static class Id implements Serializable {
        public Long ballotId;
        public Long optionId;
        public Id() {}
        public Id(Long ballotId, Long optionId) { this.ballotId = ballotId; this.optionId = optionId; }
        @Override public boolean equals(Object other) { return other instanceof Id value && Objects.equals(ballotId, value.ballotId) && Objects.equals(optionId, value.optionId); }
        @Override public int hashCode() { return Objects.hash(ballotId, optionId); }
    }
}
