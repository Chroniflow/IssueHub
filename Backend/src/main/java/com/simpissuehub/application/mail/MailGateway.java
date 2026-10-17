package com.simpissuehub.application.mail;

import java.util.List;

public interface MailGateway {
    void send(String recipient, String subject, String text, String html);
    List<IncomingMail> fetchUnread();
    record IncomingMail(String messageId, String from, String subject, String inReplyTo, String text, long attachmentBytes) {}
}
