package com.cvforge.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApiAbuseProtectionFilterTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void perUserBurstIsCappedEvenWhenXForwardedForChanges() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("leonardo", null, List.of()));
        ApiAbuseProtectionFilter filter = new ApiAbuseProtectionFilter();
        AtomicInteger passed = new AtomicInteger();
        int limited = 0;
        for (int i = 0; i < 8; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs/analyze");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
            if (response.getStatus() == 429) limited++;
        }
        assertEquals(5, passed.get());
        assertEquals(3, limited);
    }

    @Test
    void concurrentVirtualRequestsCannotExceedBucketCapacity() throws Exception {
        PerKeyTokenBucket limiter = new PerKeyTokenBucket(40, 40, java.time.Duration.ofSeconds(1), 100);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 250; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire("same-user", 1L);
                }));
            }
            start.countDown();
            long admitted = 0;
            for (var result : results) if (result.get(5, TimeUnit.SECONDS)) admitted++;
            assertEquals(40, admitted);
        }
    }

    @Test
    void configuredQuotaRefillsTheConfiguredNumberOfTokensPerWindow() {
        PerKeyTokenBucket limiter = new PerKeyTokenBucket(30, 30, java.time.Duration.ofSeconds(2), 100);
        for (int i = 0; i < 30; i++) assertTrue(limiter.tryAcquire("user", 1L));
        assertFalse(limiter.tryAcquire("user", 1L));
        for (int i = 0; i < 30; i++) assertTrue(limiter.tryAcquire("user", 2_000_000_001L));
        assertFalse(limiter.tryAcquire("user", 2_000_000_001L));
        assertEquals(2, limiter.retryAfterSeconds("user", 2_000_000_001L));
    }

    @Test
    void anonymousRequestsAndHealthProbesDoNotConsumeAuthenticatedQuota() throws Exception {
        SecurityContextHolder.clearContext();
        ApiAbuseProtectionFilter filter = new ApiAbuseProtectionFilter();
        for (int i = 0; i < 40; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
            assertEquals(200, response.getStatus());
        }
        for (int i = 0; i < 8; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs/analyze");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
            assertEquals(200, response.getStatus());
        }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("leonardo", null, List.of()));
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest authenticatedRequest = new MockHttpServletRequest("POST", "/api/v1/jobs/analyze");
            MockHttpServletResponse authenticatedResponse = new MockHttpServletResponse();
            filter.doFilter(authenticatedRequest, authenticatedResponse,
                    (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
            assertEquals(200, authenticatedResponse.getStatus());
        }
        MockHttpServletRequest limitedRequest = new MockHttpServletRequest("POST", "/api/v1/jobs/analyze");
        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        filter.doFilter(limitedRequest, limitedResponse, (req, res) -> fail("limited request should not continue"));
        assertEquals(429, limitedResponse.getStatus());
    }
}
