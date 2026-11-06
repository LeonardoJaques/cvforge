package com.cvforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Applies authenticated-principal limits before expensive API use cases run. */
public final class ApiAbuseProtectionFilter extends OncePerRequestFilter {
    private final PerKeyTokenBucket generalApi = new PerKeyTokenBucket(30, Duration.ofSeconds(2), 10_000);
    private final PerKeyTokenBucket jobAnalysis = new PerKeyTokenBucket(5, Duration.ofSeconds(12), 10_000);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication == null ? "unauthenticated" : authentication.getName();
        long now = System.nanoTime();
        if (!generalApi.tryAcquire(principal + "|api", now)) {
            reject(response, 2);
            return;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/jobs/analyze".equals(request.getRequestURI())
                && !jobAnalysis.tryAcquire(principal + "|job-analysis", now)) {
            reject(response, 12);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Integer.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
    }
}
