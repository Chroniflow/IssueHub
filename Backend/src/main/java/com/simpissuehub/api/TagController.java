package com.simpissuehub.api;

import cn.dev33.satoken.stp.StpUtil;
import com.simpissuehub.domain.Tag;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.UserRepository;
import com.simpissuehub.repository.TagRepository;
import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@SecurityRequirement(name = "sessionToken")
@SecurityRequirement(name = "apiToken")
public class TagController {
    private final TagRepository tags;
    private final UserRepository users;
    private final AuditService audit;
    private final CurrentUserService identity;
    private final AuthorizationService authorization;

    public TagController(TagRepository tags, UserRepository users, AuditService audit, CurrentUserService identity, AuthorizationService authorization) {
        this.tags = tags; this.users = users; this.audit = audit; this.identity = identity; this.authorization = authorization;
    }

    @GetMapping
    public List<TagView> list() {
        return tags.findSummaries().stream()
                .map(tag -> new TagView(tag.getId(), tag.getName(), tag.getColor(), tag.getIssueCount()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TagView create(@Valid @RequestBody TagRequest request) {
        User user = authorization.requireStaff(currentUser());
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签名称不能为空");
        if (tags.findByName(name).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "标签名称已存在");
        Tag tag = new Tag(); tag.name = name; tag.color = request.color() == null || request.color().isBlank() ? "#DDEBE3" : request.color().toUpperCase();
        Tag saved = tags.saveAndFlush(tag); audit.record(user, "TAG_CREATED", "TAG", saved.id, "创建标签 " + saved.name);
        return new TagView(saved.id, saved.name, saved.color, 0L);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        User user = authorization.requireAdmin(currentUser());
        Tag tag = tags.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在"));
        if (tags.countIssuesByTagId(id) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "标签仍被工单使用，无法删除");
        tags.delete(tag); tags.flush(); audit.record(user, "TAG_DELETED", "TAG", id, "删除标签 " + tag.name);
    }

    private User currentUser() { return identity.require(); }
    public record TagView(Long id, String name, String color, Long issueCount) {}
    public record TagRequest(@NotBlank @Size(max = 32) String name, @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "颜色必须是六位十六进制值") String color) {}
}
