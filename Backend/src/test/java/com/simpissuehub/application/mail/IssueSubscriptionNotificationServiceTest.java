package com.simpissuehub.application.mail;

import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssueSubscriptionNotificationServiceTest {
    private final IssueSubscriptionRepository subscriptions = mock(IssueSubscriptionRepository.class);
    private final MailEventPublisher events = mock(MailEventPublisher.class);
    private final IssueSubscriptionNotificationService service = new IssueSubscriptionNotificationService(subscriptions, events);

    @Test
    void notifiesEnabledSubscribersExceptActorAndPrimaryRecipient() {
        User actor = user(1L, "actor");
        User author = user(2L, "author");
        User subscriber = user(3L, "subscriber");
        Issue issue = new Issue();
        issue.id = 42L;
        issue.author = author;
        when(subscriptions.findEnabledSubscribers(42L)).thenReturn(List.of(actor, author, subscriber));

        service.publish(MailEventType.COMMENT_CREATED, issue, actor, author,
                "订阅通知", "工单有新评论", 9L);

        verify(events).publish(argThat(event -> event.recipient().id.equals(subscriber.id)
                && event.issueId().equals(42L)
                && event.commentId().equals(9L)
                && !event.includeConfiguredRecipients()));
        verify(events, never()).publish(argThat(event -> event.recipient().id.equals(actor.id) || event.recipient().id.equals(author.id)));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.email = username + "@example.test";
        user.enabled = true;
        return user;
    }
}
