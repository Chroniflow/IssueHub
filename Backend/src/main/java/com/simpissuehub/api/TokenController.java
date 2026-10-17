package com.simpissuehub.api;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.domain.ApiToken;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.ApiTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/tokens")
@SecurityRequirement(name = "sessionToken")
@SecurityRequirement(name = "apiToken")
public class TokenController {
    private final ApiTokenRepository tokens; private final AuditService audit; private final CurrentUserService identity; private final SecureRandom random = new SecureRandom();
    public TokenController(ApiTokenRepository tokens, AuditService audit, CurrentUserService identity) { this.tokens = tokens; this.audit = audit; this.identity = identity; }

    @GetMapping public List<TokenView> list() { User user = currentUser(); return tokens.findByUserIdOrderByCreatedAtDescIdDesc(user.id).stream().map(TokenView::of).toList(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CreatedToken create(@Valid @RequestBody TokenRequest request) { User user = currentUser(); String scopes = request.permission() == Permission.READ_WRITE ? "READ,WRITE" : "READ"; CreatedToken created = issue(user, request.name(), scopes, request.expiresAt()); audit.record(user, "API_TOKEN_CREATED", "API_TOKEN", created.id(), "创建 API Token"); return created; }

    @PostMapping("/{id}/rotate")
    @Transactional
    public CreatedToken rotate(@PathVariable Long id) { User user = currentUser(); ApiToken old = ownedForUpdate(id, user); if (old.revokedAt != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "已撤销的 Token 无法轮换"); if (old.expiresAt != null && !old.expiresAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.CONFLICT, "已过期的 Token 无法轮换，请创建新的 Token"); old.revokedAt = Instant.now(); tokens.saveAndFlush(old); CreatedToken created = issue(user, old.name, old.scopes, old.expiresAt); audit.record(user, "API_TOKEN_ROTATED", "API_TOKEN", created.id(), "轮换 API Token，原 Token ID：" + old.id); return created; }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable Long id) { User user = currentUser(); ApiToken token = ownedForUpdate(id, user); if (token.revokedAt != null) return; token.revokedAt = Instant.now(); tokens.saveAndFlush(token); audit.record(user, "API_TOKEN_REVOKED", "API_TOKEN", id, "撤销 API Token"); }

    private CreatedToken issue(User user, String name, String scopes, Instant expiresAt) { String plain = "sih_" + randomHex(32); ApiToken token = new ApiToken(); token.user = user; token.name = name.trim(); token.scopes = scopes; token.expiresAt = expiresAt; token.tokenHash = hash(plain); ApiToken saved = tokens.saveAndFlush(token); return new CreatedToken(saved.id, saved.name, plain, saved.scopes, saved.expiresAt); }
    private ApiToken ownedForUpdate(Long id, User user) { return tokens.findByIdForUpdate(id).filter(t -> t.user.id.equals(user.id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token 不存在")); }
    private User currentUser() { return identity.require(); }
    private String randomHex(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return HexFormat.of().formatHex(value); }
    public static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    public enum Permission { READ, READ_WRITE }
    public record TokenRequest(@NotBlank @Size(max = 64) String name, @NotNull Permission permission, @Future Instant expiresAt) {}
    public record TokenView(Long id, String name, String scopes, Instant expiresAt, Instant lastUsedAt, Instant revokedAt, Instant createdAt) { static TokenView of(ApiToken t) { return new TokenView(t.id, t.name, t.scopes, t.expiresAt, t.lastUsedAt, t.revokedAt, t.createdAt); } }
    public record CreatedToken(Long id, String name, String token, String scopes, Instant expiresAt) {}
}
