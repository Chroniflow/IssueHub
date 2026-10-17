package com.simpissuehub.cli;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IssueRepository;
import com.simpissuehub.repository.TagRepository;
import com.simpissuehub.repository.UserRepository;
import com.simpissuehub.repository.IssueCommentRepository;
import com.simpissuehub.repository.AuditLogRepository;
import com.simpissuehub.application.AuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.simpissuehub.domain.Issue;
import com.simpissuehub.application.mail.MailDispatchService;
import com.simpissuehub.application.RuntimeConfigurationService;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TuiCommandExecutor {
    private final String version;
    private final Environment environment;
    private final UserRepository users;
    private final TagRepository tags;
    private final IssueRepository issues;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final IssueCommentRepository comments;
    private final AuditLogRepository auditLogs;
    private final MailDispatchService mailDispatch;
    private final RuntimeConfigurationService runtimeConfiguration;
    private User authenticatedAdmin;

    public TuiCommandExecutor(@Value("${app.version:0.1.0-SNAPSHOT}") String version, Environment environment,
                               UserRepository users, TagRepository tags, IssueRepository issues, PasswordEncoder passwordEncoder, AuditService audit,
                               IssueCommentRepository comments, AuditLogRepository auditLogs, MailDispatchService mailDispatch, RuntimeConfigurationService runtimeConfiguration) {
        this.version = version; this.environment = environment; this.users = users; this.tags = tags; this.issues = issues; this.passwordEncoder = passwordEncoder; this.audit = audit; this.comments = comments; this.auditLogs = auditLogs; this.mailDispatch = mailDispatch; this.runtimeConfiguration = runtimeConfiguration;
    }

    @Transactional
    public TuiCommandResult execute(TuiCommand command) {
        if (command.name().isBlank()) return TuiCommandResult.ok("");
        if (command.is("HELP") || command.is("?")) return TuiCommandResult.ok("可用命令：AUTH LOGIN <user> <password>、AUTH LOGOUT、AUTH STATUS、AUTH ADD <user> <email> <password>、AUTH DELETE <user> --force、AUTH EMAIL SET <user> <email>、AUTH PASSWD <user> <password>、GRANT <user> WITH PERMISSION USER|STAFF|ADMIN、CONFIG <node> <value>、HELP、VERSION、SHOW USERS、SHOW TAGS、SHOW ISSUES、SHOW CONFIG、STATS、ISSUE SET <id> STATE OPEN|CLOSED、ISSUE SET <id> AUTHOR <user>、ISSUE DELETE <id> --force、TAG ADD <tag>、TAG DELETE <tag> --force、TAG MV <oldTag> <newTag>、EXIT");
        if (command.is("VERSION")) return TuiCommandResult.ok("SimpIssueHub TUI " + version);
        if (command.is("EXIT") || command.is("QUIT")) return TuiCommandResult.exit();
        if (command.is("AUTH")) return auth(command.arguments());
        if (command.is("SHOW")) return show(command.arguments());
        if (command.is("ISSUE")) return issue(command.arguments());
        if (command.is("TAG")) return tag(command.arguments());
        if (command.is("STATS")) return stats(command.arguments());
        if (command.is("GRANT")) return grant(command.arguments());
        if (command.is("CONFIG")) return configure(command.arguments());
        return TuiCommandResult.error("未知命令或参数。输入 HELP 查看用法。");
    }

    private TuiCommandResult configure(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 2) return TuiCommandResult.error("用法：CONFIG <app.cors.allowed-origins|sa-token.timeout|sa-token.active-timeout> <value>");
        String node = arguments.get(0).toLowerCase(Locale.ROOT);
        String value = arguments.get(1).trim();
        if (value.isEmpty()) return TuiCommandResult.error("配置值不能为空。");
        if (node.equals("sa-token.timeout") || node.equals("sa-token.active-timeout")) {
            try {
                long seconds = Long.parseLong(value);
                if (seconds <= 0 || seconds > 31_536_000L) return TuiCommandResult.error("会话时长必须为 1-31536000 秒。");
            } catch (NumberFormatException exception) {
                return TuiCommandResult.error("会话时长必须是整数秒数。");
            }
        } else if (node.equals("app.cors.allowed-origins")) {
            String[] origins = value.split(",");
            if (origins.length == 0 || origins.length > 20 || java.util.Arrays.stream(origins).anyMatch(origin -> !origin.trim().matches("https?://[^\\s,]+"))) {
                return TuiCommandResult.error("CORS 来源必须是逗号分隔的 http/https 地址。");
            }
        } else {
            return TuiCommandResult.error("不允许修改该配置。可修改：app.cors.allowed-origins、sa-token.timeout、sa-token.active-timeout。");
        }
        String previous = runtimeConfiguration.value(node);
        runtimeConfiguration.update(node, value);
        audit.record(authenticatedAdmin, "CONFIG_CHANGED", "CONFIG", node, "TUI 修改运行时配置");
        return TuiCommandResult.ok("配置已更新：" + node + " = " + value + "（原值：" + previous + "）");
    }

    private TuiCommandResult grant(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 4 || !arguments.get(1).equalsIgnoreCase("WITH") || !arguments.get(2).equalsIgnoreCase("PERMISSION")) {
            return TuiCommandResult.error("用法：GRANT <user> WITH PERMISSION USER|STAFF|ADMIN");
        }
        User target = users.findByUsernameOrEmail(arguments.get(0), arguments.get(0)).orElse(null);
        if (target == null) return TuiCommandResult.error("用户不存在：" + arguments.get(0));
        if (!target.enabled) return TuiCommandResult.error("目标用户已停用：" + target.username);
        User.Role role;
        try { role = User.Role.valueOf(arguments.get(3).toUpperCase()); }
        catch (IllegalArgumentException exception) { return TuiCommandResult.error("权限必须是 USER、STAFF 或 ADMIN。"); }
        if (target.role == role) return TuiCommandResult.error("用户已经是 " + role + " 权限。");
        User.Role previous = target.role;
        target.role = role;
        users.saveAndFlush(target);
        audit.record(authenticatedAdmin, "USER_ROLE_CHANGED", "USER", target.id, "TUI 将权限从 " + previous + " 变更为 " + role);
        return TuiCommandResult.ok("用户 " + target.username + " 权限已更新为 " + role + "。");
    }

    @Transactional
    private TuiCommandResult issue(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() >= 1 && arguments.get(0).equalsIgnoreCase("DELETE")) return deleteIssue(arguments);
        if (arguments.size() == 4 && arguments.get(0).equalsIgnoreCase("SET") && arguments.get(2).equalsIgnoreCase("AUTHOR")) return setAuthor(arguments);
        if (arguments.size() != 4 || !arguments.get(0).equalsIgnoreCase("SET") || !arguments.get(2).equalsIgnoreCase("STATE")) {
            return TuiCommandResult.error("用法：ISSUE SET <issueId> STATE OPEN|CLOSED | ISSUE SET <issueId> AUTHOR <user> | ISSUE DELETE <issueId> --force");
        }
        final long issueId;
        try { issueId = Long.parseLong(arguments.get(1)); }
        catch (NumberFormatException exception) { return TuiCommandResult.error("issueId 必须是正整数。"); }
        if (issueId <= 0) return TuiCommandResult.error("issueId 必须是正整数。");
        Issue issue = issues.findById(issueId).filter(candidate -> !candidate.deleted).orElse(null);
        if (issue == null) return TuiCommandResult.error("工单不存在：" + issueId);
        Issue.State state;
        try { state = Issue.State.valueOf(arguments.get(3).toUpperCase()); }
        catch (IllegalArgumentException exception) { return TuiCommandResult.error("状态必须是 OPEN 或 CLOSED。"); }
        if (issue.state == state) return TuiCommandResult.error("工单已经是 " + state + " 状态。");
        issue.state = state;
        if (state == Issue.State.CLOSED) { issue.closedBy = authenticatedAdmin; issue.closedAt = java.time.Instant.now(); issue.closeReason = Issue.CloseReason.COMPLETED; issue.duplicateOf = null; }
        else { issue.closedBy = null; issue.closedAt = null; issue.closeReason = null; issue.duplicateOf = null; }
        issues.saveAndFlush(issue);
        audit.record(authenticatedAdmin, "ISSUE_STATE_CHANGED", "ISSUE", issue.id, "TUI 设置状态为 " + state);
        return TuiCommandResult.ok("工单 #" + issue.id + " 状态已更新为 " + state + "。");
    }

    @Transactional
    private TuiCommandResult deleteIssue(List<String> arguments) {
        if (arguments.size() != 3 || !arguments.get(2).equalsIgnoreCase("--force")) {
            return TuiCommandResult.error("危险操作需要显式确认：ISSUE DELETE <issueId> --force");
        }
        final long issueId;
        try { issueId = Long.parseLong(arguments.get(1)); }
        catch (NumberFormatException exception) { return TuiCommandResult.error("issueId 必须是正整数。"); }
        if (issueId <= 0) return TuiCommandResult.error("issueId 必须是正整数。");
        Issue issue = issues.findById(issueId).filter(candidate -> !candidate.deleted).orElse(null);
        if (issue == null) return TuiCommandResult.error("工单不存在：" + issueId);
        issue.deleted = true;
        issues.saveAndFlush(issue);
        audit.record(authenticatedAdmin, "ISSUE_DELETED", "ISSUE", issue.id, "TUI 软删除工单");
        return TuiCommandResult.ok("工单 #" + issue.id + " 已软删除。");
    }

    @Transactional
    private TuiCommandResult setAuthor(List<String> arguments) {
        final long issueId;
        try { issueId = Long.parseLong(arguments.get(1)); }
        catch (NumberFormatException exception) { return TuiCommandResult.error("issueId 必须是正整数。"); }
        if (issueId <= 0) return TuiCommandResult.error("issueId 必须是正整数。");
        Issue issue = issues.findById(issueId).filter(candidate -> !candidate.deleted).orElse(null);
        if (issue == null) return TuiCommandResult.error("工单不存在：" + issueId);
        User newAuthor = users.findByUsernameOrEmail(arguments.get(3), arguments.get(3)).filter(candidate -> candidate.enabled).orElse(null);
        if (newAuthor == null) return TuiCommandResult.error("目标用户不存在或已停用：" + arguments.get(3));
        if (issue.author.id.equals(newAuthor.id)) return TuiCommandResult.error("工单作者未发生变化。");
        String previousAuthor = issue.author.username;
        issue.author = newAuthor;
        issues.saveAndFlush(issue);
        audit.record(authenticatedAdmin, "ISSUE_AUTHOR_CHANGED", "ISSUE", issue.id, "TUI 将作者从 " + previousAuthor + " 变更为 " + newAuthor.username);
        return TuiCommandResult.ok("工单 #" + issue.id + " 作者已更新为 " + newAuthor.username + "。");
    }

    private TuiCommandResult tag(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.isEmpty()) return TuiCommandResult.error("用法：TAG ADD <tag> | TAG DELETE <tag> --force | TAG MV <oldTag> <newTag>");
        return switch (arguments.get(0).toUpperCase()) {
            case "ADD" -> addTag(arguments);
            case "DELETE" -> deleteTag(arguments);
            case "MV" -> moveTag(arguments);
            default -> TuiCommandResult.error("未知 TAG 操作：" + arguments.get(0));
        };
    }

    private TuiCommandResult stats(List<String> arguments) {
        if (!arguments.isEmpty()) return TuiCommandResult.error("用法：STATS");
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        MailDispatchService.DeliveryStats mail = mailDispatch.stats();
        String output = "METRIC\tVALUE\n" +
                "users.total\t" + users.count() + "\n" +
                "users.enabled\t" + users.countByEnabledTrue() + "\n" +
                "issues.total\t" + issues.countByDeletedFalse() + "\n" +
                "issues.open\t" + issues.countByStateAndDeletedFalse(Issue.State.OPEN) + "\n" +
                "issues.closed\t" + issues.countByStateAndDeletedFalse(Issue.State.CLOSED) + "\n" +
                "tags.total\t" + tags.count() + "\n" +
                "comments.total\t" + comments.countByDeletedFalse() + "\n" +
                "audit_logs.total\t" + auditLogs.count() + "\n" +
                "mail.sent\t" + mail.sent() + "\n" +
                "mail.failed\t" + mail.failed() + "\n" +
                "mail.dead_letters\t" + mail.deadLetters();
        return TuiCommandResult.ok(output);
    }

    private TuiCommandResult addTag(List<String> arguments) {
        if (arguments.size() != 2) return TuiCommandResult.error("用法：TAG ADD <tag>");
        String name = normalizeTagName(arguments.get(1));
        if (name == null) return TuiCommandResult.error("标签名必须是 1-32 个字符。");
        if (tags.findByName(name).isPresent()) return TuiCommandResult.error("标签已存在：" + name);
        com.simpissuehub.domain.Tag tag = new com.simpissuehub.domain.Tag();
        tag.name = name; tag.color = "#DDEBE3";
        tags.saveAndFlush(tag);
        audit.record(authenticatedAdmin, "TAG_CREATED", "TAG", tag.id, "TUI 创建标签 " + name);
        return TuiCommandResult.ok("标签已创建：" + name);
    }

    private TuiCommandResult deleteTag(List<String> arguments) {
        if (arguments.size() != 3 || !arguments.get(2).equalsIgnoreCase("--force")) return TuiCommandResult.error("危险操作需要显式确认：TAG DELETE <tag> --force");
        String name = normalizeTagName(arguments.get(1));
        if (name == null) return TuiCommandResult.error("标签名必须是 1-32 个字符。");
        com.simpissuehub.domain.Tag tag = tags.findByName(name).orElse(null);
        if (tag == null) return TuiCommandResult.error("标签不存在：" + name);
        if (tags.countIssuesByTagId(tag.id) > 0) return TuiCommandResult.error("标签仍被工单使用，无法删除。");
        tags.delete(tag); tags.flush();
        audit.record(authenticatedAdmin, "TAG_DELETED", "TAG", tag.id, "TUI 删除标签 " + name);
        return TuiCommandResult.ok("标签已删除：" + name);
    }

    private TuiCommandResult moveTag(List<String> arguments) {
        if (arguments.size() != 3) return TuiCommandResult.error("用法：TAG MV <oldTag> <newTag>");
        String oldName = normalizeTagName(arguments.get(1));
        String newName = normalizeTagName(arguments.get(2));
        if (oldName == null || newName == null) return TuiCommandResult.error("标签名必须是 1-32 个字符。");
        com.simpissuehub.domain.Tag tag = tags.findByName(oldName).orElse(null);
        if (tag == null) return TuiCommandResult.error("标签不存在：" + oldName);
        if (tags.findByName(newName).isPresent()) return TuiCommandResult.error("目标标签已存在：" + newName);
        tag.name = newName; tags.saveAndFlush(tag);
        audit.record(authenticatedAdmin, "TAG_RENAMED", "TAG", tag.id, "TUI 将标签从 " + oldName + " 改名为 " + newName);
        return TuiCommandResult.ok("标签已改名：" + oldName + " -> " + newName);
    }

    private String normalizeTagName(String value) {
        if (value == null) return null;
        String name = value.trim();
        return name.isEmpty() || name.length() > 32 ? null : name;
    }

    private TuiCommandResult show(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 1) return TuiCommandResult.error("用法：SHOW USERS|TAGS|ISSUES|CONFIG");
        return switch (arguments.get(0).toUpperCase()) {
            case "USERS" -> showUsers();
            case "TAGS" -> showTags();
            case "ISSUES" -> showIssues();
            case "CONFIG" -> showConfig();
            default -> TuiCommandResult.error("未知 SHOW 目标：" + arguments.get(0));
        };
    }

    private synchronized TuiCommandResult auth(List<String> arguments) {
        if (arguments.size() == 1 && arguments.get(0).equalsIgnoreCase("LOGOUT")) {
            authenticatedAdmin = null;
            return TuiCommandResult.ok("TUI 管理员会话已注销。");
        }
        if (arguments.size() == 1 && arguments.get(0).equalsIgnoreCase("STATUS")) {
            return requireAuthenticatedAdmin()
                    ? TuiCommandResult.ok("已认证管理员：" + authenticatedAdmin.username)
                    : TuiCommandResult.ok("未认证。");
        }
        if (!arguments.isEmpty() && arguments.get(0).equalsIgnoreCase("ADD")) return addUser(arguments);
        if (!arguments.isEmpty() && arguments.get(0).equalsIgnoreCase("DELETE")) return disableUser(arguments);
        if (arguments.size() >= 1 && arguments.get(0).equalsIgnoreCase("EMAIL") && arguments.size() >= 2 && arguments.get(1).equalsIgnoreCase("SET")) return setEmail(arguments);
        if (!arguments.isEmpty() && arguments.get(0).equalsIgnoreCase("PASSWD")) return setPassword(arguments);
        if (arguments.size() != 3 || !arguments.get(0).equalsIgnoreCase("LOGIN")) {
            return TuiCommandResult.error("用法：AUTH LOGIN <管理员用户名> <密码> | AUTH LOGOUT | AUTH STATUS | AUTH ADD <用户> <邮箱> <密码> | AUTH DELETE <用户> --force | AUTH EMAIL SET <用户> <邮箱> | AUTH PASSWD <用户> <密码>");
        }
        String username = arguments.get(1);
        User user = users.findByUsernameOrEmail(username, username).filter(candidate -> candidate.enabled).orElse(null);
        if (user == null || user.role != User.Role.ADMIN || !passwordEncoder.matches(arguments.get(2), user.passwordHash)) {
            authenticatedAdmin = null;
            return TuiCommandResult.error("管理员账号或密码错误。");
        }
        authenticatedAdmin = user;
        return TuiCommandResult.ok("TUI 管理员认证成功：" + user.username);
    }

    private TuiCommandResult addUser(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 4) return TuiCommandResult.error("用法：AUTH ADD <用户> <邮箱> <密码>");
        String username = arguments.get(1).trim();
        String email = arguments.get(2).trim().toLowerCase(Locale.ROOT);
        String password = arguments.get(3);
        if (!validUsername(username)) return TuiCommandResult.error("用户名必须为 3-32 个字符。");
        if (!validEmail(email)) return TuiCommandResult.error("邮箱格式不正确。");
        if (password.length() < 8 || password.length() > 128) return TuiCommandResult.error("密码长度必须为 8-128 个字符。");
        if (users.existsByUsernameOrEmail(username, email)) return TuiCommandResult.error("用户名或邮箱已存在。");
        User user = new User(); user.username = username; user.email = email; user.passwordHash = passwordEncoder.encode(password); user.role = User.Role.USER; user.enabled = true;
        User saved = users.saveAndFlush(user);
        audit.record(authenticatedAdmin, "USER_CREATED", "USER", saved.id, "TUI 创建用户");
        return TuiCommandResult.ok("用户已创建：" + saved.username);
    }

    private TuiCommandResult disableUser(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 3 || !arguments.get(2).equalsIgnoreCase("--force")) return TuiCommandResult.error("危险操作需要显式确认：AUTH DELETE <用户> --force");
        User target = findUser(arguments.get(1));
        if (target == null) return TuiCommandResult.error("用户不存在：" + arguments.get(1));
        if (target.id.equals(authenticatedAdmin.id)) return TuiCommandResult.error("不能停用当前 TUI 管理员账号。");
        if (!target.enabled) return TuiCommandResult.error("用户已经停用：" + target.username);
        target.enabled = false; users.saveAndFlush(target); kickoutAfterCommit(target.id);
        audit.record(authenticatedAdmin, "USER_DISABLED", "USER", target.id, "TUI 停用用户");
        return TuiCommandResult.ok("用户已停用：" + target.username);
    }

    private TuiCommandResult setEmail(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 4) return TuiCommandResult.error("用法：AUTH EMAIL SET <用户> <邮箱>");
        User target = findUser(arguments.get(2));
        if (target == null) return TuiCommandResult.error("用户不存在：" + arguments.get(2));
        String email = arguments.get(3).trim().toLowerCase(Locale.ROOT);
        if (!validEmail(email)) return TuiCommandResult.error("邮箱格式不正确。");
        if (email.equalsIgnoreCase(target.email)) return TuiCommandResult.error("邮箱未发生变化。");
        if (users.existsByEmailIgnoreCaseAndIdNot(email, target.id)) return TuiCommandResult.error("邮箱已被其他账号使用。");
        target.email = email; users.saveAndFlush(target);
        audit.record(authenticatedAdmin, "EMAIL_CHANGED", "USER", target.id, "TUI 修改用户邮箱");
        return TuiCommandResult.ok("用户邮箱已更新：" + target.username);
    }

    private TuiCommandResult setPassword(List<String> arguments) {
        if (!requireAuthenticatedAdmin()) return TuiCommandResult.error("请先执行 AUTH LOGIN <管理员用户名> <密码>。");
        if (arguments.size() != 3) return TuiCommandResult.error("用法：AUTH PASSWD <用户> <密码>");
        User target = findUser(arguments.get(1));
        if (target == null) return TuiCommandResult.error("用户不存在：" + arguments.get(1));
        String password = arguments.get(2);
        if (password.length() < 8 || password.length() > 128) return TuiCommandResult.error("密码长度必须为 8-128 个字符。");
        target.passwordHash = passwordEncoder.encode(password); users.saveAndFlush(target); kickoutAfterCommit(target.id);
        audit.record(authenticatedAdmin, "PASSWORD_RESET", "USER", target.id, "TUI 重置用户密码");
        return TuiCommandResult.ok("用户密码已更新：" + target.username);
    }

    private boolean requireAuthenticatedAdmin() {
        if (authenticatedAdmin == null || authenticatedAdmin.id == null) return false;
        User refreshed = users.findById(authenticatedAdmin.id).orElse(null);
        if (refreshed == null || !refreshed.enabled || refreshed.role != User.Role.ADMIN) {
            authenticatedAdmin = null;
            return false;
        }
        authenticatedAdmin = refreshed;
        return true;
    }
    private User findUser(String value) { return users.findByUsernameOrEmail(value, value).orElse(null); }
    private void kickoutAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { StpUtil.kickout(userId); }
            });
        } else {
            StpUtil.kickout(userId);
        }
    }
    private boolean validUsername(String value) { return value != null && value.length() >= 3 && value.length() <= 32; }
    private boolean validEmail(String value) { return value != null && Pattern.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", value) && value.length() <= 254; }

    private TuiCommandResult showUsers() {
        String output = users.findForTui().stream()
                .map(user -> "%d\t%s\t%s\t%s\t%s".formatted(user.id, user.username, user.email, user.role, user.enabled ? "ENABLED" : "DISABLED"))
                .collect(Collectors.joining("\n", "ID\tUSERNAME\tEMAIL\tROLE\tSTATUS\n", ""));
        return TuiCommandResult.ok(output.isBlank() ? "暂无用户。" : output);
    }

    private TuiCommandResult showTags() {
        String output = tags.findSummaries().stream()
                .map(tag -> "%d\t%s\t%s\t%d".formatted(tag.getId(), tag.getName(), tag.getColor(), tag.getIssueCount()))
                .collect(Collectors.joining("\n", "ID\tNAME\tCOLOR\tISSUES\n", ""));
        return TuiCommandResult.ok(output.isBlank() ? "暂无标签。" : output);
    }

    private TuiCommandResult showIssues() {
        String output = issues.findTop50ByDeletedFalseOrderByUpdatedAtDescIdDesc().stream()
                .map(issue -> "%d\t%s\t%s\t%s\t%s".formatted(issue.id, issue.state, issue.author.username, issue.title, issue.tags.stream().map(tag -> tag.name).collect(Collectors.joining(","))))
                .collect(Collectors.joining("\n", "ID\tSTATE\tAUTHOR\tTITLE\tTAGS\n", ""));
        return TuiCommandResult.ok(output.isBlank() ? "暂无工单。" : output);
    }

    private TuiCommandResult showConfig() {
        String output = "KEY\tVALUE\n" +
                "app.version\t" + version + "\n" +
                "app.tui.enabled\t" + environment.getProperty("app.tui.enabled", "false") + "\n" +
                "app.cors.allowed-origins\t" + runtimeConfiguration.value("app.cors.allowed-origins") + "\n" +
                "sa-token.timeout\t" + runtimeConfiguration.value("sa-token.timeout") + "\n" +
                "sa-token.active-timeout\t" + runtimeConfiguration.value("sa-token.active-timeout") + "\n" +
                "spring.profiles.active\t" + String.join(",", environment.getActiveProfiles());
        return TuiCommandResult.ok(output);
    }
}
