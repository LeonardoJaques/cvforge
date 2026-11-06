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
        PerKeyTokenBucket limiter = new PerKeyTokenBucket(40, java.time.Duration.ofSeconds(1), 100);
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
}
