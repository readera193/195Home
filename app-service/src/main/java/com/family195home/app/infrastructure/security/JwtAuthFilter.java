package com.family195home.app.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import com.family195home.app.shared.AuthenticatedUser;

/**
 * 對 /api/**（排除 /api/users/register、/api/users/login、/api/internal/**、靜態資源）
 * 以簽章密鑰本地驗證 JWT（見 research.md 決策 7）。
 * 驗證失敗（缺少/過期/簽章錯誤）不會在此丟出例外，而是保持未驗證狀態，
 * 交由 Spring Security 的 authorizeHttpRequests 規則統一回傳 401。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/internal/")) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                jwtTokenProvider.parseClaims(token).ifPresent(this::setAuthentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims) {
        Long userId = jwtTokenProvider.getUserId(claims);
        String email = claims.get("email", String.class);
        AuthenticatedUser principal = new AuthenticatedUser(userId, email);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
