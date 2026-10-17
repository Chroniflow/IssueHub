package com.simpissuehub.api;

import cn.dev33.satoken.stp.StpUtil;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.IssueComment;
import com.simpissuehub.domain.IssueLink;
import com.simpissuehub.domain.IdempotencyRecord;
import com.simpissuehub.domain.User;
import com.simpissuehub.domain.Tag;
import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.IdempotencyService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.application.IssueFilterQuery;
import com.simpissuehub.application.IssuePollService;
import com.simpissuehub.application.mail.MailEvent;
import com.simpissuehub.application.mail.MailEventPublisher;
import com.simpissuehub.application.mail.MailEventType;
import com.simpissuehub.application.mail.IssueSubscriptionNotificationService;
import com.simpissuehub.application.mail.MentionNotificationService;
import com.simpissuehub.repository.IssueCommentRepository;
import com.simpissuehub.repository.IssueLinkRepository;
import com.simpissuehub.repository.IssueRepository;
import com.simpissuehub.repository.IssueSubscriptionRepository;
import com.simpissuehub.repository.UserRepository;
import com.simpissuehub.repository.TagRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/issues")
@SecurityRequirement(name = "sessionToken")
@SecurityRequirement(name = "apiToken")
public class IssueController {
    private final IssueRepository issues; private final IssueCommentRepository comments; private final IssueLinkRepository links; private final UserRepository users; private final TagRepository tags; private final IssueSubscriptionRepository subscriptions; private final IssuePollService polls; private final AuditService audit; private final CurrentUserService identity; private final IdempotencyService idempotency; private final AuthorizationService authorization; private final MailEventPublisher mailEvents; private final MentionNotificationService mentions; private final IssueSubscriptionNotificationService subscriptionNotifications;
    public IssueController(IssueRepository issues, IssueCommentRepository comments, IssueLinkRepository links, UserRepository users, TagRepository tags, IssueSubscriptionRepository subscriptions, IssuePollService polls, AuditService audit, CurrentUserService identity, IdempotencyService idempotency, AuthorizationService authorization, MailEventPublisher mailEvents, MentionNotificationService mentions, IssueSubscriptionNotificationService subscriptionNotifications) { this.issues = issues; this.comments = comments; this.links = links; this.users = users; this.tags = tags; this.subscriptions = subscriptions; this.polls = polls; this.audit = audit; this.identity = identity; this.idempotency = idempotency; this.authorization = authorization; this.mailEvents = mailEvents; this.mentions = mentions; this.subscriptionNotifications = subscriptionNotifications; }

