package com.cvforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class RemoteUserAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RemoteUserAuthenticationFilter.class);
    private final String trustedProxyIp;
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://jaquesprojetos.com.br",
            "http://localhost:4200"
    );

    public RemoteUserAuthenticationFilter() {
        this(System.getenv().getOrDefault("CVFORGE_TRUSTED_PROXY_IP", "127.0.0.1"));
    }

    RemoteUserAuthenticationFilter(String trustedProxyIp) {
        this.trustedProxyIp = trustedProxyIp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
            String origin = request.getHeader("Origin");
            if (origin == null || !ALLOWED_ORIGINS.contains(origin)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origem não permitida");
                return;
            }
        }

        String user = request.getHeader("Remote-User");
        if (user != null && !user.isBlank()) {
            if (!trustedProxyIp.equals(request.getRemoteAddr())) {
                log.error("CVFORGE_PROXY_TRUST_MISMATCH path={} expected_proxy_ip={} actual_peer_ip={}",
                        request.getRequestURI(), trustedProxyIp, request.getRemoteAddr());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Proxy de autenticação não confiável");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            user.trim(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
        chain.doFilter(request, response);
    }
}
