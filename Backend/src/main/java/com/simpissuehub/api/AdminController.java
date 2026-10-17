package com.simpissuehub.api;

import cn.dev33.satoken.stp.StpUtil;
import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.application.RuntimeConfigurationService;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "sessionToken")
@SecurityRequirement(name = "apiToken")
public class AdminController {
    private final UserRepository users; private final AuditService audit; private final CurrentUserService identity; private final AuthorizationService authorization; private final RuntimeConfigurationService configuration;
    public AdminController(UserRepository users, AuditService audit, CurrentUserService identity, AuthorizationService authorization, RuntimeConfigurationService configuration) { this.users = users; this.audit = audit; this.identity = identity; this.authorization = authorization; this.configuration = configuration; }

    @GetMapping("/config")
    public List<RuntimeConfigurationService.Entry> config() { requireAdmin(); return configuration.entries(); }

    @PatchMapping("/config")
    @Transactional
    public RuntimeConfigurationService.Entry updateConfig(@RequestBody UpdateConfigRequest request) {
        User actor = requireAdmin();
        try { configuration.update(request.key(), request.value()); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
        audit.record(actor, "CONFIG_CHANGED", "CONFIG", request.key(), "网页管理面板修改站点功能配置");
        return configuration.entries().stream().filter(item -> item.key().equals(request.key())).findFirst().orElseThrow();
    }

    @GetMapping("/users")
    public PageResponse<UserView> users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.from(users.findAll(pageable), UserView::of);
    }

    @PatchMapping("/users/{id}")
    @Transactional
    public UserView updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User actor = requireAdmin();
        User target = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (target.id.equals(actor.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改自己的管理员账号");
        User.Role role = request.role() == null ? target.role : request.role().toDomainRole();
        boolean disabling = target.enabled && Boolean.FALSE.equals(request.enabled());
        target.role = role; if (request.enabled() != null) target.enabled = request.enabled();
        User saved = users.saveAndFlush(target); audit.record(actor, "USER_UPDATED", "USER", saved.id, "更新用户权限或启用状态");
        if (disabling) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { StpUtil.kickout(saved.id); }
        });
        return UserView.of(saved);
    }

    @PostMapping("/users/{id}/kickout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void kickout(@PathVariable Long id) {
        User actor = requireAdmin();
        User target = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (target.id.equals(actor.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能踢出当前管理员会话");
        audit.record(actor, "USER_KICKED_OUT", "USER", target.id, "管理员强制用户下线");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { StpUtil.kickout(target.id); }
        });
    }

    @DeleteMapping("/users/{id}/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUserSession(@PathVariable Long id) { kickout(id); }

    private User requireAdmin() { return authorization.requireAdmin(identity.require()); }
    /** 网页管理接口只允许在普通用户与工作人员之间调整；ADMIN 仅能由 TUI 授予。 */
    public enum WebAssignableRole {
        USER, STAFF;

        User.Role toDomainRole() { return User.Role.valueOf(name()); }
    }
    public record UpdateUserRequest(WebAssignableRole role, Boolean enabled) {}
    public record UpdateConfigRequest(String key, String value) {}
    public record UserView(Long id, String username, String email, String avatarUrl, User.Role role, boolean enabled, Instant createdAt) { static UserView of(User u) { return new UserView(u.id, u.username, u.email, u.avatarUrl, u.role, u.enabled, u.createdAt); } }
}
