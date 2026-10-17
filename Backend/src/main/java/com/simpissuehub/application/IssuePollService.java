package com.simpissuehub.application;

import com.simpissuehub.domain.*;
import com.simpissuehub.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IssuePollService {
    private final IssuePollRepository polls;
    private final IssuePollBallotRepository ballots;
    private final IssuePollChoiceRepository choices;

    public IssuePollService(IssuePollRepository polls, IssuePollBallotRepository ballots, IssuePollChoiceRepository choices) {
        this.polls = polls;
        this.ballots = ballots;
        this.choices = choices;
    }

    @Transactional
    public PollSnapshot create(Issue issue, String question, boolean multipleChoice, List<String> optionTexts) {
        return create(issue, issue.author, question, multipleChoice, optionTexts);
    }

    @Transactional
    public PollSnapshot create(Issue issue, User creator, String question, boolean multipleChoice, List<String> optionTexts) {
        if (polls.findByIssue_Id(issue.id).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "该工单已有投票");
        List<String> normalized = optionTexts.stream().map(String::trim).toList();
        if (normalized.stream().anyMatch(String::isBlank)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票选项不能为空");
        if (normalized.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != normalized.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票选项不能重复");

        IssuePoll poll = new IssuePoll();
        poll.issue = issue;
        poll.creator = creator;
        poll.question = question.trim();
        poll.multipleChoice = multipleChoice;
        for (int index = 0; index < normalized.size(); index++) {
            IssuePollOption option = new IssuePollOption();
            option.poll = poll;
            option.text = normalized.get(index);
            option.position = (short) index;
            poll.options.add(option);
        }
        return snapshot(polls.saveAndFlush(poll));
    }

    @Transactional(readOnly = true)
    public PollSnapshot get(Long issueId) {
        return snapshot(requirePoll(issueId));
    }

    @Transactional
    public PollSnapshot createStandalone(User creator, String question, boolean multipleChoice, List<String> optionTexts) {
        return createPoll(null, creator, question, multipleChoice, optionTexts);
    }

    @Transactional(readOnly = true)
    public List<CommunityPollSnapshot> listStandalone() {
        return polls.findByIssueIsNullOrderByCreatedAtDescIdDesc().stream()
                .map(poll -> new CommunityPollSnapshot(snapshot(poll), poll.creator.username, poll.creator.id, poll.createdAt)).toList();
    }

    @Transactional(readOnly = true)
    public PollSnapshot getById(Long pollId) { return snapshot(requireStandaloneById(pollId)); }

    @Transactional(readOnly = true)
    public List<Long> selectionsById(Long pollId, Long userId) {
        IssuePoll poll = requireStandaloneById(pollId);
        return ballots.findByPoll_IdAndUser_Id(poll.id, userId).map(ballot -> choices.findOptionIdsByBallotId(ballot.id)).orElseGet(List::of);
    }

    @Transactional
    public PollSnapshot voteById(Long pollId, User user, List<Long> optionIds) { return voteOnPoll(requireStandaloneByIdForUpdate(pollId), user, optionIds); }

    @Transactional
    public PollSnapshot withdrawById(Long pollId, User user) {
        IssuePoll poll = requireStandaloneByIdForUpdate(pollId);
        ballots.findLocked(poll.id, user.id).ifPresent(ballots::delete); ballots.flush(); return snapshot(poll);
    }

    @Transactional
    public void deleteById(Long pollId, User user) {
        IssuePoll poll = requireStandaloneById(pollId);
        if (!poll.creator.id.equals(user.id) && user.role != User.Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有发起者或管理员可以删除投票");
        if (ballots.countByPoll_Id(poll.id) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有用户参与，不能删除投票");
        polls.delete(poll);
    }

    @Transactional(readOnly = true)
    public List<Long> selections(Long issueId, Long userId) {
        IssuePoll poll = requirePoll(issueId);
        return ballots.findByPoll_IdAndUser_Id(poll.id, userId).map(ballot -> choices.findOptionIdsByBallotId(ballot.id)).orElseGet(List::of);
    }

    @Transactional
    public PollSnapshot vote(Long issueId, User user, List<Long> requestedOptionIds) {
        return voteOnPoll(requirePollForUpdate(issueId), user, requestedOptionIds);
    }

    private PollSnapshot voteOnPoll(IssuePoll poll, User user, List<Long> requestedOptionIds) {
        List<Long> optionIds = requestedOptionIds.stream().distinct().toList();
        if (!poll.multipleChoice && optionIds.size() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该投票只能选择一个选项");
        if (optionIds.isEmpty() || optionIds.size() > poll.options.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的投票选项");

        Map<Long, IssuePollOption> available = poll.options.stream().collect(Collectors.toMap(option -> option.id, Function.identity()));
        if (!available.keySet().containsAll(optionIds)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票选项不属于该投票");

        ballots.ensureBallot(poll.id, user.id);
        IssuePollBallot ballot = ballots.findLocked(poll.id, user.id)
                .orElseThrow(() -> new IllegalStateException("选票创建失败"));
        choices.deleteByBallotId(ballot.id);
        choices.flush();
        optionIds.forEach(optionId -> choices.addChoice(ballot.id, optionId));
        ballot.updatedAt = Instant.now();
        return snapshot(poll);
    }

    @Transactional
    public PollSnapshot withdraw(Long issueId, User user) {
        IssuePoll poll = requirePollForUpdate(issueId);
        ballots.findLocked(poll.id, user.id).ifPresent(ballots::delete);
        ballots.flush();
        return snapshot(poll);
    }

    @Transactional
    public void delete(Long issueId, User user) {
        IssuePoll poll = requirePoll(issueId);
        if (!poll.creator.id.equals(user.id) && user.role == User.Role.USER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有发起者或工作人员可以删除投票");
        if (ballots.countByPoll_Id(poll.id) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有用户参与，不能删除投票");
        polls.delete(poll);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> participantCounts(Collection<Long> issueIds) {
        if (issueIds.isEmpty()) return Map.of();
        return ballots.countParticipantsByIssueIds(issueIds).stream().collect(Collectors.toMap(
                IssuePollBallotRepository.IssueParticipantCount::getIssueId,
                IssuePollBallotRepository.IssueParticipantCount::getParticipantCount
        ));
    }

    private IssuePoll requirePoll(Long issueId) {
        return polls.findByIssue_Id(issueId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该工单没有投票"));
    }

    private IssuePoll requirePollForUpdate(Long issueId) {
        return polls.findLockedByIssue_Id(issueId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该工单没有投票"));
    }

    /**
     * 独立投票接口（/api/v1/polls）只允许操作未绑定工单的投票；
     * 工单投票仍通过 /api/v1/issues/{id}/poll 访问。对非独立投票统一返回 404，避免泄露存在性。
     */
    private IssuePoll requireStandaloneById(Long pollId) {
        IssuePoll poll = polls.findWithOptionsById(pollId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投票不存在"));
        if (poll.issue != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "投票不存在");
        return poll;
    }

    private IssuePoll requireStandaloneByIdForUpdate(Long pollId) {
        IssuePoll poll = polls.findLockedWithOptionsById(pollId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投票不存在"));
        if (poll.issue != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "投票不存在");
        return poll;
    }

    private PollSnapshot createPoll(Issue issue, User creator, String question, boolean multipleChoice, List<String> optionTexts) {
        List<String> normalized = optionTexts.stream().map(String::trim).toList();
        if (question == null || question.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票问题不能为空");
        if (normalized.size() < 2 || normalized.size() > 10 || normalized.stream().anyMatch(String::isBlank)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票选项必须为 2-10 个且不能为空");
        if (normalized.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != normalized.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票选项不能重复");
        IssuePoll poll = new IssuePoll(); poll.issue = issue; poll.creator = creator; poll.question = question.trim(); poll.multipleChoice = multipleChoice;
        for (int index = 0; index < normalized.size(); index++) { IssuePollOption option = new IssuePollOption(); option.poll = poll; option.text = normalized.get(index); option.position = (short) index; poll.options.add(option); }
        return snapshot(polls.saveAndFlush(poll));
    }

    private PollSnapshot snapshot(IssuePoll poll) {
        Map<Long, Long> counts = choices.countByPollId(poll.id).stream().collect(Collectors.toMap(
                IssuePollChoiceRepository.OptionVoteCount::getOptionId,
                IssuePollChoiceRepository.OptionVoteCount::getVoteCount
        ));
        List<OptionSnapshot> options = poll.options.stream()
                .map(option -> new OptionSnapshot(option.id, option.text, counts.getOrDefault(option.id, 0L)))
                .toList();
        return new PollSnapshot(poll.id, poll.creator.id, poll.question, poll.multipleChoice, ballots.countByPoll_Id(poll.id), options);
    }

    public record PollSnapshot(Long id, Long creatorId, String question, boolean multipleChoice, long participantCount, List<OptionSnapshot> options) {}
    public record CommunityPollSnapshot(PollSnapshot poll, String creator, Long creatorId, Instant createdAt) {}
    public record OptionSnapshot(Long id, String text, long voteCount) {}
}