    @GetMapping
    public PageResponse<IssueView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Issue.State state, @RequestParam(required = false) String tag, @RequestParam(required = false) String category, @RequestParam(required = false) String query, @RequestParam(required = false) String author, @RequestParam(defaultValue = "updatedDesc") String sort) {
        if (query != null && query.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索关键词不能超过 200 个字符");
        if (author != null && author.length() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "作者用户名不能超过 32 个字符");
        String normalizedSort = switch (sort) { case "updatedAsc", "createdDesc", "createdAsc" -> sort; default -> "updatedDesc"; };
        Sort issueSort = switch (normalizedSort) { case "updatedAsc" -> Sort.by(Sort.Direction.ASC, "updatedAt"); case "createdDesc" -> Sort.by(Sort.Direction.DESC, "createdAt"); case "createdAsc" -> Sort.by(Sort.Direction.ASC, "createdAt"); default -> Sort.by(Sort.Direction.DESC, "updatedAt"); };
        issueSort = Sort.by(Sort.Direction.DESC, "pinned").and(issueSort).and(Sort.by(Sort.Direction.DESC, "id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), issueSort);
        User viewer = identity.optional();
        IssueFilterQuery filter = IssueFilterQuery.parse(query, viewer == null ? null : viewer.username);
        String normalizedTag = mergeFilter(filter.tag(), tag);
        String normalizedCategory = mergeFilter(filter.category(), category);
        String normalizedQuery = filter.query();
        String normalizedAuthor = mergeFilter(filter.author(), author);
        Page<Issue> result;
        result = issues.searchFiltered(normalizedQuery, normalizedTag, normalizedCategory, state, normalizedAuthor, pageable);
        var participantCounts = polls.participantCounts(result.getContent().stream().map(issue -> issue.id).toList());
        return PageResponse.from(result, issue -> IssueView.of(issue, participantCounts.getOrDefault(issue.id, 0L)));
    }

    @GetMapping("/counts")
    public IssueCounts counts(@RequestParam(required = false) String tag, @RequestParam(required = false) String category,
                              @RequestParam(required = false) String query, @RequestParam(required = false) String author) {
        if (query != null && query.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索关键词不能超过 200 个字符");
        if (author != null && author.length() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "作者用户名不能超过 32 个字符");
        User viewer = identity.optional();
        IssueFilterQuery filter = IssueFilterQuery.parse(query, viewer == null ? null : viewer.username);
        String normalizedTag = mergeFilter(filter.tag(), tag);
        String normalizedCategory = mergeFilter(filter.category(), category);
        String normalizedQuery = filter.query();
        String normalizedAuthor = mergeFilter(filter.author(), author);
        return new IssueCounts(
                issues.countFiltered(normalizedQuery, normalizedTag, normalizedCategory, null, normalizedAuthor),
                issues.countFiltered(normalizedQuery, normalizedTag, normalizedCategory, Issue.State.OPEN, normalizedAuthor),
                issues.countFiltered(normalizedQuery, normalizedTag, normalizedCategory, Issue.State.CLOSED, normalizedAuthor));
    }

    @GetMapping("/{id}")
    public IssueDetail detail(@PathVariable Long id) {
        Issue issue = findIssue(id); return IssueDetail.of(issue, List.of());
    }

    @PatchMapping("/{id}/pinned")
    @Transactional
    public IssueDetail setPinned(@PathVariable Long id, @Valid @RequestBody PinRequest request) {
        User user = authorization.requireStaff(identity.require());
        Issue issue = findIssue(id);
        issue.pinned = request.pinned();
        issues.saveAndFlush(issue);
        audit.record(user, issue.pinned ? "ISSUE_PINNED" : "ISSUE_UNPINNED", "ISSUE", id, issue.pinned ? "置顶工单" : "取消置顶工单");
        return detail(id);
    }

    @GetMapping("/{id}/sub-issues")
    @Transactional(readOnly = true)
    public SubIssueList subIssues(@PathVariable Long id) {
        findIssue(id);
        List<Issue> children = issues.findByParent_IdAndDeletedFalseOrderByCreatedAtAscIdAsc(id);
        long completed = children.stream().filter(child -> child.state == Issue.State.CLOSED).count();
        return new SubIssueList(children.stream().map(SubIssueView::of).toList(), children.size(), completed);
    }

    @PostMapping("/{id}/sub-issues")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public IssueDetail createSubIssue(@PathVariable Long id, @Valid @RequestBody CreateSubIssueRequest request,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser();
        Issue parent = findIssue(id);
        requireIssueTagManager(parent, user);
        String title = request.title() == null ? "" : request.title().trim();
        String body = request.body() == null ? "" : request.body().trim();
        if (title.isBlank() || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和正文不能为空");
        String category = request.category() == null || request.category().isBlank() ? parent.category : request.category().trim();
        if (category.length() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务器名称不能超过 32 个字符");
        String tagName = request.tag() == null || request.tag().isBlank()
                ? parent.tags.stream().findFirst().map(tag -> tag.name).orElse("反馈") : request.tag().trim();
        Tag tag = tags.findByName(tagName).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签不存在"));
        String key = normalizeIdempotencyKey(idempotencyKey);
        String hash = idempotency.hash("ISSUE_SUB_CREATE", id.toString(), title, body, tagName, category);
        if (key != null) {
            Issue previous = issues.findByAuthor_IdAndIdempotencyKey(user.id, key).orElse(null);
            if (previous != null) {
                if (!hash.equals(previous.idempotencyHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于其他请求");
                return detail(previous.id);
            }
        }
        Issue child = new Issue();
        child.title = title; child.body = body; child.category = category; child.author = user; child.parent = parent;
        child.tags.add(tag); child.idempotencyKey = key; child.idempotencyHash = hash;
        child = issues.saveAndFlush(child);
        audit.record(user, "ISSUE_SUB_CREATED", "ISSUE", child.id, "创建子议题，父议题 #" + parent.id);
        mentions.publish(child.title + "\n" + child.body, user, child.id, null);
        return IssueDetail.of(child, List.of());
    }

    @GetMapping("/{id}/subscription")
    @Transactional(readOnly = true)
    public SubscriptionView subscription(@PathVariable Long id) {
        findIssue(id);
        User user = currentUser();
        return subscriptionView(id, user.id);
    }

    @RequestMapping(value = "/{id}/subscription", method = {RequestMethod.PUT, RequestMethod.POST})
    @Transactional
    public SubscriptionView subscribe(@PathVariable Long id) {
        findIssue(id);
        User user = currentUser();
        if (subscriptions.subscribe(id, user.id) > 0) audit.record(user, "ISSUE_SUBSCRIBED", "ISSUE", id, "订阅工单通知");
        return subscriptionView(id, user.id);
    }

    @DeleteMapping("/{id}/subscription")
    @Transactional
    public SubscriptionView unsubscribe(@PathVariable Long id) {
        findIssue(id);
        User user = currentUser();
        if (subscriptions.unsubscribe(id, user.id) > 0) audit.record(user, "ISSUE_UNSUBSCRIBED", "ISSUE", id, "取消订阅工单通知");
        return subscriptionView(id, user.id);
    }

    @GetMapping("/{id}/comments")
    public PageResponse<CommentView> commentPage(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        findIssue(id);
        var commentSort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), commentSort);
        return PageResponse.from(comments.findByIssueIdAndDeletedFalse(id, pageable), CommentView::of);
    }

    @PostMapping("/{issueId}/tags")
    @Transactional
    public IssueDetail bindTag(@PathVariable Long issueId, @Valid @RequestBody TagBindingRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser(); Issue issue = findIssue(issueId); requireIssueTagManager(issue, user);
        Tag tag = tags.findById(request.tagId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在"));
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_TAG_BIND", issueId.toString(), tag.id.toString());
        if (idempotency.find(user, "ISSUE_TAG_BIND", key, hash) != null) return detail(issueId);
        if (issue.tags.stream().anyMatch(existing -> existing.id.equals(tag.id)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已经绑定该标签");
        issue.tags.add(tag); issues.saveAndFlush(issue); audit.record(user, "ISSUE_TAG_BOUND", "ISSUE", issueId, "绑定标签 " + tag.name); idempotency.save(user, "ISSUE_TAG_BIND", key, hash, "ISSUE", issueId.toString()); return detail(issueId);
    }

    @DeleteMapping("/{issueId}/tags/{tagId}")
    @Transactional
    public IssueDetail unbindTag(@PathVariable Long issueId, @PathVariable Long tagId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser(); Issue issue = findIssue(issueId); requireIssueTagManager(issue, user);
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_TAG_UNBIND", issueId.toString(), tagId.toString());
        if (idempotency.find(user, "ISSUE_TAG_UNBIND", key, hash) != null) return detail(issueId);
        boolean removed = issue.tags.removeIf(tag -> tag.id.equals(tagId));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单未绑定该标签");
        issues.saveAndFlush(issue); audit.record(user, "ISSUE_TAG_UNBOUND", "ISSUE", issueId, "解绑标签 " + tagId); idempotency.save(user, "ISSUE_TAG_UNBIND", key, hash, "ISSUE", issueId.toString()); return detail(issueId);
    }

    @GetMapping("/{issueId}/links")
    public List<LinkView> issueLinks(@PathVariable Long issueId) {
        findIssue(issueId);
        return links.findForIssue(issueId).stream().filter(link -> !link.source.deleted && !link.target.deleted).map(link -> LinkView.of(link, issueId)).toList();
    }

    @PostMapping("/{issueId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LinkView addLink(@PathVariable Long issueId, @Valid @RequestBody LinkRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser(); Issue source = findIssue(issueId); requireIssueTagManager(source, user);
        Issue target = findIssue(request.targetIssueId());
        if (source.id.equals(target.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能链接工单自身");
        String relation = request.relationType() == null || request.relationType().isBlank() ? "RELATED" : request.relationType().trim().toUpperCase();
        if (!List.of("RELATED", "BLOCKS", "DUPLICATES").contains(relation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的链接关系");
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_LINK_CREATE", issueId.toString(), target.id.toString(), relation);
        IdempotencyRecord existing = idempotency.find(user, "ISSUE_LINK_CREATE", key, hash);
        if (existing != null) return LinkView.of(links.findById(new IssueLink.Id(source.id, target.id, relation)).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "幂等请求对应的链接不存在")), source.id);
        if (links.existsBetween(source.id, target.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "两个工单已经建立链接");
        IssueLink link = new IssueLink(); link.id = new IssueLink.Id(source.id, target.id, relation); link.source = source; link.target = target;
        LinkView view = LinkView.of(links.save(link), source.id); audit.record(user, "ISSUE_LINK_CREATED", "ISSUE_LINK", source.id + "-" + target.id, "建立工单关联"); idempotency.save(user, "ISSUE_LINK_CREATE", key, hash, "ISSUE_LINK", source.id + ":" + target.id + ":" + relation); return view;
    }

    @DeleteMapping("/{issueId}/links/{targetIssueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeLink(@PathVariable Long issueId, @PathVariable Long targetIssueId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser(); Issue source = findIssue(issueId); requireIssueTagManager(source, user);
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_LINK_DELETE", issueId.toString(), targetIssueId.toString());
        if (idempotency.find(user, "ISSUE_LINK_DELETE", key, hash) != null) return;
        links.findForIssue(issueId).stream().filter(link -> (link.source.id.equals(issueId) && link.target.id.equals(targetIssueId)) || (link.target.id.equals(issueId) && link.source.id.equals(targetIssueId))).forEach(links::delete); audit.record(user, "ISSUE_LINK_DELETED", "ISSUE_LINK", issueId + "-" + targetIssueId, "删除工单关联"); idempotency.save(user, "ISSUE_LINK_DELETE", key, hash, "ISSUE_LINK", issueId + ":" + targetIssueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "创建工单", description = "需要登录。tag 必须是已存在的标签。", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"title\":\"地牢入口无法加载\",\"body\":\"坐标 348,72,-190 附近区块无法加载。\",\"tag\":\"bug\"}"))))
    public IssueDetail create(@Valid @RequestBody CreateIssueRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String title = request.title() == null ? "" : request.title().trim();
        String body = request.body() == null ? "" : request.body().trim();
        if (title.isBlank() || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和正文不能为空");
        User user = currentUser();
        if (request.poll() != null) authorization.requireStaff(user);
        Issue issue = new Issue(); issue.title = title; issue.body = body; issue.author = user;
        String tagName = request.tag() == null || request.tag().isBlank() ? "反馈" : request.tag().trim();
        String category = request.category() == null ? "" : request.category().trim();
        if (category.isBlank() || category.length() > 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须选择反馈所属的服务器");
        Tag tag = tags.findByName(tagName).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签不存在"));
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = hashRequest(title, body, tagName, category, request.poll());
        if (normalizedKey != null) {
            Issue previous = issues.findByAuthor_IdAndIdempotencyKey(user.id, normalizedKey).orElse(null);
            if (previous != null) {
                if (!requestHash.equals(previous.idempotencyHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于其他请求");
                return IssueDetail.of(previous, List.of());
            }
            issue.idempotencyKey = normalizedKey; issue.idempotencyHash = requestHash;
        }
        issue.tags.add(tag);
        issue.category = category;
        issue = issues.saveAndFlush(issue);
        subscriptions.subscribe(issue.id, user.id);
        if (request.poll() != null) {
            var poll = polls.create(issue, user, request.poll().question(), request.poll().multipleChoice(), request.poll().options());
            audit.record(user, "ISSUE_POLL_CREATED", "ISSUE_POLL", poll.id(), "创建工单投票");
        }
        audit.record(user, "ISSUE_CREATED", "ISSUE", issue.id, "创建工单"); mentions.publish(issue.title + "\n" + issue.body, user, issue.id, null); return IssueDetail.of(issue, List.of());
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @Transactional
    public IssueDetail edit(@PathVariable Long id, @Valid @RequestBody EditIssueRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser(); Issue issue = findIssue(id); requireIssueTagManager(issue, user);
        String title = request.title() == null ? "" : request.title().trim();
        String body = request.body() == null ? "" : request.body().trim();
        if (title.isBlank() || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和正文不能为空");
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_EDIT", id.toString(), title, body);
        if (idempotency.find(user, "ISSUE_EDIT", key, hash) != null) return detail(id);
        issue.title = title; issue.body = body;
        issues.saveAndFlush(issue); audit.record(user, "ISSUE_EDITED", "ISSUE", id, "编辑工单内容"); idempotency.save(user, "ISSUE_EDIT", key, hash, "ISSUE", id.toString()); return detail(id);
    }

    /** REST 兼容入口：通过资源状态更新替代动作式的 /close 和 /reopen。 */
    @PatchMapping("/{id}/state")
    public IssueDetail updateState(@PathVariable Long id, @Valid @RequestBody StateRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return request.state() == Issue.State.CLOSED ? close(id, null, idempotencyKey) : reopen(id, idempotencyKey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteIssue(@PathVariable Long id, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Issue issue = findIssue(id); User user = currentUser();
        authorization.requireAdmin(user);
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_DELETE", id.toString());
        if (idempotency.find(user, "ISSUE_DELETE", key, hash) != null) return;
        issue.deleted = true; issues.saveAndFlush(issue); audit.record(user, "ISSUE_DELETED", "ISSUE", id, "软删除工单"); idempotency.save(user, "ISSUE_DELETE", key, hash, "ISSUE", id.toString());
    }

    @PostMapping("/{id}/close")
    @Transactional
    public IssueDetail close(@PathVariable Long id, @Valid @RequestBody(required = false) CloseRequest request,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Issue issue = findIssue(id); User user = currentUser();
        if (!authorization.canCloseIssue(issue, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工单作者或工作人员可以关闭工单");
        Issue.CloseReason reason = request == null || request.reason() == null ? Issue.CloseReason.COMPLETED : request.reason();
        Long duplicateOfId = request == null ? null : request.duplicateOfIssueId();
        Issue duplicateOf = null;
        if (reason == Issue.CloseReason.DUPLICATE) {
            if (duplicateOfId == null || duplicateOfId.equals(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "重复工单必须指定另一个工单编号");
            duplicateOf = findIssue(duplicateOfId);
        } else if (duplicateOfId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有重复工单可以指定重复目标");
        }
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_CLOSE", id.toString(), reason.name(), duplicateOfId == null ? "" : duplicateOfId.toString());
        if (idempotency.find(user, "ISSUE_CLOSE", key, hash) != null) return detail(id);
        if (issue.state == Issue.State.CLOSED) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已经是关闭状态");
        issue.state = Issue.State.CLOSED; issue.closedBy = user; issue.closedAt = Instant.now(); issue.closeReason = reason; issue.duplicateOf = duplicateOf;
        issues.saveAndFlush(issue); audit.record(user, "ISSUE_CLOSED", "ISSUE", id, "关闭工单：" + reasonLabel(reason, duplicateOfId)); mailEvents.publish(new MailEvent(MailEventType.ISSUE_STATE_CHANGED, issue.author, "工单已关闭 #" + id, "你的反馈已关闭（" + reasonLabel(reason, duplicateOfId) + "）。", id, null)); subscriptionNotifications.publish(MailEventType.ISSUE_STATE_CHANGED, issue, user, issue.author, "订阅的工单已关闭 #" + id, "你订阅的反馈已关闭（" + reasonLabel(reason, duplicateOfId) + "）。", null); idempotency.save(user, "ISSUE_CLOSE", key, hash, "ISSUE", id.toString());
        return detail(id);
    }

    @PostMapping("/{id}/reopen")
    @Transactional
    public IssueDetail reopen(@PathVariable Long id, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Issue issue = findIssue(id); User user = currentUser();
        authorization.requireStaff(user);
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("ISSUE_REOPEN", id.toString());
        if (idempotency.find(user, "ISSUE_REOPEN", key, hash) != null) return detail(id);
        if (issue.state == Issue.State.OPEN) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单已经是打开状态");
        issue.state = Issue.State.OPEN; issue.closedBy = null; issue.closedAt = null; issue.closeReason = null; issue.duplicateOf = null; issues.saveAndFlush(issue); audit.record(user, "ISSUE_REOPENED", "ISSUE", id, "重新打开工单"); mailEvents.publish(new MailEvent(MailEventType.ISSUE_STATE_CHANGED, issue.author, "工单已重新打开 #" + id, "你的反馈已重新打开。", id, null)); subscriptionNotifications.publish(MailEventType.ISSUE_STATE_CHANGED, issue, user, issue.author, "订阅的工单已重新打开 #" + id, "你订阅的反馈已重新打开。", null); idempotency.save(user, "ISSUE_REOPEN", key, hash, "ISSUE", id.toString());
        return detail(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CommentView comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String body = request.body() == null ? "" : request.body().trim();
        if (body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        Issue issue = findIssue(id); User user = currentUser(); String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("COMMENT_CREATE", id.toString(), body);
        IdempotencyRecord existing = idempotency.find(user, "COMMENT_CREATE", key, hash);
        if (existing != null) return CommentView.of(comments.findById(Long.valueOf(existing.resourceId)).filter(comment -> !comment.deleted).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "幂等请求对应的评论不存在")));
        IssueComment comment = new IssueComment(); comment.issue = issue; comment.author = user; comment.body = body;
        CommentView view = CommentView.of(comments.saveAndFlush(comment)); subscriptions.subscribe(issue.id, user.id); audit.record(user, "COMMENT_CREATED", "COMMENT", comment.id, "创建评论"); mailEvents.publish(new MailEvent(MailEventType.COMMENT_CREATED, issue.author, "工单收到新评论 #" + id, "你的反馈收到了一条新评论。", id, comment.id)); subscriptionNotifications.publish(MailEventType.COMMENT_CREATED, issue, user, issue.author, "订阅的工单收到新评论 #" + id, "你订阅的反馈收到了一条新评论。", comment.id); mentions.publish(comment.body, user, issue.id, comment.id); idempotency.save(user, "COMMENT_CREATE", key, hash, "COMMENT", comment.id.toString()); return view;
    }

    @PatchMapping("/{issueId}/comments/{commentId}")
    @Transactional
    public CommentView editComment(@PathVariable Long issueId, @PathVariable Long commentId, @Valid @RequestBody CommentRequest request, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IssueComment comment = findComment(issueId, commentId); User user = currentUser();
        requireCommentOwnerOrAdmin(comment, user);
        String body = request.body() == null ? "" : request.body().trim();
        if (body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("COMMENT_EDIT", issueId.toString(), commentId.toString(), body);
        IdempotencyRecord existing = idempotency.find(user, "COMMENT_EDIT", key, hash);
        if (existing != null) return CommentView.of(findComment(issueId, commentId));
        comment.body = body;
        CommentView view = CommentView.of(comments.saveAndFlush(comment)); audit.record(user, "COMMENT_EDITED", "COMMENT", comment.id, "编辑评论"); idempotency.save(user, "COMMENT_EDIT", key, hash, "COMMENT", comment.id.toString()); return view;
    }

    @DeleteMapping("/{issueId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteComment(@PathVariable Long issueId, @PathVariable Long commentId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        User user = currentUser();
        String key = idempotency.normalizeKey(idempotencyKey); String hash = idempotency.hash("COMMENT_DELETE", issueId.toString(), commentId.toString());
        if (idempotency.find(user, "COMMENT_DELETE", key, hash) != null) return;
        IssueComment comment = findComment(issueId, commentId);
        requireCommentOwnerOrAdmin(comment, user);
        comment.deleted = true;
        comments.saveAndFlush(comment); audit.record(user, "COMMENT_DELETED", "COMMENT", comment.id, "删除评论"); idempotency.save(user, "COMMENT_DELETE", key, hash, "COMMENT", comment.id.toString());
    }

    private Issue findIssue(Long id) { return issues.findById(id).filter(i -> !i.deleted).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在")); }
    private IssueComment findComment(Long issueId, Long commentId) {
        return comments.findById(commentId).filter(comment -> !comment.deleted && comment.issue.id.equals(issueId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评论不存在"));
    }
    private void requireCommentOwnerOrAdmin(IssueComment comment, User user) {
        if (!comment.author.id.equals(user.id) && user.role != User.Role.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有评论作者或管理员可以执行此操作");
    }
    private void requireIssueTagManager(Issue issue, User user) {
        if (!authorization.canManageIssue(issue, user))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工单作者或工作人员可以修改标签");
    }
    private User currentUser() { return identity.require(); }

    /** 搜索框内联过滤条件优先，其次为显式查询参数；两者都为空则返回 {@code null}。 */
    private static String mergeFilter(String inline, String explicit) {
        if (inline != null && !inline.isBlank()) return inline.trim();
        return explicit == null || explicit.isBlank() ? null : explicit.trim();
    }

    private String reasonLabel(Issue.CloseReason reason, Long duplicateOfId) {
        return switch (reason) { case COMPLETED -> "已完成"; case NOT_PLANNED -> "非计划"; case MEANINGLESS -> "无意义"; case DUPLICATE -> "重复于 #" + duplicateOfId; };
    }

    private SubscriptionView subscriptionView(Long issueId, Long userId) {
        return new SubscriptionView(subscriptions.existsByIssue_IdAndUser_Id(issueId, userId), subscriptions.countByIssue_Id(issueId));
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 不能超过 128 个字符");
        return normalized;
    }

    private String hashRequest(String title, String body, String tag, String category, IssuePollController.CreatePollRequest poll) {
        try {
            String pollValue = poll == null ? "" : "\n" + poll.question() + "\n" + poll.multipleChoice() + "\n" + String.join("\n", poll.options());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((title + "\n" + body + "\n" + tag + "\n" + category + pollValue).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append("%02x".formatted(value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record CreateIssueRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 20000) String body, @Size(max = 32) String tag, @NotBlank @Size(max = 32) String category, @jakarta.validation.Valid IssuePollController.CreatePollRequest poll) {}
    public record IssueCounts(long all, long open, long closed) {}
    public record EditIssueRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 20000) String body) {}
    public record StateRequest(@jakarta.validation.constraints.NotNull Issue.State state) {}
    public record CloseRequest(Issue.CloseReason reason, Long duplicateOfIssueId) {}
    public record PinRequest(boolean pinned) {}
    public record CommentRequest(@NotBlank @Size(max = 10000) String body) {}
    public record SubscriptionView(boolean subscribed, long subscriberCount) {}
    public record TagBindingRequest(Long tagId) {}
    public record LinkRequest(@jakarta.validation.constraints.NotNull Long targetIssueId, @Size(max = 24) String relationType) {}
    public record IssueView(Long id, String title, String summary, String state, boolean pinned, String author, String authorAvatarUrl, String category, List<String> tags, long pollParticipants, Instant createdAt, Instant updatedAt) { static IssueView of(Issue i, long pollParticipants) { return new IssueView(i.id, i.title, i.body.length() > 160 ? i.body.substring(0, 160) : i.body, i.state.name(), i.pinned, i.author.username, i.author.avatarUrl, i.category, i.tags.stream().map(tag -> tag.name).toList(), pollParticipants, i.createdAt, i.updatedAt); } }
    public record IssueDetail(Long id, String title, String body, String state, boolean pinned, String author, String authorAvatarUrl, String category, List<String> tags, List<Long> tagIds, Instant createdAt, Instant updatedAt, List<CommentView> comments, Long parentIssueId, String parentIssueTitle, String closeReason, Long duplicateOfIssueId, String duplicateOfIssueTitle) { static IssueDetail of(Issue i, List<CommentView> c) { return new IssueDetail(i.id, i.title, i.body, i.state.name(), i.pinned, i.author.username, i.author.avatarUrl, i.category, i.tags.stream().map(tag -> tag.name).toList(), i.tags.stream().map(tag -> tag.id).toList(), i.createdAt, i.updatedAt, c, i.parent == null ? null : i.parent.id, i.parent == null ? null : i.parent.title, i.closeReason == null ? null : i.closeReason.name(), i.duplicateOf == null ? null : i.duplicateOf.id, i.duplicateOf == null ? null : i.duplicateOf.title); } }
    public record CommentView(Long id, String author, String authorAvatarUrl, String body, Instant createdAt) { static CommentView of(IssueComment c) { return new CommentView(c.id, c.author.username, c.author.avatarUrl, c.body, c.createdAt); } }
    public record CreateSubIssueRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 20000) String body, @Size(max = 32) String tag, @Size(max = 32) String category) {}
    public record SubIssueView(Long id, String title, String state, String author, String authorAvatarUrl, Instant createdAt, Instant updatedAt) { static SubIssueView of(Issue issue) { return new SubIssueView(issue.id, issue.title, issue.state.name(), issue.author.username, issue.author.avatarUrl, issue.createdAt, issue.updatedAt); } }
    public record SubIssueList(List<SubIssueView> items, long total, long completed) {}
    public enum LinkDirection { OUTGOING, INCOMING }
    public record LinkView(Long issueId, Long targetIssueId, String relationType, LinkDirection direction, String targetTitle) {
        static LinkView of(IssueLink link, Long currentId) {
            boolean outgoing = link.source.id.equals(currentId);
            return new LinkView(
                    currentId,
                    outgoing ? link.target.id : link.source.id,
                    link.id.relationType,
                    outgoing ? LinkDirection.OUTGOING : LinkDirection.INCOMING,
                    outgoing ? link.target.title : link.source.title
            );
        }
    }
}
