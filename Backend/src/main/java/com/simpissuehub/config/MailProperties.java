package com.simpissuehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {
    private String from = "no-reply@localhost";
    private String publicBaseUrl = "http://localhost:5173";
    private Templates templates = new Templates();
    private Rules rules = new Rules();
    private Notifications notifications = new Notifications();
    private Attachments attachments = new Attachments();
    private Retention retention = new Retention();

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String value) { publicBaseUrl = value; }
    public Templates getTemplates() { return templates; }
    public void setTemplates(Templates value) { templates = value; }

    public static class Templates {
        private Template verificationCode = new Template("[{{siteName}}] 注册验证码", "你的注册验证码是：{{verificationCode}}\n\n验证码在 {{expiresMinutes}} 分钟内有效，请勿转发给他人。");
        private Template registration = new Template("[{{siteName}}] 欢迎加入", "你好，{{username}}：\n\n{{message}}");
        private Template passwordReset = new Template("[{{siteName}}] 重置密码", "你好，{{username}}：\n\n{{message}}");
        private Template issueCreated = new Template("[{{siteName}}] 工单已创建 #{{issueId}}", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}");
        private Template commentCreated = new Template("[{{siteName}}] 工单 #{{issueId}} 收到新评论", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}");
        private Template issueStateChanged = new Template("[{{siteName}}] 工单状态更新 #{{issueId}}", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}");
        private Template mentioned = new Template("[{{siteName}}] 你在工单 #{{issueId}} 中被提及", "你好，{{username}}：\n\n{{message}}\n\n工单编号：#{{issueId}}");
        public Template getVerificationCode() { return verificationCode; } public void setVerificationCode(Template v) { verificationCode = v; }
        public Template getRegistration() { return registration; } public void setRegistration(Template v) { registration = v; }
        public Template getPasswordReset() { return passwordReset; } public void setPasswordReset(Template v) { passwordReset = v; }
        public Template getIssueCreated() { return issueCreated; } public void setIssueCreated(Template v) { issueCreated = v; }
        public Template getCommentCreated() { return commentCreated; } public void setCommentCreated(Template v) { commentCreated = v; }
        public Template getIssueStateChanged() { return issueStateChanged; } public void setIssueStateChanged(Template v) { issueStateChanged = v; }
        public Template getMentioned() { return mentioned; } public void setMentioned(Template v) { mentioned = v; }
    }
    public static class Template {
        private String subject; private String body;
        public Template() {}
        public Template(String subject, String body) { this.subject = subject; this.body = body; }
        public String getSubject() { return subject; } public void setSubject(String v) { subject = v; }
        public String getBody() { return body; } public void setBody(String v) { body = v; }
    }
    public Rules getRules() { return rules; }
    public void setRules(Rules rules) { this.rules = rules; }
    public Notifications getNotifications() { return notifications; }
    public void setNotifications(Notifications notifications) { this.notifications = notifications; }
    public Attachments getAttachments() { return attachments; }
    public void setAttachments(Attachments attachments) { this.attachments = attachments; }
    public Retention getRetention() { return retention; }
    public void setRetention(Retention retention) { this.retention = retention; }

    public static class Rules {
        private boolean replyCreatesComment = true;
        private String issueReference = "SUBJECT_OR_IN_REPLY_TO";
        private boolean requireSenderVerification = true;
        private boolean deduplicateByMessageId = true;
        public boolean isReplyCreatesComment() { return replyCreatesComment; }
        public void setReplyCreatesComment(boolean value) { replyCreatesComment = value; }
        public String getIssueReference() { return issueReference; }
        public void setIssueReference(String value) { issueReference = value; }
        public boolean isRequireSenderVerification() { return requireSenderVerification; }
        public void setRequireSenderVerification(boolean value) { requireSenderVerification = value; }
        public boolean isDeduplicateByMessageId() { return deduplicateByMessageId; }
        public void setDeduplicateByMessageId(boolean value) { deduplicateByMessageId = value; }
    }

    public static class Notifications {
        private boolean issueCreated = false;
        private boolean commentCreated = true;
        private boolean stateChanged = true;
        private boolean mentioned = true;
        private List<String> additionalRecipients = new ArrayList<>();
        public boolean isIssueCreated() { return issueCreated; }
        public void setIssueCreated(boolean value) { issueCreated = value; }
        public boolean isCommentCreated() { return commentCreated; }
        public void setCommentCreated(boolean value) { commentCreated = value; }
        public boolean isStateChanged() { return stateChanged; }
        public void setStateChanged(boolean value) { stateChanged = value; }
        public boolean isMentioned() { return mentioned; }
        public void setMentioned(boolean value) { mentioned = value; }
        public List<String> getAdditionalRecipients() { return additionalRecipients; }
        public void setAdditionalRecipients(List<String> value) { additionalRecipients = value; }
    }

    public static class Attachments {
        private long maxBytes = 10 * 1024 * 1024;
        private String mode = "REJECT";
        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long value) { maxBytes = value; }
        public String getMode() { return mode; }
        public void setMode(String value) { mode = value; }
    }

    public static class Retention {
        private long incomingDays = 30;
        private long outgoingDays = 90;
        public long getIncomingDays() { return incomingDays; }
        public void setIncomingDays(long value) { incomingDays = value; }
        public long getOutgoingDays() { return outgoingDays; }
        public void setOutgoingDays(long value) { outgoingDays = value; }
    }
}
