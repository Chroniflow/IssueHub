package com.simpissuehub.application.mail;

import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueSubscriptionRepository;
import org.springframework.stereotype.Service;

@Service
public class IssueSubscriptionNotificationService {
    private final IssueSubscriptionRepository subscriptions;
    private final MailEventPublisher events;

    public IssueSubscriptionNotificationService(IssueSubscriptionRepository subscriptions, MailEventPublisher events) {
        this.subscriptions = subscriptions;
        this.events = events;
    }

    public void publish(MailEventType type, Issue issue, User actor, User primaryRecipient,
                        String subject, String text, Long commentId) {
        subscriptions.findEnabledSubscribers(issue.id).stream()
                .filter(user -> actor == null || !user.id.equals(actor.id))
                .filter(user -> primaryRecipient == null || !user.id.equals(primaryRecipient.id))
                .forEach(user -> events.publish(MailEvent.direct(type, user, subject, text, issue.id, commentId)));
    }
}
