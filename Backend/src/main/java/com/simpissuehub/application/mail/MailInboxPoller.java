package com.simpissuehub.application.mail;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.config.MailProperties;
import com.simpissuehub.domain.IncomingMailMessage;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.IssueComment;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IncomingMailMessageRepository;
import com.simpissuehub.repository.IssueCommentRepository;
import com.simpissuehub.repository.IssueRepository;
import com.simpissuehub.repository.IssueSubscriptionRepository;
import com.simpissuehub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MailInboxPoller {
    private static final Logger log = LoggerFactory.getLogger(MailInboxPoller.class);
    private static final Pattern ISSUE_NUMBER = Pattern.compile("(?i)(?:#|issue\\s*)(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("<([^>]+)>");
    private final MailGateway gateway;
    private final MailProperties properties;
    private final IncomingMailMessageRepository messages;
    private final IssueRepository issues;
    private final IssueCommentRepository comments;
    private final UserRepository users;
    private final AuditService audit;
    private final MailEventPublisher events;
    private final IssueSubscriptionNotificationService subscriptionNotifications;
    private final IssueSubscriptionRepository subscriptions;

    public MailInboxPoller(MailGateway gateway, MailProperties properties, IncomingMailMessageRepository messages,
                           IssueRepository issues, IssueCommentRepository comments, UserRepository users,
                           AuditService audit, MailEventPublisher events,
                           IssueSubscriptionNotificationService subscriptionNotifications,
                           IssueSubscriptionRepository subscriptions) {
        this.gateway = gateway; this.properties = properties; this.messages = messages; this.issues = issues;
        this.comments = comments; this.users = users; this.audit = audit; this.events = events;
        this.subscriptionNotifications = subscriptionNotifications;
        this.subscriptions = subscriptions;
    }

    @Scheduled(fixedDelayString = "${app.mail.imap.poll-interval:60000}")
    @Transactional
    public void poll() {
        cleanupRetainedMessages();
        if (!properties.getRules().isReplyCreatesComment()) return;
        try {
            for (MailGateway.IncomingMail mail : gateway.fetchUnread()) process(mail);
        } catch (Exception exception) {
            log.warn("IMAP 收信轮询失败", exception);
        }
    }

    private void cleanupRetainedMessages() {
        long retentionDays = Math.min(36_500L, Math.max(1L, properties.getRetention().getIncomingDays()));
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        int deleted = messages.deleteReceivedBefore(cutoff);
        if (deleted > 0) log.info("已清理过期收信记录：count={}, retentionDays={}", deleted, retentionDays);
    }

    private void process(MailGateway.IncomingMail mail) {
        String messageId = mail.messageId() == null || mail.messageId().isBlank()
                ? digest(mail.from() + "\n" + mail.subject() + "\n" + mail.text()) : mail.messageId().trim();
        if (messages.findByMessageId(messageId).isPresent()) {
            if (properties.getRules().isDeduplicateByMessageId()) return;
            messageId = digest(messageId + ":" + UUID.randomUUID());
        }
        String senderAddress = addressOf(mail.from());
        IncomingMailMessage record = new IncomingMailMessage(); record.messageId = messageId;
        record.sender = senderAddress; record.status = "IGNORED";
        String referenceMode = properties.getRules().getIssueReference() == null ? "SUBJECT_OR_IN_REPLY_TO" : properties.getRules().getIssueReference().trim().toUpperCase(Locale.ROOT);
        String reference = switch (referenceMode) {
            case "IN_REPLY_TO" -> mail.inReplyTo();
            case "SUBJECT" -> mail.subject();
            default -> (mail.subject() == null ? "" : mail.subject()) + " " + (mail.inReplyTo() == null ? "" : mail.inReplyTo());
        };
        Matcher issueMatcher = ISSUE_NUMBER.matcher(reference == null ? "" : reference);
        User sender = properties.getRules().isRequireSenderVerification()
                ? users.findByEmailIgnoreCase(senderAddress).orElse(null)
                : users.findByUsernameOrEmail(senderAddress, senderAddress).orElse(null);
        // 评论必须关联本地用户；关闭严格校验也不会创建匿名评论。
        if (sender == null) {
            record.status = "UNVERIFIED_SENDER"; record.processedAt = Instant.now(); messages.save(record); return;
        }
        if (issueMatcher.find()) {
            long issueId = Long.parseLong(issueMatcher.group(1));
            Issue issue = issues.findById(issueId).filter(value -> !value.deleted).orElse(null);
            String text = mail.text() == null ? "" : mail.text().trim();
            boolean rejectOversizedAttachment = "REJECT".equalsIgnoreCase(properties.getAttachments().getMode())
                    && mail.attachmentBytes() > Math.max(0, properties.getAttachments().getMaxBytes());
            if (rejectOversizedAttachment) {
                record.status = "ATTACHMENT_TOO_LARGE";
            } else if (issue != null && !text.isBlank() && text.length() <= 10000) {
                IssueComment comment = new IssueComment(); comment.issue = issue; comment.author = sender; comment.body = text;
                comments.saveAndFlush(comment); subscriptions.subscribe(issue.id, sender.id); record.issue = issue; record.status = "COMMENT_CREATED";
                audit.record(sender, "COMMENT_CREATED_BY_EMAIL", "COMMENT", comment.id, "邮件回复创建评论");
                events.publish(new MailEvent(MailEventType.COMMENT_CREATED, issue.author, "工单收到新评论 #" + issue.id, "你的反馈收到了一条新评论。", issue.id, comment.id));
                subscriptionNotifications.publish(MailEventType.COMMENT_CREATED, issue, sender, issue.author,
                        "订阅的工单收到新评论 #" + issue.id, "你订阅的反馈收到了一条新评论。", comment.id);
            } else if (issue == null) record.status = "ISSUE_NOT_FOUND";
            else if (text.isBlank()) record.status = "EMPTY_BODY";
            else record.status = "BODY_TOO_LARGE";
        } else record.status = "ISSUE_REFERENCE_MISSING";
        record.processedAt = Instant.now(); messages.save(record);
    }

    private String addressOf(String value) {
        if (value == null) return "";
        Matcher matcher = EMAIL.matcher(value);
        return (matcher.find() ? matcher.group(1) : value).trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
