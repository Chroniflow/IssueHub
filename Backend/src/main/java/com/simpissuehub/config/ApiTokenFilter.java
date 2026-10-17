package com.simpissuehub.config;

import com.simpissuehub.api.TokenController;
import com.simpissuehub.domain.ApiToken;
import com.simpissuehub.repository.ApiTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {
    public static final String USER_ID = "apiTokenUserId";
    private final ApiTokenRepository tokens;
    public ApiTokenFilter(ApiTokenRepository tokens) { this.tokens = tokens; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.length() < 11 || !authorization.regionMatches(true, 0, "Bearer sih_", 0, 11)) { chain.doFilter(request, response); return; }
        ApiToken token = tokens.findByTokenHash(TokenController.hash(authorization.substring(7)))
                .filter(t -> t.user.enabled && t.revokedAt == null
                        && (t.expiresAt == null || t.expiresAt.isAfter(Instant.now())))
                .orElse(null);
        String required = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) ? "READ" : "WRITE";
        if (token == null || !hasScope(token.scopes, required)) { response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setHeader("WWW-Authenticate", "Bearer"); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"status\":401,\"code\":\"INVALID_API_TOKEN\",\"message\":\"API Token 无效、已过期或权限不足\",\"path\":\"" + escape(request.getRequestURI()) + "\",\"timestamp\":\"" + Instant.now() + "\"}"); return; }
        token.lastUsedAt = Instant.now(); tokens.save(token); request.setAttribute(USER_ID, token.user.id); chain.doFilter(request, response);
    }
    private boolean hasScope(String scopes, String required) {
        if (scopes == null) return false;
        return java.util.Arrays.stream(scopes.split(",")).map(String::trim).anyMatch(required::equals);
    }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
