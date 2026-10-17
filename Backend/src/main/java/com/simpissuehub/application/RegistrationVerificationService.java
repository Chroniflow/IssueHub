package com.simpissuehub.application;

import com.simpissuehub.application.mail.MailGateway;
import com.simpissuehub.application.mail.MailTemplateRenderer;
import com.simpissuehub.domain.RegistrationVerificationCode;
import com.simpissuehub.repository.RegistrationVerificationCodeRepository;
import com.simpissuehub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class RegistrationVerificationService {
    private final RegistrationVerificationCodeRepository codes;
    private final UserRepository users;
    private final MailGateway mailGateway;
    private final MailTemplateRenderer templates;
    private final SecureRandom random = new SecureRandom();
    private final boolean required;
    private final Duration ttl;
    private final Duration cooldown;
    private final int maxAttempts;

    public RegistrationVerificationService(RegistrationVerificationCodeRepository codes, UserRepository users,
                                           MailGateway mailGateway, MailTemplateRenderer templates,
                                           @Value("${app.security.registration-verification.required:true}") boolean required,
                                           @Value("${app.security.registration-verification.ttl-seconds:600}") long ttlSeconds,
                                           @Value("${app.security.registration-verification.cooldown-seconds:60}") long cooldownSeconds,
                                           @Value("${app.security.registration-verification.max-attempts:5}") int maxAttempts) {
        this.codes = codes;
        this.users = users;
        this.mailGateway = mailGateway;
        this.templates = templates;
        this.required = required;
        this.ttl = Duration.ofSeconds(Math.max(60, Math.min(ttlSeconds, 3600)));
        this.cooldown = Duration.ofSeconds(Math.max(10, Math.min(cooldownSeconds, 600)));
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 10));
    }

    public boolean required() { return required; }

    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        Instant now = Instant.now();
        codes.findTopByEmailOrderByCreatedAtDesc(email).ifPresent(latest -> {
            if (latest.createdAt.plus(cooldown).isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后重试");
            }
        });

        codes.consumeUnused(email, now);
        String plainCode = "%06d".formatted(random.nextInt(1_000_000));
        String salt = randomHex(16);
        RegistrationVerificationCode code = new RegistrationVerificationCode();
        code.email = email;
        code.salt = salt;
        code.codeHash = hash(salt, email, plainCode);
        code.expiresAt = now.plus(ttl);
        code.createdAt = now;
        codes.saveAndFlush(code);
        try {
            MailTemplateRenderer.RenderedMail mail = templates.renderVerificationCode(plainCode, ttl.toMinutes());
            mailGateway.send(email, mail.subject(), mail.text(), mail.html());
        } catch (RuntimeException exception) {
            // Do not expose SMTP details as a 500: this is an upstream mail dependency failure.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "邮件服务暂时不可用，请稍后重试", exception);
        }
    }

    @Transactional
    public void verifyAndConsume(String rawEmail, String plainCode) {
        if (!required) return;
        String email = normalize(rawEmail);
        Instant now = Instant.now();
        RegistrationVerificationCode code = codes.findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(email, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码无效或已过期"));
        if (code.attempts >= maxAttempts) {
            code.consumedAt = now;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码尝试次数过多，请重新获取");
        }
        code.attempts++;
        byte[] expected = code.codeHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(code.salt, email, plainCode == null ? "" : plainCode.trim()).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            if (code.attempts >= maxAttempts) code.consumedAt = now;
            codes.saveAndFlush(code);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱验证码错误");
        }
        code.consumedAt = now;
        codes.saveAndFlush(code);
    }

    @Scheduled(fixedDelayString = "${app.security.registration-verification.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpired() {
        codes.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String randomHex(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return HexFormat.of().formatHex(value); }
    private String hash(String salt, String email, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt + ":" + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
