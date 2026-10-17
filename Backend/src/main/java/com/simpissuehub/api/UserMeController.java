package com.simpissuehub.api;

import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.domain.User;
import com.simpissuehub.application.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/users/me")
@SecurityRequirement(name = "sessionToken")
@SecurityRequirement(name = "apiToken")
public class UserMeController {
    private final CurrentUserService identity;
    private final AuditService audit;
    public UserMeController(CurrentUserService identity, AuditService audit) { this.identity = identity; this.audit = audit; }

    @GetMapping
    public UserView get() { return UserView.of(identity.require()); }

    @PatchMapping("/avatar")
    @Transactional
    public UserView updateAvatar(@Valid @RequestBody AvatarRequest request) {
        User user = identity.require();
        String value = request.avatarUrl() == null ? null : request.avatarUrl().trim();
        if (value != null && value.isBlank()) value = null;
        user.avatarUrl = value;
        audit.record(user, "AVATAR_UPDATED", "USER", user.id, value == null ? "清除头像" : "更新头像地址");
        return UserView.of(user);
    }

    public record AvatarRequest(@Size(max = 512) @Pattern(regexp = "https?://\\S+", message = "头像地址必须是 http 或 https URL") String avatarUrl) {}
    public record UserView(Long id, String username, String email, String avatarUrl, User.Role role, boolean enabled) {
        static UserView of(User user) { return new UserView(user.id, user.username, user.email, user.avatarUrl, user.role, user.enabled); }
    }
}
