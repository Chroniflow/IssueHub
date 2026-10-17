package com.simpissuehub.application.mail;

import com.simpissuehub.application.RuntimeConfigurationService;
import com.simpissuehub.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailTemplateRendererTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rendersConfiguredTemplateAndEscapesDynamicHtml() {
        RuntimeConfigurationService configuration = configuration();
        configuration.update("app.mail.public-base-url", "https://issue.example.test/");
        configuration.update("app.mail.templates.comment-created.subject", "{{siteName}} #{{issueId}}\nignored");
        configuration.update("app.mail.templates.comment-created.body", "你好 {{username}}\n{{message}}");
        User recipient = new User(); recipient.username = "<Player>";

        var result = new MailTemplateRenderer(configuration).render(new MailEvent(
                MailEventType.COMMENT_CREATED, recipient, "unused", "<script>alert(1)</script>", 42L, 7L));

        assertEquals("SimpIssueHub #42 ignored", result.subject());
        assertTrue(result.text().contains("<script>alert(1)</script>"));
        assertTrue(result.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(result.html().contains("https://issue.example.test/issues/42"));
        assertTrue(result.html().contains("查看反馈详情"));
        assertTrue(result.html().contains("反馈状态通知"));
        assertFalse(result.html().contains("<script>alert(1)</script>"));
    }

    @Test
    void verificationTemplateContainsOnlyWhitelistedValues() {
        RuntimeConfigurationService configuration = configuration();
        configuration.update("app.mail.templates.verification-code.body", "验证码 {{verificationCode}}，{{expiresMinutes}} 分钟。{{username}}");

        var result = new MailTemplateRenderer(configuration).renderVerificationCode("123456", 10);

        assertEquals("验证码 123456，10 分钟。玩家", result.text());
        assertFalse(result.html().contains("@"));
    }

    @Test
    void validatesSiteIconUrlAndPersistsIt() {
        RuntimeConfigurationService configuration = configuration();
        configuration.update("app.site.icon-url", "https://example.test/icon.png");
        assertEquals("https://example.test/icon.png", configuration.value("app.site.icon-url"));
        assertThrows(IllegalArgumentException.class, () -> configuration.update("app.site.icon-url", "javascript:alert(1)"));
    }

    private RuntimeConfigurationService configuration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("SITE_CONFIG_FILE", temporaryDirectory.resolve("site.properties").toString());
        return new RuntimeConfigurationService(environment);
    }
}
