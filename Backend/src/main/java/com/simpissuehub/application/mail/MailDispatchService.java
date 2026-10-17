package com.simpissuehub.application.mail;

import com.simpissuehub.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MailDispatchService {
    private static final Logger log = LoggerFactory.getLogger(MailDispatchService.class);
    private final MailGateway gateway;
    private final MailProperties properties;
    private final MailTemplateRenderer templates;
    private final int maxAttempts;
    private final long backoffMillis;
    private final ConcurrentLinkedDeque<FailedMail> deadLetters = new ConcurrentLinkedDeque<>();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public MailDispatchService(MailGateway gateway,
                               MailProperties properties,
                               MailTemplateRenderer templates,
                               @Value("${app.mail.dispatch.max-attempts:3}") int maxAttempts,
                               @Value("${app.mail.dispatch.backoff-ms:1000}") long backoffMillis) {
        this.gateway = gateway; this.properties = properties; this.templates = templates; this.maxAttempts = Math.max(1, Math.min(maxAttempts, 10)); this.backoffMillis = Math.max(0, Math.min(backoffMillis, 300_000));
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailEvent(MailEvent event) {
        if (!enabled(event.type())) return;
        List<String> recipients = new ArrayList<>();
        if (event.recipient() != null && event.recipient().email != null) recipients.add(event.recipient().email);
        if (event.includeConfiguredRecipients()) for (String address : properties.getNotifications().getAdditionalRecipients()) {
            if (address != null && !address.isBlank() && recipients.stream().noneMatch(existing -> existing.equalsIgnoreCase(address.trim()))) recipients.add(address.trim());
        }
        for (String recipient : recipients) deliver(event, recipient);
    }

    private boolean enabled(MailEventType type) {
        return switch (type) {
            case ISSUE_CREATED -> false;
            case COMMENT_CREATED -> properties.getNotifications().isCommentCreated();
            case ISSUE_STATE_CHANGED -> properties.getNotifications().isStateChanged();
            case MENTIONED -> properties.getNotifications().isMentioned();
            default -> true;
        };
    }

    private void deliver(MailEvent event, String recipient) {
        if (recipient.isBlank() || !recipient.contains("@")) return;
        MailTemplateRenderer.RenderedMail mail = templates.render(event);
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                gateway.send(recipient, mail.subject(), mail.text(), mail.html());
                sent.incrementAndGet();
                return;
            } catch (Exception exception) {
                last = exception;
                if (attempt < maxAttempts && backoffMillis > 0) try { Thread.sleep(backoffMillis * (1L << Math.min(attempt - 1, 8))); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
        failed.incrementAndGet();
        while (deadLetters.size() >= 1000) deadLetters.pollFirst();
        deadLetters.addLast(new FailedMail(event, last == null ? "unknown" : last.getMessage(), Instant.now()));
        log.error("邮件投递失败，已进入死信队列：type={}, recipient={}", event.type(), recipient, last);
    }

    @Scheduled(fixedDelayString = "${app.mail.retention.cleanup-interval:3600000}")
    public void cleanupExpiredDeadLetters() {
        long retentionDays = Math.min(36_500L, Math.max(1L, properties.getRetention().getOutgoingDays()));
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        int before = deadLetters.size();
        deadLetters.removeIf(letter -> letter.failedAt().isBefore(cutoff));
        int removed = before - deadLetters.size();
        if (removed > 0) log.info("已清理过期出站邮件死信：count={}, retentionDays={}", removed, retentionDays);
    }

    public DeliveryStats stats() { return new DeliveryStats(sent.get(), failed.get(), deadLetters.size()); }
    public record DeliveryStats(long sent, long failed, int deadLetters) {}
    public record FailedMail(MailEvent event, String reason, Instant failedAt) {}
}
