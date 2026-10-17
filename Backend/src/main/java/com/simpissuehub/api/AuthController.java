package com.simpissuehub.api;

import cn.dev33.satoken.stp.StpUtil;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.UserRepository;
import com.simpissuehub.repository.PasswordResetTokenRepository;
import com.simpissuehub.domain.PasswordResetToken;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.LoginRateLimiter;
import com.simpissuehub.application.RegistrationVerificationService;
import com.simpissuehub.application.TurnstileVerifier;
import com.simpissuehub.application.TurnstileChallengeLimiter;
import com.simpissuehub.config.ClientIpResolver;
import com.simpissuehub.application.mail.MailEvent;
import com.simpissuehub.application.mail.MailEventPublisher;
import com.simpissuehub.application.mail.MailEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUserService identity;
    private final AuditService audit;
    private final MailEventPublisher mailEvents;
    private final PasswordResetTokenRepository resetTokens;
    private final SecureRandom random = new SecureRandom();
    private final String resetBaseUrl;
    private final LoginRateLimiter loginRateLimiter;
    private final HttpServletRequest httpRequest;
    private final RegistrationVerificationService registrationVerification;
    private final TurnstileVerifier turnstile;
    private final TurnstileChallengeLimiter turnstileLimiter;
    private final ClientIpResolver clientIpResolver;
    public AuthController(UserRepository users, PasswordEncoder encoder, CurrentUserService identity, AuditService audit,
                          MailEventPublisher mailEvents, PasswordResetTokenRepository resetTokens,
                          @Value("${app.frontend.base-url:http://localhost:5173}") String resetBaseUrl,
                          LoginRateLimiter loginRateLimiter, HttpServletRequest httpRequest,
                          RegistrationVerificationService registrationVerification, TurnstileVerifier turnstile,
                          TurnstileChallengeLimiter turnstileLimiter, ClientIpResolver clientIpResolver) {
        this.users = users; this.encoder = encoder; this.identity = identity; this.audit = audit;
        this.mailEvents = mailEvents; this.resetTokens = resetTokens; this.resetBaseUrl = resetBaseUrl;
        this.loginRateLimiter = loginRateLimiter; this.httpRequest = httpRequest;
        this.registrationVerification = registrationVerification; this.turnstile = turnstile; this.turnstileLimiter = turnstileLimiter; this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/registration/config")
    public RegistrationConfig registrationConfig() {
        String ip = clientIpResolver.resolve(httpRequest);
        return new RegistrationConfig(registrationVerification.required(), turnstile.enabled(), turnstile.enabled() && turnstileLimiter.required(ip));
    }

    @PostMapping("/registration/code")
    public MessageView requestRegistrationCode(@Valid @RequestBody RegistrationCodeRequest request) {
        String ip = clientIpResolver.resolve(httpRequest);
        if (turnstileLimiter.recordAndCheck(ip)) turnstile.verify(request.turnstileToken(), ip);
        registrationVerification.sendCode(request.email());
        return new MessageView("如果该邮箱可用于注册，验证码已发送。");
    }

    @PostMapping("/register")
    @Transactional
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        String ip = clientIpResolver.resolve(httpRequest);
        if (turnstileLimiter.recordAndCheck(ip)) turnstile.verify(request.turnstileToken(), ip);
        String username = request.username().trim();
        if (username.length() < 3 || username.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名长度必须为 3-32 个字符");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameOrEmail(username, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或邮箱已存在");
        }
        registrationVerification.verifyAndConsume(email, request.verificationCode());
        User user = new User();
        user.username = username; user.email = email; user.passwordHash = encoder.encode(request.password());
        user = users.saveAndFlush(user);
        User registeredUser = user;
        audit.record(user, "USER_REGISTERED", "USER", user.id, "注册用户");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { StpUtil.login(registeredUser.id); }
        });
        mailEvents.publish(new MailEvent(MailEventType.REGISTRATION, user, "欢迎加入 SimpIssueHub", "你的账号已创建成功。", null, null));
        return UserView.of(user);
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request) {
        String ip = clientIpResolver.resolve(httpRequest);
        if (turnstileLimiter.recordAndCheck(ip)) turnstile.verify(request.turnstileToken(), ip, "login");
        String username = request.username().trim();
        if (username.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名或邮箱不能为空");
        String rateKey = rateKey(username, ip);
        if (!loginRateLimiter.allowed(rateKey)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请 15 分钟后重试");
        User user = users.findByUsernameIgnoreCase(username).or(() -> users.findByEmailIgnoreCase(username)).filter(u -> u.enabled)
                .orElse(null);
        if (user == null || !encoder.matches(request.password(), user.passwordHash)) { loginRateLimiter.failed(rateKey); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误"); }
        loginRateLimiter.succeeded(rateKey); StpUtil.login(user.id); return UserView.of(user);
    }

    @PostMapping("/logout") public void logout() { StpUtil.logout(); }
    @DeleteMapping("/session") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession() { StpUtil.logout(); }

    @PostMapping("/password-reset/request")
    public MessageView requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        String identifier = request.usernameOrEmail().trim();
        users.findByUsernameIgnoreCase(identifier).or(() -> users.findByEmailIgnoreCase(identifier)).filter(user -> user.enabled).ifPresent(user -> {
            byte[] bytes = new byte[32]; random.nextBytes(bytes); String plain = HexFormat.of().formatHex(bytes);
            PasswordResetToken token = new PasswordResetToken(); token.user = user; token.tokenHash = hash(plain); token.expiresAt = Instant.now().plusSeconds(30 * 60); resetTokens.save(token);
            String link = resetBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + plain;
            mailEvents.publish(new MailEvent(MailEventType.PASSWORD_RESET, user, "重置 SimpIssueHub 密码", "请在 30 分钟内打开以下链接重置密码：\n" + link, null, null));
            audit.record(user, "PASSWORD_RESET_REQUESTED", "USER", user.id, "申请密码重置");
        });
        return new MessageView("如果账号存在，重置邮件已发送。");
    }

    @PostMapping("/password-reset/confirm")
    @Transactional
    public MessageView confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokens.findUsableByHashForUpdate(hash(request.token()), now).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "重置链接无效或已过期"));
        User user = token.user;
        user.passwordHash = encoder.encode(request.newPassword()); user.updatedAt = now; users.saveAndFlush(user);
        resetTokens.consumeAllUnusedForUser(user.id, now);
        audit.record(user, "PASSWORD_RESET_CONFIRMED", "USER", user.id, "完成密码重置并使全部重置链接失效");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { StpUtil.kickout(user.id); }
        });
        return new MessageView("密码已重置，请使用新密码登录。");
    }

    @GetMapping("/me")
    public UserView me() {
        return UserView.of(identity.require());
    }

    @PatchMapping("/me/password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = identity.require();
        if (!encoder.matches(request.currentPassword(), user.passwordHash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        if (request.currentPassword().equals(request.newPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        user.passwordHash = encoder.encode(request.newPassword()); users.saveAndFlush(user); audit.record(user, "PASSWORD_CHANGED", "USER", user.id, "修改密码并使旧会话失效");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { StpUtil.kickout(user.id); }
        });
    }

    @PatchMapping("/me/email")
    @Transactional
    public UserView changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        User user = identity.require();
        if (!encoder.matches(request.currentPassword(), user.passwordHash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被其他账号使用");
        if (user.email.equalsIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新邮箱与当前邮箱相同");
        user.email = email; User saved = users.saveAndFlush(user); audit.record(user, "EMAIL_CHANGED", "USER", user.id, "修改账户邮箱"); return UserView.of(saved);
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 8, max = 128) String password,
                                  @Size(max = 6) String verificationCode,
                                  @Size(max = 2048) String turnstileToken) {}
    public record RegistrationCodeRequest(@NotBlank @Email @Size(max = 254) String email,
                                          @Size(max = 2048) String turnstileToken) {}
    public record RegistrationConfig(boolean emailVerificationRequired, boolean turnstileEnabled, boolean turnstileRequired) {}
    public record LoginRequest(@NotBlank @Size(max = 254) String username,
                               @NotBlank @Size(max = 128) String password,
                               @Size(max = 2048) String turnstileToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
    public record ChangeEmailRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank String currentPassword) {}
    public record PasswordResetRequest(@NotBlank @Size(max = 254) String usernameOrEmail) {}
    public record PasswordResetConfirmRequest(@NotBlank @Size(max = 128) String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
    public record MessageView(String message) {}
    public record UserView(Long id, String username, String email, String avatarUrl, User.Role role) { static UserView of(User u) { return new UserView(u.id, u.username, u.email, u.avatarUrl, u.role); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String rateKey(String username, String remote) { return (remote == null ? "unknown" : remote) + "|" + username.trim().toLowerCase(Locale.ROOT); }
}
