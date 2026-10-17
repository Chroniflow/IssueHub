package com.simpissuehub.application.mail;

import com.simpissuehub.domain.User;

public record MailEvent(MailEventType type, User recipient, String subject, String text, Long issueId,
                        Long commentId, boolean includeConfiguredRecipients) {
    public MailEvent(MailEventType type, User recipient, String subject, String text, Long issueId, Long commentId) {
        this(type, recipient, subject, text, issueId, commentId, true);
    }

    public static MailEvent direct(MailEventType type, User recipient, String subject, String text,
                                   Long issueId, Long commentId) {
        return new MailEvent(type, recipient, subject, text, issueId, commentId, false);
    }
}
