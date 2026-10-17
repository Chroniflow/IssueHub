package com.simpissuehub.application;
import com.simpissuehub.domain.AuditLog;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import com.simpissuehub.config.RequestIdFilter;
import java.util.regex.Pattern;
@Service public class AuditService {
    private static final Pattern SENSITIVE_VALUE = Pattern.compile("(?i)(password|passwd|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+" );
    private final AuditLogRepository logs;
    private final HttpServletRequest request;
    public AuditService(AuditLogRepository logs, HttpServletRequest request) { this.logs = logs; this.request = request; }
    public void record(User actor, String action, String targetType, Object targetId, String summary) {
        AuditLog log = new AuditLog(); log.actor = actor; log.action = action; log.targetType = targetType; log.targetId = String.valueOf(targetId); log.summary = sanitizeSummary(summary); Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE); log.requestId = requestId == null ? null : String.valueOf(requestId); logs.save(log);
    }

    private String sanitizeSummary(String summary) {
        if (summary == null) return null;
        String redacted = SENSITIVE_VALUE.matcher(summary).replaceAll("$1=[REDACTED]");
        return redacted.substring(0, Math.min(redacted.length(), 500));
    }
}
