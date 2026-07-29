package com.human.ev_relay_mes.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class CollectorApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Collector-Key";
    public static final String COLLECTOR_ROLE = "ROLE_COLLECTOR";

    private final byte[] configuredApiKey;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public CollectorApiKeyAuthenticationFilter(
            @Value("${mes.collector.api-key}") String configuredApiKey,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new IllegalStateException("mes.collector.api-key must not be blank");
        }
        this.configuredApiKey = configuredApiKey.getBytes(StandardCharsets.UTF_8);
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return !requestPath.equals("/api/collector")
                && !requestPath.startsWith("/api/collector/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String providedApiKey = request.getHeader(HEADER_NAME);
        if (!matches(providedApiKey)) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid collector API key"));
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "collector",
                        null,
                        List.of(new SimpleGrantedAuthority(COLLECTOR_ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matches(String providedApiKey) {
        if (providedApiKey == null || providedApiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredApiKey,
                providedApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
