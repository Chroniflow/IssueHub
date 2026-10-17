package com.simpissuehub.api;

import com.simpissuehub.application.RuntimeConfigurationService;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final JdbcTemplate jdbc;
    private final RuntimeConfigurationService configuration;

    public SystemController(JdbcTemplate jdbc, RuntimeConfigurationService configuration) {
        this.jdbc = jdbc;
        this.configuration = configuration;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthView> health() {
        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            if (result == null || result != 1) return down("数据库探测返回异常结果");
            return ResponseEntity.ok(new HealthView("UP", "simp-issue-hub-backend", "UP", null, Instant.now()));
        } catch (Exception exception) {
            return down("数据库连接不可用");
        }
    }

    /** Public, non-sensitive site presentation settings used by the static frontend. */
    @GetMapping("/site/config")
    public SiteConfig siteConfig() {
        String iconUrl = configuration.value("app.site.icon-url");
        return new SiteConfig(iconUrl.isBlank() ? null : iconUrl, configuration.value("app.site.about-content"));
    }

    private ResponseEntity<HealthView> down(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthView("DOWN", "simp-issue-hub-backend", "DOWN", message, Instant.now()));
    }

    public record HealthView(String status, String service, String database, String message, Instant timestamp) {}
    public record SiteConfig(String iconUrl, String aboutContent) {}
}
