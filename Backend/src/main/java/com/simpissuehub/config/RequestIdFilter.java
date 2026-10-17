package com.simpissuehub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-ID");
        String requestId = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}") ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId); response.setHeader("X-Request-ID", requestId); MDC.put(ATTRIBUTE, requestId);
        long started = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.info("http_request method={} path={} status={} duration_ms={}", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(ATTRIBUTE);
        }
    }
}
