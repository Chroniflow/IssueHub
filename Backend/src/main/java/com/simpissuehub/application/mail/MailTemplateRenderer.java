package com.simpissuehub.application.mail;

import com.simpissuehub.application.RuntimeConfigurationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MailTemplateRenderer {
    private static final String SITE_NAME = "SimpIssueHub";
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
    private final RuntimeConfigurationService configuration;

    public MailTemplateRenderer(RuntimeConfigurationService configuration) { this.configuration = configuration; }

    public RenderedMail render(MailEvent event) {
        String templateName = switch (event.type()) {
            case REGISTRATION -> "registration";
            case PASSWORD_RESET -> "password-reset";
            case ISSUE_CREATED -> "issue-created";
            case COMMENT_CREATED -> "comment-created";
            case ISSUE_STATE_CHANGED -> "issue-state-changed";
            case MENTIONED -> "mentioned";
        };
        Map<String, String> values = baseValues();
        values.put("username", event.recipient() == null ? "玩家" : event.recipient().username);
        values.put("issueId", event.issueId() == null ? "" : event.issueId().toString());
        values.put("commentId", event.commentId() == null ? "" : event.commentId().toString());
        values.put("subject", safe(event.subject()));
        values.put("message", safe(event.text()));
        String actionUrl = event.issueId() == null ? firstUrl(event.text()) : issueUrl(event.issueId());
        values.put("actionUrl", actionUrl);
        return render(templateName, values, actionUrl);
    }

    public RenderedMail renderVerificationCode(String code, long expiresMinutes) {
        Map<String, String> values = baseValues();
        values.put("verificationCode", safe(code));
        values.put("expiresMinutes", Long.toString(expiresMinutes));
        return render("verification-code", values, "");
    }

    private RenderedMail render(String templateName, Map<String, String> values, String actionUrl) {
        String prefix = "app.mail.templates." + templateName;
        String subject = replace(configuration.value(prefix + ".subject"), values).replace('\r', ' ').replace('\n', ' ').trim();
        String text = replace(configuration.value(prefix + ".body"), values).replace("\\n", "\n").trim();
        return new RenderedMail(subject, text, html(text, actionUrl));
    }

    private Map<String, String> baseValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("siteName", SITE_NAME); values.put("username", "玩家"); values.put("issueId", "");
        values.put("commentId", ""); values.put("subject", ""); values.put("message", "");
        values.put("actionUrl", ""); values.put("verificationCode", ""); values.put("expiresMinutes", "");
        return values;
    }

    private String replace(String template, Map<String, String> values) {
        String result = safe(template);
        for (Map.Entry<String, String> entry : values.entrySet()) result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        return result;
    }

    private String issueUrl(Long issueId) {
        String base = configuration.value("app.mail.public-base-url");
        if (!isHttpUrl(base)) return "";
        return base.replaceAll("/+$", "") + "/issues/" + issueId;
    }

    private String firstUrl(String value) {
        Matcher matcher = URL.matcher(safe(value));
        return matcher.find() && isHttpUrl(matcher.group()) ? matcher.group() : "";
    }

    private boolean isHttpUrl(String value) {
        try { String scheme = URI.create(value).getScheme(); return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private String html(String text, String actionUrl) {
        String content = paragraphs(text);
        String action = isHttpUrl(actionUrl) ? "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-top:24px\"><tr><td style=\"border-radius:6px;background:#1f883d\"><a href=\"" + escapeHtml(actionUrl) + "\" style=\"display:inline-block;padding:10px 18px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600\">查看反馈详情</a></td></tr></table>" : "";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f6f8fa;color:#1f2328;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans SC',Arial,sans-serif\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f6f8fa\"><tr><td align=\"center\" style=\"padding:32px 16px\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;border:1px solid #d0d7de;border-radius:6px;background:#ffffff\">"
                + "<tr><td style=\"padding:18px 24px;border-bottom:1px solid #d8dee4\"><div style=\"font-size:18px;font-weight:600;line-height:1.4\">SimpIssueHub</div><div style=\"margin-top:3px;color:#656d76;font-size:12px\">反馈状态通知</div></td></tr>"
                + "<tr><td style=\"padding:28px 24px;font-size:15px;line-height:1.7\">" + content + action + "</td></tr>"
                + "<tr><td style=\"padding:16px 24px;border-top:1px solid #d8dee4;color:#656d76;font-size:12px;line-height:1.6\">此邮件由 SimpIssueHub 自动发送，请勿直接回复或在邮件中提供密码、验证码等敏感信息。</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String paragraphs(String text) {
        String normalized = safe(text).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder html = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n", -1)) {
            if (paragraph.isBlank()) continue;
            html.append("<p style=\"margin:0 0 16px\">")
                    .append(escapeHtml(paragraph).replace("\n", "<br>"))
                    .append("</p>");
        }
        return html.isEmpty() ? "<p style=\"margin:0\">你有一条新的通知。</p>" : html.toString();
    }

    private String escapeHtml(String value) { return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private String safe(String value) { return value == null ? "" : value; }
    public record RenderedMail(String subject, String text, String html) {}
}
