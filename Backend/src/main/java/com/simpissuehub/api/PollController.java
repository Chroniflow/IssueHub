package com.simpissuehub.api;

import com.simpissuehub.application.AuditService;
import com.simpissuehub.application.CurrentUserService;
import com.simpissuehub.application.IssuePollService;
import com.simpissuehub.application.AuthorizationService;
import com.simpissuehub.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/polls")
public class PollController {
    private final IssuePollService polls;
    private final CurrentUserService identity;
    private final AuditService audit;
    private final AuthorizationService authorization;

    public PollController(IssuePollService polls, CurrentUserService identity, AuditService audit, AuthorizationService authorization) {
        this.polls = polls; this.identity = identity; this.audit = audit; this.authorization = authorization;
    }

    @GetMapping
    public List<IssuePollService.CommunityPollSnapshot> list() { return polls.listStandalone(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuePollService.PollSnapshot create(@Valid @RequestBody CreatePollRequest request) {
        User user = identity.require();
        authorization.requireStaff(user);
        var result = polls.createStandalone(user, request.question(), request.multipleChoice(), request.options());
        audit.record(user, "POLL_CREATED", "POLL", result.id(), "创建独立投票"); return result;
    }

    @GetMapping("/{pollId}")
    public IssuePollService.PollSnapshot get(@PathVariable Long pollId) { return polls.getById(pollId); }

    @GetMapping("/{pollId}/ballot")
    public BallotView ballot(@PathVariable Long pollId) { return new BallotView(polls.selectionsById(pollId, identity.require().id)); }

    @PutMapping("/{pollId}/ballot")
    public IssuePollService.PollSnapshot vote(@PathVariable Long pollId, @Valid @RequestBody VoteRequest request) {
        User user = identity.require(); var result = polls.voteById(pollId, user, request.optionIds());
        audit.record(user, "POLL_VOTED", "POLL", pollId, "提交独立投票"); return result;
    }

    @DeleteMapping("/{pollId}/ballot")
    public IssuePollService.PollSnapshot withdraw(@PathVariable Long pollId) {
        User user = identity.require(); var result = polls.withdrawById(pollId, user);
        audit.record(user, "POLL_WITHDRAWN", "POLL", pollId, "撤销独立投票"); return result;
    }

    @DeleteMapping("/{pollId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long pollId) { User user = identity.require(); polls.deleteById(pollId, user); audit.record(user, "POLL_DELETED", "POLL", pollId, "删除独立投票"); }

    public record CreatePollRequest(@NotBlank @Size(max=200) String question, boolean multipleChoice,
                                    @NotNull @Size(min=2,max=10) List<@NotBlank @Size(max=100) String> options) {}
    public record VoteRequest(@NotNull @Size(min=1,max=10) List<@NotNull Long> optionIds) {}
    public record BallotView(List<Long> optionIds) {}
}
