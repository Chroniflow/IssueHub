package com.simpissuehub.application;

import com.simpissuehub.application.mail.MailGateway;
import com.simpissuehub.application.mail.MailTemplateRenderer;
import com.simpissuehub.domain.RegistrationVerificationCode;
import com.simpissuehub.repository.RegistrationVerificationCodeRepository;
import com.simpissuehub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationVerificationServiceTest {
    @Test
    void sendsHashedCodeAndConsumesMatchingValue() {
        RegistrationVerificationCodeRepository codes = mock(RegistrationVerificationCodeRepository.class);
        UserRepository users = mock(UserRepository.class);
        MailGateway mail = mock(MailGateway.class);
        MailTemplateRenderer templates = mock(MailTemplateRenderer.class);
        when(templates.renderVerificationCode(anyString(), anyLong())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return new MailTemplateRenderer.RenderedMail("注册验证码", "验证码：" + value, "<p>验证码：" + value + "</p>");
        });
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(codes.findTopByEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(codes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RegistrationVerificationService service = new RegistrationVerificationService(codes, users, mail, templates, true, 600, 60, 5);

        service.sendCode(" Player@Example.Test ");

        ArgumentCaptor<RegistrationVerificationCode> entity = ArgumentCaptor.forClass(RegistrationVerificationCode.class);
        verify(codes).saveAndFlush(entity.capture());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("player@example.test"), contains("注册验证码"), body.capture(), anyString());
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(body.getValue());
        assertTrue(matcher.find());
        assertFalse(entity.getValue().codeHash.contains(matcher.group(1)));

        when(codes.findFirstByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq("player@example.test"), any(Instant.class)))
                .thenReturn(Optional.of(entity.getValue()));
        service.verifyAndConsume("player@example.test", matcher.group(1));
        assertNotNull(entity.getValue().consumedAt);
        assertEquals(1, entity.getValue().attempts);
    }
}
