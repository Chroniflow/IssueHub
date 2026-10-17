package com.simpissuehub.api;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.IssuePollService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/poll")
public class IssuePollController {
    private final IssueRepository issues;
    private final IssuePollService polls;
    private final CurrentUserService identity;
    private final AuditService audit;
    private final AuthorizationService authorization;

    public IssuePollController(IssueRepository issues, IssuePollService polls, CurrentUserService identity, AuditService audit, AuthorizationService authorization) {
        this.issues = issues;
        this.polls = polls;
        this.identity = identity;
        this.audit = audit;
        this.authorization = authorization;
    }

    @GetMapping
    public IssuePollService.PollSnapshot get(@PathVariable Long issueId) {
        findIssue(issueId);
        return polls.get(issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuePollService.PollSnapshot create(@PathVariable Long issueId, @Valid @RequestBody CreatePollRequest request) {
        Issue issue = findIssue(issueId);
        User user = identity.require();
        authorization.requireStaff(user);
        var result = polls.create(issue, user, request.question(), request.multipleChoice(), request.options());
        audit.record(user, "ISSUE_POLL_CREATED", "ISSUE_POLL", result.id(), "创建工单投票");
        return result;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long issueId) {
        Issue issue = findIssue(issueId);
        User user = identity.require();
        polls.delete(issueId, user);
        audit.record(user, "ISSUE_POLL_DELETED", "ISSUE", issueId, "删除未开始的工单投票");
    }

    @GetMapping("/ballot")
    public BallotView ballot(@PathVariable Long issueId) {
        findIssue(issueId);
        return new BallotView(polls.selections(issueId, identity.require().id));
    }

    @PutMapping("/ballot")
    public IssuePollService.PollSnapshot vote(@PathVariable Long issueId, @Valid @RequestBody VoteRequest request) {
        findIssue(issueId);
        User user = identity.require();
        var result = polls.vote(issueId, user, request.optionIds());
        audit.record(user, "ISSUE_POLL_VOTED", "ISSUE_POLL", result.id(), "提交工单投票");
        return result;
    }

    @DeleteMapping("/ballot")
    public IssuePollService.PollSnapshot withdraw(@PathVariable Long issueId) {
        findIssue(issueId);
        User user = identity.require();
        var result = polls.withdraw(issueId, user);
        audit.record(user, "ISSUE_POLL_WITHDRAWN", "ISSUE_POLL", result.id(), "撤销工单投票");
        return result;
    }

    private Issue findIssue(Long issueId) {
        return issues.findById(issueId).filter(issue -> !issue.deleted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在"));
    }

    public record CreatePollRequest(
            @NotBlank @Size(max = 200) String question,
            boolean multipleChoice,
            @NotNull @Size(min = 2, max = 10) List<@NotBlank @Size(max = 100) String> options
    ) {}
    public record VoteRequest(@NotNull @Size(min = 1, max = 10) List<@NotNull Long> optionIds) {}
    public record BallotView(List<Long> optionIds) {}
}
