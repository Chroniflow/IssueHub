package com.simpissuehub.application;

import com.simpissuehub.domain.*;
import com.simpissuehub.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
class IssuePollConcurrencyIntegrationTest {
    @Autowired private IssuePollService service;
    @Autowired private IssuePollRepository polls;
    @Autowired private IssuePollBallotRepository ballots;
    @Autowired private IssuePollChoiceRepository choices;
    @Autowired private IssueRepository issues;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private Issue issue;
    private IssuePollService.PollSnapshot poll;

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        user = new User();
        user.username = "poll_" + suffix;
        user.email = user.username + "@example.test";
        user.passwordHash = passwordEncoder.encode("integration-password");
        user = users.saveAndFlush(user);
        issue = new Issue();
        issue.title = "Concurrent poll test";
        issue.body = "Verifies per-user ballot serialization.";
        issue.author = user;
        issue = issues.saveAndFlush(issue);
        poll = service.create(issue, "Pick one", false, List.of("A", "B"));
    }

    @AfterEach
    void cleanUp() {
        if (issue != null) polls.findByIssue_Id(issue.id).ifPresent(polls::delete);
        if (issue != null) issues.deleteById(issue.id);
        if (user != null) users.deleteById(user.id);
    }

    @Test
    void concurrentVotesFromOneUserRemainOneBallotWithOneChoice() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> voteAfterBarrier(poll.options().get(0).id(), ready, start));
            Future<?> second = executor.submit(() -> voteAfterBarrier(poll.options().get(1).id(), ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, ballots.countByPoll_Id(poll.id()));
        long selectedOptions = choices.countByPollId(poll.id()).stream().mapToLong(IssuePollChoiceRepository.OptionVoteCount::getVoteCount).sum();
        assertEquals(1, selectedOptions);
    }

    private void voteAfterBarrier(Long optionId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("vote barrier timed out");
            service.vote(issue.id, user, List.of(optionId));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
