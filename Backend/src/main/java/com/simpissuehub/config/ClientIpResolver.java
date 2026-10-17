package com.simpissuehub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the client address when the application is behind a trusted reverse proxy. */
@Component
public class ClientIpResolver {
    private final String realIpHeader;

    public ClientIpResolver(@Value("${REAL_IP_HEADER:}") String realIpHeader) {
        this.realIpHeader = realIpHeader == null ? "" : realIpHeader.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (!realIpHeader.isBlank()) {
            String forwarded = request.getHeader(realIpHeader);
            if (forwarded != null && !forwarded.isBlank()) {
                // X-Forwarded-For may contain a chain; the first address is the client.
                String first = forwarded.split(",", 2)[0].trim();
                if (!first.isBlank()) return first;
            }
        }
        return request.getRemoteAddr();
    }
}
