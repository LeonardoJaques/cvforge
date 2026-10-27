package com.cvforge.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteUserAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAndLogsRemoteUserFromUnexpectedProxyIp() throws Exception {
        RemoteUserAuthenticationFilter filter = new RemoteUserAuthenticationFilter("172.19.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resumes");
        request.setRemoteAddr("172.19.0.16");
        request.addHeader("Remote-User", "test-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Logger logger = (Logger) LoggerFactory.getLogger(RemoteUserAuthenticationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, chain);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(403, response.getStatus());
        assertEquals("Proxy de autenticação não confiável", response.getErrorMessage());
        assertNull(chain.getRequest(), "the request must not reach the API controller");
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("CVFORGE_PROXY_TRUST_MISMATCH")
                        && event.getFormattedMessage().contains("expected_proxy_ip=172.19.0.9")
                        && event.getFormattedMessage().contains("actual_peer_ip=172.19.0.16")));
    }

    @Test
    void acceptsRemoteUserFromConfiguredProxyIp() throws Exception {
        RemoteUserAuthenticationFilter filter = new RemoteUserAuthenticationFilter("172.19.0.9");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resumes");
        request.setRemoteAddr("172.19.0.9");
        request.addHeader("Remote-User", "test-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("test-user", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(request, chain.getRequest());
    }
}
