package com.simpissuehub.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TurnstileVerifierTest {
    @Test
    void disabledWithoutSecretForLocalDevelopment() {
        TurnstileVerifier verifier = new TurnstileVerifier(RestClient.builder(), "", "");
        assertFalse(verifier.enabled());
        assertDoesNotThrow(() -> verifier.verify("", "127.0.0.1"));
    }

    @Test
    void nullConfigurationIsTreatedAsDisabled() {
        TurnstileVerifier verifier = new TurnstileVerifier(RestClient.builder(), null, null);
        assertFalse(verifier.enabled());
        assertDoesNotThrow(() -> verifier.verify(null, "127.0.0.1"));
    }
}
