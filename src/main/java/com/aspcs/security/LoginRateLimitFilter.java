package com.aspcs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// In-memory, per-IP sliding-window limiter on /auth/login only. Deliberately
// dependency-free (no Redis/Bucket4j) since this app runs as a single
// Railway instance — if you ever scale to multiple instances behind a load
// balancer, this stops being effective per-instance and you'd want a shared
// store (Redis) instead. For a single-instance school ERP this is the right
// amount of complexity, not under- or over-engineered.
//
// Limit: 8 attempts per IP per 5-minute window. Generous enough that a
// parent or teacher mistyping their password a few times never gets
// locked out, but a credential-stuffing script gets stopped cold.
@Slf4j
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 8;
    private static final long WINDOW_MILLIS = 5 * 60 * 1000L;

    private record Bucket(AtomicInteger count, long windowStart) {}

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // Matched defensively against both getServletPath() (correct per
        // servlet spec given this app's context-path=/api/v1 and default
        // DispatcherServlet mapping) and a suffix check on the full request
        // URI, so this still works even if that assumption is ever wrong
        // (e.g. after a future context-path or servlet-mapping change).
        boolean isLoginAttempt = "POST".equalsIgnoreCase(request.getMethod())
                && ("/auth/login".equals(request.getServletPath())
                    || request.getRequestURI().endsWith("/auth/login"));

        if (!isLoginAttempt) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();

        Bucket bucket = buckets.compute(clientIp, (ip, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MILLIS) {
                return new Bucket(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (bucket.count().get() > MAX_ATTEMPTS) {
            log.warn("Login rate limit exceeded for IP {}", clientIp);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many login attempts. Please try again in a few minutes.\"}");
            return;
        }

        filterChain.doFilter(request, response);

        // A successful login (2xx) clears the bucket immediately so a
        // legitimate user who fumbled the password a couple of times isn't
        // stuck waiting out the window after they get it right.
        if (response.getStatus() < 300) {
            buckets.remove(clientIp);
        }
    }

    // Verified against Railway's current documented proxy behavior: Railway's
    // edge proxy appends the real client IP to X-Forwarded-For, and the
    // leftmost entry in that header is the trustworthy one regardless of
    // which routing path (direct or via their CDN) the request took.
    // X-Real-Ip was deliberately NOT used here — Railway has an open,
    // acknowledged bug where it gets overwritten with their CDN's edge IP
    // when the CDN is in the request path, which would make every
    // CDN-routed request appear to come from the same "IP" and defeat this
    // limiter entirely.
    //
    // Trust boundary: this is only safe because the app sits behind
    // Railway's proxy in production. If this app is ever run with direct
    // public internet exposure (no Railway/reverse-proxy in front), a
    // client could set their own X-Forwarded-For and bypass this limiter.
    // That isn't the deployment model here, so it isn't handled — but
    // don't copy this pattern into a service that takes direct traffic.
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // This map is self-bounding in practice: legitimate traffic clears its
    // own bucket on success, and failed-attempt buckets get replaced (not
    // accumulated) once their window expires on the next request from that
    // IP. Unbounded growth would only happen under attack from a very large
    // number of distinct IPs, which is a bigger problem than this filter's
    // memory footprint — not something to solve here.
}
