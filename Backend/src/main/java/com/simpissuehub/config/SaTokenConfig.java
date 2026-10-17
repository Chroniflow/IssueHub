package com.simpissuehub.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.simpissuehub.application.RuntimeConfigurationService;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({MailProperties.class, AttachmentProperties.class})
public class SaTokenConfig implements WebMvcConfigurer {
    @Bean
    public SaInterceptor saInterceptor() {
        return new SaInterceptor(handler -> {
            var attributes = RequestContextHolder.getRequestAttributes();
            var request = attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes.getRequest() : null;
            if (request == null || request.getAttribute(ApiTokenFilter.USER_ID) == null && !isPublicRead(request)) {
                cn.dev33.satoken.stp.StpUtil.checkLogin();
            }
        });
    }

    private boolean isPublicRead(jakarta.servlet.http.HttpServletRequest request) {
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) return false;
        String path = request.getRequestURI();
        return path.equals("/api/v1/site/config");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(saInterceptor()).addPathPatterns("/api/**").excludePathPatterns("/api/v1/health", "/api/v1/auth/**");
    }

    @Bean
    public CorsFilter corsFilter(RuntimeConfigurationService runtime) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(jakarta.servlet.http.HttpServletRequest request) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(runtime.corsOrigins());
                configuration.setAllowedMethods(java.util.List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(java.util.List.of("Content-Type", "Authorization", "satoken", "If-None-Match", "Idempotency-Key", "X-Request-ID"));
                configuration.setExposedHeaders(java.util.List.of("ETag", "Location", "X-Request-ID"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);
                return request.getRequestURI().startsWith("/api/") ? configuration : null;
            }
        };
        source.registerCorsConfiguration("/api/**", new CorsConfiguration());
        return new CorsFilter(source);
    }
}
