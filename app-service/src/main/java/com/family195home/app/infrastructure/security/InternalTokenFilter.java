package com.family195home.app.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 驗證 /api/internal/** 的 X-Internal-Token 共用密鑰 Header，僅供 notification-service 呼叫
 * （見 contracts/app-service.md 內部 API 一節、research.md 決策 7）。
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenFilter(@Value("${app.internal.token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/internal/")) {
            String header = request.getHeader(HEADER_NAME);
            if (expectedToken.equals(header)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
