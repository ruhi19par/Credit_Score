package com.credbridge.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    private final boolean enabled;
    private final int authLimitPerMinute;
    private final int uploadLimitPerHour;
    private final int reportLimitPerMinute;
    private final int generalLimitPerMinute;

    public RateLimitingFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.auth-per-minute:10}") int authLimitPerMinute,
            @Value("${app.rate-limit.uploads-per-hour:10}") int uploadLimitPerHour,
            @Value("${app.rate-limit.reports-per-minute:120}") int reportLimitPerMinute,
            @Value("${app.rate-limit.general-per-minute:300}") int generalLimitPerMinute
    ) {
        this.enabled = enabled;
        this.authLimitPerMinute = authLimitPerMinute;
        this.uploadLimitPerHour = uploadLimitPerHour;
        this.reportLimitPerMinute = reportLimitPerMinute;
        this.generalLimitPerMinute = generalLimitPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitRule rule = ruleFor(request);
        String key = rule.name() + ":" + clientKey(request);
        if (!allow(key, rule)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitRule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            return new LimitRule("auth", authLimitPerMinute, Duration.ofMinutes(1));
        }
        if (path.equals("/api/documents/upload") && "POST".equalsIgnoreCase(method)) {
            return new LimitRule("uploads", uploadLimitPerHour, Duration.ofHours(1));
        }
        if (path.startsWith("/api/reports/")) {
            return new LimitRule("reports", reportLimitPerMinute, Duration.ofMinutes(1));
        }
        return new LimitRule("general", generalLimitPerMinute, Duration.ofMinutes(1));
    }

    private boolean allow(String key, LimitRule rule) {
        Instant now = clock.instant();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.windowStartedAt().plus(rule.window()))) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(existing.windowStartedAt(), existing.count() + 1);
        });
        return counter.count() <= rule.limit();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record LimitRule(String name, int limit, Duration window) {
    }

    private record WindowCounter(Instant windowStartedAt, int count) {
    }
}
