package com.simpissuehub.application.mail;

import com.simpissuehub.domain.User;
import com.simpissuehub.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MentionNotificationService {
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([A-Za-z0-9_]{3,32})");
    private final UserRepository users;
    private final MailEventPublisher events;

    public MentionNotificationService(UserRepository users, MailEventPublisher events) {
        this.users = users;
        this.events = events;
    }

    public void publish(String text, User actor, Long issueId, Long commentId) {
        if (text == null || text.isBlank()) return;
        Matcher matcher = MENTION.matcher(text);
        HashSet<Long> notified = new HashSet<>();
        while (matcher.find()) {
            users.findByUsernameIgnoreCase(matcher.group(1))
                    .filter(user -> user.enabled && !user.id.equals(actor.id) && notified.add(user.id))
                    .ifPresent(user -> events.publish(new MailEvent(
                            MailEventType.MENTIONED, user,
                            "你在工单 #" + issueId + " 中被提及",
                            "玩家 " + actor.username + " 在工单中提及了你，请打开反馈查看详情。",
                            issueId, commentId)));
        }
    }
}
