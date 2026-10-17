package com.simpissuehub.api;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.IssuePollService;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PollAuthorizationTest {
    @Test
    void ordinaryUserCannotCreateStandalonePoll() {
        IssuePollService polls = mock(IssuePollService.class);
        CurrentUserService identity = mock(CurrentUserService.class);
        when(identity.require()).thenReturn(user(1L, User.Role.USER));
        PollController controller = new PollController(polls, identity, mock(AuditService.class), new AuthorizationService());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(new PollController.CreatePollRequest("Question", false, List.of("A", "B"))));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(polls);
    }

    @Test
    void ordinaryIssueAuthorCannotCreateIssuePoll() {
        User author = user(1L, User.Role.USER);
        Issue issue = new Issue(); issue.id = 42L; issue.author = author;
        IssueRepository issues = mock(IssueRepository.class);
        when(issues.findById(42L)).thenReturn(Optional.of(issue));
        CurrentUserService identity = mock(CurrentUserService.class);
        when(identity.require()).thenReturn(author);
        IssuePollService polls = mock(IssuePollService.class);
        IssuePollController controller = new IssuePollController(issues, polls, identity, mock(AuditService.class), new AuthorizationService());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(42L, new IssuePollController.CreatePollRequest("Question", false, List.of("A", "B"))));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(polls);
    }

    private static User user(Long id, User.Role role) {
        User user = new User(); user.id = id; user.role = role; user.enabled = true; return user;
    }
}
