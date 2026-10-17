package com.simpissuehub.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class TurnstileVerifier {
    private final RestClient restClient;
    private final String secretKey;
    private final String expectedHostname;

    public TurnstileVerifier(RestClient.Builder builder,
                             @Value("${app.security.turnstile.secret-key:}") String secretKey,
                             @Value("${app.security.turnstile.expected-hostname:}") String expectedHostname) {
        this.restClient = builder.baseUrl("https://challenges.cloudflare.com/turnstile/v0").build();
        // Empty Spring placeholders can be exposed as null by some launchers.
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.expectedHostname = expectedHostname == null ? "" : expectedHostname.trim();
    }

    public boolean enabled() {
        return !secretKey.isBlank();
    }

    public void verify(String token, String remoteAddress) {
        verify(token, remoteAddress, "register");
    }

    public void verify(String token, String remoteAddress, String expectedAction) {
        if (!enabled()) return;
        if (token == null || token.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "请完成人机验证");

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteAddress != null && !remoteAddress.isBlank()) form.add("remoteip", remoteAddress);

        SiteverifyResponse response;
        try {
            response = restClient.post().uri("/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "人机验证服务暂时不可用");
        }
        if (response == null || !response.success()) throw new ResponseStatusException(BAD_REQUEST, "人机验证失败，请重试");
        // Some Turnstile modes omit optional metadata while still returning success.
        // Validate it when supplied, but do not reject an otherwise valid token solely
        // because hostname/action is absent.
        if (!expectedHostname.isBlank() && response.hostname() != null && !response.hostname().isBlank()
                && !expectedHostname.equalsIgnoreCase(response.hostname())) {
            throw new ResponseStatusException(BAD_REQUEST, "人机验证来源不匹配");
        }
        if (expectedAction != null && !expectedAction.isBlank() && response.action() != null && !response.action().isBlank()
                && !expectedAction.equals(response.action())) {
            throw new ResponseStatusException(BAD_REQUEST, "人机验证操作不匹配");
        }
    }

    public record SiteverifyResponse(boolean success, String hostname, String action,
                                     @JsonProperty("error-codes") List<String> errorCodes) {}
}
