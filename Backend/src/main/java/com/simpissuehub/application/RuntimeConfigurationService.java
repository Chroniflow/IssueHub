package com.simpissuehub.application;

import cn.dev33.satoken.SaManager;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Whitelisted site feature settings. Environment variables always win at startup. */
@Service
public class RuntimeConfigurationService {
    private record Definition(String key, String env, String label, String type, String defaultValue) {}
    public record Entry(String key, String label, String type, String value, boolean environmentLocked) {}
    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("app.security.registration-verification.required", "EMAIL_VERIFICATION_REQUIRED", "注册邮箱验证码", "BOOLEAN", "true"),
            new Definition("app.security.turnstile.secret-key", "TURNSTILE_SECRET_KEY", "Turnstile 服务端校验", "SECRET", ""),
            new Definition("app.security.turnstile.expected-hostname", "TURNSTILE_EXPECTED_HOSTNAME", "Turnstile 域名校验", "TEXT", ""),
            new Definition("app.site.icon-url", "SITE_ICON_URL", "站点图标 URL", "URL", ""),
            new Definition("app.site.about-content", "SITE_ABOUT_CONTENT", "关于页面内容", "TEMPLATE", "SimpIssueHub 面向 Minecraft 服务器玩家，用于提交反馈、功能建议和 Bug 报告。\n\n登录后可查看每条反馈的处理状态。玩家可以查看讨论、补充复现信息，并与工作人员一起让服务器变得更好。"),
            new Definition("app.mail.rules.reply-creates-comment", "MAIL_REPLY_CREATES_COMMENT", "邮件回复创建评论", "BOOLEAN", "true"),
            new Definition("app.mail.rules.require-sender-verification", "MAIL_REQUIRE_SENDER_VERIFICATION", "邮件发件人验证", "BOOLEAN", "true"),
            new Definition("app.mail.notifications.comment-created", "MAIL_NOTIFY_COMMENT_CREATED", "评论创建通知", "BOOLEAN", "true"),
            new Definition("app.mail.notifications.state-changed", "MAIL_NOTIFY_STATE_CHANGED", "状态变更通知", "BOOLEAN", "true"),
            new Definition("app.mail.notifications.mentioned", "MAIL_NOTIFY_MENTIONED", "提及用户通知", "BOOLEAN", "true"),
            new Definition("app.mail.public-base-url", "MAIL_PUBLIC_BASE_URL", "邮件公开站点地址", "TEXT", "http://localhost:5173"),
            new Definition("app.mail.templates.verification-code.subject", "MAIL_TEMPLATE_VERIFICATION_CODE_SUBJECT", "验证码邮件主题", "TEXT", "[{{siteName}}] 注册验证码"),
            new Definition("app.mail.templates.verification-code.body", "MAIL_TEMPLATE_VERIFICATION_CODE_BODY", "验证码邮件正文", "TEMPLATE", "你的注册验证码是：{{verificationCode}}\n\n验证码在 {{expiresMinutes}} 分钟内有效，请勿转发给他人。"),
            new Definition("app.mail.templates.registration.subject", "MAIL_TEMPLATE_REGISTRATION_SUBJECT", "注册成功邮件主题", "TEXT", "[{{siteName}}] 欢迎加入"),
            new Definition("app.mail.templates.registration.body", "MAIL_TEMPLATE_REGISTRATION_BODY", "注册成功邮件正文", "TEMPLATE", "你好，{{username}}：\n\n{{message}}"),
            new Definition("app.mail.templates.password-reset.subject", "MAIL_TEMPLATE_PASSWORD_RESET_SUBJECT", "密码重置邮件主题", "TEXT", "[{{siteName}}] 重置密码"),
            new Definition("app.mail.templates.password-reset.body", "MAIL_TEMPLATE_PASSWORD_RESET_BODY", "密码重置邮件正文", "TEMPLATE", "你好，{{username}}：\n\n{{message}}"),
            new Definition("app.mail.templates.comment-created.subject", "MAIL_TEMPLATE_COMMENT_CREATED_SUBJECT", "评论通知邮件主题", "TEXT", "[{{siteName}}] 工单 #{{issueId}} 收到新评论"),
            new Definition("app.mail.templates.comment-created.body", "MAIL_TEMPLATE_COMMENT_CREATED_BODY", "评论通知邮件正文", "TEMPLATE", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}"),
            new Definition("app.mail.templates.issue-state-changed.subject", "MAIL_TEMPLATE_ISSUE_STATE_CHANGED_SUBJECT", "状态通知邮件主题", "TEXT", "[{{siteName}}] 工单状态更新 #{{issueId}}"),
            new Definition("app.mail.templates.issue-state-changed.body", "MAIL_TEMPLATE_ISSUE_STATE_CHANGED_BODY", "状态通知邮件正文", "TEMPLATE", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}"),
            new Definition("app.mail.templates.mentioned.subject", "MAIL_TEMPLATE_MENTIONED_SUBJECT", "提及通知邮件主题", "TEXT", "[{{siteName}}] 你在工单 #{{issueId}} 中被提及"),
            new Definition("app.mail.templates.mentioned.body", "MAIL_TEMPLATE_MENTIONED_BODY", "提及通知邮件正文", "TEMPLATE", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}"),
            new Definition("app.cors.allowed-origins", "APP_CORS_ALLOWED_ORIGINS", "允许的跨域来源", "TEXT", "http://localhost:5173,http://127.0.0.1:5173"),
            new Definition("sa-token.timeout", "SA_TOKEN_TIMEOUT", "会话有效期（秒）", "INTEGER", "2592000"),
            new Definition("sa-token.active-timeout", "SA_TOKEN_ACTIVE_TIMEOUT", "会话活跃期（秒）", "INTEGER", "1800")
    );
    private final Environment environment; private final Path file; private final Map<String, String> values = new LinkedHashMap<>();

    public RuntimeConfigurationService(Environment environment) {
        this.environment = environment;
        this.file = Path.of(environment.getProperty("SITE_CONFIG_FILE", "./data/site-config.properties")).toAbsolutePath().normalize();
        loadStartupValues();
    }
    public synchronized List<String> corsOrigins() { return split(values.get("app.cors.allowed-origins")); }
    public synchronized String value(String node) {
        if ("sa-token.timeout".equals(node)) return Long.toString(SaManager.getConfig().getTimeout());
        if ("sa-token.active-timeout".equals(node)) return Long.toString(SaManager.getConfig().getActiveTimeout());
        return values.getOrDefault(node, "");
    }
    public synchronized List<Entry> entries() { return DEFINITIONS.stream().map(d -> new Entry(d.key(), d.label(), d.type(), displayValue(d), isEnvironmentSet(d.env()))).toList(); }
    public synchronized void update(String node, String rawValue) {
        Definition d = DEFINITIONS.stream().filter(item -> item.key().equals(node)).findFirst().orElseThrow(() -> new IllegalArgumentException("不支持的运行时配置：" + node));
        if (isEnvironmentSet(d.env())) throw new IllegalStateException("该配置由环境变量 " + d.env() + " 控制，无法在网页持久化修改");
        String normalized = validate(d, rawValue); values.put(node, normalized); applyRuntime(node, normalized); writeFile();
    }
    private void loadStartupValues() {
        Map<String, String> stored = readFile();
        for (Definition d : DEFINITIONS) { String resolved = environment.getProperty(d.key(), d.defaultValue()); values.put(d.key(), isEnvironmentSet(d.env()) ? resolved : stored.getOrDefault(d.env(), stored.getOrDefault(d.key(), resolved))); }
        applyRuntime("sa-token.timeout", values.get("sa-token.timeout")); applyRuntime("sa-token.active-timeout", values.get("sa-token.active-timeout")); writeFile();
    }
    private String displayValue(Definition d) { String v = values.getOrDefault(d.key(), ""); return "SECRET".equals(d.type()) && !v.isBlank() ? "已配置" : v; }
    private boolean isEnvironmentSet(String name) { String v = System.getenv(name); return v != null && !v.isBlank(); }
    private String validate(Definition d, String input) {
        String v = input == null ? "" : input.trim();
        if ("BOOLEAN".equals(d.type()) && !List.of("true", "false").contains(v.toLowerCase())) throw new IllegalArgumentException("配置必须是 true 或 false");
        if ("INTEGER".equals(d.type())) { try { if (Long.parseLong(v) < 0) throw new NumberFormatException(); } catch (NumberFormatException e) { throw new IllegalArgumentException("配置必须是非负整数"); } }
        if (("TEXT".equals(d.type()) || "TEMPLATE".equals(d.type())) && v.length() > 10_000) throw new IllegalArgumentException("配置内容不能超过 10000 个字符");
        if ("URL".equals(d.type())) {
            if (v.length() > 2_048) throw new IllegalArgumentException("URL 不能超过 2048 个字符");
            if (!v.isEmpty() && !v.matches("https?://\\S+")) throw new IllegalArgumentException("站点图标 URL 必须是 http 或 https URL");
        }
        return v;
    }
    private void applyRuntime(String node, String value) { if ("sa-token.timeout".equals(node)) SaManager.getConfig().setTimeout(Long.parseLong(value)); else if ("sa-token.active-timeout".equals(node)) SaManager.getConfig().setActiveTimeout(Long.parseLong(value)); }
    private Map<String, String> readFile() { if (!Files.exists(file)) return Map.of(); try { Properties p = new Properties(); try (var reader = Files.newBufferedReader(file)) { p.load(reader); } Map<String, String> result = new LinkedHashMap<>(); p.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v))); return result; } catch (Exception ignored) { return Map.of(); } }
    private void writeFile() { try { Files.createDirectories(file.getParent()); Properties p = new Properties(); for (Definition d : DEFINITIONS) p.setProperty(d.env(), values.getOrDefault(d.key(), d.defaultValue())); Path tmp = file.resolveSibling(file.getFileName() + ".tmp"); try (var writer = Files.newBufferedWriter(tmp)) { p.store(writer, "SimpIssueHub site feature configuration; environment variables override these values"); } Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (IOException e) { throw new IllegalStateException("无法写入站点配置文件：" + file, e); } }
    private List<String> split(String value) { if (value == null) return List.of(); List<String> out = new ArrayList<>(); for (String item : value.split(",")) { String v = item.trim(); if (!v.isEmpty() && !out.contains(v)) out.add(v); } return out; }
}
